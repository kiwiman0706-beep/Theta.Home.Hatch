package jp.yaegaki.theta.home.ui

import android.Manifest
import android.content.Intent
import android.provider.CalendarContract
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import jp.yaegaki.theta.home.HomeModel
import jp.yaegaki.theta.home.data.Calendar
import jp.yaegaki.theta.home.data.Event
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.Calendar as JCal

private val WEEK = listOf("SUN", "MON", "TUE", "WED", "THU", "FRI", "SAT")

/** 日曜始まりの週の最初の日。 */
private fun weekStart(t: Long): Long = Calendar.startOfDay(t).let { d ->
    val c = JCal.getInstance().apply { timeInMillis = d }
    Calendar.addDays(d, -(c.get(JCal.DAY_OF_WEEK) - JCal.SUNDAY))
}

private fun monthStart(t: Long): Long = JCal.getInstance().apply {
    timeInMillis = Calendar.startOfDay(t)
    set(JCal.DAY_OF_MONTH, 1)
}.timeInMillis

private fun addMonths(t: Long, n: Int): Long = JCal.getInstance().apply { timeInMillis = t; add(JCal.MONTH, n) }.timeInMillis

private fun field(t: Long, f: Int) = JCal.getInstance().apply { timeInMillis = t }.get(f)

/** その日の予定。終日が先、あとは始まる順。 */
private fun eventsOn(all: List<Event>, day: Long): List<Event> {
    val next = Calendar.addDays(day, 1)
    return all.filter { it.overlaps(day, next) }.sortedWith(compareBy({ !it.allDay }, { it.localBegin }, { it.title }))
}

private fun Event.tint(p: Palette): Color = if (color != 0) Color(color) else p.widget

/**
 * カレンダーのドロワー：月・週・日を切り替える。月は日曜始まりで、予定は何件あっても全部出す（週の行の高さを予定の数に合わせる）。
 * 左右になぞると前後へ。日付を押すとその日の表示へ。
 */
@Composable
fun CalendarDrawer(model: HomeModel) {
    val p = LocalPalette.current
    val actions = LocalActions.current
    if (!model.has(Manifest.permission.READ_CALENDAR)) {
        NeedPermission("端末のカレンダー（Google カレンダーと同期しているもの）から予定を表示します。", "予定の読み取りを許可") {
            actions.requestPermissions(arrayOf(Manifest.permission.READ_CALENDAR)) { model.refreshEvents() }
        }
        return
    }
    var anchor by remember { mutableLongStateOf(Calendar.startOfDay()) }
    val mode = model.calendarMode
    val today = Calendar.startOfDay()
    val (from, to) = when (mode) {
        "month" -> {
            val first = weekStart(monthStart(anchor))
            val last = Calendar.addDays(weekStart(Calendar.addDays(addMonths(monthStart(anchor), 1), -1)), 7)
            first to last
        }
        "week" -> weekStart(anchor).let { it to Calendar.addDays(it, 7) }
        else -> anchor to Calendar.addDays(anchor, 1)
    }
    LaunchedEffect(from, to) { model.loadCalendar(from, to) }
    fun move(n: Int) {
        anchor = when (mode) {
            "month" -> addMonths(monthStart(anchor), n)
            "week" -> Calendar.addDays(anchor, 7 * n)
            else -> Calendar.addDays(anchor, n)
        }
    }
    val title = when (mode) {
        "month" -> SimpleDateFormat("yyyy.MM", Locale.US).format(Date(anchor))
        "week" -> SimpleDateFormat("MM/dd", Locale.US).format(Date(from)) + " – " + SimpleDateFormat("MM/dd", Locale.US).format(Date(Calendar.addDays(to, -1)))
        else -> SimpleDateFormat("MM/dd EEE", Locale.US).format(Date(anchor)).uppercase(Locale.US)
    }

    Column(Modifier.fillMaxSize()) {
        // 表示の切り替えと前後
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            listOf("month" to "MONTH", "week" to "WEEK", "day" to "DAY").forEach { (m, label) ->
                val on = m == mode
                Text(label, color = if (on) p.onAccent else p.fg, fontSize = 12.sp, fontFamily = Hud.mono,
                    modifier = Modifier.background(if (on) p.accent else Color.Transparent).border(1.dp, if (on) p.accent else p.line)
                        .clickable(onClickLabel = "$label で見る") { model.changeCalendarMode(m) }.padding(horizontal = 10.dp, vertical = 7.dp))
            }
            Spacer(Modifier.weight(1f))
            Text("TODAY", color = p.accent, fontSize = 12.sp, fontFamily = Hud.mono,
                modifier = Modifier.border(1.dp, p.accent).clickable(onClickLabel = "今日へ") { anchor = today }.padding(horizontal = 8.dp, vertical = 7.dp))
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            GlyphButton(Glyph.BACK, "前へ", { move(-1) })
            Text(title, color = p.fg, fontSize = 20.sp, fontFamily = Hud.display, modifier = Modifier.weight(1f), maxLines = 1)
            GlyphButton(Glyph.PLUS, "予定を追加", {
                val begin = if (mode == "day") anchor + 9 * 3600_000L else maxOf(System.currentTimeMillis(), anchor)
                actions.start(Calendar.newEventIntent().putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, begin))
            })
            Box(Modifier.clickable(onClickLabel = "次へ") { move(1) }.padding(12.dp)) {
                Box(Modifier.graphicsFlip()) { GlyphIcon(Glyph.BACK, p.muted) }
            }
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(p.line))
        Box(
            Modifier.weight(1f).fillMaxWidth().pointerInput(mode, anchor) {
                var total = 0f
                detectHorizontalDragGestures(onDragStart = { total = 0f }, onDragEnd = {
                    if (total > 120) move(-1) else if (total < -120) move(1)
                }) { _, d -> total += d }
            },
        ) {
            when (mode) {
                "month" -> MonthView(model.calendarEvents, from, to, anchor, today) { anchor = it; model.changeCalendarMode("day") }
                "week" -> WeekView(model.calendarEvents, from, today, { anchor = it; model.changeCalendarMode("day") }) { actions.start(Calendar.openIntent(it)) }
                else -> DayView(model.calendarEvents, anchor, today) { actions.start(Calendar.openIntent(it)) }
            }
        }
    }
}

