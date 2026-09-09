package com.alfietv.player

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest

/** Provider-scoped disk cache for Movies, Series and episode metadata. Passwords are never cached. */
object ContentCache {
    data class Data(
        val categories: List<IptvCategory>,
        val vod: List<VodItem>,
        val series: List<SeriesItem>,
        val savedAt: Long
    )

    data class EpisodeData(
        val seriesId: String,
        val episodes: List<SeriesEpisode>,
        val savedAt: Long
    )

    private fun file(context: Context, config: XtreamConfig, mode: String): File {
        val key = sha256("${config.serverUrl.trimEnd('/')}|${config.username}|$mode")
        return File(context.filesDir, "content_$key.json")
    }

    private fun episodeFile(context: Context, config: XtreamConfig, seriesId: String): File {
        val key = sha256("${config.serverUrl.trimEnd('/')}|${config.username}|episodes|$seriesId")
        return File(context.filesDir, "episodes_$key.json")
    }

    fun read(context: Context, config: XtreamConfig, mode: String): Data? {
        return try {
            val f = file(context, config, mode)
            if (!f.exists()) return null
            val root = JSONObject(f.readText())
            val categories = root.optJSONArray("categories").toCategories()
            val savedAt = root.optLong("savedAt", 0L)
            if (mode == "vod") Data(categories, root.optJSONArray("items").toVod(), emptyList(), savedAt)
            else Data(categories, emptyList(), root.optJSONArray("items").toSeries(), savedAt)
        } catch (_: Exception) {
            null
        }
    }

    fun write(context: Context, config: XtreamConfig, mode: String, categories: List<IptvCategory>, vod: List<VodItem> = emptyList(), series: List<SeriesItem> = emptyList()) {
        try {
            val root = JSONObject().put("savedAt", System.currentTimeMillis())
            root.put("categories", JSONArray().apply { categories.forEach { put(JSONObject().put("id", it.id).put("name", it.name).put("type", it.type)) } })
            root.put("items", if (mode == "vod") JSONArray().apply { vod.forEach { put(JSONObject().put("id", it.id).put("name", it.name).put("streamUrl", it.streamUrl).put("categoryId", it.categoryId).put("posterUrl", it.posterUrl).put("year", it.year).put("rating", it.rating).put("duration", it.duration)) } } else JSONArray().apply { series.forEach { put(JSONObject().put("id", it.id).put("name", it.name).put("categoryId", it.categoryId).put("posterUrl", it.posterUrl).put("year", it.year).put("rating", it.rating)) } })
            writeAtomically(file(context, config, mode), root.toString())
        } catch (_: Exception) { }
    }

    fun readEpisodes(context: Context, config: XtreamConfig, seriesId: String): EpisodeData? {
        return try {
            val f = episodeFile(context, config, seriesId)
            if (!f.exists()) return null
            val root = JSONObject(f.readText())
            val cachedId = root.optString("seriesId")
            if (cachedId.isBlank() || cachedId != seriesId) return null
            EpisodeData(cachedId, root.optJSONArray("episodes").toEpisodes(), root.optLong("savedAt", 0L))
        } catch (_: Exception) {
            null
        }
    }

    fun writeEpisodes(context: Context, config: XtreamConfig, seriesId: String, episodes: List<SeriesEpisode>) {
        try {
            val root = JSONObject().put("seriesId", seriesId).put("savedAt", System.currentTimeMillis())
            root.put("episodes", JSONArray().apply {
                episodes.forEach { e ->
                    put(JSONObject()
                        .put("id", e.id)
                        .put("name", e.name)
                        .put("streamUrl", e.streamUrl)
                        .put("season", e.season)
                        .put("episode", e.episode)
                        .put("overview", e.overview))
                }
            })
            writeAtomically(episodeFile(context, config, seriesId), root.toString())
        } catch (_: Exception) { }
    }

    fun clear(context: Context, config: XtreamConfig) {
        file(context, config, "vod").delete()
        file(context, config, "series").delete()
        context.filesDir.listFiles()?.filter { it.name.startsWith("episodes_") && it.name.endsWith(".json") }?.forEach { it.delete() }
    }

    fun ageText(data: Data): String = ageText(data.savedAt)

    fun ageText(data: EpisodeData): String = ageText(data.savedAt)

    private fun ageText(savedAt: Long): String {
        val age = (System.currentTimeMillis() - savedAt).coerceAtLeast(0L)
        val minutes = age / 60_000L
        return when { minutes < 1 -> "just now"; minutes < 60 -> "${minutes}m ago"; minutes < 1440 -> "${minutes / 60}h ago"; else -> "${minutes / 1440}d ago" }
    }

    private fun writeAtomically(target: File, text: String) {
        val tmp = File(target.parentFile, "${target.name}.tmp")
        tmp.writeText(text)
        if (!tmp.renameTo(target)) { target.writeText(text); tmp.delete() }
    }

    private fun JSONArray?.toCategories(): List<IptvCategory> = this?.let { a -> List(a.length()) { i -> a.getJSONObject(i).let { IptvCategory(it.optString("id"), it.optString("name"), it.optString("type")) } } } ?: emptyList()
    private fun JSONArray?.toVod(): List<VodItem> = this?.let { a -> List(a.length()) { i -> a.getJSONObject(i).let { VodItem(it.optString("id"), it.optString("name"), it.optString("streamUrl"), it.optString("categoryId").ifBlank { null }, it.optString("posterUrl").ifBlank { null }, it.optString("year").ifBlank { null }, it.optString("rating").ifBlank { null }, it.optString("duration").ifBlank { null }) } } } ?: emptyList()
    private fun JSONArray?.toSeries(): List<SeriesItem> = this?.let { a -> List(a.length()) { i -> a.getJSONObject(i).let { SeriesItem(it.optString("id"), it.optString("name"), it.optString("categoryId").ifBlank { null }, it.optString("posterUrl").ifBlank { null }, it.optString("year").ifBlank { null }, it.optString("rating").ifBlank { null }) } } } ?: emptyList()
    private fun JSONArray?.toEpisodes(): List<SeriesEpisode> = this?.let { a -> List(a.length()) { i -> a.getJSONObject(i).let { SeriesEpisode(it.optString("id"), it.optString("name"), it.optString("streamUrl"), it.optInt("season", 0).takeIf { n -> n != 0 }, it.optInt("episode", 0).takeIf { n -> n != 0 }, it.optString("overview").ifBlank { null }) } } } ?: emptyList()
    private fun sha256(value: String): String = MessageDigest.getInstance("SHA-256").digest(value.toByteArray()).joinToString("") { "%02x".format(it) }
}
