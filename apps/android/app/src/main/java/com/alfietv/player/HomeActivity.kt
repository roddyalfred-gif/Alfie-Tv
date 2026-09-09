package com.alfietv.player

import android.content.Intent
import android.os.Bundle
import android.view.Gravity
import android.widget.*

class HomeActivity : androidx.activity.ComponentActivity() {
    private lateinit var config: XtreamConfig
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        config = XtreamConfig(intent.getStringExtra("server") ?: "", intent.getStringExtra("username") ?: "", intent.getStringExtra("password") ?: "")
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER; setPadding(48, 32, 48, 32) }
        root.addView(TextView(this).apply { text = "Alfie TV"; textSize = 34f; gravity = Gravity.CENTER })
        root.addView(TextView(this).apply { text = "Choose what you want to watch"; textSize = 18f; gravity = Gravity.CENTER; setPadding(0, 8, 0, 24) })
        addButton(root, "Live TV") { open(LiveTvActivity::class.java) }
        addButton(root, "Movies") { open(ContentActivity::class.java, "vod") }
        addButton(root, "Series") { open(ContentActivity::class.java, "series") }
        addButton(root, "Favorites") { open(LiveTvActivity::class.java) }
        setContentView(root)
    }
    private fun addButton(root: LinearLayout, label: String, action: () -> Unit) { root.addView(Button(this).apply { text = label; isAllCaps = false; minHeight = 64; setOnClickListener { action() } }, LinearLayout.LayoutParams(-1, -2).apply { topMargin = 8 }) }
    private fun open(clazz: Class<*>, mode: String? = null) { startActivity(Intent(this, clazz).apply { putExtra("server", config.serverUrl); putExtra("username", config.username); putExtra("password", config.password); mode?.let { putExtra("mode", it) } }) }
}
