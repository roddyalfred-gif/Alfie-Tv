package com.alfietv.player

import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.TextView
import androidx.activity.ComponentActivity
import java.text.DateFormat
import java.util.Date
import java.util.concurrent.Executors

/** Dedicated multi-program TV Guide for fast TV/remote browsing. */
class EpgGuideActivity : ComponentActivity() {
    private val executor = Executors.newSingleThreadExecutor()
    private lateinit var channelList: ListView
    private lateinit var schedule: LinearLayout
    private lateinit var status: TextView
    private lateinit var config: XtreamConfig
    private var channels = emptyList<IptvChannel>()
    private var selectedChannel: IptvChannel? = null
    private val bg = Color.rgb(5, 9, 18)
    private val panel = Color.rgb(13, 21, 35)
    private val row = Color.rgb(15, 24, 40)
    private val accent = Color.rgb(0, 168, 255)
    private val muted = Color.rgb(170, 181, 200)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = bg
        window.navigationBarColor = bg
        config = XtreamConfig(intent.getStringExtra("server") ?: "", intent.getStringExtra("username") ?: "", intent.getStringExtra("password") ?: "")
        buildUi()
        loadChannels()
    }

    private fun buildUi() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(18, 14, 18, 10)
            setBackgroundColor(bg)
        }
        val header = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        header.addView(TextView(this).apply {
            text = "TV GUIDE"
            textSize = 28f
            setTextColor(Color.WHITE)
            typeface = Typeface.DEFAULT_BOLD
        }, LinearLayout.LayoutParams(0, -2, 1f))
        status = TextView(this).apply { text = "Loading guide…"; textSize = 13f; setTextColor(muted) }
        header.addView(status)
        root.addView(header, LinearLayout.LayoutParams(-1, 50))

        val timeBar = HorizontalScrollView(this).apply { isHorizontalScrollBarEnabled = false; isFocusable = false }
        val times = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        val now = System.currentTimeMillis()
        repeat(6) { index ->
            times.addView(TextView(this@EpgGuideActivity).apply {
                text = DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(now + index * 60 * 60 * 1000L))
                textSize = 12f
                setTextColor(muted)
                gravity = Gravity.CENTER
                setPadding(18, 0, 18, 0)
            }, LinearLayout.LayoutParams(150, 36))
        }
        timeBar.addView(times)
        root.addView(timeBar, LinearLayout.LayoutParams(-1, 38))

        val content = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        channelList = ListView(this).apply {
            divider = null
            dividerHeight = 0
            isFocusable = true
            isFocusableInTouchMode = true
            setPadding(0, 2, 8, 2)
            setOnKeyListener { _, keyCode, event ->
                if (event.action == KeyEvent.ACTION_DOWN && keyCode == KeyEvent.KEYCODE_DPAD_RIGHT) {
                    schedule.requestFocus(); true
                } else false
            }
        }
        content.addView(channelList, LinearLayout.LayoutParams(0, -1, 0.78f))

        val scheduleScroll = ScrollView(this).apply { isFillViewport = true; isFocusable = false }
        schedule = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(12, 4, 4, 4)
            setBackgroundColor(panel)
            isFocusable = true
            isFocusableInTouchMode = true
            setOnKeyListener { _, keyCode, event ->
                if (event.action == KeyEvent.ACTION_DOWN && keyCode == KeyEvent.KEYCODE_DPAD_LEFT) {
                    channelList.requestFocus(); true
                } else false
            }
        }
        scheduleScroll.addView(schedule)
        content.addView(scheduleScroll, LinearLayout.LayoutParams(0, -1, 2.22f))
        root.addView(content, LinearLayout.LayoutParams(-1, 0, 1f))
        setContentView(root)

        channelList.setOnItemClickListener { _, _, position, _ -> channels.getOrNull(position)?.let(::selectChannel) }
    }

    private fun loadChannels() {
        val cached = LiveTvCache.read(this, config)
        if (cached != null) applyChannels(cached.channels)
        executor.execute {
            try {
                val (_, fresh) = XtreamClient().load(config)
                LiveTvCache.write(this, config, cached?.categories ?: emptyList(), fresh)
                runOnUiThread { applyChannels(fresh); status.text = "${fresh.size} channels • Guide ready" }
            } catch (_: Exception) {
                runOnUiThread { status.text = if (channels.isNotEmpty()) "Offline cache • Select a channel" else "Unable to load channels" }
            }
        }
    }

    private fun applyChannels(value: List<IptvChannel>) {
        channels = value
        channelList.adapter = object : ArrayAdapter<IptvChannel>(this, android.R.layout.simple_list_item_1, channels) {
            override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
                val channel = getItem(position) ?: return TextView(this@EpgGuideActivity)
                val box = LinearLayout(this@EpgGuideActivity).apply {
                    orientation = LinearLayout.VERTICAL
                    gravity = Gravity.CENTER_VERTICAL
                    setPadding(14, 8, 10, 8)
                    minimumHeight = 64
                    background = rounded(if (channel.id == selectedChannel?.id) accent else row, 12f)
                }
                box.addView(TextView(this@EpgGuideActivity).apply {
                    text = channel.name
                    textSize = 15f
                    setTextColor(Color.WHITE)
                    typeface = Typeface.DEFAULT_BOLD
                })
                box.addView(TextView(this@EpgGuideActivity).apply {
                    text = channel.categoryId ?: "Live TV"
                    textSize = 11f
                    setTextColor(muted)
                    setPadding(0, 2, 0, 0)
                })
                return box
            }
        }
        if (selectedChannel == null && channels.isNotEmpty()) selectChannel(channels.first()) else selectedChannel?.let(::selectChannel)
    }

    private fun selectChannel(channel: IptvChannel) {
        selectedChannel = channel
        channelList.invalidateViews()
        channelList.setSelection(channels.indexOfFirst { it.id == channel.id }.coerceAtLeast(0))
        schedule.removeAllViews()
        schedule.addView(TextView(this).apply {
            text = channel.name
            textSize = 20f
            setTextColor(Color.WHITE)
            typeface = Typeface.DEFAULT_BOLD
            setPadding(6, 8, 6, 12)
        })
        val loading = ProgressBar(this)
        schedule.addView(loading, LinearLayout.LayoutParams(-1, 5))
        val cached = EpgCache.read(this, config, channel)
        cached?.let { renderPrograms(it.programs) }
        if (cached != null && EpgCache.isFresh(cached)) return
        executor.execute {
            try {
                val programs = XtreamClient().loadEpg(config, channel)
                EpgCache.write(this, config, channel, programs)
                runOnUiThread { if (selectedChannel?.id == channel.id) renderPrograms(programs) }
            } catch (_: Exception) {
                runOnUiThread {
                    if (selectedChannel?.id == channel.id) {
                        schedule.removeAllViews()
                        schedule.addView(TextView(this).apply { text = "EPG unavailable for this channel"; textSize = 15f; setTextColor(muted); setPadding(8, 20, 8, 20) })
                    }
                }
            }
        }
    }

    private fun renderPrograms(programs: List<EpgProgram>) {
        val now = System.currentTimeMillis()
        val visible = programs.sortedBy { it.startUtcMs }.filter { it.endUtcMs > now - 30 * 60 * 1000L }.take(18)
        schedule.removeAllViews()
        if (visible.isEmpty()) {
            schedule.addView(TextView(this).apply { text = "No programme data available"; textSize = 15f; setTextColor(muted); setPadding(8, 20, 8, 20) })
            return
        }
        visible.forEachIndexed { index, program ->
            val current = now in program.startUtcMs until program.endUtcMs
            val card = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(16, 10, 16, 10)
                background = rounded(if (current) Color.rgb(0, 85, 160) else row, 12f)
                isFocusable = true
                isFocusableInTouchMode = true
                setOnFocusChangeListener { view, focused -> view.background = rounded(if (focused || current) accent else row, 12f) }
            }
            card.addView(TextView(this).apply {
                text = "${DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(program.startUtcMs))}  –  ${DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(program.endUtcMs))}"
                textSize = 12f
                setTextColor(if (current) Color.WHITE else muted)
            })
            card.addView(TextView(this).apply {
                text = if (current) "NOW  •  ${program.title}" else program.title
                textSize = 17f
                setTextColor(Color.WHITE)
                typeface = Typeface.DEFAULT_BOLD
                setPadding(0, 4, 0, 2)
            })
            program.description?.takeIf { it.isNotBlank() }?.let { description ->
                card.addView(TextView(this).apply { text = description; textSize = 12f; setTextColor(muted) })
            }
            schedule.addView(card, LinearLayout.LayoutParams(-1, -2).apply { topMargin = if (index == 0) 4 else 8 })
        }
        schedule.post { schedule.getChildAt(1)?.requestFocus() }
    }

    private fun rounded(color: Int, radiusDp: Float): GradientDrawable = GradientDrawable().apply {
        setColor(color)
        cornerRadius = radiusDp * resources.displayMetrics.density
    }

    override fun onDestroy() {
        executor.shutdownNow()
        super.onDestroy()
    }
}
