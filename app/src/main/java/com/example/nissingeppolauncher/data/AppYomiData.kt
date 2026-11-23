package com.example.nissingeppolauncher.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * 主要アプリの読み仮名辞書（app_yomi.json）。
 * 設定ツリーや歌詞エフェクトと同じ配信方式: リポジトリ直下のJSONを起動時に取得し、
 * 端末キャッシュ→同梱アセットの順に読む。手動登録（yomi_map_json）が常に優先される。
 */
object AppYomiData {
    private const val DATA_URL =
        "https://raw.githubusercontent.com/sh4tter815/NissinGeppoLauncher/master/app_yomi.json"
    private const val CACHE_FILE = "app_yomi_cache.json"
    private const val ASSET_FILE = "app_yomi.json"

    @Volatile
    var appliedVersion: Int = 0
        private set

    @Volatile
    private var cachedMap: Map<String, String>? = null

    /** キャッシュ→同梱アセットの順に読み、検証を通った辞書を返す */
    fun load(context: Context): Map<String, String> {
        cachedMap?.let { return it }
        val cacheFile = File(context.filesDir, CACHE_FILE)
        if (cacheFile.exists()) {
            try {
                parse(cacheFile.readText())?.let { (v, map) ->
                    appliedVersion = v
                    cachedMap = map
                    return map
                }
            } catch (_: Exception) {
            }
        }
        try {
            val text = context.assets.open(ASSET_FILE).bufferedReader().use { it.readText() }
            parse(text)?.let { (v, map) ->
                appliedVersion = v
                cachedMap = map
                return map
            }
        } catch (_: Exception) {
        }
        return emptyMap()
    }

    /** リモートの最新を取りに行き、今より新しい正当な辞書ならキャッシュする。更新できたら true */
    suspend fun refresh(context: Context): Boolean = withContext(Dispatchers.IO) {
        try {
            val conn = URL(DATA_URL).openConnection() as HttpURLConnection
            conn.connectTimeout = 8000
            conn.readTimeout = 8000
            conn.setRequestProperty("User-Agent", "NissinGeppoLauncher")
            val text = conn.inputStream.bufferedReader().use { it.readText() }
            conn.disconnect()
            val (v, map) = parse(text) ?: return@withContext false
            if (v > appliedVersion) {
                File(context.filesDir, CACHE_FILE).writeText(text)
                appliedVersion = v
                cachedMap = map
                true
            } else {
                false
            }
        } catch (_: Exception) {
            false
        }
    }

    /** 検証込みパース。形式が崩れていたら null */
    fun parse(text: String): Pair<Int, Map<String, String>>? {
        return try {
            val root = JSONObject(text)
            val version = root.getInt("version")
            val readings = root.getJSONObject("readings")
            if (readings.length() > 5000) return null
            val map = mutableMapOf<String, String>()
            val keys = readings.keys()
            while (keys.hasNext()) {
                val pkg = keys.next()
                val yomi = readings.getString(pkg)
                if (pkg.isBlank() || yomi.isBlank() || yomi.length > 40) return null
                map[pkg] = yomi
            }
            Pair(version, map)
        } catch (_: Exception) {
            null
        }
    }
}
