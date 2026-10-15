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
import androidx.compose.ui.graphics.Color
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

@Composable
fun FallingOverlay(state: CosmeticState) {
    val lifecycle by LocalLifecycleOwner.current.lifecycle.currentStateAsState()
    if (!state.enabled || !lifecycle.isAtLeast(Lifecycle.State.RESUMED)) return
    // ADAPTED: iKunSU draws its own 194 KB raster mascot. This fork ships the
    // artwork supplied by the project owner as density-bucketed launcher
    // foreground bitmaps, so the overlay reuses that same art rather than
    // duplicating it as a separate drawable.
    val particle = painterResource(R.drawable.ic_launcher_foreground)
    val particles = remember { List(26) { Particle(Random.nextFloat(), Random.nextFloat(), 0.035f + Random.nextFloat() * 0.055f, 18f + Random.nextFloat() * 20f, Random.nextFloat() * 360f) } }
    var elapsed by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(Unit) {
        var previous = 0L
        while (true) {
            withFrameNanos { now ->
                if (previous != 0L) elapsed += ((now - previous) / 1_000_000_000f).coerceAtMost(0.05f) * state.speed
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
            if (state.snow) {
                drawCircle(Color(0xFFE0ECFF).copy(alpha = 0.75f), side / 8f, Offset(x, y))
            } else {
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
}
