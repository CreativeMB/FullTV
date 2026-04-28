package com.creativem.fulltv.principal

import android.content.res.Configuration
import android.os.Bundle
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.text.HtmlCompat
import androidx.leanback.widget.Presenter
import com.creativem.fulltv.R

/**
 * Activity that displays information about the application ("Nosotros" / "About Us").
 * It adjusts its layout dynamically based on the device orientation (TV/Landscape or Mobile/Portrait).
 */
class Nosotros : AppCompatActivity() {

    /**
     * Initializes the activity, sets the content view, and configures the layout
     * based on the current screen orientation.
     *
     * @param savedInstanceState If the activity is being re-initialized after
     * previously being shut down then this Bundle contains the data it most
     * recently supplied in [onSaveInstanceState].
     */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.nosotros)

        // Pantalla completa inmersiva
        window.decorView.systemUiVisibility =
            View.SYSTEM_UI_FLAG_FULLSCREEN or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY

        val container = findViewById<LinearLayout>(R.id.containerLayout)
        val card1 = findViewById<View>(R.id.cardSoporte)
        val card2 = findViewById<View>(R.id.cardColumna2)
        val card3 = findViewById<View>(R.id.cardNotificacion)

        val orientation = resources.configuration.orientation

        if (orientation == Configuration.ORIENTATION_LANDSCAPE) {
            // MODO TV / HORIZONTAL
            container.orientation = LinearLayout.HORIZONTAL

            // Definimos que cada tarjeta ocupe 0dp de ancho pero con peso 1 (reparto equitativo)
            val params = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f)
            params.setMargins(10, 10, 10, 10)

            card1.layoutParams = params
            card2.layoutParams = params
            card3.layoutParams = params
        } else {
            // MODO MÓVIL / VERTICAL
            container.orientation = LinearLayout.VERTICAL

            // En vertical cada una ocupa todo el ancho
            val params = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            params.setMargins(0, 10, 0, 10)

            card1.layoutParams = params
            card2.layoutParams = params
            card3.layoutParams = params
        }

        // Asignar contenido HTML
        val col1: TextView = findViewById(R.id.col1)
        val col2: TextView = findViewById(R.id.col2)
        val col3: TextView = findViewById(R.id.col3)

        // Usamos try-catch o comprobación por si los strings no existen aún
        col1.text = HtmlCompat.fromHtml(getString(R.string.columna_1), HtmlCompat.FROM_HTML_MODE_LEGACY)
        col2.text = HtmlCompat.fromHtml(getString(R.string.columna_2), HtmlCompat.FROM_HTML_MODE_LEGACY)
        col3.text = HtmlCompat.fromHtml(getString(R.string.columna_3), HtmlCompat.FROM_HTML_MODE_LEGACY)

        setupHeader()
    }

    /**
     * Sets up the header of the activity by binding a [HeaderPresenter] to the
     * header container view if it exists.
     */
    private fun setupHeader() {
        val headerView = findViewById<View>(R.id.headerContainer)
        if (headerView != null) {
            val presenter = HeaderPresenter()
            val viewHolder = Presenter.ViewHolder(headerView)
            presenter.onBindViewHolder(viewHolder, null)
            Log.d("HEADER_LOG", "Header vinculado con éxito")
        }
    }
}