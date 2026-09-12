package com.alfietv.player

import android.animation.ObjectAnimator
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import java.util.concurrent.Executors

/** TV-first application hub with a modern, responsive and remote-friendly interface. */
class HomeActivity : androidx.activity.ComponentActivity() {
    private lateinit var config: XtreamConfig
    private val executor = Executors.newSingleThreadExecutor()
    private var status: TextView? = null
    private val backgroundColor = Color.rgb(5, 8, 17)
    private val surfaceColor = Color.rgb(16, 24, 40)
    private val surfaceAltColor = Color.rgb(24, 34, 54)
    private val accentColor = Color.rgb(0, 174, 255)
    private val accentAltColor = Color.rgb(104, 76, 255)
    private val textColor = Color.WHITE
    private val secondaryTextColor = Color.rgb(166, 178, 199)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = backgroundColor
        window.navigationBarColor = backgroundColor
        config = SessionStore.load(this) ?: XtreamConfig(
            intent.getStringExtra("server") ?: "",
            intent.getStringExtra("username") ?: "",
            intent.getStringExtra("password") ?: ""
        )
        if (config.serverUrl.isBlank() || config.username.isBlank() || config.password.isBlank()) {
            goToLogin()
            return
        }
        SessionStore.save(this, config)
        buildHome()
    }

    private fun buildHome() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(28, 20, 28, 18)
            setBackgroundColor(backgroundColor)
        }

        val topBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(4, 0, 4, 14)
        }
        val brand = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        brand.addView(TextView(this).apply {
            text = "ALFIE TV"
            textSize = 29f
            setTextColor(accentColor)
            typeface = Typeface.DEFAULT_BOLD
            letterSpacing = 0.12f
        })
        brand.addView(TextView(this).apply {
            text = "STREAM • WATCH • ENJOY"
            textSize = 10f
            setTextColor(secondaryTextColor)
            letterSpacing = 0.12f
            setPadding(0, 2, 0, 0)
        })
        topBar.addView(brand, LinearLayout.LayoutParams(0, -2, 1f))
        status = TextView(this).apply {
            text = "●  PROVIDER CONNECTED"
            textSize = 11f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            setTextColor(Color.rgb(120, 225, 175))
            background = roundedBackground(surfaceAltColor, 18f)
            setPadding(18, 9, 18, 9)
        }
        topBar.addView(status)
        root.addView(topBar)

        val hero = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24, 18, 24, 18)
            background = gradientBackground(accentAltColor, Color.rgb(22, 42, 75), 20f)
        }
        hero.addView(TextView(this).apply {
            text = "WELCOME TO ALFIE TV"
            textSize = 21f
            setTextColor(textColor)
            typeface = Typeface.DEFAULT_BOLD
        })
        hero.addView(TextView(this).apply {
            text = "Live television, your TV Guide, movies and series — all in one place."
            textSize = 12f
            setTextColor(Color.rgb(225, 232, 245))
            setPadding(0, 5, 0, 0)
        })
        root.addView(hero, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = 10 })

        val scroll = ScrollView(this).apply {
            isFillViewport = true
            isFocusable = false
            overScrollMode = View.OVER_SCROLL_IF_CONTENT_SCROLLS
        }
        val menu = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, 0, 0, 12)
        }
        scroll.addView(menu)

        addSectionLabel(menu, "LIVE & TV", "Your live channels and multi-program schedule")
        val liveRow = addCardRow(menu)
        val liveButton = addButton(liveRow, "📺  LIVE TV", "Channels • favorites • playback") {
            open(LiveTvActivity::class.java)
        }
        addButton(liveRow, "🗓  TV GUIDE", "NOW • NEXT • full schedule") {
            open(EpgGuideActivity::class.java)
        }

        addSectionLabel(menu, "ON DEMAND", "Browse your provider's entertainment library")
        val vodRow = addCardRow(menu)
        addButton(vodRow, "🎬  MOVIES", "Browse and play movies") {
            open(ContentActivity::class.java, "vod")
        }
        addButton(vodRow, "📺  SERIES", "Shows • seasons • episodes") {
            open(ContentActivity::class.java, "series")
        }

        addSectionLabel(menu, "MY LIBRARY", "Your saved and recently viewed content")
        val libraryRow = addCardRow(menu)
        addButton(libraryRow, "★  FAVORITES", "Saved channels and content") {
            open(FavoritesActivity::class.java)
        }
        addButton(libraryRow, "◷  RECENTLY WATCHED", "Quickly resume recent viewing") {
            open(FavoritesActivity::class.java)
        }

        addSectionLabel(menu, "APP & PROVIDER", "Manage playback, settings and your IPTV connection")
        val toolsRow = addCardRow(menu)
        addButton(toolsRow, "⚙  SETTINGS", "Player • display • playback") {
            open(SettingsActivity::class.java)
        }
        addButton(toolsRow, "↻  REFRESH", "Update channels and guide cache") {
            refreshProvider()
        }
        addButton(menu, "⇄  CHANGE PROVIDER / LOGOUT", "Disconnect and sign in with another IPTV provider") {
            clearProviderCache()
            SessionStore.clear(this)
            goToLogin()
        }

        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))

        val footer = TextView(this).apply {
            text = "↑ ↓ ← → Navigate   •   OK / Enter Select   •   Back Return"
            textSize = 10f
            gravity = Gravity.CENTER
            setTextColor(secondaryTextColor)
            setPadding(0, 8, 0, 0)
            isFocusable = false
        }
        root.addView(footer, LinearLayout.LayoutParams(-1, -2))

        setContentView(root)
        root.post { liveButton.requestFocus() }
    }

    private fun addSectionLabel(root: LinearLayout, title: String, subtitle: String) {
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(6, 9, 6, 4)
            isFocusable = false
        }
        container.addView(TextView(this).apply {
            text = title
            textSize = 12f
            setTextColor(accentColor)
            typeface = Typeface.DEFAULT_BOLD
            letterSpacing = 0.08f
        })
        container.addView(TextView(this).apply {
            text = subtitle
            textSize = 10f
            setTextColor(secondaryTextColor)
            setPadding(0, 2, 0, 0)
        })
        root.addView(container)
    }

    private fun addCardRow(root: LinearLayout): LinearLayout {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, 0, 0, 1)
        }
        root.addView(row, LinearLayout.LayoutParams(-1, 76))
        return row
    }

    private fun addButton(root: LinearLayout, label: String, description: String, action: () -> Unit): Button {
        val button = Button(this).apply {
            text = "$label\n$description"
            isAllCaps = false
            textSize = 13f
            minHeight = 0
            gravity = Gravity.CENTER_VERTICAL or Gravity.START
            setPadding(18, 0, 12, 0)
            setTextColor(textColor)
            background = roundedBackground(surfaceColor, 16f)
            isFocusable = true
            isFocusableInTouchMode = true
            stateListAnimator = null
            setOnFocusChangeListener { view, focused ->
                view.background = roundedBackground(if (focused) accentColor else surfaceColor, 16f)
                animateFocus(view, focused)
                if (focused) ensureVisible(view)
            }
            setOnClickListener { action() }
        }
        val params = LinearLayout.LayoutParams(0, -1, 1f).apply {
            leftMargin = 3
            rightMargin = 3
        }
        root.addView(button, params)
        return button
    }

    private fun animateFocus(view: View, focused: Boolean) {
        val target = if (focused) 1.025f else 1f
        ObjectAnimator.ofFloat(view, View.SCALE_X, view.scaleX, target).setDuration(140).start()
        ObjectAnimator.ofFloat(view, View.SCALE_Y, view.scaleY, target).setDuration(140).start()
    }

    private fun ensureVisible(view: View) {
        val scroll = view.parent?.parent?.parent as? ScrollView
        scroll?.post { scroll.smoothScrollTo(0, (view.top - 70).coerceAtLeast(0)) }
    }

    private fun refreshProvider() {
        status?.text = "↻  REFRESHING PROVIDER…"
        executor.execute {
            try {
                val (categories, channels) = XtreamClient().load(config)
                LiveTvCache.write(this, config, categories, channels)
                runOnUiThread {
                    status?.text = "●  ${channels.size} LIVE CHANNELS READY"
                }
            } catch (e: Exception) {
                runOnUiThread {
                    status?.text = "⚠  REFRESH FAILED"
                }
            }
        }
    }

    private fun roundedBackground(color: Int, radiusDp: Float): GradientDrawable = GradientDrawable().apply {
        setColor(color)
        cornerRadius = radiusDp * resources.displayMetrics.density
    }

    private fun gradientBackground(start: Int, end: Int, radiusDp: Float): GradientDrawable = GradientDrawable(
        GradientDrawable.Orientation.TL_BR,
        intArrayOf(start, end)
    ).apply {
        cornerRadius = radiusDp * resources.displayMetrics.density
    }

    private fun open(clazz: Class<*>, mode: String? = null) {
        startActivity(Intent(this, clazz).apply {
            putExtra("server", config.serverUrl)
            putExtra("username", config.username)
            putExtra("password", config.password)
            mode?.let { putExtra("mode", it) }
        })
    }

    private fun clearProviderCache() {
        LiveTvCache.clear(this)
        EpgCache.clear(this)
    }

    private fun goToLogin() {
        startActivity(Intent(this, LoginActivity::class.java).apply {
            putExtra("forceLogin", true)
        })
        finish()
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if (keyCode == KeyEvent.KEYCODE_MENU) {
            clearProviderCache()
            SessionStore.clear(this)
            goToLogin()
            return true
        }
        return super.onKeyDown(keyCode, event)
    }

    override fun onDestroy() {
        executor.shutdownNow()
        super.onDestroy()
    }
}
