package com.alfietv.player

import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URI

class XtreamClient {
    fun load(config: XtreamConfig): Pair<List<IptvCategory>, List<IptvChannel>> {
        val base = checkedBase(config)
        val authJson = JSONObject(get("$base/player_api.php?username=${enc(config.username)}&password=${enc(config.password)}"))
        validateAuth(authJson)
        val categories = parseArray(get(api(config, "get_live_categories"))).map { IptvCategory(it.optString("category_id"), it.optString("category_name"), "live") }
        val channels = parseArray(get(api(config, "get_live_streams"))).mapNotNull { o ->
            val id = o.optString("stream_id").takeIf { it.isNotBlank() } ?: return@mapNotNull null
            val fallback = "$base/live/${enc(config.username)}/${enc(config.password)}/$id.m3u8"
            IptvChannel(id, o.optString("name"), o.optString("direct_source").trim().ifBlank { fallback }, o.optString("category_id").ifBlank { null }, o.optString("stream_icon").ifBlank { null }, o.optString("epg_channel_id").ifBlank { null })
        }
        return categories to channels
    }

    fun loadVod(config: XtreamConfig): Pair<List<IptvCategory>, List<VodItem>> {
        val base = checkedBase(config)
        val categories = parseArray(get(api(config, "get_vod_categories"))).map { IptvCategory(it.optString("category_id"), it.optString("category_name"), "vod") }
        val items = parseArray(get(api(config, "get_vod_streams"))).mapNotNull { o ->
            val id = o.optString("stream_id").takeIf { it.isNotBlank() } ?: return@mapNotNull null
            val ext = o.optString("container_extension").ifBlank { "mp4" }
            val fallback = "$base/movie/${enc(config.username)}/${enc(config.password)}/$id.$ext"
            VodItem(id, o.optString("name"), o.optString("direct_source").trim().ifBlank { fallback }, o.optString("category_id").ifBlank { null }, o.optString("stream_icon").ifBlank { null }, o.optString("year").ifBlank { null }, o.optString("rating").ifBlank { null }, o.optString("duration").ifBlank { null })
        }
        return categories to items
    }

    fun loadSeries(config: XtreamConfig): Pair<List<IptvCategory>, List<SeriesItem>> {
        val categories = parseArray(get(api(config, "get_series_categories"))).map { IptvCategory(it.optString("category_id"), it.optString("category_name"), "series") }
        val items = parseArray(get(api(config, "get_series"))).mapNotNull { o ->
            val id = o.optString("series_id").takeIf { it.isNotBlank() } ?: return@mapNotNull null
            SeriesItem(id, o.optString("name"), o.optString("category_id").ifBlank { null }, o.optString("cover").ifBlank { null }, o.optString("year").ifBlank { null }, o.optString("rating").ifBlank { null })
        }
        return categories to items
    }

    fun loadSeriesEpisodes(config: XtreamConfig, seriesId: String): List<SeriesEpisode> {
        val root = JSONObject(get(api(config, "get_series_info", "series_id" to seriesId)))
        val episodes = mutableListOf<SeriesEpisode>()
        val seasons = root.optJSONObject("episodes") ?: return episodes
        for (seasonKey in seasons.keys()) {
            val seasonNumber = seasonKey.toIntOrNull()
            val array = seasons.optJSONArray(seasonKey) ?: continue
            for (i in 0 until array.length()) {
                val o = array.optJSONObject(i) ?: continue
                val id = o.optString("id").ifBlank { o.optString("episode_num") }.takeIf { it.isNotBlank() } ?: continue
                val episodeNumber = o.optInt("episode_num", 0).takeIf { it > 0 }
                val ext = o.optString("container_extension").ifBlank { "mp4" }
                val fallback = "${checkedBase(config)}/series/${enc(config.username)}/${enc(config.password)}/$id.$ext"
                episodes += SeriesEpisode(id, o.optString("title").ifBlank { "Episode $episodeNumber" }, o.optString("direct_source").trim().ifBlank { fallback }, seasonNumber, episodeNumber, o.optString("info").ifBlank { null })
            }
        }
        return episodes.sortedWith(compareBy({ it.season ?: 0 }, { it.episode ?: 0 }))
    }

