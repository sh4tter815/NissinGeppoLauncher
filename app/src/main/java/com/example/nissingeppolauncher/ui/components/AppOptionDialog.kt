package com.example.nissingeppolauncher.ui.components

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.net.toUri
import com.example.nissingeppolauncher.data.AppInfo
import com.example.nissingeppolauncher.data.AppPreferenceManager

@Composable
fun AppOptionDialog(
    app: AppInfo,
    onDismiss: () -> Unit,
    onToggleFavorite: (AppInfo) -> Unit,
    onSetYomi: (AppInfo, String) -> Unit,
    onSetYomiKana: (AppInfo, String) -> Unit,
    onAddAlias: (AppInfo, String) -> Unit,
    onRemoveAlias: (AppInfo, String) -> Unit,
    onToggleHide: (AppInfo) -> Unit
) {
    val context = LocalContext.current
    var showYomiInputDialog by remember { mutableStateOf(false) }
    var showAliasDialog by remember { mutableStateOf(false) }
    var yomiInputText by remember { mutableStateOf(app.customYomi) }
    var yomiKanaInputText by remember { mutableStateOf(app.customYomiKana) }
    var aliasInputText by remember { mutableStateOf("") }

    val fontFamily = LocalLauncherFontFamily.current
    val fontWeight = LocalLauncherFontWeight.current
    val textStyle = TextStyle(fontFamily = fontFamily, fontWeight = fontWeight)

    if (showYomiInputDialog) {
        AlertDialog(
            onDismissRequest = { showYomiInputDialog = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        onSetYomi(app, yomiInputText)
                        onSetYomiKana(app, yomiKanaInputText)
                        showYomiInputDialog = false
                        onDismiss()
                    }
                ) {
                    Text("保存", color = Color(0xFF88C0FF), fontFamily = fontFamily, fontWeight = fontWeight)
                }
            },
            dismissButton = {
                TextButton(onClick = { showYomiInputDialog = false }) {
                    Text("キャンセル", color = Color.Gray, fontFamily = fontFamily, fontWeight = fontWeight)
                }
            },
            title = { Text("よみがな登録", fontSize = 18.sp, fontFamily = fontFamily, fontWeight = fontWeight) },
            text = {
                Column {
                    Text(
                        text = "「${app.label}」の読みを登録します。漢字の読みが基本的に優先され、カタカナ読みはスペル順モードでは無効になります。",
                        color = Color.LightGray,
                        fontSize = 14.sp,
                        fontFamily = fontFamily,
                        fontWeight = fontWeight
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "漢字の読み",
                        color = Color.White,
                        fontSize = 13.sp,
                        fontFamily = fontFamily,
                        fontWeight = fontWeight
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    OutlinedTextField(
                        value = yomiInputText,
                        onValueChange = { yomiInputText = it },
                        singleLine = true,
                        placeholder = { Text("例: とけい", color = Color.Gray, fontFamily = fontFamily, fontWeight = fontWeight) },
                        textStyle = textStyle,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedBorderColor = Color.White,
                            unfocusedBorderColor = Color.Gray
                        )
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "カタカナ読み",
                        color = Color.White,
                        fontSize = 13.sp,
                        fontFamily = fontFamily,
                        fontWeight = fontWeight
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    OutlinedTextField(
                        value = yomiKanaInputText,
                        onValueChange = { yomiKanaInputText = it },
                        singleLine = true,
                        placeholder = { Text("例: ライン", color = Color.Gray, fontFamily = fontFamily, fontWeight = fontWeight) },
                        textStyle = textStyle,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedBorderColor = Color.White,
                            unfocusedBorderColor = Color.Gray
                        )
                    )
                }
            },
            containerColor = Color(0xFF141C2E),
            titleContentColor = Color.White
        )
    } else if (showAliasDialog) {
        AlertDialog(
            onDismissRequest = { showAliasDialog = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (aliasInputText.isNotBlank()) {
                            onAddAlias(app, aliasInputText)
                            aliasInputText = ""
                        }
                    }
                ) {
                    Text("追加", color = Color(0xFF88C0FF), fontFamily = fontFamily, fontWeight = fontWeight)
                }
            },
            dismissButton = {
                TextButton(onClick = { showAliasDialog = false; onDismiss() }) {
                    Text("完了", color = Color.Gray, fontFamily = fontFamily, fontWeight = fontWeight)
                }
            },
            title = { Text("検索可能な別名を追加する", fontSize = 18.sp, fontFamily = fontFamily, fontWeight = fontWeight) },
            text = {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = "「${app.label}」を検索する際に追加でヒットさせるキーワード（例: 計算機、電卓など）を何個でも追加登録できます。",
                        color = Color.LightGray,
                        fontSize = 13.sp,
                        fontFamily = fontFamily,
                        fontWeight = fontWeight
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(
                        value = aliasInputText,
                        onValueChange = { aliasInputText = it },
                        singleLine = true,
                        placeholder = { Text("例: 計算機", color = Color.Gray, fontFamily = fontFamily, fontWeight = fontWeight) },
                        modifier = Modifier.fillMaxWidth(),
                        textStyle = textStyle,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedBorderColor = Color.White,
                            unfocusedBorderColor = Color.Gray
                        )
                    )

                    if (app.searchAliases.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "登録済みの別名:",
                            color = Color.White,
                            fontSize = 14.sp,
                            fontFamily = fontFamily,
                            fontWeight = fontWeight
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 140.dp)
                        ) {
                            items(app.searchAliases) { alias ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 4.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "・ $alias",
                                        color = Color.LightGray,
                                        fontSize = 14.sp,
                                        fontFamily = fontFamily,
                                        fontWeight = fontWeight
                                    )
                                    Text(
                                        text = "削除",
                                        color = Color(0xFFFF8888),
                                        fontSize = 13.sp,
                                        fontFamily = fontFamily,
                                        fontWeight = fontWeight,
                                        modifier = Modifier.pressScale().clickable {
                                            onRemoveAlias(app, alias)
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            },
            containerColor = Color(0xFF141C2E),
            titleContentColor = Color.White
        )
    } else {
        val categories = listOf(
            OptionEntry(
                kind = "favorite",
                label = "お気に入り",
                valueText = if (app.isFavorite) "登録中" else null,
                actions = listOf(
                    if (app.isFavorite) {
                        OptionAction("favorite", "解除する") { onToggleFavorite(app); onDismiss() }
                    } else {
                        OptionAction("favorite", "登録する") { onToggleFavorite(app); onDismiss() }
                    }
                )
            ),
            OptionEntry(
                kind = "yomi",
                label = "よみがな",
                valueText = app.customYomi.ifBlank { app.customYomiKana }.ifBlank { "未登録" },
                actions = buildList {
                    add(OptionAction("yomi", if (app.customYomi.isNotBlank() || app.customYomiKana.isNotBlank()) "変更する" else "登録する") { showYomiInputDialog = true })
                    if (app.customYomi.isNotBlank() || app.customYomiKana.isNotBlank()) {
                        add(OptionAction("trash", "削除する", danger = true) { onSetYomi(app, ""); onSetYomiKana(app, ""); onDismiss() })
                    }
                }
            ),
            OptionEntry(
                kind = "alias",
                label = "別名",
                valueText = if (app.searchAliases.isEmpty()) "未登録" else "${app.searchAliases.size}件",
                actions = buildList {
                    add(OptionAction("plus", "追加する") { showAliasDialog = true })
                    app.searchAliases.forEach { alias ->
                        add(OptionAction("trash", alias, valueText = "削除", danger = true) { onRemoveAlias(app, alias) })
                    }
                }
            ),
            OptionEntry(
                kind = "hide",
                label = "非表示",
                valueText = if (app.isHidden) "非表示中" else null,
                actions = listOf(
                    if (app.isHidden) {
                        OptionAction("hide", "解除する") { onToggleHide(app); onDismiss() }
                    } else {
                        OptionAction("hide", "非表示にする", danger = true) { onToggleHide(app); onDismiss() }
                    }
                )
            ),
            OptionEntry(
                kind = "info",
                label = "アプリ情報",
                valueText = null,
                actions = listOf(
                    OptionAction("open", "開く") {
                        val intent = Intent(
                            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                            "package:${app.packageName}".toUri()
                        )
                        context.startActivity(intent)
                        onDismiss()
                    }
                )
            ),
            OptionEntry(
                kind = "close",
                label = "閉じる",
                valueText = null,
                actions = listOf(
                    OptionAction("close", "閉じる") { onDismiss() }
                )
            )
        )
        var focusIdx by remember { mutableIntStateOf(0) }
        val categoryDiscState = rememberDiscCarouselState(initialIndex = 0, stepAngle = 30f)
        val actionDiscState = rememberDiscCarouselState(initialIndex = 0, stepAngle = 30f)
        var leftHeld by remember { mutableStateOf(false) }
        val density = LocalDensity.current.density
        Dialog(
            onDismissRequest = onDismiss,
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            val dialogView = LocalView.current
            DisposableEffect(dialogView) {
                val window = (dialogView.parent as? DialogWindowProvider)?.window
                var blurApplied = false
                if (window != null && android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                    blurApplied = runCatching {
                        window.addFlags(android.view.WindowManager.LayoutParams.FLAG_BLUR_BEHIND)
                        window.attributes = window.attributes.apply { blurBehindRadius = 48 }
                        true
                    }.getOrDefault(false)
                }
                onDispose {
                    if (blurApplied && window != null) {
                        runCatching {
                            window.clearFlags(android.view.WindowManager.LayoutParams.FLAG_BLUR_BEHIND)
                            window.attributes = window.attributes.apply { blurBehindRadius = 0 }
                        }
                    }
                }
            }
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.94f)
                    .height(480.dp)
                    .clip(RoundedCornerShape(24.dp))
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(start = 20.dp, top = 18.dp, end = 20.dp)
                ) {
                    val bmp = app.iconBitmap
                    if (bmp != null) {
                        Image(bitmap = bmp, contentDescription = null, modifier = Modifier.size(40.dp).clip(RoundedCornerShape(percent = (AppPreferenceManager(context).getIconCornerRatio() * 50).toInt())))
                        Spacer(modifier = Modifier.width(12.dp))
                    }
                    Column {
                        Text(text = app.label, color = Color.White, fontSize = 19.sp, fontFamily = fontFamily, fontWeight = fontWeight)
                        Text(text = app.packageName, fontSize = 11.sp, color = Color.Gray, fontFamily = fontFamily, fontWeight = fontWeight)
                    }
                }
                Box(modifier = Modifier.fillMaxSize().padding(top = 76.dp)) {
                    DiscWheel(
                        entries = categories.map { cat ->
                            DiscEntry(
                                id = cat.kind,
                                label = cat.label,
                                valueText = cat.valueText,
                                icon = { DiscGlyph(optionGlyph(cat.kind), Color(0xFF88C0FF)) }
                            )
                        },
                        side = DiscSide.LEFT,
                        accentColor = Color(0xFF88C0FF),
                        modifier = Modifier.fillMaxSize(),
                        state = categoryDiscState,
                        gesturesEnabled = false,
                        stepAngle = 30f,
                        discCenterXDp = -80f,
                        focusedLabelOnly = true,
                        verticalRestLabel = true,
                        fadeLabelsOnScroll = false,
                        holdExpand = true,
                        holdActive = leftHeld,
                        onFocusChange = { focusIdx = it }
                    )
                    val focusedActions = categories.getOrNull(focusIdx)?.actions ?: emptyList()
                    DiscWheel(
                        entries = focusedActions.map { act ->
                            DiscEntry(
                                id = act.kind + ":" + act.label,
                                label = act.label,
                                valueText = act.valueText,
                                icon = { DiscGlyph(optionGlyph(act.kind), if (act.danger) Color(0xFFEF5350) else Color(0xFF88C0FF)) }
                            )
                        },
                        side = DiscSide.RIGHT,
                        accentColor = Color(0xFF88C0FF),
                        modifier = Modifier.fillMaxSize(),
                        state = actionDiscState,
                        stepAngle = 30f,
                        contentKey = focusIdx,
                        tapToApply = true,
                        accentSelectedLabel = true,
                        fadeLabelsOnScroll = false,
                        onEntryClick = { idx -> focusedActions.getOrNull(idx)?.run?.invoke() }
                    )
                    Row(modifier = Modifier.fillMaxSize()) {
                        Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
                            DiscTouchHalf(
                                state = categoryDiscState,
                                isRight = false,
                                radiusPx = 165f * density,
                                isReverseScroll = { AppPreferenceManager(context).isReverseScroll() },
                                modifier = Modifier.fillMaxSize(),
                                onHoldingChanged = { leftHeld = it },
                                discCenterXPx = -80f * density,
                                discCenterYPx = 202f * density,
                                onItemTap = { idx -> categoryDiscState.animateToIndex(idx); focusIdx = idx }
                            )
                        }
                        Box(modifier = Modifier.weight(1f).fillMaxHeight())
                    }
                }
            }
        }
    }

}

