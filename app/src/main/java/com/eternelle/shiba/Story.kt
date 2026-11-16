package com.eternelle.shiba

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import io.github.sceneview.math.Position

/** The eight-part story, in order. INVITATION and LOADING are 2D; the rest happen in the universe. */
enum class Chapter(val title: String) {
    INVITATION("Invitation"),
    LOADING("Loading"),
    PORTAL("Portal"),
    LUMI("Lumi"),
    CITIES("Two Cities"),
    BOOK("Blessings"),
    CAKE("Cake"),
    HUG("Hug"),
    FINAL("Gift");

    companion object {
        val universe = listOf(PORTAL, LUMI, CITIES, BOOK, CAKE, HUG, FINAL)
    }
}

enum class Mood { Idle, Happy, Laugh, Wave, Spin }

/**
 * Plain (non-Compose) state that the 3D frame loop reads every frame.
 * Written from the UI thread, read from the render callbacks on the same thread.
 */
class LumiBrain {
    var target = Position(0f, 0.2f, 0.1f)
    var mood = Mood.Idle
    var moodAt = 0L
    var appearAt = 0L
    var x = 0f; var y = 0.22f; var z = -0.02f
    var lastNow = 0L
    var taps = 0

    fun feel(m: Mood) { mood = m; moodAt = System.nanoTime() }
    fun secondsInMood(now: Long) = (now - moodAt) / 1e9f
}

class BookBrain {
    val turnedAt = LongArray(7) { 0L }
}

data class Blessing(val title: String, val line: String)

val Blessings = listOf(
    Blessing("Happiness", "May your days be full of the kind of joy that makes you dance in the kitchen for no reason."),
    Blessing("Laughter", "May you laugh until your cheeks hurt — often, loudly, and with the people you love."),
    Blessing("Health", "May your body stay strong, your sleep be deep, and your chai always be perfectly hot."),
    Blessing("Peace", "May your heart be as calm as a quiet Sunday morning, wherever this year takes you."),
    Blessing("Adventures", "May this year bring new places, brave beginnings and stories worth telling forever."),
    Blessing("Dreams", "May every dream you whisper find its way to you — and bring a few friends along."),
    Blessing("Unconditional Love", "And may you always know: you are loved completely, endlessly, with no conditions at all."),
)

object Lines {
    val lumiIntro = listOf(
        "Finally! You're here!",
        "Hi, I'm Lumi! Your veer sent me with a whole universe, just for you.",
        "Okay, birthday queen, ready?",
    )
    val lumiTaps = listOf(
        "Hehe! That tickles!",
        "Boop! Right back at you!",
        "Sending you a million little hugs!",
        "You're glowing today. Like, actually glowing.",
        "Wheee! Again! Again!",
        "Shh… I'm practising my birthday dance.",
    )
    const val lumiSecret = "Okay okay, secret mission: your veer says you're his favourite person. Don't tell anyone!"
    const val cities = "Look! Jamshedpur and Kolkata — two cities, one home."
    const val book = "This is the Blessing Book. Touch a page to open it!"
    const val cake1 = "One more magical surprise!"
    const val cake2 = "Close your eyes, make a wish… then blow!"
    const val cakeDone = "WOOHOO! Your wish is already on its way to the stars!"
    const val hug = "Shh… someone special has a message for you."
    const val final = "Happy Birthday, Shiba Sis! This little universe is yours forever."
    const val moonEgg = "You found a wishing star! Quick — make another wish!"
    const val heartEgg = "Psst… you found the secret celebration button!"
    const val inviteEgg = "Psst… Lumi practised saying \"Happy Birthday\" for 364 days."
    const val jamshedpur = "Jamshedpur — where it all began. Home, always."
    const val kolkata = "Kolkata — where your new days are unfolding."
}

/** Compose-observable story state, shared by the 2D overlay and the 3D universe. */
class Story(context: Context) {
    private val prefs = context.getSharedPreferences("eternelle", Context.MODE_PRIVATE)

