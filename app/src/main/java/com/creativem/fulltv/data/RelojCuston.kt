package com.creativem.fulltv.data

import android.annotation.SuppressLint
import android.widget.TextView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class RelojCuston(private val textViewHora: TextView, private val textViewFecha: TextView) {

    private val clockScope = CoroutineScope(Job() + Dispatchers.Main)

    @SuppressLint("SetTextI18n")
    fun startClock() {
        clockScope.launch {
            while (true) {
                // Formato de fecha con la primera letra en mayúscula
                val fecha = SimpleDateFormat("EEEE dd MM yy", Locale.getDefault()).format(Date())
                val fechaConMayuscula = fecha.substring(0, 1).uppercase() + fecha.substring(1)

                // Formato de hora con AM/PM en mayúsculas
                val horaActual = SimpleDateFormat("hh:mm aa", Locale.getDefault()).format(Date())
                val horaConMayuscula = horaActual.replace("am", "AM").replace("pm", "PM")

                // Asignar la hora y la fecha formateada a los TextViews
                textViewHora.text = horaConMayuscula  // Ahora asignamos correctamente 'horaConMayuscula'
                textViewFecha.text = fechaConMayuscula

                // Retraso de 1 segundo (1000 milisegundos)
                delay(1000)
            }
        }
    }

    fun stopClock() {
        clockScope.cancel()
    }
}