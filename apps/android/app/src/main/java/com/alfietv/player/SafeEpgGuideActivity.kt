package com.alfietv.player

import android.app.AlertDialog
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
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
    private val epgCompleted = AtomicInteger(0)
    private var epgTotal = 0
    private var guideScrollX = 0
    private var syncingGuideScroll = false

    private val bg = Color.rgb(9, 15, 25)
    private val panel = Color.rgb(20, 27, 38)
    private val row = Color.rgb(28, 37, 51)
    private val muted = Color.rgb(160, 170, 185)
    private val accent = Color.rgb(0, 140, 255)
    private val current = Color.rgb(0, 85, 160)
    private val compact get() = resources.configuration.screenWidthDp < 600
    private val labelWidth get() = dp(if (compact) 132 else 180)
    private val pxPerMinute get() = if (compact) 2.2f * resources.displayMetrics.density else 3f * resources.displayMetrics.density

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        config = XtreamConfig(
            intent.getStringExtra("server") ?: "",
            intent.getStringExtra("username") ?: "",
            intent.getStringExtra("password") ?: ""
        )
        buildUi()
        loadGuide()
    }

    private fun buildUi() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(bg)
        }
        status = TextView(this).apply {
            text = "Loading provider TV Guide…"
            textSize = if (compact) 12f else 13f
            setTextColor(muted)
            setPadding(dp(12), dp(8), dp(12), dp(8))
        }
        root.addView(status, LinearLayout.LayoutParams(-1, -2))

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
        }
        adapter = GuideAdapter()
        list.adapter = adapter
        root.addView(list, LinearLayout.LayoutParams(-1, 0, 1f))
        setContentView(root)
    }

    private fun loadGuide() {
        executor.execute {
            try {
                val (categories, loadedChannels) = XtreamClient().load(config)
                channels = loadedChannels.distinctBy { it.id }
                selectedChannelId = intent.getStringExtra("preview_channel_id")?.takeIf { id -> channels.any { it.id == id } }
                    ?: channels.firstOrNull()?.id
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
                            val programs = XtreamClient().loadEpg(config, channel)
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
                text = channel.name
                textSize = if (compact) 12f else 13f
                typeface = Typeface.DEFAULT_BOLD
                setTextColor(Color.WHITE)
                gravity = Gravity.CENTER_VERTICAL
                maxLines = 2
                setPadding(dp(8), dp(4), dp(8), dp(4))
                background = rounded(if (selected) accent else row, 8f)
                isFocusable = true
                isClickable = true
                contentDescription = "${channel.name}, live channel preview"
                setOnClickListener {
                    selectedChannelId = channel.id
                    adapter.notifyDataSetChanged()
                    playChannel(channel)
                }
            }
            root.addView(channelCell, LinearLayout.LayoutParams(labelWidth, dp(if (compact) 68 else 74)).apply { marginEnd = dp(3) })

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
                    val now = System.currentTimeMillis()
                    val currentProgram = now in program.startUtcMs until program.endUtcMs
                    text = if (currentProgram) "NOW\n${program.title}" else "${DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(program.startUtcMs))}\n${program.title}"
                    textSize = if (compact) 10f else 11f
                    setTextColor(Color.WHITE)
                    gravity = Gravity.CENTER_VERTICAL
                    maxLines = 2
                    setPadding(dp(8), dp(3), dp(8), dp(3))
                    background = rounded(if (currentProgram) current else row, 8f)
                    isFocusable = true
                    isClickable = true
                    contentDescription = "${program.title}, ${if (currentProgram) "now playing" else "upcoming programme"}"
                    setOnClickListener { if (currentProgram || program.startUtcMs <= now) playChannel(channel) else showFuture(program, channel) }
                }
                schedule.addView(card, LinearLayout.LayoutParams(timeWidth(to - from), -1).apply { marginEnd = dp(2) })
                cursor = maxOf(cursor, to)
            }
            if (cursor < end) schedule.addView(TextView(this@SafeEpgGuideActivity).apply {
                text = "No programme data • OK to preview live"
                textSize = 11f
                setTextColor(muted)
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(10), 0, dp(10), 0)
                isFocusable = true
                isClickable = true
                setOnClickListener { playChannel(channel) }
            }, LinearLayout.LayoutParams(timeWidth(end - cursor).coerceAtLeast(dp(260)), -1))
            scroll.addView(schedule, ViewGroup.LayoutParams(-2, -1))
            root.addView(scroll, LinearLayout.LayoutParams(0, -1, 1f))
            scroll.post { scroll.scrollTo(guideScrollX, 0) }
            return root
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
        repeat(13) { index ->
            val time = start + index * 30L * 60L * 1000L
            timeHeader.addView(TextView(this).apply {
                text = DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(time))
                textSize = if (compact) 10f else 11f
                typeface = Typeface.DEFAULT_BOLD
                setTextColor(if (index == 1) Color.WHITE else muted)
                gravity = Gravity.CENTER
                background = rounded(if (index == 1) current else row, 6f)
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

    private fun playChannel(channel: IptvChannel) {
        if (channel.streamUrl.isBlank()) {
            status.text = "${channel.name} has no stream URL"
            return
        }
        val index = channels.indexOfFirst { it.id == channel.id }.coerceAtLeast(0)
        runCatching {
            startActivity(Intent(this, LiveTvActivity::class.java).apply {
                putExtra("server", config.serverUrl)
                putExtra("username", config.username)
                putExtra("password", config.password)
                putExtra("preview_channel_id", channel.id)
                putExtra("preview_channel_index", index)
                putExtra("channel_urls", ArrayList(channels.map { it.streamUrl }))
                putExtra("channel_titles", ArrayList(channels.map { it.name }))
                putExtra("channel_ids", ArrayList(channels.map { it.id }))
                putExtra("channel_numbers", ArrayList(channels.indices.map { (it + 1).toString() }))
            })
        }.onFailure { status.text = "Unable to open Live TV: ${it.message ?: "unknown error"}" }
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt().coerceAtLeast(1)

    private fun rounded(color: Int, radius: Float) = GradientDrawable().apply {
        setColor(color)
        cornerRadius = radius * resources.displayMetrics.density
    }

    override fun onDestroy() {
        executor.shutdownNow()
        epgExecutor.shutdownNow()
        super.onDestroy()
    }
}
