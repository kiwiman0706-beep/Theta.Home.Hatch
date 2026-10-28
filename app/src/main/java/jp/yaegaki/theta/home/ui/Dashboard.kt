package jp.yaegaki.theta.home.ui

import android.Manifest
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.BatteryManager
import android.os.Environment
import android.os.StatFs
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import jp.yaegaki.theta.home.HomeModel
import jp.yaegaki.theta.home.data.Files
import jp.yaegaki.theta.home.data.Weather
import jp.yaegaki.theta.home.data.WeatherPlace
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

/**
 * 狭い画面（Fold5 のカバー・Titan など）の背景。ファイラーの代わりに、今日の予定・天気・つながり・端末・ファイルへの入口を並べる。
 * ファイラーはフォルダのタブか「FILES」の場所を押すと開く。
 */
@Composable
fun Dashboard(model: HomeModel, openNasSettings: () -> Unit) {
    LaunchedEffect(model.weatherPlace) { model.refreshWeather() }
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 14.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        ClockBlock(big = false)
        Ticks(Modifier.fillMaxWidth().height(6.dp))
        SearchRow(model)
        if (model.query.isNotBlank()) SearchResults(model)
        else DashboardPanels(model, openNasSettings)
    }
}

@Composable
private fun DashboardPanels(model: HomeModel, openNasSettings: () -> Unit) {
    Panel(model, "TODAY", "予定", folded = {
        val next = model.nextEvent
        if (next != null) Text(dayLabel(next.localBegin) + "  " + next.title, color = LocalPalette.current.muted, fontSize = 13.sp, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
    }) { TodayEvents(model, max = 12) }
    NoticePanel(model)
    Panel(model, "WEATHER", "天気", folded = {
        val f = model.forecast
        val warn = model.alerts?.list.orEmpty().joinToString("・") { it.name }
        if (f != null) Text("${f.temp.roundToInt()}°  ${Weather.label(f.code)}" + (if (warn.isNotEmpty()) "  ⚠ $warn" else ""),
            color = if (warn.isNotEmpty()) LocalPalette.current.accent else LocalPalette.current.muted, fontSize = 13.sp, maxLines = 1,
            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
    }) { WeatherPanel(model) }
    StatusStrip(model, openNasSettings)
    Panel(model, "FILES", "ファイル") { FilesPanel(model) }
    Panel(model, "DEVICE", "端末") { DevicePanel() }
    Gap(8.dp)
}

/** 条件に合う通知の区画。条件が無い・合う通知が無いときは出さない（通知へのアクセスが無ければ許可を促す）。 */
@Composable
private fun NoticePanel(model: HomeModel) {
    val picked = filteredNotices(model)
    val enabled = jp.yaegaki.theta.home.notify.HatchNotificationListener.isEnabled(LocalActions.current.activity)
    if (model.noticeRules.isEmpty() || (picked.isEmpty() && enabled)) return
    Panel(model, "NOTICE", "通知", folded = { FilteredNoticesFolded(picked) }) { FilteredNotices(model, picked, max = 6) }
}

/**
 * 計器の区画：見出し（英字＋日本語）と、四隅のかぎ括弧。POP では角丸の枠に、色の付いた見出しの帯。
 * 見出しを押すとたたむ（たたんだら folded の 1 行だけ）。たたんだのは覚えておく。
 */
@Composable
private fun Panel(model: HomeModel, code: String, title: String, folded: (@Composable () -> Unit)? = null, body: @Composable () -> Unit) {
    val p = LocalPalette.current
    val closed = model.isCollapsed(code)
    val content: @Composable () -> Unit = { if (closed) folded?.invoke() else body() }
    val toggle = Modifier.clickable(onClickLabel = if (closed) "広げる" else "たたむ") { model.toggleCollapsed(code) }
    if (p.pop) {
        val shape = androidx.compose.foundation.shape.RoundedCornerShape(10.dp)
        val band = when (code) {
            "TODAY" -> p.accent
            "WEATHER" -> p.accent3
            "FILES" -> p.accent2
            else -> p.raised
        }
        Column(Modifier.fillMaxWidth().popShadow(p, shape).clip(shape).background(p.panel).border(lineWidth, p.line, shape)) {
            Row(
                Modifier.fillMaxWidth().background(band).then(toggle).padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(code, color = if (band == p.accent) p.onAccent else p.fg, fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                Text(title, color = if (band == p.accent) p.onAccent else p.fg, fontSize = 12.sp, modifier = Modifier.weight(1f))
                Text(if (closed) "▸" else "▾", color = if (band == p.accent) p.onAccent else p.fg, fontSize = 12.sp)
            }
            if (!closed || folded != null) {
                Box(Modifier.fillMaxWidth().height(lineWidth).background(p.line))
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) { content() }
            }
        }
        return
    }
    Column(Modifier.fillMaxWidth().border(1.dp, p.line).corners(p.accent, 8.dp).padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(toggle, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("[ $code ]", color = p.accent, fontSize = 11.sp, fontFamily = Hud.mono, letterSpacing = 2.sp)
            Text(title, color = p.muted, fontSize = 11.sp)
            Stripes(Modifier.weight(1f).height(5.dp), color = p.line)
            Text(if (closed) "▸" else "▾", color = p.accent, fontSize = 11.sp, fontFamily = Hud.mono)
        }
        content()
    }
}

// ---- 天気 ----

@Composable
private fun WeatherPanel(model: HomeModel) {
    val p = LocalPalette.current
    val actions = LocalActions.current
    var choosing by remember { mutableStateOf(false) }
    val place = model.weatherPlace
    val f = model.forecast
    if (place == null) {
        Text("天気を出す場所を選んでください（Open-Meteo の予報。API キーは要りません）", color = p.muted, fontSize = 13.sp, lineHeight = 19.sp)
        FlowRow2 {
            PillButton("現在地を使う", {
                actions.requestPermissions(arrayOf(Manifest.permission.ACCESS_COARSE_LOCATION)) {
                    if (model.has(Manifest.permission.ACCESS_COARSE_LOCATION)) model.chooseWeatherPlace(WeatherPlace("現在地", 35.68, 139.76, useLocation = true))
                    else actions.toast("位置の許可が無いので、地名で選んでください")
                }
            }, primary = true)
            PillButton("地名で探す", { choosing = true })
        }
    } else {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            WeatherGlyph(f?.code ?: 3, 52.dp, p.accent, night = f?.isDay == false)
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(f?.let { "${it.temp.roundToInt()}°" } ?: "--°", color = p.fg, fontSize = 40.sp, fontFamily = Hud.display, fontWeight = FontWeight.Light, lineHeight = 44.sp)
                    Text(f?.let { Weather.label(it.code) } ?: "", color = p.fg, fontSize = 14.sp, modifier = Modifier.padding(bottom = 6.dp))
                }
                if (f != null) Text(
                    "体感 ${f.feels.roundToInt()}° · 湿度 ${f.humidity}% · 風 ${"%.0f".format(f.wind)}m/s" +
                        (f.days.firstOrNull()?.let { " · ${it.max.roundToInt()}°/${it.min.roundToInt()}° · 雨 ${it.rain}%" } ?: ""),
                    color = p.muted, fontSize = 11.sp, fontFamily = Hud.mono, lineHeight = 15.sp,
                )
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("◎ " + place.name, color = p.muted, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f).clickable(onClickLabel = "場所を変える") { choosing = true })
            Text(
                when {
                    model.weatherLoading -> "UPDATING…"
                    f != null -> "UPD " + SimpleDateFormat("HH:mm", Locale.US).format(Date(f.fetchedAt))
                    else -> ""
                },
                color = p.faint, fontSize = 10.sp, fontFamily = Hud.mono,
                modifier = Modifier.clickable(onClickLabel = "今すぐ取り直す") { model.refreshWeather(force = true) },
            )
        }
        model.weatherError?.let { Text(it, color = p.accent, fontSize = 12.sp) }
        AlertChips(model)
        if (f != null) {
            // 24 時間：時刻・絵・気温・降水確率
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                f.hours.forEach { h ->
                    Column(Modifier.width(40.dp).background(p.raised).padding(vertical = 6.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(SimpleDateFormat("HH", Locale.US).format(Date(h.time)), color = p.faint, fontSize = 10.sp, fontFamily = Hud.mono)
                        WeatherGlyph(h.code, 20.dp, p.fg)
                        Text("${h.temp.roundToInt()}°", color = p.fg, fontSize = 12.sp, fontFamily = Hud.mono)
                        Text("${h.rain}%", color = if (h.rain >= 50) p.accent else p.muted, fontSize = 10.sp, fontFamily = Hud.mono)
                    }
                }
            }
            // 7 日
            Column {
                f.days.forEachIndexed { i, d ->
                    Row(
                        Modifier.fillMaxWidth().background(if (i % 2 == 1) p.zebra else Color.Transparent).padding(horizontal = 6.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Text(
                            if (i == 0) "TODAY" else SimpleDateFormat("MM/dd EEE", Locale.US).format(Date(d.date)).uppercase(Locale.US),
                            color = if (i == 0) p.accent else p.fg, fontSize = 12.sp, fontFamily = Hud.mono, modifier = Modifier.width(90.dp),
                        )
                        WeatherGlyph(d.code, 18.dp, p.fg)
                        Spacer(Modifier.weight(1f))
                        Text("${d.max.roundToInt()}° / ${d.min.roundToInt()}°", color = p.fg, fontSize = 12.sp, fontFamily = Hud.mono)
                        Text("${d.rain}%", color = if (d.rain >= 50) p.accent else p.muted, fontSize = 11.sp, fontFamily = Hud.mono, modifier = Modifier.width(34.dp))
                    }
                }
            }
        }
    }
    if (choosing) PlaceDialog(model) { choosing = false }
}

