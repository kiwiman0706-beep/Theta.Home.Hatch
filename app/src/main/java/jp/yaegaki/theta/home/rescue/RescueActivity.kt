package jp.yaegaki.theta.home.rescue

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import jp.yaegaki.theta.home.MainActivity
import jp.yaegaki.theta.home.hatch.SelfUpdate

/**
 * レスキュー画面。ホームが落ち続ける・固まったとき、クイック設定のタイル、設定から開く。
 * 本体とは別のプロセスで、Compose も使わず Android の素の部品だけで描く（本体が壊れていても開けるように）。
 */
class RescueActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val d = resources.displayMetrics.density
        fun px(v: Int) = (v * d).toInt()
        val orange = Color.rgb(0xFF, 0x6A, 0x1A)
        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(px(24), px(48), px(24), px(32))
            setBackgroundColor(Color.rgb(0x0D, 0x0E, 0x10))
        }
        fun text(s: String, size: Float, color: Int = Color.WHITE, bold: Boolean = false) = TextView(this).apply {
            text = s
            textSize = size
            setTextColor(color)
            if (bold) typeface = Typeface.DEFAULT_BOLD
            setPadding(0, px(6), 0, px(6))
            col.addView(this)
        }
        fun button(label: String, primary: Boolean = false, onClick: () -> Unit) = Button(this).apply {
            text = label
            isAllCaps = false
            textSize = 16f
            setTextColor(if (primary) Color.BLACK else Color.WHITE)
            setBackgroundColor(if (primary) orange else Color.rgb(0x22, 0x24, 0x28))
            gravity = Gravity.CENTER
            minHeight = px(56)
            setPadding(px(12), px(10), px(12), px(10))
            setOnClickListener { onClick() }
            col.addView(this, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { topMargin = px(10) })
        }

        text("[ RESCUE ]", 13f, orange, bold = true)
        text("ホームのレスキュー", 26f, bold = true)
        val trouble = Rescue.recentTrouble(this)
        text(
            if (trouble >= Rescue.LIMIT) "ホームが続けて止まったので、ホームの代わりにこの画面を出しています。"
            else "ホームがおかしいときに、ここから逃げられます。",
            15f, Color.rgb(0xCC, 0xCC, 0xCC),
        )
        Rescue.lastTrouble(this)?.let { text("最後の不具合：$it", 12f, Color.rgb(0x88, 0x88, 0x88)) }

        button("別のホームアプリを選ぶ", primary = true) { Rescue.chooseHome(this) }
        text("端末に最初から入っているホーム（One UI ホームなど）を選べば、いつもどおり使えます。あとで戻すときは Theta Home Hatch を選び直します。", 12f, Color.rgb(0x99, 0x99, 0x99))
        button("セーフモードで開く（ウィジェットと天気を止める）") {
            Rescue.reset(this)
            startActivity(Intent(this, MainActivity::class.java).putExtra(Rescue.EXTRA_SAFE, true).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK))
            finish()
        }
        button("普通に開き直す") {
            Rescue.reset(this)
            startActivity(Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK))
            finish()
        }
        button("最新版を入れる（Releases をブラウザで）") { open(Rescue.RELEASES) }
        button("Android の設定を開く") { runCatching { startActivity(Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } }
        button("不具合を伝える（開発の会話）") { open(SelfUpdate.REQUEST_URL) }
        text("通知パネルのクイック設定に「ホームのレスキュー」タイルを足しておくと、ホームが固まっていてもここを開けます。", 12f, Color.rgb(0x99, 0x99, 0x99))

        setContentView(ScrollView(this).apply { setBackgroundColor(Color.rgb(0x0D, 0x0E, 0x10)); addView(col) })
    }

    private fun open(url: String) {
        runCatching { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
    }
}
