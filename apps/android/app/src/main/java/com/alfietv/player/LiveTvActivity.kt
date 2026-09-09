package com.alfietv.player

import android.content.Context
import android.os.Bundle
import android.view.Gravity
import android.view.KeyEvent
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
        categoryRow.addView(Button(this).apply {
            text = "All"
            isAllCaps = false
            setOnClickListener { selectedCategory = null; render() }
        })
        categoryScroll.addView(categoryRow)
        epg = TextView(this).apply { text = "Select a channel to view program information"; textSize = 16f; setPadding(8, 10, 8, 10) }
        list = ListView(this).apply {
            isFocusable = true
            isFocusableInTouchMode = true
            setOnKeyListener { _, keyCode, event ->
                if (event.action == KeyEvent.ACTION_DOWN && keyCode == KeyEvent.KEYCODE_CHANNEL_UP) { moveChannel(-1); true }
                else if (event.action == KeyEvent.ACTION_DOWN && keyCode == KeyEvent.KEYCODE_CHANNEL_DOWN) { moveChannel(1); true }
                else false
            }
        }
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
                        prefs.getString("last_channel_id", null)?.let { id -> channels.firstOrNull { it.id == id }?.let { showEpg(it) } }
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
            val star = if (favorites.contains(channel.id)) "★ " else ""
            "${index + 1}  $star${channel.name}"
        })
        status.text = "${channels.size} channels • Long-press to favorite • CH+/CH− supported"
        list.requestFocus()
    }

    private fun moveChannel(delta: Int) {
        val count = list.adapter?.count ?: return
        if (count == 0) return
        val next = (list.selectedItemPosition + delta).coerceIn(0, count - 1)
        list.setSelection(next)
        filteredChannels().getOrNull(next)?.let { showEpg(it) }
    }

    private fun play(channel: IptvChannel) {
        val channels = filteredChannels()
        val index = channels.indexOfFirst { it.id == channel.id }.coerceAtLeast(0)
        val windowStart = (index - 50).coerceAtLeast(0)
        val windowEnd = (index + 51).coerceAtMost(channels.size)
        val window = channels.subList(windowStart, windowEnd)
        prefs.edit().putString("last_channel_id", channel.id).apply()
        showEpg(channel)
        startActivity(android.content.Intent(this, MainActivity::class.java).apply {
            putExtra("stream_url", channel.streamUrl)
            putExtra("title", channel.name)
            putExtra("channel_number", (index + 1).toString())
            putExtra("channel_index", index - windowStart)
            putExtra("channel_urls", ArrayList(window.map { it.streamUrl }))
            putExtra("channel_titles", ArrayList(window.map { it.name }))
            putExtra("channel_ids", ArrayList(window.map { it.id }))
            putExtra("channel_numbers", ArrayList(window.mapIndexed { i, _ -> (windowStart + i + 1).toString() }))
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
                val nowText = current?.let { "NOW  ${it.title}  ${formatTime(it.startUtcMs)}–${formatTime(it.endUtcMs)}" } ?: "NOW  No current program"
                val nextText = next?.let { "NEXT ${it.title}  ${formatTime(it.startUtcMs)}" } ?: "NEXT No upcoming program"
                val progress = current?.let {
                    val duration = (it.endUtcMs - it.startUtcMs).coerceAtLeast(1L)
                    ((now - it.startUtcMs).coerceIn(0L, duration) * 100 / duration).toInt()
                } ?: 0
                val text = "$nowText\n$nextText\nProgress: $progress%"
                prefs.edit().putString("epg_${channel.id}", text).apply()
                runOnUiThread { epg.text = "${channel.name}\n$text" }
            } catch (_: Exception) {
                val text = "NOW  No current program\nNEXT No upcoming program\nProgress: 0%"
                prefs.edit().putString("epg_${channel.id}", text).apply()
                runOnUiThread { epg.text = "${channel.name}\nEPG unavailable" }
            }
        }
    }

    private fun formatTime(ms: Long): String = DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(ms))

    override fun onDestroy() { executor.shutdownNow(); super.onDestroy() }
}
