package com.orba.app.backup

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.orba.app.data.Prefs

/** Respaldo automático en segundo plano, unos segundos después de cada cambio. */
class BackupWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        if (Prefs.get(applicationContext).backupEmail.value == null) return Result.success()
        val error = CloudBackup.backupNow(applicationContext)
        return when {
            error == null -> Result.success()
            Prefs.get(applicationContext).backupNeedsLogin.value -> Result.failure()
            runAttemptCount < 3 -> Result.retry()
            else -> Result.failure()
        }
    }
}
