package com.xinglinghu.manager.ui.cosmetic

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.res.painterResource
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.currentStateAsState
import com.xinglinghu.manager.R
import kotlin.math.sin
import kotlin.random.Random

private data class Particle(val x: Float, val y: Float, val speed: Float, val size: Float, val rotation: Float)

/**
 * Global falling-effect overlay.
 *
 * Runs above the whole navigation stack but below system dialogs, and draws only
 * (it never consumes pointer input), so every gesture still reaches the Manager
 * underneath. Suspended whenever the app is not [Lifecycle.State.RESUMED] so the
 * frame loop costs nothing in the background.
 *
 * The heavy particle systems live in [SnowfallOverlay] and [TrollRainOverlay];
 * this composable is the single dispatcher that also keeps the original mascot
 * drift available as [FallingEffect.LAUNCHER].
 */
@Composable
fun FallingOverlay(state: CosmeticState) {
    if (!state.enabled) return
    val lifecycle by LocalLifecycleOwner.current.lifecycle.currentStateAsState()
    if (!lifecycle.isAtLeast(Lifecycle.State.RESUMED)) return

    when (state.effect) {
        FallingEffect.SNOW -> SnowfallOverlay(
            flakeCount = 130,
            sizeScale = state.scale,
            speedScale = state.speed,
            tiltEnabled = state.tilt,
            gravityMode = state.gravityMode,
        )

        FallingEffect.TROLL -> TrollRainOverlay(
            itemCount = 36,
            sizeScale = state.scale,
            speedScale = state.speed,
            tiltEnabled = state.tilt,
            gravityMode = state.gravityMode,
        )

        FallingEffect.LAUNCHER -> LauncherDriftOverlay(state)

        FallingEffect.OFF -> Unit
    }
}

/**
 * The original 星棱狐 mascot drift, retained as one of the selectable effects.
 *
 * ADAPTED: iKunSU draws its own 194 KB raster mascot. This fork ships the
 * artwork supplied by the project owner as density-bucketed launcher foreground
 * bitmaps, so the overlay reuses that same art rather than duplicating it as a
 * separate drawable. Unlike the snow and troll systems this one does not use the
 * gravity field: the sprites are much larger, so tilt reads as noise.
 */
@Composable
private fun LauncherDriftOverlay(state: CosmeticState) {
    val particle = painterResource(R.drawable.ic_launcher_foreground)
    val particles = remember {
        List(26) {
            Particle(
                Random.nextFloat(),
                Random.nextFloat(),
                0.035f + Random.nextFloat() * 0.055f,
                18f + Random.nextFloat() * 20f,
                Random.nextFloat() * 360f,
            )
        }
    }
    var elapsed by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(Unit) {
        var previous = 0L
        while (true) {
            withFrameNanos { now ->
                if (previous != 0L) {
                    elapsed += ((now - previous) / 1_000_000_000f).coerceAtMost(0.05f) * state.speed
                }
                previous = now
            }
        }
    }
    // A drawing-only overlay leaves all pointer input to the Manager beneath it.
    Canvas(Modifier.fillMaxSize()) {
        particles.forEach { p ->
            val side = p.size * density * state.scale
            val x = p.x * size.width + sin(elapsed + p.rotation) * 18f * density
            val y = ((p.y + elapsed * p.speed) % 1f) * (size.height + side * 2f) - side
            rotate(p.rotation + elapsed * 25f, Offset(x, y)) {
                translate(x - side / 2f, y - side / 2f) {
                    with(particle) {
                        draw(Size(side.coerceAtLeast(1f), side.coerceAtLeast(1f)), alpha = 0.7f)
                    }
                }
            }
        }
    }
}
