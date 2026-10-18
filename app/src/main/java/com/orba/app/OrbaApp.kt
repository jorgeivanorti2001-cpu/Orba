package com.orba.app

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import android.util.Log
import com.orba.app.data.DownloadState
import com.orba.app.data.Prefs
import com.orba.app.data.Repository
import com.orba.app.download.Downloads
import com.orba.app.engine.Engine
import com.yausername.youtubedl_android.YoutubeDL
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import com.orba.app.backup.CloudBackup

class OrbaApp : Application() {
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        createChannels()
        Engine.preferSpanish = Prefs.get(this).spanishAudio.value
        appScope.launch {
            runCatching { Engine.ensureInit(this@OrbaApp) }
                .onFailure { Log.e("Orba", "No se pudo iniciar el motor", it) }
            autoUpdateEngine()
            resumeDownloads()
        }
        watchForBackup()
    }

    /** Cada vez que cambia la lista (enlaces, categorías o notas) se programa un respaldo en Drive. */
    @OptIn(FlowPreview::class)
    private fun watchForBackup() {
        val repo = Repository.get(this)
        val prefs = Prefs.get(this)
        appScope.launch {
            combine(repo.videos, repo.categories, repo.allNotes, prefs.grantedDevices) { v, c, n, g ->
                // Solo lo que va en el respaldo (no el progreso de descargas ni la posición)
                listOf(
                    v.filter { it.platform != Engine.PLATFORM_LOCAL }
                        .map { "${it.sourceUrl}|${it.kind}|${it.title}|${it.categoryId}|${it.favorite}|${it.orientation}|${it.boostDb}" },
                    c.map { "${it.id}|${it.name}" },
                    n.map { "${it.id}|${it.text}|${it.positionMs}" },
                    listOf(g),
                ).hashCode()
            }
                .distinctUntilChanged()
                .drop(1) // el estado al abrir la app no es un cambio
                .debounce(5_000)
                .collect { CloudBackup.schedule(this@OrbaApp) }
        }
    }

    /** YouTube cambia seguido; mantener yt-dlp al día evita que los enlaces dejen de funcionar. */
    private fun autoUpdateEngine() {
        val prefs = Prefs.get(this)
        val threeDays = 3 * 24 * 60 * 60 * 1000L
        if (System.currentTimeMillis() - prefs.lastEngineUpdate < threeDays) return
        runCatching {
            YoutubeDL.getInstance().updateYoutubeDL(this)
            prefs.lastEngineUpdate = System.currentTimeMillis()
        }.onFailure { Log.w("Orba", "Actualización automática falló", it) }
    }

    /** Si la app se cerró a mitad de una descarga, se vuelve a poner en cola. */
    private suspend fun resumeDownloads() {
        Repository.get(this).videoDao.unfinishedDownloads().forEach {
            if (it.downloadState == DownloadState.QUEUED || it.downloadState == DownloadState.DOWNLOADING) {
                Downloads.enqueue(this, it.id)
            }
        }
    }

    private fun createChannels() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_DOWNLOADS, "Descargas", NotificationManager.IMPORTANCE_LOW)
        )
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_NOTICES, "Avisos", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "Cuando termina de agregar videos o canciones"
            }
        )
    }

    companion object {
        const val CHANNEL_DOWNLOADS = "downloads"
        const val CHANNEL_NOTICES = "avisos"
    }
}
