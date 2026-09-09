package com.alfietv.player

import android.content.Context
import android.os.Bundle
import android.view.Gravity
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.TextView
import androidx.activity.ComponentActivity
import java.text.DateFormat
import java.util.Date
import java.util.concurrent.Executors

class LiveTvActivity : ComponentActivity() {
    private val executor = Executors.newSingleThreadExecutor()
    private lateinit var list: ListView
    private lateinit var status: TextView
    private lateinit var epg: TextView
    private lateinit var search: EditText
    private var allChannels = emptyList<IptvChannel>()
    private var selectedCategory: String? = null
    private lateinit var config: XtreamConfig
    private lateinit var prefs: android.content.SharedPreferences
    private val favorites = mutableSetOf<String>()
    private var restoredLastChannel = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        config = XtreamConfig(intent.getStringExtra("server") ?: "", intent.getStringExtra("username") ?: "", intent.getStringExtra("password") ?: "")
        prefs = getSharedPreferences("alfie_tv", Context.MODE_PRIVATE)
        favorites.addAll(prefs.getStringSet("favorites", emptySet()) ?: emptySet())

        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(20, 16, 20, 16) }
        val header = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        val title = TextView(this).apply { text = "Live TV"; textSize = 26f }
        search = EditText(this).apply { hint = "Search channels"; singleLine = true; isFocusable = true }
        header.addView(title, LinearLayout.LayoutParams(0, -2, 1f))
        header.addView(search, LinearLayout.LayoutParams(0, -2, 2f))

        val categoryScroll = HorizontalScrollView(this)
        val categoryRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        categoryScroll.addView(categoryRow)
        epg = TextView(this).apply { text = "Select a channel to view program information"; textSize = 16f; setPadding(8, 10, 8, 10) }
        list = ListView(this).apply { isFocusable = true; isFocusableInTouchMode = true }
        status = TextView(this).apply { text = "Loading channels..."; textSize = 14f }

        root.addView(header)
        root.addView(categoryScroll, LinearLayout.LayoutParams(-1, -2))
        root.addView(epg, LinearLayout.LayoutParams(-1, -2))
        root.addView(list, LinearLayout.LayoutParams(-1, 0, 1f))
        root.addView(status)
        setContentView(root)

        search.setOnEditorActionListener { _, _, _ -> render(); false }
        list.setOnItemClickListener { _, _, position, _ -> play(filteredChannels()[position]) }
        list.setOnItemLongClickListener { _, _, position, _ ->
            val channel = filteredChannels()[position]
            if (!favorites.add(channel.id)) favorites.remove(channel.id)
            prefs.edit().putStringSet("favorites", favorites).apply()
            render(); true
        }
        load(categoryRow)
    }

    private fun load(categoryRow: LinearLayout) {
        executor.execute {
            try {
                val (categories, channels) = XtreamClient().load(config)
                runOnUiThread {
                    allChannels = channels
                    categories.forEach { category ->
                        categoryRow.addView(Button(this).apply {
                            text = category.name
                            isAllCaps = false
                            setOnClickListener { selectedCategory = category.id; render() }
                        })
                    }
                    render()
                    if (!restoredLastChannel) {
                        restoredLastChannel = true
                        prefs.getString("last_channel_id", null)?.let { id ->
                            channels.firstOrNull { it.id == id }?.let { showEpg(it) }
                        }
                    }
                }
            } catch (e: Exception) {
                runOnUiThread { status.text = "Unable to load channels: ${e.message ?: "unknown error"}" }
            }
        }
    }

    private fun filteredChannels(): List<IptvChannel> {
        val query = search.text.toString().trim().lowercase()
        return allChannels.filter { channel ->
            (selectedCategory == null || channel.categoryId == selectedCategory) &&
                (query.isBlank() || channel.name.lowercase().contains(query))
        }
    }

    private fun render() {
        val channels = filteredChannels()
        list.adapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, channels.mapIndexed { index, channel ->
            val number = index + 1
            val star = if (favorites.contains(channel.id)) "★ " else ""
            "$number  $star${channel.name}"
        })
        status.text = "${channels.size} channels • Long-press to favorite"
        list.requestFocus()
    }

    private fun play(channel: IptvChannel) {
        prefs.edit().putString("last_channel_id", channel.id).apply()
        showEpg(channel)
        startActivity(android.content.Intent(this, MainActivity::class.java).apply {
            putExtra("stream_url", channel.streamUrl)
            putExtra("title", channel.name)
        })
    }

    private fun showEpg(channel: IptvChannel) {
        epg.text = "${channel.name}\nLoading program guide..."
        executor.execute {
            try {
                val programs = XtreamClient().loadEpg(config, channel)
                val now = System.currentTimeMillis()
                val current = programs.firstOrNull { now in it.startUtcMs until it.endUtcMs }
                val next = programs.firstOrNull { it.startUtcMs > now }
                val text = buildString {
                    append(channel.name)
                    append("\n")
                    append(current?.let { "NOW  ${it.title}  ${formatTime(it.startUtcMs)}–${formatTime(it.endUtcMs)}" } ?: "NOW  No current program")
                    append("\n")
                    append(next?.let { "NEXT ${it.title}  ${formatTime(it.startUtcMs)}" } ?: "NEXT No upcoming program")
                }
                runOnUiThread { epg.text = text }
            } catch (e: Exception) {
                runOnUiThread { epg.text = "${channel.name}\nEPG unavailable" }
            }
        }
    }

    private fun formatTime(ms: Long): String = DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(ms))

    override fun onDestroy() { executor.shutdownNow(); super.onDestroy() }
}
