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
import android.widget.EditText
import android.widget.GridView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast

/** Provider-scoped Favorites and Recently Watched with persistent Grid/List/Tile layouts. */
class FavoritesActivity : androidx.activity.ComponentActivity() {
    private lateinit var list: GridView
    private lateinit var status: TextView
    private lateinit var config: XtreamConfig
    private lateinit var search: EditText
    private lateinit var favoritesButton: TextView
    private lateinit var recentButton: TextView
    private var favorites = emptyList<UserLibraryStore.Item>()
    private var recent = emptyList<UserLibraryStore.Item>()
    private var showingRecent = false
    private var currentLayoutMode = LayoutMode.LIST
    private var query = ""

    private val bg = Color.rgb(5, 9, 18)
    private val panel = Color.rgb(13, 21, 35)
    private val row = Color.rgb(15, 24, 40)
    private val accent = Color.rgb(0, 168, 255)
    private val muted = Color.rgb(170, 181, 200)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = bg
        window.navigationBarColor = bg
        config = XtreamConfig(intent.getStringExtra("server") ?: "", intent.getStringExtra("username") ?: "", intent.getStringExtra("password") ?: "")
        currentLayoutMode = LayoutModeStore.get(this, "favorites", LayoutMode.LIST)
        buildUi()
        reload()
    }

    override fun onResume() { super.onResume(); if (::list.isInitialized) reload() }

    private fun buildUi() {
        val widthDp = resources.configuration.screenWidthDp
        val compact = widthDp < 600
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(if (widthDp < 360) 10 else if (compact) 14 else 18, if (compact) 10 else 14,
                if (widthDp < 360) 10 else if (compact) 14 else 18, if (compact) 8 else 10)
            setBackgroundColor(SkinStore.current(this@FavoritesActivity).background)
        }
        val header = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        header.addView(TextView(this).apply {
            text = "My Library"; textSize = if (compact) 22f else 28f; setTextColor(Color.WHITE); typeface = Typeface.DEFAULT_BOLD
        }, LinearLayout.LayoutParams(0, -2, 1f))
        search = EditText(this).apply {
            hint = "Search library..."; setSingleLine(true); textSize = 16f
            setTextColor(Color.WHITE); setHintTextColor(muted); setPadding(16, 0, 16, 0)
            background = roundedBackground(panel, 12f)
            addTextChangedListener(object : android.text.TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) { query = s?.toString()?.trim()?.lowercase().orEmpty(); render() }
                override fun afterTextChanged(s: android.text.Editable?) = Unit
            })
        }
        if (compact) {
            header.orientation = LinearLayout.VERTICAL
            header.gravity = Gravity.START
            header.addView(TextView(this).apply {
                text = if (showingRecent) "Recently Watched" else "Saved Favorites"
                textSize = 11f
                setTextColor(muted)
            }, LinearLayout.LayoutParams(-1, 22))
            header.addView(search, LinearLayout.LayoutParams(-1, 48).apply { topMargin = 4 })
            root.addView(header, LinearLayout.LayoutParams(-1, 78))
        } else {
            header.addView(search, LinearLayout.LayoutParams(0, 52, 1.6f))
            root.addView(header, LinearLayout.LayoutParams(-1, 58))
        }

        val tabs = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        favoritesButton = tabButton("FAVORITES") { showingRecent = false; render() }
        recentButton = tabButton("RECENT") { showingRecent = true; render() }
        tabs.addView(favoritesButton, LinearLayout.LayoutParams(0, 44, 1f).apply { marginEnd = 6 })
        tabs.addView(recentButton, LinearLayout.LayoutParams(0, 44, 1f))
        root.addView(tabs, LinearLayout.LayoutParams(-1, 48))

        val layoutRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        layoutRow.addView(TextView(this).apply { text = "View"; textSize = 13f; setTextColor(muted); setPadding(4, 0, 8, 0) }, LinearLayout.LayoutParams(-2, 44))
        layoutRow.addView(LayoutModeControls.create(this, "favorites", LayoutMode.LIST) { selected ->
            currentLayoutMode = selected
            applyLayoutMode()
            render()
        }, LinearLayout.LayoutParams(0, 44, 1f))
        root.addView(layoutRow, LinearLayout.LayoutParams(-1, 46).apply { bottomMargin = 4 })

        list = GridView(this).apply {
            isFocusable = true; isFocusableInTouchMode = true
            verticalSpacing = 8; horizontalSpacing = 8; stretchMode = GridView.STRETCH_COLUMN_WIDTH
            setPadding(0, 2, 0, 2)
            setOnKeyListener { _, keyCode, event ->
                if (event.action != KeyEvent.ACTION_DOWN) return@setOnKeyListener false
                when (keyCode) {
                    KeyEvent.KEYCODE_DPAD_LEFT -> if (numColumns > 1 && selectedItemPosition % numColumns == 0) { favoritesButton.requestFocus(); true } else false
                    KeyEvent.KEYCODE_DPAD_UP -> if (selectedItemPosition in 0 until maxOf(1, numColumns)) { favoritesButton.requestFocus(); true } else false
                    else -> false
                }
            }
        }
        root.addView(list, LinearLayout.LayoutParams(-1, 0, 1f))
        status = TextView(this).apply { textSize = 13f; setTextColor(muted); setPadding(4, 7, 4, 2) }
        root.addView(status, LinearLayout.LayoutParams(-1, 28))
        setContentView(root)

        list.setOnItemClickListener { _, _, position, _ -> filteredItems().getOrNull(position)?.let(::open) }
        list.setOnItemLongClickListener { _, _, position, _ ->
            if (!showingRecent) {
                filteredItems().getOrNull(position)?.let { item ->
                    UserLibraryStore.toggleFavorite(this, config, item)
                    Toast.makeText(this, "Removed from favorites", Toast.LENGTH_SHORT).show()
                    reload()
                }
                true
            } else false
        }
        applyLayoutMode()
    }

    private fun tabButton(label: String, action: () -> Unit) = TextView(this).apply {
        text = label; textSize = 14f; gravity = Gravity.CENTER; isFocusable = true; isFocusableInTouchMode = true
        setTextColor(Color.WHITE); background = roundedBackground(row, 12f); setOnClickListener { action() }
        setOnFocusChangeListener { view, focused -> view.background = roundedBackground(if (focused) accent else row, 12f) }
    }

    private fun applyLayoutMode() {
        list.numColumns = when (currentLayoutMode) {
            LayoutMode.LIST -> 1
            LayoutMode.GRID -> if (resources.configuration.screenWidthDp < 360) 1 else if (resources.configuration.screenWidthDp < 600) 2 else 3
            LayoutMode.TILE -> if (resources.configuration.screenWidthDp < 600) 2 else 4
        }
        list.horizontalSpacing = if (currentLayoutMode == LayoutMode.LIST) 0 else 8
        list.verticalSpacing = 10
    }

    private fun reload() {
        favorites = UserLibraryStore.favorites(this, config)
        recent = UserLibraryStore.recent(this, config)
        render()
    }

    private fun filteredItems(): List<UserLibraryStore.Item> {
        val source = if (showingRecent) recent else favorites
        return if (query.isBlank()) source else source.filter { it.title.lowercase().contains(query) }
    }

    private fun render() {
        val items = filteredItems()
        favoritesButton.setTextColor(if (!showingRecent) Color.WHITE else muted)
        recentButton.setTextColor(if (showingRecent) Color.WHITE else muted)
        list.adapter = object : ArrayAdapter<UserLibraryStore.Item>(this, android.R.layout.simple_list_item_1, items) {
            override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
                val item = getItem(position) ?: return TextView(this@FavoritesActivity)
                val card = LinearLayout(this@FavoritesActivity).apply {
                    orientation = if (currentLayoutMode == LayoutMode.LIST) LinearLayout.HORIZONTAL else LinearLayout.VERTICAL
                    gravity = Gravity.CENTER
                    minimumHeight = when (currentLayoutMode) { LayoutMode.LIST -> 72; LayoutMode.GRID -> 150; LayoutMode.TILE -> 112 }
                    setPadding(if (resources.configuration.screenWidthDp < 600) 8 else 10, 8, if (resources.configuration.screenWidthDp < 600) 8 else 10, 8); background = roundedBackground(row, 12f)
                }
                val imageSize = when (currentLayoutMode) { LayoutMode.LIST -> 52; LayoutMode.GRID -> 86; LayoutMode.TILE -> 58 }
                val icon = ImageView(this@FavoritesActivity).apply {
                    layoutParams = LinearLayout.LayoutParams(imageSize, imageSize).apply { if (currentLayoutMode == LayoutMode.LIST) marginEnd = 12 else bottomMargin = 5 }
                    scaleType = ImageView.ScaleType.CENTER_INSIDE
                    setImageResource(android.R.drawable.ic_media_play)
                }
                val box = LinearLayout(this@FavoritesActivity).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER }
                val title = TextView(this@FavoritesActivity).apply {
                    text = "${if (!showingRecent) "★ " else ""}${item.title}"
                    textSize = if (currentLayoutMode == LayoutMode.LIST) 17f else 14f
                    setTextColor(Color.WHITE); typeface = Typeface.DEFAULT_BOLD; gravity = Gravity.CENTER; maxLines = 2
                }
                val type = TextView(this@FavoritesActivity).apply {
                    text = when (item.type) { UserLibraryStore.Type.LIVE -> "LIVE"; UserLibraryStore.Type.MOVIE -> "MOVIE"; UserLibraryStore.Type.SERIES -> "SERIES"; UserLibraryStore.Type.EPISODE -> "EPISODE" }
                    textSize = 11f; setTextColor(muted); gravity = Gravity.CENTER; setPadding(0, 3, 0, 0)
                }
                box.addView(title); box.addView(type)
                card.addView(icon); card.addView(box, LinearLayout.LayoutParams(if (currentLayoutMode == LayoutMode.LIST) 0 else -1, -2, if (currentLayoutMode == LayoutMode.LIST) 1f else 0f))
                return card
            }
        }
        status.text = if (items.isEmpty()) {
            if (showingRecent) "Nothing watched yet" else "No favorites yet • Long-press an item to remove it"
        } else if (showingRecent) "${items.size} recently watched • ${layoutLabel()} view" else "${items.size} favorites • Long-press to remove • ${layoutLabel()} view"
        list.post { list.requestFocus() }
    }

    private fun layoutLabel() = when (currentLayoutMode) { LayoutMode.GRID -> "Grid"; LayoutMode.LIST -> "List"; LayoutMode.TILE -> "Tile" }

    private fun open(item: UserLibraryStore.Item) {
        when (item.type) {
            UserLibraryStore.Type.SERIES -> startActivity(Intent(this, ContentActivity::class.java).apply {
                putExtra("server", config.serverUrl); putExtra("username", config.username); putExtra("password", config.password)
                putExtra("mode", "series"); putExtra("selected_series_id", item.id)
            })
            else -> startActivity(Intent(this, MainActivity::class.java).apply {
                putExtra("stream_url", item.streamUrl); putExtra("title", item.title); putExtra("content_id", item.id)
                putExtra("content_type", item.type.name); putExtra("server", config.serverUrl); putExtra("username", config.username); putExtra("password", config.password)
            })
        }
    }

    private fun roundedBackground(color: Int, radius: Float): GradientDrawable = GradientDrawable().apply {
        setColor(color); cornerRadius = radius * resources.displayMetrics.density
    }
}
