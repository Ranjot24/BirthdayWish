package com.eternelle.shiba

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import com.google.android.filament.MaterialInstance
import io.github.sceneview.SceneScope
import io.github.sceneview.math.Position
import io.github.sceneview.math.Rotation
import io.github.sceneview.math.Scale
import io.github.sceneview.math.Size
import io.github.sceneview.node.Node
import kotlinx.coroutines.delay
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

/*
 * The whole 3D universe, built from SceneView geometry nodes and animated every frame.
 * Units are metres; y = 0 is the real floor/table in AR. +Z faces the viewer.
 */

@Composable
fun SceneScope.Universe(story: Story) {
    val p = remember(materialLoader) { Palette(materialLoader) }
    val chapter = story.chapter
    key(story.runId) {
        AmbientSparkles(p, if (story.reduceMotion) 8 else 18)
        when (chapter) {
            Chapter.PORTAL, Chapter.LUMI -> PortalScene(p, story)
            Chapter.CITIES -> CitiesScene(p, story)
            Chapter.BOOK -> BookScene(p, story)
            Chapter.CAKE -> CakeScene(p, story)
            Chapter.HUG -> HugScene(p, story)
            Chapter.FINAL -> FinalScene(p, story)
            else -> {}
        }
        if (chapter.ordinal >= Chapter.LUMI.ordinal) {
            val target = when (chapter) {
                Chapter.LUMI -> Position(0f, 0.2f, 0.12f)
                Chapter.CITIES -> Position(0f, 0.1f, 0.17f)
                Chapter.BOOK -> Position(-0.2f, 0.22f, 0.06f)
                Chapter.CAKE -> Position(0.21f, 0.24f, 0.04f)
                Chapter.HUG -> Position(-0.2f, 0.27f, 0.05f)
                else -> Position(0f, 0.13f, 0.17f)
            }
            SideEffect { story.lumi.target = target }
            Lumi(p, story)
        }
    }
}

// ───────────────────────────── building blocks ─────────────────────────────

@Composable
fun SceneScope.Ball(
    r: Float,
    mat: MaterialInstance,
    position: Position = Position(0f, 0f, 0f),
    scale: Scale = Scale(1f),
    segments: Int = 12,
    onTap: (() -> Unit)? = null,
    motion: (Node.(t: Float, now: Long) -> Unit)? = null,
) {
    SphereNode(
        radius = r, stacks = segments, slices = segments + 2, materialInstance = mat,
        position = position, scale = scale,
        apply = {
            tapper(onTap)(this)
            if (motion != null) animate(motion)
        }
    )
}

/** A 3D heart: two spheres and a downward cone. */
@Composable
fun SceneScope.Heart(
    mat: MaterialInstance,
    size: Float,
    position: Position = Position(0f, 0f, 0f),
    onTap: (() -> Unit)? = null,
    motion: (Node.(t: Float, now: Long) -> Unit)? = null,
) {
    Node(position = position, apply = { if (motion != null) animate(motion) }) {
        val r = size * 0.5f
        SphereNode(radius = r, stacks = 14, slices = 16, materialInstance = mat,
            position = Position(-size * 0.36f, size * 0.12f, 0f), apply = tapper(onTap))
        SphereNode(radius = r, stacks = 14, slices = 16, materialInstance = mat,
            position = Position(size * 0.36f, size * 0.12f, 0f), apply = tapper(onTap))
        ConeNode(radius = size * 0.8f, height = size * 1.05f, sideCount = 20, materialInstance = mat,
            position = Position(0f, -size * 0.42f, 0f), rotation = Rotation(0f, 0f, 180f), apply = tapper(onTap))
    }
}

/** A five-pointed star standing upright, facing the viewer. */
@Composable
fun SceneScope.Star(core: MaterialInstance, points: MaterialInstance, size: Float) {
    Ball(size * 0.35f, core)
    for (k in 0 until 5) {
        val a = k * 72f
        val rad = Math.toRadians(a.toDouble()).toFloat()
        ConeNode(radius = size * 0.22f, height = size * 0.75f, sideCount = 8, materialInstance = points,
            position = Position(-sin(rad) * size * 0.55f, cos(rad) * size * 0.55f, 0f),
            rotation = Rotation(0f, 0f, a))
    }
}

@Composable
fun SceneScope.Flower(p: Palette, petal: MaterialInstance, position: Position, delay: Float, floating: Boolean) {
    Node(position = position, apply = {
        animate { t, _ ->
            val bloom = easeOutBack((t - delay) / 0.9f)
            val bob = if (floating) sin(t * 1.3f + delay * 3f) * 0.012f * Motion.amp else 0f
            this.position = Position(position.x, position.y + bob, position.z)
            rotation = Rotation(0f, t * 18f * Motion.amp, 0f)
            scale = Scale(safe(if (t < delay) 0f else bloom))
        }
    }) {
        if (!floating) CylinderNode(radius = 0.0018f, height = 0.05f, sideCount = 6,
            materialInstance = p.leaf, position = Position(0f, -0.025f, 0f))
        for (k in 0 until 5) {
            val a = k * TAU / 5
            Ball(0.012f, petal, Position(cos(a) * 0.012f, 0.002f, sin(a) * 0.012f), Scale(1f, 0.28f, 0.62f))
        }
        Ball(0.0065f, p.goldGlow, Position(0f, 0.004f, 0f))
    }
}

@Composable
fun SceneScope.Butterfly(p: Palette, wing: MaterialInstance, radius: Float, height: Float, speed: Float, phase: Float, delay: Float = 0f) {
    Node(apply = {
        animate { t, _ ->
            val a = phase + t * speed * Motion.amp
            val y = height + sin(t * 1.7f + phase) * 0.04f * Motion.amp
            position = Position(cos(a) * radius, y, sin(a) * radius)
            rotation = Rotation(0f, -Math.toDegrees(a.toDouble()).toFloat(), 0f)
            scale = Scale(safe(easeOutBack((t - delay) / 0.8f)))
        }
    }) {
        CapsuleNode(radius = 0.0025f, height = 0.012f, materialInstance = p.plumDeep, rotation = Rotation(90f, 0f, 0f))
        for (side in listOf(-1f, 1f)) {
            Node(apply = {
                animate { t, _ ->
                    val flap = sin(t * 16f + phase) * 55f * Motion.amp
                    rotation = Rotation(0f, 0f, side * (20f + flap))
                }
            }) {
                Ball(0.014f, wing, Position(side * 0.013f, 0f, 0.004f), Scale(1f, 0.1f, 0.8f), 8)
                Ball(0.009f, wing, Position(side * 0.01f, 0f, -0.01f), Scale(1f, 0.1f, 0.8f), 8)
                Ball(0.0025f, p.goldGlow, Position(side * 0.016f, 0.001f, 0.004f), segments = 6)
            }
        }
    }
}

