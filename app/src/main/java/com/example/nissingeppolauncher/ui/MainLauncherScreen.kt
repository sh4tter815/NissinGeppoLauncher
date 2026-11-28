package com.example.nissingeppolauncher.ui

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import androidx.compose.foundation.border
import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.changedToUp
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.TransformOrigin
import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import android.view.HapticFeedbackConstants
import androidx.compose.ui.platform.LocalView
import androidx.compose.foundation.background
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.example.nissingeppolauncher.data.AppInfo
import com.example.nissingeppolauncher.data.AppRepository
import com.example.nissingeppolauncher.data.LyricLine
import com.example.nissingeppolauncher.data.UpdateChecker
import com.example.nissingeppolauncher.data.LyricsManager
import com.example.nissingeppolauncher.data.WidgetPlacement
import com.example.nissingeppolauncher.service.LauncherMediaListenerService
import com.example.nissingeppolauncher.ui.components.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import kotlin.math.*

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MainLauncherScreen() {
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val haptic = LocalHapticFeedback.current
    val view = LocalView.current

    val insets = WindowInsets.systemBars.asPaddingValues()
    val topPaddingDp = insets.calculateTopPadding()
    val bottomPaddingDp = insets.calculateBottomPadding()

    val screenWidthDp = configuration.screenWidthDp.toFloat()
    val screenHeightDp = configuration.screenHeightDp.toFloat()
    val coroutineScope = rememberCoroutineScope()
    val repo = remember { AppRepository(context) }
    val keyboardController = LocalSoftwareKeyboardController.current

    // ページ管理 (0: 設定, 1: ホーム, 2: アプリ一覧, 3: フォルダ)
    var currentLauncherPage by remember { mutableIntStateOf(if (repo.prefManager.getStartupPage() == "last") repo.prefManager.getLastPage() else 1) }

    var accentHex by remember { mutableStateOf(repo.prefManager.getAccentColorHex()) }
    var subHex by remember { mutableStateOf(repo.prefManager.getSubColorHex()) }
    val manualAccentColor = remember(accentHex) { parseHexColor(accentHex) }
    // サブ色の手動指定（空 = 自動なので null）
    val manualSubColor = remember(subHex) { if (subHex.isNotBlank()) parseHexColor(subHex) else null }
    var thirdHex by remember { mutableStateOf(repo.prefManager.getThirdColorHex()) }
    var monoColorMode by remember { mutableStateOf(repo.prefManager.getMonoColorMode()) }
    val manualThirdColor = remember(thirdHex) { if (thirdHex.isNotBlank()) parseHexColor(thirdHex) else null }

    // アートワーク連動カラー: 設定でONのとき、再生中セッションのアートワークから
    // アクセント色（前景）と背景色を抽出してテーマカラーにする
    var matchArtwork by remember { mutableStateOf(repo.prefManager.isMatchArtworkColor()) }
    var matchWallpaper by remember { mutableStateOf(repo.prefManager.isMatchWallpaperColor()) }
    var bgMode by remember { mutableStateOf(repo.prefManager.getBackgroundMode()) }
    // 背景にアートワークを使うモードか（壁紙モード以外）。単色/ぼかしは WallpaperBackground 側の style で切り替える
    val useArtworkBg = bgMode != "wallpaper"
    var liquidBackground by remember { mutableStateOf(repo.prefManager.isLiquidBackgroundEnabled()) }
    var homeDiscLayout by remember { mutableStateOf(repo.prefManager.getHomeDiscLayout()) }
    var disc3d by remember { mutableStateOf(repo.prefManager.isDisc3dEnabled()) }
    var disc3dTilt by remember { mutableStateOf(repo.prefManager.isDisc3dTiltEnabled()) }
    // 傾き連動の現在値（ローパス済み）。graphicsLayer の中でのみ読むので再コンポーズは走らない
    val discTiltX = remember { mutableFloatStateOf(0f) }
    val discTiltY = remember { mutableFloatStateOf(0f) }
    DisposableEffect(disc3d, disc3dTilt, currentLauncherPage) {
        if (!disc3dTilt || currentLauncherPage !in 0..3) {
            discTiltX.value = 0f
            discTiltY.value = 0f
            return@DisposableEffect onDispose { }
        }
        val sm = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
        val sensor = sm?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        if (sm == null || sensor == null) return@DisposableEffect onDispose { }
        val listener = object : SensorEventListener {
            override fun onSensorChanged(e: SensorEvent) {
                // 手ブレ対策: ローパスを強め＋小さな傾きはデッドゾーンで0にする
                val k = 0.10f
                val dz = 2.5f
                val rawY = (e.values[0] / 9.8f * 22f).coerceIn(-22f, 22f)
                val rawX = ((e.values[1] - 9.8f) / 9.8f * 22f).coerceIn(-22f, 22f)
                val targetY = if (kotlin.math.abs(rawY) < dz) 0f else rawY
                val targetX = if (kotlin.math.abs(rawX) < dz) 0f else rawX
                discTiltY.value += (targetY - discTiltY.value) * k
                discTiltX.value += (targetX - discTiltX.value) * k
            }
            override fun onAccuracyChanged(s: Sensor?, accuracy: Int) { }
        }
        sm.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_UI)
        onDispose { sm.unregisterListener(listener) }
    }
    var dateDisplayMode by remember { mutableStateOf(repo.prefManager.getDateDisplayMode()) }
    var sessionAlbumArt by remember { mutableStateOf<Bitmap?>(null) }
    var artworkAccent by remember { mutableStateOf<Color?>(null) }
    var artworkSub by remember { mutableStateOf<Color?>(null) }
    var artworkThird by remember { mutableStateOf<Color?>(null) }
    var artworkBackground by remember { mutableStateOf<Color?>(null) }
    var wallpaperAccent by remember { mutableStateOf<Color?>(null) }
    var wallpaperSub by remember { mutableStateOf<Color?>(null) }
    var wallpaperThird by remember { mutableStateOf<Color?>(null) }
    var wallpaperBackground by remember { mutableStateOf<Color?>(null) }

    // ホームの時計下に出す同期歌詞（再生中セッションの曲情報に追随する）
    var sessionTrackTitle by remember { mutableStateOf("") }
    var sessionIsPlaying by remember { mutableStateOf(false) }
    var sessionTrackArtist by remember { mutableStateOf("") }
    var sessionTrackAlbum by remember { mutableStateOf("") }
    var sessionDurationMs by remember { mutableLongStateOf(0L) }
    var syncedLyricLines by remember { mutableStateOf<List<LyricLine>>(emptyList()) }
    var currentLyricLine by remember { mutableStateOf("") }
    var sessionPositionMs by remember { mutableLongStateOf(0L) }
    var lyricOffsetMs by remember { mutableLongStateOf(0L) }
    var lyricsDisplayMode by remember { mutableStateOf(repo.prefManager.getLyricsDisplayMode()) }
    // 2026-11-23 追加設定の状態（設定画面の onChanged で読み直す）
    var lyricsEnabled by remember { mutableStateOf(repo.prefManager.isLyricsEnabled()) }
    var sleepRemainingMs by remember { mutableLongStateOf(0L) }
    var discDropOnPv by remember { mutableStateOf(repo.prefManager.isDiscDropOnPvEnabled()) }
    var clockShowSeconds by remember { mutableStateOf(repo.prefManager.isClockShowSeconds()) }
    var clockHorizontal by remember { mutableStateOf(repo.prefManager.isClockHorizontal()) }
    var clock12h by remember { mutableStateOf(repo.prefManager.isClock12h()) }
    var clockBattery by remember { mutableStateOf(repo.prefManager.isClockShowBattery()) }
    var dateWareki by remember { mutableStateOf(repo.prefManager.isDateWareki()) }
    var pageTransitionSpeed by remember { mutableFloatStateOf(repo.prefManager.getPageTransitionSpeed()) }
    var dialVisibility by remember { mutableStateOf(repo.prefManager.getDialVisibility()) }
    var dialLastTouchMs by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var powerSaveMode by remember { mutableStateOf(repo.prefManager.getPowerSaveMode()) }
    var systemPowerSave by remember { mutableStateOf(false) }
    DisposableEffect(Unit) {
        val pm = context.getSystemService(android.content.Context.POWER_SERVICE) as android.os.PowerManager
        systemPowerSave = pm.isPowerSaveMode
        val receiver = object : android.content.BroadcastReceiver() {
            override fun onReceive(c: android.content.Context?, intent: android.content.Intent?) {
                systemPowerSave = pm.isPowerSaveMode
            }
        }
        context.registerReceiver(receiver, android.content.IntentFilter(android.os.PowerManager.ACTION_POWER_SAVE_MODE_CHANGED))
        onDispose { try { context.unregisterReceiver(receiver) } catch (e: Exception) {} }
    }
    val powerSaveActive = repo.prefManager.isPowerSaveActive(context)
    var keepScreenOn by remember { mutableStateOf(repo.prefManager.isKeepScreenOn()) }
    var pvFontScale by remember { mutableFloatStateOf(repo.prefManager.getPvFontScale()) }
    var notificationBadges by remember { mutableStateOf(repo.prefManager.isNotificationBadgesEnabled()) }
    var appSortMode by remember { mutableStateOf(repo.prefManager.getAppSortMode()) }
    var appSortDescending by remember { mutableStateOf(repo.prefManager.isAppSortDescending()) }
    var indexShowEnglish by remember { mutableStateOf(repo.prefManager.isIndexShowEnglish()) }
    var indexMode by remember { mutableStateOf(repo.prefManager.getIndexMode()) }
    var indexHideEmpty by remember { mutableStateOf(repo.prefManager.isIndexHideEmptyEnabled()) }
    var labelsWhilePlaying by remember { mutableStateOf(repo.prefManager.isLabelsWhilePlayingEnabled()) }
    var usageStatsMap by remember { mutableStateOf<Map<String, Long>>(emptyMap()) }
    LaunchedEffect(appSortMode) {
        if (appSortMode == "usage" || appSortMode == "recent") {
            usageStatsMap = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { loadUsageStats(context, appSortMode) }
        }
    }
    var pvTopOffset by remember { mutableFloatStateOf(repo.prefManager.getPvTopOffsetDp()) }
    var layoutEditMode by remember { mutableStateOf(false) }
    var clockOffsetX by remember { mutableFloatStateOf(repo.prefManager.getClockOffsetXDp()) }
    var discOffsetX by remember { mutableFloatStateOf(repo.prefManager.getDiscOffsetX()) }
    var discOffsetY by remember { mutableFloatStateOf(repo.prefManager.getDiscOffsetY()) }
    // 円盤の左右は中央で固定: 保存済みのXずれは起動時に0へ戻す
    LaunchedEffect(Unit) {
        if (discOffsetX != 0f) {
            discOffsetX = 0f
            repo.prefManager.setDiscOffset(0f, discOffsetY)
        }
    }
    var headerOffsetY by remember { mutableFloatStateOf(repo.prefManager.getHeaderOffsetY()) }
    var dateOffsetX by remember { mutableFloatStateOf(repo.prefManager.getDateOffsetX().let { if (it.isNaN()) 40.40f else it }) }
    var dateOffsetY by remember { mutableFloatStateOf(repo.prefManager.getDateOffsetY().let { if (it.isNaN()) (-26.56f) else it }) }
    var batteryOffsetX by remember { mutableFloatStateOf(repo.prefManager.getBatteryOffsetX().let { if (it.isNaN()) (-2.31f) else it }) }
    var batteryOffsetY by remember { mutableFloatStateOf(repo.prefManager.getBatteryOffsetY().let { if (it.isNaN()) 70.73f else it }) }
    var secondsOffsetX by remember { mutableFloatStateOf(repo.prefManager.getSecondsOffsetX().let { if (it.isNaN()) 43.34f else it }) }
    var secondsOffsetY by remember { mutableFloatStateOf(repo.prefManager.getSecondsOffsetY().let { if (it.isNaN()) 46.20f else it }) }
    var clockPartScale by remember { mutableFloatStateOf(repo.prefManager.getLayoutScale("clock")) }
    var datePartScale by remember { mutableFloatStateOf(repo.prefManager.getLayoutScale("date")) }
    var batteryPartScale by remember { mutableFloatStateOf(repo.prefManager.getLayoutScale("battery")) }
    var secondsPartScale by remember { mutableFloatStateOf(repo.prefManager.getLayoutScale("seconds")) }
    var lyricsLineOffsetX by remember { mutableFloatStateOf(repo.prefManager.getLyricsLineOffsetXDp()) }
    var lyricsLineOffsetY by remember { mutableFloatStateOf(repo.prefManager.getLyricsLineOffsetYDp()) }
    var lyricsLinePartScale by remember { mutableFloatStateOf(repo.prefManager.getLayoutScale("lyrics_line")) }
    var lyricsMultiOffsetX by remember { mutableFloatStateOf(repo.prefManager.getLyricsMultiOffsetXDp()) }
    var lyricsMultiOffsetY by remember { mutableFloatStateOf(repo.prefManager.getLyricsMultiOffsetYDp()) }
    var lyricsMultiPartScale by remember { mutableFloatStateOf(repo.prefManager.getLayoutScale("lyrics_multi")) }
    val headerTopDp = topPaddingDp + headerOffsetY.dp
    var clockOffsetY by remember { mutableFloatStateOf(repo.prefManager.getClockOffsetYDp()) }
    var pvOffsetX by remember { mutableFloatStateOf(repo.prefManager.getPvOffsetXDp()) }
    var pvWidthDp by remember { mutableFloatStateOf(repo.prefManager.getPvWidth()) }
    var pvHeightDp by remember { mutableFloatStateOf(repo.prefManager.getPvHeight()) }
    val pvEffWidth = if (pvWidthDp.isNaN()) screenWidthDp - 56f else pvWidthDp
    val pvEffHeight = if (pvHeightDp.isNaN()) 200f else pvHeightDp
    var tonearmScale by remember { mutableFloatStateOf(repo.prefManager.getTonearmScale()) }
    var pvTextColor by remember { mutableStateOf(repo.prefManager.getPvTextColor()) }
    var pvRuby by remember { mutableStateOf(repo.prefManager.isPvRubyEnabled()) }
    var pvInterludeArt by remember { mutableStateOf(repo.prefManager.isPvInterludeArtEnabled()) }
    var liquidSpeed by remember { mutableFloatStateOf(repo.prefManager.getLiquidSpeed()) }
    var liquidAlpha by remember { mutableFloatStateOf(repo.prefManager.getLiquidAlpha()) }
    var wallpaperBlur by remember { mutableFloatStateOf(repo.prefManager.getWallpaperBlurDp()) }
    var lyricsPvFont by remember { mutableStateOf(repo.prefManager.getLyricsPvFont()) }
    var lyricsEnFont by remember { mutableStateOf(repo.prefManager.getLyricsEnFont()) }
    var lyricsEnCustomPath by remember { mutableStateOf(repo.prefManager.getLyricsEnCustomFontPath()) }
    var lyricsBold by remember { mutableStateOf(repo.prefManager.isLyricsBold()) }
    var pvPartsEntry by remember { mutableStateOf(repo.prefManager.isPvPartsEntryEnabled()) }
    var dopagakiMode by remember { mutableStateOf(repo.prefManager.isDopagakiEnabled()) }
    var pvEnabled by remember { mutableStateOf(repo.prefManager.isPvEnabled()) }
    var dopaBlackFont by remember { mutableStateOf(repo.prefManager.isDopaBlackFontEnabled()) }
    // 色塗り行でのアクセントフラッシュ（液体背景とPVのグローが一瞬その色になる）。一定時間で自動で戻る
    var pvFlashColor by remember { mutableStateOf<Color?>(null) }
    var pvLineIndex by remember { mutableStateOf(0) }
    LaunchedEffect(pvFlashColor) {
        if (pvFlashColor != null) {
            delay(2400)
            pvFlashColor = null
        }
    }
    var isLyricsBrowsing by remember { mutableStateOf(false) }
    var pvFullscreen by remember { mutableStateOf(false) }

    // アプリ内アップデート: 起動時に新ビルドを確認する（6時間に1回まで）
    var updateInfo by remember { mutableStateOf<UpdateChecker.LatestRelease?>(null) }
    var showUpdateDialog by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        val info = UpdateChecker.checkIfDue(context)
        if (info != null) {
            updateInfo = info
            showUpdateDialog = true
        }
    }
    // 歌詞エフェクトデータ: 端末キャッシュを読み込んでから、配信データの更新を確認する
    LaunchedEffect(Unit) {
        LyricEffectsData.init(context)
    }
    val pvActivity = LocalContext.current as? android.app.Activity

    // --- 他社製ウィジェット（AppWidget）: ホストと配置 ---
    val appWidgetManager = remember { android.appwidget.AppWidgetManager.getInstance(context) }
    val appWidgetHost = remember { android.appwidget.AppWidgetHost(context, 42) }
    androidx.compose.runtime.DisposableEffect(Unit) {
        appWidgetHost.startListening()
        onDispose { appWidgetHost.stopListening() }
    }
    var widgetPlacements by remember { mutableStateOf(repo.prefManager.getWidgetPlacements()) }
    var pendingWidgetId by remember { mutableStateOf(-1) }
    var showWidgetPicker by remember { mutableStateOf(false) }
    val addWidgetPlacement: (Int) -> Unit = { wid ->
        val info = appWidgetManager.getAppWidgetInfo(wid)
        val wDp = (info?.minWidth ?: 180).toFloat().coerceIn(80f, 340f)
        val hDp = (info?.minHeight ?: 110).toFloat().coerceIn(60f, 340f)
        val next = widgetPlacements + com.example.nissingeppolauncher.data.WidgetPlacement(wid, 16f, 380f, wDp, hDp)
        widgetPlacements = next
        repo.prefManager.setWidgetPlacements(next)
    }
    val widgetConfigureLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val wid = pendingWidgetId
        if (wid >= 0) {
            if (result.resultCode == android.app.Activity.RESULT_OK) {
                addWidgetPlacement(wid)
            } else {
                appWidgetHost.deleteAppWidgetId(wid)
            }
        }
        pendingWidgetId = -1
    }
    // バインド完了後: 設定画面があるウィジェットは設定へ、なければそのまま配置
    val proceedAfterWidgetBind: (Int) -> Unit = { wid ->
        val info = appWidgetManager.getAppWidgetInfo(wid)
        val configure = info?.configure
        if (configure != null) {
            pendingWidgetId = wid
            widgetConfigureLauncher.launch(
                android.content.Intent(android.appwidget.AppWidgetManager.ACTION_APPWIDGET_CONFIGURE)
                    .setComponent(configure)
                    .putExtra(android.appwidget.AppWidgetManager.EXTRA_APPWIDGET_ID, wid)
            )
        } else {
            addWidgetPlacement(wid)
        }
    }
    val widgetBindLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val wid = pendingWidgetId
        pendingWidgetId = -1
        if (wid >= 0) {
            if (result.resultCode == android.app.Activity.RESULT_OK) {
                proceedAfterWidgetBind(wid)
            } else {
                appWidgetHost.deleteAppWidgetId(wid)
            }
        }
    }
    LaunchedEffect(widgetPlacements) {
        val valid = widgetPlacements.filter { appWidgetManager.getAppWidgetInfo(it.id) != null }
        if (valid.size != widgetPlacements.size) {
            widgetPlacements = valid
            repo.prefManager.setWidgetPlacements(valid)
        }
    }
    LaunchedEffect(pvFullscreen) {
        pvActivity?.requestedOrientation = if (pvFullscreen) {
            android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        } else {
            android.content.pm.ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        }
    }
    var lyricsBrowseStartIndex by remember { mutableIntStateOf(0) }
    var lyricsRefreshTick by remember { mutableIntStateOf(0) }
    var showLyricSearch by remember { mutableStateOf(false) }
    var lyricsSource by remember { mutableStateOf("") }
    // 歌詞の対訳（洋楽向け）: 歌詞ブラウズ内のボタンでON/OFFする
    var lyricTranslations by remember { mutableStateOf<List<String>?>(null) }
    var lyricTranslationsHead by remember { mutableStateOf<List<String>?>(null) }
    var translationEnabled by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        translationEnabled = repo.prefManager.isLyricTranslationEnabled()
    }
    LaunchedEffect(syncedLyricLines, translationEnabled) {
        lyricTranslations = null
        lyricTranslationsHead = null
        if (translationEnabled && syncedLyricLines.isNotEmpty()) {
            // PV・行表示は文のまとまりの全行に同じ訳を出す。全体歌詞は先頭の行だけ
            lyricTranslations = LyricsManager.translateToJapanese(
                context, sessionTrackTitle, sessionTrackArtist, sessionTrackAlbum, syncedLyricLines,
                fillChunkLines = true
            )
            lyricTranslationsHead = LyricsManager.translateToJapanese(
                context, sessionTrackTitle, sessionTrackArtist, sessionTrackAlbum, syncedLyricLines,
                placeAtGroupEnd = true
            )
        }
    }
    var lyricsLoading by remember { mutableStateOf(false) }
    // 左円のディスクラック表示中は右円と下ダイヤルを隠す
    var vinylRackOpen by remember { mutableStateOf(false) }
    // 再生中セッションへシークする（対応していないプレイヤーでは何も起きない）
    fun seekSessionTo(posMs: Long) {
        try {
            val msm = context.getSystemService(android.content.Context.MEDIA_SESSION_SERVICE) as? android.media.session.MediaSessionManager
            val comp = android.content.ComponentName(context, com.example.nissingeppolauncher.service.LauncherMediaListenerService::class.java)
            val controllers = msm?.getActiveSessions(comp)
            val active = controllers?.firstOrNull {
                it.playbackState?.state == android.media.session.PlaybackState.STATE_PLAYING
            } ?: controllers?.firstOrNull()
            active?.transportControls?.seekTo(posMs.coerceAtLeast(0L))
        } catch (e: Exception) {
            // シークできないセッションでは何もしない
        }
    }

    // 再生中セッションの次の曲へ進む（対応していないプレイヤーでは何も起きない）
    fun skipToNextTrack() {
        try {
            val msm = context.getSystemService(android.content.Context.MEDIA_SESSION_SERVICE) as? android.media.session.MediaSessionManager
            val comp = android.content.ComponentName(context, com.example.nissingeppolauncher.service.LauncherMediaListenerService::class.java)
            val controllers = msm?.getActiveSessions(comp)
            val active = controllers?.firstOrNull {
                it.playbackState?.state == android.media.session.PlaybackState.STATE_PLAYING
            } ?: controllers?.firstOrNull()
            active?.transportControls?.skipToNext()
        } catch (e: Exception) {
            // 次へ進めないセッションでは何もしない
        }
    }

    // 再生中セッションの再生／一時停止を切り替える（右円の空き場所タップ用）
    fun toggleSessionPlayback() {
        try {
            val msm = context.getSystemService(android.content.Context.MEDIA_SESSION_SERVICE) as? android.media.session.MediaSessionManager
            val comp = android.content.ComponentName(context, com.example.nissingeppolauncher.service.LauncherMediaListenerService::class.java)
            val controllers = msm?.getActiveSessions(comp)
            val active = controllers?.firstOrNull {
                it.playbackState?.state == android.media.session.PlaybackState.STATE_PLAYING
            } ?: controllers?.firstOrNull()
            val tc = active?.transportControls ?: return
            if (active.playbackState?.state == android.media.session.PlaybackState.STATE_PLAYING) tc.pause() else tc.play()
        } catch (e: Exception) {
            // 操作できないセッションでは何もしない
        }
    }

    // 「前回のページ」を覚える（起動時に開くページ設定用）
    LaunchedEffect(currentLauncherPage) {
        repo.prefManager.setLastPage(currentLauncherPage)
    }

    // 再生中は画面を点けたまま
    LaunchedEffect(keepScreenOn, sessionIsPlaying) {
        view.keepScreenOn = keepScreenOn && sessionIsPlaying
    }

    // ステータスバー歌詞オーバーレイ: 設定がONで権限があればサービスを起動しておく
    LaunchedEffect(Unit) {
        if (repo.prefManager.isLyricsEnabled() && repo.prefManager.isStatusBarLyricsEnabled() && android.provider.Settings.canDrawOverlays(context)) {
            // 自己修復: サービスが死んでいる・応答していないときは、トグルし直さなくても再起動する
            val svc = com.example.nissingeppolauncher.service.LyricsOverlayService
            // 再起動は start の呼び直しだけで行う。stop を挟むと前景サービスの開始が競合し、
            // 制限時間内に前景化できずアプリごと落ちることがあるため、ここでは止めない
            svc.start(context)
        }
    }

    // スリープタイマー: 設定した時刻になったら、再生中のセッションを一時停止する
    LaunchedEffect(Unit) {
        while (true) {
            try {
                val endMs = repo.prefManager.getSleepTimerEndMs()
                sleepRemainingMs = if (endMs > 0L) (endMs - System.currentTimeMillis()).coerceAtLeast(0L) else 0L
                if (endMs > 0L && System.currentTimeMillis() >= endMs) {
                    repo.prefManager.setSleepTimerEndMs(0L)
                    val msmT = context.getSystemService(android.content.Context.MEDIA_SESSION_SERVICE) as? android.media.session.MediaSessionManager
                    val compT = android.content.ComponentName(context, com.example.nissingeppolauncher.service.LauncherMediaListenerService::class.java)
                    val ctrlsT = msmT?.getActiveSessions(compT)
                    val activeT = ctrlsT?.firstOrNull {
                        it.playbackState?.state == android.media.session.PlaybackState.STATE_PLAYING
                    } ?: ctrlsT?.firstOrNull()
                    activeT?.transportControls?.pause()
                }
            } catch (e: Exception) {
                // セッションが取れないときは何もしない
            }
            delay(5000)
        }
    }

    // 権限画面から戻った直後など、画面が再表示されるタイミングでも起動を確認する
    // （初回起動チェックだけだと、許可した後にサービスが始まらないことがあった）
    val overlayLifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    androidx.compose.runtime.DisposableEffect(overlayLifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                com.example.nissingeppolauncher.service.LyricsOverlayService.launcherInForeground = true
                if (repo.prefManager.isLyricsEnabled() && repo.prefManager.isStatusBarLyricsEnabled() && android.provider.Settings.canDrawOverlays(context)) {
                    val svc = com.example.nissingeppolauncher.service.LyricsOverlayService
                    svc.start(context)
                }
            } else if (event == androidx.lifecycle.Lifecycle.Event.ON_PAUSE) {
                com.example.nissingeppolauncher.service.LyricsOverlayService.launcherInForeground = false
            }
        }
        overlayLifecycleOwner.lifecycle.addObserver(observer)
        onDispose { overlayLifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // 再生中セッションの曲情報を定期取得する（歌詞の照合用）
    LaunchedEffect(Unit) {
        val mediaSessionManager = context.getSystemService(android.content.Context.MEDIA_SESSION_SERVICE) as? android.media.session.MediaSessionManager
        val listenerComponent = android.content.ComponentName(context, com.example.nissingeppolauncher.service.LauncherMediaListenerService::class.java)
        while (true) {
            if (!(context.getSystemService(android.content.Context.POWER_SERVICE) as android.os.PowerManager).isInteractive) {
                delay(2000)
                continue
            }
            try {
                val controllers = mediaSessionManager?.getActiveSessions(listenerComponent)
                val activeController = controllers?.firstOrNull {
                    it.playbackState?.state == android.media.session.PlaybackState.STATE_PLAYING
                } ?: controllers?.firstOrNull()
                val metadata = activeController?.metadata
                sessionTrackTitle = metadata?.getString(android.media.MediaMetadata.METADATA_KEY_TITLE) ?: ""
                sessionTrackArtist = metadata?.getString(android.media.MediaMetadata.METADATA_KEY_ARTIST) ?: ""
                sessionTrackAlbum = metadata?.getString(android.media.MediaMetadata.METADATA_KEY_ALBUM) ?: ""
                sessionDurationMs = metadata?.getLong(android.media.MediaMetadata.METADATA_KEY_DURATION) ?: 0L
                sessionIsPlaying = activeController?.playbackState?.state == android.media.session.PlaybackState.STATE_PLAYING
            } catch (e: Exception) {
                sessionTrackTitle = ""
                sessionTrackArtist = ""
                sessionTrackAlbum = ""
                sessionDurationMs = 0L
                sessionIsPlaying = false
            }
            delay(1500)
        }
    }

    // 曲が変わったら同期歌詞を取り直す
    LaunchedEffect(sessionTrackTitle, sessionTrackArtist, lyricsRefreshTick) {
        // 曲が変わったら前の歌詞を即リセットして、読み込み表示にする（音楽アプリと同じ挙動）
        syncedLyricLines = emptyList()
        lyricsSource = ""
        currentLyricLine = ""
        if (sessionTrackTitle.isBlank()) {
            lyricsLoading = false
            return@LaunchedEffect
        }
        lyricsLoading = true
        delay(600)
        val lines = LyricsManager.getSyncedLyrics(
            context, sessionTrackTitle, sessionTrackArtist, sessionTrackAlbum, sessionDurationMs, null
        )
        syncedLyricLines = lines ?: emptyList()
        lyricsSource = if (lines != null) LyricsManager.lastFetchSource else ""
        if (lines == null) currentLyricLine = ""
        lyricOffsetMs = repo.prefManager.getLyricOffsetMs(sessionTrackTitle, sessionTrackArtist)
        lyricsLoading = false
    }

    // 再生位置に合わせて現在の歌詞行を更新する
    LaunchedEffect(syncedLyricLines) {
        if (syncedLyricLines.isEmpty()) return@LaunchedEffect
        val mediaSessionManager = context.getSystemService(android.content.Context.MEDIA_SESSION_SERVICE) as? android.media.session.MediaSessionManager
        val listenerComponent = android.content.ComponentName(context, com.example.nissingeppolauncher.service.LauncherMediaListenerService::class.java)
        // Spotify などはセッションの再生位置の更新が粗く、位置が止まって見えることがある。
        // そのため「最後に信じられた位置＋経過時間」で自前でも位置を進め、
        // セッション側の位置と大きく（3秒超）ズレたらそちらに合わせ直す（シーク追随）
        var anchorPosMs = -1L
        var anchorTimeMs = 0L
        while (true) {
            if (!(context.getSystemService(android.content.Context.POWER_SERVICE) as android.os.PowerManager).isInteractive) {
                delay(2000)
                continue
            }
            try {
                val controllers = mediaSessionManager?.getActiveSessions(listenerComponent)
                val activeController = controllers?.firstOrNull {
                    it.playbackState?.state == android.media.session.PlaybackState.STATE_PLAYING
                } ?: controllers?.firstOrNull()
                val ps = activeController?.playbackState
                if (ps != null) {
                    val nowMs = android.os.SystemClock.elapsedRealtime()
                    val playing = ps.state == android.media.session.PlaybackState.STATE_PLAYING
                    val reportedMs = if (playing) {
                        ps.position + ((nowMs - ps.lastPositionUpdateTime) * ps.playbackSpeed).toLong()
                    } else {
                        ps.position
                    }
                    if (anchorPosMs < 0L || !playing) {
                        anchorPosMs = reportedMs
                        anchorTimeMs = nowMs
                    }
                    var posMs = if (playing) anchorPosMs + (nowMs - anchorTimeMs) else anchorPosMs
                    if (playing && kotlin.math.abs(reportedMs - posMs) > 3000) {
                        anchorPosMs = reportedMs
                        anchorTimeMs = nowMs
                        posMs = reportedMs
                    }
                    sessionPositionMs = posMs
                    // 間奏（次の行まで間が空いている区間）では歌詞の代わりに音符マークを出す
                    val curIdx = syncedLyricLines.indexOfLast { it.timeMs <= posMs }
                    val curLine = syncedLyricLines.getOrNull(curIdx)
                    val nextLine = syncedLyricLines.getOrNull(curIdx + 1)
                    val inInterlude = when {
                        curLine == null -> nextLine != null && (nextLine.timeMs - posMs) > com.example.nissingeppolauncher.ui.lyricInterludeThresholdMs(syncedLyricLines)
                        nextLine == null -> (posMs - curLine.timeMs) > 15000
                        else -> (nextLine.timeMs - curLine.timeMs) > com.example.nissingeppolauncher.ui.lyricInterludeThresholdMs(syncedLyricLines) && (posMs - curLine.timeMs) > 10000
                    }
                    currentLyricLine = when {
                        inInterlude -> "♪"
                        curLine != null -> curLine.text
                        else -> ""
                    }
                }
            } catch (e: Exception) {
                // セッションが取れないときは表示を維持する
            }
            delay(300)
        }
    }

    // 再生中セッションのアートワークを定期取得する（ホームの音楽ウィジェットと同じ方式）
    LaunchedEffect(matchArtwork, useArtworkBg) {
        // 歌詞ヘッダー等の表示にも使うため、背景/アクセント連動がオフでも取得は続ける
        val mediaSessionManager = context.getSystemService(android.content.Context.MEDIA_SESSION_SERVICE) as? android.media.session.MediaSessionManager
        val listenerComponent = android.content.ComponentName(context, com.example.nissingeppolauncher.service.LauncherMediaListenerService::class.java)
        var lastArtKey = ""
        var artMissKey = ""
        var artMissCount = 0
        var artPollTick = 0
        while (true) {
            try {
                val controllers = mediaSessionManager?.getActiveSessions(listenerComponent)
                val activeController = controllers?.firstOrNull {
                    it.playbackState?.state == android.media.session.PlaybackState.STATE_PLAYING
                } ?: controllers?.firstOrNull()
                val metadata = activeController?.metadata
                // 曲が変わったときだけアートワークを取り直す。
                // 毎回Bitmapを読み直すと別インスタンスになって色抽出と背景ぼかしが
                // 回り続けて重くなるため、タイトル/アーティスト/アルバムで判定する。
                // ただし画像がまだ届いていない場合は、届くまで再試行する。
                // プレイヤーは曲名を先に渡して画像を遅れて渡すことがあり、最初の1回で
                // nullを掴んだまま固定すると、その曲の間ずっと壁紙のままになっていた。
                val artKey = listOf(
                    metadata?.getString(android.media.MediaMetadata.METADATA_KEY_TITLE) ?: "",
                    metadata?.getString(android.media.MediaMetadata.METADATA_KEY_ARTIST) ?: "",
                    metadata?.getString(android.media.MediaMetadata.METADATA_KEY_ALBUM) ?: ""
                ).joinToString("|")
                if (artKey.isBlank()) {
                    if (lastArtKey.isNotBlank()) {
                        lastArtKey = ""
                        sessionAlbumArt = null
                    }
                    artMissKey = ""
                    artMissCount = 0
                } else if (artKey != lastArtKey && (artMissKey != artKey || artMissCount < 10 || artPollTick % 10 == 0)) {
                    val loaded = withContext(Dispatchers.IO) {
                        try {
                            metadata?.getBitmap(android.media.MediaMetadata.METADATA_KEY_ALBUM_ART)
                                ?: metadata?.getBitmap(android.media.MediaMetadata.METADATA_KEY_ART)
                                ?: metadata?.getBitmap(android.media.MediaMetadata.METADATA_KEY_DISPLAY_ICON)
                                ?: run {
                                    val uriStr = metadata?.getString(android.media.MediaMetadata.METADATA_KEY_ALBUM_ART_URI)
                                        ?: metadata?.getString(android.media.MediaMetadata.METADATA_KEY_ART_URI)
                                        ?: metadata?.getString(android.media.MediaMetadata.METADATA_KEY_DISPLAY_ICON_URI)
                                    val uri = uriStr?.let { Uri.parse(it) }
                                    when {
                                        uri == null -> null
                                        uriStr != null && uriStr.startsWith("spotify:image:") -> try {
                                            java.net.URL("https://i.scdn.co/image/" + uriStr.removePrefix("spotify:image:")).openStream().use { BitmapFactory.decodeStream(it) }
                                        } catch (e: Exception) {
                                            null
                                        }
                                        uri.scheme == "http" || uri.scheme == "https" -> try {
                                            java.net.URL(uriStr).openStream().use { BitmapFactory.decodeStream(it) }
                                        } catch (e: Exception) {
                                            null
                                        }
                                        else -> try {
                                            context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it) }
                                        } catch (e: Exception) {
                                            null
                                        }
                                    }
                                }
                        } catch (e: Exception) {
                            null
                        }
                    }
                    val effectiveLoaded = loaded ?: run {
                        // セッションから取れなかったときは、メディア通知のジャケットで代用する
                        val nArt = LauncherMediaListenerService.mediaArtBitmap
                        val nTitle = LauncherMediaListenerService.mediaArtTitle
                        val curTitle = metadata?.getString(android.media.MediaMetadata.METADATA_KEY_TITLE) ?: ""
                        if (nArt != null && LauncherMediaListenerService.mediaArtPackage == activeController?.packageName &&
                            (nTitle.isNullOrBlank() || curTitle.isBlank() || nTitle == curTitle)
                        ) nArt else null
                    }
                    if (effectiveLoaded != null) {
                        lastArtKey = artKey
                        sessionAlbumArt = effectiveLoaded
                        artMissKey = ""
                        artMissCount = 0
                    } else {
                        if (artMissKey != artKey) {
                            // この曲で初めて取りに行って空だった。前の曲の画像は残さない
                            artMissKey = artKey
                            artMissCount = 0
                            sessionAlbumArt = null
                        }
                        artMissCount++
                        // 打ち切らずに、間隔を空けて取り続ける（プレイヤーが遅れて画像を渡すことがある）
                    }
                }
            } catch (e: Exception) {
                // セッションが読めないときは今の表示を維持する
            }
            artPollTick++
            delay(1500)
        }
    }
    LaunchedEffect(sessionAlbumArt, matchArtwork, useArtworkBg) {
        val art = sessionAlbumArt
        if ((matchArtwork || useArtworkBg) && art != null) {
            val (fg, sub, third, bg) = withContext(Dispatchers.Default) { extractArtworkColors(art) }
            artworkAccent = fg
            artworkSub = sub
            artworkThird = third
            artworkBackground = bg
        } else {
            artworkAccent = null
            artworkSub = null
            artworkThird = null
            artworkBackground = null
        }
    }
    // 配色の優先順位: アクセントカラーが「自動」のときは、再生中はアートワーク、普段は壁紙（各設定がオンのとき）。
    // 固定色のときは常にその色。アートワークの色が無いときは壁紙へフォールバックする
    val paletteAccent = when {
        matchArtwork && artworkAccent != null -> artworkAccent
        matchWallpaper && wallpaperAccent != null -> wallpaperAccent
        else -> null
    }
    val paletteBackground = when {
        matchArtwork && artworkAccent != null -> artworkBackground
        matchWallpaper && wallpaperAccent != null -> wallpaperBackground
        else -> null
    }
    val autoAccent = accentHex.isEmpty()
    val accentColor = if (!autoAccent) {
        manualAccentColor
    } else if (paletteAccent != null) {
        val extracted = paletteAccent
        val bg = paletteBackground
        if (bg != null && contrastRatio(extracted, bg) < 2.8f) {
            // コントラストが低い場合は白へ混ぜず、同じ色相・彩度のまま明度だけを段階的に上げる
            val bgNN: Color = bg
            var adjusted: Color = extracted
            var guard = 0
            while (contrastRatio(adjusted, bgNN) < 3.0f && guard < 12) {
                guard++
                val next = brightenStepKeepingHue(adjusted)
                if (next == adjusted) break
                adjusted = next
            }
            adjusted
        } else {
            extracted
        }
    } else {
        Color(0xFFE53935)
    }
    // サブ色: アートワーク連動時は抽出した第2色、そうでなければ一次色の色相を55度回した色
    val subColor = manualSubColor ?: if (matchArtwork && artworkSub != null) artworkSub!! else if (matchWallpaper && wallpaperSub != null) wallpaperSub!! else ensureSubDistinct(accentColor, rotateHueColor(accentColor, 55f))
    // 第3色（装飾専用）: 手動指定が最優先。自動では抽出の第3色、無ければトライアド（120度/240度）のうちサブから遠い方
    val thirdColor = manualThirdColor ?: if (matchArtwork && artworkThird != null) artworkThird!! else if (matchWallpaper && wallpaperThird != null) wallpaperThird!! else {
        val t120 = rotateHueColor(accentColor, 120f)
        val t240 = rotateHueColor(accentColor, 240f)
        if (hueDistanceOf(t120, subColor) >= hueDistanceOf(t240, subColor)) t120 else t240
    }

    // 色の切り替わりはチカチカしないよう、800msかけてなめらかに寄せる
    val effectiveAccent by animateColorAsState(
        targetValue = pvFlashColor ?: accentColor,
        animationSpec = tween(durationMillis = 800),
        label = "accentFlash"
    )

    val effectiveSub by animateColorAsState(
        targetValue = pvFlashColor?.let { rotateHueColor(it, 55f) } ?: subColor,
        animationSpec = tween(durationMillis = 800),
        label = "subColorAnim"
    )

    val effectiveThird by animateColorAsState(
        targetValue = thirdColor,
        animationSpec = tween(durationMillis = 800),
        label = "thirdColorAnim"
    )

    var showWelcomeTutorial by remember { mutableStateOf(false) }
    var showIndexTutorial by remember { mutableStateOf(false) }
    var showHideTutorial by remember { mutableStateOf(false) }

    // ページが切り替わるたびにアクセントカラー等を自動再読込
    LaunchedEffect(currentLauncherPage) {
        accentHex = repo.prefManager.getAccentColorHex()
        subHex = repo.prefManager.getSubColorHex()
        thirdHex = repo.prefManager.getThirdColorHex()
        monoColorMode = repo.prefManager.getMonoColorMode()
        matchArtwork = repo.prefManager.isMatchArtworkColor()
        matchWallpaper = repo.prefManager.isMatchWallpaperColor()
        bgMode = repo.prefManager.getBackgroundMode()
        homeDiscLayout = repo.prefManager.getHomeDiscLayout()
        disc3d = repo.prefManager.isDisc3dEnabled()
        disc3dTilt = repo.prefManager.isDisc3dTiltEnabled()
        liquidBackground = repo.prefManager.isLiquidBackgroundEnabled()
        dateDisplayMode = repo.prefManager.getDateDisplayMode()
        if (currentLauncherPage == 1) {
            if (!repo.prefManager.isFirstWelcomeTutorialShown()) {
                showWelcomeTutorial = true
            }
        } else if (currentLauncherPage == 2) {
            if (!repo.prefManager.isFirstIndexTutorialShown()) {
                showIndexTutorial = true
            } else if (!repo.prefManager.isFirstHideTutorialShown()) {
                showHideTutorial = true
            }
        }
    }

    var searchQuery by remember { mutableStateOf("") }
    var isSearchActive by remember { mutableStateOf(false) }

    // ページ切り替え時の一時的引っ込みアニメーション用状態
    var isTransitioning by remember { mutableStateOf(false) }

    var customBackgroundPath by remember { mutableStateOf(repo.prefManager.getCustomBackgroundPath()) }
    var nightWallpaperActive by remember { mutableStateOf(repo.prefManager.isNightWallpaperEnabled() && repo.prefManager.getNightWallpaperPath() != null && repo.prefManager.isNightTimeNow()) }
    LaunchedEffect(Unit) {
        while (true) {
            nightWallpaperActive = repo.prefManager.isNightWallpaperEnabled() && repo.prefManager.getNightWallpaperPath() != null && repo.prefManager.isNightTimeNow()
            delay(60000)
        }
    }
    LaunchedEffect(Unit) {
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            try { LyricsManager.enforceLyricsCacheLimit(context) } catch (_: Exception) { }
        }
    }
    var backgroundUpdateKey by remember { mutableLongStateOf(System.currentTimeMillis()) }

    // 壁紙に合わせる（音楽を再生していない普段の配色）: 現在表示中の壁紙から色を抽出する
    val currentWallpaperPath = if (nightWallpaperActive) repo.prefManager.getNightWallpaperPath() else (repo.prefManager.getPageWallpaperPath(currentLauncherPage) ?: customBackgroundPath)
    LaunchedEffect(matchWallpaper, currentWallpaperPath, backgroundUpdateKey) {
        if (matchWallpaper && currentWallpaperPath != null) {
            val colors = withContext(Dispatchers.IO) {
                try {
                    val bmp = android.graphics.BitmapFactory.decodeFile(currentWallpaperPath)
                    if (bmp != null) {
                        val small = android.graphics.Bitmap.createScaledBitmap(bmp, 48, 48, true)
                        val c = extractArtworkColors(small)
                        if (small !== bmp) small.recycle()
                        bmp.recycle()
                        c
                    } else null
                } catch (e: Exception) { null }
            }
            wallpaperAccent = colors?.primary
            wallpaperSub = colors?.secondary
            wallpaperThird = colors?.third
            wallpaperBackground = colors?.background
        } else {
            wallpaperAccent = null
            wallpaperSub = null
            wallpaperThird = null
            wallpaperBackground = null
        }
    }

    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            try {
                val bitmap = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    ImageDecoder.decodeBitmap(ImageDecoder.createSource(context.contentResolver, uri))
                } else {
                    @Suppress("DEPRECATION")
                    MediaStore.Images.Media.getBitmap(context.contentResolver, uri)
                }
                val destFile = File(context.filesDir, "custom_bg.png")
                FileOutputStream(destFile).use { out ->
                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
                }
                val path = destFile.absolutePath
                repo.prefManager.setCustomBackgroundPath(path)
                customBackgroundPath = path
            } catch (_: Exception) {}
        }
    }

    var selectedAppForOption by remember { mutableStateOf<AppInfo?>(null) }

    var allApps by remember { mutableStateOf<List<AppInfo>>(emptyList()) }
    var favoriteApps by remember { mutableStateOf<List<AppInfo>>(emptyList()) }
    var showSettingsScreen by remember { mutableStateOf(false) }

    // --- フォルダ管理状態 (Page 2 用) ---
    var foldersMap by remember { mutableStateOf(repo.prefManager.getFoldersMap()) }
    var favoriteFolderIds by remember { mutableStateOf(repo.prefManager.getFavoriteFolderIds()) }
    var folderIcons by remember { mutableStateOf(repo.prefManager.getFolderIcons()) }
    var folderOptionTargetId by remember { mutableStateOf<String?>(null) }
    var showFavReorderDialog by remember { mutableStateOf(false) }
    var favSortList by remember { mutableStateOf<List<HomeEntry>>(emptyList()) }
    var favoriteOrder by remember { mutableStateOf(repo.prefManager.getFavoriteOrder()) }
    var favDragKey by remember { mutableStateOf<String?>(null) }
    var favDragOffset by remember { mutableFloatStateOf(0f) }
    var showFolderAppSelectDialog by remember { mutableStateOf(false) }
    var showDeleteFolderDialog by remember { mutableStateOf(false) }

    var isEditingFolder by remember { mutableStateOf(false) }
    var editingFolderNameInput by remember { mutableStateOf("") }
    var editingSelectedPackages by remember { mutableStateOf<Set<String>>(emptySet()) }

    var isHideMode by remember { mutableStateOf(false) }
    var hideDimStep by remember { mutableIntStateOf(0) }
    var showPasscodeDialog by remember { mutableStateOf(false) }
    var indexTapCount by remember { mutableIntStateOf(0) }
    var lastIndexTapMs by remember { mutableLongStateOf(0L) }

    var newFolderNameInput by remember { mutableStateOf("") }

    // --- フォルダ多重階層の状態 ---
    var folderParents by remember { mutableStateOf(repo.prefManager.getFolderParents()) }
    var folderPath by remember {
        mutableStateOf(run {
            val saved = repo.prefManager.getFolderPath()
            val parents = repo.prefManager.getFolderParents()
            val valid = mutableListOf<String>()
            var expectedParent: String? = null
            for (id in saved) {
                if (id !in foldersMap) break
                if (parents[id] != expectedParent) break
                valid.add(id)
                expectedParent = id
            }
            valid.toList()
        })
    }
    LaunchedEffect(folderPath) {
        repo.prefManager.saveFolderPath(folderPath)
    }
    var pendingFocusFolderId by remember { mutableStateOf<String?>(null) }
    // フォルダ切り替え時、右円を引っ込ませてから中身を差し替える（設定画面の navigateAnimated と同じ作法）
    var folderDiscExiting by remember { mutableStateOf(false) }
    var folderNavBusy by remember { mutableStateOf(false) }
    fun navigateFolderAnimated(change: () -> Unit) {
        if (folderNavBusy) return
        folderNavBusy = true
        coroutineScope.launch {
            folderDiscExiting = true
            kotlinx.coroutines.delay(175)
            change()
            folderDiscExiting = false
            folderNavBusy = false
        }
    }
    var showCreateFolderDialog by remember { mutableStateOf(false) }
    var createFolderParentId by remember { mutableStateOf<String?>(null) }

    val folderList = remember(foldersMap) {
        foldersMap.map { (id, pair) -> Pair(id, pair.first) }
    }

    // Page 2 左ディスク (フォルダ選択ダイヤル) の回転角度
    val folderWheelRotationAnim = remember { Animatable(0f) }
    val folderWheelRotation = folderWheelRotationAnim.value
    var isHoldingFolderWheel by remember { mutableStateOf(false) }
    // 共通部品（DiscCarousel）化した円盤の状態（フォルダ左・フォルダ右・アプリー覧右・索引）
    val folderDiscState = rememberDiscCarouselState(initialIndex = 0, stepAngle = 24f)
    var folderSelectedIndex by remember { mutableIntStateOf(0) }
    val folderAppDiscState = rememberDiscCarouselState(initialIndex = 0, stepAngle = 20f)
    val appListDiscState = rememberDiscCarouselState(initialIndex = 0, stepAngle = 20f)
    val homeDiscState = rememberDiscCarouselState(initialIndex = 0, stepAngle = 20f)
    val badgeCounts by com.example.nissingeppolauncher.service.LauncherMediaListenerService.badgeCounts.collectAsState()
    val effBadges = if (notificationBadges) badgeCounts else emptyMap()
    val homeDiscItems = remember(favoriteApps, effBadges) { buildDiscItems(favoriteApps, false, effBadges) }
    // ホーム右円にはお気に入りアプリの後ろにお気に入りフォルダも並べる
    val homeFavoriteFolders = remember(favoriteFolderIds, folderList, folderIcons) {
        favoriteFolderIds.mapNotNull { fid -> folderList.firstOrNull { it.first == fid }?.let { FolderDiscEntry.Folder(it.first, it.second, folderIcons[it.first] ?: "") } }
    }
    // アプリとフォルダを共通の順序（favoriteOrder）で混ぜて並べる。順序に無いものは末尾へ
    val homeEntries = remember(homeDiscItems, homeFavoriteFolders, favoriteOrder) {
        val byKey = LinkedHashMap<String, HomeEntry>()
        homeDiscItems.forEach { di ->
            (di as? DiscItem.AppEntry)?.let { byKey["a:" + it.app.packageName] = HomeEntry.App(it.app, di) }
        }
        homeFavoriteFolders.forEach { fe -> byKey["f:" + fe.folderId] = HomeEntry.Folder(fe) }
        val ordered = mutableListOf<HomeEntry>()
        val used = HashSet<String>()
        for (key in favoriteOrder) {
            val e = byKey[key]
            if (e != null && used.add(key)) ordered.add(e)
        }
        for ((key, e) in byKey) {
            if (used.add(key)) ordered.add(e)
        }
        ordered
    }
    // お気に入りが2件以上あるときは、ホーム右円の末尾に「並び替え」要素を1つ足す
    val favEditItemCount = if (homeEntries.size >= 2) 1 else 0
    val homeDiscCount = homeEntries.size + favEditItemCount
    // よく使う順のときの★5段階（使用実績のあるアプリを使用量の五分位で分ける。実績なしは★）
    val usageTierOf: Map<String, Int> = remember(usageStatsMap, allApps) {
        val used = allApps.filter { (usageStatsMap[it.packageName] ?: 0L) > 0L }
            .sortedByDescending { usageStatsMap[it.packageName] ?: 0L }
        val n = used.size
        val map = HashMap<String, Int>()
        if (n > 0) {
            used.forEachIndexed { rank, app ->
                map[app.packageName] = 5 - minOf(4, (rank * 5) / n)
            }
        }
        map
    }
    val usageIndexActive = appSortMode == "usage" && searchQuery.isBlank() && !isHideMode
    // 最近使った順のときのインデックス（今日・昨日・○日前…。使用状況は直近30日分まで）
    val recentLabelOf: Map<String, String> = remember(usageStatsMap, allApps) {
        fun dayStart(t: Long): Long {
            val c = java.util.Calendar.getInstance()
            c.timeInMillis = t
            c.set(java.util.Calendar.HOUR_OF_DAY, 0)
            c.set(java.util.Calendar.MINUTE, 0)
            c.set(java.util.Calendar.SECOND, 0)
            c.set(java.util.Calendar.MILLISECOND, 0)
            return c.timeInMillis
        }
        val todayStart = dayStart(System.currentTimeMillis())
        val map = HashMap<String, String>()
        for (app in allApps) {
            val ts = usageStatsMap[app.packageName] ?: 0L
            map[app.packageName] = if (ts <= 0L) {
                "未使用"
            } else {
                val days = Math.round((todayStart - dayStart(ts)).toDouble() / 86400000.0).toInt().coerceAtLeast(0)
                when {
                    days <= 0 -> "今日"
                    days == 1 -> "昨日"
                    days in 2..6 -> "${days}日前"
                    days in 7..29 -> "1週間以上前"
                    else -> "1か月以上前"
                }
            }
        }
        map
    }
    val recentIndexActive = appSortMode == "recent" && searchQuery.isBlank() && !isHideMode
    val recentIndexOrder = listOf("今日", "昨日", "2日前", "3日前", "4日前", "5日前", "6日前", "1週間以上前", "1か月以上前", "未使用")
    val indexBaseItems = if (indexMode == "kana") INDEX_ITEMS_KANA else INDEX_ITEMS
    val indexItems = when {
        usageIndexActive -> if (appSortDescending) listOf("★★★★★", "★★★★", "★★★", "★★", "★") else listOf("★", "★★", "★★★", "★★★★", "★★★★★")
        recentIndexActive -> {
            val present = recentIndexOrder.filter { label -> allApps.any { !it.isHidden && (recentLabelOf[it.packageName] ?: "未使用") == label } }
            if (appSortDescending) present else present.reversed()
        }
        else -> {
            val base = if (indexShowEnglish) indexBaseItems else indexBaseItems.filter { it[0].code > 128 }
            if (!indexHideEmpty) base else base.filter { c ->
                allApps.any { app ->
                    if (app.isHidden) false else {
                        val g = app.discGroup
                        val eff = when {
                            g.length == 1 && g[0] in 'A'..'Z' -> if (indexShowEnglish) g else "＃"
                            indexMode == "kana" -> app.discKana
                            else -> g
                        }
                        eff.equals(c, ignoreCase = true)
                    }
                }
            }.ifEmpty { base }
        }
    }
    // 索引グループの実効値: 英語オフなら英語グループ（A〜Z）は＃へ寄せ、あいうえおモードなら文字単位のグループを使う。
    // 右円（アプリ円盤）の見出しとジャンプ先はこの実効値で揃える
    val indexGroupOf: (AppInfo) -> String = { app ->
        if (usageIndexActive) {
            "★".repeat(usageTierOf[app.packageName] ?: 1)
        } else if (recentIndexActive) {
            recentLabelOf[app.packageName] ?: "未使用"
        } else {
            val g = app.discGroup
            when {
                g.length == 1 && g[0] in 'A'..'Z' -> if (indexShowEnglish) g else "＃"
                indexMode == "kana" -> app.discKana
                else -> g
            }
        }
    }
    val indexDiscState = key(indexItems.size) { rememberDiscCarouselState(initialIndex = 0, stepAngle = 360f / indexItems.size) }
    val indexEntries = remember(indexItems) { indexItems.map { DiscEntry(id = it, label = it, icon = {}) } }
    var indexSelected by remember { mutableIntStateOf(repo.prefManager.getAppListIndexChar()?.let { c -> indexItems.indexOf(c) }?.takeIf { it >= 0 } ?: 0) }
    fun snapDiscTo(state: DiscCarouselState, angle: Float) {
        state.rawRotation = angle
        coroutineScope.launch { state.rotationAnim.snapTo(angle) }
    }
    val folderStepAngle = repo.prefManager.getAppDiscStepAngle()

    val createNewFolderStr = "新規作成"
    val currentFolderParentId = folderPath.lastOrNull()
    val childFolders = remember(folderList, folderParents, currentFolderParentId) {
        folderList.filter { (folderParents[it.first]) == currentFolderParentId }
    }
    val folderItemsWithNew = remember(childFolders, createNewFolderStr, folderPath) {
        childFolders + Pair("NEW_FOLDER", createNewFolderStr) +
            (if (folderPath.isNotEmpty()) listOf(Pair("__BACK", "戻る")) else emptyList())
    }
    // フォルダの左円も共通部品 DiscWheel で描く（設定・索引と仕様を統一）
    val folderEntries = remember(folderItemsWithNew, folderIcons) {
        folderItemsWithNew.map { (fid, fname) ->
            when (fid) {
                "NEW_FOLDER" -> DiscEntry(id = fid, label = fname, icon = { WhitePlusIcon(size = 36.dp, color = Color.White) })
                "__BACK" -> DiscEntry(id = fid, label = fname, immediate = true, icon = { DiscGlyph("back", accentColor) })
                else -> DiscEntry(id = fid, label = fname, icon = {
                    val kind = folderIcons[fid] ?: ""
                    Box(contentAlignment = Alignment.Center) {
                        WhiteFolderIcon(size = 36.dp, color = Color.White)
                        if (kind.isNotBlank()) {
                            Box(modifier = Modifier.offset(y = 4.dp)) {
                                DiscGlyph(kind, Color.White, sizeDp = 15f)
                            }
                        }
                    }
                })
            }
        }
    }

    // 階層を移動したら、移動先で対象フォルダ（潜った先・戻る前にいたフォルダ）にフォーカスを合わせる
    LaunchedEffect(folderPath) {
        val target = pendingFocusFolderId
        if (target != null) {
            val idx = folderItemsWithNew.indexOfFirst { it.first == target }
            if (idx >= 0) {
                folderSelectedIndex = idx
                folderDiscState.itemCount = folderItemsWithNew.size
                folderDiscState.animateToIndex(idx)
            }
            pendingFocusFolderId = null
        }
    }

    val selectedFolderItemIndex = if (folderItemsWithNew.isEmpty()) -1 else folderSelectedIndex.coerceIn(0, folderItemsWithNew.size - 1)

    val selectedFolderPair = remember(selectedFolderItemIndex, folderItemsWithNew) {
        if (selectedFolderItemIndex in folderItemsWithNew.indices) folderItemsWithNew[selectedFolderItemIndex] else null
    }

    val defaultFolderName = "フォルダ"
    val selectedFolderId = selectedFolderPair?.first
    val selectedFolderName = selectedFolderPair?.second ?: defaultFolderName

    fun refreshApps() {
        coroutineScope.launch {
            val installed = repo.getInstalledApps()
            allApps = installed
            val favAppOrder = repo.prefManager.getFavoriteAppOrder()
            favoriteApps = installed.filter { it.isFavorite && !it.isHidden }
                .sortedBy { val i = favAppOrder.indexOf(it.packageName); if (i >= 0) i else Int.MAX_VALUE }
            foldersMap = repo.prefManager.getFoldersMap()
            folderParents = repo.prefManager.getFolderParents()
        }
    }

    LaunchedEffect(Unit) {
        refreshApps()
    }
    // アプリのインストール・アンインストール・更新を検知して一覧を自動で最新化する
    DisposableEffect(Unit) {
        val receiver = object : android.content.BroadcastReceiver() {
            override fun onReceive(receiverContext: android.content.Context?, intent: android.content.Intent?) {
                AppRepository.invalidateCache()
                refreshApps()
            }
        }
        val filter = android.content.IntentFilter().apply {
            addAction(android.content.Intent.ACTION_PACKAGE_ADDED)
            addAction(android.content.Intent.ACTION_PACKAGE_REMOVED)
            addAction(android.content.Intent.ACTION_PACKAGE_CHANGED)
            addAction(android.content.Intent.ACTION_EXTERNAL_APPLICATIONS_AVAILABLE)
            addAction(android.content.Intent.ACTION_EXTERNAL_APPLICATIONS_UNAVAILABLE)
            addDataScheme("package")
        }
        androidx.core.content.ContextCompat.registerReceiver(
            context,
            receiver,
            filter,
            androidx.core.content.ContextCompat.RECEIVER_NOT_EXPORTED
        )
        onDispose {
            try {
                context.unregisterReceiver(receiver)
            } catch (_: Exception) {
            }
        }
    }

    // --- 左右の円の固定絶対座標配置 ---
    // ホーム画面では設定により「アートワーク側（左円）を大きく」レイアウトを選べる
    // 大きめレイアウトの寸法は音楽プレーヤーのホーム画面に合わせる（左円: 半径165・中心X=15）
    // レイアウト設定「自動」: 再生中だけ大きめレイアウトに切り替える（日付表示の自動と同じ考え方）
    val isBigDiscLayout = (homeDiscLayout == "artwork" || (homeDiscLayout == "auto" && sessionIsPlaying)) && currentLauncherPage == 1
    val disc1RadiusDp = 165f
    val disc1CenterX = (if (isBigDiscLayout) 15f else -80f) + discOffsetX
    val disc1CenterY = screenHeightDp * 0.50f + discOffsetY

    val disc2RadiusDp = 165f
    val disc2CenterX = screenWidthDp + 80f + discOffsetX
    val disc2CenterY = screenHeightDp * 0.50f + discOffsetY

    // 歌詞のPV／複数行モードでは、音楽アプリと同じように円を下へ逃がす
    val lyricsDiscDrop by animateFloatAsState(
        targetValue = if (discDropOnPv && currentLauncherPage == 1 && lyricsEnabled && !isLyricsBrowsing && syncedLyricLines.isNotEmpty()) {
            when (lyricsDisplayMode) {
                "video" -> if (pvEnabled) 80f else 0f
                "multi" -> 120f
                else -> 0f
            }
        } else 0f,
        animationSpec = tween(durationMillis = 350, easing = FastOutSlowInEasing),
        label = "LyricsDiscDrop"
    )

    // 歌詞表示中は円周テキストを下半分に寄せて、上の歌詞と被らないようにする
    val lyricsTextAngle by animateFloatAsState(
        targetValue = if ((lyricsDisplayMode == "video" || lyricsDisplayMode == "multi") && syncedLyricLines.isNotEmpty()) 0f else -90f,
        animationSpec = tween(durationMillis = 350),
        label = "lyricsTextAngle"
    )

    // --- 左側（索引ダイヤル）の状態管理 ---
    val leftRotationAnim = remember { Animatable(0f) }
    val leftRotation = leftRotationAnim.value
    var isHoldingLeft by remember { mutableStateOf(false) }
    var isHoldingRight by remember { mutableStateOf(false) }
    val stepAngle = 360f / indexItems.size

    val animatedDisc1CenterX by animateFloatAsState(
        targetValue = when {
            isTransitioning -> -disc1RadiusDp
            else -> disc1CenterX
        },
        animationSpec = tween(durationMillis = 220, easing = FastOutSlowInEasing),
        label = "Disc1CenterX"
    )

    val animatedDisc2CenterX by animateFloatAsState(
        targetValue = when {
            isTransitioning -> screenWidthDp + disc2RadiusDp
            else -> disc2CenterX
        },
        animationSpec = tween(durationMillis = 220, easing = FastOutSlowInEasing),
        label = "Disc2CenterX"
    )

    val selectedIndexChar = if (indexSelected in indexItems.indices) indexItems[indexSelected] else null
    LaunchedEffect(selectedIndexChar) {
        selectedIndexChar?.let { repo.prefManager.saveAppListIndexChar(it) }
    }

    val onAppClick: (String) -> Unit = { packageName ->
        coroutineScope.launch {
            val intent = context.packageManager.getLaunchIntentForPackage(packageName)
            if (intent != null) {
                context.startActivity(intent)
            }
        }
    }

    fun changePageWithTransition(targetPage: Int) {
        if (currentLauncherPage == targetPage || isTransitioning) return
        coroutineScope.launch {
            isHoldingLeft = false
            isHoldingRight = false
            isHoldingFolderWheel = false
            isTransitioning = true
            delay((220f / repo.prefManager.getPageTransitionSpeed()).toLong().coerceAtLeast(60L))
            currentLauncherPage = targetPage
            isSearchActive = false
            searchQuery = ""
            keyboardController?.hide()
            delay(60)
            isTransitioning = false
        }
    }

    val transitionAlpha by animateFloatAsState(
        targetValue = if (isTransitioning) 0.0f else 1.0f,
        animationSpec = tween(durationMillis = (220f / pageTransitionSpeed).toInt().coerceAtLeast(60), easing = FastOutSlowInEasing),
        label = "TransitionAlpha"
    )

    val appDiscIconSize = repo.prefManager.getAppDiscIconSizeDp()
    val iconCornerPercent = (repo.prefManager.getIconCornerRatio() * 50).toInt()
    val appDiscTextSize = repo.prefManager.getAppDiscTextSizeSp()
    val appDiscLetterSpacing = repo.prefManager.getAppDiscLetterSpacingSp()
    val appDiscStepAngle = repo.prefManager.getAppDiscStepAngle()

    var fontChoice by remember { mutableStateOf(repo.prefManager.getFontChoice()) }
    var customFontPath by remember { mutableStateOf(repo.prefManager.getCustomFontPath()) }
    var useCustomFont by remember { mutableStateOf(repo.prefManager.isUseCustomFont()) }

    val currentFontFamily = remember(fontChoice, customFontPath, useCustomFont) {
        parseFontFamily(fontChoice, if (useCustomFont) customFontPath else null)
    }

    val lyricsFontFamily = parseFontFamily(lyricsPvFont, if (lyricsPvFont == "custom") customFontPath else null)
    // ドパガキ中（設定ON時）は歌詞を Zen Kaku Gothic New の極太で出す
    val pvFontFamily = if (dopagakiMode && dopaBlackFont) ZenKakuBlackFontFamily else lyricsFontFamily
    // ドパガキ中は歌詞行を短い断片に自動分割する（切り替わり頻度とサイズを上げる）。対訳は断片の先頭にだけ付ける
    // 英字フォント: "same" のときは日本語フォントと同じ（=混合しないので null を渡す）
    val lyricsEnFontFamily = if (lyricsEnFont == "same") lyricsFontFamily else parseFontFamily(lyricsEnFont, if (lyricsEnFont == "custom") lyricsEnCustomPath else null)
    val lyricsEnFontArg = if (lyricsEnFont == "same") null else lyricsEnFontFamily
    val lyricWeight = if (lyricsBold) FontWeight.Bold else FontWeight.Normal

    CompositionLocalProvider(
        LocalTextStyle provides LocalTextStyle.current.copy(fontFamily = currentFontFamily),
        LocalLauncherFontFamily provides currentFontFamily
    ) {
        Box(
            modifier = Modifier.fillMaxSize().pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent(PointerEventPass.Initial)
                        if (event.changes.any { it.pressed && !it.previousPressed }) {
                            dialLastTouchMs = System.currentTimeMillis()
                        }
                    }
                }
            }
        ) {
        // 歌詞ブラウズ中はホームの要素をそのまま残し、背面全体をぼかす（ブラウズ表示だけは外に出して鮮明に保つ）
        // ぼかしはポップアップのようにフェードで効かせる
        val browseBlurRadius by animateFloatAsState(
            targetValue = if (isLyricsBrowsing) 22f else 0f,
            animationSpec = tween(durationMillis = 320),
            label = "browseBlur"
        )
        Box(
            modifier = Modifier.fillMaxSize().graphicsLayer {
                val r = browseBlurRadius
                renderEffect = if (r > 0.5f && android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                    androidx.compose.ui.graphics.BlurEffect(r, r, androidx.compose.ui.graphics.TileMode.Clamp)
                } else {
                    null
                }
            }
        ) {
                WallpaperBackground(
                    liquidSpeed = liquidSpeed,
                    liquidAlphaMul = liquidAlpha,
                    wallpaperBlurDp = wallpaperBlur,
                    customPath = if (nightWallpaperActive) repo.prefManager.getNightWallpaperPath() else (repo.prefManager.getPageWallpaperPath(currentLauncherPage) ?: customBackgroundPath),
                    updateKey = backgroundUpdateKey,
                    isHome = (currentLauncherPage == 1),
                    currentAlbumArt = if (matchArtwork || useArtworkBg) sessionAlbumArt else null,
                    dynamicBackground = if (useArtworkBg) artworkBackground else if (autoAccent && matchArtwork) artworkBackground else if (autoAccent && matchWallpaper) wallpaperBackground else null,
                    artworkBackgroundStyle = if (bgMode == "artwork_blur") "blur" else "solid",
                    liquidEnabled = liquidBackground,
                    liquidAccent = if (dopagakiMode) Color.hsv(Math.floorMod(pvLineIndex * 47, 360).toFloat(), 0.72f, 1f) else effectiveAccent,
                    liquidSub = if (dopagakiMode) Color.hsv(Math.floorMod(pvLineIndex * 47 + 120, 360).toFloat(), 0.72f, 1f) else effectiveSub,
                    liquidThird = if (dopagakiMode) Color.hsv(Math.floorMod(pvLineIndex * 47 + 240, 360).toFloat(), 0.72f, 1f) else effectiveThird,
                    staticMode = powerSaveActive || isLyricsBrowsing || pvFullscreen
                )

            // 非表示モード中は背景だけを段階的に暗くする（円盤や文字などのUIは暗くしない）
            if (currentLauncherPage == 2 && hideDimStep > 0) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.15f * hideDimStep))
                )
            }


            if (dopagakiMode && currentLauncherPage == 1) {
                // ドパガキ中は画面の縁に虹色の回る枠を出す
            }

            // ===== 歌詞の読み込み中表示 =====
            if (lyricsLoading && currentLauncherPage == 1) {
                Column(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = topPaddingDp + 185.dp)
                        .zIndex(58f),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    CircularProgressIndicator(color = accentColor, modifier = Modifier.size(22.dp))
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "取得中…",
                        color = Color.White.copy(alpha = 0.75f),
                        fontSize = 12.sp,
                        style = LocalTextStyle.current.copy(
                            shadow = Shadow(color = accentColor.copy(alpha = 0.55f), blurRadius = 12f)
                        )
                    )
                }
            }

            // リリックビデオ（文字PV）: 再生中セッション（Spotify等も含む）の歌詞を大きく散らして表示する
            if (lyricsEnabled && pvEnabled && lyricsDisplayMode == "video" && currentLauncherPage == 1 && !isLyricsBrowsing && syncedLyricLines.isNotEmpty()) {
              Box(Modifier.fillMaxSize().offset(x = pvOffsetX.dp).zIndex(if (dopagakiMode) 60f else 0f)) {
                LyricsPvLayer(
                    pvTilt3d = disc3d,
                    lines = syncedLyricLines,
                    positionMs = sessionPositionMs,
                    lyricOffsetMs = lyricOffsetMs,
                    bold = lyricsBold,
                    fontFamily = pvFontFamily,
                    accentColor = effectiveAccent,
                    screenWidthDp = pvEffWidth,
                    translationEnabled = translationEnabled,
                    translations = lyricTranslations,
                    pvFontScale = pvFontScale * (pvEffHeight / 200f),
                    pvBaseColor = when (pvTextColor) { "accent" -> effectiveAccent; "black" -> Color.Black; else -> Color.White },
                    pvRuby = pvRuby,
                    pvInterludeArt = pvInterludeArt,
                    pvTopDp = topPaddingDp + 165.dp + pvTopOffset.dp,
                    interludeTopDp = topPaddingDp + 145.dp,
                    translationTopDp = topPaddingDp + 320.dp,
                    partsEntry = pvPartsEntry,
                    dopagaki = dopagakiMode,
                    enFontFamily = lyricsEnFontArg,
                    tiltX = discTiltX,
                    tiltY = discTiltY,
                    onAccentFlash = { pvFlashColor = it },
                    onSeek = { pos -> seekSessionTo(pos) },
                    onNextTrack = { skipToNextTrack() },
                    onLineChange = { pvLineIndex = it }
                )
              }
            }

            // レイアウト編集モードの各種ハンドル（別コンポーザブルに分離）
            LayoutEditHandles(
                repo = repo, accentColor = accentColor, layoutEditMode = layoutEditMode, currentPage = currentLauncherPage,
                topPaddingDp = topPaddingDp, headerTopDp = headerTopDp, screenWidthDp = screenWidthDp,
                pvOffset = rememberUpdatedState(pvOffsetX), onPvOffsetChange = { pvOffsetX = it },
                pvTop = rememberUpdatedState(pvTopOffset), onPvTopChange = { pvTopOffset = it },
                pvWidth = rememberUpdatedState(pvWidthDp), onPvWidthChange = { pvWidthDp = it },
                pvHeight = rememberUpdatedState(pvHeightDp), onPvHeightChange = { pvHeightDp = it },
                pvEffWidth = pvEffWidth, pvEffHeight = pvEffHeight,
                disc1CenterX = disc1CenterX, disc1CenterY = disc1CenterY, disc2CenterX = disc2CenterX, disc2CenterY = disc2CenterY,
                discOffsetY = rememberUpdatedState(discOffsetY), onDiscOffsetYChange = { discOffsetY = it },
                animatedDisc1CenterX = animatedDisc1CenterX, disc1RadiusDp = disc1RadiusDp, lyricsDiscDrop = lyricsDiscDrop,
                tonearmScale = rememberUpdatedState(tonearmScale), onTonearmScaleChange = { tonearmScale = it },
                headerOffsetY = rememberUpdatedState(headerOffsetY), onHeaderOffsetYChange = { headerOffsetY = it }
            )

            // 他社製ウィジェット（ホームのみ）
            if (currentLauncherPage == 1 && !pvFullscreen && !isLyricsBrowsing) {
                HomeWidgetsLayer(
                    placements = rememberUpdatedState(widgetPlacements), onPlacementsChange = { widgetPlacements = it },
                    appWidgetManager = appWidgetManager, appWidgetHost = appWidgetHost,
                    layoutEditMode = layoutEditMode, accentColor = accentColor, repo = repo,
                    pvActive = lyricsEnabled && pvEnabled && lyricsDisplayMode == "video" && !isLyricsBrowsing && syncedLyricLines.isNotEmpty(),
                    pvLeft = 28f + pvOffsetX, pvTop = topPaddingDp.value + 150f + pvTopOffset,
                    pvEffWidth = pvEffWidth, pvEffHeight = pvEffHeight
                )
            }

            // レイアウト編集モード: 完了ボタンとヒント（全ページ共通）
            LayoutEditFinishBar(
                accentColor = accentColor, topPaddingDp = topPaddingDp, currentPage = currentLauncherPage,
                layoutEditMode = layoutEditMode, onFinish = { layoutEditMode = false }, onAddWidget = { showWidgetPicker = true }
            )

            // --- メインコンテンツ描画 (0: 設定, 1: ホーム, 2: アプリ一覧, 3: フォルダ) ---
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer { alpha = transitionAlpha }
            ) {
                when (currentLauncherPage) {
                    0 -> {
                        // --- 0: 設定画面 ---
                        SettingsScreen(
                            repo = repo,
                            onBack = {
                                changePageWithTransition(1)
                            },
                            onChanged = {
                                accentHex = repo.prefManager.getAccentColorHex()
        subHex = repo.prefManager.getSubColorHex()
        thirdHex = repo.prefManager.getThirdColorHex()
        monoColorMode = repo.prefManager.getMonoColorMode()
                                customBackgroundPath = repo.prefManager.getCustomBackgroundPath()
                                backgroundUpdateKey = System.currentTimeMillis()
                                customFontPath = repo.prefManager.getCustomFontPath()
                                useCustomFont = repo.prefManager.isUseCustomFont()
                                fontChoice = repo.prefManager.getFontChoice()
                                lyricsDisplayMode = repo.prefManager.getLyricsDisplayMode()
                                lyricsEnabled = repo.prefManager.isLyricsEnabled()
                                clockShowSeconds = repo.prefManager.isClockShowSeconds()
                                clockHorizontal = repo.prefManager.isClockHorizontal()
                                clock12h = repo.prefManager.isClock12h()
                                clockBattery = repo.prefManager.isClockShowBattery()
                                dateWareki = repo.prefManager.isDateWareki()
                                pageTransitionSpeed = repo.prefManager.getPageTransitionSpeed()
                                dialVisibility = repo.prefManager.getDialVisibility()
                                powerSaveMode = repo.prefManager.getPowerSaveMode()
                                keepScreenOn = repo.prefManager.isKeepScreenOn()
                                notificationBadges = repo.prefManager.isNotificationBadgesEnabled()
                                appSortMode = repo.prefManager.getAppSortMode()
                                appSortDescending = repo.prefManager.isAppSortDescending()
                                indexShowEnglish = repo.prefManager.isIndexShowEnglish()
                                indexMode = repo.prefManager.getIndexMode()
                                indexHideEmpty = repo.prefManager.isIndexHideEmptyEnabled()
                                labelsWhilePlaying = repo.prefManager.isLabelsWhilePlayingEnabled()
                                clockOffsetX = repo.prefManager.getClockOffsetXDp()
                                clockOffsetY = repo.prefManager.getClockOffsetYDp()
                                lyricsLineOffsetX = repo.prefManager.getLyricsLineOffsetXDp()
                                lyricsLineOffsetY = repo.prefManager.getLyricsLineOffsetYDp()
                                lyricsMultiOffsetX = repo.prefManager.getLyricsMultiOffsetXDp()
                                lyricsMultiOffsetY = repo.prefManager.getLyricsMultiOffsetYDp()
                                pvOffsetX = repo.prefManager.getPvOffsetXDp()
                                pvTopOffset = repo.prefManager.getPvTopOffsetDp()
                                if (repo.prefManager.isLayoutEditRequested()) {
                                    repo.prefManager.clearLayoutEditRequest()
                                    layoutEditMode = true
                                    if (currentLauncherPage != 1) changePageWithTransition(1)
                                }
                                pvFontScale = repo.prefManager.getPvFontScale()
                                pvTopOffset = repo.prefManager.getPvTopOffsetDp()
                                discOffsetX = repo.prefManager.getDiscOffsetX()
                                discOffsetY = repo.prefManager.getDiscOffsetY()
                                headerOffsetY = repo.prefManager.getHeaderOffsetY()
                                dateOffsetX = repo.prefManager.getDateOffsetX().let { if (it.isNaN()) dateOffsetX else it }
                                dateOffsetY = repo.prefManager.getDateOffsetY().let { if (it.isNaN()) dateOffsetY else it }
                                batteryOffsetX = repo.prefManager.getBatteryOffsetX().let { if (it.isNaN()) batteryOffsetX else it }
                                batteryOffsetY = repo.prefManager.getBatteryOffsetY().let { if (it.isNaN()) batteryOffsetY else it }
                                secondsOffsetX = repo.prefManager.getSecondsOffsetX().let { if (it.isNaN()) secondsOffsetX else it }
                                secondsOffsetY = repo.prefManager.getSecondsOffsetY().let { if (it.isNaN()) secondsOffsetY else it }
                                discDropOnPv = repo.prefManager.isDiscDropOnPvEnabled()
                                pvTextColor = repo.prefManager.getPvTextColor()
                                pvRuby = repo.prefManager.isPvRubyEnabled()
                                pvInterludeArt = repo.prefManager.isPvInterludeArtEnabled()
                                liquidSpeed = repo.prefManager.getLiquidSpeed()
                                liquidAlpha = repo.prefManager.getLiquidAlpha()
                                wallpaperBlur = repo.prefManager.getWallpaperBlurDp()
                                if (!lyricsEnabled) {
                                    com.example.nissingeppolauncher.service.LyricsOverlayService.stop(context)
                                } else if (repo.prefManager.isStatusBarLyricsEnabled() && android.provider.Settings.canDrawOverlays(context)) {
                                    com.example.nissingeppolauncher.service.LyricsOverlayService.start(context)
                                }
                                lyricsPvFont = repo.prefManager.getLyricsPvFont()
                                lyricsEnFont = repo.prefManager.getLyricsEnFont()
                                lyricsEnCustomPath = repo.prefManager.getLyricsEnCustomFontPath()
                                lyricsBold = repo.prefManager.isLyricsBold()
                                dopagakiMode = repo.prefManager.isDopagakiEnabled()
                                dopaBlackFont = repo.prefManager.isDopaBlackFontEnabled()
                            },
                            onAppsUpdated = {
                                refreshApps()
                            },
                            accentColor = accentColor,
                            disc3d = disc3d,
                            tiltX = discTiltX,
                            tiltY = discTiltY
                        )
                    }
                1 -> {
                    // --- 1: ホーム画面 ---
                        LayoutClockPart(
                            offsetX = clockOffsetX,
                            offsetY = clockOffsetY,
                            onOffsetChange = { dx, dy -> clockOffsetX += dx; clockOffsetY += dy },
                            onOffsetSave = { repo.prefManager.setClockOffset(clockOffsetX, clockOffsetY) },
                            scale = clockPartScale,
                            onScaleChange = { d -> clockPartScale = (clockPartScale + d).coerceIn(0.5f, 2.5f) },
                            onScaleSave = { repo.prefManager.setLayoutScale("clock", clockPartScale) },
                            layoutEditMode = layoutEditMode,
                            onEnterLayoutEdit = { layoutEditMode = true },
                            accentColor = accentColor,
                            topPaddingDp = topPaddingDp
                        ) {
                            Column {
                            ClockWidget(
                                showDate = false,
                                showSeconds = false,
                                horizontal = clockHorizontal,
                                hour12 = clock12h,
                                showBattery = false,
                                wareki = dateWareki,
                                modifier = Modifier.graphicsLayer {
                                                                        if (disc3d) {
                                        transformOrigin = TransformOrigin(0.5f, 0.5f)
                                        rotationX = 27f + discTiltX.value
                                        rotationY = discTiltY.value
                                        cameraDistance = 6.5f * density
                                    }
                                },
                                lyricsLine = "",
                                translationText = "",
                                karaokeLine = null,
                                karaokePositionMs = sessionPositionMs + lyricOffsetMs,
                                karaokeSungColor = accentColor,
                                lyricFontFamily = lyricsFontFamily,
                                lyricEnFontFamily = lyricsEnFontArg,
                                dateMode = if (lyricsDisplayMode == "video" && syncedLyricLines.isNotEmpty()) "sideways" else dateDisplayMode,
                                glowColor = accentColor,
                                rainbowGlow = dopagakiMode
                            )
                            if (sleepRemainingMs > 0L) {
                                val totalSec = sleepRemainingMs / 1000L
                                val sleepText = if (totalSec >= 3600L) {
                                    "スリープまで %d:%02d:%02d".format(totalSec / 3600L, (totalSec % 3600L) / 60L, totalSec % 60L)
                                } else {
                                    "スリープまで %d:%02d".format(totalSec / 60L, totalSec % 60L)
                                }
                                Text(text = sleepText, color = Color.White.copy(alpha = 0.75f), fontSize = 12.sp)
                            }
                            }
                        }


                    // 1行歌詞（時計から分離。レイアウト編集で個別に移動できる）
                    if (lyricsEnabled && lyricsDisplayMode == "line" && (currentLyricLine.isNotBlank() || layoutEditMode)) {
                        LayoutClockPart(
                            offsetX = lyricsLineOffsetX,
                            offsetY = lyricsLineOffsetY,
                            onOffsetChange = { dx, dy -> lyricsLineOffsetX += dx; lyricsLineOffsetY += dy },
                            onOffsetSave = { repo.prefManager.setLyricsLineOffset(lyricsLineOffsetX, lyricsLineOffsetY) },
                            scale = lyricsLinePartScale,
                            onScaleChange = { d -> lyricsLinePartScale = (lyricsLinePartScale + d).coerceIn(0.5f, 2.5f) },
                            onScaleSave = { repo.prefManager.setLayoutScale("lyrics_line", lyricsLinePartScale) },
                            layoutEditMode = layoutEditMode,
                            onEnterLayoutEdit = { layoutEditMode = true },
                            accentColor = accentColor,
                            topPaddingDp = topPaddingDp
                        ) {
                            LyricsLineView(
                                lyricsLine = if (currentLyricLine.isNotBlank()) currentLyricLine else "現在の行のサンプル",
                                translationText = if (translationEnabled && currentLyricLine.isNotBlank()) lyricTranslations?.getOrNull(syncedLyricLines.indexOfLast { it.timeMs <= sessionPositionMs + lyricOffsetMs }) ?: "" else "",
                                karaokeLine = if (currentLyricLine.isNotBlank()) syncedLyricLines.lastOrNull { it.timeMs <= sessionPositionMs + lyricOffsetMs } else null,
                                karaokePositionMs = sessionPositionMs + lyricOffsetMs,
                                karaokeSungColor = accentColor,
                                lyricFontFamily = lyricsFontFamily,
                                lyricEnFontFamily = lyricsEnFontArg,
                                glowColor = accentColor,
                                rainbowGlow = dopagakiMode,
                                dopagaki = dopagakiMode
                            )
                        }
                    }

                    // 日付（時計から分離。レイアウト編集で個別に移動できる）
                        LayoutClockPart(
                            offsetX = dateOffsetX,
                            offsetY = dateOffsetY,
                            onOffsetChange = { dx, dy -> dateOffsetX += dx; dateOffsetY += dy },
                            onOffsetSave = { repo.prefManager.setDateOffset(dateOffsetX, dateOffsetY) },
                            scale = datePartScale,
                            onScaleChange = { d -> datePartScale = (datePartScale + d).coerceIn(0.5f, 2.5f) },
                            onScaleSave = { repo.prefManager.setLayoutScale("date", datePartScale) },
                            layoutEditMode = layoutEditMode,
                            onEnterLayoutEdit = { layoutEditMode = true },
                            accentColor = accentColor,
                            topPaddingDp = topPaddingDp
                        ) {
                            ClockWidget(
                                showTime = false,
                                showBattery = false,
                                wareki = dateWareki,
                                modifier = Modifier.graphicsLayer {
                                                                        if (disc3d) {
                                        transformOrigin = TransformOrigin(0.5f, 0.5f)
                                        rotationX = 27f + discTiltX.value
                                        rotationY = discTiltY.value
                                        cameraDistance = 6.5f * density
                                    }
                                },
                                dateMode = if (lyricsDisplayMode == "video" && syncedLyricLines.isNotEmpty()) "sideways" else dateDisplayMode,
                                glowColor = accentColor,
                                rainbowGlow = dopagakiMode
                            )
                        }
                    // バッテリー（時計から分離。レイアウト編集で個別に移動できる）
                        if (clockBattery) {
                            LayoutClockPart(
                                offsetX = batteryOffsetX,
                                offsetY = batteryOffsetY,
                                onOffsetChange = { dx, dy -> batteryOffsetX += dx; batteryOffsetY += dy },
                                onOffsetSave = { repo.prefManager.setBatteryOffset(batteryOffsetX, batteryOffsetY) },
                                scale = batteryPartScale,
                                onScaleChange = { d -> batteryPartScale = (batteryPartScale + d).coerceIn(0.5f, 2.5f) },
                                onScaleSave = { repo.prefManager.setLayoutScale("battery", batteryPartScale) },
                                layoutEditMode = layoutEditMode,
                                onEnterLayoutEdit = { layoutEditMode = true },
                                accentColor = accentColor,
                                topPaddingDp = topPaddingDp
                            ) {
                            ClockWidget(
                                showTime = false,
                                showDate = false,
                                showBattery = true,
                                secondsOnly = false,
                                wareki = dateWareki,
                                modifier = Modifier.graphicsLayer {
                                                                        if (disc3d) {
                                        transformOrigin = TransformOrigin(0.5f, 0.5f)
                                        rotationX = 27f + discTiltX.value
                                        rotationY = discTiltY.value
                                        cameraDistance = 6.5f * density
                                    }
                                },
                                dateMode = if (lyricsDisplayMode == "video" && syncedLyricLines.isNotEmpty()) "sideways" else dateDisplayMode,
                                glowColor = accentColor,
                                rainbowGlow = dopagakiMode
                            )
                            }
                        }

                    // 秒（時計から分離。レイアウト編集で個別に移動できる）
                        if (clockShowSeconds) {
                            LayoutClockPart(
                                offsetX = secondsOffsetX,
                                offsetY = secondsOffsetY,
                                onOffsetChange = { dx, dy -> secondsOffsetX += dx; secondsOffsetY += dy },
                                onOffsetSave = { repo.prefManager.setSecondsOffset(secondsOffsetX, secondsOffsetY) },
                                scale = secondsPartScale,
                                onScaleChange = { d -> secondsPartScale = (secondsPartScale + d).coerceIn(0.5f, 2.5f) },
                                onScaleSave = { repo.prefManager.setLayoutScale("seconds", secondsPartScale) },
                                layoutEditMode = layoutEditMode,
                                onEnterLayoutEdit = { layoutEditMode = true },
                                accentColor = accentColor,
                                topPaddingDp = topPaddingDp
                            ) {
                            ClockWidget(
                                showTime = false,
                                showDate = false,
                                showBattery = false,
                                secondsOnly = true,
                                wareki = dateWareki,
                                modifier = Modifier.graphicsLayer {
                                                                        if (disc3d) {
                                        transformOrigin = TransformOrigin(0.5f, 0.5f)
                                        rotationX = 27f + discTiltX.value
                                        rotationY = discTiltY.value
                                        cameraDistance = 6.5f * density
                                    }
                                },
                                dateMode = if (lyricsDisplayMode == "video" && syncedLyricLines.isNotEmpty()) "sideways" else dateDisplayMode,
                                glowColor = accentColor,
                                rainbowGlow = dopagakiMode
                            )
                            }
                        }

                    // 複数行歌詞（時計から分離。レイアウト編集で個別に移動できる）
                    if (lyricsEnabled && lyricsDisplayMode == "multi" && !isLyricsBrowsing && (syncedLyricLines.isNotEmpty() || layoutEditMode)) {
                        LayoutClockPart(
                            offsetX = lyricsMultiOffsetX,
                            offsetY = lyricsMultiOffsetY,
                            onOffsetChange = { dx, dy -> lyricsMultiOffsetX += dx; lyricsMultiOffsetY += dy },
                            onOffsetSave = { repo.prefManager.setLyricsMultiOffset(lyricsMultiOffsetX, lyricsMultiOffsetY) },
                            scale = lyricsMultiPartScale,
                            onScaleChange = { d -> lyricsMultiPartScale = (lyricsMultiPartScale + d).coerceIn(0.5f, 2.5f) },
                            onScaleSave = { repo.prefManager.setLayoutScale("lyrics_multi", lyricsMultiPartScale) },
                            layoutEditMode = layoutEditMode,
                            onEnterLayoutEdit = { layoutEditMode = true },
                            accentColor = accentColor,
                            topPaddingDp = topPaddingDp
                        ) {
                            LauncherMultiLineLyrics(
                                lines = if (syncedLyricLines.isNotEmpty()) syncedLyricLines else listOf(
                                    LyricLine(timeMs = 0L, text = "前の行のサンプル"),
                                    LyricLine(timeMs = 1000L, text = "今の行のサンプル"),
                                    LyricLine(timeMs = 2000L, text = "次の行のサンプル")
                                ),
                                positionMs = if (syncedLyricLines.isNotEmpty()) sessionPositionMs else 1000L,
                                lyricOffsetMs = lyricOffsetMs,
                                translationEnabled = translationEnabled,
                                translations = lyricTranslations,
                                fontFamily = pvFontFamily,
                                enFontFamily = lyricsEnFontArg,
                                lyricWeight = lyricWeight,
                                accentColor = accentColor,
                                screenWidthDp = screenWidthDp,
                                topPaddingDp = topPaddingDp,
                                dopagaki = dopagakiMode,
                                inLayoutPart = true
                            )
                        }
                    }

                    if (!vinylRackOpen) {
                    DiscCarousel(
                        itemCount = homeDiscCount,
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer {
                                                                if (disc3d) {
                                    transformOrigin = TransformOrigin(0.5f, 0.5f)
                                    rotationX = 27f + discTiltX.value
                                    rotationY = discTiltY.value
                                    cameraDistance = 6.5f * density
                                }
                            },
                        discCenterXDp = animatedDisc2CenterX,
                        discCenterYDp = disc2CenterY + lyricsDiscDrop,
                        discRadiusDp = disc2RadiusDp,
                        stepAngle = appDiscStepAngle,
                        isRightDisc = true,
                        isLoopable = false,
                        fadeLabelsOnScroll = false,
                        state = homeDiscState,
                        onHoldingChanged = { isHoldingRight = it },
                        onEmptyTap = { },
                        onLongPressEmpty = if (!layoutEditMode && !pvFullscreen && !isLyricsBrowsing) {
                            {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                layoutEditMode = true
                            }
                        } else null
                    ) { index, rad, labelAlpha, isSelected ->
                        val homeEntry = homeEntries.getOrNull(index)
                        if (homeEntry is HomeEntry.Folder) {
                            DiscFolderItem(
                                entry = homeEntry.folder,
                                discCenterXDp = animatedDisc2CenterX,
                                discCenterYDp = disc2CenterY + lyricsDiscDrop,
                                discRadiusDp = disc2RadiusDp,
                                rad = rad,
                                labelAlpha = labelAlpha,
                                textSizeSp = appDiscTextSize,
                                iconSizeDp = appDiscIconSize,
                                iconCornerPercent = iconCornerPercent,
                                letterSpacingSp = appDiscLetterSpacing,
                                accentColor = accentColor,
                                subColor = subColor,
                                monoColorMode = monoColorMode,
                                isFocused = isSelected,
                                iconTilt3d = disc3d,
                                onFolderClick = { fid ->
                                    if (isSelected) {
                                        val chain = mutableListOf<String>()
                                        var parent = folderParents[fid]
                                        while (parent != null) {
                                            chain.add(0, parent)
                                            parent = folderParents[parent]
                                        }
                                        pendingFocusFolderId = fid
                                        folderPath = chain
                                        // 移動先の階層の子フォルダ一覧から対象の位置を求める
                                        // （この時点の folderItemsWithNew は移動前の階層のものなので使えない）
                                        val newChildren = folderList.filter { folderParents[it.first] == chain.lastOrNull() }
                                        val targetIdx = newChildren.indexOfFirst { it.first == fid }
                                        if (targetIdx >= 0) {
                                            folderSelectedIndex = targetIdx
                                            // フォルダページ未表示の間は円盤の項目数が前回表示時のまま残り、
                                            // animateToIndex の範囲クランプで別のフォルダに合ってしまうため先に同期する
                                            folderDiscState.itemCount = newChildren.size + 1 + if (chain.isNotEmpty()) 1 else 0
                                            folderDiscState.animateToIndex(targetIdx)
                                        }
                                        changePageWithTransition(3)
                                    } else {
                                        homeDiscState.animateToIndex(index)
                                    }
                                },
                                onAppClick = { },
                                onAppLongClick = { },
                                onNewFolderClick = { },
                                onFolderLongClick = { fid -> folderOptionTargetId = fid },
                                showLabels = labelsWhilePlaying || !isBigDiscLayout,
                                isFavorite = true
                            )
                        }
                        if (favEditItemCount == 1 && index == homeEntries.size) {
                            val editOuterR = disc2RadiusDp + 8f
                            val editInnerR = disc2RadiusDp - 18f
                            val editLabelX = animatedDisc2CenterX + editOuterR * cos(rad)
                            val editLabelY = disc2CenterY + lyricsDiscDrop + editOuterR * sin(rad)
                            val editIconX = animatedDisc2CenterX + editInnerR * cos(rad)
                            val editIconY = disc2CenterY + lyricsDiscDrop + editInnerR * sin(rad)
                            val openFavReorder = {
                                if (isSelected) {
                                    favSortList = homeEntries
                                    showFavReorderDialog = true
                                } else {
                                    homeDiscState.animateToIndex(index)
                                }
                            }
                            Box(
                                modifier = Modifier
                                    .offset(x = (editIconX - ((appDiscIconSize + 14f) / 2f)).dp, y = (editIconY - ((appDiscIconSize + 14f) / 2f)).dp)
                                    .size((appDiscIconSize + 14f).dp)
                                    .pressScale()
                                    .clickable { openFavReorder() },
                                contentAlignment = Alignment.Center
                            ) {
                                WhiteEditIcon(size = 20.dp, color = Color.White)
                            }
                            if (!isBigDiscLayout) {
                                Box(
                                    modifier = Modifier
                                        .offset(x = (editLabelX - 240f).dp, y = (editLabelY - 22f).dp)
                                        .width(240.dp)
                                        .height(44.dp),
                                    contentAlignment = Alignment.CenterEnd
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.End,
                                        modifier = Modifier
                                            .wrapContentSize()
                                            .clickable { openFavReorder() }
                                            .padding(horizontal = 4.dp, vertical = 2.dp)
                                    ) {
                                        Text(
                                            text = "並び替え",
                                            color = if (isSelected) accentColor.copy(alpha = labelAlpha) else Color.White.copy(alpha = 0.9f * labelAlpha),
                                            fontSize = appDiscTextSize.sp,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                            letterSpacing = appDiscLetterSpacing.sp,
                                            textAlign = TextAlign.End,
                                            maxLines = 1
                                        )
                                    }
                                }
                            }
                        }
                        if (homeEntry is HomeEntry.App) {
                            DiscAppItem(
                                item = homeEntry.item,
                                discCenterXDp = animatedDisc2CenterX,
                                discCenterYDp = disc2CenterY + lyricsDiscDrop,
                                discRadiusDp = disc2RadiusDp,
                                rad = rad,
                                labelAlpha = labelAlpha,
                                textSizeSp = appDiscTextSize,
                                iconSizeDp = appDiscIconSize,
                                iconCornerPercent = iconCornerPercent,
                                letterSpacingSp = appDiscLetterSpacing,
                                accentColor = accentColor,
                                subColor = subColor,
                                monoColorMode = monoColorMode,
                                selectedPackages = null,
                                iconTilt3d = disc3d,
                                rainbowGlow = dopagakiMode,
                                showLabels = labelsWhilePlaying || !isBigDiscLayout,
                                onAppClick = { onAppClick(it.packageName) },
                                onLongClick = { selectedAppForOption = it }
                            )
                        }
                    }
                    }

                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .zIndex(50f)
                            .graphicsLayer {
                                                                if (disc3d) {
                                    transformOrigin = TransformOrigin(0.5f, 0.5f)
                                    rotationX = 27f + discTiltX.value
                                    rotationY = discTiltY.value
                                    cameraDistance = 6.5f * density
                                }
                            }
                    ) {
                        MusicVinylWidget(
                            modifier = Modifier.fillMaxSize(),
                            discCenterXDp = animatedDisc1CenterX,
                            discCenterYDp = disc1CenterY + lyricsDiscDrop,
                            discRadiusDp = disc1RadiusDp,
                            accentColor = accentColor,
                            subColor = subColor,
                            thirdColor = thirdColor,
                            rainbowGlow = dopagakiMode,
                            textStartAngle = lyricsTextAngle,
                            artworkTilt3d = disc3d,
                            tonearmScaleOverride = tonearmScale,
                            spinFrozen = isLyricsBrowsing || pvFullscreen,
                            onRackVisibilityChanged = { vinylRackOpen = it },
                            onThemeSettingsRequest = {
                                repo.prefManager.requestSettingsPath("lyrics,music_art,music_theme")
                                changePageWithTransition(0)
                            }
                        )
                    }
                }
                2 -> {
                    // --- 2: アプリ一覧画面 ---
                    DiscWheel(
                        entries = indexEntries,
                        side = DiscSide.LEFT,
                        accentColor = subColor,
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer {
                                if (disc3d) {
                                    transformOrigin = TransformOrigin(0.5f, 0.5f)
                                    rotationX = 27f + discTiltX.value
                                    rotationY = discTiltY.value
                                    cameraDistance = 6.5f * density
                                }
                            },
                        state = indexDiscState,
                        iconSizeDp = appDiscIconSize,
                        gesturesEnabled = false,
                        holdExpand = true,
                        holdActive = isHoldingLeft,
                        fadeLabelsOnScroll = false,
                        accentSelectedLabel = true,
                        verticalRestLabel = true,
                        stepAngle = stepAngle,
                        isLoopable = true,
                        discCenterXDp = animatedDisc1CenterX,
                        discCenterYDp = disc1CenterY,
                        textSizeSp = appDiscTextSize,
                        letterSpacingSp = appDiscLetterSpacing,
                        dotMarkers = true,
                        restLabelText = selectedIndexChar,
                        onFocusChange = { indexSelected = it }
                    )

                    val displayedApps = remember(searchQuery, allApps, isHideMode, appSortMode, usageStatsMap, appSortDescending) {
                        if (isHideMode) {
                            allApps.filter { it.isHidden }
                        } else if (searchQuery.isNotBlank()) {
                            allApps.filter {
                                (it.label.contains(searchQuery, ignoreCase = true) ||
                                        it.customYomi.contains(searchQuery, ignoreCase = true) ||
                                        it.customYomiKana.contains(searchQuery, ignoreCase = true) ||
                                        it.initialJapanese.contains(searchQuery, ignoreCase = true) ||
                                        it.searchAliases.any { alias -> alias.contains(searchQuery, ignoreCase = true) }) && !it.isHidden
                            }
                        } else {
                            val base = allApps.filter { !it.isHidden }
                            if (appSortMode == "usage" || appSortMode == "recent") {
                                if (appSortDescending) {
                                    base.sortedWith(compareByDescending<AppInfo> { usageStatsMap[it.packageName] ?: 0L }.thenBy { it.label.lowercase() })
                                } else {
                                    base.sortedWith(compareBy<AppInfo> { usageStatsMap[it.packageName] ?: 0L }.thenBy { it.label.lowercase() })
                                }
                            } else if (appSortDescending) {
                                base.reversed()
                            } else {
                                base
                            }
                        }
                    }

                    val appDiscItems = remember(displayedApps, effBadges, appSortMode, searchQuery, isHideMode, indexMode, indexShowEnglish, appSortDescending, usageTierOf, recentLabelOf) { buildDiscItems(displayedApps, appSortMode == "name" || appSortMode == "usage" || appSortMode == "recent" || searchQuery.isNotBlank() || isHideMode, effBadges, indexGroupOf) }
                    LaunchedEffect(selectedIndexChar) {
                        if (!selectedIndexChar.isNullOrEmpty() && appDiscItems.isNotEmpty()) {
                            val targetIndex = appDiscItems.indexOfFirst { item ->
                                when (item) {
                                    is DiscItem.IndexEntry -> item.char.equals(selectedIndexChar, ignoreCase = true)
                                    is DiscItem.AppEntry -> indexGroupOf(item.app).equals(selectedIndexChar, ignoreCase = true)
                                }
                            }
                            if (targetIndex != -1) {
                                val rawTarget = -targetIndex * appDiscStepAngle
                                val loopable = !isHideMode && searchQuery.isBlank()
                                val targetAngle = if (loopable) rawTarget else rawTarget.coerceIn(-((appDiscItems.size - 1) * appDiscStepAngle).coerceAtLeast(0f), 0f)
                                snapDiscTo(appListDiscState, targetAngle)
                            }
                        }
                    }
                    LaunchedEffect(appDiscItems, isHideMode, searchQuery) {
                        snapshotFlow {
                            if (appDiscItems.isEmpty()) null else {
                                val raw = Math.round(-appListDiscState.rawRotation / appDiscStepAngle)
                                val idx = if (!isHideMode && searchQuery.isBlank()) {
                                    ((raw % appDiscItems.size) + appDiscItems.size) % appDiscItems.size
                                } else {
                                    raw.coerceIn(0, appDiscItems.size - 1)
                                }
                                (appDiscItems.getOrNull(idx) as? DiscItem.AppEntry)?.app?.packageName
                            }
                        }.distinctUntilChanged().collect { pkg ->
                            if (pkg != null) repo.prefManager.saveAppListFocusPackage(pkg)
                        }
                    }
                    var appFocusRestored by remember { mutableStateOf(false) }
                    LaunchedEffect(appDiscItems, currentLauncherPage) {
                        if (currentLauncherPage == 2 && !appFocusRestored && appDiscItems.isNotEmpty()) {
                            appFocusRestored = true
                            val pkg = repo.prefManager.getAppListFocusPackage()
                            val targetIndex = appDiscItems.indexOfFirst { (it as? DiscItem.AppEntry)?.app?.packageName == pkg }
                            if (targetIndex >= 0) {
                                val rawTarget = -targetIndex * appDiscStepAngle
                                val loopable = !isHideMode && searchQuery.isBlank()
                                val targetAngle = if (loopable) rawTarget else rawTarget.coerceIn(-((appDiscItems.size - 1) * appDiscStepAngle).coerceAtLeast(0f), 0f)
                                snapDiscTo(appListDiscState, targetAngle)
                            }
                        }
                    }
                    DiscCarousel(
                        itemCount = appDiscItems.size,
                        modifier = Modifier.fillMaxSize()
                                .graphicsLayer {
                                    if (disc3d) {
                                        transformOrigin = TransformOrigin(0.5f, 0.5f)
                                        rotationX = 27f + discTiltX.value
                                        rotationY = discTiltY.value
                                        cameraDistance = 6.5f * density
                                    }
                                },
                        discCenterXDp = animatedDisc2CenterX,
                        discCenterYDp = disc2CenterY,
                        discRadiusDp = disc2RadiusDp,
                        stepAngle = appDiscStepAngle,
                        isRightDisc = true,
                        isLoopable = (!isHideMode && searchQuery.isBlank()),
                        fadeLabelsOnScroll = false,
                        state = appListDiscState,
                        onHoldingChanged = { isHoldingRight = it }
                    ) { index, rad, labelAlpha, isSelected ->
                        val di = appDiscItems.getOrNull(index)
                        if (di != null) {
                            DiscAppItem(
                                item = di,
                                discCenterXDp = animatedDisc2CenterX,
                                discCenterYDp = disc2CenterY,
                                discRadiusDp = disc2RadiusDp,
                                rad = rad,
                                labelAlpha = labelAlpha,
                                textSizeSp = appDiscTextSize,
                                iconSizeDp = appDiscIconSize,
                                iconCornerPercent = iconCornerPercent,
                                letterSpacingSp = appDiscLetterSpacing,
                                accentColor = accentColor,
                                subColor = subColor,
                                monoColorMode = monoColorMode,
                                selectedPackages = null,
                                iconTilt3d = disc3d,
                                rainbowGlow = dopagakiMode,
                                isFocused = isSelected,
                                onAppClick = { app ->
                                    if (isSelected) onAppClick(app.packageName) else appListDiscState.animateToIndex(index)
                                },
                                onLongClick = { selectedAppForOption = it }
                            )
                        }
                    }
                }
                3 -> {
                    // --- 3: フォルダ画面 ---
                    if (isEditingFolder) {
                        DiscWheel(
                            entries = indexEntries,
                            side = DiscSide.LEFT,
                            accentColor = subColor,
                            modifier = Modifier.fillMaxSize()
                                .graphicsLayer {
                                    if (disc3d) {
                                        transformOrigin = TransformOrigin(0.5f, 0.5f)
                                        rotationX = 27f + discTiltX.value
                                        rotationY = discTiltY.value
                                        cameraDistance = 6.5f * density
                                    }
                                },
                            state = indexDiscState,
                            iconSizeDp = appDiscIconSize,
                            gesturesEnabled = false,
                            holdExpand = true,
                            holdActive = isHoldingLeft,
                            fadeLabelsOnScroll = false,
                            accentSelectedLabel = true,
                            verticalRestLabel = true,
                            stepAngle = stepAngle,
                            isLoopable = true,
                            discCenterXDp = animatedDisc1CenterX,
                            discCenterYDp = disc1CenterY,
                            textSizeSp = appDiscTextSize,
                            letterSpacingSp = appDiscLetterSpacing,
                            dotMarkers = true,
                            restLabelText = selectedIndexChar,
                            onFocusChange = { indexSelected = it }
                        )
                    } else {
                        // 左側: フォルダ選択ダイヤル
                        DiscWheel(
                            entries = folderEntries,
                            iconSizeDp = appDiscIconSize,
                            side = DiscSide.LEFT,
                            accentColor = accentColor,
                            modifier = Modifier.fillMaxSize()
                                .graphicsLayer {
                                    if (disc3d) {
                                        transformOrigin = TransformOrigin(0.5f, 0.5f)
                                        rotationX = 27f + discTiltX.value
                                        rotationY = discTiltY.value
                                        cameraDistance = 6.5f * density
                                    }
                                },
                            state = folderDiscState,
                            gesturesEnabled = false,
                            holdExpand = true,
                            holdActive = isHoldingFolderWheel,
                            focusedLabelOnly = true,
                            fadeLabelsOnScroll = false,
                            accentSelectedLabel = true,
                            verticalRestLabel = true,
                            textSizeSp = appDiscTextSize,
                            letterSpacingSp = appDiscLetterSpacing,
                            stepAngle = appDiscStepAngle,
                            discCenterXDp = animatedDisc1CenterX,
                            discCenterYDp = disc1CenterY,
                            onFocusChange = { folderSelectedIndex = it },
                            onEntryLongClick = { idx ->
                                val e = folderEntries.getOrNull(idx)
                                if (e != null && e.id != "NEW_FOLDER" && e.id != "__BACK") {
                                    folderOptionTargetId = e.id
                                }
                            }
                        )
                    }

                    // 右側: 通常時はフォルダ内アプリ、編集時は全アプリ（チェック付き）
                    val folderPackages = remember(selectedFolderId, foldersMap) {
                        if (selectedFolderId != null && selectedFolderId != "NEW_FOLDER" && selectedFolderId != "__BACK") {
                            foldersMap[selectedFolderId]?.second ?: emptyList()
                        } else {
                            emptyList()
                        }
                    }

                    val discApps = if (isEditingFolder) {
                        allApps
                    } else {
                        allApps.filter { folderPackages.contains(it.packageName) }
                    }

                    val editDiscItems = remember(discApps, isEditingFolder, effBadges) { if (isEditingFolder) buildDiscItems(discApps, true, effBadges) else emptyList() }
                    // 通常時の右円: フォーカス中フォルダの子フォルダ＋アプリ＋新規作成
                    val childFoldersOfSelected = remember(folderList, folderParents, selectedFolderId) {
                        if (selectedFolderId != null && selectedFolderId != "NEW_FOLDER" && selectedFolderId != "__BACK") {
                            folderList.filter { folderParents[it.first] == selectedFolderId }
                        } else emptyList()
                    }
                    val sortedChildFolders = remember(childFoldersOfSelected, favoriteFolderIds) {
                        val favs = favoriteFolderIds.mapNotNull { fid -> childFoldersOfSelected.firstOrNull { it.first == fid } }
                        favs + childFoldersOfSelected.filter { it.first !in favoriteFolderIds }
                    }
                    val folderRightEntries = remember(sortedChildFolders, discApps, selectedFolderId, isEditingFolder, folderPath, folderIcons) {
                        if (isEditingFolder || selectedFolderId == null || selectedFolderId == "NEW_FOLDER" || selectedFolderId == "__BACK") emptyList()
                        else sortedChildFolders.map { FolderDiscEntry.Folder(it.first, it.second, folderIcons[it.first] ?: "") } +
                            discApps.map { FolderDiscEntry.App(it) } +
                            listOf(FolderDiscEntry.NewFolder) +
                            (if (folderPath.isNotEmpty()) listOf(FolderDiscEntry.Back) else emptyList())
                    }
                    val rightItemCount = if (isEditingFolder) editDiscItems.size else folderRightEntries.size
                    LaunchedEffect(selectedIndexChar, isEditingFolder) {
                        val char = if (isEditingFolder) selectedIndexChar else null
                        if (!char.isNullOrEmpty() && editDiscItems.isNotEmpty()) {
                            val targetIndex = editDiscItems.indexOfFirst { item ->
                                when (item) {
                                    is DiscItem.IndexEntry -> item.char.equals(char, ignoreCase = true)
                                    is DiscItem.AppEntry -> (item.app.initialJapanese == char) || (item.app.initialEnglish.equals(char, ignoreCase = true))
                                }
                            }
                            if (targetIndex != -1) {
                                val targetAngle = (-targetIndex * appDiscStepAngle).coerceIn(-((editDiscItems.size - 1) * appDiscStepAngle).coerceAtLeast(0f), 0f)
                                snapDiscTo(folderAppDiscState, targetAngle)
                            }
                        }
                    }
                    DiscCarousel(
                        itemCount = rightItemCount,
                        modifier = Modifier.fillMaxSize()
                                .graphicsLayer {
                                    if (disc3d) {
                                        transformOrigin = TransformOrigin(0.5f, 0.5f)
                                        rotationX = 27f + discTiltX.value
                                        rotationY = discTiltY.value
                                        cameraDistance = 6.5f * density
                                    }
                                },
                        discCenterXDp = animatedDisc2CenterX,
                        discCenterYDp = disc2CenterY,
                        discRadiusDp = disc2RadiusDp,
                        stepAngle = appDiscStepAngle,
                        isRightDisc = true,
                        isLoopable = false,
                        fadeLabelsOnScroll = false,
                        state = folderAppDiscState,
                        forceSlideOut = folderDiscExiting,
                        contentKey = folderPath.joinToString("/"),
                        onHoldingChanged = { isHoldingRight = it }
                    ) { index, rad, labelAlpha, isSelected ->
                        if (isEditingFolder) {
                            val di = editDiscItems.getOrNull(index)
                            if (di != null) {
                                DiscAppItem(
                                    item = di,
                                    discCenterXDp = animatedDisc2CenterX,
                                    discCenterYDp = disc2CenterY,
                                    discRadiusDp = disc2RadiusDp,
                                    rad = rad,
                                    labelAlpha = labelAlpha,
                                    textSizeSp = appDiscTextSize,
                                    iconSizeDp = appDiscIconSize,
                                    iconCornerPercent = iconCornerPercent,
                                    letterSpacingSp = appDiscLetterSpacing,
                                    accentColor = accentColor,
                                    subColor = subColor,
                                    monoColorMode = monoColorMode,
                                    selectedPackages = editingSelectedPackages,
                                    iconTilt3d = disc3d,
                                    rainbowGlow = dopagakiMode,
                                    isFocused = isSelected,
                                    onAppClick = { app ->
                                        if (isSelected) {
                                            val updated = editingSelectedPackages.toMutableSet()
                                            if (updated.contains(app.packageName)) {
                                                updated.remove(app.packageName)
                                            } else {
                                                updated.add(app.packageName)
                                            }
                                            editingSelectedPackages = updated
                                        } else {
                                            folderAppDiscState.animateToIndex(index)
                                        }
                                    },
                                    onLongClick = { }
                                )
                            }
                        } else {
                            val fe = folderRightEntries.getOrNull(index)
                            if (fe != null) {
                                DiscFolderItem(
                                    entry = fe,
                                    discCenterXDp = animatedDisc2CenterX,
                                    discCenterYDp = disc2CenterY,
                                    discRadiusDp = disc2RadiusDp,
                                    rad = rad,
                                    labelAlpha = labelAlpha,
                                    textSizeSp = appDiscTextSize,
                                    iconSizeDp = appDiscIconSize,
                                    iconCornerPercent = iconCornerPercent,
                                    letterSpacingSp = appDiscLetterSpacing,
                                    accentColor = accentColor,
                                    subColor = subColor,
                                    monoColorMode = monoColorMode,
                                    isFocused = isSelected,
                                    iconTilt3d = disc3d,
                                    onFolderClick = { fid ->
                                        if (isSelected) {
                                            val cur = selectedFolderId
                                            if (cur != null && cur != "NEW_FOLDER" && cur != "__BACK") {
                                                navigateFolderAnimated {
                                                    pendingFocusFolderId = fid
                                                    folderPath = folderPath + cur
                                                }
                                            }
                                        } else {
                                            folderAppDiscState.animateToIndex(index)
                                        }
                                    },
                                    onAppClick = { app ->
                                        if (isSelected) onAppClick(app.packageName) else folderAppDiscState.animateToIndex(index)
                                    },
                                    onAppLongClick = { app -> selectedAppForOption = app },
                                    isFavorite = (fe as? FolderDiscEntry.Folder)?.folderId?.let { it in favoriteFolderIds } == true,
                                    onBackClick = {
                                        if (isSelected && folderPath.isNotEmpty()) {
                                            navigateFolderAnimated {
                                                pendingFocusFolderId = folderPath.lastOrNull()
                                                folderPath = folderPath.dropLast(1)
                                            }
                                        } else {
                                            folderAppDiscState.animateToIndex(index)
                                        }
                                    },
                                    onFolderLongClick = { fid ->
                                        folderOptionTargetId = fid
                                    },
                                    onNewFolderClick = {
                                        if (isSelected) {
                                            createFolderParentId = selectedFolderId
                                            newFolderNameInput = ""
                                            showCreateFolderDialog = true
                                        } else {
                                            folderAppDiscState.animateToIndex(index)
                                        }
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }

        // 下ダイヤル「自動」: 4秒触らないと引っ込み、下部エリアの長押しで呼び出す
        var dialRetracted by remember { mutableStateOf(false) }
        LaunchedEffect(currentLauncherPage) { dialLastTouchMs = System.currentTimeMillis() }
        LaunchedEffect(vinylRackOpen) { dialLastTouchMs = System.currentTimeMillis() }
        LaunchedEffect(dialVisibility) {
            while (true) {
                if (dialVisibility == "auto" && !dialRetracted && System.currentTimeMillis() - dialLastTouchMs > 4000) {
                    dialRetracted = true
                }
                delay(500)
            }
        }
        val dialOut = dialVisibility == "always" || !dialRetracted
        val dialAnim by animateFloatAsState(
            targetValue = if (dialOut) 1f else 0f,
            animationSpec = tween(durationMillis = 250),
            label = "dialAnim"
        )

                // --- 画面下部 ページ切り替え (ボタン式 / ダイヤル式を設定で切替) ---
        if (!isLyricsBrowsing && !pvFullscreen && dialVisibility != "hidden" && (dialVisibility == "always" || !vinylRackOpen) && (dialVisibility == "auto" || dialOut || dialAnim > 0.01f) && repo.prefManager.getPageSwitchStyle() == "dial") {
            PeekPageDial(
                currentPage = currentLauncherPage,
                onPageSelected = { targetPage ->
                    changePageWithTransition(targetPage)
                },
                accentColor = accentColor,
                rainbowGlow = dopagakiMode,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = bottomPaddingDp + 4.dp)
                    .zIndex(250f)
                    .pointerInput(Unit) {
                        awaitPointerEventScope {
                            while (true) {
                                val event = awaitPointerEvent(PointerEventPass.Initial)
                                if (event.changes.any { it.pressed && !it.previousPressed }) {
                                    dialRetracted = false
                                    dialLastTouchMs = System.currentTimeMillis()
                                }
                            }
                        }
                    }
                    .graphicsLayer {
                        alpha = dialAnim
                        if (disc3d) {
                            transformOrigin = TransformOrigin(0.5f, 0.5f)
                            rotationX = 27f + discTiltX.value
                            rotationY = discTiltY.value
                            cameraDistance = 6.5f * density
                        }
                    }
            )
        } else if (!isLyricsBrowsing && !pvFullscreen && dialVisibility != "hidden" && (dialVisibility == "always" || !vinylRackOpen) && (dialVisibility == "auto" || dialOut || dialAnim > 0.01f)) {
            BottomPageDial(
                currentPage = currentLauncherPage,
                onPageSelected = { targetPage ->
                    changePageWithTransition(targetPage)
                },
                accentColor = accentColor,
                rainbowGlow = dopagakiMode,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = bottomPaddingDp + 4.dp)
                    .zIndex(250f)
                    .pointerInput(Unit) {
                        awaitPointerEventScope {
                            while (true) {
                                val event = awaitPointerEvent(PointerEventPass.Initial)
                                if (event.changes.any { it.pressed && !it.previousPressed }) {
                                    dialRetracted = false
                                    dialLastTouchMs = System.currentTimeMillis()
                                }
                            }
                        }
                    }
                    .graphicsLayer {
                        alpha = dialAnim
                        if (disc3d) {
                            transformOrigin = TransformOrigin(0.5f, 0.5f)
                            rotationX = 27f + discTiltX.value
                            rotationY = discTiltY.value
                            cameraDistance = 6.5f * density
                        }
                    }
            )
        }

        // --- フォルダページ通常時: 左半分は共通部品の操作レイヤーでフォルダホイールを駆動 ---
        if (!isTransitioning && currentLauncherPage == 3 && !isEditingFolder) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(top = topPaddingDp + 60.dp, bottom = bottomPaddingDp + 60.dp)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .weight(1f)
                ) {
                    DiscTouchHalf(
                        state = folderDiscState,
                        isRight = false,
                        radiusPx = with(LocalDensity.current) { disc1RadiusDp.dp.toPx() },
                        isReverseScroll = { repo.prefManager.isReverseScroll() },
                        modifier = Modifier.fillMaxSize(),
                        onHoldingChanged = { holding ->
                            isHoldingFolderWheel = holding
                            if (!holding && selectedFolderId == "NEW_FOLDER") {
                                newFolderNameInput = ""
                            }
                        },
                        discCenterXPx = with(LocalDensity.current) { animatedDisc1CenterX.dp.toPx() },
                        discCenterYPx = with(LocalDensity.current) { (disc1CenterY - topPaddingDp.value - 60f).dp.toPx() },
                        onItemTap = { idx ->
                            val tappedId = folderItemsWithNew.getOrNull(idx)?.first
                            if (tappedId == "__BACK") {
                                navigateFolderAnimated {
                                    pendingFocusFolderId = folderPath.lastOrNull()
                                    folderPath = folderPath.dropLast(1)
                                }
                            } else if (tappedId == "NEW_FOLDER" && idx == folderSelectedIndex) {
                                createFolderParentId = currentFolderParentId
                                newFolderNameInput = ""
                                showCreateFolderDialog = true
                            } else {
                                folderDiscState.animateToIndex(idx)
                                if (tappedId == "NEW_FOLDER") {
                                    newFolderNameInput = ""
                                }
                            }
                        },
                        onItemLongPress = { idx ->
                            val id = folderItemsWithNew.getOrNull(idx)?.first
                            if (id != null && id != "NEW_FOLDER" && id != "__BACK") {
                                folderOptionTargetId = id
                            }
                        }
                    )
                }
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .weight(1f)
                )
            }
        }

        // --- タッチ操作パネル (左半分: アプリ一覧(2) & フォルダ(3)) ---
        if (!isTransitioning && (currentLauncherPage == 2 || (currentLauncherPage == 3 && isEditingFolder))) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(top = topPaddingDp + 60.dp, bottom = bottomPaddingDp + 60.dp)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .weight(1f)
                        .pointerInput(currentLauncherPage, isEditingFolder, indexDiscState, stepAngle) {
                            awaitPointerEventScope {
                                try {
                                    while (true) {
                                        val down = awaitFirstDown(requireUnconsumed = false)
                                        val isIndexWheel = (currentLauncherPage == 2 || (currentLauncherPage == 3 && isEditingFolder))
                                        if (isIndexWheel) {
                                            isHoldingLeft = true
                                        } else {
                                            isHoldingFolderWheel = true
                                        }
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)

                                        var lastY = down.position.y
                                        var maxDist = 0f
                                        val downTimeMs = System.currentTimeMillis()
                                        var accumulatedDy = 0f
                                        var currentStepCount = if (isIndexWheel) {
                                            (-indexDiscState.rawRotation / stepAngle).roundToInt()
                                        } else {
                                            (-folderWheelRotationAnim.value / folderStepAngle).roundToInt().coerceIn(0, folderItemsWithNew.size - 1)
                                        }
                                        val stepThreshold = 40f // スワイプの感度調整

                                        drag(down.id) { change ->
                                            change.consume()
                                            maxDist = maxOf(maxDist, (change.position - down.position).getDistance())
                                            // パネル駆動でもスクロール中フラグを立てる（索引の文字表示などが isScrolling を見ているため）
                                            if (isIndexWheel) indexDiscState.isScrolling = true
                                            val rawDeltaY = change.position.y - lastY
                                            val deltaY = rawDeltaY * (if (repo.prefManager.isReverseScroll()) -1f else 1f)
                                            lastY = change.position.y
                                            accumulatedDy += deltaY

                                            while (accumulatedDy >= stepThreshold) {
                                                accumulatedDy -= stepThreshold
                                                currentStepCount++
                                                if (isIndexWheel) {
                                                    val targetRotation = -currentStepCount * stepAngle
                                                    indexDiscState.rawRotation = targetRotation
                                                    coroutineScope.launch {
                                                        indexDiscState.rotationAnim.animateTo(
                                                            targetValue = targetRotation,
                                                            animationSpec = tween(durationMillis = 180, easing = FastOutSlowInEasing)
                                                        )
                                                    }
                                                    view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                                                } else {
                                                    if (folderItemsWithNew.isNotEmpty()) {
                                                        val minFolderRotation = -((folderItemsWithNew.size - 1) * folderStepAngle).coerceAtLeast(0f)
                                                        val maxFolderRotation = 0f
                                                        val clampedStep = currentStepCount.coerceIn(0, folderItemsWithNew.size - 1)
                                                        currentStepCount = clampedStep
                                                        val targetRotation = (-clampedStep * folderStepAngle).coerceIn(minFolderRotation, maxFolderRotation)
                                                        coroutineScope.launch {
                                                            folderWheelRotationAnim.animateTo(
                                                                targetValue = targetRotation,
                                                                animationSpec = tween(durationMillis = 180, easing = FastOutSlowInEasing)
                                                            )
                                                        }
                                                        view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                                                    }
                                                }
                                            }

                                            while (accumulatedDy <= -stepThreshold) {
                                                accumulatedDy += stepThreshold
                                                currentStepCount--
                                                if (isIndexWheel) {
                                                    val targetRotation = -currentStepCount * stepAngle
                                                    indexDiscState.rawRotation = targetRotation
                                                    coroutineScope.launch {
                                                        indexDiscState.rotationAnim.animateTo(
                                                            targetValue = targetRotation,
                                                            animationSpec = tween(durationMillis = 180, easing = FastOutSlowInEasing)
                                                        )
                                                    }
                                                    view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                                                } else {
                                                    if (folderItemsWithNew.isNotEmpty()) {
                                                        val minFolderRotation = -((folderItemsWithNew.size - 1) * folderStepAngle).coerceAtLeast(0f)
                                                        val maxFolderRotation = 0f
                                                        val clampedStep = currentStepCount.coerceIn(0, folderItemsWithNew.size - 1)
                                                        currentStepCount = clampedStep
                                                        val targetRotation = (-clampedStep * folderStepAngle).coerceIn(minFolderRotation, maxFolderRotation)
                                                        coroutineScope.launch {
                                                            folderWheelRotationAnim.animateTo(
                                                                targetValue = targetRotation,
                                                                animationSpec = tween(durationMillis = 180, easing = FastOutSlowInEasing)
                                                            )
                                                        }
                                                        view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                                                    }
                                                }
                                            }
                                        }

                                        // 指を離した瞬間: 索引ダイヤルのタップを数え、5回タップで非表示アプリリストを開く
                                        if (currentLauncherPage == 2 && !isEditingFolder) {
                                            val heldMs = System.currentTimeMillis() - downTimeMs
                                            if (maxDist < 14f && heldMs < 300L) {
                                                val nowTap = System.currentTimeMillis()
                                                indexTapCount = if (nowTap - lastIndexTapMs < 3000L) indexTapCount + 1 else 1
                                                lastIndexTapMs = nowTap
                                                if (indexTapCount >= 5) {
                                                    indexTapCount = 0
                                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                                    if (repo.prefManager.isUsePasscodeForHidden()) {
                                                        showPasscodeDialog = true
                                                    } else {
                                                        isHideMode = true
                                                    }
                                                }
                                            }
                                        }

                                        if (currentLauncherPage == 3 && folderItemsWithNew.isNotEmpty()) {
                                            if (selectedFolderId == "NEW_FOLDER") {
                                                newFolderNameInput = ""
                                            }
                                        }

                                        isHoldingLeft = false
                                        isHoldingFolderWheel = false
                                        indexDiscState.isScrolling = false
                                    }
                                } finally {
                                    isHoldingLeft = false
                                    isHoldingFolderWheel = false
                                    indexDiscState.isScrolling = false
                                }
                            }
                        }
                )
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .weight(1f)
                )
            }
        }


    // 昇順/降順の切替
    val toggleAppSortDirection: () -> Unit = {
        val next = !appSortDescending
        appSortDescending = next
        repo.prefManager.setAppSortDescending(next)
    }

    // アプリの並び順を巡回切替する（名前順 → よく使う順 → 最近使った順）
    val cycleAppSort: () -> Unit = {
        val modes = listOf("name", "usage", "recent")
        val next = modes[(modes.indexOf(appSortMode) + 1) % modes.size]
        appSortMode = next
        repo.prefManager.setAppSortMode(next)
        // 並び順を変えたときのトースト通知は出さない（ユーザーの希望）
        if (next != "name") {
            val granted = try {
                val appOps = context.getSystemService(android.content.Context.APP_OPS_SERVICE) as android.app.AppOpsManager
                appOps.checkOpNoThrow(android.app.AppOpsManager.OPSTR_GET_USAGE_STATS, android.os.Process.myUid(), context.packageName) == android.app.AppOpsManager.MODE_ALLOWED
            } catch (_: Exception) { false }
            if (!granted) {
                android.widget.Toast.makeText(context, "使用状況へのアクセスを許可してください", android.widget.Toast.LENGTH_LONG).show()
                try {
                    context.startActivity(android.content.Intent(android.provider.Settings.ACTION_USAGE_ACCESS_SETTINGS).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK))
                } catch (_: Exception) {
                }
            }
        }
    }

        // --- 画面上部 検索ボックス / 非表示リストを閉じるボタン ---
        if (currentLauncherPage == 2) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .zIndex(150f),
                contentAlignment = Alignment.TopCenter
            ) {
                Box(
                    modifier = Modifier.padding(top = headerTopDp + 10.dp),
                    contentAlignment = Alignment.TopCenter
                ) {
                if (isHideMode) {
                    Row(
                        modifier = Modifier
                            .width(320.dp)
                            .height(48.dp)
                            .pressScale()
                            .clickable { isHideMode = false }
                            .padding(horizontal = 16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = "非表示リストを閉じる ✕",
                            color = Color(0xFFFF5252),
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center
                        )
                    }
                } else {
                    Row(
                        modifier = Modifier
                            .width(320.dp)
                            .height(48.dp)
                            .padding(horizontal = 16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        WhiteSearchIcon(size = 18.dp, color = Color.White.copy(alpha = 0.85f))
                        Spacer(modifier = Modifier.width(8.dp))
                        BasicTextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            singleLine = true,
                            textStyle = androidx.compose.ui.text.TextStyle(
                                color = Color.White,
                                fontSize = 15.sp,
                                textAlign = TextAlign.Center
                            ),
                            cursorBrush = SolidColor(accentColor),
                            modifier = Modifier.weight(1f)
                        ) { innerTextField ->
                            Box(contentAlignment = Alignment.Center) {
                                if (searchQuery.isEmpty()) {
                                    Text(
                                        text = "アプリを検索…",
                                        color = Color.White.copy(alpha = 0.85f),
                                        fontSize = 14.sp,
                                        textAlign = TextAlign.Center
                                    )
                                }
                                innerTextField()
                            }
                        }

                        if (searchQuery.isNotEmpty()) {
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                text = "✕",
                                color = Color.White.copy(alpha = 0.9f),
                                fontSize = 15.sp,
                                modifier = Modifier
                                    .pressScale()
                                    .clickable { searchQuery = "" }
                                    .padding(4.dp)
                            )
                        }
                    }
                }
                }
            }
        }


        // --- 画面上部 ソートバー (アプリ一覧(2)用、フォルダ画面の編集バーと同じ形式) ---
        if (currentLauncherPage == 2 && !isHideMode) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .zIndex(150f),
                contentAlignment = Alignment.TopCenter
            ) {
                Row(
                    modifier = Modifier
                        .padding(top = headerTopDp + 66.dp)
                        .width(320.dp)
                        .height(48.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // 左半分: 現在の並び順（タップでも切り替わる）
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .pressScale(0.96f)
                            .clickable { cycleAppSort() },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = when (appSortMode) { "usage" -> "よく使う順"; "recent" -> "最近使った順"; else -> "名前順" },
                            color = Color.White.copy(alpha = 0.85f),
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    // 中央区切り線
                    Box(
                        modifier = Modifier
                            .width(1.dp)
                            .height(20.dp)
                            .background(Color.White.copy(alpha = 0.2f))
                    )

                    // 右半分: 昇順/降順の切替
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .pressScale(0.96f)
                            .clickable { toggleAppSortDirection() },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = if (appSortDescending) "降順" else "昇順",
                            color = accentColor,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }

        // --- 画面上部 フォルダ名＆編集バー / 新規作成バー (フォルダ画面(3)用、半透明ダークグラス) ---
        if (currentLauncherPage == 3) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .zIndex(150f),
                contentAlignment = Alignment.TopCenter
            ) {
                Box(
                    modifier = Modifier.padding(top = headerTopDp + 10.dp),
                    contentAlignment = Alignment.TopCenter
                ) {
                if (selectedFolderId != null && selectedFolderId != "__BACK") {
                    Row(
                        modifier = Modifier
                            .width(320.dp)
                            .height(48.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (isEditingFolder) {
                            // 編集モード中: 左半分 = 適用、右半分 = キャンセル
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxHeight()
                                    .pressScale(0.96f)
                                    .clickable {
                                        val name = editingFolderNameInput.ifBlank { selectedFolderName }
                                        repo.prefManager.saveFolder(selectedFolderId, name, editingSelectedPackages.toList())
                                        refreshApps()
                                        isEditingFolder = false
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.Center
                                ) {
                                    WhiteCheckIcon(size = 18.dp, color = Color(0xFF4CAF50))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "適用",
                                        color = Color(0xFF4CAF50),
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }

                            // 中央区切り線
                            Box(
                                modifier = Modifier
                                    .width(1.dp)
                                    .height(20.dp)
                                    .background(Color.White.copy(alpha = 0.2f))
                            )

                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxHeight()
                                    .pressScale(0.96f)
                                    .clickable {
                                        isEditingFolder = false
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.Center
                                ) {
                                    WhiteCloseIcon(size = 18.dp, color = Color.Gray)
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "キャンセル",
                                        color = Color.LightGray,
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        } else {
                            // 通常時: 左半分 = 編集、右半分 = 削除
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxHeight()
                                    .pressScale(0.96f)
                                    .clickable {
                                        editingFolderNameInput = selectedFolderName
                                        editingSelectedPackages = (foldersMap[selectedFolderId]?.second ?: emptyList()).toSet()
                                        isEditingFolder = true
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.Center
                                ) {
                                    WhiteEditIcon(size = 18.dp, color = accentColor)
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "編集",
                                        color = Color.White,
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }

                            // 中央区切り線
                            Box(
                                modifier = Modifier
                                    .width(1.dp)
                                    .height(20.dp)
                                    .background(Color.White.copy(alpha = 0.2f))
                            )

                            // お気に入りトグル（選択中フォルダが対象。一番上の階層のフォルダもここで登録できる）
                            val selFav = selectedFolderId in favoriteFolderIds
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxHeight()
                                    .pressScale(0.96f)
                                    .clickable {
                                        favoriteFolderIds = repo.prefManager.toggleFavoriteFolder(selectedFolderId)
                                        android.widget.Toast.makeText(context, if (selFav) "お気に入りから外しました" else "お気に入りに追加しました", android.widget.Toast.LENGTH_SHORT).show()
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.Center
                                ) {
                                    Text(
                                        text = if (selFav) "★" else "☆",
                                        color = if (selFav) accentColor else Color.Gray,
                                        fontSize = 16.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = "お気に入り",
                                        color = Color.White,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }

                            // 中央区切り線
                            Box(
                                modifier = Modifier
                                    .width(1.dp)
                                    .height(20.dp)
                                    .background(Color.White.copy(alpha = 0.2f))
                            )

                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxHeight()
                                    .pressScale(0.96f)
                                    .clickable {
                                        showDeleteFolderDialog = true
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.Center
                                ) {
                                    WhiteDeleteIcon(size = 18.dp, color = Color(0xFFFF5252))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "削除",
                                        color = Color(0xFFFF5252),
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }
                }
                }
            }
        }

        // --- 現在地チップ（フォルダ階層。タップで1つ戻る） ---
        if (currentLauncherPage == 3 && folderPath.isNotEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = headerTopDp + 70.dp)
                    .zIndex(150f),
                contentAlignment = Alignment.TopCenter
            ) {
                DiscInfoChip(
                    label = "‹ " + (foldersMap[folderPath.last()]?.first ?: "フォルダ"),
                    accentColor = accentColor,
                    onClick = {
                        navigateFolderAnimated {
                            pendingFocusFolderId = folderPath.lastOrNull()
                            folderPath = folderPath.dropLast(1)
                        }
                    }
                )
            }
        }

        // --- 画面上部 フォルダ名編集欄 (編集モード中) ---
        if (currentLauncherPage == 3 && isEditingFolder) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = headerTopDp + 102.dp)
                    .zIndex(150f),
                contentAlignment = Alignment.TopCenter
            ) {
                Row(
                    modifier = Modifier
                        .width(320.dp)
                        .height(44.dp)
                        .background(Color(0x99101728), RoundedCornerShape(14.dp))
                        .padding(horizontal = 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    BasicTextField(
                        value = editingFolderNameInput,
                        onValueChange = { editingFolderNameInput = it },
                        singleLine = true,
                        textStyle = androidx.compose.ui.text.TextStyle(
                            color = Color.White,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center,
                            fontFamily = LocalLauncherFontFamily.current
                        ),
                        cursorBrush = SolidColor(accentColor),
                        modifier = Modifier.weight(1f)
                    ) { innerTextField ->
                        Box(contentAlignment = Alignment.Center) {
                            if (editingFolderNameInput.isEmpty()) {
                                Text(
                                    text = "フォルダ名を入力...",
                                    color = Color.Gray.copy(alpha = 0.7f),
                                    fontSize = 14.sp,
                                    textAlign = TextAlign.Center
                                )
                            }
                            innerTextField()
                        }
                    }
                }
            }
        }

        // --- システムの戻るキー対応 ---
        // お気に入りの並び替えダイアログ（右端のつまみをドラッグして入れ替え）
        if (showFavReorderDialog) {
            BlurDialog(onDismissRequest = { showFavReorderDialog = false }) {

                    Text("お気に入りの並び替え", color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(12.dp))
                    Column {
                        if (favSortList.isEmpty()) {
                            Text("お気に入りがありません。", color = Color.LightGray, fontSize = 13.sp)
                        }
                        favSortList.forEach { entry ->
                            val rowKey = homeEntryKey(entry)
                            key(rowKey) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(52.dp)
                                        .zIndex(if (favDragKey == rowKey) 1f else 0f)
                                        .graphicsLayer { translationY = if (favDragKey == rowKey) favDragOffset else 0f }
                                        .background(if (favDragKey == rowKey) Color(0xFF37474F) else Color.Transparent)
                                ) {
                                    when (entry) {
                                        is HomeEntry.App -> {
                                            val app = entry.app
                                            if (app.iconBitmap != null) {
                                                Image(bitmap = app.iconBitmap, contentDescription = null, modifier = Modifier.size(32.dp).clip(RoundedCornerShape(8.dp)))
                                            } else {
                                                Box(modifier = Modifier.size(32.dp))
                                            }
                                            Spacer(modifier = Modifier.width(10.dp))
                                            Text(app.label, color = Color.White, fontSize = 15.sp, maxLines = 1, modifier = Modifier.weight(1f))
                                        }
                                        is HomeEntry.Folder -> {
                                            Box(modifier = Modifier.size(32.dp), contentAlignment = Alignment.Center) {
                                                Text("★", color = accentColor, fontSize = 16.sp)
                                            }
                                            Spacer(modifier = Modifier.width(10.dp))
                                            Text(entry.folder.name, color = Color.White, fontSize = 15.sp, maxLines = 1, modifier = Modifier.weight(1f))
                                        }
                                    }
                                    Box(
                                        modifier = Modifier.size(44.dp).pointerInput(rowKey) {
                                            detectDragGestures(
                                                onDragStart = { favDragKey = rowKey; favDragOffset = 0f },
                                                onDragEnd = { favDragKey = null; favDragOffset = 0f },
                                                onDragCancel = { favDragKey = null; favDragOffset = 0f },
                                                onDrag = { change, amount ->
                                                    change.consume()
                                                    favDragOffset += amount.y
                                                    val rowPx = 52.dp.toPx()
                                                    while (true) {
                                                        val idx = favSortList.indexOfFirst { homeEntryKey(it) == rowKey }
                                                        if (idx < 0) break
                                                        if (favDragOffset > rowPx / 2f && idx < favSortList.lastIndex) {
                                                            val next = favSortList.toMutableList()
                                                            val tmp = next[idx]; next[idx] = next[idx + 1]; next[idx + 1] = tmp
                                                            favSortList = next
                                                            favoriteOrder = next.map { homeEntryKey(it) }
                                                            repo.prefManager.setFavoriteOrder(favoriteOrder)
                                                            favDragOffset -= rowPx
                                                        } else if (favDragOffset < -rowPx / 2f && idx > 0) {
                                                            val next = favSortList.toMutableList()
                                                            val tmp = next[idx]; next[idx] = next[idx - 1]; next[idx - 1] = tmp
                                                            favSortList = next
                                                            favoriteOrder = next.map { homeEntryKey(it) }
                                                            repo.prefManager.setFavoriteOrder(favoriteOrder)
                                                            favDragOffset += rowPx
                                                        } else break
                                                    }
                                                }
                                            )
                                        },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text("≡", color = Color.LightGray, fontSize = 20.sp)
                                    }
                                }
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                    Box(
                        modifier = Modifier
                            .align(Alignment.End)
                            .pressScale()
                            .clickable { showFavReorderDialog = false }
                            .padding(horizontal = 8.dp, vertical = 6.dp)
                    ) {
                        Text("閉じる", color = accentColor, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                    }
            }
        }

        BackHandler(enabled = currentLauncherPage != 1 || isSearchActive || isEditingFolder || isLyricsBrowsing || pvFullscreen) {
            if (isLyricsBrowsing) {
                isLyricsBrowsing = false
            } else if (pvFullscreen) {
                pvFullscreen = false
            } else if (isEditingFolder) {
                isEditingFolder = false
            } else if (isSearchActive) {
                isSearchActive = false
                searchQuery = ""
                keyboardController?.hide()
            } else if (currentLauncherPage != 1) {
                changePageWithTransition(1)
            }
        }
        // ウィジェット選択ダイアログ（自前。システム標準のピッカーはこの端末だと開くときに落ちるため使わない）
        if (showWidgetPicker) {
            val providers = remember(showWidgetPicker) {
                try {
                    appWidgetManager.installedProviders.sortedBy { it.loadLabel(context.packageManager).lowercase() }
                } catch (_: Exception) { emptyList() }
            }
            Dialog(onDismissRequest = { showWidgetPicker = false }) {
                DialogBlurBehind()
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 540.dp)
                        .padding(18.dp)
                ) {
                    Text(text = "ウィジェットを追加", color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(12.dp))
                    LazyColumn(modifier = Modifier.weight(1f, fill = false)) {
                        items(providers) { info ->
                            val label = remember(info.provider) { try { info.loadLabel(context.packageManager) } catch (_: Exception) { info.provider.packageName } }
                            val preview = remember(info.provider) {
                                try {
                                    val d = info.loadPreviewImage(context, context.resources.displayMetrics.densityDpi)
                                        ?: info.loadIcon(context, context.resources.displayMetrics.densityDpi)
                                    if (d != null) {
                                        val bmp = Bitmap.createBitmap(96, 96, Bitmap.Config.ARGB_8888)
                                        val cv = android.graphics.Canvas(bmp)
                                        d.setBounds(0, 0, 96, 96)
                                        d.draw(cv)
                                        bmp
                                    } else null
                                } catch (_: Exception) { null }
                            }
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .pressScale(0.97f)
                                    .clickable {
                                        showWidgetPicker = false
                                        val wid = appWidgetHost.allocateAppWidgetId()
                                        val bound = try { appWidgetManager.bindAppWidgetIdIfAllowed(wid, info.provider) } catch (_: Exception) { false }
                                        if (bound) {
                                            proceedAfterWidgetBind(wid)
                                        } else {
                                            pendingWidgetId = wid
                                            widgetBindLauncher.launch(
                                                android.content.Intent(android.appwidget.AppWidgetManager.ACTION_APPWIDGET_BIND)
                                                    .putExtra(android.appwidget.AppWidgetManager.EXTRA_APPWIDGET_ID, wid)
                                                    .putExtra(android.appwidget.AppWidgetManager.EXTRA_APPWIDGET_PROVIDER, info.provider)
                                            )
                                        }
                                    }
                                    .padding(horizontal = 8.dp, vertical = 10.dp)
                            ) {
                                if (preview != null) {
                                    Image(bitmap = preview.asImageBitmap(), contentDescription = null, modifier = Modifier.size(40.dp).clip(RoundedCornerShape(8.dp)))
                                } else {
                                    Box(modifier = Modifier.size(40.dp).clip(RoundedCornerShape(8.dp)).background(Color.White.copy(alpha = 0.12f)))
                                }
                                Column(modifier = Modifier.padding(start = 12.dp)) {
                                    Text(text = label, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Medium)
                                    Text(text = "${info.minWidth} × ${info.minHeight} dp", color = Color.White.copy(alpha = 0.55f), fontSize = 12.sp)
                                }
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                    Box(
                        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Color.White.copy(alpha = 0.08f)).pressScale(0.97f).clickable { showWidgetPicker = false }.padding(vertical = 11.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(text = "キャンセル", color = Color.White, fontSize = 14.sp)
                    }
                }
            }
        }

        BackHandler(enabled = layoutEditMode) {
            layoutEditMode = false
        }

        // 歌詞ボタン: 時計と同じ高さ、画面右側。タップで歌詞全文表示（歌詞が無いときはタグ検索）、長押しで歌詞を再取得
        if (currentLauncherPage == 1 && !isLyricsBrowsing && !pvFullscreen && sessionTrackTitle.isNotBlank()) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = topPaddingDp + 48.dp, end = 22.dp)
                    .size(44.dp)
                    .zIndex(95f)
                    .combinedClickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = {
                            if (syncedLyricLines.isEmpty()) {
                                // 歌詞が取得できていないときは、タグを指定して手動検索できるようにする
                                showLyricSearch = true
                            } else {
                                lyricsBrowseStartIndex = syncedLyricLines.indexOfLast {
                                    it.timeMs <= sessionPositionMs + lyricOffsetMs
                                }.coerceAtLeast(0)
                                isLyricsBrowsing = true
                            }
                        },
                        onLongClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            LyricsManager.invalidateCache(context, sessionTrackTitle, sessionTrackArtist, sessionTrackAlbum)
                            syncedLyricLines = emptyList()
                            currentLyricLine = ""
                            lyricsRefreshTick++
                        }
                    ),
                contentAlignment = Alignment.Center
            ) {
                LucideIcon(
                    icon = LucideListMusic,
                    size = 24.dp,
                    color = accentColor,
                    modifier = Modifier.blur(6.dp)
                )
                LucideIcon(
                    icon = LucideListMusic,
                    size = 24.dp,
                    color = accentColor
                )
            }
        }

        // タグ指定で歌詞を検索するダイアログ
        if (showLyricSearch) {
            LyricTagSearchDialog(
                currentTitle = sessionTrackTitle,
                currentArtist = sessionTrackArtist,
                currentAlbum = sessionTrackAlbum,
                currentLines = syncedLyricLines,
                onApply = { applied, source ->
                    syncedLyricLines = applied
                    lyricsSource = source
                },
                onCleared = {
                    syncedLyricLines = emptyList()
                    lyricsSource = ""
                    lyricsRefreshTick++
                },
                onDismiss = { showLyricSearch = false }
            )
        }

        // アップデート通知ダイアログ
        if (showUpdateDialog && updateInfo != null) {
            UpdateDialog(
                info = updateInfo!!,
                onDismiss = {
                    showUpdateDialog = false
                    UpdateChecker.markDismissed(context, updateInfo!!.buildNumber)
                }
            )
        }

        // フルスクリーンボタン: 歌詞ボタンの下。押すと横画面で歌詞だけの画面になる
        if (currentLauncherPage == 1 && !isLyricsBrowsing && !pvFullscreen && sessionTrackTitle.isNotBlank()) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = topPaddingDp + 100.dp, end = 22.dp)
                    .size(44.dp)
                    .zIndex(95f)
                    .pressScale()
                    .clickable { pvFullscreen = true },
                contentAlignment = Alignment.Center
            ) {
                LucideIcon(
                    icon = LucideMaximize,
                    size = 24.dp,
                    color = accentColor
                )
            }
        }

        // 間奏スキップ（通常モード用・最前面）: PVレイヤーの中にあると円盤にタップを奪われるため、ここに出す
        if (currentLauncherPage == 1 && !isLyricsBrowsing && !pvFullscreen && lyricsDisplayMode == "video" && !dopagakiMode && syncedLyricLines.isNotEmpty()) {
            val skipPosMs = sessionPositionMs + lyricOffsetMs
            val skipIdx = syncedLyricLines.indexOfLast { it.timeMs <= skipPosMs }
            val skipCur = syncedLyricLines.getOrNull(skipIdx)
            val skipNext = syncedLyricLines.getOrNull(skipIdx + 1)
            val skipInterlude = when {
                skipCur == null -> skipIdx < 0 && (syncedLyricLines.firstOrNull()?.let { (it.timeMs - skipPosMs) > 2500 } ?: false)
                skipNext == null -> (skipPosMs - skipCur.timeMs) > 8000
                else -> (skipNext.timeMs - skipCur.timeMs) > 15000 && (skipPosMs - skipCur.timeMs) > 5000
            }
            if (skipInterlude) {
                val skipLabel = when {
                    skipNext != null && skipCur == null -> "前奏をスキップ▷"
                    skipNext != null -> "間奏をスキップ▷"
                    else -> "次の曲へ▷"
                }
                val skipTarget = skipNext?.let { (it.timeMs - lyricOffsetMs - 300L).coerceAtLeast(0L) }
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = topPaddingDp + 145.dp + 44.dp, end = 16.dp)
                        .zIndex(96f),
                    contentAlignment = Alignment.CenterEnd
                ) {
                    Text(
                        text = skipLabel,
                        color = Color.White.copy(alpha = 0.85f),
                        fontSize = 13.sp,
                        style = LocalTextStyle.current.copy(fontFamily = pvFontFamily),
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .pressScale()
                            .clickable {
                                android.widget.Toast.makeText(context, if (skipLabel.contains("次の曲")) "次の曲へ" else if (skipLabel.contains("前奏")) "前奏をスキップ" else "間奏をスキップ", android.widget.Toast.LENGTH_SHORT).show()
                                if (skipTarget != null) seekSessionTo(skipTarget) else skipToNextTrack()
                            }
                            .padding(horizontal = 12.dp, vertical = 10.dp)
                    )
                }
            }
        }

        }

        // 歌詞ブラウズ（全文表示）: ドラッグでステップスクロール、同期モードでは再生に追従
        if (isLyricsBrowsing && syncedLyricLines.isNotEmpty()) {
            LyricsBrowseOverlay(
                artwork = sessionAlbumArt,
                trackTitle = sessionTrackTitle,
                trackArtist = sessionTrackArtist,
                lines = syncedLyricLines,
                startIndex = lyricsBrowseStartIndex,
                positionMs = sessionPositionMs,
                lyricOffsetMs = lyricOffsetMs,
                onOffsetChange = { newOffset ->
                    lyricOffsetMs = newOffset
                    repo.prefManager.setLyricOffsetMs(sessionTrackTitle, sessionTrackArtist, newOffset)
                },
                fontFamily = lyricsFontFamily,
                enFontFamily = lyricsEnFontArg,
                uiFontFamily = currentFontFamily,
                lyricWeight = lyricWeight,
                accentColor = accentColor,
                accentGlow = true,
                topPaddingDp = topPaddingDp,
                source = lyricsSource,
                translationEnabled = translationEnabled,
                translations = lyricTranslationsHead,
                onToggleTranslation = {
                    translationEnabled = !translationEnabled
                    repo.prefManager.setLyricTranslationEnabled(translationEnabled)
                },
                reverseScroll = repo.prefManager.isReverseScroll(),
                onSeek = { pos -> seekSessionTo(pos) },
                onRefresh = {
                    LyricsManager.invalidateCache(context, sessionTrackTitle, sessionTrackArtist, sessionTrackAlbum)
                    syncedLyricLines = emptyList()
                    currentLyricLine = ""
                    lyricsRefreshTick++
                    isLyricsBrowsing = false
                },
                onOpenSearch = { showLyricSearch = true },
                onSaveEditedLyrics = { editedText ->
                    LyricsManager.saveEditedLyrics(context, sessionTrackTitle, sessionTrackArtist, sessionTrackAlbum, editedText)
                    lyricsRefreshTick++
                },
                onPublishLyrics = { lrcText, report ->
                    coroutineScope.launch {
                        val err = LyricsManager.publishToLrclib(
                            sessionTrackTitle, sessionTrackArtist, sessionTrackAlbum,
                            (sessionDurationMs / 1000).toInt(), lrcText
                        )
                        report(if (err == null) "OK" else err)
                    }
                },
                dopagaki = dopagakiMode,
                onToggleDopagaki = {
                    dopagakiMode = !dopagakiMode
                    repo.prefManager.setDopagakiEnabled(dopagakiMode)
                },
                pvEnabled = pvEnabled,
                onTogglePv = {
                    pvEnabled = !pvEnabled
                    repo.prefManager.setPvEnabled(pvEnabled)
                },
                homeLyricsEnabled = lyricsEnabled,
                onToggleHomeLyrics = {
                    lyricsEnabled = !lyricsEnabled
                    repo.prefManager.setLyricsEnabled(lyricsEnabled)
                    if (!lyricsEnabled) {
                        com.example.nissingeppolauncher.service.LyricsOverlayService.stop(context)
                    }
                },
                onClose = { isLyricsBrowsing = false }
            )
        }

        // 歌詞フルスクリーン（横画面）: PVモードならPVだけ、1行/複数行モードなら曲名などと一緒に表示
        if (pvFullscreen && currentLauncherPage == 1) {
            LyricsFullscreenOverlay(
                pvTilt3d = disc3d,
                lines = syncedLyricLines,
                positionMs = sessionPositionMs,
                lyricOffsetMs = lyricOffsetMs,
                displayMode = lyricsDisplayMode,
                bold = lyricsBold,
                fontFamily = pvFontFamily,
                enFontFamily = lyricsEnFontArg,
                lyricWeight = lyricWeight,
                accentColor = effectiveAccent,
                artwork = sessionAlbumArt,
                artworkBackground = artworkBackground,
                liquidEnabled = liquidBackground,
                title = sessionTrackTitle,
                artist = sessionTrackArtist,
                album = sessionTrackAlbum,
                partsEntry = pvPartsEntry,
                dopagaki = dopagakiMode,
                tiltX = discTiltX,
                tiltY = discTiltY,
                pvFontScale = pvFontScale,
                pvBaseColor = when (pvTextColor) { "accent" -> effectiveAccent; "black" -> Color.Black; else -> Color.White },
                pvRuby = pvRuby,
                pvInterludeArt = pvInterludeArt,
                liquidSpeed = liquidSpeed,
                liquidAlphaMul = liquidAlpha,
                onClose = { pvFullscreen = false },
                onSeek = { pos -> seekSessionTo(pos) },
                onNextTrack = { skipToNextTrack() }
            )
        }

        // 非表示モード中は背景を段階的に暗くする
        LaunchedEffect(isHideMode) {
            if (isHideMode) {
                for (i in 1..3) { delay(110); hideDimStep = i }
            } else if (hideDimStep > 0) {
                for (i in 2 downTo 0) { delay(80); hideDimStep = i }
            }
        }
        // --- ダイアログ類 ---
        // 0. パスコード認証ダイアログ (非表示リスト用)
        if (showPasscodeDialog) {
            PasscodeDialog(
                accentColor = accentColor,
                onDismiss = { showPasscodeDialog = false },
                onSuccess = {
                    isHideMode = true
                }
            )
        }

        // 0.0 ウェルカムチュートリアル
        if (showWelcomeTutorial) {
            TutorialDialog(
                title = "インストールしていただきありがとうございます",
                message = "インストールしていただきありがとうございます！\n\nアプリをお気に入り登録するには、アプリ一覧（三本線）でアプリを長押しし、「お気に入りに追加」をタップします。",
                onDismiss = { dontShow ->
                    if (dontShow) {
                        repo.prefManager.setFirstWelcomeTutorialShown(true)
                    }
                    showWelcomeTutorial = false
                }
            )
        }

        // 0.1 インデックスダイヤル解説チュートリアル
        if (showIndexTutorial) {
            TutorialDialog(
                title = "インデックスダイヤルの使い方",
                message = "左側の円（インデックスダイヤル）を上下にスワイプして回転させると、頭文字（あ・か・さ・た・な…）ごとにアプリリストを素早くジャンプ・スクロールできます。",
                onDismiss = { dontShow ->
                    if (dontShow) {
                        repo.prefManager.setFirstIndexTutorialShown(true)
                    }
                    showIndexTutorial = false
                    if (!repo.prefManager.isFirstHideTutorialShown()) {
                        showHideTutorial = true
                    }
                }
            )
        }

        // 0.2 非表示リスト右スワイプチュートリアル
        if (showHideTutorial) {
            TutorialDialog(
                title = "非表示リストの開き方",
                message = "アプリ一覧の左にある索引ダイヤルを5回タップすると、パスコード認証を経て「非表示アプリリスト」を開くことができます。",
                onDismiss = { dontShow ->
                    if (dontShow) {
                        repo.prefManager.setFirstHideTutorialShown(true)
                    }
                    showHideTutorial = false
                }
            )
        }



        // 3. フォルダ削除確認ダイアログ
        // --- 新規フォルダ作成ダイアログ ---
        if (showCreateFolderDialog) {
                        BlurDialog(onDismissRequest = { showCreateFolderDialog = false }) {
                Text("新規フォルダ", color = Color.White, fontSize = 18.sp)
                Spacer(modifier = Modifier.height(10.dp))
                BasicTextField(
                        value = newFolderNameInput,
                        onValueChange = { newFolderNameInput = it },
                        singleLine = true,
                        textStyle = androidx.compose.ui.text.TextStyle(color = Color.White, fontSize = 16.sp),
                        cursorBrush = SolidColor(accentColor),
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, Color.Gray.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                            .padding(12.dp)
                    ) { innerTextField ->
                        Box {
                            if (newFolderNameInput.isEmpty()) {
                                Text("フォルダ名を入力...", color = Color.Gray.copy(alpha = 0.7f), fontSize = 14.sp)
                            }
                            innerTextField()
                        }
                    }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = { showCreateFolderDialog = false }) {
                        Text("キャンセル", color = Color.Gray)
                    }
                    TextButton(
                        onClick = {
                            val name = newFolderNameInput.ifBlank { "新規フォルダ" }
                            val newId = System.currentTimeMillis().toString()
                            repo.prefManager.saveFolder(newId, name, emptyList())
                            if (createFolderParentId != null) repo.prefManager.setFolderParent(newId, createFolderParentId)
                            refreshApps()
                            newFolderNameInput = ""
                            showCreateFolderDialog = false
                        }
                    ) {
                        Text("作成", color = accentColor, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        if (showDeleteFolderDialog && selectedFolderId != null && selectedFolderId != "__BACK") {
                        BlurDialog(onDismissRequest = { showDeleteFolderDialog = false }) {
                Text("フォルダの削除", color = Color.White, fontSize = 18.sp)
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                        text = "「$selectedFolderName」を削除してもよろしいですか？（フォルダ内のアプリ自体はアンインストールされません）",
                        color = Color.LightGray,
                        fontSize = 14.sp
                    )
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = { showDeleteFolderDialog = false }) {
                        Text("キャンセル", color = Color.Gray)
                    }
                    TextButton(
                        onClick = {
                            val delId = selectedFolderId
                            if (delId != null && delId != "__BACK" && delId != "NEW_FOLDER") {
                                val delParent = folderParents[delId]
                                // 子フォルダは、削除するフォルダの親へ付け替える
                                foldersMap.keys.filter { folderParents[it] == delId }.forEach { childId ->
                                    repo.prefManager.setFolderParent(childId, delParent)
                                }
                                // 中のアプリは親フォルダへ移す（ルートの場合はフォルダから外れるだけ）
                                if (delParent != null) {
                                    val merged = (foldersMap[delParent]?.second ?: emptyList()).toMutableList()
                                    (foldersMap[delId]?.second ?: emptyList()).forEach { pkg ->
                                        if (!merged.contains(pkg)) merged.add(pkg)
                                    }
                                    repo.prefManager.saveFolder(delParent, foldersMap[delParent]?.first ?: "", merged)
                                }
                                repo.prefManager.deleteFolder(delId)
                                repo.prefManager.setFolderIcon(delId, null)
                                folderIcons = repo.prefManager.getFolderIcons()
                                repo.prefManager.setFolderParent(delId, null)
                            }
                            refreshApps()
                            showDeleteFolderDialog = false
                        }
                    ) {
                        Text("削除", color = Color(0xFFFF5252), fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        // フォルダの長押しポップアップ（お気に入り切替・アイコン変更）
        folderOptionTargetId?.let { fid ->
            FolderOptionDialog(
                folderName = foldersMap[fid]?.first ?: "",
                isFavorite = fid in favoriteFolderIds,
                currentIconKind = folderIcons[fid] ?: "",
                accentColor = accentColor,
                onToggleFavorite = {
                    favoriteFolderIds = repo.prefManager.toggleFavoriteFolder(fid)
                },
                onPickIcon = { kind ->
                    repo.prefManager.setFolderIcon(fid, kind)
                    folderIcons = repo.prefManager.getFolderIcons()
                },
                onDismiss = { folderOptionTargetId = null }
            )
        }

        // 3. フォルダ内アプリ選択モーダル
        if (showFolderAppSelectDialog && selectedFolderId != null) {
            FolderAppSelectDialog(
                folderName = selectedFolderName,
                allApps = allApps,
                selectedPackages = foldersMap[selectedFolderId]?.second ?: emptyList(),
                onDismiss = { showFolderAppSelectDialog = false },
                onSave = { updatedPackages ->
                    repo.prefManager.saveFolder(selectedFolderId, selectedFolderName, updatedPackages)
                    refreshApps()
                }
            )
        }

        // 4. アプリ長押し設定ダイアログ
        selectedAppForOption?.let { app ->
            AppOptionDialogHost(
                app = app,
                repo = repo,
                foldersMap = foldersMap,
                coroutineScope = coroutineScope,
                onRefresh = { refreshApps() },
                onDismiss = { selectedAppForOption = null }
            )
        }
    }
}
}

/**
 * アートワークからテーマカラーを抽出する（追加ライブラリなしの簡易実装）。
 * 戻り値: (アクセント色=前景, 背景色)
 * アクセントは「画素数 × 平均彩度」が最大の色相グループの平均色、
 * 背景は暗い画素の平均色（なければ全体平均を暗く落とす）。
 */
/** 相対輝度ベースのコントラスト比（WCAG方式、1.0〜21.0）。音楽アプリと同一実装 */
private fun contrastRatio(a: Color, b: Color): Float {
    fun luminance(c: Color): Float {
        fun lin(v: Float): Float = if (v <= 0.03928f) v / 12.92f else ((v + 0.055f) / 1.055f).pow(2.4f)
        return 0.2126f * lin(c.red) + 0.7152f * lin(c.green) + 0.0722f * lin(c.blue)
    }
    val l1 = luminance(a)
    val l2 = luminance(b)
    return (max(l1, l2) + 0.05f) / (min(l1, l2) + 0.05f)
}

/** 無彩色画像用: 元の色味を保ったまま、明度だけを指定値にしたグレー階調を作る */
private fun grayToneOf(color: Color, value: Float): Color {
    val hsv = FloatArray(3)
    android.graphics.Color.RGBToHSV(
        (color.red * 255f).toInt().coerceIn(0, 255),
        (color.green * 255f).toInt().coerceIn(0, 255),
        (color.blue * 255f).toInt().coerceIn(0, 255),
        hsv
    )
    hsv[2] = value
    val c = android.graphics.Color.HSVToColor(hsv)
    return Color(
        red = android.graphics.Color.red(c),
        green = android.graphics.Color.green(c),
        blue = android.graphics.Color.blue(c)
    )
}

/** サブ色が一次色と似すぎている場合に、色相を補色寄り（+150度）へ逃がして区別できる色にする。
 *  無彩色同士は色相が意味を持たないため対象外（明度差で区別する） */
private fun ensureSubDistinct(accent: Color, sub: Color): Color {
    val a = FloatArray(3)
    val s = FloatArray(3)
    android.graphics.Color.RGBToHSV(
        (accent.red * 255f).toInt().coerceIn(0, 255),
        (accent.green * 255f).toInt().coerceIn(0, 255),
        (accent.blue * 255f).toInt().coerceIn(0, 255),
        a
    )
    android.graphics.Color.RGBToHSV(
        (sub.red * 255f).toInt().coerceIn(0, 255),
        (sub.green * 255f).toInt().coerceIn(0, 255),
        (sub.blue * 255f).toInt().coerceIn(0, 255),
        s
    )
    if (a[1] < 0.08f || s[1] < 0.08f) return sub
    val hueDist = kotlin.math.min(kotlin.math.abs(a[0] - s[0]), 360f - kotlin.math.abs(a[0] - s[0]))
    val tooSimilar = hueDist < 50f || (hueDist < 90f && kotlin.math.abs(a[1] - s[1]) < 0.15f && kotlin.math.abs(a[2] - s[2]) < 0.15f)
    if (!tooSimilar) return sub
    s[0] = (a[0] + 150f) % 360f
    val c = android.graphics.Color.HSVToColor(s)
    return Color(
        red = android.graphics.Color.red(c),
        green = android.graphics.Color.green(c),
        blue = android.graphics.Color.blue(c)
    )
}

/** 色相だけを回した色を返す（サブ色のフォールバック生成用） */
private fun rotateHueColor(color: Color, hueDelta: Float): Color {
    val hsv = FloatArray(3)
    android.graphics.Color.RGBToHSV(
        (color.red * 255f).toInt().coerceIn(0, 255),
        (color.green * 255f).toInt().coerceIn(0, 255),
        (color.blue * 255f).toInt().coerceIn(0, 255),
        hsv
    )
    hsv[0] = (hsv[0] + hueDelta + 360f) % 360f
    val c = android.graphics.Color.HSVToColor(hsv)
    return Color(
        red = android.graphics.Color.red(c),
        green = android.graphics.Color.green(c),
        blue = android.graphics.Color.blue(c)
    )
}

/** 同じ色相・彩度のまま、明度だけを下限まで上げる（白へ混ぜると彩度が落ちるため HSV で持ち上げる） */
private fun raiseBrightnessKeepingHue(color: Color, minValue: Float): Color {
    val hsv = FloatArray(3)
    android.graphics.Color.RGBToHSV(
        (color.red * 255f).toInt().coerceIn(0, 255),
        (color.green * 255f).toInt().coerceIn(0, 255),
        (color.blue * 255f).toInt().coerceIn(0, 255),
        hsv
    )
    if (hsv[2] >= minValue) return color
    hsv[2] = minValue
    val c = android.graphics.Color.HSVToColor(hsv)
    return Color(
        red = android.graphics.Color.red(c),
        green = android.graphics.Color.green(c),
        blue = android.graphics.Color.blue(c)
    )
}

/** 同じ色相・彩度のまま明度を少しだけ上げる（コントラスト調整のステップ用） */
private fun brightenStepKeepingHue(color: Color): Color {
    val hsv = FloatArray(3)
    android.graphics.Color.RGBToHSV(
        (color.red * 255f).toInt().coerceIn(0, 255),
        (color.green * 255f).toInt().coerceIn(0, 255),
        (color.blue * 255f).toInt().coerceIn(0, 255),
        hsv
    )
    hsv[2] = (hsv[2] + 0.07f).coerceAtMost(1f)
    val c = android.graphics.Color.HSVToColor(hsv)
    return Color(
        red = android.graphics.Color.red(c),
        green = android.graphics.Color.green(c),
        blue = android.graphics.Color.blue(c)
    )
}

/** アクセント用に彩度と明度を保証する: 黒・白・灰に寄った色は色味のある範囲へ寄せる。
 *  ほぼ無彩色（彩度0.08未満）の場合は色相が定まらないためそのまま返す */
private fun vividAccentColor(color: Color): Color {
    val hsv = FloatArray(3)
    android.graphics.Color.RGBToHSV(
        (color.red * 255f).toInt().coerceIn(0, 255),
        (color.green * 255f).toInt().coerceIn(0, 255),
        (color.blue * 255f).toInt().coerceIn(0, 255),
        hsv
    )
    if (hsv[1] < 0.08f) return color
    hsv[1] = maxOf(hsv[1], 0.38f)
    hsv[2] = hsv[2].coerceIn(0.62f, 0.95f)
    val c = android.graphics.Color.HSVToColor(hsv)
    return Color(
        red = android.graphics.Color.red(c),
        green = android.graphics.Color.green(c),
        blue = android.graphics.Color.blue(c)
    )
}

/** アートワーク／壁紙から抽出したパレット（一次色・サブ・第3色・背景） */
private data class ArtworkPalette(val primary: Color, val secondary: Color, val third: Color, val background: Color)

/** 2色の色相距離（0〜180度） */
private fun hueDistanceOf(a: Color, b: Color): Float {
    val ha = FloatArray(3)
    val hb = FloatArray(3)
    android.graphics.Color.RGBToHSV((a.red * 255f).toInt().coerceIn(0, 255), (a.green * 255f).toInt().coerceIn(0, 255), (a.blue * 255f).toInt().coerceIn(0, 255), ha)
    android.graphics.Color.RGBToHSV((b.red * 255f).toInt().coerceIn(0, 255), (b.green * 255f).toInt().coerceIn(0, 255), (b.blue * 255f).toInt().coerceIn(0, 255), hb)
    val d = kotlin.math.abs(ha[0] - hb[0])
    return kotlin.math.min(d, 360f - d)
}

private fun extractArtworkColors(bitmap: Bitmap): ArtworkPalette {
    val size = 48
    val scaled = if (bitmap.width == size && bitmap.height == size) bitmap
        else Bitmap.createScaledBitmap(bitmap, size, size, true)
    val bucketCount = 12
    val counts = IntArray(bucketCount)
    val satSums = FloatArray(bucketCount)
    val rSums = LongArray(bucketCount)
    val gSums = LongArray(bucketCount)
    val bSums = LongArray(bucketCount)
    val hsv = FloatArray(3)
    var darkCount = 0
    var darkR = 0L; var darkG = 0L; var darkB = 0L
    var totalR = 0L; var totalG = 0L; var totalB = 0L
    val totalPx = size * size
    for (y in 0 until size) {
        for (x in 0 until size) {
            val px = scaled.getPixel(x, y)
            val r = android.graphics.Color.red(px)
            val g = android.graphics.Color.green(px)
            val b = android.graphics.Color.blue(px)
            totalR += r; totalG += g; totalB += b
            android.graphics.Color.RGBToHSV(r, g, b, hsv)
            if (hsv[1] > 0.22f && hsv[2] > 0.22f) {
                val bucket = (hsv[0] / 30f).toInt().coerceIn(0, bucketCount - 1)
                counts[bucket]++
                satSums[bucket] += hsv[1]
                rSums[bucket] += r; gSums[bucket] += g; bSums[bucket] += b
            }
            val lum = (0.299f * r + 0.587f * g + 0.114f * b) / 255f
            if (lum < 0.35f) {
                darkCount++; darkR += r; darkG += g; darkB += b
            }
        }
    }
    if (scaled !== bitmap) scaled.recycle()

    var bestBucket = -1
    var bestScore = -1f
    for (i in 0 until bucketCount) {
        if (counts[i] == 0) continue
        val score = counts[i] * (satSums[i] / counts[i])
        if (score > bestScore) {
            bestScore = score
            bestBucket = i
        }
    }
    var accent = if (bestBucket >= 0) {
        Color(
            red = (rSums[bestBucket] / counts[bestBucket]).toInt(),
            green = (gSums[bestBucket] / counts[bestBucket]).toInt(),
            blue = (bSums[bestBucket] / counts[bestBucket]).toInt()
        )
    } else {
        // 無彩色寄りのアートワークでは全体の平均色を使う
        Color(
            red = (totalR / totalPx).toInt(),
            green = (totalG / totalPx).toInt(),
            blue = (totalB / totalPx).toInt()
        )
    }
    // 暗すぎるアクセントは、白へ混ぜずに同じ色相・彩度のまま明度だけを上げて視認性を確保する
    val accentLum = 0.299f * accent.red + 0.587f * accent.green + 0.114f * accent.blue
    if (accentLum < 0.22f) {
        accent = raiseBrightnessKeepingHue(accent, 0.65f)
    }

    accent = vividAccentColor(accent)

    // サブカラー: 一次色と色相が離れた（60度以上）バケットのうち最も強いもの。
    // 候補が弱い・無い場合は一次色の色相を40度回して作る
    var secondary = accent
    var third = accent
    var subBucket = -1
    if (bestBucket >= 0) {
        var subScore = -1f
        for (i in 0 until bucketCount) {
            if (counts[i] == 0) continue
            val dist = kotlin.math.min(kotlin.math.abs(i - bestBucket), bucketCount - kotlin.math.abs(i - bestBucket))
            if (dist < 2) continue
            val score = counts[i] * (satSums[i] / counts[i])
            if (score > subScore) { subScore = score; subBucket = i }
        }
        secondary = if (subBucket >= 0 && subScore > bestScore * 0.18f) {
            Color(
                red = (rSums[subBucket] / counts[subBucket]).toInt(),
                green = (gSums[subBucket] / counts[subBucket]).toInt(),
                blue = (bSums[subBucket] / counts[subBucket]).toInt()
            )
        } else {
            rotateHueColor(accent, 40f)
        }
        val subLum = 0.299f * secondary.red + 0.587f * secondary.green + 0.114f * secondary.blue
        if (subLum < 0.22f) {
            secondary = raiseBrightnessKeepingHue(secondary, 0.65f)
        }
        // 第3色（装飾専用）: 一次色・サブの両方から色相が離れたバケットのうち最も強いもの。
        // 無ければトライアド（120度/240度）のうちサブから遠い方で作る
        var thirdBucket = -1
        var thirdScore = -1f
        for (i in 0 until bucketCount) {
            if (counts[i] == 0) continue
            val dAcc = kotlin.math.min(kotlin.math.abs(i - bestBucket), bucketCount - kotlin.math.abs(i - bestBucket))
            if (dAcc < 2) continue
            if (subBucket >= 0) {
                val dSub = kotlin.math.min(kotlin.math.abs(i - subBucket), bucketCount - kotlin.math.abs(i - subBucket))
                if (dSub < 2) continue
            }
            val score = counts[i] * (satSums[i] / counts[i])
            if (score > thirdScore) { thirdScore = score; thirdBucket = i }
        }
        third = if (thirdBucket >= 0 && thirdScore > bestScore * 0.12f) {
            val rawThird = Color(
                red = (rSums[thirdBucket] / counts[thirdBucket]).toInt(),
                green = (gSums[thirdBucket] / counts[thirdBucket]).toInt(),
                blue = (bSums[thirdBucket] / counts[thirdBucket]).toInt()
            )
            val thirdLum = 0.299f * rawThird.red + 0.587f * rawThird.green + 0.114f * rawThird.blue
            if (thirdLum < 0.22f) raiseBrightnessKeepingHue(rawThird, 0.65f) else rawThird
        } else {
            val t120 = rotateHueColor(accent, 120f)
            val t240 = rotateHueColor(accent, 240f)
            if (hueDistanceOf(t120, secondary) >= hueDistanceOf(t240, secondary)) t120 else t240
        }
    }

    secondary = vividAccentColor(secondary)
    secondary = ensureSubDistinct(accent, secondary)
    third = vividAccentColor(third)
    if (hueDistanceOf(third, secondary) < 50f && hueDistanceOf(third, accent) >= 50f) {
        third = rotateHueColor(third, 90f)
    }

    var background = if (darkCount > 0) {
        Color(
            red = (darkR / darkCount).toInt(),
            green = (darkG / darkCount).toInt(),
            blue = (darkB / darkCount).toInt()
        )
    } else {
        Color(
            red = (totalR / totalPx).toInt(),
            green = (totalG / totalPx).toInt(),
            blue = (totalB / totalPx).toInt()
        )
    }
    // 背景が明るすぎる場合は文字が読める明るさまで落とす
    val bgLum = 0.299f * background.red + 0.587f * background.green + 0.114f * background.blue
    if (bgLum > 0.16f) {
        val f = 0.16f / bgLum
        background = Color(red = background.red * f, green = background.green * f, blue = background.blue * f)
    }
    if (bestBucket < 0) {
        // 無彩色（グレー系）の画像: 色味が取れないため、明るさの階調でパレットを作る。
        // 明るいグレーはアクセントとして使えるので、一次色は明るく、サブは一段暗くする
        val base = accent
        accent = grayToneOf(base, 0.85f)
        secondary = grayToneOf(base, 0.60f)
        third = grayToneOf(base, 0.45f)
    }
    return ArtworkPalette(accent, secondary, third, background)
}

// 複数行モードの歌詞表示（VerifyError 回避のため MainLauncherScreen から切り出したトップレベル関数）
@Composable
private fun LauncherMultiLineLyrics(
    lines: List<LyricLine>,
    positionMs: Long,
    lyricOffsetMs: Long,
    translationEnabled: Boolean,
    translations: List<String>?,
    fontFamily: FontFamily?,
    enFontFamily: FontFamily? = null,
    lyricWeight: FontWeight,
    accentColor: Color,
    screenWidthDp: Float,
    topPaddingDp: Dp,
    dopagaki: Boolean = false,
    inLayoutPart: Boolean = false
) {
    val multiLines = lines
    // ドパガキ中もサイズは通常のまま。今の行にだけ虹色グローをかける
    val curBase = 30f
    val sideBase = 18f
    val curShadow = if (dopagaki) Shadow(color = dopaRainbowColor(seed = 17).copy(alpha = 0.9f), blurRadius = 26f) else Shadow(color = accentColor.copy(alpha = 0.8f), blurRadius = 16f)
    val mPos = positionMs + lyricOffsetMs
    val mRaw = multiLines.indexOfLast { it.timeMs <= mPos }
    val mCur = multiLines.getOrNull(mRaw)
    val mNext = multiLines.getOrNull(mRaw + 1)
    val mInterlude = when {
        mCur == null -> true
        mNext == null -> (mPos - mCur.timeMs) > 8000
        else -> (mNext.timeMs - mCur.timeMs) > 15000 && (mPos - mCur.timeMs) > 5000
    }
    Crossfade(targetState = mRaw, animationSpec = tween(250), label = "multiLyrics") { rawIdx ->
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = if (inLayoutPart) 0.dp else 28.dp, end = 24.dp, top = if (inLayoutPart) 0.dp else topPaddingDp + 165.dp)
        ) {
            val prevText = multiLines.getOrNull(rawIdx - 1)?.text
            val curText = multiLines.getOrNull(rawIdx)?.text
            val nextText = multiLines.getOrNull(rawIdx + 1)?.text
            if (prevText != null) {
                Text(lyricMixedFontText(prevText, enFontFamily), color = Color.White.copy(alpha = 0.45f), fontSize = sideBase.sp, style = LocalTextStyle.current.copy(fontFamily = fontFamily), modifier = Modifier.padding(start = 0.dp))
                if (translationEnabled) {
                    val trP = translations?.getOrNull(rawIdx - 1)
                    if (!trP.isNullOrBlank()) {
                        Text(trP, color = Color.White.copy(alpha = 0.35f), fontSize = 10.sp, style = LocalTextStyle.current.copy(fontFamily = fontFamily))
                    }
                }
            } else {
                Spacer(Modifier.height(24.dp))
            }
            Spacer(Modifier.height(16.dp))
            val curLineObj = multiLines.getOrNull(rawIdx)
            if (!mInterlude && curLineObj != null && curLineObj.words.isNotEmpty()) {
                KaraokeLyricText(
                    line = curLineObj,
                    positionMs = positionMs + lyricOffsetMs,
                    sungColor = accentColor,
                    baseColor = Color.White,
                    fontSize = curBase.sp,
                    fontWeight = lyricWeight,
                    fontFamily = fontFamily,
                    enFontFamily = enFontFamily,
                    maxLines = Int.MAX_VALUE,
                    shadow = curShadow,
                    modifier = Modifier.padding(start = 28.dp)
                )
            } else {
                Text(
                    text = lyricMixedFontText(if (mInterlude) "♪" else (curText ?: ""), enFontFamily),
                    color = Color.White,
                    fontSize = curBase.sp,
                    fontWeight = lyricWeight,
                    style = LocalTextStyle.current.copy(fontFamily = fontFamily, shadow = curShadow),
                    modifier = Modifier.padding(start = 28.dp)
                )
            }
            if (translationEnabled && !mInterlude) {
                val trC = translations?.getOrNull(rawIdx)
                if (!trC.isNullOrBlank()) {
                    Text(trC, color = Color.White.copy(alpha = 0.75f), fontSize = 12.sp, style = LocalTextStyle.current.copy(fontFamily = fontFamily), modifier = Modifier.padding(start = 28.dp, top = 4.dp))
                }
            }
            Spacer(Modifier.height(16.dp))
            if (nextText != null) {
                Text(lyricMixedFontText(nextText, enFontFamily), color = Color.White.copy(alpha = 0.45f), fontSize = sideBase.sp, style = LocalTextStyle.current.copy(fontFamily = fontFamily), modifier = Modifier.padding(start = 56.dp))
                if (translationEnabled) {
                    val trN = translations?.getOrNull(rawIdx + 1)
                    if (!trN.isNullOrBlank()) {
                        Text(trN, color = Color.White.copy(alpha = 0.35f), fontSize = 10.sp, style = LocalTextStyle.current.copy(fontFamily = fontFamily), modifier = Modifier.padding(start = 56.dp))
                    }
                }
            }
        }
    }
}


