package jp.yaegaki.theta.home.notify

import android.content.ComponentName
import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.Handler
import android.os.Looper
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** 再生中の曲 1 つ。position は updatedAt の時点の位置（再生中なら、そこから進んでいる）。 */
data class NowPlaying(
    val packageName: String,
    val title: String,
    val artist: String,
    val album: String,
    val art: Bitmap?,
    val artUri: String?,
    val playing: Boolean,
    val duration: Long,
    val position: Long,
    val speed: Float,
    val updatedAt: Long,
) {
    /** 今の位置（ミリ秒）。 */
    fun positionNow(now: Long = android.os.SystemClock.elapsedRealtime()): Long =
        if (!playing || position < 0) position.coerceAtLeast(0)
        else (position + ((now - updatedAt) * speed).toLong()).let { if (duration > 0) it.coerceAtMost(duration) else it }
}

/**
 * 音楽アプリの再生（メディアセッション）を見る：曲名・アーティスト・アルバム・ジャケット・再生中か、と操作。
 * 通知へのアクセスを許可していると、どの音楽アプリのものでも読める。Theta Music を先に、なければ再生中のもの、なければ最後に使ったもの。
 */
object MediaWatch {
    const val THETA_MUSIC = "com.thetahatch.theta_music"

    private val state = MutableStateFlow<NowPlaying?>(null)
    val now: StateFlow<NowPlaying?> = state

    private var manager: MediaSessionManager? = null
    private var controllers: List<MediaController> = emptyList()
    private var current: MediaController? = null
    private val main = Handler(Looper.getMainLooper())

    private val callback = object : MediaController.Callback() {
        override fun onMetadataChanged(metadata: MediaMetadata?) = pick()
        override fun onPlaybackStateChanged(s: PlaybackState?) = pick()
        override fun onSessionDestroyed() = pick()
    }

    private val sessions = MediaSessionManager.OnActiveSessionsChangedListener { list -> watch(list.orEmpty()) }

    /** 見始める（通知へのアクセスが無ければ何もしない）。何度呼んでもよい。 */
    fun start(context: Context) {
        val app = context.applicationContext
        if (!HatchNotificationListener.isEnabled(app)) return
        val comp = ComponentName(app, HatchNotificationListener::class.java)
        runCatching {
            val m = manager ?: app.getSystemService(MediaSessionManager::class.java)!!.also {
                it.addOnActiveSessionsChangedListener(sessions, comp, main)
                manager = it
            }
            watch(m.getActiveSessions(comp))
        }
    }

    private fun watch(list: List<MediaController>) {
        controllers.forEach { runCatching { it.unregisterCallback(callback) } }
        controllers = list
        list.forEach { runCatching { it.registerCallback(callback, main) } }
        pick()
    }

    private fun pick() {
        val live = controllers.filter { it.metadata != null }
        val playing = live.filter { it.playbackState?.state == PlaybackState.STATE_PLAYING }
        val c = playing.firstOrNull { it.packageName == THETA_MUSIC } ?: playing.firstOrNull()
            ?: live.firstOrNull { it.packageName == THETA_MUSIC } ?: live.firstOrNull()
        current = c
        state.value = c?.let(::read)
    }

    private fun read(c: MediaController): NowPlaying {
        val m = c.metadata
        val s = c.playbackState
        fun text(vararg keys: String) = keys.firstNotNullOfOrNull { k -> m?.getString(k)?.takeIf { it.isNotBlank() } }.orEmpty()
        val art = m?.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART) ?: m?.getBitmap(MediaMetadata.METADATA_KEY_ART) ?: m?.getBitmap(MediaMetadata.METADATA_KEY_DISPLAY_ICON)
        return NowPlaying(
            packageName = c.packageName,
            title = text(MediaMetadata.METADATA_KEY_TITLE, MediaMetadata.METADATA_KEY_DISPLAY_TITLE),
            artist = text(MediaMetadata.METADATA_KEY_ARTIST, MediaMetadata.METADATA_KEY_ALBUM_ARTIST, MediaMetadata.METADATA_KEY_DISPLAY_SUBTITLE),
            album = text(MediaMetadata.METADATA_KEY_ALBUM),
            art = art,
            artUri = if (art == null) text(MediaMetadata.METADATA_KEY_ALBUM_ART_URI, MediaMetadata.METADATA_KEY_ART_URI, MediaMetadata.METADATA_KEY_DISPLAY_ICON_URI).ifEmpty { null } else null,
            playing = s?.state == PlaybackState.STATE_PLAYING,
            duration = m?.getLong(MediaMetadata.METADATA_KEY_DURATION) ?: 0,
            position = s?.position ?: 0,
            speed = s?.playbackSpeed?.takeIf { it > 0f } ?: 1f,
            updatedAt = s?.lastPositionUpdateTime ?: android.os.SystemClock.elapsedRealtime(),
        )
    }

    fun playPause() {
        val c = current ?: return
        if (c.playbackState?.state == PlaybackState.STATE_PLAYING) c.transportControls.pause() else c.transportControls.play()
    }

    fun next() { current?.transportControls?.skipToNext() }
    fun previous() { current?.transportControls?.skipToPrevious() }
    fun seekTo(ms: Long) { current?.transportControls?.seekTo(ms) }

    /** 試験で、見本の曲を入れる。 */
    @androidx.annotation.VisibleForTesting
    internal fun setForTest(n: NowPlaying?) { state.value = n }
}
