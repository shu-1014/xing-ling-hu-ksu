package com.xinglinghu.manager.ui.cosmetic

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf

class CosmeticState(context: Context) {
    private val prefs = context.getSharedPreferences("ikun_cosmetic", Context.MODE_PRIVATE)
    var enabled by mutableStateOf(prefs.getBoolean("enabled", false))
        private set
    var snow by mutableStateOf(prefs.getBoolean("snow", false))
        private set
    var scale by mutableFloatStateOf(prefs.getFloat("scale", 1f).coerceIn(0.5f, 2f))
        private set
    var speed by mutableFloatStateOf(prefs.getFloat("speed", 1f).coerceIn(0.3f, 3f))
        private set

    fun enable(value: Boolean) { enabled = value; prefs.edit().putBoolean("enabled", value).apply() }
    fun snow(value: Boolean) { snow = value; prefs.edit().putBoolean("snow", value).apply() }
    fun scale(value: Float) { scale = value.coerceIn(0.5f, 2f); prefs.edit().putFloat("scale", scale).apply() }
    fun speed(value: Float) { speed = value.coerceIn(0.3f, 3f); prefs.edit().putFloat("speed", speed).apply() }
}

val LocalCosmeticState = staticCompositionLocalOf<CosmeticState> { error("Cosmetic state unavailable") }
