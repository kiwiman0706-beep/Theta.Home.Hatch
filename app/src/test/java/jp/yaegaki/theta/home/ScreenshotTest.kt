package jp.yaegaki.theta.home

import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.ApplicationInfo
import android.content.pm.ResolveInfo
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * 実際の画面を JVM 上で描いて PNG に残す（build/screenshots/）。権限やデータは無く、アプリは架空のものを数個入れた状態。
 * 実機の代わりにはならないが、レイアウト崩れの確認に使う。
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class ScreenshotTest {
    @get:Rule
    val compose = createEmptyComposeRule()

    private var scenario: ActivityScenario<MainActivity>? = null

    private fun launch(theme: String = "dark") {
        val app = ApplicationProvider.getApplicationContext<HomeApp>()
        // 前のテストの設定（テーマ・カテゴリー）を持ち越さない
        app.store.prefs.edit().clear().commit()
        app.store.themeMode = theme
        val pm = shadowOf(app.packageManager)
        val launcher = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        listOf(
            Triple("com.example.chat", "Chat", ApplicationInfo.CATEGORY_SOCIAL),
            Triple("com.example.maps", "Maps", ApplicationInfo.CATEGORY_MAPS),
            Triple("com.example.music", "Music", ApplicationInfo.CATEGORY_AUDIO),
            Triple("com.example.notes", "Notes", ApplicationInfo.CATEGORY_PRODUCTIVITY),
            Triple("com.example.puzzle", "Puzzle", ApplicationInfo.CATEGORY_GAME),
        ).forEach { (pkg, label, category) ->
            val info = ApplicationInfo().apply { packageName = pkg; this.category = category; nonLocalizedLabel = label }
            pm.addResolveInfoForIntent(launcher, ResolveInfo().apply {
                activityInfo = ActivityInfo().apply { packageName = pkg; name = "$pkg.Main"; applicationInfo = info; nonLocalizedLabel = label }
                nonLocalizedLabel = label
            })
        }
        scenario = ActivityScenario.launch(MainActivity::class.java)
        compose.waitForIdle()
    }

    /** カテゴリーの用意（1 段目）が出ていれば、候補のまま始める。 */
    private fun startSorting() {
        compose.waitForIdle()
        if (compose.onAllNodesWithText("個で仕分けを始める", substring = true).fetchSemanticsNodes().isNotEmpty()) {
            compose.onNodeWithText("個で仕分けを始める", substring = true).performClick()
        }
    }

    @After
    fun close() {
        scenario?.close()
    }

    private fun shot(name: String) {
        compose.waitForIdle()
        compose.onRoot().captureRoboImage("build/screenshots/$name.png")
    }

    @Test
    @Config(qualifiers = "w904dp-h1104dp-xhdpi")
    fun foldMain() {
        launch()
        shot("fold5-main-filer")
        compose.onNodeWithContentDescription("Hatch（Alt+H）").performClick()
        compose.mainClock.advanceTimeBy(600)
        shot("fold5-main-hatch")
    }

    @Test
    @Config(qualifiers = "w904dp-h1104dp-xhdpi")
    fun foldMainLight() {
        launch("light")
        shot("fold5-main-light")
        compose.onNodeWithContentDescription("アプリ（Alt+A）").performClick()
        compose.mainClock.advanceTimeBy(600)
        shot("fold5-main-light-apps")
    }

    @Test
    @Config(qualifiers = "w360dp-h860dp-xxhdpi")
    fun foldCover() {
        launch()
        shot("fold5-cover-filer")
        compose.onNodeWithContentDescription("記録（Alt+R）").performClick()
        compose.mainClock.advanceTimeBy(600)
        shot("fold5-cover-capture")
    }

    @Test
    @Config(qualifiers = "w360dp-h860dp-xxhdpi")
    fun sorter() {
        launch()
        compose.onNodeWithContentDescription("アプリ（Alt+A）").performClick()
        compose.mainClock.advanceTimeBy(600)
        shot("sort-apps-drawer")
        compose.onNodeWithText("仕分け 5").performClick()
        shot("sort-step1-categories")
        startSorting()
        shot("sort-step2-assign")
    }

    @Test
    @Config(qualifiers = "w360dp-h860dp-xxhdpi")
    fun sorterLight() {
        launch("light")
        compose.onNodeWithContentDescription("アプリ（Alt+A）").performClick()
        compose.mainClock.advanceTimeBy(600)
        compose.onNodeWithText("仕分け 5").performClick()
        startSorting()
        shot("sort-step2-assign-light")
    }

    @Test
    @Config(qualifiers = "w1280dp-h800dp-mdpi")
    fun tablet() {
        launch()
        shot("tablet-home")
    }

    @Test
    @Config(qualifiers = "w480dp-h480dp-xhdpi")
    fun titan() {
        launch()
        shot("titan-home")
    }
}
