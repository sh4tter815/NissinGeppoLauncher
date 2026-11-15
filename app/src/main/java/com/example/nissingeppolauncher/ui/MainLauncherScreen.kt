package com.example.nissingeppolauncher.ui

import android.graphics.Bitmap
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
import androidx.compose.animation.core.tween
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.foundation.Canvas
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shadow
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
import androidx.compose.foundation.gestures.awaitFirstDown
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
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.example.nissingeppolauncher.data.AppInfo
import com.example.nissingeppolauncher.data.AppRepository
import com.example.nissingeppolauncher.data.LyricLine
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
    var blurArtworkBg by remember { mutableStateOf(repo.prefManager.isBlurArtworkBackground()) }
    var artworkBgStyle by remember { mutableStateOf(repo.prefManager.getArtworkBackgroundStyle()) }
    var liquidBackground by remember { mutableStateOf(repo.prefManager.isLiquidBackgroundEnabled()) }
    var homeDiscLayout by remember { mutableStateOf(repo.prefManager.getHomeDiscLayout()) }
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
    var lyricsBold by remember { mutableStateOf(repo.prefManager.isLyricsBold()) }

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
    LaunchedEffect(sessionTrackTitle, sessionTrackArtist) {
        if (sessionTrackTitle.isBlank()) {
            syncedLyricLines = emptyList()
            currentLyricLine = ""
            return@LaunchedEffect
        }
        delay(600)
        val lines = LyricsManager.getSyncedLyrics(
            context, sessionTrackTitle, sessionTrackArtist, sessionTrackAlbum, sessionDurationMs, null
        )
        syncedLyricLines = lines ?: emptyList()
        if (lines == null) currentLyricLine = ""
        lyricOffsetMs = repo.prefManager.getLyricOffsetMs(sessionTrackTitle, sessionTrackArtist)
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
    LaunchedEffect(matchArtwork, blurArtworkBg) {
        if (!matchArtwork && !blurArtworkBg) {
            sessionAlbumArt = null
            return@LaunchedEffect
        }
        val mediaSessionManager = context.getSystemService(android.content.Context.MEDIA_SESSION_SERVICE) as? android.media.session.MediaSessionManager
        val listenerComponent = android.content.ComponentName(context, com.example.nissingeppolauncher.service.LauncherMediaListenerService::class.java)
        while (true) {
            sessionAlbumArt = try {
                val controllers = mediaSessionManager?.getActiveSessions(listenerComponent)
                val activeController = controllers?.firstOrNull {
                    it.playbackState?.state == android.media.session.PlaybackState.STATE_PLAYING
                } ?: controllers?.firstOrNull()
                val metadata = activeController?.metadata
                metadata?.getBitmap(android.media.MediaMetadata.METADATA_KEY_ALBUM_ART)
                    ?: metadata?.getBitmap(android.media.MediaMetadata.METADATA_KEY_ART)
            } catch (e: Exception) {
                null
            }
            delay(1500)
        }
    }
    LaunchedEffect(sessionAlbumArt, matchArtwork, blurArtworkBg) {
        val art = sessionAlbumArt
        if ((matchArtwork || blurArtworkBg) && art != null) {
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

    var showWelcomeTutorial by remember { mutableStateOf(false) }
    var showIndexTutorial by remember { mutableStateOf(false) }
    var showHideTutorial by remember { mutableStateOf(false) }

    // ページが切り替わるたびにアクセントカラー等を自動再読込
    LaunchedEffect(currentLauncherPage) {
        accentHex = repo.prefManager.getAccentColorHex()
        matchArtwork = repo.prefManager.isMatchArtworkColor()
        blurArtworkBg = repo.prefManager.isBlurArtworkBackground()
        artworkBgStyle = repo.prefManager.getArtworkBackgroundStyle()
        homeDiscLayout = repo.prefManager.getHomeDiscLayout()
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
    val folderStepAngle = 24f

    val createNewFolderStr = "新規作成"
    val folderItemsWithNew = remember(folderList, createNewFolderStr) {
        folderList + Pair("NEW_FOLDER", createNewFolderStr)
    }

    val selectedFolderItemIndex = remember(folderWheelRotation, folderItemsWithNew.size) {
        if (folderItemsWithNew.isEmpty()) -1
        else {
            val rawIdx = (-folderWheelRotation / folderStepAngle).roundToInt()
            ((rawIdx % folderItemsWithNew.size) + folderItemsWithNew.size) % folderItemsWithNew.size
        }
    }

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

    val selectedIndex = remember(leftRotation, INDEX_ITEMS.size) {
        if (INDEX_ITEMS.isEmpty()) -1
        else {
            val rawIdx = (-leftRotation / stepAngle).roundToInt()
            ((rawIdx % INDEX_ITEMS.size) + INDEX_ITEMS.size) % INDEX_ITEMS.size
        }
    }
    val selectedIndexChar = remember(selectedIndex) {
        if (selectedIndex in INDEX_ITEMS.indices) INDEX_ITEMS[selectedIndex] else null
    }

    var lastHapticIndex by remember { mutableIntStateOf(-1) }
    LaunchedEffect(selectedIndex) {
        if ((selectedIndex != -1) && (selectedIndex != lastHapticIndex) && (lastHapticIndex != -1)) {
            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        }
        lastHapticIndex = selectedIndex
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
                currentAlbumArt = if (matchArtwork || blurArtworkBg) sessionAlbumArt else null,
                dynamicBackground = if (matchArtwork || blurArtworkBg) artworkBackground else null,
                artworkBackgroundStyle = artworkBgStyle,
                liquidEnabled = liquidBackground
            )

            // リリックビデオ（文字PV）: 再生中セッション（Spotify等も含む）の歌詞を大きく散らして表示する
            if (lyricsDisplayMode == "video" && currentLauncherPage == 1 && syncedLyricLines.isNotEmpty()) {
                val pvLines = syncedLyricLines
                val pvPos = sessionPositionMs + lyricOffsetMs
                val pvIndex = pvLines.indexOfLast { it.timeMs <= pvPos }
                val pvCurLine = pvLines.getOrNull(pvIndex)
                val pvNextLine = pvLines.getOrNull(pvIndex + 1)
                val pvInterlude = when {
                    pvCurLine == null -> false
                    pvNextLine == null -> (pvPos - pvCurLine.timeMs) > 8000
                    else -> (pvNextLine.timeMs - pvCurLine.timeMs) > 15000 && (pvPos - pvCurLine.timeMs) > 5000
                }
                val pvDisplayIndex = if (pvInterlude) -2 else pvIndex
                if (pvIndex >= 0) {
                    val pvFontFamily = lyricsFontFamily
                    val pvKindMap = remember { mutableStateMapOf<Int, Int>() }
                    fun pvKindFor(idx: Int, text: String): Int {
                        pvKindMap[idx]?.let { return it }
                        val raw = Math.floorMod(Math.floorMod(idx * 31 + text.hashCode(), 997), 8)
                        val prev = pvKindMap[idx - 1]
                        val kind = if (prev != null && raw == prev) (raw + 1) % 8 else raw
                        pvKindMap[idx] = kind
                        return kind
                    }
                    var pvCurrent by remember { mutableStateOf<Pair<Int, String>?>(null) }
                    var pvExiting by remember { mutableStateOf<Pair<Int, String>?>(null) }
                    LaunchedEffect(syncedLyricLines) {
                        pvCurrent = null
                        pvExiting = null
                    }
                    LaunchedEffect(pvDisplayIndex) {
                        if (pvDisplayIndex >= 0) {
                            if (pvCurrent?.first != pvDisplayIndex) {
                                pvCurrent?.let { pvExiting = it }
                                if (pvExiting?.first == pvDisplayIndex) pvExiting = null
                                pvCurrent = pvDisplayIndex to pvLines[pvDisplayIndex].text
                            }
                        } else if (pvDisplayIndex == -2) {
                            pvCurrent?.let { pvExiting = it }
                            pvCurrent = null
                        }
                        if (pvExiting != null) {
                            delay(750)
                            pvExiting = null
                        }
                    }
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
                        pvExiting?.let { (exIdx, exText) ->
                            key("pvexit$exIdx") {
                                PvLyricLine(
                                    bold = lyricsBold,
                                    text = exText,
                                    baseSeed = Math.floorMod(exIdx * 31 + exText.hashCode(), 997),
                                    fontFamily = pvFontFamily,
                                    accentColor = accentColor,
                                    screenWidthDp = screenWidthDp,
                                    exiting = true,
                                    effectKind = pvKindFor(exIdx, exText),
                                    lineDurationMs = pvLines.getOrNull(exIdx + 1)?.let { it.timeMs - pvLines[exIdx].timeMs } ?: Long.MAX_VALUE,
                                    modifier = Modifier.padding(top = topPaddingDp + 165.dp)
                                )
                            }
                        }
                        pvCurrent?.let { (curIdx, curText) ->
                            key("pvcur$curIdx") {
                                PvLyricLine(
                                    bold = lyricsBold,
                                    text = curText,
                                    baseSeed = Math.floorMod(curIdx * 31 + curText.hashCode(), 997),
                                    fontFamily = pvFontFamily,
                                    accentColor = accentColor,
                                    screenWidthDp = screenWidthDp,
                                    exiting = false,
                                    effectKind = pvKindFor(curIdx, curText),
                                    lineDurationMs = pvLines.getOrNull(curIdx + 1)?.let { it.timeMs - pvLines[curIdx].timeMs } ?: Long.MAX_VALUE,
                                    modifier = Modifier.padding(top = topPaddingDp + 165.dp)
                                )
                            }
                        }
                    }
                    if (pvDisplayIndex == -2) {
                        PvInterludeArt(accentColor = accentColor, modifier = Modifier.fillMaxWidth().padding(top = topPaddingDp + 145.dp).height(230.dp))
                    }
                    pvCurrent?.let { (_, curText) ->
                        val ambKind = when {
                            curText.contains("桜") -> 2
                            curText.contains("雨") -> 0
                            curText.contains("雪") -> 1
                            else -> -1
                        }
                        if (ambKind >= 0) {
                            PvAmbientEffect(kind = ambKind, modifier = Modifier.fillMaxSize())
                        }
                    }
                }
            }

            // 複数行モード: 時計の下に前・今・次の3行を表示（今の行が大きめ）
            if (lyricsDisplayMode == "multi" && currentLauncherPage == 1 && syncedLyricLines.isNotEmpty()) {
                val multiLines = syncedLyricLines
                val mPos = sessionPositionMs + lyricOffsetMs
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
                            Text(prevText, color = Color.White.copy(alpha = 0.45f), fontSize = fitFontSizeSp(prevText, 18f, screenWidthDp - 52f).sp, letterSpacing = 2.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, style = LocalTextStyle.current.copy(fontFamily = lyricsFontFamily), modifier = Modifier.padding(start = 0.dp))
                        } else {
                            Spacer(Modifier.height(24.dp))
                        }
                        Spacer(Modifier.height(16.dp))
                        Text(
                            text = if (mInterlude) "♪" else (curText ?: ""),
                            color = Color.White,
                            fontSize = fitFontSizeSp(if (mInterlude) "♪" else (curText ?: ""), 30f, screenWidthDp - 80f).sp,
                            fontWeight = lyricWeight,
                            letterSpacing = 3.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            style = LocalTextStyle.current.copy(fontFamily = lyricsFontFamily, shadow = Shadow(color = accentColor.copy(alpha = 0.8f), blurRadius = 16f)),
                            modifier = Modifier.padding(start = 28.dp)
                        )
                        Spacer(Modifier.height(16.dp))
                        if (nextText != null) {
                            Text(nextText, color = Color.White.copy(alpha = 0.45f), fontSize = fitFontSizeSp(nextText, 18f, screenWidthDp - 108f).sp, letterSpacing = 2.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, style = LocalTextStyle.current.copy(fontFamily = lyricsFontFamily), modifier = Modifier.padding(start = 56.dp))
                        }
                    }
                }
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
                                lyricsBold = repo.prefManager.isLyricsBold()
                            },
                            onAppsUpdated = {
                                refreshApps()
                            },
                            accentColor = accentColor
                        )
                    }
                1 -> {
                    // --- 1: ホーム画面 ---
                    ClockWidget(
                        modifier = Modifier
                            .padding(start = 28.dp, top = topPaddingDp + 48.dp)
                            .zIndex(10f),
                        lyricsLine = if (lyricsDisplayMode == "line") currentLyricLine else "",
                        dateMode = dateDisplayMode,
                        glowColor = accentColor
                    )

                    RotatableAppDisc(
                        modifier = Modifier.fillMaxSize(),
                        apps = favoriteApps,
                        discCenterXDp = animatedDisc2CenterX,
                        discCenterYDp = disc2CenterY,
                        discRadiusDp = disc2RadiusDp,
                        textSizeSp = appDiscTextSize,
                        iconSizeDp = appDiscIconSize,
                        letterSpacingSp = appDiscLetterSpacing,
                        stepAngle = appDiscStepAngle,
                        selectedIndexChar = null,
                        isRightDisc = true,
                        showIndices = false,
                        showLabels = !isBigDiscLayout, // アートワーク大きめレイアウトではお気に入り名を隠す
                        isLoopable = false, // ホームお気に入りはループしない
                        accentColor = accentColor,
                        onAppClick = { onAppClick(it.packageName) },
                        onLongClick = { selectedAppForOption = it },
                        onHoldingChanged = { isHoldingRight = it }
                    )

                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .zIndex(50f)
                    ) {
                        MusicVinylWidget(
                            modifier = Modifier.fillMaxSize(),
                            discCenterXDp = animatedDisc1CenterX,
                            discCenterYDp = disc1CenterY,
                            discRadiusDp = disc1RadiusDp,
                            accentColor = accentColor
                        )
                    }
                }
                2 -> {
                    // --- 2: アプリ一覧画面 ---
                    RotatableIndexWheel(
                        modifier = Modifier.fillMaxSize(),
                        discCenterXDp = animatedDisc1CenterX,
                        discCenterYDp = disc1CenterY,
                        discRadiusDp = disc1RadiusDp,
                        currentRotation = leftRotation,
                        isHolding = isHoldingLeft,
                        stepAngle = stepAngle,
                        accentColor = accentColor,
                        centerIcon = {
                            if (isReadyToOpenHiddenList) {
                                Text(
                                    text = "非表示\nリストへ",
                                    color = accentColor,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    textAlign = TextAlign.Center
                                )
                            } else {
                                WhiteSearchIcon(size = 24.dp)
                            }
                        }
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

                    RotatableAppDisc(
                        modifier = Modifier.fillMaxSize(),
                        apps = displayedApps,
                        discCenterXDp = animatedDisc2CenterX,
                        discCenterYDp = disc2CenterY,
                        discRadiusDp = disc2RadiusDp,
                        textSizeSp = appDiscTextSize,
                        iconSizeDp = appDiscIconSize,
                        letterSpacingSp = appDiscLetterSpacing,
                        stepAngle = appDiscStepAngle,
                        selectedIndexChar = selectedIndexChar,
                        isRightDisc = true,
                        showIndices = true,
                        isLoopable = (!isHideMode && searchQuery.isBlank()), // 通常アプリ一覧(検索なし)のみ循環ループ、検索時や非表示リスト表示時はループなし
                        accentColor = accentColor,
                        onAppClick = { onAppClick(it.packageName) },
                        onLongClick = { selectedAppForOption = it },
                        onHoldingChanged = { isHoldingRight = it }
                    )
                }
                3 -> {
                    // --- 3: フォルダ画面 ---
                    if (isEditingFolder) {
                        RotatableIndexWheel(
                            modifier = Modifier.fillMaxSize(),
                            discCenterXDp = animatedDisc1CenterX,
                            discCenterYDp = disc1CenterY,
                            discRadiusDp = disc1RadiusDp,
                            currentRotation = leftRotation,
                            isHolding = isHoldingLeft,
                            stepAngle = stepAngle,
                            accentColor = accentColor,
                            centerIcon = { WhiteSearchIcon(size = 24.dp) }
                        )
                    } else {
                        // 左側: フォルダ選択ダイヤル
                        RotatableFolderWheel(
                            folders = folderList,
                            modifier = Modifier.fillMaxSize(),
                            discCenterXDp = animatedDisc1CenterX,
                            discCenterYDp = disc1CenterY,
                            discRadiusDp = disc1RadiusDp,
                            currentRotation = folderWheelRotation,
                            isHolding = isHoldingFolderWheel,
                            stepAngle = folderStepAngle,
                            textSizeSp = appDiscTextSize,
                            letterSpacingSp = appDiscLetterSpacing,
                            accentColor = accentColor,
                            onNewFolderClick = {
                                val idx = folderItemsWithNew.indexOfFirst { it.first == "NEW_FOLDER" }
                                if (idx != -1) {
                                    coroutineScope.launch {
                                        folderWheelRotationAnim.animateTo(-idx * folderStepAngle, animationSpec = tween(200))
                                    }
                                }
                                newFolderNameInput = ""
                            },
                            onRenameFolderClick = { id, _ ->
                                val idx = folderItemsWithNew.indexOfFirst { it.first == id }
                                if (idx != -1) {
                                    coroutineScope.launch {
                                        folderWheelRotationAnim.animateTo(-idx * folderStepAngle, animationSpec = tween(200))
                                    }
                                }
                            }
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

                    RotatableAppDisc(
                        modifier = Modifier.fillMaxSize(),
                        apps = discApps,
                        discCenterXDp = animatedDisc2CenterX,
                        discCenterYDp = disc2CenterY,
                        discRadiusDp = disc2RadiusDp,
                        textSizeSp = appDiscTextSize,
                        iconSizeDp = appDiscIconSize,
                        letterSpacingSp = appDiscLetterSpacing,
                        stepAngle = appDiscStepAngle,
                        selectedIndexChar = if (isEditingFolder) selectedIndexChar else null,
                        isRightDisc = true,
                        showIndices = isEditingFolder,
                        isLoopable = false, // フォルダ内アプリはループしない
                        selectedPackages = if (isEditingFolder) editingSelectedPackages else null,
                        accentColor = accentColor,
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
                        },
                        onHoldingChanged = { isHoldingRight = it }
                    )
                }
            }
        }

                // --- 画面下部 ページ切り替え (ボタン式 / ダイヤル式を設定で切替) ---
        if (repo.prefManager.getPageSwitchStyle() == "dial") {
            PeekPageDial(
                currentPage = currentLauncherPage,
                onPageSelected = { targetPage ->
                    changePageWithTransition(targetPage)
                },
                accentColor = accentColor,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = bottomPaddingDp + 4.dp)
                    .zIndex(250f)
            )
        } else {
            BottomPageDial(
                currentPage = currentLauncherPage,
                onPageSelected = { targetPage ->
                    changePageWithTransition(targetPage)
                },
                accentColor = accentColor,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = bottomPaddingDp + 4.dp)
                    .zIndex(250f)
            )
        }

        // --- タッチ操作パネル (左半分: アプリ一覧(2) & フォルダ(3)) ---
        if (!isTransitioning && (currentLauncherPage == 2 || currentLauncherPage == 3)) {
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
                                            (-leftRotationAnim.value / stepAngle).roundToInt()
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
                                                    coroutineScope.launch {
                                                        leftRotationAnim.animateTo(
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
                                                    coroutineScope.launch {
                                                        leftRotationAnim.animateTo(
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
        BackHandler(enabled = currentLauncherPage != 1 || isSearchActive || isEditingFolder) {
            if (isEditingFolder) {
                isEditingFolder = false
            } else if (isSearchActive) {
                isSearchActive = false
                searchQuery = ""
                keyboardController?.hide()
            } else if (currentLauncherPage != 1) {
                changePageWithTransition(1)
            }
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

private object LyricIcon {
    const val UMBRELLA = 0; const val RAIN = 1; const val SNOW = 2; const val STAR = 3
    const val MOON = 4; const val SUN = 5; const val CLOUD = 6; const val FLOWER = 7
    const val WAVES = 8; const val HEART = 9; const val KEY = 10; const val NOTE = 11
    const val ENVELOPE = 12; const val CLOCK = 13; const val FLAME = 14; const val DROP = 15
    const val RAINBOW = 16; const val LIGHTNING = 17; const val SMILE = 18; const val FISH = 19
    const val MOUNTAIN = 20; const val WIND = 21
    const val LINE = 22; const val CIRCLE = 23
    const val GLOBE = 24; const val BUBBLE = 25
    const val COFFIN = 26; const val EYE = 27; const val PLUS = 28
    const val MINUS = 29; const val MAP = 30; const val BONE = 31
}

/** 歌詞に出てきたらアイコンを出す単語（前にあるものが優先） */
private val LYRIC_ICON_WORDS: List<Pair<String, Int>> = listOf(
    "傘" to LyricIcon.UMBRELLA, "桜" to LyricIcon.FLOWER, "雨" to LyricIcon.RAIN,
    "雪" to LyricIcon.SNOW, "星" to LyricIcon.STAR, "月" to LyricIcon.MOON,
    "太陽" to LyricIcon.SUN, "雲" to LyricIcon.CLOUD, "花" to LyricIcon.FLOWER,
    "海" to LyricIcon.WAVES, "波" to LyricIcon.WAVES, "心" to LyricIcon.HEART,
    "ハート" to LyricIcon.HEART, "恋" to LyricIcon.HEART, "愛" to LyricIcon.HEART,
    "夢" to LyricIcon.BUBBLE, "夜" to LyricIcon.MOON, "鍵" to LyricIcon.KEY,
    "音楽" to LyricIcon.NOTE, "歌" to LyricIcon.NOTE, "手紙" to LyricIcon.ENVELOPE,
    "時計" to LyricIcon.CLOCK, "炎" to LyricIcon.FLAME, "火" to LyricIcon.FLAME,
    "涙" to LyricIcon.DROP, "雫" to LyricIcon.DROP, "虹" to LyricIcon.RAINBOW,
    "雷" to LyricIcon.LIGHTNING, "笑顔" to LyricIcon.SMILE, "魚" to LyricIcon.FISH,
    "山" to LyricIcon.MOUNTAIN, "風" to LyricIcon.WIND,
"音" to LyricIcon.NOTE, "濡" to LyricIcon.DROP,
    "時間" to LyricIcon.CLOCK, "場所" to LyricIcon.MAP, "死" to LyricIcon.COFFIN,
    "目" to LyricIcon.EYE, "水" to LyricIcon.DROP, "増" to LyricIcon.PLUS,
    "減" to LyricIcon.MINUS, "骨" to LyricIcon.BONE
)

/** 白い枠線だけの小さな線画アイコンを描く（キャンバスは正方形前提） */
private fun DrawScope.drawLyricIcon(kind: Int) {
    val col = Color.White.copy(alpha = 0.92f)
    val u = size.width / 100f
    val cx = size.width / 2f
    val cy = size.height / 2f
    val st = Stroke(width = 2.3f * u, cap = StrokeCap.Round, join = StrokeJoin.Round)
    fun pt(dx: Float, dy: Float) = Offset(cx + dx * u, cy + dy * u)
    when (kind) {
        LyricIcon.STAR -> {
            val path = Path()
            for (i in 0..9) {
                val ang = Math.toRadians(-90.0 + i * 36.0)
                val r = if (i % 2 == 0) 34.0 else 15.0
                val x = cx + (r * kotlin.math.cos(ang)).toFloat() * u
                val y = cy + (r * kotlin.math.sin(ang)).toFloat() * u
                if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }
            path.close()
            drawPath(path, col, style = st)
        }
        LyricIcon.HEART -> {
            val path = Path().apply {
                moveTo(cx, cy + 30f * u)
                cubicTo(cx - 44f * u, cy + 2f * u, cx - 28f * u, cy - 32f * u, cx, cy - 13f * u)
                cubicTo(cx + 28f * u, cy - 32f * u, cx + 44f * u, cy + 2f * u, cx, cy + 30f * u)
                close()
            }
            drawPath(path, col, style = st)
        }
        LyricIcon.MOON -> {
            // 三日月: 外周と内側の食い込みをベジェで結んだ輪郭
            val path = Path().apply {
                moveTo(14f * u, -22f * u)
                cubicTo(26f * u, -6f * u, 26f * u, 8f * u, 12f * u, 22f * u)
                cubicTo(-8f * u, 12f * u, -8f * u, -10f * u, 14f * u, -22f * u)
                close()
            }
            drawPath(path, col, style = st)
        }
        LyricIcon.SUN -> {
            drawCircle(col, radius = 15f * u, center = pt(0f, 0f), style = st)
            for (i in 0 until 8) {
                val ang = Math.toRadians(i * 45.0)
                val c = kotlin.math.cos(ang).toFloat(); val sn = kotlin.math.sin(ang).toFloat()
                drawLine(col, pt(c * 23f, sn * 23f), pt(c * 31f, sn * 31f), strokeWidth = 3.0f * u, cap = StrokeCap.Round)
            }
        }
        LyricIcon.CLOUD, LyricIcon.RAIN -> {
            val oy = if (kind == LyricIcon.RAIN) -8f else 0f
            drawLine(col, pt(-26f, 16f + oy), pt(26f, 16f + oy), strokeWidth = 3.0f * u, cap = StrokeCap.Round)
            drawArc(col, topLeft = pt(-28f, -8f + oy), size = Size(30f * u, 24f * u), startAngle = 180f, sweepAngle = 180f, useCenter = false, style = st)
            drawArc(col, topLeft = pt(-4f, -18f + oy), size = Size(34f * u, 34f * u), startAngle = 180f, sweepAngle = 180f, useCenter = false, style = st)
            drawArc(col, topLeft = pt(12f, -6f + oy), size = Size(26f * u, 22f * u), startAngle = 180f, sweepAngle = 180f, useCenter = false, style = st)
            drawLine(col, pt(-26f, 4f + oy), pt(-26f, 16f + oy), strokeWidth = 3.0f * u, cap = StrokeCap.Round)
            drawLine(col, pt(26f, 4f + oy), pt(26f, 16f + oy), strokeWidth = 3.0f * u, cap = StrokeCap.Round)
            if (kind == LyricIcon.RAIN) {
                drawLine(col, pt(-16f, 26f), pt(-20f, 36f), strokeWidth = 3.0f * u, cap = StrokeCap.Round)
                drawLine(col, pt(0f, 26f), pt(-4f, 36f), strokeWidth = 3.0f * u, cap = StrokeCap.Round)
                drawLine(col, pt(16f, 26f), pt(12f, 36f), strokeWidth = 3.0f * u, cap = StrokeCap.Round)
            }
        }
        LyricIcon.SNOW -> {
            for (i in 0 until 3) {
                val ang = Math.toRadians(i * 60.0)
                val c = kotlin.math.cos(ang).toFloat(); val sn = kotlin.math.sin(ang).toFloat()
                drawLine(col, pt(-c * 30f, -sn * 30f), pt(c * 30f, sn * 30f), strokeWidth = 2.8f * u, cap = StrokeCap.Round)
            }
            drawCircle(col, radius = 5f * u, center = pt(0f, 0f), style = st)
        }
        LyricIcon.UMBRELLA -> {
            drawArc(col, topLeft = pt(-32f, -28f), size = Size(64f * u, 54f * u), startAngle = 180f, sweepAngle = 180f, useCenter = false, style = st)
            drawLine(col, pt(-32f, -1f), pt(-11f, -1f), strokeWidth = 2.8f * u, cap = StrokeCap.Round)
            drawLine(col, pt(-11f, -1f), pt(11f, -1f), strokeWidth = 2.8f * u, cap = StrokeCap.Round)
            drawLine(col, pt(11f, -1f), pt(32f, -1f), strokeWidth = 2.8f * u, cap = StrokeCap.Round)
            drawLine(col, pt(0f, -28f), pt(0f, 20f), strokeWidth = 3.0f * u, cap = StrokeCap.Round)
            drawArc(col, topLeft = pt(0f, 10f), size = Size(18f * u, 18f * u), startAngle = 0f, sweepAngle = 180f, useCenter = false, style = st)
        }
        LyricIcon.FLOWER -> {
            for (i in 0 until 5) {
                val ang = Math.toRadians(-90.0 + i * 72.0)
                val px = (15.0 * kotlin.math.cos(ang)).toFloat(); val py = (15.0 * kotlin.math.sin(ang)).toFloat()
                drawCircle(col, radius = 10.5f * u, center = pt(px, py), style = st)
            }
            drawCircle(col, radius = 5.5f * u, center = pt(0f, 0f), style = st)
        }
        LyricIcon.WAVES -> {
            val path = Path()
            for (yOff in listOf(-6f, 12f)) {
                path.moveTo(cx - 32f * u, cy + yOff * u)
                path.quadraticBezierTo(cx - 16f * u, cy + (yOff - 15f) * u, cx, cy + yOff * u)
                path.quadraticBezierTo(cx + 16f * u, cy + (yOff + 15f) * u, cx + 32f * u, cy + yOff * u)
            }
            drawPath(path, col, style = st)
        }
        LyricIcon.KEY -> {
            drawCircle(col, radius = 13f * u, center = pt(-13f, -7f), style = st)
            drawLine(col, pt(-4f, 2f), pt(25f, 29f), strokeWidth = 3.3f * u, cap = StrokeCap.Round)
            drawLine(col, pt(15f, 19f), pt(23f, 11f), strokeWidth = 3.0f * u, cap = StrokeCap.Round)
            drawLine(col, pt(21f, 25f), pt(28f, 18f), strokeWidth = 3.0f * u, cap = StrokeCap.Round)
        }
        LyricIcon.NOTE -> {
            drawOval(col, topLeft = pt(-26f, 10f), size = Size(17f * u, 13f * u), style = st)
            drawOval(col, topLeft = pt(9f, 4f), size = Size(17f * u, 13f * u), style = st)
            drawLine(col, pt(-9f, 13f), pt(-9f, -22f), strokeWidth = 3.0f * u, cap = StrokeCap.Round)
            drawLine(col, pt(26f, 7f), pt(26f, -28f), strokeWidth = 3.0f * u, cap = StrokeCap.Round)
            drawLine(col, pt(-9f, -22f), pt(26f, -28f), strokeWidth = 5.0f * u, cap = StrokeCap.Round)
        }
        LyricIcon.ENVELOPE -> {
            drawRoundRect(col, topLeft = pt(-30f, -18f), size = Size(60f * u, 36f * u), cornerRadius = CornerRadius(4f * u, 4f * u), style = st)
            drawLine(col, pt(-29f, -17f), pt(0f, 5f), strokeWidth = 2.8f * u, cap = StrokeCap.Round)
            drawLine(col, pt(29f, -17f), pt(0f, 5f), strokeWidth = 2.8f * u, cap = StrokeCap.Round)
        }
        LyricIcon.CLOCK -> {
            drawCircle(col, radius = 28f * u, center = pt(0f, 0f), style = st)
            drawLine(col, pt(0f, 0f), pt(0f, -15f), strokeWidth = 3.0f * u, cap = StrokeCap.Round)
            drawLine(col, pt(0f, 0f), pt(11f, 6f), strokeWidth = 3.0f * u, cap = StrokeCap.Round)
        }
        LyricIcon.FLAME -> {
            val path = Path().apply {
                moveTo(cx, cy - 32f * u)
                cubicTo(cx + 20f * u, cy - 8f * u, cx + 22f * u, cy + 6f * u, cx + 14f * u, cy + 18f * u)
                cubicTo(cx + 8f * u, cy + 29f * u, cx - 8f * u, cy + 29f * u, cx - 14f * u, cy + 18f * u)
                cubicTo(cx - 22f * u, cy + 6f * u, cx - 13f * u, cy - 10f * u, cx, cy - 32f * u)
                close()
            }
            drawPath(path, col, style = st)
        }
        LyricIcon.DROP -> {
            val path = Path().apply {
                moveTo(cx, cy - 30f * u)
                cubicTo(cx + 17f * u, cy - 2f * u, cx + 20f * u, cy + 8f * u, cx + 20f * u, cy + 14f * u)
                cubicTo(cx + 20f * u, cy + 27f * u, cx - 20f * u, cy + 27f * u, cx - 20f * u, cy + 14f * u)
                cubicTo(cx - 20f * u, cy + 8f * u, cx - 17f * u, cy - 2f * u, cx, cy - 30f * u)
                close()
            }
            drawPath(path, col, style = st)
        }
        LyricIcon.RAINBOW -> {
            drawArc(col, topLeft = pt(-32f, -10f), size = Size(64f * u, 64f * u), startAngle = 180f, sweepAngle = 180f, useCenter = false, style = st)
            drawArc(col, topLeft = pt(-23f, -1f), size = Size(46f * u, 46f * u), startAngle = 180f, sweepAngle = 180f, useCenter = false, style = st)
            drawArc(col, topLeft = pt(-14f, 8f), size = Size(28f * u, 28f * u), startAngle = 180f, sweepAngle = 180f, useCenter = false, style = st)
        }
        LyricIcon.LIGHTNING -> {
            val path = Path().apply {
                moveTo(cx + 7f * u, cy - 31f * u)
                lineTo(cx - 14f * u, cy + 6f * u)
                lineTo(cx - 1f * u, cy + 6f * u)
                lineTo(cx - 7f * u, cy + 31f * u)
                lineTo(cx + 16f * u, cy - 8f * u)
                lineTo(cx + 3f * u, cy - 8f * u)
                close()
            }
            drawPath(path, col, style = st)
        }
        LyricIcon.SMILE -> {
            drawCircle(col, radius = 27f * u, center = pt(0f, 0f), style = st)
            drawCircle(col, radius = 2.8f * u, center = pt(-11f, -6f))
            drawCircle(col, radius = 2.8f * u, center = pt(11f, -6f))
            drawArc(col, topLeft = pt(-14f, -3f), size = Size(28f * u, 22f * u), startAngle = 25f, sweepAngle = 130f, useCenter = false, style = st)
        }
        LyricIcon.FISH -> {
            drawOval(col, topLeft = pt(-29f, -11f), size = Size(45f * u, 22f * u), style = st)
            val path = Path().apply {
                moveTo(cx + 13f * u, cy)
                lineTo(cx + 30f * u, cy - 12f * u)
                lineTo(cx + 30f * u, cy + 12f * u)
                close()
            }
            drawPath(path, col, style = st)
            drawCircle(col, radius = 2.6f * u, center = pt(-17f, -3f))
        }
        LyricIcon.MOUNTAIN -> {
            val path = Path().apply {
                moveTo(cx - 32f * u, cy + 22f * u)
                lineTo(cx - 8f * u, cy - 16f * u)
                lineTo(cx + 9f * u, cy + 22f * u)
                moveTo(cx - 3f * u, cy + 22f * u)
                lineTo(cx + 17f * u, cy - 5f * u)
                lineTo(cx + 32f * u, cy + 22f * u)
            }
            drawPath(path, col, style = st)
        }
        LyricIcon.LINE -> {
            // 「線」そのもの: 少し斜めの線を1本引く
            drawLine(col, pt(-30f, 8f), pt(30f, -10f), strokeWidth = 3.3f * u, cap = StrokeCap.Round)
        }
        LyricIcon.CIRCLE -> {
            // 「円」そのもの: 円を描く
            drawCircle(col, radius = 26f * u, center = pt(0f, 0f), style = st)
        }
        LyricIcon.COFFIN -> {
            // 棺: 肩が広がった六角形
            val path = Path().apply {
                moveTo(-13f * u, -30f * u)
                lineTo(13f * u, -30f * u)
                lineTo(25f * u, -6f * u)
                lineTo(14f * u, 30f * u)
                lineTo(-14f * u, 30f * u)
                lineTo(-25f * u, -6f * u)
                close()
            }
            drawPath(path, col, style = st)
        }
        LyricIcon.EYE -> {
            // 目: アーモンド型の輪郭に虹彩
            val path = Path().apply {
                moveTo(-30f * u, 0f)
                quadraticTo(0f, -27f * u, 30f * u, 0f)
                quadraticTo(0f, 27f * u, -30f * u, 0f)
                close()
            }
            drawPath(path, col, style = st)
            drawCircle(col, radius = 10f * u, center = pt(0f, 0f), style = st)
            drawCircle(col, radius = 3.2f * u, center = pt(0f, 0f))
        }
        LyricIcon.PLUS -> {
            drawLine(col, pt(-17f, 0f), pt(17f, 0f), strokeWidth = 3.9f * u, cap = StrokeCap.Round)
            drawLine(col, pt(0f, -17f), pt(0f, 17f), strokeWidth = 3.9f * u, cap = StrokeCap.Round)
        }
        LyricIcon.MINUS -> {
            drawLine(col, pt(-17f, 0f), pt(17f, 0f), strokeWidth = 3.9f * u, cap = StrokeCap.Round)
        }
        LyricIcon.MAP -> {
            // 折れた地図: 三つ折りの面と折り目
            val path = Path().apply {
                moveTo(-28f * u, -17f * u)
                lineTo(-10f * u, -23f * u)
                lineTo(10f * u, -17f * u)
                lineTo(28f * u, -23f * u)
                lineTo(28f * u, 17f * u)
                lineTo(10f * u, 23f * u)
                lineTo(-10f * u, 17f * u)
                lineTo(-28f * u, 23f * u)
                close()
            }
            drawPath(path, col, style = st)
            drawLine(col, pt(-10f, -23f), pt(-10f, 17f), strokeWidth = 2.2f * u, cap = StrokeCap.Round)
            drawLine(col, pt(10f, -17f), pt(10f, 23f), strokeWidth = 2.2f * u, cap = StrokeCap.Round)
        }
        LyricIcon.BONE -> {
            // 骨: 斜めの軸の両端にこぶ
            drawLine(col, pt(-14f, 14f), pt(14f, -14f), strokeWidth = 5.0f * u, cap = StrokeCap.Round)
            drawCircle(col, radius = 7.5f * u, center = pt(-19f, 11f), style = st)
            drawCircle(col, radius = 7.5f * u, center = pt(-11f, 19f), style = st)
            drawCircle(col, radius = 7.5f * u, center = pt(11f, -19f), style = st)
            drawCircle(col, radius = 7.5f * u, center = pt(19f, -11f), style = st)
        }
        LyricIcon.GLOBE -> {
            // 地球儀: 円に経線と緯線
            drawCircle(col, radius = 27f * u, center = pt(0f, 0f), style = st)
            drawOval(col, topLeft = pt(-13f, -27f), size = Size(26f * u, 54f * u), style = st)
            drawOval(col, topLeft = pt(-27f, -10f), size = Size(54f * u, 20f * u), style = st)
            drawLine(col, pt(0f, -27f), pt(0f, 27f), strokeWidth = 2.2f * u, cap = StrokeCap.Round)
        }
        LyricIcon.BUBBLE -> {
            // 夢の吹き出し: 角丸の四角にしっぽ
            drawRoundRect(col, topLeft = pt(-28f, -25f), size = Size(56f * u, 40f * u), cornerRadius = CornerRadius(11f * u, 11f * u), style = st)
            drawLine(col, pt(-12f, 14f), pt(-19f, 30f), strokeWidth = 2.8f * u, cap = StrokeCap.Round)
            drawLine(col, pt(3f, 14f), pt(-19f, 30f), strokeWidth = 2.8f * u, cap = StrokeCap.Round)
        }
        LyricIcon.WIND -> {
            val path = Path().apply {
                moveTo(cx - 30f * u, cy - 13f * u)
                lineTo(cx + 6f * u, cy - 13f * u)
                quadraticBezierTo(cx + 27f * u, cy - 13f * u, cx + 25f * u, cy - 24f * u)
                quadraticBezierTo(cx + 23f * u, cy - 32f * u, cx + 15f * u, cy - 28f * u)
                moveTo(cx - 30f * u, cy + 2f * u)
                lineTo(cx + 14f * u, cy + 2f * u)
                quadraticBezierTo(cx + 31f * u, cy + 2f * u, cx + 29f * u, cy - 7f * u)
                quadraticBezierTo(cx + 27f * u, cy - 14f * u, cx + 20f * u, cy - 11f * u)
                moveTo(cx - 30f * u, cy + 17f * u)
                lineTo(cx + 2f * u, cy + 17f * u)
            }
            drawPath(path, col, style = st)
        }
    }
}

/** 4方向に尖ったキラキラ（スパークル）を塗りで描く */
private fun DrawScope.drawSparkle(cx: Float, cy: Float, r: Float, color: Color, alpha: Float) {
    val path = Path().apply {
        moveTo(cx, cy - r)
        quadraticBezierTo(cx, cy, cx + r, cy)
        quadraticBezierTo(cx, cy, cx, cy + r)
        quadraticBezierTo(cx, cy, cx - r, cy)
        quadraticBezierTo(cx, cy, cx, cy - r)
        close()
    }
    drawPath(path, color.copy(alpha = alpha))
}

/** 歌詞の単語に反応する全画面エフェクト（0=雨、1=雪、2=桜）。PVレイヤーの上に重ねる */
@Composable
private fun PvAmbientEffect(kind: Int, modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "pvambient")
    val duration = when (kind) { 0 -> 1100; 1 -> 9000; else -> 12000 }
    val t by transition.animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(duration, easing = LinearEasing), RepeatMode.Restart),
        label = "pvambientT"
    )
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        fun frac(v: Float) = v - floor(v)
        when (kind) {
            0 -> {
                // 雨: 細い線がたくさん斜めに落ちてくる
                val col = Color.White.copy(alpha = 0.30f)
                for (i in 0 until 46) {
                    val speed = 0.85f + frac(i * 0.371f) * 0.8f
                    val x = frac(i * 0.618034f) * w + frac(i * 0.113f) * 30f
                    val y = frac(frac(i * 0.529f) + t * speed) * (h + 160f) - 80f
                    val len = 46f + frac(i * 0.77f) * 54f
                    drawLine(col, Offset(x, y), Offset(x - len * 0.16f, y + len), strokeWidth = 2.4f, cap = StrokeCap.Round)
                }
            }
            1 -> {
                // 雪: 白い点がゆっくり舞い落ちる
                val col = Color.White.copy(alpha = 0.55f)
                for (i in 0 until 30) {
                    val speed = 0.55f + frac(i * 0.371f) * 0.6f
                    val x = frac(i * 0.618034f) * w + sin(t * 6.2832f + i * 1.7f) * 16f
                    val y = frac(frac(i * 0.529f) + t * speed) * (h + 80f) - 40f
                    drawCircle(col, radius = 2.2f + frac(i * 0.77f) * 3.4f, center = Offset(x, y))
                }
            }
            else -> {
                // 桜: 花びらがひらひら回りながら落ちてくる
                val col = Color(0xFFF7B7C8)
                for (i in 0 until 14) {
                    val speed = 0.5f + frac(i * 0.371f) * 0.55f
                    val x = frac(i * 0.618034f) * w + sin(t * 6.2832f + i * 2.3f) * 34f
                    val y = frac(frac(i * 0.529f) + t * speed) * (h + 120f) - 60f
                    val petal = 9f + frac(i * 0.77f) * 7f
                    val rot = t * 360f * (if (i % 2 == 0) 1f else -1f) + i * 47f
                    val rad = Math.toRadians(rot.toDouble())
                    val cs = kotlin.math.cos(rad).toFloat()
                    val sn = kotlin.math.sin(rad).toFloat()
                    fun rp(lx: Float, ly: Float) = Offset(x + lx * cs - ly * sn, y + lx * sn + ly * cs)
                    val p0 = rp(0f, -petal)
                    val p1 = rp(petal * 0.95f, -petal * 0.15f)
                    val p2 = rp(0f, petal)
                    val p3 = rp(-petal * 0.95f, -petal * 0.15f)
                    val path = Path().apply {
                        moveTo(p0.x, p0.y)
                        quadraticBezierTo(p1.x, p1.y, p2.x, p2.y)
                        quadraticBezierTo(p3.x, p3.y, p0.x, p0.y)
                        close()
                    }
                    drawPath(path, col.copy(alpha = 0.8f))
                }
            }
        }
    }
}

/** アイコン対象から外す複合語（「心配」の「心」でハートが出ないように） */
private val LYRIC_ICON_EXCLUSIONS: Map<String, List<String>> = mapOf(
    "心" to listOf("心配"),
    "山" to listOf("沢山")
)

/** 行の中からアイコン対象の単語を探す。除外語の一部として現れる箇所は数えない */
private fun findIconWord(text: String): Pair<String, Int>? {
    for ((word, kind) in LYRIC_ICON_WORDS) {
        val exclusions = LYRIC_ICON_EXCLUSIONS[word] ?: emptyList()
        var from = 0
        while (true) {
            val i = text.indexOf(word, from)
            if (i < 0) break
            if (exclusions.any { text.startsWith(it, i) }) {
                from = i + 1
                continue
            }
            return word to kind
        }
    }
    return null
}

/** 折り返さずに1行で収まる最大の文字サイズを返す（なるべく大きく、収まらなければ下げる） */
private fun fitFontSizeSp(text: String, baseSp: Float, availWidthDp: Float): Float {
    if (text.isEmpty() || availWidthDp <= 0f) return baseSp
    var em = 0f
    for (ch in text) {
        em += when {
            ch == ' ' || ch == '　' -> 0.30f
            ch.code < 0x300 -> 0.55f
            else -> 1.02f
        }
    }
    if (em <= 0f) return baseSp
    return minOf(baseSp, availWidthDp * 0.95f / em).coerceAtLeast(8f)
}

/** コーラスの掛け声（yeah / ah / ラララ等）かどうか */
private val CHORUS_REGEX = Regex("^(yeah|yea|ah|oh|ooh|wow|la|na|hey|uh|yay|hoo|ha|mm|hmm)+$")

private fun isChorusWord(text: String): Boolean {
    val t = text.trim().lowercase().trim('!', '?', ',', '.', '~', '〜', '・')
    if (t.isEmpty() || !t.all { it in 'a'..'z' }) return false
    return CHORUS_REGEX.matches(t)
}

/** 色名が出てきたら、その文字をその色で塗るための対応表（長い語を先に判定） */
private val COLOR_WORDS: List<Pair<String, Color>> = listOf(
    "水色" to Color(0xFF7FD4F2), "ピンク" to Color(0xFFFF8FBF), "オレンジ" to Color(0xFFFF9A3D),
    "グレー" to Color(0xFF9AA0A6),
    "青" to Color(0xFF3D7BFF), "赤" to Color(0xFFFF4545), "白" to Color(0xFFF2F3F5),
    "黒" to Color(0xFF26262C), "紫" to Color(0xFFB07FE8), "緑" to Color(0xFF4FC96B),
    "黄" to Color(0xFFFFD94D), "金" to Color(0xFFFFC94D), "銀" to Color(0xFFC9CED6),
    "茶" to Color(0xFFB07B4F), "灰" to Color(0xFF9AA0A6)
)

/** 間奏中に出す、曲線がいろんな方向に交差するビジュアルアート */
@Composable
private fun PvInterludeArt(accentColor: Color, modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "pvart")
    val t by transition.animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(16000, easing = LinearEasing), RepeatMode.Restart),
        label = "pvartT"
    )
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val ph = t * 6.2832f
        for (i in 0 until 7) {
            val seed = i * 1.618f
            val yBase = h * (0.16f + 0.12f * i)
            val amp = h * (0.16f + 0.05f * (i % 3))
            val colr = if (i % 3 == 0) Color.White.copy(alpha = 0.30f) else accentColor.copy(alpha = 0.38f)
            val path = Path().apply {
                moveTo(-w * 0.05f, yBase + sin(ph + seed) * h * 0.05f)
                cubicTo(
                    w * 0.30f, yBase - amp * sin(ph * 0.9f + seed * 2f),
                    w * 0.65f, yBase + amp * kotlin.math.cos(ph * 0.8f + seed),
                    w * 1.05f, yBase - sin(ph + seed * 3f) * h * 0.06f
                )
            }
            drawPath(path, colr, style = Stroke(width = 2.2f + (i % 3) * 0.9f, cap = StrokeCap.Round))
        }
        for (i in 0 until 3) {
            val cxp = w * (0.25f + 0.25f * i) + sin(ph * 0.7f + i * 2.1f) * w * 0.03f
            val cyp = h * (0.35f + 0.15f * i) + kotlin.math.cos(ph * 0.6f + i * 1.3f) * h * 0.05f
            drawCircle(
                color = Color.White.copy(alpha = 0.22f),
                radius = h * (0.10f + 0.04f * i) * (0.9f + 0.1f * sin(ph + i)),
                center = Offset(cxp, cyp),
                style = Stroke(width = 1.8f)
            )
        }
    }
}

