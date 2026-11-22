package com.example.nissingeppolauncher.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.layout.widthIn
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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.pow
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
    centerContent: (@Composable BoxScope.() -> Unit)? = null,
    state: DiscCarouselState,
    gesturesEnabled: Boolean = true,
    iconSizeDp: Float = 25f
) {
    if (entries.isEmpty()) return
    // 中身だけを設定用に入れ替えて、円盤のエンジン自体はアプリ円盤と共通の DiscCarousel を使う
    BoxWithConstraints(modifier = modifier) {
        val centerX = if (side == DiscSide.LEFT) -80f else maxWidth.value + 80f
        val centerY = maxHeight.value / 2f
        val carouselState = state
        DiscCarousel(
            itemCount = entries.size,
            discCenterXDp = centerX,
            discCenterYDp = centerY,
            discRadiusDp = 165f,
            stepAngle = 20f,
            isRightDisc = side == DiscSide.RIGHT,
            isLoopable = false,
            gesturesEnabled = gesturesEnabled,
            state = carouselState,
            onSelectedIndexChange = onFocusChange,
            centerContent = centerContent,
            modifier = Modifier.fillMaxSize()
        ) { index, rad, labelAlpha, isSelected ->
            val entry = entries[index]
            // アイコン: アプリ円盤と同じく輪郭の内側（半径-18）
            val iconX = centerX + 147f * cos(rad)
            val iconY = centerY + 147f * sin(rad)
            val iconSize = iconSizeDp + 14f
            Box(
                modifier = Modifier
                    .offset(x = (iconX - iconSize / 2f).dp, y = (iconY - iconSize / 2f).dp)
                    .size(iconSize.dp)
                    .clickable {
                        if (isSelected) onEntryClick(index) else carouselState.animateToIndex(index)
                    },
                contentAlignment = Alignment.Center
            ) {
                Box(modifier = Modifier.graphicsLayer { val sc = (if (isSelected) 1.12f else 1f) * (iconSizeDp / 36f); scaleX = sc; scaleY = sc }) {
                    entry.icon()
                }
            }
            // ラベル: アプリ円盤と同じく輪郭の外側（半径+8）を起点に置く
            val ax = centerX + 173f * cos(rad)
            val ay = centerY + 173f * sin(rad)
            Box(
                modifier = Modifier
                    .offset(x = (if (side == DiscSide.LEFT) ax else ax - 240f).dp, y = (ay - 22f).dp)
                    .width(240.dp)
                    .height(44.dp)
                    .graphicsLayer { this.alpha = labelAlpha },
                contentAlignment = if (side == DiscSide.LEFT) Alignment.CenterStart else Alignment.CenterEnd
            ) {
                Row(
                    modifier = Modifier.clickable {
                        if (isSelected) onEntryClick(index) else carouselState.animateToIndex(index)
                    },
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = if (side == DiscSide.LEFT) Arrangement.Start else Arrangement.End
                ) {
                    if (side == DiscSide.RIGHT && entry.valueText != null) {
                        Text(entry.valueText, color = accentColor.copy(alpha = 0.75f), fontSize = 10.sp, maxLines = 1)
                        Spacer(modifier = Modifier.width(6.dp))
                    }
                    Text(
                        text = entry.label + if (entry.checked) "  ✓" else "",
                        color = if (entry.checked) accentColor else Color.White.copy(alpha = 0.9f),
                        fontSize = 15.sp,
                        fontWeight = if (isSelected || entry.checked) FontWeight.Bold else FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.widthIn(max = 150.dp)
                    )
                    if (side == DiscSide.LEFT && entry.valueText != null) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(entry.valueText, color = accentColor.copy(alpha = 0.75f), fontSize = 10.sp, maxLines = 1)
                    }
                }
            }
        }
    }
}

// スライダー用の円: 目盛りの外側に数値ラベルを出し、現在値は円の中、決定は円の左に置く
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
    val textMeasurer = rememberTextMeasurer()
    BoxWithConstraints(modifier = modifier) {
        val wDp = maxWidth.value
        val hDp = maxHeight.value
        val rDp = 165f
        val centerX = wDp + 80f
        val centerY = hDp / 2f
        Canvas(modifier = Modifier.fillMaxSize()) {
            val c = Offset(centerX.dp.toPx(), centerY.dp.toPx())
            drawCircle(color = Color.White.copy(alpha = 0.40f), radius = rDp.dp.toPx(), center = c, style = Stroke(width = 1.5.dp.toPx()))
            // 目盛り（60分割、5つごとに長く）と、その外側に数値ラベル
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
                if (major) {
                    val v = range.start + (t / 60f) * (range.endInclusive - range.start)
                    val layout = textMeasurer.measure(
                        format(v),
                        TextStyle(color = Color.White.copy(alpha = 0.75f), fontSize = 11.sp)
                    )
                    val lr = (rDp + 24f).dp.toPx()
                    val pos = Offset(c.x + (lr * cos(ang)).toFloat(), c.y + (lr * sin(ang)).toFloat())
                    drawText(layout, topLeft = Offset(pos.x - layout.size.width / 2f, pos.y - layout.size.height / 2f))
                }
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
        // 円の中: 現在値
        Box(
            modifier = Modifier
                .offset(x = (centerX - 128f - 52f).dp, y = (centerY - 30f).dp)
                .size(104.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(format(value), color = Color.White, fontSize = 25.sp, fontWeight = FontWeight.Bold)
                Text(title, color = Color.Gray, fontSize = 11.sp)
            }
        }
        // 決定: 円の左に
        Surface(
            color = accentColor.copy(alpha = 0.25f),
            shape = RoundedCornerShape(12.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, accentColor),
            modifier = Modifier
                .offset(x = (centerX - rDp - 118f).dp, y = (centerY - 21f).dp)
                .clickable { onConfirm(value) }
        ) {
            Text("決定", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 22.dp, vertical = 10.dp))
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
            "palette" -> {
                drawCircle(color = ink, radius = w * 0.34f, center = Offset(w * 0.50f, h * 0.50f), style = thin)
                drawCircle(color = accentColor, radius = w * 0.08f, center = Offset(w * 0.38f, h * 0.38f))
                drawCircle(color = Color(0xFF88C0FF), radius = w * 0.08f, center = Offset(w * 0.62f, h * 0.38f))
                drawCircle(color = Color(0xFF9AA0A6), radius = w * 0.08f, center = Offset(w * 0.50f, h * 0.62f))
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
