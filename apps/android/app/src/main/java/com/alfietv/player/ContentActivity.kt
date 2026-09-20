package com.alfietv.player

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.*
import java.util.concurrent.Executors

class ContentActivity : androidx.activity.ComponentActivity() {
    private val skin get() = SkinStore.current(this)
    private val bg get() = skin.background
    private val panel get() = skin.surface
    private val row get() = skin.surface2
    private val accent get() = skin.accent
    private val executor = Executors.newSingleThreadExecutor()
    private lateinit var config: XtreamConfig
    private lateinit var list: GridView
    private lateinit var status: TextView
    private lateinit var search: EditText
    private lateinit var categoryRow: LinearLayout
    private var mode = "vod"
    private var layoutMode = LayoutMode.GRID
    private var categories = emptyList<IptvCategory>()
    private var vod = emptyList<VodItem>()
    private var series = emptyList<SeriesItem>()
    private var episodes = emptyList<SeriesEpisode>()
    private var selectedCategory: String? = null
    private var selectedSeason: Int? = null
    private var selectedSeriesId: String? = null
    private var pendingSeriesId: String? = null
    private var sortMode = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        SkinStore.applyWindow(this)
        mode = intent.getStringExtra("mode") ?: "vod"
        config = XtreamConfig(intent.getStringExtra("server") ?: "", intent.getStringExtra("username") ?: "", intent.getStringExtra("password") ?: "")
        pendingSeriesId = intent.getStringExtra("selected_series_id")
        layoutMode = LayoutModeStore.get(this, screenKey(), LayoutMode.GRID)

