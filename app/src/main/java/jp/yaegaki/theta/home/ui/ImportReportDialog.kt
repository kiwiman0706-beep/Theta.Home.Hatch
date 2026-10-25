package jp.yaegaki.theta.home.ui

import android.content.ActivityNotFoundException
import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import jp.yaegaki.theta.home.HomeModel
import jp.yaegaki.theta.home.data.Apps
import jp.yaegaki.theta.home.data.Backup
import jp.yaegaki.theta.home.data.ImportReport
import jp.yaegaki.theta.home.data.TabKind

/**
 * 設定を読み込んだ後の案内。入っていないアプリは Play ストア（Hatch シリーズは Hatch のドロワー）へ。
 * 入れて戻ってくると一覧から消える。ウィジェット・フォルダの許可・NAS のパスワードは移せないので、やり直しを案内する。
 */
@Composable
fun ImportReportDialog(model: HomeModel, report: ImportReport, onDismiss: () -> Unit) {
    val actions = LocalActions.current
    val ctx = actions.activity
    // アプリが入るたびに model.apps が変わるので、そのたびに入っていないものを数え直す
    val missing = remember(model.apps, report) { report.missing.filter { !Apps.isInstalled(ctx, it.packageName) } }

    fun openStore(pkg: String) {
        try {
            ctx.startActivity(Intent(Intent.ACTION_VIEW, Backup.playStoreUri(pkg)))
        } catch (_: ActivityNotFoundException) {
            actions.start(Intent(Intent.ACTION_VIEW, Backup.playStoreWebUri(pkg)))
        }
    }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        val p = LocalPalette.current
        Box(Modifier.fillMaxSize().background(p.bg).windowInsetsPadding(WindowInsets.safeDrawing)) {
            Column(Modifier.fillMaxSize().padding(horizontal = 20.dp, vertical = 16.dp)) {
                Stripes(Modifier.fillMaxWidth().height(5.dp))
                Row(Modifier.padding(top = 12.dp), verticalAlignment = Alignment.Top) {
                    Column(Modifier.weight(1f)) {
                        Text("[ IMPORT ] // REPORT", color = p.accent, fontSize = 11.sp, letterSpacing = 2.sp, fontFamily = Hud.mono)
                        Text("設定を読み込みました", color = p.fg, fontSize = 28.sp, fontFamily = Hud.display)
                    }
                    GlyphButton(Glyph.CLOSE, "閉じる", onDismiss)
                }
                Ticks(Modifier.fillMaxWidth().height(6.dp))
                Row(Modifier.padding(vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                    Readout("MISSING APPS", "%03d".format(missing.size), accent = missing.isNotEmpty())
                    Readout("WIDGETS", "%02d".format(report.widgets.size))
                    Readout("FOLDERS", "%02d".format(report.folders.size))
                }
                DrawerList {
                    if (missing.isNotEmpty()) {
                        item { SectionTitle("入っていないアプリ（入れて戻ると消えます）", Modifier.padding(vertical = 6.dp)) }
                        itemsIndexed(missing, key = { _, it -> it.packageName }) { i, m ->
                            RowButton(zebra = i, onClick = {
                                if (m.hatch) {
                                    onDismiss(); model.tabWith("hatch")?.let { model.toggle(it.id) }
                                } else openStore(m.packageName)
                            }) {
                                TwoLines(m.label, m.packageName, Modifier.weight(1f))
                                Text(if (m.hatch) "HATCH ›" else "PLAY ›", color = p.accent, fontSize = 13.sp, fontFamily = Hud.mono)
                            }
                        }
                    } else {
                        item { Note("前のスマホにあったアプリは、すべて入っています。") }
                    }
                    if (report.folders.isNotEmpty()) {
                        item {
                            SectionTitle("フォルダを選び直す", Modifier.padding(top = 16.dp, bottom = 6.dp))
                            Note("フォルダの許可は端末ごとなので、次のタブはフォルダを選び直してください：" + report.folders.joinToString("、"))
                        }
                    }
                    if (report.widgets.isNotEmpty()) {
                        item {
                            SectionTitle("ウィジェットを足し直す", Modifier.padding(top = 16.dp, bottom = 6.dp))
                            Note("ウィジェットは端末ごとの割り当てなので移せません。前のスマホにあったもの：" + report.widgets.joinToString("、") + "。端のタブの下の「＋」から足してください。")
                        }
                    }
                    if (report.nasNeedsPassword) {
                        item {
                            SectionTitle("NAS のパスワード", Modifier.padding(top = 16.dp, bottom = 6.dp))
                            Note("接続先とアカウントは移しました。パスワードは書き出していないので、設定の「NAS の接続先」で入れてください。")
                        }
                    }
                }
            }
        }
    }
}
