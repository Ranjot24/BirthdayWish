package com.eternelle.shiba

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.google.ar.core.ArCoreApk
import kotlinx.coroutines.delay

private val Serif = FontFamily.Serif

@Composable
fun EternelleApp(audio: AudioDirector) {
    val context = LocalContext.current
    val story = remember { Story(context.applicationContext) }
    SideEffect { story.sfx = { audio.play(it) } }
    LaunchedEffect(story.soundOn, story.musicOn, story.reduceMotion) {
        audio.sfxOn = story.soundOn
        audio.musicOn = story.musicOn
        Motion.reduced = story.reduceMotion
    }

    // Honest device check: AR only where ARCore says the phone is supported.
    var arSupported by remember { mutableStateOf<Boolean?>(null) }
    LaunchedEffect(Unit) {
        repeat(12) {
            val a = try { ArCoreApk.getInstance().checkAvailability(context) } catch (_: Throwable) { null }
            if (a == null) { arSupported = false; return@LaunchedEffect }
            if (!a.isTransient) { arSupported = a.isSupported; return@LaunchedEffect }
            delay(250)
        }
        arSupported = false
    }
    var arMode by remember { mutableStateOf(false) }
    var modeNote by remember { mutableStateOf<String?>(null) }
    val cameraPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        arMode = granted
        if (!granted) modeNote = "Without the camera, your universe opens in 3D view instead."
        story.goTo(Chapter.PORTAL)
    }

    val baseDensity = LocalDensity.current
    val density = if (story.largeText) Density(baseDensity.density, baseDensity.fontScale * 1.25f) else baseDensity
    CompositionLocalProvider(LocalDensity provides density) {
        Box(Modifier.fillMaxSize().background(Midnight)) {
            when (story.chapter) {
                Chapter.INVITATION -> InvitationScreen(story) {
                    audio.startMusic()
                    story.sfx(Sfx.SPARKLE)
                    story.goTo(Chapter.LOADING)
                }
                Chapter.LOADING -> LoadingScreen(story, audio, arSupported) {
                    if (arSupported == true) {
                        val has = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
                        if (has) { arMode = true; story.goTo(Chapter.PORTAL) } else cameraPermission.launch(Manifest.permission.CAMERA)
                    } else {
                        arMode = false
                        modeNote = "This phone doesn't support Google ARCore, so your universe opens in 3D view. Drag to look around!"
                        story.goTo(Chapter.PORTAL)
                    }
                }
                else -> {
                    if (arMode) {
                        ArStage(story) { msg ->
                            arMode = false
                            story.placed = false
                            modeNote = "AR couldn't start ($msg). Showing your universe in 3D view instead."
                        }
                    } else {
                        StarryBackdrop()
                        FallbackStage(story)
                    }
                    Overlay(story, audio, arMode, modeNote, onDismissNote = { modeNote = null }, onUse3D = {
                        arMode = false
                        story.placed = false
                        modeNote = "Switched to 3D view. Drag to look around, pinch to zoom."
                    })
                }
            }
        }
    }
}

// ───────────────────────────── 1. Invitation ─────────────────────────────

