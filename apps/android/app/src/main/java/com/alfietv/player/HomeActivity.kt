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

/** TV-first application hub with a responsive, scrollable and remote-friendly menu. */
class HomeActivity : androidx.activity.ComponentActivity() {
    private lateinit var config: XtreamConfig
    private val executor = Executors.newSingleThreadExecutor()
    private var status: TextView? = null
    private val backgroundColor = Color.rgb(5, 9, 18)
    private val surfaceColor = Color.rgb(15, 23, 38)
    private val surfaceAltColor = Color.rgb(21, 31, 50)
    private val accentColor = Color.rgb(0, 168, 255)
    private val textColor = Color.WHITE
    private val secondaryTextColor = Color.rgb(170, 181, 200)

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
            setPadding(42, 24, 42, 24)
            setBackgroundColor(backgroundColor)
        }

        val header = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            isFocusable = false
        }
        header.addView(TextView(this).apply {
            text = "ALFIE TV"
            textSize = 32f
            gravity = Gravity.CENTER
            setTextColor(accentColor)
            typeface = Typeface.DEFAULT_BOLD
            letterSpacing = 0.14f
        })
        header.addView(TextView(this).apply {
            text = "LIVE • MOVIES • SERIES • YOUR LIBRARY"
            textSize = 13f
            gravity = Gravity.CENTER
            setTextColor(secondaryTextColor)
            setPadding(0, 5, 0, 3)
        })
        status = TextView(this).apply {
            gravity = Gravity.CENTER
            textSize = 12f
            text = "●  Provider connected"
            setTextColor(secondaryTextColor)
        }
        header.addView(status)
        root.addView(header, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = 8 })

        val scroll = ScrollView(this).apply {
            isFillViewport = true
            isFocusable = false
            overScrollMode = View.OVER_SCROLL_IF_CONTENT_SCROLLS
        }
        val menu = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, 0, 0, 14)
        }
        scroll.addView(menu)

        addSectionLabel(menu, "WATCH NOW", "Jump straight into live television or on-demand content")
        val liveButton = addButton(menu, "📺  Live TV", "Channels • EPG • favorites • live playback") {
            open(LiveTvActivity::class.java)
        }
        addButton(menu, "🗓  TV Guide / EPG", "Browse the programme schedule and select a channel") {
            open(LiveTvActivity::class.java)
        }

        addSectionLabel(menu, "ON DEMAND", "Movies and TV series from your provider")
        addButton(menu, "🎬  Movies", "Browse, select and play your movie library") {
            open(ContentActivity::class.java, "vod")
        }
        addButton(menu, "📺  Series", "Browse shows, seasons and episodes") {
            open(ContentActivity::class.java, "series")
        }

        addSectionLabel(menu, "MY LIBRARY", "Keep your viewing easy to resume")
        addButton(menu, "★  Favorites", "Your saved channels and content") {
            open(FavoritesActivity::class.java)
        }
        addButton(menu, "◷  Recently Watched", "Jump back into your recent viewing") {
            open(FavoritesActivity::class.java)
        }

        addSectionLabel(menu, "PLAYER & APP", "Customize playback and application behaviour")
        addButton(menu, "⚙  Settings", "Player, display, playback and app settings") {
            open(SettingsActivity::class.java)
        }

        addSectionLabel(menu, "PROVIDER", "Keep channels, categories and guide data current")
        addButton(menu, "↻  Refresh Provider", "Update channels, categories and guide cache") {
            refreshProvider()
        }
        addButton(menu, "⇄  Change Provider / Logout", "Disconnect and sign in with another provider") {
            clearProviderCache()
            SessionStore.clear(this)
            goToLogin()
        }

        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))

        val footer = TextView(this).apply {
            text = "Use ↑ ↓ to navigate • OK / Enter to select • Back to return"
            textSize = 11f
            gravity = Gravity.CENTER
            setTextColor(secondaryTextColor)
            setPadding(0, 8, 0, 0)
            isFocusable = false
        }
        root.addView(footer, LinearLayout.LayoutParams(-1, -2))

        setContentView(root)
        root.post { liveButton.requestFocus() }
    }

    private fun addSectionLabel(root: LinearLayout, title: String, subtitle: String? = null) {
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(6, 12, 6, 4)
            isFocusable = false
        }
        container.addView(TextView(this).apply {
            text = title
            textSize = 12f
            setTextColor(accentColor)
            typeface = Typeface.DEFAULT_BOLD
            letterSpacing = 0.08f
        })
        subtitle?.let {
            container.addView(TextView(this).apply {
                text = it
                textSize = 11f
                setTextColor(secondaryTextColor)
                setPadding(0, 2, 0, 0)
            })
        }
        root.addView(container)
    }

    private fun addButton(root: LinearLayout, label: String, description: String, action: () -> Unit): Button {
        val button = Button(this).apply {
            text = "$label\n$description"
            isAllCaps = false
            textSize = 15f
            minHeight = 66
            gravity = Gravity.CENTER_VERTICAL or Gravity.START
            setPadding(24, 0, 20, 0)
            setTextColor(textColor)
            background = roundedBackground(surfaceColor, 14f)
            isFocusable = true
            isFocusableInTouchMode = true
            stateListAnimator = null
            setOnFocusChangeListener { view, focused ->
                view.background = roundedBackground(if (focused) accentColor else surfaceColor, 14f)
                (view as Button).setTextColor(Color.WHITE)
                animateFocus(view, focused)
                if (focused) ensureVisible(view)
            }
            setOnClickListener { action() }
        }
        root.addView(button, LinearLayout.LayoutParams(-1, 68).apply { topMargin = 5 })
        return button
    }

    private fun animateFocus(view: View, focused: Boolean) {
        val target = if (focused) 1.025f else 1f
        ObjectAnimator.ofFloat(view, View.SCALE_X, view.scaleX, target).setDuration(140).start()
        ObjectAnimator.ofFloat(view, View.SCALE_Y, view.scaleY, target).setDuration(140).start()
    }

    private fun ensureVisible(view: View) {
        val scroll = view.parent?.parent as? ScrollView
        scroll?.post { scroll.smoothScrollTo(0, (view.top - 90).coerceAtLeast(0)) }
    }

    private fun refreshProvider() {
        status?.text = "Refreshing provider…"
        executor.execute {
            try {
                val (categories, channels) = XtreamClient().load(config)
                LiveTvCache.write(this, config, categories, channels)
                runOnUiThread {
                    status?.text = "●  Provider refreshed • ${channels.size} live channels cached"
                }
            } catch (e: Exception) {
                runOnUiThread {
                    status?.text = "Refresh failed • ${e.message ?: "unknown error"}"
                }
            }
        }
    }

    private fun roundedBackground(color: Int, radiusDp: Float): GradientDrawable = GradientDrawable().apply {
        setColor(color)
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
