package jp.yaegaki.theta.home

import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import com.github.takahirom.roborazzi.captureRoboImage
import jp.yaegaki.theta.home.rescue.Rescue
import jp.yaegaki.theta.home.rescue.RescueActivity
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class RescueTest {
    private val app get() = ApplicationProvider.getApplicationContext<HomeApp>()

    @Test
    fun healthyStartsDoNotTrigger() {
        Rescue.reset(app)
        repeat(5) {
            assertFalse(Rescue.onHomeStart(app))
            Rescue.onHomeHealthy(app)
        }
    }

    @Test
    fun repeatedFailuresShowRescue() {
        Rescue.reset(app)
        // 「無事」の印が付かないまま起動し直すのが続く（落ちた・固まって止められた）
        assertFalse(Rescue.onHomeStart(app))
        assertFalse(Rescue.onHomeStart(app))
        assertFalse(Rescue.onHomeStart(app))
        assertTrue(Rescue.onHomeStart(app))
        Rescue.reset(app)
        assertFalse(Rescue.onHomeStart(app))
    }

    @Test
    @Config(qualifiers = "w360dp-h860dp-xxhdpi")
    fun screen() {
        ActivityScenario.launch(RescueActivity::class.java).use { s ->
            s.onActivity { it.window.decorView.captureRoboImage("build/screenshots/rescue.png") }
        }
    }
}
