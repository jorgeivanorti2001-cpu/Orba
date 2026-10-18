package com.orba.app.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Ajustes del usuario, observables desde la interfaz y desde el servicio de reproducción. */
class Prefs private constructor(context: Context) {
    private val sp = context.getSharedPreferences("settings", Context.MODE_PRIVATE)

    private val _maxHeight = MutableStateFlow(sp.getInt(K_HEIGHT, 720))
    val maxHeight: StateFlow<Int> = _maxHeight.asStateFlow()

    private val _autoDownload = MutableStateFlow(sp.getBoolean(K_AUTO, true))
    val autoDownload: StateFlow<Boolean> = _autoDownload.asStateFlow()

    private val _wifiOnly = MutableStateFlow(sp.getBoolean(K_WIFI, false))
    val wifiOnly: StateFlow<Boolean> = _wifiOnly.asStateFlow()

    private val _audioOnly = MutableStateFlow(sp.getBoolean(K_AUDIO, false))
    val audioOnly: StateFlow<Boolean> = _audioOnly.asStateFlow()

    /** Repetir el video que suena (hasta [MAX_PLAYS] veces) en vez de detenerse al final. */
    private val _loop = MutableStateFlow(sp.getBoolean(K_LOOP, true))
    val loop: StateFlow<Boolean> = _loop.asStateFlow()

    /** Nombre del usuario para el saludo del inicio (se pregunta una sola vez). */
    private val _userName = MutableStateFlow(sp.getString(K_NAME, null))
    val userName: StateFlow<String?> = _userName.asStateFlow()

    /** Escuchar: orden aleatorio y repetición (0 = no, 1 = toda la lista, 2 = la canción). */
    private val _shuffle = MutableStateFlow(sp.getBoolean(K_SHUFFLE, false))
    val shuffle: StateFlow<Boolean> = _shuffle.asStateFlow()
    private val _audioRepeat = MutableStateFlow(sp.getInt(K_AREPEAT, REPEAT_ALL))
    val audioRepeat: StateFlow<Int> = _audioRepeat.asStateFlow()

    // ---------- Audio (sección Escuchar): descargas y calidad propias ----------
    private val _autoDownloadAudio = MutableStateFlow(sp.getBoolean(K_AUTO_A, true))
    val autoDownloadAudio: StateFlow<Boolean> = _autoDownloadAudio.asStateFlow()
    private val _wifiOnlyAudio = MutableStateFlow(sp.getBoolean(K_WIFI_A, false))
    val wifiOnlyAudio: StateFlow<Boolean> = _wifiOnlyAudio.asStateFlow()
    /** 0 = calidad normal, 1 = ahorro (pesa cerca de la mitad). */
    private val _audioQuality = MutableStateFlow(sp.getInt(K_AQ, 0))
    val audioQuality: StateFlow<Int> = _audioQuality.asStateFlow()
    fun setAutoDownloadAudio(v: Boolean) { sp.edit().putBoolean(K_AUTO_A, v).apply(); _autoDownloadAudio.value = v }
    fun setWifiOnlyAudio(v: Boolean) { sp.edit().putBoolean(K_WIFI_A, v).apply(); _wifiOnlyAudio.value = v }
    fun setAudioQuality(v: Int) { sp.edit().putInt(K_AQ, v).apply(); _audioQuality.value = v }

    /** Preferir la pista de audio en español cuando la plataforma la ofrece (doblajes). */
    private val _spanishAudio = MutableStateFlow(sp.getBoolean(K_SPANISH, true))
    val spanishAudio: StateFlow<Boolean> = _spanishAudio.asStateFlow()
    fun setSpanishAudio(v: Boolean) { sp.edit().putBoolean(K_SPANISH, v).apply(); _spanishAudio.value = v }

    // ---------- Activación ----------
    var activationKey: String?
        get() = sp.getString(K_ACT, null)
        set(v) = sp.edit().putString(K_ACT, v).apply()
    var isAdmin: Boolean
        get() = sp.getBoolean(K_ADMIN, false)
        set(v) = sp.edit().putBoolean(K_ADMIN, v).apply()

    // ---------- Escuchar: letra y orden ----------
    private val _showLyrics = MutableStateFlow(sp.getBoolean(K_LYRICS, true))
    val showLyrics: StateFlow<Boolean> = _showLyrics.asStateFlow()
    fun setShowLyrics(v: Boolean) { sp.edit().putBoolean(K_LYRICS, v).apply(); _showLyrics.value = v }

    /** Orden de las canciones: [SORT_RECENT] o [SORT_AZ]. */
    private val _listenSort = MutableStateFlow(sp.getInt(K_SORT, SORT_RECENT))
    val listenSort: StateFlow<Int> = _listenSort.asStateFlow()
    fun setListenSort(v: Int) { sp.edit().putInt(K_SORT, v).apply(); _listenSort.value = v }