/** 右向きの矢印（BACK を左右反転）。 */
private fun Modifier.graphicsFlip() = this.graphicsLayer(scaleX = -1f)

/** 月：日曜始まり。1 日 1 マス、予定は小さな字で全部並べる（週の行はいちばん多い日に合わせて伸びる）。 */
@Composable
internal fun MonthView(all: List<Event>, from: Long, to: Long, anchor: Long, today: Long, onDay: (Long) -> Unit) {
    val p = LocalPalette.current
    val month = field(anchor, JCal.MONTH)
    val weeks = generateSequence(from) { Calendar.addDays(it, 7) }.takeWhile { it < to }.toList()
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 4.dp, vertical = 4.dp)) {
        Row(Modifier.fillMaxWidth()) {
            WEEK.forEachIndexed { i, w ->
                Text(w, color = when (i) { 0 -> p.accent; 6 -> p.muted; else -> p.faint }, fontSize = 10.sp, fontFamily = Hud.mono,
                    modifier = Modifier.weight(1f).padding(vertical = 4.dp), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
            }
        }
        weeks.forEach { ws ->
            Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
                for (i in 0 until 7) {
                    val day = Calendar.addDays(ws, i)
                    val inMonth = field(day, JCal.MONTH) == month
                    val isToday = day == today
                    val list = eventsOn(all, day)
                    Column(
                        Modifier.weight(1f).fillMaxHeight().heightIn(min = 64.dp).border(0.5.dp, p.line)
                            .background(if (isToday) p.raised else Color.Transparent)
                            .clickable(onClickLabel = "この日を見る") { onDay(day) }.padding(2.dp),
                        verticalArrangement = Arrangement.spacedBy(1.dp),
                    ) {
                        Text(
                            field(day, JCal.DAY_OF_MONTH).toString(),
                            color = when {
                                isToday -> p.onAccent
                                !inMonth -> p.faint
                                i == 0 -> p.accent
                                else -> p.fg
                            },
                            fontSize = 11.sp, fontFamily = Hud.mono,
                            modifier = Modifier.background(if (isToday) p.accent else Color.Transparent).padding(horizontal = 3.dp),
                        )
                        list.forEach { e ->
                            val c = e.tint(p)
                            Row(
                                Modifier.fillMaxWidth().background(if (e.allDay) c.copy(alpha = 0.28f) else Color.Transparent).clipToBounds(),
                                verticalAlignment = Alignment.Top,
                            ) {
                                Box(Modifier.width(2.dp).height(11.dp).background(c))
                                Text(
                                    e.title, color = if (inMonth) p.fg else p.muted, fontSize = 9.sp, lineHeight = 11.sp,
                                    maxLines = 2, overflow = TextOverflow.Clip, modifier = Modifier.padding(start = 2.dp),
                                )
                            }
                        }
                    }
                }
            }
        }
        Note("日付を押すとその日の予定、左右になぞると前後の月へ。")
    }
}

