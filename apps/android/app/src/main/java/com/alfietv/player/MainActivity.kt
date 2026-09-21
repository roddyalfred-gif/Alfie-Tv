package com.alfietv.player

import android.app.AlertDialog
import android.app.PictureInPictureParams
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Rational
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
    private lateinit var topOverlay: LinearLayout
    private lateinit var titleView: TextView
    private lateinit var statusView: TextView
    private lateinit var formatView: TextView
    private lateinit var epgView: TextView
    private lateinit var zapView: TextView
    private lateinit var zapProgress: ProgressBar
    private lateinit var trackPanel: LinearLayout
    private lateinit var trackScroll: android.widget.HorizontalScrollView
    private var channelUrls = emptyList<String>()
    private var channelTitles = emptyList<String>()
    private var channelIds = emptyList<String>()
    private var channelNumbers = emptyList<String>()
    private var channelFallbackUrls = emptyList<String>()
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
    private val widthDp: Int get() = resources.configuration.screenWidthDp
    private val heightDp: Int get() = resources.configuration.screenHeightDp

    // A phone in landscape can have a width above 600dp, so width alone
    // incorrectly gives it TV-sized controls. Use the shorter dimension too.
    private val phonePlayer: Boolean
        get() = widthDp < 600 || heightDp < 500

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt().coerceAtLeast(1)
