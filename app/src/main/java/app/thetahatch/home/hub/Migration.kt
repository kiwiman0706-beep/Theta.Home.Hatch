package app.thetahatch.home.hub

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File

/**
 * 移行（docs/MIGRATION.md §3）の 1 アプリ分の段階。
 * 橋渡し版のあるアプリ：橋渡し版に更新 → 書き出し → 新しい版を入れる → 読み込み → 古いアプリを削除。
 * それ以外：新しい版を入れる → 古いアプリを削除。
 */
enum class MigrationStep { BRIDGE, EXPORT, INSTALL_NEW, IMPORT, DELETE_OLD, DONE }

object Migration {
    const val ACTION_EXPORT = "app.thetahatch.action.EXPORT"
    const val ACTION_IMPORT = "app.thetahatch.action.IMPORT"

    /** 今どの段階か。hasExport：古いアプリに書き出しの画面がある（＝橋渡し版になっている）。 */
    fun step(a: CatalogApp, oldInstalled: Boolean, newInstalled: Boolean, hasExport: Boolean, exported: Boolean, imported: Boolean): MigrationStep = when {
        !oldInstalled -> MigrationStep.DONE
        a.bridge && !exported && !newInstalled && !hasExport -> MigrationStep.BRIDGE
        a.bridge && !exported -> MigrationStep.EXPORT
        !newInstalled -> MigrationStep.INSTALL_NEW
        a.bridge && !imported -> MigrationStep.IMPORT
        else -> MigrationStep.DELETE_OLD
    }

    fun step(context: Context, a: CatalogApp, store: app.thetahatch.home.data.Store): MigrationStep {
        val v = UpdateHub.versionOf(context)
        val old = a.currentPackage ?: return MigrationStep.DONE
        val new = a.targetPackage ?: return MigrationStep.DONE
        if (old == new) return MigrationStep.DONE
        return step(a, v(old) != null, v(new) != null, exportIntent(context, a) != null, exportFile(context, a).exists(), store.migrated(a.id))
    }

    fun exportIntent(context: Context, a: CatalogApp): Intent? {
        val i = Intent(ACTION_EXPORT).setPackage(a.currentPackage)
        return i.takeIf { context.packageManager.resolveActivity(i, 0) != null }
    }

    fun importIntent(context: Context, a: CatalogApp): Intent? {
        val f = exportFile(context, a)
        if (!f.exists()) return null
        val uri: Uri = FileProvider.getUriForFile(context, "${context.packageName}.files", f)
        val i = Intent(ACTION_IMPORT).setPackage(a.targetPackage).setDataAndType(uri, "application/zip")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        return i.takeIf { context.packageManager.resolveActivity(i, 0) != null }
    }

    fun exportFile(context: Context, a: CatalogApp) = File(File(context.filesDir, "Migrate").apply { mkdirs() }, "${a.id}.zip")

    /** 書き出しの結果（URI）を写す。 */
    fun keep(context: Context, a: CatalogApp, uri: Uri): Boolean = runCatching {
        context.contentResolver.openInputStream(uri)!!.use { input -> exportFile(context, a).outputStream().use { input.copyTo(it) } }
        true
    }.getOrDefault(false)

    fun deleteIntent(a: CatalogApp) = Intent(Intent.ACTION_DELETE, Uri.parse("package:${a.currentPackage}"))
}