@Composable
fun SceneScope.AmbientSparkles(p: Palette, count: Int) {
    val seeds = remember(count) {
        val rnd = Random(1204)
        List(count) { floatArrayOf(rnd.nextFloat(), rnd.nextFloat(), rnd.nextFloat(), rnd.nextFloat()) }
    }
    seeds.forEachIndexed { i, s ->
        Ball(0.0035f, if (i % 3 == 0) p.lavenderGlow else p.goldGlow, segments = 6, motion = { t, _ ->
            val r = 0.25f + s[0] * 0.25f
            val a = s[1] * TAU + t * (0.08f + s[2] * 0.12f) * Motion.amp
            val y = 0.05f + s[3] * 0.5f + sin(t * 0.6f + i) * 0.03f
            position = Position(cos(a) * r, y, sin(a) * r)
            scale = Scale(0.6f + 0.6f * abs(sin(t * (1.2f + s[2]) + i)))
        })
    }
}

// ───────────────────────────── Lumi ─────────────────────────────

@Composable
fun SceneScope.Lumi(p: Palette, story: Story) {
    val b = story.lumi
    val tap = { story.tapLumi() }
    Node(apply = {
        b.appearAt = System.nanoTime()
        b.x = 0f; b.y = 0.22f; b.z = -0.02f; b.lastNow = 0L
        animate { t, now ->
            val dt = if (b.lastNow == 0L) 0.016f else ((now - b.lastNow) / 1e9f).coerceIn(0f, 0.1f)
            b.lastNow = now
            val k = 1f - exp(-dt * 2.2f)
            b.x += (b.target.x - b.x) * k
            b.y += (b.target.y - b.y) * k
            b.z += (b.target.z - b.z) * k
            val amp = Motion.amp
            val m = b.secondsInMood(now)
            var bob = sin(t * 2.2f) * 0.012f * amp
            var rotY = sin(t * 0.7f) * 14f * amp
            var rotZ = sin(t * 1.1f) * 4f * amp
            var sy = 1f
            var sxz = 1f
            when (b.mood) {
                Mood.Happy -> if (m < 1.2f) bob += abs(sin(m * 9.4f)) * 0.03f * (1.2f - m) * amp
                Mood.Laugh -> if (m < 1.3f) {
                    rotZ = sin(m * 30f) * 12f * (1.3f - m) * amp
                    sy = 1f + 0.09f * sin(m * 24f) * (1.3f - m)
                    sxz = 1f - 0.05f * sin(m * 24f) * (1.3f - m)
                }
                Mood.Spin -> if (m < 1.5f) {
                    rotY += 360f * ease(m / 1.5f)
                    bob += sin(m / 1.5f * 3.1416f) * 0.06f
                }
                Mood.Wave -> rotZ = sin(m * 5f) * 10f * amp
                Mood.Idle -> {}
            }
            if (m > 2.6f && b.mood != Mood.Idle && b.mood != Mood.Wave) b.mood = Mood.Idle
            val appear = safe(easeOutBack(t / 1.2f))
            position = Position(b.x, b.y + bob, b.z)
            rotation = Rotation(0f, rotY, rotZ)
            scale = Scale(sxz * appear, sy * appear, sxz * appear)
        }
    }) {
        // pearl body
        SphereNode(radius = 0.045f, stacks = 24, slices = 28, materialInstance = p.pearl, apply = tapper(tap))
        // little feet / tail glow
        Ball(0.012f, p.lavenderGlow, Position(0f, -0.045f, 0f), Scale(1f, 0.5f, 1f), 8)
        // cheeks
        Ball(0.009f, p.rose, Position(-0.026f, -0.006f, 0.035f), Scale(1f, 0.6f, 0.35f), 8)
        Ball(0.009f, p.rose, Position(0.026f, -0.006f, 0.035f), Scale(1f, 0.6f, 0.35f), 8)
        // sparkling eyes with blinking + happy squint
        for (side in listOf(-1f, 1f)) {
            Node(position = Position(side * 0.015f, 0.009f, 0.039f), apply = {
                animate { _, now ->
                    val s = now / 1e9f
                    val blink = if ((s + side * 0.01f) % 3.7f < 0.13f) 0.12f else 1f
                    val m = b.secondsInMood(now)
                    val happy = (b.mood == Mood.Happy || b.mood == Mood.Laugh || b.mood == Mood.Spin) && m < 2.2f
                    scale = Scale(1f, if (happy) 0.32f else blink, 1f)
                }
            }) {
                Ball(0.0085f, p.ink, segments = 12)
                Ball(0.0028f, p.white, Position(0.0025f, 0.0035f, 0.0072f), segments = 6)
                Ball(0.0014f, p.white, Position(-0.002f, -0.002f, 0.0078f), segments = 6)
            }
        }
        // smile
        for (k in -2..2) {
            val x = k * 0.0042f
            Ball(0.0017f, p.ink, Position(x, -0.009f - 0.0035f * (1f - (k * k) / 4f), 0.0425f), segments = 6)
        }
        // antenna with a glowing star bead
        CylinderNode(radius = 0.0013f, height = 0.026f, sideCount = 6, materialInstance = p.gold,
            position = Position(0f, 0.055f, 0f), rotation = Rotation(0f, 0f, 10f))
        Ball(0.0065f, p.goldGlow, Position(-0.0025f, 0.07f, 0f), segments = 10, motion = { t, _ ->
            scale = Scale(1f + 0.25f * sin(t * 4f))
        })
        // glowing heart on her chest
        Heart(p.roseGlow, 0.012f, Position(0f, -0.024f, 0.04f), onTap = tap, motion = { t, _ ->
            val beat = 1f + 0.18f * max(0f, sin(t * 7f))
            scale = Scale(beat)
        })
        // butterfly-like wings
        for (side in listOf(-1f, 1f)) {
            Node(position = Position(side * 0.03f, 0.012f, -0.022f), apply = {
                animate { t, now ->
                    val excited = b.mood != Mood.Idle && b.secondsInMood(now) < 2f
                    val speed = if (excited) 22f else 9f
                    rotation = Rotation(0f, side * (-25f + sin(t * speed) * 28f * Motion.amp), side * 12f)
                }
            }) {
                SphereNode(radius = 0.032f, stacks = 12, slices = 14, materialInstance = p.lavenderGlass,
                    position = Position(side * 0.03f, 0.014f, 0f), scale = Scale(1f, 0.72f, 0.1f), apply = tapper(tap))
                Ball(0.02f, p.lavenderGlass, Position(side * 0.024f, -0.018f, 0f), Scale(1f, 0.62f, 0.1f))
                Ball(0.004f, p.goldGlow, Position(side * 0.045f, 0.025f, 0.004f), segments = 6)
            }
        }
        // tiny floating golden particles orbiting her
        for (i in 0 until 6) {
            Ball(0.0022f, p.goldGlow, segments = 6, motion = { t, _ ->
                val a = i * TAU / 6 + t * 1.4f
                position = Position(cos(a) * 0.07f, sin(t * 2f + i) * 0.03f, sin(a) * 0.07f)
            })
        }
    }
}