// アプリの並び順用: 使用統計（よく使う順=総使用時間、最近順=最終使用時刻）。
// 使用状況へのアクセス許可が無い場合は空が返り、名前順のままになる
private fun loadUsageStats(context: android.content.Context, mode: String): Map<String, Long> {
    return try {
        val usm = context.getSystemService(android.content.Context.USAGE_STATS_SERVICE) as android.app.usage.UsageStatsManager
        val now = System.currentTimeMillis()
        val from = now - 30L * 24 * 3600 * 1000
        if (mode == "usage") {
            val stats = usm.queryUsageStats(android.app.usage.UsageStatsManager.INTERVAL_DAILY, from, now)
            val map = mutableMapOf<String, Long>()
            for (s in stats) {
                map[s.packageName] = (map[s.packageName] ?: 0L) + s.totalTimeInForeground
            }
            map
        } else {
            val events = usm.queryEvents(from, now)
            val map = mutableMapOf<String, Long>()
            val e = android.app.usage.UsageEvents.Event()
            while (events.hasNextEvent()) {
                events.getNextEvent(e)
                if (e.eventType == android.app.usage.UsageEvents.Event.ACTIVITY_RESUMED) {
                    val prev = map[e.packageName] ?: 0L
                    if (e.timeStamp > prev) map[e.packageName] = e.timeStamp
                }
            }
            map
        }
    } catch (_: Exception) {
        emptyMap()
    }
}

