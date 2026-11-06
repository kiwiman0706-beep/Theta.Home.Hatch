package app.thetahatch.home.hub

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import app.thetahatch.home.MainActivity

/**
 * ほかのアプリの「更新する」から呼ばれる：Intent("app.thetahatch.action.UPDATE")（extra packageName）。
 * ホームの Hatch の一覧を開き、そのアプリの新しい版を確かめる。
 */
class UpdateActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val pkg = intent?.getStringExtra("packageName") ?: callingPackage
        Catalog.load(this)
        val id = pkg?.let { Catalog.byPackage(it)?.id } ?: ""
        startActivity(
            Intent(this, MainActivity::class.java)
                .putExtra(MainActivity.EXTRA_HATCH, id)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
        )
        finish()
    }
}
