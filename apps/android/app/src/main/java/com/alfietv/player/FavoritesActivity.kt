package com.alfietv.player

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.Gravity
import android.view.KeyEvent
import android.widget.ArrayAdapter
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.TextView
import java.util.concurrent.Executors

/** Dedicated favorites screen for TV-first navigation. */
class FavoritesActivity : androidx.activity.ComponentActivity() {
    private val executor = Executors.newSingleThreadExecutor()
    private lateinit var list: ListView
    private lateinit var status: TextView
    private lateinit var config: XtreamConfig
    private var favorites = emptySet<String>()
    private var channels = emptyList<IptvChannel>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        config = XtreamConfig(
            intent.getStringExtra("server") ?: "",
            intent.getStringExtra("username") ?: "",
            intent.getStringExtra("password") ?: ""
        )
        val prefs = getSharedPreferences("alfie_tv", Context.MODE_PRIVATE)
        favorites = prefs.getStringSet("favorites", emptySet()) ?: emptySet()

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24, 20, 24, 20)
        }
        root.addView(TextView(this).apply {
            text = "Favorites"
            textSize = 28f
            gravity = Gravity.CENTER_VERTICAL
        }, LinearLayout.LayoutParams(-1, -2))
        status = TextView(this).apply { text = "Loading favorites…"; textSize = 14f }
        list = ListView(this).apply {
            isFocusable = true
            isFocusableInTouchMode = true
            setOnKeyListener { _, keyCode, event ->
                if (event.action == KeyEvent.ACTION_DOWN && keyCode == KeyEvent.KEYCODE_CHANNEL_UP) {
                    moveSelection(-1); true
                } else if (event.action == KeyEvent.ACTION_DOWN && keyCode == KeyEvent.KEYCODE_CHANNEL_DOWN) {
                    moveSelection(1); true
                } else false
            }
        }
        root.addView(list, LinearLayout.LayoutParams(-1, 0, 1f))
        root.addView(status, LinearLayout.LayoutParams(-1, -2))
        setContentView(root)

        list.setOnItemClickListener { _, _, position, _ ->
            play(favoriteChannels().getOrNull(position) ?: return@setOnItemClickListener)
        }
        load()
    }

    private fun load() = executor.execute {
        try {
            val loaded = XtreamClient().load(config).second
            channels = loaded
            runOnUiThread { render() }
        } catch (e: Exception) {
            runOnUiThread { status.text = "Unable to load favorites: ${e.message ?: "unknown error"}" }
        }
    }

    private fun favoriteChannels(): List<IptvChannel> = channels.filter { favorites.contains(it.id) }

    private fun render() {
        val items = favoriteChannels()
        list.adapter = ArrayAdapter(
            this,
            android.R.layout.simple_list_item_1,
            items.mapIndexed { index, channel -> "${index + 1}. ★ ${channel.name}" }
        )
        status.text = if (items.isEmpty()) "No favorites yet • Long-press a Live TV channel to add one" else "${items.size} favorite channels"
        list.requestFocus()
    }

    private fun moveSelection(delta: Int) {
        val count = list.adapter?.count ?: return
        if (count == 0) return
        val next = (list.selectedItemPosition + delta).coerceIn(0, count - 1)
        list.setSelection(next)
    }

    private fun play(channel: IptvChannel) {
        val items = favoriteChannels()
        val index = items.indexOfFirst { it.id == channel.id }.coerceAtLeast(0)
        startActivity(Intent(this, MainActivity::class.java).apply {
            putExtra("stream_url", channel.streamUrl)
            putExtra("title", channel.name)
            putExtra("channel_number", (index + 1).toString())
            putExtra("channel_index", index)
            putExtra("channel_urls", ArrayList(items.map { it.streamUrl }))
            putExtra("channel_titles", ArrayList(items.map { it.name }))
            putExtra("channel_ids", ArrayList(items.map { it.id }))
            putExtra("channel_numbers", ArrayList(items.mapIndexed { i, _ -> (i + 1).toString() }))
        })
    }

    override fun onDestroy() {
        executor.shutdownNow()
        super.onDestroy()
    }
}
