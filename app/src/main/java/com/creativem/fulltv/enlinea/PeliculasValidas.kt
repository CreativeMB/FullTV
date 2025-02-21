package com.creativem.fulltv.enlinea

import android.os.Bundle
import androidx.fragment.app.FragmentActivity
import com.creativem.fulltv.R


class PeliculasValidas : FragmentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.peliculas_validas) // Asegúrate de que existe este XML

        // Cargar el fragmento en el contenedor si no está agregado
        if (savedInstanceState == null) {
            supportFragmentManager.beginTransaction()
                .replace(R.id.peliculasvalidas, PeliculasFragment()) // Usa el ID correcto
                .commit()
        }
    }
}