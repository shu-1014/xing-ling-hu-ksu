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
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Draws the personalized wallpaper behind [content], frosted into a translucent
 * glass sheet.
 *
 * ADAPTED from 7kimisu's `WallpaperHost`. Their version additionally layers a dim
 * overlay and plays video wallpapers; this fork keeps the image path and exposes
 * opacity through [alpha] instead, which is what the Manager's own "wallpaper
 * opacity" row drives.
 *
 * The backdrop is only drawn when [alpha] is above zero, so users who never open
 * the personalization screen pay nothing: no decode, no extra layer.
 *
 * Resolution order is own file -> bundled default, and the decode happens off the
 * main thread. While it is in flight the bundled painter is shown, so the
 * backdrop never flashes empty.
 *
 * The background is rendered as frosted glass rather than the flat black scrim
 * this fork started with: the wallpaper layer itself is blurred and then tinted
 * with the user's glass colour. Blurring the layer that is actually behind the
 * translucent Manager surfaces is what makes the whole background read as glass,
 * and it keeps light text legible over a bright photograph.
 *
 * The frosting is always on while a wallpaper is shown, and the glass opacity
 * only drives the tint. That keeps the control continuous - 0% is clear frosted
 * glass (blur, no colour) and 100% is solid colour - instead of snapping between
 * a sharp and a blurred wallpaper partway along the slider. Removing the
 * wallpaper entirely (wallpaper opacity 0%) is what turns the effect off.
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

    val cosmetic = LocalCosmeticState.current
    val glassTint = Color(cosmetic.glassColor)
    val glassOpacity = cosmetic.glassAlpha.coerceIn(0f, 1f)

    val slot = WallpaperStore.effective(WallpaperStore.slotFor(configuration))
    val file = remember(revision, slot) { WallpaperStore.userFile(slot) }
    val fallback = WallpaperStore.defaultRes(slot)

    // Keyed on the resolved path so tuning opacity does not re-decode a large
    // image on every frame of the slider drag.
    val bitmap by produceState<ImageBitmap?>(
        initialValue = null,
        revision,
        file?.absolutePath,
    ) {
        value = withContext(Dispatchers.IO) {
            file?.let { runCatching { BitmapFactory.decodeFile(it.absolutePath)?.asImageBitmap() }.getOrNull() }
        }
    }

    Box(Modifier.fillMaxSize()) {
        // Frosting the wallpaper layer is the whole effect. Rectangle edge
        // treatment clamps at the layer bounds, so the blur does not fade to
        // transparent along the screen edges the way an unbounded blur would.
        val wallpaperModifier = Modifier
            .fillMaxSize()
            .blur(28.dp, BlurredEdgeTreatment.Rectangle)
        val image = bitmap
        if (image != null) {
            Image(
                bitmap = image,
                contentDescription = null,
                modifier = wallpaperModifier,
                contentScale = ContentScale.Crop,
                alpha = opacity,
            )
        } else {
            Image(
                painter = painterResource(fallback),
                contentDescription = null,
                modifier = wallpaperModifier,
                contentScale = ContentScale.Crop,
                alpha = opacity,
            )
        }
        // The colour that turns the blurred wallpaper into coloured glass. A
        // fully transparent tint leaves the glass clear rather than disabling
        // the frosting, which is what "translucency" is meant to select.
        Box(
            Modifier
                .fillMaxSize()
                .background(glassTint.copy(alpha = glassOpacity))
        )
        content()
    }
}
