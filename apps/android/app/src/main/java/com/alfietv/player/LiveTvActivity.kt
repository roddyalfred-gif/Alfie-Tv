package com.alfietv.player

import android.animation.ObjectAnimator
import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.GridView
import android.widget.HorizontalScrollView
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.media3.ui.PlayerView
import java.text.DateFormat
import java.util.Date
import java.util.concurrent.Executors

/** Responsive Live TV screen: visible category names, complete controls, preview and two-press fullscreen. */
class LiveTvActivity : ComponentActivity() {
    private val executor = Executors.newSingleThreadExecutor()
    private val mainHandler = Handler(Looper.getMainLooper())
    private lateinit var list: GridView
    private lateinit var status: TextView
    private lateinit var details: LinearLayout
    private lateinit var previewView: PlayerView
    private lateinit var search: EditText
    private lateinit var categoryRow: LinearLayout
    private lateinit var epgTitle: TextView
    private lateinit var epgNow: TextView
    private lateinit var epgNext: TextView
    private lateinit var epgProgress: ProgressBar
    private lateinit var epgMeta: TextView
    private var previewPlayer: AlfiePlayer? = null
    private var previewChannelId: String? = null
    private var previewCategoryId: String? = null
    private var selectedCategory: String? = null
    private var allChannels = emptyList<IptvChannel>()
    private var selectedIndex = -1
    private var restoredLastChannel = false
    private var fullscreenLaunchInProgress = false
    private var awaitingFullscreenReturn = false
    private var categories = emptyList<IptvCategory>()
    private lateinit var config: XtreamConfig
    private val bg = Color.rgb(5, 9, 18)
    private val panel = Color.rgb(13, 21, 35)
    private val row = Color.rgb(15, 24, 40)
    private val accent = Color.rgb(0, 168, 255)
    private val muted = Color.rgb(170, 181, 200)

    private val ticker = object : Runnable {
        override fun run() { selectedChannel()?.let { refreshEpg(it) }; mainHandler.postDelayed(this, 30_000L) }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = bg
        window.navigationBarColor = bg
        config = XtreamConfig(intent.getStringExtra("server") ?: "", intent.getStringExtra("username") ?: "", intent.getStringExtra("password") ?: "")
        buildUi()
        load()
        mainHandler.post(ticker)
    }

