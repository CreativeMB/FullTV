package com.creativem.fulltv.principal

import android.content.Context
import android.media.*
import android.os.Build
import androidx.annotation.RequiresApi

object AudioFocusHelper {
    private lateinit var audioManager: AudioManager
    private var focusRequest: AudioFocusRequest? = null

    @RequiresApi(Build.VERSION_CODES.O)
    fun requestAudioFocus(context: Context): Boolean {
        audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

        focusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MOVIE)
                    .build()
            )
            .setOnAudioFocusChangeListener { /* Puedes manejar cambios aquí si quieres */ }
            .setWillPauseWhenDucked(true)
            .build()

        val result = audioManager.requestAudioFocus(focusRequest!!)
        return result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
    }

    @RequiresApi(Build.VERSION_CODES.O)
    fun abandonAudioFocus() {
        if (AudioFocusHelper::audioManager.isInitialized && focusRequest != null) {
            audioManager.abandonAudioFocusRequest(focusRequest!!)
        }
    }
}
