package com.alfietv.player

import android.content.Intent
import android.os.Bundle
import android.view.Gravity
import android.widget.*
import java.util.concurrent.Executors

class ContentActivity : androidx.activity.ComponentActivity() {
    private val executor = Executors.newSingleThreadExecutor()
    private lateinit var config: XtreamConfig
    private lateinit var list: ListView
    private lateinit var status: TextView
    private lateinit var search: EditText
    private var mode = "vod"
    private var categories = emptyList<IptvCategory>()
    private var vod = emptyList<VodItem>()
    private var series = emptyList<SeriesItem>()
    private var episodes = emptyList<SeriesEpisode>()
    private var selectedCategory: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        mode = intent.getStringExtra("mode") ?: "vod"
        config = XtreamConfig(intent.getStringExtra("server") ?: "", intent.getStringExtra("username") ?: "", intent.getStringExtra("password") ?: "")
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(20, 16, 20, 16) }
        val header = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        val title = TextView(this).apply { text = if (mode == "series") "Series" else "Movies"; textSize = 26f }
        search = EditText(this).apply { hint = "Search"; setSingleLine(true) }
        header.addView(title, LinearLayout.LayoutParams(0, -2, 1f)); header.addView(search, LinearLayout.LayoutParams(0, -2, 2f))
        val cats = HorizontalScrollView(this)
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        row.addView(Button(this).apply { text = "All"; setOnClickListener { selectedCategory = null; render() } })
        cats.addView(row)
        list = ListView(this).apply { isFocusable = true; isFocusableInTouchMode = true }
        status = TextView(this).apply { text = "Loading..." }
        root.addView(header); root.addView(cats, LinearLayout.LayoutParams(-1, -2)); root.addView(list, LinearLayout.LayoutParams(-1, 0, 1f)); root.addView(status)
        setContentView(root)
        search.setOnEditorActionListener { _, _, _ -> render(); false }
        list.setOnItemClickListener { _, _, position, _ -> if (mode == "vod") playVod(filteredVod()[position]) else if (episodes.isNotEmpty()) playEpisode(episodes[position]) else loadEpisodes(series[position].id) }
        load(row)
    }

    private fun load(row: LinearLayout) = executor.execute {
        try {
            if (mode == "vod") { val result = XtreamClient().loadVod(config); categories = result.first; vod = result.second }
            else { val result = XtreamClient().loadSeries(config); categories = result.first; series = result.second }
            runOnUiThread { categories.forEach { c -> row.addView(Button(this).apply { text = c.name; isAllCaps = false; setOnClickListener { selectedCategory = c.id; render() } }) }; render() }
        } catch (e: Exception) { runOnUiThread { status.text = "Unable to load content: ${e.message ?: "unknown error"}" } }
    }

    private fun filteredVod() = vod.filter { (selectedCategory == null || it.categoryId == selectedCategory) && search.text.toString().trim().let { q -> q.isBlank() || it.name.contains(q, true) } }
    private fun filteredSeries() = series.filter { (selectedCategory == null || it.categoryId == selectedCategory) && search.text.toString().trim().let { q -> q.isBlank() || it.name.contains(q, true) } }

    private fun render() {
        if (mode == "vod") { val items = filteredVod(); list.adapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, items.mapIndexed { i, x -> "${i + 1}. ${x.name}" }); status.text = "${items.size} movies • Select to play" }
        else if (episodes.isEmpty()) { val items = filteredSeries(); list.adapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, items.mapIndexed { i, x -> "${i + 1}. ${x.name}" }); status.text = "${items.size} series • Select for episodes" }
        else { list.adapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, episodes.map { e -> "S${e.season ?: 0} E${e.episode ?: 0}  ${e.name}" }); status.text = "${episodes.size} episodes" }
        list.requestFocus()
    }

    private fun loadEpisodes(seriesId: String) = executor.execute { try { episodes = XtreamClient().loadSeriesEpisodes(config, seriesId); runOnUiThread { render() } } catch (e: Exception) { runOnUiThread { status.text = "Unable to load episodes: ${e.message ?: "unknown error"}" } } }
    private fun playVod(item: VodItem) = play(item.streamUrl, item.name)
    private fun playEpisode(item: SeriesEpisode) = play(item.streamUrl, item.name)
    private fun play(url: String, title: String) { startActivity(Intent(this, MainActivity::class.java).apply { putExtra("stream_url", url); putExtra("title", title) }) }
    override fun onDestroy() { executor.shutdownNow(); super.onDestroy() }
}
