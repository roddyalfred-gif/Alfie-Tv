package com.alfietv.player

import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.KeyEvent
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Switch

/** TV-friendly settings hub. All options persist locally and are safe to use with a remote. */
class SettingsActivity : androidx.activity.ComponentActivity() {
    private val bg = Color.rgb(8, 12, 22)
    private val surface = Color.rgb(18, 25, 40)
    private val accent = Color.rgb(0, 168, 255)
    private val text = Color.WHITE
    private val secondary = Color.rgb(170, 181, 200)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = bg
        window.navigationBarColor = bg
        buildSettings()
    }

    private fun buildSettings() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(72, 28, 72, 28)
            setBackgroundColor(bg)
            isFocusable = false
        }
        root.addView(TextView(this).apply {
            text = "SETTINGS"
            textSize = 30f
            setTextColor(accent)
            typeface = Typeface.DEFAULT_BOLD
        }, LinearLayout.LayoutParams(-1, -2))
        root.addView(TextView(this).apply {
            text = "Customize playback, interface and viewing behavior"
            textSize = 14f
            setTextColor(secondary)
            setPadding(0, 4, 0, 16)
        }, LinearLayout.LayoutParams(-1, -2))

        addHeader(root, "PLAYBACK")
        addSwitch(root, "Auto-play channels", "Start playback automatically when a channel is selected", SettingsStore.autoPlay(this)) { SettingsStore.setAutoPlay(this, it) }
        addSwitch(root, "Remember playback position", "Resume movies and series where you stopped", SettingsStore.rememberPosition(this)) { SettingsStore.setRememberPosition(this, it) }
        addSwitch(root, "Player controls", "Show the on-screen playback controls", SettingsStore.playerControls(this)) { SettingsStore.setPlayerControls(this, it) }

        addHeader(root, "INTERFACE")
        addSwitch(root, "Show clock", "Display the device time where supported by the interface", SettingsStore.showClock(this)) { SettingsStore.setShowClock(this, it) }
        addSwitch(root, "Confirm exit", "Ask before leaving the app with the Back key", SettingsStore.confirmExit(this)) { SettingsStore.setConfirmExit(this, it) }

        addHeader(root, "MAINTENANCE")
        addButton(root, "↻  Refresh provider data") { refresh() }
        addButton(root, "♻  Reset app preferences") {
            SettingsStore.reset(this)
            buildSettings()
        }
        addButton(root, "←  Back") { finish() }
        setContentView(root)
        root.post { if (root.childCount > 2) root.getChildAt(2).requestFocus() }
    }

    private fun addHeader(root: LinearLayout, title: String) {
        root.addView(TextView(this).apply {
            text = title
            textSize = 12f
            setTextColor(accent)
            typeface = Typeface.DEFAULT_BOLD
            setPadding(4, 12, 4, 4)
        }, LinearLayout.LayoutParams(-1, -2))
    }

    private fun addSwitch(root: LinearLayout, title: String, subtitle: String, checked: Boolean, changed: (Boolean) -> Unit) {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(16, 8, 16, 8)
            background = rounded(surface)
            isFocusable = true
            isFocusableInTouchMode = true
            setOnFocusChangeListener { v, focused -> v.background = rounded(if (focused) accent else surface) }
        }
        val labels = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        labels.addView(TextView(this).apply { text = title; textSize = 16f; setTextColor(text) })
        labels.addView(TextView(this).apply { text = subtitle; textSize = 11f; setTextColor(secondary); setPadding(0, 2, 0, 0) })
        row.addView(labels, LinearLayout.LayoutParams(0, -2, 1f))
        row.addView(Switch(this).apply { isChecked = checked; setOnCheckedChangeListener { _, value -> changed(value) } }, LinearLayout.LayoutParams(-2, -2))
        row.setOnClickListener { val sw = row.getChildAt(1) as Switch; sw.toggle() }
        root.addView(row, LinearLayout.LayoutParams(-1, 64).apply { topMargin = 5 })
    }

    private fun addButton(root: LinearLayout, label: String, action: () -> Unit) {
        val button = Button(this).apply {
            text = label
            isAllCaps = false
            textSize = 15f
            minHeight = 54
            setTextColor(text)
            background = rounded(surface)
            isFocusable = true
            stateListAnimator = null
            setOnFocusChangeListener { v, focused -> v.background = rounded(if (focused) accent else surface) }
            setOnClickListener { action() }
        }
        root.addView(button, LinearLayout.LayoutParams(-1, 54).apply { topMargin = 5 })
    }

    private fun refresh() {
        val config = SessionStore.load(this) ?: return
        Thread {
            runCatching {
                val (categories, channels) = XtreamClient().load(config)
                LiveTvCache.write(this, config, categories, channels)
            }
        }.start()
    }

    private fun rounded(color: Int) = GradientDrawable().apply { setColor(color); cornerRadius = 14f * resources.displayMetrics.density }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if (keyCode == KeyEvent.KEYCODE_BACK) { finish(); return true }
        return super.onKeyDown(keyCode, event)
    }
}
