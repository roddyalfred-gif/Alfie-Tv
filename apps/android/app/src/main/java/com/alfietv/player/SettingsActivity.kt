package com.alfietv.player

import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.KeyEvent
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Switch

/** TV-friendly settings hub. Options persist locally and are usable with a remote. */
class SettingsActivity : androidx.activity.ComponentActivity() {
    private var renderedSkinId: String? = null
    private val skin get() = SkinStore.current(this)
    private val bg get() = skin.background
    private val surface get() = skin.surface
    private val accent get() = skin.accent
    private val primaryText = Color.WHITE
    private val secondary = Color.rgb(185, 195, 210)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = bg
        window.navigationBarColor = bg
        SkinStore.applyWindow(this)
        renderedSkinId = skin.id
        buildSettings()
    }

    override fun onResume() {
        super.onResume()
        SkinStore.applyWindow(this)
        val selectedSkin = skin.id
        if (renderedSkinId != selectedSkin) {
            renderedSkinId = selectedSkin
            buildSettings()
        }
    }

    private fun buildSettings() {
        val widthDp = resources.configuration.screenWidthDp
        val compact = widthDp < 600
        val horizontalPadding = when {
            widthDp < 360 -> 12
            compact -> 16
            widthDp < 900 -> 28
            else -> 72
        }
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(horizontalPadding, if (compact) 16 else 28, horizontalPadding, if (compact) 24 else 40)
            setBackgroundColor(bg)
            isFocusable = false
        }
        content.addView(TextView(this).apply {
            text = "SETTINGS"
            textSize = if (compact) 24f else 30f
            setTextColor(accent)
            typeface = Typeface.DEFAULT_BOLD
        })
        content.addView(TextView(this).apply {
            text = "Player, picture, interface and recovery options"
            textSize = if (compact) 12f else 14f
            setTextColor(secondary)
            setPadding(0, 4, 0, if (compact) 10 else 14)
        })

        addHeader(content, "PLAYBACK")
        addSwitch(content, "Auto-play channels", "Start a stream immediately when selected", SettingsStore.autoPlay(this)) { SettingsStore.setAutoPlay(this, it) }
        addSwitch(content, "Remember playback position", "Resume movies and episodes from your last position", SettingsStore.rememberPosition(this)) { SettingsStore.setRememberPosition(this, it) }
        addSwitch(content, "Player controls", "Show Media3 playback controls on the player", SettingsStore.playerControls(this)) { SettingsStore.setPlayerControls(this, it) }
        addSwitch(content, "Auto-retry playback", "Recover automatically from stream and audio failures", SettingsStore.autoRetry(this)) { SettingsStore.setAutoRetry(this, it) }
        addSwitch(content, "Remember last channel", "Return to the last selected live channel", SettingsStore.rememberChannel(this)) { SettingsStore.setRememberChannel(this, it) }
        addChoice(content, "Picture size", "Auto uses the stream/device aspect ratio; Fit, Fill and Zoom are manual overrides", SettingsStore.aspectRatio(this), listOf("auto" to "Auto", "fit" to "Fit", "fill" to "Fill", "zoom" to "Zoom")) { SettingsStore.setAspectRatio(this, it) }
        addChoice(content, "Seek interval", "Skip amount for movies and episodes", SettingsStore.seekSeconds(this).toString(), listOf("5" to "5 sec", "10" to "10 sec", "15" to "15 sec", "30" to "30 sec", "60" to "60 sec")) { SettingsStore.setSeekSeconds(this, it.toInt()) }

        addHeader(content, "INTERFACE")
        val selectedSkin = SkinStore.current(this)
        val skinPicker = LinearLayout(this).apply {
            orientation = if (compact) LinearLayout.VERTICAL else LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        SkinStore.all().forEach { option ->
            val button = Button(this).apply {
                text = if (option.id == selectedSkin.id) "✓ ${option.name}" else option.name
                isAllCaps = false
                setTextColor(primaryText)
                stateListAnimator = null
                background = rounded(if (option.id == selectedSkin.id) option.accent else option.surface)
                isFocusable = true
                isFocusableInTouchMode = true
                setOnClickListener {
                    SkinStore.set(this@SettingsActivity, option.id)
                    SkinStore.applyWindow(this@SettingsActivity)
                    buildSettings()
                }
            }
            if (compact) {
                skinPicker.addView(button, LinearLayout.LayoutParams(-1, 50).apply { topMargin = 4 })
            } else {
                skinPicker.addView(button, LinearLayout.LayoutParams(0, 54, 1f).apply { marginEnd = 6 })
            }
        }
        content.addView(skinPicker, LinearLayout.LayoutParams(-1, if (compact) -2 else 60))
        addChoice(content, "Visual skin", "Animated adaptive appearance for phone, tablet and TV", selectedSkin.id, SkinStore.all().map { it.id to it.name }) {
            SkinStore.set(this, it)
            SkinStore.applyWindow(this)
            buildSettings()
        }
        addSwitch(content, "Show clock", "Display the current device time in the player overlay", SettingsStore.showClock(this)) { SettingsStore.setShowClock(this, it) }
        addSwitch(content, "Confirm exit", "Ask before closing the player with Back", SettingsStore.confirmExit(this)) { SettingsStore.setConfirmExit(this, it) }

        addHeader(content, "MAINTENANCE")
        addButton(content, "↻  Refresh provider data") { refresh() }
        addButton(content, "♻  Reset app preferences") { SettingsStore.reset(this); buildSettings() }
        addButton(content, "←  Back") { finish() }

        val scroll = ScrollView(this).apply {
            isFillViewport = true
            isFocusable = false
            setBackgroundColor(bg)
            addView(content, ViewGroup.LayoutParams(-1, -2))
        }
        setContentView(scroll)
        SkinStore.animate(content, skin)
        content.post { content.getChildAt(3)?.requestFocus() }
    }

    private fun addHeader(root: LinearLayout, title: String) {
        root.addView(TextView(this).apply { text = title; textSize = 12f; setTextColor(accent); typeface = Typeface.DEFAULT_BOLD; setPadding(4, 12, 4, 4) })
    }

    private fun addSwitch(root: LinearLayout, title: String, subtitle: String, checked: Boolean, changed: (Boolean) -> Unit) {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; setPadding(16, 8, 16, 8); background = rounded(surface)
            isFocusable = true; isFocusableInTouchMode = true
            setOnFocusChangeListener { v, focused -> v.background = if (focused) focusedRow() else rounded(surface) }
        }
        val labels = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; isFocusable = false }
        labels.addView(TextView(this).apply { text = title; textSize = 16f; setTextColor(primaryText) })
        labels.addView(TextView(this).apply { text = subtitle; textSize = 11f; setTextColor(secondary); setPadding(0, 2, 0, 0) })
        row.addView(labels, LinearLayout.LayoutParams(0, -2, 1f))
        row.addView(Switch(this).apply { isFocusable = false; isChecked = checked; setOnCheckedChangeListener { _, value -> changed(value) } })
        row.setOnClickListener { (row.getChildAt(1) as Switch).toggle() }
        root.addView(row, LinearLayout.LayoutParams(-1, 64).apply { topMargin = 5 })
    }

    private fun addChoice(root: LinearLayout, title: String, subtitle: String, selected: String, choices: List<Pair<String, String>>, changed: (String) -> Unit) {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; setPadding(16, 8, 16, 8); background = rounded(surface)
            isFocusable = true; isFocusableInTouchMode = true
            setOnFocusChangeListener { v, focused -> v.background = rounded(if (focused) accent else surface) }
        }
        val labels = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; isFocusable = false }
        labels.addView(TextView(this).apply { text = title; textSize = 16f; setTextColor(primaryText) })
        labels.addView(TextView(this).apply { text = subtitle; textSize = 11f; setTextColor(secondary); setPadding(0, 2, 0, 0) })
        row.addView(labels, LinearLayout.LayoutParams(0, -2, 1f))
        val button = Button(this).apply {
            isAllCaps = false; textSize = 14f; setTextColor(primaryText); stateListAnimator = null; isFocusable = false; background = rounded(surface)
            text = choices.firstOrNull { it.first == selected }?.second ?: choices.first().second
            setOnClickListener {
                val current = when {
                    title == "Seek interval" -> SettingsStore.seekSeconds(this@SettingsActivity).toString()
                    title == "Picture size" -> SettingsStore.aspectRatio(this@SettingsActivity)
                    else -> selected
                }
                val index = choices.indexOfFirst { it.first == current }.let { if (it >= 0) it else 0 }
                val next = choices[(index + 1) % choices.size]
                changed(next.first); text = next.second
            }
        }
        row.addView(button, LinearLayout.LayoutParams(
            if (resources.configuration.screenWidthDp < 600) -2 else 150, 52
        ))
        row.setOnClickListener { button.performClick() }
        root.addView(row, LinearLayout.LayoutParams(-1, if (resources.configuration.screenWidthDp < 600) 86 else 72).apply { topMargin = 5 })
    }

    private fun addButton(root: LinearLayout, label: String, action: () -> Unit) {
        val button = Button(this).apply {
            text = label; isAllCaps = false; textSize = 15f; minHeight = 54; setTextColor(primaryText); background = rounded(surface)
            isFocusable = true; isFocusableInTouchMode = true; stateListAnimator = null
            setOnFocusChangeListener { v, focused -> v.background = rounded(if (focused) accent else surface) }
            setOnClickListener { action() }
        }
        root.addView(button, LinearLayout.LayoutParams(-1, 54).apply { topMargin = 5 })
    }

    private fun refresh() {
        val config = SessionStore.load(this) ?: return
        Thread { runCatching { val (categories, channels) = XtreamClient().load(config); LiveTvCache.write(this, config, categories, channels) } }.start()
    }

    private fun rounded(color: Int) = GradientDrawable().apply { setColor(color); cornerRadius = 14f * resources.displayMetrics.density }

    private fun focusedRow() = GradientDrawable().apply {
        setColor(surface)
        setStroke((2f * resources.displayMetrics.density).toInt(), accent)
        cornerRadius = 14f * resources.displayMetrics.density
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if (keyCode == KeyEvent.KEYCODE_BACK) { finish(); return true }
        return super.onKeyDown(keyCode, event)
    }
}