@Composable
fun InvitationScreen(story: Story, onBegin: () -> Unit) {
    var egg by remember { mutableStateOf(false) }
    Box(Modifier.fillMaxSize()) {
        StarryBackdrop()
        Column(
            Modifier.fillMaxSize().safeDrawingPadding().padding(horizontal = 28.dp).verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Spacer(Modifier.height(24.dp))
            Text("A ONE-OF-ONE BIRTHDAY UNIVERSE", color = Gold, fontSize = 11.sp, letterSpacing = 2.4.sp)
            Spacer(Modifier.height(10.dp))
            Lumi2D(Modifier.size(170.dp), excited = egg, reduceMotion = story.reduceMotion)
            Text("ETERNELLE", color = Pearl, fontSize = 18.sp, letterSpacing = 8.sp, fontWeight = FontWeight.Light,
                modifier = Modifier.semantics { heading() })
            Spacer(Modifier.height(6.dp))
            Text("Shiba Sis.", color = Rose, fontSize = 56.sp, fontFamily = Serif, fontStyle = FontStyle.Italic,
                modifier = Modifier.pointerInput(Unit) {
                    detectTapGestures(onLongPress = {
                        egg = true
                        story.found("invite")
                        story.sfx(Sfx.GIGGLE)
                    })
                })
            Spacer(Modifier.height(18.dp))
            Text(
                "You're invited to a tiny universe that has been waiting all year to meet you.\n\n" +
                    "It knows your laugh, your kindness, and the way you make every place feel like home… " +
                    "and it knows that today is all about YOU.",
                color = Pearl.copy(alpha = 0.92f), fontSize = 16.sp, lineHeight = 25.sp, textAlign = TextAlign.Center,
            )
            AnimatedVisibility(egg) {
                Text(Lines.inviteEgg, color = Gold, fontSize = 13.sp, textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 12.dp))
            }
            Spacer(Modifier.height(30.dp))
            PillButton("Begin My Universe ✦", primary = true, onClick = onBegin)
            Spacer(Modifier.height(18.dp))
            Text("4 DECEMBER 2026  ·  FROM JAMSHEDPUR, WITH LOVE", color = Lavender, fontSize = 10.sp, letterSpacing = 1.4.sp,
                textAlign = TextAlign.Center)
            Spacer(Modifier.height(10.dp))
            Text("Best with sound on 🔊", color = Color.White.copy(alpha = 0.55f), fontSize = 11.sp)
            Spacer(Modifier.height(24.dp))
        }
    }
}

// ───────────────────────────── Loading with Lumi ─────────────────────────────

@Composable
fun LoadingScreen(story: Story, audio: AudioDirector, arSupported: Boolean?, onReady: () -> Unit) {
    val messages = listOf("Lumi is polishing the stars…", "Untangling the golden ribbon…", "Fluffing the flowers…",
        "Lighting a very special candle…", "Almost there, birthday queen…")
    var progress by remember { mutableFloatStateOf(0f) }
    var msg by remember { mutableStateOf(messages[0]) }
    val arKnown by rememberUpdatedState(arSupported)
    val ready by rememberUpdatedState(onReady)
    LaunchedEffect(Unit) {
        val start = System.currentTimeMillis()
        var i = 0
        while (true) {
            val elapsed = System.currentTimeMillis() - start
            val assets = audio.loadedCount.toFloat() / audio.total
            val time = (elapsed / 3200f).coerceAtMost(1f)
            progress = minOf(assets, time) * (if (arKnown == null) 0.9f else 1f)
            msg = messages[(elapsed / 900).toInt().coerceAtMost(messages.lastIndex)]
            if (progress >= 1f || elapsed > 9000) break
            delay(80)
            i++
        }
        progress = 1f
        delay(350)
        ready()
    }
    Box(Modifier.fillMaxSize()) {
        StarryBackdrop()
        Column(Modifier.fillMaxSize().safeDrawingPadding().padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Lumi2D(Modifier.size(200.dp), excited = true, reduceMotion = story.reduceMotion)
            Spacer(Modifier.height(18.dp))
            Text(msg, color = Pearl, fontSize = 16.sp, fontFamily = Serif, fontStyle = FontStyle.Italic, textAlign = TextAlign.Center)
            Spacer(Modifier.height(16.dp))
            LinearProgressIndicator(progress = { progress }, color = Gold, trackColor = Color.White.copy(alpha = 0.15f),
                modifier = Modifier.width(220.dp).height(6.dp).clip(RoundedCornerShape(3.dp)))
        }
    }
}

// ───────────────────────────── Story overlay ─────────────────────────────

