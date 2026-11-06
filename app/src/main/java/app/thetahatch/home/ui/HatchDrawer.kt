package app.thetahatch.home.ui

import android.app.Activity
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.thetahatch.home.HomeModel
import app.thetahatch.home.hub.Catalog
import app.thetahatch.home.hub.CatalogApp
import app.thetahatch.home.hub.HatchStatus
import app.thetahatch.home.hub.Migration
import app.thetahatch.home.hub.MigrationStep
import app.thetahatch.home.hub.UpdateHub

/** Theta Hatch シリーズの起動・入手・更新・移行（更新のハブ：spec §6.1）。 */
@Composable
fun HatchDrawer(model: HomeModel) {
    val actions = LocalActions.current
    val ctx = actions.activity
    val status by UpdateHub.status.collectAsState()
    val apps by Catalog.apps.collectAsState()
    @Suppress("UNUSED_VARIABLE") val tick = model.migrationTick
    val v = UpdateHub.versionOf(ctx)
    val shown = apps.filter { it.status == null }
    val migrating = shown.filter { it.android && it.needsMigration(v) }.map { it to model.migrationStep(it) }.filter { it.second != MigrationStep.DONE }
    val newer = shown.filter { UpdateHub.hasNewer(it, status[it.id] ?: HatchStatus()) && !it.needsMigration(v) }
    var notes by remember { mutableStateOf<Pair<CatalogApp, String>?>(null) }

    fun needPermission(): Boolean {
        UpdateHub.permissionIntent(ctx)?.let {
            actions.toast("「この提供元のアプリを許可」をオンにしてから、もう一度押してください")
            actions.start(it)
            return true
        }
        return false
    }

    DrawerList {
        if (migrating.isNotEmpty()) {
            item { SectionTitle("移行が必要（新しい ID へ） ${migrating.size}") }
            itemsIndexed(migrating, key = { _, it -> "m-" + it.first.id }) { zi, (a, step) -> MigrationRow(model, a, step, zi, ::needPermission) }
            item { Gap(12.dp) }
        }
        if (newer.size > 1) {
            item { FlowRow2 { PillButton("すべて更新（${newer.size}）", { if (!needPermission()) model.updateAllHatch() }, primary = true) } }
        }
        itemsIndexed(shown, key = { _, it -> it.id }) { zi, a ->
            HatchRow(model, a, status[a.id] ?: HatchStatus(), zi, focus = model.hatchFocus == a.id, needPermission = ::needPermission) { notes = a to it }
        }
        item {
            Note("GitHub Releases の公開版を確かめ、取得 → SHA-256・署名（シリーズ共通の鍵）・パッケージ・版を照合してから入れます。初回は「この提供元のアプリを許可」を求められます。このホームが入れたアプリは、次からは確認なしで更新できることがあります。")
        }
    }

    notes?.let { (a, body) ->
        AlertDialog(
            onDismissRequest = { notes = null },
            title = { Text("${a.name} の変更点") },
            text = { Box(Modifier.verticalScroll(rememberScrollState())) { Text(body.ifBlank { "（変更点の記載がありません）" }, fontSize = 13.sp) } },
            confirmButton = { TextButton(onClick = { notes = null }) { Text("閉じる") } },
        )
    }
}

@Composable
private fun AppBadge(a: CatalogApp) {
    val p = LocalPalette.current
    Box(Modifier.size(40.dp).border(1.dp, p.line, RoundedCornerShape(0.dp)), contentAlignment = Alignment.Center) {
        Text(a.short, color = p.muted, fontSize = 13.sp)
    }
}