/** 天気を出す場所：現在地か、地名で探す。 */
@Composable
private fun PlaceDialog(model: HomeModel, onDismiss: () -> Unit) {
    val p = LocalPalette.current
    val actions = LocalActions.current
    val scope = rememberCoroutineScope()
    var q by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<WeatherPlace>>(emptyList()) }
    var msg by remember { mutableStateOf<String?>(null) }
    fun search() {
        if (q.isBlank()) return
        msg = "探しています…"
        scope.launch {
            val r = runCatching { withContext(Dispatchers.IO) { Weather.search(q) } }
            results = r.getOrDefault(emptyList())
            msg = r.exceptionOrNull()?.let { "探せませんでした（${it.message}）" } ?: if (results.isEmpty()) "見つかりませんでした" else null
        }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("天気の場所") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                PillButton("現在地を使う", {
                    actions.requestPermissions(arrayOf(Manifest.permission.ACCESS_COARSE_LOCATION)) {
                        if (model.has(Manifest.permission.ACCESS_COARSE_LOCATION)) {
                            model.chooseWeatherPlace(WeatherPlace("現在地", model.weatherPlace?.lat ?: 35.68, model.weatherPlace?.lon ?: 139.76, useLocation = true))
                            onDismiss()
                        } else actions.toast("位置の許可がありません")
                    }
                }, primary = true)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Field(q, { q = it }, "地名（例 札幌、横浜市）", Modifier.weight(1f))
                    PillButton("探す", ::search)
                }
                msg?.let { Text(it, color = p.muted, fontSize = 12.sp) }
                LazyColumn(Modifier.heightIn(max = 280.dp)) {
                    items(results) { r ->
                        RowButton(onClick = { model.chooseWeatherPlace(r); onDismiss() }) {
                            TwoLines(r.name, "%.2f, %.2f".format(Locale.US, r.lat, r.lon), Modifier.weight(1f))
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("閉じる") } },
    )
}

