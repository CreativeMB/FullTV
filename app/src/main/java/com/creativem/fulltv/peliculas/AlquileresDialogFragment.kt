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
import com.creativem.fulltv.peliculasvalidas.AlquileresAdapter
import com.creativem.fulltv.principal.Modelo
import com.google.firebase.database.FirebaseDatabase

class AlquileresDialogFragment : DialogFragment() {

    companion object {
        private const val ARG_CORREO_KEY = "correo_key"
        private var alquileresList: List<Modelo.AlquilerItem> = emptyList()

        fun newInstance(alquileres: List<Modelo.AlquilerItem>, correoKey: String): AlquileresDialogFragment {
            alquileresList = alquileres
            val fragment = AlquileresDialogFragment()
            val args = Bundle().apply {
                putString(ARG_CORREO_KEY, correoKey)
            }
            fragment.arguments = args
            return fragment
        }
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val parentActivity = requireActivity() as PeliculasActivity
        val correoKey = arguments?.getString(ARG_CORREO_KEY) ?: ""

        val colorDorado = Color.parseColor("#C5A059")
        val colorFondo = Color.parseColor("#0A122A")
        val displayMetrics = resources.displayMetrics
        val density = displayMetrics.density
        val dpToPx = { dp: Int -> (dp * density).toInt() }

        val maxDialogWidth = dpToPx(520)
        val targetWidth = minOf((displayMetrics.widthPixels * 0.9f).toInt(), maxDialogWidth)

        val scrollView = ScrollView(parentActivity).apply {
            layoutParams = ViewGroup.LayoutParams(
                targetWidth,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            isFillViewport = true
            clipChildren = true
            clipToPadding = true
            background = GradientDrawable().apply {
                setColor(colorFondo)
                cornerRadius = dpToPx(16).toFloat()
                setStroke(dpToPx(2), colorDorado)
            }
        }

        val container = LinearLayout(parentActivity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dpToPx(20), dpToPx(20), dpToPx(20), dpToPx(20))
            clipChildren = true
            clipToPadding = true
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
            text = "Tienes películas listas con tiempo de visualización activo: (ver en Perfil)"
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

        val rvAlquileres = RecyclerView(parentActivity).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = dpToPx(20)
            }
            setPadding(0, dpToPx(6), 0, dpToPx(6))
            clipToPadding = true
            clipChildren = true

            layoutManager =
                LinearLayoutManager(parentActivity, LinearLayoutManager.HORIZONTAL, false)
            isFocusable = true
            isFocusableInTouchMode = true
        }

        val adapter = AlquileresAdapter(
            items = alquileresList.toMutableList(),
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
                if (alquileresList.isNotEmpty()) {
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