package com.example.nissingeppolauncher.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

// 2つの円で階層を移動する共通コンポーネント（フォルダページと同じ骨格）。
// 左の円・右の円どちらも「縁に項目が並び、ドラッグで回って、真ん中の高さに来た項目がフォーカス」。
// フォーカス中の項目をタップすると決定（onEntryClick）、それ以外はタップでその項目まで回る。

data class DiscEntry(
    val id: String,
    val label: String,
    val checked: Boolean = false,
    val valueText: String? = null,
    val icon: @Composable () -> Unit = {}
)

enum class DiscSide { LEFT, RIGHT }

@Composable
fun DiscWheel(
    entries: List<DiscEntry>,
    side: DiscSide,
    accentColor: Color,
    modifier: Modifier = Modifier,
    initialFocus: Int = 0,
    onFocusChange: (Int) -> Unit = {},
    onEntryClick: (Int) -> Unit = {},
    centerContent: (@Composable BoxScope.() -> Unit)? = null
) {
    val safeSize = entries.size.coerceAtLeast(1)
    val scope = rememberCoroutineScope()
    val haptics = LocalHapticFeedback.current
    val anim = remember(safeSize) {
        Animatable(initialFocus.coerceIn(0, safeSize - 1).toFloat())
    }
    val focused = anim.value.roundToInt().coerceIn(0, safeSize - 1)
    LaunchedEffect(focused) {
        onFocusChange(focused)
        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
    }
    fun snapToNearest() {
        val target = anim.value.roundToInt().coerceIn(0, safeSize - 1).toFloat()
        scope.launch { anim.animateTo(target, spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessLow)) }
    }
    BoxWithConstraints(modifier = modifier) {
        val wDp = maxWidth.value
        val hDp = maxHeight.value
        val rDp = 165f
        // 配置はランチャーのフォルダ/アプリページと同一: 中心は画面の外80dp、半径165dp
        val centerX = if (side == DiscSide.LEFT) -80f else wDp + 80f
        val centerY = hDp / 2f
        Canvas(modifier = Modifier.fillMaxSize()) {
            val c = Offset(centerX.dp.toPx(), centerY.dp.toPx())
            drawCircle(color = Color.White.copy(alpha = 0.40f), radius = rDp.dp.toPx(), center = c, style = Stroke(width = 1.5.dp.toPx()))
            drawCircle(color = Color.White.copy(alpha = 0.13f), radius = (rDp - 27f).dp.toPx(), center = c, style = Stroke(width = 1.dp.toPx()))
        }
        // ドラッグで回す層（項目の下）。左右の円を重ねて使うため、自分の側の半分だけを受ける
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .fillMaxWidth(0.55f)
                .align(if (side == DiscSide.LEFT) Alignment.CenterStart else Alignment.CenterEnd)
                .pointerInput(entries.size) {
                    val spacingPx = 58.dp.toPx()
                    detectDragGestures(
                        onDragEnd = { snapToNearest() },
                        onDragCancel = { snapToNearest() }
                    ) { change, dragAmount ->
                        change.consume()
                        scope.launch {
                            anim.snapTo((anim.value - dragAmount.y / spacingPx).coerceIn(-0.45f, safeSize - 0.55f))
                        }
                    }
                }
        )
        // 項目（縁の上にアイコン＋ラベル）
        entries.forEachIndexed { i, entry ->
            val rel = i - anim.value
            val yOff = rel * 58f
            if (kotlin.math.abs(yOff) > rDp - 16f) return@forEachIndexed
            val arcX = sqrt((rDp * rDp - yOff * yOff).coerceAtLeast(0f))
            val iconSize = if (i == focused) 52f else 45f
            val iconCenterX = if (side == DiscSide.LEFT) centerX + arcX else centerX - arcX
            val fade = (1f - kotlin.math.abs(rel) / 3.4f).coerceIn(0.12f, 1f)
            Box(
                modifier = Modifier
                    .offset(x = (iconCenterX - iconSize / 2f).dp, y = (centerY + yOff - iconSize / 2f).dp)
                    .size(iconSize.dp)
                    .clickable {
                        if (i == focused) onEntryClick(i)
                        else scope.launch { anim.animateTo(i.toFloat(), spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessLow)) }
                    },
                contentAlignment = Alignment.Center
            ) {
                Box(modifier = Modifier.graphicsLayer { val sc = if (i == focused) 1.12f else 1f; scaleX = sc; scaleY = sc }) {
                    entry.icon()
                }
            }
            // ラベル（左円はアイコンの右、右円はアイコンの左に置く）
            val labelW = 168f
            val labelX = if (side == DiscSide.LEFT) iconCenterX + iconSize / 2f + 10f else iconCenterX - iconSize / 2f - 10f - labelW
            Box(
                modifier = Modifier
                    .offset(x = labelX.dp, y = (centerY + yOff - 22f).dp)
                    .width(labelW.dp)
            ) {
                Column(
                    horizontalAlignment = if (side == DiscSide.LEFT) Alignment.Start else Alignment.End
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (side == DiscSide.RIGHT && entry.checked) {
                            Text("✓ ", color = accentColor.copy(alpha = fade), fontSize = 14.sp, fontWeight = FontWeight.Bold)
                        }
                        Text(
                            entry.label,
                            color = (if (i == focused) accentColor else Color.White).copy(alpha = if (i == focused) 1f else fade),
                            fontSize = if (i == focused) 15.sp else 13.5.sp,
                            fontWeight = if (i == focused) FontWeight.Bold else FontWeight.Normal,
                            maxLines = 1
                        )
                        if (side == DiscSide.LEFT && entry.checked) {
                            Text(" ✓", color = accentColor.copy(alpha = fade), fontSize = 14.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                    if (entry.valueText != null) {
                        Text(entry.valueText, color = Color.White.copy(alpha = 0.55f * fade + 0.2f), fontSize = 11.sp, maxLines = 1)
                    }
                }
            }
        }
        // 円の中央のプレビュー（主に右円用）
        if (centerContent != null) {
            val previewCx = if (side == DiscSide.RIGHT) centerX - 128f else centerX
            Box(
                modifier = Modifier
                    .offset(x = (previewCx - 52f).dp, y = (centerY - 52f).dp)
                    .size(104.dp),
                contentAlignment = Alignment.Center,
                content = centerContent
            )
        }
    }
}

