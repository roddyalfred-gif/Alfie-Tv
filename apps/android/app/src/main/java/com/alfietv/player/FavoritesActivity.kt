package com.alfietv.player

import android.content.Intent
import android.os.Bundle
import android.view.Gravity
import android.view.KeyEvent
import android.widget.ArrayAdapter
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.TextView

/** Unified provider-scoped Favorites and Recently Watched screen. */
class FavoritesActivity : androidx.activity.ComponentActivity() {
    private lateinit var list: ListView
    private lateinit var status: TextView
    private lateinit var config: XtreamConfig
    private var favorites = emptyList<UserLibraryStore.Item>()
    private var recent = emptyList<UserLibraryStore.Item>()
    private var showingRecent = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        config = XtreamConfig(intent.getStringExtra("server") ?: "", intent.getStringExtra("username") ?: "", intent.getStringExtra("password") ?: "")
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(24, 20, 24, 20) }
        val header = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        header.addView(TextView(this).apply { text = "Library"; textSize = 28f }, LinearLayout.LayoutParams(0, -2, 1f))
        val favoritesButton = TextView(this).apply { text = "FAVORITES"; textSize = 15f; isFocusable = true; isFocusableInTouchMode = true; setPadding(16, 12, 16, 12); setOnClickListener { showingRecent = false; render() } }
        val recentButton = TextView(this).apply { text = "RECENT"; textSize = 15f; isFocusable = true; isFocusableInTouchMode = true; setPadding(16, 12, 16, 12); setOnClickListener { showingRecent = true; render() } }
        header.addView(favoritesButton); header.addView(recentButton)
        status = TextView(this).apply { textSize = 14f; setPadding(0, 8, 0, 8) }
        list = ListView(this).apply { isFocusable = true; isFocusableInTouchMode = true }
        root.addView(header); root.addView(status, LinearLayout.LayoutParams(-1, -2)); root.addView(list, LinearLayout.LayoutParams(-1, 0, 1f)); setContentView(root)
        list.setOnItemClickListener { _, _, position, _ -> open((if (showingRecent) recent else favorites).getOrNull(position) ?: return@setOnItemClickListener) }
        list.setOnItemLongClickListener { _, _, position, _ -> if (!showingRecent) { favorites.getOrNull(position)?.let { UserLibraryStore.toggleFavorite(this, config, it); reload() }; true } else false }
        reload()
    }

    override fun onResume() { super.onResume(); if (::list.isInitialized) reload() }

    private fun reload() {
        favorites = UserLibraryStore.favorites(this, config)
        recent = UserLibraryStore.recent(this, config)
        render()
    }

    private fun render() {
        val items = if (showingRecent) recent else favorites
        list.adapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, items.mapIndexed { index, item ->
            val type = when (item.type) { UserLibraryStore.Type.LIVE -> "LIVE"; UserLibraryStore.Type.MOVIE -> "MOVIE"; UserLibraryStore.Type.SERIES -> "SERIES"; UserLibraryStore.Type.EPISODE -> "EPISODE" }
            "${index + 1}. ${if (!showingRecent) "★ " else ""}$type • ${item.title}"
        })
        status.text = if (items.isEmpty()) if (showingRecent) "Nothing watched yet" else "No favorites yet • Long-press an item to remove it" else if (showingRecent) "${items.size} recently watched" else "${items.size} favorites • Long-press to remove"
        list.requestFocus()
    }

    private fun open(item: UserLibraryStore.Item) {
        when (item.type) {
            UserLibraryStore.Type.SERIES -> startActivity(Intent(this, ContentActivity::class.java).apply { putExtra("server", config.serverUrl); putExtra("username", config.username); putExtra("password", config.password); putExtra("mode", "series") })
            else -> startActivity(Intent(this, MainActivity::class.java).apply {
                putExtra("stream_url", item.streamUrl); putExtra("title", item.title); putExtra("content_id", item.id); putExtra("content_type", item.type.name)
            })
        }
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if (keyCode == KeyEvent.KEYCODE_CHANNEL_UP || keyCode == KeyEvent.KEYCODE_CHANNEL_DOWN) return true
        return super.onKeyDown(keyCode, event)
    }
}
