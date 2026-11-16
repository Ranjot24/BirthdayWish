package com.eternelle.shiba

import android.app.ActivityManager
import android.app.ApplicationExitInfo
import android.content.Context
import android.os.Build
import java.io.File

/**
 * Records why the app closed so the next launch can show it (and Ranjot can copy it).
 * Kotlin/Java crashes are caught directly; native (3D engine) crashes are read from Android's
 * exit history on Android 11+. Nothing is sent anywhere.
 */
object CrashReporter {
    private fun file(ctx: Context) = File(ctx.filesDir, "last_crash.txt")

    fun install(ctx: Context) {
        val app = ctx.applicationContext
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, e ->
            try {
                file(app).writeText("v${BuildInfo.VERSION} · ${Build.MANUFACTURER} ${Build.MODEL} · Android ${Build.VERSION.RELEASE}\n" +
                    "Chapter: ${lastChapter}\n" + android.util.Log.getStackTraceString(e).take(6000))
            } catch (_: Exception) {}
            previous?.uncaughtException(thread, e)
        }
    }

    @Volatile var lastChapter: String = "?"

    /** Returns a report about the previous crash, if any, and clears it. */
    fun consume(ctx: Context): String? {
        val prefs = ctx.getSharedPreferences("eternelle", Context.MODE_PRIVATE)
        val f = file(ctx)
        if (f.exists()) {
            val text = f.readText(); f.delete()
            return text
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            try {
                val am = ctx.getSystemService(ActivityManager::class.java)
                val info = am.getHistoricalProcessExitReasons(ctx.packageName, 0, 1).firstOrNull() ?: return null
                val seen = prefs.getLong("lastExitSeen", 0L)
                if (info.timestamp <= seen) return null
                prefs.edit().putLong("lastExitSeen", info.timestamp).apply()
                if (info.reason != ApplicationExitInfo.REASON_CRASH_NATIVE && info.reason != ApplicationExitInfo.REASON_CRASH &&
                    info.reason != ApplicationExitInfo.REASON_ANR) return null
                val kind = when (info.reason) {
                    ApplicationExitInfo.REASON_CRASH_NATIVE -> "native crash"
                    ApplicationExitInfo.REASON_ANR -> "app not responding"
                    else -> "crash"
                }
                val trace = try {
                    info.traceInputStream?.use { input ->
                        val bytes = input.readBytes()
                        // pull readable text (abort message, function names) out of the tombstone
                        val sb = StringBuilder(); val run = StringBuilder()
                        for (b in bytes) {
                            val c = b.toInt() and 0xFF
                            if (c in 32..126) run.append(c.toChar()) else {
                                if (run.length >= 6) sb.append(run).append('\n')
                                run.setLength(0)
                            }
                            if (sb.length > 7000) break
                        }
                        sb.toString()
                    }
                } catch (_: Exception) { null }
                return "v${BuildInfo.VERSION} · ${Build.MANUFACTURER} ${Build.MODEL} · Android ${Build.VERSION.RELEASE}\n" +
                    "Last chapter: ${prefs.getString("lastChapter", "?")}\n" +
                    "Exit: $kind — ${info.description ?: ""}\n" + (trace ?: "(no trace available)").take(6000)
            } catch (_: Exception) {}
        }
        return null
    }
}

object BuildInfo { const val VERSION = "2.2" }
