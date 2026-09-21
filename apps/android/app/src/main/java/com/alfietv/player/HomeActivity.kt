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
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import java.util.concurrent.Executors

/** Tivi-inspired TV-first dashboard with adaptive navigation for phone, tablet and TV. */
class HomeActivity : androidx.activity.ComponentActivity() {
    private lateinit var config: XtreamConfig
    private val executor = Executors.newSingleThreadExecutor()
    private var status: TextView? = null

    private val skin get() = SkinStore.current(this)
    private val bg get() = skin.background
    private val rail get() = skin.surface
    private val panel get() = skin.surface
    private val panel2 get() = skin.surface2
    private val accent get() = skin.accent
    private val purple get() = skin.secondary
    private val cyan get() = skin.current
    private val white = Color.WHITE
    private var renderedSkinId: String? = null
    private var homeLiveCard: View? = null
    private val muted = Color.rgb(153, 166, 188)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        SkinStore.applyWindow(this)
        renderedSkinId = skin.id
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

    override fun onResume() {
        super.onResume()
        SkinStore.applyWindow(this)
        val selected = skin.id
        if (renderedSkinId != null && renderedSkinId != selected) {
            renderedSkinId = selected
            if (::config.isInitialized && config.serverUrl.isNotBlank() && config.username.isNotBlank() && config.password.isNotBlank()) buildHome()
        } else {
            // Restore the primary TV landing focus after returning from Live TV, Guide, or fullscreen.
            homeLiveCard?.post { if (!isFinishing) homeLiveCard?.requestFocus() }
        }
    }

    private fun buildHome() {
        val widthDp = resources.configuration.screenWidthDp
        val heightDp = resources.configuration.screenHeightDp
        val compact = widthDp < 600
        val tablet = widthDp in 600..899
        val wide = widthDp >= 900
        val contentHorizontalPadding = when {
            widthDp < 360 -> 10
            compact -> 16
            tablet -> 24
            else -> 32
        }
        val contentTopPadding = when {
            heightDp < 480 -> 12
            compact -> 20
            else -> 24
        }
        val root = LinearLayout(this).apply {
            orientation = if (compact) LinearLayout.VERTICAL else LinearLayout.HORIZONTAL
            setBackgroundColor(bg)
        }

        val navigation = LinearLayout(this).apply {
            orientation = if (compact) LinearLayout.HORIZONTAL else LinearLayout.VERTICAL
            gravity = if (compact) Gravity.CENTER else Gravity.TOP
            setPadding(if (compact) 6 else 14, if (compact) 6 else 18, if (compact) 6 else 14, if (compact) 6 else 16)
            setBackgroundColor(rail)
        }
        navigation.addView(TextView(this).apply {
            text = "ALFIE\nTV"
            textSize = 22f
            setTextColor(white)
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            letterSpacing = 0.08f
            setPadding(0, 0, 0, 24)
            visibility = if (compact) View.GONE else View.VISIBLE
        }, LinearLayout.LayoutParams(if (compact) 0 else 94, if (compact) 0 else 0, if (compact) 0f else 0.22f))

        val navHome = navButton(navigation, "⌂", "HOME", true) { buildHome() }
        navButton(navigation, "▣", "LIVE TV") { open(LiveTvActivity::class.java) }
        navButton(navigation, "GUIDE", "TV GUIDE") { open(SafeEpgGuideActivity::class.java) }
        navButton(navigation, "●", "MOVIES") { open(ContentActivity::class.java, "vod") }
        navButton(navigation, "▶", "SERIES") { open(ContentActivity::class.java, "series") }
        navButton(navigation, "★", "FAVORITES") { open(FavoritesActivity::class.java) }
        navButton(navigation, "⚙", "SETTINGS") { open(SettingsActivity::class.java) }
        if (!compact) {
            navigation.addView(View(this), LinearLayout.LayoutParams(1, 0, 1f))
            status = TextView(this).apply {
                text = "● ONLINE"
                textSize = 9f
                gravity = Gravity.CENTER
                setTextColor(cyan)
            }
            navigation.addView(status, LinearLayout.LayoutParams(-1, 36))
        }
        if (compact) {
            val navScroll = HorizontalScrollView(this).apply {
                isHorizontalScrollBarEnabled = false
                overScrollMode = View.OVER_SCROLL_IF_CONTENT_SCROLLS
                setPadding(4, 0, 4, 0)
                addView(navigation, LinearLayout.LayoutParams(-2, 72))
            }
            root.addView(navScroll, LinearLayout.LayoutParams(-1, 72))
        } else {
            root.addView(navigation, LinearLayout.LayoutParams(if (tablet) 132 else if (wide) 144 else 122, -1))
        }

        val scroll = ScrollView(this).apply {
            isFillViewport = true
            overScrollMode = View.OVER_SCROLL_IF_CONTENT_SCROLLS
        }
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(contentHorizontalPadding, contentTopPadding, contentHorizontalPadding, if (compact) 24 else 28)
        }
        scroll.addView(content)
        root.addView(scroll, if (compact) LinearLayout.LayoutParams(-1, 0, 1f) else LinearLayout.LayoutParams(0, -1, 1f))