// ───────────────────────────── 2. Portal ─────────────────────────────

@Composable
fun SceneScope.PortalScene(p: Palette, story: Story) {
    LaunchedEffect(Unit) {
        story.sfx(Sfx.POP)
        delay(2100); story.sfx(Sfx.PORTAL)
        delay(2400); story.portalOpened = true
    }
    // the tiny golden star that grows into the portal
    Node(apply = {
        animate { t, _ ->
            val pop = easeOutBack(t / 0.7f)
            val rise = ease((t - 0.9f) / 1.3f)
            val shrink = 1f - ease((t - 2.6f) / 0.7f)
            position = Position(0f, 0.02f + 0.2f * rise, 0f)
            rotation = Rotation(0f, t * 140f * Motion.amp, 0f)
            scale = Scale(safe(pop * (1f + 0.8f * rise) * shrink))
        }
    }) { Star(p.goldGlow, p.gold, 0.035f) }

    // the portal ring
    Node(position = Position(0f, 0.22f, -0.02f), apply = {
        animate { t, _ ->
            val open = easeOutBack((t - 2.2f) / 1.3f)
            scale = Scale(safe(open * (1f + 0.025f * sin(t * 2f))))
            rotation = Rotation(0f, 0f, t * 6f * Motion.amp)
        }
    }) {
        TorusNode(majorRadius = 0.17f, minorRadius = 0.008f, majorSegments = 48, minorSegments = 10,
            materialInstance = p.gold, rotation = Rotation(90f, 0f, 0f))
        TorusNode(majorRadius = 0.186f, minorRadius = 0.0025f, majorSegments = 48, minorSegments = 6,
            materialInstance = p.goldGlow, rotation = Rotation(90f, 0f, 0f))
        TorusNode(majorRadius = 0.155f, minorRadius = 0.0018f, majorSegments = 48, minorSegments = 6,
            materialInstance = p.lavenderGlow, rotation = Rotation(90f, 0f, 0f))
        Ball(0.165f, p.portalVeil, scale = Scale(1f, 1f, 0.03f), segments = 24)
        // jewels around the ring
        for (k in 0 until 12) {
            val a = k * TAU / 12
            Ball(0.007f, if (k % 2 == 0) p.roseGlow else p.goldGlow,
                Position(cos(a) * 0.17f, sin(a) * 0.17f, 0.006f), segments = 8)
        }
        // swirling stardust inside
        for (i in 0 until 22) {
            Ball(0.0032f, if (i % 2 == 0) p.goldGlow else p.white, segments = 6, motion = { t, _ ->
                val a = i * 0.9f + t * (0.9f + (i % 5) * 0.12f) * Motion.amp
                val r = 0.03f + ((i * 37) % 100) / 100f * 0.12f * (0.85f + 0.15f * sin(t + i))
                position = Position(cos(a) * r, sin(a) * r, 0.01f)
            })
        }
        // golden spiral particles around the outside
        for (i in 0 until 16) {
            Ball(0.0028f, p.goldGlow, segments = 6, motion = { t, _ ->
                val a = i * TAU / 16 - t * 0.6f * Motion.amp
                val r = 0.2f + 0.015f * sin(t * 3f + i)
                position = Position(cos(a) * r, sin(a) * r, 0.02f * sin(t * 2f + i))
            })
        }
    }
    // flowers blooming around the base, and a few floating ones
    val ring = listOf(200f, 235f, 270f, 305f, 340f, 160f, 125f, 90f, 55f, 20f)
    ring.forEachIndexed { i, deg ->
        val a = Math.toRadians(deg.toDouble()).toFloat()
        val r = 0.2f + (i % 2) * 0.03f
        Flower(p, if (i % 3 == 0) p.lavender else p.rose, Position(cos(a) * r, 0.05f, sin(a) * r - 0.02f), 3.3f + i * 0.1f, false)
    }
    for (i in 0 until 4) {
        val a = i * TAU / 4 + 0.6f
        Flower(p, if (i % 2 == 0) p.rose else p.lavender, Position(cos(a) * 0.27f, 0.28f + 0.05f * (i % 2), sin(a) * 0.12f), 3.8f + i * 0.2f, true)
    }
    for (i in 0 until 4) {
        Butterfly(p, if (i % 2 == 0) p.roseGlass else p.lavenderGlass,
            0.24f + i * 0.02f, 0.18f + i * 0.05f, 0.5f + i * 0.1f, i * 1.6f, 3.6f + i * 0.3f)
    }
}

// ───────────────────────────── 4. Two cities, one home ─────────────────────────────

@Composable
fun SceneScope.FloatingIsland(p: Palette, x: Float, onTap: () -> Unit, label: String, content: @Composable SceneScope.() -> Unit) {
    Node(position = Position(x, 0.07f, 0f), apply = {
        animate { t, _ ->
            val rise = easeOutBack(t / 1.1f)
            position = Position(x, 0.07f + sin(t * 1.1f + x * 10f) * 0.01f * Motion.amp, 0f)
            scale = Scale(safe(rise))
        }
    }) {
        CylinderNode(radius = 0.1f, height = 0.02f, sideCount = 28, materialInstance = p.plum, apply = tapper(onTap))
        CylinderNode(radius = 0.098f, height = 0.006f, sideCount = 28, materialInstance = p.champagne,
            position = Position(0f, 0.012f, 0f), apply = tapper(onTap))
        ConeNode(radius = 0.1f, height = 0.075f, sideCount = 20, materialInstance = p.plumDeep,
            position = Position(0f, -0.047f, 0f), rotation = Rotation(0f, 0f, 180f), apply = tapper(onTap))
        Ball(0.006f, p.goldGlow, Position(0f, -0.09f, 0f), segments = 8, motion = { t, _ ->
            scale = Scale(1f + 0.4f * sin(t * 3f))
        })
        content(this)
        // small golden beacon above each city (3D text labels removed for stability)
        Ball(0.006f, p.goldGlow, Position(0f, 0.15f, 0f), segments = 8, onTap = onTap, motion = { t, _ ->
            scale = Scale(1f + 0.3f * sin(t * 2.5f))
        })
    }
}

