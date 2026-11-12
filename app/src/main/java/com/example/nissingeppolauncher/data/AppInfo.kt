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
import kotlinx.coroutines.Dispatchers
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
    val searchAliases: List<String> = emptyList(), // 検索可能な別名（複数保存可能）
    val isFavorite: Boolean = false,
    val isHidden: Boolean = false
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

    private fun loadIconBitmapFromPackage(packageName: String): Bitmap? {
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
        val yomiMap = prefManager.getYomiMap()
        val aliasesMap = prefManager.getAliasesMap()

        val apps = records.map { rec ->
            val isHidden = hiddenSet.contains(rec.packageName)
            val isFavorite = favoritesSet.contains(rec.packageName)
            val customYomi = yomiMap[rec.packageName] ?: ""
            val searchAliases = aliasesMap[rec.packageName] ?: emptyList()
            val targetForInitials = if (customYomi.isNotBlank()) customYomi else rec.label

            // 起動時はアプリ内保存済みアイコンだけを使う（PackageManagerから読み直さない）
            val iconBitmapRaw = loadIconBitmap(rec.packageName, allowPackageLoad = false)
            val iconDrawable = iconBitmapRaw?.let { BitmapDrawable(context.resources, it) }
            val (japaneseInitial, englishInitial) = extractInitials(targetForInitials)

            AppInfo(
                label = rec.label,
                packageName = rec.packageName,
                icon = iconDrawable,
                iconBitmap = iconBitmapRaw?.asImageBitmap(),
                initialJapanese = japaneseInitial,
                initialEnglish = englishInitial,
                customYomi = customYomi,
                searchAliases = searchAliases,
                isFavorite = isFavorite,
                isHidden = isHidden
            )
        }

        return apps.sortedBy { app ->
            val rawKey = if (app.customYomi.isNotBlank()) app.customYomi else app.label
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

    private fun toHiragana(text: String): String {
        return text.map { ch ->
            val code = ch.code
            if (code in 0x30A1..0x30FA) (code - 0x60).toChar() else ch
        }.joinToString("")
    }
}