/** 天気の絵（線だけ）：晴れ・晴れ時々くもり・くもり・霧・雨・雪・雷。 */
@Composable
fun WeatherGlyph(code: Int, size: Dp, color: Color, night: Boolean = false) {
    val kind = Weather.kind(code)
    Canvas(Modifier.size(size)) {
        val w = this.size.width
        val st = Stroke(width = w * 0.06f, cap = StrokeCap.Round)
        fun sun(c: Offset, r: Float) {
            if (night) {
                drawCircle(color, r, c, style = st)
                drawCircle(color, r * 0.8f, Offset(c.x + r * 0.55f, c.y - r * 0.35f), style = st)
                return
            }
            drawCircle(color, r, c, style = st)
            for (i in 0 until 8) {
                val a = Math.toRadians(i * 45.0)
                val o1 = Offset(c.x + (r * 1.45f * Math.cos(a)).toFloat(), c.y + (r * 1.45f * Math.sin(a)).toFloat())
                val o2 = Offset(c.x + (r * 1.85f * Math.cos(a)).toFloat(), c.y + (r * 1.85f * Math.sin(a)).toFloat())
                drawLine(color, o1, o2, st.width, StrokeCap.Round)
            }
        }
        fun cloud(dy: Float = 0f) {
            drawCircle(color, w * 0.15f, Offset(w * 0.34f, w * 0.52f + dy), style = st)
            drawCircle(color, w * 0.2f, Offset(w * 0.54f, w * 0.44f + dy), style = st)
            drawCircle(color, w * 0.13f, Offset(w * 0.72f, w * 0.55f + dy), style = st)
            drawLine(color, Offset(w * 0.22f, w * 0.67f + dy), Offset(w * 0.82f, w * 0.67f + dy), st.width, StrokeCap.Round)
        }
        when (kind) {
            "clear" -> sun(Offset(w / 2, w / 2), w * 0.2f)
            "partly" -> { sun(Offset(w * 0.32f, w * 0.3f), w * 0.12f); cloud(w * 0.06f) }
            "cloudy" -> cloud()
            "fog" -> for (i in 0 until 3) drawLine(color, Offset(w * 0.15f, w * (0.35f + i * 0.15f)), Offset(w * 0.85f, w * (0.35f + i * 0.15f)), st.width, StrokeCap.Round)
            "rain" -> { cloud(-w * 0.1f); for (i in 0 until 3) drawLine(color, Offset(w * (0.35f + i * 0.15f), w * 0.7f), Offset(w * (0.3f + i * 0.15f), w * 0.86f), st.width, StrokeCap.Round) }
            "snow" -> { cloud(-w * 0.1f); for (i in 0 until 3) drawCircle(color, w * 0.03f, Offset(w * (0.33f + i * 0.17f), w * 0.8f)) }
            "thunder" -> {
                cloud(-w * 0.1f)
                drawLine(color, Offset(w * 0.52f, w * 0.62f), Offset(w * 0.44f, w * 0.78f), st.width, StrokeCap.Round)
                drawLine(color, Offset(w * 0.44f, w * 0.78f), Offset(w * 0.56f, w * 0.78f), st.width, StrokeCap.Round)
                drawLine(color, Offset(w * 0.56f, w * 0.78f), Offset(w * 0.48f, w * 0.94f), st.width, StrokeCap.Round)
            }
        }
    }
}

