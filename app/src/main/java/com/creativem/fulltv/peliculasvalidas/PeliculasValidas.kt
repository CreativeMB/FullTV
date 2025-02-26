package com.creativem.fulltv.peliculasvalidas

import android.os.Bundle
import android.view.WindowManager
import androidx.core.view.WindowCompat
import androidx.fragment.app.FragmentActivity
import com.creativem.fulltv.R


class PeliculasValidas : FragmentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.fragment_peliculas_validas) // Asegúrate de que existe este XML

        // Cargar el fragmento en el contenedor si no está agregado
        if (savedInstanceState == null) {
            supportFragmentManager.beginTransaction()
                .replace(R.id.peliculasvalidas, PeliculasValidasFragment()) // Usa el ID correcto
                .commit()
        }
        // Mantener la pantalla encendida
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.setFlags(
            WindowManager.LayoutParams.FLAG_FULLSCREEN,
            WindowManager.LayoutParams.FLAG_FULLSCREEN
        )
    }
}