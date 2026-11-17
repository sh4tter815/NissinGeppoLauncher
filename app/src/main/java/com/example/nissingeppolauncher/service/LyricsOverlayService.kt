package com.example.nissingeppolauncher.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.media.MediaMetadata
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.Build
import android.os.IBinder
import android.os.SystemClock
import android.provider.Settings
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.view.WindowInsets
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView
import com.example.nissingeppolauncher.data.AppPreferenceManager
import com.example.nissingeppolauncher.data.LyricLine
import com.example.nissingeppolauncher.data.LyricsManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * ステータスバー左側（時計＋通知アイコンの領域）に重ねて、
 * 小さい時計と同期歌詞1行を表示する常駐オーバーレイ。
 * ステータスバーが隠れるイマーシブ中は出さない。
 * 表示はタッチ透過で、通知シェードの操作は邪魔しない。
 */
class LyricsOverlayService : Service() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var windowManager: WindowManager? = null
    private var overlayRoot: LinearLayout? = null
    private var clockView: TextView? = null
    private var lyricView: TextView? = null
    private var attached = false

    private var statusBarVisible = true
    private var currentLine = ""
    private var shownLine: String? = null

    private var trackKey = ""
    private var trackTitle = ""
    private var trackArtist = ""
    private var trackAlbum = ""
    private var trackDurationMs = 0L
    private var lyricLines: List<LyricLine> = emptyList()
    private var lyricOffsetMs = 0L
    private var fetchJob: Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        startForegroundWithNotification()
        if (!Settings.canDrawOverlays(this)) {
            stopSelf()
            return
        }
        buildOverlay()
        startClockLoop()
        startSessionLoops()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_STICKY
    }

    override fun onDestroy() {
        serviceScope.cancel()
        detachOverlay()
        super.onDestroy()
    }

    private fun startForegroundWithNotification() {
        val channelId = "lyrics_overlay"
        val nm = getSystemService(NotificationManager::class.java)
        if (nm.getNotificationChannel(channelId) == null) {
            nm.createNotificationChannel(
                NotificationChannel(channelId, "ステータスバー歌詞", NotificationManager.IMPORTANCE_MIN)
            )
        }
        val notification: Notification = Notification.Builder(this, channelId)
            .setContentTitle("ステータスバー歌詞を表示中")
            .setContentText("時計と同期歌詞1行をステータスバーに重ねています")
            .setSmallIcon(applicationInfo.icon)
            .setOngoing(true)
            .build()
        try {
            if (Build.VERSION.SDK_INT >= 34) {
                startForeground(9201, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
            } else {
                startForeground(9201, notification)
            }
        } catch (e: Exception) {
            try {
                startForeground(9201, notification)
            } catch (e2: Exception) {
                // 前景化に失敗してもオーバーレイ自体は続ける
            }
        }
    }

    private fun dp(value: Float): Int = (value * resources.displayMetrics.density).toInt()

    private fun buildOverlay() {
        val wm = getSystemService(WindowManager::class.java) ?: return
        windowManager = wm
        val statusBarHeight = resources.getIdentifier("status_bar_height", "dimen", "android")
            .takeIf { it > 0 }?.let { resources.getDimensionPixelSize(it) } ?: dp(24f)
        val width = (resources.displayMetrics.widthPixels * 0.60f).toInt()

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = ColorDrawable(Color.argb(165, 6, 8, 14))
            setPadding(dp(10f), 0, dp(8f), 0)
        }
        val clock = TextView(this).apply {
            setTextColor(Color.WHITE)
            textSize = 12.5f
            typeface = Typeface.DEFAULT_BOLD
            includeFontPadding = false
            setShadowLayer(4f, 0f, 0f, Color.argb(160, 0, 0, 0))
        }
        val lyric = TextView(this).apply {
            setTextColor(Color.WHITE)
            textSize = 12f
            includeFontPadding = false
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
            setShadowLayer(7f, 0f, 0f, Color.argb(200, 30, 30, 30))
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                marginStart = dp(8f)
            }
        }
        root.addView(clock)
        root.addView(lyric)
        clockView = clock
        lyricView = lyric
        overlayRoot = root

        root.setOnApplyWindowInsetsListener { _, insets ->
            statusBarVisible = insets.isVisible(WindowInsets.Type.statusBars())
            updateVisibility()
            insets
        }

        val params = WindowManager.LayoutParams(
            width,
            statusBarHeight,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 0
            y = 0
        }
        try {
            wm.addView(root, params)
            attached = true
            root.visibility = View.INVISIBLE
            root.requestApplyInsets()
        } catch (e: Exception) {
            attached = false
        }
    }

    private fun detachOverlay() {
        val root = overlayRoot ?: return
        if (attached) {
            try {
                windowManager?.removeView(root)
            } catch (e: Exception) {
                // 既に外れていたら何もしない
            }
            attached = false
        }
        overlayRoot = null
        clockView = null
        lyricView = null
    }

    private fun updateVisibility() {
        val root = overlayRoot ?: return
        val show = statusBarVisible && currentLine.isNotBlank()
        val target = if (show) View.VISIBLE else View.INVISIBLE
        if (root.visibility != target) root.visibility = target
    }

    private fun startClockLoop() {
        val format = SimpleDateFormat("HH:mm", Locale.JAPAN)
        serviceScope.launch {
            while (true) {
                clockView?.text = format.format(Date())
                delay(5000)
            }
        }
    }

    private fun activeController(): android.media.session.MediaController? {
        return try {
            val msm = getSystemService(MediaSessionManager::class.java) ?: return null
            val comp = ComponentName(this, LauncherMediaListenerService::class.java)
            val controllers = msm.getActiveSessions(comp)
            controllers.firstOrNull {
                it.playbackState?.state == PlaybackState.STATE_PLAYING
            } ?: controllers.firstOrNull()
        } catch (e: Exception) {
            null
        }
    }

    private fun startSessionLoops() {
        // 曲情報のポーリング（変わったら歌詞を取り直す）
        serviceScope.launch {
            while (true) {
                try {
                    val metadata = activeController()?.metadata
                    val title = metadata?.getString(MediaMetadata.METADATA_KEY_TITLE) ?: ""
                    val artist = metadata?.getString(MediaMetadata.METADATA_KEY_ARTIST) ?: ""
                    val album = metadata?.getString(MediaMetadata.METADATA_KEY_ALBUM) ?: ""
                    val duration = metadata?.getLong(MediaMetadata.METADATA_KEY_DURATION) ?: 0L
                    val key = "$title|$artist|$album"
                    if (key != trackKey) {
                        trackKey = key
                        trackTitle = title
                        trackArtist = artist
                        trackAlbum = album
                        trackDurationMs = duration
                        lyricLines = emptyList()
                        currentLine = ""
                        updateVisibility()
                        fetchJob?.cancel()
                        if (title.isNotBlank()) {
                            fetchJob = serviceScope.launch {
                                delay(600)
                                val lines = LyricsManager.getSyncedLyrics(
                                    this@LyricsOverlayService, title, artist, album, duration, null
                                )
                                if (trackKey == key) {
                                    lyricLines = lines ?: emptyList()
                                    lyricOffsetMs = AppPreferenceManager(this@LyricsOverlayService)
                                        .getLyricOffsetMs(title, artist)
                                }
                            }
                        }
                    }
                } catch (e: Exception) {
                    // セッションが読めないときは表示を維持する
                }
                delay(1500)
            }
        }
        // 再生位置のポーリング（現在の行を割り出す）
        serviceScope.launch {
            while (true) {
                try {
                    // ステータスバーの表示状態をポーリングでも確認する（インセット通知の保険）
                    overlayRoot?.rootWindowInsets?.let { insets ->
                        val vis = insets.isVisible(WindowInsets.Type.statusBars())
                        if (vis != statusBarVisible) {
                            statusBarVisible = vis
                            updateVisibility()
                        }
                    }
                    val lines = lyricLines
                    if (lines.isNotEmpty()) {
                        val ps = activeController()?.playbackState
                        if (ps != null) {
                            val nowMs = SystemClock.elapsedRealtime()
                            val playing = ps.state == PlaybackState.STATE_PLAYING
                            val reportedMs = if (playing) {
                                ps.position + ((nowMs - ps.lastPositionUpdateTime) * ps.playbackSpeed).toLong()
                            } else {
                                ps.position
                            }
                            val posMs = reportedMs + lyricOffsetMs
                            val curIdx = lines.indexOfLast { it.timeMs <= posMs }
                            val curLine = lines.getOrNull(curIdx)
                            val nextLine = lines.getOrNull(curIdx + 1)
                            val inInterlude = when {
                                curLine == null -> nextLine != null && (nextLine.timeMs - posMs) > 3000
                                nextLine == null -> (posMs - curLine.timeMs) > 8000
                                else -> (nextLine.timeMs - posMs) > 4000 && (posMs - curLine.timeMs) > 3000
                            }
                            val newLine = when {
                                inInterlude -> "♪"
                                curLine != null -> curLine.text
                                else -> ""
                            }
                            if (newLine != currentLine) {
                                currentLine = newLine
                                showLine(newLine)
                            }
                            updateVisibility()
                        }
                    } else if (currentLine.isNotEmpty()) {
                        currentLine = ""
                        updateVisibility()
                    }
                } catch (e: Exception) {
                    // 読めないときは表示を維持する
                }
                delay(400)
            }
        }
    }

    private fun showLine(line: String) {
        val view = lyricView ?: return
        if (shownLine == line) return
        shownLine = line
        view.animate().alpha(0f).setDuration(110).withEndAction {
            view.text = line
            view.animate().alpha(1f).setDuration(160).start()
        }.start()
    }

    companion object {
        fun start(context: Context) {
            try {
                val intent = Intent(context, LyricsOverlayService::class.java)
                if (Build.VERSION.SDK_INT >= 26) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
            } catch (e: Exception) {
                // 起動できない環境では何もしない
            }
        }

        fun stop(context: Context) {
            try {
                context.stopService(Intent(context, LyricsOverlayService::class.java))
            } catch (e: Exception) {
                // 止まっていれば何もしない
            }
        }
    }
}
