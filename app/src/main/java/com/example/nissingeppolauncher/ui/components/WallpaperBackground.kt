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
    dynamicBackground: Color? = null,
    // アートワーク背景の見せ方: "solid"（単色）/ "blur"（ぼかし画像）
    artworkBackgroundStyle: String = "solid"
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

    // ぼかし背景は曲ごとに1回だけ作る静止画（256pxまで縮小→強いボックスブラーを3回→
    // 画面サイズへバイリニアで引き伸ばし）。毎フレームの再計算はないので軽い
    val blurredArtImage = remember(currentAlbumArt) {
        currentAlbumArt?.let { makeBlurredBackground(it).asImageBitmap() }
    }

    Box(modifier = modifier.fillMaxSize()) {
        if (currentAlbumArt != null && artworkBackgroundStyle == "blur" && blurredArtImage != null) {
            androidx.compose.foundation.Canvas(modifier = Modifier.fillMaxSize()) {
                drawImage(
                    image = blurredArtImage,
                    dstSize = androidx.compose.ui.unit.IntSize(size.width.toInt(), size.height.toInt()),
                    filterQuality = androidx.compose.ui.graphics.FilterQuality.High
                )
            }
            // 音楽アプリと同じく、抽出した背景色を55%で重ねて色味を残す
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background((dynamicBackground ?: Color(0xFF0B101D)).copy(alpha = 0.55f))
            )
        } else if (currentAlbumArt != null) {
            // アートワーク連動: 抽出した背景色で単色に塗る
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

private fun boxBlurBitmap(src: Bitmap, radius: Int): Bitmap {
    val w = src.width
    val h = src.height
    if (w <= 0 || h <= 0 || radius <= 0) return src
    val pixels = IntArray(w * h)
    src.getPixels(pixels, 0, w, 0, 0, w, h)
    val tmp = IntArray(w * h)
    val div = 2 * radius + 1
    fun a(p: Int) = android.graphics.Color.alpha(p)
    fun r(p: Int) = android.graphics.Color.red(p)
    fun g(p: Int) = android.graphics.Color.green(p)
    fun b(p: Int) = android.graphics.Color.blue(p)
    // 横方向
    for (y in 0 until h) {
        var sa = 0; var sr = 0; var sg = 0; var sb = 0
        val row = y * w
        for (i in -radius..radius) {
            val p = pixels[row + i.coerceIn(0, w - 1)]
            sa += a(p); sr += r(p); sg += g(p); sb += b(p)
        }
        for (x in 0 until w) {
            tmp[row + x] = android.graphics.Color.argb(sa / div, sr / div, sg / div, sb / div)
            val po = pixels[row + (x - radius).coerceIn(0, w - 1)]
            val pi = pixels[row + (x + radius + 1).coerceIn(0, w - 1)]
            sa += a(pi) - a(po); sr += r(pi) - r(po); sg += g(pi) - g(po); sb += b(pi) - b(po)
        }
    }
    // 縦方向
    for (x in 0 until w) {
        var sa = 0; var sr = 0; var sg = 0; var sb = 0
        for (i in -radius..radius) {
            val p = tmp[i.coerceIn(0, h - 1) * w + x]
            sa += a(p); sr += r(p); sg += g(p); sb += b(p)
        }
        for (y in 0 until h) {
            pixels[y * w + x] = android.graphics.Color.argb(sa / div, sr / div, sg / div, sb / div)
            val po = tmp[(y - radius).coerceIn(0, h - 1) * w + x]
            val pi = tmp[(y + radius + 1).coerceIn(0, h - 1) * w + x]
            sa += a(pi) - a(po); sr += r(pi) - r(po); sg += g(pi) - g(po); sb += b(pi) - b(po)
        }
    }
    val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
    out.setPixels(pixels, 0, w, 0, 0, w, h)
    return out
}

/** アートワークからぼかし背景用の小さな静止画を作る */
private fun makeBlurredBackground(source: Bitmap): Bitmap {
    val targetW = 256
    val targetH = maxOf(1, (source.height * (targetW.toFloat() / source.width)).toInt())
    var small = Bitmap.createScaledBitmap(source, targetW, targetH, true)
    small = boxBlurBitmap(small, 26)
    small = boxBlurBitmap(small, 26)
    small = boxBlurBitmap(small, 26)
    return small
}
