package com.xinglinghu.manager.ui.cosmetic

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Draws the personalized wallpaper behind [content].
 *
 * ADAPTED from 7kimisu's `WallpaperHost`. Their version additionally layers a
 * dim overlay and plays video wallpapers; this fork keeps the image path and
 * exposes opacity through [alpha] instead, which is what the Manager's own
 * "wallpaper opacity" row drives.
 *
 * The backdrop is only drawn when [alpha] is above zero, so users who never open
 * the personalization screen pay nothing: no decode, no extra layer.
 *
 * Resolution order is own file -> bundled default, and the decode happens off the
 * main thread. While it is in flight the bundled painter is shown, so the
 * backdrop never flashes empty.
 */
@Composable
fun WallpaperHost(alpha: Float, content: @Composable () -> Unit) {
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val revision = WallpaperStore.revision
    val opacity = alpha.coerceIn(0f, 1f)

    if (opacity <= 0.001f) {
        content()
        return
    }

    val slot = WallpaperStore.effective(WallpaperStore.slotFor(configuration))
    val file = remember(revision, slot) { WallpaperStore.userFile(slot) }
    val fallback = WallpaperStore.defaultRes(slot)

    // Keyed on the resolved path so tuning opacity does not re-decode a large
    // image on every frame of the slider drag.
    val bitmap by produceState<androidx.compose.ui.graphics.ImageBitmap?>(
        initialValue = null,
        revision,
        file?.absolutePath,
    ) {
        value = withContext(Dispatchers.IO) {
            file?.let { runCatching { BitmapFactory.decodeFile(it.absolutePath)?.asImageBitmap() }.getOrNull() }
        }
    }

    Box(Modifier.fillMaxSize()) {
        val image = bitmap
        if (image != null) {
            Image(
                bitmap = image,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
                alpha = opacity,
            )
        } else {
            Image(
                painter = painterResource(fallback),
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
                alpha = opacity,
            )
        }
        // The Manager surfaces are translucent, so a dark scrim keeps text
        // legible over bright photographs without hiding the artwork.
        Box(
            Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.30f * opacity))
        )
        content()
    }
}