// スライダー用の円: 目盛り付きのリングで値を決め、「決定」で保存する
@Composable
fun DiscGauge(
    title: String,
    initialValue: Float,
    range: ClosedFloatingPointRange<Float>,
    steps: Int,
    format: (Float) -> String,
    accentColor: Color,
    modifier: Modifier = Modifier,
    onConfirm: (Float) -> Unit
) {
    val stepSize = (range.endInclusive - range.start) / (steps + 1)
    fun snap(v: Float): Float =
        (range.start + Math.round((v - range.start) / stepSize) * stepSize).coerceIn(range.start, range.endInclusive)
    var value by remember { mutableFloatStateOf(snap(initialValue)) }
    val frac = (value - range.start) / (range.endInclusive - range.start)
    BoxWithConstraints(modifier = modifier) {
        val wDp = maxWidth.value
        val hDp = maxHeight.value
        val rDp = 165f
        val centerX = wDp + 80f
        val centerY = hDp / 2f
        Canvas(modifier = Modifier.fillMaxSize()) {
            val c = Offset(centerX.dp.toPx(), centerY.dp.toPx())
            drawCircle(color = Color.White.copy(alpha = 0.40f), radius = rDp.dp.toPx(), center = c, style = Stroke(width = 1.5.dp.toPx()))
            // 目盛り（経過時間のスケールのように60分割、5つごとに長く）
            for (t in 0 until 60) {
                val ang = Math.toRadians((t * 6 - 90).toDouble())
                val major = t % 5 == 0
                val r1 = rDp - if (major) 20f else 13f
                val r2 = rDp - 6f
                drawLine(
                    color = Color.White.copy(alpha = if (major) 0.55f else 0.28f),
                    start = Offset(c.x + (r1.dp.toPx() * cos(ang)).toFloat(), c.y + (r1.dp.toPx() * sin(ang)).toFloat()),
                    end = Offset(c.x + (r2.dp.toPx() * cos(ang)).toFloat(), c.y + (r2.dp.toPx() * sin(ang)).toFloat()),
                    strokeWidth = if (major) 2.dp.toPx() else 1.2.dp.toPx(),
                    cap = StrokeCap.Round
                )
            }
            // 値の弧
            drawArc(
                color = accentColor,
                startAngle = -90f,
                sweepAngle = frac * 360f,
                useCenter = false,
                topLeft = Offset(c.x - (rDp - 34f).dp.toPx(), c.y - (rDp - 34f).dp.toPx()),
                size = Size((rDp - 34f).dp.toPx() * 2f, (rDp - 34f).dp.toPx() * 2f),
                style = Stroke(width = 6.dp.toPx(), cap = StrokeCap.Round)
            )
        }
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .fillMaxWidth(0.55f)
                .align(Alignment.CenterEnd)
                .pointerInput(Unit) {
                    val span = range.endInclusive - range.start
                    detectDragGestures { change, dragAmount ->
                        change.consume()
                        value = snap(value - dragAmount.y * span / 280.dp.toPx() * 1f)
                    }
                }
        )
        // 中央: 値と決定ボタン
        Box(
            modifier = Modifier
                .offset(x = (centerX - 128f - 52f).dp, y = (centerY - 52f).dp)
                .size(104.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(format(value), color = Color.White, fontSize = 25.sp, fontWeight = FontWeight.Bold)
                Text(title, color = Color.Gray, fontSize = 11.sp)
                Spacer(modifier = Modifier.height(10.dp))
                Surface(
                    color = accentColor.copy(alpha = 0.25f),
                    shape = RoundedCornerShape(12.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, accentColor),
                    modifier = Modifier.clickable { onConfirm(value) }
                ) {
                    Text("決定", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp))
                }
            }
        }
    }
}

