package com.alfietv.player

import android.animation.ValueAnimator
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.View
import android.view.animation.LinearInterpolator

/** Device-local Alfie TV visual skin selection with lightweight ambient animation. */
object SkinStore {
    data class Skin(val id: String, val name: String, val background: Int, val surface: Int, val surface2: Int, val accent: Int, val secondary: Int, val current: Int)
    private const val PREFS = "alfie_tv_settings"
    private const val KEY = "skin"
    private val skins = listOf(
        Skin("aurora", "Aurora", Color.rgb(5, 9, 22), Color.rgb(15, 23, 43), Color.rgb(25, 35, 60), Color.rgb(38, 194, 255), Color.rgb(142, 91, 255), Color.rgb(24, 105, 190)),
        Skin("neon", "Neon", Color.rgb(18, 6, 20), Color.rgb(35, 14, 38), Color.rgb(53, 20, 57), Color.rgb(255, 69, 173), Color.rgb(255, 139, 61), Color.rgb(143, 34, 105)),
        Skin("ocean", "Ocean", Color.rgb(3, 13, 20), Color.rgb(9, 28, 39), Color.rgb(15, 42, 55), Color.rgb(35, 213, 188), Color.rgb(32, 133, 255), Color.rgb(12, 91, 120))
    )
    fun all(): List<Skin> = skins
    fun current(context: android.content.Context): Skin = skins.firstOrNull {
        it.id == context.getSharedPreferences(PREFS, android.content.Context.MODE_PRIVATE).getString(KEY, "aurora")
    } ?: skins.first()
    fun set(context: android.content.Context, id: String) {
        if (skins.any { it.id == id }) context.getSharedPreferences(PREFS, android.content.Context.MODE_PRIVATE).edit().putString(KEY, id).apply()
    }
    fun animate(view: View, skin: Skin) {
        view.background = GradientDrawable(GradientDrawable.Orientation.TL_BR, intArrayOf(skin.background, skin.surface, skin.background))
        ValueAnimator.ofFloat(0.86f, 1f).apply {
            duration = 2200L
            repeatMode = ValueAnimator.REVERSE
            repeatCount = ValueAnimator.INFINITE
            interpolator = LinearInterpolator()
            addUpdateListener { view.alpha = it.animatedValue as Float }
            start()
        }
    }
}