/** PV入場の8方向（上・右上・右・右下・下・左下・左・左上） */
/** PVでカタカナ語の下に小さく添える英単語（歌詞でよく出る語だけの内蔵対応表） */
private val KATAKANA_EN = mapOf(
    "ハート" to "HEART", "ラブ" to "LOVE", "キス" to "KISS", "ドリーム" to "DREAM",
    "ライト" to "LIGHT", "ナイト" to "NIGHT", "スター" to "STAR", "ムーン" to "MOON",
    "スカイ" to "SKY", "ミュージック" to "MUSIC", "ソング" to "SONG", "ダンス" to "DANCE",
    "ロック" to "ROCK", "ポップ" to "POP", "ビート" to "BEAT", "リズム" to "RHYTHM",
    "メロディ" to "MELODY", "ハーモニー" to "HARMONY", "ボイス" to "VOICE", "サウンド" to "SOUND",
    "ワールド" to "WORLD", "ライフ" to "LIFE", "タイム" to "TIME", "モーメント" to "MOMENT",
    "メモリー" to "MEMORY", "ストーリー" to "STORY", "フューチャー" to "FUTURE", "プロミス" to "PROMISE",
    "シークレット" to "SECRET", "ミステリー" to "MYSTERY", "マジック" to "MAGIC", "パワー" to "POWER",
    "エナジー" to "ENERGY", "ファイア" to "FIRE", "ウォーター" to "WATER", "レイン" to "RAIN",
    "スノー" to "SNOW", "フラワー" to "FLOWER", "バタフライ" to "BUTTERFLY", "エンジェル" to "ANGEL",
    "デビル" to "DEVIL", "ゴースト" to "GHOST", "シャドウ" to "SHADOW", "シャイン" to "SHINE",
    "ダーク" to "DARK", "カラー" to "COLOR", "ブルー" to "BLUE", "ピンク" to "PINK",
    "ブラック" to "BLACK", "ホワイト" to "WHITE", "ゴールド" to "GOLD", "シルバー" to "SILVER",
    "クリスタル" to "CRYSTAL", "ダイヤモンド" to "DIAMOND", "プラズマ" to "PLASMA", "ブラックアウト" to "BLACKOUT",
    "テレビ" to "TV", "ラジオ" to "RADIO", "ゲーム" to "GAME", "ヒーロー" to "HERO",
    "クイーン" to "QUEEN", "プリンセス" to "PRINCESS", "ベイビー" to "BABY", "ダーリン" to "DARLING",
    "ハニー" to "HONEY", "フレンド" to "FRIEND", "ラバー" to "LOVER", "エンド" to "END",
    "スタート" to "START", "ゴール" to "GOAL", "ドア" to "DOOR", "キー" to "KEY",
    "ロード" to "ROAD", "シティ" to "CITY", "ホーム" to "HOME", "ミラー" to "MIRROR",
    "スマイル" to "SMILE", "ソウル" to "SOUL", "マインド" to "MIND", "ステージ" to "STAGE",
    "ライブ" to "LIVE", "パーティ" to "PARTY", "カフェ" to "CAFE", "ホテル" to "HOTEL",
    "レター" to "LETTER", "カード" to "CARD", "チケット" to "TICKET", "ドレス" to "DRESS",
    "リング" to "RING", "ブック" to "BOOK", "シーズン" to "SEASON", "サマー" to "SUMMER",
    "ウィンター" to "WINTER", "モーニング" to "MORNING", "ミッドナイト" to "MIDNIGHT", "トゥモロー" to "TOMORROW",
    "イエスタデイ" to "YESTERDAY", "フォーエバー" to "FOREVER", "パラダイス" to "PARADISE", "ヘブン" to "HEAVEN",
    "アース" to "EARTH", "オーシャン" to "OCEAN", "リバー" to "RIVER", "フォレスト" to "FOREST"
)

