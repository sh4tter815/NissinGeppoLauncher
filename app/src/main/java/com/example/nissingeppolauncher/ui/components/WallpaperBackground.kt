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

/**
 * 小さいビットマップ専用の分離型ボックスブラー。
 * 横→縦の順にスライディングウィンドウで平均化し、iterations 回繰り返すことで
 * ガウスぼかしに近い滑らかな結果になる。端はクランプ（端ピクセルの複製）で処理する。
 */
private fun boxBlurBitmap(source: Bitmap, radius: Int, iterations: Int): Bitmap {
    val w = source.width
    val h = source.height
    if (radius <= 0 || w == 0 || h == 0) return source
    var src = IntArray(w * h)
    source.getPixels(src, 0, w, 0, 0, w, h)
    var dst = IntArray(w * h)
    val count = radius * 2 + 1

    repeat(iterations) {
        // 横方向
        for (y in 0 until h) {
            val row = y * w
            var accA = 0
            var accR = 0
            var accG = 0
            var accB = 0
            for (i in -radius..radius) {
                val p = src[row + i.coerceIn(0, w - 1)]
                accA += (p ushr 24) and 0xFF
                accR += (p ushr 16) and 0xFF
                accG += (p ushr 8) and 0xFF
                accB += p and 0xFF
            }
            for (x in 0 until w) {
                dst[row + x] = ((accA / count) shl 24) or ((accR / count) shl 16) or
                    ((accG / count) shl 8) or (accB / count)
                val leave = src[row + (x - radius).coerceIn(0, w - 1)]
                val enter = src[row + (x + radius + 1).coerceIn(0, w - 1)]
                accA += ((enter ushr 24) and 0xFF) - ((leave ushr 24) and 0xFF)
                accR += ((enter ushr 16) and 0xFF) - ((leave ushr 16) and 0xFF)
                accG += ((enter ushr 8) and 0xFF) - ((leave ushr 8) and 0xFF)
                accB += (enter and 0xFF) - (leave and 0xFF)
            }
        }
        // 縦方向（dst → src）
        for (x in 0 until w) {
            var accA = 0
            var accR = 0
            var accG = 0
            var accB = 0
            for (i in -radius..radius) {
                val p = dst[i.coerceIn(0, h - 1) * w + x]
                accA += (p ushr 24) and 0xFF
                accR += (p ushr 16) and 0xFF
                accG += (p ushr 8) and 0xFF
                accB += p and 0xFF
            }
            for (y in 0 until h) {
                src[y * w + x] = ((accA / count) shl 24) or ((accR / count) shl 16) or
                    ((accG / count) shl 8) or (accB / count)
                val leave = dst[(y - radius).coerceIn(0, h - 1) * w + x]
                val enter = dst[(y + radius + 1).coerceIn(0, h - 1) * w + x]
                accA += ((enter ushr 24) and 0xFF) - ((leave ushr 24) and 0xFF)
                accR += ((enter ushr 16) and 0xFF) - ((leave ushr 16) and 0xFF)
                accG += ((enter ushr 8) and 0xFF) - ((leave ushr 8) and 0xFF)
                accB += (enter and 0xFF) - (leave and 0xFF)
            }
        }
    }

    val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
    out.setPixels(src, 0, w, 0, 0, w, h)
    return out
}

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

    // ぼかし背景用の静止画を曲ごとに1回だけ作る。
    // 全画面レイヤーへの blur はアニメーションのたびに再計算されて重いので、
    // 小さい画像のうちに本物のぼかし（ボックスブラー3回）をかけてから拡大して通常描画する。
    // 縮小→拡大だけだとぼかしが弱く粗さが見えるが、この方式なら強く滑らかで、計算は曲ごとに1回だけ。
    val blurredArtImage = remember(currentAlbumArt) {
        val art = currentAlbumArt ?: return@remember null
        try {
            val scale = (120f / maxOf(art.width, art.height).coerceAtLeast(1)).coerceAtMost(1f)
            val sw = (art.width * scale).toInt().coerceAtLeast(1)
            val sh = (art.height * scale).toInt().coerceAtLeast(1)
            val small = Bitmap.createScaledBitmap(art, sw, sh, true)
            val blurred = boxBlurBitmap(small, radius = 18, iterations = 3)
            Bitmap.createScaledBitmap(blurred, sw * 4, sh * 4, true).asImageBitmap()
        } catch (_: Exception) {
            null
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        if (currentAlbumArt != null) {
            // アートワーク連動: 再生中のアートワークをぼかして背景にし、抽出色で染める
            Image(
                bitmap = blurredArtImage ?: currentAlbumArt.asImageBitmap(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background((dynamicBackground ?: Color(0xFF0B101D)).copy(alpha = 0.55f))
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
