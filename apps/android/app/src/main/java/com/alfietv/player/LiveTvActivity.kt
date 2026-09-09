package com.alfietv.player

import android.os.Bundle
import android.view.Gravity
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.TextView
import androidx.activity.ComponentActivity
import java.util.concurrent.Executors

class LiveTvActivity : ComponentActivity() {
    private val executor = Executors.newSingleThreadExecutor()
    private lateinit var list: ListView
    private lateinit var status: TextView
    private lateinit var search: EditText
    private var allChannels = emptyList<IptvChannel>()
    private var selectedCategory: String? = null
    private lateinit var config: XtreamConfig
    private val favorites = mutableSetOf<String>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        config = XtreamConfig(intent.getStringExtra("server") ?: "", intent.getStringExtra("username") ?: "", intent.getStringExtra("password") ?: "")
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(20, 16, 20, 16) }
        val header = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        val title = TextView(this).apply { text = "Live TV"; textSize = 26f }
        search = EditText(this).apply { hint = "Search channels"; singleLine = true }
        header.addView(title, LinearLayout.LayoutParams(0, -2, 1f))
        header.addView(search, LinearLayout.LayoutParams(0, -2, 2f))
        val categoryRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        categoryRow.addView(Button(this).apply { text = "All"; isAllCaps = false; setOnClickListener { selectedCategory = null; render() } })
        list = ListView(this).apply { isFocusable = true; isFocusableInTouchMode = true }
        status = TextView(this).apply { text = "Loading channels..."; textSize = 14f }
        root.addView(header)
        root.addView(categoryRow, LinearLayout.LayoutParams(-1, -2))
        root.addView(list, LinearLayout.LayoutParams(-1, 0, 1f))
        root.addView(status)
        setContentView(root)
        search.setOnEditorActionListener { _, _, _ -> render(); false }
        list.setOnItemClickListener { _, _, position, _ -> play(filteredChannels()[position]) }
        list.setOnItemLongClickListener { _, _, position, _ ->
            val channel = filteredChannels()[position]
            if (!favorites.add(channel.id)) favorites.remove(channel.id)
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
        list.adapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, channels.map { if (favorites.contains(it.id)) "★ ${it.name}" else it.name })
        status.text = "${channels.size} channels • Long-press to favorite"
        list.requestFocus()
    }

    private fun play(channel: IptvChannel) {
        startActivity(android.content.Intent(this, MainActivity::class.java).apply {
            putExtra("stream_url", channel.streamUrl)
            putExtra("title", channel.name)
        })
    }

    override fun onDestroy() { executor.shutdownNow(); super.onDestroy() }
}
