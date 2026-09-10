package com.alfietv.player

import android.content.Context
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
import android.widget.TextView
import androidx.activity.ComponentActivity
import java.security.MessageDigest
import java.text.DateFormat
import java.util.Date
import java.util.concurrent.Executors

class LiveTvActivity : ComponentActivity() {
    private val executor = Executors.newSingleThreadExecutor()
    private val mainHandler = Handler(Looper.getMainLooper())
    private lateinit var list: ListView
    private lateinit var status: TextView
    private lateinit var epg: TextView
    private lateinit var search: EditText
    private lateinit var categoryRow: LinearLayout
    private var allChannels = emptyList<IptvChannel>()
    private var selectedCategory: String? = null
    private lateinit var config: XtreamConfig
    private var restoredLastChannel = false
    private var baseStatus = "Loading channels..."
    private var selectedEpgChannel: IptvChannel? = null

    private val epgTicker = object : Runnable {
        override fun run() {
            refreshSelectedEpgDisplay()
            mainHandler.postDelayed(this, 60_000L)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        config = XtreamConfig(intent.getStringExtra("server") ?: "", intent.getStringExtra("username") ?: "", intent.getStringExtra("password") ?: "")
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(20, 16, 20, 16) }
        val header = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        val title = TextView(this).apply { text = "Live TV"; textSize = 26f; isFocusable = false }
        search = EditText(this).apply { hint = "Search channels"; setSingleLine(true); isFocusable = true; isFocusableInTouchMode = true; imeOptions = android.view.inputmethod.EditorInfo.IME_ACTION_DONE }
        header.addView(title, LinearLayout.LayoutParams(0, -2, 1f)); header.addView(search, LinearLayout.LayoutParams(0, -2, 2f))

        val categoryScroll = HorizontalScrollView(this).apply { isFocusable = false; isHorizontalScrollBarEnabled = false }
        categoryRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; isFocusable = false }
        categoryScroll.addView(categoryRow)
        addCategoryButton("All", null)

