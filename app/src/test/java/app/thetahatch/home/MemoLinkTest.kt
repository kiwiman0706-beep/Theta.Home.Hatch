package app.thetahatch.home

import android.content.ComponentName
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ActivityInfo
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/** MemoHatch へのつなぎ：一覧や catalog に頼らず、入っていれば必ず MemoHatch の画面を名指しして開く。 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class MemoLinkTest {
    private val app = ApplicationProvider.getApplicationContext<HomeApp>()
    private val pm = shadowOf(app.packageManager)
    private val main = ComponentName(HomeModel.MEMO_NEW, "app.thetahatch.memo.MainActivity")

    private fun installMemo(filters: Boolean) {
        pm.installPackage(PackageInfo().apply {
            packageName = HomeModel.MEMO_NEW
            applicationInfo = ApplicationInfo().apply { packageName = HomeModel.MEMO_NEW }
        })
        pm.addOrUpdateActivity(ActivityInfo().apply { packageName = HomeModel.MEMO_NEW; name = main.className; exported = true })
        pm.addIntentFilterForActivity(main, IntentFilter(Intent.ACTION_MAIN).apply { addCategory(Intent.CATEGORY_LAUNCHER) })
        if (filters) {
            pm.addIntentFilterForActivity(main, IntentFilter("app.thetahatch.memo.NEW").apply { addCategory(Intent.CATEGORY_DEFAULT) })
            pm.addIntentFilterForActivity(main, IntentFilter(HomeModel.SCAN).apply { addCategory(Intent.CATEGORY_DEFAULT) })
        }
    }

    @Test
    fun notInstalled() {
        val m = HomeModel(app)
        assertFalse(m.memoInstalled())
        val started = mutableListOf<Intent>()
        assertFalse(m.memoNew("text") { started += it })
        assertFalse(m.memoCapture { started += it })
        assertTrue(started.isEmpty())
    }

    @Test
    fun newAndScanReachMemo() {
        installMemo(filters = true)
        val m = HomeModel(app)
        assertTrue(m.memoInstalled())
        val started = mutableListOf<Intent>()
        assertTrue(m.memoNew("list") { started += it })
        assertTrue(m.memoCapture { started += it })
        val (n, s) = started
        assertEquals("app.thetahatch.memo.NEW", n.action)
        assertEquals(main, n.component)
        assertEquals("list", n.getStringExtra("type"))
        assertEquals("list", n.getStringExtra("new"))
        assertEquals(HomeModel.SCAN, s.action)
        assertEquals(main, s.component)
        assertEquals("camera", s.getStringExtra("mode"))
    }

    @Test
    fun withoutFiltersStillNamesTheLauncher() {
        installMemo(filters = false)
        val m = HomeModel(app)
        val started = mutableListOf<Intent>()
        assertTrue(m.memoNew("ink") { started += it })
        assertEquals(main, started.single().component)
        assertEquals("app.thetahatch.memo.NEW", started.single().action)
        assertNull(started.single().`package`?.takeIf { it != HomeModel.MEMO_NEW })
    }
}
