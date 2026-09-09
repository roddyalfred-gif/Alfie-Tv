package com.alfietv.player

data class XtreamConfig(
    val serverUrl: String,
    val username: String,
    val password: String
)

data class IptvCategory(
    val id: String,
    val name: String,
    val type: String
)

data class IptvChannel(
    val id: String,
    val name: String,
    val streamUrl: String,
    val categoryId: String? = null,
    val logoUrl: String? = null,
    val epgId: String? = null
)

data class EpgProgram(
    val channelId: String,
    val title: String,
    val startUtcMs: Long,
    val endUtcMs: Long,
    val description: String? = null
)