/** レイアウト編集対応の時計パーツ土台（位置決め＋拡縮＋枠＋移動/拡縮ハンドル）。中身は呼び出し側が渡す */
@Composable
private fun LayoutClockPart(
    offsetX: Float,
    offsetY: Float,
    onOffsetChange: (Float, Float) -> Unit,
    onOffsetSave: () -> Unit,
    scale: Float,
    onScaleChange: (Float) -> Unit,
    onScaleSave: () -> Unit,
    layoutEditMode: Boolean,
    onEnterLayoutEdit: () -> Unit,
    accentColor: Color,
    topPaddingDp: Dp,
    content: @Composable () -> Unit
) {
    val latestScale by rememberUpdatedState(scale)
    Box(
        modifier = Modifier
            .padding(start = 28.dp, top = topPaddingDp + 48.dp)
            .offset(x = offsetX.dp, y = offsetY.dp)
            .zIndex(10f)
    ) {
        var partSize by remember { mutableStateOf(IntSize.Zero) }
        val partDensity = LocalDensity.current.density
        Box(
            modifier = Modifier
                .graphicsLayer { scaleX = scale; scaleY = scale; transformOrigin = TransformOrigin(0f, 0f) }
                .onSizeChanged { partSize = it }
                .pointerInput(layoutEditMode) {
                    if (layoutEditMode) {
                        detectDragGestures(
                            onDragEnd = { onOffsetSave() },
                            onDrag = { change, drag ->
                                change.consume()
                                onOffsetChange(drag.x / density * latestScale, drag.y / density * latestScale)
                            }
                        )
                    } else {
                        detectTapGestures(onLongPress = { onEnterLayoutEdit() })
                    }
                }
        ) {
            content()
        }
        if (layoutEditMode && partSize != IntSize.Zero) {
            Box(
                modifier = Modifier
                    .size(width = (partSize.width * scale / partDensity).dp, height = (partSize.height * scale / partDensity).dp)
                    .border(1.dp, accentColor.copy(alpha = 0.8f), RoundedCornerShape(10.dp))
            )
            MoveHandle(
                color = accentColor,
                modifier = Modifier
                    .offset(x = -12.dp, y = -12.dp)
                    .zIndex(20f)
                    .pointerInput(Unit) {
                        detectDragGestures(
                            onDragEnd = { onOffsetSave() },
                            onDrag = { change, drag ->
                                change.consume()
                                onOffsetChange(drag.x / density, drag.y / density)
                            }
                        )
                    }
            )
            ResizeHandle(
                color = accentColor,
                modifier = Modifier
                    .offset(x = (partSize.width * scale / partDensity).dp - 12.dp, y = (partSize.height * scale / partDensity).dp - 12.dp)
                    .zIndex(20f)
                    .pointerInput(Unit) {
                        detectDragGestures(
                            onDragEnd = { onScaleSave() },
                            onDrag = { change, drag ->
                                change.consume()
                                onScaleChange((drag.x + drag.y) / 2f / density / 150f)
                            }
                        )
                    }
            )
        }
    }
}

