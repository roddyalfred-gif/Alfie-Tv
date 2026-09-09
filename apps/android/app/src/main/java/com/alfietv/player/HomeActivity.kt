package com.alfietv.player

import android.content.Intent
import android.os.Bundle
import android.view.Gravity
import android.view.KeyEvent
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView

/** TV-first application hub connecting the major IPTV sections. */
class HomeActivity : androidx.activity.ComponentActivity() {
    private lateinit var config: XtreamConfig

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        config = XtreamConfig(
            intent.getStringExtra("server") ?: "",
            intent.getStringExtra("username") ?: "",
            intent.getStringExtra("password") ?: ""
        )
        buildHome()
    }

    private fun buildHome() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(64, 36, 64, 36)
        }
        root.addView(TextView(this).apply {
            text = "Alfie TV"
            textSize = 38f
            gravity = Gravity.CENTER
        }, LinearLayout.LayoutParams(-1, -2))
        root.addView(TextView(this).apply {
            text = "Your IPTV entertainment hub"
            textSize = 18f
            gravity = Gravity.CENTER
            setPadding(0, 8, 0, 28)
        }, LinearLayout.LayoutParams(-1, -2))

        addButton(root, "Live TV") { open(LiveTvActivity::class.java) }
        addButton(root, "Movies") { open(ContentActivity::class.java, "vod") }
        addButton(root, "Series") { open(ContentActivity::class.java, "series") }
        addButton(root, "Favorites") { open(FavoritesActivity::class.java) }
        addButton(root, "Reconnect / Change Provider") {
            startActivity(Intent(this, LoginActivity::class.java))
        }
        setContentView(root)
        root.post { root.getChildAt(2)?.requestFocus() }
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

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if (keyCode == KeyEvent.KEYCODE_MENU) {
            startActivity(Intent(this, LoginActivity::class.java))
            return true
        }
        return super.onKeyDown(keyCode, event)
    }
}
