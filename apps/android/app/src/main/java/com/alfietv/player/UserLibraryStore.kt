package com.alfietv.player

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.security.MessageDigest

/** Provider-scoped favorites, recently-watched items, and VOD playback progress. Passwords are never stored. */
object UserLibraryStore {
    enum class Type { LIVE, MOVIE, SERIES, EPISODE }

    data class Item(
        val id: String,
        val type: Type,
        val title: String,
        val streamUrl: String,
        val categoryId: String? = null,
        val posterUrl: String? = null,
        val seriesId: String? = null,
        val season: Int? = null,
        val episode: Int? = null,
        val watchedAt: Long = System.currentTimeMillis(),
        val positionMs: Long = 0L,
        val durationMs: Long = 0L
    )

    private const val PREFS = "alfie_tv_library"
    private const val FAVORITES = "favorites"
    private const val RECENT = "recent"
    private const val MAX_RECENT = 30

    private fun prefs(context: Context, config: XtreamConfig) =
        context.getSharedPreferences("${PREFS}_${providerKey(config)}", Context.MODE_PRIVATE)

    private fun key(item: Item): String = "${item.type.name}|${item.id}|${item.seriesId.orEmpty()}"

    fun favorites(context: Context, config: XtreamConfig): List<Item> =
        read(prefs(context, config).getString(FAVORITES, null)).sortedBy { it.title.lowercase() }

    fun isFavorite(context: Context, config: XtreamConfig, item: Item): Boolean =
        favorites(context, config).any { key(it) == key(item) }

    fun toggleFavorite(context: Context, config: XtreamConfig, item: Item): Boolean {
        val current = favorites(context, config).toMutableList()
        val index = current.indexOfFirst { key(it) == key(item) }
        val added = index < 0
        if (added) current.add(item) else current.removeAt(index)
        write(prefs(context, config), FAVORITES, current)
        return added
    }

    fun recent(context: Context, config: XtreamConfig): List<Item> =
        read(prefs(context, config).getString(RECENT, null)).sortedByDescending { it.watchedAt }

    fun findRecent(context: Context, config: XtreamConfig, item: Item): Item? =
        recent(context, config).firstOrNull { key(it) == key(item) }

    fun recordWatched(context: Context, config: XtreamConfig, item: Item) {
        val current = recent(context, config).filterNot { key(it) == key(item) }.toMutableList()
        current.add(0, item.copy(watchedAt = System.currentTimeMillis()))
        write(prefs(context, config), RECENT, current.take(MAX_RECENT))
    }

    fun updateProgress(context: Context, config: XtreamConfig, item: Item, positionMs: Long, durationMs: Long) {
        if (item.type != Type.MOVIE && item.type != Type.EPISODE) return
        val current = recent(context, config).toMutableList()
        val index = current.indexOfFirst { key(it) == key(item) }
        val updated = item.copy(
            watchedAt = System.currentTimeMillis(),
            positionMs = positionMs.coerceAtLeast(0L),
            durationMs = durationMs.coerceAtLeast(0L)
        )
        if (index >= 0) current[index] = updated else current.add(0, updated)
        write(prefs(context, config), RECENT, current.take(MAX_RECENT))
    }

    fun clearProgress(context: Context, config: XtreamConfig, item: Item) {
        val current = recent(context, config).map { if (key(it) == key(item)) it.copy(positionMs = 0L, durationMs = 0L) else it }
        write(prefs(context, config), RECENT, current)
    }

    fun clear(context: Context, config: XtreamConfig) = prefs(context, config).edit().clear().apply()

    private fun write(prefs: android.content.SharedPreferences, name: String, items: List<Item>) {
        val array = JSONArray()
        items.forEach { item ->
            array.put(JSONObject().apply {
                put("id", item.id); put("type", item.type.name); put("title", item.title); put("streamUrl", item.streamUrl)
                item.categoryId?.let { put("categoryId", it) }; item.posterUrl?.let { put("posterUrl", it) }
                item.seriesId?.let { put("seriesId", it) }; item.season?.let { put("season", it) }; item.episode?.let { put("episode", it) }
                put("watchedAt", item.watchedAt); put("positionMs", item.positionMs); put("durationMs", item.durationMs)
            })
        }
        prefs.edit().putString(name, array.toString()).apply()
    }

    private fun read(raw: String?): List<Item> {
        if (raw.isNullOrBlank()) return emptyList()
        return try {
            val array = JSONArray(raw)
            List(array.length()) { i ->
                val o = array.getJSONObject(i)
                val type = runCatching { Type.valueOf(o.optString("type", Type.LIVE.name)) }.getOrDefault(Type.LIVE)
                Item(
                    o.optString("id"), type, o.optString("title"), o.optString("streamUrl"),
                    o.optString("categoryId").ifBlank { null }, o.optString("posterUrl").ifBlank { null },
                    o.optString("seriesId").ifBlank { null }, o.optInt("season", 0).takeIf { it != 0 },
                    o.optInt("episode", 0).takeIf { it != 0 }, o.optLong("watchedAt", 0L),
                    o.optLong("positionMs", 0L), o.optLong("durationMs", 0L)
                )
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun providerKey(config: XtreamConfig): String = sha256("${config.serverUrl.trimEnd('/')}|${config.username}").take(24)
    private fun sha256(value: String): String = MessageDigest.getInstance("SHA-256").digest(value.toByteArray()).joinToString("") { "%02x".format(it) }
}