/** レイアウト編集モード用: 右下の拡縮ハンドル（アクセント色の丸＋白の斜め両矢印） */
@Composable
private fun ResizeHandle(modifier: Modifier = Modifier, color: Color) {
    Box(
        modifier = modifier
            .size(24.dp)
            .background(color, CircleShape)
            .border(2.dp, Color.White, CircleShape),
        contentAlignment = Alignment.Center
    ) {
        Canvas(Modifier.size(13.dp)) {
            val sw = 1.8.dp.toPx()
            val c = Color.White
            val a = Offset(size.width * 0.14f, size.height * 0.86f)
            val b = Offset(size.width * 0.86f, size.height * 0.14f)
            drawLine(c, a, b, strokeWidth = sw, cap = StrokeCap.Round)
            drawLine(c, b, Offset(size.width * 0.48f, size.height * 0.14f), strokeWidth = sw, cap = StrokeCap.Round)
            drawLine(c, b, Offset(size.width * 0.86f, size.height * 0.48f), strokeWidth = sw, cap = StrokeCap.Round)
            drawLine(c, a, Offset(size.width * 0.52f, size.height * 0.86f), strokeWidth = sw, cap = StrokeCap.Round)
            drawLine(c, a, Offset(size.width * 0.14f, size.height * 0.52f), strokeWidth = sw, cap = StrokeCap.Round)
        }
    }
}

