package com.xinglinghu.manager.ui.screen.ikun

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.xinglinghu.manager.R
import com.xinglinghu.manager.ui.cosmetic.FallingEffect

/** Localized name for one selectable falling effect. */
@Composable
internal fun FallingEffect.label(): String = stringResource(
    when (this) {
        FallingEffect.OFF -> R.string.ikun_effect_off
        FallingEffect.SNOW -> R.string.ikun_effect_snow
        FallingEffect.TROLL -> R.string.ikun_effect_troll
        FallingEffect.LAUNCHER -> R.string.ikun_effect_launcher
    }
)

/**
 * Gravity calibration choices, in the same order as the `gravityMode` values the
 * overlay passes to the sensor code: normal, mirror X, mirror Y, rotate 180.
 */
internal val GRAVITY_KEYS = listOf(
    R.string.ikun_gravity_normal,
    R.string.ikun_gravity_mirror_x,
    R.string.ikun_gravity_mirror_y,
    R.string.ikun_gravity_rotate,
)

/**
 * Preset frosted-glass tints, in the order they are laid out.
 *
 * The list deliberately spans both ends of the brightness range: the deep tints
 * keep the existing dark look and guarantee contrast for light text, while the
 * near-white tint is the classic "frosted" appearance. A pure hue slider cannot
 * reach either end, which is why the presets exist at all.
 */
internal val GLASS_COLOR_PRESETS = listOf(
    0xFFF2F5FF.toInt(), // near-white frost
    0xFF0B0E1A.toInt(), // near-black
    0xFF16203A.toInt(), // deep blue-grey (the default)
    0xFF7FB6E8.toInt(), // sky blue
    0xFF9A7FD0.toInt(), // violet
    0xFF6AC8B0.toInt(), // teal
    0xFFD0A06A.toInt(), // amber
    0xFFD07FA0.toInt(), // pink
)

/** Hue in degrees (0..360) of [color], used to seed the custom-colour slider. */
internal fun glassHueOf(color: Int): Float {
    val c = Color(color)
    val max = maxOf(c.red, c.green, c.blue)
    val min = minOf(c.red, c.green, c.blue)
    val delta = max - min
    if (delta <= 0.0001f) return 0f
    val hue = when (max) {
        c.red -> ((c.green - c.blue) / delta) % 6f
        c.green -> (c.blue - c.red) / delta + 2f
        else -> (c.red - c.green) / delta + 4f
    }
    return ((hue * 60f) + 360f) % 360f
}

/** Tint produced by the custom-colour slider at [hue], kept soft and light. */
internal fun glassColorForHue(hue: Float): Int =
    Color.hsv(((hue % 360f) + 360f) % 360f, 0.35f, 0.95f).toArgb()

/**
 * Row of tappable tint swatches, shared by both UI modes.
 *
 * Built from plain Compose primitives rather than Miuix or Material3 controls so
 * one implementation serves both themes; the selection ring uses a fixed accent
 * that stays visible against dark and light card surfaces alike.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun GlassColorSwatches(selected: Int, onSelect: (Int) -> Unit) {
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        GLASS_COLOR_PRESETS.forEach { preset ->
            val isSelected = preset == selected
            Box(
                Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(Color(preset))
                    .border(
                        if (isSelected) BorderStroke(2.dp, Color(0xFF3B82F6)) else BorderStroke(1.dp, Color(0x33000000)),
                        CircleShape,
                    )
                    .clickable { onSelect(preset) }
            )
        }
    }
}
