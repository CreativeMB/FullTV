package com.creativem.fulltv.ui.data

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
                val fecha = SimpleDateFormat("EEEE dd-MM-yy", Locale.getDefault()).format(Date())
                val horaActual = SimpleDateFormat("hh:mm aa", Locale.getDefault()).format(Date())

                textViewHora.text = horaActual
                textViewFecha.text = fecha

                delay(1000)
            }
        }
    }

    fun stopClock() {
        clockScope.cancel()
    }
}