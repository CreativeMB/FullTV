package com.creativem.fulltv.principal

import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContentProviderCompat.requireContext
import androidx.core.text.HtmlCompat
import com.creativem.fulltv.R

class Nosotros : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.nosotros) // Asegúrate de tener este layout creado

        window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_FULLSCREEN or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
        val col1: TextView = findViewById(R.id.col1)
        val col2: TextView = findViewById(R.id.col2)
        val col3: TextView = findViewById(R.id.col3)

        col1.text = HtmlCompat.fromHtml(getString(R.string.columna_1), HtmlCompat.FROM_HTML_MODE_LEGACY)
        col2.text = HtmlCompat.fromHtml(getString(R.string.columna_2), HtmlCompat.FROM_HTML_MODE_LEGACY)
        col3.text = HtmlCompat.fromHtml(getString(R.string.columna_3), HtmlCompat.FROM_HTML_MODE_LEGACY)
    }
    override fun onResume() {
        super.onResume()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val granted = AudioFocusHelper.requestAudioFocus(this)
            if (granted) {
                // Aquí podrías reproducir audio si lo tuvieras
            }
        }
    }

    override fun onPause() {
        super.onPause()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            AudioFocusHelper.abandonAudioFocus()
        }
    }

}