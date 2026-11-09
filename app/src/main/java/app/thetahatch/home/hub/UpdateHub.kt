package app.thetahatch.home.hub

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.Uri
import android.provider.Settings
import androidx.core.app.NotificationCompat
import app.thetahatch.home.HomeApp
import app.thetahatch.home.MainActivity
import app.thetahatch.home.R
import app.thetahatch.home.ui.isNewer
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

data class Release(
    val tag: String,
    val apkName: String,
    val apkUrl: String,
    /** GitHub が付ける "sha256:…" */
    val digest: String?,
    val sumsUrl: String?,
    val body: String = "",
) {
    /** "v0.1.1" → "0.1.1" */
    val version: String get() = tag.removePrefix("v")
}

/** 一覧の 1 行の状態。 */
data class HatchStatus(
    val installed: String? = null,
    val latest: Release? = null,
    val checking: Boolean = false,
    val progress: Float? = null,
    /** 取得して照合まで済んだ APK（モバイル回線でなければ先に取っておく） */
    val ready: File? = null,
    val installing: Boolean = false,
    val error: String? = null,
    /** 直前の更新の結果（「入れました」など） */
    val result: String? = null,
)

/**
 * 更新のハブ（spec §6.1）。シリーズの Android アプリの新しい版を確かめ、取得・照合して PackageInstaller で入れる。
 * Home と KidsHome の両方があるときは、既定のホームアプリになっている方が受け持つ（どちらでもなければ Home）。
 */
object UpdateHub {
    const val ACTION_UPDATE = "app.thetahatch.action.UPDATE"
    const val ACTION_BUSY = "app.thetahatch.action.UPDATE_BUSY"
    const val PERMISSION = "app.thetahatch.permission.SHARE"
    val KIDS_HUBS = listOf("app.thetahatch.kidshome", "com.theta.kidshome")
    private const val API = "https://api.github.com/repos"
    private const val CHANNEL = "hub"

    private val _status = MutableStateFlow<Map<String, HatchStatus>>(emptyMap())
    val status: StateFlow<Map<String, HatchStatus>> = _status

    /** 画面が前にあるか（確認画面をすぐ出すか、通知にするか）。 */
    @Volatile var foreground = false

    /** 相手のハブが更新中のアプリ → いつまで。 */
    private val busyByOther = mutableMapOf<String, Long>()
    private val installLock = Mutex()
    private val pending = mutableMapOf<Int, CompletableDeferred<Pair<Int, String>>>()

    fun get(id: String) = _status.value[id] ?: HatchStatus()
    private fun put(id: String, f: (HatchStatus) -> HatchStatus) {
        synchronized(this) { _status.value = _status.value + (id to f(get(id))) }
    }

    fun versionOf(context: Context): (String) -> String? = { pkg ->
        try { context.packageManager.getPackageInfo(pkg, 0).versionName ?: "" } catch (_: PackageManager.NameNotFoundException) { null }
    }

    // ---- 受け持ち ----

    /** 既定のホームアプリのパッケージ（決まっていなければ null）。 */
    fun defaultHome(context: Context): String? {
        val i = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        val r = context.packageManager.resolveActivity(i, PackageManager.MATCH_DEFAULT_ONLY) ?: return null
        return r.activityInfo?.packageName?.takeIf { it != "android" }
    }

    /** このハブが更新を受け持つか：KidsHome が既定のホームならそちらに任せる。 */
    fun ownerFor(defaultHome: String?): Boolean = defaultHome == null || defaultHome !in KIDS_HUBS

    fun isOwner(context: Context) = ownerFor(defaultHome(context))

    fun markBusyByOther(pkg: String, busy: Boolean) {
        synchronized(busyByOther) { if (busy) busyByOther[pkg] = System.currentTimeMillis() + 10 * 60_000 else busyByOther.remove(pkg) }
    }

    private fun busyElsewhere(pkg: String) = synchronized(busyByOther) { (busyByOther[pkg] ?: 0) > System.currentTimeMillis() }

    private fun tellOthers(context: Context, pkg: String, busy: Boolean) {
        KIDS_HUBS.forEach { hub ->
            runCatching {
                context.sendBroadcast(
                    Intent(ACTION_BUSY).setPackage(hub).putExtra("packageName", pkg).putExtra("busy", busy).putExtra("from", context.packageName),
                    PERMISSION,
                )
            }
        }
    }

    // ---- 確認 ----

