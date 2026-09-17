package com.alfietv.player

import android.app.AlertDialog
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.widget.Button
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

/** Provider-driven time-aligned TV Guide optimized for TV, phone and D-pad navigation. */
class EpgGuideActivity : ComponentActivity() {
    private val executor = Executors.newSingleThreadExecutor()
    private lateinit var grid: LinearLayout
    private lateinit var status: TextView
    private lateinit var config: XtreamConfig
    private var channels = emptyList<IptvChannel>()
    private val epgByChannel = linkedMapOf<String, List<EpgProgram>>()

    private val bg = Color.rgb(5, 9, 18)
    private val panel = Color.rgb(13, 21, 35)
    private val row = Color.rgb(18, 29, 47)
    private val accent = Color.rgb(0, 168, 255)
    private val muted = Color.rgb(170, 181, 200)
    private val channelWidthDp = 190
    private val minuteWidthDp = 3.0f
    private val rowHeightDp = 78
    private val guideStartMinutes = 30

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = bg
        window.navigationBarColor = bg
        config = XtreamConfig(
            intent.getStringExtra("server") ?: "",
            intent.getStringExtra("username") ?: "",
            intent.getStringExtra("password") ?: ""
        )
        buildUi()
        loadChannels()
    }

    private fun buildUi() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(10), dp(14), dp(8))
            setBackgroundColor(bg)
        }
        val header = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        header.addView(TextView(this).apply {
            text = "TV GUIDE"
            textSize = 27f
            setTextColor(Color.WHITE)
            typeface = Typeface.DEFAULT_BOLD
        }, LinearLayout.LayoutParams(0, -2, 1f))
        status = TextView(this).apply {
            text = "Loading guide…"
            textSize = 13f
            setTextColor(muted)
            gravity = Gravity.CENTER_VERTICAL
        }
        header.addView(status)
        root.addView(header, LinearLayout.LayoutParams(-1, dp(46)))

        val actionRow = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        val refresh = Button(this).apply {
            text = "↻ Refresh Guide"
            isAllCaps = false
            textSize = 12f
            setTextColor(Color.WHITE)
            background = rounded(row, 12f)
            isFocusable = true
            stateListAnimator = null
            setOnFocusChangeListener { view, focused ->
                view.background = rounded(if (focused) accent else row, 12f)
            }
            setOnClickListener { loadChannels(forceRefresh = true) }
        }
        actionRow.addView(refresh, LinearLayout.LayoutParams(-2, dp(40)))
        actionRow.addView(TextView(this).apply {
            text = "  ←/→ programmes • ↑/↓ channels • OK = preview / watch full screen"
            textSize = 11f
            setTextColor(muted)
            gravity = Gravity.CENTER_VERTICAL
        }, LinearLayout.LayoutParams(0, dp(40), 1f))
        root.addView(actionRow, LinearLayout.LayoutParams(-1, dp(42)))

        val gridScroll = HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = true
            isFillViewport = false
            isFocusable = false
        }
        val vertical = ScrollView(this).apply {
            isFillViewport = true
            isVerticalScrollBarEnabled = true
        }
        grid = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(panel)
        }
        vertical.addView(grid)
        gridScroll.addView(vertical)
        root.addView(gridScroll, LinearLayout.LayoutParams(-1, 0, 1f))
        setContentView(root)
    }

    private fun loadChannels(forceRefresh: Boolean = false) {
        val cached = if (!forceRefresh) LiveTvCache.read(this, config) else null
        if (cached != null) {
            applyChannels(cached.channels)
            status.text = "${cached.channels.size} channels • loading provider EPG…"
        } else {
            status.text = "Refreshing channels and guide…"
            renderLoadingGrid()
        }
        executor.execute {
            try {
                val (categories, fresh) = XtreamClient().load(config)
                LiveTvCache.write(this, config, categories, fresh)
                runOnUiThread {
                    applyChannels(fresh)
                    status.text = "${fresh.size} channels • loading provider EPG…"
                }
                loadAllEpg(fresh, forceRefresh)
            } catch (_: Exception) {
                runOnUiThread {
                    status.text = if (channels.isNotEmpty()) "Offline cache • guide data may be partial" else "Unable to load channels"
                    if (channels.isEmpty()) renderUnavailableGrid()
                }
            }
        }
    }

    private fun applyChannels(value: List<IptvChannel>) {
        channels = value
        epgByChannel.clear()
        renderGrid()
    }

    private fun loadAllEpg(value: List<IptvChannel>, forceRefresh: Boolean) {
        var loaded = 0
        value.forEach { channel ->
            try {
                val cached = if (!forceRefresh) EpgCache.read(this, config, channel) else null
                val programs = if (cached != null && EpgCache.isFresh(cached)) {
                    cached.programs
                } else {
                    XtreamClient().loadEpg(config, channel).also {
                        EpgCache.write(this, config, channel, it)
                    }
                }
                synchronized(epgByChannel) { epgByChannel[channel.id] = programs }
            } catch (_: Exception) {
                synchronized(epgByChannel) { epgByChannel[channel.id] = emptyList() }
            }
            loaded++
            if (loaded % 5 == 0 || loaded == value.size) {
                val done = loaded
                runOnUiThread {
                    status.text = "$done/${value.size} channels • EPG loaded"
                    renderGrid()
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
            text = "No channel data available.\n\nUse Refresh Guide after checking the provider connection."
            textSize = 16f
            setTextColor(muted)
            setPadding(dp(18), dp(30), dp(18), dp(30))
        })
    }

    private fun renderGrid() {
        if (channels.isEmpty()) {
            renderUnavailableGrid()
            return
        }
        val now = System.currentTimeMillis()
        val start = ((now / 60000L) - guideStartMinutes) * 60000L
        val end = start + 6L * 60L * 60L * 1000L
        val pxPerMinute = dp(minuteWidthDp)
        val channelWidth = dp(channelWidthDp)

        grid.removeAllViews()
        grid.addView(buildTimeHeader(start, end, channelWidth, pxPerMinute), LinearLayout.LayoutParams(-2, dp(46)))
        channels.forEach { channel ->
            val programs = synchronized(epgByChannel) { epgByChannel[channel.id].orEmpty() }
            grid.addView(
                buildChannelRow(channel, programs, start, end, channelWidth, pxPerMinute),
                LinearLayout.LayoutParams(-2, dp(rowHeightDp))
            )
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

    private fun buildChannelRow(
        channel: IptvChannel,
        programs: List<EpgProgram>,
        start: Long,
        end: Long,
        channelWidth: Int,
        pxPerMinute: Float
    ): View {
        val rowView = FrameLayout(this).apply { setBackgroundColor(panel) }
        val channelCell = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(12), dp(5), dp(8), dp(5))
            background = rounded(row, 8f)
            isFocusable = true
            isClickable = true
            contentDescription = "${channel.name}, channel"
            setOnFocusChangeListener { view, focused ->
                view.background = rounded(if (focused) accent else row, 8f)
            }
            setOnClickListener { playChannel(channel) }
            setOnKeyListener { _, keyCode, event ->
                if (event.action == KeyEvent.ACTION_DOWN && keyCode == KeyEvent.KEYCODE_DPAD_RIGHT) {
                    rowView.findFocus()?.focusSearch(View.FOCUS_RIGHT)?.requestFocus()
                    true
                } else false
            }
        }
        channelCell.addView(TextView(this).apply {
            text = channel.name
            textSize = 14f
            setTextColor(Color.WHITE)
            typeface = Typeface.DEFAULT_BOLD
            maxLines = 1
        })
        channelCell.addView(TextView(this).apply {
            text = channel.categoryId ?: "Live TV"
            textSize = 10f
            setTextColor(muted)
            maxLines = 1
        })
        rowView.addView(channelCell, FrameLayout.LayoutParams(channelWidth, -1))

        val programmeArea = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setBackgroundColor(Color.rgb(9, 15, 25))
        }
        val visible = programs.sortedBy { it.startUtcMs }
            .filter { it.endUtcMs > start && it.startUtcMs < end }
        var cursor = start
        visible.forEach { program ->
            if (program.startUtcMs > cursor) {
                programmeArea.addView(spacer(((program.startUtcMs - cursor) / 60000f * pxPerMinute).toInt()))
            }
            val clippedStart = maxOf(program.startUtcMs, start)
            val clippedEnd = minOf(program.endUtcMs, end)
            val width = ((clippedEnd - clippedStart) / 60000f * pxPerMinute)
                .toInt().coerceAtLeast(dp(82))
            programmeArea.addView(
                buildProgrammeCard(program, channel),
                LinearLayout.LayoutParams(width, -1).apply { marginEnd = dp(2) }
            )
            cursor = maxOf(cursor, program.endUtcMs)
        }
        if (cursor < end) {
            programmeArea.addView(spacer(((end - cursor) / 60000f * pxPerMinute).toInt()))
        }
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
        rowView.addView(
            programmeArea,
            FrameLayout.LayoutParams(-2, -1).apply { leftMargin = channelWidth + dp(2) }
        )

        val now = System.currentTimeMillis()
        if (nowInRange(now, start, end)) {
            val markerX = channelWidth + dp(2) + ((now - start) / 60000f * pxPerMinute).toInt()
            rowView.addView(View(this).apply { setBackgroundColor(accent) }, FrameLayout.LayoutParams(dp(2), -1).apply {
                leftMargin = markerX
                topMargin = 0
            })
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
            setOnFocusChangeListener { view, focused ->
                view.background = rounded(if (focused || current) accent else row, 8f)
            }
            setOnClickListener {
                if (current) playChannel(channel)
                else if (upcoming) showFutureProgramme(program, channel)
                else playChannel(channel)
            }
            addView(TextView(this@EpgGuideActivity).apply {
                text = if (current) "NOW" else DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(program.startUtcMs))
                textSize = 10f
                setTextColor(if (current) Color.WHITE else muted)
                typeface = Typeface.DEFAULT_BOLD
            })
            addView(TextView(this@EpgGuideActivity).apply {
                text = program.title
                textSize = 13f
                setTextColor(Color.WHITE)
                typeface = Typeface.DEFAULT_BOLD
                maxLines = 2
            })
            if (current) {
                val duration = (program.endUtcMs - program.startUtcMs).coerceAtLeast(1L)
                val elapsed = (now - program.startUtcMs).coerceAtLeast(0L)
                val pct = ((elapsed.toDouble() / duration.toDouble()) * 100).toInt().coerceIn(0, 100)
                addView(ProgressBar(this@EpgGuideActivity, null, android.R.attr.progressBarStyleHorizontal).apply {
                    max = 100
                    progress = pct
                }, LinearLayout.LayoutParams(-1, dp(4)).apply { topMargin = dp(4) })
            }
        }
    }

    private fun showFutureProgramme(program: EpgProgram, channel: IptvChannel) {
        val start = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(program.startUtcMs))
        val end = DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(program.endUtcMs))
        AlertDialog.Builder(this)
            .setTitle(program.title)
            .setMessage("${channel.name}\n$start – $end\n\nThis programme has not started yet. You can set a reminder or dismiss this notice.")
            .setNegativeButton("Dismiss", null)
            .setPositiveButton("Remind me") { _, _ ->
                getPreferences(MODE_PRIVATE).edit()
                    .putLong("reminder_${channel.id}_${program.startUtcMs}", program.startUtcMs)
                    .apply()
                status.text = "Reminder set for ${program.title}"
            }
            .show()
    }

    private fun playChannel(channel: IptvChannel) {
        val url = channel.streamUrl.trim()
        if (url.isBlank()) {
            status.text = "${channel.name} has no stream URL • Refresh Provider"
            return
        }
        val channelIndex = channels.indexOfFirst { it.id == channel.id }.coerceAtLeast(0)
        val channelUrls = ArrayList(channels.map { it.streamUrl })
        val channelTitles = ArrayList(channels.map { it.name })
        val channelIds = ArrayList(channels.map { it.id })
        val channelNumbers = ArrayList(channels.indices.map { (it + 1).toString() })
        try {
            startActivity(Intent(this, LiveTvActivity::class.java).apply {
                putExtra("server", config.serverUrl)
                putExtra("username", config.username)
                putExtra("password", config.password)
                putExtra("preview_channel_id", channel.id)
                putExtra("preview_channel_index", channelIndex)
                putExtra("channel_urls", channelUrls)
                putExtra("channel_titles", channelTitles)
                putExtra("channel_ids", channelIds)
                putExtra("channel_numbers", channelNumbers)
            })
        } catch (e: Exception) {
            status.text = "Unable to open Live TV: ${e.message ?: "unknown error"}"
        }
    }

    private fun spacer(width: Int): View = View(this).apply {
        layoutParams = LinearLayout.LayoutParams(width.coerceAtLeast(0), -1)
    }

    private fun nowInRange(now: Long, start: Long, end: Long): Boolean = now in start..end

    /** Accept Int and Float dp arguments so Kotlin never widens Int layout values incorrectly. */
    private fun dp(value: Number): Int = (value.toFloat() * resources.displayMetrics.density)
        .toInt().coerceAtLeast(1)

    private fun rounded(color: Int, radiusDp: Float): GradientDrawable = GradientDrawable().apply {
        setColor(color)
        cornerRadius = radiusDp * resources.displayMetrics.density
    }

    override fun onDestroy() {
        executor.shutdownNow()
        super.onDestroy()
    }
}