    fun loadEpg(config: XtreamConfig, channel: IptvChannel, limit: Int = 12): List<EpgProgram> {
        val safeLimit = limit.coerceIn(1, 50)
        val url = api(config, "get_short_epg", "stream_id" to channel.id, "limit" to safeLimit.toString())
        val root = get(url)
        val objects = runCatching { parseArray(root) }.getOrElse {
            val json = JSONObject(root)
            when {
                json.optJSONArray("epg_listings") != null -> parseJsonArray(json.getJSONArray("epg_listings"))
                json.optJSONArray("epg") != null -> parseJsonArray(json.getJSONArray("epg"))
                else -> emptyList()
            }
        }
        return objects.mapNotNull { o ->
            val start = parseXtreamTime(o.optString("start").ifBlank { o.optString("start_timestamp") }) ?: return@mapNotNull null
            val end = parseXtreamTime(o.optString("end").ifBlank { o.optString("stop_timestamp") }) ?: return@mapNotNull null
            if (end <= start) return@mapNotNull null
            EpgProgram(channel.id, o.optString("title").ifBlank { o.optString("name") }.ifBlank { "Program" }, start, end, o.optString("description").ifBlank { null })
        }.sortedBy { it.startUtcMs }
    }

    private fun validateAuth(authJson: JSONObject) {
        val userInfo = authJson.optJSONObject("user_info") ?: throw IllegalStateException("Provider returned an invalid authentication response")
        if (userInfo.optInt("auth", 0) != 1) throw IllegalStateException("Provider authentication failed")
        val status = userInfo.optString("status").trim().lowercase()
        if (status.isNotBlank() && status !in setOf("active", "enabled")) throw IllegalStateException("Provider account is not active")
        val expDate = userInfo.optString("exp_date").trim()
        val expiry = expDate.toLongOrNull()
        if (expiry != null && expiry > 0 && expiry <= System.currentTimeMillis() / 1000) throw IllegalStateException("Provider account has expired")
    }

    private fun checkedBase(config: XtreamConfig): String = config.serverUrl.trimEnd('/').also {
        require(URI(it).scheme in listOf("http", "https")) { "Invalid server URL" }
        require(config.username.isNotBlank()) { "Provider username is required" }
        require(config.password.isNotBlank()) { "Provider password is required" }
    }

    private fun api(config: XtreamConfig, action: String, vararg params: Pair<String, String>): String {
        val base = checkedBase(config)
        val query = buildList {
            add("username=${enc(config.username)}")
            add("password=${enc(config.password)}")
            add("action=${enc(action)}")
            params.forEach { add("${enc(it.first)}=${enc(it.second)}") }
        }.joinToString("&")
        return "$base/player_api.php?$query"
    }

    private fun parseXtreamTime(value: String): Long? {
        val raw = value.trim()
        if (raw.isBlank()) return null
        raw.toLongOrNull()?.let { epoch -> return if (epoch < 100_000_000_000L) epoch * 1000L else epoch }
        return runCatching { java.time.OffsetDateTime.parse(raw).toInstant().toEpochMilli() }.getOrNull()
            ?: runCatching { java.time.LocalDateTime.parse(raw.replace(" ", "T")).toInstant(java.time.ZoneOffset.UTC).toEpochMilli() }.getOrNull()
    }

    private fun get(url: String): String {
        val c = URI(url).toURL().openConnection() as HttpURLConnection
        c.connectTimeout = 10_000
        c.readTimeout = 20_000
        c.requestMethod = "GET"
        return try {
            if (c.responseCode !in 200..299) throw IllegalStateException("Provider returned HTTP ${c.responseCode}")
            c.inputStream.bufferedReader().use { it.readText() }
        } finally { c.disconnect() }
    }

    private fun parseArray(text: String): List<JSONObject> = parseJsonArray(JSONArray(text))

    private fun parseJsonArray(array: JSONArray): List<JSONObject> = List(array.length()) { array.getJSONObject(it) }

    private fun enc(value: String): String = java.net.URLEncoder.encode(value, Charsets.UTF_8.name())
}
