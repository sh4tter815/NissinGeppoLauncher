package com.example.geppolauncher.ui

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.drag
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
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.geppolauncher.R
import com.example.geppolauncher.data.AppRepository
import java.io.File
import java.io.FileOutputStream
import kotlin.math.roundToInt

val ACCENT_PRESETS = listOf(
    Pair(R.string.color_red, "#E53935"),
    Pair(R.string.color_blue, "#88C0FF"),
    Pair(R.string.color_green, "#4CAF50"),
    Pair(R.string.color_purple, "#AB47BC"),
    Pair(R.string.color_orange, "#FF9800"),
    Pair(R.string.color_cyan, "#00E5FF"),
    Pair(R.string.color_pink, "#FF4081")
)

fun parseHexColor(hex: String, fallback: Color = Color(0xFFE53935)): Color {
    return try {
        Color(android.graphics.Color.parseColor(hex))
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

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(36.dp)
            .onGloballyPositioned { widthPx = it.size.width.toFloat().coerceAtLeast(1f) }
            .pointerInput(valueRange, steps) {
                awaitPointerEventScope {
                    while (true) {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        val updateVal = { xPx: Float ->
                            val f = (xPx / widthPx).coerceIn(0f, 1f)
                            val stepIdx = (f * stepCount).roundToInt().coerceIn(0, stepCount)
                            val newVal = valueRange.start + stepIdx * ((valueRange.endInclusive - valueRange.start) / stepCount)
                            onValueChange(newVal)
                        }
                        updateVal(down.position.x)
                        drag(down.id) { change ->
                            change.consume()
                            updateVal(change.position.x)
                        }
                    }
                }
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
        "default" to stringResource(R.string.font_default),
        "receipt" to stringResource(R.string.font_receipt),
        "custom" to stringResource(R.string.font_custom)
    )
    val currentLabel = fontOptions[currentFontChoice] ?: stringResource(R.string.font_default)

    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(stringResource(R.string.font_selection), color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Normal)

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
            Text(stringResource(R.string.import_font_file), color = Color.White, fontWeight = FontWeight.Light)
        }

        if (!customFontPath.isNullOrBlank()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(stringResource(R.string.use_custom_font), color = Color.White, fontSize = 13.sp)
                Switch(
                    checked = useCustomFont,
                    onCheckedChange = onUseCustomFontChanged,
                    colors = switchColors
                )
            }
        }
    }
}

