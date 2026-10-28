package jp.yaegaki.theta.home.data

import android.app.AppOpsManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.os.Process

/**
 * タスクバーに出す「最近使ったアプリ」と「よく使うアプリ」。
 * 端末の「使用状況へのアクセス」を許可されていれば、ほかの開き方（通知・ほかのアプリから）も含めて端末の記録から。
 * 許可が無ければ、このホームから開いた記録（[Store.launchLog]）から。
 */
object AppUsage {
    fun hasAccess(context: Context): Boolean = runCatching {
        val ops = context.getSystemService(AppOpsManager::class.java)
        ops.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName) == AppOpsManager.MODE_ALLOWED
    }.getOrDefault(false)

    /** 端末の記録：前に出た順（新しいもの先）のパッケージと、7 日間に前に出た回数。 */
    fun fromSystem(context: Context): Pair<List<String>, Map<String, Int>> {
        val usm = context.getSystemService(UsageStatsManager::class.java) ?: return emptyList<String>() to emptyMap()
        val now = System.currentTimeMillis()
        val events = usm.queryEvents(now - 7 * 86_400_000L, now)
        val order = ArrayList<Pair<String, Long>>()
        val counts = HashMap<String, Int>()
        val e = UsageEvents.Event()
        while (events.hasNextEvent()) {
            events.getNextEvent(e)
            if (e.eventType != UsageEvents.Event.ACTIVITY_RESUMED) continue
            val pkg = e.packageName ?: continue
            order += pkg to e.timeStamp
            counts[pkg] = (counts[pkg] ?: 0) + 1
        }
        val recent = order.sortedByDescending { it.second }.map { it.first }.distinct()
        return recent to counts
    }

    /**
     * 並べるもの：最近のもの（多くても recentMax）、そのあとよく使うもの（最近のものと重ならない）。
     * known は出してよいパッケージ（起動できるアプリ）、skip は出さないもの（このホームなど）。
     */
    fun pick(recent: List<String>, counts: Map<String, Int>, known: Set<String>, skip: Set<String>, recentMax: Int, total: Int): Pair<List<String>, List<String>> {
        val r = recent.filter { it in known && it !in skip }.take(recentMax)
        val f = counts.entries.filter { it.key in known && it.key !in skip && it.key !in r }
            .sortedByDescending { it.value }.map { it.key }.take((total - r.size).coerceAtLeast(0))
        return r to f
    }
}
