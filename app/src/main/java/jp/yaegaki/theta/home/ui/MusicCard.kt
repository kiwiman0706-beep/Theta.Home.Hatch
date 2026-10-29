package jp.yaegaki.theta.home.ui

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import jp.yaegaki.theta.home.HomeModel
import jp.yaegaki.theta.home.notify.HatchNotificationListener
import jp.yaegaki.theta.home.notify.MediaWatch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/**
 * 再生中の音楽：ジャケット・曲名・アーティスト・アルバム・進み具合と、前・再生／一時停止・次・アプリを開く。
 * ジャケットを押してもアプリを開く。ホームの区画「音楽」とタブの「MUSIC」の中身。
 */
@Composable
fun MusicCard(model: HomeModel) {
    val p = LocalPalette.current
    val actions = LocalActions.current
    val ctx = actions.activity
    val now by MediaWatch.now.collectAsState()
    val musicApp = model.apps.firstOrNull { it.packageName == MediaWatch.THETA_MUSIC }
    val n = now
    if (n == null) {
        val enabled = HatchNotificationListener.isEnabled(ctx)
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                if (enabled) "再生中の音楽はありません" else "通知へのアクセスを許可すると、再生中の曲（どの音楽アプリでも）がここに出て、操作できます。",
                color = p.muted, fontSize = 13.sp, lineHeight = 19.sp,
            )
            FlowRow2 {
                if (!enabled) PillButton("通知へのアクセスを許可", { actions.start(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) }, primary = true)
                musicApp?.let { a -> PillButton("${a.label} を開く", { actions.start(jp.yaegaki.theta.home.data.Apps.launchIntent(a)) }) }
            }
            if (!enabled) ListenerHelp()
        }
        return
    }
    val app = model.apps.firstOrNull { it.packageName == n.packageName }
    fun openApp() {
        val i = app?.let { jp.yaegaki.theta.home.data.Apps.launchIntent(it) } ?: ctx.packageManager.getLaunchIntentForPackage(n.packageName)
        i?.let(actions.start)
    }
    // 再生中は 1 秒ごとに進み具合を描き直す
    var tick by remember { mutableLongStateOf(android.os.SystemClock.elapsedRealtime()) }
    LaunchedEffect(n.playing, n.updatedAt) {
        while (n.playing) {
            tick = android.os.SystemClock.elapsedRealtime()
            delay(1000)
        }
    }
    // ジャケットが URI でしか来ないとき（端末の中・ネットのもの）は読み込む
    val loaded by produceState<android.graphics.Bitmap?>(null, n.artUri) {
        val uri = n.artUri ?: return@produceState
        value = withContext(Dispatchers.IO) {
            runCatching {
                if (uri.startsWith("http")) java.net.URL(uri).openStream().use { android.graphics.BitmapFactory.decodeStream(it) }
                else ctx.contentResolver.openInputStream(android.net.Uri.parse(uri))?.use { android.graphics.BitmapFactory.decodeStream(it) }
            }.getOrNull()
        }
    }
    val art = n.art ?: loaded
    val shape = RoundedCornerShape(if (p.pop) p.radius else 0.dp)
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(76.dp).clip(shape).background(p.raised).border(lineWidth, p.line, shape)
                    .clickable(onClickLabel = "アプリを開く") { openApp() }.semantics { contentDescription = "ジャケット（押すとアプリを開く）" },
                contentAlignment = Alignment.Center,
            ) {
                if (art != null) Image(art.asImageBitmap(), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                else GlyphIcon(Glyph.MUSIC, p.faint, 32.dp)
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(n.title.ifBlank { "（曲名なし）" }, color = p.fg, fontSize = 16.sp, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                if (n.artist.isNotBlank()) Text(n.artist, color = p.muted, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (n.album.isNotBlank()) Text(n.album, color = p.faint, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(app?.label ?: n.packageName, color = p.faint, fontSize = 10.sp, fontFamily = Hud.mono, maxLines = 1)
            }
        }
        if (n.duration > 0) {
            val pos = n.positionNow(tick)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(clock(pos), color = p.faint, fontSize = 11.sp, fontFamily = Hud.mono)
                Box(Modifier.weight(1f).height(4.dp).background(p.raised)) {
                    Box(Modifier.fillMaxHeight().fillMaxWidth((pos.toFloat() / n.duration).coerceIn(0f, 1f)).background(p.accent))
                }
                Text(clock(n.duration), color = p.faint, fontSize = 11.sp, fontFamily = Hud.mono)
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Control("◀◀", "前の曲") { MediaWatch.previous() }
            Control(if (n.playing) "❚❚" else "▶", if (n.playing) "一時停止" else "再生", big = true) { MediaWatch.playPause() }
            Control("▶▶", "次の曲") { MediaWatch.next() }
            Spacer(Modifier.weight(1f))
            PillButton("開く", { openApp() })
        }
    }
}

@Composable
private fun Control(label: String, description: String, big: Boolean = false, onClick: () -> Unit) {
    val p = LocalPalette.current
    Box(
        Modifier.size(if (big) 52.dp else 44.dp).clip(RoundedCornerShape(if (p.pop) 26.dp else 0.dp))
            .background(if (big) p.accent else p.raised)
            .clickable(role = Role.Button, onClickLabel = description, onClick = onClick)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) { Text(label, color = if (big) p.onAccent else p.fg, fontSize = if (big) 18.sp else 14.sp, fontWeight = FontWeight.Bold) }
    Spacer(Modifier.size(8.dp))
}

private fun clock(ms: Long): String {
    val s = ms / 1000
    return "%d:%02d".format(s / 60, s % 60)
}