@Composable
fun Overlay(
    story: Story,
    audio: AudioDirector,
    arMode: Boolean,
    modeNote: String?,
    onDismissNote: () -> Unit,
    onUse3D: () -> Unit,
) {
    var settingsOpen by remember { mutableStateOf(false) }
    Box(Modifier.fillMaxSize().safeDrawingPadding()) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp)) {
            TopBar(story, arMode, onSettings = { settingsOpen = !settingsOpen })
            if (modeNote != null) {
                Spacer(Modifier.height(8.dp))
                Surface(color = Midnight.copy(alpha = 0.85f), shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.clickable { onDismissNote() }) {
                    Text("$modeNote  (tap to close)", color = Pearl, fontSize = 12.sp, modifier = Modifier.padding(12.dp))
                }
            }
            Spacer(Modifier.height(8.dp))
            SpeechBubble(story)
        }
        if (!story.keepsakeMode || story.chapter != Chapter.FINAL) {
            Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(12.dp)) {
                ChapterCard(story, audio, arMode, onUse3D)
            }
        } else {
            Row(Modifier.align(Alignment.BottomCenter).padding(16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                SaveGreetingButton(story)
                PillButton("Exit keepsake", primary = false) { story.keepsakeMode = false }
            }
        }
        if (settingsOpen) SettingsPanel(story, Modifier.align(Alignment.TopEnd).padding(top = 56.dp, end = 12.dp)) { settingsOpen = false }
    }
}

@Composable
fun TopBar(story: Story, arMode: Boolean, onSettings: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.weight(1f).horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Chapter.universe.forEachIndexed { i, c ->
                val reached = c.ordinal <= story.furthest && story.placed
                val current = c == story.chapter
                Text(
                    "${i + 1} ${c.title}",
                    color = if (current) Midnight else if (reached) Pearl else Pearl.copy(alpha = 0.35f),
                    fontSize = 11.sp, fontWeight = if (current) FontWeight.Bold else FontWeight.Normal,
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(if (current) Gold else Midnight.copy(alpha = 0.6f))
                        .clickable(enabled = reached && !current) { if (c == Chapter.PORTAL) story.replay() else story.goTo(c) }
                        .padding(horizontal = 10.dp, vertical = 7.dp)
                        .semantics { contentDescription = "Chapter ${i + 1}, ${c.title}" + if (reached) "" else ", not reached yet" }
                )
            }
        }
        if (arMode && story.placed) {
            RoundIcon("−", "Make universe smaller") { story.arScale = (story.arScale - 0.2f).coerceAtLeast(0.6f) }
            RoundIcon("+", "Make universe bigger") { story.arScale = (story.arScale + 0.2f).coerceAtMost(2.4f) }
        }
        RoundIcon("Aa", "Settings and accessibility", onClick = onSettings)
    }
}

@Composable
fun RoundIcon(label: String, description: String, onClick: () -> Unit) {
    Box(
        Modifier.padding(start = 6.dp).size(44.dp).clip(CircleShape).background(Midnight.copy(alpha = 0.75f))
            .border(1.dp, Gold.copy(alpha = 0.5f), CircleShape)
            .clickable(onClick = onClick).semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) { Text(label, color = Gold, fontSize = 15.sp, fontWeight = FontWeight.Bold) }
}

@Composable
fun SpeechBubble(story: Story) {
    val line = story.lumiLine
    LaunchedEffect(story.lumiLineId) {
        delay(5200)
        story.lumiLine = null
    }
    AnimatedVisibility(line != null, enter = fadeIn() + slideInVertically(), exit = fadeOut()) {
        Row(verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(Pearl.copy(alpha = 0.94f)).padding(10.dp)) {
            Lumi2D(Modifier.size(42.dp), excited = true, reduceMotion = story.reduceMotion)
            Spacer(Modifier.width(8.dp))
            Column {
                Text("LUMI", color = Plum, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp)
                Text(line ?: "", color = Midnight, fontSize = 15.sp, lineHeight = 20.sp)
            }
        }
    }
}

@Composable
fun SettingsPanel(story: Story, modifier: Modifier, onClose: () -> Unit) {
    Surface(color = Midnight.copy(alpha = 0.96f), shape = RoundedCornerShape(18.dp), modifier = modifier.widthIn(max = 300.dp)) {
        Column(Modifier.padding(16.dp)) {
            Text("Comfort settings", color = Gold, fontSize = 15.sp, fontWeight = FontWeight.Bold, modifier = Modifier.semantics { heading() })
            Spacer(Modifier.height(8.dp))
            ToggleRow("Sound effects", story.soundOn) { story.soundOn = it }
            ToggleRow("Music", story.musicOn) { story.musicOn = it }
            ToggleRow("Reduce motion", story.reduceMotion) { story.reduceMotion = it }
            ToggleRow("Bigger text", story.largeText) { story.largeText = it }
            Spacer(Modifier.height(6.dp))
            Text("Every 3D tap also has a button below, so nothing needs precise tapping.",
                color = Pearl.copy(alpha = 0.7f), fontSize = 11.sp)
            Spacer(Modifier.height(8.dp))
            PillButton("Done", primary = true, onClick = onClose)
        }
    }
}

