package com.creativem.fulltv.principal

import android.content.Context

object ViewUtils {
    fun calcularColumnas(context: Context): Int {
        val displayMetrics = context.resources.displayMetrics
        val widthDp = displayMetrics.widthPixels / displayMetrics.density

        // Mantén este 160 igual para que la proporción no cambie entre pantallas
        val targetItemWidthDp = 160

        val columnas = (widthDp / targetItemWidthDp).toInt()

        return when {
            columnas < 2 -> 2
            columnas > 6 -> 6
            else -> columnas
        }
    }
}