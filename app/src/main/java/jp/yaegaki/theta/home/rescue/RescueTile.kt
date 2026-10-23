package jp.yaegaki.theta.home.rescue

import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService

/**
 * クイック設定のタイル「ホームのレスキュー」。通知パネルは Android 側の画面なので、ホームが固まっていても押せる。
 * 本体と別のプロセスで動く。
 */
class RescueTile : TileService() {
    override fun onStartListening() {
        qsTile?.apply {
            state = Tile.STATE_INACTIVE
            label = "ホームのレスキュー"
            updateTile()
        }
    }

    override fun onClick() {
        val i = Intent(this, RescueActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (Build.VERSION.SDK_INT >= 34) {
            startActivityAndCollapse(PendingIntent.getActivity(this, 0, i, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT))
        } else {
            @Suppress("DEPRECATION", "StartActivityAndCollapseDeprecated")
            startActivityAndCollapse(i)
        }
    }
}