@Composable
fun SceneScope.CitiesScene(p: Palette, story: Story) {
    LaunchedEffect(Unit) {
        story.say(Lines.cities)
        delay(1300); story.sfx(Sfx.WHOOSH)
        delay(3400); story.sfx(Sfx.SPARKLE)
        delay(1700); story.heartFormed = true; story.sfx(Sfx.CHIME)
    }
    val jam = {
        story.sfx(Sfx.BELL_LEFT); story.say(Lines.jamshedpur); story.lumi.feel(Mood.Happy)
    }
    val kol = {
        story.sfx(Sfx.BELL_RIGHT); story.say(Lines.kolkata); story.lumi.feel(Mood.Happy)
    }
    // Jamshedpur: steel city, Jubilee Park, Dimna hills
    FloatingIsland(p, -0.22f, jam, "Jamshedpur") {
        for ((cx, cz) in listOf(-0.05f to -0.03f, -0.028f to -0.045f)) {
            CylinderNode(radius = 0.007f, height = 0.1f, sideCount = 10, materialInstance = p.steel,
                position = Position(cx, 0.065f, cz), apply = tapper(jam))
            TorusNode(majorRadius = 0.0075f, minorRadius = 0.0018f, majorSegments = 16, minorSegments = 6,
                materialInstance = p.gold, position = Position(cx, 0.1f, cz))
            for (k in 0 until 3) {
                Ball(0.008f, p.smoke, segments = 8, motion = { t, _ ->
                    val c = ((t * 0.35f + k / 3f) % 1f)
                    position = Position(cx + c * 0.02f, 0.12f + c * 0.06f, cz)
                    scale = Scale(safe((0.5f + c) * (1f - c)))
                })
            }
        }
        CylinderNode(radius = 0.018f, height = 0.05f, sideCount = 14, materialInstance = p.steel,
            position = Position(-0.065f, 0.04f, 0.025f), apply = tapper(jam))
        ConeNode(radius = 0.018f, height = 0.02f, sideCount = 14, materialInstance = p.gold,
            position = Position(-0.065f, 0.075f, 0.025f))
        ConeNode(radius = 0.045f, height = 0.055f, sideCount = 16, materialInstance = p.lavender,
            position = Position(0.045f, 0.043f, -0.045f), apply = tapper(jam))
        Ball(0.03f, p.sky, Position(0.03f, 0.016f, 0.035f), Scale(1f, 0.08f, 0.7f))
        for ((tx, tz) in listOf(0.0f to 0.0f, 0.065f to 0.02f, 0.01f to 0.06f, 0.06f to 0.055f)) {
            ConeNode(radius = 0.011f, height = 0.032f, sideCount = 10, materialInstance = p.leaf,
                position = Position(tx, 0.031f, tz))
        }
    }
    // Kolkata: Howrah Bridge over the Hooghly, Victoria Memorial, a yellow taxi
    FloatingIsland(p, 0.22f, kol, "Kolkata") {
        Ball(0.06f, p.sky, Position(0f, 0.016f, 0.02f), Scale(1f, 0.06f, 0.3f))
        for (bx in listOf(-0.045f, 0.045f)) {
            CubeNode(size = Size(0.009f, 0.075f, 0.022f), materialInstance = p.steel,
                position = Position(bx, 0.052f, 0.02f), apply = tapper(kol))
        }
        CubeNode(size = Size(0.11f, 0.004f, 0.02f), materialInstance = p.steel, position = Position(0f, 0.032f, 0.02f))
        CubeNode(size = Size(0.1f, 0.004f, 0.006f), materialInstance = p.steel, position = Position(0f, 0.088f, 0.02f))
        for (k in 0..8) {
            val bx = -0.04f + k * 0.01f
            val y = 0.088f - 0.035f * (1f - (bx / 0.045f) * (bx / 0.045f))
            Ball(0.0022f, p.goldGlow, Position(bx, y, 0.02f), segments = 6)
        }
        Node(apply = {
            animate { t, _ ->
                val u = sin(t * 0.8f)
                position = Position(u * 0.045f, 0.038f, 0.02f)
                rotation = Rotation(0f, if (cos(t * 0.8f) > 0) 0f else 180f, 0f)
            }
        }) {
            CubeNode(size = Size(0.014f, 0.006f, 0.007f), materialInstance = p.taxi)
            CubeNode(size = Size(0.008f, 0.004f, 0.006f), materialInstance = p.taxi, position = Position(0f, 0.004f, 0f))
        }
        CubeNode(size = Size(0.05f, 0.018f, 0.032f), materialInstance = p.pearl,
            position = Position(0f, 0.024f, -0.05f), apply = tapper(kol))
        Ball(0.016f, p.pearl, Position(0f, 0.04f, -0.05f), segments = 14, onTap = kol)
        Ball(0.004f, p.gold, Position(0f, 0.06f, -0.05f), segments = 8)
        for (tx in listOf(-0.03f, 0.03f)) Ball(0.007f, p.pearl, Position(tx, 0.037f, -0.05f), segments = 8)
    }
    // the luminous golden ribbon that becomes a heart-shaped constellation
    val n = 30
    val a = Position(-0.2f, 0.15f, 0f)
    val c = Position(0f, 0.5f, 0.02f)
    val e = Position(0.2f, 0.15f, 0f)
    for (i in 0 until n) {
        val u = i / (n - 1f)
        val bp = bezier(a, c, e, u)
        val (hx, hy) = heartPoint(i * TAU / n)
        Ball(if (i % 3 == 0) 0.0055f else 0.004f, if (i % 3 == 0) p.goldGlow else p.gold, segments = 8, motion = { t, _ ->
            val appear = easeOutBack((t - 1.3f - i * 0.07f) / 0.4f)
            val m = ease((t - 4.7f) / 1.6f)
            val x = lerp(bp.x, hx * 0.11f, m)
            val y = lerp(bp.y, 0.36f + hy * 0.11f, m)
            val z = lerp(bp.z, 0.03f, m)
            position = Position(x, y + sin(t * 2f + i) * 0.004f * Motion.amp, z)
            val twinkle = if (m > 0.99f) 1f + 0.35f * sin(t * 5f + i * 1.3f) else 1f
            scale = Scale(safe(appear * twinkle))
        })
    }
    // the heart glows once the constellation forms
    Heart(p.roseGlass, 0.06f, Position(0f, 0.355f, 0.03f), onTap = {
        story.sfx(Sfx.CHIME); story.lumi.feel(Mood.Spin)
    }, motion = { t, _ ->
        val show = ease((t - 6.0f) / 0.8f)
        scale = Scale(safe(show * (1f + 0.08f * sin(t * 3.5f))))
        rotation = Rotation(0f, sin(t * 0.8f) * 20f, 0f)
    })
}

