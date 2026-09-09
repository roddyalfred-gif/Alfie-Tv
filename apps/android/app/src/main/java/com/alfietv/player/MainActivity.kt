package com.alfietv.player

import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView

class MainActivity : ComponentActivity() {
    private lateinit var root: FrameLayout
    private lateinit var playerView: PlayerView
    private lateinit var alfiePlayer: AlfiePlayer
    private lateinit var titleView: TextView
    private lateinit var statusView: TextView
    private lateinit var formatView: TextView
    private lateinit var epgView: TextView
    private lateinit var zapView: TextView
    private lateinit var zapProgress: ProgressBar
    private lateinit var trackPanel: LinearLayout
    private var channelUrls = emptyList<String>()
    private var channelTitles = emptyList<String>()
    private var channelIds = emptyList<String>()
    private var channelNumbers = emptyList<String>()
    private var channelIndex = 0
    private var zapHideAt = 0L

    private val refreshRunnable = object : Runnable {
        override fun run() {
            refreshOverlay()
            if (zapView.visibility == View.VISIBLE && System.currentTimeMillis() >= zapHideAt) zapView.visibility = View.GONE
            root.postDelayed(this, 1000L)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        channelUrls = intent.getStringArrayListExtra("channel_urls") ?: emptyList()
        channelTitles = intent.getStringArrayListExtra("channel_titles") ?: emptyList()
        channelIds = intent.getStringArrayListExtra("channel_ids") ?: emptyList()
        channelNumbers = intent.getStringArrayListExtra("channel_numbers") ?: emptyList()
        channelIndex = intent.getIntExtra("channel_index", 0).coerceIn(0, (channelUrls.size - 1).coerceAtLeast(0))

        root = FrameLayout(this)
        playerView = PlayerView(this).apply {
            useController = true
            setShowBuffering(PlayerView.SHOW_BUFFERING_WHEN_PLAYING)
            resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
            isFocusable = true
            isFocusableInTouchMode = true
        }
        root.addView(playerView, FrameLayout.LayoutParams(-1, -1))
        buildOverlay()
        setContentView(root)

        alfiePlayer = AlfiePlayer(this)
        alfiePlayer.attach(playerView)

        val url = intent.getStringExtra("stream_url")
        if (!url.isNullOrBlank()) alfiePlayer.play(url, currentTitle())
        showZapOverlay()
        refreshOverlay()
        root.postDelayed(refreshRunnable, 1000L)
    }

    private fun buildOverlay() {
        val top = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(28, 20, 28, 16)
            setBackgroundColor(Color.argb(185, 0, 0, 0))
        }
        titleView = TextView(this).apply { setTextColor(Color.WHITE); textSize = 22f }
        statusView = TextView(this).apply { setTextColor(Color.WHITE); textSize = 14f; setPadding(0, 6, 0, 0) }
        formatView = TextView(this).apply { setTextColor(Color.LTGRAY); textSize = 13f; setPadding(0, 3, 0, 0) }
        epgView = TextView(this).apply { setTextColor(Color.WHITE); textSize = 14f; setPadding(0, 8, 0, 0) }
        top.addView(titleView); top.addView(statusView); top.addView(formatView); top.addView(epgView)
        root.addView(top, FrameLayout.LayoutParams(-1, FrameLayout.LayoutParams.WRAP_CONTENT, Gravity.TOP))

        zapView = TextView(this).apply {
            setTextColor(Color.WHITE)
            textSize = 26f
            gravity = Gravity.CENTER
            setPadding(32, 22, 32, 12)
            setBackgroundColor(Color.argb(205, 0, 0, 0))
            visibility = View.GONE
        }
        root.addView(zapView, FrameLayout.LayoutParams(-2, FrameLayout.LayoutParams.WRAP_CONTENT, Gravity.CENTER))
        zapProgress = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply { max = 100; progress = 0; visibility = View.GONE }
        root.addView(zapProgress, FrameLayout.LayoutParams(500, 10, Gravity.CENTER).apply { topMargin = 100 })

        trackPanel = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER; setPadding(18, 12, 18, 18)
            setBackgroundColor(Color.argb(200, 0, 0, 0)); visibility = View.GONE
        }
        root.addView(trackPanel, FrameLayout.LayoutParams(-1, FrameLayout.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM))
    }

    private fun refreshOverlay() {
        if (!::alfiePlayer.isInitialized) return
        titleView.text = currentTitle()
        val number = channelNumbers.getOrNull(channelIndex) ?: intent.getStringExtra("channel_number")
        val channelText = number?.let { "CH $it" } ?: "LIVE TV"
        statusView.text = "$channelText  •  ${alfiePlayer.statusText()}"
        formatView.text = alfiePlayer.videoFormatText()
        val id = channelIds.getOrNull(channelIndex) ?: intent.getStringExtra("channel_id")
        epgView.text = id?.let { getSharedPreferences("alfie_tv", MODE_PRIVATE).getString("epg_$it", null) }
            ?: "NOW  Program guide loading…\nNEXT  —"
    }

    private fun currentTitle(): String = channelTitles.getOrNull(channelIndex)
        ?: intent.getStringExtra("title") ?: "Alfie TV"

    private fun switchChannel(delta: Int) {
        if (channelUrls.isEmpty()) return
        val next = (channelIndex + delta).coerceIn(0, channelUrls.lastIndex)
        if (next == channelIndex) return
        channelIndex = next
        val title = currentTitle()
        alfiePlayer.switchChannel(channelUrls[channelIndex], title)
        getSharedPreferences("alfie_tv", MODE_PRIVATE).edit().putString("last_channel_id", channelIds.getOrNull(channelIndex)).apply()
        showZapOverlay()
        refreshOverlay()
    }

    private fun showZapOverlay() {
        zapView.text = "CH ${channelNumbers.getOrNull(channelIndex) ?: (channelIndex + 1)}\n$currentTitle()"
        zapView.visibility = View.VISIBLE
        zapHideAt = System.currentTimeMillis() + 2500L
    }

    private fun showTrackPanel() {
        trackPanel.removeAllViews()
        val audio = alfiePlayer.audioTracks(); val subtitles = alfiePlayer.subtitleTracks()
        addTrackButton("AUDIO") { showAudioOptions(audio) }
        addTrackButton("SUBTITLES") { showSubtitleOptions(subtitles) }
        addTrackButton("CLOSE") { trackPanel.visibility = View.GONE; playerView.requestFocus() }
        trackPanel.visibility = View.VISIBLE; trackPanel.getChildAt(0)?.requestFocus()
    }

    private fun showAudioOptions(options: List<TrackOption>) {
        trackPanel.removeAllViews()
        if (options.isEmpty()) addTrackButton("No audio tracks") { showTrackPanel() }
        options.forEach { option -> addTrackButton(option.label) { alfiePlayer.selectAudio(option); trackPanel.visibility = View.GONE; playerView.requestFocus() } }
        addTrackButton("BACK") { showTrackPanel() }; trackPanel.getChildAt(0)?.requestFocus()
    }

    private fun showSubtitleOptions(options: List<TrackOption>) {
        trackPanel.removeAllViews()
        addTrackButton("OFF") { alfiePlayer.selectSubtitle(null); trackPanel.visibility = View.GONE; playerView.requestFocus() }
        options.forEach { option -> addTrackButton(option.label) { alfiePlayer.selectSubtitle(option); trackPanel.visibility = View.GONE; playerView.requestFocus() } }
        addTrackButton("BACK") { showTrackPanel() }; trackPanel.getChildAt(0)?.requestFocus()
    }

    private fun addTrackButton(label: String, action: () -> Unit) {
        val button = Button(this).apply { text = label; isFocusable = true; isFocusableInTouchMode = true; setOnClickListener { action() } }
        trackPanel.addView(button, LinearLayout.LayoutParams(0, FrameLayout.LayoutParams.WRAP_CONTENT, 1f).apply { setMargins(6, 0, 6, 0) })
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.action == KeyEvent.ACTION_DOWN) when (event.keyCode) {
            KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE -> { if (alfiePlayer.player.isPlaying) alfiePlayer.player.pause() else alfiePlayer.player.play(); return true }
            KeyEvent.KEYCODE_MEDIA_PLAY -> { alfiePlayer.player.play(); return true }
            KeyEvent.KEYCODE_MEDIA_PAUSE -> { alfiePlayer.player.pause(); return true }
            KeyEvent.KEYCODE_CHANNEL_UP -> { switchChannel(-1); return true }
            KeyEvent.KEYCODE_CHANNEL_DOWN -> { switchChannel(1); return true }
            KeyEvent.KEYCODE_DPAD_UP -> { if (trackPanel.visibility == View.GONE) showTrackPanel(); return true }
            KeyEvent.KEYCODE_MENU, KeyEvent.KEYCODE_INFO -> { showTrackPanel(); return true }
            KeyEvent.KEYCODE_BACK -> if (trackPanel.visibility == View.VISIBLE) { trackPanel.visibility = View.GONE; playerView.requestFocus(); return true }
        }
        return super.dispatchKeyEvent(event)
    }

    override fun onStart() { super.onStart(); playerView.requestFocus(); alfiePlayer.player.playWhenReady = true }
    override fun onStop() { root.removeCallbacks(refreshRunnable); alfiePlayer.stop(); super.onStop() }
    override fun onDestroy() { root.removeCallbacks(refreshRunnable); alfiePlayer.release(); super.onDestroy() }
}