// ---- ファイル（ファイラーへの入口） ----

@Composable
private fun FilesPanel(model: HomeModel) {
    val p = LocalPalette.current
    val actions = LocalActions.current
    PlaceChips(model)
    val recent = model.recents.take(3)
    if (recent.isEmpty()) Text("フォルダのタブか、上の場所を押すとファイラーが開きます", color = p.muted, fontSize = 12.sp)
    recent.forEach { r ->
        Row(
            Modifier.fillMaxWidth().clickable { openFile(model, actions, jp.yaegaki.theta.home.data.ViewItem(Uri.parse(r.uri), r.name, r.mime)) }.padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Badge(r.name.substringAfterLast('.', "FILE").uppercase(Locale.ROOT).take(4))
            TwoLines(r.name, r.where, Modifier.weight(1f))
        }
    }
}

// ---- 端末：電池・空き容量 ----

private data class DeviceInfo(val battery: Int, val charging: Boolean, val freeGb: Double, val totalGb: Double)

@Composable
private fun DevicePanel() {
    val p = LocalPalette.current
    val ctx = LocalActions.current.activity
    val info by produceState<DeviceInfo?>(null) {
        while (true) {
            value = withContext(Dispatchers.IO) {
                val b = ctx.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
                val level = b?.let { it.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) * 100 / it.getIntExtra(BatteryManager.EXTRA_SCALE, 100).coerceAtLeast(1) } ?: -1
                val status = b?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
                val fs = runCatching { StatFs(Environment.getDataDirectory().path) }.getOrNull()
                DeviceInfo(
                    level,
                    status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL,
                    (fs?.availableBytes ?: 0) / 1e9, (fs?.totalBytes ?: 0) / 1e9,
                )
            }
            delay(60_000)
        }
    }
    val i = info ?: return
    Row(horizontalArrangement = Arrangement.spacedBy(18.dp), verticalAlignment = Alignment.CenterVertically) {
        Readout("BATTERY", if (i.battery >= 0) "${i.battery}%" + if (i.charging) " ⚡" else "" else "--", accent = i.battery in 0..20)
        Readout("STORAGE", "%.0f / %.0f GB".format(i.freeGb, i.totalGb))
        Column(Modifier.weight(1f)) {
            Meter(i.battery / 100f, if (i.battery in 0..20) p.accent else p.fg)
            Gap(4.dp)
            Meter(if (i.totalGb > 0) (1 - i.freeGb / i.totalGb).toFloat() else 0f, p.muted)
        }
    }
}

