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

/** TV-friendly settings hub. Options persist locally and are usable with a remote. */
class SettingsActivity : androidx.activity.ComponentActivity() {
    private val bg = Color.rgb(8, 12, 22)
    private val surface = Color.rgb(18, 25, 40)
    private val accent = Color.rgb(0, 168, 255)
    private val textColor = Color.WHITE
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
        })
        root.addView(TextView(this).apply {
            text = "Make Alfie TV work the way you like"
            textSize = 14f
            setTextColor(secondary)
            setPadding(0, 4, 0, 14)
        })

        addHeader(root, "PLAYBACK")
        addSwitch(root, "Auto-play channels", "Start a stream immediately when selected", SettingsStore.autoPlay(this)) { SettingsStore.setAutoPlay(this, it) }
        addSwitch(root, "Remember playback position", "Resume movies and episodes from your last position", SettingsStore.rememberPosition(this)) { SettingsStore.setRememberPosition(this, it) }
        addSwitch(root, "Player controls", "Show Media3 playback controls", SettingsStore.playerControls(this)) { SettingsStore.setPlayerControls(this, it) }
        addChoice(root, "Picture size", "Fit preserves the full picture; Fill and Zoom use more of the screen", SettingsStore.aspectRatio(this), listOf("fit" to "Fit", "fill" to "Fill", "zoom" to "Zoom")) { SettingsStore.setAspectRatio(this, it) }

        addHeader(root, "INTERFACE")
        addSwitch(root, "Show clock", "Allow the interface to display device time", SettingsStore.showClock(this)) { SettingsStore.setShowClock(this, it) }
        addSwitch(root, "Confirm exit", "Ask before closing the player with Back", SettingsStore.confirmExit(this)) { SettingsStore.setConfirmExit(this, it) }

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
        })
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
        labels.addView(TextView(this).apply { text = title; textSize = 16f; setTextColor(textColor) })
        labels.addView(TextView(this).apply { text = subtitle; textSize = 11f; setTextColor(secondary); setPadding(0, 2, 0, 0) })
        row.addView(labels, LinearLayout.LayoutParams(0, -2, 1f))
        row.addView(Switch(this).apply { isChecked = checked; setOnCheckedChangeListener { _, value -> changed(value) } })
        row.setOnClickListener { (row.getChildAt(1) as Switch).toggle() }
        root.addView(row, LinearLayout.LayoutParams(-1, 64).apply { topMargin = 5 })
    }

    private fun addChoice(root: LinearLayout, title: String, subtitle: String, selected: String, choices: List<Pair<String, String>>, changed: (String) -> Unit) {
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
        labels.addView(TextView(this).apply { text = title; textSize = 16f; setTextColor(textColor) })
        labels.addView(TextView(this).apply { text = subtitle; textSize = 11f; setTextColor(secondary); setPadding(0, 2, 0, 0) })
        row.addView(labels, LinearLayout.LayoutParams(0, -2, 1f))
        val button = Button(this).apply {
            isAllCaps = false
            textSize = 14f
            text = choices.firstOrNull { it.first == selected }?.second ?: choices.first().second
            setTextColor(textColor)
            stateListAnimator = null
            background = rounded(surface)
            setOnClickListener {
                val index = choices.indexOfFirst { it.first == SettingsStore.aspectRatio(this@SettingsActivity) }.let { if (it >= 0) it else 0 }
                val next = choices[(index + 1) % choices.size]
                changed(next.first)
                text = next.second
            }
        }
        row.addView(button, LinearLayout.LayoutParams(150, 52))
        row.setOnClickListener { button.performClick() }
        root.addView(row, LinearLayout.LayoutParams(-1, 72).apply { topMargin = 5 })
    }

    private fun addButton(root: LinearLayout, label: String, action: () -> Unit) {
        val button = Button(this).apply {
            text = label
            isAllCaps = false
            textSize = 15f
            minHeight = 54
            setTextColor(textColor)
            background = rounded(surface)
            isFocusable = true
            isFocusableInTouchMode = true
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
