package jp.yaegaki.theta.home

import android.content.pm.LauncherApps
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import jp.yaegaki.theta.home.data.PinnedShortcut
import jp.yaegaki.theta.home.data.Shortcuts
import jp.yaegaki.theta.home.data.Store
import jp.yaegaki.theta.home.ui.HomeTheme
import jp.yaegaki.theta.home.ui.LocalPalette

/**
 * ほかのアプリの「ホーム画面に追加」（LINE のトークルームなど）を受けて、どのタブに置くかを選ぶ（カテゴリーのタブ・ほかのタブ）。
 * 既定のホームのときだけ Android から呼ばれる。初めはそのアプリのカテゴリーを選んでおく。
 */
class PinShortcutActivity : ComponentActivity() {
    @OptIn(ExperimentalLayoutApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val req = runCatching { getSystemService(LauncherApps::class.java).getPinItemRequest(intent) }.getOrNull()
        val info = req?.takeIf { it.requestType == LauncherApps.PinItemRequest.REQUEST_TYPE_SHORTCUT }?.shortcutInfo
        if (req == null || info == null) {
            finish()
            return
        }
        val store = (application as HomeApp).store
        // 置き場所：カテゴリーのタブ（カテゴリーの id）と、ほかのタブ（"tab:<id>"）
        val cats = listOf(HomeModel.DOCK to "ピン留め（端の下）", HomeModel.RECONNECT to "再接続ボタン（ルーチンなど）") + store.categories().map { it.id to it.name } +
            store.tabs().filter { it.kind != jp.yaegaki.theta.home.data.TabKind.CATEGORY && it.kind != jp.yaegaki.theta.home.data.TabKind.APPS }.map { "tab:${it.id}" to it.label }
        val appCat = store.appCategories().entries.firstOrNull { it.key.startsWith(info.`package` + "/") }?.value
        val icon = runCatching {
            getSystemService(LauncherApps::class.java).getShortcutIconDrawable(info, resources.displayMetrics.densityDpi)?.let { d ->
                android.graphics.Bitmap.createBitmap(144, 144, android.graphics.Bitmap.Config.ARGB_8888).also { b ->
                    d.setBounds(0, 0, 144, 144); d.draw(android.graphics.Canvas(b))
                }
            }
        }.getOrNull()
        setContent {
            HomeTheme(store.themeMode) {
                val p = LocalPalette.current
                var cat by remember { mutableStateOf(appCat ?: cats.getOrNull(1)?.first ?: cats.first().first) }
                AlertDialog(
                    onDismissRequest = { finish() },
                    title = { Text("タブに置く") },
                    text = {
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                icon?.let { Image(it.asImageBitmap(), null, Modifier.size(40.dp)) }
                                Text(Shortcuts.label(info), color = p.fg, fontSize = 16.sp)
                            }
                            Text(if (cats.isEmpty()) "カテゴリーがまだ無いので、アプリの一覧（未分類）に置きます。" else "どのタブに置きますか", color = p.muted, fontSize = 13.sp)
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                cats.forEach { (id, name) ->
                                    val on = id == cat
                                    Text(name, color = if (on) p.onAccent else p.fg, fontSize = 14.sp,
                                        modifier = Modifier.background(if (on) p.accent else Color.Transparent).border(1.dp, if (on) p.accent else p.line)
                                            .clickable { cat = id }.padding(horizontal = 12.dp, vertical = 8.dp))
                                }
                            }
                        }
                    },
                    confirmButton = {
                        TextButton(onClick = {
                            if (runCatching { req.accept() }.getOrDefault(false)) {
                                val sc = PinnedShortcut(Store.newId(), info.`package`, info.id, Shortcuts.label(info), cat)
                                Shortcuts.saveIcon(this@PinShortcutActivity, info, sc.id)
                                store.saveShortcuts(store.shortcuts() + sc)
                                // 端の下に置くときは並びにも（8 つまで）
                                if (cat == HomeModel.DOCK) store.favoriteApps = (store.favoriteApps + ("sc:" + sc.id)).take(HomeModel.DOCK_MAX)
                                (application as HomeApp).toast("「${sc.label}」を置きました")
                            }
                            finish()
                        }) { Text("置く") }
                    },
                    dismissButton = { TextButton(onClick = { finish() }) { Text("やめる") } },
                )
            }
        }
    }
}
