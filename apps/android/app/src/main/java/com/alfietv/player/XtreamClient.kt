package com.alfietv.player

import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URI

class XtreamClient {
    fun load(config: XtreamConfig): Pair<List<IptvCategory>, List<IptvChannel>> {
        val base = checkedBase(config)
        val authJson = JSONObject(get("$base/player_api.php?username=${enc(config.username)}&password=${enc(config.password)}"))
        if (authJson.optJSONObject("user_info")?.optString("auth") == "0") throw IllegalStateException("Provider authentication failed")
        val categories = parseArray(get(api(config, "get_live_categories"))).map { IptvCategory(it.optString("category_id"), it.optString("category_name"), "live") }
        val channels = parseArray(get(api(config, "get_live_streams"))).mapNotNull { o ->
            val id = o.optString("stream_id").takeIf { it.isNotBlank() } ?: return@mapNotNull null
            IptvChannel(id, o.optString("name"), "$base/live/${enc(config.username)}/${enc(config.password)}/$id.m3u8", o.optString("category_id").ifBlank { null }, o.optString("stream_icon").ifBlank { null }, o.optString("epg_channel_id").ifBlank { null })
        }
        return categories to channels
    }

    fun loadVod(config: XtreamConfig): Pair<List<IptvCategory>, List<VodItem>> {
        val categories = parseArray(get(api(config, "get_vod_categories"))).map { IptvCategory(it.optString("category_id"), it.optString("category_name"), "vod") }
        val base = checkedBase(config)
        val items = parseArray(get(api(config, "get_vod_streams"))).mapNotNull { o ->
            val id = o.optString("stream_id").takeIf { it.isNotBlank() } ?: return@mapNotNull null
            val ext = o.optString("container_extension").ifBlank { "mp4" }
            VodItem(id, o.optString("name"), "$base/movie/${enc(config.username)}/${enc(config.password)}/$id.$ext", o.optString("category_id").ifBlank { null }, o.optString("stream_icon").ifBlank { null }, o.optString("year").ifBlank { null }, o.optString("rating").ifBlank { null }, o.optString("duration").ifBlank { null })
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
                episodes += SeriesEpisode(id, o.optString("title").ifBlank { "Episode $episodeNumber" }, "${checkedBase(config)}/series/${enc(config.username)}/${enc(config.password)}/$id.$ext", seasonNumber, episodeNumber, o.optString("info").ifBlank { null })
            }
        }
        return episodes.sortedWith(compareBy({ it.season ?: 0 }, { it.episode ?: 0 }))
    }

    fun loadEpg(config: XtreamConfig, channel: IptvChannel, limit: Int = 8): List<EpgProgram> {
        val url = api(config, "get_short_epg", "stream_id" to channel.id, "limit" to limit.toString())
        return parseArray(get(url)).mapNotNull { o ->
            val start = parseXtreamTime(o.optString("start")) ?: return@mapNotNull null
            val end = parseXtreamTime(o.optString("end")) ?: return@mapNotNull null
            EpgProgram(channel.id, o.optString("title").ifBlank { "Program" }, start, end, o.optString("description").ifBlank { null })
        }.sortedBy { it.startUtcMs }
    }

    private fun checkedBase(config: XtreamConfig): String = config.serverUrl.trimEnd('/').also { require(URI(it).scheme in listOf("http", "https")) { "Invalid server URL" } }
    private fun api(config: XtreamConfig, action: String, vararg params: Pair<String, String>): String {
        val base = checkedBase(config)
        val query = buildList { add("username=${enc(config.username)}"); add("password=${enc(config.password)}"); add("action=${enc(action)}"); params.forEach { add("${enc(it.first)}=${enc(it.second)}") } }.joinToString("&")
        return "$base/player_api.php?$query"
    }
    private fun parseXtreamTime(value: String): Long? = try { java.time.LocalDateTime.parse(value.replace(" ", "T")).toInstant(java.time.ZoneOffset.UTC).toEpochMilli() } catch (_: Exception) { null }
    private fun get(url: String): String {
        val c = URI(url).toURL().openConnection() as HttpURLConnection
        c.connectTimeout = 10_000; c.readTimeout = 20_000; c.requestMethod = "GET"
        return try { if (c.responseCode !in 200..299) throw IllegalStateException("Provider returned HTTP ${c.responseCode}"); c.inputStream.bufferedReader().use { it.readText() } } finally { c.disconnect() }
    }
    private fun parseArray(text: String): List<JSONObject> { val a = JSONArray(text); return List(a.length()) { a.getJSONObject(it) } }
    private fun enc(value: String): String = java.net.URLEncoder.encode(value, Charsets.UTF_8.name())
}