/** 週：日曜から土曜まで、日ごとに予定を並べる。 */
@Composable
internal fun WeekView(all: List<Event>, from: Long, today: Long, onDay: (Long) -> Unit, onEvent: (Event) -> Unit) {
    val p = LocalPalette.current
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 12.dp, vertical = 6.dp)) {
        for (i in 0 until 7) {
            val day = Calendar.addDays(from, i)
            val list = eventsOn(all, day)
            val isToday = day == today
            Row(
                Modifier.fillMaxWidth().padding(top = 10.dp, bottom = 4.dp).clickable(onClickLabel = "この日を見る") { onDay(day) },
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(WEEK[i], color = if (isToday) p.onAccent else if (i == 0) p.accent else p.muted, fontSize = 11.sp, fontFamily = Hud.mono,
                    modifier = Modifier.background(if (isToday) p.accent else Color.Transparent).padding(horizontal = 4.dp, vertical = 1.dp))
                Text(SimpleDateFormat("MM/dd", Locale.US).format(Date(day)), color = p.fg, fontSize = 15.sp, fontFamily = Hud.mono)
                Text("%02d".format(list.size), color = p.faint, fontSize = 10.sp, fontFamily = Hud.mono)
                Ticks(Modifier.weight(1f).height(5.dp))
            }
            if (list.isEmpty()) Text("—", color = p.faint, fontSize = 12.sp, modifier = Modifier.padding(start = 8.dp))
            list.forEach { e -> EventLine(e) { onEvent(e) } }
        }
        Gap(10.dp)
    }
}

/** 日：上に終日、下に 0〜24 時の時間割。重なる予定は横に並べる。今の時刻に線。 */
@Composable
internal fun DayView(all: List<Event>, day: Long, today: Long, onEvent: (Event) -> Unit) {
    val p = LocalPalette.current
    val hourH = 48.dp
    val list = eventsOn(all, day)
    val allDay = list.filter { it.allDay }
    val timed = list.filter { !it.allDay }
    val scroll = rememberScrollState()
    val density = LocalDensity.current
    LaunchedEffect(day) {
        val h = if (day == today) (field(System.currentTimeMillis(), JCal.HOUR_OF_DAY) - 1).coerceAtLeast(0) else 8
        scroll.scrollTo(with(density) { (hourH * h).roundToPx() })
    }
    // 重なりを列に分ける
    val lanes = ArrayList<Long>()
    val lane = timed.associateWith { e ->
        val i = lanes.indexOfFirst { it <= e.begin }.let { if (it < 0) { lanes += 0L; lanes.lastIndex } else it }
        lanes[i] = maxOf(e.end, e.begin + 15 * 60_000)
        i
    }
    val laneCount = lanes.size.coerceAtLeast(1)
    Column(Modifier.fillMaxSize()) {
        if (allDay.isNotEmpty()) Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            allDay.forEach { e ->
                Text(e.title, color = p.fg, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.fillMaxWidth().background(e.tint(p).copy(alpha = 0.3f)).clickable { onEvent(e) }.padding(horizontal = 8.dp, vertical = 4.dp))
            }
        }
        if (list.isEmpty()) Note("予定はありません")
        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth().verticalScroll(scroll)) {
            val labelW = 44.dp
            val colW = (maxWidth - labelW - 8.dp) / laneCount
            Box(Modifier.fillMaxWidth().height(hourH * 24)) {
                for (h in 0 until 24) {
                    Box(Modifier.offset(y = hourH * h).fillMaxWidth().height(0.5.dp).background(p.line))
                    Text("%02d".format(h), color = p.faint, fontSize = 10.sp, fontFamily = Hud.mono, modifier = Modifier.offset(x = 8.dp, y = hourH * h + 2.dp))
                }
                timed.forEach { e ->
                    val start = ((maxOf(e.begin, day) - day) / 60_000f)
                    val end = ((minOf(e.end, Calendar.addDays(day, 1)) - day) / 60_000f).coerceAtLeast(start + 20)
                    val c = e.tint(p)
                    Column(
                        Modifier.offset(x = labelW + colW * (lane[e] ?: 0), y = hourH * (start / 60f))
                            .width(colW - 2.dp).height(hourH * ((end - start) / 60f))
                            .background(c.copy(alpha = 0.25f)).border(1.dp, c).clickable { onEvent(e) }.padding(horizontal = 4.dp, vertical = 2.dp),
                    ) {
                        val time = SimpleDateFormat("HH:mm", Locale.US).format(Date(e.begin))
                        if (end - start < 50) {
                            // 短い予定は時刻と題名を 1 行に
                            Text("$time ${e.title}", color = p.fg, fontSize = 11.sp, lineHeight = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        } else {
                            Text(time, color = p.fg, fontSize = 10.sp, fontFamily = Hud.mono)
                            Text(e.title, color = p.fg, fontSize = 12.sp, lineHeight = 15.sp, overflow = TextOverflow.Clip)
                            e.location?.let { Text(it, color = p.muted, fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                        }
                    }
                }
                if (day == today) {
                    val mins = (System.currentTimeMillis() - day) / 60_000f
                    Box(Modifier.offset(x = labelW, y = hourH * (mins / 60f)).fillMaxWidth().height(2.dp).background(p.accent))
                }
            }
        }
    }
}
