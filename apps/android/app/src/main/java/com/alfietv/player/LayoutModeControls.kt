package com.alfietv.player

import android.content.Context
import android.graphics.Typeface
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout

object LayoutModeControls {
    fun create(context: Context, screen: String, defaultMode: LayoutMode, onChanged: (LayoutMode) -> Unit): LinearLayout {
        val row = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            isFocusable = false
        }
        val buttons = linkedMapOf(LayoutMode.GRID to "▦ Grid", LayoutMode.LIST to "☰ List", LayoutMode.TILE to "⊞ Tiles")
        val current = LayoutModeStore.get(context, screen, defaultMode)
        buttons.forEach { (mode, label) ->
            val button = Button(context).apply {
                text = label
                isAllCaps = false
                textSize = 13f
                isFocusable = true
                isFocusableInTouchMode = true
                setOnClickListener {
                    LayoutModeStore.set(context, screen, mode)
                    updateSelection(row, mode)
                    onChanged(mode)
                }
                layoutParams = LinearLayout.LayoutParams(0, -2, 1f).apply { marginEnd = 6 }
            }
            button.tag = mode
            row.addView(button)
        }
        updateSelection(row, current)
        return row
    }

    private fun updateSelection(row: LinearLayout, selected: LayoutMode) {
        for (i in 0 until row.childCount) {
            val button = row.getChildAt(i) as? Button ?: continue
            val active = button.tag == selected
            button.setTypeface(null, if (active) Typeface.BOLD else Typeface.NORMAL)
            button.alpha = if (active) 1f else 0.72f
        }
    }
}
