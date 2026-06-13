package com.creativem.fulltv

import android.graphics.Color
import android.os.Bundle
import android.util.Log
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.coroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class LivePlayerActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 1. Configuración de la vista principal
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.BLACK)
        }

        // 2. Banner de cabecera
        val banner = TextView(this).apply {
            text = "CineParche: Programación"
            setTextColor(Color.parseColor("#C5A059"))
            textSize = 18f // Reduje un poco el tamaño para que sea más elegante
            gravity = Gravity.CENTER

            // Original: 0, 40, 0, 20 (Superior, Izquierda, Inferior, Derecha)
            // Nuevo: 0, 10, 0, 5 (Mucho más ajustado)
            setPadding(0, 10, 0, 5)

            setTypeface(null, android.graphics.Typeface.BOLD)
        }
        container.addView(banner)

        // 3. Área de lista de partidos
        val scrollView = ScrollView(this)
        val listaPartidos = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }
        scrollView.addView(listaPartidos)
        container.addView(scrollView)

        setContentView(container)

        // 4. Iniciar carga de datos
        cargarResultadosMundial(listaPartidos)
    }

    private fun cargarResultadosMundial(contenedor: LinearLayout) {
        val service = retrofit2.Retrofit.Builder()
            .baseUrl("https://api.football-data.org/v4/")
            .addConverterFactory(retrofit2.converter.gson.GsonConverterFactory.create())
            .build().create(FootballApiService::class.java)

        lifecycle.coroutineScope.launch(Dispatchers.IO) {
            try {
                val response = service.getWorldCupMatches()

                withContext(Dispatchers.Main) {
                    contenedor.removeAllViews()

                    if (response.isSuccessful && response.body() != null) {
                        val matches = response.body()!!.matches.sortedBy { it.utcDate }
                        val inflater = android.view.LayoutInflater.from(this@LivePlayerActivity)
                        val imageLoader = coil.ImageLoader.Builder(this@LivePlayerActivity)
                            .components { add(coil.decode.SvgDecoder.Factory()) }
                            .build()

                        matches.forEach { match ->
                            val view = inflater.inflate(R.layout.item_partido, contenedor, false)

                            val txtHome = view.findViewById<TextView>(R.id.txtHome)
                            val txtAway = view.findViewById<TextView>(R.id.txtAway)
                            val txtScore = view.findViewById<TextView>(R.id.txtScore)
                            val imgHome = view.findViewById<android.widget.ImageView>(R.id.imgHome)
                            val imgAway = view.findViewById<android.widget.ImageView>(R.id.imgAway)

                            txtHome.text = match.homeTeam.name
                            txtAway.text = match.awayTeam.name

                            val homeScore = match.score.fullTime?.home ?: 0
                            val awayScore = match.score.fullTime?.away ?: 0

                            when (match.status) {
                                "FINISHED" -> {
                                    txtScore.text = "FINAL: $homeScore - $awayScore"
                                    txtScore.setTextColor(Color.parseColor("#C5A059"))
                                }
                                "IN_PLAY", "PAUSED" -> {
                                    txtScore.text = "🔴 TRANSMITIENDO AHORA: $homeScore - $awayScore"
                                    txtScore.setTextColor(Color.RED)
                                }
                                "TIMED", "SCHEDULED" -> {
                                    if (match.utcDate.length >= 16) {
                                        val horaUTC = match.utcDate.substring(11, 13).toIntOrNull() ?: 0
                                        val min = match.utcDate.substring(14, 16).toIntOrNull() ?: 0
                                        val horaLocal = (horaUTC - 5 + 24) % 24
                                        val horaAmPm = formatoAmPm(horaLocal, min)
                                        val dia = match.utcDate.substring(8, 10)
                                        val mes = match.utcDate.substring(5, 7)
                                        txtScore.text = "🔜 $dia/$mes  📍 $horaAmPm Colombia"
                                        txtScore.setTextColor(Color.parseColor("#C5A059"))
                                    }
                                }
                            }

                            // Carga de imágenes (Coil)
                            match.homeTeam.crest?.let { url ->
                                imageLoader.enqueue(coil.request.ImageRequest.Builder(this@LivePlayerActivity)
                                    .data(url).target(imgHome).build())
                            }
                            match.awayTeam.crest?.let { url ->
                                imageLoader.enqueue(coil.request.ImageRequest.Builder(this@LivePlayerActivity)
                                    .data(url).target(imgAway).build())
                            }

                            contenedor.addView(view)
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e("API_DEBUG", "Error al cargar partidos: ${e.message}")
            }
        }
    }

    private fun formatoAmPm(hora24: Int, minuto: Int): String {
        val amPm = if (hora24 >= 12) "PM" else "AM"
        val hora12 = if (hora24 % 12 == 0) 12 else hora24 % 12
        return String.format("%d:%02d %s", hora12, minuto, amPm)
    }
}