// ───────────────────────────── 5. The Blessing Book ─────────────────────────────

@Composable
fun SceneScope.BookScene(p: Palette, story: Story) {
    LaunchedEffect(Unit) { story.say(Lines.book) }
    Node(position = Position(0f, 0.1f, 0.02f), apply = {
        animate { t, _ ->
            position = Position(0f, 0.1f + sin(t * 1.2f) * 0.008f * Motion.amp, 0.02f)
            scale = Scale(safe(easeOutBack(t / 1f)))
        }
    }) {
        // covers and spine
        CubeNode(size = Size(0.135f, 0.008f, 0.18f), materialInstance = p.plum, position = Position(-0.069f, 0f, 0f))
        CubeNode(size = Size(0.135f, 0.008f, 0.18f), materialInstance = p.plum, position = Position(0.069f, 0f, 0f))
        CylinderNode(radius = 0.007f, height = 0.182f, sideCount = 12, materialInstance = p.gold, rotation = Rotation(90f, 0f, 0f))
        for (corner in listOf(-1f, 1f)) for (zc in listOf(-1f, 1f)) {
            Ball(0.006f, p.gold, Position(corner * 0.13f, 0.004f, zc * 0.085f), segments = 8)
        }
        // seven pages, each hinged at the spine
        for (i in 0 until 7) {
            Node(position = Position(0f, 0.0055f + (6 - i) * 0.0016f, 0f), apply = {
                animate { _, now ->
                    val s = secondsSince(story.book.turnedAt[i], now)
                    val turn = if (s < 0f) 0f else ease(s / 0.9f)
                    rotation = Rotation(0f, 0f, 178f * turn)
                }
            }) {
                CubeNode(size = Size(0.128f, 0.0012f, 0.17f), materialInstance = p.cream,
                    position = Position(0.066f, 0f, 0f), apply = tapper {
                        if (i == story.pagesOpened) story.openNextPage()
                    })
                Heart(p.roseGlow, 0.012f, Position(0.066f, 0.0012f, 0f))
            }
        }
        // a soft glow rising from the book
        for (i in 0 until 10) {
            Ball(0.0025f, p.goldGlow, segments = 6, motion = { t, _ ->
                val c = (t * 0.3f + i / 10f) % 1f
                val a = i * 2.3f
                position = Position(cos(a) * 0.06f * (1f - c), 0.01f + c * 0.18f, sin(a) * 0.05f)
                scale = Scale(safe(1f - c))
            })
        }
    }
    val opened = story.pagesOpened
    if (opened > 0) {
        key(opened) { BlessingVisual(p, story, opened - 1) }
    }
}