private val PV_ENTRY_DIRS = listOf(
    0f to -1f, 0.72f to -0.72f, 1f to 0f, 0.72f to 0.72f,
    0f to 1f, -0.72f to 0.72f, -1f to 0f, -0.72f to -0.72f
)

@Composable
private fun PvLyricLine(
    text: String,
    baseSeed: Int,
    fontFamily: FontFamily?,
    accentColor: Color,
    effectKind: Int = 0,
    bold: Boolean = true,
    bigText: Boolean = false,
    lineDurationMs: Long = Long.MAX_VALUE,
    screenWidthDp: Float,
    exiting: Boolean,
    modifier: Modifier = Modifier
) {
    val pvEaseIn = CubicBezierEasing(0.42f, 0f, 1f, 1f)
    // 改行位置は塊（文節・英単語）の境界で決める。描画とアニメーションは文字ごと（英単語だけ単語ごと）
    val iconWord = findIconWord(text)
    val chunks = remember(text) { splitLyricUnits(text) }
    if (chunks.isEmpty()) return
    fun isLatinWord(c: String): Boolean = c.isNotEmpty() && c.all { it.code < 0x300 }
    fun charEm(ch: Char): Float = when {
        ch in "ぁぃぅぇぉっゃゅょゎァィゥェォッャュョヮヵヶ" -> 0.6f
        ch in '一'..'鿿' || ch == '々' || ch == '〆' || ch == 'ヶ' -> 1.25f
        ch in 'ぁ'..'ゟ' -> 0.82f
        ch in '゠'..'ヿ' -> 1.05f
        ch.code < 0x300 -> 0.6f
        else -> 0.98f
    }
    fun chunkEm(c: String): Float = c.fold(0f) { acc, ch -> acc + charEm(ch) * 1.02f }
    val latinDominant = text.count { it.code in 0x41..0x7A } > text.count { it.code >= 0x300 }
    val gapEm = if (latinDominant) 0.32f else 0f
    // 基本2行構成: 塊の境界の中で、左右のemが一番均等になる位置で割る
    // ただし「」や（）で囲まれた部分は丸ごと1行で表示する（閉じ括弧がこの行に無い場合は通常扱い）
    val totalChars = text.count { it != ' ' && it != '　' }
    fun splitChunks(list: List<String>): List<List<String>> {
        if (list.size >= 2 && list.sumOf { it.count { ch -> ch != ' ' && ch != '　' } } >= 6) {
            val totalEm = list.sumOf { chunkEm(it).toDouble() }.toFloat() + gapEm * (list.size - 1)
            var acc = 0f
            var best = 1
            var bestDiff = Float.MAX_VALUE
            for (idx in 1 until list.size) {
                acc += chunkEm(list[idx - 1]) + gapEm
                val diff = kotlin.math.abs(totalEm / 2f - acc)
                if (diff < bestDiff) { bestDiff = diff; best = idx }
            }
            return listOf(list.subList(0, best), list.subList(best, list.size))
        }
        return listOf(list)
    }
    // 括弧セグメントの検出（開き括弧から閉じ括弧までを1つの塊にする）
    val segs = mutableListOf<Pair<Boolean, List<String>>>()
    var bracketBuf = mutableListOf<String>()
    var inBracket = false
    for (c in chunks) {
        if (!inBracket && (c.contains("（") || c.contains("(") || c.contains("「"))) {
            inBracket = true
            bracketBuf = mutableListOf(c)
            if (c.contains("）") || c.contains(")") || c.contains("」")) {
                segs.add(true to bracketBuf.toList())
                inBracket = false
            }
        } else if (inBracket) {
            bracketBuf.add(c)
            if (c.contains("）") || c.contains(")") || c.contains("」")) {
                segs.add(true to bracketBuf.toList())
                inBracket = false
            }
        } else {
            segs.add(false to listOf(c))
        }
    }
    if (inBracket) {
        // 閉じ括弧がこの行に無い → 括弧扱いをやめて通常の塊として並べ直す
        for (c in bracketBuf) segs.add(false to listOf(c))
    }
    val rowsBuilt = mutableListOf<List<String>>()
    var runBuf = mutableListOf<String>()
    fun flushRun() {
        if (runBuf.isNotEmpty()) {
            rowsBuilt.addAll(splitChunks(runBuf.toList()))
            runBuf = mutableListOf()
        }
    }
    for ((isBracket, segChunks) in segs) {
        if (isBracket) {
            flushRun()
            rowsBuilt.add(segChunks)
        } else {
            runBuf.addAll(segChunks)
        }
    }
    flushRun()
    // 改行は最大3行まで。括弧を1行にすると3行を超える場合は、括弧ルールを無視して通常分割にする
    val rows: List<List<String>> = when {
        rowsBuilt.isEmpty() -> listOf(chunks)
        rowsBuilt.size > 3 -> splitChunks(chunks)
        else -> rowsBuilt
    }
    fun rowEm(row: List<String>): Float = row.sumOf { chunkEm(it).toDouble() }.toFloat() + gapEm * (row.size - 1).coerceAtLeast(0)
    val longestRowEm = rows.maxOf { rowEm(it) }.coerceAtLeast(1f)
    // 行ごとの大きさを均等に: どの行も「枠いっぱいに広がるサイズ」を基本にし、短い行だけ高さ上限で止める
    val heightCapBase = when {
        rows.size >= 3 -> 40f
        rows.size == 2 -> 52f
        else -> 64f
    }
    val heightCap = if (bigText) heightCapBase * 1.8f else heightCapBase
    val fitSize = minOf((screenWidthDp - 8f) / (longestRowEm * 1.06f), heightCap)
    val lineOffsetX = ((baseSeed / 7) % 17 - 8).dp
    val lineOffsetY = ((baseSeed / 11) % 13 - 6).dp
    val row2Indent = ((baseSeed / 13) % 4 * 14).dp
    // 退場は1本の時計（exitProgress）で全文字を駆動する（位相だけ文字ごとにずらす）
    val exitProgress = remember { Animatable(0f) }
    if (exiting) {
        LaunchedEffect(Unit) {
            exitProgress.animateTo(1f, animationSpec = tween(durationMillis = 720, easing = LinearEasing))
        }
    }
    // 行の表示時間が極端に短いときは、装飾エフェクトを出す暇がないので歌詞の表示を優先する
    val effKind = when {
        lineDurationMs < 1400L -> 0
        text.contains("！") || text.contains("!") -> 4
        text.contains("円") -> 1
        text.contains("四角形") -> 10
        text.contains("三角形") -> 9
        text.contains("線") -> 8
        else -> effectKind
    }
    // 装飾エフェクト（親から渡される。前の行と被らないランダム）:
    // 0=下線スイープ / 1=波紋 / 2=白スワイプ / 3=点滅 / 4=グリッチ / 5=枠線 / 6=粒子バースト / 7=波打ち登場
    val barAnim = remember { Animatable(0f) }
    val rippleAnim = remember { Animatable(0f) }
    val fxAnim = remember { Animatable(0f) }
    if (!exiting) {
        LaunchedEffect(Unit) {
            barAnim.animateTo(1f, animationSpec = tween(durationMillis = 190, easing = FastOutSlowInEasing))
            delay(60)
            barAnim.animateTo(2f, animationSpec = tween(durationMillis = 220, easing = FastOutSlowInEasing))
            rippleAnim.animateTo(1f, animationSpec = tween(durationMillis = 1150, easing = LinearEasing))
        }
        LaunchedEffect(Unit) {
            fxAnim.animateTo(1f, animationSpec = tween(durationMillis = 1000, easing = LinearEasing))
        }
    }
    // 下線: 入場と同時に、左から右へ伸びて（イーズアウト）、すぐに左から右へ消える（イーズイン）
    val underline = remember { Animatable(0f) }
    if (!exiting) {
        LaunchedEffect(Unit) {
            underline.animateTo(1f, animationSpec = tween(durationMillis = 320, easing = FastOutSlowInEasing))
            underline.animateTo(2f, animationSpec = tween(durationMillis = 320, easing = pvEaseIn))
        }
    }
    Box(
        modifier = modifier
            .fillMaxWidth()
            .offset(x = lineOffsetX, y = lineOffsetY)
            .graphicsLayer {
                // 退場の最後は行ごとフェードアウトして残像を残さない
                alpha = if (exiting) 1f - ((exitProgress.value - 0.6f) / 0.4f).coerceIn(0f, 1f) else 1f
            }
    ) {
        if (effKind == 1) {
            Canvas(modifier = Modifier.matchParentSize()) {
                val t = rippleAnim.value
                if (t > 0f && t < 1f) {
                    for (k in 0 until 3) {
                        val local = (t * 1.35f - k * 0.16f).coerceIn(0f, 1f)
                        if (local > 0f) {
                            val cx = size.width * (0.24f + 0.26f * k) + ((baseSeed / (k + 3)) % 40 - 20).toFloat()
                            val cy = size.height * (0.32f + 0.18f * ((baseSeed / (k + 5)) % 3))
                            drawCircle(
                                color = accentColor.copy(alpha = (1f - local) * 0.28f),
                                radius = local * size.height * 1.45f,
                                center = Offset(cx, cy),
                                style = Stroke(width = 3.5f)
                            )
                        }
                    }
                }
            }
        }
        if (effKind == 9 || effKind == 10) {
            // 図形の波紋: 三角形・四角形の輪が広がる
            Canvas(modifier = Modifier.matchParentSize()) {
                val t = rippleAnim.value
                if (t > 0f && t < 1f) {
                    val cx = size.width / 2f
                    val cy = size.height / 2f
                    for (k in 0 until 3) {
                        val lt = ((t * 1.25f) - k * 0.18f).coerceIn(0f, 1f)
                        if (lt <= 0f) continue
                        val r = lt * size.width * 0.42f
                        val alpha = (1f - lt) * 0.28f
                        if (effKind == 9) {
                            val path = Path().apply {
                                moveTo(cx, cy - r)
                                lineTo(cx + r * 0.87f, cy + r * 0.5f)
                                lineTo(cx - r * 0.87f, cy + r * 0.5f)
                                close()
                            }
                            drawPath(path, accentColor.copy(alpha = alpha), style = Stroke(width = 3.5f))
                        } else {
                            drawRect(accentColor.copy(alpha = alpha), topLeft = Offset(cx - r, cy - r), size = Size(r * 2f, r * 2f), style = Stroke(width = 3.5f))
                        }
                    }
                }
            }
        }
        if (effKind == 8) {
            // 線エフェクト: アクセント色の横線が3本、左から右へ伸びて消える
            Canvas(modifier = Modifier.matchParentSize()) {
                val t = fxAnim.value
                if (t > 0f && t < 1f) {
                    for (k in 0 until 3) {
                        val lt = ((t * 1.4f) - k * 0.16f).coerceIn(0f, 1f)
                        if (lt <= 0f) continue
                        val y = size.height * (0.22f + 0.28f * k)
                        drawLine(
                            color = accentColor.copy(alpha = sin(lt * 3.14159f) * 0.5f),
                            start = Offset(0f, y),
                            end = Offset(size.width * lt, y),
                            strokeWidth = 2.5f,
                            cap = StrokeCap.Round
                        )
                    }
                }
            }
        }
        if (effKind == 5 && !exiting) {
            // 枠線: 行を囲む角丸の枠がふわっと現れて消える
            val ft = fxAnim.value
            if (ft > 0f && ft < 1f) {
                Canvas(modifier = Modifier.matchParentSize()) {
                    val inset = (1f - ft) * size.width * 0.06f + 6f
                    drawRoundRect(
                        color = accentColor.copy(alpha = sin(ft * 3.14159f) * 0.5f),
                        topLeft = Offset(inset, inset * 0.6f),
                        size = Size(size.width - inset * 2f, size.height - inset * 1.2f),
                        cornerRadius = CornerRadius(12.dp.toPx(), 12.dp.toPx()),
                        style = Stroke(width = 3f)
                    )
                }
            }
        }
        if (effKind == 6 && !exiting) {
            // 粒子バースト: 行の中心からキラキラと粒が飛び散る
            val ft = fxAnim.value
            if (ft > 0f && ft < 1f) {
                Canvas(modifier = Modifier.matchParentSize()) {
                    val pe = 1f - (1f - ft) * (1f - ft) * (1f - ft)
                    val bcx = size.width / 2f
                    val bcy = size.height / 2f
                    for (i in 0 until 12) {
                        val ang = (i * 137.5f + baseSeed * 7f) * 0.0174533f
                        val dist = pe * size.width * (0.16f + (i % 4) * 0.05f)
                        val px = bcx + kotlin.math.cos(ang.toDouble()).toFloat() * dist
                        val py = bcy + kotlin.math.sin(ang.toDouble()).toFloat() * dist * 0.6f
                        val pa = (1f - ft) * 0.8f
                        if (i % 3 == 0) {
                            drawSparkle(px, py, (1f - ft * 0.5f) * 13f, accentColor, pa)
                        } else {
                            drawCircle(color = accentColor.copy(alpha = pa), radius = 3.2f, center = Offset(px, py))
                        }
                    }
                }
            }
        }
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxWidth()
        ) {
        var globalIndex = 0
        var iconPlaced = false
        var inParen = false
        rows.forEachIndexed { rowIdx, rowChunks ->
        val unitsInRow = rowChunks.sumOf { renderUnitsOf(it).size }
        var rowUnitIdx = 0
        Box {
        Row(
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
            modifier = if (rowIdx > 0) Modifier.padding(start = row2Indent, top = 0.dp) else Modifier
        ) {
            rowChunks.forEachIndexed { posInRow, chunk ->
                if (posInRow > 0 && latinDominant) {
                    Spacer(Modifier.width((fitSize * 0.32f).dp))
                }
                // コーラス部分（括弧の中・yeah等の掛け声）は薄く表示する
                val chunkDim = inParen || chunk.startsWith("（") || chunk.startsWith("(") || isChorusWord(chunk)
                if (chunk.contains("（") || chunk.contains("(")) inParen = true
                if (chunk.contains("）") || chunk.contains(")")) inParen = false
                val chunkIconKind = if (!iconPlaced && iconWord != null && chunk.contains(iconWord.first)) iconWord.second else null
                var unitIdxInChunk = 0
                for (ru in renderUnitsOf(chunk)) {
                    val gi = globalIndex++
                    val thisUnitIcon = if (chunkIconKind != null && unitIdxInChunk == 0) chunkIconKind else null
                    if (thisUnitIcon != null) iconPlaced = true
                    unitIdxInChunk++
                    val rIdx = rowUnitIdx++
                    // 文字同士がほんの少し被るくらいに、行の中心へ向けて詰める（画面外へのはみ出しは増やさない）
                    val uXShift = ((unitsInRow - 1) / 2f - rIdx) * fitSize * 0.05f
                    val uSeed = Math.floorMod(baseSeed * 7 + gi * 131, 997)
                    val charFactor = when {
                        ru.length > 1 && ru.all { it in '゠'..'ヿ' } -> 1.05f // カタカナ単語
                        ru.length > 1 -> 0.98f // 英単語
                        ru[0] in "ぁぃぅぇぉっゃゅょゎァィゥェォッャュョヮヵヶ" -> 1.0f // 小カナの縮小はPvLyricUnit内のスパンで1回だけ行う
                        ru[0] in '一'..'鿿' || ru[0] == '々' || ru[0] == '〆' || ru[0] == 'ヶ' -> 1.25f
                        ru[0] in 'ぁ'..'ゟ' -> 0.82f
                        ru[0] in '゠'..'ヿ' -> 1.05f
                        else -> 0.98f
                    }
                    PvLyricUnit(
                        bold = bold,
                        unitText = ru,
                        uSize = (fitSize * (0.92f + (uSeed % 22) / 100f) * charFactor).coerceIn(fitSize * 0.55f, fitSize * 1.35f).sp,
                        uRot = ((uSeed / 3) % 29 - 14).toFloat(),
                        uYOffset = ((uSeed / 5) % 17 - 8).dp,
                        uXShiftDp = uXShift,
                        staggerIndex = gi,
                        exiting = exiting,
                        exitProgressValue = exitProgress.value,
                        exitDelayMs = (uSeed % 5) * 35f + gi * 12f,
                        fontFamily = fontFamily,
                        accentColor = accentColor,
                        effectKind = effKind,
                        iconKind = thisUnitIcon,
                        dimmed = chunkDim
                    )
                }
            }
        }
            if (effKind == 2) {
                // 白い四角が文字の上を左から右へしゅっと塗り、そのまま右へしゅっと消える
                val bv = barAnim.value
                if (bv > 0f && bv < 2f) {
                    Canvas(modifier = Modifier.matchParentSize()) {
                        val frac = if (bv <= 1f) bv else 2f - bv
                        val xOff = if (bv <= 1f) 0f else (bv - 1f) * size.width
                        // 文字ギリギリのサイズの、角丸なしの白い四角（点滅しながら塗る）
                        val blink = if (sin(bv * 9.42f) > 0f) 1f else 0.30f
                        drawRect(
                            color = Color.White.copy(alpha = blink),
                            topLeft = Offset(xOff, 0f),
                            size = Size(frac * size.width, size.height)
                        )
                    }
                }
            }
        }
        }
        }
    }
}

