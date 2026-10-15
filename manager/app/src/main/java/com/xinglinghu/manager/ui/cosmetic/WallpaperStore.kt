package com.xinglinghu.manager.ui.cosmetic

import android.content.Context
import android.content.res.Configuration
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import com.xinglinghu.manager.R
import java.io.File

/**
 * Portrait/landscape wallpaper slots.
 *
 * Kept as two independent slots so a wide image is not cropped to shreds on a
 * tablet in landscape. When the landscape slot has nothing usable the host falls
 * back to the portrait slot, which is what a single-wallpaper user expects.
 */
enum class WallpaperSlot(val key: String, val dirName: String) {
    PORTRAIT("wallpaper_portrait", "wallpaper"),
    LANDSCAPE("wallpaper_landscape", "wallpaper_land"),
}

/**
 * App-private wallpaper storage.
 *
 * ADAPTED from 7kimisu's `WallpaperStore`: their version is 633 lines built on a
 * settings repository with video support and a layered fallback chain. This fork
 * keeps the parts that carry the user-visible feature — pick an image, store it
 * privately without any storage permission, survive reinstalls of the APK, and
 * degrade to a bundled default rather than ever painting an empty backdrop.
 *
 * Video wallpapers are deliberately not ported: they would add a MediaPlayer
 * lifecycle, a second failure mode, and a large asset for a backdrop that sits
 * behind an opaque Manager UI most of the time.
 *
 * A [Context] is stashed once in [init] because the overlay is reachable from
 * composables that only ever hold the application context.
 */
object WallpaperStore {

    /** Bumped on every mutation so composables re-resolve. */
    var revision by mutableIntStateOf(0)
        private set

    private const val PREFS = "ikun_cosmetic"
    private const val SEEDED = "wallpaper_seeded"

    private var appContext: Context? = null

    fun init(context: Context) {
        appContext = context.applicationContext
    }

    private fun prefs(): android.content.SharedPreferences? =
        appContext?.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private fun dir(slot: WallpaperSlot): File? =
        appContext?.let { File(it.filesDir, slot.dirName).apply { mkdirs() } }

    /** The user's own wallpaper file for [slot], or null when unset/unreadable. */
    fun userFile(slot: WallpaperSlot): File? {
        val name = prefs()?.getString(slot.key, null) ?: return null
        val file = File(dir(slot), name)
        // A file can exist yet be unreadable (data cleared mid-flight, bad copy).
        // Treat that as "unset" so the host falls back instead of drawing nothing.
        return file.takeIf { it.isFile && it.length() > 0 }
    }

    /** Bundled art that always exists, so the backdrop is never blank. */
    fun defaultRes(slot: WallpaperSlot): Int = when (slot) {
        WallpaperSlot.PORTRAIT -> R.drawable.wallpaper_default_portrait
        WallpaperSlot.LANDSCAPE -> R.drawable.wallpaper_default_landscape
    }

    /**
     * Slot actually used for rendering: landscape borrows the portrait image
     * until the user sets a landscape-specific one.
     */
    fun effective(slot: WallpaperSlot): WallpaperSlot =
        if (slot == WallpaperSlot.LANDSCAPE && userFile(WallpaperSlot.LANDSCAPE) == null) {
            WallpaperSlot.PORTRAIT
        } else {
            slot
        }

    fun slotFor(configuration: Configuration): WallpaperSlot =
        if (configuration.orientation == Configuration.ORIENTATION_LANDSCAPE) {
            WallpaperSlot.LANDSCAPE
        } else {
            WallpaperSlot.PORTRAIT
        }

    fun hasOwn(slot: WallpaperSlot): Boolean = userFile(slot) != null

    /** Copies [uri] into private storage. Returns false when the image cannot be read. */
    fun pick(slot: WallpaperSlot, uri: android.net.Uri): Boolean {
        val context = appContext ?: return false
        val target = dir(slot) ?: return false
        val name = "wall_${slot.name.lowercase()}_${System.currentTimeMillis()}"
        val file = File(target, name)
        return runCatching {
            context.contentResolver.openInputStream(uri).use { input ->
                requireNotNull(input) { "no stream" }
                file.outputStream().use { output -> input.copyTo(output) }
            }
            require(file.length() > 0) { "empty" }
            // Drop the previous file only after the new one is safely in place.
            userFile(slot)?.takeIf { it != file }?.delete()
            prefs()?.edit()?.putString(slot.key, name)?.apply()
            true
        }.getOrElse {
            file.delete()
            false
        }.also { if (it) revision++ }
    }

    fun clear(slot: WallpaperSlot) {
        userFile(slot)?.delete()
        prefs()?.edit()?.remove(slot.key)?.apply()
        revision++
    }

    /**
     * Writes the bundled defaults into private storage on first launch only.
     *
     * Seeding means the user opens the wallpaper picker with a real image already
     * selected and can tune opacity against something, instead of facing a
     * "no wallpaper set" dead end. Existing installs are never touched.
     */
    fun seedDefaultsIfNeeded() {
        val context = appContext ?: return
        val store = prefs() ?: return
        if (store.getBoolean(SEEDED, false)) return
        var changed = false
        for (slot in WallpaperSlot.entries) {
            if (userFile(slot) != null) continue
            val name = "builtin_${slot.name.lowercase()}.jpg"
            val file = File(dir(slot), name)
            val ok = runCatching {
                context.resources.openRawResource(defaultRes(slot)).use { input ->
                    file.outputStream().use { output -> input.copyTo(output) }
                }
                file.length() > 0
            }.getOrDefault(false)
            if (ok) {
                store.edit().putString(slot.key, name).apply()
                changed = true
            }
        }
        store.edit().putBoolean(SEEDED, true).apply()
        if (changed) revision++
    }
}
