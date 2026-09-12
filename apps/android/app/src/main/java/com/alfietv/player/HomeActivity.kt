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

/** Modern TV-first Alfie TV dashboard with remote-friendly navigation. */
class HomeActivity : androidx.activity.ComponentActivity() {
    private lateinit var config: XtreamConfig
    private val executor = Executors.newSingleThreadExecutor()
    private var status: TextView? = null

    private val bg = Color.rgb(4, 7, 15)
    private val panel = Color.rgb(13, 19, 32)
    private val panel2 = Color.rgb(20, 28, 45)
    private val blue = Color.rgb(35, 168, 255)
    private val purple = Color.rgb(122, 82, 255)
    private val cyan = Color.rgb(53, 224, 220)
    private val white = Color.WHITE
    private val muted = Color.rgb(157, 170, 193)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = bg
        window.navigationBarColor = bg
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
            setBackgroundColor(bg)
            setPadding(30, 18, 30, 14)
        }

        // Compact premium header.
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(4, 0, 4, 14)
        }
        val brand = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        brand.addView(TextView(this).apply {
            text = "ALFIE TV"
            textSize = 30f
            setTextColor(white)
            typeface = Typeface.DEFAULT_BOLD
            letterSpacing = 0.10f
        })
        brand.addView(TextView(this).apply {
            text = "YOUR ENTERTAINMENT HUB"
            textSize = 9f
            setTextColor(cyan)
            letterSpacing = 0.16f
            setPadding(0, 1, 0, 0)
        })
        header.addView(brand, LinearLayout.LayoutParams(0, -2, 1f))
        status = TextView(this).apply {
            text = "●  CONNECTED"
            textSize = 10f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            setTextColor(Color.rgb(120, 235, 175))
            background = pill(Color.rgb(16, 55, 49))
            setPadding(16, 8, 16, 8)
        }
        header.addView(status)
        root.addView(header)

        val scroll = ScrollView(this).apply {
            isFillViewport = true
            isFocusable = false
            overScrollMode = View.OVER_SCROLL_IF_CONTENT_SCROLLS
        }
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, 0, 0, 10)
        }
        scroll.addView(content)

        // Hero area: the first thing users see.
        val hero = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(26, 20, 26, 20)
            background = gradient(Color.rgb(78, 48, 180), Color.rgb(15, 72, 105), 24f)
        }
        hero.addView(TextView(this).apply {
            text = "WELCOME BACK"
            textSize = 10f
            setTextColor(Color.rgb(214, 224, 255))
            typeface = Typeface.DEFAULT_BOLD
            letterSpacing = 0.14f
        })
        hero.addView(TextView(this).apply {
            text = "What do you want to watch?"
            textSize = 24f
            setTextColor(white)
            typeface = Typeface.DEFAULT_BOLD
            setPadding(0, 5, 0, 2)
        })
        hero.addView(TextView(this).apply {
            text = "Live channels, TV Guide, movies and series — designed for your TV remote."
            textSize = 12f
            setTextColor(Color.rgb(226, 233, 248))
        })
        content.addView(hero, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = 14 })

        addSection(content, "WATCH NOW", "Jump straight into live entertainment")
        val liveRow = row(content, 94)
        val live = card(liveRow, "LIVE TV", "Channels  •  Favorites", blue) {
            open(LiveTvActivity::class.java)
        }
        card(liveRow, "TV GUIDE", "NOW  •  NEXT  •  Schedule", purple) {
            open(EpgGuideActivity::class.java)
        }

        addSection(content, "ON DEMAND", "Explore movies and series from your provider")
        val vodRow = row(content, 86)
        card(vodRow, "MOVIES", "Cinema  •  New releases", Color.rgb(226, 89, 137)) {
            open(ContentActivity::class.java, "vod")
        }
        card(vodRow, "SERIES", "Shows  •  Seasons  •  Episodes", Color.rgb(45, 190, 151)) {
            open(ContentActivity::class.java, "series")
        }

        addSection(content, "MY SPACE", "Keep your favorite entertainment close")
        val libraryRow = row(content, 86)
        card(libraryRow, "FAVORITES", "Saved channels and content", Color.rgb(244, 174, 65)) {
            open(FavoritesActivity::class.java)
        }
        card(libraryRow, "RECENT", "Continue your recent viewing", Color.rgb(92, 139, 255)) {
            open(FavoritesActivity::class.java)
        }

        addSection(content, "CONTROL CENTER", "Connection and playback settings")
        val toolsRow = row(content, 78)
        card(toolsRow, "SETTINGS", "Player  •  Display  •  Playback", Color.rgb(108, 123, 146)) {
            open(SettingsActivity::class.java)
        }
        card(toolsRow, "REFRESH", "Update channels and TV Guide", cyan) {
            refreshProvider()
        }
        card(content, "CHANGE PROVIDER", "Sign out and connect another IPTV provider", Color.rgb(86, 96, 120)) {
            clearProviderCache()
            SessionStore.clear(this)
            goToLogin()
        }

        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))

        val footer = TextView(this).apply {
            text = "← → Move   •   ↑ ↓ Browse   •   OK Select   •   BACK Return"
            textSize = 10f
            gravity = Gravity.CENTER
            setTextColor(muted)
            setPadding(0, 7, 0, 0)
            isFocusable = false
        }
        root.addView(footer, LinearLayout.LayoutParams(-1, -2))

        setContentView(root)
        root.post { live.requestFocus() }
    }

    private fun addSection(root: LinearLayout, title: String, subtitle: String) {
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(5, 5, 5, 6)
            isFocusable = false
        }
        box.addView(TextView(this).apply {
            text = title
            textSize = 12f
            setTextColor(white)
            typeface = Typeface.DEFAULT_BOLD
            letterSpacing = 0.10f
        })
        box.addView(TextView(this).apply {
            text = subtitle
            textSize = 10f
            setTextColor(muted)
            setPadding(0, 2, 0, 0)
        })
        root.addView(box)
    }

    private fun row(root: LinearLayout, height: Int): LinearLayout {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, 0, 0, 2)
        }
        root.addView(row, LinearLayout.LayoutParams(-1, height))
        return row
    }

    private fun card(root: LinearLayout, title: String, subtitle: String, accent: Int, action: () -> Unit): Button {
        val button = Button(this).apply {
            text = "$title\n$subtitle"
            isAllCaps = false
            textSize = 12f
            minHeight = 0
            gravity = Gravity.CENTER_VERTICAL or Gravity.START
            setPadding(20, 0, 12, 0)
            setTextColor(white)
            background = cardBackground(panel)
            isFocusable = true
            isFocusableInTouchMode = true
            stateListAnimator = null
            setOnFocusChangeListener { view, focused ->
                view.background = if (focused) focusedBackground(accent) else cardBackground(panel)
                animateFocus(view, focused)
                if (focused) ensureVisible(view)
            }
            setOnClickListener { action() }
        }
        root.addView(button, LinearLayout.LayoutParams(0, -1, 1f).apply {
            leftMargin = 3
            rightMargin = 3
        })
        return button
    }

    private fun card(root: LinearLayout, title: String, subtitle: String, accent: Int, action: () -> Unit) {
        val button = card(root, title, subtitle, accent, action)
        button.layoutParams = (button.layoutParams as LinearLayout.LayoutParams).apply {
            width = -1
            weight = 0f
        }
    }

    private fun animateFocus(view: View, focused: Boolean) {
        val target = if (focused) 1.025f else 1f
        ObjectAnimator.ofFloat(view, View.SCALE_X, view.scaleX, target).setDuration(130).start()
        ObjectAnimator.ofFloat(view, View.SCALE_Y, view.scaleY, target).setDuration(130).start()
    }

    private fun ensureVisible(view: View) {
        val scroll = view.parent?.parent?.parent as? ScrollView
        scroll?.post { scroll.smoothScrollTo(0, (view.top - 80).coerceAtLeast(0)) }
    }

    private fun refreshProvider() {
        status?.text = "↻  REFRESHING…"
        executor.execute {
            try {
                val (_, channels) = XtreamClient().load(config)
                val categories = XtreamClient().load(config).first
                LiveTvCache.write(this, config, categories, channels)
                runOnUiThread { status?.text = "●  ${channels.size} CHANNELS READY" }
            } catch (_: Exception) {
                runOnUiThread { status?.text = "⚠  REFRESH FAILED" }
            }
        }
    }

    private fun cardBackground(color: Int) = GradientDrawable().apply {
        setColor(color)
        cornerRadius = 18f * resources.displayMetrics.density
    }

    private fun focusedBackground(accent: Int) = GradientDrawable().apply {
        setColor(Color.rgb(25, 35, 54))
        setStroke((2 * resources.displayMetrics.density).toInt(), accent)
        cornerRadius = 18f * resources.displayMetrics.density
    }

    private fun pill(color: Int) = GradientDrawable().apply {
        setColor(color)
        cornerRadius = 24f * resources.displayMetrics.density
    }

    private fun gradient(start: Int, end: Int, radiusDp: Float) = GradientDrawable(
        GradientDrawable.Orientation.TL_BR,
        intArrayOf(start, end)
    ).apply { cornerRadius = radiusDp * resources.displayMetrics.density }

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
        startActivity(Intent(this, LoginActivity::class.java).apply { putExtra("forceLogin", true) })
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
