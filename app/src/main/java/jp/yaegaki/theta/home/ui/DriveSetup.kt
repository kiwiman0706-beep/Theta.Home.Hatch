package jp.yaegaki.theta.home.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import jp.yaegaki.theta.home.HomeModel
import jp.yaegaki.theta.home.drive.GDrive

/**
 * Google ドライブにつなぐ。初回だけ、自分の Google Cloud プロジェクトで Drive API を有効にし、
 * このアプリを Android の OAuth クライアントとして登録する（パッケージ名と署名の SHA-1 をここに出す）。
 */
@Composable
fun DriveDialog(model: HomeModel, onDismiss: () -> Unit) {
    val p = LocalPalette.current
    val actions = LocalActions.current
    val ctx = actions.activity
    val sha1 = remember { GDrive.signingSha1(ctx) }
    fun copy(label: String, text: String) {
        ctx.getSystemService(ClipboardManager::class.java)?.setPrimaryClip(ClipData.newPlainText(label, text))
        actions.toast("$label をコピーしました")
    }
    fun web(url: String) = actions.start(Intent(Intent.ACTION_VIEW, Uri.parse(url)))

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("GOOGLE DRIVE") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Readout("STATE", if (model.driveOn) "接続中 ${model.driveEmail}".trim() else "未接続", accent = !model.driveOn)
                if (!model.driveOn) {
                    Text("初回だけ、Google Cloud でこのアプリを登録します（PC のブラウザでも可。無料）。", color = p.muted, fontSize = 12.sp, lineHeight = 18.sp)
                    Step(1, "プロジェクトを作る", "名前は自由（例 theta-home）") { web("https://console.cloud.google.com/projectcreate") }
                    Step(2, "Drive API を有効にする", "「有効にする」を押す") { web("https://console.cloud.google.com/apis/library/drive.googleapis.com") }
                    Step(3, "Google Auth Platform を設定", "アプリ名とメール、対象は「外部」。「対象」でテストユーザーに自分のアドレスを足す") { web("https://console.cloud.google.com/auth/overview") }
                    Step(4, "クライアントを作る", "種類は「Android」。下のパッケージ名と SHA-1 を貼る") { web("https://console.cloud.google.com/auth/clients/create") }
                    CopyLine("PACKAGE", ctx.packageName) { copy("パッケージ名", ctx.packageName) }
                    CopyLine("SHA-1", sha1) { copy("SHA-1", sha1) }
                    Text(
                        "5. 下の「Google でログイン」。範囲は読み取りだけ（drive.readonly）。\n" +
                            "「確認されていないアプリ」と出たら、自分で作ったものなので「続行」で進めます。" +
                            "公開ステータスが「テスト」のままだと、1 週間ほどでログインし直しを求められることがあります（「本番環境に push」で避けられる）。",
                        color = p.muted, fontSize = 12.sp, lineHeight = 18.sp,
                    )
                } else {
                    Text("ファイラーの「DRIVE」から辿れます。フォルダを長押し →「〇〇のタブにする」で、フォルダのタブにも割り当てられます。", color = p.muted, fontSize = 12.sp, lineHeight = 18.sp)
                    PillButton("Google 側の許可を見る（取り消し）", { web("https://myaccount.google.com/connections") })
                }
            }
        },
        confirmButton = {
            if (model.driveOn) TextButton(onClick = { model.openDrive(); onDismiss() }) { Text("ドライブを開く") }
            else TextButton(onClick = { model.connectDrive() }) { Text("Google でログイン") }
        },
        dismissButton = {
            if (model.driveOn) TextButton(onClick = { model.disconnectDrive() }) { Text("切断") }
            else TextButton(onClick = onDismiss) { Text("閉じる") }
        },
    )
}

@Composable
private fun Step(n: Int, title: String, sub: String, onClick: () -> Unit) {
    RowButton(onClick = onClick, label = "$title を開く") {
        Text("%02d".format(n), color = LocalPalette.current.accent, fontSize = 13.sp, fontFamily = Hud.mono)
        TwoLines("$title ↗", sub, Modifier.weight(1f))
    }
}

@Composable
private fun CopyLine(label: String, value: String, onCopy: () -> Unit) {
    val p = LocalPalette.current
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Column(Modifier.weight(1f)) {
            Text(label, color = p.faint, fontSize = 10.sp, fontFamily = Hud.mono, letterSpacing = 1.5.sp)
            Text(value, color = p.fg, fontSize = 12.sp, fontFamily = Hud.mono, lineHeight = 16.sp)
        }
        PillButton("コピー", onCopy)
    }
}
