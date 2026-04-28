package com.creativem.fulltv.principal

import android.content.Intent
import android.content.res.Configuration
import android.os.Bundle
import android.text.Html
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.text.HtmlCompat
import androidx.leanback.widget.Presenter
import com.creativem.fulltv.R
import com.creativem.fulltv.peliculas.HeaderPresenter

class Nosotros : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.nosotros)

        // Pantalla completa inmersiva
        window.decorView.systemUiVisibility =
            View.SYSTEM_UI_FLAG_FULLSCREEN or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
        val container = findViewById<LinearLayout>(R.id.containerLayout)
        val orientation = resources.configuration.orientation

        if (orientation == Configuration.ORIENTATION_LANDSCAPE) {
            // Si la pantalla está horizontal (TV/Tablet), poner de lado
            container.orientation = LinearLayout.HORIZONTAL
        } else {
            // Si es móvil vertical, poner uno sobre otro
            container.orientation = LinearLayout.VERTICAL
        }
        // Asignar contenido HTML
        val col1: TextView = findViewById(R.id.col1)
        val col2: TextView = findViewById(R.id.col2)
        val col3: TextView = findViewById(R.id.col3)

        col1.text = HtmlCompat.fromHtml(getString(R.string.columna_1), HtmlCompat.FROM_HTML_MODE_LEGACY)
        col2.text = HtmlCompat.fromHtml(getString(R.string.columna_2), HtmlCompat.FROM_HTML_MODE_LEGACY)
        col3.text = HtmlCompat.fromHtml(getString(R.string.columna_3), HtmlCompat.FROM_HTML_MODE_LEGACY)

        setupHeader()
    }
    private fun setupHeader() {
        // 1. Buscamos el contenedor que ya está en el XML unificado
        val headerView = findViewById<View>(R.id.headerContainer)

        if (headerView != null) {
            val presenter = HeaderPresenter()

            // 2. En lugar de crear un ViewHolder nuevo inflándolo,
            // creamos uno usando la vista que ya encontró el findViewById
            val viewHolder = Presenter.ViewHolder(headerView)

            // 3. Ejecutamos el bind para que se llenen los datos (Firebase, Fecha, etc.)
            presenter.onBindViewHolder(viewHolder, null)

            Log.d("HEADER_LOG", "Header configurado correctamente sobre el XML unificado")
        } else {
            Log.e("HEADER_LOG", "No se encontró el id headerContainer en el layout")
        }
    }

}