        val top = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(2, 0, 2, 16)
        }
        top.addView(TextView(this).apply {
            text = "HOME"
            textSize = when { widthDp < 360 -> 19f; compact -> 21f; tablet -> 23f; else -> 25f }
            setTextColor(white)
            typeface = Typeface.DEFAULT_BOLD
        }, LinearLayout.LayoutParams(0, -2, 1f))
        top.addView(Button(this).apply {
            text = "⌕  Search"
            textSize = if (compact) 11f else 12f
            isAllCaps = false
            setTextColor(white)
            background = rounded(panel)
            isFocusable = true
            isFocusableInTouchMode = true
            stateListAnimator = null
            setPadding(18, 0, 18, 0)
            contentDescription = "Search channels"
            setOnFocusChangeListener { v, focused ->
                v.background = if (focused) focusedCard(accent) else rounded(panel)
                animateFocus(v, focused)
            }
            setOnClickListener {
                startActivity(Intent(this@HomeActivity, LiveTvActivity::class.java).apply {
                    putExtra("server", config.serverUrl)
                    putExtra("username", config.username)
                    putExtra("password", config.password)
                    putExtra("focus_search", true)
                })
            }
        }, LinearLayout.LayoutParams(-2, if (compact) 48 else 50))
        content.addView(top)

        val hero = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(28, 24, 28, 24)
            background = gradient(skin.secondary, skin.current)
        }
        hero.addView(TextView(this).apply {
            text = "WELCOME BACK"
            textSize = 10f
            setTextColor(Color.WHITE)
            typeface = Typeface.DEFAULT_BOLD
            letterSpacing = 0.16f
        })
        hero.addView(TextView(this).apply {
            text = "Watch what you love."
            textSize = 27f
            setTextColor(white)
            typeface = Typeface.DEFAULT_BOLD
            setPadding(0, 5, 0, 4)
        })
        hero.addView(TextView(this).apply {
            text = "Live TV • Guide • Movies • Series"
            textSize = 12f
            setTextColor(Color.WHITE)
        })
        content.addView(hero, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = 18 })

        section(content, "LIVE TV", "Jump into your channels")
        val liveRow = row(content, when { widthDp < 360 -> 96; compact -> 104; tablet -> 112; else -> 124 })
        val live = card(liveRow, "LIVE TV", "Channels and categories", accent) { open(LiveTvActivity::class.java) }
        homeLiveCard = live
        card(liveRow, "TV GUIDE", "Now • Next • Full schedule", purple) { open(SafeEpgGuideActivity::class.java) }
        live.setOnKeyListener { _, keyCode, event ->
            if (event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0 &&
                (keyCode == KeyEvent.KEYCODE_DPAD_UP || keyCode == KeyEvent.KEYCODE_DPAD_LEFT)) {
                navHome.requestFocus()
                true
            } else false
        }
        live.requestFocus()

        section(content, "CONTINUE WATCHING", "Pick up where you left off")
        val recent = row(content, if (compact) 92 else if (tablet) 100 else 108)
        card(recent, "RECENTLY WATCHED", "Continue your latest channels", accent) { open(FavoritesActivity::class.java) }
        card(recent, "FAVORITES", "Your saved channels and content", purple) { open(FavoritesActivity::class.java) }

        section(content, "ON DEMAND", "Browse your provider catalog")
        val vod = row(content, if (compact) 92 else if (tablet) 100 else 108)
        card(vod, "MOVIES", "Cinema • New releases", skin.secondary) { open(ContentActivity::class.java, "vod") }
        card(vod, "SERIES", "Shows • Seasons • Episodes", cyan) { open(ContentActivity::class.java, "series") }

        section(content, "QUICK ACTIONS", "Everything important in one place")
        val tools = row(content, 80)
        card(tools, "REFRESH", "Update channels and guide", cyan) { refreshProvider() }
        card(tools, "CHANGE PROVIDER", "Connect another IPTV service", muted) {
            clearProviderCache(); SessionStore.clear(this); goToLogin()
        }

        content.addView(TextView(this).apply {
            text = "↑ ↓ Navigate   •   OK Select   •   BACK Return   •   MENU Provider"
            textSize = 10f
            gravity = Gravity.CENTER
            setTextColor(muted)
            setPadding(0, 16, 0, 0)
        })

        setContentView(root)
        SkinStore.animate(hero, skin)
        // Keep the TV-first landing focus on Live TV after the hierarchy is attached.
        root.post { live.requestFocus() }
    }

    private fun section(root: LinearLayout, title: String, subtitle: String) {
        root.addView(TextView(this).apply {
            text = title
            textSize = 12f
            setTextColor(white)
            typeface = Typeface.DEFAULT_BOLD
            letterSpacing = 0.10f
            setPadding(3, 2, 3, 2)
        })
        root.addView(TextView(this).apply {
            text = subtitle
            textSize = 10f
            setTextColor(muted)
            setPadding(3, 0, 3, 7)
        })
    }

    private fun row(root: LinearLayout, height: Int): LinearLayout {
        val compact = resources.configuration.screenWidthDp < 600
        val r = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        if (compact) {
            val scroll = HorizontalScrollView(this).apply {
                isHorizontalScrollBarEnabled = false
                overScrollMode = View.OVER_SCROLL_IF_CONTENT_SCROLLS
                clipToPadding = false
                setPadding(2, 0, 2, 0)
                addView(r, LinearLayout.LayoutParams(-2, height))
            }
            root.addView(scroll, LinearLayout.LayoutParams(-1, height).apply { bottomMargin = 6 })
        } else {
            root.addView(r, LinearLayout.LayoutParams(-1, height).apply { bottomMargin = 6 })
        }
        return r
    }

    private fun navButton(root: LinearLayout, icon: String, title: String, selected: Boolean = false, action: () -> Unit): Button {
        val compact = resources.configuration.screenWidthDp < 600
        val b = Button(this).apply {
            text = "$icon\n$title"
            isAllCaps = false
            textSize = if (compact) 8f else 9f
            gravity = Gravity.CENTER
            setTextColor(if (selected) white else muted)
            background = if (selected) selectedNav(accent) else rounded(rail)
            isFocusable = true
            isFocusableInTouchMode = true
            stateListAnimator = null
            setOnFocusChangeListener { v, focused ->
                v.background = if (focused) selectedNav(accent) else if (selected) selectedNav(accent) else rounded(rail)
                if (focused) animateFocus(v, true)
            }
            setOnClickListener { action() }
        }
        if (compact) {
            val navWidth = (86 * resources.displayMetrics.density).toInt()
            root.addView(b, LinearLayout.LayoutParams(navWidth, -1).apply { leftMargin = 2; rightMargin = 2 })
        } else {
            root.addView(b, LinearLayout.LayoutParams(-1, 64).apply { bottomMargin = 4 })
        }
        return b
    }

    private fun card(root: LinearLayout, title: String, subtitle: String, color: Int, action: () -> Unit): Button {
        val b = Button(this).apply {
            text = "$title\n$subtitle"
            isAllCaps = false
            textSize = 12f
            gravity = Gravity.CENTER_VERTICAL or Gravity.START
            setPadding(20, 0, 12, 0)
            setTextColor(white)
            background = rounded(panel)
            isFocusable = true
            isFocusableInTouchMode = true
            stateListAnimator = null
            setOnFocusChangeListener { v, focused ->
                v.background = if (focused) focusedCard(color) else rounded(panel)
                animateFocus(v, focused)
                if (focused) {
                    var parent = v.parent
                    while (parent != null && parent !is HorizontalScrollView) {
                        parent = parent.parent
                    }
                    if (parent is HorizontalScrollView) {
                        val scroll = parent
                        val target = (v.left - (scroll.width - v.width) / 2).coerceAtLeast(0)
                        scroll.smoothScrollTo(target, 0)
                    }
                }
            }
            setOnClickListener { action() }
        }
        val compact = resources.configuration.screenWidthDp < 600
        if (compact) {
            val widthDp = if (resources.configuration.screenWidthDp < 360) 230 else 260
            root.addView(
                b,
                LinearLayout.LayoutParams(
                    (widthDp * resources.displayMetrics.density).toInt(), -1
                ).apply { leftMargin = 4; rightMargin = 4 }
            )
        } else {
            root.addView(b, LinearLayout.LayoutParams(0, -1, 1f).apply { leftMargin = 4; rightMargin = 4 })
        }
        return b
    }

    private fun animateFocus(view: View, focused: Boolean) {
        val target = if (focused) 1.035f else 1f
        ObjectAnimator.ofFloat(view, View.SCALE_X, view.scaleX, target).setDuration(120).start()
        ObjectAnimator.ofFloat(view, View.SCALE_Y, view.scaleY, target).setDuration(120).start()
    }

    private fun rounded(color: Int) = GradientDrawable().apply {
        setColor(color); cornerRadius = 16f * resources.displayMetrics.density
    }

    private fun focusedCard(color: Int) = GradientDrawable().apply {
        setColor(panel2); setStroke((2 * resources.displayMetrics.density).toInt(), color)
        cornerRadius = 16f * resources.displayMetrics.density
    }

    private fun selectedNav(color: Int) = GradientDrawable().apply {
        setColor(panel2); setStroke((1.5f * resources.displayMetrics.density).toInt(), color)
        cornerRadius = 14f * resources.displayMetrics.density
    }

    private fun gradient(start: Int, end: Int) = GradientDrawable(
        GradientDrawable.Orientation.TL_BR, intArrayOf(start, end)
    ).apply { cornerRadius = 20f * resources.displayMetrics.density }

    private fun open(clazz: Class<*>, mode: String? = null) {
        startActivity(Intent(this, clazz).apply {
            putExtra("server", config.serverUrl); putExtra("username", config.username); putExtra("password", config.password)
            mode?.let { putExtra("mode", it) }
        })
    }

    private fun refreshProvider() {
        status?.text = "↻ REFRESH"
        executor.execute {
            try {
                val (categories, channels) = XtreamClient().load(config)
                LiveTvCache.write(this, config, categories, channels)
                runOnUiThread { status?.text = "● ${channels.size} CH" }
            } catch (_: Exception) {
                runOnUiThread { status?.text = "⚠ OFFLINE" }
            }
        }
    }

    private fun clearProviderCache() { LiveTvCache.clear(this); EpgCache.clear(this) }

    private fun goToLogin() {
        startActivity(Intent(this, LoginActivity::class.java).apply { putExtra("forceLogin", true) }); finish()
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if (keyCode == KeyEvent.KEYCODE_MENU) { clearProviderCache(); SessionStore.clear(this); goToLogin(); return true }
        return super.onKeyDown(keyCode, event)
    }

    override fun onDestroy() { executor.shutdownNow(); super.onDestroy() }
}
