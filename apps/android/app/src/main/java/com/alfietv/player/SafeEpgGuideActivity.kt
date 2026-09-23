package com.alfietv.player

import android.app.AlertDialog
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.ProgressBar
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.media3.ui.PlayerView
import java.text.DateFormat
import java.util.Date
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger

/** Crash-safe provider-driven TV Guide with an adaptive time-aligned schedule grid. */
class SafeEpgGuideActivity : ComponentActivity() {
    private lateinit var status: TextView
    private lateinit var list: ListView
    private lateinit var adapter: GuideAdapter
    private lateinit var config: XtreamConfig
    private lateinit var timeHeader: LinearLayout
    private lateinit var timeHeaderScroll: HorizontalScrollView
    private val executor = Executors.newFixedThreadPool(2)
    private val epgExecutor = Executors.newFixedThreadPool(4)
    private val epgByChannel = mutableMapOf<String, List<EpgProgram>>()
    private var channels: List<IptvChannel> = emptyList()
    private var selectedChannelId: String? = null
    private var previewPlayer: AlfiePlayer? = null
    private lateinit var previewView: PlayerView
    private var fullscreenLaunchInProgress = false
    private var awaitingFullscreenReturn = false
    private val epgCompleted = AtomicInteger(0)
    private var epgTotal = 0
    private var guideScrollX = 0
    private var syncingGuideScroll = false
    private val guideClockHandler = Handler(Looper.getMainLooper())
    private val guideClockTicker = object : Runnable {
        override fun run() {
            if (isFinishing || isDestroyed) return
            val preservedScroll = guideScrollX
            renderTimeHeader()
            adapter.notifyDataSetChanged()
            guideScrollX = preservedScroll
            timeHeaderScroll.post { timeHeaderScroll.scrollTo(preservedScroll, 0) }
            syncVisibleScheduleRows(preservedScroll)
            guideClockHandler.postDelayed(this, 30_000L)
        }
    }

