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
import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.filled.Lyrics
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material.icons.filled.Timer
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.asImageBitmap
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
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import android.view.HapticFeedbackConstants
import androidx.compose.ui.platform.LocalView
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.example.nissingeppolauncher.data.AppInfo
import com.example.nissingeppolauncher.data.AppRepository
import com.example.nissingeppolauncher.data.LyricLine
import com.example.nissingeppolauncher.data.UpdateChecker
import com.example.nissingeppolauncher.data.LyricsManager
import com.example.nissingeppolauncher.ui.components.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import kotlin.math.*

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
    var currentLauncherPage by remember { mutableIntStateOf(1) }

    var accentHex by remember { mutableStateOf(repo.prefManager.getAccentColorHex()) }
    val manualAccentColor = remember(accentHex) { parseHexColor(accentHex) }

    // アートワーク連動カラー: 設定でONのとき、再生中セッションのアートワークから
    // アクセント色（前景）と背景色を抽出してテーマカラーにする
    var matchArtwork by remember { mutableStateOf(repo.prefManager.isMatchArtworkColor()) }
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
    var artworkBackground by remember { mutableStateOf<Color?>(null) }

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
    var lyricsPvFont by remember { mutableStateOf(repo.prefManager.getLyricsPvFont()) }
    var lyricsEnFont by remember { mutableStateOf(repo.prefManager.getLyricsEnFont()) }
    var lyricsEnCustomPath by remember { mutableStateOf(repo.prefManager.getLyricsEnCustomFontPath()) }
    var lyricsBold by remember { mutableStateOf(repo.prefManager.isLyricsBold()) }
    var pvPartsEntry by remember { mutableStateOf(repo.prefManager.isPvPartsEntryEnabled()) }
    var dopagakiMode by remember { mutableStateOf(repo.prefManager.isDopagakiEnabled()) }
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
    var translationEnabled by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        translationEnabled = repo.prefManager.isLyricTranslationEnabled()
    }
    LaunchedEffect(syncedLyricLines, translationEnabled) {
        lyricTranslations = null
        if (translationEnabled && syncedLyricLines.isNotEmpty()) {
            lyricTranslations = LyricsManager.translateToJapanese(
                context, sessionTrackTitle, sessionTrackArtist, sessionTrackAlbum, syncedLyricLines
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

    // ステータスバー歌詞オーバーレイ: 設定がONで権限があればサービスを起動しておく
    LaunchedEffect(Unit) {
        if (repo.prefManager.isStatusBarLyricsEnabled() && android.provider.Settings.canDrawOverlays(context)) {
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
                if (repo.prefManager.isStatusBarLyricsEnabled() && android.provider.Settings.canDrawOverlays(context)) {
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
                        curLine == null -> nextLine != null && (nextLine.timeMs - posMs) > 3000
                        nextLine == null -> (posMs - curLine.timeMs) > 8000
                        else -> (nextLine.timeMs - posMs) > 4000 && (posMs - curLine.timeMs) > 3000
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
        if (!matchArtwork && !useArtworkBg) {
            sessionAlbumArt = null
            return@LaunchedEffect
        }
        val mediaSessionManager = context.getSystemService(android.content.Context.MEDIA_SESSION_SERVICE) as? android.media.session.MediaSessionManager
        val listenerComponent = android.content.ComponentName(context, com.example.nissingeppolauncher.service.LauncherMediaListenerService::class.java)
        var lastArtKey = ""
        var artMissKey = ""
        var artMissCount = 0
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
                } else if (artKey != lastArtKey) {
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
                    if (loaded != null) {
                        lastArtKey = artKey
                        sessionAlbumArt = loaded
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
                        if (artMissCount >= 10) {
                            // 約15秒取りに行っても無ければ、本当にアートワークが無い曲として打ち切る
                            lastArtKey = artKey
                            artMissKey = ""
                            artMissCount = 0
                        }
                    }
                }
            } catch (e: Exception) {
                // セッションが読めないときは今の表示を維持する
            }
            delay(1500)
        }
    }
    LaunchedEffect(sessionAlbumArt, matchArtwork, useArtworkBg) {
        val art = sessionAlbumArt
        if ((matchArtwork || useArtworkBg) && art != null) {
            val (fg, bg) = withContext(Dispatchers.Default) { extractArtworkColors(art) }
            artworkAccent = fg
            artworkBackground = bg
        } else {
            artworkAccent = null
            artworkBackground = null
        }
    }
    val accentColor = if (matchArtwork) {
        val extracted = artworkAccent
        val bg = artworkBackground
        if (extracted != null && bg != null && contrastRatio(extracted, bg) < 2.8f) {
            // 音楽アプリと同じ調整: コントラストが低い場合は同じ色味のまま白へ寄せて明るくする
            val fg: Color = extracted
            val bgNN: Color = bg
            var blended = fg
            var t = 0f
            while (contrastRatio(blended, bgNN) < 3.0f && t < 0.5f) {
                t += 0.1f
                blended = Color(
                    red = fg.red + (1f - fg.red) * t,
                    green = fg.green + (1f - fg.green) * t,
                    blue = fg.blue + (1f - fg.blue) * t,
                    alpha = fg.alpha
                )
            }
            blended
        } else {
            extracted ?: manualAccentColor
        }
    } else {
        manualAccentColor
    }
    // 色の切り替わりはチカチカしないよう、800msかけてなめらかに寄せる
    val effectiveAccent by animateColorAsState(
        targetValue = pvFlashColor ?: accentColor,
        animationSpec = tween(durationMillis = 800),
        label = "accentFlash"
    )

    var showWelcomeTutorial by remember { mutableStateOf(false) }
    var showIndexTutorial by remember { mutableStateOf(false) }
    var showHideTutorial by remember { mutableStateOf(false) }

    // ページが切り替わるたびにアクセントカラー等を自動再読込
    LaunchedEffect(currentLauncherPage) {
        accentHex = repo.prefManager.getAccentColorHex()
        matchArtwork = repo.prefManager.isMatchArtworkColor()
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
    var backgroundUpdateKey by remember { mutableLongStateOf(System.currentTimeMillis()) }

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
    var showFolderAppSelectDialog by remember { mutableStateOf(false) }
    var showDeleteFolderDialog by remember { mutableStateOf(false) }

    var isEditingFolder by remember { mutableStateOf(false) }
    var editingFolderNameInput by remember { mutableStateOf("") }
    var editingSelectedPackages by remember { mutableStateOf<Set<String>>(emptySet()) }

    var isHideMode by remember { mutableStateOf(false) }
    var showPasscodeDialog by remember { mutableStateOf(false) }
    var isReadyToOpenHiddenList by remember { mutableStateOf(false) }

    var newFolderNameInput by remember { mutableStateOf("") }

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
    val homeDiscItems = remember(favoriteApps) { buildDiscItems(favoriteApps, false) }
    val indexDiscState = rememberDiscCarouselState(initialIndex = 0, stepAngle = 360f / INDEX_ITEMS.size)
    val indexEntries = remember { INDEX_ITEMS.map { DiscEntry(id = it, label = it, icon = {}) } }
    var indexSelected by remember { mutableIntStateOf(0) }
    fun snapDiscTo(state: DiscCarouselState, angle: Float) {
        state.rawRotation = angle
        coroutineScope.launch { state.rotationAnim.snapTo(angle) }
    }
    val folderStepAngle = 24f

    val createNewFolderStr = "新規作成"
    val folderItemsWithNew = remember(folderList, createNewFolderStr) {
        folderList + Pair("NEW_FOLDER", createNewFolderStr)
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
            favoriteApps = installed.filter { it.isFavorite && !it.isHidden }
            foldersMap = repo.prefManager.getFoldersMap()
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
    val disc1CenterX = if (isBigDiscLayout) 15f else -80f
    val disc1CenterY = screenHeightDp * 0.50f

    val disc2RadiusDp = 165f
    val disc2CenterX = screenWidthDp + 80f
    val disc2CenterY = screenHeightDp * 0.50f

    // 歌詞のPV／複数行モードでは、音楽アプリと同じように円を下へ逃がす
    val lyricsDiscDrop by animateFloatAsState(
        targetValue = if (currentLauncherPage == 1 && syncedLyricLines.isNotEmpty()) {
            when (lyricsDisplayMode) {
                "video" -> 80f
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
    val stepAngle = 360f / INDEX_ITEMS.size

    val animatedDisc1CenterX by animateFloatAsState(
        targetValue = when {
            isTransitioning || isHoldingRight -> -disc1RadiusDp
            isReadyToOpenHiddenList -> disc1CenterX + 110f // 右へ大きくオフセットしてスイッチが入ったことを視覚化
            else -> disc1CenterX
        },
        animationSpec = tween(durationMillis = 220, easing = FastOutSlowInEasing),
        label = "Disc1CenterX"
    )

    val animatedDisc2CenterX by animateFloatAsState(
        targetValue = when {
            isTransitioning || isHoldingLeft || isHoldingFolderWheel -> screenWidthDp + disc2RadiusDp
            else -> disc2CenterX
        },
        animationSpec = tween(durationMillis = 220, easing = FastOutSlowInEasing),
        label = "Disc2CenterX"
    )

    val selectedIndexChar = if (indexSelected in INDEX_ITEMS.indices) INDEX_ITEMS[indexSelected] else null

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
            delay(220)
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
        animationSpec = tween(durationMillis = 220, easing = FastOutSlowInEasing),
        label = "TransitionAlpha"
    )

    val appDiscIconSize = repo.prefManager.getAppDiscIconSizeDp()
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
            modifier = Modifier.fillMaxSize()
        ) {
                WallpaperBackground(
                    customPath = customBackgroundPath,
                    updateKey = backgroundUpdateKey,
                    isHome = (currentLauncherPage == 1),
                    currentAlbumArt = if (matchArtwork || useArtworkBg) sessionAlbumArt else null,
                    dynamicBackground = if (matchArtwork || useArtworkBg) artworkBackground else null,
                    artworkBackgroundStyle = if (bgMode == "artwork_blur") "blur" else "solid",
                    liquidEnabled = liquidBackground,
                    liquidAccent = if (dopagakiMode) Color.hsv(Math.floorMod(pvLineIndex * 47, 360).toFloat(), 0.72f, 1f) else effectiveAccent
                )

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
            if (lyricsDisplayMode == "video" && currentLauncherPage == 1 && !isLyricsBrowsing && syncedLyricLines.isNotEmpty()) {
              Box(Modifier.fillMaxSize().zIndex(if (dopagakiMode) 60f else 0f)) {
                LyricsPvLayer(
                    pvTilt3d = disc3d,
                    lines = syncedLyricLines,
                    positionMs = sessionPositionMs,
                    lyricOffsetMs = lyricOffsetMs,
                    bold = lyricsBold,
                    fontFamily = pvFontFamily,
                    accentColor = effectiveAccent,
                    screenWidthDp = screenWidthDp,
                    translationEnabled = translationEnabled,
                    translations = lyricTranslations,
                    pvTopDp = topPaddingDp + 165.dp,
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

            // 複数行モード: 時計の下に前・今・次の3行を表示（今の行が大きめ）
            if (lyricsDisplayMode == "multi" && currentLauncherPage == 1 && !isLyricsBrowsing && syncedLyricLines.isNotEmpty()) {
                LauncherMultiLineLyrics(
                    lines = syncedLyricLines,
                    positionMs = sessionPositionMs,
                    lyricOffsetMs = lyricOffsetMs,
                    translationEnabled = translationEnabled,
                    translations = lyricTranslations,
                    fontFamily = pvFontFamily,
                    enFontFamily = lyricsEnFontArg,
                    lyricWeight = lyricWeight,
                    accentColor = accentColor,
                    screenWidthDp = screenWidthDp,
                    topPaddingDp = topPaddingDp,
                    dopagaki = dopagakiMode
                )
            }

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
                                customBackgroundPath = repo.prefManager.getCustomBackgroundPath()
                                backgroundUpdateKey = System.currentTimeMillis()
                                customFontPath = repo.prefManager.getCustomFontPath()
                                useCustomFont = repo.prefManager.isUseCustomFont()
                                fontChoice = repo.prefManager.getFontChoice()
                                lyricsDisplayMode = repo.prefManager.getLyricsDisplayMode()
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
                    ClockWidget(
                        modifier = Modifier
                            .padding(start = 28.dp, top = topPaddingDp + 48.dp)
                            .zIndex(10f)
                            .graphicsLayer {
                                alpha = if (isLyricsBrowsing) 0f else 1f
                                if (disc3d) {
                                    transformOrigin = TransformOrigin(0.5f, 0.5f)
                                    rotationX = 27f + discTiltX.value
                                    rotationY = discTiltY.value
                                    cameraDistance = 6.5f * density
                                }
                            },
                        lyricsLine = if (lyricsDisplayMode == "line") currentLyricLine else "",
                        translationText = if (lyricsDisplayMode == "line" && translationEnabled) lyricTranslations?.getOrNull(syncedLyricLines.indexOfLast { it.timeMs <= sessionPositionMs + lyricOffsetMs }) ?: "" else "",
                        karaokeLine = if (lyricsDisplayMode == "line") syncedLyricLines.lastOrNull { it.timeMs <= sessionPositionMs + lyricOffsetMs } else null,
                        karaokePositionMs = sessionPositionMs + lyricOffsetMs,
                        karaokeSungColor = accentColor,
                        lyricFontFamily = lyricsFontFamily,
                        lyricEnFontFamily = lyricsEnFontArg,
                        dateMode = if (lyricsDisplayMode == "video" && syncedLyricLines.isNotEmpty()) "sideways" else dateDisplayMode,
                        glowColor = accentColor,
                        rainbowGlow = dopagakiMode
                    )

                    if (!vinylRackOpen) {
                    DiscCarousel(
                        itemCount = homeDiscItems.size,
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer {
                                alpha = if (isLyricsBrowsing) 0f else 1f
                                if (disc3d) {
                                    transformOrigin = TransformOrigin(
                                        (animatedDisc2CenterX / screenWidthDp).coerceIn(-2f, 3f),
                                        ((disc2CenterY + lyricsDiscDrop) / screenHeightDp).coerceIn(-2f, 3f)
                                    )
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
                        state = homeDiscState,
                        onHoldingChanged = { isHoldingRight = it },
                        onEmptyTap = { }
                    ) { index, rad, labelAlpha, _ ->
                        val di = homeDiscItems.getOrNull(index)
                        if (di != null) {
                            DiscAppItem(
                                item = di,
                                discCenterXDp = animatedDisc2CenterX,
                                discCenterYDp = disc2CenterY + lyricsDiscDrop,
                                discRadiusDp = disc2RadiusDp,
                                rad = rad,
                                labelAlpha = labelAlpha,
                                textSizeSp = appDiscTextSize,
                                iconSizeDp = appDiscIconSize,
                                letterSpacingSp = appDiscLetterSpacing,
                                accentColor = accentColor,
                                selectedPackages = null,
                                iconTilt3d = disc3d,
                                rainbowGlow = dopagakiMode,
                                showLabels = !isBigDiscLayout,
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
                                alpha = if (isLyricsBrowsing) 0f else 1f
                                if (disc3d) {
                                    transformOrigin = TransformOrigin(
                                        (animatedDisc1CenterX / screenWidthDp).coerceIn(-2f, 3f),
                                        ((disc1CenterY + lyricsDiscDrop) / screenHeightDp).coerceIn(-2f, 3f)
                                    )
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
                            rainbowGlow = dopagakiMode,
                            textStartAngle = lyricsTextAngle,
                            artworkTilt3d = disc3d,
                            onRackVisibilityChanged = { vinylRackOpen = it }
                        )
                    }
                }
                2 -> {
                    // --- 2: アプリ一覧画面 ---
                    DiscWheel(
                        entries = indexEntries,
                        side = DiscSide.LEFT,
                        accentColor = accentColor,
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer {
                                if (disc3d) {
                                    transformOrigin = TransformOrigin(
                                        (animatedDisc1CenterX / screenWidthDp).coerceIn(-2f, 3f),
                                        (disc1CenterY / screenHeightDp).coerceIn(-2f, 3f)
                                    )
                                    rotationX = 27f + discTiltX.value
                                    rotationY = discTiltY.value
                                    cameraDistance = 6.5f * density
                                }
                            },
                        state = indexDiscState,
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
                        onFocusChange = { indexSelected = it }
                    )

                    val displayedApps = remember(searchQuery, allApps, isHideMode) {
                        if (isHideMode) {
                            allApps.filter { it.isHidden }
                        } else if (searchQuery.isNotBlank()) {
                            allApps.filter {
                                (it.label.contains(searchQuery, ignoreCase = true) ||
                                        it.customYomi.contains(searchQuery, ignoreCase = true) ||
                                        it.initialJapanese.contains(searchQuery, ignoreCase = true) ||
                                        it.searchAliases.any { alias -> alias.contains(searchQuery, ignoreCase = true) }) && !it.isHidden
                            }
                        } else {
                            allApps.filter { !it.isHidden }
                        }
                    }

                    val appDiscItems = remember(displayedApps) { buildDiscItems(displayedApps, true) }
                    LaunchedEffect(selectedIndexChar) {
                        if (!selectedIndexChar.isNullOrEmpty() && appDiscItems.isNotEmpty()) {
                            val targetIndex = appDiscItems.indexOfFirst { item ->
                                when (item) {
                                    is DiscItem.IndexEntry -> item.char.equals(selectedIndexChar, ignoreCase = true)
                                    is DiscItem.AppEntry -> (item.app.initialJapanese == selectedIndexChar) || (item.app.initialEnglish.equals(selectedIndexChar, ignoreCase = true))
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
                    DiscCarousel(
                        itemCount = appDiscItems.size,
                        modifier = Modifier.fillMaxSize()
                                .graphicsLayer {
                                    if (disc3d) {
                                        transformOrigin = TransformOrigin((animatedDisc2CenterX / screenWidthDp).coerceIn(-2f, 3f), (disc2CenterY / screenHeightDp).coerceIn(-2f, 3f))
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
                        state = appListDiscState,
                        onHoldingChanged = { isHoldingRight = it }
                    ) { index, rad, labelAlpha, _ ->
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
                                letterSpacingSp = appDiscLetterSpacing,
                                accentColor = accentColor,
                                selectedPackages = null,
                                iconTilt3d = disc3d,
                                rainbowGlow = dopagakiMode,
                                onAppClick = { onAppClick(it.packageName) },
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
                            accentColor = accentColor,
                            modifier = Modifier.fillMaxSize()
                                .graphicsLayer {
                                    if (disc3d) {
                                        transformOrigin = TransformOrigin((animatedDisc1CenterX / screenWidthDp).coerceIn(-2f, 3f), (disc1CenterY / screenHeightDp).coerceIn(-2f, 3f))
                                        rotationX = 27f + discTiltX.value
                                        rotationY = discTiltY.value
                                        cameraDistance = 6.5f * density
                                    }
                                },
                            state = indexDiscState,
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
                            onFocusChange = { indexSelected = it }
                        )
                    } else {
                        // 左側: フォルダ選択ダイヤル
                        DiscFolderWheel(
                            folders = folderList,
                            state = folderDiscState,
                            modifier = Modifier.fillMaxSize()
                                .graphicsLayer {
                                    if (disc3d) {
                                        transformOrigin = TransformOrigin((animatedDisc1CenterX / screenWidthDp).coerceIn(-2f, 3f), (disc1CenterY / screenHeightDp).coerceIn(-2f, 3f))
                                        rotationX = 27f + discTiltX.value
                                        rotationY = discTiltY.value
                                        cameraDistance = 6.5f * density
                                    }
                                },
                            discCenterXDp = animatedDisc1CenterX,
                            discCenterYDp = disc1CenterY,
                            discRadiusDp = disc1RadiusDp,
                            isHolding = isHoldingFolderWheel,
                            textSizeSp = appDiscTextSize,
                            letterSpacingSp = appDiscLetterSpacing,
                            accentColor = accentColor,
                            onSelectedIndexChange = { folderSelectedIndex = it }
                        )
                    }

                    // 右側: 通常時はフォルダ内アプリ、編集時は全アプリ（チェック付き）
                    val folderPackages = remember(selectedFolderId, foldersMap) {
                        if (selectedFolderId != null && selectedFolderId != "NEW_FOLDER") {
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

                    val folderDiscItems = remember(discApps, isEditingFolder) { buildDiscItems(discApps, isEditingFolder) }
                    LaunchedEffect(selectedIndexChar, isEditingFolder) {
                        val char = if (isEditingFolder) selectedIndexChar else null
                        if (!char.isNullOrEmpty() && folderDiscItems.isNotEmpty()) {
                            val targetIndex = folderDiscItems.indexOfFirst { item ->
                                when (item) {
                                    is DiscItem.IndexEntry -> item.char.equals(char, ignoreCase = true)
                                    is DiscItem.AppEntry -> (item.app.initialJapanese == char) || (item.app.initialEnglish.equals(char, ignoreCase = true))
                                }
                            }
                            if (targetIndex != -1) {
                                val targetAngle = (-targetIndex * appDiscStepAngle).coerceIn(-((folderDiscItems.size - 1) * appDiscStepAngle).coerceAtLeast(0f), 0f)
                                snapDiscTo(folderAppDiscState, targetAngle)
                            }
                        }
                    }
                    DiscCarousel(
                        itemCount = folderDiscItems.size,
                        modifier = Modifier.fillMaxSize()
                                .graphicsLayer {
                                    if (disc3d) {
                                        transformOrigin = TransformOrigin((animatedDisc2CenterX / screenWidthDp).coerceIn(-2f, 3f), (disc2CenterY / screenHeightDp).coerceIn(-2f, 3f))
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
                        state = folderAppDiscState,
                        onHoldingChanged = { isHoldingRight = it }
                    ) { index, rad, labelAlpha, _ ->
                        val di = folderDiscItems.getOrNull(index)
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
                                letterSpacingSp = appDiscLetterSpacing,
                                accentColor = accentColor,
                                selectedPackages = if (isEditingFolder) editingSelectedPackages else null,
                                iconTilt3d = disc3d,
                                rainbowGlow = dopagakiMode,
                                onAppClick = { app ->
                                    if (isEditingFolder) {
                                        val updated = editingSelectedPackages.toMutableSet()
                                        if (updated.contains(app.packageName)) {
                                            updated.remove(app.packageName)
                                        } else {
                                            updated.add(app.packageName)
                                        }
                                        editingSelectedPackages = updated
                                    } else {
                                        onAppClick(app.packageName)
                                    }
                                },
                                onLongClick = { app ->
                                    if (!isEditingFolder) {
                                        selectedAppForOption = app
                                    }
                                }
                            )
                        }
                    }
                }
            }
        }

                // --- 画面下部 ページ切り替え (ボタン式 / ダイヤル式を設定で切替) ---
        if (!isLyricsBrowsing && !pvFullscreen && !vinylRackOpen && repo.prefManager.getPageSwitchStyle() == "dial") {
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
                    .graphicsLayer {
                        if (disc3d) {
                            transformOrigin = TransformOrigin(0.5f, 0.5f)
                            rotationX = 27f + discTiltX.value
                            rotationY = discTiltY.value
                            cameraDistance = 6.5f * density
                        }
                    }
            )
        } else if (!isLyricsBrowsing && !pvFullscreen) {
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
                    .graphicsLayer {
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
                        discCenterYPx = with(LocalDensity.current) { (disc1CenterY - topPaddingDp - 60f).dp.toPx() },
                        onItemTap = { idx ->
                            folderDiscState.animateToIndex(idx)
                            if (folderItemsWithNew.getOrNull(idx)?.first == "NEW_FOLDER") {
                                newFolderNameInput = ""
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
                        .pointerInput(currentLauncherPage, isEditingFolder) {
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
                                        var accumulatedDy = 0f
                                        var currentStepCount = if (isIndexWheel) {
                                            (-indexDiscState.rawRotation / stepAngle).roundToInt()
                                        } else {
                                            (-folderWheelRotationAnim.value / folderStepAngle).roundToInt().coerceIn(0, folderItemsWithNew.size - 1)
                                        }
                                        val stepThreshold = 40f // スワイプの感度調整

                                        drag(down.id) { change ->
                                            change.consume()
                                            val pos = change.position
                                            if (currentLauncherPage == 2 && !isEditingFolder) {
                                                val shouldOpen = pos.x >= size.width * 0.78f
                                                if (shouldOpen != isReadyToOpenHiddenList) {
                                                    isReadyToOpenHiddenList = shouldOpen
                                                    if (shouldOpen) {
                                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                                    }
                                                }
                                            }

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

                                        // 指を離した瞬間
                                        if (currentLauncherPage == 2 && !isEditingFolder) {
                                            if (isReadyToOpenHiddenList) {
                                                if (repo.prefManager.isUsePasscodeForHidden()) {
                                                    showPasscodeDialog = true
                                                } else {
                                                    isHideMode = true
                                                }
                                            }
                                            isReadyToOpenHiddenList = false
                                        }

                                        if (currentLauncherPage == 3 && folderItemsWithNew.isNotEmpty()) {
                                            if (selectedFolderId == "NEW_FOLDER") {
                                                newFolderNameInput = ""
                                            }
                                        }

                                        isHoldingLeft = false
                                        isHoldingFolderWheel = false
                                    }
                                } finally {
                                    isHoldingLeft = false
                                    isHoldingFolderWheel = false
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

        // --- 画面上部 検索ボックス / 非表示リストを閉じるボタン ---
        if (currentLauncherPage == 2) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = topPaddingDp + 48.dp)
                    .zIndex(150f),
                contentAlignment = Alignment.TopCenter
            ) {
                if (isHideMode) {
                    Row(
                        modifier = Modifier
                            .width(320.dp)
                            .height(48.dp)
                            .background(Color(0x99101728), RoundedCornerShape(16.dp))
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
                            .background(Color(0x99101728), RoundedCornerShape(16.dp))
                            .padding(horizontal = 16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        BasicTextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            singleLine = true,
                            textStyle = androidx.compose.ui.text.TextStyle(
                                color = Color.White,
                                fontSize = 15.sp,
                                textAlign = TextAlign.Center
                            ),
                            cursorBrush = SolidColor(Color(0xFF88C0FF)),
                            modifier = Modifier.weight(1f)
                        ) { innerTextField ->
                            Box(contentAlignment = Alignment.Center) {
                                if (searchQuery.isEmpty()) {
                                    Text(
                                        text = "アプリを検索…",
                                        color = Color.Gray.copy(alpha = 0.7f),
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
                                    .clickable { searchQuery = "" }
                                    .padding(4.dp)
                            )
                        }
                    }
                }
            }
        }

        // --- 画面上部 フォルダ名＆編集バー / 新規作成バー (フォルダ画面(3)用、半透明ダークグラス) ---
        if (currentLauncherPage == 3) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = topPaddingDp + 48.dp)
                    .zIndex(150f),
                contentAlignment = Alignment.TopCenter
            ) {
                if (selectedFolderId == "NEW_FOLDER") {
                    Row(
                        modifier = Modifier
                            .width(320.dp)
                            .height(48.dp)
                            .background(Color(0x99101728), RoundedCornerShape(16.dp))
                            .padding(horizontal = 16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        BasicTextField(
                            value = newFolderNameInput,
                            onValueChange = { newFolderNameInput = it },
                            singleLine = true,
                            textStyle = androidx.compose.ui.text.TextStyle(
                                color = Color.White,
                                fontSize = 15.sp,
                                textAlign = TextAlign.Center
                            ),
                            cursorBrush = SolidColor(Color(0xFF88C0FF)),
                            modifier = Modifier.weight(1f)
                        ) { innerTextField ->
                            Box(contentAlignment = Alignment.Center) {
                                if (newFolderNameInput.isEmpty()) {
                                    Text(
                                        text = "新規フォルダ名を入力...",
                                        color = Color.Gray.copy(alpha = 0.7f),
                                        fontSize = 14.sp,
                                        textAlign = TextAlign.Center
                                    )
                                }
                                innerTextField()
                            }
                        }

                        Spacer(modifier = Modifier.width(8.dp))

                        val defaultNewFolderName = "新規フォルダ"
                        TextButton(
                            onClick = {
                                val name = newFolderNameInput.ifBlank { defaultNewFolderName }
                                val newId = System.currentTimeMillis().toString()
                                repo.prefManager.saveFolder(newId, name, emptyList())
                                refreshApps()
                                newFolderNameInput = ""
                            },
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                        ) {
                            Text("作成", color = accentColor, fontWeight = FontWeight.Bold)
                        }
                    }
                } else if (selectedFolderId != null) {
                    Row(
                        modifier = Modifier
                            .width(320.dp)
                            .height(48.dp)
                            .background(Color(0x99101728), RoundedCornerShape(16.dp)),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (isEditingFolder) {
                            // 編集モード中: 左半分 = 適用、右半分 = キャンセル
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxHeight()
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

                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxHeight()
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

        // --- 画面上部 フォルダ名編集欄 (編集モード中) ---
        if (currentLauncherPage == 3 && isEditingFolder) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = topPaddingDp + 102.dp)
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

        // 歌詞ボタン: 時計と同じ高さ、画面右側。タップで歌詞全文表示（歌詞が無いときはタグ検索）、長押しで歌詞を再取得
        if (currentLauncherPage == 1 && !isLyricsBrowsing && !pvFullscreen && sessionTrackTitle.isNotBlank()) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = topPaddingDp + 48.dp, end = 22.dp)
                    .size(44.dp)
                    .zIndex(95f)
                    .pointerInput(Unit) {
                        detectTapGestures(
                            onTap = {
                                if (syncedLyricLines.isEmpty()) {
                                    // 歌詞が取得できていないときは、タグを指定して手動検索できるようにする
                                    showLyricSearch = true
                                    return@detectTapGestures
                                }
                                lyricsBrowseStartIndex = syncedLyricLines.indexOfLast {
                                    it.timeMs <= sessionPositionMs + lyricOffsetMs
                                }.coerceAtLeast(0)
                                isLyricsBrowsing = true
                            },
                            onLongPress = {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                LyricsManager.invalidateCache(context, sessionTrackTitle, sessionTrackArtist, sessionTrackAlbum)
                                syncedLyricLines = emptyList()
                                currentLyricLine = ""
                                lyricsRefreshTick++
                            }
                        )
                    },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Lyrics,
                    contentDescription = null,
                    tint = accentColor,
                    modifier = Modifier.size(24.dp).blur(6.dp)
                )
                Icon(
                    imageVector = Icons.Default.Lyrics,
                    contentDescription = "歌詞",
                    tint = accentColor,
                    modifier = Modifier.size(24.dp)
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
                    .clickable { pvFullscreen = true },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Fullscreen,
                    contentDescription = "歌詞を全画面表示",
                    tint = accentColor,
                    modifier = Modifier.size(24.dp)
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
                            .clickable {
                                android.widget.Toast.makeText(context, if (skipLabel.contains("次の曲")) "次の曲へ" else if (skipLabel.contains("前奏")) "前奏をスキップ" else "間奏をスキップ", android.widget.Toast.LENGTH_SHORT).show()
                                if (skipTarget != null) seekSessionTo(skipTarget) else skipToNextTrack()
                            }
                            .padding(horizontal = 12.dp, vertical = 10.dp)
                    )
                }
            }
        }

        // 歌詞ブラウズ（全文表示）: ドラッグでステップスクロール、同期モードでは再生に追従
        if (isLyricsBrowsing && syncedLyricLines.isNotEmpty()) {
            LyricsBrowseOverlay(
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
                lyricWeight = lyricWeight,
                accentColor = accentColor,
                accentGlow = true,
                topPaddingDp = topPaddingDp,
                source = lyricsSource,
                translationEnabled = translationEnabled,
                translations = lyricTranslations,
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
                onClose = { pvFullscreen = false },
                onSeek = { pos -> seekSessionTo(pos) },
                onNextTrack = { skipToNextTrack() }
            )
        }

        // --- ダイアログ類 ---
        // 0. パスコード認証ダイアログ (非表示リスト用)
        if (showPasscodeDialog) {
            PasscodeDialog(
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
                message = "左の円を長押ししたまま、画面の右端までグッとスワイプすると、パスコード認証を経て「非表示アプリリスト」を開くことができます。",
                onDismiss = { dontShow ->
                    if (dontShow) {
                        repo.prefManager.setFirstHideTutorialShown(true)
                    }
                    showHideTutorial = false
                }
            )
        }



        // 3. フォルダ削除確認ダイアログ
        if (showDeleteFolderDialog && selectedFolderId != null) {
            AlertDialog(
                onDismissRequest = { showDeleteFolderDialog = false },
                confirmButton = {
                    TextButton(
                        onClick = {
                            repo.prefManager.deleteFolder(selectedFolderId)
                            refreshApps()
                            showDeleteFolderDialog = false
                        }
                    ) {
                        Text("削除", color = Color(0xFFFF5252), fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showDeleteFolderDialog = false }) {
                        Text("キャンセル", color = Color.Gray)
                    }
                },
                title = { Text("フォルダの削除", fontSize = 18.sp) },
                text = {
                    Text(
                        text = "「$selectedFolderName」を削除してもよろしいですか？（フォルダ内のアプリ自体はアンインストールされません）",
                        color = Color.LightGray,
                        fontSize = 14.sp
                    )
                },
                containerColor = Color(0xFF141C2E),
                titleContentColor = Color.White
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
            AppOptionDialog(
                app = app,
                onDismiss = { selectedAppForOption = null },
                onToggleFavorite = { targetApp ->
                    repo.setFavorite(targetApp.packageName, !targetApp.isFavorite)
                    refreshApps()
                },
                onSetYomi = { targetApp, yomi ->
                    repo.setYomi(targetApp.packageName, yomi)
                    refreshApps()
                },
                onAddAlias = { targetApp, alias ->
                    repo.addSearchAlias(targetApp.packageName, alias)
                    refreshApps()
                },
                onRemoveAlias = { targetApp, alias ->
                    repo.removeSearchAlias(targetApp.packageName, alias)
                    refreshApps()
                },
                onToggleHide = { targetApp ->
                    val willHide = !targetApp.isHidden
                    repo.setHidden(targetApp.packageName, willHide)
                    refreshApps()
                }
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

private fun extractArtworkColors(bitmap: Bitmap): Pair<Color, Color> {
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
            if (hsv[1] > 0.15f && hsv[2] > 0.15f) {
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
    // 暗すぎるアクセントは白と混ぜて視認性を上げる
    val accentLum = 0.299f * accent.red + 0.587f * accent.green + 0.114f * accent.blue
    if (accentLum < 0.22f) {
        accent = Color(
            red = accent.red + (1f - accent.red) * 0.35f,
            green = accent.green + (1f - accent.green) * 0.35f,
            blue = accent.blue + (1f - accent.blue) * 0.35f
        )
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
    return accent to background
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
    dopagaki: Boolean = false
) {
    val multiLines = lines
    // ドパガキ中もサイズは通常のまま。今の行にだけ虹色グローをかける
    val curBase = 30f
    val curWidth = screenWidthDp - 80f
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
                .padding(start = 28.dp, end = 24.dp, top = topPaddingDp + 165.dp)
        ) {
            val prevText = multiLines.getOrNull(rawIdx - 1)?.text
            val curText = multiLines.getOrNull(rawIdx)?.text
            val nextText = multiLines.getOrNull(rawIdx + 1)?.text
            if (prevText != null) {
                Text(lyricMixedFontText(prevText, enFontFamily), color = Color.White.copy(alpha = 0.45f), fontSize = fitFontSizeSp(prevText, sideBase, screenWidthDp - 52f).sp, letterSpacing = 2.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, style = LocalTextStyle.current.copy(fontFamily = fontFamily), modifier = Modifier.padding(start = 0.dp))
                if (translationEnabled) {
                    val trP = translations?.getOrNull(rawIdx - 1)
                    if (!trP.isNullOrBlank()) {
                        Text(trP, color = Color.White.copy(alpha = 0.35f), fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, style = LocalTextStyle.current.copy(fontFamily = fontFamily))
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
                    fontSize = fitFontSizeSp(curLineObj.text, curBase, curWidth).sp,
                    fontWeight = lyricWeight,
                    fontFamily = fontFamily,
                    enFontFamily = enFontFamily,
                    letterSpacing = 3.sp,
                    shadow = curShadow,
                    modifier = Modifier.padding(start = 28.dp)
                )
            } else {
                Text(
                    text = lyricMixedFontText(if (mInterlude) "♪" else (curText ?: ""), enFontFamily),
                    color = Color.White,
                    fontSize = fitFontSizeSp(if (mInterlude) "♪" else (curText ?: ""), curBase, curWidth).sp,
                    fontWeight = lyricWeight,
                    letterSpacing = 3.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = LocalTextStyle.current.copy(fontFamily = fontFamily, shadow = curShadow),
                    modifier = Modifier.padding(start = 28.dp)
                )
            }
            if (translationEnabled && !mInterlude) {
                val trC = translations?.getOrNull(rawIdx)
                if (!trC.isNullOrBlank()) {
                    Text(trC, color = Color.White.copy(alpha = 0.75f), fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, style = LocalTextStyle.current.copy(fontFamily = fontFamily), modifier = Modifier.padding(start = 28.dp, top = 4.dp))
                }
            }
            Spacer(Modifier.height(16.dp))
            if (nextText != null) {
                Text(lyricMixedFontText(nextText, enFontFamily), color = Color.White.copy(alpha = 0.45f), fontSize = fitFontSizeSp(nextText, sideBase, screenWidthDp - 108f).sp, letterSpacing = 2.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, style = LocalTextStyle.current.copy(fontFamily = fontFamily), modifier = Modifier.padding(start = 56.dp))
                if (translationEnabled) {
                    val trN = translations?.getOrNull(rawIdx + 1)
                    if (!trN.isNullOrBlank()) {
                        Text(trN, color = Color.White.copy(alpha = 0.35f), fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, style = LocalTextStyle.current.copy(fontFamily = fontFamily), modifier = Modifier.padding(start = 56.dp))
                    }
                }
            }
        }
    }
}