/** PVの1描画単位（1文字または英単語）。入場はスタッガード、退場は共通の時計から位相計算する */
@Composable
private fun PvLyricUnit(
    unitText: String,
    uSize: androidx.compose.ui.unit.TextUnit,
    uRot: Float,
    uYOffset: androidx.compose.ui.unit.Dp,
    uXShiftDp: Float = 0f,
    staggerIndex: Int,
    exiting: Boolean,
    exitProgressValue: Float,
    exitDelayMs: Float,
    fontFamily: FontFamily?,
    accentColor: Color,
    effectKind: Int = 0,
    iconKind: Int? = null,
    dimmed: Boolean = false,
    bold: Boolean = true
) {
    val pvEaseIn = CubicBezierEasing(0.42f, 0f, 1f, 1f)
    val uSeed = staggerIndex
    val uAnim = remember(staggerIndex) { Animatable(0f) }
    // 表示中ずっと、位置がごく小さくぐらぐら揺れる（角度は変えない）
    val sway = remember(staggerIndex) { Animatable(0f) }
    if (!exiting) {
        LaunchedEffect(staggerIndex) {
            delay(staggerIndex * 45L)
            uAnim.animateTo(1f, animationSpec = tween(durationMillis = 380, easing = FastOutSlowInEasing))
        }
        LaunchedEffect(staggerIndex) {
            sway.animateTo(
                1f,
                animationSpec = infiniteRepeatable(
                    animation = tween(durationMillis = 2400 + (uSeed % 5) * 380, easing = LinearEasing),
                    repeatMode = RepeatMode.Restart
                )
            )
        }
    }
    val annotatedUnit = remember(unitText, uSize) {
        buildAnnotatedString {
            for (ch in unitText) {
                if (ch in "ぁぃぅぇぉっゃゅょゎァィゥェォッャュョヮヵヶ") {
                    withStyle(SpanStyle(fontSize = uSize * 0.6f)) { append(ch) }
                } else {
                    append(ch)
                }
            }
        }
    }
    // ユニットごとの下線: 入場に合わせて左から伸びて、左から消える
    val ulAnim = remember(staggerIndex) { Animatable(0f) }
    if (!exiting) {
        LaunchedEffect(staggerIndex) {
            delay(staggerIndex * 45L)
            ulAnim.animateTo(1f, animationSpec = tween(durationMillis = 260, easing = FastOutSlowInEasing))
            ulAnim.animateTo(2f, animationSpec = tween(durationMillis = 260, easing = pvEaseIn))
        }
    }
    // 色名の文字を、その色で左上から右下へ斜めに塗っていく
    val colorHit = if (!exiting) COLOR_WORDS.firstOrNull { unitText.contains(it.first) } else null
    val wipeT = remember { Animatable(0f) }
    if (colorHit != null) {
        LaunchedEffect(staggerIndex) {
            delay(staggerIndex * 45L + 200L)
            wipeT.animateTo(1f, animationSpec = tween(durationMillis = 650, easing = LinearEasing))
        }
    }
    // 点滅(3)とグリッチ(4)用の進行度
    val fxT = remember { Animatable(0f) }
    if (!exiting && (effectKind == 3 || effectKind == 4)) {
        LaunchedEffect(staggerIndex) {
            delay(staggerIndex * 45L)
            fxT.animateTo(1f, animationSpec = tween(durationMillis = 800, easing = LinearEasing))
        }
    }
    Box(
        modifier = Modifier
            .width(IntrinsicSize.Max)
            .offset(x = uXShiftDp.dp, y = uYOffset)
            .graphicsLayer {
                if (exiting) {
                    val localX = ((exitProgressValue * 720f - exitDelayMs) / 320f).coerceIn(0f, 1f)
                    val a = 1f - pvEaseIn.transform(localX)
                    alpha = a
                    val es = 0.5f + 0.5f * a
                    scaleX = es
                    scaleY = es
                    rotationZ = uRot * (2f - a)
                    translationY = (1f - a) * 30f
                } else {
                    val a = uAnim.value
                    val blinkF = if (effectKind == 3 && fxT.value < 1f) {
                        if (sin(fxT.value * 21.99f + uSeed * 1.3f) > -0.2f) 1f else 0.12f
                    } else 1f
                    alpha = a * blinkF
                    val ns = 0.6f + 0.4f * a
                    scaleX = ns
                    scaleY = ns
                    rotationZ = uRot * a
                    // 入場は単位ごとにバラバラの方向（8方向）から飛んでくる
                    val dir = PV_ENTRY_DIRS[Math.floorMod(uSeed * 131 + 7, 8)]
                    // 波に乗るような揺れ: サイン波でなめらかに、位相は文字ごとにずらす
                    val ph = sway.value * 6.2832f + (uSeed % 7) * 0.9f
                    val swx = sin(ph) * 3.4f
                    val swy = sin(ph + 1.3f) * 2.6f
                    translationX = dir.first * 52f * (1f - a) + swx * a + if (effectKind == 4 && fxT.value < 1f) sin(fxT.value * 55f + uSeed * 2.7f) * 5f * (1f - fxT.value) else 0f
                    translationY = dir.second * 52f * (1f - a) + swy * a + if (effectKind == 7) sin(a * 6.2832f + uSeed * 0.8f) * 15f * (1f - a) else 0f
                }
            }
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box {
            if (!exiting && effectKind == 4 && fxT.value < 1f) {
                val flick = (0.35f + 0.3f * sin(fxT.value * 47f + uSeed * 3.1f)).coerceIn(0.1f, 0.65f)
                Text(
                    text = annotatedUnit,
                    color = accentColor.copy(alpha = flick),
                    fontSize = uSize,
                    fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal,
                    textAlign = TextAlign.Center,
                    overflow = TextOverflow.Visible,
                    style = LocalTextStyle.current.copy(fontFamily = fontFamily, lineHeight = uSize * 1.02f),
                    modifier = Modifier.offset(x = 3.5.dp)
                )
                Text(
                    text = annotatedUnit,
                    color = accentColor.copy(alpha = flick * 0.55f),
                    fontSize = uSize,
                    fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal,
                    textAlign = TextAlign.Center,
                    overflow = TextOverflow.Visible,
                    style = LocalTextStyle.current.copy(fontFamily = fontFamily, lineHeight = uSize * 1.02f),
                    modifier = Modifier.offset(x = (-3.5).dp)
                )
            }
            Text(
                text = annotatedUnit,
            color = Color.White.copy(alpha = if (dimmed) 0.40f else 0.92f),
            fontSize = uSize,
            fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal,
            textAlign = TextAlign.Center,
            overflow = TextOverflow.Visible,
            style = LocalTextStyle.current.copy(
                fontFamily = fontFamily,
                lineHeight = uSize * 1.02f,
                    shadow = Shadow(color = accentColor.copy(alpha = if (dimmed) 0.35f else 0.85f), blurRadius = 22f)
                )
            )
            if (colorHit != null) {
                val cWord = colorHit.first
                // 原色そのままではなく、アクセントカラーに色相を寄せて白で薄めた淡い色にする
                val base = colorHit.second
                val cColor = Color(
                    red = (base.red * 0.55f + accentColor.red * 0.45f) * 0.55f + 0.45f,
                    green = (base.green * 0.55f + accentColor.green * 0.45f) * 0.55f + 0.45f,
                    blue = (base.blue * 0.55f + accentColor.blue * 0.45f) * 0.55f + 0.45f
                )
                val cStart = unitText.indexOf(cWord)
                val wp = wipeT.value.coerceIn(0f, 1f)
                val wipeBrush = Brush.linearGradient(
                    colorStops = arrayOf(
                        0f to cColor,
                        wp to cColor,
                        (wp + 0.03f).coerceIn(0f, 1f) to Color.Transparent,
                        1f to Color.Transparent
                    ),
                    start = Offset.Zero,
                    end = Offset(Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY)
                )
                val overlayText = buildAnnotatedString {
                    unitText.forEachIndexed { ci, ch ->
                        val inWord = ci >= cStart && ci < cStart + cWord.length
                        val span = if (inWord) SpanStyle(brush = wipeBrush) else SpanStyle(color = Color.Transparent)
                        if (ch in "ぁぃぅぇぉっゃゅょゎァィゥェォッャュョヮヵヶ") {
                            withStyle(SpanStyle(fontSize = uSize * 0.6f)) {
                                withStyle(span) { append(ch) }
                            }
                        } else {
                            withStyle(span) { append(ch) }
                        }
                    }
                }
                Text(
                    text = overlayText,
                    fontSize = uSize,
                    fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal,
                    textAlign = TextAlign.Center,
                    overflow = TextOverflow.Visible,
                    style = LocalTextStyle.current.copy(
                        fontFamily = fontFamily,
                        lineHeight = uSize * 1.02f
                    )
                )
            }
            if (!exiting && (unitText.contains("光") || unitText.contains("輝"))) {
                // 「光」「輝」はコーティングが反射するみたいにキラキラさせる
                Canvas(modifier = Modifier.matchParentSize()) {
                    val phBase = sway.value * 6.2832f
                    val sparkleCol = Color(0xFFFFF2B0)
                    val positions = listOf(0.10f to 0.02f, 0.90f to 0.22f, 0.58f to -0.06f)
                    positions.forEachIndexed { i, (fx, fy) ->
                        val tw = 0.5f + 0.5f * sin(phBase * 2f + i * 2.1f + uSeed)
                        val r = size.height * (0.15f + 0.14f * tw)
                        drawSparkle(size.width * fx, size.height * fy, r, sparkleCol, 0.30f + 0.65f * tw)
                    }
                }
            }
        }
        val enCaption = if (!exiting && unitText.length > 1 && unitText.all { it in 'ァ'..'ヶ' || it == 'ー' || it == '・' }) KATAKANA_EN[unitText] else null
        if (!exiting && effectKind == 0) {
            Spacer(modifier = Modifier.height(3.dp))
            val ulv = ulAnim.value
            Canvas(modifier = Modifier.fillMaxWidth().height(3.5.dp)) {
                val startFrac = if (ulv <= 1f) 0f else ulv - 1f
                val endFrac = if (ulv <= 1f) ulv else 1f
                if (endFrac > startFrac) {
                    drawRoundRect(
                        color = accentColor.copy(alpha = 0.6f),
                        topLeft = Offset(size.width * startFrac, 0f),
                        size = Size(size.width * (endFrac - startFrac), size.height),
                        cornerRadius = CornerRadius(size.height / 2f, size.height / 2f)
                    )
                }
            }
        }
        if (enCaption != null) {
            Text(
                text = enCaption,
                color = accentColor.copy(alpha = 0.85f),
                fontSize = (uSize.value * 0.30f).sp,
                fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal,
                letterSpacing = (uSize.value * 0.12f).sp,
                maxLines = 1,
                style = LocalTextStyle.current.copy(fontFamily = fontFamily),
                modifier = Modifier.padding(top = 2.dp)
            )
        }
        }
        if (!exiting && iconKind != null) {
            // アイコンはその単語の右肩に小さく重ねる
            Canvas(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .offset(x = 12.dp, y = (-12).dp)
                    .size((uSize.value * 0.55f).dp)
                    .graphicsLayer {
                        alpha = ulAnim.value.coerceIn(0f, 1f) * 0.45f
                        rotationZ = -9f
                    }
            ) {
                drawLyricIcon(iconKind)
            }
        }
    }
}

