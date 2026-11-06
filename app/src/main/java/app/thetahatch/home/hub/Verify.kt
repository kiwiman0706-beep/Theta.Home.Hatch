package app.thetahatch.home.hub

import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import java.io.File
import java.security.MessageDigest

/** APK・入っているアプリの、確かめるのに要ること。signers は証明書の SHA-256（小文字の 16 進）。 */
data class ApkFacts(val packageName: String, val versionName: String, val versionCode: Long, val signers: Set<String>)

enum class InstallKind {
    /** 入っているアプリの更新 */
    UPDATE,
    /** 入っていないアプリ（移行の新しい版も） */
    NEW,
    /** 移行の橋渡し版（古い ID・古い鍵） */
    BRIDGE,
}

sealed class Verdict {
    data class Ok(val kind: InstallKind) : Verdict()
    data class Bad(val reason: String) : Verdict()
}

/**
 * 入れてよいかを決める（spec §6.1「取得と確認」）。共通の鍵でない APK は、移行の橋渡し版のほかは入れない。
 */
object Verify {
    /** シリーズ共通の鍵の SHA-256（secrets/README.md）。 */
    const val SERIES_KEY = "f47740d8549bc1c37feb00b18780d80b52758b60252ba0d2217d83ab3f012a23"

    fun judge(app: CatalogApp, apk: ApkFacts, installedOf: (String) -> ApkFacts?): Verdict {
        if (apk.packageName != app.targetPackage && apk.packageName != app.currentPackage)
            return Verdict.Bad("パッケージ名が違います（${apk.packageName}）")
        val series = SERIES_KEY in apk.signers
        val installed = installedOf(apk.packageName)
        if (installed == null) {
            return if (series) Verdict.Ok(InstallKind.NEW) else Verdict.Bad("シリーズ共通の鍵で署名されていません")
        }
        if (apk.versionCode <= installed.versionCode) return Verdict.Bad("入っている版（${installed.versionName}）より新しくありません")
        if (apk.signers != installed.signers) return Verdict.Bad("署名が入っているアプリと違います")
        if (series) return Verdict.Ok(InstallKind.UPDATE)
        val oldId = app.currentPackage != null && app.currentPackage != app.targetPackage && apk.packageName == app.currentPackage
        return if (app.bridge && oldId) Verdict.Ok(InstallKind.BRIDGE) else Verdict.Bad("シリーズ共通の鍵で署名されていません")
    }

    fun archive(context: Context, apk: File): ApkFacts? {
        @Suppress("DEPRECATION")
        val info = context.packageManager.getPackageArchiveInfo(apk.path, PackageManager.GET_SIGNING_CERTIFICATES) ?: return null
        return facts(info)
    }

    fun installed(context: Context, pkg: String): ApkFacts? = runCatching {
        facts(context.packageManager.getPackageInfo(pkg, PackageManager.GET_SIGNING_CERTIFICATES))
    }.getOrNull()

    private fun facts(info: PackageInfo): ApkFacts {
        val signers = info.signingInfo?.let { s ->
            (if (s.hasMultipleSigners()) s.apkContentsSigners else s.signingCertificateHistory?.takeLast(1)?.toTypedArray())
                ?.map { sha256(it.toByteArray()) }?.toSet()
        } ?: emptySet()
        @Suppress("DEPRECATION")
        val code = if (android.os.Build.VERSION.SDK_INT >= 28) info.longVersionCode else info.versionCode.toLong()
        return ApkFacts(info.packageName, info.versionName ?: "", code, signers)
    }

    fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
}
