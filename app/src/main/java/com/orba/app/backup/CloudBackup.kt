package com.orba.app.backup

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.google.android.gms.auth.GoogleAuthUtil
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.AuthorizationResult
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.api.Scope
import com.google.android.gms.tasks.Tasks
import com.orba.app.data.Prefs
import com.orba.app.data.Repository
import com.orba.app.ui.lock.GrantedDevices
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

/**
 * Respaldo en Google Drive. La lista (enlaces, categorías, notas, favoritos) se guarda en una
 * carpeta privada de la app dentro de tu Drive, que no se ve entre tus archivos ni ocupa
 * espacio notable. En otro teléfono, al conectar la misma cuenta, se recupera todo.
 * Las descargas no se suben: se vuelven a descargar cuando quieras.
 *
 * Cada respaldo crea un archivo nuevo y luego borra los anteriores (Android no permite
 * "PATCH", que es lo que Drive pide para reemplazar el contenido de un archivo).
 */
object CloudBackup {
    private const val TAG = "OrbaBackup"
    private const val FILE_NAME = "orba-backup.json"
    private const val DRIVE_SCOPE = "https://www.googleapis.com/auth/drive.appdata"
    private val SCOPES = listOf(Scope(DRIVE_SCOPE), Scope("email"))

    sealed interface Auth {
        data class Token(val accessToken: String) : Auth
        /** Google necesita que confirmes la cuenta (se abre una ventana). */
        data class NeedsConsent(val intent: PendingIntent) : Auth
        data class Failed(val message: String) : Auth
    }

    /** Drive respondió con un error. [expired] = el permiso ya no sirve y hay que pedir otro. */
    class DriveException(message: String, val expired: Boolean = false) : Exception(message)

    private fun request() = AuthorizationRequest.builder().setRequestedScopes(SCOPES).build()

    private const val NO_DRIVE_PERMISSION =
        "Google no dio permiso para guardar en Drive. Toca «Conectar» otra vez y, si aparece la casilla de Google Drive, márcala."

    private fun fromResult(result: AuthorizationResult): Auth {
        val token = result.accessToken ?: return Auth.Failed("Google no entregó el permiso")
        val granted = runCatching { result.grantedScopes }.getOrNull().orEmpty()
        if (granted.isNotEmpty() && granted.none { it == DRIVE_SCOPE }) return Auth.Failed(NO_DRIVE_PERMISSION)
        return Auth.Token(token)
    }

    /** Pide el permiso a Google. Si ya se dio antes, vuelve sin mostrar nada. */
    suspend fun authorize(context: Context): Auth = withContext(Dispatchers.IO) {
        try {
            val result = Tasks.await(
                Identity.getAuthorizationClient(context.applicationContext).authorize(request()),
                30, TimeUnit.SECONDS,
            )
            val pending = result.pendingIntent
            if (result.hasResolution() && pending != null) Auth.NeedsConsent(pending) else fromResult(result)
        } catch (t: Throwable) {
            Log.w(TAG, "authorize", t)
            Auth.Failed(explain(t))
        }
    }

    /** Lee el permiso que volvió de la ventana de Google. */
    fun resultFromIntent(context: Context, data: Intent?): Auth = try {
        fromResult(Identity.getAuthorizationClient(context.applicationContext).getAuthorizationResultFromIntent(data))
    } catch (t: Throwable) {
        Log.w(TAG, "resultFromIntent", t)
        Auth.Failed(explain(t))
    }

    /** Olvida un permiso guardado (cuando Drive dice que ya no sirve, o al desconectar). */
    private suspend fun forget(context: Context, token: String) = withContext(Dispatchers.IO) {
        runCatching { GoogleAuthUtil.clearToken(context.applicationContext, token) }
            .onFailure { Log.w(TAG, "clearToken", it) }
    }

    /** Al desconectar: se borra el permiso guardado en el teléfono. Lo de Drive no se toca. */
    suspend fun disconnect(context: Context) {
        val prefs = Prefs.get(context)
        prefs.setBackupEmail(null)
        prefs.setBackupNeedsLogin(false)
        (authorizeSilently(context) as? Auth.Token)?.let { forget(context, it.accessToken) }
    }

    /** Igual que [authorize], pero nunca abre ventanas (para el respaldo automático). */
    private suspend fun authorizeSilently(context: Context): Auth = when (val a = authorize(context)) {
        is Auth.NeedsConsent -> Auth.Failed("Toca «Conectar» de nuevo para renovar el permiso")
        else -> a
    }

