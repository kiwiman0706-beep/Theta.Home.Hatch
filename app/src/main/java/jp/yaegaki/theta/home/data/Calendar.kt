package jp.yaegaki.theta.home.data

import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.provider.CalendarContract
import java.util.TimeZone

data class Event(
    val eventId: Long,
    val title: String,
    val begin: Long,
    val end: Long,
    val allDay: Boolean,
    val location: String?,
    val color: Int,
) {
    /** 終日の予定は UTC の 0 時で記録されるため、端末の時刻帯の同じ日付に読み替えた開始時刻。 */
    val localBegin: Long
        get() = if (allDay) begin - TimeZone.getDefault().getOffset(begin) else begin

    val localEnd: Long
        get() = if (allDay) end - TimeZone.getDefault().getOffset(end) else end

    /** [from, to) に少しでも掛かるか。 */
    fun overlaps(from: Long, to: Long): Boolean = localBegin < to && maxOf(localEnd, localBegin + 1) > from
}

/** 端末のカレンダー（Google カレンダーと同期済みのものを含む）から予定を読む。READ_CALENDAR が必要。 */
object Calendar {
    const val DAY = 24L * 3600_000

    /** その日の 0 時（端末の時刻帯）。 */
    fun startOfDay(t: Long = System.currentTimeMillis()): Long = java.util.Calendar.getInstance().apply {
        timeInMillis = t
        set(java.util.Calendar.HOUR_OF_DAY, 0); set(java.util.Calendar.MINUTE, 0)
        set(java.util.Calendar.SECOND, 0); set(java.util.Calendar.MILLISECOND, 0)
    }.timeInMillis

    /** 日をまたいで足す（夏時間でもずれないよう暦で数える）。 */
    fun addDays(t: Long, days: Int): Long = java.util.Calendar.getInstance().apply {
        timeInMillis = t
        add(java.util.Calendar.DAY_OF_MONTH, days)
    }.timeInMillis

    /** [from, to) に掛かる予定。終わったものも含む。 */
    fun range(context: Context, from: Long, to: Long): List<Event> = query(context, from - DAY, to + DAY).filter { it.overlaps(from, to) }

    /** 今日から days 日分。今日の終わった予定も含む（「今日の予定」に使う）。 */
    fun upcoming(context: Context, days: Int = 7): List<Event> {
        val dayStart = startOfDay()
        return range(context, dayStart, addDays(dayStart, days))
    }

    private fun query(context: Context, from: Long, to: Long): List<Event> {
        val uri = CalendarContract.Instances.CONTENT_URI.buildUpon().also {
            ContentUris.appendId(it, from)
            ContentUris.appendId(it, to)
        }.build()
        val cols = arrayOf(
            CalendarContract.Instances.EVENT_ID,
            CalendarContract.Instances.TITLE,
            CalendarContract.Instances.BEGIN,
            CalendarContract.Instances.END,
            CalendarContract.Instances.ALL_DAY,
            CalendarContract.Instances.EVENT_LOCATION,
            CalendarContract.Instances.DISPLAY_COLOR,
        )
        val out = ArrayList<Event>()
        context.contentResolver.query(uri, cols, null, null, "${CalendarContract.Instances.BEGIN} ASC")?.use { c ->
            while (c.moveToNext()) {
                val e = Event(
                    eventId = c.getLong(0),
                    title = c.getString(1)?.ifBlank { null } ?: "（無題）",
                    begin = c.getLong(2),
                    end = c.getLong(3),
                    allDay = c.getInt(4) == 1,
                    location = c.getString(5)?.ifBlank { null },
                    color = c.getInt(6),
                )
                out += e
            }
        }
        return out.sortedBy { it.localBegin }
    }

    fun openIntent(e: Event): Intent =
        Intent(Intent.ACTION_VIEW, ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, e.eventId))
            .putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, e.begin)
            .putExtra(CalendarContract.EXTRA_EVENT_END_TIME, e.end)

    fun openAppIntent(): Intent {
        val time = CalendarContract.CONTENT_URI.buildUpon().appendPath("time")
        ContentUris.appendId(time, System.currentTimeMillis())
        return Intent(Intent.ACTION_VIEW).setData(time.build())
    }

    fun newEventIntent(): Intent = Intent(Intent.ACTION_INSERT).setData(CalendarContract.Events.CONTENT_URI)
}
