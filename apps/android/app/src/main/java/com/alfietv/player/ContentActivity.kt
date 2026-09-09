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
    private lateinit var categoryRow: LinearLayout
    private var mode = "vod"
    private var categories = emptyList<IptvCategory>()
    private var vod = emptyList<VodItem>()
    private var series = emptyList<SeriesItem>()
    private var episodes = emptyList<SeriesEpisode>()
    private var selectedCategory: String? = null
    private var selectedSeriesId: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        mode = intent.getStringExtra("mode") ?: "vod"
        config = XtreamConfig(intent.getStringExtra("server") ?: "", intent.getStringExtra("username") ?: "", intent.getStringExtra("password") ?: "")
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(20, 16, 20, 16) }
        val header = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        val title = TextView(this).apply { text = if (mode == "series") "Series" else "Movies"; textSize = 26f }
        search = EditText(this).apply { hint = "Search"; setSingleLine(true); isFocusable = true }
        header.addView(title, LinearLayout.LayoutParams(0, -2, 1f)); header.addView(search, LinearLayout.LayoutParams(0, -2, 2f))
        val cats = HorizontalScrollView(this)
        categoryRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        categoryRow.addView(Button(this).apply { text = "All"; isAllCaps = false; setOnClickListener { selectedCategory = null; render() } })
        cats.addView(categoryRow)
        list = ListView(this).apply { isFocusable = true; isFocusableInTouchMode = true }
        status = TextView(this).apply { text = "Loading..." }
        root.addView(header); root.addView(cats, LinearLayout.LayoutParams(-1, -2)); root.addView(list, LinearLayout.LayoutParams(-1, 0, 1f)); root.addView(status)
        setContentView(root)
        search.setOnEditorActionListener { _, _, _ -> render(); false }
        list.setOnItemClickListener { _, _, position, _ ->
            if (mode == "vod") playVod(filteredVod()[position])
            else if (episodes.isNotEmpty()) playEpisode(episodes[position])
            else loadEpisodes(filteredSeries()[position].id)
        }
        list.setOnItemLongClickListener { _, _, position, _ ->
            if (mode == "vod") {
                val item = filteredVod()[position]
                val added = UserLibraryStore.toggleFavorite(this, config, item.toLibraryItem())
                status.text = if (added) "★ Added to favorites: ${item.name}" else "Removed from favorites: ${item.name}"
                render(); true
            } else if (episodes.isNotEmpty()) {
                val item = episodes[position]
                val added = UserLibraryStore.toggleFavorite(this, config, item.toLibraryItem(selectedSeriesId))
                status.text = if (added) "★ Added to favorites: ${item.name}" else "Removed from favorites: ${item.name}"
                render(); true
            } else {
                val item = filteredSeries()[position]
                val added = UserLibraryStore.toggleFavorite(this, config, item.toLibraryItem())
                status.text = if (added) "★ Added to favorites: ${item.name}" else "Removed from favorites: ${item.name}"
                render(); true
            }
        }
        load()
    }

    private fun load() {
        val cached = ContentCache.read(this, config, mode)
        if (cached != null) {
            apply(cached.categories, cached.vod, cached.series)
            status.text = "${itemCount()} ${label()} • Cached ${ContentCache.ageText(cached)} • Refreshing..."
        } else status.text = "Loading ${label()}..."
        executor.execute {
            try {
                if (mode == "vod") {
                    val result = XtreamClient().loadVod(config)
                    ContentCache.write(this, config, mode, result.first, vod = result.second)
                    runOnUiThread { apply(result.first, result.second, emptyList()); status.text = "${result.second.size} movies • Updated just now" }
                } else {
                    val result = XtreamClient().loadSeries(config)
                    ContentCache.write(this, config, mode, result.first, series = result.second)
                    runOnUiThread { apply(result.first, emptyList(), result.second); status.text = "${result.second.size} series • Updated just now" }
                }
            } catch (e: Exception) {
                runOnUiThread { status.text = if (itemCount() > 0) "${itemCount()} ${label()} • Offline cache • Refresh failed" else "Unable to load ${label()}: ${e.message ?: "unknown error"}" }
            }
        }
    }

    private fun apply(newCategories: List<IptvCategory>, newVod: List<VodItem>, newSeries: List<SeriesItem>) {
        categories = newCategories
        if (mode == "vod") vod = newVod else series = newSeries
        while (categoryRow.childCount > 1) categoryRow.removeViewAt(1)
        categories.forEach { c -> categoryRow.addView(Button(this).apply { text = c.name; isAllCaps = false; setOnClickListener { selectedCategory = c.id; render() } }) }
        if (selectedCategory != null && categories.none { it.id == selectedCategory }) selectedCategory = null
        render()
    }

    private fun itemCount() = if (mode == "vod") vod.size else series.size
    private fun label() = if (mode == "vod") "movies" else "series"
    private fun filteredVod() = vod.filter { (selectedCategory == null || it.categoryId == selectedCategory) && search.text.toString().trim().let { q -> q.isBlank() || it.name.contains(q, true) } }
    private fun filteredSeries() = series.filter { (selectedCategory == null || it.categoryId == selectedCategory) && search.text.toString().trim().let { q -> q.isBlank() || it.name.contains(q, true) } }

    private fun render() {
        if (mode == "vod") {
            val items = filteredVod()
            list.adapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, items.mapIndexed { i, x -> "${i + 1}. ${if (UserLibraryStore.isFavorite(this, config, x.toLibraryItem())) "★ " else ""}${x.name}" })
            if (!status.text.contains("Refreshing") && !status.text.contains("Offline") && !status.text.contains("Updated") && !status.text.contains("Added") && !status.text.contains("Removed")) status.text = "${items.size} movies • Select to play • Long-press to favorite"
        } else if (episodes.isEmpty()) {
            val items = filteredSeries()
            list.adapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, items.mapIndexed { i, x -> "${i + 1}. ${if (UserLibraryStore.isFavorite(this, config, x.toLibraryItem())) "★ " else ""}${x.name}" })
            if (!status.text.contains("Refreshing") && !status.text.contains("Offline") && !status.text.contains("Updated") && !status.text.contains("Added") && !status.text.contains("Removed")) status.text = "${items.size} series • Select for episodes • Long-press to favorite"
        } else {
            list.adapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, episodes.map { e -> "S${e.season ?: 0} E${e.episode ?: 0}  ${if (UserLibraryStore.isFavorite(this, config, e.toLibraryItem(selectedSeriesId))) "★ " else ""}${e.name}" })
            if (!status.text.contains("Refreshing") && !status.text.contains("Offline") && !status.text.contains("Updated") && !status.text.contains("Added") && !status.text.contains("Removed")) status.text = "${episodes.size} episodes • Long-press to favorite"
        }
        list.requestFocus()
    }

    private fun loadEpisodes(seriesId: String) {
        selectedSeriesId = seriesId
        val cached = ContentCache.readEpisodes(this, config, seriesId)
        if (cached != null) { episodes = cached.episodes; status.text = "${episodes.size} episodes • Cached ${ContentCache.ageText(cached)} • Refreshing..."; render() }
        else { episodes = emptyList(); status.text = "Loading episodes..."; render() }
        executor.execute {
            try {
                val fresh = XtreamClient().loadSeriesEpisodes(config, seriesId)
                ContentCache.writeEpisodes(this, config, seriesId, fresh)
                runOnUiThread { if (selectedSeriesId == seriesId) { episodes = fresh; status.text = "${fresh.size} episodes • Updated just now"; render() } }
            } catch (e: Exception) {
                runOnUiThread { if (selectedSeriesId == seriesId) status.text = if (episodes.isNotEmpty()) "${episodes.size} episodes • Offline cache • Refresh failed" else "Unable to load episodes: ${e.message ?: "unknown error"}" }
            }
        }
    }

    private fun playVod(item: VodItem) { UserLibraryStore.recordWatched(this, config, item.toLibraryItem()); play(item.streamUrl, item.name, item.id, UserLibraryStore.Type.MOVIE.name) }
    private fun playEpisode(item: SeriesEpisode) { UserLibraryStore.recordWatched(this, config, item.toLibraryItem(selectedSeriesId)); play(item.streamUrl, item.name, item.id, UserLibraryStore.Type.EPISODE.name) }
    private fun play(url: String, title: String, id: String, type: String) { startActivity(Intent(this, MainActivity::class.java).apply { putExtra("stream_url", url); putExtra("title", title); putExtra("content_id", id); putExtra("content_type", type); putExtra("server", config.serverUrl); putExtra("username", config.username); putExtra("password", config.password) }) }

    private fun VodItem.toLibraryItem() = UserLibraryStore.Item(id, UserLibraryStore.Type.MOVIE, name, streamUrl, categoryId, posterUrl)
    private fun SeriesItem.toLibraryItem() = UserLibraryStore.Item(id, UserLibraryStore.Type.SERIES, name, "", categoryId, posterUrl)
    private fun SeriesEpisode.toLibraryItem(seriesId: String?) = UserLibraryStore.Item(id, UserLibraryStore.Type.EPISODE, name, streamUrl, seriesId = seriesId, season = season, episode = episode)

    override fun onBackPressed() {
        if (mode == "series" && episodes.isNotEmpty()) { episodes = emptyList(); selectedSeriesId = null; render() } else super.onBackPressed()
    }

    override fun onDestroy() { executor.shutdownNow(); super.onDestroy() }
}
