package com.creativem.fulltv.api
import android.os.Bundle
import androidx.fragment.app.FragmentActivity

class PeliculasApi : FragmentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Cargar Fragmento que usa el CardPresenter
        val fragment = PeliculasApiFragment()
        supportFragmentManager.beginTransaction()
            .replace(android.R.id.content, fragment)
            .commit()
    }
}