@Composable
private fun Meter(value: Float, color: Color) {
    val p = LocalPalette.current
    Box(Modifier.fillMaxWidth().height(6.dp).border(1.dp, p.line)) {
        Box(Modifier.fillMaxWidth(value.coerceIn(0f, 1f)).height(6.dp).background(color))
    }
}

/**
 * 気象庁の警報・注意報。特別警報・警報は警告の色の地、注意報は枠だけ。何も出ていなければ「なし」と小さく。
 * 日本の外・取れないときは何も出さない。
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AlertChips(model: HomeModel) {
    val p = LocalPalette.current
    val actions = LocalActions.current
    val a = model.alerts ?: return
    // 色はテーマに依らず、気象庁の色分けに近く：特別警報＝紫、警報＝赤、注意報＝黄（明るい地では濃いめ）
    val dark = p.isDark
    fun colorOf(level: Int) = when (level) {
        3 -> if (dark) Color(0xFFC060F0) else Color(0xFF7B1FA2)
        2 -> if (dark) Color(0xFFFF3B3B) else Color(0xFFC62828)
        else -> if (dark) Color(0xFFF2C200) else Color(0xFF8A6D00)
    }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("JMA", color = p.faint, fontSize = 10.sp, fontFamily = Hud.mono, letterSpacing = 1.sp)
        Text(a.area, color = p.muted, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        if (a.list.isEmpty()) Text("警報・注意報なし", color = p.faint, fontSize = 11.sp)
    }
    if (a.list.isNotEmpty()) FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        a.list.forEach { al ->
            val strong = al.level >= 2
            val warn = colorOf(al.level)
            Text(
                (if (al.level >= 3) "▲ " else "") + al.name,
                color = if (strong) Color.White else warn, fontSize = 12.sp, fontWeight = if (strong) FontWeight.Bold else FontWeight.Normal,
                modifier = Modifier.background(if (strong) warn else Color.Transparent).border(1.dp, warn)
                    // 押すと気象庁の警報・注意報のページ
                    .clickable(onClickLabel = "気象庁のページを開く") { actions.start(android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse("https://www.jma.go.jp/bosai/warning/"))) }
                    .padding(horizontal = 8.dp, vertical = 4.dp),
            )
        }
    }
}