    private fun explain(t: Throwable): String {
        val m = (t.cause?.message ?: t.message ?: "").lowercase()
        return when {
            "10:" in m || "developer_error" in m ->
                "Google no reconoce esta app. Revisa en Google Cloud que el ID de cliente Android tenga el paquete com.orba.app y la huella SHA-1 correcta."
            "7:" in m || "network" in m -> "Sin conexión a internet"
            "16:" in m || "cancel" in m -> "Se canceló la conexión"
            else -> "No se pudo conectar con Google (${t.cause?.message ?: t.message ?: t.javaClass.simpleName})"
        }
    }

    // ---------- Google Drive (carpeta privada de la app) ----------

    data class Remote(val id: String, val modified: String?)

    suspend fun email(token: String): String? = withContext(Dispatchers.IO) {
        runCatching {
            val r = http("GET", "https://www.googleapis.com/oauth2/v3/userinfo", token)
            if (r.code in 200..299) JSONObject(r.body).optString("email").takeIf { it.isNotBlank() } else null
        }.getOrNull()
    }

    /** Todos los respaldos guardados, el más reciente primero. */
    private fun list(token: String): List<Remote> {
        val q = URLEncoder.encode("name='$FILE_NAME' and trashed=false", "UTF-8")
        val fields = URLEncoder.encode("files(id,modifiedTime)", "UTF-8")
        val body = ok(
            http(
                "GET",
                "https://www.googleapis.com/drive/v3/files?spaces=appDataFolder&q=$q&fields=$fields&orderBy=modifiedTime%20desc&pageSize=20",
                token,
            ),
            "leer tu Drive",
        )
        val files = JSONObject(body).optJSONArray("files") ?: return emptyList()
        return (0 until files.length()).map { i ->
            val f = files.getJSONObject(i)
            Remote(f.getString("id"), f.optString("modifiedTime").takeIf { it.isNotBlank() })
        }
    }

    suspend fun find(token: String): Remote? = withContext(Dispatchers.IO) { list(token).firstOrNull() }

    suspend fun download(token: String, id: String): JSONObject = withContext(Dispatchers.IO) {
        JSONObject(ok(http("GET", "https://www.googleapis.com/drive/v3/files/$id?alt=media", token), "descargar el respaldo"))
    }

    /** Sube un archivo nuevo y, si salió bien, borra los respaldos anteriores. */
    private suspend fun upload(token: String, json: String) = withContext(Dispatchers.IO) {
        val old = list(token)
        val boundary = "orba" + System.currentTimeMillis()
        val meta = JSONObject().put("name", FILE_NAME).put("parents", JSONArray().put("appDataFolder"))
        val multipart = buildString {
            append("--").append(boundary).append("\r\n")
            append("Content-Type: application/json; charset=UTF-8\r\n\r\n")
            append(meta.toString()).append("\r\n")
            append("--").append(boundary).append("\r\n")
            append("Content-Type: application/json; charset=UTF-8\r\n\r\n")
            append(json).append("\r\n")
            append("--").append(boundary).append("--\r\n")
        }
        ok(
            http(
                "POST",
                "https://www.googleapis.com/upload/drive/v3/files?uploadType=multipart&fields=id",
                token,
                body = multipart.toByteArray(Charsets.UTF_8),
                contentType = "multipart/related; boundary=$boundary",
            ),
            "guardar en Drive",
        )
        old.forEach { r -> runCatching { http("DELETE", "https://www.googleapis.com/drive/v3/files/${r.id}", token) } }
    }

    /** Arma el respaldo completo (lista + teléfonos con acceso). */
    suspend fun buildBackup(context: Context): String {
        val o = Repository.get(context).exportBackup()
        o.put("grantedDevices", JSONArray(Prefs.get(context).grantedDevices.value))
        return o.toString()
    }

