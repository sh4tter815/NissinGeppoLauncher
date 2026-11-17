package com.example.nissingeppolauncher.ui

import androidx.compose.ui.graphics.Color

// 歌詞PVのデータ定義（単語アイコン対応表・除外リスト・色名・カタカナ英訳・コーラス判定・入場方向）。
// 対応表の中身は LyricEffectsData が持つ。配信データ（このリポジトリ直下の lyric_effects.json）が
// 取得できればそちらが優先され、無ければ内蔵デフォルトが使われる。

object LyricIcon {
    const val UMBRELLA = 0; const val RAIN = 1; const val SNOW = 2; const val STAR = 3
    const val MOON = 4; const val SUN = 5; const val CLOUD = 6; const val FLOWER = 7
    const val WAVES = 8; const val HEART = 9; const val KEY = 10; const val NOTE = 11
    const val ENVELOPE = 12; const val CLOCK = 13; const val FLAME = 14; const val DROP = 15
    const val RAINBOW = 16; const val LIGHTNING = 17; const val SMILE = 18; const val FISH = 19
    const val MOUNTAIN = 20; const val WIND = 21
    const val LINE = 22; const val CIRCLE = 23
    const val GLOBE = 24; const val BUBBLE = 25
    const val COFFIN = 26; const val EYE = 27; const val PLUS = 28
    const val MINUS = 29; const val MAP = 30; const val BONE = 31; const val CROSS = 32; const val BANDAGE = 33
}

/** 歌詞に出てきたらアイコンを出す単語（前にあるものが優先） */
val LYRIC_ICON_WORDS: List<Pair<String, Int>>
    get() = LyricEffectsData.iconWords
val LYRIC_ICON_EXCLUSIONS: Map<String, List<String>>
    get() = LyricEffectsData.iconExclusions

/** 行の中からアイコン対象の単語を探す。除外語の一部として現れる箇所は数えない */
// 英語の単語→アイコン（単語境界＋大文字小文字なし。"rain" が "train" に当たらないように）
val LYRIC_ICON_WORDS_EN: List<Pair<String, Int>>
    get() = LyricEffectsData.iconWordsEn
fun findIconWord(text: String): Pair<String, Int>? {
    for ((word, kind) in LYRIC_ICON_WORDS) {
        // 「心」が単体で出てきたときはハートを出さない
        if (word == "心" && text.trim() == "心") continue
        val exclusions = LYRIC_ICON_EXCLUSIONS[word] ?: emptyList()
        var from = 0
        while (true) {
            val i = text.indexOf(word, from)
            if (i < 0) break
            if (exclusions.any { text.startsWith(it, i) }) {
                from = i + 1
                continue
            }
            // 「何日目」「3回目」のような助数詞の目は、目のアイコンを出さない
            if (word == "目" && i > 0 && text[i - 1] in "0123456789０１２３４５６７８９一二三四五六七八九十百千何日回番度つ個曲点週年月項標的注面") {
                from = i + 1
                continue
            }
            return word to kind
        }
    }
    for ((word, kind) in LYRIC_ICON_WORDS_EN) {
        val m = Regex("""(?i)(?<![A-Za-z])${Regex.escape(word)}(?![A-Za-z])""").find(text)
        if (m != null) return m.value to kind
    }
    return null
}
// コーラス（yeah や la の繰り返し）判定の音節は配信データ（LyricEffectsData.chorusSyllables）が正本。
// 音節リストから正規表現を組み立て、リストが差し替わったときだけ作り直す
private var chorusRegexCache: Pair<List<String>, Regex>? = null
val CHORUS_REGEX: Regex
    get() {
        val syl = LyricEffectsData.chorusSyllables
        val c = chorusRegexCache
        if (c != null && c.first == syl) return c.second
        val r = Regex("^(" + syl.joinToString("|") { Regex.escape(it) } + ")+$")
        chorusRegexCache = syl to r
        return r
    }

fun isChorusWord(text: String): Boolean {
    val t = text.trim().lowercase().trim('!', '?', ',', '.', '~', '〜', '・')
    if (t.isEmpty() || !t.all { it in 'a'..'z' }) return false
    return CHORUS_REGEX.matches(t)
}

/** 色名が出てきたら、その文字をその色で塗るための対応表（長い語を先に判定） */
val COLOR_WORDS: List<Pair<String, Color>>
    get() = LyricEffectsData.colorWords
// 色名ではないけれど色を連想させる言葉 → 連想色で塗る（愛→ピンク、血→赤、夜→藍、月→金）
val ASSOC_COLOR_WORDS: List<Pair<String, Color>>
    get() = LyricEffectsData.assocColorWords
val KATAKANA_EN: Map<String, String>
    get() = LyricEffectsData.katakanaEn

// エフェクト系の対象単語（データ配信側。対象語の追加は lyric_effects.json の更新だけで済む）
val DRIP_WORDS: List<Pair<String, Color>>
    get() = LyricEffectsData.dripWords
val DRIP_EXCLUSIONS: Map<String, List<String>>
    get() = LyricEffectsData.dripExclusions
val DREAM_WORDS: List<String>
    get() = LyricEffectsData.dreamWords
val WAVE_WORDS: List<String>
    get() = LyricEffectsData.waveWords
val SAND_WORDS: List<String>
    get() = LyricEffectsData.sandWords
val WIND_WORDS: List<String>
    get() = LyricEffectsData.windWords
val FALL_WORDS: List<String>
    get() = LyricEffectsData.fallWords
val RIPPLE_WORDS: List<String>
    get() = LyricEffectsData.rippleWords
val MOSAIC_WORDS: List<String>
    get() = LyricEffectsData.mosaicWords
val PILE_WORDS: List<String>
    get() = LyricEffectsData.pileWords
val PARTS_WORDS: List<String>
    get() = LyricEffectsData.partsWords
val NEG_WORDS: List<String>
    get() = LyricEffectsData.negWords
val GHOST_WORDS: List<String>
    get() = LyricEffectsData.ghostWords
val SPARKLE_WORDS: List<String>
    get() = LyricEffectsData.sparkleWords
val DARK_WORDS: List<String>
    get() = LyricEffectsData.darkWords
val STRETCH_VERTICAL_WORDS: List<String>
    get() = LyricEffectsData.stretchVerticalWords
val STRETCH_HORIZONTAL_WORDS: List<String>
    get() = LyricEffectsData.stretchHorizontalWords
val AMBIENT_WORDS: List<Pair<String, Int>>
    get() = LyricEffectsData.ambientWords

val PV_ENTRY_DIRS = listOf(
    0f to -1f, 0.72f to -0.72f, 1f to 0f, 0.72f to 0.72f,
    0f to 1f, -0.72f to 0.72f, -1f to 0f, -0.72f to -0.72f
)