@Composable
private fun DialogOptionItem(
    kind: String,
    text: String,
    subtitle: String,
    danger: Boolean = false,
    fontFamily: androidx.compose.ui.text.font.FontFamily,
    fontWeight: androidx.compose.ui.text.font.FontWeight,
    onClick: () -> Unit
) {
    Surface(
        color = Color(0xFF1E283D),
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier
            .fillMaxWidth()
            .pressScale()
            .clickable { onClick() }
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OptionKindIcon(kind = kind, danger = danger)
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text(
                    text = text,
                    color = if (danger) Color(0xFFFFB4B4) else Color.White,
                    fontSize = 15.5.sp,
                    fontFamily = fontFamily,
                    fontWeight = fontWeight
                )
                Text(
                    text = subtitle,
                    color = Color.Gray,
                    fontSize = 11.5.sp,
                    fontFamily = fontFamily,
                    fontWeight = fontWeight
                )
            }
        }
    }
}

// 長押しメニューの小さな線画アイコン（設定画面のカテゴリアイコンと同じタッチ）
@Composable
private fun OptionKindIcon(kind: String, danger: Boolean) {
    val accent = if (danger) Color(0xFFFF9E9E) else Color(0xFF88C0FF)
    Canvas(modifier = Modifier.size(30.dp)) {
        val w = size.width
        val h = size.height
        val sw = w * 0.055f
        when (kind) {
            "favorite" -> {
                val cx = w * 0.5f
                val cy = h * 0.52f
                val rO = w * 0.40f
                val rI = w * 0.17f
                val path = Path()
                for (i in 0 until 10) {
                    val r = if (i % 2 == 0) rO else rI
                    val a = Math.toRadians(-90.0 + i * 36.0)
                    val x = cx + (r * kotlin.math.cos(a)).toFloat()
                    val y = cy + (r * kotlin.math.sin(a)).toFloat()
                    if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
                }
                path.close()
                drawPath(path, color = accent, style = Stroke(width = sw, cap = StrokeCap.Round))
            }
            "yomi" -> {
                // 鉛筆: 斜めの軸と三角の芯先、下線
                drawLine(accent, Offset(w * 0.30f, h * 0.74f), Offset(w * 0.68f, h * 0.36f), strokeWidth = sw * 1.5f, cap = StrokeCap.Round)
                drawLine(accent, Offset(w * 0.68f, h * 0.36f), Offset(w * 0.76f, h * 0.24f), strokeWidth = sw * 1.5f, cap = StrokeCap.Round)
                drawLine(accent, Offset(w * 0.76f, h * 0.24f), Offset(w * 0.38f, h * 0.82f), strokeWidth = sw, cap = StrokeCap.Round)
                drawLine(accent, Offset(w * 0.16f, h * 0.88f), Offset(w * 0.84f, h * 0.88f), strokeWidth = sw, cap = StrokeCap.Round)
            }
            "alias" -> {
                drawCircle(color = accent, radius = w * 0.24f, center = Offset(w * 0.42f, h * 0.42f), style = Stroke(width = sw))
                drawLine(accent, Offset(w * 0.60f, h * 0.60f), Offset(w * 0.82f, h * 0.82f), strokeWidth = sw * 1.4f, cap = StrokeCap.Round)
            }
            "hide" -> {
                val path = Path()
                path.moveTo(w * 0.08f, h * 0.52f)
                path.cubicTo(w * 0.30f, h * 0.08f, w * 0.70f, h * 0.08f, w * 0.92f, h * 0.52f)
                path.cubicTo(w * 0.70f, h * 0.96f, w * 0.30f, h * 0.96f, w * 0.08f, h * 0.52f)
                drawPath(path, color = accent, style = Stroke(width = sw, cap = StrokeCap.Round))
                drawCircle(color = accent, radius = w * 0.09f, center = Offset(w * 0.5f, h * 0.52f), style = Stroke(width = sw))
                drawLine(accent, Offset(w * 0.18f, h * 0.86f), Offset(w * 0.82f, h * 0.18f), strokeWidth = sw * 1.3f, cap = StrokeCap.Round)
            }
            "close" -> {
                drawLine(accent, Offset(w * 0.26f, h * 0.26f), Offset(w * 0.74f, h * 0.74f), strokeWidth = sw * 1.4f, cap = StrokeCap.Round)
                drawLine(accent, Offset(w * 0.74f, h * 0.26f), Offset(w * 0.26f, h * 0.74f), strokeWidth = sw * 1.4f, cap = StrokeCap.Round)
            }
            "plus" -> {
                drawLine(accent, Offset(w * 0.5f, h * 0.24f), Offset(w * 0.5f, h * 0.76f), strokeWidth = sw * 1.4f, cap = StrokeCap.Round)
                drawLine(accent, Offset(w * 0.24f, h * 0.5f), Offset(w * 0.76f, h * 0.5f), strokeWidth = sw * 1.4f, cap = StrokeCap.Round)
            }
            "trash" -> {
                drawLine(accent, Offset(w * 0.20f, h * 0.30f), Offset(w * 0.80f, h * 0.30f), strokeWidth = sw * 1.3f, cap = StrokeCap.Round)
                drawLine(accent, Offset(w * 0.38f, h * 0.30f), Offset(w * 0.42f, h * 0.78f), strokeWidth = sw * 1.2f, cap = StrokeCap.Round)
                drawLine(accent, Offset(w * 0.62f, h * 0.30f), Offset(w * 0.58f, h * 0.78f), strokeWidth = sw * 1.2f, cap = StrokeCap.Round)
                drawLine(accent, Offset(w * 0.42f, h * 0.78f), Offset(w * 0.58f, h * 0.78f), strokeWidth = sw * 1.2f, cap = StrokeCap.Round)
                drawLine(accent, Offset(w * 0.40f, h * 0.22f), Offset(w * 0.60f, h * 0.22f), strokeWidth = sw * 1.2f, cap = StrokeCap.Round)
            }
            "open" -> {
                drawLine(accent, Offset(w * 0.30f, h * 0.30f), Offset(w * 0.30f, h * 0.74f), strokeWidth = sw * 1.3f, cap = StrokeCap.Round)
                drawLine(accent, Offset(w * 0.30f, h * 0.74f), Offset(w * 0.74f, h * 0.74f), strokeWidth = sw * 1.3f, cap = StrokeCap.Round)
                drawLine(accent, Offset(w * 0.74f, h * 0.74f), Offset(w * 0.74f, h * 0.52f), strokeWidth = sw * 1.3f, cap = StrokeCap.Round)
                drawLine(accent, Offset(w * 0.46f, h * 0.54f), Offset(w * 0.76f, h * 0.24f), strokeWidth = sw * 1.3f, cap = StrokeCap.Round)
                drawLine(accent, Offset(w * 0.58f, h * 0.24f), Offset(w * 0.76f, h * 0.24f), strokeWidth = sw * 1.3f, cap = StrokeCap.Round)
                drawLine(accent, Offset(w * 0.76f, h * 0.24f), Offset(w * 0.76f, h * 0.42f), strokeWidth = sw * 1.3f, cap = StrokeCap.Round)
            }
            else -> {
                // info: 円と「i」
                drawCircle(color = accent, radius = w * 0.38f, center = Offset(w * 0.5f, h * 0.5f), style = Stroke(width = sw))
                drawCircle(color = accent, radius = w * 0.045f, center = Offset(w * 0.5f, h * 0.33f))
                drawLine(accent, Offset(w * 0.5f, h * 0.47f), Offset(w * 0.5f, h * 0.70f), strokeWidth = sw * 1.4f, cap = StrokeCap.Round)
            }
        }
    }
}

/** ポップアップの項目種別 → Lucideグリフ名 */
private fun optionGlyph(kind: String): String = when (kind) {
    "favorite" -> "star"
    "yomi" -> "languages"
    "alias" -> "tag"
    "hide" -> "eye-off"
    "info" -> "info"
    "close" -> "x"
    "plus" -> "plus"
    "trash" -> "trash"
    "open" -> "external-link"
    else -> "cursor"
}

private class OptionAction(
    val kind: String,
    val label: String,
    val valueText: String? = null,
    val danger: Boolean = false,
    val run: () -> Unit
)

private class OptionEntry(
    val kind: String,
    val label: String,
    val valueText: String?,
    val actions: List<OptionAction>
)
