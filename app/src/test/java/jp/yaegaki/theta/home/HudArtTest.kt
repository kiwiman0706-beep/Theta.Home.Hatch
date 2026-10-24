package jp.yaegaki.theta.home

import android.graphics.Bitmap
import androidx.test.core.app.ApplicationProvider
import jp.yaegaki.theta.home.data.HudArt
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/** HUD の壁紙（Fold5 のメイン・カバー、ダーク・ライト）を描いて残す。 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class HudArtTest {
    private val ctx get() = ApplicationProvider.getApplicationContext<HomeApp>()

    private fun save(b: Bitmap, name: String) {
        val f = File("build/screenshots/$name.png").apply { parentFile?.mkdirs() }
        f.outputStream().use { b.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    @Test
    fun wallpapers() {
        for (dark in listOf(true, false)) {
            val t = if (dark) "dark" else "light"
            // Fold5：1dp ≒ 2.625px
            save(HudArt.wallpaper(ctx, 1812, 2176, dark, 2.625f), "wallpaper-fold5-main-$t")
            save(HudArt.wallpaper(ctx, 904, 2316, dark, 2.625f), "wallpaper-fold5-cover-$t")
        }
    }

    @Test
    fun paintMaskUsesThreeColors() {
        val mask = Bitmap.createBitmap(3, 1, Bitmap.Config.ARGB_8888)
        mask.setPixel(0, 0, android.graphics.Color.BLACK)
        mask.setPixel(1, 0, android.graphics.Color.RED)
        mask.setPixel(2, 0, android.graphics.Color.GREEN)
        val out = HudArt.paintMask(mask, 3, 0xFF111111.toInt(), 0xFFEEEEEE.toInt(), 0xFFFF6B1A.toInt())
        assertEquals(0xFF111111.toInt(), out.getPixel(0, 0))
        assertEquals(0xFFEEEEEE.toInt(), out.getPixel(1, 0))
        assertEquals(0xFFFF6B1A.toInt(), out.getPixel(2, 0))
    }
}