    /** releases/latest（下書き・試験版は含まれない）。 */
    suspend fun latest(repo: String): Release? = withContext(Dispatchers.IO) {
        fetch("$API/${Catalog.OWNER}/$repo/releases/latest")?.let { parseRelease(JSONObject(it)) }
    }

    /** 公開した版を新しい順に（試験版・下書きは除く）。移行の橋渡し版を探すのに使う。 */
    suspend fun releases(repo: String): List<Release> = withContext(Dispatchers.IO) {
        val text = fetch("$API/${Catalog.OWNER}/$repo/releases?per_page=15") ?: return@withContext emptyList()
        val arr = JSONArray(text)
        (0 until arr.length()).map { arr.getJSONObject(it) }
            .filter { !it.optBoolean("draft") && !it.optBoolean("prerelease") }
            .mapNotNull { parseRelease(it) }
    }

    internal fun parseRelease(o: JSONObject): Release? {
        val assets = o.optJSONArray("assets") ?: return null
        var apk: JSONObject? = null
        var sums: String? = null
        for (i in 0 until assets.length()) {
            val a = assets.getJSONObject(i)
            val name = a.optString("name")
            if (name.endsWith(".apk") && apk == null) apk = a
            if (name.contains("SHA256", ignoreCase = true)) sums = a.optString("browser_download_url")
        }
        apk ?: return null
        return Release(
            o.optString("tag_name"), apk.getString("name"), apk.getString("browser_download_url"),
            apk.optString("digest").takeIf { it.startsWith("sha256:") }, sums, o.optString("body"),
        )
    }

    /** 入っている版を読み直す（ネットは使わない）。 */
    fun refreshInstalled(context: Context) {
        val v = versionOf(context)
        Catalog.shown.forEach { a -> put(a.id) { it.copy(installed = a.installedPackage(v)?.let(v)) } }
    }

    /** 入っているアプリの新しい版を確かめる。only を渡せばその 1 つだけ。 */
    suspend fun check(context: Context, only: String? = null) {
        Catalog.refresh(context)
        refreshInstalled(context)
        Catalog.android.filter { only == null || it.id == only }.forEach { a ->
            put(a.id) { it.copy(checking = true, error = null) }
            val r = runCatching { latest(a.repo!!) }
            put(a.id) { s ->
                val keep = s.ready?.takeIf { r.getOrNull()?.apkName == it.name }
                s.copy(checking = false, latest = r.getOrNull(), ready = keep, error = r.exceptionOrNull()?.message)
            }
        }
    }

    fun hasNewer(a: CatalogApp, s: HatchStatus) = s.installed != null && s.latest != null && isNewer(s.latest.version, s.installed)

    // ---- 取得と確認 ----

    suspend fun download(context: Context, release: Release, onProgress: (Float) -> Unit = {}): File = withContext(Dispatchers.IO) {
        val dir = File(context.cacheDir, "hatch").apply { mkdirs() }
        val out = File(dir, release.apkName.replace('/', '_'))
        val conn = open(release.apkUrl)
        val total = conn.contentLengthLong
        val md = MessageDigest.getInstance("SHA-256")
        conn.inputStream.use { input ->
            out.outputStream().use { o ->
                val buf = ByteArray(64 * 1024)
                var done = 0L
                while (true) {
                    val n = input.read(buf)
                    if (n < 0) break
                    o.write(buf, 0, n)
                    md.update(buf, 0, n)
                    done += n
                    if (total > 0) onProgress(done.toFloat() / total)
                }
            }
        }
        val hash = md.digest().joinToString("") { "%02x".format(it) }
        val expected = release.digest?.removePrefix("sha256:")?.lowercase() ?: release.sumsUrl?.let { fetch(it) }?.let { sums ->
            sums.lineSequence().map { it.trim().split(Regex("\\s+")) }
                .firstOrNull { it.size >= 2 && it.last().removePrefix("*").endsWith(release.apkName) }?.first()?.lowercase()
        }
        if (expected == null || expected != hash) {
            out.delete()
            throw IOException(if (expected == null) "SHA-256 が見つかりません" else "SHA-256 が一致しません")
        }
        out
    }

    /** 取得して、入れてよいか確かめる。だめなら IOException（理由つき）。 */
    suspend fun prepare(context: Context, a: CatalogApp, release: Release, onProgress: (Float) -> Unit = {}): Pair<File, InstallKind> {
        val file = download(context, release, onProgress)
        val facts = Verify.archive(context, file) ?: run { file.delete(); throw IOException("APK を開けません") }
        return when (val v = Verify.judge(a, facts) { Verify.installed(context, it) }) {
            is Verdict.Ok -> file to v.kind
            is Verdict.Bad -> { file.delete(); throw IOException(v.reason) }
        }
    }

