package com.creativem.fulltv.tv
import android.os.Bundle
import androidx.fragment.app.FragmentActivity
import com.creativem.fulltv.R

class TvActivity : FragmentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_tv) // Asegúrate de que existe este XML

        // Cargar el fragmento en el contenedor si no está agregado
        if (savedInstanceState == null) {
            supportFragmentManager.beginTransaction()
                .replace(R.id.fragment_container, TvFragment()) // Usa el ID correcto
                .commit()
        }
    }
}