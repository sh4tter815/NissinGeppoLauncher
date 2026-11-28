package com.example.nissingeppolauncher.data

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.os.Build
import android.util.LruCache
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.drawable.toBitmap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream

data class AppInfo(
    val label: String,
    val packageName: String,
    val icon: Drawable? = null,
    val iconBitmap: ImageBitmap? = null,
    val initialJapanese: String = "＃", // 「あ」「か」「さ」など
    val initialEnglish: String = "＃",   // "A", "B", "C" など
    val customYomi: String = "",
    val customYomiKana: String = "",
    val discGroup: String = "＃", // 円盤の索引グループ（通常は initialJapanese、スペル順モードの英語名アプリは頭文字）
    val discKana: String = "＃", // 索引の文字単位グループ（あいうえおモード用。濁点・小文字は基本文字に寄せる）
    val searchAliases: List<String> = emptyList(), // 検索可能な別名（複数保存可能）
    val isFavorite: Boolean = false,
    val isHidden: Boolean = false,
    val iconIsMono: Boolean = false // テーマ（単色）アイコンとして読み込まれた印
)

class AppRepository(private val context: Context) {
    val prefManager = AppPreferenceManager(context)

    companion object {
        private var cachedApps: List<AppInfo>? = null
        private var cachedRecords: List<AppRecord>? = null
        private var needsDiffCheck: Boolean = true
        private val iconCache = LruCache<String, Bitmap>(200)

        /** 一覧の再構築と差分チェックを要求する（パッケージ変更の検知時など） */
        fun invalidateCache() {
            cachedApps = null
            needsDiffCheck = true
        }
    }

    /** 永続化できるアプリ情報（アイコン画像は含まない） */
    private data class AppRecord(
        val packageName: String,
        val label: String,
        val lastUpdateTime: Long
    )

    suspend fun getInstalledApps(forceRefresh: Boolean = false): List<AppInfo> = withContext(Dispatchers.IO) {
        if (!forceRefresh && cachedApps != null) {
            return@withContext cachedApps!!
        }

        var records = cachedRecords
        if (records == null) {
            val diskRecords = loadRecordsFromDisk()
            if (diskRecords != null) {
                // 保存済み一覧を読み、差分だけ最新化する
                records = applyDiff(diskRecords) ?: diskRecords
            } else {
                // 初回（保存データなし）はフルスキャン
                records = fullScan()
            }
            cachedRecords = records
            needsDiffCheck = false
        } else if (forceRefresh || needsDiffCheck) {
            // 設定の手動更新、またはパッケージ変更を検知したとき: 差分更新のみ行う
            val updated = applyDiff(records)
            if (updated != null) {
                records = updated
                cachedRecords = updated
            }
            needsDiffCheck = false
        }

        val result = buildAppInfoList(records)
        cachedApps = result
        result
    }

    // ---------------- スキャン / 差分更新 ----------------

