package com.alfietv.player

import android.content.Context
import android.content.SharedPreferences

enum class LayoutMode { GRID, LIST, TILE }

object LayoutModeStore {
    private const val PREFS = "alfie_layout"
    private const val VERSION = 1
    private fun prefs(context: Context): SharedPreferences = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    fun get(context: Context, screen: String, default: LayoutMode = LayoutMode.GRID): LayoutMode {
        return runCatching { LayoutMode.valueOf(prefs(context).getString("v$VERSION:$screen", default.name) ?: default.name) }.getOrDefault(default)
    }
    fun set(context: Context, screen: String, mode: LayoutMode) { prefs(context).edit().putString("v$VERSION:$screen", mode.name).apply() }
}