        val widthDp = resources.configuration.screenWidthDp
        val compact = widthDp < 600
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(bg)
            setPadding(if (widthDp < 360) 10 else if (compact) 14 else 20, if (compact) 10 else 16,
                if (widthDp < 360) 10 else if (compact) 14 else 20, if (compact) 10 else 16)
        }
        val header = LinearLayout(this).apply {
            gravity = Gravity.CENTER_VERTICAL
            orientation = if (compact) LinearLayout.VERTICAL else LinearLayout.HORIZONTAL
        }
        val title = TextView(this).apply {
            text = if (mode == "series") "Series" else "Movies"
            setTextColor(Color.WHITE)
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            textSize = if (compact) 22f else 26f
            isFocusable = false
        }
        search = EditText(this).apply {
            hint = "Search"
            setTextColor(Color.WHITE)
            setHintTextColor(android.graphics.Color.rgb(185, 195, 210))
            background = roundedSkinField()
            setSingleLine(true)
            isFocusable = true
            isFocusableInTouchMode = true
        }
        val sort = Button(this).apply {
            text = "A-Z"
            setTextColor(Color.WHITE)
            background = roundedSkinField()
            isAllCaps = false
            isFocusable = true
            isFocusableInTouchMode = true
            setOnClickListener {
                sortMode = (sortMode + 1) % 3
                text = when (sortMode) { 0 -> "A-Z"; 1 -> "Z-A"; else -> "Recent" }
                render()
                list.requestFocus()
            }
        }
        if (compact) {
            header.addView(title, LinearLayout.LayoutParams(-1, 32))
            header.addView(search, LinearLayout.LayoutParams(-1, 48).apply { topMargin = 4 })
            header.addView(sort, LinearLayout.LayoutParams(-1, 44).apply { topMargin = 4 })
        } else {
            header.addView(title, LinearLayout.LayoutParams(0, -2, 1f))
            header.addView(search, LinearLayout.LayoutParams(0, -2, 2f))
            header.addView(sort, LinearLayout.LayoutParams(0, -2, 0.8f))
        }

        val layoutControls = LayoutModeControls.create(this, screenKey(), LayoutMode.GRID) { selected ->
            layoutMode = selected
            applyLayoutMode()
            render()
            list.requestFocus()
        }

        val cats = HorizontalScrollView(this).apply { isFocusable = false }
        categoryRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; isFocusable = false }
        categoryRow.addView(Button(this).apply { text = "All"; isAllCaps = false; isFocusable = true; isFocusableInTouchMode = true; setOnClickListener { selectedCategory = null; render(); list.requestFocus() } })
        cats.addView(categoryRow)

        list = GridView(this).apply {
            isFocusable = true
            isFocusableInTouchMode = true
            verticalSpacing = 12
            horizontalSpacing = 12
            stretchMode = GridView.STRETCH_COLUMN_WIDTH
        }
        applyLayoutMode()
        status = TextView(this).apply { text = "Loading..."; isFocusable = false }

        root.addView(header)
        root.addView(layoutControls, LinearLayout.LayoutParams(-1, -2))
        root.addView(cats, LinearLayout.LayoutParams(-1, -2))
        root.addView(list, LinearLayout.LayoutParams(-1, 0, 1f))
        root.addView(status)
        setContentView(root)
        SkinStore.animate(root, skin)

        search.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) { render() }
            override fun afterTextChanged(s: Editable?) = Unit
        })
        search.setOnEditorActionListener { _, _, _ -> list.requestFocus(); true }
        list.setOnItemClickListener { _, _, position, _ ->
            if (mode == "vod") playVod(filteredVod()[position])
            else if (episodes.isNotEmpty()) playEpisode(filteredEpisodes()[position])
            else loadEpisodes(filteredSeries()[position].id)
        }
        list.setOnItemLongClickListener { _, _, position, _ ->
            if (mode == "vod") {
                val item = filteredVod()[position]
                val added = UserLibraryStore.toggleFavorite(this, config, item.toLibraryItem())
                status.text = if (added) "★ Added to favorites: ${item.name}" else "Removed from favorites: ${item.name}"
                render(); true
            } else if (episodes.isNotEmpty()) {
                val item = filteredEpisodes()[position]
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

    private fun roundedSkinField() = android.graphics.drawable.GradientDrawable().apply { setColor(panel); cornerRadius = 12f * resources.displayMetrics.density }

    private fun screenKey(): String = if (mode == "series") "series" else "movies"

    private fun applyLayoutMode() {
        val columns = when (layoutMode) {
            LayoutMode.LIST -> 1
            LayoutMode.GRID -> when {
                resources.configuration.screenWidthDp < 360 -> 1
                resources.configuration.screenWidthDp < 600 -> 2
                resources.configuration.screenWidthDp < 900 -> 3
                else -> 4
            }
            LayoutMode.TILE -> when {
                resources.configuration.screenWidthDp < 600 -> 2
                resources.configuration.screenWidthDp < 900 -> 4
                else -> 5
            }
        }
        list.numColumns = columns
        list.verticalSpacing = if (layoutMode == LayoutMode.LIST) 6 else 14
        list.horizontalSpacing = if (layoutMode == LayoutMode.LIST) 0 else 12
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
        rebuildCategoryButtons()
        if (selectedCategory != null && categories.none { it.id == selectedCategory }) selectedCategory = null
        render()
        if (mode == "series" && episodes.isEmpty() && selectedSeriesId == null) {
            pendingSeriesId?.let { id -> if (series.any { it.id == id }) { pendingSeriesId = null; loadEpisodes(id) } }
        }
    }

    private fun rebuildCategoryButtons() {
        while (categoryRow.childCount > 0) categoryRow.removeViewAt(0)
        categoryRow.addView(Button(this).apply { text = "All"; isAllCaps = false; isFocusable = true; isFocusableInTouchMode = true; setOnClickListener { selectedCategory = null; render(); list.requestFocus() } })
        categories.forEach { c -> categoryRow.addView(Button(this).apply { text = c.name; isAllCaps = false; isFocusable = true; isFocusableInTouchMode = true; setOnClickListener { selectedCategory = c.id; render(); list.requestFocus() } }) }
    }

    private fun rebuildSeasonButtons() {
        while (categoryRow.childCount > 0) categoryRow.removeViewAt(0)
        val seasons = episodes.mapNotNull { it.season }.distinct().sorted()
        categoryRow.addView(Button(this).apply {
            text = "All Seasons"
            isAllCaps = false
            isFocusable = true
            isFocusableInTouchMode = true
            setOnClickListener { selectedSeason = null; render(); list.requestFocus() }
        })
        seasons.forEach { season ->
            categoryRow.addView(Button(this).apply {
                text = "Season $season"
                isAllCaps = false
                isFocusable = true
                isFocusableInTouchMode = true
                setOnClickListener { selectedSeason = season; render(); list.requestFocus() }
            })
        }
    }

    private fun itemCount() = if (mode == "vod") vod.size else series.size
    private fun label() = if (mode == "vod") "movies" else "series"
    private fun query() = search.text.toString().trim()
    private fun filteredVod() = sortVod(vod.filter { (selectedCategory == null || it.categoryId == selectedCategory) && query().let { q -> q.isBlank() || it.name.contains(q, true) } })
    private fun filteredSeries() = sortSeries(series.filter { (selectedCategory == null || it.categoryId == selectedCategory) && query().let { q -> q.isBlank() || it.name.contains(q, true) } })
    private fun filteredEpisodes() = episodes.filter { selectedSeason == null || it.season == selectedSeason }.let { items ->
        if (query().isBlank()) items else items.filter { it.name.contains(query(), true) }
    }.sortedWith(compareBy<SeriesEpisode> { it.season ?: 0 }.thenBy { it.episode ?: 0 })
    private fun sortVod(items: List<VodItem>) = when (sortMode) { 1 -> items.sortedByDescending { it.name.lowercase() }; 2 -> items.sortedByDescending { it.year ?: "" }; else -> items.sortedBy { it.name.lowercase() } }
    private fun sortSeries(items: List<SeriesItem>) = when (sortMode) { 1 -> items.sortedByDescending { it.name.lowercase() }; 2 -> items.sortedByDescending { it.year ?: "" }; else -> items.sortedBy { it.name.lowercase() } }

    private fun renderArtworkList(items: List<Any>, labels: List<String>, urls: List<String?>, placeholder: Int) {
        list.adapter = object : ArrayAdapter<Any>(this, android.R.layout.simple_list_item_1, items) {
            override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
                val vertical = layoutMode != LayoutMode.LIST
                val row = LinearLayout(this@ContentActivity).apply {
                    orientation = if (vertical) LinearLayout.VERTICAL else LinearLayout.HORIZONTAL
                    gravity = if (vertical) Gravity.TOP else Gravity.CENTER_VERTICAL
                    setPadding(if (vertical) 6 else 14, 8, if (vertical) 6 else 14, 8)
                    setBackgroundColor(panel)
                    minimumHeight = if (vertical) 170 else 78
                }
                val icon = ImageView(this@ContentActivity).apply {
                    scaleType = ImageView.ScaleType.CENTER_CROP
                    layoutParams = if (vertical) {
                        LinearLayout.LayoutParams(-1, if (layoutMode == LayoutMode.TILE) 190 else 220)
                    } else {
                        LinearLayout.LayoutParams(58, 68).apply { marginEnd = 16 }
                    }
                }
                val text = TextView(this@ContentActivity).apply {
                    textSize = if (vertical) 14f else 18f
                    isFocusable = false
                    maxLines = if (vertical) 2 else 1
                    gravity = if (vertical) Gravity.CENTER_HORIZONTAL else Gravity.CENTER_VERTICAL
                    setPadding(4, if (vertical) 8 else 0, 4, 0)
                    setTextColor(Color.WHITE)
                }
                text.text = labels[position]
                row.addView(icon)
                row.addView(text, if (vertical) LinearLayout.LayoutParams(-1, -2) else LinearLayout.LayoutParams(0, -2, 1f))
                ArtworkLoader.load(urls.getOrNull(position), icon, placeholder)
                return row
            }
        }
    }

    private fun render() {
        if (mode == "vod") {
            val items = filteredVod()
            val labels = items.mapIndexed { i, x -> "${i + 1}. ${if (UserLibraryStore.isFavorite(this, config, x.toLibraryItem())) "★ " else ""}${x.name}" }
            renderArtworkList(items, labels, items.map { it.posterUrl }, android.R.drawable.ic_menu_gallery)
            if (!status.text.contains("Refreshing") && !status.text.contains("Offline") && !status.text.contains("Updated") && !status.text.contains("Added") && !status.text.contains("Removed")) status.text = "${items.size} movies • ${layoutMode.name.lowercase()} • Select to play • Long-press to favorite"
        } else if (episodes.isEmpty()) {
            val items = filteredSeries()
            val labels = items.mapIndexed { i, x -> "${i + 1}. ${if (UserLibraryStore.isFavorite(this, config, x.toLibraryItem())) "★ " else ""}${x.name}" }
            renderArtworkList(items, labels, items.map { it.posterUrl }, android.R.drawable.ic_menu_gallery)
            if (!status.text.contains("Refreshing") && !status.text.contains("Offline") && !status.text.contains("Updated") && !status.text.contains("Added") && !status.text.contains("Removed")) status.text = "${items.size} series • ${layoutMode.name.lowercase()} • Select for episodes • Long-press to favorite"
        } else {
            val items = filteredEpisodes()
            val labels = items.map { e -> "S${e.season ?: 0} E${e.episode ?: 0}  ${if (UserLibraryStore.isFavorite(this, config, e.toLibraryItem(selectedSeriesId))) "★ " else ""}${e.name}" }
            renderArtworkList(items, labels, List(items.size) { null }, android.R.drawable.ic_media_play)
            status.text = "${items.size} episodes • ${if (selectedSeason == null) "All Seasons" else "Season $selectedSeason"} • Select to play • Long-press to favorite"
        }
        list.post { if (!search.hasFocus()) list.requestFocus() }
    }

    private fun loadEpisodes(seriesId: String) {
        selectedSeriesId = seriesId
        selectedSeason = null
        val cached = ContentCache.readEpisodes(this, config, seriesId)
        if (cached != null) { episodes = cached.episodes; rebuildSeasonButtons(); status.text = "${episodes.size} episodes • Cached ${ContentCache.ageText(cached)} • Refreshing..."; render() }
        else { episodes = emptyList(); render() }
        executor.execute {
            try {
                val fresh = XtreamClient().loadSeriesEpisodes(config, seriesId)
                ContentCache.writeEpisodes(this, config, seriesId, fresh)
                runOnUiThread {
                    if (selectedSeriesId == seriesId) {
                        episodes = fresh
                        selectedSeason = null
                        rebuildSeasonButtons()
                        status.text = "${fresh.size} episodes • Updated just now"
                        render()
                    }
                }
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
        if (search.hasFocus()) {
            if (search.text.isNotEmpty()) search.text.clear()
            search.clearFocus()
            list.requestFocus()
            return
        }
        if (mode == "series" && episodes.isNotEmpty()) {
            if (selectedSeason != null) { selectedSeason = null; render(); list.requestFocus(); return }
            episodes = emptyList()
            selectedSeriesId = null
            selectedSeason = null
            rebuildCategoryButtons()
            render()
        } else super.onBackPressed()
    }

    override fun onDestroy() { executor.shutdownNow(); super.onDestroy() }
}