@Composable
fun ToggleRow(label: String, value: Boolean, onChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
        Text(label, color = Pearl, fontSize = 14.sp, modifier = Modifier.weight(1f))
        Switch(checked = value, onCheckedChange = onChange,
            colors = SwitchDefaults.colors(checkedThumbColor = Midnight, checkedTrackColor = Gold))
    }
}

@Composable
fun PillButton(label: String, primary: Boolean, enabled: Boolean = true, onClick: () -> Unit) {
    if (primary) {
        Button(onClick = onClick, enabled = enabled, shape = RoundedCornerShape(50),
            colors = ButtonDefaults.buttonColors(containerColor = Rose, contentColor = Midnight,
                disabledContainerColor = Rose.copy(alpha = 0.3f), disabledContentColor = Midnight.copy(alpha = 0.5f)),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp)) {
            Text(label, fontSize = 14.sp, fontWeight = FontWeight.Bold)
        }
    } else {
        OutlinedButton(onClick = onClick, enabled = enabled, shape = RoundedCornerShape(50),
            colors = ButtonDefaults.outlinedButtonColors(containerColor = Midnight.copy(alpha = 0.7f), contentColor = Pearl),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp)) {
            Text(label, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
fun CardShell(content: @Composable ColumnScope.() -> Unit) {
    Surface(color = Midnight.copy(alpha = 0.86f), shape = RoundedCornerShape(24.dp), tonalElevation = 6.dp,
        modifier = Modifier.fillMaxWidth().border(1.dp, Gold.copy(alpha = 0.25f), RoundedCornerShape(24.dp))) {
        Column(Modifier.padding(horizontal = 18.dp, vertical = 16.dp), horizontalAlignment = Alignment.CenterHorizontally, content = content)
    }
}

@Composable
fun CardTitle(kicker: String, title: String) {
    Text(kicker, color = Gold, fontSize = 10.sp, letterSpacing = 2.sp, fontWeight = FontWeight.Bold)
    Spacer(Modifier.height(4.dp))
    Text(title, color = Pearl, fontSize = 20.sp, fontFamily = Serif, fontStyle = FontStyle.Italic, textAlign = TextAlign.Center,
        modifier = Modifier.semantics { heading() })
}

@Composable
fun Body(text: String) {
    Spacer(Modifier.height(6.dp))
    Text(text, color = Pearl.copy(alpha = 0.88f), fontSize = 14.sp, lineHeight = 20.sp, textAlign = TextAlign.Center)
}

@Composable
fun Buttons(content: @Composable () -> Unit) {
    Spacer(Modifier.height(12.dp))
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically) { content() }
}

@Composable
fun ChapterCard(story: Story, audio: AudioDirector, arMode: Boolean, onUse3D: () -> Unit) {
    val context = LocalContext.current
    when (story.chapter) {
        Chapter.PORTAL -> CardShell {
            if (!story.placed) {
                var waited by remember { mutableStateOf(false) }
                LaunchedEffect(Unit) { delay(25_000); waited = true }
                CardTitle("CHAPTER 1 · THE PORTAL", "Find a little space for magic")
                Body(story.arHint.ifEmpty { "Slowly move your phone and point it at the floor or a table." })
                Buttons {
                    PillButton("Place my star here ✦", primary = true, enabled = story.planeFound) {
                        if (story.placeCenter?.invoke() != true) story.arHint = "Aim the centre of the screen at the sparkly dots, then try again."
                    }
                    if (waited) PillButton("Use 3D view instead", primary = false, onClick = onUse3D)
                }
            } else if (!story.portalOpened) {
                CardTitle("CHAPTER 1 · THE PORTAL", "A tiny golden star is waking up…")
                Body("Watch closely.")
            } else {
                CardTitle("CHAPTER 1 · THE PORTAL", "The portal is open")
                Body("Something small, sparkly and very excited is about to come through.")
                Buttons { PillButton("Who's there? ✦", primary = true) { story.goTo(Chapter.LUMI) } }
            }
        }
        Chapter.LUMI -> CardShell {
            LaunchedEffect(Unit) {
                story.sfx(Sfx.SPARKLE)
                for (l in Lines.lumiIntro) { story.say(l); story.lumi.feel(Mood.Happy); delay(3000) }
            }
            CardTitle("CHAPTER 2 · MEET LUMI", "A tiny celestial friend")
            Body(if (arMode) "Tap Lumi to say hi — walk around her too!" else "Tap Lumi to say hi. Drag to look around her.")
            Buttons {
                PillButton("Say hi 👋", primary = false) { story.tapLumi() }
                PillButton("I'm ready ✦", primary = true) { story.next() }
            }
        }
        Chapter.CITIES -> CardShell {
            CardTitle("CHAPTER 3 · TWO CITIES, ONE HOME", if (story.heartFormed) "" else "A golden ribbon between two cities")
            if (story.heartFormed) {
                Text("Distance can change the address.\nNever the bond.", color = Rose, fontSize = 21.sp, lineHeight = 29.sp,
                    fontFamily = Serif, fontStyle = FontStyle.Italic, textAlign = TextAlign.Center)
            } else Body("Watch the ribbon travel from Jamshedpur to Kolkata…")
            Buttons {
                PillButton("Jamshedpur 🔔", primary = false) { story.sfx(Sfx.BELL_LEFT); story.say(Lines.jamshedpur); story.lumi.feel(Mood.Happy) }
                PillButton("Kolkata 🔔", primary = false) { story.sfx(Sfx.BELL_RIGHT); story.say(Lines.kolkata); story.lumi.feel(Mood.Happy) }
                PillButton("Next ✦", primary = true, enabled = story.heartFormed) { story.next() }
            }
        }
        Chapter.BOOK -> CardShell {
            val n = story.pagesOpened
            if (n == 0) {
                CardTitle("CHAPTER 4 · THE BLESSING BOOK", "Seven pages, seven blessings")
                Body("Touch the top page of the floating book to open it.")
            } else {
                val b = Blessings[n - 1]
                CardTitle("BLESSING $n OF 7", b.title)
                Body(b.line)
                if (n == 6) Body("Psst… try tapping the moon.")
            }
            Buttons {
                if (n < 7) PillButton(if (n == 0) "Open the book ✦" else "Next page ✦", primary = n < 7) { story.openNextPage() }
                if (n == 7) PillButton("One more surprise ✦", primary = true) { story.next() }
            }
        }
        Chapter.CAKE -> CakeCard(story)
        Chapter.HUG -> CardShell {
            LaunchedEffect(Unit) {
                delay(1800)
                story.voicePlaying = true
                audio.playVoice { story.voicePlaying = false }
            }
            CardTitle("CHAPTER 6 · THE INVISIBLE HUG", if (story.ribbonWrapped) "" else "Listen…")
            if (story.ribbonWrapped) {
                Text("Your veer is always with you.", color = Rose, fontSize = 23.sp, fontFamily = Serif,
                    fontStyle = FontStyle.Italic, textAlign = TextAlign.Center)
            } else Body("A golden ribbon is wrapping a hug around your heart.")
            if (VOICE_IS_PLACEHOLDER) Body("(Voice message coming soon — this is a placeholder melody.)")
            Buttons {
                if (story.voicePlaying) PillButton("■ Pause message", primary = false) { audio.stopVoice(); story.voicePlaying = false }
                else PillButton("▶ Play message", primary = false) { story.voicePlaying = true; audio.playVoice { story.voicePlaying = false } }
                PillButton("Open my gift ✦", primary = true) { audio.stopVoice(); story.voicePlaying = false; story.next() }
            }
        }
        Chapter.FINAL -> FinalCard(story)
        else -> {}
    }
}

@Composable
fun CakeCard(story: Story) {
    val context = LocalContext.current
    var listening by remember { mutableStateOf(false) }
    var level by remember { mutableFloatStateOf(0f) }
    val micPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        if (ok) listening = true
        else Toast.makeText(context, "No problem — just tap the flame or the button!", Toast.LENGTH_SHORT).show()
    }
    if (listening && story.candleLit) {
        LaunchedEffect(Unit) {
            listenForBlow(onLevel = { level = it }, onBlow = { story.blowOut() })
            listening = false
        }
    }
    CardShell {
        CardTitle("CHAPTER 5 · THE BIRTHDAY CELEBRATION", if (story.candleLit) "Make a wish…" else "Your wish is on its way ✦")
        if (story.candleLit) {
            Body("Then blow gently towards your phone's microphone, tap the flame, or use the button.")
            if (listening) {
                Spacer(Modifier.height(10.dp))
                Text("Listening… blow now! (sound stays on your phone)", color = Gold, fontSize = 12.sp)
                Spacer(Modifier.height(6.dp))
                LinearProgressIndicator(progress = { level.coerceIn(0f, 1f) }, color = Rose, trackColor = Color.White.copy(alpha = 0.15f),
                    modifier = Modifier.width(200.dp).height(6.dp).clip(RoundedCornerShape(3.dp)))
            }
            Buttons {
                PillButton(if (listening) "Stop listening" else "🎤 Blow with mic", primary = false) {
                    if (listening) listening = false
                    else if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) listening = true
                    else micPermission.launch(Manifest.permission.RECORD_AUDIO)
                }
                PillButton("Blow out the candle", primary = true) { story.blowOut() }
            }
        } else {
            Body("Happy Birthday, Shiba Sis! 🎂")
            Buttons {
                PillButton("Celebrate again 🎉", primary = false) { story.celebrateAgain() }
                PillButton("Next ✦", primary = true) { story.next() }
            }
        }
    }
}

