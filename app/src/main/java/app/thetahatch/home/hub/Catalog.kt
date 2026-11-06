package app.thetahatch.home.hub

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * シリーズのアプリ 1 つ（Theta.Hatch.Series の catalog/apps.json の 1 行）。
 * currentPackage は今の ID、targetPackage は揃えた後の ID（移行が済めば同じ）。
 */
data class CatalogApp(
    val id: String,
    val name: String,
    val nickname: String?,
    val repo: String?,
    val currentPackage: String?,
    val targetPackage: String?,
    val android: Boolean,
    val bridge: Boolean,
    val updateHub: Boolean,
    val status: String?,
) {
    val short: String get() = SHORT[id] ?: id.take(2).uppercase()
    val summary: String get() = SUMMARY[id] ?: nickname ?: ""
    val releasesUrl: String? get() = repo?.let { "https://github.com/${Catalog.OWNER}/$it/releases" }

    /** 端末に入っている方の ID（新しい ID を先に見る）。 */
    fun installedPackage(versionOf: (String) -> String?): String? =
        targetPackage?.takeIf { versionOf(it) != null } ?: currentPackage?.takeIf { versionOf(it) != null }

    /** 古い ID が入っている（新しい版を入れて、古いアプリを消す必要がある）。 */
    fun needsMigration(versionOf: (String) -> String?): Boolean =
        currentPackage != null && targetPackage != null && currentPackage != targetPackage && versionOf(currentPackage) != null

    /** 入れるときの ID（新しい ID があればそちら）。 */
    val packageName: String? get() = targetPackage ?: currentPackage

    companion object {
        private val SHORT = mapOf(
            "memo" to "MM", "music" to "MU", "kidshome" to "KH", "travel" to "TR", "device" to "DV", "item" to "IT",
            "mensetsu" to "MS", "label" to "LB", "doorphoto" to "DP", "kamigata" to "KG", "oekaki" to "OE",
            "book" to "DB", "meishi" to "MC", "desk" to "DH", "hitsudan" to "HD", "home" to "HM", "photo" to "PH",
            "chirashi" to "CH",
        )
        private val SUMMARY = mapOf(
            "memo" to "付箋の壁のメモ（声・写真・手書き）", "music" to "NAS の音楽を再生（iPod 風・HUD）",
            "kidshome" to "子ども用タブレットのホーム", "travel" to "旅の計画としおり、思い出", "device" to "PC・電子機器の台帳とラベル",
            "item" to "持ち物と思い出", "mensetsu" to "面接の管理", "label" to "音声でテプラのラベル印刷",
            "doorphoto" to "玄関ドアの写真年表", "kamigata" to "髪型を角度別の写真で残す", "oekaki" to "描いた絵がゲームで動く",
            "book" to "NAS の本棚", "meishi" to "名刺管理", "desk" to "PC 用ランチャーバー（Windows・Mac）",
            "hitsudan" to "診察室の筆談・翻訳表示（PC で動かす）", "home" to "このホーム", "photo" to "NAS の写真",
            "chirashi" to "チラシの読み取り",
        )
    }
}

/**
 * シリーズのアプリの一覧。APK に同梱した assets/apps.json と、ネットで読んだ最新（filesDir に保存）の新しい方を使う。
 */
object Catalog {
    const val OWNER = "kiwiman0706-beep"
    const val URL = "https://raw.githubusercontent.com/$OWNER/Theta.Hatch.Series/HEAD/catalog/apps.json"
    private const val CACHE = "catalog.json"

    private val _apps = MutableStateFlow<List<CatalogApp>>(emptyList())
    val apps: StateFlow<List<CatalogApp>> = _apps

    /** 一覧に出すもの（構想中・設計中・試作は出さない）。 */
    val shown: List<CatalogApp> get() = _apps.value.filter { it.status == null }

    /** Android 版がある（更新・移行の対象）。 */
    val android: List<CatalogApp> get() = _apps.value.filter { it.android && it.repo != null && it.packageName != null }

    fun byPackage(pkg: String): CatalogApp? = _apps.value.firstOrNull { it.currentPackage == pkg || it.targetPackage == pkg }

    /** シリーズのアプリの ID（古い ID も含む）。 */
    val packages: Set<String> get() = _apps.value.flatMap { listOfNotNull(it.currentPackage, it.targetPackage) }.toSet()

    fun load(context: Context) {
        if (_apps.value.isNotEmpty()) return
        val bundled = runCatching { context.assets.open("apps.json").bufferedReader().use { it.readText() } }.getOrNull()
        val cached = runCatching { File(context.filesDir, CACHE).takeIf { it.exists() }?.readText() }.getOrNull()
        _apps.value = newer(bundled, cached)?.let { parse(it) } ?: emptyList()
    }

    /** ネットから読み直す。読めなければ今のまま。 */
    suspend fun refresh(context: Context) = withContext(Dispatchers.IO) {
        load(context)
        val text = runCatching {
            val c = URL(URL).openConnection() as HttpURLConnection
            c.connectTimeout = 10_000
            c.readTimeout = 15_000
            c.setRequestProperty("User-Agent", "ThetaHomeHatch")
            if (c.responseCode !in 200..299) null else c.inputStream.bufferedReader().use { it.readText() }
        }.getOrNull() ?: return@withContext
        val list = runCatching { parse(text) }.getOrNull()?.takeIf { it.isNotEmpty() } ?: return@withContext
        runCatching { File(context.filesDir, CACHE).writeText(text) }
        _apps.value = list
    }

    /** "updated"（日付）が新しい方。 */
    internal fun newer(a: String?, b: String?): String? {
        fun date(s: String?) = s?.let { runCatching { JSONObject(it).optString("updated") }.getOrNull() } ?: ""
        if (a == null) return b
        if (b == null) return a
        return if (date(b) >= date(a)) b else a
    }

    fun parse(text: String): List<CatalogApp> {
        val arr = JSONObject(text).getJSONArray("apps")
        return (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            val cur = o.optJSONObject("current")
            val tgt = o.optJSONObject("target")
            val platforms = o.optJSONArray("platforms")
            val android = platforms != null && (0 until platforms.length()).any { platforms.optString(it) == "android" }
            fun JSONObject?.str(k: String) = this?.takeUnless { it.isNull(k) }?.optString(k)?.takeIf { it.isNotBlank() }
            CatalogApp(
                id = o.getString("id"),
                name = o.optString("name"),
                nickname = o.str("nickname"),
                repo = tgt.str("repo") ?: cur.str("repo"),
                currentPackage = cur.str("packageName"),
                targetPackage = tgt.str("packageName"),
                android = android,
                bridge = o.optJSONObject("migration")?.optBoolean("bridge") == true,
                updateHub = o.optBoolean("updateHub"),
                status = o.str("status"),
            )
        }
    }

    /** 試験用：一覧を差し替える。 */
    internal fun setForTest(list: List<CatalogApp>) { _apps.value = list }
}
