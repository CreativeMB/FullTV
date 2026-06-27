package com.creativem.fulltv.peliculas

import android.app.AlertDialog
import android.app.Dialog
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.fragment.app.DialogFragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.creativem.fulltv.peliculas.AlquileresAdapter
import com.creativem.fulltv.principal.Modelo
import com.google.firebase.database.FirebaseDatabase

class AlquileresDialogFragment : DialogFragment() {
    companion object {
        private const val ARG_CORREO_KEY = "correo_key"
        private const val ARG_MOVIES_LIST = "movies_list"
        private const val ARG_CREATED_ATS = "created_ats"
        private const val ARG_COUNTDOWNS = "countdowns"

        fun newInstance(alquileres: List<Modelo.AlquilerItem>, correoKey: String): AlquileresDialogFragment {
            val fragment = AlquileresDialogFragment()

            val movies = ArrayList<Modelo>()
            val createdAts = LongArray(alquileres.size)
            val countdowns = IntArray(alquileres.size)

            alquileres.forEachIndexed { index, item ->
                movies.add(item.movie)
                createdAts[index] = item.createdAt
                countdowns[index] = item.countdownMinutes
            }

            val args = Bundle().apply {
                putString(ARG_CORREO_KEY, correoKey)
                putSerializable(ARG_MOVIES_LIST, movies)
                putLongArray(ARG_CREATED_ATS, createdAts)
                putIntArray(ARG_COUNTDOWNS, countdowns)
            }
            fragment.arguments = args
            return fragment
        }
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val parentActivity = requireActivity() as PeliculasActivity
        val correoKey = arguments?.getString(ARG_CORREO_KEY) ?: ""

        val movies = arguments?.getSerializable(ARG_MOVIES_LIST) as? ArrayList<Modelo> ?: ArrayList()
        val createdAts = arguments?.getLongArray(ARG_CREATED_ATS) ?: LongArray(0)
        val countdowns = arguments?.getIntArray(ARG_COUNTDOWNS) ?: IntArray(0)

        // --- RECONSTRUCCIÓN Y FILTRADO SEGURO DE ALQUILERES ---
        val ahora = System.currentTimeMillis()

        val alquileresReconstruidos = movies.mapIndexed { index, movie ->
            Modelo.AlquilerItem(
                movie = movie,
                createdAt = createdAts.getOrElse(index) { 0L },
                countdownMinutes = countdowns.getOrElse(index) { 0 }
            )
        }.filter { item ->
            // 🟢 CLAVE: Solo se incluyen en la ventana emergente si ya inició su hora de activación
            val tiempoTranscurrido = ahora - item.createdAt
            tiempoTranscurrido >= 0
        }

        // 🧹 Si tras el filtro no hay alquileres verdaderamente activos, se cancela el diálogo de inmediato
        if (alquileresReconstruidos.isEmpty()) {
            Handler(Looper.getMainLooper()).post {
                dismiss()
            }
        }

        val colorDorado = Color.parseColor("#C5A059")
        val colorFondo = Color.parseColor("#0A122A")
        val displayMetrics = resources.displayMetrics
        val density = displayMetrics.density
        val dpToPx = { dp: Int -> (dp * density).toInt() }

        val maxDialogWidth = dpToPx(520)
        val targetWidth = minOf((displayMetrics.widthPixels * 0.9f).toInt(), maxDialogWidth)

        // OPTIMIZACIÓN: Se desactivó clipChildren y clipToPadding para evitar recortes al escalar
        val scrollView = ScrollView(parentActivity).apply {
            layoutParams = ViewGroup.LayoutParams(
                targetWidth,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            isFillViewport = true
            clipChildren = false // PERMITIR DIBUJADO FUERA DE LÍMITES
            clipToPadding = false // PERMITIR DIBUJADO EN EL PADDING
            background = GradientDrawable().apply {
                setColor(colorFondo)
                cornerRadius = dpToPx(16).toFloat()
                setStroke(dpToPx(2), colorDorado)
            }
        }

        // OPTIMIZACIÓN: Se desactivó clipChildren y clipToPadding
        val container = LinearLayout(parentActivity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dpToPx(20), dpToPx(20), dpToPx(20), dpToPx(20))
            clipChildren = false // PERMITIR DIBUJADO FUERA DE LÍMITES
            clipToPadding = false // PERMITIR DIBUJADO EN EL PADDING
            layoutParams = android.widget.FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }

        val titulo = TextView(parentActivity).apply {
            text = "🍿 ALQUILERES ACTIVOS"
            textSize = 18f
            setTextColor(colorDorado)
            gravity = Gravity.CENTER
            setTypeface(null, Typeface.BOLD)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = dpToPx(8)
            }
        }

