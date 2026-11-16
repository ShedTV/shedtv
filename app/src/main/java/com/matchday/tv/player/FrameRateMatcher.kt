package com.matchday.tv.player

import android.app.Activity
import android.view.Display
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Switches the TV's refresh rate to suit the stream (e.g. 50Hz for UK sport),
 * so motion is smooth instead of juddering at the Fire Stick's default 60Hz.
 * On Fire TV this needs "Match original frame rate" turned on in display settings.
 */
object FrameRateMatcher {

    /** UK broadcasts are 25/50fps; used when a stream doesn't say. */
    const val DEFAULT_FPS = 50f

    fun match(activity: Activity, fps: Float) {
        val frameRate = if (fps > 1f) fps else DEFAULT_FPS
        val display = currentDisplay(activity) ?: return
        val current = display.mode
        val candidates = display.supportedModes
            .filter { it.physicalWidth == current.physicalWidth && it.physicalHeight == current.physicalHeight }
            .filter { it.refreshRate <= 61f && isMultiple(it.refreshRate, frameRate) }
            .sortedByDescending { it.refreshRate }
        val target = candidates.firstOrNull() ?: return
        if (target.modeId == current.modeId) return
        val attrs = activity.window.attributes
        attrs.preferredDisplayModeId = target.modeId
        activity.window.attributes = attrs
    }

    fun reset(activity: Activity) {
        val attrs = activity.window.attributes
        if (attrs.preferredDisplayModeId != 0) {
            attrs.preferredDisplayModeId = 0
            activity.window.attributes = attrs
        }
    }

    private fun isMultiple(refresh: Float, fps: Float): Boolean {
        val ratio = refresh / fps
        val n = ratio.roundToInt()
        return n >= 1 && abs(ratio - n) < 0.02f
    }

    @Suppress("DEPRECATION")
    private fun currentDisplay(activity: Activity): Display? =
        if (android.os.Build.VERSION.SDK_INT >= 30) activity.display else activity.windowManager.defaultDisplay
}
