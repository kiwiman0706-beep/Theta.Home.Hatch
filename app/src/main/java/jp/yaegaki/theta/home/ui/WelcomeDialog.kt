package jp.yaegaki.theta.home.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.dp
import jp.yaegaki.theta.home.HomeModel

/**
 * 初めて入れたときの案内。1. 前の端末・バックアップから設定を引き継ぐか（設定の書き出しで作ったファイル）、
 * 2. 引き継がないなら、アプリをカテゴリーに仕分けするか。どちらも設定からいつでもできる。
 */
@Composable
fun WelcomeDialog(model: HomeModel) {
    if (!model.welcome) return
    val actions = LocalActions.current
    var step by remember { mutableIntStateOf(0) }
    when (step) {
        0 -> AlertDialog(
            onDismissRequest = {},
            title = { Text("Theta Home Hatch へようこそ") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("前の端末やバックアップから、設定を引き継ぎますか？")
                    Note("前に「設定を書き出す」で作ったファイル（タブ・カテゴリー・栞・ピン留めなど）を読み込みます。ウィジェットはこの端末で置き直します。")
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    actions.openDocument(arrayOf("application/json", "text/plain", "application/octet-stream")) { uri ->
                        model.importSettings(uri)
                        model.finishWelcome()
                    }
                    step = 2
                }) { Text("設定を読み込む") }
            },
            dismissButton = { TextButton(onClick = { step = 1 }) { Text("新しく始める") } },
        )
        1 -> AlertDialog(
            onDismissRequest = {},
            title = { Text("アプリを仕分けしますか？") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("入っているアプリを「仕事」「連絡」「写真」などのカテゴリーに分けて、端のタブにできます。候補を出すので、確かめて決めるだけです。")
                    Note("あとからでも、設定の「アプリのカテゴリー」から仕分けできます。")
                }
            },
            confirmButton = { TextButton(onClick = { step = 3 }) { Text("仕分けする") } },
            dismissButton = { TextButton(onClick = { model.finishWelcome() }) { Text("あとで") } },
        )
        // ファイルを選んでいる間（選ばずに戻ったら、次へ進める）
        2 -> AlertDialog(
            onDismissRequest = {},
            title = { Text("設定の読み込み") },
            text = { Text("ファイルを選ぶと読み込みます。選ばずに戻ったときは、新しく始められます。") },
            confirmButton = { TextButton(onClick = { step = 1 }) { Text("新しく始める") } },
            dismissButton = { TextButton(onClick = { step = 0 }) { Text("戻る") } },
        )
        else -> SortGuide(model) { model.finishWelcome() }
    }
}
