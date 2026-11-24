package com.example.nissingeppolauncher.ui.components

import android.content.Intent
import android.provider.AlarmClock
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import com.example.nissingeppolauncher.ui.lyricMixedFontText
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.nissingeppolauncher.data.LyricLine
import com.example.nissingeppolauncher.ui.KaraokeLyricText
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun ClockWidget(
    modifier: Modifier = Modifier,
    lyricsLine: String = "",
    // 日付の表示方式: "sideways"=常時横倒し / "under"=常時時計の下 / "auto"=歌詞の有無で切替
    dateMode: String = "auto",
    // 歌詞行のグロー色（アクセントカラー）
    glowColor: Color = Color.White,
    // カラオケ表示用: 文字同期つきの現在行（あれば歌詞行をテロップ表示にする）
    karaokeLine: LyricLine? = null,
    karaokePositionMs: Long = 0L,
    karaokeSungColor: Color = Color(0xFF7FD4F2),
    // 対訳（翻訳ONのとき、現在行の下に小さく出す）
    translationText: String = "",
    // 歌詞行のフォント（歌詞設定のフォント）と、英字だけ別にする場合のフォント
    lyricFontFamily: FontFamily? = null,
    lyricEnFontFamily: FontFamily? = null,
    dopagaki: Boolean = false,
    rainbowGlow: Boolean = false,
    // 追加表示: 秒 / 12時間制 / バッテリー残量 / 和暦
    showSeconds: Boolean = false,
    hour12: Boolean = false,
    // 時刻を横一列（10:34）で表示する
    horizontal: Boolean = false,
    showBattery: Boolean = false,
    wareki: Boolean = false,
    // 時計と日付の分離表示用（レイアウト編集で別々に動かすため、片方だけ出す使い方ができる）
    showTime: Boolean = true,
    showDate: Boolean = true
) {
    val context = LocalContext.current
    val effGlow = if (rainbowGlow) dopaRainbowColor(seed = 5) else glowColor
    var currentTime by remember { mutableStateOf(Date()) }

    // 1秒毎にシステム時刻を更新
    LaunchedEffect(Unit) {
        while (true) {
            currentTime = Date()
            delay(1000)
        }
    }

    val hourFormat = SimpleDateFormat(if (hour12) "h" else "HH", Locale.getDefault())
    val minuteFormat = SimpleDateFormat("mm", Locale.getDefault())
    val secondFormat = SimpleDateFormat("ss", Locale.getDefault())
    val ampmFormat = SimpleDateFormat("a", Locale.JAPANESE)
    val dateFormat = if (wareki) SimpleDateFormat("GGy年M月d日 (E)", Locale.JAPANESE) else SimpleDateFormat("M/d (E)", Locale.JAPANESE)

    val hourText = hourFormat.format(currentTime)
    val minuteText = minuteFormat.format(currentTime)
    val secondText = secondFormat.format(currentTime)
    val ampmText = ampmFormat.format(currentTime)
    val dateText = dateFormat.format(currentTime)
    // バッテリー残量はスティッキーインテントから毎秒ついでに読む（レシーバ登録はしない）
    val batteryPct = if (showBattery) {
        try {
            val bi = context.registerReceiver(null, android.content.IntentFilter(android.content.Intent.ACTION_BATTERY_CHANGED))
            val level = bi?.getIntExtra(android.os.BatteryManager.EXTRA_LEVEL, -1) ?: -1
            val scale = bi?.getIntExtra(android.os.BatteryManager.EXTRA_SCALE, -1) ?: -1
            if (level >= 0 && scale > 0) level * 100 / scale else -1
        } catch (_: Exception) { -1 }
    } else -1

    val openClock = {
        try {
            val intent = Intent(AlarmClock.ACTION_SHOW_ALARMS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
        } catch (_: Exception) {
            try {
                val intent = context.packageManager.getLaunchIntentForPackage("com.google.android.deskclock")
                    ?: context.packageManager.getLaunchIntentForPackage("com.android.deskclock")
                if (intent != null) context.startActivity(intent)
            } catch (_: Exception) {}
        }
    }

    val openCalendar = {
        try {
            val intent = Intent(Intent.ACTION_VIEW).apply {
                data = android.provider.CalendarContract.CONTENT_URI.buildUpon().appendPath("time").build()
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (_: Exception) {
            try {
                val intent = context.packageManager.getLaunchIntentForPackage("com.google.android.calendar")
                    ?: context.packageManager.getLaunchIntentForPackage("com.android.calendar")
                if (intent != null) context.startActivity(intent)
            } catch (_: Exception) {}
        }
    }

    val showLyrics = lyricsLine.isNotBlank()
    // 日付を横倒し（時刻の右）にするかどうか
    val sideways = when (dateMode) {
        "sideways" -> true
        "under" -> false
        else -> showLyrics
    }

    // 歌詞行のグロー（アクセントカラーで発光させる）
    val lyricsStyle = LocalTextStyle.current.copy(
        fontFamily = lyricFontFamily,
        shadow = Shadow(
            color = effGlow.copy(alpha = 0.9f),
            offset = Offset(0f, 0f),
            blurRadius = 16f
        )
    )

    Column(modifier = modifier) {
        if (sideways) {
            Row {
                // 時刻ブロック (タップで時計アプリ起動)
                if (showTime) Column(
                    modifier = Modifier
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) { openClock() }
                ) {
                    if (horizontal) {
                        Row(verticalAlignment = Alignment.Bottom) {
                            if (dopagaki) {
                                DopaFlashText(text = "$hourText:$minuteText", fontSize = 54.sp, lineHeight = 54.sp)
                            } else {
                                Text(
                                    text = "$hourText:$minuteText",
                                    color = Color.White,
                                    fontSize = 54.sp,
                                    fontWeight = FontWeight.Bold,
                                    lineHeight = 54.sp
                                )
                            }
                            if (hour12) {
                                Text(
                                    text = ampmText,
                                    color = Color.White.copy(alpha = 0.75f),
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Medium,
                                    modifier = Modifier.padding(start = 6.dp, bottom = 8.dp)
                                )
                            }
                            if (showSeconds) {
                                Text(
                                    text = ":$secondText",
                                    color = Color.White.copy(alpha = 0.7f),
                                    fontSize = 22.sp,
                                    fontWeight = FontWeight.Medium,
                                    modifier = Modifier.padding(start = 2.dp, bottom = 6.dp)
                                )
                            }
                        }
                    } else {
                    Row(verticalAlignment = Alignment.Bottom) {
                        if (dopagaki) {
                            DopaFlashText(text = hourText, fontSize = 54.sp, lineHeight = 54.sp)
                        } else {
                            Text(
                                text = hourText,
                                color = Color.White,
                                fontSize = 54.sp,
                                fontWeight = FontWeight.Bold,
                                lineHeight = 54.sp
                            )
                        }
                        if (hour12) {
                            Text(
                                text = ampmText,
                                color = Color.White.copy(alpha = 0.75f),
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Medium,
                                modifier = Modifier.padding(start = 6.dp, bottom = 8.dp)
                            )
                        }
                    }
                    Row(verticalAlignment = Alignment.Bottom) {
                        if (dopagaki) {
                            DopaFlashText(text = minuteText, fontSize = 54.sp, lineHeight = 54.sp)
                        } else {
                            Text(
                                text = minuteText,
                                color = Color.White,
                                fontSize = 54.sp,
                                fontWeight = FontWeight.Bold,
                                lineHeight = 54.sp
                            )
                        }
                        if (showSeconds) {
                            Text(
                                text = ":$secondText",
                                color = Color.White.copy(alpha = 0.7f),
                                fontSize = 22.sp,
                                fontWeight = FontWeight.Medium,
                                modifier = Modifier.padding(start = 2.dp, bottom = 6.dp)
                            )
                        }
                    }
                    }
                }

                // 日付ブロック (時刻の右に横倒しで表示。タップでカレンダーアプリ起動)
                if (showDate) Box(
                    modifier = Modifier
                        .padding(start = 12.dp, top = 2.dp)
                        .width(20.dp)
                        .height(if (horizontal) 56.dp else 108.dp)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) { openCalendar() },
                    contentAlignment = Alignment.Center
                ) {
                    if (dopagaki) {
                        DopaFlashText(text = dateText, fontSize = 17.sp, maxLines = 1, modifier = Modifier.wrapContentSize(unbounded = true).graphicsLayer { rotationZ = 90f })
                    } else {
                        Text(
                            text = dateText,
                            color = Color.White.copy(alpha = 0.75f),
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1,
                            modifier = Modifier
                                .wrapContentSize(unbounded = true)
                                .graphicsLayer { rotationZ = 90f }
                        )
                    }
                }
            }

            if (showTime && showBattery && batteryPct >= 0) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                    modifier = Modifier.padding(top = 8.dp)
                ) {
                    BatteryLevelIcon(pct = batteryPct, color = Color.White.copy(alpha = 0.85f))
                    Text(
                        text = "$batteryPct%",
                        color = Color.White.copy(alpha = 0.7f),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            // 同期歌詞の現在行 (時計の下に表示)。行の切り替わりは音楽アプリと同じ
            // 350msのクロスフェードでつなぐ
            if (showTime && showLyrics) {
                Crossfade(
                    targetState = lyricsLine,
                    animationSpec = tween(durationMillis = 350),
                    label = "LyricsCrossfade",
                    modifier = Modifier
                        .padding(top = 16.dp)
                        .widthIn(max = 250.dp)
                ) { line ->
                    val kLine = karaokeLine
                    if (dopagaki) {
                        DopaFlashText(text = line, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    } else if (kLine != null && kLine.text == line && kLine.words.isNotEmpty()) {
                        KaraokeLyricText(
                            line = kLine,
                            positionMs = karaokePositionMs,
                            sungColor = karaokeSungColor,
                            baseColor = Color.White,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Medium,
                            fontFamily = lyricFontFamily,
                            enFontFamily = lyricEnFontFamily,
                            shadow = Shadow(color = effGlow.copy(alpha = 0.9f), offset = Offset(0f, 0f), blurRadius = 16f)
                        )
                    } else {
                        Text(
                            text = lyricMixedFontText(line, lyricEnFontFamily),
                            color = Color.White,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            style = lyricsStyle
                        )
                    }
                }
                if (translationText.isNotBlank()) {
                    Text(
                        text = translationText,
                        color = Color.White.copy(alpha = 0.7f),
                        fontSize = 11.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 3.dp).widthIn(max = 250.dp)
                    )
                }
            }
        } else {
            // 時刻ブロック (タップで時計アプリ起動)
            if (showTime) Column(
                modifier = Modifier
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) { openClock() }
            ) {
                if (horizontal) {
                    Row(verticalAlignment = Alignment.Bottom) {
                        if (dopagaki) {
                            DopaFlashText(text = "$hourText:$minuteText", fontSize = 54.sp, lineHeight = 54.sp)
                        } else {
                            Text(
                                text = "$hourText:$minuteText",
                                color = Color.White,
                                fontSize = 54.sp,
                                fontWeight = FontWeight.Bold,
                                lineHeight = 54.sp
                            )
                        }
                        if (hour12) {
                            Text(
                                text = ampmText,
                                color = Color.White.copy(alpha = 0.75f),
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Medium,
                                modifier = Modifier.padding(start = 6.dp, bottom = 8.dp)
                            )
                        }
                        if (showSeconds) {
                            Text(
                                text = ":$secondText",
                                color = Color.White.copy(alpha = 0.7f),
                                fontSize = 22.sp,
                                fontWeight = FontWeight.Medium,
                                modifier = Modifier.padding(start = 2.dp, bottom = 6.dp)
                            )
                        }
                    }
                } else {
                Row(verticalAlignment = Alignment.Bottom) {
                    if (dopagaki) {
                    DopaFlashText(text = hourText, fontSize = 54.sp, lineHeight = 54.sp)
                    } else {
                    Text(
                        text = hourText,
                        color = Color.White,
                        fontSize = 54.sp,
                        fontWeight = FontWeight.Bold,
                        lineHeight = 54.sp
                    )
                    }
                    if (hour12) {
                    Text(
                        text = ampmText,
                        color = Color.White.copy(alpha = 0.75f),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.padding(start = 6.dp, bottom = 8.dp)
                    )
                    }
                }
                Row(verticalAlignment = Alignment.Bottom) {
                    if (dopagaki) {
                    DopaFlashText(text = minuteText, fontSize = 54.sp, lineHeight = 54.sp)
                    } else {
                    Text(
                        text = minuteText,
                        color = Color.White,
                        fontSize = 54.sp,
                        fontWeight = FontWeight.Bold,
                        lineHeight = 54.sp
                    )
                    }
                    if (showSeconds) {
                    Text(
                        text = ":$secondText",
                        color = Color.White.copy(alpha = 0.7f),
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.padding(start = 2.dp, bottom = 6.dp)
                    )
                    }
                }
                }
            }

            // 日付ブロック (時計の下・横書き。タップでカレンダーアプリ起動)
            if (showDate || showBattery) Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(top = 16.dp)
            ) {
                if (showDate) Text(
                    text = dateText,
                    color = Color.White.copy(alpha = 0.75f),
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) { openCalendar() }
                )
                if (showBattery && batteryPct >= 0) {
                    Spacer(modifier = Modifier.width(10.dp))
                    BatteryLevelIcon(pct = batteryPct, color = Color.White.copy(alpha = 0.85f))
                    Text(
                        text = "$batteryPct%",
                        color = Color.White.copy(alpha = 0.7f),
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.padding(start = 4.dp)
                    )
                }
            }

            // 同期歌詞の現在行 (日付の下に表示)。行の切り替わりは350msのクロスフェード
            if (showTime && showLyrics) {
                Crossfade(
                    targetState = lyricsLine,
                    animationSpec = tween(durationMillis = 350),
                    label = "LyricsCrossfade",
                    modifier = Modifier
                        .padding(top = 10.dp)
                        .widthIn(max = 250.dp)
                ) { line ->
                    val kLine = karaokeLine
                    if (dopagaki) {
                        DopaFlashText(text = line, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    } else if (kLine != null && kLine.text == line && kLine.words.isNotEmpty()) {
                        KaraokeLyricText(
                            line = kLine,
                            positionMs = karaokePositionMs,
                            sungColor = karaokeSungColor,
                            baseColor = Color.White,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Medium,
                            fontFamily = lyricFontFamily,
                            enFontFamily = lyricEnFontFamily,
                            shadow = Shadow(color = effGlow.copy(alpha = 0.9f), offset = Offset(0f, 0f), blurRadius = 16f)
                        )
                    } else {
                        Text(
                            text = lyricMixedFontText(line, lyricEnFontFamily),
                            color = Color.White,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            style = lyricsStyle
                        )
                    }
                }
                if (translationText.isNotBlank()) {
                    Text(
                        text = translationText,
                        color = Color.White.copy(alpha = 0.7f),
                        fontSize = 11.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 3.dp).widthIn(max = 250.dp)
                    )
                }
            }
        }
    }
}

/** Lucide「battery」の輪郭に、残量ぶんだけ中身を塗った電池アイコン */
@Composable
private fun BatteryLevelIcon(pct: Int, color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier.size(width = 27.dp, height = 15.dp)) {
        val sx = size.width / 24f
        val sy = size.height / 24f
        val stroke = 1.6f * sx
        drawRoundRect(
            color = color,
            topLeft = Offset(2f * sx, 7f * sy),
            size = Size(16f * sx, 10f * sy),
            cornerRadius = CornerRadius(2f * sx, 2f * sy),
            style = Stroke(width = stroke)
        )
        drawLine(
            color = color,
            start = Offset(22f * sx, 11f * sy),
            end = Offset(22f * sx, 13f * sy),
            strokeWidth = stroke
        )
        val frac = pct.coerceIn(0, 100) / 100f
        if (frac > 0f) {
            drawRoundRect(
                color = color,
                topLeft = Offset(3.6f * sx, 8.6f * sy),
                size = Size(12.8f * sx * frac, 6.8f * sy),
                cornerRadius = CornerRadius(1.2f * sx, 1.2f * sy)
            )
        }
    }
}
