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
import android.os.PowerManager
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
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.ui.graphics.graphicsLayer
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
import com.example.nissingeppolauncher.ui.components.parseFontFamily
import com.example.nissingeppolauncher.data.LyricsManager
import com.example.nissingeppolauncher.ui.LyricEffectsData
import com.example.nissingeppolauncher.ui.splitLyricUnits
import com.example.nissingeppolauncher.ui.lyricInterludeThresholdMs
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import kotlin.math.abs
import kotlin.math.sin
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
    private val pvInterludeState = mutableStateOf(false)
    private val pvPosState = mutableStateOf(0L)
    private val pvFrozenState = mutableStateOf(false)
    private val pvColorWordsState = mutableStateOf<List<Pair<String, androidx.compose.ui.graphics.Color>>>(emptyList())
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
        val width = (resources.displayMetrics.widthPixels * AppPreferenceManager(this).getStatusBarWidthFactor()).toInt()
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
        serviceScope.launch {
            LyricEffectsData.init(this@LyricsOverlayService)
            pvColorWordsState.value = LyricEffectsData.colorWords + LyricEffectsData.assocColorWords
        }
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

    // PVミニの行描画: 登録ワードの色 + 文字ごとの大きさ・ゆらぎ + 1文字ずつの入場 + 意味の区切りで最大2行
    @Composable
    private fun MiniPvLine(line: LyricLine, posMs: Long, exitP: Float = 0f) {
        val measurer = rememberTextMeasurer()
        val weight = if (pvBoldState.value) FontWeight.Bold else FontWeight.Normal
        val fontFam = pvFontState.value
        val enFam = pvEnFontState.value
        val baseCol = androidx.compose.ui.graphics.Color.White.copy(alpha = 0.92f)
        val shadow = Shadow(androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.6f), Offset(0f, 1.5f), 4f)
        val colorWords = pvColorWordsState.value
        // 登録ワード（色名・連想ワード）に反応する文字色。長いワードを先に、リスト順優先で塗る
        val charColors = remember(line.text, colorWords) {
            val arr = arrayOfNulls<androidx.compose.ui.graphics.Color>(line.text.length)
            if (colorWords.isNotEmpty() && line.text.isNotEmpty()) {
                for (pass in 0..1) {
                    for ((w, c) in colorWords) {
                        if (w.isEmpty()) continue
                        if (pass == 0 && w.length < 2) continue
                        if (pass == 1 && w.length >= 2) continue
                        var from = 0
                        while (true) {
                            val idx = line.text.indexOf(w, from)
                            if (idx < 0) break
                            for (k in idx until idx + w.length) if (arr[k] == null) arr[k] = c
                            from = idx + 1
                        }
                    }
                }
            }
            arr
        }
        val timings = remember(line) {
            if (line.words.isEmpty()) null else {
                val list = mutableListOf<Pair<Long, Long>>()
                for (w in line.words) {
                    val n = w.text.length.coerceAtLeast(1)
                    val dur = (w.endMs - w.startMs).coerceAtLeast(0L)
                    for (i in 0 until n) {
                        list.add((w.startMs + dur * i / n) to (w.startMs + dur * (i + 1) / n))
                    }
                }
                list
            }
        }
        // 本家PVと同じ文字種ルール: 漢字は大きめ(1.25)、ひらがな小さめ(0.82)、カタカナ1.05、小カナは更に×0.6、ーは前の文字に合わせる、行頭がひらがな続きなら先頭だけ1.25
        // 英字フォントは「英数字を含む半角ラン」だけ（本家と同じ判定）。記号だけのランは前後のランに合わせる
        val charUseEn = remember(line.text) {
            val arr = BooleanArray(line.text.length)
            val runs = mutableListOf<Triple<Int, Int, Boolean>>()
            var i0 = 0
            while (i0 < line.text.length) {
                val ascii = line.text[i0] in ' '..'~'
                var j0 = i0
                while (j0 < line.text.length && (line.text[j0] in ' '..'~') == ascii) j0++
                runs.add(Triple(i0, j0, ascii))
                i0 = j0
            }
            val runEn = BooleanArray(runs.size)
            for (k in runs.indices) {
                val (s, e, ascii) = runs[k]
                if (ascii && e - s > 1 && line.text.substring(s, e).any { it in 'a'..'z' || it in 'A'..'Z' || it in '0'..'9' }) runEn[k] = true
            }
            for (k in runs.indices) {
                if (!runs[k].third || runEn[k]) continue
                runEn[k] = if (k > 0) runEn[k - 1] else (if (runs.size > 1) runEn[k + 1] else false)
            }
            for (k in runs.indices) {
                if (!runEn[k]) continue
                for (x in runs[k].first until runs[k].second) arr[x] = true
            }
            arr
        }
        val charFactors = remember(line.text) {
            val smallKana = "ぁぃぅぇぉっゃゅょゎァィゥェォッャュョヮヵヶ"
            val leadBoost = line.text.length >= 2 && line.text[0] in 'ぁ'..'ゟ' && line.text[1] in 'ぁ'..'ゟ'
            val arr = FloatArray(line.text.length)
            var prev = 0.98f
            for (idx in line.text.indices) {
                val ch = line.text[idx]
                val classCf = when {
                    ch in smallKana -> 1.0f
                    ch == 'ー' -> prev
                    ch in '一'..'鿿' || ch == '々' || ch == '〆' || ch == 'ヶ' -> 1.25f
                    ch in 'ぁ'..'ゟ' -> 0.82f
                    ch in '゠'..'ヿ' -> 1.05f
                    else -> 0.98f
                }
                var cf = if (idx == 0 && leadBoost) 1.25f else classCf
                prev = cf
                if (ch in smallKana) cf *= 0.6f
                arr[idx] = cf
            }
            arr
        }
        val chunks = remember(line.text) { try { splitLyricUnits(line.text) } catch (e: Exception) { listOf(line.text) } }
        val splitIdx = remember(line.text, chunks) { computeMiniSplit(line.text, chunks) }
        val rowTexts = if (splitIdx in 1 until line.text.length) {
            listOf(line.text.substring(0, splitIdx), line.text.substring(splitIdx))
        } else listOf(line.text)
        val swayTrans = rememberInfiniteTransition(label = "miniSway")
        val swayT by swayTrans.animateFloat(
            initialValue = 0f, targetValue = 6.2832f,
            animationSpec = infiniteRepeatable(tween(3400, easing = LinearEasing), RepeatMode.Restart),
            label = "swayT"
        )
        BoxWithConstraints(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            val style20 = TextStyle(fontFamily = fontFam, fontSize = 20.sp, fontWeight = weight)
            val style20En = TextStyle(fontFamily = enFam ?: fontFam, fontSize = 20.sp, fontWeight = weight)
            val availPx = with(LocalDensity.current) { maxWidth.toPx() }
            val rawFit = remember(line.text, rowTexts, availPx, fontFam, enFam, weight, charFactors, charUseEn, splitIdx) {
                var f = 18f
                rowTexts.forEachIndexed { r, rt ->
                    val st = if (r == 0) 0 else splitIdx
                    var w = 0f
                    for (j in rt.indices) {
                        // 実際に描くフォントで測る（英字フォントは幅が違うので、日本語フォントで測ると英語がはみ出す）
                        val stl = if (charUseEn.getOrElse(st + j) { false }) style20En else style20
                        w += measurer.measure(rt[j].toString(), stl, softWrap = false).size.width * charFactors.getOrElse(st + j) { 0.98f }
                    }
                    if (w > 0) f = minOf(f, 20f * availPx / w)
                }
                (f * 0.80f).coerceIn(7f, 15f)
            }
            val fitSp = if (rowTexts.size == 2) {
                val cap = (maxHeight.value / 2f) / 1.60f / LocalDensity.current.fontScale
                minOf(rawFit, cap).coerceAtLeast(6f)
            } else rawFit
            Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy((-1).dp, Alignment.CenterVertically)) {
                rowTexts.forEachIndexed { r, rowText ->
                    val startIdx = if (r == 0) 0 else splitIdx
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        rowText.forEachIndexed { j, ch ->
                            val i = startIdx + j
                            val t = timings?.getOrNull(i)
                            val col = charColors.getOrNull(i) ?: baseCol
                            val entered = if (t != null) posMs >= t.first else posMs >= line.timeMs + i * 45L
                            val anim = remember(line.timeMs, i) { Animatable(if (entered) 1f else 0f) }
                            LaunchedEffect(entered) {
                                if (entered && anim.value < 1f) anim.animateTo(1f, spring(dampingRatio = 0.55f, stiffness = 380f))
                            }
                            val seed = (line.timeMs / 100L).toInt() * 31 + i * 17
                            val factor = 0.92f + ((seed % 22 + 22) % 22) / 100f
                            val phase = ((seed % 628 + 628) % 628) / 100f
                            // 入場の向き（本家のカメラワーク相当）: 0=右から 1=左から 2=上から 3=下から 4=押し込む 5=回転
                            val dir = ((seed % 6) + 6) % 6
                            val charFont = if (enFam != null && charUseEn.getOrElse(i) { false }) enFam else fontFam
                            val charSp = (fitSp * factor * charFactors.getOrElse(i) { 0.98f }).coerceIn(fitSp * 0.55f, fitSp * 1.35f)
                            Box(
                                modifier = Modifier.graphicsLayer {
                                    val p = anim.value
                                    val ep = 1f - p
                                    alpha = p * (1f - exitP)
                                    val es = if (dir == 4) (1.7f - 0.7f * p) else (0.55f + 0.45f * p)
                                    val breathe = 1f + 0.03f * sin(swayT * 1.3f + phase).toFloat()
                                    scaleX = es * breathe * (1f - 0.15f * exitP)
                                    scaleY = es * breathe * (1f - 0.15f * exitP)
                                    translationX = when (dir) {
                                        0 -> ep * 16.dp.toPx()
                                        1 -> -ep * 16.dp.toPx()
                                        else -> 0f
                                    }
                                    translationY = ep * 5.dp.toPx() + sin(swayT + phase).toFloat() * 1.6.dp.toPx() - exitP * 6.dp.toPx() + when (dir) {
                                        2 -> -ep * 7.dp.toPx()
                                        3 -> ep * 7.dp.toPx()
                                        else -> 0f
                                    }
                                    rotationZ = ((seed % 13 + 13) % 13 - 6).toFloat() * (1f - p * 0.4f) + sin(swayT * 0.8f + phase * 1.7f).toFloat() * 2.5f + if (dir == 5) ep * (if (seed % 2 == 0) 40f else -40f) else 0f
                                }
                            ) {
                                // グロー層（文字色で光らせる。本家PVのグロー相当＋コントラスト確保）
                                Text(
                                    text = ch.toString(),
                                    color = col,
                                    fontSize = charSp.sp,
                                    fontWeight = weight,
                                    fontFamily = charFont,
                                    lineHeight = (charSp * 1.02f).sp,
                                    style = TextStyle(shadow = Shadow(col.copy(alpha = 0.85f), Offset(0f, 0f), 9f))
                                )
                                Text(
                                    text = ch.toString(),
                                    color = col,
                                    fontSize = charSp.sp,
                                    fontWeight = weight,
                                    fontFamily = charFont,
                                    lineHeight = (charSp * 1.02f).sp,
                                    style = TextStyle(shadow = shadow)
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    // PVミニの間奏: 本家と同じ波の線＋粒子（瞬く点）
    @Composable
    private fun MiniInterludeWave() {
        val trans = rememberInfiniteTransition(label = "miniWave")
        val t by trans.animateFloat(
            initialValue = 0f, targetValue = 6.2832f,
            animationSpec = infiniteRepeatable(tween(2600, easing = LinearEasing), RepeatMode.Restart),
            label = "waveT"
        )
        Canvas(modifier = Modifier.fillMaxSize()) {
            for (w in 0..1) {
                val amp = (3.2f - w * 1.1f).dp.toPx()
                val baseY = size.height * (0.5f + (w - 0.5f) * 0.18f)
                val path = Path()
                var x = 0f
                path.moveTo(0f, baseY + sin(t + w * 1.7f).toFloat() * amp)
                while (x <= size.width) {
                    x += 6f
                    path.lineTo(x, baseY + sin(t + x / size.width * 4.2f + w * 1.7f).toFloat() * amp)
                }
                drawPath(
                    path,
                    color = androidx.compose.ui.graphics.Color.White.copy(alpha = 0.5f - w * 0.22f),
                    style = Stroke(width = 1.2.dp.toPx())
                )
            }
            for (i in 0 until 10) {
                val px = ((i * 47) % 100) / 100f * size.width
                val py = ((i * 31) % 70 + 15) / 100f * size.height
                val tw = 0.35f + 0.65f * abs(sin(t * 1.4f + i * 1.93f)).toFloat()
                drawCircle(
                    androidx.compose.ui.graphics.Color.White.copy(alpha = 0.5f * tw),
                    radius = (if (i % 3 == 0) 1.6f else 1.1f) * tw,
                    center = Offset(px, py)
                )
            }
        }
    }

    private fun miniCharEm(ch: Char): Float = when {
        ch.code in 0x20..0x7E -> 0.56f
        ch.code in 0xFF61..0xFF9F -> 0.5f
        else -> 1f
    }

    private fun miniChunkEm(s: String): Float = s.fold(0f) { a, c -> a + miniCharEm(c) * 1.02f }

    // 本家と同じ考え方の改行位置: 塊の境界で左右均等、長い1塊は助詞の位置で割る
    private fun computeMiniSplit(text: String, chunks: List<String>): Int {
        val nonSpace = text.count { it != ' ' && it != '　' }
        if (nonSpace < 6) return -1
        // 塊は空白を除いた並びなので、元の文での開始位置を引き直す（位置を間違えると単語の途中で割れる）
        val starts = IntArray(chunks.size)
        var searchFrom = 0
        for (k in chunks.indices) {
            val at = text.indexOf(chunks[k], searchFrom)
            starts[k] = if (at >= 0) at else searchFrom
            searchFrom = starts[k] + chunks[k].length
        }
        if (chunks.size == 1) {
            val s = chunks[0]
            if (s.length >= 6 && miniChunkEm(s) > 12f) {
                val total = miniChunkEm(s)
                val particles = "はがをにへとやのもでからまでよりばねよわ"
                var bestI = -1
                var bestD = Float.MAX_VALUE
                var acc = 0f
                for (i in 1 until s.length) {
                    acc += miniCharEm(s[i - 1]) * 1.02f
                    if (i >= 2 && i <= s.length - 2 && (s[i - 1] in particles || s[i - 1] == ' ' || s[i - 1] == '　')) {
                        val d = abs(total / 2f - acc)
                        if (d < bestD) { bestD = d; bestI = i }
                    }
                }
                if (bestI < 0) {
                    acc = 0f
                    bestD = Float.MAX_VALUE
                    for (i in 1 until s.length) {
                        acc += miniCharEm(s[i - 1]) * 1.02f
                        val d = abs(total / 2f - acc)
                        if (d < bestD) { bestD = d; bestI = i }
                    }
                }
                val noStart = "ーぁぃぅぇぉっゃゅょゎァィゥェォッャュョヮ・、。，．！!？?）)】」』\""
                while (bestI > 1 && bestI < s.length && s[bestI] in noStart) bestI--
                while (bestI < s.length - 1 && bestI > 0 && s[bestI] in noStart) bestI++
                if (bestI in 1 until s.length) return starts[0] + bestI
            }
            return -1
        }
        val totalEm = chunks.sumOf { miniChunkEm(it).toDouble() }.toFloat()
        var acc = 0f
        var best = 1
        var bestDiff = Float.MAX_VALUE
        for (idx in 1 until chunks.size) {
            acc += miniChunkEm(chunks[idx - 1])
            val diff = abs(totalEm / 2f - acc)
            if (diff < bestDiff) { bestDiff = diff; best = idx }
        }
        val split = starts[best]
        return if (split in 1 until text.length) split else -1
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
                // 行の退場: 前の行を少し残して上へ消す（本家の退場保持に相当）
                var curLine by remember { mutableStateOf<LyricLine?>(null) }
                var exitLine by remember { mutableStateOf<LyricLine?>(null) }
                val exitAnim = remember { Animatable(0f) }
                LaunchedEffect(line) {
                    if (line != curLine) {
                        exitLine = curLine
                        curLine = line
                        if (exitLine != null) {
                            exitAnim.snapTo(0f)
                            exitAnim.animateTo(1f, tween(durationMillis = 260, easing = FastOutSlowInEasing))
                            exitLine = null
                        }
                    }
                }
                Box(modifier = Modifier.fillMaxSize()) {
                    if (pvFrozenState.value) {
                        val cl = curLine
                        if (cl != null) {
                            Text(
                                text = cl.text,
                                color = androidx.compose.ui.graphics.Color.White.copy(alpha = 0.92f),
                                fontSize = 13.sp,
                                fontWeight = if (pvBoldState.value) FontWeight.Bold else FontWeight.Normal,
                                fontFamily = pvFontState.value,
                                maxLines = 1,
                                style = TextStyle(shadow = Shadow(androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.6f), Offset(0f, 1.5f), 4f))
                            )
                        }
                    } else {
                        exitLine?.let { MiniPvLine(line = it, posMs = pvPosState.value, exitP = exitAnim.value) }
                        val cl = curLine
                        if (cl != null) {
                            MiniPvLine(line = cl, posMs = pvPosState.value)
                        } else if (exitLine == null && pvInterludeState.value) {
                            MiniInterludeWave()
                        }
                    }
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
                    pvColorWordsState.value = LyricEffectsData.colorWords + LyricEffectsData.assocColorWords
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
                                curLine == null -> nextLine != null && (nextLine.timeMs - posMs) > lyricInterludeThresholdMs(lines)
                                nextLine == null -> (posMs - curLine.timeMs) > 15000
                                else -> (nextLine.timeMs - curLine.timeMs) > lyricInterludeThresholdMs(lines) && (posMs - curLine.timeMs) > 10000
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
                                pvInterludeState.value = inInterlude
                                pvPosState.value = posMs
                            }
                            updateVisibility()
                        }
                    } else if (currentLine.isNotEmpty()) {
                        currentLine = ""
                        pvLineState.value = null
                        pvInterludeState.value = false
                        updateVisibility()
                    }
                } catch (e: Exception) {
                    // 読めないときは表示を維持する
                }
                val pmNow = getSystemService(Context.POWER_SERVICE) as PowerManager
                val screenOff = !pmNow.isInteractive
                val saving = screenOff || AppPreferenceManager(this@LyricsOverlayService).isPowerSaveActive(this@LyricsOverlayService)
                pvFrozenState.value = saving && overlayMode == "pv"
                delay(
                    when {
                        screenOff -> 1000L
                        saving -> 500L
                        overlayMode == "pv" -> 120L
                        else -> 250L
                    }
                )
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
            if (e - s > 1 && line.substring(s, e).any { it in 'a'..'z' || it in 'A'..'Z' || it in '0'..'9' }) useEn[k] = true
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