    /**
     * Sube el respaldo ahora. Devuelve null si salió bien, o el motivo del error.
     * Si Google dice que el permiso venció, se pide uno nuevo sin molestar (una vez).
     */
    suspend fun backupNow(context: Context, token: String? = null): String? {
        val prefs = Prefs.get(context)
        var t = token ?: when (val a = authorizeSilently(context)) {
            is Auth.Token -> a.accessToken
            is Auth.Failed -> { if ("Conectar" in a.message) prefs.setBackupNeedsLogin(true); return a.message }
            is Auth.NeedsConsent -> { prefs.setBackupNeedsLogin(true); return "Toca «Conectar» de nuevo para renovar el permiso" }
        }
        val json = buildBackup(context)
        repeat(2) { attempt ->
            try {
                upload(t, json)
                prefs.setLastBackupAt(System.currentTimeMillis())
                prefs.setBackupNeedsLogin(false)
                return null
            } catch (e: DriveException) {
                if (!e.expired || attempt == 1) return e.message
                forget(context, t)
                t = when (val a = authorizeSilently(context)) {
                    is Auth.Token -> a.accessToken
                    else -> { prefs.setBackupNeedsLogin(true); return "Toca «Conectar» de nuevo para renovar el permiso" }
                }
            } catch (e: IOException) {
                return "Sin conexión a internet"
            } catch (e: Throwable) {
                Log.w(TAG, "backupNow", e)
                return "No se pudo guardar el respaldo (${e.message ?: e.javaClass.simpleName})"
            }
        }
        return "No se pudo guardar el respaldo"
    }

    /** Trae el respaldo de Drive y agrega lo que falte. Devuelve cuántos elementos se recuperaron. */
    suspend fun restore(context: Context, token: String): Int {
        val remote = find(token) ?: return 0
        val o = download(token, remote.id)
        val added = Repository.get(context).importBackup(o)
        o.optJSONArray("grantedDevices")?.let { GrantedDevices.merge(Prefs.get(context), GrantedDevices.parse(it.toString())) }
        return added
    }

    /** Programa un respaldo automático (se agrupan los cambios seguidos en uno solo). */
    fun schedule(context: Context, delaySeconds: Long = 20) {
        if (Prefs.get(context).backupEmail.value == null) return
        val request = OneTimeWorkRequestBuilder<BackupWorker>()
            .setInitialDelay(delaySeconds, TimeUnit.SECONDS)
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork("orba-backup", ExistingWorkPolicy.REPLACE, request)
    }

    // ---------- HTTP ----------

    private class Response(val code: Int, val body: String)

    private fun http(
        method: String,
        url: String,
        token: String,
        body: ByteArray? = null,
        contentType: String? = null,
    ): Response {
        val c = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = 15_000
            readTimeout = 30_000
            setRequestProperty("Authorization", "Bearer $token")
            if (body != null) {
                doOutput = true
                setRequestProperty("Content-Type", contentType ?: "application/octet-stream")
                setFixedLengthStreamingMode(body.size)
            }
        }
        try {
            if (body != null) c.outputStream.use { it.write(body) }
            val code = c.responseCode
            val stream = if (code in 200..299) c.inputStream else c.errorStream
            val text = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
            if (code !in 200..299) Log.w(TAG, "$method $url → $code $text")
            return Response(code, text)
        } finally {
            c.disconnect()
        }
    }

    /** Devuelve el cuerpo si salió bien; si no, un error con la explicación de Google en español. */
    private fun ok(r: Response, action: String): String {
        if (r.code in 200..299) return r.body
        val err = runCatching { JSONObject(r.body).optJSONObject("error") }.getOrNull()
        val reason = err?.optJSONArray("errors")?.optJSONObject(0)?.optString("reason").orEmpty()
        val status = err?.optString("status").orEmpty()
        val msg = err?.optString("message").orEmpty()
        val all = "$reason $status $msg".lowercase()
        throw when {
            r.code == 401 -> DriveException("El permiso de Google venció", expired = true)
            "accessnotconfigured" in all || "service_disabled" in all || "has not been used" in all ->
                DriveException("La API de Google Drive no está habilitada en tu proyecto de Google Cloud (APIs y servicios → Biblioteca → Google Drive API → Habilitar).")
            "insufficient" in all || "scope" in all -> DriveException(NO_DRIVE_PERMISSION)
            "storagequotaexceeded" in all -> DriveException("Tu Google Drive está lleno")
            "ratelimit" in all || "userratelimit" in all || r.code == 429 -> DriveException("Google pidió esperar un momento. Intenta de nuevo en unos minutos.")
            else -> DriveException("No se pudo $action (Drive respondió ${r.code}${if (msg.isNotBlank()) ": $msg" else ""})")
        }
    }
}
