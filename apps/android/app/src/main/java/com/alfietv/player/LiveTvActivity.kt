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
import android.widget.GridView
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.media3.ui.PlayerView
import java.security.MessageDigest
import java.text.DateFormat
import java.util.Date
import java.util.concurrent.Executors

/** TV-first Live TV screen with channel layouts, inline preview and live EPG panel. */
class LiveTvActivity : ComponentActivity() {
    private val executor = Executors.newSingleThreadExecutor()
    private val mainHandler = Handler(Looper.getMainLooper())
    private lateinit var list: GridView
    private lateinit var status: TextView
    private lateinit var epgContainer: LinearLayout
    private lateinit var epgTitle: TextView
    private lateinit var epgNow: TextView
    private lateinit var epgNext: TextView
    private lateinit var epgProgress: ProgressBar
    private lateinit var epgMeta: TextView
    private lateinit var search: EditText
    private lateinit var categoryRow: LinearLayout
    private lateinit var previewView: PlayerView
    private var previewPlayer: AlfiePlayer? = null
    private var previewChannelId: String? = null
    private var pendingPreviewHandled = false
    private var currentLayoutMode: LayoutMode = LayoutMode.LIST
    private var allChannels = emptyList<IptvChannel>()
    private var selectedCategory: String? = null
    private lateinit var config: XtreamConfig
    private var restoredLastChannel = false
    private var baseStatus = "Loading channels..."
    private var selectedEpgChannel: IptvChannel? = null
    private var selectedIndex = -1
    private var fullscreenLaunchInProgress = false

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
        currentLayoutMode = LayoutModeStore.get(this, "live", LayoutMode.LIST)
        buildUi()
        load()
        mainHandler.post(epgTicker)
    }

    private fun buildUi() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(18, 14, 18, 10)
            setBackgroundColor(bg)
        }
        val header = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        header.addView(TextView(this).apply {
            text = "Live TV"; textSize = 28f; setTextColor(Color.WHITE); typeface = Typeface.DEFAULT_BOLD
        }, LinearLayout.LayoutParams(0, -2, 1f))
        search = EditText(this).apply {
            hint = "Search channels..."; setSingleLine(true); textSize = 16f; setTextColor(Color.WHITE)
            setHintTextColor(muted); isFocusable = true; isFocusableInTouchMode = true
            imeOptions = android.view.inputmethod.EditorInfo.IME_ACTION_DONE
            background = roundedBackground(panel, 12f); setPadding(18, 0, 18, 0)
        }
        header.addView(search, LinearLayout.LayoutParams(0, 52, 1.7f))
        root.addView(header, LinearLayout.LayoutParams(-1, 56))

        val categoryScroll = HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false; overScrollMode = View.OVER_SCROLL_IF_CONTENT_SCROLLS; isFocusable = false
        }
        categoryRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        categoryScroll.addView(categoryRow)
        addCategoryButton("All", null)
        root.addView(categoryScroll, LinearLayout.LayoutParams(-1, 52).apply { bottomMargin = 2 })

        val layoutRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        layoutRow.addView(TextView(this).apply {
            text = "View"; textSize = 13f; setTextColor(muted); gravity = Gravity.CENTER_VERTICAL; setPadding(4, 0, 8, 0)
        }, LinearLayout.LayoutParams(-2, 44))
        layoutRow.addView(LayoutModeControls.create(this, "live", LayoutMode.LIST) { selected ->
            currentLayoutMode = selected
            applyLayoutMode()
            render()
            setBaseStatus("${filteredChannels().size} channels • ${layoutModeLabel() } view")
            list.requestFocus()
        }, LinearLayout.LayoutParams(0, 44, 1f))
        root.addView(layoutRow, LinearLayout.LayoutParams(-1, 46).apply { bottomMargin = 6 })

        val content = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        list = GridView(this).apply {
            isFocusable = true; isFocusableInTouchMode = true; clipToPadding = false
            verticalSpacing = 8; horizontalSpacing = 8; stretchMode = GridView.STRETCH_COLUMN_WIDTH
            setPadding(0, 2, 8, 2)
            setOnKeyListener { _, keyCode, event ->
                if (event.action != KeyEvent.ACTION_DOWN) return@setOnKeyListener false
                when (keyCode) {
                    KeyEvent.KEYCODE_CHANNEL_UP -> { moveChannel(-1); true }
                    KeyEvent.KEYCODE_CHANNEL_DOWN -> { moveChannel(1); true }
                    KeyEvent.KEYCODE_DPAD_RIGHT -> { epgContainer.requestFocus(); true }
                    KeyEvent.KEYCODE_DPAD_UP -> {
                        val columns = maxOf(1, numColumns)
                        if (selectedItemPosition in 0 until columns) {
                            categoryRow.getChildAt(0)?.requestFocus(); true
                        } else false
                    }
                    else -> false
                }
            }
            setOnFocusChangeListener { _, focused ->
                if (focused && selectedItemPosition >= 0) filteredChannels().getOrNull(selectedItemPosition)?.let(::showEpg)
            }
        }
        content.addView(list, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1.62f))

        val epgScroll = ScrollView(this).apply {
            isFillViewport = true; isFocusable = false; overScrollMode = View.OVER_SCROLL_IF_CONTENT_SCROLLS
        }
        epgContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; setPadding(18, 18, 18, 18); setBackgroundColor(panel)
            isFocusable = true; isFocusableInTouchMode = true
            setOnKeyListener { _, keyCode, event ->
                if (event.action == KeyEvent.ACTION_DOWN && keyCode == KeyEvent.KEYCODE_DPAD_LEFT) { list.requestFocus(); true } else false
            }
        }
        previewView = PlayerView(this).apply {
            useController = false
            controllerAutoShow = false
            setShowBuffering(PlayerView.SHOW_BUFFERING_ALWAYS)
            resizeMode = androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_FIT
            isFocusable = false
            setBackgroundColor(Color.BLACK)
        }
        epgContainer.addView(previewView, LinearLayout.LayoutParams(-1, 190).apply { bottomMargin = 10 })
        epgTitle = TextView(this).apply { text = "LIVE PREVIEW"; textSize = 21f; setTextColor(Color.WHITE); typeface = Typeface.DEFAULT_BOLD }
        epgNow = TextView(this).apply { textSize = 17f; setTextColor(accent); setPadding(0, 18, 0, 8) }
        epgProgress = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply { max = 100; progress = 0; isIndeterminate = false }
        epgNext = TextView(this).apply { textSize = 15f; setTextColor(Color.WHITE); setPadding(0, 14, 0, 8) }
        epgMeta = TextView(this).apply { textSize = 13f; setTextColor(muted); setPadding(0, 10, 0, 0) }
        epgContainer.addView(epgTitle); epgContainer.addView(epgNow); epgContainer.addView(epgProgress, LinearLayout.LayoutParams(-1, 8)); epgContainer.addView(epgNext); epgContainer.addView(epgMeta)
        epgScroll.addView(epgContainer)
        content.addView(epgScroll, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 0.92f).apply { leftMargin = 8 })
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

        // IMPORTANT: GridView owns item activation. The child view must not also
        // consume clicks, otherwise one remote/touch press can dispatch twice and
        // turn the first preview activation into an unintended fullscreen launch.
        list.setOnItemClickListener { _, _, position, _ ->
            filteredChannels().getOrNull(position)?.let { channel ->
                selectedIndex = position
                list.setSelection(position)
                showEpg(channel)
                list.invalidateViews()
                handleChannelClick(channel)
            }
        }
        list.setOnItemLongClickListener { _, _, position, _ ->
            filteredChannels().getOrNull(position)?.let { channel ->
                val added = UserLibraryStore.toggleFavorite(this, config, channel.toLibraryItem())
                setBaseStatus(if (added) "★ Added to favorites: ${channel.name}" else "Removed from favorites: ${channel.name}")
                render()
            }
            true
        }
        applyLayoutMode()
    }

    private fun layoutModeLabel(): String = when (currentLayoutMode) {
        LayoutMode.GRID -> "Grid"
        LayoutMode.LIST -> "List"
        LayoutMode.TILE -> "Tile"
    }

    private fun applyLayoutMode() {
        when (currentLayoutMode) {
            LayoutMode.LIST -> { list.numColumns = 1; list.horizontalSpacing = 0; list.verticalSpacing = 8 }
            LayoutMode.GRID -> { list.numColumns = 2; list.horizontalSpacing = 8; list.verticalSpacing = 10 }
            LayoutMode.TILE -> { list.numColumns = 4; list.horizontalSpacing = 8; list.verticalSpacing = 10 }
        }
    }

    private fun addCategoryButton(name: String, id: String?) {
        val button = Button(this).apply {
            text = name; isAllCaps = false; textSize = 14f; setTextColor(Color.WHITE); background = roundedBackground(row, 12f)
            isFocusable = true; isFocusableInTouchMode = true; stateListAnimator = null; setPadding(18, 0, 18, 0)
            setOnFocusChangeListener { view, focused -> view.background = roundedBackground(if (focused) accent else row, 12f); animateFocus(view, focused) }
            setOnClickListener { selectedCategory = id; selectedIndex = -1; setBaseStatus("${filteredChannels().size} channels • OK to preview • OK again for full screen"); render(); list.requestFocus() }
            setOnKeyListener { _, keyCode, event -> if (event.action == KeyEvent.ACTION_DOWN && keyCode == KeyEvent.KEYCODE_DPAD_DOWN) { list.requestFocus(); true } else false }
        }
        categoryRow.addView(button, LinearLayout.LayoutParams(-2, 46).apply { marginEnd = 8 })
    }

    private fun load() {
        val cached = LiveTvCache.read(this, config)
        if (cached != null) { applyChannels(cached.categories, cached.channels); setBaseStatus("${cached.channels.size} channels • Cached ${LiveTvCache.ageText(cached)} • Refreshing...") }
        executor.execute {
            try {
                val (categories, channels) = XtreamClient().load(config)
                LiveTvCache.write(this, config, categories, channels)
                runOnUiThread { applyChannels(categories, channels); setBaseStatus("${channels.size} channels • Updated just now • OK to preview • OK again for full screen") }
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

        val pendingId = intent.getStringExtra("preview_channel_id")
        if (!pendingPreviewHandled && !pendingId.isNullOrBlank()) {
            val pending = channels.firstOrNull { it.id == pendingId }
            if (pending != null) {
                pendingPreviewHandled = true
                selectedIndex = filteredChannels().indexOfFirst { it.id == pending.id }.takeIf { it >= 0 } ?: 0
                list.post {
                    list.setSelection(selectedIndex.coerceAtLeast(0))
                    showEpg(pending)
                    list.invalidateViews()
                    preview(pending)
                }
                setBaseStatus("${pending.name} • Preview playing • Press OK again for full screen")
                return
            }
        }

        if (!restoredLastChannel) {
            restoredLastChannel = true
            val lastId = prefs.getString(lastChannelKey(), null)
            val last = channels.firstOrNull { it.id == lastId }
            if (last != null) {
                selectedIndex = filteredChannels().indexOfFirst { it.id == last.id }.takeIf { it >= 0 } ?: -1
                list.post {
                    val position = filteredChannels().indexOfFirst { it.id == last.id }
                    if (position >= 0) list.setSelection(position)
                    showEpg(last)
                }
                setBaseStatus("Restored last channel: ${last.name} • OK to preview")
            }
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
    private fun filteredChannels(): List<IptvChannel> {
        val query = search.text.toString().trim().lowercase()
        return allChannels.filter { (selectedCategory == null || it.categoryId == selectedCategory) && (query.isBlank() || it.name.lowercase().contains(query)) }
    }

    private fun render() {
        val channels = filteredChannels()
        list.adapter = object : ArrayAdapter<IptvChannel>(this, android.R.layout.simple_list_item_1, channels) {
            override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
                val channel = getItem(position) ?: return TextView(this@LiveTvActivity)
                val focused = position == selectedIndex
                val favorite = UserLibraryStore.isFavorite(this@LiveTvActivity, config, channel.toLibraryItem())
                val item = LinearLayout(this@LiveTvActivity).apply {
                    gravity = Gravity.CENTER
                    setPadding(10, 8, 10, 8)
                    minimumHeight = when (currentLayoutMode) {
                        LayoutMode.LIST -> 70
                        LayoutMode.GRID -> 118
                        LayoutMode.TILE -> 92
                    }
                    orientation = if (currentLayoutMode == LayoutMode.LIST) LinearLayout.HORIZONTAL else LinearLayout.VERTICAL
                    background = roundedBackground(if (focused) Color.rgb(0, 85, 160) else row, 12f)
                    isFocusable = true
                }
                val iconSize = when (currentLayoutMode) {
                    LayoutMode.LIST -> 50
                    LayoutMode.GRID -> 66
                    LayoutMode.TILE -> 58
                }
                val icon = ImageView(this@LiveTvActivity).apply {
                    layoutParams = LinearLayout.LayoutParams(iconSize, iconSize).apply {
                        if (currentLayoutMode == LayoutMode.LIST) marginEnd = 12 else bottomMargin = 6
                    }
                    scaleType = ImageView.ScaleType.CENTER_INSIDE
                }
                val textBox = LinearLayout(this@LiveTvActivity).apply {
                    orientation = LinearLayout.VERTICAL
                    gravity = if (currentLayoutMode == LayoutMode.LIST) Gravity.CENTER_VERTICAL else Gravity.CENTER
                }
                val name = TextView(this@LiveTvActivity).apply {
                    textSize = if (currentLayoutMode == LayoutMode.LIST) 17f else 14f
                    setTextColor(Color.WHITE); typeface = Typeface.DEFAULT_BOLD; gravity = Gravity.CENTER
                    maxLines = if (currentLayoutMode == LayoutMode.TILE) 2 else 3
                }
                val sub = TextView(this@LiveTvActivity).apply {
                    textSize = 11f; setTextColor(if (focused) Color.WHITE else muted); gravity = Gravity.CENTER; setPadding(0, 3, 0, 0)
                    maxLines = 2
                }
                name.text = "${position + 1}. ${if (favorite) "★ " else ""}${channel.name}"
                sub.text = when {
                    channel.epgId != null -> "EPG available"
                    channel.categoryId != null -> channel.categoryId
                    else -> "Live"
                }
                textBox.addView(name); textBox.addView(sub)
                item.addView(icon); item.addView(textBox, LinearLayout.LayoutParams(if (currentLayoutMode == LayoutMode.LIST) 0 else -1, -2, if (currentLayoutMode == LayoutMode.LIST) 1f else 0f))

                // Do not install a child click listener here. GridView's single
                // OnItemClickListener is the only activation path for OK/tap.
                // This prevents duplicate dispatch on Android TV remotes.
                item.setOnFocusChangeListener { view, hasFocus ->
                    if (hasFocus) { selectedIndex = position; showEpg(channel) }
                    view.background = roundedBackground(if (hasFocus) Color.rgb(0, 85, 160) else row, 12f)
                }
                return item
            }
        }
        list.setSelection(selectedIndex.coerceAtLeast(0))
    }

    private fun moveChannel(delta: Int) {
        val channels = filteredChannels()
        if (channels.isEmpty()) return
        val next = (selectedIndex + delta).coerceIn(0, channels.lastIndex)
        selectedIndex = next
        list.setSelection(next)
        channels.getOrNull(next)?.let { showEpg(it) }
        list.invalidateViews()
    }

    /** First OK shows an inline video preview. A second OK on the same channel opens full screen. */
    private fun handleChannelClick(channel: IptvChannel) {
        if (fullscreenLaunchInProgress) return
        if (previewChannelId == channel.id) {
            playFullscreen(channel)
        } else {
            preview(channel)
        }
    }

    private fun preview(channel: IptvChannel) {
        val streamUrl = channel.streamUrl.trim()
        if (streamUrl.isBlank()) {
            setBaseStatus("${channel.name} has no stream URL • Provider refresh required")
            return
        }
        prefs.edit().putString(lastChannelKey(), channel.id).apply()
        previewChannelId = channel.id
        epgTitle.text = "PREVIEW  •  ${channel.name}"
        setBaseStatus("${channel.name} • Preview playing • Press OK again for full screen")
        if (previewPlayer == null) {
            previewPlayer = AlfiePlayer(this).also { it.attach(previewView) }
        }
        previewPlayer?.play(streamUrl, channel.name, channelNumber = ((filteredChannels().indexOfFirst { it.id == channel.id } + 1).coerceAtLeast(1)).toString())
        previewView.post { previewView.requestLayout() }
    }

    private fun playFullscreen(channel: IptvChannel) {
        if (fullscreenLaunchInProgress) return
        val streamUrl = channel.streamUrl.trim()
        if (streamUrl.isBlank()) {
            setBaseStatus("${channel.name} has no stream URL • Provider refresh required")
            return
        }
        fullscreenLaunchInProgress = true
        val channelIndex = filteredChannels().indexOfFirst { it.id == channel.id }.coerceAtLeast(0)
        val channelUrls = ArrayList(filteredChannels().map { it.streamUrl })
        val channelTitles = ArrayList(filteredChannels().map { it.name })
        val channelIds = ArrayList(filteredChannels().map { it.id })
        val channelNumbers = ArrayList(filteredChannels().indices.map { (it + 1).toString() })
        prefs.edit().putString(lastChannelKey(), channel.id).apply()

        // Release the inline player before transferring the same stream to the
        // fullscreen Activity. This avoids two Media3 sessions fighting over the
        // same decoder/audio focus during the handoff.
        previewPlayer?.release()
        previewPlayer = null
        previewView.player = null

        val launchIntent = android.content.Intent(this, VideoPlayerActivity::class.java).apply {
            putExtra("url", streamUrl); putExtra("stream_url", streamUrl); putExtra("title", channel.name)
            putExtra("content_id", channel.id); putExtra("content_type", "LIVE"); putExtra("channel_id", channel.id)
            putExtra("channel_number", (channelIndex + 1).toString()); putExtra("channel_urls", channelUrls)
            putExtra("channel_titles", channelTitles); putExtra("channel_ids", channelIds); putExtra("channel_numbers", channelNumbers)
            putExtra("channel_index", channelIndex); putExtra("server", config.serverUrl); putExtra("username", config.username); putExtra("password", config.password)
        }
        try {
            startActivity(launchIntent)
        } catch (e: Exception) {
            fullscreenLaunchInProgress = false
            setBaseStatus("Unable to open fullscreen player: ${e.message ?: "unknown error"}")
            preview(channel)
        }
    }

    override fun onResume() {
        super.onResume()
        // A failed/cancelled fullscreen launch must never permanently lock the
        // channel activation path. A successful handoff has already released the
        // preview player, so there is nothing to resume here.
        fullscreenLaunchInProgress = false
    }

    private fun showEpg(channel: IptvChannel) {
        selectedEpgChannel = channel
        if (previewChannelId != channel.id) epgTitle.text = "LIVE PREVIEW"
        epgNow.text = "Loading NOW / NEXT…"
        epgNext.text = ""
        epgMeta.text = "EPG channel: ${channel.epgId ?: "not mapped"}"
        epgProgress.progress = 0
        val cached = EpgCache.read(this, config, channel)
        if (cached != null) {
            renderEpg(channel, cached.programs)
            if (EpgCache.isFresh(cached)) return
        }
        executor.execute {
            try {
                val programs = XtreamClient().loadEpg(config, channel)
                EpgCache.write(this, config, channel, programs)
                runOnUiThread { if (selectedEpgChannel?.id == channel.id) renderEpg(channel, programs) }
            } catch (_: Exception) {
                runOnUiThread {
                    if (selectedEpgChannel?.id == channel.id && cached == null) {
                        epgNow.text = "No programme data available"
                        epgNext.text = "Press OK to preview live. Press OK again for full screen."
                        epgProgress.progress = 0
                    }
                }
            }
        }
    }

    private fun refreshSelectedEpgDisplay() {
        selectedEpgChannel?.let { channel ->
            val cached = EpgCache.read(this, config, channel)
            if (cached != null) renderEpg(channel, cached.programs)
        }
    }

    private fun renderEpg(channel: IptvChannel, programs: List<EpgProgram>) {
        val now = System.currentTimeMillis()
        val sorted = programs.sortedBy { it.startUtcMs }
        val current = sorted.firstOrNull { now in it.startUtcMs until it.endUtcMs }
        val next = sorted.firstOrNull { it.startUtcMs > now }
        if (previewChannelId == channel.id) epgTitle.text = "PREVIEW  •  ${channel.name}"
        epgNow.text = if (current != null) "NOW  ${current.title}" else "NOW  No current programme"
        epgNext.text = if (next != null) "NEXT  ${next.title}" else "NEXT  No upcoming programme"
        epgProgress.progress = if (current != null) {
            val duration = (current.endUtcMs - current.startUtcMs).coerceAtLeast(1L)
            (((now - current.startUtcMs).toDouble() / duration) * 100.0).toInt().coerceIn(0, 100)
        } else 0
        epgMeta.text = buildString {
            append(channel.name)
            append(" • ")
            append(DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(now)))
            current?.let { append(" • "); append(DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(it.startUtcMs))); append("–"); append(DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(it.endUtcMs))) }
        }
    }

    private fun setBaseStatus(text: String) { baseStatus = text; status.text = text }
    private fun roundedBackground(color: Int, radiusDp: Float): GradientDrawable = GradientDrawable().apply { setColor(color); cornerRadius = radiusDp * resources.displayMetrics.density }
    private fun animateFocus(view: View, focused: Boolean) { ObjectAnimator.ofFloat(view, "scaleX", if (focused) 1.03f else 1f).setDuration(120).start(); ObjectAnimator.ofFloat(view, "scaleY", if (focused) 1.03f else 1f).setDuration(120).start() }
    override fun onDestroy() {
        mainHandler.removeCallbacks(epgTicker)
        previewPlayer?.release()
        previewPlayer = null
        executor.shutdownNow()
        super.onDestroy()
    }
}
