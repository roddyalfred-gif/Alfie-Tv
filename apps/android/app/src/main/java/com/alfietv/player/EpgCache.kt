package com.alfietv.player

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.security.MessageDigest

/** Provider- and channel-scoped EPG cache. Credentials are never stored. */
object EpgCache {
    private const val VERSION = 1
    private const val MAX_AGE_MS = 10 * 60 * 1000L

    data class Snapshot(val programs: List<EpgProgram>, val savedAt: Long)

    fun read(context: Context, config: XtreamConfig, channel: IptvChannel): Snapshot? {
        val raw = runCatching {
            context.openFileInput(fileName(config, channel)).bufferedReader().use { it.readText() }
        }.getOrNull() ?: return null
        return runCatching {
            val root = JSONObject(raw)
            if (root.optInt("version") != VERSION) return null
            val savedAt = root.optLong("savedAt", 0L)
            if (savedAt <= 0L) return null
            val programs = mutableListOf<EpgProgram>()
            val array = root.optJSONArray("programs") ?: JSONArray()
            for (i in 0 until array.length()) {
                val o = array.optJSONObject(i) ?: continue
                val title = o.optString("title").takeIf { it.isNotBlank() } ?: continue
                val start = o.optLong("startUtcMs", 0L)
                val end = o.optLong("endUtcMs", 0L)
                if (end <= start) continue
                programs += EpgProgram(channel.id, title, start, end, o.optString("description").ifBlank { null })
            }
            if (programs.isEmpty()) return null
            Snapshot(programs, savedAt)
        }.getOrNull()
    }

    fun write(context: Context, config: XtreamConfig, channel: IptvChannel, programs: List<EpgProgram>) {
        if (programs.isEmpty()) return
        val root = JSONObject().put("version", VERSION).put("savedAt", System.currentTimeMillis())
        root.put("programs", JSONArray().also { array ->
            programs.forEach { program ->
                array.put(JSONObject()
                    .put("title", program.title)
                    .put("startUtcMs", program.startUtcMs)
                    .put("endUtcMs", program.endUtcMs)
                    .put("description", program.description ?: ""))
            }
        })
        runCatching {
            context.openFileOutput(fileName(config, channel), Context.MODE_PRIVATE).bufferedWriter().use { it.write(root.toString()) }
        }
    }

    fun isFresh(snapshot: Snapshot): Boolean = System.currentTimeMillis() - snapshot.savedAt <= MAX_AGE_MS

    fun ageText(snapshot: Snapshot): String {
        val minutes = ((System.currentTimeMillis() - snapshot.savedAt).coerceAtLeast(0L) / 60_000L)
        return when {
            minutes < 1 -> "just now"
            minutes == 1L -> "1 min ago"
            else -> "$minutes min ago"
        }
    }

    fun clear(context: Context) {
        context.fileList().filter { it.startsWith("epg_") && it.endsWith(".json") }.forEach { context.deleteFile(it) }
    }

    private fun fileName(config: XtreamConfig, channel: IptvChannel): String =
        "epg_${sha256(config.serverUrl.trimEnd('/') + "\n" + config.username + "\n" + channel.id)}.json"

    private fun sha256(value: String): String = MessageDigest.getInstance("SHA-256")
        .digest(value.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
}
