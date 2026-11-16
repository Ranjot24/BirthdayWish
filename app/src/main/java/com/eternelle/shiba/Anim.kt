package com.eternelle.shiba

import androidx.compose.ui.graphics.Color
import com.google.android.filament.MaterialInstance
import io.github.sceneview.loaders.MaterialLoader
import io.github.sceneview.math.Position
import io.github.sceneview.node.Node
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin

/** Global motion preference (Accessibility → Reduce motion). Read by every animation. */
object Motion {
    @Volatile var reduced = false
    val amp: Float get() = if (reduced) 0.3f else 1f
}

/**
 * Drives a node from the render loop. [block] receives seconds since the node first rendered and
 * the current frame time (System.nanoTime() base), so story events stamped with nanoTime line up.
 */
fun Node.animate(block: Node.(t: Float, now: Long) -> Unit) {
    var start = -1L
    onFrame = { now ->
        if (start < 0L) start = now
        this@animate.block((now - start) / 1e9f, now)
    }
}

/** Makes a node respond to a single tap. */
fun tapper(onTap: (() -> Unit)?): Node.() -> Unit = {
    if (onTap != null) onSingleTapConfirmed = { onTap(); true }
}

const val TAU = (2 * PI).toFloat()
fun clamp01(x: Float) = x.coerceIn(0f, 1f)
fun ease(x: Float): Float { val c = clamp01(x); return c * c * (3f - 2f * c) }
fun easeOutBack(x: Float): Float {
    val c = clamp01(x); val s = 1.70158f; val y = c - 1f
    return 1f + (s + 1f) * y * y * y + s * y * y
}
fun lerp(a: Float, b: Float, t: Float) = a + (b - a) * t
fun safe(s: Float) = if (s < 0.001f) 0.001f else s
fun secondsSince(stamp: Long, now: Long) = if (stamp == 0L) -1f else (now - stamp) / 1e9f

/** Classic heart curve, normalised to roughly [-1, 1]. */
fun heartPoint(theta: Float): Pair<Float, Float> {
    val x = 16f * sin(theta).pow(3)
    val y = 13f * cos(theta) - 5f * cos(2 * theta) - 2f * cos(3 * theta) - cos(4 * theta)
    return Pair(x / 17f, (y + 2.5f) / 17f)
}

fun bezier(a: Position, c: Position, b: Position, u: Float): Position {
    val v = 1f - u
    return Position(
        v * v * a.x + 2 * v * u * c.x + u * u * b.x,
        v * v * a.y + 2 * v * u * c.y + u * u * b.y,
        v * v * a.z + 2 * v * u * c.z + u * u * b.z,
    )
}

/** All materials for the universe: deep plum, lavender, rose pink, champagne gold, pearl white. */
class Palette(ml: MaterialLoader) {
    val gold: MaterialInstance = ml.createColorInstance(Color(0xFFF0C46A), metallic = 0.95f, roughness = 0.22f)
    val goldGlow: MaterialInstance = ml.createUnlitColorInstance(Color(0xFFFFE2A0))
    val champagne: MaterialInstance = ml.createColorInstance(Color(0xFFF6E3C4), metallic = 0.6f, roughness = 0.3f)
    val pearl: MaterialInstance = ml.createColorInstance(Color(0xFFFFF7F3), metallic = 0.15f, roughness = 0.12f, reflectance = 0.9f)
    val plum: MaterialInstance = ml.createColorInstance(Color(0xFF4A1D55), roughness = 0.45f)
    val plumDeep: MaterialInstance = ml.createColorInstance(Color(0xFF2C1236), roughness = 0.6f)
    val lavender: MaterialInstance = ml.createColorInstance(Color(0xFFC7B4FF), roughness = 0.35f)
    val lavenderGlass: MaterialInstance = ml.createColorInstance(Color(0x99CDB9FF), roughness = 0.05f, reflectance = 0.9f)
    val lavenderGlow: MaterialInstance = ml.createUnlitColorInstance(Color(0xFFD9C8FF))
    val portalVeil: MaterialInstance = ml.createUnlitColorInstance(Color(0x55D8C4FF))
    val rose: MaterialInstance = ml.createColorInstance(Color(0xFFFF9FBD), roughness = 0.35f)
    val roseGlass: MaterialInstance = ml.createColorInstance(Color(0xA6FF8FB4), roughness = 0.03f, reflectance = 0.95f)
    val roseGlow: MaterialInstance = ml.createUnlitColorInstance(Color(0xFFFF86AE))
    val cream: MaterialInstance = ml.createColorInstance(Color(0xFFFFF0DE), roughness = 0.55f)
    val ink: MaterialInstance = ml.createColorInstance(Color(0xFF1E1026), roughness = 0.1f, reflectance = 0.9f)
    val white: MaterialInstance = ml.createUnlitColorInstance(Color(0xFFFFFFFF))
    val leaf: MaterialInstance = ml.createColorInstance(Color(0xFF8BD3AE), roughness = 0.5f)
    val leafGlow: MaterialInstance = ml.createUnlitColorInstance(Color(0xFF9DF0C4))
    val sky: MaterialInstance = ml.createColorInstance(Color(0xFFA6D8FF), roughness = 0.08f, reflectance = 0.8f)
    val steel: MaterialInstance = ml.createColorInstance(Color(0xFF9C95B8), metallic = 0.8f, roughness = 0.35f)
    val taxi: MaterialInstance = ml.createColorInstance(Color(0xFFFFD23F), roughness = 0.4f)
    val flame: MaterialInstance = ml.createUnlitColorInstance(Color(0xFFFFA142))
    val flameCore: MaterialInstance = ml.createUnlitColorInstance(Color(0xFFFFF3B8))
    val smoke: MaterialInstance = ml.createUnlitColorInstance(Color(0x66E9DEFF))
}
