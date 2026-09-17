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
import android.view.KeyEvent
import android.view.View
import android.widget.Button
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.TextView
import androidx.activity.ComponentActivity
import java.text.DateFormat
import java.util.Date
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/** Provider-driven, time-aligned EPG grid optimized for TV, phone and D-pad navigation. */
class EpgGuideActivity : ComponentActivity() {
    private val executor = Executors.newFixedThreadPool(4)
    private val handler = Handler(Looper.getMainLooper())
    private lateinit var grid: LinearLayout
    private lateinit var status: TextView
    private lateinit var timeHeader: LinearLayout
    private lateinit var timeScroll: HorizontalScrollView
    private lateinit var gridScroll: ScrollView
    private lateinit var config: XtreamConfig
    private var channels = emptyList<IptvChannel>()
    private var epgByChannel = emptyMap<String, List<EpgProgram>>()
    private val bg = Color.rgb(5, 9, 18)
    private val panel = Color.rgb(13, 21, 35)
    private val row = Color.rgb(18, 29, 47)
    private val current = Color.rgb(0, 85, 160)
    private val accent = Color.rgb(0, 168, 255)
    private val muted = Color.rgb(170, 181, 200)
    private val hourWidth = 220
    private val labelWidth = 210
    private val reminderLeadMs = 5 * 60 * 1000L
    private val reminders = mutableSetOf<String>()
    private var lastReminderKey: String? = null

    private val clock = object : Runnable {
        override fun run() {
            if (::timeHeader.isInitialized) {
                renderTimeHeader()
                if (epgByChannel.isNotEmpty()) renderGrid()
                checkReminders()
            }
            handler.postDelayed(this, 30_000L)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = bg
        window.navigationBarColor = bg
        config = XtreamConfig(
            intent.getStringExtra("server") ?: "",
            intent.getStringExtra("username") ?: "",
            intent.getStringExtra("password") ?: ""
        )
        loadSavedReminders()
        buildUi()
        loadChannels()
        handler.post(clock)
    }

    private fun buildUi() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(18, 12, 18, 10)
            setBackgroundColor(bg)
        }
        val header = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        header.addView(TextView(this).apply {
            text = "TV GUIDE"
            textSize = 28f
            setTextColor(Color.WHITE)
            typeface = Typeface.DEFAULT_BOLD
        }, LinearLayout.LayoutParams(0, -2, 1f))
        status = TextView(this).apply {
            text = "Loading provider guide…"
            textSize = 13f
            setTextColor(muted)
            gravity = Gravity.CENTER_VERTICAL
        }
        header.addView(status)
        root.addView(header, LinearLayout.LayoutParams(-1, 48))

        val actions = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        actions.addView(button("↻ Refresh Guide") { loadChannels(true) }, LinearLayout.LayoutParams(-2, 42))
        actions.addView(TextView(this).apply {
            text = "  Current programme = preview • OK again = fullscreen • future programme = details/reminder"
            textSize = 11f
            setTextColor(muted)
            gravity = Gravity.CENTER_VERTICAL
        }, LinearLayout.LayoutParams(0, 42, 1f))
        root.addView(actions, LinearLayout.LayoutParams(-1, 44))

