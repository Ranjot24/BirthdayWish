package com.eternelle.shiba

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent

class MainActivity : ComponentActivity() {
    private lateinit var audio: AudioDirector

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        @Suppress("DEPRECATION")
        window.statusBarColor = android.graphics.Color.rgb(23, 17, 42)
        @Suppress("DEPRECATION")
        window.navigationBarColor = android.graphics.Color.rgb(23, 17, 42)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        audio = AudioDirector(applicationContext)
        setContent { EternelleApp(audio) }
    }

    override fun onResume() {
        super.onResume()
        audio.onForeground()
    }

    override fun onPause() {
        audio.onBackground()
        super.onPause()
    }

    override fun onDestroy() {
        audio.release()
        super.onDestroy()
    }
}