        epg = TextView(this).apply { text = "Select a channel to view program information"; textSize = 16f; setPadding(8, 10, 8, 10); isFocusable = false; maxLines = 4 }
        list = ListView(this).apply {
            isFocusable = true; isFocusableInTouchMode = true; dividerHeight = 1
            setOnKeyListener { _, keyCode, event ->
                if (event.action != KeyEvent.ACTION_DOWN) return@setOnKeyListener false
                when (keyCode) {
                    KeyEvent.KEYCODE_CHANNEL_UP -> { moveChannel(-1); true }
                    KeyEvent.KEYCODE_CHANNEL_DOWN -> { moveChannel(1); true }
                    KeyEvent.KEYCODE_DPAD_LEFT -> { categoryRow.getChildAt(0)?.requestFocus() ?: false }
                    else -> false
                }
            }
        }
        status = TextView(this).apply { text = baseStatus; textSize = 14f; isFocusable = false; setPadding(8, 8, 8, 4) }
        root.addView(header)
        root.addView(categoryScroll, LinearLayout.LayoutParams(-1, -2))
        root.addView(epg, LinearLayout.LayoutParams(-1, -2))
        root.addView(list, LinearLayout.LayoutParams(-1, 0, 1f))
        root.addView(status)
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
        load(categoryRow)
        mainHandler.post(epgTicker)
    }

    private fun addCategoryButton(name: String, id: String?) {
        val button = Button(this).apply {
            text = name
            isAllCaps = false
            isFocusable = true
            isFocusableInTouchMode = true
            setOnClickListener { selectedCategory = id; setBaseStatus("${filteredChannels().size} channels • Select to play • Long-press to favorite"); render(); list.requestFocus() }
            setOnKeyListener { _, keyCode, event ->
                if (event.action == KeyEvent.ACTION_DOWN && keyCode == KeyEvent.KEYCODE_DPAD_DOWN) { list.requestFocus(); true } else false
            }
        }
        categoryRow.addView(button, LinearLayout.LayoutParams(-2, -2).apply { marginEnd = 8 })
    }

    private fun load(categoryRow: LinearLayout) {
        val cached = LiveTvCache.read(this, config)
        if (cached != null) {
            applyChannels(cached.categories, cached.channels, categoryRow)
            setBaseStatus("${cached.channels.size} channels • Cached ${LiveTvCache.ageText(cached)} • Refreshing...")
        } else setBaseStatus("Loading channels...")
        executor.execute {
            try {
                val (categories, channels) = XtreamClient().load(config)
                LiveTvCache.write(this, config, categories, channels)
                runOnUiThread {
                    applyChannels(categories, channels, categoryRow)
                    setBaseStatus("${channels.size} channels • Updated just now • OK to play • Long-press favorite")
                }
            } catch (e: Exception) {
                runOnUiThread { setBaseStatus(if (allChannels.isNotEmpty()) "${allChannels.size} channels • Offline cache • Refresh failed: ${e.message ?: "unknown error"}" else "Unable to load channels: ${e.message ?: "unknown error"}") }
            }
        }
    }

    private fun applyChannels(categories: List<IptvCategory>, channels: List<IptvChannel>, categoryRow: LinearLayout) {
        allChannels = channels
        migrateLegacyFavorites(channels)
        while (categoryRow.childCount > 1) categoryRow.removeViewAt(1)
        categories.forEach { category -> addCategoryButton(category.name, category.id) }
        if (selectedCategory != null && categories.none { it.id == selectedCategory }) selectedCategory = null
        render()
        if (!restoredLastChannel) {
            restoredLastChannel = true
            prefs.getString(lastChannelKey(), null)?.let { id -> channels.firstOrNull { it.id == id }?.let(::showEpg) }
        }
    }

    private val prefs: android.content.SharedPreferences
        get() = getSharedPreferences("alfie_tv", Context.MODE_PRIVATE)

    private fun lastChannelKey(): String = "last_channel_${providerKey()}"

    private fun providerKey(): String = sha256(config.serverUrl.trimEnd('/') + "\n" + config.username)

    private fun sha256(value: String): String = MessageDigest.getInstance("SHA-256")
        .digest(value.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }

    private fun migrateLegacyFavorites(channels: List<IptvChannel>) {
        if (prefs.getBoolean("favorites_migrated_v2", false)) return
        val legacy = prefs.getStringSet("favorites", emptySet()).orEmpty()
        legacy.forEach { id -> channels.firstOrNull { it.id == id }?.let { UserLibraryStore.toggleFavorite(this, config, it.toLibraryItem()) } }
        prefs.edit().putBoolean("favorites_migrated_v2", true).apply()
    }

    private fun filteredChannels(): List<IptvChannel> {
        val query = search.text.toString().trim().lowercase()
        return allChannels.filter { channel -> (selectedCategory == null || channel.categoryId == selectedCategory) && (query.isBlank() || channel.name.lowercase().contains(query)) }
    }

    private fun render() {
        val channels = filteredChannels()
        list.adapter = object : ArrayAdapter<IptvChannel>(this, android.R.layout.simple_list_item_1, channels) {
            override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
                val row = LinearLayout(this@LiveTvActivity).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    setPadding(14, 8, 14, 8)
                    minimumHeight = 72
                }
                val icon = ImageView(this@LiveTvActivity).apply {
                    layoutParams = LinearLayout.LayoutParams(56, 56).apply { marginEnd = 16 }
                    scaleType = ImageView.ScaleType.CENTER_INSIDE
                }
                val label = TextView(this@LiveTvActivity).apply { textSize = 18f; isFocusable = false }
                val channel = getItem(position) ?: return row
                label.text = "${position + 1}. ${if (UserLibraryStore.isFavorite(this@LiveTvActivity, config, channel.toLibraryItem())) "★ " else ""}${channel.name}"
                row.addView(icon); row.addView(label, LinearLayout.LayoutParams(0, -2, 1f))
                ArtworkLoader.load(channel.logoUrl, icon, android.R.drawable.ic_menu_gallery)
                return row
            }
        }
        status.text = baseStatus
        list.post {
            if (!search.hasFocus()) {
                list.requestFocus()
                if (list.count > 0 && list.selectedItemPosition < 0) list.setSelection(0)
            }
        }
        refreshSelectedEpgDisplay()
    }

    private fun setBaseStatus(value: String) {
        baseStatus = value
        if (::status.isInitialized) status.text = value
    }

    private fun moveChannel(delta: Int) {
        val count = list.adapter?.count ?: 0
        if (count == 0) return
        val current = list.selectedItemPosition.takeIf { it >= 0 } ?: 0
        val next = (current + delta).coerceIn(0, count - 1)
        list.setSelection(next)
        filteredChannels().getOrNull(next)?.let(::showEpg)
    }

    private fun play(channel: IptvChannel) {
        val channels = filteredChannels(); val index = channels.indexOfFirst { it.id == channel.id }.coerceAtLeast(0); val windowStart = (index - 50).coerceAtLeast(0); val windowEnd = (index + 51).coerceAtMost(channels.size); val window = channels.subList(windowStart, windowEnd)
        prefs.edit().putString(lastChannelKey(), channel.id).apply(); showEpg(channel)
        UserLibraryStore.recordWatched(this, config, channel.toLibraryItem())
        startActivity(android.content.Intent(this, MainActivity::class.java).apply {
            putExtra("stream_url", channel.streamUrl); putExtra("title", channel.name); putExtra("content_id", channel.id); putExtra("content_type", UserLibraryStore.Type.LIVE.name)
            putExtra("channel_number", (index + 1).toString()); putExtra("channel_index", index - windowStart)
            putExtra("channel_urls", ArrayList(window.map { it.streamUrl })); putExtra("channel_titles", ArrayList(window.map { it.name })); putExtra("channel_ids", ArrayList(window.map { it.id })); putExtra("channel_numbers", ArrayList(window.mapIndexed { i, _ -> (windowStart + i + 1).toString() }))
        })
    }

    private fun IptvChannel.toLibraryItem() = UserLibraryStore.Item(id, UserLibraryStore.Type.LIVE, name, streamUrl, categoryId, logoUrl)

    private fun showEpg(channel: IptvChannel) {
        selectedEpgChannel = channel
        val cached = EpgCache.read(this, config, channel)
        val cachedText = cached?.let { formatPrograms(channel, it.programs, it.savedAt) }
        epg.text = cachedText?.let { if (EpgCache.isFresh(cached)) it else "$it\nRefreshing guide..." }
            ?: "${channel.name}\nLoading program guide..."
        cachedText?.let { prefs.edit().putString("epg_${channel.id}", it).apply() }
        if (cached != null && EpgCache.isFresh(cached)) return
        executor.execute {
            try {
                val programs = XtreamClient().loadEpg(config, channel)
                EpgCache.write(this, config, channel, programs)
                val text = formatPrograms(channel, programs, System.currentTimeMillis())
                prefs.edit().putString("epg_${channel.id}", text).apply()
                runOnUiThread { if (selectedEpgChannel?.id == channel.id) epg.text = text }
            } catch (_: Exception) {
                runOnUiThread { if (selectedEpgChannel?.id == channel.id && cached == null) epg.text = "${channel.name}\nEPG unavailable" }
            }
        }
    }

    private fun refreshSelectedEpgDisplay() {
        val channel = selectedEpgChannel ?: return
        val cached = EpgCache.read(this, config, channel) ?: return
        epg.text = formatPrograms(channel, cached.programs, cached.savedAt)
        prefs.edit().putString("epg_${channel.id}", epg.text.toString()).apply()
    }

    private fun formatPrograms(channel: IptvChannel, programs: List<EpgProgram>, savedAt: Long): String {
        val now = System.currentTimeMillis()
        val ordered = programs.sortedBy { it.startUtcMs }
        val current = ordered.firstOrNull { now in it.startUtcMs until it.endUtcMs }
        val next = ordered.firstOrNull { it.startUtcMs > now }
        val nowText = current?.let { "NOW  ${it.title}  ${formatTime(it.startUtcMs)}–${formatTime(it.endUtcMs)}" } ?: "NOW  No current program"
        val nextText = next?.let { "NEXT ${it.title}  ${formatTime(it.startUtcMs)}" } ?: "NEXT No upcoming program"
        val progress = current?.let { val duration = (it.endUtcMs - it.startUtcMs).coerceAtLeast(1L); ((now - it.startUtcMs).coerceIn(0L, duration) * 100 / duration).toInt() } ?: 0
        val age = if (savedAt > 0L) " • Guide ${EpgCache.ageText(EpgCache.Snapshot(ordered, savedAt))}" else ""
        return "${channel.name}\n$nowText\n$nextText\nProgress: $progress%$age"
    }

    private fun formatTime(ms: Long): String = DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(ms))

    override fun onDestroy() {
        mainHandler.removeCallbacks(epgTicker)
        executor.shutdownNow()
        super.onDestroy()
    }
}
