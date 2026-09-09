package com.alfietv.player

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.security.MessageDigest

/** Provider-scoped favorites and recently-watched library. Passwords are never stored. */
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
        val watchedAt: Long = System.currentTimeMillis()
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

    fun recordWatched(context: Context, config: XtreamConfig, item: Item) {
        val current = recent(context, config).filterNot { key(it) == key(item) }.toMutableList()
        current.add(0, item.copy(watchedAt = System.currentTimeMillis()))
        write(prefs(context, config), RECENT, current.take(MAX_RECENT))
    }

    fun clear(context: Context, config: XtreamConfig) = prefs(context, config).edit().clear().apply()

    private fun write(prefs: android.content.SharedPreferences, name: String, items: List<Item>) {
        val array = JSONArray()
        items.forEach { item ->
            array.put(JSONObject().apply {
                put("id", item.id); put("type", item.type.name); put("title", item.title); put("streamUrl", item.streamUrl)
                item.categoryId?.let { put("categoryId", it) }; item.posterUrl?.let { put("posterUrl", it) }
                item.seriesId?.let { put("seriesId", it) }; item.season?.let { put("season", it) }; item.episode?.let { put("episode", it) }
                put("watchedAt", item.watchedAt)
            })
        }
        prefs.edit().putString(name, array.toString()).apply()
    }

    private fun read(raw: String?): List<Item> = try {
        if (raw.isNullOrBlank()) return emptyList()
        val array = JSONArray(raw)
        List(array.length()) { i ->
            val o = array.getJSONObject(i)
            Item(
                o.optString("id"), Type.valueOf(o.optString("type", Type.LIVE.name)), o.optString("title"), o.optString("streamUrl"),
                o.optString("categoryId").ifBlank { null }, o.optString("posterUrl").ifBlank { null }, o.optString("seriesId").ifBlank { null },
                o.optInt("season", 0).takeIf { it != 0 }, o.optInt("episode", 0).takeIf { it != 0 }, o.optLong("watchedAt", 0L)
            )
        }
    } catch (_: Exception) { emptyList() }

    private fun providerKey(config: XtreamConfig): String = sha256("${config.serverUrl.trimEnd('/')}|${config.username}").take(24)
    private fun sha256(value: String): String = MessageDigest.getInstance("SHA-256").digest(value.toByteArray()).joinToString("") { "%02x".format(it) }
}
