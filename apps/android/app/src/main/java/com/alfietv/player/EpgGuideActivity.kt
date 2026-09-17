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
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.TextView
import androidx.activity.ComponentActivity
import java.text.DateFormat
import java.util.Date
import java.util.concurrent.Executors

/** Provider-driven horizontal EPG grid optimized for touch and TV D-pad navigation. */
class EpgGuideActivity : ComponentActivity() {
    private lateinit var grid: LinearLayout
    private lateinit var status: TextView
    private val executor = Executors.newSingleThreadExecutor()
    private val epgByChannel = mutableMapOf<String, List<EpgProgram>>()
    private var channels: List<IptvChannel> = emptyList()
    private lateinit var config: XtreamConfig

    private val panel = Color.rgb(20, 27, 38)
    private val row = Color.rgb(28, 37, 51)
    private val muted = Color.rgb(160, 170, 185)
    private val accent = Color.rgb(0, 140, 255)
    private val channelWidthDp = 190
    private val minuteWidthDp = 3.0f
    private val rowHeightDp = 78
    private val guideStartMinutes = 30

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        config = XtreamConfig(intent.getStringExtra("server") ?: "", intent.getStringExtra("username") ?: "", intent.getStringExtra("password") ?: "")
        buildUi()
        loadGuide()
    }

    private fun buildUi() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.rgb(9, 15, 25))
        }
        status = TextView(this).apply {
            text = "Loading provider TV Guide…"
            textSize = 13f
            setTextColor(muted)
            setPadding(dp(14), dp(10), dp(14), dp(10))
        }
        root.addView(status, LinearLayout.LayoutParams(-1, -2))

        val horizontal = HorizontalScrollView(this).apply {
            isFillViewport = false
            isHorizontalScrollBarEnabled = true
        }
        val vertical = ScrollView(this).apply { isFillViewport = true }
        grid = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.rgb(9, 15, 25))
        }
        vertical.addView(grid, ViewGroup.LayoutParams(-2, -2))
        horizontal.addView(vertical, ViewGroup.LayoutParams(-1, -1))
        root.addView(horizontal, LinearLayout.LayoutParams(-1, 0, 1f))
        setContentView(root)
    }

    private fun loadGuide() {
        renderLoadingGrid()
        executor.execute {
            try {
                val (categories, loadedChannels) = XtreamClient().load(config)
                channels = loadedChannels
                LiveTvCache.write(this, config, categories, channels)
                channels.forEach { channel ->
                    EpgCache.read(this, config, channel)?.let { snapshot ->
                        synchronized(epgByChannel) { epgByChannel[channel.id] = snapshot.programs }
                    }
                }
                runOnUiThread {
                    status.text = "${channels.size} channels • Loading provider EPG…"
                    renderGrid()
                }
                channels.forEach { channel ->
                    try {
                        val programs = XtreamClient().loadEpg(config, channel)
                        if (programs.isNotEmpty()) {
                            synchronized(epgByChannel) { epgByChannel[channel.id] = programs }
                            EpgCache.write(this, config, channel, programs)
                            runOnUiThread { renderGrid() }
                        }
                    } catch (_: Exception) { }
                }
                runOnUiThread {
                    val epgCount = synchronized(epgByChannel) { epgByChannel.size }
                    status.text = "${channels.size} channels • ${epgCount} channels with EPG • Guide ready"
                }
            } catch (error: Exception) {
                val cached = LiveTvCache.read(this, config)
                channels = cached?.channels.orEmpty()
                runOnUiThread {
                    renderGrid()
                    status.text = if (channels.isNotEmpty()) "Provider unavailable • using ${channels.size} cached channels" else "Unable to load provider channels • ${error.message ?: "check provider connection"}"
                }
            }
        }
    }

    private fun renderLoadingGrid() {
        grid.removeAllViews()
        grid.addView(TextView(this).apply {
            text = "Loading channels and provider EPG…"
            textSize = 16f
            setTextColor(muted)
            setPadding(dp(18), dp(30), dp(18), dp(30))
        })
        grid.addView(ProgressBar(this).apply { isIndeterminate = true }, LinearLayout.LayoutParams(-1, dp(4)))
    }

    private fun renderUnavailableGrid() {
        grid.removeAllViews()
        grid.addView(TextView(this).apply {
            text = "No channel data available.\n\nCheck the provider connection and return to TV Guide."
            textSize = 16f
            setTextColor(muted)
            setPadding(dp(18), dp(30), dp(18), dp(30))
        })
    }

    private fun renderGrid() {
        if (channels.isEmpty()) { renderUnavailableGrid(); return }
        val now = System.currentTimeMillis()
        val start = ((now / 60000L) - guideStartMinutes) * 60000L
        val end = start + 6L * 60L * 60L * 1000L
        val pxPerMinute = minuteWidthDp * resources.displayMetrics.density
        val channelWidth = dp(channelWidthDp)
        grid.removeAllViews()
        grid.addView(buildTimeHeader(start, end, channelWidth, pxPerMinute), LinearLayout.LayoutParams(-2, dp(46)))
        channels.forEach { channel ->
            val programs = synchronized(epgByChannel) { epgByChannel[channel.id].orEmpty() }
            grid.addView(buildChannelRow(channel, programs, start, end, channelWidth, pxPerMinute), LinearLayout.LayoutParams(-2, dp(rowHeightDp)))
        }
    }

    private fun buildTimeHeader(start: Long, end: Long, channelWidth: Int, pxPerMinute: Float): View {
        val rowView = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setBackgroundColor(panel)
        }
        rowView.addView(TextView(this).apply {
            text = "CHANNELS"
            textSize = 12f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(12), 0, dp(8), 0)
        }, LinearLayout.LayoutParams(channelWidth, -1))
        var t = start
        while (t < end) {
            val next = t + 60 * 60 * 1000L
            val width = ((next - t) / 60000f * pxPerMinute).toInt().coerceAtLeast(dp(100))
            rowView.addView(TextView(this).apply {
                text = DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(t))
                textSize = 12f
                typeface = Typeface.DEFAULT_BOLD
                setTextColor(muted)
                gravity = Gravity.CENTER
                background = rounded(row, 0f)
            }, LinearLayout.LayoutParams(width, -1).apply { marginEnd = dp(1) })
            t = next
        }
        return rowView
    }

    private fun buildChannelRow(channel: IptvChannel, programs: List<EpgProgram>, start: Long, end: Long, channelWidth: Int, pxPerMinute: Float): View {
        val rowView = FrameLayout(this).apply { setBackgroundColor(panel) }
        val channelCell = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(12), dp(5), dp(8), dp(5))
            background = rounded(row, 8f)
            isFocusable = true
            isClickable = true
            contentDescription = "${channel.name}, channel"
            setOnFocusChangeListener { view, focused -> view.background = rounded(if (focused) accent else row, 8f) }
            setOnClickListener { playChannel(channel) }
        }
        channelCell.addView(TextView(this).apply { text = channel.name; textSize = 14f; setTextColor(Color.WHITE); typeface = Typeface.DEFAULT_BOLD; maxLines = 1 })
        channelCell.addView(TextView(this).apply { text = channel.categoryId ?: "Live TV"; textSize = 10f; setTextColor(muted); maxLines = 1 })
        rowView.addView(channelCell, FrameLayout.LayoutParams(channelWidth, -1))

        val programmeArea = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; setBackgroundColor(Color.rgb(9, 15, 25)) }
        val visible = programs.sortedBy { it.startUtcMs }.filter { it.endUtcMs > start && it.startUtcMs < end }
        var cursor = start
        visible.forEach { program ->
            val clippedStart = maxOf(program.startUtcMs, start)
            val clippedEnd = minOf(program.endUtcMs, end)
            if (clippedStart > cursor) programmeArea.addView(spacer(((clippedStart - cursor) / 60000f * pxPerMinute).toInt()))
            val width = ((clippedEnd - clippedStart) / 60000f * pxPerMinute).toInt().coerceAtLeast(dp(82))
            programmeArea.addView(buildProgrammeCard(program, channel), LinearLayout.LayoutParams(width, -1).apply { marginEnd = dp(2) })
            cursor = maxOf(cursor, clippedEnd)
        }
        if (cursor < end) programmeArea.addView(spacer(((end - cursor) / 60000f * pxPerMinute).toInt()))
        if (visible.isEmpty()) {
            programmeArea.addView(TextView(this).apply {
                text = "No programme data  •  OK to preview live"
                textSize = 12f
                setTextColor(muted)
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(12), 0, dp(12), 0)
                isFocusable = true
                isClickable = true
                setOnClickListener { playChannel(channel) }
            }, LinearLayout.LayoutParams(dp(360), -1))
        }
        rowView.addView(programmeArea, FrameLayout.LayoutParams(-2, -1).apply { leftMargin = channelWidth + dp(2) })
        val current = System.currentTimeMillis()
        if (current in start until end) {
            val markerX = channelWidth + dp(2) + ((current - start) / 60000f * pxPerMinute).toInt()
            rowView.addView(View(this).apply { setBackgroundColor(accent) }, FrameLayout.LayoutParams(dp(2), -1).apply { leftMargin = markerX })
        }
        return rowView
    }

    private fun buildProgrammeCard(program: EpgProgram, channel: IptvChannel): View {
        val now = System.currentTimeMillis()
        val current = now in program.startUtcMs until program.endUtcMs
        val upcoming = program.startUtcMs > now
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(10), dp(5), dp(10), dp(5))
            background = rounded(if (current) Color.rgb(0, 85, 160) else row, 8f)
            isFocusable = true
            isClickable = true
            contentDescription = if (current) "${program.title}, now playing" else "${program.title}, upcoming programme"
            setOnFocusChangeListener { view, focused -> view.background = rounded(if (focused || current) accent else row, 8f) }
            setOnClickListener { if (upcoming) showFutureProgramme(program, channel) else playChannel(channel) }
            addView(TextView(this@EpgGuideActivity).apply { text = if (current) "NOW" else DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(program.startUtcMs)); textSize = 10f; setTextColor(if (current) Color.WHITE else muted); typeface = Typeface.DEFAULT_BOLD })
            addView(TextView(this@EpgGuideActivity).apply { text = program.title; textSize = 13f; setTextColor(Color.WHITE); typeface = Typeface.DEFAULT_BOLD; maxLines = 2 })
            if (current) {
                val duration = (program.endUtcMs - program.startUtcMs).coerceAtLeast(1L)
                val elapsed = (now - program.startUtcMs).coerceAtLeast(0L)
                val pct = ((elapsed.toDouble() / duration.toDouble()) * 100).toInt().coerceIn(0, 100)
                addView(ProgressBar(this@EpgGuideActivity, null, android.R.attr.progressBarStyleHorizontal).apply { max = 100; progress = pct }, LinearLayout.LayoutParams(-1, dp(4)).apply { topMargin = dp(4) })
            }
        }
    }

    private fun showFutureProgramme(program: EpgProgram, channel: IptvChannel) {
        val start = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(program.startUtcMs))
        val end = DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(program.endUtcMs))
        AlertDialog.Builder(this).setTitle(program.title)
            .setMessage("${channel.name}\n$start – $end\n\nThis programme has not started yet. You can set a reminder or dismiss this notice.")
            .setNegativeButton("Dismiss", null)
            .setPositiveButton("Remind me") { _, _ ->
                getPreferences(MODE_PRIVATE).edit().putLong("reminder_${channel.id}_${program.startUtcMs}", program.startUtcMs).apply()
                status.text = "Reminder set for ${program.title}"
            }.show()
    }

    private fun playChannel(channel: IptvChannel) {
        if (channel.streamUrl.trim().isBlank()) { status.text = "${channel.name} has no stream URL"; return }
        val channelIndex = channels.indexOfFirst { it.id == channel.id }.coerceAtLeast(0)
        try {
            startActivity(Intent(this, LiveTvActivity::class.java).apply {
                putExtra("server", config.serverUrl); putExtra("username", config.username); putExtra("password", config.password)
                putExtra("preview_channel_id", channel.id); putExtra("preview_channel_index", channelIndex)
                putExtra("channel_urls", ArrayList(channels.map { it.streamUrl })); putExtra("channel_titles", ArrayList(channels.map { it.name }))
                putExtra("channel_ids", ArrayList(channels.map { it.id })); putExtra("channel_numbers", ArrayList(channels.indices.map { (it + 1).toString() }))
            })
        } catch (e: Exception) { status.text = "Unable to open Live TV: ${e.message ?: "unknown error"}" }
    }

    private fun spacer(width: Int): View = View(this).apply { layoutParams = LinearLayout.LayoutParams(width.coerceAtLeast(0), -1) }
    private fun dp(value: Number): Int = (value.toFloat() * resources.displayMetrics.density).toInt().coerceAtLeast(1)
    private fun rounded(color: Int, radiusDp: Float): GradientDrawable = GradientDrawable().apply { setColor(color); cornerRadius = radiusDp * resources.displayMetrics.density }

    override fun onDestroy() { executor.shutdownNow(); super.onDestroy() }
}
