package com.creativem.fulltv.principal

import android.app.Activity
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.animation.AnticipateOvershootInterpolator
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView

object CineAlert {

    enum class Tipo { EXITO, ERROR, INFO }

    /**
     * @param onComplete Acción opcional que se ejecutará tras 2.5 segundos de espera.
     */
    fun show(
        activity: Activity,
        mensaje: String,
        tipo: Tipo = Tipo.EXITO,
        contenedorManual: ViewGroup? = null,
        onComplete: (() -> Unit)? = null
    ) {

        // 1. Determinar el contenedor (Activity o DecorView del Dialog)
        val rootLayout = contenedorManual ?: activity.findViewById<ViewGroup>(android.R.id.content)

        // 2. Limpiar avisos anteriores
        val avisoAnterior = rootLayout.findViewWithTag<View>("CineAlertTag")
        if (avisoAnterior != null) rootLayout.removeView(avisoAnterior)

        // 3. Configuración de colores
        val colorDorado = Color.parseColor("#C5A059")
        val colorFondo = Color.parseColor("#F20A122A") // Azul noche premium
        val colorIcono = when (tipo) {
            Tipo.EXITO -> Color.GREEN
            Tipo.ERROR -> Color.RED
            Tipo.INFO -> colorDorado
        }

        // 4. Crear el contenedor visual (Diseño Premium TV)
        val contenedor = LinearLayout(activity).apply {
            tag = "CineAlertTag"
            orientation = LinearLayout.HORIZONTAL
            setPadding(65, 35, 75, 35)
            gravity = Gravity.CENTER_VERTICAL
            background = GradientDrawable().apply {
                setColor(colorFondo)
                cornerRadius = 90f
                setStroke(5, colorDorado)
            }
            elevation = 100f
            translationZ = 100f // Asegura que esté por encima de diálogos
        }

        // 5. Icono dinámico
        val icono = ImageView(activity).apply {
            val resId = when (tipo) {
                Tipo.EXITO -> android.R.drawable.checkbox_on_background
                Tipo.ERROR -> android.R.drawable.ic_delete
                Tipo.INFO -> android.R.drawable.ic_dialog_info
            }
            setImageResource(resId)
            setColorFilter(colorIcono)
            layoutParams = LinearLayout.LayoutParams(85, 85).apply { marginEnd = 30 }
        }

        // 6. Texto grande para TV
        val texto = TextView(activity).apply {
            text = mensaje
            setTextColor(Color.WHITE)
            textSize = 24f
            setTypeface(null, Typeface.BOLD)
            setShadowLayer(8f, 0f, 0f, Color.BLACK)
        }

        contenedor.addView(icono)
        contenedor.addView(texto)

        // 7. Parámetros de posición
        val params = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.WRAP_CONTENT,
            FrameLayout.LayoutParams.WRAP_CONTENT
        ).apply {
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            topMargin = 120
        }

        // 8. Animación de entrada y salida
        contenedor.alpha = 0f
        contenedor.translationY = -250f
        rootLayout.addView(contenedor, params)

        contenedor.animate()
            .alpha(1f)
            .translationY(0f)
            .setDuration(700)
            .setInterpolator(AnticipateOvershootInterpolator())
            .withEndAction {
                // Se oculta tras 3.5 segundos (tiempo visual)
                contenedor.animate()
                    .alpha(0f)
                    .translationY(-250f)
                    .setStartDelay(3500)
                    .setDuration(600)
                    .withEndAction { rootLayout.removeView(contenedor) }
                    .start()
            }
            .start()

        // 9. LÓGICA DE ESPERA PARA ACCIÓN (onComplete)
        if (onComplete != null) {
            Handler(Looper.getMainLooper()).postDelayed({
                // Verificamos que la actividad no se haya cerrado
                if (!activity.isFinishing && !activity.isDestroyed) {
                    onComplete()
                }
            }, 2500) // 2.5 segundos es el tiempo perfecto para leer en TV
        }
    }
}