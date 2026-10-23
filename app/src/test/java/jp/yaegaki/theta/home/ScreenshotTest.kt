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
import androidx.activity.compose.setContent
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

    private fun launch(theme: String = "dark", iconMode: String = "mono", setup: (HomeApp) -> Unit = {}) {
        val app = ApplicationProvider.getApplicationContext<HomeApp>()
        // 前のテストの設定（テーマ・カテゴリー）を持ち越さない
        app.store.prefs.edit().clear().commit()
        app.store.themeMode = theme
        app.store.iconMode = iconMode
        app.store.autoUpdate = false
        setup(app)
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
        compose.onNodeWithContentDescription("HATCH（Alt+H）").performClick()
        compose.mainClock.advanceTimeBy(600)
        shot("fold5-main-hatch")
    }

    @Test
    @Config(qualifiers = "w904dp-h1104dp-xhdpi")
    fun foldMainLight() {
        launch("light")
        shot("fold5-main-light")
        compose.onNodeWithContentDescription("APPS（Alt+A）").performClick()
        compose.mainClock.advanceTimeBy(600)
        shot("fold5-main-light-apps")
    }

    @Test
    @Config(qualifiers = "w360dp-h860dp-xxhdpi")
    fun foldCover() {
        launch()
        shot("fold5-cover-filer")
        compose.onNodeWithContentDescription("CAPTURE（Alt+R）").performClick()
        compose.mainClock.advanceTimeBy(600)
        shot("fold5-cover-capture")
    }

    /** 最近のファイルに写真を並べ、グリッドとサムネイルを確かめる。 */
    private fun photos(app: HomeApp, view: String) {
        val dir = java.io.File(app.filesDir, "Capture").apply { mkdirs() }
        val colors = listOf(0xFFB0452A, 0xFF2A6FB0, 0xFF3C8C4A, 0xFFC9A227, 0xFF6B3FA0, 0xFF444444)
        val list = colors.mapIndexed { i, c ->
            val f = java.io.File(dir, "photo-${i + 1}.png")
            val bmp = android.graphics.Bitmap.createBitmap(240, 180, android.graphics.Bitmap.Config.ARGB_8888)
            val canvas = android.graphics.Canvas(bmp)
            canvas.drawColor(c.toInt())
            canvas.drawCircle(120f, 90f, 50f, android.graphics.Paint().apply { color = android.graphics.Color.WHITE })
            f.outputStream().use { bmp.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
            jp.yaegaki.theta.home.data.RecentFile(android.net.Uri.fromFile(f).toString(), f.name, "image/png", "PHOTO", 1_761_200_000_000L - i * 3_600_000L)
        } + jp.yaegaki.theta.home.data.RecentFile("content://x/doc.pdf", "report.pdf", "application/pdf", "WORK", 1_761_100_000_000L)
        app.store.saveRecents(list)
        app.store.location = "recent"
        app.store.saveFolderViews(mapOf("recent" to view))
    }

    @Test
    @Config(qualifiers = "w360dp-h860dp-xxhdpi")
    fun filerGrid() {
        launch { photos(it, "grid:1") }
        compose.mainClock.advanceTimeBy(1000)
        shot("filer-grid")
    }

    @Test
    @Config(qualifiers = "w360dp-h860dp-xxhdpi")
    fun filerList() {
        launch("light") { photos(it, "list:2") }
        compose.mainClock.advanceTimeBy(1000)
        shot("filer-list-thumbs")
    }

    /** 検索フォルダ（仮想フォルダ）を作る画面。 */
    @Test
    @Config(qualifiers = "w360dp-h860dp-xxhdpi")
    fun queryDialog() {
        launch()
        scenario!!.onActivity { a ->
            val model = androidx.lifecycle.ViewModelProvider(a)[HomeModel::class.java]
            val q = jp.yaegaki.theta.home.data.SavedQuery(
                "q-test", "請求書", "drive", kinds = setOf("pdf", "sheet"), nameContains = "請求", days = 30,
                folderId = "1abc", folderName = "経理",
            )
            a.setContent {
                jp.yaegaki.theta.home.ui.HomeTheme(dark = true) { jp.yaegaki.theta.home.ui.QueryDialog(model, q, inline = true) {} }
            }
        }
        compose.waitForIdle()
        compose.onRoot().captureRoboImage("build/screenshots/query-dialog.png")
    }

    @Test
    @Config(qualifiers = "w360dp-h860dp-xxhdpi")
    fun sorter() {
        launch()
        compose.onNodeWithContentDescription("APPS（Alt+A）").performClick()
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
        compose.onNodeWithContentDescription("APPS（Alt+A）").performClick()
        compose.mainClock.advanceTimeBy(600)
        compose.onNodeWithText("仕分け 5").performClick()
        startSorting()
        shot("sort-step2-assign-light")
    }

    @Test
    @Config(qualifiers = "w360dp-h860dp-xxhdpi")
    fun twoToneIcons() {
        launch(iconMode = "local")
        compose.onNodeWithContentDescription("APPS（Alt+A）").performClick()
        compose.mainClock.advanceTimeBy(600)
        shot("apps-two-tone")
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
