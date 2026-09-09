package com.alfietv.player

import android.content.Context

/** Small persistent provider session store shared by the TV activities. */
object SessionStore {
    private const val PREFS = "alfie_tv_session"
    private const val SERVER = "server"
    private const val USERNAME = "username"
    private const val PASSWORD = "password"

    fun save(context: Context, config: XtreamConfig) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(SERVER, config.serverUrl)
            .putString(USERNAME, config.username)
            .putString(PASSWORD, config.password)
            .apply()
    }

    fun load(context: Context): XtreamConfig? {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val server = prefs.getString(SERVER, null)?.trim()?.trimEnd('/')
        val username = prefs.getString(USERNAME, null)
        val password = prefs.getString(PASSWORD, null)
        if (server.isNullOrBlank() || username.isNullOrBlank() || password == null || password.isBlank()) return null
        return XtreamConfig(server, username, password)
    }

    fun clear(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().clear().apply()
    }
}
