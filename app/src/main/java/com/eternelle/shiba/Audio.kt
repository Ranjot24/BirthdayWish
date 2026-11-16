package com.eternelle.shiba

import android.annotation.SuppressLint
import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.media.SoundPool
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import java.util.Locale
import androidx.annotation.RawRes
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import kotlin.math.max
import kotlin.math.sqrt

enum class Sfx(@RawRes val res: Int) {
    CHIME(R.raw.sfx_chime),
    SPARKLE(R.raw.sfx_sparkle),
    GIGGLE(R.raw.sfx_giggle),
    WHOOSH(R.raw.sfx_whoosh),
    PAGE(R.raw.sfx_page),
    BLOW(R.raw.sfx_blow),
    POP(R.raw.sfx_pop),
    PORTAL(R.raw.sfx_portal),
    CELEBRATE(R.raw.sfx_celebrate),
    BELL_LEFT(R.raw.sfx_bell_left),
    BELL_RIGHT(R.raw.sfx_bell_right),
    HEARTBEAT(R.raw.sfx_heartbeat),
}

/**
 * The hug message is spoken in Punjabi by the phone's own text-to-speech voice (pa-IN).
 * If that voice isn't installed, a gentle melody plays and the words are shown on screen.
 */

/** Owns all sound: SoundPool for effects, MediaPlayer for the music bed and the voice message. */
class AudioDirector(private val context: Context) {
    private val pool = SoundPool.Builder()
        .setMaxStreams(8)
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_GAME)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()
        ).build()
    private val ids = HashMap<Sfx, Int>()
    @Volatile var loadedCount = 0
        private set
    val total = Sfx.entries.size

    var sfxOn = true
    var musicOn = true
        set(value) { field = value; if (value) startMusic() else music?.pause() }

    private var music: MediaPlayer? = null
    private var voice: MediaPlayer? = null
    private var musicVolume = 0.32f
    private var foreground = true

    private val main = Handler(Looper.getMainLooper())
    private var tts: TextToSpeech? = null
    @Volatile var punjabiVoice = false
        private set

    init {
        tts = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                @Suppress("DEPRECATION")
                val r = tts?.setLanguage(Locale("pa", "IN")) ?: TextToSpeech.LANG_NOT_SUPPORTED
                punjabiVoice = r >= TextToSpeech.LANG_AVAILABLE
                tts?.setSpeechRate(0.88f)
                tts?.setPitch(1.0f)
            }
        }
        pool.setOnLoadCompleteListener { _, _, _ -> loadedCount++ }
        Sfx.entries.forEach { ids[it] = pool.load(context, it.res, 1) }
    }

    fun play(s: Sfx, volume: Float = 0.9f) {
        if (!sfxOn) return
        val id = ids[s] ?: return
        pool.play(id, volume, volume, 1, 0, 1f)
    }

    fun startMusic() {
        if (!musicOn || !foreground || filmPlaying) return
        if (music == null) {
            music = MediaPlayer.create(context, R.raw.music_universe)?.apply {
                isLooping = true
                setVolume(musicVolume, musicVolume)
            }
        }
        if (music?.isPlaying == false) music?.start()
    }

    private fun setMusicVolume(v: Float) {
        musicVolume = v
        music?.setVolume(v, v)
    }

    fun playVoice(onDone: () -> Unit) {
        stopVoice()
        setMusicVolume(0.06f) // never overpower the voice
        val engine = tts
        if (punjabiVoice && engine != null) {
            engine.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) {}
                override fun onDone(utteranceId: String?) {
                    main.post { setMusicVolume(0.32f); onDone() }
                }
                @Deprecated("Deprecated in Java")
                override fun onError(utteranceId: String?) {
                    main.post { setMusicVolume(0.32f); onDone() }
                }
            })
            val ok = engine.speak(Lines.veerPunjabi, TextToSpeech.QUEUE_FLUSH, Bundle(), "veer")
            if (ok == TextToSpeech.SUCCESS) return
        }
        voice = MediaPlayer.create(context, R.raw.veer_message)?.apply {
            setOnCompletionListener {
                setMusicVolume(0.32f)
                onDone()
            }
            start()
        }
        if (voice == null) { setMusicVolume(0.32f); onDone() }
    }

    fun stopVoice() {
        tts?.stop()
        voice?.run { try { stop() } catch (_: Exception) {}; release() }
        voice = null
        setMusicVolume(0.32f)
    }

    private var filmPlaying = false
    fun pauseForFilm() { filmPlaying = true; stopVoice(); music?.pause() }
    fun resumeAfterFilm() { filmPlaying = false; startMusic() }

    fun onForeground() { foreground = true; startMusic() }

    fun onBackground() {
        foreground = false
        music?.pause()
        voice?.pause()
        tts?.stop()
    }

    fun release() {
        stopVoice()
        tts?.shutdown(); tts = null
        music?.release(); music = null
        pool.release()
    }
}

/**
 * Listens to the microphone locally and reports a "blow" when a sustained loud, breathy sound is
 * heard. Audio never leaves the phone and is never saved — samples are discarded after measuring.
 */
@SuppressLint("MissingPermission") // caller checks RECORD_AUDIO before calling
suspend fun listenForBlow(onLevel: (Float) -> Unit, onBlow: () -> Unit) = withContext(Dispatchers.IO) {
    val rate = 16000
    val minBuf = AudioRecord.getMinBufferSize(rate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
    if (minBuf <= 0) return@withContext
    val rec = try {
        AudioRecord(MediaRecorder.AudioSource.MIC, rate, AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT, max(minBuf, 2048))
    } catch (_: Exception) { return@withContext }
    if (rec.state != AudioRecord.STATE_INITIALIZED) { rec.release(); return@withContext }
    val buf = ShortArray(800) // 50 ms
    var ambient = 0.02f
    var frames = 0
    var loudRun = 0
    try {
        rec.startRecording()
        while (isActive) {
            val n = rec.read(buf, 0, buf.size)
            if (n <= 0) continue
            var sum = 0.0
            for (i in 0 until n) { val s = buf[i] / 32768.0; sum += s * s }
            val rms = sqrt(sum / n).toFloat()
            frames++
            if (frames <= 10) ambient = max(ambient, rms) // ~0.5 s calibration
            val threshold = max(0.10f, ambient * 3.5f)
            withContext(Dispatchers.Main) { onLevel((rms / threshold).coerceIn(0f, 1.5f)) }
            if (frames > 10) {
                loudRun = if (rms > threshold) loudRun + 1 else max(0, loudRun - 1)
                if (loudRun >= 5) { // ~250 ms of sustained blowing
                    withContext(Dispatchers.Main) { onBlow() }
                    break
                }
            }
        }
    } finally {
        try { rec.stop() } catch (_: Exception) {}
        rec.release()
    }
}
