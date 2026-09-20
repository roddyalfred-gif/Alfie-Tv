package com.alfietv.player

import android.animation.ArgbEvaluator
import android.animation.ValueAnimator
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.View
import android.app.Activity
import android.view.animation.LinearInterpolator
import java.util.WeakHashMap

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
    private val animators = WeakHashMap<View, ValueAnimator>()

    fun all(): List<Skin> = skins

    fun current(context: android.content.Context): Skin = skins.firstOrNull {
        it.id == context.getSharedPreferences(PREFS, android.content.Context.MODE_PRIVATE).getString(KEY, "aurora")
    } ?: skins.first()

    fun set(context: android.content.Context, id: String) {
        if (skins.any { it.id == id }) {
            context.getSharedPreferences(PREFS, android.content.Context.MODE_PRIVATE)
                .edit()
                .putString(KEY, id)
                .apply()
        }
    }

    fun applyWindow(activity: Activity) {
        val selected = current(activity)
        activity.window.statusBarColor = selected.background
        activity.window.navigationBarColor = selected.background
    }

    fun animate(view: View, skin: Skin) {
        animators.remove(view)?.cancel()

        val drawable = GradientDrawable(
            GradientDrawable.Orientation.TL_BR,
            intArrayOf(skin.background, skin.surface, skin.background)
        )
        view.background = drawable
        view.alpha = 1f

        val from = intArrayOf(skin.background, skin.surface, skin.background)
        val to = intArrayOf(skin.surface, skin.surface2, skin.background)

        val animator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 3200L
            repeatMode = ValueAnimator.REVERSE
            repeatCount = ValueAnimator.INFINITE
            interpolator = LinearInterpolator()
            addUpdateListener {
                val fraction = it.animatedValue as Float
                drawable.colors = intArrayOf(
                    ArgbEvaluator().evaluate(fraction, from[0], to[0]) as Int,
                    ArgbEvaluator().evaluate(fraction, from[1], to[1]) as Int,
                    ArgbEvaluator().evaluate(fraction, from[2], to[2]) as Int
                )
            }
            start()
        }
        animators[view] = animator
    }
}
