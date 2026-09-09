package com.alfietv.player

import android.content.Intent
import android.os.Bundle
import android.view.Gravity
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.activity.ComponentActivity
import java.net.URI
import java.util.concurrent.Executors

class LoginActivity : ComponentActivity() {
    private val executor = Executors.newSingleThreadExecutor()
    private lateinit var status: TextView
    private lateinit var button: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        title = "Alfie TV"

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(48, 32, 48, 32)
        }
        val title = TextView(this).apply {
            text = "Alfie TV"
            textSize = 32f
            gravity = Gravity.CENTER
        }
        val server = EditText(this).apply { hint = "Provider URL (https://...)"; inputType = 33 }
        val username = EditText(this).apply { hint = "Username"; inputType = 33 }
        val password = EditText(this).apply { hint = "Password"; inputType = 129 }
        button = Button(this).apply { text = "Connect"; isAllCaps = false }
        status = TextView(this).apply { gravity = Gravity.CENTER; textSize = 15f }
        val spinner = ProgressBar(this).apply { visibility = ProgressBar.GONE }

        root.addView(title, LinearLayout.LayoutParams(-1, -2))
        root.addView(server, LinearLayout.LayoutParams(-1, -2).apply { topMargin = 24 })
        root.addView(username, LinearLayout.LayoutParams(-1, -2))
        root.addView(password, LinearLayout.LayoutParams(-1, -2))
        root.addView(button, LinearLayout.LayoutParams(-1, -2).apply { topMargin = 20 })
        root.addView(spinner, LinearLayout.LayoutParams(-1, -2))
        root.addView(status, LinearLayout.LayoutParams(-1, -2).apply { topMargin = 12 })
        setContentView(root)

        button.setOnClickListener {
            val url = server.text.toString().trim().trimEnd('/')
            val user = username.text.toString().trim()
            val pass = password.text.toString()
            if (!isValid(url) || user.isBlank() || pass.isBlank()) {
                status.text = "Enter a valid provider URL, username and password."
                return@setOnClickListener
            }
            button.isEnabled = false
            spinner.visibility = ProgressBar.VISIBLE
            status.text = "Connecting..."
            executor.execute {
                try {
                    val config = XtreamConfig(url, user, pass)
                    val (categories, channels) = XtreamClient().load(config)
                    runOnUiThread {
                        spinner.visibility = ProgressBar.GONE
                        button.isEnabled = true
                        if (channels.isEmpty()) {
                            status.text = "Connected, but no live channels were returned."
                        } else {
                            startActivity(Intent(this, LiveTvActivity::class.java).apply {
                                putExtra("server", config.serverUrl)
                                putExtra("username", config.username)
                                putExtra("password", config.password)
                                putParcelableArrayListExtra("categories", ArrayList(categories.map { it.toParcelable() }))
                                putParcelableArrayListExtra("channels", ArrayList(channels.map { it.toParcelable() }))
                            })
                        }
                    }
                } catch (e: Exception) {
                    runOnUiThread {
                        spinner.visibility = ProgressBar.GONE
                        button.isEnabled = true
                        status.text = "Connection failed: ${e.message ?: "unknown error"}"
                    }
                }
            }
        }
    }

    private fun isValid(url: String): Boolean = try {
        URI(url).scheme in listOf("http", "https") && URI(url).host != null
    } catch (_: Exception) { false }

    override fun onDestroy() {
        executor.shutdownNow()
        super.onDestroy()
    }
}