    // ---------- Teléfonos a los que se dio acceso (JSON) ----------
    private val _grantedDevices = MutableStateFlow(sp.getString(K_GRANTED, "[]") ?: "[]")
    val grantedDevices: StateFlow<String> = _grantedDevices.asStateFlow()
    fun setGrantedDevices(json: String) { sp.edit().putString(K_GRANTED, json).apply(); _grantedDevices.value = json }

    // ---------- Respaldo en Google Drive ----------
    private val _backupEmail = MutableStateFlow(sp.getString(K_BK_EMAIL, null))
    /** Cuenta de Google conectada (null = sin respaldo). */
    val backupEmail: StateFlow<String?> = _backupEmail.asStateFlow()
    fun setBackupEmail(v: String?) { sp.edit().putString(K_BK_EMAIL, v).apply(); _backupEmail.value = v }

    private val _lastBackupAt = MutableStateFlow(sp.getLong(K_BK_AT, 0L))
    val lastBackupAt: StateFlow<Long> = _lastBackupAt.asStateFlow()
    fun setLastBackupAt(v: Long) { sp.edit().putLong(K_BK_AT, v).apply(); _lastBackupAt.value = v }

    /** El permiso de Google caducó: hay que volver a tocar "Conectar". */
    private val _backupNeedsLogin = MutableStateFlow(sp.getBoolean(K_BK_LOGIN, false))
    val backupNeedsLogin: StateFlow<Boolean> = _backupNeedsLogin.asStateFlow()
    fun setBackupNeedsLogin(v: Boolean) { sp.edit().putBoolean(K_BK_LOGIN, v).apply(); _backupNeedsLogin.value = v }

    /** En Orba: mostrar también la sección Escuchar (Orba Music integrado). */
    private val _unified = MutableStateFlow(sp.getBoolean(K_UNIFIED, false))
    val unified: StateFlow<Boolean> = _unified.asStateFlow()
    fun setUnified(v: Boolean) { sp.edit().putBoolean(K_UNIFIED, v).apply(); _unified.value = v }

    fun setUserName(v: String) { sp.edit().putString(K_NAME, v.trim()).apply(); _userName.value = v.trim() }
    fun setShuffle(v: Boolean) { sp.edit().putBoolean(K_SHUFFLE, v).apply(); _shuffle.value = v }
    fun setAudioRepeat(v: Int) { sp.edit().putInt(K_AREPEAT, v).apply(); _audioRepeat.value = v }

    var lastEngineUpdate: Long
        get() = sp.getLong(K_ENGINE, 0)
        set(v) = sp.edit().putLong(K_ENGINE, v).apply()

    fun setMaxHeight(v: Int) { sp.edit().putInt(K_HEIGHT, v).apply(); _maxHeight.value = v }
    fun setAutoDownload(v: Boolean) { sp.edit().putBoolean(K_AUTO, v).apply(); _autoDownload.value = v }
    fun setWifiOnly(v: Boolean) { sp.edit().putBoolean(K_WIFI, v).apply(); _wifiOnly.value = v }
    fun setAudioOnly(v: Boolean) { sp.edit().putBoolean(K_AUDIO, v).apply(); _audioOnly.value = v }
    fun setLoop(v: Boolean) { sp.edit().putBoolean(K_LOOP, v).apply(); _loop.value = v }

    companion object {
        const val MAX_PLAYS = 10
        const val REPEAT_OFF = 0
        const val REPEAT_ALL = 1
        const val REPEAT_ONE = 2

        const val SORT_RECENT = 0
        const val SORT_AZ = 1

        private const val K_LYRICS = "showLyrics"
        private const val K_SORT = "listenSort"
        private const val K_GRANTED = "grantedDevices"
        private const val K_BK_EMAIL = "backupEmail"
        private const val K_BK_AT = "lastBackupAt"
        private const val K_BK_LOGIN = "backupNeedsLogin"
        private const val K_NAME = "userName"
        private const val K_AUTO_A = "autoDownloadAudio"
        private const val K_WIFI_A = "wifiOnlyAudio"
        private const val K_AQ = "audioQuality"
        private const val K_SPANISH = "spanishAudio"
        private const val K_ACT = "activationKey"
        private const val K_ADMIN = "isAdmin"
        private const val K_UNIFIED = "unified"
        private const val K_SHUFFLE = "shuffle"
        private const val K_AREPEAT = "audioRepeat"

        private const val K_HEIGHT = "maxHeight"
        private const val K_AUTO = "autoDownload"
        private const val K_WIFI = "wifiOnly"
        private const val K_AUDIO = "audioOnly"
        private const val K_LOOP = "loop"
        private const val K_ENGINE = "lastEngineUpdate"

        @Volatile
        private var instance: Prefs? = null
        fun get(context: Context): Prefs =
            instance ?: synchronized(this) {
                instance ?: Prefs(context.applicationContext).also { instance = it }
            }
    }
}
