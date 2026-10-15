package com.xinglinghu.manager.ui.screen.ikun

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
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
