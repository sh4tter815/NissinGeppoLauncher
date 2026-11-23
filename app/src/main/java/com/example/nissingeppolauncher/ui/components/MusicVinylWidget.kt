package com.example.nissingeppolauncher.ui.components

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.media.MediaMetadata
import android.media.session.MediaSession
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.graphics.Bitmap
import android.provider.MediaStore
import android.provider.Settings
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.nissingeppolauncher.data.AppPreferenceManager
import com.example.nissingeppolauncher.service.LauncherMediaListenerService
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.style.TextOverflow
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sign

fun isNotificationServiceEnabled(context: Context): Boolean {
    val packageName = context.packageName
    val flat = Settings.Secure.getString(context.contentResolver, "enabled_notification_listeners")
    return flat?.contains(packageName) == true
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MusicVinylWidget(
    modifier: Modifier = Modifier,
    discCenterXDp: Float,
    discCenterYDp: Float,
    discRadiusDp: Float = 165f,
    accentColor: Color = Color(0xFFE53935),
    textStartAngle: Float = -90f,
    artworkTilt3d: Boolean = false,
    onRackVisibilityChanged: (Boolean) -> Unit = {},
    dopagaki: Boolean = false,
    rainbowGlow: Boolean = false
) {
    val context = LocalContext.current
    val effAccent = if (rainbowGlow) dopaRainbowColor(seed = 13) else accentColor
    val prefManager = remember { AppPreferenceManager(context) }
    var isPlaying by remember { mutableStateOf(false) }
    var albumArtBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var trackTitle by remember { mutableStateOf("") }
    var trackArtist by remember { mutableStateOf("") }
    var trackAlbum by remember { mutableStateOf("") }
    var activePackageName by remember { mutableStateOf<String?>(null) }
    var sessionController by remember { mutableStateOf<MediaController?>(null) }
    var showPermissionDialog by remember { mutableStateOf(false) }

    // ディスクラック用: セッションの再生キューと操作状態
    var sessionQueue by remember { mutableStateOf<List<MediaSession.QueueItem>>(emptyList()) }
    // キュー項目のアートワークキャッシュ（iconUri → Bitmap）。Spotifyは iconBitmap を入れないため、iconUri を辿って取得する
    var queueArtCache by remember { mutableStateOf<Map<String, Bitmap>>(emptyMap()) }
    LaunchedEffect(sessionQueue) {
        val missing = sessionQueue.mapNotNull { it.description?.iconUri?.toString() }.distinct().filter { it !in queueArtCache }
        for (uriStr in missing) {
            val bmp = withContext(Dispatchers.IO) {
                runCatching {
                    val uri = android.net.Uri.parse(uriStr)
                    val raw = when (uri.scheme) {
                        "http", "https" -> java.net.URL(uriStr).openStream().use { android.graphics.BitmapFactory.decodeStream(it) }
                        else -> context.contentResolver.openInputStream(uri)?.use { android.graphics.BitmapFactory.decodeStream(it) }
                    }
                    raw?.let { if (it.width > 256) Bitmap.createScaledBitmap(it, 256, (it.height * 256f / it.width).toInt().coerceAtLeast(1), true) else it }
                }.getOrNull()
            }
            if (bmp != null) queueArtCache = queueArtCache + (uriStr to bmp)
        }
    }
    var rackOpen by remember { mutableStateOf(false) }
    var rackShow by remember { mutableStateOf(false) }
    val widgetScope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current
    val rackScrollAnim = remember { Animatable(0f) }
    var rackTargetOffset by remember { mutableIntStateOf(0) }
    var rackStepAnimRunning by remember { mutableStateOf(false) }
    fun runRackSteps() {
        if (rackStepAnimRunning) return
        rackStepAnimRunning = true
        widgetScope.launch {
            while (rackScrollAnim.value != rackTargetOffset.toFloat()) {
                rackScrollAnim.animateTo(
                    targetValue = rackTargetOffset.toFloat(),
                    animationSpec = tween(durationMillis = 170, easing = FastOutSlowInEasing)
                )
            }
            rackStepAnimRunning = false
        }
    }
    LaunchedEffect(rackOpen) {
        onRackVisibilityChanged(rackOpen)
        if (rackOpen) {
            delay(120)
            if (rackOpen) rackShow = true
        } else {
            rackShow = false
        }
    }

    val mediaSessionManager = remember { context.getSystemService(Context.MEDIA_SESSION_SERVICE) as? MediaSessionManager }
    val audioManager = remember { context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager }
    val listenerComponent = remember { ComponentName(context, LauncherMediaListenerService::class.java) }

    // 定期的に音楽再生状態とメタデータ（アルバムアート・曲情報）を取得
    LaunchedEffect(Unit) {
        while (true) {
            val enabled = isNotificationServiceEnabled(context)
            if (enabled) {
                try {
                    val controllers = mediaSessionManager?.getActiveSessions(listenerComponent)
                    val activeController = controllers?.firstOrNull {
                        it.playbackState?.state == PlaybackState.STATE_PLAYING
                    } ?: controllers?.firstOrNull()

                    if (activeController != null) {
                        isPlaying = activeController.playbackState?.state == PlaybackState.STATE_PLAYING
                        val controllerPkg = activeController.packageName
                        activePackageName = if (controllerPkg != context.packageName) controllerPkg else null
                        sessionController = activeController
                        if (!rackOpen) {
                            try { sessionQueue = activeController.queue ?: emptyList() } catch (_: Exception) {}
                        }
                        val metadata = activeController.metadata
                        val bitmap = metadata?.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART)
                            ?: metadata?.getBitmap(MediaMetadata.METADATA_KEY_ART)
                        albumArtBitmap = bitmap
                        trackTitle = metadata?.getString(MediaMetadata.METADATA_KEY_TITLE) ?: ""
                        trackArtist = metadata?.getString(MediaMetadata.METADATA_KEY_ARTIST) ?: ""
                        trackAlbum = metadata?.getString(MediaMetadata.METADATA_KEY_ALBUM) ?: ""
                    } else {
                        isPlaying = audioManager?.isMusicActive == true
                        albumArtBitmap = null
                        activePackageName = null
                        sessionController = null
                        trackTitle = ""
                        trackArtist = ""
                        trackAlbum = ""
                    }
                } catch (_: Exception) {
                    isPlaying = audioManager?.isMusicActive == true
                    albumArtBitmap = null
                    activePackageName = null
                    sessionController = null
                    trackTitle = ""
                    trackArtist = ""
                    trackAlbum = ""
                }
            } else {
                isPlaying = audioManager?.isMusicActive == true
                albumArtBitmap = null
                activePackageName = null
                sessionController = null
                trackTitle = ""
                trackArtist = ""
                trackAlbum = ""
            }
            delay(1500)
        }
    }

    // 再生中の自動回転アニメーション (5秒で360度回転)
    val infiniteTransition = rememberInfiniteTransition(label = "VinylRotation")
    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 5000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "Rotation"
    )

    val currentRotation = if (isPlaying) rotation else 0f

    // 曲が変わった瞬間、アートワークが縦軸でくるっと1回転する
    val flipAnim = remember { Animatable(1f) }
    var lastFlipKey by remember { mutableStateOf("") }
    LaunchedEffect(trackTitle, trackArtist) {
        val key = "$trackTitle|$trackArtist"
        // 読み込み中はタイトルが一瞬空になることがある。空の状態は曲の切り替わりとみなさず、
        // 前の曲のキーを保持したまま、新しい曲が来たときだけ回転させる
        if (trackTitle.isNotBlank() && key != lastFlipKey) {
            val hadTrack = lastFlipKey.isNotBlank() && lastFlipKey != "|"
            lastFlipKey = key
            if (hadTrack) {
                flipAnim.snapTo(0f)
                flipAnim.animateTo(1f, animationSpec = tween(durationMillis = 650, easing = FastOutSlowInEasing))
            }
        }
    }

    // ディスクラックの中身: キューが取れて現在曲が含まれていればキュー全体、
    // 取れなければ前・今・次の3枚だけを並べる（スクロールで前曲／次曲へ切り替える）
    val queueCurrentIndex = remember(sessionQueue, trackTitle, trackArtist) {
        sessionQueue.indexOfFirst { item ->
            val d = item.description
            val t = d?.title?.toString() ?: ""
            val sub = d?.subtitle?.toString() ?: ""
            t.isNotBlank() && t == trackTitle && (trackArtist.isBlank() || sub.isBlank() || sub == trackArtist)
        }
    }
    val queueMode = queueCurrentIndex >= 0
    val rackSize = if (queueMode) sessionQueue.size else 3
    val rackCurrentIndex = if (queueMode) queueCurrentIndex else 1
    fun rackTitleAt(index: Int): String = when {
        queueMode -> sessionQueue.getOrNull(index)?.description?.title?.toString() ?: ""
        index == rackCurrentIndex -> trackTitle
        index < rackCurrentIndex -> "前の曲"
        else -> "次の曲"
    }
    fun rackArtAt(index: Int): Bitmap? = when {
        queueMode -> sessionQueue.getOrNull(index)?.description?.let { d -> d.iconBitmap ?: d.iconUri?.toString()?.let { queueArtCache[it] } } ?: if (index == rackCurrentIndex) albumArtBitmap else null
        index == rackCurrentIndex -> albumArtBitmap
        else -> null
    }
    // pointerInput のラムダは作られた時点の値を捕捉したままになるため、最新値を参照し続ける箱を用意する
    val latestRackCurrentIndex by rememberUpdatedState(rackCurrentIndex)
    val latestRackSize by rememberUpdatedState(rackSize)
    val latestQueueMode by rememberUpdatedState(queueMode)
    val latestSessionQueue by rememberUpdatedState(sessionQueue)
    val rackFocusedIndex = if (rackSize == 0) 0 else (rackCurrentIndex + rackScrollAnim.value.roundToInt()).coerceIn(0, rackSize - 1)
    var rackLastHapticIndex by remember { mutableIntStateOf(-1) }
    LaunchedEffect(rackShow, rackFocusedIndex) {
        if (rackShow && rackLastHapticIndex != -1 && rackFocusedIndex != rackLastHapticIndex) {
            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        }
        if (rackShow) rackLastHapticIndex = rackFocusedIndex
    }
    val rackTiltX by animateFloatAsState(
        targetValue = if (rackShow) 55f else 0f,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "RackTiltX"
    )

    if (showPermissionDialog) {
        val fontFamily = LocalLauncherFontFamily.current
        AlertDialog(
            onDismissRequest = { showPermissionDialog = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        showPermissionDialog = false
                        try {
                            val intent = Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
                            context.startActivity(intent)
                        } catch (_: Exception) {}
                    }
                ) {
                    Text("設定を開く", color = Color(0xFF88C0FF), fontFamily = fontFamily)
                }
            },
            dismissButton = {
                TextButton(onClick = { showPermissionDialog = false }) {
                    Text("キャンセル", color = Color.Gray, fontFamily = fontFamily)
                }
            },
            title = { Text("通知アクセスの許可", fontSize = 18.sp, fontFamily = fontFamily) },
            text = {
                Text(
                    text = "再生中の音楽のジャケット画像（アートワーク）や曲情報を取得するには、通知アクセスの許可が必要です。設定画面で本アプリを有効にしてください。\n\n※機種（Xiaomi、OPPO、Galaxyなど）によっては、設定画面の右上メニューやアプリ情報等から「制限付き設定を許可」を選択してからでないと通知アクセスを有効にできない場合があります。",
                    color = Color.LightGray,
                    fontSize = 14.sp,
                    fontFamily = fontFamily
                )
            },
            containerColor = Color(0xFF141C2E),
            titleContentColor = Color.White
        )
    }

    // タップ: 再生中のプレイヤーアプリを開く
    fun openPlayerApp() {
                    if (!isNotificationServiceEnabled(context)) {
                        showPermissionDialog = true
                    } else {
                        var launched = false
                        val appContext = context.applicationContext

                        fun launchTaskIntent(intent: Intent): Boolean {
                            return try {
                                // 既存のタスクを前面に出すだけにして、新規セッション（別タスク）を
                                // 作らない。MULTIPLE_TASK / NEW_DOCUMENT は付けない
                                intent.addFlags(
                                    Intent.FLAG_ACTIVITY_NEW_TASK or
                                            Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
                                )
                                appContext.startActivity(intent)
                                true
                            } catch (_: Exception) {
                                false
                            }
                        }

                        // 1. アクティブな音楽アプリを通常起動で開く（既存タスクを前面へ）。
                        //    media3 系アプリはセッションアクティビティの PendingIntent が
                        //    実質効かない場合があり、送信が成功扱いでも画面が開かないことが
                        //    あるため、確実なパッケージ起動を先に試す
                        if (!launched) {
                            val activePkg = activePackageName
                            if (!activePkg.isNullOrBlank() && activePkg != context.packageName) {
                                val launchIntent = context.packageManager.getLaunchIntentForPackage(activePkg)
                                if (launchIntent != null) {
                                    launched = launchTaskIntent(launchIntent)
                                }
                            }
                        }

                        // 2. セッション自身が指定する画面を開く
                        //    （パッケージ起動が使えない場合のフォールバック）
                        if (!launched) {
                            val sessionActivity = sessionController?.sessionActivity
                            if (sessionActivity != null) {
                                launched = try {
                                    sessionActivity.send()
                                    true
                                } catch (_: Exception) {
                                    false
                                }
                            }
                        }

                        // 3. メディアプレーヤー標準/カテゴリインテント
                        if (!launched) {
                            try {
                                val musicCategoryIntent = Intent(Intent.ACTION_MAIN).apply {
                                    addCategory(Intent.CATEGORY_APP_MUSIC)
                                }
                                if (musicCategoryIntent.resolveActivity(context.packageManager) != null) {
                                    launched = launchTaskIntent(musicCategoryIntent)
                                }
                            } catch (_: Exception) {}
                        }

                        if (!launched) {
                            try {
                                val musicMediaIntent = Intent(MediaStore.INTENT_ACTION_MUSIC_PLAYER)
                                if (musicMediaIntent.resolveActivity(context.packageManager) != null) {
                                    launched = launchTaskIntent(musicMediaIntent)
                                }
                            } catch (_: Exception) {}
                        }

                        // 4. 主要音楽アプリの起動試行
                        if (!launched) {
                            val musicPackages = listOf(
                                "com.spotify.music",
                                "com.google.android.apps.youtube.music",
                                "com.apple.android.music",
                                "com.amazon.mp3",
                                "com.sec.android.app.music",
                                "com.sony.walkman",
                                "com.maxmpz.audioplayer",
                                "com.android.music"
                            )
                            for (pkg in musicPackages) {
                                if (pkg == context.packageName) continue
                                val intent = context.packageManager.getLaunchIntentForPackage(pkg)
                                if (intent != null) {
                                    if (launchTaskIntent(intent)) {
                                        launched = true
                                        break
                                    }
                                }
                            }
                        }
                    }
    }

    Box(
        modifier = modifier.fillMaxSize()
    ) {
        Box(
            modifier = Modifier
                .offset(x = (discCenterXDp - discRadiusDp).dp, y = (discCenterYDp - discRadiusDp).dp)
                .size((discRadiusDp * 2f).dp)
                .clip(CircleShape)
                .pointerInput(sessionController != null) {
                    awaitPointerEventScope {
                        while (true) {
                            val down = awaitFirstDown(requireUnconsumed = false)
                            val hasSession = sessionController != null
                            val startIdx = latestRackCurrentIndex
                            rackTargetOffset = 0
                            if (hasSession) rackOpen = true
                            var moved = false
                            var maxDist = 0f
                            var stepBaseY = down.position.y
                            val dirSign = if (prefManager.isReverseScroll()) -1 else 1
                            val downWall = System.currentTimeMillis()
                            drag(down.id) { change ->
                                maxDist = maxOf(maxDist, (change.position - down.position).getDistance())
                                if (hasSession) {
                                    var dy = change.position.y - stepBaseY
                                    while (dy <= -40f || dy >= 40f) {
                                        val dir = (if (dy <= -40f) 1 else -1) * dirSign
                                        val newTarget = rackTargetOffset + dir
                                        if (startIdx + newTarget in 0 until latestRackSize) {
                                            rackTargetOffset = newTarget
                                            moved = true
                                            runRackSteps()
                                        }
                                        if (dy <= -40f) {
                                            stepBaseY -= 40f
                                            dy += 40f
                                        } else {
                                            stepBaseY += 40f
                                            dy -= 40f
                                        }
                                    }
                                }
                                change.consume()
                            }
                            val heldMs = System.currentTimeMillis() - downWall
                            val finalIndex = (startIdx + rackTargetOffset).coerceIn(0, (latestRackSize - 1).coerceAtLeast(0))
                            val shouldJump = hasSession && moved && finalIndex != startIdx
                            if (shouldJump) {
                                widgetScope.launch {
                                    var guard = 0
                                    while ((rackStepAnimRunning || rackScrollAnim.value != rackTargetOffset.toFloat()) && guard < 120) {
                                        delay(30)
                                        guard++
                                    }
                                    try {
                                        val tc = sessionController?.transportControls
                                        if (shouldJump) {
                                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                            if (latestQueueMode) {
                                                latestSessionQueue.getOrNull(finalIndex)?.let { tc?.skipToQueueItem(it.queueId) }
                                            } else if (finalIndex < startIdx) {
                                                tc?.skipToPrevious()
                                            } else {
                                                tc?.skipToNext()
                                            }
                                        }
                                    } catch (e: Exception) {
                                        // 操作できないセッションでは何もしない
                                    }
                                    rackOpen = false
                                    rackScrollAnim.snapTo(0f)
                                    rackTargetOffset = 0
                                }
                            } else {
                                // 短いタップ（またはセッション無し）: ラックを閉じてプレイヤーを開く。ドラッグだった場合は何もしない
                                rackOpen = false
                                if (maxDist < 14.dp.toPx() && heldMs < 300L) openPlayerApp()
                            }
                        }
                    }
                }
                .graphicsLayer { alpha = if (rackShow) 0f else 1f },
            contentAlignment = Alignment.Center
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val center = Offset(size.width / 2f, size.height / 2f)
                val outerRadius = size.width / 2f

                // 外周の白い円盤輪郭
                drawCircle(
                    color = Color.White.copy(alpha = 0.85f),
                    radius = outerRadius,
                    center = center,
                    style = Stroke(width = 1.5f)
                )
            }

            // 薄い立体感: アートワークの下に暗い円を少しずらして敷き、盤の厚みとして見せる
            if (artworkTilt3d && albumArtBitmap != null) {
                Box(
                    modifier = Modifier
                        .size((discRadiusDp * 2f * 0.92f).dp)
                        .offset(y = 3.5.dp)
                        .background(Color.Black.copy(alpha = 0.45f), CircleShape)
                )
            }

            // 大サイズ（円サイズの92%）の回転するアートワーク / レーベル部分
            Box(
                modifier = Modifier
                    .size((discRadiusDp * 2f * 0.92f).dp)
                    .graphicsLayer {
                        rotationZ = currentRotation
                        rotationY = (1f - flipAnim.value) * 360f
                        cameraDistance = 9f * density
                    }
                    .clip(CircleShape),
                contentAlignment = Alignment.Center
            ) {
                val bitmap = albumArtBitmap
                if (bitmap != null) {
                    Image(
                        bitmap = bitmap.asImageBitmap(),
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize()
                    )
                }
                // アートワークが無い（再生していない）ときは色を塗らない。
                // センターホール（中心の点）は再生中・停止中ともに出さない
            }
        }
    }

    // --- ディスクラック: 横倒しのレコード盤がカスケード状に並ぶ（音楽アプリと同じ見た目） ---
    if (rackShow && rackSize > 0) {
        val virtualPos = rackCurrentIndex + rackScrollAnim.value
        val firstIdx = maxOf(0, floor(virtualPos - 6f).toInt())
        val lastIdx = minOf(rackSize - 1, ceil(virtualPos + 6f).toInt())
        for (targetIdx in firstIdx..lastIdx) {
            val relPos = targetIdx - virtualPos
            val gapOffset = sign(relPos) * 24f * min(1f, abs(relPos))
            val yPosDp = discCenterYDp + (relPos * 42f) + gapOffset
            val isFocused = targetIdx == rackFocusedIndex
            val focusBump = (1f - min(1f, abs(relPos))) * 30f
            val xShiftDp = relPos * 7f + focusBump
            val edgeFade = (1f - (abs(relPos) - 4.2f) / 2f).coerceIn(0f, 1f)
            Box(
                modifier = Modifier
                    .offset(x = (discCenterXDp - discRadiusDp + xShiftDp).dp, y = (yPosDp - discRadiusDp).dp)
                    .size((discRadiusDp * 2f).dp)
                    .graphicsLayer {
                        rotationX = rackTiltX
                        cameraDistance = 12f * density
                        alpha = edgeFade
                    }
                    .clip(CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val center = Offset(size.width / 2f, size.height / 2f)
                    drawCircle(
                        color = if (isFocused) effAccent else Color.White.copy(alpha = 0.5f),
                        radius = size.width / 2f,
                        center = center,
                        style = Stroke(width = if (isFocused) 3f else 1.5f)
                    )
                }
                Box(
                    modifier = Modifier
                        .size((discRadiusDp * 2f * 0.92f).dp)
                        .clip(CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    val art = rackArtAt(targetIdx)
                    if (art != null) {
                        Image(
                            bitmap = art.asImageBitmap(),
                            contentDescription = null,
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        Canvas(modifier = Modifier.fillMaxSize()) {
                            drawCircle(color = effAccent.copy(alpha = 0.5f))
                        }
                    }
                }
            }
            // 各ディスクの右に曲名を表示
            Text(
                text = rackTitleAt(targetIdx),
                color = if (isFocused) Color.White.copy(alpha = edgeFade) else Color.White.copy(alpha = 0.65f * edgeFade),
                fontSize = if (isFocused) 14.sp else 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .offset(
                        x = (discCenterXDp + discRadiusDp + xShiftDp + 10f).dp,
                        y = (yPosDp - 9f).dp
                    )
                    .width(105.dp)
            )
        }
    }

    // --- 円の右側（画面内）の円周に沿って湾曲描画する曲情報 ---
    val noMusicText = "音楽を再生するとここに曲名が表示されます  —  No Music Playing  —  "
    val displayInfo = remember(trackTitle, trackArtist, trackAlbum, isPlaying, noMusicText) {
        val raw = listOfNotNull(
            trackTitle.ifBlank { null },
            trackArtist.ifBlank { null },
            trackAlbum.ifBlank { null }
        ).joinToString("  —  ")
        if (raw.isNotBlank()) raw else noMusicText
    }

    val customFontPath = remember(prefManager.getCustomFontPath(), prefManager.isUseCustomFont()) {
        if (prefManager.isUseCustomFont()) prefManager.getCustomFontPath() else null
    }
    val fontChoice = prefManager.getFontChoice()
    // 円周テキストのフォントはアプリ全体のフォント設定（parseFontFamily）と揃える。
    // 以前はカスタムフォントファイルしか見ておらず、同梱フォント（receipt）選択時にデフォルト固定になっていた
    val customTypeface = remember(customFontPath, fontChoice) {
        try {
            when (fontChoice) {
                "receipt" -> androidx.core.content.res.ResourcesCompat.getFont(
                    context,
                    com.example.nissingeppolauncher.R.font.receipt_font
                )
                "denkichip" -> androidx.core.content.res.ResourcesCompat.getFont(
                    context,
                    com.example.nissingeppolauncher.R.font.denkichip_font
                )
                else -> if (!customFontPath.isNullOrBlank()) {
                    val file = java.io.File(customFontPath)
                    if (file.exists()) android.graphics.Typeface.createFromFile(file) else null
                } else null
            }
        } catch (_: Exception) {
            null
        }
    }

    Box(
        modifier = modifier.fillMaxSize().graphicsLayer { alpha = if (rackShow) 0f else 1f }
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val centerX = discCenterXDp.dp.toPx()
            val centerY = discCenterYDp.dp.toPx()
            val outerRadius = discRadiusDp.dp.toPx()
            val pathRadius = outerRadius + 14.dp.toPx()

            val textPath = android.graphics.Path().apply {
                addArc(
                    centerX - pathRadius,
                    centerY - pathRadius,
                    centerX + pathRadius,
                    centerY + pathRadius,
                    textStartAngle,
                    180f
                )
            }
            val paint = android.graphics.Paint().apply {
                color = android.graphics.Color.WHITE
                textSize = 11.sp.toPx()
                isAntiAlias = true
                letterSpacing = 0.15f
                textAlign = android.graphics.Paint.Align.LEFT
                setShadowLayer(4f, 0f, 0f, android.graphics.Color.BLACK)
                if (dopagaki) {
                    // ドパガキモード: 円周テキストも虹色グラデ＋金のグローにする
                    shader = android.graphics.LinearGradient(
                        0f, 0f, size.width, size.height,
                        intArrayOf(
                            android.graphics.Color.rgb(255, 93, 162),
                            android.graphics.Color.rgb(214, 51, 255),
                            android.graphics.Color.rgb(122, 92, 255),
                            android.graphics.Color.rgb(51, 181, 255),
                            android.graphics.Color.rgb(63, 224, 197),
                            android.graphics.Color.rgb(255, 217, 59),
                            android.graphics.Color.rgb(255, 138, 59)
                        ),
                        null,
                        android.graphics.Shader.TileMode.CLAMP
                    )
                    setShadowLayer(6f, 0f, 0f, android.graphics.Color.rgb(255, 196, 0))
                }
                if (customTypeface != null) {
                    typeface = customTypeface
                }
            }
            // 文字列の中心が円の右下（約35°）に来るよう、弧の長さと文字幅から開始位置を決める
            val arcLen = (Math.PI * pathRadius).toFloat()
            val textW = paint.measureText(displayInfo)
            val centerPos = (35f - textStartAngle) / 180f * arcLen
            val hOff = (centerPos - textW / 2f).coerceIn(0f, maxOf(0f, arcLen - textW))
            drawContext.canvas.nativeCanvas.drawTextOnPath(displayInfo, textPath, hOff, 0f, paint)
        }
    }
}