/** レイアウト編集モード用: 移動ハンドル（アクセント色の丸＋白の十字両矢印） */
@Composable
private fun MoveHandle(modifier: Modifier = Modifier, color: Color) {
    Box(
        modifier = modifier
            .size(24.dp)
            .background(color, CircleShape)
            .border(2.dp, Color.White, CircleShape),
        contentAlignment = Alignment.Center
    ) {
        Canvas(Modifier.size(14.dp)) {
            val sw = 1.8.dp.toPx()
            val c = Color.White
            val cx = size.width / 2f
            val cy = size.height / 2f
            val l = size.width * 0.10f
            val r = size.width * 0.90f
            val tp = size.height * 0.10f
            val bt = size.height * 0.90f
            val ah = size.width * 0.20f
            drawLine(c, Offset(l, cy), Offset(r, cy), strokeWidth = sw, cap = StrokeCap.Round)
            drawLine(c, Offset(cx, tp), Offset(cx, bt), strokeWidth = sw, cap = StrokeCap.Round)
            drawLine(c, Offset(l, cy), Offset(l + ah, cy - ah), strokeWidth = sw, cap = StrokeCap.Round)
            drawLine(c, Offset(l, cy), Offset(l + ah, cy + ah), strokeWidth = sw, cap = StrokeCap.Round)
            drawLine(c, Offset(r, cy), Offset(r - ah, cy - ah), strokeWidth = sw, cap = StrokeCap.Round)
            drawLine(c, Offset(r, cy), Offset(r - ah, cy + ah), strokeWidth = sw, cap = StrokeCap.Round)
            drawLine(c, Offset(cx, tp), Offset(cx - ah, tp + ah), strokeWidth = sw, cap = StrokeCap.Round)
            drawLine(c, Offset(cx, tp), Offset(cx + ah, tp + ah), strokeWidth = sw, cap = StrokeCap.Round)
            drawLine(c, Offset(cx, bt), Offset(cx - ah, bt - ah), strokeWidth = sw, cap = StrokeCap.Round)
            drawLine(c, Offset(cx, bt), Offset(cx + ah, bt - ah), strokeWidth = sw, cap = StrokeCap.Round)
        }
    }
}


