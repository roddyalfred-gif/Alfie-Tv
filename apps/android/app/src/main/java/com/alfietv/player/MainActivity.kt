package com.alfietv.player

import android.os.Bundle
import android.view.KeyEvent
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView

class MainActivity : ComponentActivity() {
    private lateinit var playerView: PlayerView
    private lateinit var alfiePlayer: AlfiePlayer

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        playerView = PlayerView(this).apply {
            useController = true
            setShowBuffering(PlayerView.SHOW_BUFFERING_WHEN_PLAYING)
            resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
            isFocusable = true
            isFocusableInTouchMode = true
        }
        setContentView(playerView)

        alfiePlayer = AlfiePlayer(this)
        alfiePlayer.attach(playerView)

        val url = intent.getStringExtra("stream_url")
        if (!url.isNullOrBlank()) {
            alfiePlayer.play(url, intent.getStringExtra("title") ?: "Alfie TV")
        }
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.action == KeyEvent.ACTION_DOWN) {
            when (event.keyCode) {
                KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE -> {
                    if (alfiePlayer.player.isPlaying) alfiePlayer.player.pause()
                    else alfiePlayer.player.play()
                    return true
                }
                KeyEvent.KEYCODE_MEDIA_PLAY -> {
                    alfiePlayer.player.play()
                    return true
                }
                KeyEvent.KEYCODE_MEDIA_PAUSE -> {
                    alfiePlayer.player.pause()
                    return true
                }
            }
        }
        return super.dispatchKeyEvent(event)
    }

    override fun onStart() {
        super.onStart()
        playerView.requestFocus()
        alfiePlayer.player.playWhenReady = true
    }

    override fun onStop() {
        alfiePlayer.stop()
        super.onStop()
    }

    override fun onDestroy() {
        alfiePlayer.release()
        super.onDestroy()
    }
}
