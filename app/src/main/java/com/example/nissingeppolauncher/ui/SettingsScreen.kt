package com.example.nissingeppolauncher.ui

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.drag
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.nissingeppolauncher.data.AppRepository
import com.example.nissingeppolauncher.service.LyricsOverlayService
import com.example.nissingeppolauncher.ui.components.parseFontFamily
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import java.io.File
import java.io.FileOutputStream
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import androidx.core.graphics.toColorInt

val ACCENT_PRESETS = listOf(
    Pair("赤", "#E53935"),
    Pair("青", "#88C0FF"),
    Pair("緑", "#4CAF50"),
    Pair("紫", "#AB47BC"),
    Pair("オレンジ", "#FF9800"),
    Pair("シアン", "#00E5FF"),
    Pair("ピンク", "#FF4081")
)

fun parseHexColor(hex: String, fallback: Color = Color(0xFFE53935)): Color {
    return try {
        Color(hex.toColorInt())
    } catch (_: Exception) {
        fallback
    }
}

@Composable
fun SimpleTickSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float>,
    steps: Int = 10,
    accentColor: Color = Color(0xFFE53935),
    modifier: Modifier = Modifier
) {
    var widthPx by remember { mutableFloatStateOf(1f) }
    val stepCount = steps + 1
    val fraction = ((value - valueRange.start) / (valueRange.endInclusive - valueRange.start)).coerceIn(0f, 1f)
    val updateVal: (Float) -> Unit = { xPx ->
        val f = (xPx / widthPx).coerceIn(0f, 1f)
        val stepIdx = (f * stepCount).roundToInt().coerceIn(0, stepCount)
        onValueChange(valueRange.start + stepIdx * ((valueRange.endInclusive - valueRange.start) / stepCount))
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(36.dp)
            .onGloballyPositioned { widthPx = it.size.width.toFloat().coerceAtLeast(1f) }
            // タップと横ドラッグだけをスライダーが受け取り、縦ドラッグは親のスクロールに渡す
            .pointerInput(valueRange, steps) {
                detectTapGestures { offset -> updateVal(offset.x) }
            }
            .pointerInput(valueRange, steps) {
                detectHorizontalDragGestures(
                    onDragStart = { offset -> updateVal(offset.x) },
                    onHorizontalDrag = { change, _ ->
                        change.consume()
                        updateVal(change.position.x)
                    }
                )
            },
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val startX = 12.dp.toPx()
            val endX = size.width - 12.dp.toPx()
            val usableWidth = endX - startX
            val centerY = size.height / 2f

            drawLine(
                color = Color.White.copy(alpha = 0.7f),
                start = Offset(startX, centerY),
                end = Offset(endX, centerY),
                strokeWidth = 2.5f
            )

            for (i in 0..stepCount) {
                val tickX = startX + i * (usableWidth / stepCount)
                drawLine(
                    color = Color.White.copy(alpha = 0.9f),
                    start = Offset(tickX, centerY - 6.dp.toPx()),
                    end = Offset(tickX, centerY + 6.dp.toPx()),
                    strokeWidth = 2f
                )
            }

            val thumbX = startX + fraction * usableWidth
            drawCircle(
                color = accentColor,
                radius = 9.dp.toPx(),
                center = Offset(thumbX, centerY)
            )
            drawCircle(
                color = Color.White,
                radius = 9.dp.toPx(),
                center = Offset(thumbX, centerY),
                style = Stroke(width = 2f)
            )
        }
    }
}

@Composable
fun FontSelectionSection(
    currentFontChoice: String,
    onFontSelected: (String) -> Unit,
    onImportClick: () -> Unit,
    customFontPath: String?,
    useCustomFont: Boolean,
    onUseCustomFontChanged: (Boolean) -> Unit,
    switchColors: SwitchColors
) {
    var expanded by remember { mutableStateOf(false) }
    val fontOptions = mapOf(
        "default" to "システムフォント",
        "receipt" to "レシートフォント (Receipt Font)",
        "denkichip" to "でんきチップ (DenkiChip)",
        "tsukuhou" to "築豊明朝 (Tsukuhou Mincho)",
        "kaibunsho" to "怪聞書フォント (Kaibunsho)",
        "zenkaku" to "Zen Kaku Gothic New (Medium)",
        "custom" to "カスタムフォント (.ttf/.otf)"
    )
    val currentLabel = fontOptions[currentFontChoice] ?: "システムフォント"

    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text("フォント選択", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Normal)

        Box(modifier = Modifier.fillMaxWidth()) {
            OutlinedButton(
                onClick = { expanded = true },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(currentLabel, fontSize = 14.sp)
                    Text("▼", fontSize = 12.sp)
                }
            }
            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
                modifier = Modifier.background(Color(0xFF141C2E))
            ) {
                fontOptions.forEach { (key, label) ->
                    DropdownMenuItem(
                        text = { Text(label, color = Color.White) },
                        onClick = {
                            onFontSelected(key)
                            expanded = false
                        }
                    )
                }
            }
        }

        Button(
            onClick = onImportClick,
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1F293D)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("フォントファイル (.ttf / .otf) をインポート", color = Color.White, fontWeight = FontWeight.Light)
        }

    }
}


// 3択などの選択肢を、イラスト付きのカードで選ぶための部品
@Composable
fun ChoiceIllustration(kind: String, accentColor: Color, selected: Boolean) {
    Canvas(modifier = Modifier.size(40.dp)) {
        val w = size.width
        val h = size.height
        val ink = Color.White.copy(alpha = 0.9f)
        val thin = Stroke(width = w * 0.05f)
        when (kind) {
            "disc_classic" -> {
                drawCircle(color = accentColor, radius = w * 0.19f, center = Offset(w * 0.30f, h * 0.50f))
                drawCircle(color = ink, radius = w * 0.19f, center = Offset(w * 0.70f, h * 0.50f))
            }
            "disc_artwork" -> {
                drawCircle(color = accentColor, radius = w * 0.26f, center = Offset(w * 0.36f, h * 0.50f))
                drawCircle(color = ink, radius = w * 0.12f, center = Offset(w * 0.78f, h * 0.50f))
            }
            "disc_auto" -> {
                drawCircle(color = accentColor, radius = w * 0.25f, center = Offset(w * 0.38f, h * 0.50f))
                val tri = Path()
                tri.moveTo(w * 0.31f, h * 0.37f)
                tri.lineTo(w * 0.31f, h * 0.63f)
                tri.lineTo(w * 0.52f, h * 0.50f)
                tri.close()
                drawPath(path = tri, color = Color(0xFF101728))
                drawCircle(color = ink, radius = w * 0.11f, center = Offset(w * 0.79f, h * 0.50f))
            }
            "date_sideways" -> {
                drawCircle(color = ink, radius = w * 0.20f, center = Offset(w * 0.33f, h * 0.50f), style = thin)
                drawLine(color = ink, start = Offset(w * 0.33f, h * 0.50f), end = Offset(w * 0.33f, h * 0.37f), strokeWidth = w * 0.05f, cap = StrokeCap.Round)
                drawLine(color = ink, start = Offset(w * 0.33f, h * 0.50f), end = Offset(w * 0.43f, h * 0.55f), strokeWidth = w * 0.05f, cap = StrokeCap.Round)
                drawCircle(color = accentColor, radius = w * 0.045f, center = Offset(w * 0.72f, h * 0.27f))
                drawCircle(color = accentColor, radius = w * 0.045f, center = Offset(w * 0.72f, h * 0.50f))
                drawCircle(color = accentColor, radius = w * 0.045f, center = Offset(w * 0.72f, h * 0.73f))
            }
            "date_under" -> {
                drawCircle(color = ink, radius = w * 0.17f, center = Offset(w * 0.50f, h * 0.36f), style = thin)
                drawLine(color = ink, start = Offset(w * 0.50f, h * 0.36f), end = Offset(w * 0.50f, h * 0.26f), strokeWidth = w * 0.05f, cap = StrokeCap.Round)
                drawLine(color = ink, start = Offset(w * 0.50f, h * 0.36f), end = Offset(w * 0.58f, h * 0.40f), strokeWidth = w * 0.05f, cap = StrokeCap.Round)
                drawLine(color = accentColor, start = Offset(w * 0.28f, h * 0.68f), end = Offset(w * 0.72f, h * 0.68f), strokeWidth = w * 0.06f, cap = StrokeCap.Round)
                drawLine(color = ink, start = Offset(w * 0.36f, h * 0.82f), end = Offset(w * 0.64f, h * 0.82f), strokeWidth = w * 0.06f, cap = StrokeCap.Round)
            }
            "date_auto" -> {
                drawCircle(color = ink, radius = w * 0.18f, center = Offset(w * 0.36f, h * 0.40f), style = thin)
                drawLine(color = ink, start = Offset(w * 0.36f, h * 0.40f), end = Offset(w * 0.36f, h * 0.29f), strokeWidth = w * 0.05f, cap = StrokeCap.Round)
                drawLine(color = ink, start = Offset(w * 0.36f, h * 0.40f), end = Offset(w * 0.45f, h * 0.45f), strokeWidth = w * 0.05f, cap = StrokeCap.Round)
                drawLine(color = accentColor, start = Offset(w * 0.56f, h * 0.70f), end = Offset(w * 0.84f, h * 0.70f), strokeWidth = w * 0.05f, cap = StrokeCap.Round)
                drawLine(color = accentColor, start = Offset(w * 0.84f, h * 0.70f), end = Offset(w * 0.74f, h * 0.61f), strokeWidth = w * 0.05f, cap = StrokeCap.Round)
                drawLine(color = accentColor, start = Offset(w * 0.84f, h * 0.86f), end = Offset(w * 0.56f, h * 0.86f), strokeWidth = w * 0.05f, cap = StrokeCap.Round)
                drawLine(color = accentColor, start = Offset(w * 0.56f, h * 0.86f), end = Offset(w * 0.66f, h * 0.77f), strokeWidth = w * 0.05f, cap = StrokeCap.Round)
            }
            "bg_wallpaper" -> {
                drawRoundRect(color = ink, topLeft = Offset(w * 0.13f, h * 0.17f), size = Size(w * 0.74f, h * 0.66f), cornerRadius = CornerRadius(w * 0.06f, w * 0.06f), style = thin)
                drawCircle(color = accentColor, radius = w * 0.08f, center = Offset(w * 0.68f, h * 0.35f))
                val m1 = Path()
                m1.moveTo(w * 0.20f, h * 0.75f)
                m1.lineTo(w * 0.42f, h * 0.45f)
                m1.lineTo(w * 0.58f, h * 0.75f)
                m1.close()
                drawPath(path = m1, color = ink.copy(alpha = 0.85f))
                val m2 = Path()
                m2.moveTo(w * 0.48f, h * 0.75f)
                m2.lineTo(w * 0.64f, h * 0.53f)
                m2.lineTo(w * 0.80f, h * 0.75f)
                m2.close()
                drawPath(path = m2, color = ink.copy(alpha = 0.6f))
            }
            "bg_solid" -> {
                drawRoundRect(color = accentColor, topLeft = Offset(w * 0.16f, h * 0.20f), size = Size(w * 0.68f, h * 0.60f), cornerRadius = CornerRadius(w * 0.06f, w * 0.06f))
            }
            "bg_blur" -> {
                drawCircle(color = accentColor.copy(alpha = 0.45f), radius = w * 0.20f, center = Offset(w * 0.38f, h * 0.42f))
                drawCircle(color = ink.copy(alpha = 0.35f), radius = w * 0.20f, center = Offset(w * 0.62f, h * 0.42f))
                drawCircle(color = Color(0xFF9AA0A6).copy(alpha = 0.40f), radius = w * 0.20f, center = Offset(w * 0.50f, h * 0.63f))
            }
        }
    }
}

