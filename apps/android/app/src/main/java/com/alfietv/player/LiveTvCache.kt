package com.alfietv.player

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.security.MessageDigest

/** Disk cache for live TV data. Credentials are never stored in the cache. */
object LiveTvCache {
    private const val VERSION = 1
    private const val MAX_AGE_MS = 15 * 60 * 1000L

    data class Snapshot(val categories: List<IptvCategory>, val channels: List<IptvChannel>, val savedAt: Long)

    fun read(context: Context, config: XtreamConfig): Snapshot? {
        val file = fileName(config)
        val raw = runCatching { context.openFileInput(file).bufferedReader().use { it.readText() } }.getOrNull() ?: return null
        return runCatching {
            val root = JSONObject(raw)
            if (root.optInt("version") != VERSION) return null
            val savedAt = root.optLong("savedAt", 0L)
            if (savedAt <= 0L) return null
            val categories = mutableListOf<IptvCategory>()
            val categoryArray = root.optJSONArray("categories") ?: JSONArray()
            for (i in 0 until categoryArray.length()) {
                val o = categoryArray.optJSONObject(i) ?: continue
                categories += IptvCategory(o.optString("id"), o.optString("name"), "live")
            }
            val channels = mutableListOf<IptvChannel>()
            val channelArray = root.optJSONArray("channels") ?: JSONArray()
            for (i in 0 until channelArray.length()) {
                val o = channelArray.optJSONObject(i) ?: continue
                val id = o.optString("id").takeIf { it.isNotBlank() } ?: continue
                channels += IptvChannel(
                    id, o.optString("name"), o.optString("streamUrl"),
                    o.optString("categoryId").ifBlank { null },
                    o.optString("logoUrl").ifBlank { null },
                    o.optString("epgId").ifBlank { null }
                )
            }
            if (channels.isEmpty()) return null
            Snapshot(categories, channels, savedAt)
        }.getOrNull()
    }

    fun write(context: Context, config: XtreamConfig, categories: List<IptvCategory>, channels: List<IptvChannel>) {
        if (channels.isEmpty()) return
        val root = JSONObject().put("version", VERSION).put("savedAt", System.currentTimeMillis())
        root.put("categories", JSONArray().also { array -> categories.forEach { array.put(JSONObject().put("id", it.id).put("name", it.name)) } })
        root.put("channels", JSONArray().also { array ->
            channels.forEach { channel ->
                array.put(JSONObject().put("id", channel.id).put("name", channel.name).put("streamUrl", channel.streamUrl)
                    .put("categoryId", channel.categoryId ?: "").put("logoUrl", channel.logoUrl ?: "").put("epgId", channel.epgId ?: ""))
            }
        })
        runCatching { context.openFileOutput(fileName(config), Context.MODE_PRIVATE).bufferedWriter().use { it.write(root.toString()) } }
    }

    fun isFresh(snapshot: Snapshot): Boolean = System.currentTimeMillis() - snapshot.savedAt <= MAX_AGE_MS

    fun ageText(snapshot: Snapshot): String {
        val ageMinutes = ((System.currentTimeMillis() - snapshot.savedAt).coerceAtLeast(0L) / 60_000L)
        return when {
            ageMinutes < 1 -> "just now"
            ageMinutes == 1L -> "1 min ago"
            else -> "$ageMinutes min ago"
        }
    }

    fun clear(context: Context) {
        context.fileList().filter { it.startsWith("live_tv_") && it.endsWith(".json") }.forEach { context.deleteFile(it) }
    }

    private fun fileName(config: XtreamConfig): String = "live_tv_${sha256(config.serverUrl.trimEnd('/') + "\n" + config.username)}.json"

    private fun sha256(value: String): String = MessageDigest.getInstance("SHA-256")
        .digest(value.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
}
