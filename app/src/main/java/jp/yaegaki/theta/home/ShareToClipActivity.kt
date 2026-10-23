package jp.yaegaki.theta.home

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.core.content.IntentCompat

/** 共有メニューで送られたテキスト・URL・ファイルを Clip に保存して、すぐ閉じる。 */
class ShareToClipActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val i = intent
        val text = i.getStringExtra(Intent.EXTRA_TEXT)
        val streams = when (i.action) {
            Intent.ACTION_SEND -> listOfNotNull(IntentCompat.getParcelableExtra(i, Intent.EXTRA_STREAM, Uri::class.java))
            Intent.ACTION_SEND_MULTIPLE -> IntentCompat.getParcelableArrayListExtra(i, Intent.EXTRA_STREAM, Uri::class.java).orEmpty()
            else -> emptyList()
        }
        Thread {
            HomeModel.addToClip(applicationContext, text, streams)
            runOnUiThread {
                Toast.makeText(applicationContext, "Clip に保存しました", Toast.LENGTH_SHORT).show()
                finish()
            }
        }.start()
    }
}