    private fun buildUi() {
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(12, 10, 12, 8); setBackgroundColor(bg) }
        val header = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        header.addView(TextView(this).apply { text = "LIVE TV"; textSize = 25f; setTextColor(Color.WHITE); typeface = Typeface.DEFAULT_BOLD }, LinearLayout.LayoutParams(0, 52, 1f))
        search = EditText(this).apply { hint = "⌕  Search channels"; setSingleLine(true); textSize = 15f; setTextColor(Color.WHITE); setHintTextColor(muted); background = roundedBackground(panel, 12f); setPadding(14, 0, 14, 0); isFocusable = true; isFocusableInTouchMode = true }
        header.addView(search, LinearLayout.LayoutParams(0, 50, 1.35f))
        root.addView(header)
        val categoryScroll = HorizontalScrollView(this).apply { isHorizontalScrollBarEnabled = false; clipToPadding = false; setPadding(0, 2, 0, 2) }
        categoryRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        categoryScroll.addView(categoryRow)
        root.addView(categoryScroll, LinearLayout.LayoutParams(-1, 56))
        val tools = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        tools.addView(TextView(this).apply { text = "CATEGORIES"; textSize = 12f; setTextColor(muted); typeface = Typeface.DEFAULT_BOLD }, LinearLayout.LayoutParams(0, 42, 1f))
        tools.addView(iconButton("search", "Search") { search.requestFocus() })
        tools.addView(iconButton("favorite", "Favorites") { selectFavorites() })
        tools.addView(iconButton("grid", "Grid") { setColumns(3) })
        tools.addView(iconButton("list", "List") { setColumns(1) })
        root.addView(tools, LinearLayout.LayoutParams(-1, 44))
        val content = LinearLayout(this).apply { orientation = if (isPortrait()) LinearLayout.VERTICAL else LinearLayout.HORIZONTAL }
        list = GridView(this).apply {
            isFocusable = true; isFocusableInTouchMode = true; clipToPadding = false; verticalSpacing = 7; horizontalSpacing = 7; stretchMode = GridView.STRETCH_COLUMN_WIDTH; setPadding(0, 4, 6, 2)
            setOnKeyListener { _, keyCode, event ->
                if (event.action != KeyEvent.ACTION_DOWN || event.repeatCount > 0) return@setOnKeyListener false
                when (keyCode) {
                    KeyEvent.KEYCODE_CHANNEL_UP -> { moveChannel(-1); true }
                    KeyEvent.KEYCODE_CHANNEL_DOWN -> { moveChannel(1); true }
                    KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> { selectedChannel()?.let(::handleChannelClick); true }
                    KeyEvent.KEYCODE_DPAD_RIGHT -> { details.requestFocus(); true }
                    else -> false
                }
            }
            setOnFocusChangeListener { _, focused -> if (focused) selectedChannel()?.let(::showEpg) }
        }
        details = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(12, 12, 12, 12); setBackgroundColor(panel); isFocusable = true; isFocusableInTouchMode = true }
        previewView = PlayerView(this).apply { useController = false; controllerAutoShow = false; setShowBuffering(PlayerView.SHOW_BUFFERING_ALWAYS); resizeMode = androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_FIT; setBackgroundColor(Color.BLACK) }
        details.addView(previewView, LinearLayout.LayoutParams(-1, if (isPortrait()) 205 else 190).apply { bottomMargin = 8 })
        epgTitle = label("LIVE PREVIEW", 20f, Color.WHITE, true)
        epgNow = label("NOW  —", 16f, accent, true)
        epgProgress = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply { max = 100 }
        epgNext = label("NEXT  —", 15f, Color.WHITE, false)
        epgMeta = label("Select a channel to preview", 12f, muted, false)
        details.addView(epgTitle); details.addView(epgNow); details.addView(epgProgress, LinearLayout.LayoutParams(-1, 8)); details.addView(epgNext); details.addView(epgMeta)
        if (isPortrait()) { content.addView(details, LinearLayout.LayoutParams(-1, 300)); content.addView(list, LinearLayout.LayoutParams(-1, 0, 1f)) }
        else { content.addView(list, LinearLayout.LayoutParams(0, -1, 1.6f)); content.addView(details, LinearLayout.LayoutParams(0, -1, 1f).apply { leftMargin = 8 }) }
        root.addView(content, LinearLayout.LayoutParams(-1, 0, 1f))
        status = label("Loading channels…", 12f, muted, false); status.setPadding(4, 5, 4, 0); root.addView(status, LinearLayout.LayoutParams(-1, 28)); setContentView(root)
        search.addTextChangedListener(object : TextWatcher { override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit; override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) { render() }; override fun afterTextChanged(s: Editable?) = Unit })
        list.setOnItemClickListener { _, _, position, _ -> filteredChannels().getOrNull(position)?.let { selectedIndex = position; handleChannelClick(it) } }
        list.setOnItemSelectedListener(object : AdapterView.OnItemSelectedListener { override fun onNothingSelected(parent: AdapterView<*>?) = Unit; override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) { selectedIndex = position; filteredChannels().getOrNull(position)?.let(::showEpg); list.post { list.invalidateViews() } } })
        setColumns(if (isPortrait()) 2 else 3)
    }
    private fun isPortrait() = resources.configuration.orientation == android.content.res.Configuration.ORIENTATION_PORTRAIT
    private fun setColumns(columns: Int) { if (::list.isInitialized) { list.numColumns = columns; list.horizontalSpacing = 7; list.verticalSpacing = 8; render() } }
    private fun iconButton(kind: String, description: String, action: () -> Unit): ImageButton = ImageButton(this).apply { contentDescription = description; val icon = when (kind) { "search" -> android.R.drawable.ic_menu_search; "favorite" -> android.R.drawable.btn_star_big_on; "grid" -> android.R.drawable.ic_menu_gallery; else -> android.R.drawable.ic_menu_sort_by_size }; setImageResource(icon); background = roundedBackground(row, 10f); setColorFilter(Color.WHITE); setOnClickListener { action() }; setPadding(9, 9, 9, 9); layoutParams = LinearLayout.LayoutParams(44, 42).apply { marginStart = 6 } }
    private fun label(text: String, size: Float, color: Int, bold: Boolean) = TextView(this).apply { this.text = text; textSize = size; setTextColor(color); typeface = if (bold) Typeface.DEFAULT_BOLD else Typeface.DEFAULT; setPadding(0, 8, 0, 6); maxLines = 3 }
    private fun addCategory(name: String, id: String?, selected: Boolean = false) { val chip = TextView(this).apply { text = "  ${if (selected) "✓ " else ""}$name  "; textSize = 14f; setTextColor(Color.WHITE); typeface = Typeface.DEFAULT_BOLD; gravity = Gravity.CENTER; isFocusable = true; isFocusableInTouchMode = true; background = roundedBackground(if (selected) accent else row, 12f); setPadding(8, 0, 8, 0); setOnFocusChangeListener { v, focused -> if (focused) v.background = roundedBackground(accent, 12f) else v.background = roundedBackground(if (selectedCategory == id) accent else row, 12f); animateFocus(v, focused) }; setOnClickListener { selectedCategory = id; selectedIndex = -1; rebuildCategories(); render(); status.text = "${filteredChannels().size} channels  •  ${name.trim()}  •  OK = preview  •  OK again = fullscreen"; list.requestFocus() }; setOnKeyListener { _, keyCode, event -> if (event.action == KeyEvent.ACTION_DOWN && keyCode == KeyEvent.KEYCODE_DPAD_DOWN) { list.requestFocus(); true } else false } }; categoryRow.addView(chip, LinearLayout.LayoutParams(-2, 44).apply { marginEnd = 7 }) }
    private fun rebuildCategories() { categoryRow.removeAllViews(); addCategory("All", null, selectedCategory == null); categories.forEach { addCategory(it.name.ifBlank { "Category" }, it.id, it.id == selectedCategory) } }
    private fun load() { val cached = LiveTvCache.read(this, config); if (cached != null) applyChannels(cached.categories, cached.channels); executor.execute { try { val result = XtreamClient().load(config); LiveTvCache.write(this, config, result.first, result.second); runOnUiThread { applyChannels(result.first, result.second); status.text = "${result.second.size} channels  •  Updated just now  •  Categories visible above" } } catch (e: Exception) { runOnUiThread { status.text = if (allChannels.isNotEmpty()) "${allChannels.size} channels  •  Offline cache  •  Refresh failed" else "Unable to load channels: ${e.message ?: "unknown error"}" } } } }
    private fun applyChannels(newCategories: List<IptvCategory>, channels: List<IptvChannel>) {
        categories = newCategories; allChannels = channels; rebuildCategories(); render()
        val pendingId = intent.getStringExtra("preview_channel_id")
        val hasPendingGuideChannel = !pendingId.isNullOrBlank()
        if (hasPendingGuideChannel) channels.firstOrNull { it.id == pendingId }?.let { intent.removeExtra("preview_channel_id"); selectedCategory = it.categoryId; previewCategoryId = it.categoryId; rebuildCategories(); selectedIndex = filteredChannels().indexOfFirst { c -> c.id == it.id }.coerceAtLeast(0); list.post { list.setSelection(selectedIndex); preview(it); focusSelectedChannel() } }
        if (!hasPendingGuideChannel && !restoredLastChannel) {
            restoredLastChannel = true
            val prefs = getSharedPreferences("alfie_tv", Context.MODE_PRIVATE)
            val lastId = prefs.getString("last_channel_${config.serverUrl}_${config.username}", null)
            val lastCategory = prefs.getString("last_category_${config.serverUrl}_${config.username}", null)
            if (!lastCategory.isNullOrBlank()) selectedCategory = lastCategory
            lastId?.let { id -> channels.firstOrNull { it.id == id }?.let { selectedCategory = it.categoryId ?: selectedCategory; previewCategoryId = it.categoryId; selectedIndex = filteredChannels().indexOfFirst { c -> c.id == it.id }.coerceAtLeast(0); rebuildCategories(); list.post { list.setSelection(selectedIndex); showEpg(it); focusSelectedChannel() } } }
        }
        status.text = "${channels.size} channels  •  ${categories.size} categories  •  OK = preview  •  OK again = fullscreen"
    }
    private fun filteredChannels(): List<IptvChannel> { val q = search.text.toString().trim().lowercase(); return allChannels.filter { (selectedCategory == null || it.categoryId == selectedCategory) && (q.isBlank() || it.name.lowercase().contains(q)) } }
    private fun selectedChannel() = filteredChannels().getOrNull(list.selectedItemPosition.takeIf { it >= 0 } ?: selectedIndex)
    private fun render() { if (!::list.isInitialized) return; val channels = filteredChannels(); list.adapter = object : ArrayAdapter<IptvChannel>(this, android.R.layout.simple_list_item_1, channels) { override fun getView(position: Int, convertView: View?, parent: ViewGroup): View { val c = getItem(position) ?: return TextView(this@LiveTvActivity); val focused = position == selectedIndex; val favorite = try { UserLibraryStore.isFavorite(this@LiveTvActivity, config, c.toLibraryItem()) } catch (_: Exception) { false }; val item = LinearLayout(this@LiveTvActivity).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER_VERTICAL; setPadding(10, 8, 10, 8); minimumHeight = if (isPortrait()) 82 else 88; background = roundedBackground(if (focused) Color.rgb(0, 85, 160) else row, 12f) }; val title = TextView(this@LiveTvActivity).apply { text = "${position + 1}. ${if (favorite) "★ " else ""}${c.name.ifBlank { "Channel ${position + 1}" }}"; textSize = if (isPortrait()) 14f else 15f; setTextColor(Color.WHITE); typeface = Typeface.DEFAULT_BOLD; maxLines = 2 }; val meta = TextView(this@LiveTvActivity).apply { text = if (c.epgId.isNullOrBlank()) "● LIVE" else "● LIVE  •  EPG"; textSize = 10f; setTextColor(if (focused) Color.WHITE else muted) }; item.addView(title); item.addView(meta); return item } }; if (channels.isNotEmpty()) { selectedIndex = selectedIndex.coerceIn(0, channels.lastIndex); list.setSelection(selectedIndex) } }
    private fun handleChannelClick(channel: IptvChannel) { if (fullscreenLaunchInProgress) return; if (previewChannelId == channel.id) playFullscreen(channel) else preview(channel) }
    private fun preview(channel: IptvChannel) { val url = channel.streamUrl.trim(); if (url.isBlank()) { status.text = "${channel.name} has no stream URL"; return }; previewChannelId = channel.id; previewCategoryId = channel.categoryId; selectedCategory = channel.categoryId; getSharedPreferences("alfie_tv", Context.MODE_PRIVATE).edit().putString("last_channel_${config.serverUrl}_${config.username}", channel.id).putString("last_category_${config.serverUrl}_${config.username}", channel.categoryId ?: "").apply(); rebuildCategories(); if (previewPlayer == null) previewPlayer = AlfiePlayer(this).also { it.attach(previewView) }; previewPlayer?.play(url, channel.name, channelNumber = (filteredChannels().indexOfFirst { it.id == channel.id } + 1).coerceAtLeast(1).toString()); epgTitle.text = "PREVIEW  •  ${channel.name}"; status.text = "${channel.name}  •  Preview playing  •  OK again = fullscreen"; showEpg(channel); list.post { list.invalidateViews(); focusSelectedChannel() } }
    private fun playFullscreen(channel: IptvChannel) { if (fullscreenLaunchInProgress) return; val url = channel.streamUrl.trim(); if (url.isBlank()) return; fullscreenLaunchInProgress = true; awaitingFullscreenReturn = true; previewChannelId = channel.id; previewCategoryId = channel.categoryId; selectedCategory = channel.categoryId; val channels = filteredChannels(); val index = channels.indexOfFirst { it.id == channel.id }.coerceAtLeast(0); val intent = android.content.Intent(this, VideoPlayerActivity::class.java).apply { putExtra("url", url); putExtra("stream_url", url); putExtra("title", channel.name); putExtra("content_id", channel.id); putExtra("content_type", "LIVE"); putExtra("channel_id", channel.id); putExtra("channel_number", (index + 1).toString()); putExtra("preview_category_id", channel.categoryId); putExtra("channel_urls", ArrayList(channels.map { it.streamUrl })); putExtra("channel_titles", ArrayList(channels.map { it.name })); putExtra("channel_ids", ArrayList(channels.map { it.id })); putExtra("channel_numbers", ArrayList(channels.indices.map { (it + 1).toString() })); putExtra("channel_index", index); putExtra("server", config.serverUrl); putExtra("username", config.username); putExtra("password", config.password) }; previewPlayer?.release(); previewPlayer = null; previewView.player = null; try { startActivity(intent) } catch (e: Exception) { fullscreenLaunchInProgress = false; awaitingFullscreenReturn = false; preview(channel); status.text = "Unable to open fullscreen player: ${e.message ?: "unknown error"}" } }
    override fun onResume() {
        super.onResume()
        if (!awaitingFullscreenReturn) return
        awaitingFullscreenReturn = false
        fullscreenLaunchInProgress = false

        val prefs = getSharedPreferences("alfie_tv", Context.MODE_PRIVATE)
        val persistedId = prefs.getString("last_channel_${config.serverUrl}_${config.username}", null)
        val persistedCategory = prefs.getString("last_category_${config.serverUrl}_${config.username}", null)
        val returnId = persistedId ?: previewChannelId
        val returnChannel = allChannels.firstOrNull { it.id == returnId }

        if (returnChannel != null) {
            selectedCategory = returnChannel.categoryId ?: persistedCategory ?: previewCategoryId ?: selectedCategory
            previewCategoryId = returnChannel.categoryId ?: persistedCategory ?: previewCategoryId
            previewChannelId = returnChannel.id
            rebuildCategories()
            selectedIndex = filteredChannels().indexOfFirst { it.id == returnChannel.id }.coerceAtLeast(0)
            list.post {
                list.setSelection(selectedIndex)
                preview(returnChannel)
                list.invalidateViews()
                focusSelectedChannel()
                highlightSelectedCategory()
            }
        } else {
            selectedCategory = persistedCategory ?: previewCategoryId ?: selectedCategory
            rebuildCategories()
            focusSelectedChannel()
            highlightSelectedCategory()
        }
    }
    private fun focusSelectedChannel() { val channels = filteredChannels(); if (channels.isEmpty()) return; selectedIndex = selectedIndex.coerceIn(0, channels.lastIndex); list.post { list.setSelection(selectedIndex); list.requestFocus(); list.invalidateViews() } }
    private fun highlightSelectedCategory() { val index = categories.indexOfFirst { it.id == selectedCategory }; val childIndex = if (index >= 0) index + 1 else 0; categoryRow.getChildAt(childIndex)?.let { view -> view.background = roundedBackground(accent, 12f) } }
    private fun showEpg(channel: IptvChannel) { epgTitle.text = if (previewChannelId == channel.id) "PREVIEW  •  ${channel.name}" else "LIVE PREVIEW  •  ${channel.name}"; epgNow.text = "NOW  Loading…"; epgNext.text = "NEXT  Loading…"; epgMeta.text = "EPG: ${channel.epgId ?: "not mapped"}"; epgProgress.progress = 0; executor.execute { try { val programs = XtreamClient().loadEpg(config, channel); runOnUiThread { if (previewChannelId == channel.id || selectedChannel()?.id == channel.id) renderEpg(channel, programs) } } catch (_: Exception) { runOnUiThread { if (selectedChannel()?.id == channel.id) { epgNow.text = "NOW  No programme data"; epgNext.text = "NEXT  Press OK to preview live" } } } } }
    private fun refreshEpg(channel: IptvChannel) { if (!isFinishing) showEpg(channel) }
    private fun renderEpg(channel: IptvChannel, programs: List<EpgProgram>) { val now = System.currentTimeMillis(); val sorted = programs.sortedBy { it.startUtcMs }; val current = sorted.firstOrNull { now in it.startUtcMs until it.endUtcMs }; val next = sorted.firstOrNull { it.startUtcMs > now }; epgNow.text = "NOW  ${current?.title ?: "No current programme"}"; epgNext.text = "NEXT  ${next?.title ?: "No upcoming programme"}"; epgProgress.progress = current?.let { (((now - it.startUtcMs).toDouble() / (it.endUtcMs - it.startUtcMs).coerceAtLeast(1L)) * 100).toInt().coerceIn(0, 100) } ?: 0; epgMeta.text = "${channel.name}  •  ${DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(now))}" }
    private fun selectFavorites() { val favorites = allChannels.filter { try { UserLibraryStore.isFavorite(this, config, it.toLibraryItem()) } catch (_: Exception) { false } }; selectedCategory = null; list.adapter = object : ArrayAdapter<IptvChannel>(this, android.R.layout.simple_list_item_1, favorites) { override fun getView(position: Int, convertView: View?, parent: ViewGroup) = TextView(this@LiveTvActivity).apply { text = "★  ${getItem(position)?.name ?: "Favorite"}"; textSize = 15f; setTextColor(Color.WHITE); setPadding(14, 12, 14, 12); background = roundedBackground(row, 12f) } }; status.text = "★ Favorites  •  ${favorites.size} channels"; if (favorites.isNotEmpty()) { selectedIndex = 0; list.setSelection(0) } }
    private fun moveChannel(delta: Int) { val channels = filteredChannels(); if (channels.isEmpty()) return; val current = list.selectedItemPosition.takeIf { it >= 0 } ?: selectedIndex; selectedIndex = (current + delta).coerceIn(0, channels.lastIndex); list.setSelection(selectedIndex); showEpg(channels[selectedIndex]) }
    private fun roundedBackground(color: Int, radiusDp: Float) = GradientDrawable().apply { setColor(color); cornerRadius = radiusDp * resources.displayMetrics.density }
    private fun animateFocus(view: View, focused: Boolean) { ObjectAnimator.ofFloat(view, "scaleX", if (focused) 1.03f else 1f).setDuration(120).start(); ObjectAnimator.ofFloat(view, "scaleY", if (focused) 1.03f else 1f).setDuration(120).start() }
    override fun onBackPressed() { if (search.hasFocus() && search.text.isNotEmpty()) { search.text.clear(); list.requestFocus(); return }; super.onBackPressed() }
    override fun onDestroy() { mainHandler.removeCallbacks(ticker); previewPlayer?.release(); previewPlayer = null; executor.shutdownNow(); super.onDestroy() }
}
