package com.alfietv.player

import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
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
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
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
    private var numericBuffer = ""
    private var showingPlaybackRetry = false
    private var resumePositionMs = 0L
    private var resumeApplied = false
    private var libraryConfig: XtreamConfig? = null
    private var libraryItem: UserLibraryStore.Item? = null
    private val mainHandler = Handler(Looper.getMainLooper())
    private val numericCommit = Runnable { commitNumericChannel() }
    private val progressSaver = object : Runnable {
        override fun run() {
            saveProgress()
            if (::alfiePlayer.isInitialized && !isFinishing) mainHandler.postDelayed(this, 5000L)
        }
    }
    private val preferences by lazy { getSharedPreferences("alfie_tv", MODE_PRIVATE) }

    private val refreshRunnable = object : Runnable {
        override fun run() {
            refreshOverlay()
            if (zapView.visibility == View.VISIBLE && System.currentTimeMillis() >= zapHideAt) zapView.visibility = View.GONE
            root.postDelayed(this, 1000L)
        }
    }

    private fun scheduleRefresh() {
        root.removeCallbacks(refreshRunnable)
        root.postDelayed(refreshRunnable, 1000L)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        channelUrls = intent.getStringArrayListExtra("channel_urls") ?: emptyList()
        channelTitles = intent.getStringArrayListExtra("channel_titles") ?: emptyList()
        channelIds = intent.getStringArrayListExtra("channel_ids") ?: emptyList()
        channelNumbers = intent.getStringArrayListExtra("channel_numbers") ?: emptyList()
        channelIndex = intent.getIntExtra("channel_index", 0).coerceIn(0, (channelUrls.size - 1).coerceAtLeast(0))
        setupLibraryProgress()
        root = FrameLayout(this)
        playerView = PlayerView(this).apply {
            useController = true
            setShowBuffering(PlayerView.SHOW_BUFFERING_WHEN_PLAYING)
            resizeMode = loadResizeMode()
            isFocusable = true
            isFocusableInTouchMode = true
        }
        root.addView(playerView, FrameLayout.LayoutParams(-1, -1))
        buildOverlay()
        setContentView(root)
        alfiePlayer = AlfiePlayer(this)
        alfiePlayer.attach(playerView)
        alfiePlayer.player.addListener(object : Player.Listener {
            override fun onPlayerError(error: PlaybackException) {
                statusView.text = "PLAYBACK ERROR • ${error.errorCodeName}"
                showPlaybackRetry()
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_READY) {
                    if (!resumeApplied && resumePositionMs > 5_000L) {
                        val duration = alfiePlayer.player.duration
                        if (duration <= 0L || resumePositionMs < duration - 10_000L) alfiePlayer.player.seekTo(resumePositionMs)
                        resumeApplied = true
                    }
                    if (showingPlaybackRetry) {
                        showingPlaybackRetry = false
                        trackPanel.visibility = View.GONE
                        playerView.requestFocus()
                    }
                } else if (playbackState == Player.STATE_ENDED) {
                    clearProgress()
                }
            }
        })
        intent.getStringExtra("stream_url")?.takeIf { it.isNotBlank() }?.let { alfiePlayer.play(it, currentTitle()) }
        showZapOverlay()
        refreshOverlay()
        scheduleRefresh()
        mainHandler.removeCallbacks(progressSaver)
        mainHandler.postDelayed(progressSaver, 5000L)
    }

    private fun setupLibraryProgress() {
        val type = runCatching { UserLibraryStore.Type.valueOf(intent.getStringExtra("content_type") ?: "") }.getOrNull()
        val id = intent.getStringExtra("content_id")?.takeIf { it.isNotBlank() }
        val config = SessionStore.load(this)
        if (config != null && type != null && id != null && (type == UserLibraryStore.Type.MOVIE || type == UserLibraryStore.Type.EPISODE)) {
            libraryConfig = config
            val fallback = UserLibraryStore.Item(id, type, intent.getStringExtra("title") ?: "Alfie TV", intent.getStringExtra("stream_url") ?: "")
            val saved = UserLibraryStore.findRecentById(this, config, type, id)
            libraryItem = saved ?: fallback
            resumePositionMs = saved?.positionMs ?: 0L
        }
    }

    private fun saveProgress() {
        val config = libraryConfig ?: return
        val item = libraryItem ?: return
        if (!::alfiePlayer.isInitialized) return
        val position = alfiePlayer.player.currentPosition
        val duration = alfiePlayer.player.duration
        if (position > 0L && duration > 0L) UserLibraryStore.updateProgress(this, config, item, position, duration)
    }

    private fun clearProgress() {
        val config = libraryConfig ?: return
        val item = libraryItem ?: return
        UserLibraryStore.clearProgress(this, config, item)
    }

    private fun loadResizeMode(): Int = when (preferences.getString("aspect_ratio", "fit")) {
        "fill" -> AspectRatioFrameLayout.RESIZE_MODE_FILL
        "zoom" -> AspectRatioFrameLayout.RESIZE_MODE_ZOOM
        else -> AspectRatioFrameLayout.RESIZE_MODE_FIT
    }

    private fun buildOverlay() {
        val top = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(28, 20, 28, 16); setBackgroundColor(Color.argb(185, 0, 0, 0)) }
        titleView = TextView(this).apply { setTextColor(Color.WHITE); textSize = 22f }
        statusView = TextView(this).apply { setTextColor(Color.WHITE); textSize = 14f; setPadding(0, 6, 0, 0) }
        formatView = TextView(this).apply { setTextColor(Color.LTGRAY); textSize = 13f; setPadding(0, 3, 0, 0) }
        epgView = TextView(this).apply { setTextColor(Color.WHITE); textSize = 14f; setPadding(0, 8, 0, 0) }
        top.addView(titleView); top.addView(statusView); top.addView(formatView); top.addView(epgView)
        root.addView(top, FrameLayout.LayoutParams(-1, FrameLayout.LayoutParams.WRAP_CONTENT, Gravity.TOP))
        zapView = TextView(this).apply { setTextColor(Color.WHITE); textSize = 26f; gravity = Gravity.CENTER; setPadding(32, 22, 32, 12); setBackgroundColor(Color.argb(205, 0, 0, 0)); visibility = View.GONE }
        root.addView(zapView, FrameLayout.LayoutParams(-2, FrameLayout.LayoutParams.WRAP_CONTENT, Gravity.CENTER))
        zapProgress = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply { max = 100; progress = 0; visibility = View.GONE }
        root.addView(zapProgress, FrameLayout.LayoutParams(500, 10, Gravity.CENTER).apply { topMargin = 100 })
        trackPanel = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER; setPadding(18, 12, 18, 18); setBackgroundColor(Color.argb(200, 0, 0, 0)); visibility = View.GONE }
        root.addView(trackPanel, FrameLayout.LayoutParams(-1, FrameLayout.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM))
    }

    private fun refreshOverlay() {
        if (!::alfiePlayer.isInitialized) return
        titleView.text = currentTitle()
        val number = channelNumbers.getOrNull(channelIndex) ?: intent.getStringExtra("channel_number")
        val error = alfiePlayer.errorText()
        statusView.text = if (error != null) "${number?.let { "CH $it" } ?: "PLAYBACK"}  •  ${alfiePlayer.statusText()}" else "${number?.let { "CH $it" } ?: "LIVE TV"}  •  ${alfiePlayer.statusText()}"
        formatView.text = if (error != null) "${alfiePlayer.videoFormatText()}\n${alfiePlayer.diagnosticsText()}\nERROR: $error" else "${alfiePlayer.videoFormatText()}\n${alfiePlayer.diagnosticsText()}"
        val id = channelIds.getOrNull(channelIndex) ?: intent.getStringExtra("channel_id")
        epgView.text = id?.let { preferences.getString("epg_$it", null) } ?: "NOW  Program guide loading…\nNEXT  —"
    }

    private fun currentTitle(): String = channelTitles.getOrNull(channelIndex) ?: intent.getStringExtra("title") ?: "Alfie TV"

    private fun switchChannel(delta: Int) {
        if (channelUrls.isEmpty()) return
        val next = (channelIndex + delta).coerceIn(0, channelUrls.lastIndex)
        if (next == channelIndex) return
        saveProgress()
        channelIndex = next; showingPlaybackRetry = false; trackPanel.visibility = View.GONE
        alfiePlayer.switchChannel(channelUrls[channelIndex], currentTitle())
        channelIds.getOrNull(channelIndex)?.let { preferences.edit().putString("last_channel_id", it).apply() }
        showZapOverlay(); refreshOverlay()
    }

    private fun switchToChannel(index: Int) {
        if (index !in channelUrls.indices || index == channelIndex) return
        saveProgress()
        channelIndex = index; showingPlaybackRetry = false; trackPanel.visibility = View.GONE
        alfiePlayer.switchChannel(channelUrls[channelIndex], currentTitle())
        channelIds.getOrNull(channelIndex)?.let { preferences.edit().putString("last_channel_id", it).apply() }
        showZapOverlay(); refreshOverlay()
    }

    private fun enterNumericDigit(digit: Int) {
        if (digit !in 0..9) return
        numericBuffer = (numericBuffer + digit).takeLast(4); zapView.text = "CH $numericBuffer"; zapView.visibility = View.VISIBLE; zapHideAt = System.currentTimeMillis() + 2200L
        mainHandler.removeCallbacks(numericCommit); mainHandler.postDelayed(numericCommit, 1200L)
    }

    private fun commitNumericChannel() {
        if (numericBuffer.isEmpty()) return
        val target = numericBuffer.toIntOrNull()?.toString() ?: numericBuffer; numericBuffer = ""
        val exact = channelNumbers.indexOfFirst { it.trim().toIntOrNull()?.toString() == target }
        if (exact >= 0) switchToChannel(exact) else showZapOverlay()
    }

    private fun showZapOverlay() {
        zapView.text = "CH ${channelNumbers.getOrNull(channelIndex) ?: (channelIndex + 1)}\n${currentTitle()}"; zapView.visibility = View.VISIBLE; zapHideAt = System.currentTimeMillis() + 2500L
    }

    private fun showPlaybackRetry() {
        showingPlaybackRetry = true; trackPanel.removeAllViews()
        addTrackButton("RETRY PLAYBACK") { alfiePlayer.retryCurrent(); showingPlaybackRetry = true; trackPanel.visibility = View.GONE; playerView.requestFocus() }
        addTrackButton("BACK") { showingPlaybackRetry = false; trackPanel.visibility = View.GONE; playerView.requestFocus() }
        trackPanel.visibility = View.VISIBLE; trackPanel.getChildAt(0)?.requestFocus()
    }

    private fun showTrackPanel() {
        showingPlaybackRetry = false; trackPanel.removeAllViews()
        addTrackButton("VIDEO") { showVideoOptions() }
        addTrackButton("AUDIO") { showAudioOptions(alfiePlayer.audioTracks()) }
        addTrackButton("SUBTITLES") { showSubtitleOptions(alfiePlayer.subtitleTracks()) }
        addTrackButton("REFRESH AUDIO") { alfiePlayer.refreshAudio(); trackPanel.visibility = View.GONE; playerView.requestFocus() }
        addTrackButton("CLOSE") { trackPanel.visibility = View.GONE; playerView.requestFocus() }
        trackPanel.visibility = View.VISIBLE; trackPanel.getChildAt(0)?.requestFocus()
    }

    private fun showVideoOptions() {
        trackPanel.removeAllViews()
        addTrackButton("FIT") { setResizeMode(AspectRatioFrameLayout.RESIZE_MODE_FIT) }
        addTrackButton("FILL") { setResizeMode(AspectRatioFrameLayout.RESIZE_MODE_FILL) }
        addTrackButton("ZOOM") { setResizeMode(AspectRatioFrameLayout.RESIZE_MODE_ZOOM) }
        addTrackButton("BACK") { showTrackPanel() }
        trackPanel.visibility = View.VISIBLE; trackPanel.getChildAt(0)?.requestFocus()
    }

    private fun setResizeMode(mode: Int) {
        playerView.resizeMode = mode
        val value = when (mode) { AspectRatioFrameLayout.RESIZE_MODE_FILL -> "fill"; AspectRatioFrameLayout.RESIZE_MODE_ZOOM -> "zoom"; else -> "fit" }
        preferences.edit().putString("aspect_ratio", value).apply(); trackPanel.visibility = View.GONE; playerView.requestFocus()
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

    private fun seekBy(deltaMs: Long) {
        if (alfiePlayer.player.currentMediaItem == null || alfiePlayer.player.isCurrentMediaItemLive) return
        if (deltaMs < 0) alfiePlayer.player.seekBack() else alfiePlayer.player.seekForward()
        playerView.showController()
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.action == KeyEvent.ACTION_DOWN) when (event.keyCode) {
            KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE -> { if (alfiePlayer.player.isPlaying) alfiePlayer.player.pause() else alfiePlayer.player.play(); playerView.showController(); return true }
            KeyEvent.KEYCODE_MEDIA_PLAY -> { alfiePlayer.player.play(); playerView.showController(); return true }
            KeyEvent.KEYCODE_MEDIA_PAUSE -> { alfiePlayer.player.pause(); playerView.showController(); return true }
            KeyEvent.KEYCODE_MEDIA_REWIND -> { seekBy(-10_000L); return true }
            KeyEvent.KEYCODE_MEDIA_FAST_FORWARD -> { seekBy(10_000L); return true }
            KeyEvent.KEYCODE_CHANNEL_UP -> { if (numericBuffer.isNotEmpty()) commitNumericChannel() else switchChannel(-1); return true }
            KeyEvent.KEYCODE_CHANNEL_DOWN -> { if (numericBuffer.isNotEmpty()) commitNumericChannel() else switchChannel(1); return true }
            KeyEvent.KEYCODE_DPAD_UP -> { if (trackPanel.visibility == View.GONE) { showTrackPanel(); return true } }
            KeyEvent.KEYCODE_DPAD_LEFT -> { if (trackPanel.visibility == View.GONE) { seekBy(-10_000L); return true } }
            KeyEvent.KEYCODE_DPAD_RIGHT -> { if (trackPanel.visibility == View.GONE) { seekBy(10_000L); return true } }
            KeyEvent.KEYCODE_MENU, KeyEvent.KEYCODE_INFO -> { showTrackPanel(); return true }
            KeyEvent.KEYCODE_BACK -> {
                if (trackPanel.visibility == View.VISIBLE) { showingPlaybackRetry = false; trackPanel.visibility = View.GONE; playerView.requestFocus() } else if (playerView.isControllerFullyVisible) { playerView.hideController(); playerView.requestFocus() } else finish()
                return true
            }
            KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_DPAD_CENTER -> if (numericBuffer.isNotEmpty()) { commitNumericChannel(); return true }
            KeyEvent.KEYCODE_0 -> { enterNumericDigit(0); return true }; KeyEvent.KEYCODE_1 -> { enterNumericDigit(1); return true }; KeyEvent.KEYCODE_2 -> { enterNumericDigit(2); return true }; KeyEvent.KEYCODE_3 -> { enterNumericDigit(3); return true }; KeyEvent.KEYCODE_4 -> { enterNumericDigit(4); return true }; KeyEvent.KEYCODE_5 -> { enterNumericDigit(5); return true }; KeyEvent.KEYCODE_6 -> { enterNumericDigit(6); return true }; KeyEvent.KEYCODE_7 -> { enterNumericDigit(7); return true }; KeyEvent.KEYCODE_8 -> { enterNumericDigit(8); return true }; KeyEvent.KEYCODE_9 -> { enterNumericDigit(9); return true }
        }
        return super.dispatchKeyEvent(event)
    }

    override fun onBackPressed() {
        if (trackPanel.visibility == View.VISIBLE) { showingPlaybackRetry = false; trackPanel.visibility = View.GONE; playerView.requestFocus() } else if (playerView.isControllerFullyVisible) { playerView.hideController(); playerView.requestFocus() } else finish()
    }

    override fun onStart() {
        super.onStart(); playerView.requestFocus()
        if (alfiePlayer.player.currentMediaItem != null && alfiePlayer.player.playbackState == Player.STATE_IDLE) alfiePlayer.player.prepare()
        alfiePlayer.player.playWhenReady = true; scheduleRefresh()
    }

    override fun onStop() {
        root.removeCallbacks(refreshRunnable); mainHandler.removeCallbacks(numericCommit); mainHandler.removeCallbacks(progressSaver); numericBuffer = ""
        saveProgress(); alfiePlayer.stop(); super.onStop()
    }

    override fun onDestroy() {
        root.removeCallbacks(refreshRunnable); mainHandler.removeCallbacks(numericCommit); mainHandler.removeCallbacks(progressSaver); alfiePlayer.release(); super.onDestroy()
    }
}
