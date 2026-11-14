package com.example.nissingeppolauncher.ui.components

import android.content.Intent
import android.provider.AlarmClock
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun ClockWidget(
    modifier: Modifier = Modifier,
    lyricsLine: String = "",
    // 日付の表示方式: "sideways"=常時横倒し / "under"=常時時計の下 / "auto"=歌詞の有無で切替
    dateMode: String = "auto"
) {
    val context = LocalContext.current
    var currentTime by remember { mutableStateOf(Date()) }

    // 1秒毎にシステム時刻を更新
    LaunchedEffect(Unit) {
        while (true) {
            currentTime = Date()
            delay(1000)
        }
    }

    val hourFormat = SimpleDateFormat("HH", Locale.getDefault())
    val minuteFormat = SimpleDateFormat("mm", Locale.getDefault())
    val dateFormat = SimpleDateFormat("M/d (E)", Locale.JAPANESE)

    val hourText = hourFormat.format(currentTime)
    val minuteText = minuteFormat.format(currentTime)
    val dateText = dateFormat.format(currentTime)

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

    Column(modifier = modifier) {
        if (sideways) {
            Row {
                // 時刻ブロック (タップで時計アプリ起動)
                Column(
                    modifier = Modifier
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) { openClock() }
                ) {
                    Text(
                        text = hourText,
                        color = Color.White,
                        fontSize = 54.sp,
                        fontWeight = FontWeight.Bold,
                        lineHeight = 54.sp
                    )
                    Text(
                        text = minuteText,
                        color = Color.White,
                        fontSize = 54.sp,
                        fontWeight = FontWeight.Bold,
                        lineHeight = 54.sp
                    )
                }

                // 日付ブロック (時刻の右に横倒しで表示。タップでカレンダーアプリ起動)
                Box(
                    modifier = Modifier
                        .padding(start = 12.dp, top = 2.dp)
                        .width(20.dp)
                        .height(108.dp)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) { openCalendar() },
                    contentAlignment = Alignment.Center
                ) {
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

            // 同期歌詞の現在行 (時計の下に表示)
            if (showLyrics) {
                Text(
                    text = lyricsLine,
                    color = Color.White,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = LocalTextStyle.current.copy(
                        shadow = Shadow(
                            color = Color.Black.copy(alpha = 0.6f),
                            offset = Offset(0f, 0f),
                            blurRadius = 8f
                        )
                    ),
                    modifier = Modifier
                        .padding(top = 16.dp)
                        .widthIn(max = 250.dp)
                )
            }
        } else {
            // 時刻ブロック (タップで時計アプリ起動)
            Column(
                modifier = Modifier
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) { openClock() }
            ) {
                Text(
                    text = hourText,
                    color = Color.White,
                    fontSize = 54.sp,
                    fontWeight = FontWeight.Bold,
                    lineHeight = 54.sp
                )
                Text(
                    text = minuteText,
                    color = Color.White,
                    fontSize = 54.sp,
                    fontWeight = FontWeight.Bold,
                    lineHeight = 54.sp
                )
            }

            // 日付ブロック (時計の下・横書き。タップでカレンダーアプリ起動)
            Text(
                text = dateText,
                color = Color.White.copy(alpha = 0.75f),
                fontSize = 17.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier
                    .padding(top = 16.dp)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) { openCalendar() }
            )

            // 同期歌詞の現在行 (日付の下に表示)
            if (showLyrics) {
                Text(
                    text = lyricsLine,
                    color = Color.White,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = LocalTextStyle.current.copy(
                        shadow = Shadow(
                            color = Color.Black.copy(alpha = 0.6f),
                            offset = Offset(0f, 0f),
                            blurRadius = 8f
                        )
                    ),
                    modifier = Modifier
                        .padding(top = 10.dp)
                        .widthIn(max = 250.dp)
                )
            }
        }
    }
}
