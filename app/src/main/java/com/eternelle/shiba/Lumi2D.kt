package com.eternelle.shiba

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

val Midnight = Color(0xFF17112A)
val Plum = Color(0xFF4A1D55)
val Lavender = Color(0xFFD7CAFF)
val Rose = Color(0xFFFFC2D1)
val Gold = Color(0xFFFFE2A0)
val Pearl = Color(0xFFFFF8F5)

/** Lumi drawn in 2D for the invitation and loading screens (the 3D Lumi lives in the universe). */
@Composable
fun Lumi2D(modifier: Modifier = Modifier, excited: Boolean = false, reduceMotion: Boolean = false) {
    val tr = rememberInfiniteTransition(label = "lumi")
    val t by tr.animateFloat(0f, 1000f, infiniteRepeatable(tween(1_000_000, easing = LinearEasing)), label = "t")
    Canvas(modifier.semantics { contentDescription = "Lumi, a tiny glowing pearl creature with butterfly wings, smiling" }) {
        val secs = t
        val amp = if (reduceMotion) 0.3f else 1f
        val s = size.minDimension
        val cx = size.width / 2f
        val cy = size.height / 2f + sin(secs * 2.4f) * s * 0.04f * amp
        val r = s * 0.24f
        // aura
        drawCircle(Brush.radialGradient(listOf(Gold.copy(alpha = 0.35f), Color.Transparent), Offset(cx, cy), r * 2.2f), r * 2.2f, Offset(cx, cy))
        // wings
        val flap = sin(secs * (if (excited) 20f else 9f)) * 12f * amp
        for (side in listOf(-1f, 1f)) {
            rotate(side * (18f + flap), Offset(cx + side * r * 0.6f, cy - r * 0.1f)) {
                drawOval(Brush.linearGradient(listOf(Lavender.copy(alpha = 0.85f), Rose.copy(alpha = 0.55f))),
                    topLeft = Offset(cx + side * r * 0.55f - (if (side < 0) r * 1.15f else 0f), cy - r * 1.05f), size = Size(r * 1.15f, r * 0.95f))
                drawOval(Lavender.copy(alpha = 0.6f),
                    topLeft = Offset(cx + side * r * 0.5f - (if (side < 0) r * 0.75f else 0f), cy - r * 0.2f), size = Size(r * 0.75f, r * 0.6f))
            }
        }
        // body
        drawCircle(Brush.radialGradient(listOf(Color.White, Color(0xFFF3E6F0), Color(0xFFE2D2EE)), Offset(cx - r * 0.3f, cy - r * 0.35f), r * 1.4f), r, Offset(cx, cy))
        // antenna
        drawLine(Color(0xFFF0C46A), Offset(cx, cy - r), Offset(cx + r * 0.12f, cy - r * 1.45f), strokeWidth = s * 0.012f)
        drawCircle(Gold, r * (0.13f + 0.03f * sin(secs * 4f)), Offset(cx + r * 0.12f, cy - r * 1.5f))
        // eyes (blink + happy squint)
        val blink = (secs % 3.7f) < 0.13f
        for (side in listOf(-1f, 1f)) {
            val ex = cx + side * r * 0.34f; val ey = cy - r * 0.08f
            if (excited || blink) {
                drawArc(Color(0xFF1E1026), 200f, 140f, false, Offset(ex - r * 0.17f, ey - r * 0.08f), Size(r * 0.34f, r * 0.3f), style = Stroke(width = s * 0.018f))
            } else {
                drawCircle(Color(0xFF1E1026), r * 0.17f, Offset(ex, ey))
                drawCircle(Color.White, r * 0.06f, Offset(ex + r * 0.06f, ey - r * 0.07f))
                drawCircle(Color.White, r * 0.03f, Offset(ex - r * 0.05f, ey + r * 0.05f))
            }
            drawCircle(Rose.copy(alpha = 0.8f), r * 0.13f, Offset(cx + side * r * 0.58f, cy + r * 0.2f))
        }
        // smile
        drawArc(Color(0xFF1E1026), 20f, 140f, false, Offset(cx - r * 0.17f, cy + r * 0.02f), Size(r * 0.34f, r * 0.26f), style = Stroke(width = s * 0.014f))
        // heart
        drawHeart(Offset(cx, cy + r * 0.62f), r * 0.2f * (1f + 0.12f * abs(sin(secs * 3.5f))), Color(0xFFFF86AE))
        // orbiting particles
        for (k in 0 until 6) {
            val a = k * TAU / 6 + secs * 1.3f * amp
            drawCircle(Gold, s * 0.012f, Offset(cx + cos(a) * r * 1.6f, cy + sin(a) * r * 0.5f))
        }
    }
}

fun DrawScope.drawHeart(c: Offset, size: Float, color: Color) {
    val p = Path()
    for (i in 0..60) {
        val (hx, hy) = heartPoint(i * TAU / 60)
        val x = c.x + hx * size; val y = c.y - hy * size
        if (i == 0) p.moveTo(x, y) else p.lineTo(x, y)
    }
    p.close()
    drawPath(p, color)
}

/** Deep plum night sky with twinkling stars — used behind the 2D screens and the 3D (non-AR) mode. */
@Composable
fun StarryBackdrop(modifier: Modifier = Modifier.fillMaxSize()) {
    val stars = remember {
        val rnd = Random(2026)
        List(90) { floatArrayOf(rnd.nextFloat(), rnd.nextFloat(), 0.6f + rnd.nextFloat() * 2.2f, rnd.nextFloat() * 6f) }
    }
    val tr = rememberInfiniteTransition(label = "sky")
    val t by tr.animateFloat(0f, 1000f, infiniteRepeatable(tween(1_000_000, easing = LinearEasing), RepeatMode.Restart), label = "t")
    Canvas(modifier) {
        drawRect(Brush.verticalGradient(listOf(Midnight, Color(0xFF2A1640), Plum, Color(0xFF6E3F7E))))
        drawCircle(Brush.radialGradient(listOf(Rose.copy(alpha = 0.18f), Color.Transparent), Offset(size.width * 0.5f, size.height * 0.45f), size.width * 0.8f),
            size.width * 0.8f, Offset(size.width * 0.5f, size.height * 0.45f))
        stars.forEach { s ->
            val a = 0.35f + 0.65f * abs(sin(t * 1.3f + s[3]))
            drawCircle((if (s[3] > 4.5f) Gold else Color.White).copy(alpha = a), s[2] * density, Offset(s[0] * size.width, s[1] * size.height))
        }
    }
}

/** Utility: transform helper kept for future scenes. */
fun DrawScope.scaled(f: Float, pivot: Offset, block: DrawScope.() -> Unit) =
    withTransform({ scale(f, f, pivot) }) { block() }
