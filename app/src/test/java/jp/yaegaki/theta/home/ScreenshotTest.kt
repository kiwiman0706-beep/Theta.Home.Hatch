package jp.yaegaki.theta.home

import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.ApplicationInfo
import android.content.pm.ResolveInfo
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.performTouchInput
import androidx.compose.foundation.background
import androidx.compose.ui.test.performScrollTo
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
        jp.yaegaki.theta.home.rescue.Rescue.reset(app)
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
        compose.onNodeWithContentDescription("CAPTURE（Alt+R）").performScrollTo().performClick()
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

    /** POP（カラフル）：開いた画面・アプリのドロワー、閉じた画面のダッシュボード。 */
    @Test
    @Config(qualifiers = "w904dp-h1104dp-xhdpi")
    fun popMain() {
        launch("pop")
        shot("pop-main-filer")
        compose.onNodeWithContentDescription("HATCH（Alt+H）").performClick()
        compose.mainClock.advanceTimeBy(600)
        shot("pop-main-hatch")
    }

    @Test
    @Config(qualifiers = "w360dp-h860dp-xxhdpi")
    fun popCover() {
        launch("pop")
        shot("pop-cover-dashboard")
        compose.onNodeWithContentDescription("START すべてのアプリ（Alt+A）").performClick()
        compose.mainClock.advanceTimeBy(600)
        shot("pop-cover-apps")
    }

    /** 端のタブを全部外しても、下の固定の START から全アプリを開ける。 */
    @Test
    @Config(qualifiers = "w360dp-h860dp-xxhdpi")
    fun startButton() {
        launch()
        compose.waitForIdle()
        scenario!!.onActivity { a ->
            val m = (a.application as HomeApp).model
            m.tabs.toList().forEach { m.removeTab(it.id) }
        }
        compose.waitForIdle()
        compose.onNodeWithContentDescription("START すべてのアプリ（Alt+A）").assertIsDisplayed().performClick()
        compose.mainClock.advanceTimeBy(600)
        compose.onNodeWithText("名前で絞り込み").assertExists()
        shot("start-button")
    }

    /** 長押し→ドラッグで並べ替え：アプリ（カテゴリーのタブ）と端のタブ。動かさずに離すとメニュー。 */
    @Test
    @Config(qualifiers = "w360dp-h860dp-xxhdpi")
    fun dragToReorder() {
        launch()
        compose.waitForIdle()
        var model: HomeModel? = null
        scenario!!.onActivity { a ->
            val m = (a.application as HomeApp).model
            val c = m.addCategory("DRAG")
            m.apps.forEach { m.assign(it, c) }
            m.addCategoryTab(c)
            model = m
        }
        compose.waitForIdle()
        compose.onNodeWithContentDescription("DRAG").performScrollTo().performClick()
        compose.mainClock.advanceTimeBy(600)
        fun drag(from: String, to: String) {
            val a = compose.onNodeWithText(from).fetchSemanticsNode().boundsInRoot
            val b = compose.onNodeWithText(to).fetchSemanticsNode().boundsInRoot
            val node = compose.onNodeWithText(from)
            node.performTouchInput { down(center) }
            compose.mainClock.advanceTimeBy(1000)
            val step = (b.center - a.center) / 10f
            repeat(10) { node.performTouchInput { moveBy(step) }; compose.mainClock.advanceTimeBy(16) }
            node.performTouchInput { up() }
            compose.waitForIdle()
        }
        val m = model!!
        val cat = m.categories.first { it.name == "DRAG" }.id
        val before = m.ordered(cat, m.apps.filter { m.appCategories[it.key] == cat }).map { it.label }
        drag(before[0], before[2])
        val expected = before.toMutableList().also { it.add(2, it.removeAt(0)) }
        org.junit.Assert.assertEquals(expected, m.ordered(cat, m.apps.filter { m.appCategories[it.key] == cat }).map { it.label })
        shot("drag-reorder-apps")

        // 動かさずに離すとメニュー
        compose.onNodeWithText(expected[0]).performTouchInput { down(center) }
        compose.mainClock.advanceTimeBy(1000)
        compose.onNodeWithText(expected[0]).performTouchInput { up() }
        compose.waitForIdle()
        compose.onNodeWithText("カテゴリーを変える").assertExists()
    }

    @Test
    @Config(qualifiers = "w360dp-h860dp-xxhdpi")
    fun dragTabs() {
        launch()
        compose.waitForIdle()
        var model: HomeModel? = null
        scenario!!.onActivity { a -> model = (a.application as HomeApp).model }
        val from = compose.onNodeWithContentDescription("WORK（Alt+1）")
        val a = from.fetchSemanticsNode().boundsInRoot
        val b = compose.onNodeWithContentDescription("PHOTO（Alt+3）").fetchSemanticsNode().boundsInRoot
        from.performTouchInput { down(center) }
        compose.mainClock.advanceTimeBy(1000)
        val step = (b.center - a.center) / 10f
        repeat(10) { from.performTouchInput { moveBy(step) }; compose.mainClock.advanceTimeBy(16) }
        from.performTouchInput { up() }
        compose.waitForIdle()
        org.junit.Assert.assertEquals(listOf("PRIVATE", "PHOTO", "WORK"), model!!.tabs.take(3).map { it.label })
    }

    /** 狭い画面のダッシュボード：今日の予定・天気（見本の予報）・状態・ファイル・端末。 */
    @Test
    @Config(qualifiers = "w360dp-h1400dp-xxhdpi")
    fun dashboard() {
        launch { app ->
            app.store.weatherPlace = jp.yaegaki.theta.home.data.WeatherPlace("東京都 / 東京", 35.68, 139.76)
            app.store.weatherCache = weatherSample(System.currentTimeMillis())
        }
        shot("dashboard-narrow")
    }

    private fun sampleEvents(): List<jp.yaegaki.theta.home.data.Event> {
        val today = jp.yaegaki.theta.home.data.Calendar.startOfDay()
        fun at(day: Int, h: Int, m: Int = 0) = jp.yaegaki.theta.home.data.Calendar.addDays(today, day) + (h * 60 + m) * 60_000L
        val utc = java.util.TimeZone.getDefault().getOffset(today)
        var id = 1L
        fun ev(title: String, day: Int, h: Int, len: Int, color: Long) = jp.yaegaki.theta.home.data.Event(id++, title, at(day, h), at(day, h) + len * 60_000L, false, null, color.toInt())
        fun all(title: String, day: Int, days: Int, color: Long) = jp.yaegaki.theta.home.data.Event(id++, title, at(day, 0) + utc, at(day + days, 0) + utc, true, null, color.toInt())
        return listOf(
            all("出張（大阪）", 1, 2, 0xFF3F7FBF), ev("朝会", 0, 9, 30, 0xFFE0662B), ev("設計レビュー", 0, 10, 90, 0xFF4CAF50),
            ev("昼食 with 田中", 0, 12, 60, 0xFF9C27B0), ev("歯医者", 0, 16, 45, 0xFFE0662B), ev("1on1", 0, 10, 30, 0xFF3F7FBF),
            ev("定例", 2, 14, 60, 0xFF4CAF50), ev("ジム", 3, 19, 60, 0xFF9C27B0), ev("請求書の締め", 5, 10, 30, 0xFFE0662B),
            ev("家族で夕食", 6, 18, 120, 0xFF3F7FBF), ev("勉強会", -2, 15, 90, 0xFF4CAF50), ev("美容院", -5, 11, 60, 0xFF9C27B0),
            ev("打ち合わせ A", 8, 9, 60, 0xFFE0662B), ev("打ち合わせ B", 8, 11, 60, 0xFF3F7FBF), ev("打ち合わせ C", 8, 13, 60, 0xFF4CAF50),
            ev("打ち合わせ D", 8, 15, 60, 0xFF9C27B0), ev("飲み会", 8, 19, 120, 0xFFE0662B), all("連休", 12, 3, 0xFF4CAF50),
        )
    }

    private fun calendarShot(name: String, content: @androidx.compose.runtime.Composable (List<jp.yaegaki.theta.home.data.Event>, Long) -> Unit) {
        launch()
        val events = sampleEvents()
        val today = jp.yaegaki.theta.home.data.Calendar.startOfDay()
        scenario!!.onActivity { a ->
            a.setContent {
                jp.yaegaki.theta.home.ui.HomeTheme(dark = true) {
                    androidx.compose.foundation.layout.Box(androidx.compose.ui.Modifier.background(jp.yaegaki.theta.home.ui.LocalPalette.current.panel)) { content(events, today) }
                }
            }
        }
        compose.waitForIdle()
        compose.onRoot().captureRoboImage("build/screenshots/$name.png")
    }

    @Test
    @Config(qualifiers = "w340dp-h900dp-xxhdpi")
    fun calendarMonth() = calendarShot("calendar-month") { ev, today ->
        val first = java.util.Calendar.getInstance().apply { timeInMillis = today; set(java.util.Calendar.DAY_OF_MONTH, 1) }.timeInMillis
        val dow = java.util.Calendar.getInstance().apply { timeInMillis = first }.get(java.util.Calendar.DAY_OF_WEEK) - 1
        val from = jp.yaegaki.theta.home.data.Calendar.addDays(first, -dow)
        jp.yaegaki.theta.home.ui.MonthView(ev, from, jp.yaegaki.theta.home.data.Calendar.addDays(from, 42), today, today) {}
    }

    @Test
    @Config(qualifiers = "w340dp-h900dp-xxhdpi")
    fun calendarDay() = calendarShot("calendar-day") { ev, today ->
        jp.yaegaki.theta.home.ui.DayView(ev, today, today) {}
    }

    /** 端のタブの「＋」でウィジェットの追加の画面が出る。 */
    @Test
    @Config(qualifiers = "w360dp-h860dp-xxhdpi")
    fun addWidgetOpens() {
        launch()
        compose.onNodeWithContentDescription("タブを追加").performScrollTo().performClick()
        compose.waitForIdle()
        compose.onNodeWithText("＋ ウィジェット").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("ウィジェットのタブを追加", substring = true).assertExists()
    }

    /** アプリのカテゴリーをタブにして開く（グリッド）。 */
    @Test
    @Config(qualifiers = "w360dp-h860dp-xxhdpi")
    fun categoryTab() {
        launch()
        compose.waitForIdle()
        scenario!!.onActivity { a ->
            val model = (a.application as HomeApp).model
            val c = model.addCategory("ツール")
            model.apps.forEach { model.assign(it, c) }
            model.addCategoryTab(c)
        }
        compose.waitForIdle()
        compose.onNodeWithContentDescription("ツール").performScrollTo().performClick()
        compose.mainClock.advanceTimeBy(600)
        shot("category-tab")
    }

    /** 検索フォルダ（仮想フォルダ）を作る画面。 */
    @Test
    @Config(qualifiers = "w360dp-h860dp-xxhdpi")
    fun queryDialog() {
        launch()
        scenario!!.onActivity { a ->
            val model = (a.application as HomeApp).model
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
        compose.onAllNodesWithText("仕分け", substring = true).onFirst().performClick()
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
        compose.onAllNodesWithText("仕分け", substring = true).onFirst().performClick()
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

    /** エッジ：ほかのアプリの上に重ねる画面。タブと中身はホームと同じ（ここではフォルダと画像のタブを開いたところ）。 */
    private fun edgeShot(name: String, side: String, theme: String) {
        launch(theme = theme) { it.store.edgeSide = side }
        compose.waitForIdle()
        val app = ApplicationProvider.getApplicationContext<HomeApp>()
        val f = java.io.File(app.filesDir, "edge.png")
        val bmp = android.graphics.Bitmap.createBitmap(600, 400, android.graphics.Bitmap.Config.ARGB_8888)
        android.graphics.Canvas(bmp).apply {
            drawColor(0xFF3C8C4A.toInt())
            drawCircle(300f, 200f, 120f, android.graphics.Paint().apply { color = android.graphics.Color.WHITE })
        }
        f.outputStream().use { bmp.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
        scenario!!.onActivity {
            val m = model()
            val (t, item) = m.addBoardTab(jp.yaegaki.theta.home.data.BoardItem.FILE, fill = false)
            m.setTabFile(item.id, android.net.Uri.fromFile(f))
            m.renameTab(t.id, "DESK")
            m.addBoardItem(t.id, jp.yaegaki.theta.home.data.BoardItem("e2", "calendar", h = mapOf("narrow" to 300)))
            m.setTabApps(t.id, m.apps.map { it.key }.take(4))
            m.toggle(t.id)
        }
        scenario?.close()
        scenario = null
        val edge = ActivityScenario.launch(jp.yaegaki.theta.home.edge.EdgeActivity::class.java)
        compose.waitForIdle()
        compose.mainClock.advanceTimeBy(1200)
        compose.onRoot().captureRoboImage("build/screenshots/$name.png")
        edge.close()
    }

    @Test
    @Config(qualifiers = "w360dp-h800dp-xxhdpi")
    fun edgeLeft() = edgeShot("edge-left", side = "left", theme = "dark")

    @Test
    @Config(qualifiers = "w360dp-h800dp-xxhdpi")
    fun edgeRightPop() = edgeShot("edge-right-pop", side = "right", theme = "pop")

    private fun model(): HomeModel {
        var m: HomeModel? = null
        scenario!!.onActivity { a -> m = (a.application as HomeApp).model }
        return m!!
    }

    /** フォルダのタブはドロワーで開く。上には、そのタブに置いたアプリ。 */
    @Test
    @Config(qualifiers = "w360dp-h860dp-xxhdpi")
    fun folderDrawer() {
        launch()
        compose.waitForIdle()
        scenario!!.onActivity {
            val m = model()
            m.setTabApps("work", m.apps.map { it.key }.take(4))
        }
        compose.onNodeWithContentDescription("WORK（Alt+1）").performClick()
        compose.mainClock.advanceTimeBy(600)
        shot("folder-drawer")
    }

    /** カテゴリーのタブで、ショートカットをアプリと別の段に。 */
    @Test
    @Config(qualifiers = "w360dp-h860dp-xxhdpi")
    fun categorySplit() {
        launch { app ->
            app.store.saveShortcuts(listOf(
                jp.yaegaki.theta.home.data.PinnedShortcut("s1", "com.example.chat", "x1", "家族のトーク", "split"),
                jp.yaegaki.theta.home.data.PinnedShortcut("s2", "com.example.chat", "x2", "仕事のトーク", "split"),
            ))
        }
        compose.waitForIdle()
        scenario!!.onActivity {
            val m = model()
            val c = m.addCategory("COM")
            m.apps.forEach { m.assign(it, c) }
            m.addCategoryTab(c)
            m.reloadShortcuts()
            val store = (it.application as HomeApp).store
            store.saveShortcuts(store.shortcuts().map { sc -> sc.copy(catId = c.id) })
            m.reloadShortcuts()
            m.setTabSplit(m.tabs.last().id, true)
        }
        compose.waitForIdle()
        compose.onNodeWithContentDescription("COM").performScrollTo().performClick()
        compose.mainClock.advanceTimeBy(600)
        shot("category-split")
    }

    /** FILE のタブ：決めた画像をタブの中に。 */
    @Test
    @Config(qualifiers = "w904dp-h1104dp-xhdpi")
    fun fileTab() {
        launch()
        compose.waitForIdle()
        val app = ApplicationProvider.getApplicationContext<HomeApp>()
        val f = java.io.File(app.filesDir, "map.png")
        val bmp = android.graphics.Bitmap.createBitmap(800, 600, android.graphics.Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(bmp)
        canvas.drawColor(0xFF2A4A6A.toInt())
        val paint = android.graphics.Paint().apply { color = android.graphics.Color.WHITE; strokeWidth = 6f }
        for (k in 0..8) canvas.drawLine(0f, k * 75f, 800f, k * 75f, paint)
        for (k in 0..10) canvas.drawLine(k * 80f, 0f, k * 80f, 600f, paint)
        f.outputStream().use { bmp.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
        scenario!!.onActivity {
            val m = model()
            val (t, item) = m.addBoardTab(jp.yaegaki.theta.home.data.BoardItem.FILE)
            m.setTabFile(item.id, android.net.Uri.fromFile(f))
            m.renameTab(t.id, "MAP")
        }
        compose.waitForIdle()
        compose.onNodeWithContentDescription("MAP").performScrollTo().performClick()
        compose.mainClock.advanceTimeBy(1200)
        shot("file-tab")
    }

    /** ふつうのタブ：画像 2 つを半分ずつ横に、その下にフォルダ。編集にすると高さ・半分・並び・外す。 */
    @Test
    @Config(qualifiers = "w904dp-h1104dp-xhdpi")
    fun boardTab() {
        launch()
        compose.waitForIdle()
        val app = ApplicationProvider.getApplicationContext<HomeApp>()
        fun image(name: String, color: Int): android.net.Uri {
            val f = java.io.File(app.filesDir, name)
            val bmp = android.graphics.Bitmap.createBitmap(600, 400, android.graphics.Bitmap.Config.ARGB_8888)
            val c = android.graphics.Canvas(bmp)
            c.drawColor(color)
            c.drawCircle(300f, 200f, 120f, android.graphics.Paint().apply { this.color = android.graphics.Color.WHITE })
            f.outputStream().use { bmp.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
            return android.net.Uri.fromFile(f)
        }
        val a = image("a.png", 0xFFB0452A.toInt())
        val b = image("b.png", 0xFF2A6FB0.toInt())
        scenario!!.onActivity {
            val m = model()
            val t = m.addTab(jp.yaegaki.theta.home.data.TabKind.BOARD)
            m.renameTab(t.id, "DESK")
            m.addBoardItem(t.id, jp.yaegaki.theta.home.data.BoardItem("i1", jp.yaegaki.theta.home.data.BoardItem.FILE, a.toString(), half = true, h = mapOf("wide" to 220)))
            m.addBoardItem(t.id, jp.yaegaki.theta.home.data.BoardItem("i2", jp.yaegaki.theta.home.data.BoardItem.FILE, b.toString(), half = true, h = mapOf("wide" to 220)))
            m.addBoardItem(t.id, jp.yaegaki.theta.home.data.BoardItem("i3", jp.yaegaki.theta.home.data.BoardItem.FOLDER, h = mapOf("wide" to 260)))
            m.setTabApps(t.id, m.apps.map { it.key }.take(3))
            m.setTabWidth(t.id, 560)
        }
        compose.waitForIdle()
        compose.onNodeWithContentDescription("DESK", substring = true).performScrollTo().performClick()
        compose.mainClock.advanceTimeBy(1200)
        shot("board-tab")
        compose.onNodeWithContentDescription("高さ・並び・足す").performClick()
        compose.mainClock.advanceTimeBy(600)
        shot("board-tab-edit")
    }

    /** 今までの専用のタブ（予定）は、中身が予定 1 つのふつうのタブ。予定の下に Web ページを足したところ。 */
    @Test
    @Config(qualifiers = "w904dp-h1104dp-xhdpi")
    fun panelBoard() {
        launch()
        compose.waitForIdle()
        scenario!!.onActivity {
            val m = model()
            val cal = m.tabWith("calendar")!!
            m.updateBoardItem(m.boardItems(cal.id).single().copy(fill = false, h = mapOf("wide" to 520)))
            m.addBoardItem(cal.id, jp.yaegaki.theta.home.data.BoardItem("n1", "mail", h = mapOf("wide" to 220)))
        }
        compose.onNodeWithContentDescription("CALENDAR", substring = true).performScrollTo().performClick()
        compose.mainClock.advanceTimeBy(800)
        shot("panel-board")
    }

    /** テーマ ALERT（警報画面）・SCI-FI（操作卓）：開いた画面で予定のタブを開いたところと、閉じた画面のダッシュボード。 */
    private fun themeShots(theme: String) {
        launch(theme)
        compose.waitForIdle()
        compose.onNodeWithContentDescription("CALENDAR", substring = true).performScrollTo().performClick()
        compose.mainClock.advanceTimeBy(800)
        shot("$theme-main")
    }

    @Test
    @Config(qualifiers = "w904dp-h1104dp-xhdpi")
    fun alertMain() = themeShots("alert")

    @Test
    @Config(qualifiers = "w904dp-h1104dp-xhdpi")
    fun scifiMain() = themeShots("scifi")

    @Test
    @Config(qualifiers = "w360dp-h860dp-xxhdpi")
    fun alertCover() {
        launch("alert")
        shot("alert-cover")
    }

    @Test
    @Config(qualifiers = "w360dp-h860dp-xxhdpi")
    fun scifiCover() {
        launch("scifi")
        shot("scifi-cover")
    }

    /** 天気の欄の警報・注意報（気象庁）。 */
    private fun alertShot(theme: String) {
        launch(theme) { it.store.weatherPlace = jp.yaegaki.theta.home.data.WeatherPlace("新宿区", 35.69, 139.70) }
        compose.waitForIdle()
        scenario!!.onActivity {
            model().alerts = jp.yaegaki.theta.home.data.AlertState(
                "新宿区",
                listOf(
                    jp.yaegaki.theta.home.data.Alert("大雨警報", 2), jp.yaegaki.theta.home.data.Alert("洪水警報", 2),
                    jp.yaegaki.theta.home.data.Alert("雷注意報", 1), jp.yaegaki.theta.home.data.Alert("強風注意報", 1),
                ),
                0L,
            )
        }
        compose.mainClock.advanceTimeBy(600)
        shot("weather-alerts-$theme")
    }

    @Test
    @Config(qualifiers = "w360dp-h860dp-xxhdpi")
    fun weatherAlerts() = alertShot("dark")

    @Test
    @Config(qualifiers = "w360dp-h860dp-xxhdpi")
    fun weatherAlertsAlert() = alertShot("alert")

    /** 中で開く：画像（左右にめくれる）・CSV。 */
    @Test
    @Config(qualifiers = "w360dp-h800dp-xxhdpi")
    fun viewer() {
        launch()
        compose.waitForIdle()
        val app = ApplicationProvider.getApplicationContext<HomeApp>()
        val img = java.io.File(app.filesDir, "sunset.png")
        val bmp = android.graphics.Bitmap.createBitmap(800, 600, android.graphics.Bitmap.Config.ARGB_8888)
        android.graphics.Canvas(bmp).apply {
            drawColor(0xFF2A4A6A.toInt())
            drawCircle(400f, 300f, 160f, android.graphics.Paint().apply { color = 0xFFFF8A3A.toInt() })
        }
        img.outputStream().use { bmp.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
        val csv = java.io.File(app.filesDir, "家計簿.csv").apply { writeText("日付,項目,金額\n10/1,食費,1200\n10/2,\"交通, バス\",230\n10/3,本,1800\n") }
        val items = listOf(
            jp.yaegaki.theta.home.data.ViewItem(android.net.Uri.fromFile(img), img.name, "image/png"),
            jp.yaegaki.theta.home.data.ViewItem(android.net.Uri.fromFile(csv), csv.name, "text/csv"),
        )
        scenario!!.onActivity { model().openViewer(items, 0) }
        // 画像は別のスレッドで読むので、読み終わるまで待つ
        compose.mainClock.autoAdvance = true
        compose.waitUntil(10_000) { compose.onAllNodesWithText("読み込み中…").fetchSemanticsNodes().isEmpty() }
        shot("viewer-image")
        scenario!!.onActivity { model().openViewer(items, 1) }
        compose.mainClock.advanceTimeBy(1500)
        shot("viewer-csv")
    }

    /** 中で開く：Word・Excel・PowerPoint のかんたんなプレビュー。 */
    private fun officeShot(file: String, mime: String, name: String) {
        launch()
        compose.waitForIdle()
        val app = ApplicationProvider.getApplicationContext<HomeApp>()
        val f = java.io.File(app.filesDir, file)
        javaClass.classLoader!!.getResourceAsStream("office/$file")!!.use { i -> f.outputStream().use { o -> i.copyTo(o) } }
        scenario!!.onActivity { model().openViewer(listOf(jp.yaegaki.theta.home.data.ViewItem(android.net.Uri.fromFile(f), file, mime)), 0) }
        compose.mainClock.autoAdvance = true
        compose.waitUntil(10_000) { compose.onAllNodesWithText("読み込み中…").fetchSemanticsNodes().isEmpty() }
        Thread.sleep(500)
        compose.waitForIdle()
        shot(name)
    }

    @Test
    @Config(qualifiers = "w360dp-h800dp-xxhdpi")
    fun viewerDocx() = officeShot("sample.docx", "application/vnd.openxmlformats-officedocument.wordprocessingml.document", "viewer-docx")

    @Test
    @Config(qualifiers = "w360dp-h800dp-xxhdpi")
    fun viewerXlsx() = officeShot("sample.xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", "viewer-xlsx")

    @Test
    @Config(qualifiers = "w360dp-h800dp-xxhdpi")
    fun viewerPptx() = officeShot("sample.pptx", "application/vnd.openxmlformats-officedocument.presentationml.presentation", "viewer-pptx")

    /** カテゴリーのタブにも、アプリの上にウィジェットなどを置ける（ここでは画像と予定）。 */
    @Test
    @Config(qualifiers = "w360dp-h860dp-xxhdpi")
    fun categoryBoard() {
        launch()
        compose.waitForIdle()
        val app = ApplicationProvider.getApplicationContext<HomeApp>()
        val f = java.io.File(app.filesDir, "family.png")
        val bmp = android.graphics.Bitmap.createBitmap(600, 300, android.graphics.Bitmap.Config.ARGB_8888)
        android.graphics.Canvas(bmp).apply { drawColor(0xFF3C8C4A.toInt()); drawCircle(300f, 150f, 90f, android.graphics.Paint().apply { color = android.graphics.Color.WHITE }) }
        f.outputStream().use { bmp.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
        scenario!!.onActivity {
            val m = model()
            val c = m.addCategory("COM")
            m.apps.forEach { a -> m.assign(a, c) }
            m.addCategoryTab(c)
            val t = m.tabs.last()
            m.addBoardItem(t.id, jp.yaegaki.theta.home.data.BoardItem("cb1", jp.yaegaki.theta.home.data.BoardItem.FILE, android.net.Uri.fromFile(f).toString(), h = mapOf("narrow" to 160)))
        }
        compose.waitForIdle()
        compose.onNodeWithContentDescription("COM", substring = true).performScrollTo().performClick()
        compose.mainClock.advanceTimeBy(1200)
        shot("category-board")
        compose.onNodeWithContentDescription("高さ・並び・足す").performClick()
        compose.mainClock.advanceTimeBy(600)
        shot("category-board-edit")
    }

    /** 端の下（START の上）に固定のよく使うアプリ。 */
    @Test
    @Config(qualifiers = "w360dp-h860dp-xxhdpi")
    fun railDock() {
        launch()
        compose.waitForIdle()
        scenario!!.onActivity { val m = model(); m.apps.take(3).forEach { a -> m.toggleDock(a.key) } }
        compose.waitForIdle()
        shot("rail-dock")
        // すべてのアプリの上にも同じものを
        compose.onNodeWithContentDescription("START", substring = true).performClick()
        compose.mainClock.advanceTimeBy(600)
        shot("apps-pinned")
    }

    /** ピン留めの初め：LINE・Google の音声検索・MemoHatch の音声メモ（入っているので出る）。 */
    @Test
    @Config(qualifiers = "w360dp-h860dp-xxhdpi")
    fun defaultPins() {
        launch { app ->
            val pm = shadowOf(app.packageManager)
            val launcher = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
            listOf("jp.naver.line.android" to "LINE", "com.google.android.googlequicksearchbox" to "Google", "jp.yaegaki.theta.memo" to "Memo Hatch").forEach { (pkg, label) ->
                val info = ApplicationInfo().apply { packageName = pkg; nonLocalizedLabel = label }
                pm.addResolveInfoForIntent(launcher, ResolveInfo().apply {
                    activityInfo = ActivityInfo().apply { packageName = pkg; name = "$pkg.Main"; applicationInfo = info; nonLocalizedLabel = label }
                    nonLocalizedLabel = label
                })
            }
        }
        compose.waitForIdle()
        org.junit.Assert.assertEquals(listOf("LINE", "音声検索", "音声メモ"), model().dockItems().map { jp.yaegaki.theta.home.ui.dockLabel(it.second) })
        compose.onNodeWithContentDescription("START", substring = true).performClick()
        compose.mainClock.advanceTimeBy(600)
        shot("default-pins")
    }
}