    /** ランチャー対象アプリのパッケージ名 → 最終更新日時の軽量マップ */
    private fun queryLaunchablePackages(): Map<String, Long> {
        val pm = context.packageManager
        val mainIntent = Intent(Intent.ACTION_MAIN, null).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
        }
        val resolveInfos = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            pm.queryIntentActivities(mainIntent, PackageManager.ResolveInfoFlags.of(0))
        } else {
            @Suppress("DEPRECATION")
            pm.queryIntentActivities(mainIntent, 0)
        }
        val launchableNames = resolveInfos.mapNotNull { it.activityInfo?.packageName }.toSet()

        val times = mutableMapOf<String, Long>()
        try {
            val packages = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                pm.getInstalledPackages(PackageManager.PackageInfoFlags.of(0))
            } else {
                @Suppress("DEPRECATION")
                pm.getInstalledPackages(0)
            }
            packages.forEach { info ->
                if (info.packageName in launchableNames) {
                    times[info.packageName] = info.lastUpdateTime
                }
            }
        } catch (_: Exception) {}
        // 時刻が取れなかったものは 0 として含める
        launchableNames.forEach { name -> if (name !in times) times[name] = 0L }
        return times
    }

    private fun fullScan(): List<AppRecord> {
        val times = queryLaunchablePackages()
        val records = times.keys.mapNotNull { packageName ->
            fetchRecord(packageName, times[packageName] ?: 0L, saveIcon = true)
        }
        saveRecordsToDisk(records)
        return records
    }

    /**
     * パッケージ名と最終更新日時だけを比較して差分を検出し、
     * 追加・更新されたアプリだけラベルとアイコンを取り直す。変化がなければ null。
     */
    private fun applyDiff(old: List<AppRecord>): List<AppRecord>? {
        val current = try {
            queryLaunchablePackages()
        } catch (_: Exception) {
            return null
        }
        val oldByName = old.associateBy { it.packageName }

        val removedNames = oldByName.keys - current.keys
        val changedNames = current.entries
            .filter { (name, time) -> oldByName[name]?.lastUpdateTime != time }
            .map { it.key }

        if (removedNames.isEmpty() && changedNames.isEmpty()) return null

        val fetched = changedNames.mapNotNull { packageName ->
            fetchRecord(packageName, current[packageName] ?: 0L, saveIcon = true)
        }
        val changedSet = changedNames.toSet()
        val merged = old.filter { it.packageName !in removedNames && it.packageName !in changedSet } + fetched

        // 使われなくなったアイコンファイルを掃除する
        val validNames = merged.map { it.packageName }.toSet()
        try {
            iconDir().listFiles()?.forEach { file ->
                if (file.nameWithoutExtension !in validNames) file.delete()
            }
        } catch (_: Exception) {}

        saveRecordsToDisk(merged)
        return merged
    }

    /** 1アプリ分のラベルとアイコンを取得する（アイコンはアプリ内に保存） */
    private fun fetchRecord(packageName: String, lastUpdateTime: Long, saveIcon: Boolean): AppRecord? {
        val pm = context.packageManager
        return try {
            val appInfo = pm.getApplicationInfo(packageName, 0)
            val label = pm.getApplicationLabel(appInfo).toString().trim()
            if (label.isBlank()) return null
            if (saveIcon) {
                val bitmap = loadIconBitmapFromPackage(packageName)
                if (bitmap != null) {
                    saveIconToDisk(packageName, bitmap)
                    iconCache.put(packageName, bitmap)
                }
            }
            AppRecord(packageName, label, lastUpdateTime)
        } catch (_: Exception) {
            null
        }
    }

    private val iconRefreshScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** アイコン再生成を設定画面の寿命に左右されない専用スコープで実行し、完了後に Main で onDone を呼ぶ。
     *  設定画面のコルーチンで回すと、ページを離れた瞬間にキャンセルされて更新が途中で止まるため */
    fun refreshIconsThen(onDone: () -> Unit) {
        iconRefreshScope.launch {
            try { refreshAllIcons() } catch (_: Exception) {}
            withContext(Dispatchers.Main) { onDone() }
        }
    }

    /** アイコンパック変更時などに、全アプリのアイコンを取り直して保存し直す */
    suspend fun refreshAllIcons() = withContext(Dispatchers.IO) {
        val records = cachedRecords ?: loadRecordsFromDisk() ?: fullScan()
        for (rec in records) {
            val bitmap = loadIconBitmapFromPackage(rec.packageName)
            if (bitmap != null) {
                saveIconToDisk(rec.packageName, bitmap)
                iconCache.put(rec.packageName, bitmap)
            }
        }
        cachedApps = null
    }

    // ---- ユーザーが画像を選んで差し替えたアイコン（最優先で使う） ----
    private fun customIconFile(packageName: String): File =
        File(File(context.filesDir, "custom_icons").apply { mkdirs() }, "$packageName.png")

    fun hasCustomIcon(packageName: String): Boolean = customIconFile(packageName).exists()

    suspend fun setCustomIcon(packageName: String, bitmap: Bitmap) = withContext(Dispatchers.IO) {
        val scaled = if (bitmap.width != 192 || bitmap.height != 192) {
            Bitmap.createScaledBitmap(bitmap, 192, 192, true)
        } else bitmap
        try {
            customIconFile(packageName).outputStream().use { scaled.compress(Bitmap.CompressFormat.PNG, 100, it) }
        } catch (_: Exception) {}
        saveIconToDisk(packageName, scaled)
        iconCache.put(packageName, scaled)
        cachedApps = null
    }

    suspend fun clearCustomIcon(packageName: String) = withContext(Dispatchers.IO) {
        try { customIconFile(packageName).delete() } catch (_: Exception) {}
        val bitmap = loadIconBitmapFromPackage(packageName)
        if (bitmap != null) {
            saveIconToDisk(packageName, bitmap)
            iconCache.put(packageName, bitmap)
        }
        cachedApps = null
    }

    private fun monoMarkerFile(packageName: String): File = File(iconDir(), "$packageName.mono")

    /** テーマ（単色）用: アダプティブアイコンの単色レイヤーを白いグリフとして描く（Android 13+） */
    private fun loadMonochromeIcon(packageName: String): Bitmap? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return null
        return try {
            val drawable = context.packageManager.getApplicationIcon(packageName)
            if (drawable !is android.graphics.drawable.AdaptiveIconDrawable) return null
            val mono = drawable.monochrome ?: return null
            val size = 192
            val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
            val canvas = android.graphics.Canvas(bmp)
            mono.setTint(android.graphics.Color.WHITE)
            // 前景レイヤーは表示領域より一回り大きく設計されているため、広げて描いて切り抜き範囲を通常アイコンに揃える
            val over = size / 4
            mono.setBounds(-over, -over, size + over, size + over)
            mono.draw(canvas)
            bmp
        } catch (_: Exception) {
            null
        }
    }

    /** 単色レイヤーが無いアプリ用: 通常アイコンから背景色を取り除いて、柄だけの単色アイコンを作る。
     *  透過が主体のアイコンはアルファを柄にし、ベタ塗りに近いアイコンは形の内側の色を背景として距離で柄を抜く。
     *  柄がほとんど無い／全面が柄になる場合は失敗として null（通常アイコンのまま） */
    private fun loadForcedMonochromeIcon(packageName: String): Bitmap? {
        return try {
            val drawable = context.packageManager.getApplicationIcon(packageName)
            val raw = drawable.toBitmap()
            val size = 192
            val src = if (raw.width != size || raw.height != size) Bitmap.createScaledBitmap(raw, size, size, true) else raw
            val px = IntArray(size * size)
            src.getPixels(px, 0, size, 0, 0, size, size)
            // 不透明な画素の割合で分岐する。角が透明でも中身がベタ塗りのアイコン（角丸の定型アイコン等）を
            // 透過アイコンと誤判定すると、形そのものが柄になって全面ベタ塗りになってしまうため
            var opaqueCount = 0
            for (p in px) if (android.graphics.Color.alpha(p) > 40) opaqueCount++
            val coverage = opaqueCount.toDouble() / px.size
            if (coverage < 0.65) {
                // 透過が主体のアイコン: アルファそのものを柄として白で塗る
                for (i in px.indices) {
                    val a = android.graphics.Color.alpha(px[i])
                    px[i] = android.graphics.Color.argb(a, 255, 255, 255)
                }
            } else {
                // ベタ塗りに近いアイコン: 形の内側（四辺から10%内側）の色を背景として柄を抜く
                val inset = size / 10
                val samples = mutableListOf<Int>()
                for (f in listOf(0.25f, 0.5f, 0.75f)) {
                    val p = (size * f).toInt()
                    listOf(
                        src.getPixel(p, inset), src.getPixel(p, size - 1 - inset),
                        src.getPixel(inset, p), src.getPixel(size - 1 - inset, p)
                    ).forEach { c -> if (android.graphics.Color.alpha(c) > 40) samples.add(c) }
                }
                if (samples.isEmpty()) return null
                val br = samples.map { android.graphics.Color.red(it) }.average()
                val bg = samples.map { android.graphics.Color.green(it) }.average()
                val bb = samples.map { android.graphics.Color.blue(it) }.average()
                var glyphCount = 0
                for (i in px.indices) {
                    val p = px[i]
                    val a0 = android.graphics.Color.alpha(p)
                    val dr = android.graphics.Color.red(p) - br
                    val dg = android.graphics.Color.green(p) - bg
                    val db = android.graphics.Color.blue(p) - bb
                    val dist = kotlin.math.sqrt(dr * dr + dg * dg + db * db)
                    val t = ((dist - 24.0) / 72.0).coerceIn(0.0, 1.0)
                    val a = (t * 255.0 * (a0 / 255.0)).toInt()
                    if (a > 8) glyphCount++
                    px[i] = android.graphics.Color.argb(a, 255, 255, 255)
                }
                // 柄がほとんど無い（単色ベタ等）／全面が柄（背景推定の失敗）なら諦める
                if (glyphCount < size * size * 0.02 || glyphCount > size * size * 0.92) return null
            }
            val out = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
            out.setPixels(px, 0, size, 0, 0, size, size)
            out
        } catch (_: Exception) {
            null
        }
    }

    private fun loadIconBitmapFromPackage(packageName: String): Bitmap? {
        // 単色アイコンの印は読み直すたびに付け直す
        try { monoMarkerFile(packageName).delete() } catch (_: Exception) {}
        val customFile = customIconFile(packageName)
        if (customFile.exists()) {
            try {
                android.graphics.BitmapFactory.decodeFile(customFile.absolutePath)?.let { return it }
            } catch (_: Exception) {}
        }
        if (prefManager.isThemedIconsEnabled()) {
            loadMonochromeIcon(packageName)?.let { bmp ->
                try { monoMarkerFile(packageName).createNewFile() } catch (_: Exception) {}
                return bmp
            }
            if (prefManager.isForcedMonoEnabled()) {
                loadForcedMonochromeIcon(packageName)?.let { bmp ->
                    try { monoMarkerFile(packageName).createNewFile() } catch (_: Exception) {}
                    return bmp
                }
            }
        }
        val packPkg = prefManager.getIconPackPackage()
        if (packPkg != null) {
            IconPackManager.iconForPackage(context, packPkg, packageName)?.let { return it }
        }
        return try {
            val drawable = context.packageManager.getApplicationIcon(packageName)
            val bitmap = drawable.toBitmap()
            if (bitmap.width > 192 || bitmap.height > 192) {
                Bitmap.createScaledBitmap(bitmap, 192, 192, true)
            } else {
                bitmap
            }
        } catch (_: Exception) {
            null
        }
    }

    // ---------------- AppInfo 構築 ----------------

    private fun buildAppInfoList(records: List<AppRecord>): List<AppInfo> {
        val favoritesSet = prefManager.getFavoritePackages()
        val hiddenSet = prefManager.getHiddenPackages()
        // 読みは2枠: 漢字の読み（基本優先）とカタカナ読み。辞書は英語名アプリの分がカタカナ読み、
        // 日本語名アプリの分が漢字の読みに入り、手動登録がそれぞれ上書きする
        val dictYomiMap = AppYomiData.load(context)
        val manualYomiMap = prefManager.getYomiMap()
        val manualKanaMap = prefManager.getYomiKanaMap()
        val spellMode = prefManager.getEnglishAppSort() == "spell"
        val aliasesMap = prefManager.getAliasesMap()

        val apps = records.map { rec ->
            val isHidden = hiddenSet.contains(rec.packageName)
            val isFavorite = favoritesSet.contains(rec.packageName)
            val searchAliases = aliasesMap[rec.packageName] ?: emptyList()
            val isLatinLabel = rec.label.firstOrNull()?.let { it.code < 128 && it.isLetter() } == true
            val dictYomi = dictYomiMap[rec.packageName] ?: ""
            val customYomi = manualYomiMap[rec.packageName] ?: if (isLatinLabel) "" else dictYomi
            val customYomiKana = manualKanaMap[rec.packageName] ?: if (isLatinLabel) dictYomi else ""
            // 実効読み: 漢字の読みが基本優先。カタカナ読みはスペル順モードでは無効
            val effectiveYomi = when {
                customYomi.isNotBlank() -> customYomi
                spellMode -> ""
                customYomiKana.isNotBlank() -> customYomiKana
                else -> ""
            }
            val targetForInitials = if (effectiveYomi.isNotBlank()) effectiveYomi else rec.label

            // 起動時はアプリ内保存済みアイコンだけを使う（PackageManagerから読み直さない）
            val iconBitmapRaw = loadIconBitmap(rec.packageName, allowPackageLoad = false)
            val iconDrawable = iconBitmapRaw?.let { BitmapDrawable(context.resources, it) }
            val (japaneseInitial, englishInitial) = extractInitials(targetForInitials)
            val japaneseKana = getJapaneseKana(targetForInitials.firstOrNull() ?: '＃')
            val discGroup = if (isLatinLabel && spellMode && effectiveYomi.isBlank()) englishInitial else japaneseInitial

            AppInfo(
                label = rec.label,
                packageName = rec.packageName,
                icon = iconDrawable,
                iconBitmap = iconBitmapRaw?.asImageBitmap(),
                initialJapanese = japaneseInitial,
                initialEnglish = englishInitial,
                customYomi = customYomi,
                customYomiKana = customYomiKana,
                discGroup = discGroup,
                discKana = japaneseKana,
                searchAliases = searchAliases,
                isFavorite = isFavorite,
                isHidden = isHidden,
                iconIsMono = iconBitmapRaw != null && prefManager.isThemedIconsEnabled() && monoMarkerFile(rec.packageName).exists()
            )
        }

        return apps.sortedBy { app ->
            val rawKey = when {
                app.customYomi.isNotBlank() -> app.customYomi
                !spellMode && app.customYomiKana.isNotBlank() -> app.customYomiKana
                else -> app.label
            }
            toHiragana(rawKey.lowercase())
        }
    }

    // ---------------- 永続化（アプリ情報） ----------------

    private fun cacheFile(): File = File(context.filesDir, "apps_cache.json")

    private fun saveRecordsToDisk(records: List<AppRecord>) {
        try {
            val arr = JSONArray()
            records.forEach { r ->
                arr.put(JSONObject().apply {
                    put("package", r.packageName)
                    put("label", r.label)
                    put("updated", r.lastUpdateTime)
                })
            }
            val root = JSONObject().apply {
                put("version", 1)
                put("apps", arr)
            }
            cacheFile().writeText(root.toString())
        } catch (_: Exception) {}
    }

    private fun loadRecordsFromDisk(): List<AppRecord>? {
        return try {
            val file = cacheFile()
            if (!file.exists()) return null
            val root = JSONObject(file.readText())
            val arr = root.getJSONArray("apps")
            val list = mutableListOf<AppRecord>()
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                list.add(
                    AppRecord(
                        packageName = o.getString("package"),
                        label = o.optString("label"),
                        lastUpdateTime = o.optLong("updated")
                    )
                )
            }
            list
        } catch (_: Exception) {
            null
        }
    }

    // ---------------- アイコン（アプリ内保存） ----------------

    private fun iconDir(): File = File(context.filesDir, "app_icons").apply { mkdirs() }

    private fun iconFile(packageName: String): File = File(iconDir(), "$packageName.png")

    private fun saveIconToDisk(packageName: String, bitmap: Bitmap) {
        try {
            FileOutputStream(iconFile(packageName)).use { out ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
            }
        } catch (_: Exception) {}
    }

    /**
     * アイコン取得の順序: メモリ → アプリ内保存ファイル → （許可時のみ）PackageManagerから取得して保存。
     * allowPackageLoad=false のときは保存済みファイルのみを使い、起動を高速に保つ。
     */
    private fun loadIconBitmap(packageName: String, allowPackageLoad: Boolean): Bitmap? {
        iconCache.get(packageName)?.let { return it }

        val file = iconFile(packageName)
        if (file.exists()) {
            val fromDisk = try { BitmapFactory.decodeFile(file.absolutePath) } catch (_: Exception) { null }
            if (fromDisk != null) {
                iconCache.put(packageName, fromDisk)
                return fromDisk
            }
        }

        if (!allowPackageLoad) return null

        val bitmap = loadIconBitmapFromPackage(packageName) ?: return null
        saveIconToDisk(packageName, bitmap)
        iconCache.put(packageName, bitmap)
        return bitmap
    }

    // ---------------- お気に入り・非表示・読み仮名など ----------------

    fun setFavorite(packageName: String, isFavorite: Boolean) {
        prefManager.setFavoritePackage(packageName, isFavorite)
        invalidateCache()
    }

    fun setHidden(packageName: String, isHidden: Boolean) {
        prefManager.setHiddenPackage(packageName, isHidden)
        invalidateCache()
    }

    fun setYomi(packageName: String, yomi: String) {
        prefManager.setYomi(packageName, yomi)
        invalidateCache()
    }

    fun setYomiKana(packageName: String, yomi: String) {
        prefManager.setYomiKana(packageName, yomi)
        invalidateCache()
    }

    fun addSearchAlias(packageName: String, alias: String) {
        prefManager.addAlias(packageName, alias)
        invalidateCache()
    }

    fun removeSearchAlias(packageName: String, alias: String) {
        prefManager.removeAlias(packageName, alias)
        invalidateCache()
    }

    private fun extractInitials(label: String): Pair<String, String> {
        val firstChar = label.firstOrNull() ?: return Pair("＃", "＃")

        val englishInitial = if (firstChar.isLetter() && firstChar.code < 128) {
            firstChar.uppercaseChar().toString()
        } else {
            "＃"
        }

        val japaneseInitial = getJapaneseGroup(firstChar)

        return Pair(japaneseInitial, englishInitial)
    }

    private fun getJapaneseGroup(ch: Char): String {
        val code = ch.code
        val hiraChar = when (code) {
            in 0x30A1..0x30FA -> (code - 0x60).toChar()
            else -> ch
        }

        return when (hiraChar) {
            'あ', 'い', 'う', 'え', 'お', 'ぁ', 'ぃ', 'ぅ', 'ぇ', 'ぉ' -> "あ"
            'か', 'き', 'く', 'け', 'こ', 'が', 'ぎ', 'ぐ', 'げ', 'ご' -> "か"
            'さ', 'し', 'す', 'せ', 'そ', 'ざ', 'じ', 'ず', 'ぜ', 'ぞ' -> "さ"
            'た', 'ち', 'つ', 'て', 'と', 'だ', 'ぢ', 'づ', 'で', 'ど', 'っ' -> "た"
            'な', 'に', 'ぬ', 'ね', 'の' -> "な"
            'は', 'ひ', 'ふ', 'へ', 'ほ', 'ば', 'び', 'ぶ', 'べ', 'ぼ', 'ぱ', 'ぴ', 'ぷ', 'ぺ', 'ぽ' -> "は"
            'ま', 'み', 'む', 'め', 'も' -> "ま"
            'や', 'ゆ', 'よ', 'ゃ', 'ゅ', 'ょ' -> "や"
            'ら', 'り', 'る', 'れ', 'ろ' -> "ら"
            'わ', 'を', 'ん' -> "わ"
            else -> {
                if (ch.isLetter() && ch.code < 128) ch.uppercaseChar().toString() else "＃"
            }
        }
    }

    // 索引の文字単位グループ（あいうえおモード用）: 濁点・半濁点・小文字を基本文字に寄せた1文字を返す
    private fun getJapaneseKana(ch: Char): String {
        val code = ch.code
        val hira = if (code in 0x30A1..0x30FA) (code - 0x60).toChar() else ch
        return when (hira) {
            'ぁ' -> "あ"
            'ぃ' -> "い"
            'ぅ' -> "う"
            'ぇ' -> "え"
            'ぉ' -> "お"
            'が' -> "か"
            'ぎ' -> "き"
            'ぐ' -> "く"
            'げ' -> "け"
            'ご' -> "こ"
            'ざ' -> "さ"
            'じ' -> "し"
            'ず' -> "す"
            'ぜ' -> "せ"
            'ぞ' -> "そ"
            'だ' -> "た"
            'ぢ' -> "ち"
            'づ' -> "つ"
            'で' -> "て"
            'ど' -> "と"
            'ば', 'ぱ' -> "は"
            'び', 'ぴ' -> "ひ"
            'ぶ', 'ぷ' -> "ふ"
            'べ', 'ぺ' -> "へ"
            'ぼ', 'ぽ' -> "ほ"
            'っ' -> "つ"
            'ゃ' -> "や"
            'ゅ' -> "ゆ"
            'ょ' -> "よ"
            'ゎ' -> "わ"
            'ゐ' -> "い"
            'ゑ' -> "え"
            in 'あ'..'ん' -> hira.toString()
            else -> "＃"
        }
    }

    private fun toHiragana(text: String): String {
        return text.map { ch ->
            val code = ch.code
            if (code in 0x30A1..0x30FA) (code - 0x60).toChar() else ch
        }.joinToString("")
    }
}