@Composable
fun SceneScope.BlessingVisual(p: Palette, story: Story, index: Int) {
    Node(position = Position(0f, 0.31f, 0.02f), apply = {
        animate { t, _ ->
            scale = Scale(safe(easeOutBack(t / 0.9f)))
            position = Position(0f, 0.31f + sin(t * 1.4f) * 0.01f * Motion.amp, 0.02f)
        }
    }) {
        when (index) {
            0 -> { // Happiness: a smiling sun
                Ball(0.032f, p.goldGlow, segments = 18)
                for (side in listOf(-1f, 1f)) Ball(0.004f, p.ink, Position(side * 0.01f, 0.006f, 0.03f), segments = 8)
                Ball(0.005f, p.rose, Position(-0.017f, -0.004f, 0.026f), Scale(1f, 0.6f, 0.4f), 8)
                Ball(0.005f, p.rose, Position(0.017f, -0.004f, 0.026f), Scale(1f, 0.6f, 0.4f), 8)
                Node(apply = { animate { t, _ -> rotation = Rotation(0f, 0f, t * 30f * Motion.amp) } }) {
                    for (k in 0 until 10) {
                        val a = k * 36f
                        val rad = Math.toRadians(a.toDouble()).toFloat()
                        ConeNode(radius = 0.006f, height = 0.022f, sideCount = 8, materialInstance = p.gold,
                            position = Position(-sin(rad) * 0.047f, cos(rad) * 0.047f, 0f), rotation = Rotation(0f, 0f, a))
                    }
                }
            }
            1 -> { // Laughter: bouncing giggle bubbles you can pop
                val popped = remember { LongArray(8) }
                for (i in 0 until 8) {
                    val bx = (i - 3.5f) * 0.022f
                    Ball(0.011f + (i % 3) * 0.003f, if (i % 2 == 0) p.roseGlass else p.lavenderGlass, segments = 12,
                        onTap = { popped[i] = System.nanoTime(); story.sfx(Sfx.POP); story.lumi.feel(Mood.Laugh) },
                        motion = { t, now ->
                            val bounce = abs(sin(t * 3f + i * 0.8f))
                            val sq = 1f - 0.25f * (1f - bounce)
                            position = Position(bx, -0.03f + bounce * 0.06f * Motion.amp, 0.01f * (i % 2))
                            val ps = secondsSince(popped[i], now)
                            val respawn = if (ps < 0f) 1f else if (ps < 0.15f) 1f + ps * 6f else ease((ps - 0.6f) / 0.5f)
                            scale = Scale(safe(respawn / sq), safe(respawn * sq), safe(respawn / sq))
                        })
                }
            }
            2 -> { // Health: a beating green heart with orbiting leaves
                Heart(p.leafGlow, 0.05f, motion = { t, _ ->
                    val beat = (t % 1.1f)
                    val s = 1f + 0.15f * (if (beat < 0.12f) beat / 0.12f else if (beat < 0.3f) 1f - (beat - 0.12f) / 0.18f else 0f)
                    scale = Scale(s)
                })
                for (k in 0 until 6) {
                    Ball(0.01f, p.leaf, scale = Scale(1f, 0.25f, 0.55f), segments = 8, motion = { t, _ ->
                        val a = k * TAU / 6 + t * 0.8f * Motion.amp
                        position = Position(cos(a) * 0.065f, sin(t * 1.5f + k) * 0.015f, sin(a) * 0.065f)
                        rotation = Rotation(0f, -Math.toDegrees(a.toDouble()).toFloat(), 25f)
                    })
                }
            }
            3 -> { // Peace: a lotus opening on calm ripples
                for (k in 0 until 8) {
                    Node(rotation = Rotation(0f, k * 45f, 0f)) {
                        Node(apply = { animate { t, _ -> rotation = Rotation(-15f - 45f * ease(t / 2f), 0f, 0f) } }) {
                            Ball(0.022f, p.rose, Position(0f, 0.016f, 0.012f), Scale(0.45f, 1f, 0.14f), 10)
                        }
                    }
                }
                for (k in 0 until 5) {
                    Node(rotation = Rotation(0f, k * 72f + 36f, 0f)) {
                        Ball(0.016f, p.lavender, Position(0f, 0.016f, 0.004f), Scale(0.45f, 1f, 0.14f), 10)
                    }
                }
                Ball(0.008f, p.goldGlow, Position(0f, 0.012f, 0f), segments = 10)
                for (k in 0 until 3) {
                    TorusNode(majorRadius = 0.05f, minorRadius = 0.0015f, majorSegments = 32, minorSegments = 4,
                        materialInstance = p.lavenderGlow, position = Position(0f, -0.005f, 0f), apply = {
                            animate { t, _ ->
                                val c = (t * 0.35f + k / 3f) % 1f
                                scale = Scale(safe(0.4f + c * 1.4f), 1f, safe(0.4f + c * 1.4f))
                            }
                        })
                }
            }
            4 -> { // Adventures: a hot-air balloon drifting over little mountains
                Node(apply = {
                    animate { t, _ ->
                        position = Position(sin(t * 0.5f) * 0.03f * Motion.amp, 0.02f + sin(t * 0.9f) * 0.012f, 0f)
                        rotation = Rotation(0f, 0f, sin(t * 0.7f) * 5f * Motion.amp)
                    }
                }) {
                    Ball(0.033f, p.rose, Position(0f, 0.03f, 0f), Scale(1f, 1.12f, 1f), 18)
                    for (yy in listOf(0.012f, 0.03f, 0.048f)) {
                        val rr = sqrt(max(0f, 0.033f * 0.033f - (yy - 0.03f) * (yy - 0.03f) / 1.25f))
                        TorusNode(majorRadius = rr + 0.0005f, minorRadius = 0.0014f, majorSegments = 24, minorSegments = 4,
                            materialInstance = p.gold, position = Position(0f, yy, 0f))
                    }
                    CubeNode(size = Size(0.014f, 0.01f, 0.014f), materialInstance = p.champagne, position = Position(0f, -0.022f, 0f))
                    for (sx in listOf(-0.006f, 0.006f)) {
                        CylinderNode(radius = 0.0006f, height = 0.02f, sideCount = 4, materialInstance = p.gold,
                            position = Position(sx, -0.008f, 0f))
                    }
                }
                for ((mx, mh) in listOf(-0.05f to 0.045f, 0f to 0.06f, 0.05f to 0.04f)) {
                    ConeNode(radius = 0.03f, height = mh, sideCount = 12, materialInstance = p.lavender,
                        position = Position(mx, -0.07f + mh / 2f, -0.03f))
                }
                CylinderNode(radius = 0.0008f, height = 0.02f, sideCount = 4, materialInstance = p.gold, position = Position(0f, 0.0f, -0.03f))
                CubeNode(size = Size(0.01f, 0.006f, 0.001f), materialInstance = p.roseGlow, position = Position(0.005f, 0.007f, -0.03f))
            }
            5 -> { // Dreams: the moon and twinkling stars — tap the moon for a wishing star
                Ball(0.03f, p.champagne, segments = 18, onTap = {
                    story.shootingStarKey++
                    story.found("moon")
                    story.say(Lines.moonEgg)
                    story.sfx(Sfx.SPARKLE)
                })
                Ball(0.031f, p.plumDeep, Position(0.014f, 0.008f, 0.004f), segments = 18)
                for (k in 0 until 14) {
                    val a = k * 2.4f
                    val r = 0.05f + (k % 4) * 0.012f
                    Ball(0.0028f, p.goldGlow, Position(cos(a) * r, sin(a) * r * 0.8f, (k % 3) * 0.01f - 0.01f), segments = 6,
                        motion = { t, _ -> scale = Scale(0.5f + 0.7f * abs(sin(t * 2f + k))) })
                }
                for (k in 0 until 3) Ball(0.012f, p.white, Position(-0.04f + k * 0.012f, -0.035f, 0.01f), Scale(1f, 0.6f, 0.6f), 10)
                key(story.shootingStarKey) {
                    if (story.shootingStarKey > 0) {
                        for (tr in 0 until 6) {
                            Ball(0.004f - tr * 0.0005f, p.goldGlow, segments = 6, motion = { t, _ ->
                                val u = (t - tr * 0.03f) / 1.2f
                                position = Position(-0.12f + u * 0.24f, 0.08f - u * 0.1f, 0.02f)
                                scale = Scale(safe(if (u in 0f..1f) 1f else 0f))
                            })
                        }
                    }
                }
            }
            else -> { // Unconditional love: a crystal heart with orbiting hearts
                Heart(p.roseGlass, 0.07f, motion = { t, _ ->
                    scale = Scale(1f + 0.06f * sin(t * 3f))
                    rotation = Rotation(0f, sin(t * 0.6f) * 25f, 0f)
                })
                Heart(p.roseGlow, 0.03f, Position(0f, 0f, 0f), motion = { t, _ ->
                    scale = Scale(1f + 0.2f * max(0f, sin(t * 6f)))
                })
                for (k in 0 until 8) {
                    Heart(if (k % 2 == 0) p.goldGlow else p.roseGlow, 0.012f, motion = { t, _ ->
                        val a = k * TAU / 8 + t * 0.9f * Motion.amp
                        position = Position(cos(a) * 0.075f, sin(t * 1.3f + k) * 0.02f, sin(a) * 0.075f)
                    })
                }
            }
        }
    }
}

