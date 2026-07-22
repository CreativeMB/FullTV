//package com.creativem.fulltv.mundial
//
//import android.graphics.Color
//import android.graphics.Typeface
//import android.os.Bundle
//import android.util.Log
//import android.view.Gravity
//import android.view.WindowManager
//import android.widget.GridLayout
//import android.widget.ImageView
//import android.widget.LinearLayout
//import android.widget.ScrollView
//import android.widget.TextView
//import androidx.appcompat.app.AppCompatActivity
//import androidx.cardview.widget.CardView
//import androidx.core.view.WindowCompat
//import androidx.lifecycle.coroutineScope
//import coil.ImageLoader
//import coil.decode.SvgDecoder
//import coil.request.ImageRequest
//import com.creativem.fulltv.mundial.MundialApiService
//import kotlinx.coroutines.Dispatchers
//import kotlinx.coroutines.launch
//import kotlinx.coroutines.withContext
//import retrofit2.Retrofit
//import retrofit2.converter.gson.GsonConverterFactory
//
//class Mundial : AppCompatActivity() {
//
//    // Diccionario de traducción para los países del mundial
//    private val traduccionesPaises = mapOf(
//        "Qatar" to "Catar",
//        "Ecuador" to "Ecuador",
//        "Senegal" to "Senegal",
//        "Netherlands" to "Países Bajos",
//        "England" to "Inglaterra",
//        "Iran" to "Irán",
//        "USA" to "EE. UU.",
//        "United States" to "EE. UU.",
//        "Wales" to "Gales",
//        "Argentina" to "Argentina",
//        "Saudi Arabia" to "Arabia Saudita",
//        "Mexico" to "México",
//        "Poland" to "Polonia",
//        "France" to "Francia",
//        "Australia" to "Australia",
//        "Denmark" to "Dinamarca",
//        "Tunisia" to "Túnez",
//        "Spain" to "España",
//        "Costa Rica" to "Costa Rica",
//        "Germany" to "Alemania",
//        "Japan" to "Japón",
//        "Belgium" to "Bélgica",
//        "Canada" to "Canadá",
//        "Morocco" to "Marruecos",
//        "Croatia" to "Croacia",
//        "Brazil" to "Brasil",
//        "Serbia" to "Serbia",
//        "Switzerland" to "Suiza",
//        "Cameroon" to "Camerún",
//        "Portugal" to "Portugal",
//        "Ghana" to "Ghana",
//        "Uruguay" to "Uruguay",
//        "South Korea" to "Corea del Sur",
//        "Korea Republic" to "Corea del Sur",
//        "Italy" to "Italia",
//        "Sweden" to "Suecia",
//        "Colombia" to "Colombia",
//        "Peru" to "Perú",
//        "Chile" to "Chile"
//    )
//
//    private class TeamStats(val name: String) {
//        var points = 0
//        var goalsDifference = 0
//        var gamesPlayed = 0
//    }
//
//    override fun onCreate(savedInstanceState: Bundle?) {
//        super.onCreate(savedInstanceState)
//        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
//        WindowCompat.setDecorFitsSystemWindows(window, false)
//        window.setFlags(
//            WindowManager.LayoutParams.FLAG_FULLSCREEN,
//            WindowManager.LayoutParams.FLAG_FULLSCREEN
//        )
//
//        val container = LinearLayout(this).apply {
//            orientation = LinearLayout.VERTICAL
//            setBackgroundColor(Color.BLACK)
//        }
//
//        val banner = TextView(this).apply {
//            text = "CineParche: Programación"
//            setTextColor(Color.parseColor("#C5A059"))
//            textSize = 20f
//            gravity = Gravity.CENTER
//            setPadding(0, 15, 0, 10)
//            setTypeface(null, Typeface.BOLD)
//        }
//        container.addView(banner)
//
//        val scrollView = ScrollView(this).apply { isFillViewport = true }
//
//        val screenWidthDp = resources.configuration.screenWidthDp
//        val columnas = when {
//            screenWidthDp >= 960 -> 4
//            screenWidthDp >= 600 -> 3
//            screenWidthDp >= 400 -> 2
//            else -> 1
//        }
//
//        val gridLayout = GridLayout(this).apply {
//            columnCount = columnas
//            alignmentMode = GridLayout.ALIGN_BOUNDS
//            layoutParams = LinearLayout.LayoutParams(
//                LinearLayout.LayoutParams.MATCH_PARENT,
//                LinearLayout.LayoutParams.WRAP_CONTENT
//            ).apply {
//                setMargins(16, 8, 16, 16)
//            }
//        }
//
//        scrollView.addView(gridLayout)
//        container.addView(scrollView)
//
//        setContentView(container)
//        cargarResultadosMundial(gridLayout)
//    }
//
//    private fun cargarResultadosMundial(contenedor: GridLayout) {
//        val service = Retrofit.Builder()
//            .baseUrl("https://api.football-data.org/v4/")
//            .addConverterFactory(GsonConverterFactory.create())
//            .build().create(MundialApiService::class.java)
//
//        lifecycle.coroutineScope.launch(Dispatchers.IO) {
//            try {
//                val response = service.getWorldCupMatches()
//
//                withContext(Dispatchers.Main) {
//                    contenedor.removeAllViews()
//
//                    if (response.isSuccessful && response.body() != null) {
//                        val matches = response.body()!!.matches.sortedBy { it.utcDate }
//
//                        val teamPoints = mutableMapOf<String, Int>()
//                        val eliminatedTeams = mutableSetOf<String>()
//
//                        val teamToGroup = mutableMapOf<String, MutableSet<String>>()
//                        val teamStatsMap = mutableMapOf<String, TeamStats>()
//
//                        // Procesamiento de partidos y agrupamiento dinámico
//                        matches.forEach { match ->
//                            val homeName = match.homeTeam.name ?: ""
//                            val awayName = match.awayTeam.name ?: ""
//
//                            if (homeName.isEmpty() || awayName.isEmpty()) return@forEach
//
//                            if (match.stage == "GROUP_STAGE") {
//                                val homeGroup = teamToGroup[homeName]
//                                val awayGroup = teamToGroup[awayName]
//
//                                if (homeGroup != null && awayGroup != null) {
//                                    if (homeGroup != awayGroup) {
//                                        homeGroup.addAll(awayGroup)
//                                        awayGroup.forEach { teamToGroup[it] = homeGroup }
//                                    }
//                                } else if (homeGroup != null) {
//                                    homeGroup.add(awayName)
//                                    teamToGroup[awayName] = homeGroup
//                                } else if (awayGroup != null) {
//                                    awayGroup.add(homeName)
//                                    teamToGroup[homeName] = awayGroup
//                                } else {
//                                    val nuevoGrupo = mutableSetOf(homeName, awayName)
//                                    teamToGroup[homeName] = nuevoGrupo
//                                    teamToGroup[awayName] = nuevoGrupo
//                                }
//
//                                val homeStats =
//                                    teamStatsMap.getOrPut(homeName) { TeamStats(homeName) }
//                                val awayStats =
//                                    teamStatsMap.getOrPut(awayName) { TeamStats(awayName) }
//
//                                if (match.status == "FINISHED") {
//                                    val homeScore = match.score.fullTime?.home ?: 0
//                                    val awayScore = match.score.fullTime?.away ?: 0
//
//                                    homeStats.gamesPlayed++
//                                    awayStats.gamesPlayed++
//                                    homeStats.goalsDifference += (homeScore - awayScore)
//                                    awayStats.goalsDifference += (awayScore - homeScore)
//
//                                    if (homeScore > awayScore) {
//                                        homeStats.points += 3
//                                    } else if (awayScore > homeScore) {
//                                        awayStats.points += 3
//                                    } else {
//                                        homeStats.points += 1
//                                        awayStats.points += 1
//                                    }
//                                }
//                            } else if (match.stage != "GROUP_STAGE" && match.status == "FINISHED") {
//                                // Determinamos el ganador comparando los goles directamente
//                                val homeScore = match.score.fullTime?.home ?: 0
//                                val awayScore = match.score.fullTime?.away ?: 0
//
//                                if (homeScore > awayScore) {
//                                    eliminatedTeams.add(awayName) // Pierde el visitante
//                                } else if (awayScore > homeScore) {
//                                    eliminatedTeams.add(homeName) // Pierde el local
//                                }
//                            }
//                        }
//
//                        // Procesar las posiciones de los grupos
//                        val gruposUnicos = teamToGroup.values.distinct()
//                        gruposUnicos.forEach { grupoEquipos ->
//                            val sorted = grupoEquipos.map { name ->
//                                teamStatsMap.getOrPut(name) { TeamStats(name) }
//                            }.sortedWith(
//                                compareByDescending<TeamStats> { it.points }
//                                    .thenByDescending { it.goalsDifference }
//                            )
//
//                            sorted.forEachIndexed { index, stats ->
//                                teamPoints[stats.name] = stats.points
//                                if (stats.gamesPlayed >= 3 && index >= 2) {
//                                    eliminatedTeams.add(stats.name)
//                                }
//                            }
//                        }
//
//                        val imageLoader = ImageLoader.Builder(this@Mundial)
//                            .components { add(SvgDecoder.Factory()) }
//                            .build()
//
//                        val density = resources.displayMetrics.density
//                        val marginPx = (6 * density).toInt()
//
//                        val isPantallaGrande = resources.configuration.screenWidthDp >= 600
//                        val tamanoBandera =
//                            if (isPantallaGrande) (64 * density).toInt() else (48 * density).toInt()
//                        val tamanoTextoNombre = if (isPantallaGrande) 11f else 10f
//                        val tamanoTextoCentral = if (isPantallaGrande) 13f else 11f
//
//                        matches.forEach { match ->
//                            val homeName = match.homeTeam.name ?: ""
//                            val awayName = match.awayTeam.name ?: ""
//
//                            val isHomeEliminated = eliminatedTeams.contains(homeName)
//                            val isAwayEliminated = eliminatedTeams.contains(awayName)
//
//                            val homePts = teamPoints[homeName] ?: 0
//                            val awayPts = teamPoints[awayName] ?: 0
//
//                            val colorConfig = obtenerColoresFase(match.stage)
//
//                            val layoutInterno = LinearLayout(this@Mundial).apply {
//                                orientation = LinearLayout.HORIZONTAL
//                                gravity = Gravity.CENTER_VERTICAL
//                                setPadding(
//                                    (10 * density).toInt(),
//                                    (12 * density).toInt(),
//                                    (10 * density).toInt(),
//                                    (12 * density).toInt()
//                                )
//                                layoutParams = LinearLayout.LayoutParams(
//                                    LinearLayout.LayoutParams.MATCH_PARENT,
//                                    LinearLayout.LayoutParams.WRAP_CONTENT
//                                )
//                            }
//
//                            // Bloque Local
//                            val bloqueLocal = LinearLayout(this@Mundial).apply {
//                                orientation = LinearLayout.VERTICAL
//                                gravity = Gravity.CENTER_HORIZONTAL
//                                layoutParams = LinearLayout.LayoutParams(
//                                    0,
//                                    LinearLayout.LayoutParams.WRAP_CONTENT,
//                                    1.2f
//                                )
//                            }
//
//                            val txtHome = TextView(this@Mundial).apply {
//                                val nameEs = traducirNombrePais(homeName)
//                                text =
//                                    if (isHomeEliminated) "❌ $nameEs" else "$nameEs ($homePts pts)"
//                                textSize = tamanoTextoNombre
//                                setTextColor(if (isHomeEliminated) Color.GRAY else Color.WHITE)
//                                gravity = Gravity.CENTER
//                                setTypeface(null, Typeface.BOLD)
//                                setPadding(0, 0, 0, (6 * density).toInt())
//                            }
//
//                            val imgHome = ImageView(this@Mundial).apply {
//                                layoutParams =
//                                    LinearLayout.LayoutParams(tamanoBandera, tamanoBandera)
//                            }
//                            bloqueLocal.addView(txtHome)
//                            bloqueLocal.addView(imgHome)
//
//                            // Bloque Central
//                            val bloqueCentral = LinearLayout(this@Mundial).apply {
//                                orientation = LinearLayout.VERTICAL
//                                gravity = Gravity.CENTER
//                                layoutParams = LinearLayout.LayoutParams(
//                                    0,
//                                    LinearLayout.LayoutParams.WRAP_CONTENT,
//                                    1.6f
//                                )
//                            }
//
//                            val txtScore = TextView(this@Mundial).apply {
//                                textSize = tamanoTextoCentral
//                                gravity = Gravity.CENTER
//                                setTypeface(null, Typeface.BOLD)
//                            }
//
//                            val homeScore = match.score.fullTime?.home ?: 0
//                            val awayScore = match.score.fullTime?.away ?: 0
//
//                            when (match.status) {
//                                "FINISHED" -> {
//                                    txtScore.text =
//                                        "${colorConfig.etiquetaFase}\nFINAL\n$homeScore - $awayScore"
//                                    txtScore.setTextColor(Color.parseColor("#C5A059"))
//                                }
//
//                                "IN_PLAY", "PAUSED" -> {
//                                    txtScore.text = "🔴 EN VIVO\n$homeScore - $awayScore"
//                                    txtScore.setTextColor(Color.RED)
//                                }
//
//                                "TIMED", "SCHEDULED" -> {
//                                    if (match.utcDate.length >= 16) {
//                                        val horaUTC =
//                                            match.utcDate.substring(11, 13).toIntOrNull() ?: 0
//                                        val min = match.utcDate.substring(14, 16).toIntOrNull() ?: 0
//                                        val horaLocal = (horaUTC - 5 + 24) % 24
//                                        val horaAmPm = formatoAmPm(horaLocal, min)
//                                        val dia = match.utcDate.substring(8, 10)
//                                        val mes = match.utcDate.substring(5, 7)
//                                        txtScore.text = "🔜 $dia/$mes\n$horaAmPm"
//                                        txtScore.setTextColor(Color.parseColor("#CCCCCC"))
//                                    }
//                                }
//                            }
//                            bloqueCentral.addView(txtScore)
//
//                            // Bloque Visitante
//                            val bloqueVisitante = LinearLayout(this@Mundial).apply {
//                                orientation = LinearLayout.VERTICAL
//                                gravity = Gravity.CENTER_HORIZONTAL
//                                layoutParams = LinearLayout.LayoutParams(
//                                    0,
//                                    LinearLayout.LayoutParams.WRAP_CONTENT,
//                                    1.2f
//                                )
//                            }
//
//                            val txtAway = TextView(this@Mundial).apply {
//                                val nameEs = traducirNombrePais(awayName)
//                                text =
//                                    if (isAwayEliminated) "❌ $nameEs" else "$nameEs ($awayPts pts)"
//                                textSize = tamanoTextoNombre
//                                setTextColor(if (isAwayEliminated) Color.GRAY else Color.WHITE)
//                                gravity = Gravity.CENTER
//                                setTypeface(null, Typeface.BOLD)
//                                setPadding(0, 0, 0, (6 * density).toInt())
//                            }
//
//                            val imgAway = ImageView(this@Mundial).apply {
//                                layoutParams =
//                                    LinearLayout.LayoutParams(tamanoBandera, tamanoBandera)
//                            }
//                            bloqueVisitante.addView(txtAway)
//                            bloqueVisitante.addView(imgAway)
//
//                            layoutInterno.addView(bloqueLocal)
//                            layoutInterno.addView(bloqueCentral)
//                            layoutInterno.addView(bloqueVisitante)
//
//                            // Banderas mediante Coil
//                            match.homeTeam.crest?.let { url ->
//                                imageLoader.enqueue(
//                                    ImageRequest.Builder(this@Mundial)
//                                        .data(url).target(imgHome).build()
//                                )
//                            }
//                            match.awayTeam.crest?.let { url ->
//                                imageLoader.enqueue(
//                                    ImageRequest.Builder(this@Mundial)
//                                        .data(url).target(imgAway).build()
//                                )
//                            }
//
//                            val cardView = CardView(this@Mundial).apply {
//                                isFocusable = true
//                                isClickable = true
//                                radius = 8 * density
//                                cardElevation = 4 * density
//                                setCardBackgroundColor(colorConfig.colorNormal)
//
//                                setOnFocusChangeListener { v, hasFocus ->
//                                    if (hasFocus) {
//                                        v.animate().scaleX(1.05f).scaleY(1.05f).setDuration(120)
//                                            .start()
//                                        setCardBackgroundColor(colorConfig.colorFocus)
//                                        cardElevation = 8 * density
//                                    } else {
//                                        v.animate().scaleX(1.0f).scaleY(1.0f).setDuration(120)
//                                            .start()
//                                        setCardBackgroundColor(colorConfig.colorNormal)
//                                        cardElevation = 4 * density
//                                    }
//                                }
//                            }
//
//                            cardView.addView(layoutInterno)
//
//                            val gridParams = GridLayout.LayoutParams().apply {
//                                columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1, 1f)
//                                rowSpec = GridLayout.spec(GridLayout.UNDEFINED, 1)
//                                width = 0
//                                height = GridLayout.LayoutParams.WRAP_CONTENT
//                                setMargins(marginPx, marginPx, marginPx, marginPx)
//                            }
//                            cardView.layoutParams = gridParams
//                            contenedor.addView(cardView)
//                        }
//                    }
//                }
//            } catch (e: Exception) {
//                Log.e("API_DEBUG", "Error al cargar partidos: ${e.message}")
//            }
//        }
//    }
//
//    private fun traducirNombrePais(nombreIngles: String?): String {
//        if (nombreIngles == null) return ""
//        return traduccionesPaises[nombreIngles] ?: nombreIngles
//    }
//
//    private fun formatoAmPm(hora24: Int, minuto: Int): String {
//        val amPm = if (hora24 >= 12) "PM" else "AM"
//        val hora12 = if (hora24 % 12 == 0) 12 else hora24 % 12
//        return String.format("%d:%02d %s", hora12, minuto, amPm)
//    }
//
//    private class ColorConfig(val colorNormal: Int, val colorFocus: Int, val etiquetaFase: String)
//
//    private fun obtenerColoresFase(stage: String): ColorConfig {
//        return when (stage) {
//            "GROUP_STAGE" -> ColorConfig(
//                Color.parseColor("#111E2E"),
//                Color.parseColor("#1B3047"),
//                "GRUPO"
//            )
//            "FINAL" -> ColorConfig(
//                Color.parseColor("#30240D"),
//                Color.parseColor("#4A3713"),
//                "FINAL"
//            )
//            else -> ColorConfig(
//                Color.parseColor("#26152B"),
//                Color.parseColor("#3B2142"),
//                "FASE DIRECTA"
//            )
//        }
//    }
//}