        val headerRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        headerRow.addView(TextView(this).apply {
            text = "CHANNEL"
            textSize = 12f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(muted)
            gravity = Gravity.CENTER_VERTICAL
            setPadding(12, 0, 12, 0)
            background = rounded(panel, 8f)
        }, LinearLayout.LayoutParams(labelWidth, 42))
        timeScroll = HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false
            isFocusable = false
        }
        timeHeader = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        timeScroll.addView(timeHeader)
        headerRow.addView(timeScroll, LinearLayout.LayoutParams(0, 42, 1f))
        root.addView(headerRow)

        val content = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        content.addView(TextView(this).apply {
            text = ""
            background = rounded(panel, 8f)
        }, LinearLayout.LayoutParams(labelWidth, -1))
        gridScroll = ScrollView(this).apply {
            isFillViewport = true
            isVerticalScrollBarEnabled = true
        }
        grid = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(panel)
        }
        gridScroll.addView(grid)
        content.addView(gridScroll, LinearLayout.LayoutParams(0, -1, 1f))
        root.addView(content, LinearLayout.LayoutParams(-1, 0, 1f))
        setContentView(root)

        gridScroll.viewTreeObserver.addOnScrollChangedListener {
            timeScroll.scrollTo(gridScroll.scrollX, 0)
        }
    }

    private fun button(label: String, action: () -> Unit): Button = Button(this).apply {
        text = label
        isAllCaps = false
        textSize = 12f
        setTextColor(Color.WHITE)
        background = rounded(row, 12f)
        isFocusable = true
        stateListAnimator = null
        setOnFocusChangeListener { view, focused ->
            view.background = rounded(if (focused) accent else row, 12f)
        }
        setOnClickListener { action() }
    }

    private fun loadChannels(forceRefresh: Boolean = false) {
        status.text = if (forceRefresh) "Refreshing provider channels and EPG…" else "Loading provider channels…"
        val cached = if (!forceRefresh) LiveTvCache.read(this, config) else null
        if (cached != null) applyChannels(cached.channels)
        executor.execute {
            try {
                val (categories, fresh) = XtreamClient().load(config)
                LiveTvCache.write(this, config, categories, fresh)
                runOnUiThread { applyChannels(fresh) }
            } catch (e: Exception) {
                runOnUiThread {
                    status.text = if (channels.isNotEmpty()) "Offline cache • loading available guide data…" else "Unable to load provider channels"
                }
            }
        }
    }

    private fun applyChannels(value: List<IptvChannel>) {
        channels = value
        epgByChannel = emptyMap()
        renderTimeHeader()
        grid.removeAllViews()
        if (channels.isEmpty()) {
            status.text = "No provider channels found"
            return
        }
        status.text = "${channels.size} channels • Loading provider EPG…"
        channels.forEach { channel ->
            addLoadingRow(channel)
            executor.execute {
                val programs = runCatching { XtreamClient().loadEpg(config, channel, 48) }.getOrDefault(emptyList())
                runOnUiThread {
                    epgByChannel = epgByChannel + (channel.id to programs)
                    renderGrid()
                    val loaded = epgByChannel.values.count { it.isNotEmpty() }
                    status.text = "${channels.size} channels • $loaded with EPG • Provider schedule"
                    checkReminders()
                }
            }
        }
    }

    private fun addLoadingRow(channel: IptvChannel) {
        val rowView = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            minimumHeight = 86
            background = rounded(row, 8f)
        }
        rowView.addView(channelLabel(channel, "Loading…"), LinearLayout.LayoutParams(labelWidth, 86))
        rowView.addView(TextView(this).apply {
            text = "  Loading provider programme data…"
            textSize = 13f
            setTextColor(muted)
            gravity = Gravity.CENTER_VERTICAL
        }, LinearLayout.LayoutParams(hourWidth * 6, 86))
        grid.addView(rowView, LinearLayout.LayoutParams(-1, 86).apply { topMargin = 4 })
    }

    private fun renderTimeHeader() {
        if (!::timeHeader.isInitialized) return
        val start = floorToHalfHour(System.currentTimeMillis())
        timeHeader.removeAllViews()
        repeat(14) { index ->
            val time = start + index * 30 * 60 * 1000L
            timeHeader.addView(TextView(this).apply {
                text = DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(time))
                textSize = 12f
                setTextColor(if (index == 0) Color.WHITE else muted)
                gravity = Gravity.CENTER
                background = rounded(row, 6f)
            }, LinearLayout.LayoutParams(hourWidth / 2, 42).apply { leftMargin = 1 })
        }
    }

    private fun renderGrid() {
        if (!::grid.isInitialized) return
        val snapshot = epgByChannel
        if (snapshot.isEmpty()) return
        grid.removeAllViews()
        channels.forEach { channel ->
            val rowView = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                minimumHeight = 94
                background = rounded(row, 8f)
            }
            val programs = snapshot[channel.id].orEmpty()
            val label = if (programs.isEmpty()) "No guide data" else "${programs.size} programmes"
            rowView.addView(channelLabel(channel, label), LinearLayout.LayoutParams(labelWidth, 94))
            val rail = HorizontalScrollView(this).apply {
                isHorizontalScrollBarEnabled = false
                isFillViewport = false
                isFocusable = false
            }
            val programRow = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                minimumHeight = 94
            }
            addProgrammeBlocks(channel, programs, programRow)
            if (programRow.childCount == 0) {
                programRow.addView(TextView(this).apply {
                    text = "No programme information available"
                    textSize = 13f
                    setTextColor(muted)
                    gravity = Gravity.CENTER_VERTICAL
                    setPadding(16, 0, 16, 0)
                }, LinearLayout.LayoutParams(hourWidth * 3, 94))
            }
            rail.addView(programRow)
            rowView.addView(rail, LinearLayout.LayoutParams(0, 94, 1f))
            grid.addView(rowView, LinearLayout.LayoutParams(-1, 94).apply { topMargin = 4 })
        }
        alignNow()
    }

    private fun channelLabel(channel: IptvChannel, sub: String): View = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        gravity = Gravity.CENTER_VERTICAL
        setPadding(12, 6, 10, 6)
        isFocusable = true
        background = rounded(row, 8f)
        setOnClickListener { playChannel(channel) }
        setOnKeyListener { _, key, event ->
            if (event.action == KeyEvent.ACTION_DOWN && key == KeyEvent.KEYCODE_DPAD_RIGHT) {
                findFirstProgramme(channel)?.requestFocus()
                true
            } else false
        }
        addView(TextView(this@EpgGuideActivity).apply {
            text = channel.name
            textSize = 14f
            setTextColor(Color.WHITE)
            typeface = Typeface.DEFAULT_BOLD
            maxLines = 1
        })
        addView(TextView(this@EpgGuideActivity).apply {
            text = sub
            textSize = 10f
            setTextColor(muted)
            maxLines = 1
        })
    }

    private fun findFirstProgramme(channel: IptvChannel): View? {
        val rowIndex = channels.indexOfFirst { it.id == channel.id }
        return grid.getChildAt(rowIndex)?.let { (it as? LinearLayout)?.getChildAt(1)?.let { rail ->
            (rail as? HorizontalScrollView)?.getChildAt(0)?.let { content -> (content as? LinearLayout)?.getChildAt(0) }
        } }
    }

    private fun addProgrammeBlocks(channel: IptvChannel, programs: List<EpgProgram>, parent: LinearLayout) {
        val now = System.currentTimeMillis()
        val windowStart = floorToHalfHour(now) - 30 * 60 * 1000L
        val windowEnd = windowStart + 14 * 60 * 60 * 1000L
        programs.sortedBy { it.startUtcMs }.filter { it.endUtcMs > windowStart && it.startUtcMs < windowEnd }.forEach { program ->
            val start = maxOf(program.startUtcMs, windowStart)
            val end = minOf(program.endUtcMs, windowEnd)
            val width = (((end - start).toDouble() / (60 * 60 * 1000L)) * hourWidth).toInt().coerceAtLeast(110)
            val isNow = now in program.startUtcMs until program.endUtcMs
            val isFuture = program.startUtcMs > now
            val card = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(12, 7, 12, 7)
                isFocusable = true
                isFocusableInTouchMode = true
                background = rounded(if (isNow) current else row, 8f)
                setOnFocusChangeListener { view, focused ->
                    view.background = rounded(if (focused || isNow) accent else row, 8f)
                }
                setOnClickListener { onProgrammeSelected(channel, program) }
                setOnKeyListener { _, key, event ->
                    if (event.action == KeyEvent.ACTION_DOWN && key == KeyEvent.KEYCODE_DPAD_LEFT) {
                        channelLabel(channel, "").requestFocus()
                        true
                    } else false
                }
            }
            card.addView(TextView(this).apply {
                text = "${DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(program.startUtcMs))} – ${DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(program.endUtcMs))}"
                textSize = 10f
                setTextColor(if (isNow) Color.WHITE else muted)
                maxLines = 1
            })
            card.addView(TextView(this).apply {
                text = if (isNow) "NOW • ${program.title}" else program.title
                textSize = 14f
                setTextColor(Color.WHITE)
                typeface = Typeface.DEFAULT_BOLD
                maxLines = 2
            })
            if (isNow) card.addView(ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
                max = 100
                progress = (((now - program.startUtcMs).toDouble() / (program.endUtcMs - program.startUtcMs).coerceAtLeast(1L)) * 100).toInt().coerceIn(0, 100)
            }, LinearLayout.LayoutParams(-1, 5).apply { topMargin = 5 })
            else if (isFuture) card.addView(TextView(this).apply {
                text = if (reminderKey(channel, program) in reminders) "🔔 Reminder set" else "Upcoming"
                textSize = 10f
                setTextColor(muted)
            })
            parent.addView(card, LinearLayout.LayoutParams(width, 94).apply { leftMargin = 2; rightMargin = 2 })
        }
    }

    private fun onProgrammeSelected(channel: IptvChannel, program: EpgProgram) {
        val now = System.currentTimeMillis()
        if (now in program.startUtcMs until program.endUtcMs) {
            playChannel(channel)
            return
        }
        if (program.startUtcMs > now) showFutureProgramme(channel, program)
        else AlertDialog.Builder(this)
            .setTitle(program.title)
            .setMessage("This programme has already ended. Select a current programme to preview the live channel.")
            .setPositiveButton("OK", null)
            .show()
    }

    private fun showFutureProgramme(channel: IptvChannel, program: EpgProgram) {
        val minutes = ((program.startUtcMs - System.currentTimeMillis()) / 60_000L).coerceAtLeast(0)
        val key = reminderKey(channel, program)
        AlertDialog.Builder(this)
            .setTitle("Coming Up")
            .setMessage("${program.title}\n${DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(program.startUtcMs))}\nStarts in about $minutes minute${if (minutes == 1L) "" else "s"}.\n\nA reminder can warn you shortly before it starts.")
            .setNegativeButton("Close", null)
            .setPositiveButton(if (key in reminders) "Remove reminder" else "Set reminder") { _, _ ->
                if (key in reminders) reminders.remove(key) else reminders.add(key)
                saveReminders()
                renderGrid()
            }
            .show()
    }

    private fun checkReminders() {
        val now = System.currentTimeMillis()
        for (channel in channels) {
            for (program in epgByChannel[channel.id].orEmpty()) {
                val key = reminderKey(channel, program)
                if (key !in reminders) continue
                val remaining = program.startUtcMs - now
                if (remaining in 0..reminderLeadMs && lastReminderKey != key) {
                    lastReminderKey = key
                    AlertDialog.Builder(this)
                        .setTitle("Starting Soon")
                        .setMessage("${program.title}\n${channel.name}\nStarts in about ${((remaining + 59_999) / 60_000).coerceAtLeast(0)} minutes.")
                        .setNegativeButton("Dismiss", null)
                        .setPositiveButton("Watch Now") { _, _ -> playChannel(channel) }
                        .show()
                }
            }
        }
    }

    private fun playChannel(channel: IptvChannel) {
        val url = channel.streamUrl.trim()
        if (url.isBlank()) {
            status.text = "${channel.name} has no stream URL"
            return
        }
        val channelIndex = channels.indexOfFirst { it.id == channel.id }.coerceAtLeast(0)
        try {
            startActivity(Intent(this, LiveTvActivity::class.java).apply {
                putExtra("server", config.serverUrl)
                putExtra("username", config.username)
                putExtra("password", config.password)
                putExtra("preview_channel_id", channel.id)
                putExtra("preview_channel_index", channelIndex)
                putExtra("channel_urls", ArrayList(channels.map { it.streamUrl }))
                putExtra("channel_titles", ArrayList(channels.map { it.name }))
                putExtra("channel_ids", ArrayList(channels.map { it.id }))
                putExtra("channel_numbers", ArrayList(channels.indices.map { (it + 1).toString() }))
            })
        } catch (e: Exception) {
            status.text = "Unable to open Live TV: ${e.message ?: "unknown error"}"
        }
    }

    private fun alignNow() {
        val now = System.currentTimeMillis()
        val start = floorToHalfHour(now) - 30 * 60 * 1000L
        val offset = (((now - start).toDouble() / 3_600_000.0) * hourWidth).toInt()
        gridScroll.post { gridScroll.scrollTo(offset.coerceAtLeast(0), gridScroll.scrollY) }
        timeScroll.post { timeScroll.scrollTo(offset.coerceAtLeast(0), 0) }
    }

    private fun floorToHalfHour(value: Long): Long = value - (value % (30 * 60 * 1000L))

    private fun reminderKey(channel: IptvChannel, program: EpgProgram): String = "${channel.id}|${program.startUtcMs}|${program.title}"

    private fun loadSavedReminders() {
        reminders.addAll(getSharedPreferences("alfie_epg_reminders", MODE_PRIVATE).getStringSet("keys", emptySet()).orEmpty())
    }

    private fun saveReminders() {
        getSharedPreferences("alfie_epg_reminders", MODE_PRIVATE).edit().putStringSet("keys", reminders.toSet()).apply()
    }

    override fun onDestroy() {
        handler.removeCallbacks(clock)
        executor.shutdownNow()
        super.onDestroy()
    }

    private fun rounded(color: Int, radiusDp: Float): GradientDrawable = GradientDrawable().apply {
        setColor(color)
        cornerRadius = radiusDp * resources.displayMetrics.density
    }
}