@Composable
fun SaveGreetingButton(story: Story) {
    val context = LocalContext.current
    fun save() {
        val ok = saveGreeting(context, drawGreeting(story.eggsFound.size))
        Toast.makeText(context, if (ok) "Saved to Pictures/ETERNELLE ✦" else "Couldn't save the image, sorry!", Toast.LENGTH_LONG).show()
        if (ok) story.sfx(Sfx.SPARKLE)
    }
    val storagePermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        if (ok) save() else Toast.makeText(context, "Storage permission is needed to save on this Android version.", Toast.LENGTH_LONG).show()
    }
    PillButton("Save birthday greeting 🖼", primary = true) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED
        ) storagePermission.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE) else save()
    }
}

@Composable
fun FinalCard(story: Story) {
    CardShell {
        CardTitle("CHAPTER 7 · YOUR GIFT", "A letter for you")
        Spacer(Modifier.height(8.dp))
        Box(Modifier.fillMaxWidth().heightIn(max = 230.dp).clip(RoundedCornerShape(16.dp))
            .background(Brush.verticalGradient(listOf(Color(0xFFFFF8F0), Color(0xFFF7E7F0))))) {
            Text(
                Letter,
                color = Color(0xFF3A1A45), fontSize = 15.sp, lineHeight = 23.sp, fontFamily = Serif,
                modifier = Modifier.verticalScroll(rememberScrollState()).padding(16.dp),
            )
        }
        Spacer(Modifier.height(6.dp))
        Text("Secrets found: ${story.eggsFound.size} of 4", color = Lavender, fontSize = 11.sp)
        Buttons {
            SaveGreetingButton(story)
            PillButton("Keepsake mode", primary = false) { story.keepsakeMode = true }
            PillButton("Replay ✦", primary = false) { story.replay() }
        }
    }
}

const val Letter = """My dearest Shiba,

Happy birthday!

I couldn't be there to hand you a gift, so I built you a whole universe instead. Lumi has strict instructions to look after you until I can give you a proper hug myself.

Kolkata may be where your days are unfolding now, but Jamshedpur will always hold a piece of home for you, and so will I. Distance can change the address. It can never change us.

I hope this year is full of laughter, chai with good people, brave adventures, quiet peace, and every dream you've been whispering about.

Whatever happens, wherever you are, remember this: tera veer hamesha tere naal hai.

With all my love,
Your veer"""