// 設定項目用の小さな線画アイコン（SettingsCategoryIcon と同じ作風）
@Composable
fun DiscGlyph(kind: String, accentColor: Color) {
    Canvas(modifier = Modifier.size(36.dp)) {
        val w = size.width
        val h = size.height
        val ink = Color.White.copy(alpha = 0.9f)
        val thin = Stroke(width = w * 0.055f)
        when (kind) {
            "toggle" -> {
                drawRoundRect(color = ink, topLeft = Offset(w * 0.10f, h * 0.32f), size = Size(w * 0.80f, h * 0.36f), cornerRadius = CornerRadius(w * 0.18f, w * 0.18f), style = thin)
                drawCircle(color = accentColor, radius = w * 0.11f, center = Offset(w * 0.68f, h * 0.50f))
            }
            "slider" -> {
                drawLine(color = ink, start = Offset(w * 0.12f, h * 0.50f), end = Offset(w * 0.88f, h * 0.50f), strokeWidth = w * 0.05f, cap = StrokeCap.Round)
                drawCircle(color = accentColor, radius = w * 0.12f, center = Offset(w * 0.58f, h * 0.50f))
                drawCircle(color = Color(0xFF101728), radius = w * 0.05f, center = Offset(w * 0.58f, h * 0.50f))
            }
            "font" -> {
                drawLine(color = ink, start = Offset(w * 0.24f, h * 0.80f), end = Offset(w * 0.50f, h * 0.20f), strokeWidth = w * 0.06f, cap = StrokeCap.Round)
                drawLine(color = ink, start = Offset(w * 0.50f, h * 0.20f), end = Offset(w * 0.76f, h * 0.80f), strokeWidth = w * 0.06f, cap = StrokeCap.Round)
                drawLine(color = accentColor, start = Offset(w * 0.35f, h * 0.60f), end = Offset(w * 0.65f, h * 0.60f), strokeWidth = w * 0.06f, cap = StrokeCap.Round)
            }
            "color" -> {
                val p = Path()
                p.moveTo(w * 0.50f, h * 0.10f)
                p.cubicTo(w * 0.50f, h * 0.10f, w * 0.16f, h * 0.52f, w * 0.16f, h * 0.64f)
                p.cubicTo(w * 0.16f, h * 0.84f, w * 0.32f, h * 0.92f, w * 0.50f, h * 0.92f)
                p.cubicTo(w * 0.68f, h * 0.92f, w * 0.84f, h * 0.84f, w * 0.84f, h * 0.64f)
                p.cubicTo(w * 0.84f, h * 0.52f, w * 0.50f, h * 0.10f, w * 0.50f, h * 0.10f)
                p.close()
                drawPath(path = p, color = accentColor)
            }
            "image" -> {
                drawRoundRect(color = ink, topLeft = Offset(w * 0.12f, h * 0.18f), size = Size(w * 0.76f, h * 0.64f), cornerRadius = CornerRadius(w * 0.06f, w * 0.06f), style = thin)
                drawCircle(color = accentColor, radius = w * 0.08f, center = Offset(w * 0.68f, h * 0.36f))
                val m = Path()
                m.moveTo(w * 0.18f, h * 0.76f)
                m.lineTo(w * 0.44f, h * 0.48f)
                m.lineTo(w * 0.82f, h * 0.76f)
                m.close()
                drawPath(path = m, color = ink.copy(alpha = 0.8f))
            }
            "clock" -> {
                drawCircle(color = ink, radius = w * 0.36f, center = Offset(w * 0.50f, h * 0.50f), style = thin)
                drawLine(color = ink, start = Offset(w * 0.50f, h * 0.50f), end = Offset(w * 0.50f, h * 0.30f), strokeWidth = w * 0.06f, cap = StrokeCap.Round)
                drawLine(color = accentColor, start = Offset(w * 0.50f, h * 0.50f), end = Offset(w * 0.66f, h * 0.58f), strokeWidth = w * 0.06f, cap = StrokeCap.Round)
            }
            "lock" -> {
                drawArc(color = ink, startAngle = 180f, sweepAngle = 180f, useCenter = false, topLeft = Offset(w * 0.29f, h * 0.08f), size = Size(w * 0.42f, h * 0.42f), style = thin)
                drawRoundRect(color = accentColor, topLeft = Offset(w * 0.18f, h * 0.34f), size = Size(w * 0.64f, h * 0.52f), cornerRadius = CornerRadius(w * 0.08f, w * 0.08f))
                drawCircle(color = Color(0xFF101728), radius = w * 0.07f, center = Offset(w * 0.50f, h * 0.58f))
            }
            "music" -> {
                drawOval(color = accentColor, topLeft = Offset(w * 0.10f, h * 0.64f), size = Size(w * 0.30f, h * 0.22f))
                drawLine(color = ink, start = Offset(w * 0.40f, h * 0.72f), end = Offset(w * 0.40f, h * 0.16f), strokeWidth = w * 0.05f, cap = StrokeCap.Round)
                drawOval(color = ink, topLeft = Offset(w * 0.56f, h * 0.58f), size = Size(w * 0.28f, h * 0.20f))
                drawLine(color = ink, start = Offset(w * 0.84f, h * 0.66f), end = Offset(w * 0.84f, h * 0.10f), strokeWidth = w * 0.05f, cap = StrokeCap.Round)
                drawLine(color = ink, start = Offset(w * 0.40f, h * 0.16f), end = Offset(w * 0.84f, h * 0.10f), strokeWidth = w * 0.07f, cap = StrokeCap.Round)
            }
            "disc" -> {
                drawCircle(color = accentColor, radius = w * 0.20f, center = Offset(w * 0.32f, h * 0.50f))
                drawCircle(color = ink, radius = w * 0.20f, center = Offset(w * 0.68f, h * 0.50f))
            }
            "update" -> {
                drawArc(color = ink, startAngle = -60f, sweepAngle = 260f, useCenter = false, topLeft = Offset(w * 0.14f, h * 0.14f), size = Size(w * 0.72f, h * 0.72f), style = Stroke(width = w * 0.06f, cap = StrokeCap.Round))
                val tri = Path()
                tri.moveTo(w * 0.86f, h * 0.10f)
                tri.lineTo(w * 0.86f, h * 0.34f)
                tri.lineTo(w * 0.64f, h * 0.22f)
                tri.close()
                drawPath(path = tri, color = accentColor)
            }
            "guide" -> {
                drawCircle(color = ink, radius = w * 0.36f, center = Offset(w * 0.50f, h * 0.50f), style = thin)
                val q = Path()
                q.moveTo(w * 0.38f, h * 0.37f)
                q.cubicTo(w * 0.38f, h * 0.23f, w * 0.62f, h * 0.23f, w * 0.62f, h * 0.37f)
                q.cubicTo(w * 0.62f, h * 0.49f, w * 0.50f, h * 0.50f, w * 0.50f, h * 0.61f)
                drawPath(path = q, color = accentColor, style = Stroke(width = w * 0.055f, cap = StrokeCap.Round))
                drawCircle(color = accentColor, radius = w * 0.045f, center = Offset(w * 0.50f, h * 0.73f))
            }
            "apps" -> {
                val cell = w * 0.32f
                val rad = CornerRadius(w * 0.06f, w * 0.06f)
                drawRoundRect(color = accentColor, topLeft = Offset(w * 0.13f, h * 0.13f), size = Size(cell, cell), cornerRadius = rad)
                drawRoundRect(color = ink, topLeft = Offset(w * 0.55f, h * 0.13f), size = Size(cell, cell), cornerRadius = rad)
                drawRoundRect(color = ink, topLeft = Offset(w * 0.13f, h * 0.55f), size = Size(cell, cell), cornerRadius = rad)
                drawRoundRect(color = accentColor, topLeft = Offset(w * 0.55f, h * 0.55f), size = Size(cell, cell), cornerRadius = rad)
            }
            "sparkle" -> {
                val p = Path()
                p.moveTo(w * 0.50f, h * 0.06f)
                p.lineTo(w * 0.60f, h * 0.40f)
                p.lineTo(w * 0.94f, h * 0.50f)
                p.lineTo(w * 0.60f, h * 0.60f)
                p.lineTo(w * 0.50f, h * 0.94f)
                p.lineTo(w * 0.40f, h * 0.60f)
                p.lineTo(w * 0.06f, h * 0.50f)
                p.lineTo(w * 0.40f, h * 0.40f)
                p.close()
                drawPath(path = p, color = accentColor)
            }
            "text" -> {
                drawLine(color = ink, start = Offset(w * 0.14f, h * 0.30f), end = Offset(w * 0.86f, h * 0.30f), strokeWidth = w * 0.07f, cap = StrokeCap.Round)
                drawLine(color = accentColor, start = Offset(w * 0.14f, h * 0.52f), end = Offset(w * 0.68f, h * 0.52f), strokeWidth = w * 0.07f, cap = StrokeCap.Round)
                drawLine(color = ink, start = Offset(w * 0.14f, h * 0.74f), end = Offset(w * 0.78f, h * 0.74f), strokeWidth = w * 0.07f, cap = StrokeCap.Round)
            }
            "swap" -> {
                val lw = w * 0.06f
                drawLine(color = accentColor, start = Offset(w * 0.16f, h * 0.35f), end = Offset(w * 0.76f, h * 0.35f), strokeWidth = lw, cap = StrokeCap.Round)
                drawLine(color = accentColor, start = Offset(w * 0.76f, h * 0.35f), end = Offset(w * 0.60f, h * 0.21f), strokeWidth = lw, cap = StrokeCap.Round)
                drawLine(color = accentColor, start = Offset(w * 0.76f, h * 0.35f), end = Offset(w * 0.60f, h * 0.49f), strokeWidth = lw, cap = StrokeCap.Round)
                drawLine(color = ink, start = Offset(w * 0.84f, h * 0.65f), end = Offset(w * 0.24f, h * 0.65f), strokeWidth = lw, cap = StrokeCap.Round)
                drawLine(color = ink, start = Offset(w * 0.24f, h * 0.65f), end = Offset(w * 0.40f, h * 0.51f), strokeWidth = lw, cap = StrokeCap.Round)
                drawLine(color = ink, start = Offset(w * 0.24f, h * 0.65f), end = Offset(w * 0.40f, h * 0.79f), strokeWidth = lw, cap = StrokeCap.Round)
            }
            else -> {
                drawCircle(color = accentColor, radius = w * 0.16f, center = Offset(w * 0.50f, h * 0.50f))
            }
        }
    }
}
