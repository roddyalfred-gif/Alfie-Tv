package com.alfietv.player

import android.app.AlertDialog
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.widget.*
import java.net.URI
import java.util.concurrent.Executors

class LoginActivity : androidx.activity.ComponentActivity() {
    private val executor = Executors.newSingleThreadExecutor()
    private lateinit var status: TextView
    private lateinit var button: Button

    private val backgroundColor = Color.rgb(8, 12, 22)
    private val surfaceColor = Color.rgb(18, 25, 40)
    private val accentColor = Color.rgb(0, 168, 255)
    private val textColor = Color.WHITE
    private val secondaryTextColor = Color.rgb(170, 181, 200)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        title = "Alfie TV"
        window.statusBarColor = backgroundColor
        window.navigationBarColor = backgroundColor

        val forceLogin = intent.getBooleanExtra("forceLogin", false)
        if (!forceLogin) {
            SessionStore.load(this)?.let { saved ->
                connect(saved, autoLogin = true)
                return
            }
        }

        buildLoginForm()
        if (!getPreferences(MODE_PRIVATE).getBoolean("content_notice_shown", false)) {
            showFirstLaunchNotice()
        }
    }

    private fun showFirstLaunchNotice() {
        AlertDialog.Builder(this)
            .setTitle("Welcome to Alfie TV")
            .setMessage("This player doesn't contain any content at first load. Press OK to confirm, then enter your provider login credentials to load your Live TV, Movies, Series and EPG content.")
            .setPositiveButton("OK") { dialog, _ ->
                getPreferences(MODE_PRIVATE).edit().putBoolean("content_notice_shown", true).apply()
                dialog.dismiss()
            }
            .setCancelable(false)
            .show()
            .also { dialog ->
                dialog.setOnShowListener {
                    dialog.getButton(AlertDialog.BUTTON_POSITIVE)?.requestFocus()
                }
            }
    }

    private fun buildLoginForm() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(72, 36, 72, 36)
            setBackgroundColor(backgroundColor)
        }

        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(56, 44, 56, 44)
            background = roundedBackground(surfaceColor, 24f)
            elevation = 12f
        }

        val logo = TextView(this).apply {
            text = "ALFIE TV"
            textSize = 30f
            setTextColor(accentColor)
            gravity = Gravity.CENTER
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            letterSpacing = 0.12f
        }
        card.addView(logo, LinearLayout.LayoutParams(-1, -2))

        val title = TextView(this).apply {
            text = "Connect your provider"
            textSize = 22f
            setTextColor(textColor)
            gravity = Gravity.CENTER
            typeface = android.graphics.Typeface.DEFAULT_BOLD
        }
        card.addView(title, LinearLayout.LayoutParams(-1, -2).apply { topMargin = 18 })

        val subtitle = TextView(this).apply {
            text = "Enter your IPTV server and account credentials to load your content."
            textSize = 14f
            setTextColor(secondaryTextColor)
            gravity = Gravity.CENTER
        }
        card.addView(subtitle, LinearLayout.LayoutParams(-1, -2).apply { topMargin = 8 })

        val server = field("Provider URL", "https://provider.example.com", 33)
        val username = field("Username", "Username", 33)
        val password = field("Password", "Password", InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD)

        card.addView(server, fieldParams())
        card.addView(username, fieldParams())
        card.addView(password, fieldParams())

        button = Button(this).apply {
            text = "CONNECT"
            isAllCaps = false
            textSize = 16f
            setTextColor(Color.WHITE)
            background = roundedBackground(accentColor, 14f)
            isFocusable = true
            isFocusableInTouchMode = true
        }
        card.addView(button, LinearLayout.LayoutParams(-1, 58).apply { topMargin = 22 })

        val spinner = ProgressBar(this).apply { visibility = View.GONE }
        card.addView(spinner, LinearLayout.LayoutParams(-1, 44).apply { topMargin = 8 })

        status = TextView(this).apply {
            gravity = Gravity.CENTER
            textSize = 14f
            setTextColor(secondaryTextColor)
        }
        card.addView(status, LinearLayout.LayoutParams(-1, -2).apply { topMargin = 4 })

        root.addView(card, LinearLayout.LayoutParams(-1, -2).apply { gravity = Gravity.CENTER; weight = 1f })

        val footer = TextView(this).apply {
            text = "Alfie TV • Live TV • Movies • Series • EPG"
            textSize = 12f
            setTextColor(secondaryTextColor)
            gravity = Gravity.CENTER
        }
        root.addView(footer, LinearLayout.LayoutParams(-1, -2).apply { topMargin = 14 })
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
            spinner.visibility = View.VISIBLE
            status.text = "Connecting securely..."
            connect(XtreamConfig(url, user, pass), autoLogin = false, spinner = spinner)
        }

        server.requestFocus()
    }

    private fun field(label: String, hint: String, inputType: Int): EditText = EditText(this).apply {
        this.hint = hint
        this.inputType = inputType
        textSize = 16f
        setTextColor(textColor)
        setHintTextColor(secondaryTextColor)
        setSingleLine(true)
        contentDescription = label
        background = roundedBackground(Color.rgb(28, 37, 55), 12f)
        setPadding(18, 0, 18, 0)
        isFocusable = true
        isFocusableInTouchMode = true
    }

    private fun fieldParams() = LinearLayout.LayoutParams(-1, 56).apply { topMargin = 12 }

    private fun roundedBackground(color: Int, radiusDp: Float): GradientDrawable = GradientDrawable().apply {
        setColor(color)
        cornerRadius = radiusDp * resources.displayMetrics.density
    }

    private fun connect(config: XtreamConfig, autoLogin: Boolean, spinner: ProgressBar? = null) {
        if (::status.isInitialized) status.text = if (autoLogin) "Restoring provider session..." else "Connecting..."
        if (::button.isInitialized) button.isEnabled = false
        spinner?.visibility = View.VISIBLE
        executor.execute {
            try {
                XtreamClient().load(config)
                SessionStore.save(this, config)
                runOnUiThread {
                    spinner?.visibility = View.GONE
                    startActivity(Intent(this, HomeActivity::class.java).apply {
                        putExtra("server", config.serverUrl)
                        putExtra("username", config.username)
                        putExtra("password", config.password)
                    })
                    finish()
                }
            } catch (e: Exception) {
                SessionStore.clear(this)
                runOnUiThread {
                    spinner?.visibility = View.GONE
                    if (::button.isInitialized) button.isEnabled = true
                    if (!::status.isInitialized) buildLoginForm()
                    status.text = if (autoLogin) "Saved provider session expired. Please reconnect." else "Connection failed: ${e.message ?: "unknown error"}"
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
