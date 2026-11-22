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
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.nissingeppolauncher.data.AppRepository
import com.example.nissingeppolauncher.service.LyricsOverlayService
import com.example.nissingeppolauncher.ui.components.DiscEntry
import com.example.nissingeppolauncher.ui.components.DiscGauge
import com.example.nissingeppolauncher.ui.components.DiscGlyph
import com.example.nissingeppolauncher.ui.components.DiscSide
import com.example.nissingeppolauncher.ui.components.DiscCarouselState
import com.example.nissingeppolauncher.ui.components.DiscTouchHalf
import com.example.nissingeppolauncher.ui.components.DiscWheel
import com.example.nissingeppolauncher.ui.components.rememberDiscCarouselState
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

// 円盤ナビの項目モデル
data class SettingChoice(
    val id: String,
    val label: String,
    val checked: Boolean,
    val illo: String? = null,
    val fontSample: FontFamily? = null,
    val colorHex: String? = null,
    val modePreview: String? = null,
    val onSelect: () -> Unit
)

data class SettingSliderSpec(
    val value: Float,
    val range: ClosedFloatingPointRange<Float>,
    val steps: Int,
    val format: (Float) -> String,
    val onSave: (Float) -> Unit
)

data class SettingItem(
    val id: String,
    val label: String,
    val desc: String,
    val glyph: String,
    val valueText: String,
    val choices: List<SettingChoice> = emptyList(),
    val slider: SettingSliderSpec? = null,
    val onAction: (() -> Unit)? = null,
    val currentIllo: String? = null,
    val currentFont: FontFamily? = null,
    val currentColorHex: String? = null
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
    accentColor: Color = Color(0xFFE53935),
    disc3d: Boolean = false,
    tiltX: State<Float>? = null,
    tiltY: State<Float>? = null
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
    // 円盤ナビの状態（1: カテゴリ/項目, 2: 選択肢/スライダー）
    var discLevel by remember { mutableIntStateOf(1) }
    var catFocusId by remember { mutableStateOf("behavior") }
    var discCategory by remember { mutableStateOf("behavior") }
    var discItemId by remember { mutableStateOf<String?>(null) }
    var leftFocus by remember { mutableIntStateOf(0) }
    var rightFocus by remember { mutableIntStateOf(0) }
    var leftDiscState by remember { mutableStateOf<DiscCarouselState?>(null) }
    var rightDiscState by remember { mutableStateOf<DiscCarouselState?>(null) }
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
    BackHandler(enabled = discLevel == 2) {
        discLevel = 1
        discItemId = null
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

    // ===== 円盤ナビの項目モデル（状態と保存処理は従来のものをそのまま使う） =====
    val discCategories = listOf(
        Triple("behavior", "動作", "ダイヤルのスクロール方向や操作感の設定"),
        Triple("appearance", "外観", "壁紙、配色、フォントなどの見た目のカスタマイズ"),
        Triple("lyrics", "歌詞", "表示モード、フォント、太さの設定"),
        Triple("apps", "アプリ一覧", "アプリ一覧データの差分更新（追加・削除されたアプリの反映）"),
        Triple("security", "セキュリティ", "非表示アプリ用のパスコード(PIN)設定"),
        Triple("tutorial", "チュートリアル", "ガイドや使い方の再表示設定"),
        Triple("__update", "アップデート", "GitHubの最新ビルドを確認します")
    )
    fun toggleChoices(current: Boolean, set: (Boolean) -> Unit): List<SettingChoice> = listOf(
        SettingChoice("on", "オン", current) { set(true) },
        SettingChoice("off", "オフ", !current) { set(false) }
    )
    fun itemsFor(catId: String): List<SettingItem> = when (catId) {
        "behavior" -> listOf(
            SettingItem(
                id = "reverse", label = "スクロール方向の反転", desc = "有効にすると、ダイヤルをドラッグした際の回転方向が逆になります",
                glyph = "swap", valueText = if (isReverseScroll) "オン" else "オフ",
                choices = toggleChoices(isReverseScroll) { v -> isReverseScroll = v; repo.prefManager.setReverseScroll(v); onChanged() }
            ),
            SettingItem(
                id = "sleep", label = "スリープタイマー", desc = "設定した時間が過ぎたら、再生中の音楽を自動で一時停止します",
                glyph = "clock",
                valueText = if (sleepTimerEndMs > sleepNowMs) "あと約 ${(sleepTimerEndMs - sleepNowMs + 59999L) / 60000L} 分" else "オフ",
                choices = listOf(0, 15, 30, 60).map { min ->
                    SettingChoice(
                        id = "min$min", label = if (min == 0) "オフ" else "${min}分",
                        checked = if (min == 0) sleepTimerEndMs <= 0L else (sleepTimerEndMs > sleepNowMs && sleepTimerMinutes == min)
                    ) {
                        sleepTimerMinutes = min
                        repo.prefManager.setSleepTimerMinutes(min)
                        val end = if (min == 0) 0L else System.currentTimeMillis() + min * 60000L
                        sleepTimerEndMs = end
                        repo.prefManager.setSleepTimerEndMs(end)
                        onChanged()
                    }
                }
            )
        )
        "apps" -> listOf(
            SettingItem(
                id = "updateApps", label = "アプリ一覧を更新", desc = "追加・削除・更新されたアプリをチェックして、一覧に反映します（差分更新）。通常は起動時に自動でチェックされます",
                glyph = "apps", valueText = appsUpdateStatus.ifEmpty { "差分更新" },
                onAction = {
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
                }
            )
        )
        "appearance" -> buildList {
            add(
                SettingItem(
                    id = "pageSwitch", label = "ボタンでページ切り替え", desc = "オフにすると下部のダイヤルで切り替えます",
                    glyph = "toggle", valueText = if (pageSwitchStyle == "buttons") "ボタン" else "ダイヤル",
                    choices = listOf(
                        SettingChoice("buttons", "ボタン", pageSwitchStyle == "buttons") { pageSwitchStyle = "buttons"; repo.prefManager.setPageSwitchStyle("buttons"); onChanged() },
                        SettingChoice("dial", "ダイヤル", pageSwitchStyle == "dial") { pageSwitchStyle = "dial"; repo.prefManager.setPageSwitchStyle("dial"); onChanged() }
                    )
                )
            )
            add(
                SettingItem(
                    id = "discLayout", label = "ホーム画面の円の大きさ", desc = "「アートワーク大きめ」では再生アートワークの円が大きくなります。「自動」は再生中だけ大きめになります",
                    glyph = "disc",
                    valueText = when (homeDiscLayout) { "artwork" -> "アートワーク大きめ"; "auto" -> "自動"; else -> "従来（均等）" },
                    currentIllo = when (homeDiscLayout) { "artwork" -> "disc_artwork"; "auto" -> "disc_auto"; else -> "disc_classic" },
                    choices = listOf(
                        Triple("classic", "従来（均等）", "disc_classic"),
                        Triple("artwork", "アートワーク大きめ", "disc_artwork"),
                        Triple("auto", "自動", "disc_auto")
                    ).map { (value, label, illo) ->
                        SettingChoice(value, label, homeDiscLayout == value, illo = illo) {
                            homeDiscLayout = value; repo.prefManager.setHomeDiscLayout(value); onChanged()
                        }
                    }
                )
            )
            add(
                SettingItem(
                    id = "disc3d", label = "UIを3D表示", desc = "円・時計・ダイヤル・歌詞PVなどを少し奥に倒して、立体的に見せます",
                    glyph = "disc", valueText = if (disc3d) "オン" else "オフ",
                    choices = toggleChoices(disc3d) { v -> disc3d = v; repo.prefManager.setDisc3dEnabled(v); onChanged() }
                )
            )
            add(
                SettingItem(
                    id = "disc3dTilt", label = "端末の傾きで角度を変える", desc = "スマホを傾けると、3Dの角度もついてきます（3D表示がオンのときだけ）",
                    glyph = "disc", valueText = if (disc3dTilt) "オン" else "オフ",
                    choices = toggleChoices(disc3dTilt) { v -> disc3dTilt = v; repo.prefManager.setDisc3dTiltEnabled(v); onChanged() }
                )
            )
            add(
                SettingItem(
                    id = "dateMode", label = "日付の表示", desc = "「常時縦」は日付を時刻の右に横倒しで、「常時横」は時計の下に表示します。「切り替え」は歌詞が出ている間だけ横倒しになります",
                    glyph = "clock",
                    valueText = when (dateDisplayMode) { "under" -> "常時横"; "auto" -> "切り替え"; else -> "常時縦" },
                    currentIllo = when (dateDisplayMode) { "under" -> "date_under"; "auto" -> "date_auto"; else -> "date_sideways" },
                    choices = listOf(
                        Triple("sideways", "常時縦", "date_sideways"),
                        Triple("under", "常時横", "date_under"),
                        Triple("auto", "切り替え", "date_auto")
                    ).map { (value, label, illo) ->
                        SettingChoice(value, label, dateDisplayMode == value, illo = illo) {
                            dateDisplayMode = value; repo.prefManager.setDateDisplayMode(value); onChanged()
                        }
                    }
                )
            )
            add(
                SettingItem(
                    id = "bgMode", label = "背景の見せ方", desc = "アートワークを選ぶと、再生中は曲のアートワーク由来の背景になります",
                    glyph = "image",
                    valueText = when (bgMode) { "artwork_solid" -> "単色"; "artwork_blur" -> "ぼかし"; else -> "壁紙" },
                    currentIllo = when (bgMode) { "artwork_solid" -> "bg_solid"; "artwork_blur" -> "bg_blur"; else -> "bg_wallpaper" },
                    choices = listOf(
                        Triple("wallpaper", "壁紙", "bg_wallpaper"),
                        Triple("artwork_solid", "単色", "bg_solid"),
                        Triple("artwork_blur", "ぼかし", "bg_blur")
                    ).map { (value, label, illo) ->
                        SettingChoice(value, label, bgMode == value, illo = illo) {
                            bgMode = value; repo.prefManager.setBackgroundMode(value); onChanged()
                        }
                    }
                )
            )
            add(
                SettingItem(
                    id = "liquid", label = "液体グラデーション背景", desc = "壁紙がないときや再生中のアートワーク背景の上で、グラデーションがゆっくり動きます",
                    glyph = "color", valueText = if (liquidBackground) "オン" else "オフ",
                    choices = toggleChoices(liquidBackground) { v -> liquidBackground = v; repo.prefManager.setLiquidBackgroundEnabled(v); onChanged() }
                )
            )
            add(
                SettingItem(
                    id = "wallpaperPick", label = "壁紙画像を選択", desc = "ホーム画面の背景にする画像を選びます",
                    glyph = "image", valueText = if (backgroundPath != null) "設定済み" else "未設定",
                    onAction = { imagePickerLauncher.launch("image/*") }
                )
            )
            if (backgroundPath != null) {
                add(
                    SettingItem(
                        id = "wallpaperClear", label = "壁紙を解除", desc = "カスタム壁紙をやめて標準の背景に戻します",
                        glyph = "image", valueText = "",
                        onAction = {
                            repo.prefManager.setCustomBackgroundPath(null)
                            backgroundPath = null
                            onChanged()
                        }
                    )
                )
            }
            add(
                SettingItem(
                    id = "brightness", label = "ホーム背景の明るさ", desc = "壁紙の上に重ねる暗さの調整です",
                    glyph = "slider", valueText = "${((1f - wallpaperAlpha) * 100).toInt()}%",
                    slider = SettingSliderSpec(
                        value = wallpaperAlpha, range = 0.05f..0.9f, steps = 17,
                        format = { "${((1f - it) * 100).toInt()}%" },
                        onSave = { v -> wallpaperAlpha = v; repo.prefManager.setHomeWallpaperAlpha(v); onChanged() }
                    )
                )
            )
            add(
                SettingItem(
                    id = "matchArtwork", label = "アートワークに合わせる", desc = "再生中の曲のアートワークから、アクセントと背景の色を自動で決めます",
                    glyph = "color", valueText = if (matchArtwork) "オン" else "オフ",
                    choices = toggleChoices(matchArtwork) { v -> matchArtwork = v; repo.prefManager.setMatchArtworkColor(v); onChanged() }
                )
            )
            add(
                SettingItem(
                    id = "accentColor", label = "アクセントカラー", desc = "UIの差し色です（アートワーク連動がオンのときは自動の色が優先されます）",
                    glyph = "color",
                    valueText = ACCENT_PRESETS.firstOrNull { it.second.equals(accentHex, true) }?.first ?: accentHex,
                    currentColorHex = accentHex,
                    choices = ACCENT_PRESETS.map { (name, hex) ->
                        SettingChoice(hex, name, accentHex.equals(hex, true), colorHex = hex) {
                            accentHex = hex; repo.prefManager.setAccentColorHex(hex); onChanged()
                        }
                    }
                )
            )
            add(
                SettingItem(
                    id = "uiFont", label = "UIフォント", desc = "ランチャー全体の文字のフォントです",
                    glyph = "font",
                    valueText = when (currentFontChoice) { "receipt" -> "レシート"; "denkichip" -> "でんきチップ"; "tsukuhou" -> "築豊明朝"; "kaibunsho" -> "怪聞書"; "zenkaku" -> "Zen Kaku"; "custom" -> "カスタム"; else -> "システム" },
                    currentFont = parseFontFamily(currentFontChoice, if (currentFontChoice == "custom") customFontPath else null),
                    choices = listOf(
                        "default" to "システムフォント",
                        "receipt" to "レシートフォント",
                        "denkichip" to "でんきチップ",
                        "tsukuhou" to "築豊明朝",
                        "kaibunsho" to "怪聞書フォント",
                        "zenkaku" to "Zen Kaku Gothic New",
                        "custom" to "＋カスタムフォント"
                    ).map { (key, label) ->
                        SettingChoice(
                            key, label, currentFontChoice == key,
                            fontSample = parseFontFamily(key, if (key == "custom") customFontPath else null)
                        ) {
                            currentFontChoice = key
                            repo.prefManager.setFontChoice(key)
                            useCustomFont = key == "custom"
                            repo.prefManager.setUseCustomFont(key == "custom")
                            onChanged()
                            if (key == "custom") fontPickerLauncher.launch("*/*")
                        }
                    }
                )
            )
            add(
                SettingItem(
                    id = "iconSize", label = "アイコンサイズ", desc = "アプリ円盤のアイコンの大きさです",
                    glyph = "slider", valueText = "${iconSize.toInt()} dp",
                    slider = SettingSliderSpec(iconSize, 16f..32f, 15, { "${it.toInt()} dp" }) { v ->
                        iconSize = v; repo.prefManager.setAppDiscIconSizeDp(v); onChanged()
                    }
                )
            )
            add(
                SettingItem(
                    id = "textSize", label = "テキストサイズ", desc = "アプリ円盤のアプリ名の大きさです",
                    glyph = "slider", valueText = "${textSize.toInt()} sp",
                    slider = SettingSliderSpec(textSize, 12f..22f, 9, { "${it.toInt()} sp" }) { v ->
                        textSize = v; repo.prefManager.setAppDiscTextSizeSp(v); onChanged()
                    }
                )
            )
            add(
                SettingItem(
                    id = "letterSpacing", label = "文字間隔", desc = "アプリ名の文字どうしの間隔です",
                    glyph = "slider", valueText = String.format(java.util.Locale.ROOT, "%.1f sp", letterSpacing),
                    slider = SettingSliderSpec(letterSpacing, 0.5f..3.0f, 25, { String.format(java.util.Locale.ROOT, "%.1f sp", it) }) { v ->
                        letterSpacing = v; repo.prefManager.setAppDiscLetterSpacingSp(v); onChanged()
                    }
                )
            )
            add(
                SettingItem(
                    id = "stepAngle", label = "リストの間隔", desc = "アプリ円盤で1アプリぶんの回転角度です",
                    glyph = "slider", valueText = String.format(java.util.Locale.ROOT, "%.1f°", stepAngle),
                    slider = SettingSliderSpec(stepAngle, 10f..20f, 20, { String.format(java.util.Locale.ROOT, "%.1f°", it) }) { v ->
                        stepAngle = v; repo.prefManager.setAppDiscStepAngle(v); onChanged()
                    }
                )
            )
        }
        "lyrics" -> buildList {
            add(
                SettingItem(
                    id = "displayMode", label = "歌詞の表示モード", desc = "複数行は時計の下に前・今・次の3行を表示します。リリックビデオはSpotifyなど他のアプリの再生でも使えます",
                    glyph = "music",
                    valueText = when (lyricsDisplayMode) { "video" -> "リリックビデオ"; "multi" -> "複数行"; else -> "現在の行" },
                    choices = listOf(
                        Triple("line", "現在の行", "line"),
                        Triple("video", "リリックビデオ", "video"),
                        Triple("multi", "複数行", "multi")
                    ).map { (value, label, mode) ->
                        SettingChoice(value, label, lyricsDisplayMode == value, modePreview = mode) {
                            lyricsDisplayMode = value; repo.prefManager.setLyricsDisplayMode(value); onChanged()
                        }
                    }
                )
            )
            add(
                SettingItem(
                    id = "jpFont", label = "歌詞のフォント", desc = "全モード共通の歌詞フォントです。「カスタム」はフォント設定で選んだファイルを使います",
                    glyph = "font",
                    valueText = when (lyricsPvFont) { "receipt" -> "レシート"; "denkichip" -> "でんきチップ"; "tsukuhou" -> "築豊明朝"; "kaibunsho" -> "怪聞書"; "zenkaku" -> "Zen Kaku"; "custom" -> "カスタム"; else -> "システム" },
                    currentFont = parseFontFamily(lyricsPvFont, if (lyricsPvFont == "custom") customFontPath else null),
                    choices = listOf(
                        "default" to "システムフォント",
                        "receipt" to "レシートフォント",
                        "denkichip" to "でんきチップ",
                        "tsukuhou" to "築豊明朝",
                        "kaibunsho" to "怪聞書フォント",
                        "zenkaku" to "Zen Kaku Gothic New",
                        "custom" to "＋カスタムフォント"
                    ).map { (key, label) ->
                        SettingChoice(
                            key, label, lyricsPvFont == key,
                            fontSample = parseFontFamily(key, if (key == "custom") customFontPath else null)
                        ) {
                            lyricsPvFont = key
                            repo.prefManager.setLyricsPvFont(key)
                            onChanged()
                            if (key == "custom" && customFontPath == null) fontPickerLauncher.launch("*/*")
                        }
                    }
                )
            )
            add(
                SettingItem(
                    id = "enFont", label = "歌詞の英字フォント", desc = "歌詞の中の英字・数字だけに使うフォントです",
                    glyph = "font",
                    valueText = when (lyricsEnFont) { "same" -> "日本語と同じ"; "freelicense" -> "Free License"; "montserratbi" -> "Montserrat"; "receipt" -> "レシート"; "denkichip" -> "でんきチップ"; "tsukuhou" -> "築豊明朝"; "kaibunsho" -> "怪聞書"; "zenkaku" -> "Zen Kaku"; "custom" -> "カスタム"; else -> "システム" },
                    currentFont = when (lyricsEnFont) {
                        "same" -> parseFontFamily(lyricsPvFont, if (lyricsPvFont == "custom") customFontPath else null)
                        "custom" -> parseFontFamily("custom", lyricsEnCustomPath)
                        else -> parseFontFamily(lyricsEnFont, null)
                    },
                    choices = listOf(
                        "same" to "日本語フォントと同じ",
                        "freelicense" to "Free License",
                        "montserratbi" to "Montserrat Bold Italic",
                        "default" to "システムフォント",
                        "receipt" to "レシートフォント",
                        "denkichip" to "でんきチップ",
                        "tsukuhou" to "築豊明朝",
                        "kaibunsho" to "怪聞書フォント",
                        "zenkaku" to "Zen Kaku Gothic New",
                        "custom" to "＋カスタムフォント"
                    ).map { (key, label) ->
                        SettingChoice(
                            key, label, lyricsEnFont == key,
                            fontSample = when (key) {
                                "same" -> parseFontFamily(lyricsPvFont, if (lyricsPvFont == "custom") customFontPath else null)
                                "custom" -> parseFontFamily("custom", lyricsEnCustomPath)
                                else -> parseFontFamily(key, null)
                            }
                        ) {
                            lyricsEnFont = key
                            repo.prefManager.setLyricsEnFont(key)
                            onChanged()
                            if (key == "custom") enFontPickerLauncher.launch("*/*")
                        }
                    }
                )
            )
            add(
                SettingItem(
                    id = "bold", label = "歌詞を太字にする", desc = "デフォルトは細字。築豊明朝は細字が映えます",
                    glyph = "text", valueText = if (lyricsBold) "オン" else "オフ",
                    choices = toggleChoices(lyricsBold) { v -> lyricsBold = v; repo.prefManager.setLyricsBold(v); onChanged() }
                )
            )
            add(
                SettingItem(
                    id = "parts", label = "文字をパーツに分けて入場させる", desc = "リリックビデオで、文字がスライス状のパーツになって集まります",
                    glyph = "sparkle", valueText = if (pvPartsEntry) "オン" else "オフ",
                    choices = toggleChoices(pvPartsEntry) { v -> pvPartsEntry = v; repo.prefManager.setPvPartsEntryEnabled(v); onChanged() }
                )
            )
            add(
                SettingItem(
                    id = "dopagaki", label = "ドパガキモード", desc = "リリックビデオにエフェクトを大量に重ねて派手にします",
                    glyph = "sparkle", valueText = if (dopagakiMode) "オン" else "オフ",
                    choices = toggleChoices(dopagakiMode) { v -> dopagakiMode = v; repo.prefManager.setDopagakiEnabled(v); onChanged() }
                )
            )
            add(
                SettingItem(
                    id = "dopaBlack", label = "ドパガキ中は極太フォント", desc = "ドパガキモードのとき、歌詞を Zen Kaku Gothic New の極太（Black）で表示します",
                    glyph = "font", valueText = if (dopaBlackFont) "オン" else "オフ",
                    choices = toggleChoices(dopaBlackFont) { v -> dopaBlackFont = v; repo.prefManager.setDopaBlackFontEnabled(v); onChanged() }
                )
            )
            add(
                SettingItem(
                    id = "statusBar", label = "ステータスバーに歌詞を表示", desc = "他のアプリの上にも、時計と同期歌詞1行を重ねます（重ねて表示の権限が必要）",
                    glyph = "music", valueText = if (statusBarLyrics) "オン" else "オフ",
                    choices = listOf(
                        SettingChoice("on", "オン", statusBarLyrics) {
                            statusBarLyrics = true
                            repo.prefManager.setStatusBarLyricsEnabled(true)
                            if (Settings.canDrawOverlays(context)) {
                                LyricsOverlayService.start(context)
                            } else {
                                val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:" + context.packageName))
                                try { context.startActivity(intent) } catch (e: Exception) { }
                            }
                            onChanged()
                        },
                        SettingChoice("off", "オフ", !statusBarLyrics) {
                            statusBarLyrics = false
                            repo.prefManager.setStatusBarLyricsEnabled(false)
                            LyricsOverlayService.stop(context)
                            onChanged()
                        }
                    )
                )
            )
            add(
                SettingItem(
                    id = "fxUpdate", label = "歌詞エフェクトの更新", desc = "単語アイコンや色の対応表を最新にします",
                    glyph = "update",
                    valueText = when { fxChecking -> "確認中…"; fxMessage != null -> fxMessage!!; else -> "タップで確認" },
                    onAction = {
                        if (!fxChecking) {
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
                    }
                )
            )
        }
        "security" -> listOf(
            SettingItem(
                id = "passcodeAuth", label = "非表示アプリのパスコード認証", desc = "有効にすると、非表示リストを開く際にパスコード認証を行います",
                glyph = "lock", valueText = if (usePasscodeForHidden) "オン" else "オフ",
                choices = toggleChoices(usePasscodeForHidden) { v -> usePasscodeForHidden = v; repo.prefManager.setUsePasscodeForHidden(v); onChanged() }
            ),
            SettingItem(
                id = "passcodeChange", label = "パスコードを変更する", desc = "非表示リスト解除用のパスコード (PIN) を変更します",
                glyph = "lock", valueText = "",
                onAction = {
                    newPasscodeField = repo.prefManager.getPasscode()
                    showPasscodeChangeDialog = true
                }
            )
        )
        "tutorial" -> listOf(
            SettingItem(
                id = "guideReshow", label = "チュートリアルを再表示する", desc = "ガイドや使い方をもう一度表示するようにします",
                glyph = "guide", valueText = "",
                onAction = {
                    repo.prefManager.setFirstWelcomeTutorialShown(false)
                    repo.prefManager.setFirstIndexTutorialShown(false)
                    repo.prefManager.setFirstHideTutorialShown(false)
                    Toast.makeText(context, "チュートリアルを再表示するように設定しました", Toast.LENGTH_SHORT).show()
                    onChanged()
                }
            )
        )
        "__update" -> listOf(
            SettingItem(
                id = "checkUpdate", label = "アップデートを確認", desc = "GitHubの最新ビルドを確認します",
                glyph = "update",
                valueText = when { updateChecking -> "確認中…"; updateMessage != null -> updateMessage!!; else -> "タップで確認" },
                onAction = {
                    if (!updateChecking) {
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
                }
            )
        )
        else -> emptyList()
    }
    fun openItem(item: SettingItem) {
        if (item.onAction != null) {
            item.onAction.invoke()
        } else {
            discCategory = catFocusId
            discItemId = item.id
            rightFocus = 0
            discLevel = 2
        }
    }
    val levelItems = itemsFor(if (discLevel == 2) discCategory else catFocusId)
    val currentItem = levelItems.firstOrNull { it.id == discItemId }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0x330B101D))
    ) {
            // --- 2つの円 ---
            val discDensity = LocalDensity.current
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .then(
                        if (disc3d) Modifier.graphicsLayer {
                            transformOrigin = TransformOrigin(0.5f, 0.5f)
                            rotationX = 27f + (tiltX?.value ?: 0f)
                            rotationY = tiltY?.value ?: 0f
                            cameraDistance = 6.5f * discDensity.density
                        } else Modifier
                    )
            ) {
                if (discLevel == 1) {
                    key("disc-cats") {
                        val catState = rememberDiscCarouselState(discCategories.indexOfFirst { it.first == catFocusId }.coerceAtLeast(0), 20f)
                        LaunchedEffect(catState) { leftDiscState = catState }
                        DiscWheel(
                            entries = discCategories.map { (id, title, _) ->
                                DiscEntry(id = id, label = title, icon = {
                                    if (id == "__update") DiscGlyph("update", currentAccentColor)
                                    else SettingsCategoryIcon(kind = id, accentColor = currentAccentColor)
                                })
                            },
                            side = DiscSide.LEFT,
                            accentColor = currentAccentColor,
                            modifier = Modifier.fillMaxSize(),
                            state = catState, gesturesEnabled = false,
                            initialFocus = discCategories.indexOfFirst { it.first == catFocusId }.coerceAtLeast(0),
                            onFocusChange = { idx -> leftFocus = idx; catFocusId = discCategories[idx].first },
                            onEntryClick = { idx -> catFocusId = discCategories[idx].first }
                        )
                    }
                    key("disc-items-$catFocusId") {
                        val itemState = rememberDiscCarouselState(0, 20f)
                        LaunchedEffect(itemState) { rightDiscState = itemState }
                        DiscWheel(
                            entries = levelItems.map { item ->
                                DiscEntry(id = item.id, label = item.label, valueText = item.valueText.ifEmpty { null }, icon = { DiscGlyph(item.glyph, currentAccentColor) })
                            },
                            side = DiscSide.RIGHT,
                            accentColor = currentAccentColor,
                            modifier = Modifier.fillMaxSize(),
                            state = itemState, gesturesEnabled = true,
                            initialFocus = 0,
                            onFocusChange = { rightFocus = it },
                            onEntryClick = { idx -> openItem(levelItems[idx]) },
                        )
                    }
                } else {
                    key("disc-l2items-$discCategory") {
                        val l2State = rememberDiscCarouselState(levelItems.indexOfFirst { it.id == discItemId }.coerceAtLeast(0), 20f)
                        LaunchedEffect(l2State) { leftDiscState = l2State }
                        DiscWheel(
                            entries = levelItems.map { item ->
                                DiscEntry(id = item.id, label = item.label, valueText = item.valueText.ifEmpty { null }, icon = { DiscGlyph(item.glyph, currentAccentColor) })
                            },
                            side = DiscSide.LEFT,
                            accentColor = currentAccentColor,
                            modifier = Modifier.fillMaxSize(),
                            state = l2State, gesturesEnabled = false,
                            initialFocus = levelItems.indexOfFirst { it.id == discItemId }.coerceAtLeast(0),
                            onFocusChange = { leftFocus = it },
                            onEntryClick = { idx ->
                                val item = levelItems[idx]
                                if (item.onAction != null) {
                                    item.onAction.invoke()
                                    discLevel = 1
                                    discItemId = null
                                } else {
                                    discItemId = item.id
                                    rightFocus = 0
                                }
                            }
                        )
                    }
                    if (currentItem != null && currentItem.slider != null) {
                        key("disc-gauge-${currentItem.id}") {
                            DiscGauge(
                                title = currentItem.label,
                                initialValue = currentItem.slider.value,
                                range = currentItem.slider.range,
                                steps = currentItem.slider.steps,
                                format = currentItem.slider.format,
                                accentColor = currentAccentColor,
                                modifier = Modifier.fillMaxSize(),
                                onConfirm = { v ->
                                    currentItem.slider.onSave(v)
                                    discLevel = 1
                                    discItemId = null
                                }
                            )
                        }
                    } else if (currentItem != null) {
                        key("disc-choices-${currentItem.id}") {
                            val choiceState = rememberDiscCarouselState(currentItem.choices.indexOfFirst { it.checked }.coerceAtLeast(0), 20f)
                            LaunchedEffect(choiceState) { rightDiscState = choiceState }
                            DiscWheel(
                                entries = currentItem.choices.map { choice ->
                                    DiscEntry(
                                        id = choice.id, label = choice.label, checked = choice.checked,
                                        icon = {
                                            val cHex = choice.colorHex
                                            val cFont = choice.fontSample
                                            val cIllo = choice.illo
                                            when {
                                                cHex != null -> Box(
                                                    modifier = Modifier.size(30.dp).clip(CircleShape).background(parseHexColor(cHex))
                                                )
                                                cFont != null -> Text("あ", fontFamily = cFont, color = Color.White, fontSize = 19.sp)
                                                cIllo != null -> Box(modifier = Modifier.graphicsLayer { scaleX = 0.78f; scaleY = 0.78f }) {
                                                    ChoiceIllustration(cIllo, currentAccentColor, choice.checked)
                                                }
                                                else -> DiscGlyph(currentItem.glyph, currentAccentColor)
                                            }
                                        }
                                    )
                                },
                                side = DiscSide.RIGHT,
                                accentColor = currentAccentColor,
                                modifier = Modifier.fillMaxSize(),
                                state = choiceState, gesturesEnabled = true,
                                initialFocus = currentItem.choices.indexOfFirst { it.checked }.coerceAtLeast(0),
                                onFocusChange = { rightFocus = it },
                                onEntryClick = { idx -> currentItem.choices[idx].onSelect() },
                            )
                        }
                    }
                }
            }

        // --- タッチ操作パネル（フォルダページと同じ3分割: ヘッダー帯とダイヤル帯を除き、左右で円盤を回す） ---
        val panelRadiusPx = with(LocalDensity.current) { 165.dp.toPx() }
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = 104.dp, bottom = 72.dp)
        ) {
            Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
                DiscTouchHalf(
                    state = leftDiscState,
                    isRight = false,
                    radiusPx = panelRadiusPx,
                    isReverseScroll = { isReverseScroll },
                    modifier = Modifier.fillMaxSize()
                )
            }
            Box(modifier = Modifier.weight(1f).fillMaxHeight())
        }

        Column(modifier = Modifier.fillMaxSize().padding(top = 40.dp)) {
            // --- 上部 タイトルバー（フォルダページと同じ浮いたピル型） ---
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
                    if (discLevel == 2) {
                        Text(
                            text = "‹ 戻る",
                            color = currentAccentColor,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Normal,
                            modifier = Modifier
                                .clickable { discLevel = 1; discItemId = null }
                                .padding(end = 16.dp)
                        )
                    }
                    Text(
                        text = if (discLevel == 2) (currentItem?.label ?: "設定") else "環境設定",
                        color = Color.White,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Normal
                    )
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