/** 塊を描画単位に分ける。カタカナの連続とラテン文字の連続はひとまとめ、それ以外は1文字ずつ */
private fun renderUnitsOf(chunk: String): List<String> {
    val out = mutableListOf<String>()
    val sb = StringBuilder()
    fun flush() {
        if (sb.isNotEmpty()) {
            out.add(sb.toString())
            sb.clear()
        }
    }
    for (ch in chunk) {
        if (ch in '゠'..'ヿ' || ch.code < 0x300) {
            sb.append(ch)
        } else {
            flush()
            out.add(ch.toString())
        }
    }
    flush()
    return out
}

/** 歌詞を行の表示ユニットに割る。ラテン文字の連続は単語としてまとめ、日本語は文節っぽい塊に切る */
private fun splitLyricUnits(text: String): List<String> {
    val units = mutableListOf<String>()
    val latin = StringBuilder()
    val jp = StringBuilder()
    fun flushLatin() {
        if (latin.isNotEmpty()) {
            units.add(latin.toString())
            latin.clear()
        }
    }
    fun flushJp() {
        if (jp.isNotEmpty()) {
            units.addAll(chunkJapanese(jp.toString()))
            jp.clear()
        }
    }
    for (ch in text) {
        when {
            ch == ' ' || ch == '　' -> {
                flushLatin(); flushJp()
            }
            ch.code < 0x300 && (ch.isLetterOrDigit() || ch == '\'' || ch == '’' || ch == '-') -> {
                flushJp(); latin.append(ch)
            }
            else -> {
                flushLatin(); jp.append(ch)
            }
        }
    }
    flushLatin(); flushJp()
    return units
}