    // ---- 入れる ----

    fun permissionIntent(context: Context): Intent? =
        if (context.packageManager.canRequestPackageInstalls()) null
        else Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}"))

    /** PackageInstaller で入れて、結果（STATUS_*・説明）を待つ。同時には 1 つずつ。 */
    suspend fun install(context: Context, apk: File, pkg: String): Pair<Int, String> = installLock.withLock {
        if (busyElsewhere(pkg)) return@withLock PackageInstaller.STATUS_FAILURE to "もう 1 つのハブが更新中です"
        tellOthers(context, pkg, true)
        try {
            val installer = context.packageManager.packageInstaller
            val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL)
            params.setAppPackageName(pkg)
            if (android.os.Build.VERSION.SDK_INT >= 31) params.setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED)
            val id = installer.createSession(params)
            val done = CompletableDeferred<Pair<Int, String>>()
            synchronized(pending) { pending[id] = done }
            withContext(Dispatchers.IO) {
                installer.openSession(id).use { session ->
                    session.openWrite("base.apk", 0, apk.length()).use { o ->
                        apk.inputStream().use { it.copyTo(o) }
                        session.fsync(o)
                    }
                    val pi = PendingIntent.getBroadcast(
                        context, id,
                        Intent(context, InstallResultReceiver::class.java).putExtra("pkg", pkg),
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE,
                    )
                    session.commit(pi.intentSender)
                }
            }
            // 利用者の確認待ちは長くなることがある。30 分で見切る（通知は残る）
            withTimeoutOrNull(30 * 60_000L) { done.await() } ?: (PackageInstaller.STATUS_PENDING_USER_ACTION to "確認待ち")
        } finally {
            tellOthers(context, pkg, false)
        }
    }

    internal fun onResult(context: Context, id: Int, status: Int, message: String, confirm: Intent?) {
        if (status == PackageInstaller.STATUS_PENDING_USER_ACTION && confirm != null) {
            if (foreground) context.startActivity(confirm.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            else notify(context, id, "更新の確認", "タップして更新を続けます", PendingIntent.getActivity(context, id, confirm, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT))
            return
        }
        synchronized(pending) { pending.remove(id) }?.complete(status to message)
    }

    /** 1 つ更新する（取得済みならそれを使う）。結果を状態に書く。 */
    suspend fun update(context: Context, a: CatalogApp): Boolean {
        // 入れる直前に最新を読み直す（前に確かめた古い版を入れようとして「新しくありません」になるのを防ぐ）
        val fresh = a.repo?.let { runCatching { latest(it) }.getOrNull() }
        if (fresh != null) put(a.id) { it.copy(latest = fresh, ready = it.ready?.takeIf { f -> f.name == fresh.apkName }) }
        val s = get(a.id)
        val r = s.latest ?: return false
        put(a.id) { it.copy(error = null, result = null) }
        val prepared = runCatching {
            s.ready?.takeIf { it.exists() }?.let { f ->
                val facts = Verify.archive(context, f) ?: throw IOException("APK を開けません")
                when (val v = Verify.judge(a, facts) { Verify.installed(context, it) }) {
                    is Verdict.Ok -> f to v.kind
                    is Verdict.Bad -> throw IOException(v.reason)
                }
            } ?: run {
                put(a.id) { it.copy(progress = 0f) }
                var shown = 0
                prepare(context, a, r) { p -> val pct = (p * 100).toInt(); if (pct > shown) { shown = pct; put(a.id) { it.copy(progress = p) } } }
            }
        }
        put(a.id) { it.copy(progress = null) }
        val (file, _) = prepared.getOrElse { e -> put(a.id) { it.copy(ready = null, error = e.message) }; return false }
        val facts = Verify.archive(context, file)
        put(a.id) { it.copy(installing = true) }
        val (status, msg) = runCatching { install(context, file, facts?.packageName ?: a.packageName!!) }
            .getOrElse { PackageInstaller.STATUS_FAILURE to (it.message ?: "") }
        val ok = status == PackageInstaller.STATUS_SUCCESS
        put(a.id) {
            it.copy(
                installing = false, ready = if (ok) null else it.ready,
                installed = if (ok) r.version else it.installed,
                result = if (ok) "${r.version} を入れました" else null,
                error = if (ok) null else if (status == PackageInstaller.STATUS_PENDING_USER_ACTION) "確認待ち（通知から続けます）" else "入れられませんでした $msg".trim(),
            )
        }
        if (ok) file.delete()
        return ok
    }

    /**
     * まとめて：確かめて、Wi-Fi（またはモバイルも許可）なら取得し、自動更新がオンなら 1 つずつ入れる。
     * 自分自身は最後（入れると今の処理が終わるので）。
     */
    suspend fun runAll(context: Context, install: Boolean, mobile: Boolean, fromWorker: Boolean): List<String> {
        if (fromWorker && !isOwner(context)) return emptyList()
        check(context)
        val metered = context.getSystemService(ConnectivityManager::class.java)?.isActiveNetworkMetered ?: true
        val todo = Catalog.android.filter { hasNewer(it, get(it.id)) && !it.needsMigration(versionOf(context)) }
            .sortedBy { it.installedPackage(versionOf(context)) == context.packageName }
        val done = mutableListOf<String>()
        if (todo.isEmpty()) return done
        if (metered && !mobile) {
            if (fromWorker) notify(context, 1, "Hatch の新しい版", todo.joinToString("・") { it.name } + "（Wi-Fi で取得します）", openHub(context))
            return done
        }
        for (a in todo) {
            if (!install || permissionIntent(context) != null) {
                // 先に取っておく（入れるのは利用者が押したとき）
                val r = get(a.id).latest ?: continue
                runCatching { prepare(context, a, r) }.onSuccess { (f, _) -> put(a.id) { it.copy(ready = f) } }
                    .onFailure { e -> put(a.id) { it.copy(error = e.message) } }
                continue
            }
            if (update(context, a)) done += a.name
        }
        if (fromWorker) {
            val failed = todo.filter { get(it.id).error != null }
            when {
                failed.isNotEmpty() -> notify(context, 2, "Hatch の更新：入れられなかったもの", failed.joinToString("・") { it.name }, openHub(context))
                done.isNotEmpty() -> notify(context, 2, "Hatch を更新しました", done.joinToString("・"), openHub(context))
                !install || permissionIntent(context) != null -> notify(context, 1, "Hatch の新しい版", todo.joinToString("・") { it.name }, openHub(context))
            }
        }
        return done
    }

    fun openHub(context: Context): PendingIntent = PendingIntent.getActivity(
        context, 0,
        Intent(context, MainActivity::class.java).putExtra(MainActivity.EXTRA_HATCH, "").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    fun notify(context: Context, id: Int, title: String, text: String, tap: PendingIntent) {
        val nm = context.getSystemService(NotificationManager::class.java) ?: return
        nm.createNotificationChannel(NotificationChannel(CHANNEL, "Hatch の更新", NotificationManager.IMPORTANCE_DEFAULT))
        val n = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(tap)
            .setAutoCancel(true)
            .build()
        runCatching { nm.notify("hub", id, n) }
    }

    private fun open(url: String): HttpURLConnection {
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.connectTimeout = 15_000
        conn.readTimeout = 30_000
        conn.setRequestProperty("Accept", "application/vnd.github+json")
        conn.setRequestProperty("User-Agent", "ThetaHomeHatch")
        conn.instanceFollowRedirects = true
        if (conn.responseCode !in 200..299) throw IOException("HTTP ${conn.responseCode}")
        return conn
    }

    private fun fetch(url: String): String? = runCatching { open(url).inputStream.bufferedReader().use { it.readText() } }.getOrNull()
}

/** PackageInstaller の結果。 */
class InstallResultReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)
        val id = intent.getIntExtra(PackageInstaller.EXTRA_SESSION_ID, -1)
        @Suppress("DEPRECATION")
        val confirm = intent.getParcelableExtra<Intent>(Intent.EXTRA_INTENT)
        UpdateHub.onResult(context, id, status, intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE) ?: "", confirm)
        if (status == PackageInstaller.STATUS_SUCCESS && intent.getStringExtra("pkg") != context.packageName) {
            (context.applicationContext as? HomeApp)?.toast("インストールしました")
        }
    }
}

/** もう 1 つのハブ（KidsHome）が更新を始めた・終えた。 */
class BusyReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pkg = intent.getStringExtra("packageName") ?: return
        UpdateHub.markBusyByOther(pkg, intent.getBooleanExtra("busy", false))
    }
}
