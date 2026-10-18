package com.orba.app.ui.lock

import android.annotation.SuppressLint
import android.content.Context
import android.provider.Settings
import com.orba.app.BuildConfig
import com.orba.app.data.Prefs
import java.security.MessageDigest
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * Permiso de uso: cada teléfono tiene un código propio y solo funciona con la clave
 * que genera el dueño (desde Ajustes → Acceso). La clave sirve únicamente en ese teléfono.
 */
object License {
    private const val ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"

    /** Código del teléfono (igual en todas las versiones de Orba del mismo teléfono). */
    @SuppressLint("HardwareIds")
    fun deviceCode(context: Context): String {
        val id = Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID) ?: "orba"
        val hash = MessageDigest.getInstance("SHA-256").digest(("orba-device:$id").toByteArray())
        return group(encode(hash, 8), 4)
    }

    /** Clave de activación para un código de teléfono. */
    fun keyFor(code: String): String = group(encode(hmac("user:" + normalize(code)), 10), 5)

    private fun adminKeyFor(code: String): String = group(encode(hmac("admin:" + normalize(code)), 10), 5)

    fun isActivated(context: Context): Boolean {
        val stored = Prefs.get(context).activationKey ?: return false
        val code = deviceCode(context)
        val s = normalize(stored)
        return s == normalize(keyFor(code)) || s == normalize(adminKeyFor(code))
    }

    fun isAdmin(context: Context): Boolean {
        val stored = Prefs.get(context).activationKey ?: return false
        return normalize(stored) == normalize(adminKeyFor(deviceCode(context)))
    }

    /** Activa con la clave recibida. Devuelve false si no corresponde a este teléfono. */
    fun activate(context: Context, key: String): Boolean {
        val ok = normalize(key) == normalize(keyFor(deviceCode(context)))
        if (ok) Prefs.get(context).activationKey = keyFor(deviceCode(context))
        return ok
    }

    /** El dueño desbloquea su propio teléfono con la contraseña de administrador. */
    fun activateAdmin(context: Context, password: String): Boolean {
        val ok = password.trim().equals(BuildConfig.ADMIN_PASSWORD, ignoreCase = true)
        if (ok) Prefs.get(context).activationKey = adminKeyFor(deviceCode(context))
        return ok
    }

    fun isValidCode(code: String): Boolean = normalize(code).length == 8 && normalize(code).all { it in ALPHABET }

    // ---------- utilidades ----------

    fun normalize(s: String): String = s.uppercase().filter { it.isLetterOrDigit() }

    private fun hmac(text: String): ByteArray {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(BuildConfig.LICENSE_SECRET.toByteArray(), "HmacSHA256"))
        return mac.doFinal(text.toByteArray())
    }

    private fun encode(bytes: ByteArray, length: Int): String {
        val sb = StringBuilder()
        var i = 0
        while (sb.length < length) {
            sb.append(ALPHABET[(bytes[i % bytes.size].toInt() and 0xFF) % ALPHABET.length])
            i++
        }
        return sb.toString()
    }

    private fun group(s: String, size: Int): String = s.chunked(size).joinToString("-")
}
