package jp.yaegaki.theta.home

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import com.github.takahirom.roborazzi.captureRoboImage
import jp.yaegaki.theta.home.data.IconStyle
import jp.yaegaki.theta.home.ui.HomeTheme
import jp.yaegaki.theta.home.ui.LocalPalette
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** 見本のアイコン（色とりどり）を、元・簡易ツートーン（ダーク・ライト）で並べて PNG に残す。 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w420dp-h300dp-xhdpi")
class IconPreviewTest {
    @get:Rule
    val compose = createComposeRule()

    private fun sample(i: Int): Bitmap {
        val b = Bitmap.createBitmap(192, 192, Bitmap.Config.ARGB_8888)
        val c = Canvas(b)
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        when (i) {
            0 -> { // 地図風：緑の角丸に赤いピン
                p.color = Color.rgb(52, 168, 83); c.drawRoundRect(RectF(8f, 8f, 184f, 184f), 40f, 40f, p)
                p.color = Color.rgb(234, 67, 53); c.drawCircle(96f, 80f, 40f, p); c.drawRect(88f, 80f, 104f, 150f, p)
            }
            1 -> { // チャット風：緑の地に白い吹き出し
                p.color = Color.rgb(6, 199, 85); c.drawRoundRect(RectF(8f, 8f, 184f, 184f), 40f, 40f, p)
                p.color = Color.WHITE; c.drawOval(RectF(36f, 48f, 156f, 136f), p)
            }
            2 -> { // カメラ風：黒の円に灰色のレンズ
                p.color = Color.rgb(30, 30, 30); c.drawCircle(96f, 96f, 88f, p)
                p.color = Color.rgb(160, 160, 170); c.drawCircle(96f, 96f, 44f, p)
            }
            else -> { // 音楽風：赤い地に白い音符
                p.color = Color.rgb(250, 45, 72); c.drawRoundRect(RectF(8f, 8f, 184f, 184f), 40f, 40f, p)
                p.color = Color.WHITE; c.drawCircle(80f, 130f, 22f, p); c.drawRect(96f, 50f, 108f, 130f, p); c.drawRect(96f, 50f, 140f, 64f, p)
            }
        }
        return b
    }

    @Test
    fun preview() {
        val icons = (0..3).map { sample(it) }
        val masks = icons.map { IconStyle.localMask(it) }
        compose.setContent {
            Column {
                for (dark in listOf(true, false)) {
                    HomeTheme(dark = dark) {
                        val p = LocalPalette.current
                        Column(Modifier.background(p.bg).padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(if (dark) "DARK  original → two-tone" else "LIGHT original → two-tone", color = p.muted)
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                icons.forEachIndexed { i, ic ->
                                    Image(ic.asImageBitmap(), null, Modifier.size(40.dp))
                                    Image(masks[i].asImageBitmap(), null, Modifier.size(40.dp).border(1.dp, p.line), colorFilter = IconStyle.twoTone(p.accent, p.raised))
                                }
                            }
                        }
                    }
                }
            }
        }
        compose.onRoot().captureRoboImage("build/screenshots/icon-two-tone-preview.png")
    }
}
