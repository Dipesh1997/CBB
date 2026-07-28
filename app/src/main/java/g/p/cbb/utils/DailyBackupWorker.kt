package g.p.cbb.utils

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters

class DailyBackupWorker(
    context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result {
        val success = BackupManager.exportDatabase(applicationContext, isAuto = true)
        return if (success) Result.success() else Result.retry()
    }
}