@Composable
fun SettingsScreen(
    repo: AppRepository,
    onBack: () -> Unit = {},
    onChanged: () -> Unit = {}
) {
    val context = LocalContext.current
    var accentHex by remember { mutableStateOf(repo.prefManager.getAccentColorHex()) }
    val currentAccentColor = parseHexColor(accentHex)

    var iconSize by remember { mutableFloatStateOf(repo.prefManager.getAppDiscIconSizeDp()) }
    var textSize by remember { mutableFloatStateOf(repo.prefManager.getAppDiscTextSizeSp()) }
    var letterSpacing by remember { mutableFloatStateOf(repo.prefManager.getAppDiscLetterSpacingSp()) }
    var stepAngle by remember { mutableFloatStateOf(repo.prefManager.getAppDiscStepAngle()) }
    var backgroundPath by remember { mutableStateOf(repo.prefManager.getCustomBackgroundPath()) }
    var wallpaperAlpha by remember { mutableFloatStateOf(repo.prefManager.getHomeWallpaperAlpha()) }
    var showPasscodeChangeDialog by remember { mutableStateOf(false) }
    var newPasscodeField by remember { mutableStateOf(repo.prefManager.getPasscode()) }

    var isReverseScroll by remember { mutableStateOf(repo.prefManager.isReverseScroll()) }
    var usePasscodeForHidden by remember { mutableStateOf(repo.prefManager.isUsePasscodeForHidden()) }
    var currentFontChoice by remember { mutableStateOf(repo.prefManager.getFontChoice()) }

    val switchColors = SwitchDefaults.colors(
        checkedThumbColor = currentAccentColor,
        checkedTrackColor = currentAccentColor.copy(alpha = 0.4f),
        uncheckedThumbColor = Color.Gray,
        uncheckedTrackColor = Color(0xFF1E283D)
    )

    // システム環境設定風 2段階ナビゲーション ("behavior", "appearance", "security", "tutorial")
    var selectedCategory by remember { mutableStateOf<String?>(null) }
    // 外観内のサブタブ ("wallpaper", "color", "font")
    var appearanceSubTab by remember { mutableStateOf("wallpaper") }

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
                            Toast.makeText(context, R.string.toast_wallpaper_changed, Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            } catch (_: Exception) {
                Toast.makeText(context, R.string.toast_wallpaper_load_failed, Toast.LENGTH_SHORT).show()
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
                    customFontPath = path
                    useCustomFont = true
                    onChanged()
                    Toast.makeText(context, R.string.toast_custom_font_loaded, Toast.LENGTH_SHORT).show()
                }
            } catch (_: Exception) {
                Toast.makeText(context, R.string.toast_font_load_failed, Toast.LENGTH_SHORT).show()
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
                            text = stringResource(R.string.back_arrow),
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
                            "behavior" -> stringResource(R.string.tab_behavior)
                            "appearance" -> stringResource(R.string.tab_appearance)
                            "security" -> stringResource(R.string.tab_security)
                            "tutorial" -> stringResource(R.string.tab_tutorial)
                            else -> stringResource(R.string.settings_title)
                        },
                        color = Color.White,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Normal
                    )
                }
            }

            val categories = listOf(
                Triple("behavior", stringResource(R.string.tab_behavior_label), stringResource(R.string.tab_behavior_desc)),
                Triple("appearance", stringResource(R.string.tab_appearance_label), stringResource(R.string.tab_appearance_desc)),
                Triple("security", stringResource(R.string.tab_security_label), stringResource(R.string.tab_security_desc)),
                Triple("tutorial", stringResource(R.string.tab_tutorial_label), stringResource(R.string.tab_tutorial_desc))
            )

            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                if (selectedCategory == null) {
                    // --- 1段階目: ジャンル選択リスト ---
                    items(categories) { (id, title, subtitle) ->
                        Surface(
                            color = Color(0x99101728),
                            shape = RoundedCornerShape(20.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { selectedCategory = id }
                        ) {
                            Row(
                                modifier = Modifier.padding(20.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text(
                                        text = title,
                                        color = Color.White,
                                        fontSize = 16.sp,
                                        fontWeight = FontWeight.Normal
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = subtitle,
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
                                        Text(stringResource(R.string.dial_control_settings), color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Normal)

                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(stringResource(R.string.reverse_scroll_direction), color = Color.White, fontSize = 14.sp)
                                                Text(stringResource(R.string.reverse_scroll_direction_desc), color = Color.Gray, fontSize = 12.sp)
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
                        }
                        "appearance" -> {
                            item {
                                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                                    // 外観サブタブ選択
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        listOf(
                                            "wallpaper" to stringResource(R.string.subtab_wallpaper),
                                            "color" to stringResource(R.string.subtab_color),
                                            "font" to stringResource(R.string.subtab_font)
                                        ).forEach { (tabId, label) ->
                                            val isSelected = appearanceSubTab == tabId
                                            Box(
                                                modifier = Modifier
                                                    .weight(1f)
                                                    .height(40.dp)
                                                    .clip(RoundedCornerShape(12.dp))
                                                    .background(if (isSelected) currentAccentColor else Color(0x99101728))
                                                    .clickable { appearanceSubTab = tabId },
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Text(
                                                    text = label,
                                                    color = Color.White,
                                                    fontSize = 14.sp,
                                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                                )
                                            }
                                        }
                                    }

                                    // サブタブ内容
                                    when (appearanceSubTab) {
                                        "wallpaper" -> {
                                            Surface(
                                                color = Color(0x99101728),
                                                shape = RoundedCornerShape(20.dp),
                                                modifier = Modifier.fillMaxWidth()
                                            ) {
                                                Column(
                                                    modifier = Modifier.padding(20.dp),
                                                    verticalArrangement = Arrangement.spacedBy(20.dp)
                                                ) {
                                                    Text(stringResource(R.string.wallpaper_image), color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Normal)
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
                                                            Text(stringResource(R.string.select_image), color = Color.White, fontWeight = FontWeight.Light)
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
                                                                Text(stringResource(R.string.clear), fontWeight = FontWeight.Light)
                                                            }
                                                        }
                                                    }

                                                    HorizontalDivider(color = Color.White.copy(alpha = 0.1f))

                                                    Column {
                                                        Row(
                                                            modifier = Modifier.fillMaxWidth(),
                                                            horizontalArrangement = Arrangement.SpaceBetween
                                                        ) {
                                                            Text(stringResource(R.string.wallpaper_brightness), color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Light)
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
                                        "color" -> {
                                            Surface(
                                                color = Color(0x99101728),
                                                shape = RoundedCornerShape(20.dp),
                                                modifier = Modifier.fillMaxWidth()
                                            ) {
                                                Column(
                                                    modifier = Modifier.padding(20.dp),
                                                    verticalArrangement = Arrangement.spacedBy(16.dp)
                                                ) {
                                                    Text(stringResource(R.string.accent_color), color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Normal)
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
                                        "font" -> {
                                            Surface(
                                                color = Color(0x99101728),
                                                shape = RoundedCornerShape(20.dp),
                                                modifier = Modifier.fillMaxWidth()
                                            ) {
                                                Column(
                                                    modifier = Modifier.padding(20.dp),
                                                    verticalArrangement = Arrangement.spacedBy(20.dp)
                                                ) {
                                                    FontSelectionSection(
                                                        currentFontChoice = currentFontChoice,
                                                        onFontSelected = { newChoice ->
                                                            currentFontChoice = newChoice
                                                            repo.prefManager.setFontChoice(newChoice)
                                                            onChanged()
                                                        },
                                                        onImportClick = { fontPickerLauncher.launch("*/*") },
                                                        customFontPath = customFontPath,
                                                        useCustomFont = useCustomFont,
                                                        onUseCustomFontChanged = { use ->
                                                            useCustomFont = use
                                                            repo.prefManager.setUseCustomFont(use)
                                                            onChanged()
                                                        },
                                                        switchColors = switchColors
                                                    )

                                                    HorizontalDivider(color = Color.White.copy(alpha = 0.1f))

                                                    // 1. アイコンサイズ
                                                    Column {
                                                        Row(
                                                            modifier = Modifier.fillMaxWidth(),
                                                            horizontalArrangement = Arrangement.SpaceBetween
                                                        ) {
                                                            Text(stringResource(R.string.icon_size), color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Light)
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
                                                            Text(stringResource(R.string.text_size), color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Light)
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
                                                            Text(stringResource(R.string.letter_spacing), color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Light)
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
                                                            Text(stringResource(R.string.list_spacing), color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Light)
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
                                        Text(stringResource(R.string.security_settings), color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Normal)

                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(stringResource(R.string.hidden_apps_passcode), color = Color.White, fontSize = 14.sp)
                                                Text(stringResource(R.string.hidden_apps_passcode_desc), color = Color.Gray, fontSize = 12.sp)
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
                                            Text(stringResource(R.string.passcode_pin), color = Color.White, fontSize = 14.sp)
                                            Spacer(modifier = Modifier.height(8.dp))
                                            OutlinedButton(
                                                onClick = {
                                                    newPasscodeField = repo.prefManager.getPasscode()
                                                    showPasscodeChangeDialog = true
                                                },
                                                modifier = Modifier.fillMaxWidth(),
                                                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White)
                                            ) {
                                                Text(stringResource(R.string.change_passcode))
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
                                        Text(stringResource(R.string.tutorial_settings), color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Normal)

                                        Column {
                                            Text(stringResource(R.string.redisplay_guides), color = Color.White, fontSize = 14.sp)
                                            Spacer(modifier = Modifier.height(8.dp))
                                            OutlinedButton(
                                                onClick = {
                                                    repo.prefManager.setFirstWelcomeTutorialShown(false)
                                                    repo.prefManager.setFirstIndexTutorialShown(false)
                                                    repo.prefManager.setFirstHideTutorialShown(false)
                                                    Toast.makeText(context, R.string.toast_tutorial_reset, Toast.LENGTH_SHORT).show()
                                                    onChanged()
                                                },
                                                modifier = Modifier.fillMaxWidth(),
                                                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White)
                                            ) {
                                                Text(stringResource(R.string.redisplay_guides_button))
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
                        Text(stringResource(R.string.save), color = currentAccentColor, fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showPasscodeChangeDialog = false }) {
                        Text(stringResource(R.string.cancel), color = Color.Gray)
                    }
                },
                title = { Text(stringResource(R.string.change_passcode_title)) },
                text = {
                    Column {
                        Text(stringResource(R.string.change_passcode_desc), color = Color.LightGray, fontSize = 13.sp)
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
