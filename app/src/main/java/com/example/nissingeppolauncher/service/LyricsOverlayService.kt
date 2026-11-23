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
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.WindowInsets
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView
import com.example.nissingeppolauncher.data.AppPreferenceManager
import android.text.SpannableString
import android.text.Spanned
import android.text.TextPaint
import android.text.style.ForegroundColorSpan
import android.text.style.MetricAffectingSpan
import androidx.core.content.res.ResourcesCompat
import com.example.nissingeppolauncher.R
import com.example.nissingeppolauncher.data.LyricLine
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.Text
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.example.nissingeppolauncher.ui.KaraokeLyricText
import com.example.nissingeppolauncher.ui.components.parseFontFamily
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
    private var overlayRoot: View? = null
    private var overlayMode: String = "line"
    private var pvLifecycleOwner: OverlayLifecycleOwner? = null
    private val pvLineState = mutableStateOf<LyricLine?>(null)
    private val pvPosState = mutableStateOf(0L)
    private val pvAccentState = mutableStateOf(androidx.compose.ui.graphics.Color(0xFFE53935))
    private val pvFontState = mutableStateOf<FontFamily?>(null)
    private val pvEnFontState = mutableStateOf<FontFamily?>(null)
    private val pvBoldState = mutableStateOf(false)
    private val pvClockState = mutableStateOf("")
    private val pvClockFontState = mutableStateOf<FontFamily?>(null)
    private var clockView: TextView? = null
    private var lyricView: TextView? = null
    private var attached = false
    private var overlayBuiltAtMs = 0L

    private var statusBarVisible = true
    private var currentLine = ""
    private var shownLine: String? = null
    // 行は分割せず、1行に収まらなければ2行に折り返して表示する
    private var segmentAnimGen = 0
    private var shownSungChars: Int = 0
    private var segmentAnimating: Boolean = false
    private var fittedLine: String? = null
    private var accentColorInt: Int = Color.rgb(229, 57, 53)
    private var segmentsDirty = false

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
        isAlive = true
        lastHeartbeatMs = SystemClock.elapsedRealtime()
        startForegroundWithNotification()
        if (!Settings.canDrawOverlays(this)) {
            stopSelf()
            return
        }
        try {
            buildOverlay()
            startClockLoop()
            startSessionLoops()
        } catch (e: Exception) {
            // 窓やセッションの初期化に失敗しても、前景化したサービス自体は生かしておく（次回 start で修復する）
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // 前景化を最初に済ませる（startForegroundService の制限時間に間に合わせるため、修復より先に呼ぶ）
        startForegroundWithNotification()
        // 自己修復: サービスが生きていても窓だけ剥がれていることがある（HyperOS 等の独自制限）。
        // start のたびに窓の付き具合を確認し、外れていたら作り直す（オンオフし直さなくて済むように）
        try {
            val root = overlayRoot
            val detached = root == null || !attached ||
                (!root.isAttachedToWindow && SystemClock.elapsedRealtime() - overlayBuiltAtMs > 3000L)
            if (detached && Settings.canDrawOverlays(this)) {
                detachOverlay()
                buildOverlay()
            }
        } catch (e: Exception) {
            // 修復に失敗してもサービスは続ける
        }
        return START_STICKY
    }

    override fun onDestroy() {
        isAlive = false
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
        // ダイナミックアイランド（中央のピル）に歌詞の末尾が隠れないよう、帯は左45%までにして早めに折り返す
        val width = (resources.displayMetrics.widthPixels * 0.33f).toInt()
        overlayMode = AppPreferenceManager(this).getStatusBarMode()
        if (overlayMode == "pv") {
            buildPvOverlay(wm, width, statusBarHeight)
            return
        }

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = ColorDrawable(Color.argb(246, 6, 8, 14))
        }
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(10f), 0, dp(8f), 0)
        }
        val clock = TextView(this).apply {
            setTextColor(Color.WHITE)
            // 時と分を縦に2行並べにして幅を詰め、歌詞の表示領域を広げる
            textSize = 9.5f
            includeFontPadding = false
            setLineSpacing(0f, 0.82f)
            gravity = Gravity.CENTER
            setShadowLayer(4f, 0f, 0f, Color.argb(160, 0, 0, 0))
        }
        val lyric = TextView(this).apply {
            setTextColor(Color.WHITE)
            textSize = 12f
            includeFontPadding = false
            maxLines = 2
            setLineSpacing(0f, 0.92f)
            ellipsize = TextUtils.TruncateAt.END
            setShadowLayer(7f, 0f, 0f, Color.argb(200, 30, 30, 30))
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                marginStart = dp(8f)
            }
        }
        row.addView(clock)
        row.addView(lyric)
        root.addView(row, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, statusBarHeight))
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
            overlayBuiltAtMs = SystemClock.elapsedRealtime()
            applyFonts()
            root.visibility = View.INVISIBLE
            root.requestApplyInsets()
        } catch (e: Exception) {
            attached = false
        }
    }

    // PVミニ: 同じ帯に Compose で「ワード同期の色付き」だけを描く（アイコン・パーティクル・カメラワークなし）
    private fun buildPvOverlay(wm: WindowManager, width: Int, statusBarHeight: Int) {
        val pref = AppPreferenceManager(this)
        pvAccentState.value = try {
            androidx.compose.ui.graphics.Color(Color.parseColor(pref.getAccentColorHex()))
        } catch (e: Exception) { pvAccentState.value }
        val jpChoice = pref.getLyricsPvFont()
        val jpFont = parseFontFamily(jpChoice, if (jpChoice == "custom") pref.getCustomFontPath() else null)
        pvFontState.value = jpFont
        val enChoice = pref.getLyricsEnFont()
        pvEnFontState.value = if (enChoice == "same") jpFont else parseFontFamily(enChoice, if (enChoice == "custom") pref.getLyricsEnCustomFontPath() else null)
        pvBoldState.value = pref.isLyricsBold()
        pvClockFontState.value = parseFontFamily(pref.getFontChoice(), if (pref.isUseCustomFont()) pref.getCustomFontPath() else null)
        val owner = OverlayLifecycleOwner()
        owner.performCreate()
        pvLifecycleOwner = owner
        val composeView = ComposeView(this).apply {
            setViewTreeLifecycleOwner(owner)
            setViewTreeViewModelStoreOwner(owner)
            setViewTreeSavedStateRegistryOwner(owner)
            setBackgroundColor(Color.argb(246, 6, 8, 14))
            setContent { StatusBarPvContent() }
        }
        overlayRoot = composeView
        clockView = null
        lyricView = null
        composeView.setOnApplyWindowInsetsListener { _, insets ->
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
            wm.addView(composeView, params)
            attached = true
            overlayBuiltAtMs = SystemClock.elapsedRealtime()
            owner.performResume()
            composeView.visibility = View.INVISIBLE
            composeView.requestApplyInsets()
        } catch (e: Exception) {
            attached = false
        }
    }

    @Composable
    private fun StatusBarPvContent() {
        val line = pvLineState.value
        Row(
            modifier = Modifier.fillMaxSize().padding(start = 10.dp, end = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = pvClockState.value,
                color = androidx.compose.ui.graphics.Color.White,
                fontSize = 9.5.sp,
                lineHeight = 8.sp,
                textAlign = TextAlign.Center,
                fontFamily = pvClockFontState.value,
                style = TextStyle(shadow = Shadow(androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.6f), Offset(0f, 1f), 4f))
            )
            BoxWithConstraints(
                modifier = Modifier.weight(1f).padding(start = 8.dp),
                contentAlignment = Alignment.CenterStart
            ) {
            if (line != null) {
                val measurer = rememberTextMeasurer()
                val weight = if (pvBoldState.value) FontWeight.Bold else FontWeight.Normal
                val fontFam = pvFontState.value
                val style = TextStyle(fontFamily = fontFam, fontSize = 20.sp, fontWeight = weight)
                val textWidth = remember(line.text, fontFam, weight) {
                    measurer.measure(line.text, style, softWrap = false).size.width
                }
                val availDp = maxWidth
                val avail = with(LocalDensity.current) { availDp.toPx() }
                val fitSp = if (textWidth > 0) (20f * avail / textWidth).coerceIn(8f, 18.5f) else 18.5f
                KaraokeLyricText(
                    line = line,
                    positionMs = pvPosState.value,
                    sungColor = pvAccentState.value,
                    baseColor = androidx.compose.ui.graphics.Color.White.copy(alpha = 0.92f),
                    fontSize = fitSp.sp,
                    fontWeight = weight,
                    fontFamily = fontFam,
                    enFontFamily = pvEnFontState.value,
                    shadow = Shadow(androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.6f), Offset(0f, 1.5f), 4f)
                )
            }
            }
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
        pvLifecycleOwner?.performDestroy()
        pvLifecycleOwner = null
    }

    private fun updateVisibility() {
        val root = overlayRoot ?: return
        val show = statusBarVisible && !launcherInForeground && currentLine.isNotBlank()
        val target = if (show) View.VISIBLE else View.INVISIBLE
        if (root.visibility != target) root.visibility = target
    }

    private fun startClockLoop() {
        val hourFormat = SimpleDateFormat("HH", Locale.JAPAN)
        val minuteFormat = SimpleDateFormat("mm", Locale.JAPAN)
        serviceScope.launch {
            while (true) {
                val now = Date()
                clockView?.text = hourFormat.format(now) + "\n" + minuteFormat.format(now)
                if (overlayMode == "pv") {
                    pvClockState.value = hourFormat.format(now) + "\n" + minuteFormat.format(now)
                    pvAccentState.value = try {
                        androidx.compose.ui.graphics.Color(Color.parseColor(AppPreferenceManager(this@LyricsOverlayService).getAccentColorHex()))
                    } catch (e: Exception) { pvAccentState.value }
                }
                applyFonts()
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
                        pvLineState.value = null
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
                    lastHeartbeatMs = SystemClock.elapsedRealtime()
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
                                else -> (nextLine.timeMs - curLine.timeMs) > 7000 && (posMs - curLine.timeMs) > 3000
                            }
                            val newLine = when {
                                inInterlude -> "♪"
                                curLine != null -> curLine.text
                                else -> ""
                            }
                            if (newLine != currentLine) {
                                currentLine = newLine
                            }
                            // 文字同期がある行は、歌い終わった文字数を数えて色付けに使う
                            val sungInLine = if (curLine != null && curLine.words.isNotEmpty()) {
                                var count = 0
                                for (w in curLine.words) {
                                    if (posMs >= w.endMs) count += w.text.length else break
                                }
                                count
                            } else 0
                            showLine(newLine, sungInLine)
                            if (overlayMode == "pv") {
                                pvLineState.value = if (inInterlude) null else curLine
                                pvPosState.value = posMs
                            }
                            updateVisibility()
                        }
                    } else if (currentLine.isNotEmpty()) {
                        currentLine = ""
                        pvLineState.value = null
                        updateVisibility()
                    }
                } catch (e: Exception) {
                    // 読めないときは表示を維持する
                }
                delay(if (overlayMode == "pv") 80L else 250L)
            }
        }
    }

    // フォント選択（UIフォント／歌詞フォント／英字フォント）をネイティブ Typeface に解決する
    private fun typefaceForChoice(choice: String, customPath: String?): Typeface? {
        return try {
            when (choice) {
                "receipt" -> ResourcesCompat.getFont(this, R.font.receipt_font)
                "denkichip" -> ResourcesCompat.getFont(this, R.font.denkichip_font)
                "tsukuhou" -> ResourcesCompat.getFont(this, R.font.tsukuhou_font)
                "kaibunsho" -> ResourcesCompat.getFont(this, R.font.kaibunsyo_font)
                "freelicense" -> ResourcesCompat.getFont(this, R.font.free_license)
                "montserratbi" -> ResourcesCompat.getFont(this, R.font.montserrat_bold_italic)
                "custom" -> customPath?.let { Typeface.createFromFile(it) }
                else -> null
            }
        } catch (e: Exception) {
            null
        }
    }

    private var appliedFontKey: String? = null
    private var lyricTypeface: Typeface = Typeface.DEFAULT
    private var enTypeface: Typeface? = null

    // 時計=UIフォント、歌詞=歌詞フォント（英字は英字フォント設定があればその部分だけ差し替える）
    private fun applyFonts() {
        val clock = clockView ?: return
        val lyric = lyricView ?: return
        val pref = AppPreferenceManager(this)
        val uiChoice = pref.getFontChoice()
        val lyricChoice = pref.getLyricsPvFont()
        val enChoice = pref.getLyricsEnFont()
        val bold = pref.isLyricsBold()
        val key = "$uiChoice|$lyricChoice|$enChoice|$bold|${pref.getCustomFontPath()}|${pref.getLyricsEnCustomFontPath()}|${pref.getAccentColorHex()}"
        if (key == appliedFontKey) return
        appliedFontKey = key
        segmentsDirty = true
        accentColorInt = try { Color.parseColor(pref.getAccentColorHex()) } catch (e: Exception) { accentColorInt }
        clock.typeface = typefaceForChoice(uiChoice, pref.getCustomFontPath()) ?: Typeface.DEFAULT_BOLD
        val base = typefaceForChoice(lyricChoice, pref.getCustomFontPath()) ?: Typeface.DEFAULT
        lyricTypeface = if (bold) Typeface.create(base, Typeface.BOLD) else base
        lyric.typeface = lyricTypeface
        enTypeface = if (enChoice == "same") null else typefaceForChoice(enChoice, pref.getLyricsEnCustomFontPath())
        shownLine?.let { lyric.text = styledLine(it) }
    }

    private class OverlayTypefaceSpan(private val tf: Typeface) : MetricAffectingSpan() {
        override fun updateDrawState(ds: TextPaint) {
            ds.typeface = tf
        }

        override fun updateMeasureState(p: TextPaint) {
            p.typeface = tf
        }
    }

    // 英字フォントが別指定のとき、英語の範囲だけそのフォントで描く。
    // 半角記号だけの並びは直前の文のフォントに合わせる（日本語文中の記号は日本語フォントのまま）
    private fun styledLine(line: String, sungChars: Int = 0): CharSequence {
        val base = styledLineBase(line)
        if (sungChars <= 0) return base
        // 文字同期がある行は、歌い終わった部分をアクセント色で塗る（カラオケのテロップ風）
        val sp = SpannableString(base)
        sp.setSpan(ForegroundColorSpan(accentColorInt), 0, sungChars.coerceIn(0, line.length), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        return sp
    }

    private fun styledLineBase(line: String): CharSequence {
        val en = enTypeface ?: return line
        if (line.none { it in ' '..'~' }) return line
        val runs = mutableListOf<Triple<Int, Int, Boolean>>()
        var i = 0
        while (i < line.length) {
            val ascii = line[i] in ' '..'~'
            var j = i
            while (j < line.length && (line[j] in ' '..'~') == ascii) j++
            runs.add(Triple(i, j, ascii))
            i = j
        }
        val useEn = BooleanArray(runs.size)
        for (k in runs.indices) {
            val (s, e, ascii) = runs[k]
            if (!ascii) continue
            if (line.substring(s, e).any { it in 'a'..'z' || it in 'A'..'Z' || it in '0'..'9' }) useEn[k] = true
        }
        for (k in runs.indices) {
            if (!runs[k].third || useEn[k]) continue
            useEn[k] = if (k > 0) useEn[k - 1] else (if (runs.size > 1) useEn[k + 1] else false)
        }
        val sp = SpannableString(line)
        for (k in runs.indices) {
            if (!useEn[k]) continue
            val (s, e, _) = runs[k]
            sp.setSpan(OverlayTypefaceSpan(en), s, e, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
        return sp
    }

    // 1行に収まるなら12spのまま、収まらなければステータスバーの高さに収まる大きさにして2行で出す
    private fun fitTextSize(view: TextView, line: String) {
        if (fittedLine == line && !segmentsDirty) return
        val avail = view.width - view.paddingLeft - view.paddingRight
        if (avail <= 0) return
        fittedLine = line
        view.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
        if (view.paint.measureText(line) <= avail) return
        val barH = overlayRoot?.height ?: 0
        val maxSp = if (barH > 0) (barH / 2f / 1.18f) / resources.displayMetrics.scaledDensity else 10.5f
        view.setTextSize(TypedValue.COMPLEX_UNIT_SP, maxSp.coerceIn(8f, 11.5f))
    }

    private fun showLine(line: String, sung: Int = 0) {
        val view = lyricView ?: return
        if (segmentsDirty) {
            fittedLine = null
            segmentsDirty = false
        }
        if (shownLine == line) {
            // 同じ行のままなら、大きさの再計算（初回レイアウト後など）とカラオケの進みだけ反映する
            // ただしスクロールアニメーション中は、表示中の旧テキストに触らない（サイズが変わって一瞬折り返すため）
            if (!segmentAnimating) {
                fitTextSize(view, line)
                if (sung != shownSungChars) {
                    shownSungChars = sung
                    view.text = styledLine(line, sung)
                }
            }
            return
        }
        shownLine = line
        shownSungChars = sung
        // 行が変わったときはスクロールで切り替える（フェードではなく上下のスライド）
        val gen = ++segmentAnimGen
        segmentAnimating = true
        val h = (view.height.takeIf { it > 0 } ?: dp(14f)).toFloat()
        view.animate().cancel()
        view.translationY = 0f
        view.alpha = 1f
        view.animate().translationY(-h * 0.7f).alpha(0f).setDuration(130).withEndAction {
            if (gen != segmentAnimGen) return@withEndAction
            // サイズ変更はテキストの差し替えと同時に行う（先に変えると旧テキストが一瞬折り返す）
            fitTextSize(view, line)
            view.text = styledLine(line, sung)
            view.translationY = h * 0.7f
            view.animate().translationY(0f).alpha(1f).setDuration(180).withEndAction {
                if (gen == segmentAnimGen) segmentAnimating = false
            }.start()
        }.start()
    }

    companion object {
        // ランチャー本体が前面にいる間はオーバーレイを隠す（本体側で同じ歌詞が見えているため）
        @Volatile
        var launcherInForeground: Boolean = false

        // 自己修復用: サービスの生存と最終応答時刻（メイン側が再起動の要否を判断する）
        @Volatile
        var isAlive: Boolean = false

        @Volatile
        var lastHeartbeatMs: Long = 0L

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

// Service のオーバーレイで Compose を動かすための最小ライフサイクル所有者
private class OverlayLifecycleOwner : LifecycleOwner, ViewModelStoreOwner, SavedStateRegistryOwner {
    private val registry = LifecycleRegistry(this)
    private val store = ViewModelStore()
    private val controller = SavedStateRegistryController.create(this)
    override val lifecycle: Lifecycle get() = registry
    override val viewModelStore: ViewModelStore get() = store
    override val savedStateRegistry: SavedStateRegistry get() = controller.savedStateRegistry
    fun performCreate() {
        controller.performRestore(null)
        registry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
    }
    fun performResume() {
        registry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
    }
    fun performDestroy() {
        registry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
        store.clear()
    }
}