@Composable
fun IllustratedChoiceCard(
    kind: String,
    label: String,
    selected: Boolean,
    accentColor: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Surface(
        color = if (selected) accentColor.copy(alpha = 0.25f) else Color(0xFF1F293D),
        shape = RoundedCornerShape(16.dp),
        border = if (selected) BorderStroke(1.5.dp, accentColor) else null,
        modifier = modifier.height(84.dp).clickable { onClick() }
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 8.dp)
        ) {
            ChoiceIllustration(kind = kind, accentColor = accentColor, selected = selected)
            Spacer(modifier = Modifier.height(5.dp))
            Text(
                text = label,
                color = if (selected) Color.White else Color.White.copy(alpha = 0.7f),
                fontSize = 11.5.sp,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                maxLines = 1
            )
        }
    }
}

// 設定トップのカテゴリ行に付ける小さなイラスト（Canvas の簡単な線画）
@Composable
fun SettingsCategoryIcon(kind: String, accentColor: Color) {
    Canvas(modifier = Modifier.size(38.dp)) {
        val w = size.width
        val h = size.height
        val ink = Color.White.copy(alpha = 0.9f)
        val thin = Stroke(width = w * 0.055f)
        when (kind) {
            "behavior" -> {
                // 左右で逆向きの2本矢印（スクロール方向・操作感のイメージ）
                val lw = w * 0.06f
                drawLine(color = accentColor, start = Offset(w * 0.16f, h * 0.35f), end = Offset(w * 0.76f, h * 0.35f), strokeWidth = lw, cap = StrokeCap.Round)
                drawLine(color = accentColor, start = Offset(w * 0.76f, h * 0.35f), end = Offset(w * 0.60f, h * 0.21f), strokeWidth = lw, cap = StrokeCap.Round)
                drawLine(color = accentColor, start = Offset(w * 0.76f, h * 0.35f), end = Offset(w * 0.60f, h * 0.49f), strokeWidth = lw, cap = StrokeCap.Round)
                drawLine(color = ink, start = Offset(w * 0.84f, h * 0.65f), end = Offset(w * 0.24f, h * 0.65f), strokeWidth = lw, cap = StrokeCap.Round)
                drawLine(color = ink, start = Offset(w * 0.24f, h * 0.65f), end = Offset(w * 0.40f, h * 0.51f), strokeWidth = lw, cap = StrokeCap.Round)
                drawLine(color = ink, start = Offset(w * 0.24f, h * 0.65f), end = Offset(w * 0.40f, h * 0.79f), strokeWidth = lw, cap = StrokeCap.Round)
            }
            "appearance" -> {
                drawCircle(color = accentColor, radius = w * 0.27f, center = Offset(w * 0.36f, h * 0.38f))
                drawCircle(color = ink.copy(alpha = 0.75f), radius = w * 0.27f, center = Offset(w * 0.64f, h * 0.38f))
                drawCircle(color = Color(0xFF9AA0A6), radius = w * 0.27f, center = Offset(w * 0.50f, h * 0.66f))
            }
            "lyrics" -> {
                drawOval(color = accentColor, topLeft = Offset(w * 0.10f, h * 0.64f), size = Size(w * 0.30f, h * 0.22f))
                drawLine(color = ink, start = Offset(w * 0.40f, h * 0.72f), end = Offset(w * 0.40f, h * 0.16f), strokeWidth = w * 0.05f, cap = StrokeCap.Round)
                drawOval(color = ink, topLeft = Offset(w * 0.56f, h * 0.58f), size = Size(w * 0.28f, h * 0.20f))
                drawLine(color = ink, start = Offset(w * 0.84f, h * 0.66f), end = Offset(w * 0.84f, h * 0.10f), strokeWidth = w * 0.05f, cap = StrokeCap.Round)
                drawLine(color = ink, start = Offset(w * 0.40f, h * 0.16f), end = Offset(w * 0.84f, h * 0.10f), strokeWidth = w * 0.07f, cap = StrokeCap.Round)
            }
            "apps" -> {
                val cell = w * 0.32f
                val rad = CornerRadius(w * 0.06f, w * 0.06f)
                drawRoundRect(color = accentColor, topLeft = Offset(w * 0.13f, h * 0.13f), size = Size(cell, cell), cornerRadius = rad)
                drawRoundRect(color = ink, topLeft = Offset(w * 0.55f, h * 0.13f), size = Size(cell, cell), cornerRadius = rad)
                drawRoundRect(color = ink, topLeft = Offset(w * 0.13f, h * 0.55f), size = Size(cell, cell), cornerRadius = rad)
                drawRoundRect(color = accentColor, topLeft = Offset(w * 0.55f, h * 0.55f), size = Size(cell, cell), cornerRadius = rad)
            }
            "security" -> {
                drawArc(color = ink, startAngle = 180f, sweepAngle = 180f, useCenter = false, topLeft = Offset(w * 0.29f, h * 0.08f), size = Size(w * 0.42f, h * 0.42f), style = thin)
                drawRoundRect(color = accentColor, topLeft = Offset(w * 0.18f, h * 0.34f), size = Size(w * 0.64f, h * 0.52f), cornerRadius = CornerRadius(w * 0.08f, w * 0.08f))
                drawCircle(color = Color(0xFF101728), radius = w * 0.07f, center = Offset(w * 0.50f, h * 0.58f))
            }
            else -> {
                drawCircle(color = ink, radius = w * 0.36f, style = thin)
                val q = Path()
                q.moveTo(w * 0.38f, h * 0.37f)
                q.cubicTo(w * 0.38f, h * 0.23f, w * 0.62f, h * 0.23f, w * 0.62f, h * 0.37f)
                q.cubicTo(w * 0.62f, h * 0.49f, w * 0.50f, h * 0.50f, w * 0.50f, h * 0.61f)
                drawPath(path = q, color = ink, style = Stroke(width = w * 0.055f, cap = StrokeCap.Round))
                drawCircle(color = ink, radius = w * 0.045f, center = Offset(w * 0.50f, h * 0.73f))
            }
        }
    }
}


