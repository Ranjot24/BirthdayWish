package com.eternelle.shiba

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.Shader
import android.graphics.Typeface
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import java.io.File
import java.io.FileOutputStream
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/** Draws the birthday greeting keepsake as a 1080×1350 image. */
fun drawGreeting(eggs: Int): Bitmap {
    val w = 1080; val h = 1350
    val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
    val c = Canvas(bmp)
    val bg = Paint().apply {
        shader = LinearGradient(0f, 0f, 0f, h.toFloat(),
            intArrayOf(0xFF1B0F2E.toInt(), 0xFF4A1D55.toInt(), 0xFF8E5C9E.toInt()), floatArrayOf(0f, 0.6f, 1f), Shader.TileMode.CLAMP)
    }
    c.drawRect(0f, 0f, w.toFloat(), h.toFloat(), bg)
    val rnd = Random(4122026)
    val star = Paint(Paint.ANTI_ALIAS_FLAG)
    repeat(160) {
        val x = rnd.nextFloat() * w; val y = rnd.nextFloat() * h * 0.85f
        val r = 1f + rnd.nextFloat() * 3.2f
        star.color = if (it % 5 == 0) 0xFFFFE2A0.toInt() else 0xCCFFFFFF.toInt()
        c.drawCircle(x, y, r, star)
    }
    // glow
    val glow = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        shader = RadialGradient(w / 2f, 560f, 420f, intArrayOf(0x66FF9FBD, 0x00FF9FBD), null, Shader.TileMode.CLAMP)
    }
    c.drawCircle(w / 2f, 560f, 420f, glow)
    // constellation heart of golden beads
    val bead = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFFFE2A0.toInt() }
    val line = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x66FFE2A0; strokeWidth = 2.5f }
    val pts = (0 until 36).map { i ->
        val (hx, hy) = heartPoint(i * TAU / 36)
        Pair(w / 2f + hx * 250f, 560f - hy * 250f)
    }
    pts.forEachIndexed { i, p -> val q = pts[(i + 1) % pts.size]; c.drawLine(p.first, p.second, q.first, q.second, line) }
    pts.forEachIndexed { i, p -> c.drawCircle(p.first, p.second, if (i % 3 == 0) 9f else 6f, bead) }
    // crystal heart
    val heart = Path()
    for (i in 0..120) {
        val (hx, hy) = heartPoint(i * TAU / 120)
        val x = w / 2f + hx * 120f; val y = 575f - hy * 120f
        if (i == 0) heart.moveTo(x, y) else heart.lineTo(x, y)
    }
    heart.close()
    c.drawPath(heart, Paint(Paint.ANTI_ALIAS_FLAG).apply {
        shader = LinearGradient(w / 2f - 120f, 450f, w / 2f + 120f, 700f, 0xFFFFB3CB.toInt(), 0xFFFF6E9C.toInt(), Shader.TileMode.CLAMP)
    })
    // little Lumi
    val lx = 230f; val ly = 1000f
    val wing = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x88CDB9FF.toInt() }
    c.drawOval(lx - 110f, ly - 70f, lx - 20f, ly + 10f, wing)
    c.drawOval(lx + 20f, ly - 70f, lx + 110f, ly + 10f, wing)
    c.drawCircle(lx, ly, 62f, Paint(Paint.ANTI_ALIAS_FLAG).apply {
        shader = RadialGradient(lx - 20f, ly - 25f, 80f, 0xFFFFFFFF.toInt(), 0xFFF1E4EE.toInt(), Shader.TileMode.CLAMP)
    })
    val ink = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF1E1026.toInt() }
    c.drawCircle(lx - 20f, ly - 8f, 9f, ink); c.drawCircle(lx + 20f, ly - 8f, 9f, ink)
    val cheek = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x99FF9FBD.toInt() }
    c.drawCircle(lx - 36f, ly + 10f, 10f, cheek); c.drawCircle(lx + 36f, ly + 10f, 10f, cheek)
    c.drawArc(lx - 12f, ly + 2f, lx + 12f, ly + 20f, 10f, 160f, false,
        Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF1E1026.toInt(); style = Paint.Style.STROKE; strokeWidth = 4f })
    for (k in 0 until 6) {
        val a = k * TAU / 6
        c.drawCircle(lx + cos(a) * 95f, ly + sin(a) * 40f - 10f, 5f, bead)
    }
    // text
    val serif = Typeface.create(Typeface.SERIF, Typeface.ITALIC)
    val gold = 0xFFFFE2A0.toInt()
    fun text(s: String, y: Float, size: Float, color: Int, tf: Typeface = serif, spacing: Float = 0f) {
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = color; textSize = size; typeface = tf; textAlign = Paint.Align.CENTER; letterSpacing = spacing
        }
        c.drawText(s, w / 2f, y, p)
    }
    text("ETERNELLE", 140f, 34f, gold, Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL), 0.5f)
    text("Happy Birthday,", 230f, 64f, 0xFFFFF8F5.toInt())
    text("Shiba Sis", 320f, 104f, 0xFFFFC2D1.toInt())
    text("Distance can change the address.", 880f, 40f, 0xFFFFF8F5.toInt())
    text("Never the bond.", 935f, 40f, 0xFFFFF8F5.toInt())
    text("Your veer is always with you.", 1110f, 46f, 0xFFFFC2D1.toInt())
    text("A little universe. A lifetime of love.", 1215f, 30f, gold, Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL), 0.05f)
    text("4 December 2026  ·  Jamshedpur ♥ Kolkata", 1270f, 26f, 0xCCD7CAFF.toInt(), Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL), 0.08f)
    if (eggs > 0) text("✦ ".repeat(eggs).trim(), 1310f, 22f, gold)
    return bmp
}

/** Saves to Pictures/ETERNELLE and returns its content Uri. Nothing is uploaded anywhere. */
fun saveGreeting(context: Context, bmp: Bitmap): android.net.Uri? = try {
    val name = "ETERNELLE_Birthday_${System.currentTimeMillis()}.png"
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, name)
            put(MediaStore.Images.Media.MIME_TYPE, "image/png")
            put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/ETERNELLE")
        }
        val uri = context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
        if (uri != null && context.contentResolver.openOutputStream(uri)?.use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) } == true) uri else null
    } else {
        @Suppress("DEPRECATION")
        MediaStore.Images.Media.insertImage(context.contentResolver, bmp, name, "ETERNELLE birthday greeting")
            ?.let { android.net.Uri.parse(it) }
    }
} catch (_: Exception) {
    null
}

/** Opens the share sheet (WhatsApp, etc.) for a saved greeting. */
fun shareGreeting(context: Context, uri: android.net.Uri) {
    val send = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
        type = "image/png"
        putExtra(android.content.Intent.EXTRA_STREAM, uri)
        putExtra(android.content.Intent.EXTRA_TEXT, "A little universe. A lifetime of love. ✦")
        addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(android.content.Intent.createChooser(send, "Share your birthday keepsake"))
}
