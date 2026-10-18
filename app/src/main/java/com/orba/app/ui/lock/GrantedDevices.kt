package com.orba.app.ui.lock

import com.orba.app.data.Prefs
import org.json.JSONArray
import org.json.JSONObject

/** Un teléfono al que el dueño le dio clave. */
data class GrantedDevice(val code: String, val name: String, val grantedAt: Long)

/**
 * Lista de teléfonos con acceso (solo en el teléfono del dueño y en su respaldo).
 * Sirve para saber a quién se le dio clave; no puede saber si la clave ya se usó.
 */
object GrantedDevices {

    fun parse(json: String): List<GrantedDevice> = runCatching {
        val arr = JSONArray(json)
        (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            GrantedDevice(o.getString("code"), o.optString("name"), o.optLong("at"))
        }
    }.getOrDefault(emptyList())

    fun toJson(list: List<GrantedDevice>): String = JSONArray().apply {
        list.forEach { d -> put(JSONObject().put("code", d.code).put("name", d.name).put("at", d.grantedAt)) }
    }.toString()

    fun add(prefs: Prefs, code: String, name: String) {
        val clean = License.normalize(code)
        val pretty = clean.chunked(4).joinToString("-")
        val list = parse(prefs.grantedDevices.value).filterNot { License.normalize(it.code) == clean }
        prefs.setGrantedDevices(toJson(list + GrantedDevice(pretty, name.trim(), System.currentTimeMillis())))
    }

    fun remove(prefs: Prefs, code: String) {
        val clean = License.normalize(code)
        prefs.setGrantedDevices(toJson(parse(prefs.grantedDevices.value).filterNot { License.normalize(it.code) == clean }))
    }

    /** Une la lista del respaldo con la local (sin repetir teléfonos). */
    fun merge(prefs: Prefs, incoming: List<GrantedDevice>) {
        val current = parse(prefs.grantedDevices.value)
        val known = current.map { License.normalize(it.code) }.toSet()
        val extra = incoming.filter { License.normalize(it.code) !in known }
        if (extra.isNotEmpty()) prefs.setGrantedDevices(toJson((current + extra).sortedBy { it.grantedAt }))
    }
}
