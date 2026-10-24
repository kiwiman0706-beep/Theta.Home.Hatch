package jp.yaegaki.theta.home

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import jp.yaegaki.theta.home.data.IconStyle
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class IconStyleTest {
    private fun icon(bg: Int, fg: Int): Bitmap {
        val b = Bitmap.createBitmap(96, 96, Bitmap.Config.ARGB_8888)
        val c = Canvas(b)
        c.drawColor(bg)
        c.drawCircle(48f, 48f, 20f, Paint().apply { color = fg; isAntiAlias = false })
        return b
    }

    // 型の作り：地＝黒、基本＝赤、アクセント＝緑

    @Test
    fun shapeIsBaseOnBackground() {
        val m = IconStyle.localMask(icon(Color.rgb(255, 200, 0), Color.rgb(20, 40, 120)))
        assertEquals(Color.RED, m.getPixel(96, 96))    // 真ん中＝形（基本）
        assertEquals(Color.BLACK, m.getPixel(4, 4))    // 隅＝地
    }

    @Test
    fun darkIconsStillBecomeShapeOnBackground() {
        // 地が暗いアイコンでも、多い方を地にする
        val m = IconStyle.localMask(icon(Color.rgb(10, 10, 10), Color.rgb(240, 240, 240)))
        assertEquals(Color.RED, m.getPixel(96, 96))
        assertEquals(Color.BLACK, m.getPixel(4, 4))
    }

    @Test
    fun secondColorBecomesAccent() {
        // 黄色の地に紺の丸、その中に小さな赤い点 → 地・基本・アクセント
        val b = icon(Color.rgb(255, 200, 0), Color.rgb(20, 40, 120))
        Canvas(b).drawCircle(48f, 48f, 6f, Paint().apply { color = Color.rgb(230, 30, 30); isAntiAlias = false })
        val m = IconStyle.localMask(b)
        assertEquals(Color.GREEN, m.getPixel(96, 96))  // 真ん中の点＝アクセント
        assertEquals(Color.RED, m.getPixel(96, 96 - 30)) // 丸の上の方＝基本
        assertEquals(Color.BLACK, m.getPixel(4, 4))
    }

    @Test
    fun legacyMaskBecomesBaseOnly() {
        val old = Bitmap.createBitmap(4, 1, Bitmap.Config.ARGB_8888)
        old.setPixel(0, 0, Color.BLACK); old.setPixel(1, 0, Color.WHITE)
        val m = IconStyle.fromLegacy(old)
        assertEquals(Color.RED, m.getPixel(0, 0))
        assertEquals(Color.BLACK, m.getPixel(1, 0))
    }
}