// 設定トップのカテゴリ円盤: ホームの円盤と同じ骨格で、左の円を回してカテゴリを選ぶ。
// 円の右端（画面中央向き）がフォーカス位置。右のパネルで内容を確認し、「開く」で詳細へ進む
@Composable
fun SettingsCategoryDisc(
    categories: List<Triple<String, String, String>>,
    accentColor: Color,
    onOpen: (String) -> Unit
) {
    val count = categories.size
    val stepDeg = 360f / count
    val scope = rememberCoroutineScope()
    val haptics = LocalHapticFeedback.current
    val rotation = remember { Animatable(0f) }
    fun focusFor(rot: Float): Int {
        val k = Math.round(-rot / stepDeg)
        return ((k % count) + count) % count
    }
    val focused = focusFor(rotation.value)
    LaunchedEffect(focused) { haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove) }
    fun snapNow() {
        val target = Math.round(rotation.value / stepDeg) * stepDeg
        scope.launch { rotation.animateTo(target, spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessLow)) }
    }
    fun rotateToIndex(idx: Int) {
        val cur = rotation.value
        val base = -idx * stepDeg
        val target = base + Math.round((cur - base) / 360f) * 360f
        scope.launch { rotation.animateTo(target, spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessLow)) }
    }
    val focusedCat = categories[focused]
    Row(
        modifier = Modifier.fillMaxWidth().height(336.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 左: 回る円盤
        Box(
            modifier = Modifier.weight(1.2f).fillMaxHeight(),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .size(300.dp)
                    .offset(x = (-56).dp)
                    .clip(CircleShape)
                    .background(Color(0x99101728))
                    .border(1.5.dp, Color.White.copy(alpha = 0.22f), CircleShape)
                    .pointerInput(Unit) {
                        detectDragGestures(
                            onDragEnd = { snapNow() },
                            onDragCancel = { snapNow() }
                        ) { change, dragAmount ->
                            change.consume()
                            val delta = (dragAmount.y + dragAmount.x) * 0.22f
                            scope.launch { rotation.snapTo(rotation.value + delta) }
                        }
                    }
            ) {
                // 縁のカテゴリアイコン（角度0=右端がフォーカス）
                categories.forEachIndexed { i, cat ->
                    val angRad = Math.toRadians((rotation.value + i * stepDeg).toDouble())
                    val rim = 112f
                    val badge = if (i == focused) 62f else 52f
                    val cx = 150f + rim * cos(angRad).toFloat() - badge / 2f
                    val cy = 150f + rim * sin(angRad).toFloat() - badge / 2f
                    Box(
                        modifier = Modifier
                            .offset(x = cx.dp, y = cy.dp)
                            .size(badge.dp)
                            .clip(CircleShape)
                            .background(if (i == focused) accentColor.copy(alpha = 0.25f) else Color(0xFF1F293D))
                            .border(if (i == focused) 2.dp else 1.dp, if (i == focused) accentColor else Color.White.copy(alpha = 0.18f), CircleShape)
                            .clickable { if (i == focused) onOpen(cat.first) else rotateToIndex(i) },
                        contentAlignment = Alignment.Center
                    ) {
                        SettingsCategoryIcon(kind = cat.first, accentColor = accentColor)
                    }
                }
                // 中心: フォーカス中のカテゴリ
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.align(Alignment.Center)
                ) {
                    Box(modifier = Modifier.graphicsLayer { scaleX = 1.35f; scaleY = 1.35f }) {
                        SettingsCategoryIcon(kind = focusedCat.first, accentColor = accentColor)
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(focusedCat.second, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
        // 右: 選択中カテゴリの説明と全カテゴリのミニリスト
        Column(modifier = Modifier.weight(1f)) {
            Text(focusedCat.second, color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(6.dp))
            Text(focusedCat.third, color = Color.Gray, fontSize = 12.5.sp, fontWeight = FontWeight.Light)
            Spacer(modifier = Modifier.height(14.dp))
            Surface(
                color = accentColor.copy(alpha = 0.22f),
                shape = RoundedCornerShape(14.dp),
                border = BorderStroke(1.dp, accentColor),
                modifier = Modifier.clickable { onOpen(focusedCat.first) }
            ) {
                Text("開く ›", color = Color.White, fontSize = 14.sp, modifier = Modifier.padding(horizontal = 18.dp, vertical = 10.dp))
            }
            Spacer(modifier = Modifier.height(18.dp))
            categories.forEachIndexed { i, cat ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .clickable { rotateToIndex(i) }
                        .padding(vertical = 3.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .clip(CircleShape)
                            .background(if (i == focused) accentColor else Color.White.copy(alpha = 0.25f))
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        cat.second,
                        color = if (i == focused) Color.White else Color.Gray,
                        fontSize = if (i == focused) 13.sp else 12.5.sp,
                        fontWeight = if (i == focused) FontWeight.Bold else FontWeight.Normal
                    )
                }
            }
        }
    }
}

// フォント選択カード: 閉じた状態では「現在のフォント名＋見本文字」だけを見せ、タップで候補リストを展開する
@Composable
fun FontPickerCard(
    title: String,
    options: List<Pair<String, String>>,
    selected: String,
    accentColor: Color,
    expanded: Boolean,
    onToggle: () -> Unit,
    onSelected: (String) -> Unit,
    fontFor: (String) -> FontFamily?,
    sampleText: String,
    footer: @Composable (() -> Unit)? = null
) {
    Surface(
        color = Color(0x99101728),
        shape = RoundedCornerShape(20.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onToggle() }
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(title, color = Color.White, fontSize = 14.sp)
                    Text(
                        text = options.firstOrNull { it.first == selected }?.second ?: "",
                        color = accentColor,
                        fontSize = 13.sp,
                        fontFamily = fontFor(selected)
                    )
                }
                Text(
                    text = sampleText,
                    color = Color.White.copy(alpha = 0.85f),
                    fontSize = 20.sp,
                    fontFamily = fontFor(selected),
                    modifier = Modifier.padding(end = 12.dp)
                )
                Text(if (expanded) "△" else "▽", color = Color.Gray, fontSize = 14.sp)
            }
            if (expanded) {
                options.forEach { (key, label) ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelected(key) }
                            .padding(vertical = 2.dp)
                    ) {
                        RadioButton(
                            selected = selected == key,
                            onClick = null,
                            colors = RadioButtonDefaults.colors(selectedColor = accentColor, unselectedColor = Color.Gray)
                        )
                        Text(
                            text = label,
                            color = if (selected == key) accentColor else Color.White.copy(alpha = 0.8f),
                            fontSize = 15.sp,
                            fontFamily = fontFor(key)
                        )
                    }
                }
            }
            footer?.invoke()
        }
    }
}