@Composable
private fun LayoutEditHandles(
    repo: AppRepository, accentColor: Color, layoutEditMode: Boolean, currentPage: Int,
    topPaddingDp: Dp, headerTopDp: Dp, screenWidthDp: Float,
    pvOffset: State<Float>, onPvOffsetChange: (Float) -> Unit,
    pvTop: State<Float>, onPvTopChange: (Float) -> Unit,
    pvWidth: State<Float>, onPvWidthChange: (Float) -> Unit,
    pvHeight: State<Float>, onPvHeightChange: (Float) -> Unit,
    pvEffWidth: Float, pvEffHeight: Float,
    disc1CenterX: Float, disc1CenterY: Float, disc2CenterX: Float, disc2CenterY: Float,
    discOffsetY: State<Float>, onDiscOffsetYChange: (Float) -> Unit,
    animatedDisc1CenterX: Float, disc1RadiusDp: Float, lyricsDiscDrop: Float,
    tonearmScale: State<Float>, onTonearmScaleChange: (Float) -> Unit,
    headerOffsetY: State<Float>, onHeaderOffsetYChange: (Float) -> Unit
) {
    // レイアウト編集モード: PVの表示範囲を大きな四角で囲む（ホームのみ）
    if (layoutEditMode && currentPage == 1) {
        Box(
            modifier = Modifier
                .padding(start = (28f + pvOffset.value).coerceAtLeast(0f).dp, top = (topPaddingDp.value + 150f + pvTop.value).coerceAtLeast(0f).dp, end = 28.dp)
                .width(pvEffWidth.dp)
                .height(pvEffHeight.dp)
                .zIndex(190f)
                .drawBehind {
                    drawRoundRect(color = Color.Black.copy(alpha = 0.06f), cornerRadius = CornerRadius(10.dp.toPx(), 10.dp.toPx()))
                    drawRoundRect(
                        color = accentColor.copy(alpha = 0.6f),
                        cornerRadius = CornerRadius(10.dp.toPx(), 10.dp.toPx()),
                        style = Stroke(width = 1.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 9f), 0f))
                    )
                }
                .pointerInput(Unit) {
                    detectDragGestures(
                        onDragEnd = {
                            repo.prefManager.setPvOffsetX(pvOffset.value)
                            repo.prefManager.setPvTopOffsetDp(pvTop.value)
                        },
                        onDrag = { change, drag ->
                            change.consume()
                            onPvOffsetChange((pvOffset.value + drag.x / density).coerceAtLeast(-28f))
                            onPvTopChange((pvTop.value + drag.y / density).coerceAtLeast(-(topPaddingDp.value + 150f)))
                        }
                    )
                }
        ) {
            Text(
                text = "PV",
                color = Color.White.copy(alpha = 0.8f),
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(start = 30.dp, top = 10.dp, end = 10.dp, bottom = 10.dp)
            )
            MoveHandle(
                color = accentColor,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .offset(x = -6.dp, y = -6.dp)
                    .zIndex(5f)
                    .pointerInput(Unit) {
                        detectDragGestures(
                            onDragEnd = {
                                repo.prefManager.setPvOffsetX(pvOffset.value)
                                repo.prefManager.setPvTopOffsetDp(pvTop.value)
                            },
                            onDrag = { change, drag ->
                                change.consume()
                                onPvOffsetChange((pvOffset.value + drag.x / density).coerceAtLeast(-28f))
                                onPvTopChange((pvTop.value + drag.y / density).coerceAtLeast(-(topPaddingDp.value + 150f)))
                            }
                        )
                    }
            )
            ResizeHandle(
                color = accentColor,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .offset(x = 6.dp, y = 6.dp)
                    .zIndex(5f)
                    .pointerInput(Unit) {
                        detectDragGestures(
                            onDragEnd = {
                                repo.prefManager.setPvWidth(pvWidth.value)
                                repo.prefManager.setPvHeight(pvHeight.value)
                            },
                            onDrag = { change, drag ->
                                change.consume()
                                val curW = if (pvWidth.value.isNaN()) screenWidthDp - 56f else pvWidth.value
                                val curH = if (pvHeight.value.isNaN()) 200f else pvHeight.value
                                onPvWidthChange((curW + drag.x / density).coerceIn(120f, 800f))
                                onPvHeightChange((curH + drag.y / density).coerceIn(80f, 800f))
                            }
                        )
                    }
            )
        }
    }

    // レイアウト編集モード: 円盤の位置ハンドル（ホームは左円=アートワークのみ、他ページは左右）
    if (layoutEditMode) {
        for ((discCx, discCy) in if (currentPage == 1) listOf(disc1CenterX to disc1CenterY) else listOf(disc1CenterX to disc1CenterY, disc2CenterX to disc2CenterY)) {
            Box(
                modifier = Modifier
                    .offset(x = (discCx - 165f).dp, y = (discCy - 165f).dp)
                    .size(330.dp)
                    .zIndex(190f)
                    .border(2.dp, accentColor.copy(alpha = 0.9f), CircleShape)
                    .background(Color.Black.copy(alpha = 0.15f), CircleShape)
                    .pointerInput(Unit) {
                        detectDragGestures(
                            onDragEnd = { repo.prefManager.setDiscOffset(0f, discOffsetY.value) },
                            onDrag = { change, drag ->
                                change.consume()
                                onDiscOffsetYChange(discOffsetY.value + drag.y / density)
                            }
                        )
                    }
            )
        }
        Box(
            modifier = Modifier
                .offset(x = ((if (currentPage == 1) disc1CenterX else disc2CenterX) - 30f).dp, y = ((if (currentPage == 1) disc1CenterY else disc2CenterY) - 185f).dp)
                .zIndex(191f)
                .background(Color.Black.copy(alpha = 0.55f), RoundedCornerShape(6.dp))
                .padding(horizontal = 8.dp, vertical = 3.dp)
        ) {
            Text(text = "円盤", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        }
    }

    // レイアウト編集モード: レコード針の大きさハンドル（ホームのみ、針の軸の位置）
    if (layoutEditMode && currentPage == 1 && repo.prefManager.getTonearmStyle() != "none") {
        val pivotX = animatedDisc1CenterX + disc1RadiusDp * 1.02f
        val pivotY = disc1CenterY + lyricsDiscDrop - disc1RadiusDp * 1.02f
        Box(modifier = Modifier.fillMaxSize().zIndex(195f)) {
            ResizeHandle(
                color = accentColor,
                modifier = Modifier
                    .padding(start = (pivotX - 12f).dp.coerceAtLeast(0.dp), top = (pivotY - 12f).dp.coerceAtLeast(0.dp))
                    .pointerInput(Unit) {
                        detectDragGestures(
                            onDragEnd = { repo.prefManager.setTonearmScale(tonearmScale.value) },
                            onDrag = { change, drag ->
                                change.consume()
                                onTonearmScaleChange((tonearmScale.value + (drag.x + drag.y) / 2f / density / 150f).coerceIn(0.5f, 2f))
                            }
                        )
                    }
            )
        }
    }

    // レイアウト編集モード: ヘッダーの位置ハンドル（設定・アプリ一覧・フォルダ）
    if (layoutEditMode && (currentPage == 0 || currentPage == 2 || currentPage == 3)) {
        Box(
            modifier = Modifier
                .padding(top = headerTopDp + 4.dp, start = 16.dp, end = 16.dp)
                .fillMaxWidth()
                .height(118.dp)
                .zIndex(189f)
                .border(2.dp, accentColor.copy(alpha = 0.9f), RoundedCornerShape(10.dp))
                .background(Color.Black.copy(alpha = 0.15f), RoundedCornerShape(10.dp))
                .pointerInput(Unit) {
                    detectDragGestures(
                        onDragEnd = { repo.prefManager.setHeaderOffsetY(headerOffsetY.value) },
                        onDrag = { change, drag ->
                            change.consume()
                            onHeaderOffsetYChange((headerOffsetY.value + drag.y / density).coerceIn(-30f, 150f))
                        }
                    )
                }
        ) {
            Text(
                text = "ヘッダー",
                color = Color.White,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(8.dp)
            )
        }
    }
}

