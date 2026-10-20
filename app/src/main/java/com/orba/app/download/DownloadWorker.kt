package com.orba.app.download

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import com.orba.app.OrbaApp
import com.orba.app.data.DownloadState
import com.orba.app.data.Prefs
import com.orba.app.data.Repository
import com.orba.app.data.isDownloaded
import com.orba.app.data.isAudio
import com.orba.app.engine.Engine
import com.orba.app.ui.MainActivity
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLRequest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import java.io.File

/** Descarga un video completo (video + audio unidos en MP4) a la memoria interna de la app. */
class DownloadWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    private val notificationManager =
        context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    // Máximo dos descargas a la vez: si agregas muchos enlaces, el teléfono no se pone lento
    override suspend fun doWork(): Result = DOWNLOAD_GATE.withPermit { runDownload() }

    private suspend fun runDownload(): Result {
        val id = inputData.getLong(KEY_ID, -1)
        val repo = Repository.get(applicationContext)
        val dao = repo.videoDao
        val video = repo.getVideo(id) ?: return Result.failure()
        if (video.isDownloaded()) return Result.success()
        if (video.platform == Engine.PLATFORM_LOCAL) return Result.failure()

        // Si estás abriendo un video o canción, la descarga espera un momento para no quitarle velocidad
        com.orba.app.engine.StreamResolver.awaitForegroundIdle()
        val notifId = NOTIF_BASE + id.toInt()
        runCatching { setForeground(foregroundInfo(notifId, video.title, -1f)) }
        dao.setDownload(id, DownloadState.DOWNLOADING, 0f, null)

        val dir = File(applicationContext.filesDir, "videos").apply { mkdirs() }
        // Los audios se descargan solo como audio (pesan mucho menos)
        val prefs = Prefs.get(applicationContext)
        val maxHeight = if (video.isAudio()) Engine.audioHeight(prefs.audioQuality.value) else prefs.maxHeight.value
        fun buildRequest(extractorArgs: String?) = YoutubeDLRequest(video.sourceUrl).apply {
            addOption("--no-playlist")
            addOption("-f", Engine.formatSelector(maxHeight))
            addOption("--merge-output-format", "mp4")
            addOption("-o", File(dir, "$id.%(ext)s").absolutePath)
            addOption("--no-mtime")
            addOption("-N", "4")
            // Pide el archivo por partes de 10 MB: YouTube no frena la velocidad como con una sola petición larga
            addOption("--http-chunk-size", "10M")
            addOption("--socket-timeout", "15")
            if (extractorArgs != null) addOption("--extractor-args", extractorArgs)
        }

        val progressScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        var lastUpdate = 0L
        return try {
            Engine.ensureInit(applicationContext)
            // Si la plataforma pide "iniciar sesión", se actualiza el motor y se prueba de otras formas
            var lastError: Throwable? = null
            for ((i, args) in Engine.extractorFallbacks(video.sourceUrl).withIndex()) {
                try {
                    runInterruptible(Dispatchers.IO) {
                        YoutubeDL.getInstance().execute(buildRequest(args), Downloads.workName(id), false) { progress, _, _ ->
                            val now = System.currentTimeMillis()
                            if (now - lastUpdate > 800 && progress >= 0f) {
                                lastUpdate = now
                                val fraction = (progress / 100f).coerceIn(0f, 1f)
                                progressScope.launch { dao.setProgress(id, fraction) }
                                setForegroundAsync(foregroundInfo(notifId, video.title, fraction))
                            }
                        }
                    }
                    lastError = null
                    break
                } catch (t: Throwable) {
                    if (isStopped || t is kotlinx.coroutines.CancellationException) throw t
                    lastError = t
                    if (!Engine.isRetryable(t)) break
                    if (i == 0) Engine.updateInBackground(applicationContext)
                }
            }
            lastError?.let { throw it }
            val file = dir.listFiles()
                ?.filter { Regex("^$id\\.(mp4|mkv|webm|m4a|mov|mp3|opus|ogg|aac)$").matches(it.name) }
                ?.maxByOrNull { it.length() }
                ?: error("El archivo descargado no apareció")
            dao.setDownloaded(id, file.absolutePath, file.length())
            dao.setUnavailable(id, false)
            notifyFinished(id, video.title, ok = true)
            Result.success()
        } catch (t: Throwable) {
            runCatching { YoutubeDL.getInstance().destroyProcessById(Downloads.workName(id)) }
            when {
                isStopped -> Result.failure()
                Engine.looksRemoved(t) -> {
                    dao.setDownload(id, DownloadState.FAILED, 0f, Engine.friendlyError(t))
                    dao.setUnavailable(id, true)
                    notifyFinished(id, video.title, ok = false)
                    Result.failure()
                }
                runAttemptCount < 2 -> {
                    dao.setDownload(id, DownloadState.QUEUED, 0f, null)
                    Result.retry()
                }
                else -> {
                    dao.setDownload(id, DownloadState.FAILED, 0f, Engine.friendlyError(t))
                    notifyFinished(id, video.title, ok = false)
                    Result.failure()
                }
            }
        } finally {
            progressScope.cancel()
        }
    }

    private fun openAppIntent(): PendingIntent = PendingIntent.getActivity(
        applicationContext, 0,
        Intent(applicationContext, MainActivity::class.java),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    private fun foregroundInfo(notifId: Int, title: String, fraction: Float): ForegroundInfo {
        val n = NotificationCompat.Builder(applicationContext, OrbaApp.CHANNEL_DOWNLOADS)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle("Descargando")
            .setContentText(title)
            .setOnlyAlertOnce(true)
            .setOngoing(true)
            .setContentIntent(openAppIntent())
            .setProgress(100, (fraction * 100).toInt().coerceAtLeast(0), fraction < 0f)
            .build()
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ForegroundInfo(notifId, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            ForegroundInfo(notifId, n)
        }
    }

    private fun notifyFinished(id: Long, title: String, ok: Boolean) {
        val n = NotificationCompat.Builder(applicationContext, OrbaApp.CHANNEL_DOWNLOADS)
            .setSmallIcon(if (ok) android.R.drawable.stat_sys_download_done else android.R.drawable.stat_notify_error)
            .setContentTitle(if (ok) "Disponible sin conexión" else "No se pudo descargar")
            .setContentText(title)
            .setAutoCancel(true)
            .setContentIntent(openAppIntent())
            .build()
        runCatching { notificationManager.notify(DONE_BASE + id.toInt(), n) }
    }

    companion object {
        const val KEY_ID = "id"
        private const val NOTIF_BASE = 10_000
        private const val DONE_BASE = 50_000
        private val DOWNLOAD_GATE = Semaphore(2)
    }
}