/**
 * 日本語の文字列を文節っぽい塊に切る軽量ヒューリスティクス。
 * 内容語（漢字・カタカナ・ひらがな語）に後ろのひらがな（送り仮名・助詞・活用語尾）をくっつけ、
 * ひらがな部分が助詞で始まって後ろに内容が続く場合は、助詞の後ろで次の塊に分ける。
 */
private fun chunkJapanese(span: String): List<String> {
    fun typeOf(ch: Char): Int = when (ch) {
        in '一'..'鿿', '々', '〆', 'ヶ' -> 1 // 漢字
        in 'ぁ'..'ゟ' -> 2 // ひらがな
        in '゠'..'ヿ' -> 3 // カタカナ（ー含む）
        else -> 0
    }
    // 同種の連続に分ける
    val runs = mutableListOf<String>()
    val sb = StringBuilder()
    var prevType = -1
    for (ch in span) {
        val t = typeOf(ch)
        if (t != prevType && sb.isNotEmpty()) {
            runs.add(sb.toString())
            sb.clear()
        }
        sb.append(ch)
        prevType = t
    }
    if (sb.isNotEmpty()) runs.add(sb.toString())

    val particles = "はがをにへとやのもでからまでよりばねよわ"
    val chunks = mutableListOf<String>()
    val cur = StringBuilder()
    fun closeChunk() {
        if (cur.isNotEmpty()) {
            chunks.add(cur.toString())
            cur.clear()
        }
    }
    for (run in runs) {
        val t = typeOf(run[0])
        when {
            t == 2 && cur.isNotEmpty() -> {
                // ひらがな部分: 先頭の助詞（最大2文字）の後ろに2文字以上残るなら、助詞までを今の塊に付けて次へ
                var pLen = 0
                while (pLen < run.length && pLen < 2 && run[pLen] in particles) pLen++
                if (pLen >= 1 && run.length - pLen >= 2) {
                    cur.append(run.substring(0, pLen))
                    closeChunk()
                    cur.append(run.substring(pLen))
                } else {
                    cur.append(run)
                }
            }
            t == 2 -> cur.append(run) // 行頭のひらがなはそのまま塊の始まり
            t == 0 -> cur.append(run) // 句読点などは前の塊に付ける
            else -> {
                // 漢字・カタカナは新しい塊の始まり
                closeChunk()
                cur.append(run)
            }
        }
    }
    closeChunk()
    return chunks
}