@Composable
private fun HomeWidgetsLayer(
    placements: State<List<WidgetPlacement>>, onPlacementsChange: (List<WidgetPlacement>) -> Unit,
    appWidgetManager: android.appwidget.AppWidgetManager, appWidgetHost: android.appwidget.AppWidgetHost,
    layoutEditMode: Boolean, accentColor: Color, repo: AppRepository,
    pvActive: Boolean, pvLeft: Float, pvTop: Float, pvEffWidth: Float, pvEffHeight: Float
) {
    val coroutineScope = rememberCoroutineScope()
        placements.value.forEach { placement ->
            val info = appWidgetManager.getAppWidgetInfo(placement.id)
            // PV表示中は、PVの場所（レイアウト編集のPV枠の範囲）に重なるウィジェットを隠す。再生が止まってPVが消えれば自動で戻る
            val hiddenByPv = !layoutEditMode && pvActive &&
                placement.x < pvLeft + pvEffWidth && placement.x + placement.w > pvLeft &&
                placement.y < pvTop + pvEffHeight && placement.y + placement.h > pvTop
            if (info != null && !hiddenByPv) {
                androidx.compose.runtime.key(placement.id) {
                    Box(
                        modifier = Modifier
                            .offset(x = placement.x.dp, y = placement.y.dp)
                            .size(placement.w.dp, placement.h.dp)
                            .zIndex(if (layoutEditMode) 200f else 30f)
                    ) {
                        androidx.compose.ui.viewinterop.AndroidView(
                            factory = { ctx ->
                                try { appWidgetHost.createView(ctx, placement.id, info) }
                                catch (_: Exception) { android.view.View(ctx) }
                            },
                            modifier = Modifier.fillMaxSize()
                        )
                        if (layoutEditMode) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .border(2.dp, accentColor, RoundedCornerShape(12.dp))
                                    .pointerInput(placement.id) {
                                        detectDragGestures(
                                            onDragEnd = { repo.prefManager.setWidgetPlacements(placements.value) },
                                            onDrag = { change, drag ->
                                                change.consume()
                                                onPlacementsChange(placements.value.map {
                                                    if (it.id == placement.id) it.copy(x = it.x + drag.x / density, y = it.y + drag.y / density) else it
                                                })
                                            }
                                        )
                                    }
                            )
                            Box(
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .offset(x = 6.dp, y = (-6).dp)
                                    .size(26.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFFE5484D))
                                    .pressScale()
                                    .clickable {
                                        val removedId = placement.id
                                        val next = placements.value.filter { it.id != removedId }
                                        onPlacementsChange(next)
                                        repo.prefManager.setWidgetPlacements(next)
                                        coroutineScope.launch {
                                            kotlinx.coroutines.delay(80)
                                            try { appWidgetHost.deleteAppWidgetId(removedId) } catch (_: Exception) { }
                                        }
                                    }
                                    .zIndex(5f),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(text = "×", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                            }
                            MoveHandle(
                                color = accentColor,
                                modifier = Modifier
                                    .align(Alignment.TopStart)
                                    .offset(x = -6.dp, y = -6.dp)
                                    .zIndex(5f)
                                    .pointerInput(placement.id) {
                                        detectDragGestures(
                                            onDragEnd = { repo.prefManager.setWidgetPlacements(placements.value) },
                                            onDrag = { change, drag ->
                                                change.consume()
                                                onPlacementsChange(placements.value.map {
                                                    if (it.id == placement.id) it.copy(x = it.x + drag.x / density, y = it.y + drag.y / density) else it
                                                })
                                            }
                                        )
                                    }
                            )
                            ResizeHandle(
                                color = accentColor,
                                modifier = Modifier
                                    .align(Alignment.BottomEnd)
                                    .offset(x = 6.dp, y = 6.dp)
                                    .pointerInput(placement.id) {
                                        detectDragGestures(
                                            onDragEnd = { repo.prefManager.setWidgetPlacements(placements.value) },
                                            onDrag = { change, drag ->
                                                change.consume()
                                                onPlacementsChange(placements.value.map {
                                                    if (it.id == placement.id) it.copy(
                                                        w = (it.w + drag.x / density).coerceIn(80f, 360f),
                                                        h = (it.h + drag.y / density).coerceIn(60f, 400f)
                                                    ) else it
                                                })
                                            }
                                        )
                                    }
                                    .zIndex(5f)
                            )
                        }
                    }
                }
            }
        }
}

