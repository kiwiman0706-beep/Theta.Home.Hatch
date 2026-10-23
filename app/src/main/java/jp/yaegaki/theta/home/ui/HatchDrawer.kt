package jp.yaegaki.theta.home.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import jp.yaegaki.theta.home.HomeModel
import jp.yaegaki.theta.home.HatchStatus
import jp.yaegaki.theta.home.hatch.HatchApp
import jp.yaegaki.theta.home.hatch.HatchCatalog
import jp.yaegaki.theta.home.hatch.HatchInstaller

/** Theta Hatch シリーズの起動・入手・更新。 */
@Composable
fun HatchDrawer(model: HomeModel) {
    DrawerList {
        items(HatchCatalog.apps, key = { it.id }) { a -> HatchRow(model, a, model.hatch[a.id] ?: HatchStatus()) }
        item {
            Note("GitHub Releases の最新版を確認し、APK を取得 → SHA-256 を照合 → Android のインストール確認画面へ進みます。初回は「この提供元のアプリを許可」を求められます。")
        }
    }
}

@Composable
private fun HatchRow(model: HomeModel, a: HatchApp, s: HatchStatus) {
    val p = LocalPalette.current
    val actions = LocalActions.current
    val ctx = actions.activity
    val newer = s.installed != null && s.latest != null && isNewer(s.latest.version, s.installed)
    val sub = when {
        s.progress != null -> "取得中 ${(s.progress * 100).toInt()}%"
        s.error != null -> "失敗：${s.error}"
        s.installed != null -> "導入済み ${s.installed}" + if (newer) " → ${s.latest!!.version} あり" else ""
        a.packageName == null -> a.summary
        s.checking -> "最新版を確認中…"
        s.latest != null -> "未導入 · 最新 ${s.latest.version}"
        a.repo == null -> "未導入 · 配布先が未登録"
        else -> "未導入"
    }

    fun install() {
        HatchInstaller.permissionIntent(ctx)?.let {
            actions.toast("「この提供元のアプリを許可」をオンにしてから、もう一度押してください")
            actions.start(it)
            return
        }
        model.downloadHatch(a) { file -> runCatching { HatchInstaller.install(ctx, file) }.onFailure { actions.toast("インストールを始められませんでした") } }
    }

    RowButton(onClick = {
        val launch = a.packageName?.let { ctx.packageManager.getLaunchIntentForPackage(it) }
        when {
            launch != null -> actions.start(launch)
            a.releasesUrl != null -> actions.start(Intent(Intent.ACTION_VIEW, Uri.parse(a.releasesUrl)))
        }
    }) {
        Box(Modifier.size(40.dp).border(1.dp, p.line, RoundedCornerShape(0.dp)), contentAlignment = Alignment.Center) {
            Text(a.short, color = p.muted, fontSize = 13.sp)
        }
        TwoLines(a.name, sub, Modifier.weight(1f))
        Row {
            when {
                s.progress != null -> Unit
                newer -> PillButton("更新", ::install, primary = true)
                s.installed != null -> PillButton("開く", {
                    ctx.packageManager.getLaunchIntentForPackage(a.packageName!!)?.let(actions.start)
                })
                a.packageName != null && s.latest != null -> PillButton("入れる", ::install, primary = true)
                a.releasesUrl != null -> PillButton("配布先", { actions.start(Intent(Intent.ACTION_VIEW, Uri.parse(a.releasesUrl))) })
            }
        }
    }
}

/** "1.10.0" と "1.9.2" のような版を数字ごとに比べる。"-alpha" などの後ろは無視する。 */
fun isNewer(latest: String, installed: String): Boolean {
    fun parts(v: String) = v.substringBefore('-').substringBefore(' ').split('.').map { it.toIntOrNull() ?: 0 }
    val a = parts(latest)
    val b = parts(installed)
    for (i in 0 until maxOf(a.size, b.size)) {
        val x = a.getOrElse(i) { 0 }
        val y = b.getOrElse(i) { 0 }
        if (x != y) return x > y
    }
    return false
}
