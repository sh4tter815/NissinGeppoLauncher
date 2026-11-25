package com.example.nissingeppolauncher.data

import android.content.Context
import android.content.Intent
import android.content.res.XmlResourceParser
import android.graphics.Bitmap
import android.graphics.Canvas
import org.xmlpull.v1.XmlPullParser

/**
 * アイコンパック（Nova互換の appfilter.xml 方式）対応。
 * パックの対応表を読んで、アプリごとの差し替えアイコンを返す。
 */
object IconPackManager {

    data class PackInfo(val packageName: String, val label: String)

    fun listPacks(context: Context): List<PackInfo> {
        val pm = context.packageManager
        val found = linkedMapOf<String, PackInfo>()
        val intents = listOf(
            Intent("org.adw.launcher.THEMES"),
            Intent(Intent.ACTION_MAIN).addCategory("com.does.launcher.THEME"),
            Intent("com.gau.go.launcherex.theme")
        )
        for (intent in intents) {
            try {
                for (ri in pm.queryIntentActivities(intent, 0)) {
                    val pkg = ri.activityInfo.packageName
                    if (!found.containsKey(pkg)) {
                        val label = try {
                            pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString()
                        } catch (e: Exception) {
                            pkg
                        }
                        found[pkg] = PackInfo(pkg, label)
                    }
                }
            } catch (e: Exception) {
                // 問い合わせに失敗しても他の方式で続ける
            }
        }
        return found.values.sortedBy { it.label.lowercase() }
    }

    private var cachedPack: String? = null
    private var cachedMap: Map<String, String> = emptyMap()

    /** パックにそのアプリのアイコンがあれば Bitmap（192px）で返す。なければ null */
    fun iconForPackage(context: Context, packPkg: String, appPackage: String): Bitmap? {
        val map = loadMap(context, packPkg)
        if (map.isEmpty()) return null
        val pm = context.packageManager
        val component = try {
            pm.getLaunchIntentForPackage(appPackage)?.component
        } catch (e: Exception) {
            null
        }
        val drawableName = (component?.let { map["${it.packageName}/${it.className}"] })
            ?: map.entries.firstOrNull { it.key.startsWith("$appPackage/") }?.value
            ?: return null
        return loadDrawableBitmap(context, packPkg, drawableName)
    }

    @Synchronized
    private fun loadMap(context: Context, packPkg: String): Map<String, String> {
        if (cachedPack == packPkg) return cachedMap
        val result = mutableMapOf<String, String>()
        // 1) res/xml/appfilter.xml
        try {
            val res = context.packageManager.getResourcesForApplication(packPkg)
            val xmlId = res.getIdentifier("appfilter", "xml", packPkg)
            if (xmlId != 0) {
                val parser = res.getXml(xmlId)
                parseAppFilter(parser, result)
                if (parser is XmlResourceParser) parser.close()
            }
        } catch (e: Exception) {
            // 次の手段へ
        }
        // 2) assets/appfilter.xml
        if (result.isEmpty()) {
            try {
                val packContext = context.createPackageContext(packPkg, 0)
                val input = packContext.assets.open("appfilter.xml")
                val factory = org.xmlpull.v1.XmlPullParserFactory.newInstance()
                val parser = factory.newPullParser()
                parser.setInput(input, "UTF-8")
                parseAppFilter(parser, result)
                input.close()
            } catch (e: Exception) {
                // 対応表が無いパック
            }
        }
        cachedPack = packPkg
        cachedMap = result
        return result
    }

    private fun parseAppFilter(parser: XmlPullParser, out: MutableMap<String, String>) {
        var event = parser.eventType
        while (event != XmlPullParser.END_DOCUMENT) {
            if (event == XmlPullParser.START_TAG && parser.name == "item") {
                val component = parser.getAttributeValue(null, "component")
                val drawable = parser.getAttributeValue(null, "drawable")
                if (component != null && drawable != null) {
                    // "ComponentInfo{pkg/class}" → "pkg/class"
                    val key = component.removePrefix("ComponentInfo{").removeSuffix("}")
                    out[key] = drawable
                }
            }
            event = parser.next()
        }
    }

    private fun loadDrawableBitmap(context: Context, packPkg: String, drawableName: String): Bitmap? {
        return try {
            val res = context.packageManager.getResourcesForApplication(packPkg)
            val id = res.getIdentifier(drawableName, "drawable", packPkg)
            if (id == 0) return null
            val drawable = res.getDrawable(id, null) ?: return null
            val size = 192
            val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            drawable.setBounds(0, 0, size, size)
            drawable.draw(canvas)
            bitmap
        } catch (e: Exception) {
            null
        }
    }
}
