package com.alfietv.player

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
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
        }
        setContentView(playerView)
        alfiePlayer = AlfiePlayer(this)
        alfiePlayer.attach(playerView)
        val url = intent.getStringExtra("stream_url")
        if (!url.isNullOrBlank()) alfiePlayer.play(url, intent.getStringExtra("title") ?: "Alfie TV")
    }

    override fun onStart() { super.onStart(); playerView.requestFocus() }
    override fun onStop() { super.onStop(); alfiePlayer.stop() }
    override fun onDestroy() { alfiePlayer.release(); super.onDestroy() }
}
