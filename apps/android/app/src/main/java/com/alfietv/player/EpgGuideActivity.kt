package com.alfietv.player

import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Button
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

/** Full-screen TV Guide optimized for landscape TV and D-pad navigation. */
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
    private val row = Color.rgb(18, 29, 47)
    private val accent = Color.rgb(0, 168, 255)
    private val muted = Color.rgb(170, 181, 200)

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
            text = "Loading guide…"
            textSize = 13f
            setTextColor(muted)
            gravity = Gravity.CENTER_VERTICAL
        }
        header.addView(status)
        root.addView(header, LinearLayout.LayoutParams(-1, 48))

        val actionRow = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        val refresh = Button(this).apply {
            text = "↻ Refresh Guide"
            isAllCaps = false
            textSize = 12f
            setTextColor(Color.WHITE)
            background = rounded(row, 12f)
            isFocusable = true
            isFocusableInTouchMode = true
            stateListAnimator = null
            setOnFocusChangeListener { view, focused ->
                view.background = rounded(if (focused) accent else row, 12f)
                view.animate().scaleX(if (focused) 1.02f else 1f).scaleY(if (focused) 1.02f else 1f).setDuration(120).start()
            }
            setOnClickListener { loadChannels(forceRefresh = true) }
        }
        actionRow.addView(refresh, LinearLayout.LayoutParams(-2, 42))
        actionRow.addView(TextView(this).apply {
            text = "  ←/→ channels • ↑/↓ programmes • OK = watch live"
            textSize = 11f
            setTextColor(muted)
            gravity = Gravity.CENTER_VERTICAL
        }, LinearLayout.LayoutParams(0, 42, 1f))
        root.addView(actionRow, LinearLayout.LayoutParams(-1, 44))

        val timeBar = HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false
            isFocusable = false
        }
        val times = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        val now = System.currentTimeMillis()
        repeat(8) { index ->
            times.addView(TextView(this@EpgGuideActivity).apply {
                text = DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(now + index * 60 * 60 * 1000L))
                textSize = 12f
                setTextColor(muted)
                gravity = Gravity.CENTER
            }, LinearLayout.LayoutParams(160, 34))
        }
        timeBar.addView(times)
        root.addView(timeBar, LinearLayout.LayoutParams(-1, 36))

        val content = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        channelList = ListView(this).apply {
            divider = null
            dividerHeight = 0
            isFocusable = true
            isFocusableInTouchMode = true
            setPadding(0, 2, 8, 2)
            setOnKeyListener { _, keyCode, event ->
                if (event.action == KeyEvent.ACTION_DOWN && keyCode == KeyEvent.KEYCODE_DPAD_RIGHT) {
                    schedule.getChildAt(1)?.requestFocus()
                    true
                } else false
            }
        }
        content.addView(channelList, LinearLayout.LayoutParams(0, -1, 0.82f))

        val scheduleScroll = ScrollView(this).apply {
            isFillViewport = true
            isFocusable = false
            isVerticalScrollBarEnabled = false
        }
        schedule = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(12, 4, 4, 4)
            setBackgroundColor(panel)
            isFocusable = true
            isFocusableInTouchMode = true
            setOnKeyListener { _, keyCode, event ->
                if (event.action == KeyEvent.ACTION_DOWN && keyCode == KeyEvent.KEYCODE_DPAD_LEFT) {
                    channelList.requestFocus()
                    true
                } else false
            }
        }
        scheduleScroll.addView(schedule)
        content.addView(scheduleScroll, LinearLayout.LayoutParams(0, -1, 2.18f))
        root.addView(content, LinearLayout.LayoutParams(-1, 0, 1f))
        setContentView(root)

        channelList.setOnItemClickListener { _, _, position, _ -> channels.getOrNull(position)?.let(::selectChannel) }
    }

    private fun loadChannels(forceRefresh: Boolean = false) {
        val cached = if (!forceRefresh) LiveTvCache.read(this, config) else null
        if (cached != null) {
            applyChannels(cached.channels)
            status.text = "${cached.channels.size} channels • cached • refreshing…"
        } else {
            status.text = "Refreshing channels and guide…"
        }
        executor.execute {
            try {
                val (categories, fresh) = XtreamClient().load(config)
                LiveTvCache.write(this, config, categories, fresh)
                runOnUiThread {
                    applyChannels(fresh)
                    status.text = "${fresh.size} channels • Guide ready"
                }
            } catch (_: Exception) {
                runOnUiThread {
                    status.text = if (channels.isNotEmpty()) "Offline cache • select a channel" else "Unable to load channels"
                }
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
                    text = "${position + 1}. ${channel.name}"
                    textSize = 15f
                    setTextColor(Color.WHITE)
                    typeface = Typeface.DEFAULT_BOLD
                    maxLines = 1
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
        if (selectedChannel == null && channels.isNotEmpty()) selectChannel(channels.first())
        else selectedChannel?.let(::selectChannel)
    }

    private fun selectChannel(channel: IptvChannel) {
        selectedChannel = channel
        channelList.invalidateViews()
        channelList.setSelection(channels.indexOfFirst { it.id == channel.id }.coerceAtLeast(0))
        renderLoading(channel)

        val cached = EpgCache.read(this, config, channel)
        if (cached != null) renderPrograms(cached.programs)
        if (cached != null && EpgCache.isFresh(cached)) return

        executor.execute {
            try {
                val programs = XtreamClient().loadEpg(config, channel)
                EpgCache.write(this, config, channel, programs)
                runOnUiThread {
                    if (selectedChannel?.id == channel.id) renderPrograms(programs)
                }
            } catch (_: Exception) {
                runOnUiThread {
                    if (selectedChannel?.id == channel.id && cached == null) renderUnavailable()
                }
            }
        }
    }

    private fun renderLoading(channel: IptvChannel) {
        schedule.removeAllViews()
        schedule.addView(TextView(this).apply {
            text = channel.name
            textSize = 20f
            setTextColor(Color.WHITE)
            typeface = Typeface.DEFAULT_BOLD
            setPadding(6, 8, 6, 4)
        })
        schedule.addView(TextView(this).apply {
            text = "Loading programme information…"
            textSize = 12f
            setTextColor(muted)
            setPadding(6, 0, 6, 8)
        })
        schedule.addView(ProgressBar(this).apply { isIndeterminate = true }, LinearLayout.LayoutParams(-1, 4))
    }

    private fun renderUnavailable() {
        schedule.removeAllViews()
        schedule.addView(TextView(this).apply {
            text = "EPG unavailable for this channel\n\nPress OK on the channel to watch live."
            textSize = 15f
            setTextColor(muted)
            setPadding(12, 28, 12, 28)
        })
    }

    private fun renderPrograms(programs: List<EpgProgram>) {
        val now = System.currentTimeMillis()
        val visible = programs.sortedBy { it.startUtcMs }
            .filter { it.endUtcMs > now - 30 * 60 * 1000L }
            .take(24)
        val channel = selectedChannel
        schedule.removeAllViews()

        if (visible.isEmpty()) {
            renderUnavailable()
            return
        }

        schedule.addView(TextView(this).apply {
            text = "${channel?.name ?: "Channel"}  •  ${visible.size} programmes"
            textSize = 13f
            setTextColor(muted)
            setPadding(6, 4, 6, 8)
        })

        visible.forEachIndexed { index, program ->
            val current = now in program.startUtcMs until program.endUtcMs
            val duration = (program.endUtcMs - program.startUtcMs).coerceAtLeast(1L)
            val elapsed = (now - program.startUtcMs).coerceAtLeast(0L)
            val progress = ((elapsed.toDouble() / duration.toDouble()) * 100.0).toInt().coerceIn(0, 100)
            val card = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(16, 10, 16, 8)
                background = rounded(if (current) Color.rgb(0, 85, 160) else row, 12f)
                isFocusable = true
                isFocusableInTouchMode = true
                setOnFocusChangeListener { view, focused ->
                    view.background = rounded(if (focused || current) accent else row, 12f)
                }
                setOnClickListener { channel?.let(::playChannel) }
                setOnKeyListener { _, keyCode, event ->
                    if (event.action == KeyEvent.ACTION_DOWN && keyCode == KeyEvent.KEYCODE_DPAD_LEFT) {
                        channelList.requestFocus()
                        true
                    } else false
                }
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
                maxLines = 2
            })
            program.description?.takeIf { it.isNotBlank() }?.let { description ->
                card.addView(TextView(this).apply {
                    text = description
                    textSize = 12f
                    setTextColor(muted)
                    maxLines = 2
                })
            }
            if (current) {
                val progressBar = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
                    max = 100
                    progress = progress
                }
                card.addView(progressBar, LinearLayout.LayoutParams(-1, 5).apply { topMargin = 7 })
            }
            schedule.addView(card, LinearLayout.LayoutParams(-1, -2).apply { topMargin = if (index == 0) 4 else 8 })
        }
        schedule.post { schedule.getChildAt(1)?.requestFocus() }
    }

    private fun playChannel(channel: IptvChannel) {
        val channelIndex = channels.indexOfFirst { it.id == channel.id }.coerceAtLeast(0)
        val channelUrls = ArrayList(channels.map { it.streamUrl })
        val channelTitles = ArrayList(channels.map { it.name })
        val channelIds = ArrayList(channels.map { it.id })
        val channelNumbers = ArrayList(channels.indices.map { (it + 1).toString() })
        val url = channel.streamUrl.trim()

        if (url.isBlank()) {
            status.text = "${channel.name} has no stream URL • Refresh Provider"
            return
        }

        startActivity(Intent(this, VideoPlayerActivity::class.java).apply {
            putExtra("url", url)
            putExtra("stream_url", url)
            putExtra("title", channel.name)
            putExtra("content_id", channel.id)
            putExtra("content_type", UserLibraryStore.Type.LIVE.name)
            putExtra("channel_id", channel.id)
            putExtra("channel_number", (channelIndex + 1).toString())
            putExtra("channel_urls", channelUrls)
            putExtra("channel_titles", channelTitles)
            putExtra("channel_ids", channelIds)
            putExtra("channel_numbers", channelNumbers)
            putExtra("channel_index", channelIndex)
            putExtra("server", config.serverUrl)
            putExtra("username", config.username)
            putExtra("password", config.password)
        })
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