@Composable
private fun HatchRow(model: HomeModel, a: CatalogApp, s: HatchStatus, zebra: Int, focus: Boolean, needPermission: () -> Boolean, showNotes: (String) -> Unit) {
    val actions = LocalActions.current
    val ctx = actions.activity
    val installedPkg = a.installedPackage(UpdateHub.versionOf(ctx))
    val newer = UpdateHub.hasNewer(a, s)
    val sub = when {
        s.progress != null -> "取得中 ${(s.progress * 100).toInt()}%"
        s.installing -> "入れています…"
        s.error != null -> "失敗：${s.error}"
        s.result != null -> s.result
        s.installed != null -> "導入済み ${s.installed}" + if (newer) " → ${s.latest!!.version} あり" + (if (s.ready != null) "（取得済み）" else "") else ""
        !a.android -> a.summary
        s.checking -> "最新版を確かめています…"
        s.latest != null -> "未導入 · 最新 ${s.latest.version}"
        a.repo == null -> "未導入 · 配布先が未登録"
        else -> "未導入"
    }
    fun releases() = a.releasesUrl?.let { actions.start(Intent(Intent.ACTION_VIEW, Uri.parse("$it/latest"))) }

    RowButton(zebra = if (focus) 1 else zebra, onClick = {
        val launch = installedPkg?.let { ctx.packageManager.getLaunchIntentForPackage(it) }
        when {
            launch != null -> actions.start(launch)
            else -> releases()
        }
    }) {
        AppBadge(a)
        TwoLines((if (focus) "▶ " else "") + a.name, sub, Modifier.weight(1f))
        Row {
            val busy = s.progress != null || s.installing
            when {
                busy -> Unit
                newer -> {
                    s.latest?.body?.takeIf { it.isNotBlank() }?.let { b -> PillButton("変更点", { showNotes(b) }) }
                    PillButton("更新", { if (!needPermission()) model.updateHatch(a) }, primary = true)
                }
                s.error != null && a.releasesUrl != null -> PillButton("Releases", { releases() })
                installedPkg != null -> PillButton("開く", { ctx.packageManager.getLaunchIntentForPackage(installedPkg)?.let(actions.start) })
                a.android && s.latest != null -> PillButton("入れる", { if (!needPermission()) model.updateHatch(a) }, primary = true)
                a.releasesUrl != null -> PillButton("配布先", { releases() })
            }
        }
    }
}

/** 移行の 1 アプリ分：今の段階のボタンだけを出す（docs/MIGRATION.md §3）。 */
@Composable
private fun MigrationRow(model: HomeModel, a: CatalogApp, step: MigrationStep, zebra: Int, needPermission: () -> Boolean) {
    val actions = LocalActions.current
    val ctx = actions.activity
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null && Migration.keep(ctx, a, uri)) model.migrationTick++ else if (uri != null) actions.toast("ファイルを読めませんでした")
    }
    val export = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { r ->
        val uri = r.data?.data
        when {
            r.resultCode == Activity.RESULT_OK && uri != null && Migration.keep(ctx, a, uri) -> { model.migrationTick++; actions.toast("書き出しました") }
            r.resultCode == Activity.RESULT_OK -> { actions.toast("書き出したファイルを選んでください"); pick.launch(arrayOf("*/*")) }
            else -> actions.toast("書き出しが終わりませんでした")
        }
    }
    val import = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { r ->
        if (r.resultCode == Activity.RESULT_OK) { model.markMigrated(a.id); actions.toast("読み込みました。古いアプリを削除できます") }
        else actions.toast("読み込めませんでした " + (r.data?.getStringExtra("error") ?: ""))
    }
    val isSelf = a.targetPackage == ctx.packageName
    val (n, text) = when (step) {
        MigrationStep.BRIDGE -> 1 to "古いアプリを橋渡し版に更新します"
        MigrationStep.EXPORT -> 2 to "古いアプリでデータを書き出します"
        MigrationStep.INSTALL_NEW -> (if (a.bridge) 3 else 1) to "新しい版を入れます（別のアプリとして入ります）"
        MigrationStep.IMPORT -> 4 to "新しい版でデータを読み込みます"
        MigrationStep.DELETE_OLD -> (if (a.bridge) 5 else 2) to if (isSelf) "古い Home を削除します。設定は、古い Home の「設定 → 書き出し」で保存し、ここの「設定 → 読み込み」で戻せます" else "古いアプリを削除します"
        MigrationStep.DONE -> 0 to ""
    }
    RowButton(zebra = zebra, onClick = {}) {
        AppBadge(a)
        TwoLines(a.name, "${n}/${if (a.bridge) 5 else 2}　$text", Modifier.weight(1f))
        Row {
            when (step) {
                MigrationStep.BRIDGE -> PillButton("橋渡し版にする", { if (!needPermission()) model.installBridge(a) }, primary = true)
                MigrationStep.EXPORT -> {
                    PillButton("書き出す", { Migration.exportIntent(ctx, a)?.let { export.launch(it) } ?: pick.launch(arrayOf("*/*")) }, primary = true)
                    PillButton("ファイルを選ぶ", { pick.launch(arrayOf("*/*")) })
                }
                MigrationStep.INSTALL_NEW -> PillButton("新しい版を入れる", { if (!needPermission()) model.installNew(a) }, primary = true)
                MigrationStep.IMPORT -> {
                    val i = Migration.importIntent(ctx, a)
                    if (i != null) PillButton("読み込む", { import.launch(i) }, primary = true)
                    else PillButton("済みにする", { model.markMigrated(a.id) })
                }
                MigrationStep.DELETE_OLD -> PillButton("古いアプリを削除", { actions.start(Migration.deleteIntent(a)) }, primary = true)
                MigrationStep.DONE -> Unit
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