// ───────────────────────────── 6. Birthday celebration ─────────────────────────────

@Composable
fun SceneScope.CakeScene(p: Palette, story: Story) {
    LaunchedEffect(Unit) {
        story.say(Lines.cake1)
        delay(3000)
        if (story.candleLit) story.say(Lines.cake2)
    }
    val blow = { story.blowOut() }
    Node(apply = {
        animate { t, _ ->
            position = Position(0f, 0.03f + sin(t * 1.0f) * 0.008f * Motion.amp, 0f)
            rotation = Rotation(0f, sin(t * 0.3f) * 10f * Motion.amp, 0f)
            scale = Scale(safe(easeOutBack(t / 1f)))
        }
    }) {
        CylinderNode(radius = 0.13f, height = 0.006f, sideCount = 40, materialInstance = p.champagne)
        CylinderNode(radius = 0.1f, height = 0.07f, sideCount = 40, materialInstance = p.rose,
            position = Position(0f, 0.038f, 0f), apply = tapper(blow))
        TorusNode(majorRadius = 0.1f, minorRadius = 0.008f, majorSegments = 40, minorSegments = 8,
            materialInstance = p.cream, position = Position(0f, 0.073f, 0f))
        for (k in 0 until 14) {
            val a = k * TAU / 14
            Ball(0.0055f, p.pearl, Position(cos(a) * 0.102f, 0.01f, sin(a) * 0.102f), segments = 8)
            Ball(0.006f, p.cream, Position(cos(a) * 0.101f, 0.064f, sin(a) * 0.101f), Scale(1f, 1.6f, 1f), 8)
        }
        CylinderNode(radius = 0.07f, height = 0.055f, sideCount = 36, materialInstance = p.lavender,
            position = Position(0f, 0.1005f, 0f), apply = tapper(blow))
        TorusNode(majorRadius = 0.07f, minorRadius = 0.007f, majorSegments = 36, minorSegments = 8,
            materialInstance = p.cream, position = Position(0f, 0.128f, 0f))
        for (k in 0 until 10) {
            val a = k * TAU / 10 + 0.3f
            Ball(0.005f, p.cream, Position(cos(a) * 0.071f, 0.121f, sin(a) * 0.071f), Scale(1f, 1.8f, 1f), 8)
        }
        for (k in 0 until 5) {
            val a = k * TAU / 5
            Heart(p.roseGlow, 0.012f, Position(cos(a) * 0.045f, 0.137f, sin(a) * 0.045f))
        }
        // candle
        CylinderNode(radius = 0.007f, height = 0.06f, sideCount = 12, materialInstance = p.pearl,
            position = Position(0f, 0.158f, 0f), apply = tapper(blow))
        for (k in 0 until 3) {
            TorusNode(majorRadius = 0.0072f, minorRadius = 0.0012f, majorSegments = 12, minorSegments = 4,
                materialInstance = p.gold, position = Position(0f, 0.14f + k * 0.017f, 0f))
        }
        if (story.candleLit) {
            Node(position = Position(0f, 0.2f, 0f), apply = {
                animate { t, _ ->
                    val f = sin(t * 23f) * 0.5f + sin(t * 37f) * 0.5f
                    scale = Scale(1f + 0.06f * f, 1f + 0.14f * f, 1f + 0.06f * f)
                    rotation = Rotation(0f, 0f, sin(t * 5f) * 6f)
                }
            }) {
                Ball(0.011f, p.flame, scale = Scale(1f, 1.75f, 1f), segments = 12, onTap = blow)
                Ball(0.0055f, p.flameCore, Position(0f, -0.003f, 0.002f), Scale(1f, 1.6f, 1f), 10)
            }
        } else {
            for (k in 0 until 5) {
                Ball(0.007f, p.smoke, segments = 8, motion = { t, _ ->
                    val c = clamp01((t - k * 0.25f) / 2.2f)
                    position = Position(sin(c * 6f + k) * 0.01f, 0.2f + c * 0.15f, 0f)
                    scale = Scale(safe(if (c >= 1f || t < k * 0.25f) 0f else (0.5f + c * 1.5f) * (1f - c)))
                })
            }
        }
    }
    val ck = story.celebrationKey
    if (ck > 0) key(ck) { Celebration(p, Position(0f, 0.22f, 0f)) }
}

/** Coordinated particle, butterfly and celestial-heart celebration. Replayable via key. */
@Composable
fun SceneScope.Celebration(p: Palette, origin: Position) {
    val parts = remember {
        val rnd = Random(System.nanoTime())
        List(if (Motion.reduced) 18 else 42) {
            val a = rnd.nextFloat() * TAU
            val up = 0.25f + rnd.nextFloat() * 0.45f
            val sp = 0.12f + rnd.nextFloat() * 0.22f
            floatArrayOf(cos(a) * sp, up, sin(a) * sp, rnd.nextFloat())
        }
    }
    val mats = listOf(p.goldGlow, p.roseGlow, p.lavenderGlow, p.white)
    parts.forEachIndexed { i, v ->
        Ball(if (i % 4 == 0) 0.005f else 0.0035f, mats[i % mats.size], segments = 6, motion = { t, _ ->
            val tt = t * 0.9f
            position = Position(origin.x + v[0] * tt, origin.y + v[1] * tt - 0.25f * tt * tt, origin.z + v[2] * tt)
            scale = Scale(safe((1f - clamp01((t - 1.6f) / 1.2f)) * (0.8f + 0.5f * abs(sin(t * 9f + i)))))
        })
    }
    for (i in 0 until 6) {
        Node(position = Position(0f, 0f, 0f), apply = {
            animate { t, _ ->
                val c = clamp01(t / 4.5f)
                position = Position(origin.x, origin.y - 0.1f + c * 0.35f, origin.z)
                scale = Scale(safe(if (c >= 1f) 0f else 1f))
            }
        }) {
            Butterfly(p, if (i % 2 == 0) p.roseGlass else p.lavenderGlass, 0.08f + i * 0.02f, 0f, 1.6f, i * 1.05f)
        }
    }
    // celestial heart of light forming above
    val n = 26
    for (i in 0 until n) {
        val (hx, hy) = heartPoint(i * TAU / n)
        Ball(0.0045f, if (i % 2 == 0) p.goldGlow else p.roseGlow, segments = 8, motion = { t, _ ->
            val m = ease((t - 0.8f) / 1.2f)
            position = Position(origin.x + hx * 0.12f * m, origin.y + 0.22f + hy * 0.12f * m, origin.z)
            scale = Scale(safe(m * (1f + 0.4f * sin(t * 6f + i))))
        })
    }
}

