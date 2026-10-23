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
}

/** 端末のカレンダー（Google カレンダーと同期済みのものを含む）から予定を読む。READ_CALENDAR が必要。 */
object Calendar {
    fun upcoming(context: Context, days: Int = 7): List<Event> {
        val now = System.currentTimeMillis()
        val dayStart = java.util.Calendar.getInstance().apply {
            set(java.util.Calendar.HOUR_OF_DAY, 0); set(java.util.Calendar.MINUTE, 0)
            set(java.util.Calendar.SECOND, 0); set(java.util.Calendar.MILLISECOND, 0)
        }.timeInMillis
        val uri = CalendarContract.Instances.CONTENT_URI.buildUpon().also {
            ContentUris.appendId(it, dayStart - 24L * 3600_000)
            ContentUris.appendId(it, dayStart + days * 24L * 3600_000)
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
                val localEnd = if (e.allDay) e.end - TimeZone.getDefault().getOffset(e.end) else e.end
                // 終わった予定は出さない。終日の予定は今日の分まで残す。
                if (if (e.allDay) localEnd > dayStart else e.end > now) out += e
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