@Composable
private fun LayoutEditFinishBar(accentColor: Color, topPaddingDp: Dp, currentPage: Int, layoutEditMode: Boolean, onFinish: () -> Unit, onAddWidget: () -> Unit) {
    // レイアウト編集モード: 完了ボタンとヒント（全ページ共通）
    if (layoutEditMode) {
        Box(
            modifier = Modifier.fillMaxWidth().padding(top = topPaddingDp + 6.dp).zIndex(195f),
            contentAlignment = Alignment.TopCenter
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(999.dp))
                        .background(accentColor)
                        .pressScale()
                        .clickable { onFinish() }
                        .padding(horizontal = 22.dp, vertical = 9.dp)
                ) {
                    Text(text = "完了", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                }
                if (currentPage == 1) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(999.dp))
                            .background(Color.White.copy(alpha = 0.92f))
                            .pressScale()
                            .clickable { onAddWidget() }
                            .padding(horizontal = 18.dp, vertical = 9.dp)
                    ) {
                        Text(text = "＋ ウィジェット", color = Color(0xFF222222), fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
        Box(
            modifier = Modifier.fillMaxSize().padding(bottom = 108.dp).zIndex(195f),
            contentAlignment = Alignment.BottomCenter
        ) {
            Text(
                text = if (currentPage == 1) "時計・日付・秒・バッテリー・PV・円盤の枠をドラッグで移動、左上の矢印で移動、右下の矢印で大きさ変更（＋でウィジェット追加）" else "円盤とヘッダーの枠をドラッグで移動できます",
                color = Color.White.copy(alpha = 0.85f),
                fontSize = 12.sp
            )
        }
    }
}

/** ホーム右円の混合項目（アプリとフォルダを1つの並びで扱う） */
private sealed class HomeEntry {
    data class App(val app: AppInfo, val item: DiscItem) : HomeEntry()
    data class Folder(val folder: FolderDiscEntry.Folder) : HomeEntry()
}

private fun homeEntryKey(entry: HomeEntry): String = when (entry) {
    is HomeEntry.App -> "a:" + entry.app.packageName
    is HomeEntry.Folder -> "f:" + entry.folder.folderId
}


/**
 * アプリ長押しポップアップのホスト。
 * MainLauncherScreen 本体が大きくなりすぎたため、ダイアログとコールバック一式を切り出した。
 */
@Composable
private fun AppOptionDialogHost(
    app: AppInfo,
    repo: AppRepository,
    foldersMap: Map<String, Pair<String, List<String>>>,
    coroutineScope: kotlinx.coroutines.CoroutineScope,
    onRefresh: () -> Unit,
    onDismiss: () -> Unit
) {
    AppOptionDialog(
        app = app,
        onDismiss = onDismiss,
        onToggleFavorite = { targetApp ->
            repo.setFavorite(targetApp.packageName, !targetApp.isFavorite)
            onRefresh()
        },
        onSetYomi = { targetApp, yomi ->
            repo.setYomi(targetApp.packageName, yomi)
            onRefresh()
        },
        onSetYomiKana = { targetApp, yomi ->
            repo.setYomiKana(targetApp.packageName, yomi)
            onRefresh()
        },
        onAddAlias = { targetApp, alias ->
            repo.addSearchAlias(targetApp.packageName, alias)
            onRefresh()
        },
        onRemoveAlias = { targetApp, alias ->
            repo.removeSearchAlias(targetApp.packageName, alias)
            onRefresh()
        },
        onToggleHide = { targetApp ->
            val willHide = !targetApp.isHidden
            repo.setHidden(targetApp.packageName, willHide)
            onRefresh()
        },
        hasCustomIcon = repo.hasCustomIcon(app.packageName),
        onPickCustomIcon = { targetApp, bmp ->
            coroutineScope.launch {
                repo.setCustomIcon(targetApp.packageName, bmp)
                onRefresh()
            }
        },
        onClearCustomIcon = { targetApp ->
            coroutineScope.launch {
                repo.clearCustomIcon(targetApp.packageName)
                onRefresh()
            }
        },
        folders = foldersMap.entries.map { (id, v) -> id to v.first },
        containingFolderIds = foldersMap.filterValues { app.packageName in it.second }.keys,
        onToggleFolder = { targetApp, fid ->
            val cur = foldersMap[fid]
            if (cur != null) {
                val pkgs = cur.second.toMutableList()
                if (targetApp.packageName in pkgs) pkgs.remove(targetApp.packageName) else pkgs.add(targetApp.packageName)
                repo.prefManager.saveFolder(fid, cur.first, pkgs)
                onRefresh()
            }
        },
        onCreateFolder = { targetApp, name ->
            val newId = System.currentTimeMillis().toString()
            repo.prefManager.saveFolder(newId, name, listOf(targetApp.packageName))
            onRefresh()
        }
    )
}
