package app.thetahatch.home.hub

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import app.thetahatch.home.HomeApp
import java.util.concurrent.TimeUnit

/** 12 時間に 1 回、シリーズのアプリの新しい版を確かめる（受け持ちのときだけ）。 */
class UpdateWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val store = (applicationContext as HomeApp).store
        if (!store.autoUpdate) return Result.success()
        runCatching { UpdateHub.runAll(applicationContext, install = true, mobile = store.hubMobile, fromWorker = true) }
        store.lastUpdateCheck = System.currentTimeMillis()
        return Result.success()
    }

    companion object {
        private const val NAME = "hatch-update"

        fun schedule(context: Context) {
            val req = PeriodicWorkRequestBuilder<UpdateWorker>(12, TimeUnit.HOURS)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(NAME, ExistingPeriodicWorkPolicy.KEEP, req)
        }
    }
}
