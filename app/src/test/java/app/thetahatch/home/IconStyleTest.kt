package app.thetahatch.home

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import app.thetahatch.home.data.IconStyle
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

    /** テーマの絵（フルカラー）：テーマごとに別の保存場所、頼み方はテーマの画風か配色から。製品の名前は入れない。 */
    @Test
    fun themeIconsPerTheme() {
        val ctx = androidx.test.core.app.ApplicationProvider.getApplicationContext<android.content.Context>()
        val g = IconStyle.fullFamily("gazee")
        org.junit.Assert.assertTrue(IconStyle.isFull(g))
        org.junit.Assert.assertFalse(IconStyle.isFull(IconStyle.FAMILY_ROUND))
        org.junit.Assert.assertNotEquals(IconStyle.cacheFile(ctx, "a/b", g), IconStyle.cacheFile(ctx, "a/b", IconStyle.fullFamily("dusk")))
        // まだ作っていなければ、ほかの型で代えない
        org.junit.Assert.assertNull(IconStyle.cachedFull(ctx, "a/b", g))
        val gazee = IconStyle.fullPrompt("gazee", listOf("#000000", "#111111", "#222222", "#333333", "#444444"))
        org.junit.Assert.assertTrue(gazee.contains("isometric"))
        org.junit.Assert.assertFalse(gazee.contains("BeOS", ignoreCase = true))
        val other = IconStyle.fullPrompt("braun", listOf("#F6F5F1", "#E8711A", "#5E7A68", "#3A3A3A", "#2A2A2A"))
        org.junit.Assert.assertTrue(other.contains("#E8711A") && other.contains("no text"))
    }
}
