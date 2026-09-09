package com.alfietv.player

import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URI

class XtreamClient {
    fun load(config: XtreamConfig): Pair<List<IptvCategory>, List<IptvChannel>> {
        val base = config.serverUrl.trimEnd('/')
        require(URI(base).scheme in listOf("http", "https")) { "Invalid server URL" }

        val auth = get("$base/player_api.php?username=${enc(config.username)}&password=${enc(config.password)}")
        val authJson = JSONObject(auth)
        if (authJson.optJSONObject("user_info")?.optString("auth") == "0") {
            throw IllegalStateException("Provider authentication failed")
        }

        val categories = mutableListOf<IptvCategory>()
        parseArray(get("$base/player_api.php?username=${enc(config.username)}&password=${enc(config.password)}&action=get_live_categories"))
            .forEach { o -> categories += IptvCategory(o.optString("category_id"), o.optString("category_name"), "live") }

        val channels = mutableListOf<IptvChannel>()
        parseArray(get("$base/player_api.php?username=${enc(config.username)}&password=${enc(config.password)}&action=get_live_streams"))
            .forEach { o ->
                val streamId = o.optString("stream_id")
                if (streamId.isNotBlank()) {
                    channels += IptvChannel(
                        id = streamId,
                        name = o.optString("name"),
                        streamUrl = "$base/live/${enc(config.username)}/${enc(config.password)}/$streamId.m3u8",
                        categoryId = o.optString("category_id").ifBlank { null },
                        logoUrl = o.optString("stream_icon").ifBlank { null },
                        epgId = o.optString("epg_channel_id").ifBlank { null }
                    )
                }
            }
        return categories to channels
    }

    private fun get(url: String): String {
        val c = URI(url).toURL().openConnection() as HttpURLConnection
        c.connectTimeout = 10_000
        c.readTimeout = 20_000
        c.requestMethod = "GET"
        return c.inputStream.bufferedReader().use { it.readText() }.also { c.disconnect() }
    }

    private fun parseArray(text: String): List<JSONObject> {
        val a = JSONArray(text)
        return List(a.length()) { a.getJSONObject(it) }
    }

    private fun enc(value: String): String = java.net.URLEncoder.encode(value, Charsets.UTF_8.name())
}
