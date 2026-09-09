package com.alfietv.player

import android.content.Intent
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
    private var homeRoot: LinearLayout? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
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
            gravity = Gravity.CENTER
            setPadding(64, 36, 64, 36)
            isFocusable = false
        }
        homeRoot = root
        root.addView(TextView(this).apply {
            text = "Alfie TV"
            textSize = 38f
            gravity = Gravity.CENTER
            isFocusable = false
        }, LinearLayout.LayoutParams(-1, -2))
        root.addView(TextView(this).apply {
            text = "Your IPTV entertainment hub"
            textSize = 18f
            gravity = Gravity.CENTER
            setPadding(0, 8, 0, 16)
            isFocusable = false
        }, LinearLayout.LayoutParams(-1, -2))
        status = TextView(this).apply { gravity = Gravity.CENTER; textSize = 14f; text = "Provider connected"; isFocusable = false }
        root.addView(status, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = 8 })

        addButton(root, "Live TV") { open(LiveTvActivity::class.java) }
        addButton(root, "Movies") { open(ContentActivity::class.java, "vod") }
        addButton(root, "Series") { open(ContentActivity::class.java, "series") }
        addButton(root, "Favorites") { open(FavoritesActivity::class.java) }
        addButton(root, "Refresh Provider") { refreshProvider() }
        addButton(root, "Change Provider / Logout") {
            clearProviderCache()
            SessionStore.clear(this)
            goToLogin()
        }
        setContentView(root)
        root.post { root.getChildAt(3)?.requestFocus() }
    }

    private fun refreshProvider() {
        status?.text = "Refreshing provider..."
        executor.execute {
            try {
                val (categories, channels) = XtreamClient().load(config)
                LiveTvCache.write(this, config, categories, channels)
                runOnUiThread { status?.text = "Provider refreshed successfully • ${channels.size} live channels cached" }
            } catch (e: Exception) {
                runOnUiThread { status?.text = "Refresh failed: ${e.message ?: "unknown error"}" }
            }
        }
    }

    private fun addButton(root: LinearLayout, label: String, action: () -> Unit) {
        root.addView(Button(this).apply {
            text = label
            isAllCaps = false
            minHeight = 64
            isFocusable = true
            isFocusableInTouchMode = true
            setOnClickListener { action() }
        }, LinearLayout.LayoutParams(-1, -2).apply { topMargin = 8 })
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
        homeRoot = null
        super.onDestroy()
    }
}