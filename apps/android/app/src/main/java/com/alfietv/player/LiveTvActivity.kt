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
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.TextView
import androidx.activity.ComponentActivity
import java.security.MessageDigest
import java.text.DateFormat
import java.util.Date
import java.util.concurrent.Executors

/** TV-first Live TV guide: full-screen scrolling playlist with animated focus and live EPG panel. */
class LiveTvActivity : ComponentActivity() {
    private val executor = Executors.newSingleThreadExecutor()
    private val mainHandler = Handler(Looper.getMainLooper())
    private lateinit var list: ListView
    private lateinit var status: TextView
    private lateinit var epgContainer: LinearLayout
    private lateinit var epgTitle: TextView
    private lateinit var epgNow: TextView
    private lateinit var epgNext: TextView
    private lateinit var epgProgress: ProgressBar
    private lateinit var epgMeta: TextView
    private lateinit var search: EditText
    private lateinit var categoryRow: LinearLayout
    private var allChannels = emptyList<IptvChannel>()
    private var selectedCategory: String? = null
    private lateinit var config: XtreamConfig
    private var restoredLastChannel = false
    private var baseStatus = "Loading channels..."
    private var selectedEpgChannel: IptvChannel? = null
    private var selectedIndex = -1

    private val bg = Color.rgb(5, 9, 18)
    private val panel = Color.rgb(13, 21, 35)
    private val row = Color.rgb(15, 24, 40)
    private val accent = Color.rgb(0, 168, 255)
    private val muted = Color.rgb(170, 181, 200)

    private val epgTicker = object : Runnable {
        override fun run() {
            refreshSelectedEpgDisplay()
            mainHandler.postDelayed(this, 30_000L)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = bg
        window.navigationBarColor = bg
        config = XtreamConfig(intent.getStringExtra("server") ?: "", intent.getStringExtra("username") ?: "", intent.getStringExtra("password") ?: "")
        buildUi()
        load()
        mainHandler.post(epgTicker)
    }

    private fun buildUi() {
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(18, 14, 18, 10); setBackgroundColor(bg) }
        val header = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        header.addView(TextView(this).apply { text = "Live TV"; textSize = 28f; setTextColor(Color.WHITE); typeface = Typeface.DEFAULT_BOLD }, LinearLayout.LayoutParams(0, -2, 1f))
        search = EditText(this).apply { hint = "Search channels..."; setSingleLine(true); textSize = 16f; setTextColor(Color.WHITE); setHintTextColor(muted); isFocusable = true; isFocusableInTouchMode = true; imeOptions = android.view.inputmethod.EditorInfo.IME_ACTION_DONE; background = roundedBackground(panel, 12f); setPadding(18, 0, 18, 0) }
        header.addView(search, LinearLayout.LayoutParams(0, 52, 1.7f))
        root.addView(header, LinearLayout.LayoutParams(-1, 56))

        val categoryScroll = HorizontalScrollView(this).apply { isHorizontalScrollBarEnabled = false; overScrollMode = View.OVER_SCROLL_IF_CONTENT_SCROLLS; isFocusable = false }
        categoryRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        categoryScroll.addView(categoryRow)
        addCategoryButton("All", null)
        root.addView(categoryScroll, LinearLayout.LayoutParams(-1, 52).apply { bottomMargin = 6 })

        val content = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        list = ListView(this).apply {
            isFocusable = true; isFocusableInTouchMode = true; divider = null; dividerHeight = 0; clipToPadding = false; setPadding(0, 2, 8, 2)
            setOnKeyListener { _, keyCode, event ->
                if (event.action != KeyEvent.ACTION_DOWN) return@setOnKeyListener false
                when (keyCode) {
                    KeyEvent.KEYCODE_CHANNEL_UP -> { moveChannel(-1); true }
                    KeyEvent.KEYCODE_CHANNEL_DOWN -> { moveChannel(1); true }
                    KeyEvent.KEYCODE_DPAD_RIGHT -> { epgContainer.requestFocus(); true }
                    KeyEvent.KEYCODE_DPAD_UP -> if (selectedItemPosition == 0) { categoryRow.getChildAt(0)?.requestFocus(); true } else false
                    else -> false
                }
            }
            setOnFocusChangeListener { _, focused -> if (focused && selectedItemPosition >= 0) filteredChannels().getOrNull(selectedItemPosition)?.let(::showEpg) }
        }
        content.addView(list, LinearLayout.LayoutParams(0, 0, 1.62f))

        val epgScroll = ScrollView(this).apply { isFillViewport = true; isFocusable = false; overScrollMode = View.OVER_SCROLL_IF_CONTENT_SCROLLS }
        epgContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; setPadding(18, 18, 18, 18); setBackgroundColor(panel); isFocusable = true; isFocusableInTouchMode = true
            setOnKeyListener { _, keyCode, event ->
                if (event.action == KeyEvent.ACTION_DOWN && keyCode == KeyEvent.KEYCODE_DPAD_LEFT) { list.requestFocus(); true } else false
            }
        }
        epgTitle = TextView(this).apply { text = "EPG"; textSize = 21f; setTextColor(Color.WHITE); typeface = Typeface.DEFAULT_BOLD }
        epgNow = TextView(this).apply { textSize = 17f; setTextColor(accent); setPadding(0, 18, 0, 8) }
        epgProgress = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply { max = 100; progress = 0; isIndeterminate = false }
        epgNext = TextView(this).apply { textSize = 15f; setTextColor(Color.WHITE); setPadding(0, 14, 0, 8) }
        epgMeta = TextView(this).apply { textSize = 13f; setTextColor(muted); setPadding(0, 10, 0, 0) }
        epgContainer.addView(epgTitle)
        epgContainer.addView(epgNow)
        epgContainer.addView(epgProgress, LinearLayout.LayoutParams(-1, 8))
        epgContainer.addView(epgNext)
        epgContainer.addView(epgMeta)
        epgScroll.addView(epgContainer)
        content.addView(epgScroll, LinearLayout.LayoutParams(0, 0, 0.92f).apply { leftMargin = 8 })
        root.addView(content, LinearLayout.LayoutParams(-1, 0, 1f))

