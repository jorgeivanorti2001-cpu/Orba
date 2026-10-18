package com.orba.app.download

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.orba.app.data.DownloadState
import com.orba.app.data.Prefs
import com.orba.app.data.Repository
import com.yausername.youtubedl_android.YoutubeDL
import java.util.concurrent.TimeUnit

object Downloads {
    fun workName(id: Long) = "dl-$id"

    suspend fun enqueue(context: Context, id: Long) {
        val prefs = Prefs.get(context)
        val item = Repository.get(context).getVideo(id) ?: return
        if (item.platform == com.orba.app.engine.Engine.PLATFORM_LOCAL) return // ya está en el teléfono
        val isAudio = item.kind == com.orba.app.data.Kind.AUDIO
        val wifiOnly = if (isAudio) prefs.wifiOnlyAudio.value else prefs.wifiOnly.value
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(if (wifiOnly) NetworkType.UNMETERED else NetworkType.CONNECTED)
            .setRequiresStorageNotLow(true)
            .build()
        val request = OneTimeWorkRequestBuilder<DownloadWorker>()
            .setInputData(workDataOf(DownloadWorker.KEY_ID to id))
            .setConstraints(constraints)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .addTag("download")
            .build()
        Repository.get(context).videoDao.setDownload(id, DownloadState.QUEUED, 0f, null)
        WorkManager.getInstance(context).enqueueUniqueWork(workName(id), ExistingWorkPolicy.KEEP, request)
    }

    suspend fun cancel(context: Context, id: Long) {
        WorkManager.getInstance(context).cancelUniqueWork(workName(id))
        runCatching { YoutubeDL.getInstance().destroyProcessById(workName(id)) }
        Repository.get(context).deleteDownload(id) // borra archivos parciales y deja el estado en "sin descargar"
    }
}
