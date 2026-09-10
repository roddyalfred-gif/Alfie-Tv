package com.alfietv.player

import android.graphics.BitmapFactory
import android.graphics.drawable.ColorDrawable
import android.os.Handler
import android.os.Looper
import android.widget.ImageView
import java.net.HttpURLConnection
import java.net.URI
import java.util.concurrent.Executors

/** Small dependency-free artwork loader for channel logos and VOD/series posters. */
internal object ArtworkLoader {
    private val executor = Executors.newFixedThreadPool(3)
    private val main = Handler(Looper.getMainLooper())
    private const val TIMEOUT_MS = 8_000

    fun load(url: String?, view: ImageView, placeholder: Int) {
        view.setImageResource(placeholder)
        val clean = url?.trim()?.takeIf { it.startsWith("http://") || it.startsWith("https://") } ?: return
        view.tag = clean
        executor.execute {
            val bitmap = runCatching {
                val connection = URI(clean).toURL().openConnection() as HttpURLConnection
                connection.connectTimeout = TIMEOUT_MS
                connection.readTimeout = TIMEOUT_MS
                connection.instanceFollowRedirects = true
                connection.inputStream.use { BitmapFactory.decodeStream(it) }.also { connection.disconnect() }
            }.getOrNull()
            if (bitmap != null) main.post {
                if (view.tag == clean) view.setImageBitmap(bitmap)
            }
        }
    }
}
