package com.alfietv.player

import android.content.Context

object SettingsStore {
    private const val PREFS = "alfie_tv_settings"
    private const val AUTO_PLAY = "auto_play"
    private const val REMEMBER_POSITION = "remember_position"
    private const val SHOW_CLOCK = "show_clock"
    private const val CONFIRM_EXIT = "confirm_exit"
    private const val PLAYER_CONTROLS = "player_controls"

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    fun autoPlay(context: Context) = prefs(context).getBoolean(AUTO_PLAY, true)
    fun setAutoPlay(context: Context, value: Boolean) = prefs(context).edit().putBoolean(AUTO_PLAY, value).apply()
    fun rememberPosition(context: Context) = prefs(context).getBoolean(REMEMBER_POSITION, true)
    fun setRememberPosition(context: Context, value: Boolean) = prefs(context).edit().putBoolean(REMEMBER_POSITION, value).apply()
    fun showClock(context: Context) = prefs(context).getBoolean(SHOW_CLOCK, true)
    fun setShowClock(context: Context, value: Boolean) = prefs(context).edit().putBoolean(SHOW_CLOCK, value).apply()
    fun confirmExit(context: Context) = prefs(context).getBoolean(CONFIRM_EXIT, false)
    fun setConfirmExit(context: Context, value: Boolean) = prefs(context).edit().putBoolean(CONFIRM_EXIT, value).apply()
    fun playerControls(context: Context) = prefs(context).getBoolean(PLAYER_CONTROLS, true)
    fun setPlayerControls(context: Context, value: Boolean) = prefs(context).edit().putBoolean(PLAYER_CONTROLS, value).apply()
    fun reset(context: Context) = prefs(context).edit().clear().apply()
}
