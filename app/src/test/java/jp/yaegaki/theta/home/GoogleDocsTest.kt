package jp.yaegaki.theta.home

import androidx.test.core.app.ApplicationProvider
import jp.yaegaki.theta.home.data.BoardItem
import jp.yaegaki.theta.home.data.SavedQuery
import jp.yaegaki.theta.home.data.Store
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** ドライブの「GOOGLE DOCS」（Google のドキュメントなどを最近開いた順）と、それを開く DOCS のタブ。 */
@RunWith(RobolectricTestRunner::class)
class GoogleDocsTest {
    private fun store(): Store = Store(ApplicationProvider.getApplicationContext()).also { it.prefs.edit().clear().commit() }

    @Test
    fun freshInstall() {
        val s = store()
        assertTrue(s.queries().any { it.id == SavedQuery.GOOGLE_DOCS.id })
        val tabs = s.tabs()
        val docs = tabs.first { it.label == "DOCS" }
        val item = s.boards()[docs.id]!!.single()
        assertEquals(BoardItem.FOLDER, item.type)
        assertEquals("gdrive:q-gdocs:GOOGLE DOCS", item.ref)
        // フォルダのタブ（WORK・PRIVATE・PHOTO）の後ろ
        assertEquals(3, tabs.indexOf(docs))
        // 読み直しても増えない
        assertEquals(1, s.tabs().count { it.label == "DOCS" })
    }

    @Test
    fun addedOnceForExistingUsers() {
        val s = store()
        // 0.37 までの人：タブも検索フォルダも保存済みで、DOCS は無い
        s.saveQueries(SavedQuery.defaults().filter { it.id != SavedQuery.GOOGLE_DOCS.id })
        s.prefs.edit().putBoolean("queriesSeeded", true).commit()
        s.saveTabs(Store.defaultTabs().filter { it.id != "gdocs" })
        s.tabs()
        assertTrue(s.queries().any { it.id == SavedQuery.GOOGLE_DOCS.id })
        assertEquals(1, s.tabs().count { it.label == "DOCS" })
        // 外したら戻さない
        s.saveTabs(s.tabs().filter { it.label != "DOCS" })
        s.saveQueries(s.queries().filter { it.id != SavedQuery.GOOGLE_DOCS.id })
        assertEquals(0, s.tabs().count { it.label == "DOCS" })
        assertTrue(s.queries().none { it.id == SavedQuery.GOOGLE_DOCS.id })
    }

    @Test
    fun driveQuery() {
        val q = SavedQuery.GOOGLE_DOCS.driveQuery()
        assertTrue(q.contains("application/vnd.google-apps.document"))
        assertTrue(q.contains("application/vnd.google-apps.spreadsheet"))
        assertTrue(q.contains("application/vnd.google-apps.presentation"))
        assertEquals("viewed", SavedQuery.fromJson(SavedQuery.GOOGLE_DOCS.toJson()).order)
    }
}
