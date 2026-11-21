package com.example.nissingeppolauncher.ui.components

import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import com.example.nissingeppolauncher.R
import java.io.File

val LocalLauncherFontFamily = compositionLocalOf<FontFamily> { FontFamily.Default }

val LocalLauncherFontWeight = compositionLocalOf { FontWeight.Light }

fun loadCustomFontFamily(path: String?): FontFamily? {
    if (path.isNullOrBlank()) return null
    return try {
        val file = File(path)
        if (file.exists()) {
            val typeface = android.graphics.Typeface.createFromFile(file)
            FontFamily(typeface)
        } else {
            null
        }
    } catch (_: Exception) {
        null
    }
}

val ReceiptFontFamily = FontFamily(Font(R.font.receipt_font))

val DenkiChipFontFamily = FontFamily(Font(R.font.denkichip_font))

val TsukuhouFontFamily = FontFamily(Font(R.font.tsukuhou_font))

val FreeLicenseFontFamily = FontFamily(Font(R.font.free_license))

val MontserratBoldItalicFontFamily = FontFamily(Font(R.font.montserrat_bold_italic))

val KaibunshoFontFamily = FontFamily(Font(R.font.kaibunsyo_font))

/** ドパガキモード専用フォント: 玉ねぎ楷書「激」。収録されていない文字はシステムフォント（ゴシック）へ自動でフォールバックする */
val DopagakiFontFamily = FontFamily(Font(R.font.dopagaki_font))

/** Zen Kaku Gothic New Medium: 通常のUI・歌詞フォント一覧用 */
val ZenKakuMediumFontFamily = FontFamily(Font(R.font.zenkaku_medium))

/** Zen Kaku Gothic New Black（極太）: ドパガキモードの歌詞用 */
val ZenKakuBlackFontFamily = FontFamily(Font(R.font.zenkaku_black))

fun parseFontFamily(fontChoice: String, customPath: String?): FontFamily {
    return when (fontChoice) {
        "receipt" -> ReceiptFontFamily
        "denkichip" -> DenkiChipFontFamily
        "tsukuhou" -> TsukuhouFontFamily
        "freelicense" -> FreeLicenseFontFamily
        "montserratbi" -> MontserratBoldItalicFontFamily
        "kaibunsho" -> KaibunshoFontFamily
        "zenkaku" -> ZenKakuMediumFontFamily
        "custom" -> loadCustomFontFamily(customPath) ?: FontFamily.Default
        else -> {
            if (!customPath.isNullOrBlank()) {
                loadCustomFontFamily(customPath) ?: FontFamily.Default
            } else {
                FontFamily.Default
            }
        }
    }
}

fun parseFontWeight(index: Int): FontWeight {
    return when (index) {
        0 -> FontWeight.Light
        1 -> FontWeight.Normal
        2 -> FontWeight.Medium
        3 -> FontWeight.Bold
        else -> FontWeight.Light
    }
}