        val mensaje = TextView(parentActivity).apply {
            text = "Tienes películas listas con tiempo de visualización activo:(contenido en Perfil)"
            textSize = 13f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = dpToPx(14)
            }
        }

        // OPTIMIZACIÓN: Aumento de padding vertical (de 6dp a 16dp) y eliminación de recorte de bordes
        val rvAlquileres = RecyclerView(parentActivity).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = dpToPx(20)
            }
            // Añadimos más espacio vertical (16dp) para el crecimiento visual de las tarjetas con foco
            setPadding(0, dpToPx(16), 0, dpToPx(16))
            clipToPadding = false // IMPORTANTE: Desactivado para que no se corte dentro del padding
            clipChildren = false  // IMPORTANTE: Desactivado para que no se corte fuera del RecyclerView

            layoutManager = LinearLayoutManager(parentActivity, LinearLayoutManager.HORIZONTAL, false)
            isFocusable = true
            isFocusableInTouchMode = true
        }

        val adapter = AlquileresAdapter(
            items = alquileresReconstruidos.toMutableList(),
            onListEmpty = {
                dismiss()
            },
            onItemExpired = { itemExpirado ->
                val clave = itemExpirado.movie.title.replace(".", "_")
                    .replace("$", "_")
                    .replace("#", "_")
                    .replace("[", "_")
                    .replace("]", "_")
                FirebaseDatabase.getInstance().reference.child("usuarios").child(correoKey)
                    .child("alquileres")
                    .child(clave)
                    .removeValue()
            },
            onItemClick = { item ->
                dismiss()
                parentActivity.irAlReproductor(item.movie)
            }
        )
        rvAlquileres.adapter = adapter

        val btnCerrar = TextView(parentActivity).apply {
            text = "ENTENDIDO"
            setTextColor(Color.BLACK)
            textSize = 14f
            setTypeface(null, Typeface.BOLD)
            gravity = Gravity.CENTER
            setPadding(dpToPx(40), dpToPx(12), dpToPx(40), dpToPx(12))
            background = GradientDrawable().apply {
                setColor(colorDorado)
                cornerRadius = dpToPx(8).toFloat()
            }
            isFocusable = true
            isFocusableInTouchMode = true

            setOnClickListener {
                dismiss()
            }

            setOnFocusChangeListener { v, hasFocus ->
                if (hasFocus) {
                    v.scaleX = 1.05f
                    v.scaleY = 1.05f
                    (v as TextView).background = GradientDrawable().apply {
                        setColor(Color.WHITE)
                        cornerRadius = dpToPx(8).toFloat()
                    }
                } else {
                    v.scaleX = 1f
                    v.scaleY = 1f
                    (v as TextView).background = GradientDrawable().apply {
                        setColor(colorDorado)
                        cornerRadius = dpToPx(8).toFloat()
                    }
                }
            }
        }

        val bottomLayout = LinearLayout(parentActivity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            addView(btnCerrar)
        }

        container.addView(titulo)
        container.addView(mensaje)
        container.addView(rvAlquileres)
        container.addView(bottomLayout)

        scrollView.addView(container)

        val builder = AlertDialog.Builder(parentActivity)
        val dialog = builder.setView(scrollView).create()

        dialog.setCancelable(false)
        dialog.setCanceledOnTouchOutside(false)

        dialog.window?.apply {
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            setLayout(targetWidth, WindowManager.LayoutParams.WRAP_CONTENT)
        }

        Handler(Looper.getMainLooper()).postDelayed({
            if (dialog.isShowing) {
                if (alquileresReconstruidos.isNotEmpty()) {
                    val primerElemento = rvAlquileres.layoutManager?.findViewByPosition(0)
                    primerElemento?.requestFocus() ?: btnCerrar.requestFocus()
                } else {
                    btnCerrar.requestFocus()
                }
            }
        }, 500)

        return dialog
    }
}