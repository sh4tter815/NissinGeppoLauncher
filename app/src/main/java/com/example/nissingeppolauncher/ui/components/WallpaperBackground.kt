package com.example.nissingeppolauncher.ui.components

import android.app.WallpaperManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import com.example.nissingeppolauncher.data.AppPreferenceManager
import java.io.File

@Composable
fun WallpaperBackground(
    modifier: Modifier = Modifier,
    customPath: String? = null,
    updateKey: Long = 0L,
    isHome: Boolean = false,
    currentAlbumArt: Bitmap? = null,
    // アートワーク連動カラーON時に、アートワークから抽出した背景色でオーバーレイを染める
    dynamicBackground: Color? = null
) {
    val context = LocalContext.current
    val prefManager = remember { AppPreferenceManager(context) }
    val homeAlpha = remember(isHome) {
        if (isHome) prefManager.getHomeWallpaperAlpha() else 0.8f
    }

    val fileLastModified = remember(customPath, updateKey) {
        if (!customPath.isNullOrBlank() && File(customPath).exists()) File(customPath).lastModified() else 0L
    }

    val backgroundBitmap = remember(customPath, updateKey, fileLastModified) {
        try {
            if (!customPath.isNullOrBlank() && File(customPath).exists()) {
                val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeFile(customPath, options)
                var sampleSize = 1
                val maxDimension = 1920
                while (options.outWidth / sampleSize > maxDimension || options.outHeight / sampleSize > maxDimension) {
                    sampleSize *= 2
                }
                val decodeOptions = BitmapFactory.Options().apply {
                    inSampleSize = sampleSize
                    inPreferredConfig = android.graphics.Bitmap.Config.ARGB_8888
                }
                BitmapFactory.decodeFile(customPath, decodeOptions)?.asImageBitmap()
            } else {
                @Suppress("DEPRECATION")
                val wallpaperDrawable = WallpaperManager.getInstance(context).builtInDrawable
                wallpaperDrawable?.toBitmap()?.asImageBitmap()
            }
        } catch (_: Exception) {
            null
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        if (currentAlbumArt != null) {
            // アートワーク連動: ぼかし画像は使わず、アートワークから抽出した背景色で単色に塗る
            // （画像を敷かないので軽く、粗さも出ない）
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(dynamicBackground ?: Color(0xFF0B101D))
            )
        } else if (backgroundBitmap != null) {
            Image(
                bitmap = backgroundBitmap,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
            // ホーム画面は設定された不透明度（alpha）、他画面は暗め（0.8f）のオーバーレイ
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xFF0B101D).copy(alpha = homeAlpha))
            )
        } else {
            val bgColor = if (isHome) Color(0xFF0B101D).copy(alpha = homeAlpha) else Color(0xFF0B101D)
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(bgColor)
            )
        }
    }
}
