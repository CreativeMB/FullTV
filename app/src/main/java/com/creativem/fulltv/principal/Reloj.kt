package com.creativem.fulltv.principal

import android.annotation.SuppressLint
import android.widget.TextView
import kotlinx.coroutines.*
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class Reloj(private val textViewHora: TextView, private val textViewFecha: TextView) {

    private val clockScope = CoroutineScope(Job() + Dispatchers.Main)

    @SuppressLint("SetTextI18n")
    fun startClock() {
        clockScope.launch {
            while (isActive) {
                val ahora = Date()

                // 1. FECHA: Mantenemos tu lógica original
                val sdfFecha = SimpleDateFormat("EEEE dd MM yy", Locale.getDefault())
                val fecha = sdfFecha.format(ahora)
                val fechaConMayuscula = fecha.substring(0, 1).uppercase() + fecha.substring(1)

                // 2. HORA: FORZAMOS Locale.US para asegurar que genere AM/PM
                // 'hh' (minúscula) es 12h. 'HH' (mayúscula) es 24h.
                val sdfHora = SimpleDateFormat("hh:mm a", Locale.US)
                val horaActual = sdfHora.format(ahora)

                // Mantenemos tu reemplazo manual por si acaso
                val horaConMayuscula = horaActual.replace("am", "AM")
                    .replace("pm", "PM")
                    .replace("AM", " AM") // Espacio extra para que no pegue al número
                    .replace("PM", " PM")

                textViewHora.text = horaConMayuscula
                textViewFecha.text = fechaConMayuscula

                delay(1000)
            }
        }
    }

    fun stopClock() {
        clockScope.cancel()
    }
}