        status = TextView(this).apply { text = baseStatus; textSize = 13f; setTextColor(muted); setPadding(4, 7, 4, 2) }
        root.addView(status, LinearLayout.LayoutParams(-1, 28))
        setContentView(root)

        search.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) { render() }
            override fun afterTextChanged(s: Editable?) = Unit
        })
        search.setOnEditorActionListener { _, _, _ -> list.requestFocus(); true }
        list.setOnItemClickListener { _, _, position, _ -> filteredChannels().getOrNull(position)?.let(::play) }
        list.setOnItemLongClickListener { _, _, position, _ ->
            filteredChannels().getOrNull(position)?.let { channel ->
                val added = UserLibraryStore.toggleFavorite(this, config, channel.toLibraryItem())
                setBaseStatus(if (added) "★ Added to favorites: ${channel.name}" else "Removed from favorites: ${channel.name}")
                render()
            }
            true
        }
    }

    private fun addCategoryButton(name: String, id: String?) {
        val button = Button(this).apply {
            text = name; isAllCaps = false; textSize = 14f; setTextColor(Color.WHITE); background = roundedBackground(row, 12f); isFocusable = true; isFocusableInTouchMode = true; stateListAnimator = null
            setPadding(18, 0, 18, 0)
            setOnFocusChangeListener { view, focused -> view.background = roundedBackground(if (focused) accent else row, 12f); animateFocus(view, focused) }
            setOnClickListener { selectedCategory = id; setBaseStatus("${filteredChannels().size} channels • OK to play • Long-press favorite"); render(); list.requestFocus() }
            setOnKeyListener { _, keyCode, event -> if (event.action == KeyEvent.ACTION_DOWN && keyCode == KeyEvent.KEYCODE_DPAD_DOWN) { list.requestFocus(); true } else false }
        }
        categoryRow.addView(button, LinearLayout.LayoutParams(-2, 46).apply { marginEnd = 8 })
    }

    private fun load() {
        val cached = LiveTvCache.read(this, config)
        if (cached != null) {
            applyChannels(cached.categories, cached.channels)
            setBaseStatus("${cached.channels.size} channels • Cached ${LiveTvCache.ageText(cached)} • Refreshing...")
        }
        executor.execute {
            try {
                val (categories, channels) = XtreamClient().load(config)
                LiveTvCache.write(this, config, categories, channels)
                runOnUiThread { applyChannels(categories, channels); setBaseStatus("${channels.size} channels • Updated just now • OK to play • Long-press favorite") }
            } catch (e: Exception) {
                runOnUiThread { setBaseStatus(if (allChannels.isNotEmpty()) "${allChannels.size} channels • Offline cache • Refresh failed" else "Unable to load channels: ${e.message ?: "unknown error"}") }
            }
        }
    }

    private fun applyChannels(categories: List<IptvCategory>, channels: List<IptvChannel>) {
        allChannels = channels
        migrateLegacyFavorites(channels)
        while (categoryRow.childCount > 1) categoryRow.removeViewAt(1)
        categories.forEach { addCategoryButton(it.name, it.id) }
        if (selectedCategory != null && categories.none { it.id == selectedCategory }) selectedCategory = null
        render()
        if (!restoredLastChannel) {
            restoredLastChannel = true
            prefs.getString(lastChannelKey(), null)?.let { id -> channels.firstOrNull { it.id == id }?.let(::showEpg) }
        }
    }

    private val prefs: android.content.SharedPreferences get() = getSharedPreferences("alfie_tv", Context.MODE_PRIVATE)
    private fun lastChannelKey(): String = "last_channel_${providerKey()}"
    private fun providerKey(): String = sha256(config.serverUrl.trimEnd('/') + "\n" + config.username)
    private fun sha256(value: String): String = MessageDigest.getInstance("SHA-256").digest(value.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
    private fun migrateLegacyFavorites(channels: List<IptvChannel>) {
        if (prefs.getBoolean("favorites_migrated_v2", false)) return
        prefs.getStringSet("favorites", emptySet()).orEmpty().forEach { id -> channels.firstOrNull { it.id == id }?.let { UserLibraryStore.toggleFavorite(this, config, it.toLibraryItem()) } }
        prefs.edit().putBoolean("favorites_migrated_v2", true).apply()
    }
    private fun filteredChannels(): List<IptvChannel> { val query = search.text.toString().trim().lowercase(); return allChannels.filter { (selectedCategory == null || it.categoryId == selectedCategory) && (query.isBlank() || it.name.lowercase().contains(query)) } }

    private fun render() {
        val channels = filteredChannels()
        list.adapter = object : ArrayAdapter<IptvChannel>(this, android.R.layout.simple_list_item_1, channels) {
            override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
                val channel = getItem(position) ?: return TextView(this@LiveTvActivity)
                val focused = position == selectedIndex
                val item = LinearLayout(this@LiveTvActivity).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; setPadding(14, 6, 12, 6); minimumHeight = 70; background = roundedBackground(if (focused) Color.rgb(0, 85, 160) else row, 12f); isFocusable = false }
                val icon = ImageView(this@LiveTvActivity).apply { layoutParams = LinearLayout.LayoutParams(50, 50).apply { marginEnd = 12 }; scaleType = ImageView.ScaleType.CENTER_INSIDE }
                val textBox = LinearLayout(this@LiveTvActivity).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER_VERTICAL }
                val name = TextView(this@LiveTvActivity).apply { textSize = 17f; setTextColor(Color.WHITE); typeface = Typeface.DEFAULT_BOLD }
                val sub = TextView(this@LiveTvActivity).apply { textSize = 12f; setTextColor(if (focused) Color.WHITE else muted); setPadding(0, 3, 0, 0) }
                name.text = "${position + 1}. ${if (UserLibraryStore.isFavorite(this@LiveTvActivity, config, channel.toLibraryItem())) "★ " else ""}${channel.name}"
                sub.text = if (focused) "NOW • EPG shown at right • OK to play" else "Select for live program information"
                textBox.addView(name); textBox.addView(sub)
                item.addView(icon); item.addView(textBox, LinearLayout.LayoutParams(0, -2, 1f))
                ArtworkLoader.load(channel.logoUrl, icon, android.R.drawable.ic_menu_gallery)
                item.setOnFocusChangeListener { view, hasFocus -> if (hasFocus) { selectedIndex = position; animateFocus(view, true); showEpg(channel); renderSelectionOnly() } else animateFocus(view, false) }
                return item
            }
        }
        list.post {
            if (!search.hasFocus()) {
                list.requestFocus()
                if (list.count > 0 && list.selectedItemPosition < 0) list.setSelection(0)
            }
        }
        status.text = baseStatus
    }

    private fun renderSelectionOnly() { list.invalidateViews() }
    private fun animateFocus(view: View, focused: Boolean) { ObjectAnimator.ofFloat(view, View.SCALE_X, view.scaleX, if (focused) 1.02f else 1f).setDuration(120).start(); ObjectAnimator.ofFloat(view, View.SCALE_Y, view.scaleY, if (focused) 1.02f else 1f).setDuration(120).start() }
    private fun setBaseStatus(value: String) { baseStatus = value; if (::status.isInitialized) status.text = value }
    private fun moveChannel(delta: Int) { val count = list.adapter?.count ?: 0; if (count == 0) return; val current = list.selectedItemPosition.takeIf { it >= 0 } ?: 0; val next = (current + delta).coerceIn(0, count - 1); list.setSelection(next); filteredChannels().getOrNull(next)?.let { selectedIndex = next; showEpg(it); list.invalidateViews() } }

    private fun showEpg(channel: IptvChannel) {
        selectedEpgChannel = channel
        epgTitle.text = channel.name
        epgNow.text = "Loading EPG…"
        epgNext.text = "NEXT  —"
        epgProgress.progress = 0
        epgMeta.text = "Program guide • ${channel.categoryId ?: "Live TV"}"
        animatePanel()
        val cached = EpgCache.read(this, config, channel)
        cached?.let { epgContainer.post { renderEpg(channel, it.programs, it.savedAt) } }
        if (cached != null && EpgCache.isFresh(cached)) return
        executor.execute {
            try {
                val programs = XtreamClient().loadEpg(config, channel)
                EpgCache.write(this, config, channel, programs)
                runOnUiThread { if (selectedEpgChannel?.id == channel.id) renderEpg(channel, programs, System.currentTimeMillis()) }
            } catch (_: Exception) {
                runOnUiThread { if (selectedEpgChannel?.id == channel.id && cached == null) { epgNow.text = "EPG unavailable"; epgNext.text = "Try refreshing the provider" } }
            }
        }
    }

    private fun renderEpg(channel: IptvChannel, programs: List<EpgProgram>, savedAt: Long) {
        val now = System.currentTimeMillis(); val ordered = programs.sortedBy { it.startUtcMs }
        val current = ordered.firstOrNull { now in it.startUtcMs until it.endUtcMs }; val next = ordered.firstOrNull { it.startUtcMs > now }
        epgTitle.text = channel.name
        epgNow.text = current?.let { "NOW  ${it.title}\n${formatTime(it.startUtcMs)} – ${formatTime(it.endUtcMs)}" } ?: "NOW  No current program"
        epgNext.text = next?.let { "NEXT  ${it.title}\n${formatTime(it.startUtcMs)} – ${formatTime(it.endUtcMs)}" } ?: "NEXT  No upcoming program"
        val progress = current?.let { val duration = (it.endUtcMs - it.startUtcMs).coerceAtLeast(1L); ((now - it.startUtcMs).coerceIn(0L, duration) * 100 / duration).toInt() } ?: 0
        epgProgress.progress = progress
        epgMeta.text = "EPG • $progress% through current program • Guide ${EpgCache.ageText(EpgCache.Snapshot(ordered, savedAt))}"
        epgContainer.setBackgroundColor(panel)
    }

    private fun refreshSelectedEpgDisplay() { val channel = selectedEpgChannel ?: return; EpgCache.read(this, config, channel)?.let { renderEpg(channel, it.programs, it.savedAt) } }
    private fun animatePanel() { ObjectAnimator.ofFloat(epgContainer, View.ALPHA, 0.65f, 1f).setDuration(180).start() }
    private fun formatTime(ms: Long): String = DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(ms))

    private fun play(channel: IptvChannel) {
        val channels = filteredChannels(); val index = channels.indexOfFirst { it.id == channel.id }.coerceAtLeast(0); val windowStart = (index - 50).coerceAtLeast(0); val windowEnd = (index + 51).coerceAtMost(channels.size); val window = channels.subList(windowStart, windowEnd)
        prefs.edit().putString(lastChannelKey(), channel.id).apply(); showEpg(channel); UserLibraryStore.recordWatched(this, config, channel.toLibraryItem())
        startActivity(android.content.Intent(this, MainActivity::class.java).apply {
            putExtra("stream_url", channel.streamUrl); putExtra("title", channel.name); putExtra("content_id", channel.id); putExtra("content_type", UserLibraryStore.Type.LIVE.name)
            putExtra("channel_number", (index + 1).toString()); putExtra("channel_index", index - windowStart)
            putExtra("channel_urls", ArrayList(window.map { it.streamUrl })); putExtra("channel_titles", ArrayList(window.map { it.name })); putExtra("channel_ids", ArrayList(window.map { it.id })); putExtra("channel_numbers", ArrayList(window.mapIndexed { i, _ -> (windowStart + i + 1).toString() }))
        })
    }

    private fun IptvChannel.toLibraryItem() = UserLibraryStore.Item(id, UserLibraryStore.Type.LIVE, name, streamUrl, categoryId, logoUrl)
    private fun roundedBackground(color: Int, radiusDp: Float): GradientDrawable = GradientDrawable().apply { setColor(color); cornerRadius = radiusDp * resources.displayMetrics.density }

    override fun onDestroy() { mainHandler.removeCallbacks(epgTicker); executor.shutdownNow(); super.onDestroy() }
}
