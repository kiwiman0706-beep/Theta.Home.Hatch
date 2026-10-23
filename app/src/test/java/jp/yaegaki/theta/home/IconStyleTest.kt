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

    @Test
    fun maskIsBlackShapeOnWhite() {
        val m = IconStyle.localMask(icon(Color.rgb(255, 200, 0), Color.rgb(20, 40, 120)))
        assertEquals(Color.BLACK, m.getPixel(96, 96))   // 真ん中＝形
        assertEquals(Color.WHITE, m.getPixel(4, 4))     // 隅＝地
    }

    @Test
    fun darkIconsStillBecomeShapeOnWhite() {
        // 地が暗いアイコンでも、多い方を地（白）にする
        val m = IconStyle.localMask(icon(Color.rgb(10, 10, 10), Color.rgb(240, 240, 240)))
        assertEquals(Color.BLACK, m.getPixel(96, 96))
        assertEquals(Color.WHITE, m.getPixel(4, 4))
    }
}