    var chapter by mutableStateOf(Chapter.INVITATION)
        private set
    var furthest by mutableIntStateOf(prefs.getInt("furthest", Chapter.PORTAL.ordinal))
        private set
    var runId by mutableIntStateOf(0)
        private set

    // Portal / AR placement
    var placed by mutableStateOf(false)
    var planeFound by mutableStateOf(false)
    var arHint by mutableStateOf("")
    var placeCenter: (() -> Boolean)? = null
    var portalOpened by mutableStateOf(false)
    // Lumi
    var lumiLine by mutableStateOf<String?>(null)
    var lumiLineId by mutableIntStateOf(0)
    val lumi = LumiBrain()
    // Cities
    var heartFormed by mutableStateOf(false)
    // Book
    var pagesOpened by mutableIntStateOf(0)
    val book = BookBrain()
    // Cake
    var candleLit by mutableStateOf(true)
    var celebrationKey by mutableIntStateOf(0)
    // Hug
    var ribbonWrapped by mutableStateOf(false)
    var voicePlaying by mutableStateOf(false)
    // Final
    var keepsakeMode by mutableStateOf(false)
    var eggsFound by mutableStateOf(setOf<String>())
    var shootingStarKey by mutableIntStateOf(0)
    var finalHeartTaps = 0

    // Settings
    var soundOn by mutableStateOf(true)
    var musicOn by mutableStateOf(true)
    var reduceMotion by mutableStateOf(false)
    var largeText by mutableStateOf(false)
    var captionsOn by mutableStateOf(true)
    var arScale by mutableStateOf(1f)

    /** Hook set by the app to play sound effects. */
    var sfx: (Sfx) -> Unit = {}

    fun say(line: String) {
        lumiLine = line
        lumiLineId++
    }

    fun goTo(c: Chapter) {
        chapter = c
        if (c.ordinal > furthest) {
            furthest = c.ordinal
            prefs.edit().putInt("furthest", furthest).apply()
        }
        when (c) {
            Chapter.CITIES -> heartFormed = false
            Chapter.BOOK -> { pagesOpened = 0; book.turnedAt.fill(0L) }
            Chapter.CAKE -> candleLit = true
            Chapter.HUG -> ribbonWrapped = false
            else -> {}
        }
    }

    fun next() {
        val i = Chapter.universe.indexOf(chapter)
        if (i in 0 until Chapter.universe.lastIndex) goTo(Chapter.universe[i + 1])
    }

    fun replay() {
        runId++
        portalOpened = false
        keepsakeMode = false
        goTo(Chapter.PORTAL)
    }

    fun found(egg: String) {
        eggsFound = eggsFound + egg
    }

    fun tapLumi() {
        lumi.taps++
        if (lumi.taps == 7) {
            lumi.feel(Mood.Spin)
            say(Lines.lumiSecret)
            found("lumi")
            sfx(Sfx.SPARKLE)
        } else {
            lumi.feel(if (lumi.taps % 2 == 0) Mood.Laugh else Mood.Happy)
            say(Lines.lumiTaps[(lumi.taps - 1) % Lines.lumiTaps.size])
            sfx(Sfx.GIGGLE)
        }
    }

    fun openNextPage() {
        if (pagesOpened >= 7) return
        book.turnedAt[pagesOpened] = System.nanoTime()
        pagesOpened++
        sfx(Sfx.PAGE)
        sfx(Sfx.SPARKLE)
    }

    fun blowOut() {
        if (!candleLit) return
        candleLit = false
        celebrationKey++
        sfx(Sfx.BLOW)
        sfx(Sfx.CELEBRATE)
        lumi.feel(Mood.Spin)
        say(Lines.cakeDone)
    }

    fun celebrateAgain() {
        celebrationKey++
        sfx(Sfx.CELEBRATE)
        lumi.feel(Mood.Happy)
    }
}
