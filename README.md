# ETERNELLE — The Universe That Knows Her

A personalised native Android AR birthday experience for **Shiba Sis** (4 December 2026).

## Get the app
Download **ETERNELLE.apk** from the **Releases** section (tag `latest`) and install it on any Android 7+ phone.
Every push to `main` rebuilds the app automatically (Actions → *Build APK*) and replaces that download.

## The story
1. Invitation → Lumi loading screen
2. AR portal — a golden star placed on a real floor/table grows into a portal
3. Meet Lumi — tap her (7 taps hides a secret)
4. Two cities, one home — Jamshedpur & Kolkata joined by a ribbon that becomes a heart
5. The Blessing Book — seven pages, seven animated blessings
6. Birthday cake — blow into the mic (or tap) to put out the candle
7. The invisible hug — a Punjabi message spoken by the phone's text-to-speech voice
8. Final gift — letter, keepsake mode, save a greeting image, replay

Phones without ARCore get the same story in an interactive 3D view.

## Tech
Kotlin · Jetpack Compose · ARCore · SceneView 4.18 (Filament). All 3D is generated in code from
SceneView geometry; music and sound effects are original and synthesised. No accounts, no network,
and microphone audio never leaves the phone.

Edit text in `app/src/main/java/com/eternelle/shiba/Story.kt` (lines, blessings, Punjabi message)
and `UI.kt` (the letter).
