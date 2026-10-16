package com.xinglinghu.manager.ui.cosmetic

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf

/**
 * Which falling effect the global overlay renders.
 *
 * [SNOW] and [TROLL] are driven by the shared gravity field, so they follow the
 * device tilt. [LAUNCHER] is the original 星棱狐 mascot drift kept from the
 * previous release; it is deliberately excluded from the tilt system because it
 * uses the launcher foreground art at a much larger size, where tilt reads as
 * noisy rather than playful.
 */
enum class FallingEffect(val value: String) {
    OFF("off"),
    SNOW("snow"),
    TROLL("troll"),
    LAUNCHER("launcher");

    companion object {
        fun from(value: String?): FallingEffect =
            entries.firstOrNull { it.value == value } ?: OFF
    }
}

/**
 * Persisted appearance settings for the falling-effect overlay and the
 * wallpaper backdrop. Deliberately stored in SharedPreferences rather than in
 * the kernel-side config so they survive without root and apply before the
 * Manager has finished its root handshake.
 */
class CosmeticState(context: Context) {
    private val prefs = context.getSharedPreferences("ikun_cosmetic", Context.MODE_PRIVATE)

    /** Master switch for the falling-effect overlay. */
    var enabled by mutableStateOf(prefs.getBoolean("enabled", false))
        private set

    /** Selected effect. Migrated from the legacy boolean `snow` flag. */
    var effect by mutableStateOf(
        FallingEffect.from(
            prefs.getString("effect", null) ?: if (prefs.getBoolean("snow", false)) "snow" else "off"
        )
    )
        private set

    var scale by mutableFloatStateOf(prefs.getFloat("scale", 1f).coerceIn(0.5f, 2f))
        private set

    var speed by mutableFloatStateOf(prefs.getFloat("speed", 1f).coerceIn(0.3f, 3f))
        private set

    /** Follow the gravity sensor so particles flow to the low side of the screen. */
    var tilt by mutableStateOf(prefs.getBoolean("tilt", true))
        private set

    /**
     * Sensor axis calibration: 0 normal, 1 mirror X, 2 mirror Y, 3 rotate 180.
     * Some vendor ROMs report the gravity axes mirrored, which would send the
     * rain upward; this lets the user correct it without a rebuild.
     */
    var gravityMode by mutableIntStateOf(prefs.getInt("gravity_mode", 0).coerceIn(0, 3))
        private set

    /** Wallpaper backdrop opacity multiplier, 0f..1f. */
    var wallpaperAlpha by mutableFloatStateOf(prefs.getFloat("wallpaper_alpha", 1f).coerceIn(0f, 1f))
        private set

    /**
     * Tint of the frosted-glass sheet drawn between the wallpaper and the UI, as
     * a packed ARGB int. Defaults to a deep blue-grey, which is the tint the
     * plain black scrim used to emulate, so existing users see no jump.
     */
    var glassColor by mutableStateOf(prefs.getInt("glass_color", DEFAULT_GLASS_COLOR))
        private set

    /**
     * Opacity of that frosted-glass sheet, 0f..1f. This is the "translucency"
     * control: lower values let more of the wallpaper through.
     */
    var glassAlpha by mutableFloatStateOf(prefs.getFloat("glass_alpha", 0.35f).coerceIn(0f, 1f))
        private set

    /**
     * Show the release announcement sheet at launch.
     *
     * Stored inverted under [AnnouncementPrefs]' "never show" key so both this
     * state object and the popup itself read one shared value and can never
     * disagree about whether the sheet is suppressed.
     */
    var announcementEnabled by mutableStateOf(
        !prefs.getBoolean("announcement_never_show", false)
    )
        private set

    fun enable(value: Boolean) {
        enabled = value
        prefs.edit().putBoolean("enabled", value).apply()
    }

    fun effect(value: FallingEffect) {
        effect = value
        prefs.edit().putString("effect", value.value).apply()
    }

    fun scale(value: Float) {
        scale = value.coerceIn(0.5f, 2f)
        prefs.edit().putFloat("scale", scale).apply()
    }

    fun speed(value: Float) {
        speed = value.coerceIn(0.3f, 3f)
        prefs.edit().putFloat("speed", speed).apply()
    }

    fun tilt(value: Boolean) {
        tilt = value
        prefs.edit().putBoolean("tilt", value).apply()
    }

    fun gravityMode(value: Int) {
        gravityMode = value.coerceIn(0, 3)
        prefs.edit().putInt("gravity_mode", gravityMode).apply()
    }

    fun wallpaperAlpha(value: Float) {
        wallpaperAlpha = value.coerceIn(0f, 1f)
        prefs.edit().putFloat("wallpaper_alpha", wallpaperAlpha).apply()
    }

    fun announcementEnabled(value: Boolean) {
        announcementEnabled = value
        prefs.edit().putBoolean("announcement_never_show", !value).apply()
    }

    fun glassColor(value: Int) {
        glassColor = value
        prefs.edit().putInt("glass_color", value).apply()
    }

    fun glassAlpha(value: Float) {
        glassAlpha = value.coerceIn(0f, 1f)
        prefs.edit().putFloat("glass_alpha", glassAlpha).apply()
    }

    /** Legacy accessor retained for the pre-existing settings rows. */
    val snow: Boolean get() = effect == FallingEffect.SNOW

    companion object {
        /**
         * Deep blue-grey frosted tint. Chosen over a flat black so the sheet
         * reads as glass rather than as a dimmer, while still keeping light text
         * legible over a bright photograph.
         */
        const val DEFAULT_GLASS_COLOR: Int = 0xFF16203A.toInt()
    }
}

val LocalCosmeticState = staticCompositionLocalOf<CosmeticState> { error("Cosmetic state unavailable") }
