package app.thetahatch.home.data

import android.app.usage.NetworkStats
import android.app.usage.NetworkStatsManager
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.Uri
import java.time.LocalDate
import java.time.ZoneId

/**
 * モバイルデータの残り（ギガ）。IIJmio の残量の API は 2025-01 で終わったので、端末が測った今月のモバイル通信量から出す。
 * 残り＝今月の容量（プラン）＋足した分（繰り越し・チャージ）−今月使った量。月は 1 日に切り替わる（IIJmio と同じ）。
 */
object Giga {
    const val MY_IIJMIO = "jp.ad.iij.myiijmio"
    private const val GB = 1_000_000_000.0

    data class State(val usedGb: Double, val totalGb: Double, val low: Boolean) {
        val remainingGb: Double get() = (totalGb - usedGb).coerceAtLeast(0.0)
    }

    fun month(today: LocalDate = LocalDate.now()): String = "%04d-%02d".format(today.year, today.monthValue)

    fun monthStartMillis(today: LocalDate = LocalDate.now(), zone: ZoneId = ZoneId.systemDefault()): Long =
        today.withDayOfMonth(1).atStartOfDay(zone).toInstant().toEpochMilli()

    /** 今月のモバイル通信量（バイト）。使用状況へのアクセスが無ければ null。 */
    fun usedBytes(context: Context): Long? {
        if (!AppUsage.hasAccess(context)) return null
        val nsm = context.getSystemService(NetworkStatsManager::class.java) ?: return null
        return runCatching {
            val b: NetworkStats.Bucket = nsm.querySummaryForDevice(ConnectivityManager.TYPE_MOBILE, null, monthStartMillis(), System.currentTimeMillis())
            b.rxBytes + b.txBytes
        }.getOrNull()
    }

    /** extraGb は今月の分だけ数える（extraMonth が今月でなければ 0）。 */
    fun state(usedBytes: Long, planGb: Double, extraGb: Double, extraMonth: String, warnGb: Double, thisMonth: String = month()): State {
        val total = planGb + if (extraMonth == thisMonth) extraGb else 0.0
        val used = usedBytes / GB
        return State(used, total, total - used < warnGb)
    }

    /** My IIJmio の残量に合わせる：足した分＝残り＋使った量−プラン。 */
    fun extraFor(remainingGb: Double, usedBytes: Long, planGb: Double): Double = remainingGb + usedBytes / GB - planGb

    fun label(gb: Double): String = if (gb < 10) "%.1f".format(gb) else "%.0f".format(gb)

    /** My IIJmio を開く。入っていなければ Google Play。 */
    fun openIntent(context: Context): Intent =
        context.packageManager.getLaunchIntentForPackage(MY_IIJMIO)
            ?: Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=$MY_IIJMIO"))
}
