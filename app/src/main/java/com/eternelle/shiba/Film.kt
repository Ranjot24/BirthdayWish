package com.eternelle.shiba

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.widget.Toast
import android.widget.VideoView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView

fun filmUri(context: Context): Uri = Uri.parse("android.resource://${context.packageName}/${R.raw.eternelle_film}")

/** Full-screen player for the cinematic birthday film. */
@Composable
fun FilmPlayer(audio: AudioDirector, onClose: () -> Unit) {
    val context = LocalContext.current
    DisposableEffect(Unit) {
        audio.pauseForFilm()
        onDispose { audio.resumeAfterFilm() }
    }
    Box(Modifier.fillMaxSize().background(Color.Black)) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                VideoView(ctx).apply {
                    setVideoURI(filmUri(context))
                    setOnCompletionListener { onClose() }
                    setOnErrorListener { _, _, _ -> onClose(); true }
                    setOnClickListener { if (isPlaying) pause() else start() }
                    start()
                }
            },
            onRelease = { it.stopPlayback() },
        )
        Box(Modifier.align(Alignment.TopEnd).safeDrawingPadding().padding(12.dp)) {
            PillButton("Skip ✕", primary = false, onClick = onClose)
        }
    }
}

/** Copies the film into the gallery (Movies/ETERNELLE) and opens the share sheet. Android 10+. */
fun shareFilm(context: Context) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
        Toast.makeText(context, "Sharing the film needs Android 10 or newer — but you can watch it here anytime!", Toast.LENGTH_LONG).show()
        return
    }
    val uri = try {
        val values = ContentValues().apply {
            put(MediaStore.Video.Media.DISPLAY_NAME, "ETERNELLE_Film_${System.currentTimeMillis()}.mp4")
            put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
            put(MediaStore.Video.Media.RELATIVE_PATH, Environment.DIRECTORY_MOVIES + "/ETERNELLE")
        }
        val out = context.contentResolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values)
        out?.also { dest ->
            context.resources.openRawResource(R.raw.eternelle_film).use { input ->
                context.contentResolver.openOutputStream(dest)?.use { input.copyTo(it) }
            }
        }
    } catch (_: Exception) { null }
    if (uri == null) {
        Toast.makeText(context, "Couldn't save the film, sorry!", Toast.LENGTH_LONG).show(); return
    }
    Toast.makeText(context, "Saved to your gallery (Movies/ETERNELLE) ✦", Toast.LENGTH_SHORT).show()
    val send = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
        type = "video/mp4"
        putExtra(android.content.Intent.EXTRA_STREAM, uri)
        addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(android.content.Intent.createChooser(send, "Share your birthday film"))
}