    private val skin get() = SkinStore.current(this)
    private val bg get() = skin.background
    private val panel get() = skin.surface
    private val row get() = skin.surface2
    private val muted get() = skin.secondary
    private val accent get() = skin.accent
    private val currentAccent: Int get() = skin.current
    private val focusStroke = Color.WHITE
    private val compact get() = resources.configuration.screenWidthDp < 600
    private val labelWidth get() = dp(if (compact) 132 else 180)
    private val pxPerMinute get() = if (compact) 2.2f * resources.displayMetrics.density else 3f * resources.displayMetrics.density

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        SkinStore.applyWindow(this)
        config = XtreamConfig(
            intent.getStringExtra("server") ?: "",
            intent.getStringExtra("username") ?: "",
            intent.getStringExtra("password") ?: ""
        )
        buildUi()
        loadGuide()
        guideClockHandler.postDelayed(guideClockTicker, 30_000L)
    }

    private fun buildUi() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(bg)
        }
        status = TextView(this).apply {
            text = "TV GUIDE  •  NOW / NEXT / 6-HOUR TIMELINE"
            textSize = if (compact) 12f else 13f
            setTextColor(muted)
            setPadding(dp(12), dp(8), dp(12), dp(8))
        }
        root.addView(TextView(this).apply {
            text = "TV GUIDE  •  Provider EPG timeline"
            textSize = if (compact) 18f else 24f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.WHITE)
            setPadding(dp(12), dp(10), dp(12), dp(4))
        }, LinearLayout.LayoutParams(-1, -2))
        root.addView(status, LinearLayout.LayoutParams(-1, -2))

        previewView = PlayerView(this).apply {
            useController = false
            controllerAutoShow = false
            setShowBuffering(PlayerView.SHOW_BUFFERING_ALWAYS)
            resizeMode = androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_FIT
            setBackgroundColor(Color.BLACK)
            isFocusable = false
        }
        root.addView(previewView, LinearLayout.LayoutParams(-1, dp(if (compact) 190 else 250)).apply {
            leftMargin = dp(8); rightMargin = dp(8); bottomMargin = dp(6)
        })

        val loading = ProgressBar(this).apply { isIndeterminate = true }
        root.addView(loading, LinearLayout.LayoutParams(-1, dp(3)))

        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        header.addView(TextView(this).apply {
            text = "CHANNEL"
            textSize = if (compact) 10f else 11f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(muted)
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(8), 0, dp(8), 0)
            background = rounded(panel, 8f)
        }, LinearLayout.LayoutParams(labelWidth, dp(42)))
        timeHeaderScroll = HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false
            isFillViewport = false
            setOnScrollChangeListener { _, scrollX, _, _, _ ->
                if (!syncingGuideScroll) {
                    guideScrollX = scrollX
                    syncVisibleScheduleRows(scrollX)
                }
            }
        }
        timeHeader = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        timeHeaderScroll.addView(timeHeader)
        header.addView(timeHeaderScroll, LinearLayout.LayoutParams(0, dp(42), 1f))
        root.addView(header, LinearLayout.LayoutParams(-1, dp(42)))
        renderTimeHeader()

        list = ListView(this).apply {
            divider = null
            dividerHeight = 0
            setBackgroundColor(bg)
            isVerticalScrollBarEnabled = true
            descendantFocusability = ViewGroup.FOCUS_AFTER_DESCENDANTS
        }
        adapter = GuideAdapter()
        list.adapter = adapter
        root.addView(list, LinearLayout.LayoutParams(-1, 0, 1f))
        setContentView(root)
        SkinStore.animate(root, skin)
    }

    private fun loadGuide() {
        executor.execute {
            try {
                val (categories, loadedChannels) = XtreamClient().load(config)
                channels = loadedChannels.distinctBy { it.id }
                val requestedChannelId = intent.getStringExtra("preview_channel_id")
                val persistedChannelId = getSharedPreferences("alfie_tv", MODE_PRIVATE)
                    .getString("guide_last_channel_${config.serverUrl}_${config.username}", null)
                    ?: getSharedPreferences("alfie_tv", MODE_PRIVATE).getString("last_channel_${config.serverUrl}_${config.username}", null)
                selectedChannelId = (requestedChannelId ?: persistedChannelId)
                    ?.takeIf { id -> channels.any { it.id == id } }
                LiveTvCache.write(this, config, categories, channels)

                channels.forEach { channel ->
                    runCatching { EpgCache.read(this, config, channel) }
                        .getOrNull()
                        ?.let { snapshot -> synchronized(epgByChannel) { epgByChannel[channel.id] = snapshot.programs } }
                }
                epgCompleted.set(0)
                epgTotal = channels.size
                runOnUiThread {
                    status.text = "${channels.size} channels • Loading provider EPG…"
                    adapter.notifyDataSetChanged()
                    focusSelectedChannel()
                }

                if (channels.isEmpty()) {
                    runOnUiThread {
                        status.text = "No channels available from provider"
                        adapter.notifyDataSetChanged()
                    }
                    return@execute
                }

                channels.forEach { channel ->
                    epgExecutor.execute {
                        try {
                            val programs = XtreamClient().loadEpg(config, channel, 48)
                            if (programs.isNotEmpty()) {
                                synchronized(epgByChannel) { epgByChannel[channel.id] = programs }
                                runCatching { EpgCache.write(this, config, channel, programs) }
                            }
                        } catch (_: Exception) {
                            // One bad channel must never close the Guide.
                        } finally {
                            val completed = epgCompleted.incrementAndGet()
                            if (completed % 10 == 0 || completed == epgTotal) {
                                runOnUiThread {
                                    status.text = "${channels.size} channels • EPG $completed/$epgTotal"
                                    adapter.notifyDataSetChanged()
                                }
                            }
                            if (completed == epgTotal) {
                                val count = synchronized(epgByChannel) { epgByChannel.size }
                                runOnUiThread {
                                    status.text = "${channels.size} channels • ${count} with EPG • Guide ready"
                                    adapter.notifyDataSetChanged()
                                    focusSelectedChannel()
                                }
                            }
                        }
                    }
                }
            } catch (error: Exception) {
                val cached = runCatching { LiveTvCache.read(this, config) }.getOrNull()
                channels = cached?.channels.orEmpty().distinctBy { it.id }
                selectedChannelId = channels.firstOrNull()?.id
                runOnUiThread {
                    adapter.notifyDataSetChanged()
                    status.text = if (channels.isNotEmpty()) {
                        "Provider unavailable • using ${channels.size} cached channels"
                    } else {
                        "Unable to load provider channels • ${error.message ?: "check provider connection"}"
                    }
                    focusSelectedChannel()
                }
            }
        }
    }

    private inner class GuideAdapter : BaseAdapter() {
        override fun getCount() = channels.size
        override fun getItem(position: Int) = channels.getOrNull(position)
        override fun getItemId(position: Int) = position.toLong()

        override fun getView(position: Int, recycled: View?, parent: ViewGroup): View {
            val channel = channels.getOrNull(position) ?: return TextView(this@SafeEpgGuideActivity)
            val root = recycled as? LinearLayout ?: LinearLayout(this@SafeEpgGuideActivity).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(4), dp(2), dp(4), dp(2))
            }
            root.removeAllViews()

            val selected = channel.id == selectedChannelId
            val channelCell = TextView(this@SafeEpgGuideActivity).apply {
                text = buildChannelLabel(channel, position)
                textSize = if (compact) 12f else 13f
                typeface = Typeface.DEFAULT_BOLD
                setTextColor(Color.WHITE)
                gravity = Gravity.CENTER_VERTICAL
                maxLines = 2
                setPadding(dp(8), dp(4), dp(8), dp(4))
                background = guideCellBackground(if (selected) accent else row, false)
                isFocusable = true
                isClickable = true
                contentDescription = "${channel.name}, live channel preview"
                setOnFocusChangeListener { view, hasFocus ->
                    view.background = guideCellBackground(if (channel.id == selectedChannelId) accent else row, hasFocus)
                }
                setOnClickListener { activateChannel(channel) }
            }
            root.addView(channelCell, LinearLayout.LayoutParams(labelWidth, dp(if (compact) 92 else 104)).apply { marginEnd = dp(3) })

            val scroll = HorizontalScrollView(this@SafeEpgGuideActivity).apply {
                isHorizontalScrollBarEnabled = false
                isFillViewport = false
                setOnScrollChangeListener { _, scrollX, _, _, _ ->
                    if (!syncingGuideScroll) {
                        guideScrollX = scrollX
                        syncGuideScroll(scrollX, this)
                    }
                }
            }
            val schedule = LinearLayout(this@SafeEpgGuideActivity).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setBackgroundColor(bg)
            }
            val start = guideStart()
            val end = start + 6L * 60L * 60L * 1000L
            val programs = synchronized(epgByChannel) { epgByChannel[channel.id].orEmpty() }
                .sortedBy { it.startUtcMs }
                .filter { it.endUtcMs > start && it.startUtcMs < end }
            var cursor = start
            programs.forEach { program ->
                val from = maxOf(start, program.startUtcMs)
                val to = minOf(end, program.endUtcMs)
                if (from > cursor) schedule.addView(View(this@SafeEpgGuideActivity), LinearLayout.LayoutParams(timeWidth(from - cursor), -1))
                val card = TextView(this@SafeEpgGuideActivity).apply {
                    val currentProgram = System.currentTimeMillis() in program.startUtcMs until program.endUtcMs
                    val startLabel = DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(program.startUtcMs))
                    val endLabel = DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(program.endUtcMs))
                    val description = program.description?.trim().orEmpty()
                    text = buildString {
                        append(if (currentProgram) "● NOW  " else "")
                        append(program.title.ifBlank { "Program" })
                        append("\n")
                        append("$startLabel – $endLabel")
                        if (description.isNotBlank()) {
                            append("\n")
                            append(description)
                        }
                    }
                    textSize = if (compact) 10f else 11f
                    setTextColor(Color.WHITE)
                    gravity = Gravity.CENTER_VERTICAL
                    maxLines = 4
                    ellipsize = android.text.TextUtils.TruncateAt.END
                    setPadding(dp(8), dp(4), dp(8), dp(4))
                    background = guideCellBackground(if (currentProgram) currentAccent else row, false)
                    isFocusable = true
                    isClickable = true
                    contentDescription = "${program.title}, ${startLabel} to ${endLabel}, ${if (currentProgram) "now playing" else "upcoming programme"}"
                    setOnFocusChangeListener { view, hasFocus ->
                        view.background = guideCellBackground(if (currentProgram) currentAccent else row, hasFocus)
                        if (hasFocus) {
                            (view.parent?.parent as? HorizontalScrollView)?.let { guideScroll ->
                                val target = (view.left - (guideScroll.width - view.width) / 2).coerceAtLeast(0)
                                guideScroll.smoothScrollTo(target, 0)
                            }
                        }
                    }
                    setOnClickListener {
                        val nowAtClick = System.currentTimeMillis()
                        if (nowAtClick in program.startUtcMs until program.endUtcMs) activateChannel(channel) else if (program.startUtcMs <= nowAtClick) activateChannel(channel) else showFuture(program, channel)
                    }
                    setOnKeyListener { _, keyCode, event ->
                        if (event.action == android.view.KeyEvent.ACTION_DOWN && event.repeatCount == 0 &&
                            (keyCode == android.view.KeyEvent.KEYCODE_DPAD_CENTER || keyCode == android.view.KeyEvent.KEYCODE_ENTER)) {
                            performClick()
                            true
                        } else false
                    }
                }
                schedule.addView(card, LinearLayout.LayoutParams(timeWidth(to - from), -1).apply { marginEnd = dp(2) })
                cursor = maxOf(cursor, to)
            }
            if (cursor < end) schedule.addView(TextView(this@SafeEpgGuideActivity).apply {
                text = "No programme data • OK to select live"
                textSize = 11f
                setTextColor(muted)
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(10), 0, dp(10), 0)
                isFocusable = true
                isClickable = true
                contentDescription = "No programme data, select live channel"
                setOnFocusChangeListener { view, hasFocus ->
                    view.background = guideCellBackground(bg, hasFocus)
                }
                setOnClickListener { activateChannel(channel) }
            }, LinearLayout.LayoutParams(timeWidth(end - cursor).coerceAtLeast(dp(260)), -1))
            scroll.addView(schedule, ViewGroup.LayoutParams(-2, -1))
            root.addView(scroll, LinearLayout.LayoutParams(0, -1, 1f))
            scroll.post { scroll.scrollTo(guideScrollX, 0) }
            return root
        }
    }

    /** Guide owns its preview. A second activation of the same channel opens fullscreen and returns here. */
    private fun activateChannel(channel: IptvChannel) {
        if (fullscreenLaunchInProgress) return
        if (selectedChannelId == channel.id) {
            playFullscreen(channel)
            return
        }
        selectedChannelId = channel.id
        getSharedPreferences("alfie_tv", MODE_PRIVATE).edit()
            .putString("guide_preview_channel_${config.serverUrl}_${config.username}", channel.id)
            .apply()
        preview(channel)
    }

    private fun preview(channel: IptvChannel) {
        val url = channel.streamUrl.trim()
        if (url.isBlank()) {
            status.text = "${channel.name} has no stream URL"
            return
        }
        selectedChannelId = channel.id
        if (previewPlayer == null) previewPlayer = AlfiePlayer(this).also { it.attach(previewView) }
        previewPlayer?.playWithFallback(
            listOf(url, channel.fallbackStreamUrl ?: "").filter { it.isNotBlank() },
            channel.name,
            (channels.indexOfFirst { it.id == channel.id } + 1).coerceAtLeast(1).toString()
        )
        status.text = "${channel.name} • Guide preview playing • OK again = fullscreen"
        adapter.notifyDataSetChanged()
        focusSelectedChannel()
    }

    private fun playFullscreen(channel: IptvChannel) {
        if (fullscreenLaunchInProgress) return
        val url = channel.streamUrl.trim()
        if (url.isBlank()) {
            status.text = "${channel.name} has no stream URL"
            return
        }
        fullscreenLaunchInProgress = true
        awaitingFullscreenReturn = true
        selectedChannelId = channel.id
        val index = channels.indexOfFirst { it.id == channel.id }.coerceAtLeast(0)
        previewPlayer?.release()
        previewPlayer = null
        previewView.player = null
        runCatching {
            startActivity(Intent(this, MainActivity::class.java).apply {
                putExtra("url", url)
                putExtra("stream_url", url)
                putExtra("fallback_stream_url", channel.fallbackStreamUrl ?: "")
                putExtra("title", channel.name)
                putExtra("content_id", channel.id)
                putExtra("content_type", "LIVE")
                putExtra("channel_id", channel.id)
                putExtra("channel_number", (index + 1).toString())
                putExtra("preview_category_id", channel.categoryId)
                putExtra("channel_index", index)
                putExtra("server", config.serverUrl)
                putExtra("username", config.username)
                putExtra("password", config.password)
                putExtra("fullscreen_handoff", true)
                putExtra("playback_owner", "guide")
            })
        }.onFailure {
            fullscreenLaunchInProgress = false
            awaitingFullscreenReturn = false
            status.text = "Unable to open fullscreen: ${it.message ?: "unknown error"}"
            preview(channel)
        }
    }

    private fun focusSelectedChannel() {
        val id = selectedChannelId ?: return
        val position = channels.indexOfFirst { it.id == id }
        if (position < 0 || !::list.isInitialized) return
        list.post {
            list.setSelection(position)
            val first = list.firstVisiblePosition
            val child = list.getChildAt(position - first) as? ViewGroup
            val channelCell = child?.getChildAt(0)
            if (channelCell?.isFocusable == true) channelCell.requestFocus()
        }
    }

    private fun syncGuideScroll(scrollX: Int, source: HorizontalScrollView) {
        syncingGuideScroll = true
        try {
            if (::timeHeaderScroll.isInitialized && timeHeaderScroll !== source) {
                timeHeaderScroll.scrollTo(scrollX, 0)
            }
            syncVisibleScheduleRows(scrollX, source)
        } finally {
            syncingGuideScroll = false
        }
    }

    private fun syncVisibleScheduleRows(scrollX: Int, source: HorizontalScrollView? = null) {
        if (!::list.isInitialized) return
        syncingGuideScroll = true
        try {
            val first = list.firstVisiblePosition
            val last = list.lastVisiblePosition
            if (last < first) return
            for (position in first..last) {
                val child = list.getChildAt(position - first) as? ViewGroup ?: continue
                for (index in 0 until child.childCount) {
                    val candidate = child.getChildAt(index)
                    if (candidate is HorizontalScrollView && candidate !== source) {
                        candidate.scrollTo(scrollX, 0)
                    }
                }
            }
        } finally {
            syncingGuideScroll = false
        }
    }

    private fun renderTimeHeader() {
        if (!::timeHeader.isInitialized) return
        timeHeader.removeAllViews()
        val start = guideStart()
        repeat(12) { index ->
            val time = start + index * 30L * 60L * 1000L
            timeHeader.addView(TextView(this).apply {
                text = DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(time))
                textSize = if (compact) 10f else 11f
                typeface = Typeface.DEFAULT_BOLD
                setTextColor(if (index == 1) Color.WHITE else muted)
                gravity = Gravity.CENTER
                background = rounded(if (index == 1) currentAccent else row, 6f)
            }, LinearLayout.LayoutParams(timeWidth(30L * 60L * 1000L), dp(40)).apply { marginEnd = dp(1) })
        }
    }

    private fun guideStart(): Long = ((System.currentTimeMillis() / 60000L) - 30L) * 60000L

    private fun timeWidth(durationMs: Long): Int {
        val minutes = durationMs.coerceAtLeast(1L) / 60000f
        return (minutes * pxPerMinute).toInt().coerceAtLeast(dp(if (compact) 72 else 82))
    }

    private fun showFuture(program: EpgProgram, channel: IptvChannel) {
        val start = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(program.startUtcMs))
        AlertDialog.Builder(this).setTitle(program.title)
            .setMessage("${channel.name}\n$start\n\nThis programme has not started yet.")
            .setNegativeButton("Dismiss", null)
            .setPositiveButton("Remind me") { _, _ ->
                getPreferences(MODE_PRIVATE).edit().putLong("reminder_${channel.id}_${program.startUtcMs}", program.startUtcMs).apply()
                status.text = "Reminder set for ${program.title}"
            }.show()
    }

    private fun buildChannelLabel(channel: IptvChannel, position: Int): String {
        val number = (position + 1).toString()
        val category = channel.categoryId?.takeIf { it.isNotBlank() }?.let { " • $it" } ?: ""
        val epg = channel.epgId?.takeIf { it.isNotBlank() }?.let { "\nEPG: $it" } ?: ""
        return "CH $number • ${channel.name}$category$epg"
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt().coerceAtLeast(1)

    private fun rounded(color: Int, radius: Float) = GradientDrawable().apply {
        setColor(color)
        cornerRadius = radius * resources.displayMetrics.density
    }

    private fun guideCellBackground(fill: Int, focused: Boolean) = GradientDrawable().apply {
        setColor(fill)
        cornerRadius = 8f * resources.displayMetrics.density
        if (focused) setStroke(dp(if (compact) 2 else 3), focusStroke)
    }

    override fun onResume() {
        super.onResume()
        val persisted = getSharedPreferences("alfie_tv", MODE_PRIVATE)
            .getString("guide_last_channel_${config.serverUrl}_${config.username}", null)
        val savedGuideId = getSharedPreferences("alfie_tv", MODE_PRIVATE)
            .getString("guide_preview_channel_${config.serverUrl}_${config.username}", null)
        val restoredId = savedGuideId ?: intent.getStringExtra("preview_channel_id") ?: persisted
        if (!restoredId.isNullOrBlank() && channels.any { it.id == restoredId }) {
            selectedChannelId = restoredId
            adapter.notifyDataSetChanged()
            focusSelectedChannel()
            if (awaitingFullscreenReturn) {
                awaitingFullscreenReturn = false
                fullscreenLaunchInProgress = false
                channels.firstOrNull { it.id == restoredId }?.let(::preview)
            }
        }
        // Rebuild the timeline anchor so NOW/current-program styling stays aligned after returning.
        renderTimeHeader()
        if (guideScrollX > 0) timeHeaderScroll.post { timeHeaderScroll.scrollTo(guideScrollX, 0) }
    }

    override fun onDestroy() {
        guideClockHandler.removeCallbacks(guideClockTicker)
        previewPlayer?.release()
        previewPlayer = null
        executor.shutdownNow()
        epgExecutor.shutdownNow()
        super.onDestroy()
    }
}