// ───────────────────────────── 7. The invisible hug ─────────────────────────────

@Composable
fun SceneScope.HugScene(p: Palette, story: Story) {
    LaunchedEffect(Unit) {
        story.say(Lines.hug)
        story.sfx(Sfx.HEARTBEAT)
        delay(5400)
        story.ribbonWrapped = true
        story.sfx(Sfx.SPARKLE)
    }
    Node(position = Position(0f, 0.25f, 0f), apply = {
        animate { t, _ ->
            position = Position(0f, 0.25f + sin(t * 1.1f) * 0.01f * Motion.amp, 0f)
            scale = Scale(safe(easeOutBack(t / 1.2f)))
        }
    }) {
        Heart(p.roseGlass, 0.11f, onTap = { story.sfx(Sfx.HEARTBEAT); story.lumi.feel(Mood.Happy) }, motion = { t, _ ->
            rotation = Rotation(0f, t * 25f * Motion.amp, 0f)
        })
        Heart(p.roseGlow, 0.05f, motion = { t, _ ->
            val beat = (t % 1.2f)
            val s = 1f + 0.22f * (if (beat < 0.1f) beat / 0.1f else if (beat < 0.25f) 1f - (beat - 0.1f) / 0.15f
                else if (beat < 0.35f) (beat - 0.25f) / 0.1f * 0.6f else if (beat < 0.5f) 0.6f - (beat - 0.35f) / 0.15f * 0.6f else 0f)
            scale = Scale(s)
            rotation = Rotation(0f, t * 25f * Motion.amp, 0f)
        })
    }
    // golden ribbon wrapping around the heart
    val n = 72
    for (i in 0 until n) {
        val f = i / (n - 1f)
        Ball(if (i % 6 == 0) 0.005f else 0.0038f, if (i % 6 == 0) p.goldGlow else p.gold, segments = 6, motion = { t, _ ->
            val appear = easeOutBack((t - 0.6f - i * 0.065f) / 0.35f)
            val a = i * 0.42f + t * 0.5f * Motion.amp
            val r = 0.025f + 0.085f * sqrt(sin(f * 3.1416f))
            position = Position(cos(a) * r, 0.14f + f * 0.23f, sin(a) * r)
            scale = Scale(safe(appear))
        })
    }
    // halo
    TorusNode(majorRadius = 0.13f, minorRadius = 0.002f, majorSegments = 48, minorSegments = 4,
        materialInstance = p.goldGlow, position = Position(0f, 0.25f, 0f), apply = {
            animate { t, _ ->
                val s = ease((t - 5.4f) / 1f)
                scale = Scale(safe(s * (1f + 0.04f * sin(t * 2f))))
                rotation = Rotation(70f, t * 10f, 0f)
            }
        })
}

// ───────────────────────────── 8. Final gift ─────────────────────────────

@Composable
fun SceneScope.FinalScene(p: Palette, story: Story) {
    LaunchedEffect(Unit) {
        story.say(Lines.final)
        story.lumi.feel(Mood.Wave)
    }
    // constellation dome
    val stars = remember {
        val rnd = Random(4122026)
        List(34) { floatArrayOf(rnd.nextFloat() * TAU, 0.15f + rnd.nextFloat() * 0.75f, rnd.nextFloat()) }
    }
    stars.forEachIndexed { i, s ->
        val r = 0.42f
        val el = s[1] * 1.3f
        Ball(if (i % 5 == 0) 0.005f else 0.003f, if (i % 4 == 0) p.lavenderGlow else p.goldGlow, segments = 6,
            position = Position(cos(s[0]) * cos(el) * r, 0.05f + sin(el) * r, sin(s[0]) * cos(el) * r - 0.1f),
            motion = { t, _ -> scale = Scale(safe(ease((t - s[2]) / 0.6f) * (0.6f + 0.6f * abs(sin(t * 1.5f + i))))) })
    }
    // golden heart constellation
    val n = 28
    for (i in 0 until n) {
        val (hx, hy) = heartPoint(i * TAU / n)
        Ball(0.0045f, p.goldGlow, Position(hx * 0.12f, 0.34f + hy * 0.12f, -0.02f), segments = 8, motion = { t, _ ->
            scale = Scale(safe(easeOutBack((t - 0.5f - i * 0.04f) / 0.5f) * (1f + 0.3f * sin(t * 3f + i))))
        })
    }
    Heart(p.roseGlass, 0.065f, Position(0f, 0.34f, -0.02f), onTap = {
        story.finalHeartTaps++
        story.sfx(Sfx.CHIME)
        if (story.finalHeartTaps % 3 == 0) {
            story.found("heart")
            story.say(Lines.heartEgg)
            story.celebrateAgain()
        }
    }, motion = { t, _ ->
        scale = Scale(safe(ease((t - 1.5f) / 0.8f) * (1f + 0.07f * sin(t * 3f))))
        rotation = Rotation(0f, t * 20f * Motion.amp, 0f)
    })
    // a small echo of the portal behind
    Node(position = Position(0f, 0.34f, -0.06f), apply = {
        animate { t, _ ->
            scale = Scale(safe(ease((t - 1f) / 1.2f)))
            rotation = Rotation(0f, 0f, t * 8f)
        }
    }) {
        TorusNode(majorRadius = 0.2f, minorRadius = 0.004f, majorSegments = 48, minorSegments = 6,
            materialInstance = p.gold, rotation = Rotation(90f, 0f, 0f))
    }
    Flower(p, p.rose, Position(-0.12f, 0.05f, 0.08f), 0.8f, false)
    Flower(p, p.lavender, Position(0.12f, 0.05f, 0.08f), 1.0f, false)
    Flower(p, p.rose, Position(-0.2f, 0.05f, -0.02f), 1.2f, false)
    Flower(p, p.lavender, Position(0.2f, 0.05f, -0.02f), 1.4f, false)
    Butterfly(p, p.roseGlass, 0.26f, 0.22f, 0.45f, 0f, 1f)
    Butterfly(p, p.lavenderGlass, 0.3f, 0.3f, 0.55f, 2.5f, 1.4f)
    val ck = story.celebrationKey
    if (ck > 0) key(ck) { Celebration(p, Position(0f, 0.2f, 0f)) }
}