@Composable
fun SettingsScreen(
    repo: AppRepository,
    onBack: () -> Unit = {},
    onChanged: () -> Unit = {},
    onAppsUpdated: () -> Unit = {},
    // 画面側で算出された現在のアクセント色（アートワーク連動時はその抽出色）
    accentColor: Color = Color(0xFFE53935)
) {
    val context = LocalContext.current
    var accentHex by remember { mutableStateOf(repo.prefManager.getAccentColorHex()) }
    var matchArtwork by remember { mutableStateOf(repo.prefManager.isMatchArtworkColor()) }
    var bgMode by remember { mutableStateOf(repo.prefManager.getBackgroundMode()) }
    var liquidBackground by remember { mutableStateOf(repo.prefManager.isLiquidBackgroundEnabled()) }
    var homeDiscLayout by remember { mutableStateOf(repo.prefManager.getHomeDiscLayout()) }
    var disc3d by remember { mutableStateOf(repo.prefManager.isDisc3dEnabled()) }
    var disc3dTilt by remember { mutableStateOf(repo.prefManager.isDisc3dTiltEnabled()) }
    var dateDisplayMode by remember { mutableStateOf(repo.prefManager.getDateDisplayMode()) }
    // 配色をアートワークに合わせるONのときは、画面側で算出されたアートワーク由来の
    // アクセント色（accentColor引数）を設定UIの色にも使う。OFFのときは手動色
    val currentAccentColor = if (matchArtwork) accentColor else parseHexColor(accentHex)

    var iconSize by remember { mutableFloatStateOf(repo.prefManager.getAppDiscIconSizeDp()) }
    var textSize by remember { mutableFloatStateOf(repo.prefManager.getAppDiscTextSizeSp()) }
    var letterSpacing by remember { mutableFloatStateOf(repo.prefManager.getAppDiscLetterSpacingSp()) }
    var stepAngle by remember { mutableFloatStateOf(repo.prefManager.getAppDiscStepAngle()) }
    var backgroundPath by remember { mutableStateOf(repo.prefManager.getCustomBackgroundPath()) }
    var wallpaperAlpha by remember { mutableFloatStateOf(repo.prefManager.getHomeWallpaperAlpha()) }
    var showPasscodeChangeDialog by remember { mutableStateOf(false) }
    var newPasscodeField by remember { mutableStateOf(repo.prefManager.getPasscode()) }

    var isReverseScroll by remember { mutableStateOf(repo.prefManager.isReverseScroll()) }
    var sleepTimerEndMs by remember { mutableStateOf(repo.prefManager.getSleepTimerEndMs()) }
    var sleepTimerMinutes by remember { mutableStateOf(repo.prefManager.getSleepTimerMinutes()) }
    var sleepNowMs by remember { mutableStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            sleepNowMs = System.currentTimeMillis()
            if (sleepTimerEndMs > 0L && sleepTimerEndMs <= sleepNowMs) sleepTimerEndMs = 0L
            delay(20000)
        }
    }
    var usePasscodeForHidden by remember { mutableStateOf(repo.prefManager.isUsePasscodeForHidden()) }
    var currentFontChoice by remember { mutableStateOf(repo.prefManager.getFontChoice()) }
    var lyricsDisplayMode by remember { mutableStateOf(repo.prefManager.getLyricsDisplayMode()) }
    var fontPickerExpanded by remember { mutableStateOf<String?>(null) }
    var lyricsPvFont by remember { mutableStateOf(repo.prefManager.getLyricsPvFont()) }
    var lyricsEnFont by remember { mutableStateOf(repo.prefManager.getLyricsEnFont()) }
    var lyricsEnCustomPath by remember { mutableStateOf(repo.prefManager.getLyricsEnCustomFontPath()) }
    var lyricsBold by remember { mutableStateOf(repo.prefManager.isLyricsBold()) }
    var pvPartsEntry by remember { mutableStateOf(repo.prefManager.isPvPartsEntryEnabled()) }
    var dopagakiMode by remember { mutableStateOf(repo.prefManager.isDopagakiEnabled()) }
    var dopaBlackFont by remember { mutableStateOf(repo.prefManager.isDopaBlackFontEnabled()) }
    var statusBarLyrics by remember { mutableStateOf(repo.prefManager.isStatusBarLyricsEnabled()) }
        var pageSwitchStyle by remember { mutableStateOf(repo.prefManager.getPageSwitchStyle()) }
    var appsUpdateStatus by remember { mutableStateOf("") }
    var isUpdatingApps by remember { mutableStateOf(false) }
    val settingsScope = rememberCoroutineScope()

    val switchColors = SwitchDefaults.colors(
        checkedThumbColor = currentAccentColor,
        checkedTrackColor = currentAccentColor.copy(alpha = 0.4f),
        uncheckedThumbColor = Color.Gray,
        uncheckedTrackColor = Color(0xFF1E283D)
    )

    // システム環境設定風 2段階ナビゲーション ("behavior", "appearance", "security", "tutorial")
    var selectedCategory by remember { mutableStateOf<String?>(null) }
    var updateChecking by remember { mutableStateOf(false) }
    var updateMessage by remember { mutableStateOf<String?>(null) }
    var updateInfo by remember { mutableStateOf<com.example.nissingeppolauncher.data.UpdateChecker.LatestRelease?>(null) }
    var fxChecking by remember { mutableStateOf(false) }
    var fxMessage by remember { mutableStateOf<String?>(null) }
    var showUpdateDialog by remember { mutableStateOf(false) }
    if (showUpdateDialog && updateInfo != null) {
        UpdateDialog(
            info = updateInfo!!,
            onDismiss = {
                showUpdateDialog = false
                com.example.nissingeppolauncher.data.UpdateChecker.markDismissed(context, updateInfo!!.buildNumber)
            }
        )
    }
    // 外観内のサブタブ ("wallpaper", "color", "font")

    var customFontPath by remember { mutableStateOf(repo.prefManager.getCustomFontPath()) }
    var useCustomFont by remember { mutableStateOf(repo.prefManager.isUseCustomFont()) }

    // システム「戻る」キーを押した時、カテゴリ詳細画面なら設定トップに戻る
    BackHandler(enabled = selectedCategory != null) {
        selectedCategory = null
    }

    // 壁紙選択ピッカー
    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            try {
                context.contentResolver.openInputStream(uri)?.use { inputStream ->
                    val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                    BitmapFactory.decodeStream(inputStream, null, options)

                    var sampleSize = 1
                    val maxDimension = 1920
                    while (options.outWidth / sampleSize > maxDimension || options.outHeight / sampleSize > maxDimension) {
                        sampleSize *= 2
                    }

                    val decodeOptions = BitmapFactory.Options().apply {
                        inSampleSize = sampleSize
                        inPreferredConfig = Bitmap.Config.ARGB_8888
                    }

                    context.contentResolver.openInputStream(uri)?.use { stream2 ->
                        val bitmap = BitmapFactory.decodeStream(stream2, null, decodeOptions)
                        if (bitmap != null) {
                            val destFile = File(context.filesDir, "custom_bg.png")
                            FileOutputStream(destFile).use { out ->
                                bitmap.compress(Bitmap.CompressFormat.PNG, 90, out)
                            }
                            bitmap.recycle()
                            val path = destFile.absolutePath
                            repo.prefManager.setCustomBackgroundPath(path)
                            backgroundPath = path
                            onChanged()
                            Toast.makeText(context, "壁紙を変更しました", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            } catch (_: Exception) {
                Toast.makeText(context, "壁紙の読み込みに失敗しました", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // フォントファイル選択ピッカー (.ttf / .otf)
    val fontPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            try {
                context.contentResolver.openInputStream(uri)?.use { inputStream ->
                    val destFile = File(context.filesDir, "custom_font.ttf")
                    FileOutputStream(destFile).use { out ->
                        inputStream.copyTo(out)
                    }
                    val path = destFile.absolutePath
                    repo.prefManager.setCustomFontPath(path)
                    repo.prefManager.setUseCustomFont(true)
                    repo.prefManager.setFontChoice("custom")
                    customFontPath = path
                    useCustomFont = true
                    currentFontChoice = "custom"
                    onChanged()
                    Toast.makeText(context, "カスタムフォントを読み込みました", Toast.LENGTH_SHORT).show()
                }
            } catch (_: Exception) {
                Toast.makeText(context, "フォントの読み込みに失敗しました", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // 英字フォント専用のカスタムフォント選択ピッカー (.ttf / .otf)
    val enFontPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            try {
                context.contentResolver.openInputStream(uri)?.use { inputStream ->
                    val destFile = File(context.filesDir, "custom_font_en.ttf")
                    FileOutputStream(destFile).use { out ->
                        inputStream.copyTo(out)
                    }
                    val path = destFile.absolutePath
                    repo.prefManager.setLyricsEnCustomFontPath(path)
                    repo.prefManager.setLyricsEnFont("custom")
                    lyricsEnCustomPath = path
                    lyricsEnFont = "custom"
                    onChanged()
                    Toast.makeText(context, "英字用カスタムフォントを読み込みました", Toast.LENGTH_SHORT).show()
                }
            } catch (_: Exception) {
                Toast.makeText(context, "フォントの読み込みに失敗しました", Toast.LENGTH_SHORT).show()
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0x330B101D))
            .padding(top = 40.dp)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // --- 上部 タイトルバー ---
            Surface(
                color = Color(0x99101728),
                shape = RoundedCornerShape(20.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 8.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (selectedCategory != null) {
                        Text(
                            text = "‹ 戻る",
                            color = currentAccentColor,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Normal,
                            modifier = Modifier
                                .clickable { selectedCategory = null }
                                .padding(end = 16.dp)
                        )
                    }
                    Text(
                        text = when (selectedCategory) {
                            "behavior" -> "動作設定"
                            "appearance" -> "外観設定"
                            "lyrics" -> "歌詞設定"
                            "apps" -> "アプリ一覧"
                            "security" -> "セキュリティ"
                            "tutorial" -> "チュートリアル"
                            else -> "環境設定"
                        },
                        color = Color.White,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Normal
                    )
                }
            }

            val categories = listOf(
                Triple("behavior", "動作", "ダイヤルのスクロール方向や操作感の設定"),
                Triple("appearance", "外観", "壁紙、配色、フォントなどの見た目のカスタマイズ"),
                Triple("lyrics", "歌詞", "表示モード、フォント、太さの設定"),
                Triple("apps", "アプリ一覧", "アプリ一覧データの差分更新（追加・削除されたアプリの反映）"),
                Triple("security", "セキュリティ", "非表示アプリ用のパスコード(PIN)設定"),
                Triple("tutorial", "チュートリアル", "ガイドや使い方の再表示設定")
            )

            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                contentPadding = PaddingValues(bottom = 240.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                if (selectedCategory == null) {
                    // --- 1段階目: ジャンル選択リスト ---
                    item {
                        SettingsCategoryDisc(
                            categories = categories,
                            accentColor = currentAccentColor,
                            onOpen = { selectedCategory = it }
                        )
                    }
                    // アップデート確認（カテゴリ外の単独行）
                    item {
                        Surface(
                            color = Color(0x99101728),
                            shape = RoundedCornerShape(20.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable(enabled = !updateChecking) {
                                    updateMessage = null
                                    updateChecking = true
                                    settingsScope.launch {
                                        val info = com.example.nissingeppolauncher.data.UpdateChecker.fetchLatest(context)
                                        updateChecking = false
                                        if (info?.isNewer == true) {
                                            updateInfo = info
                                            showUpdateDialog = true
                                        } else if (info == null) {
                                            updateMessage = "確認に失敗しました"
                                        } else {
                                            updateMessage = "新しいビルドはありませんでした（最新: #${info.buildNumber}）"
                                        }
                                    }
                                }
                        ) {
                            Row(
                                modifier = Modifier.padding(20.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text(
                                        text = "アップデートを確認",
                                        color = Color.White,
                                        fontSize = 16.sp,
                                        fontWeight = FontWeight.Normal
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = when {
                                            updateChecking -> "確認中…"
                                            updateMessage != null -> updateMessage!!
                                            else -> "GitHubの最新ビルドを確認します"
                                        },
                                        color = Color.Gray,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Light
                                    )
                                }
                                Text(
                                    text = "›",
                                    color = Color.Gray,
                                    fontSize = 22.sp,
                                    fontWeight = FontWeight.Light
                                )
                            }
                        }
                    }
                } else {
                    // --- 2段階目: 各カテゴリの詳細設定 ---
                    when (selectedCategory) {
                        "lyrics" -> {
                            item {
                                Surface(
                                    color = Color(0x99101728),
                                    shape = RoundedCornerShape(20.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(
                                        modifier = Modifier.padding(20.dp),
                                        verticalArrangement = Arrangement.spacedBy(16.dp)
                                    ) {
                                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                            Text("歌詞の表示モード", color = Color.White, fontSize = 14.sp)
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                                            ) {
                                                LyricsModePreviewCard(
                                                    mode = "line",
                                                    label = "現在の行",
                                                    selected = lyricsDisplayMode == "line",
                                                    accentColor = currentAccentColor,
                                                    modifier = Modifier.weight(1f)
                                                ) {
                                                    lyricsDisplayMode = "line"
                                                    repo.prefManager.setLyricsDisplayMode("line")
                                                    onChanged()
                                                }
                                                LyricsModePreviewCard(
                                                    mode = "video",
                                                    label = "リリックビデオ",
                                                    selected = lyricsDisplayMode == "video",
                                                    accentColor = currentAccentColor,
                                                    modifier = Modifier.weight(1f)
                                                ) {
                                                    lyricsDisplayMode = "video"
                                                    repo.prefManager.setLyricsDisplayMode("video")
                                                    onChanged()
                                                }
                                                LyricsModePreviewCard(
                                                    mode = "multi",
                                                    label = "複数行",
                                                    selected = lyricsDisplayMode == "multi",
                                                    accentColor = currentAccentColor,
                                                    modifier = Modifier.weight(1f)
                                                ) {
                                                    lyricsDisplayMode = "multi"
                                                    repo.prefManager.setLyricsDisplayMode("multi")
                                                    onChanged()
                                                }
                                            }
                                            Text("複数行は時計の下に前・今・次の3行を表示します。リリックビデオはSpotifyなど他のアプリの再生でも使えます", color = Color.Gray, fontSize = 12.sp)
                                        }
                                    }
                                }
                            }
                            item {
                                FontPickerCard(
                                    title = "歌詞のフォント（全モード共通）",
                                    options = listOf(
                                        "default" to "システムフォント",
                                        "receipt" to "レシートフォント (Receipt Font)",
                                        "denkichip" to "でんきチップ (DenkiChip)",
                                        "tsukuhou" to "築豊明朝 (Tsukuhou Mincho)",
                                        "kaibunsho" to "怪聞書フォント (Kaibunsho)",
                                        "zenkaku" to "Zen Kaku Gothic New (Medium)",
                                        "custom" to "カスタムフォント (.ttf/.otf)"
                                    ),
                                    selected = lyricsPvFont,
                                    accentColor = currentAccentColor,
                                    expanded = fontPickerExpanded == "jp",
                                    onToggle = { fontPickerExpanded = if (fontPickerExpanded == "jp") null else "jp" },
                                    onSelected = { key ->
                                        lyricsPvFont = key
                                        repo.prefManager.setLyricsPvFont(key)
                                        onChanged()
                                    },
                                    fontFor = { key -> parseFontFamily(key, if (key == "custom") customFontPath else null) },
                                    sampleText = "あア 歌詞",
                                    footer = {
                                        Text("「カスタムフォント」はフォント設定で選んだファイルを使います", color = Color.Gray, fontSize = 12.sp)
                                    }
                                )
                            }
                            item {
                                FontPickerCard(
                                    title = "歌詞の英字フォント",
                                    options = listOf(
                                        "same" to "日本語フォントと同じ",
                                        "freelicense" to "Free License",
                                        "montserratbi" to "Montserrat Bold Italic",
                                        "default" to "システムフォント",
                                        "receipt" to "レシートフォント (Receipt Font)",
                                        "denkichip" to "でんきチップ (DenkiChip)",
                                        "tsukuhou" to "築豊明朝 (Tsukuhou Mincho)",
                                        "kaibunsho" to "怪聞書フォント (Kaibunsho)",
                                        "zenkaku" to "Zen Kaku Gothic New (Medium)",
                                        "custom" to "カスタムフォント (.ttf/.otf)"
                                    ),
                                    selected = lyricsEnFont,
                                    accentColor = currentAccentColor,
                                    expanded = fontPickerExpanded == "en",
                                    onToggle = { fontPickerExpanded = if (fontPickerExpanded == "en") null else "en" },
                                    onSelected = { key ->
                                        lyricsEnFont = key
                                        repo.prefManager.setLyricsEnFont(key)
                                        onChanged()
                                    },
                                    fontFor = { key ->
                                        when (key) {
                                            "same" -> parseFontFamily(lyricsPvFont, if (lyricsPvFont == "custom") customFontPath else null)
                                            "custom" -> parseFontFamily("custom", lyricsEnCustomPath)
                                            else -> parseFontFamily(key, null)
                                        }
                                    },
                                    sampleText = "ABC abc",
                                    footer = if (lyricsEnFont == "custom") {
                                        {
                                            Button(
                                                onClick = { enFontPickerLauncher.launch("*/*") },
                                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1F293D)),
                                                modifier = Modifier.fillMaxWidth()
                                            ) {
                                                Text("英字用フォントファイル (.ttf / .otf) をインポート", color = Color.White, fontWeight = FontWeight.Light)
                                            }
                                            Text("英字フォント専用のファイルです（日本語用とは別に選べます）", color = Color.Gray, fontSize = 12.sp)
                                        }
                                    } else null
                                )
                            }
                            item {
                                Surface(
                                    color = Color(0x99101728),
                                    shape = RoundedCornerShape(20.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(
                                        modifier = Modifier.padding(20.dp),
                                        verticalArrangement = Arrangement.spacedBy(16.dp)
                                    ) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text("歌詞を太字にする", color = Color.White, fontSize = 14.sp)
                                                Text("デフォルトは細字。築豊明朝は細字が映えます", color = Color.Gray, fontSize = 12.sp)
                                            }
                                            Switch(
                                                checked = lyricsBold,
                                                onCheckedChange = {
                                                    lyricsBold = it
                                                    repo.prefManager.setLyricsBold(it)
                                                    onChanged()
                                                },
                                                colors = switchColors
                                            )
                                        }
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text("文字をパーツに分けて入場させる", color = Color.White, fontSize = 14.sp)
                                                Text("リリックビデオで、文字がスライス状のパーツになって集まります", color = Color.Gray, fontSize = 12.sp)
                                            }
                                            Switch(
                                                checked = pvPartsEntry,
                                                onCheckedChange = {
                                                    pvPartsEntry = it
                                                    repo.prefManager.setPvPartsEntryEnabled(it)
                                                    onChanged()
                                                },
                                                colors = switchColors
                                            )
                                        }
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text("ドパガキモード", color = Color.White, fontSize = 14.sp)
                                                Text("リリックビデオにエフェクトを大量に重ねて派手にします", color = Color.Gray, fontSize = 12.sp)
                                            }
                                            Switch(
                                                checked = dopagakiMode,
                                                onCheckedChange = {
                                                    dopagakiMode = it
                                                    repo.prefManager.setDopagakiEnabled(it)
                                                    onChanged()
                                                },
                                                colors = switchColors
                                            )
                                        }
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text("ドパガキ中は極太フォント", color = Color.White, fontSize = 14.sp)
                                                Text("ドパガキモードのとき、歌詞を Zen Kaku Gothic New の極太（Black）で表示します", color = Color.Gray, fontSize = 12.sp)
                                            }
                                            Switch(
                                                checked = dopaBlackFont,
                                                onCheckedChange = {
                                                    dopaBlackFont = it
                                                    repo.prefManager.setDopaBlackFontEnabled(it)
                                                    onChanged()
                                                },
                                                colors = switchColors
                                            )
                                        }
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text("ステータスバーに歌詞を表示", color = Color.White, fontSize = 14.sp)
                                                Text("他のアプリの上にも、時計と同期歌詞1行を重ねます（重ねて表示の権限が必要）", color = Color.Gray, fontSize = 12.sp)
                                            }
                                            Switch(
                                                checked = statusBarLyrics,
                                                onCheckedChange = { checked ->
                                                    statusBarLyrics = checked
                                                    repo.prefManager.setStatusBarLyricsEnabled(checked)
                                                    if (checked) {
                                                        if (Settings.canDrawOverlays(context)) {
                                                            LyricsOverlayService.start(context)
                                                        } else {
                                                            val intent = Intent(
                                                                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                                                Uri.parse("package:" + context.packageName)
                                                            )
                                                            try {
                                                                context.startActivity(intent)
                                                            } catch (e: Exception) {
                                                                // 権限画面を開けない場合は何もしない
                                                            }
                                                        }
                                                    } else {
                                                        LyricsOverlayService.stop(context)
                                                    }
                                                    onChanged()
                                                },
                                                colors = switchColors
                                            )
                                        }
                                    }
                                }
                            }
                            item {
                                Surface(
                                    color = Color(0x99101728),
                                    shape = RoundedCornerShape(20.dp),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable(enabled = !fxChecking) {
                                            fxMessage = null
                                            fxChecking = true
                                            settingsScope.launch {
                                                val result = LyricEffectsData.refresh(context)
                                                fxChecking = false
                                                fxMessage = when (result) {
                                                    1 -> "更新しました（データ v${LyricEffectsData.appliedVersion}）"
                                                    0 -> "最新のデータです（データ v${LyricEffectsData.appliedVersion}）"
                                                    else -> "確認に失敗しました" + (LyricEffectsData.lastRefreshError?.let { "（$it）" } ?: "")
                                                }
                                            }
                                        }
                                ) {
                                    Row(
                                        modifier = Modifier.padding(20.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column {
                                            Text(
                                                text = "歌詞エフェクトの更新",
                                                color = Color.White,
                                                fontSize = 16.sp,
                                                fontWeight = FontWeight.Normal
                                            )
                                            Spacer(modifier = Modifier.height(4.dp))
                                            Text(
                                                text = when {
                                                    fxChecking -> "確認中…"
                                                    fxMessage != null -> fxMessage!!
                                                    else -> "単語アイコンや色の対応表を最新にします"
                                                },
                                                color = Color.Gray,
                                                fontSize = 13.sp,
                                                fontWeight = FontWeight.Light
                                            )
                                        }
                                        Text(
                                            text = "›",
                                            color = Color.Gray,
                                            fontSize = 22.sp,
                                            fontWeight = FontWeight.Light
                                        )
                                    }
                                }
                            }
                        }
                        "behavior" -> {
                            item {
                                Surface(
                                    color = Color(0x99101728),
                                    shape = RoundedCornerShape(20.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(
                                        modifier = Modifier.padding(20.dp),
                                        verticalArrangement = Arrangement.spacedBy(16.dp)
                                    ) {
                                        Text("ダイヤルの操作設定", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Normal)

                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text("スクロール方向の反転", color = Color.White, fontSize = 14.sp)
                                                Text("有効にすると、ダイヤルをドラッグした際の回転方向が逆になります", color = Color.Gray, fontSize = 12.sp)
                                            }
                                            Switch(
                                                checked = isReverseScroll,
                                                onCheckedChange = {
                                                    isReverseScroll = it
                                                    repo.prefManager.setReverseScroll(it)
                                                    onChanged()
                                                },
                                                colors = switchColors
                                            )
                                        }
                                    }
                                }
                            }
                            item {
                                Surface(
                                    color = Color(0x99101728),
                                    shape = RoundedCornerShape(20.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(
                                        modifier = Modifier.padding(20.dp),
                                        verticalArrangement = Arrangement.spacedBy(16.dp)
                                    ) {
                                        Text("スリープタイマー", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Normal)
                                        Text("設定した時間が過ぎたら、再生中の音楽を自動で一時停止します", color = Color.Gray, fontSize = 12.sp)
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            for (min in listOf(0, 15, 30, 60)) {
                                                val selected = if (min == 0) sleepTimerEndMs <= 0L else (sleepTimerEndMs > sleepNowMs && sleepTimerMinutes == min)
                                                Surface(
                                                    onClick = {
                                                        sleepTimerMinutes = min
                                                        repo.prefManager.setSleepTimerMinutes(min)
                                                        val end = if (min == 0) 0L else System.currentTimeMillis() + min * 60000L
                                                        sleepTimerEndMs = end
                                                        repo.prefManager.setSleepTimerEndMs(end)
                                                        onChanged()
                                                    },
                                                    color = if (selected) currentAccentColor else Color(0xFF1F293D),
                                                    shape = RoundedCornerShape(12.dp),
                                                    modifier = Modifier.weight(1f)
                                                ) {
                                                    Text(
                                                        text = if (min == 0) "オフ" else "${min}分",
                                                        color = Color.White,
                                                        fontSize = 13.sp,
                                                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                                        modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp)
                                                    )
                                                }
                                            }
                                        }
                                        if (sleepTimerEndMs > sleepNowMs) {
                                            Text("あと約 ${(sleepTimerEndMs - sleepNowMs + 59999L) / 60000L} 分で一時停止します", color = Color.Gray, fontSize = 12.sp)
                                        }
                                    }
                                }
                            }
                        }
                                                "apps" -> {
                            item {
                                Surface(
                                    color = Color(0x99101728),
                                    shape = RoundedCornerShape(20.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(
                                        modifier = Modifier.padding(20.dp),
                                        verticalArrangement = Arrangement.spacedBy(16.dp)
                                    ) {
                                        Text("アプリデータの更新", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Normal)
                                        Text("追加・削除・更新されたアプリをチェックして、一覧に反映します（差分更新）。通常は起動時とアプリのインストール時に自動でチェックされるため、手動更新が要るのは表示が古いと感じたときなどです。", color = Color.Gray, fontSize = 12.sp)
                                        Button(
                                            onClick = {
                                                if (!isUpdatingApps) {
                                                    isUpdatingApps = true
                                                    appsUpdateStatus = "更新中…"
                                                    settingsScope.launch {
                                                        val apps = repo.getInstalledApps(forceRefresh = true)
                                                        appsUpdateStatus = "更新しました（全 ${apps.size} 件）"
                                                        isUpdatingApps = false
                                                        onAppsUpdated()
                                                    }
                                                }
                                            },
                                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1F293D)),
                                            elevation = null
                                        ) {
                                            Text("アプリ一覧を更新", color = Color.White, fontWeight = FontWeight.Light)
                                        }
                                        if (appsUpdateStatus.isNotEmpty()) {
                                            Text(appsUpdateStatus, color = Color.Gray, fontSize = 13.sp)
                                        }
                                    }
                                }
                            }
                        }
                        "appearance" -> {
                            item {
                                Surface(
                                    color = Color(0x99101728),
                                    shape = RoundedCornerShape(20.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 20.dp, vertical = 14.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text("ボタンでページ切り替え", color = Color.White, fontSize = 15.sp)
                                            Text("オフにすると下部のダイヤルで切り替えます", color = Color.Gray, fontSize = 12.sp)
                                        }
                                        Switch(
                                            checked = pageSwitchStyle == "buttons",
                                            onCheckedChange = { useButtons ->
                                                pageSwitchStyle = if (useButtons) "buttons" else "dial"
                                                repo.prefManager.setPageSwitchStyle(pageSwitchStyle)
                                                onChanged()
                                            },
                                            colors = SwitchDefaults.colors(
                                                checkedThumbColor = Color.White,
                                                checkedTrackColor = currentAccentColor,
                                                uncheckedThumbColor = Color.White,
                                                uncheckedTrackColor = Color(0xFF3A4358)
                                            )
                                        )
                                    }
                                }
                            }
                            item {
                                Surface(
                                    color = Color(0x99101728),
                                    shape = RoundedCornerShape(20.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(
                                        modifier = Modifier.padding(20.dp),
                                        verticalArrangement = Arrangement.spacedBy(12.dp)
                                    ) {
                                        Text("ホーム画面の円の大きさ", color = Color.White, fontSize = 15.sp)
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            listOf(
                                                Triple("classic", "従来（均等）", "disc_classic"),
                                                Triple("artwork", "アートワーク大きめ", "disc_artwork"),
                                                Triple("auto", "自動", "disc_auto")
                                            ).forEach { (value, label, illo) ->
                                                IllustratedChoiceCard(
                                                    kind = illo,
                                                    label = label,
                                                    selected = homeDiscLayout == value,
                                                    accentColor = currentAccentColor,
                                                    modifier = Modifier.weight(1f)
                                                ) {
                                                    homeDiscLayout = value
                                                    repo.prefManager.setHomeDiscLayout(value)
                                                    onChanged()
                                                }
                                            }
                                        }
                                        Text(
                                            "「アートワーク大きめ」では、再生アートワークの円が大きくなり、右の円のお気に入りアプリ名は非表示になります。「自動」では、音楽の再生中だけ大きめレイアウトに切り替わります",
                                            color = Color.Gray,
                                            fontSize = 12.sp
                                        )
                                    }
                                }
                            }
                            item {
                                Surface(
                                    color = Color(0x99101728),
                                    shape = RoundedCornerShape(20.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(
                                        modifier = Modifier.padding(20.dp),
                                        verticalArrangement = Arrangement.spacedBy(12.dp)
                                    ) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text("UIを3D表示", color = Color.White, fontSize = 15.sp)
                                                Text("円・時計・ダイヤル・歌詞PVなどを少し奥に倒して、立体的に見せます", color = Color.Gray, fontSize = 12.sp)
                                            }
                                            Switch(
                                                checked = disc3d,
                                                onCheckedChange = {
                                                    disc3d = it
                                                    repo.prefManager.setDisc3dEnabled(it)
                                                    onChanged()
                                                },
                                                colors = switchColors
                                            )
                                        }
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text("端末の傾きで角度を変える", color = if (disc3d) Color.White else Color.Gray, fontSize = 14.sp)
                                                Text("スマホを傾けると、3Dの角度もついてきます（3D表示がオンのときだけ）", color = Color.Gray, fontSize = 12.sp)
                                            }
                                            Switch(
                                                checked = disc3dTilt,
                                                onCheckedChange = {
                                                    disc3dTilt = it
                                                    repo.prefManager.setDisc3dTiltEnabled(it)
                                                    onChanged()
                                                },
                                                colors = switchColors,
                                                enabled = disc3d
                                            )
                                        }
                                    }
                                }
                            }
                            item {
                                Surface(
                                    color = Color(0x99101728),
                                    shape = RoundedCornerShape(20.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(
                                        modifier = Modifier.padding(20.dp),
                                        verticalArrangement = Arrangement.spacedBy(12.dp)
                                    ) {
                                        Text("日付の表示", color = Color.White, fontSize = 15.sp)
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            listOf(
                                                Triple("sideways", "常時縦", "date_sideways"),
                                                Triple("under", "常時横", "date_under"),
                                                Triple("auto", "切り替え", "date_auto")
                                            ).forEach { (value, label, illo) ->
                                                IllustratedChoiceCard(
                                                    kind = illo,
                                                    label = label,
                                                    selected = dateDisplayMode == value,
                                                    accentColor = currentAccentColor,
                                                    modifier = Modifier.weight(1f)
                                                ) {
                                                    dateDisplayMode = value
                                                    repo.prefManager.setDateDisplayMode(value)
                                                    onChanged()
                                                }
                                            }
                                        }
                                        Text(
                                            "「常時縦」は日付を時刻の右に横倒しで、「常時横」は時計の下に表示します。「切り替え」は歌詞が出ている間だけ横倒しになります",
                                            color = Color.Gray,
                                            fontSize = 12.sp
                                        )
                                    }
                                }
                            }
                            item {
                                Surface(
                                    color = Color(0x99101728),
                                    shape = RoundedCornerShape(20.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(
                                        modifier = Modifier.padding(20.dp),
                                        verticalArrangement = Arrangement.spacedBy(20.dp)
                                    ) {
                                        Text("壁紙画像", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Normal)
                                        Column {
                                            Text("背景の見せ方", color = Color.White, fontSize = 14.sp)
                                            Text("アートワークを選ぶと、再生中は曲のアートワーク由来の背景になります", color = Color.Gray, fontSize = 12.sp)
                                        }
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            listOf(
                                                Triple("wallpaper", "壁紙", "bg_wallpaper"),
                                                Triple("artwork_solid", "単色", "bg_solid"),
                                                Triple("artwork_blur", "ぼかし", "bg_blur")
                                            ).forEach { (value, label, illo) ->
                                                IllustratedChoiceCard(
                                                    kind = illo,
                                                    label = label,
                                                    selected = bgMode == value,
                                                    accentColor = currentAccentColor,
                                                    modifier = Modifier.weight(1f)
                                                ) {
                                                    bgMode = value
                                                    repo.prefManager.setBackgroundMode(value)
                                                    onChanged()
                                                }
                                            }
                                        }
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text("液体グラデーション背景", color = Color.White, fontSize = 14.sp)
                                                Text("壁紙がないときや再生中のアートワーク背景の上で、グラデーションがゆっくり動きます", color = Color.Gray, fontSize = 12.sp)
                                            }
                                            Switch(
                                                checked = liquidBackground,
                                                onCheckedChange = {
                                                    liquidBackground = it
                                                    repo.prefManager.setLiquidBackgroundEnabled(it)
                                                    onChanged()
                                                },
                                                colors = switchColors
                                            )
                                        }
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Button(
                                                onClick = { imagePickerLauncher.launch("image/*") },
                                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1F293D)),
                                                elevation = null
                                            ) {
                                                Text("画像を選択", color = Color.White, fontWeight = FontWeight.Light)
                                            }
                                            if (backgroundPath != null) {
                                                OutlinedButton(
                                                    onClick = {
                                                        repo.prefManager.setCustomBackgroundPath(null)
                                                        backgroundPath = null
                                                        onChanged()
                                                    },
                                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.Red),
                                                    border = null
                                                ) {
                                                    Text("解除", fontWeight = FontWeight.Light)
                                                }
                                            }
                                        }

                                        HorizontalDivider(color = Color.White.copy(alpha = 0.1f))

                                        Column {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween
                                            ) {
                                                Text("ホーム背景の明るさ", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Light)
                                                Text("${((1f - wallpaperAlpha) * 100).toInt()}%", color = Color.LightGray, fontSize = 14.sp, fontWeight = FontWeight.Light)
                                            }
                                            Spacer(modifier = Modifier.height(8.dp))
                                            SimpleTickSlider(
                                                value = wallpaperAlpha,
                                                onValueChange = {
                                                    wallpaperAlpha = it
                                                    repo.prefManager.setHomeWallpaperAlpha(it)
                                                    onChanged()
                                                },
                                                valueRange = 0.05f..0.9f,
                                                steps = 17,
                                                accentColor = currentAccentColor
                                            )
                                        }
                                    }
                                }
                            }
                            item {
                                Surface(
                                    color = Color(0x99101728),
                                    shape = RoundedCornerShape(20.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(
                                        modifier = Modifier.padding(20.dp),
                                        verticalArrangement = Arrangement.spacedBy(16.dp)
                                    ) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text("アートワークに合わせる", color = Color.White, fontSize = 14.sp)
                                                Text("再生中の曲のアートワークから、アクセントと背景の色を自動で決めます", color = Color.Gray, fontSize = 12.sp)
                                            }
                                            Switch(
                                                checked = matchArtwork,
                                                onCheckedChange = {
                                                    matchArtwork = it
                                                    repo.prefManager.setMatchArtworkColor(it)
                                                    onChanged()
                                                },
                                                colors = switchColors
                                            )
                                        }
                                        Text("アクセントカラー", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Normal)
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            ACCENT_PRESETS.forEach { (_, hex) ->
                                                val color = parseHexColor(hex)
                                                val isSelected = accentHex.equals(hex, ignoreCase = true)
                                                Box(
                                                    modifier = Modifier
                                                        .size(36.dp)
                                                        .clip(CircleShape)
                                                        .background(color)
                                                        .clickable {
                                                            accentHex = hex
                                                            repo.prefManager.setAccentColorHex(hex)
                                                            onChanged()
                                                        },
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    if (isSelected) {
                                                        Box(
                                                            modifier = Modifier
                                                                .size(14.dp)
                                                                .clip(CircleShape)
                                                                .background(Color.White)
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                            item {
                                Surface(
                                    color = Color(0x99101728),
                                    shape = RoundedCornerShape(20.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(
                                        modifier = Modifier.padding(20.dp),
                                        verticalArrangement = Arrangement.spacedBy(20.dp)
                                    ) {
                                        FontPickerCard(
                                            title = "UIフォント",
                                            options = listOf(
                                                "default" to "システムフォント",
                                                "receipt" to "レシートフォント (Receipt Font)",
                                                "denkichip" to "でんきチップ (DenkiChip)",
                                                "tsukuhou" to "築豊明朝 (Tsukuhou Mincho)",
                                                "kaibunsho" to "怪聞書フォント (Kaibunsho)",
                                                "zenkaku" to "Zen Kaku Gothic New (Medium)",
                                                "custom" to "カスタムフォント (.ttf/.otf)"
                                            ),
                                            selected = currentFontChoice,
                                            accentColor = currentAccentColor,
                                            expanded = fontPickerExpanded == "ui",
                                            onToggle = { fontPickerExpanded = if (fontPickerExpanded == "ui") null else "ui" },
                                            onSelected = { newChoice ->
                                                currentFontChoice = newChoice
                                                repo.prefManager.setFontChoice(newChoice)
                                                useCustomFont = newChoice == "custom"
                                                repo.prefManager.setUseCustomFont(newChoice == "custom")
                                                onChanged()
                                            },
                                            fontFor = { key -> parseFontFamily(key, if (key == "custom") customFontPath else null) },
                                            sampleText = "あア 12:34",
                                            footer = {
                                                Button(
                                                    onClick = { fontPickerLauncher.launch("*/*") },
                                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1F293D)),
                                                    modifier = Modifier.fillMaxWidth()
                                                ) {
                                                    Text("フォントファイル (.ttf / .otf) をインポート", color = Color.White, fontWeight = FontWeight.Light)
                                                }
                                            }
                                        )

                                        HorizontalDivider(color = Color.White.copy(alpha = 0.1f))

                                        // 1. アイコンサイズ
                                        Column {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween
                                            ) {
                                                Text("アイコンサイズ", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Light)
                                                Text("${iconSize.toInt()} dp", color = Color.LightGray, fontSize = 14.sp, fontWeight = FontWeight.Light)
                                            }
                                            Spacer(modifier = Modifier.height(6.dp))
                                            SimpleTickSlider(
                                                value = iconSize,
                                                onValueChange = {
                                                    iconSize = it
                                                    repo.prefManager.setAppDiscIconSizeDp(it)
                                                    onChanged()
                                                },
                                                valueRange = 16f..32f,
                                                steps = 15,
                                                accentColor = currentAccentColor
                                            )
                                        }

                                        // 2. テキストサイズ
                                        Column {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween
                                            ) {
                                                Text("テキストサイズ", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Light)
                                                Text("${textSize.toInt()} sp", color = Color.LightGray, fontSize = 14.sp, fontWeight = FontWeight.Light)
                                            }
                                            Spacer(modifier = Modifier.height(6.dp))
                                            SimpleTickSlider(
                                                value = textSize,
                                                onValueChange = {
                                                    textSize = it
                                                    repo.prefManager.setAppDiscTextSizeSp(it)
                                                    onChanged()
                                                },
                                                valueRange = 12f..22f,
                                                steps = 9,
                                                accentColor = currentAccentColor
                                            )
                                        }

                                        // 3. 文字間隔
                                        Column {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween
                                            ) {
                                                Text("文字間隔", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Light)
                                                Text(String.format(java.util.Locale.ROOT, "%.1f sp", letterSpacing), color = Color.LightGray, fontSize = 14.sp, fontWeight = FontWeight.Light)
                                            }
                                            Spacer(modifier = Modifier.height(6.dp))
                                            SimpleTickSlider(
                                                value = letterSpacing,
                                                onValueChange = {
                                                    letterSpacing = it
                                                    repo.prefManager.setAppDiscLetterSpacingSp(it)
                                                    onChanged()
                                                },
                                                valueRange = 0.5f..3.0f,
                                                steps = 25,
                                                accentColor = currentAccentColor
                                            )
                                        }

                                        // 4. リストの間隔
                                        Column {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween
                                            ) {
                                                Text("リストの間隔", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Light)
                                                Text(String.format(java.util.Locale.ROOT, "%.1f°", stepAngle), color = Color.LightGray, fontSize = 14.sp, fontWeight = FontWeight.Light)
                                            }
                                            Spacer(modifier = Modifier.height(6.dp))
                                            SimpleTickSlider(
                                                value = stepAngle,
                                                onValueChange = {
                                                    stepAngle = it
                                                    repo.prefManager.setAppDiscStepAngle(it)
                                                    onChanged()
                                                },
                                                valueRange = 10f..20f,
                                                steps = 20,
                                                accentColor = currentAccentColor
                                            )
                                        }
                                    }
                                }
                            }
                        }
                        "security" -> {
                            item {
                                Surface(
                                    color = Color(0x99101728),
                                    shape = RoundedCornerShape(20.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(
                                        modifier = Modifier.padding(20.dp),
                                        verticalArrangement = Arrangement.spacedBy(20.dp)
                                    ) {
                                        Text("セキュリティ設定", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Normal)

                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text("非表示アプリのパスコード認証", color = Color.White, fontSize = 14.sp)
                                                Text("有効にすると、非表示リストを開く際にパスコード認証を行います", color = Color.Gray, fontSize = 12.sp)
                                            }
                                            Switch(
                                                checked = usePasscodeForHidden,
                                                onCheckedChange = {
                                                    usePasscodeForHidden = it
                                                    repo.prefManager.setUsePasscodeForHidden(it)
                                                    onChanged()
                                                },
                                                colors = switchColors
                                            )
                                        }

                                        Column {
                                            Text("パスコード (PIN)", color = Color.White, fontSize = 14.sp)
                                            Spacer(modifier = Modifier.height(8.dp))
                                            OutlinedButton(
                                                onClick = {
                                                    newPasscodeField = repo.prefManager.getPasscode()
                                                    showPasscodeChangeDialog = true
                                                },
                                                modifier = Modifier.fillMaxWidth(),
                                                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White)
                                            ) {
                                                Text("パスコードを変更する")
                                            }
                                        }
                                    }
                                }
                            }
                        }
                        "tutorial" -> {
                            item {
                                Surface(
                                    color = Color(0x99101728),
                                    shape = RoundedCornerShape(20.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(
                                        modifier = Modifier.padding(20.dp),
                                        verticalArrangement = Arrangement.spacedBy(20.dp)
                                    ) {
                                        Text("チュートリアル設定", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Normal)

                                        Column {
                                            Text("ガイドの再表示", color = Color.White, fontSize = 14.sp)
                                            Spacer(modifier = Modifier.height(8.dp))
                                            OutlinedButton(
                                                onClick = {
                                                    repo.prefManager.setFirstWelcomeTutorialShown(false)
                                                    repo.prefManager.setFirstIndexTutorialShown(false)
                                                    repo.prefManager.setFirstHideTutorialShown(false)
                                                    Toast.makeText(context, "チュートリアルを再表示するように設定しました", Toast.LENGTH_SHORT).show()
                                                    onChanged()
                                                },
                                                modifier = Modifier.fillMaxWidth(),
                                                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White)
                                            ) {
                                                Text("チュートリアルを再表示する")
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        if (showPasscodeChangeDialog) {
            AlertDialog(
                onDismissRequest = { showPasscodeChangeDialog = false },
                confirmButton = {
                    TextButton(
                        onClick = {
                            if (newPasscodeField.isNotBlank()) {
                                repo.prefManager.setPasscode(newPasscodeField)
                                showPasscodeChangeDialog = false
                            }
                        }
                    ) {
                        Text("保存", color = currentAccentColor, fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showPasscodeChangeDialog = false }) {
                        Text("キャンセル", color = Color.Gray)
                    }
                },
                title = { Text("パスコードの変更 (PIN)") },
                text = {
                    Column {
                        Text("非表示リスト解除用の新しいパスコードを入力してください。", color = Color.LightGray, fontSize = 13.sp)
                        Spacer(modifier = Modifier.height(12.dp))
                        OutlinedTextField(
                            value = newPasscodeField,
                            onValueChange = { newPasscodeField = it },
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White,
                                focusedBorderColor = currentAccentColor,
                                unfocusedBorderColor = Color.Gray
                            )
                        )
                    }
                },
                containerColor = Color(0xFF141C2E),
                titleContentColor = Color.White
            )
        }
    }
}

