package jp.yaegaki.theta.home

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * 実際の画面を JVM 上で描いて PNG に残す（build/screenshots/）。端末の権限やデータは無い状態の見た目。
 * 実機の代わりにはならないが、レイアウト崩れの確認に使う。
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class ScreenshotTest {
    @get:Rule
    val compose = createAndroidComposeRule<MainActivity>()

    private fun shot(name: String) {
        compose.waitForIdle()
        compose.onRoot().captureRoboImage("build/screenshots/$name.png")
    }

    @Test
    @Config(qualifiers = "w904dp-h1104dp-xhdpi")
    fun foldMain() {
        shot("fold5-main-filer")
        compose.onNodeWithContentDescription("Hatch（Alt+H）").performClick()
        compose.mainClock.advanceTimeBy(600)
        shot("fold5-main-hatch")
    }

    @Test
    @Config(qualifiers = "w360dp-h860dp-xxhdpi")
    fun foldCover() {
        shot("fold5-cover-filer")
        compose.onNodeWithContentDescription("記録（Alt+R）").performClick()
        compose.mainClock.advanceTimeBy(600)
        shot("fold5-cover-capture")
    }

    @Test
    @Config(qualifiers = "w1280dp-h800dp-mdpi")
    fun tablet() {
        shot("tablet-home")
    }

    @Test
    @Config(qualifiers = "w480dp-h480dp-xhdpi")
    fun titan() {
        shot("titan-home")
    }
}
