package com.alfietv.player

import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.KeyEvent
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import java.util.concurrent.Executors

/** TV-first application hub connecting the major IPTV sections. */
class HomeActivity : androidx.activity.ComponentActivity() {
    private lateinit var config: XtreamConfig
    private val executor = Executors.newSingleThreadExecutor()
    private var status: TextView? = null
    private val backgroundColor = Color.rgb(8, 12, 22)
    private val surfaceColor = Color.rgb(18, 25, 40)
    private val accentColor = Color.rgb(0, 168, 255)
    private val textColor = Color.WHITE
    private val secondaryTextColor = Color.rgb(170, 181, 200)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = backgroundColor
        window.navigationBarColor = backgroundColor
        config = SessionStore.load(this) ?: XtreamConfig(intent.getStringExtra("server") ?: "", intent.getStringExtra("username") ?: "", intent.getStringExtra("password") ?: "")
        if (config.serverUrl.isBlank() || config.username.isBlank() || config.password.isBlank()) { goToLogin(); return }
        SessionStore.save(this, config)
        buildHome()
    }

    private fun buildHome() {
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER; setPadding(72, 30, 72, 30); setBackgroundColor(backgroundColor); isFocusable = false }
        root.addView(TextView(this).apply { text = "ALFIE TV"; textSize = 34f; gravity = Gravity.CENTER; setTextColor(accentColor); typeface = Typeface.DEFAULT_BOLD; letterSpacing = 0.1f }, LinearLayout.LayoutParams(-1, -2))
        root.addView(TextView(this).apply { text = "Choose what you want to watch"; textSize = 15f; gravity = Gravity.CENTER; setTextColor(secondaryTextColor); setPadding(0, 5, 0, 10) }, LinearLayout.LayoutParams(-1, -2))
        status = TextView(this).apply { gravity = Gravity.CENTER; textSize = 13f; text = "●  Provider connected"; setTextColor(secondaryTextColor) }
        root.addView(status, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = 8 })
        addSectionLabel(root, "LIVE & TV", "Live television and channel guide")
        val firstButton = addButton(root, "📺  Live TV") { open(LiveTvActivity::class.java) }
        addSectionLabel(root, "ON DEMAND", "Movies and TV series")
        addButton(root, "🎬  Movies") { open(ContentActivity::class.java, "vod") }
        addButton(root, "📺  Series") { open(ContentActivity::class.java, "series") }
        addSectionLabel(root, "MY LIBRARY", "Your saved and recently watched content")
        addButton(root, "★  Favorites") { open(FavoritesActivity::class.java) }
        addSectionLabel(root, "APP")
        addButton(root, "⚙  Settings") { open(SettingsActivity::class.java) }
        addSectionLabel(root, "PROVIDER", "Manage your IPTV connection")
        addButton(root, "↻  Refresh Provider") { refreshProvider() }
        addButton(root, "⚙  Change Provider / Logout") { clearProviderCache(); SessionStore.clear(this); goToLogin() }
        setContentView(root)
        root.post { firstButton.requestFocus() }
    }

    private fun addSectionLabel(root: LinearLayout, title: String, subtitle: String? = null) {
        val container = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(4, 12, 4, 2); isFocusable = false }
        container.addView(TextView(this).apply { text = title; textSize = 12f; setTextColor(accentColor); typeface = Typeface.DEFAULT_BOLD; letterSpacing = 0.08f })
        subtitle?.let { container.addView(TextView(this).apply { text = it; textSize = 11f; setTextColor(secondaryTextColor); setPadding(0, 2, 0, 0) }) }
        root.addView(container, LinearLayout.LayoutParams(-1, -2))
    }

    private fun refreshProvider() {
        status?.text = "Refreshing provider..."
        executor.execute { try { val (categories, channels) = XtreamClient().load(config); LiveTvCache.write(this, config, categories, channels); runOnUiThread { status?.text = "●  Provider refreshed • ${channels.size} live channels cached" } } catch (e: Exception) { runOnUiThread { status?.text = "Refresh failed: ${e.message ?: "unknown error"}" } } }
    }

    private fun addButton(root: LinearLayout, label: String, action: () -> Unit): Button {
        val button = Button(this).apply {
            text = label; isAllCaps = false; textSize = 16f; minHeight = 58; setTextColor(textColor); background = roundedBackground(surfaceColor, 14f); isFocusable = true; isFocusableInTouchMode = true; stateListAnimator = null
            setOnFocusChangeListener { view, focused -> view.background = roundedBackground(if (focused) accentColor else surfaceColor, 14f); (view as Button).setTextColor(Color.WHITE) }
            setOnClickListener { action() }
        }
        root.addView(button, LinearLayout.LayoutParams(-1, 58).apply { topMargin = 5 })
        return button
    }

    private fun roundedBackground(color: Int, radiusDp: Float): GradientDrawable = GradientDrawable().apply { setColor(color); cornerRadius = radiusDp * resources.displayMetrics.density }
    private fun open(clazz: Class<*>, mode: String? = null) { startActivity(Intent(this, clazz).apply { putExtra("server", config.serverUrl); putExtra("username", config.username); putExtra("password", config.password); mode?.let { putExtra("mode", it) } }) }
    private fun clearProviderCache() { LiveTvCache.clear(this); EpgCache.clear(this) }
    private fun goToLogin() { startActivity(Intent(this, LoginActivity::class.java).apply { putExtra("forceLogin", true) }); finish() }
    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean { if (keyCode == KeyEvent.KEYCODE_MENU) { clearProviderCache(); SessionStore.clear(this); goToLogin(); return true }; return super.onKeyDown(keyCode, event) }
    override fun onDestroy() { executor.shutdownNow(); super.onDestroy() }
}
