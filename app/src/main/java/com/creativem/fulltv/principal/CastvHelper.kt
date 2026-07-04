package com.creativem.fulltv.principal

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import com.bumptech.glide.Glide
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*
import java.text.SimpleDateFormat
import java.util.*
import com.creativem.fulltv.R
import com.creativem.fulltv.peliculas.PeliculasActivity


object CastvHelper {

    private const val TAG = "CastvHelper"
    private val activeListeners = mutableMapOf<String, ValueEventListener>()
    private val databaseRef = FirebaseDatabase.getInstance().reference

    fun inicializarHeader(
        rootView: View,
        onDataLoaded: (nombre: String, castv: Int) -> Unit
    ) {
        val auth = FirebaseAuth.getInstance()
        val user = auth.currentUser
        val context = rootView.context

        // Referencias visuales (usamos .findViewById sin !! para evitar crash si el ID no existe)
        val textFecha = rootView.findViewById<TextView>(R.id.textfecha)
        val textHora = rootView.findViewById<TextView>(R.id.textHora)
        val textUsuario = rootView.findViewById<TextView>(R.id.textUsuario)
        val userOnline = rootView.findViewById<TextView>(R.id.useronline)
        val userOff = rootView.findViewById<TextView>(R.id.useroff)
        val textCastv = rootView.findViewById<TextView>(R.id.textCastv)
        val imagenUser = rootView.findViewById<ImageView>(R.id.imagenuser)
        val txtBanner = rootView.findViewById<TextView>(R.id.txtBanner)

        textFecha?.text = SimpleDateFormat("EEEE dd MM yy", Locale("es", "ES")).format(Date()).replaceFirstChar { it.uppercase() }
        textHora?.text = SimpleDateFormat("hh:mm aa", Locale.getDefault()).format(Date()).replace("am", "AM").replace("pm", "PM")

        // 1. Escuchar Noticia (Banner)
        val noticiaRef = databaseRef.child("noticia").child("us4vaaf0VPezu9vuc4ns")
        val noticiaListener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val mensaje = snapshot.child("banner").getValue(String::class.java) ?: ""
                val version = snapshot.child("versionapk").getValue(String::class.java) ?: ""
                txtBanner?.text = " 📢 $mensaje | Versión: $version "
                txtBanner?.isSelected = true
            }
            override fun onCancelled(error: DatabaseError) {}
        }
        noticiaRef.addValueEventListener(noticiaListener)
        activeListeners["noticia"] = noticiaListener

        // 2. Escuchar Usuarios Online
        val usuariosRef = databaseRef.child("usuarios")
        val usersListener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                var on = 0; var off = 0
                snapshot.children.forEach { if (it.child("enlinea").getValue(Boolean::class.java) == true) on++ else off++ }
                userOnline?.text = "ON-$on"
                userOff?.text = "OFF-$off"
            }
            override fun onCancelled(error: DatabaseError) {}
        }
        usuariosRef.addValueEventListener(usersListener)
        activeListeners["usuarios"] = usersListener

        // 3. Cargar datos del usuario actual
        user?.email?.let { email ->
            val userListener = obtenerDatosUsuario(email, { nombre, _, castv, enlinea ->
                textUsuario?.text = "\uD83E\uDDD1 $nombre" + if (enlinea) " 🟢" else " 🔴"

                databaseRef.child("movies").get().addOnSuccessListener { snapshot ->
                    val totalPeliculas = snapshot.childrenCount
                    textCastv?.text = "🎬 Películas: $totalPeliculas | ⭐ Castv: $castv"
                    onDataLoaded(nombre, castv)
                }
            }, { e -> Log.e(TAG, "Error cargando usuario", e) })

            activeListeners["usuario_actual"] = userListener

            // Foto de perfil
            if (imagenUser != null) {
                user.photoUrl?.let {
                    Glide.with(context).load(it).placeholder(R.drawable.icono).into(imagenUser)
                } ?: imagenUser.setImageResource(R.drawable.icono)
            }
        }
    }
    // 🟢 Función 1 corregida: Protege el cálculo contra llamadas recursivas de recursos
    fun obtenerFactorEscalaDinamico(context: android.content.Context, res: android.content.res.Resources): Float {
        val config = res.configuration
        val appContext = context.applicationContext
        val uiModeManager = appContext.getSystemService(android.content.Context.UI_MODE_SERVICE) as? android.app.UiModeManager
        val esTv = uiModeManager?.currentModeType == android.content.res.Configuration.UI_MODE_TYPE_TELEVISION

        // Diferenciamos una Tablet estándar (emulador) de una pantalla panorámica de Auto
        val esPantallaAutoOVeryWide = config.smallestScreenWidthDp >= 750
        val esTabletEstandar = config.smallestScreenWidthDp in 600..749

        return when {
            esTv -> 1.0f                   // 📺 TV original
            esPantallaAutoOVeryWide -> 1.60f // 🚗 Pantallas de Auto anchas
            esTabletEstandar -> 1.10f      // 📱 Emuladores y Tablets normales (ligero aumento sin desborde)
            else -> 0.75f                   // 📱 Móviles
        }
    }

    // 🟢 Función 2 corregida
    fun ajustarContexto(context: android.content.Context): android.content.Context {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.JELLY_BEAN_MR1) {
            val res = context.resources
            val factorEscala = obtenerFactorEscalaDinamico(context, res)
            if (factorEscala == 1.0f) return context

            val config = android.content.res.Configuration(res.configuration)
            val baseDpi = android.content.res.Resources.getSystem().displayMetrics.densityDpi
            config.densityDpi = (baseDpi * factorEscala).toInt()

            return context.createConfigurationContext(config)
        }
        return context
    }

    // 🟢 Función 3 corregida: Pasa el objeto 'res' directamente a la detección de escala
    fun ajustarRecursos(res: android.content.res.Resources, context: android.content.Context) {
        val factorEscala = obtenerFactorEscalaDinamico(context, res)
        if (factorEscala == 1.0f) return

        val config = res.configuration
        val baseDpi = android.content.res.Resources.getSystem().displayMetrics.densityDpi
        config.densityDpi = (baseDpi * factorEscala).toInt()

        res.updateConfiguration(config, res.displayMetrics)
    }
    fun regresarAPeliculas(activity: Activity) {
        if (activity.isFinishing || activity.isDestroyed) return

        try {
            val intent = Intent(activity, PeliculasActivity::class.java).apply {
                // CLEAR_TOP busca PeliculasActivity en la pila y destruye todo lo que esté por encima.
                // SINGLE_TOP asegura que si ya existe en la pila, NO se vuelva a crear (evita la recarga de datos).
                flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            }

            activity.startActivity(intent)
            activity.finish()

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                activity.overrideActivityTransition(
                    Activity.OVERRIDE_TRANSITION_CLOSE,
                    0,
                    0
                )
            } else {
                @Suppress("DEPRECATION")
                activity.overridePendingTransition(0, 0)
            }

        } catch (e: Exception) {
            Log.e("CastvHelper", "Error al regresar: ${e.message}", e)
            activity.finish()
        }
    }

    fun limpiarListeners() {
        activeListeners.forEach { (key, listener) ->
            databaseRef.child(key).removeEventListener(listener)
        }
        activeListeners.clear()
    }

    // --- MÉTODOS DE APOYO ---
    private fun codificarCorreo(correo: String) = correo.replace(".", "_").replace("@", "_")

    fun nuevosusuarios(context: Context, nombre: String, email: String?) {
        if (email.isNullOrBlank()) return
        val correoKey = codificarCorreo(email)
        val userRef = FirebaseDatabase.getInstance().reference.child("usuarios").child(correoKey)
        userRef.get().addOnSuccessListener { snapshot ->
            if (snapshot.exists()) {
                if (snapshot.child("estado").getValue(String::class.java) == "eliminado") manejarUsuarioEliminado(context)
                else configurarPresencia(userRef)
            } else crearNuevoUsuario(context, userRef, nombre, email)
        }
    }

    private fun crearNuevoUsuario(context: Context, userRef: DatabaseReference, nombre: String, email: String) {
        val userId = FirebaseAuth.getInstance().currentUser?.uid ?: ""
        val user = mapOf("nombre" to nombre, "correo" to email, "castv" to 250, "userId" to userId, "estado" to "activo", "enlinea" to true, "fechaCreacion" to SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(Date()))
        userRef.setValue(user).addOnSuccessListener { configurarPresencia(userRef) }
    }

    fun registrarConsumo(email: String, nombrePelicula: String, costo: Int) {
        val correoKey = codificarCorreo(email)
        val consumoRef = FirebaseDatabase.getInstance().reference.child("historial_consumos").push()
        consumoRef.setValue(mapOf("usuarioCorreo" to email, "pelicula" to nombrePelicula, "creditosGastados" to costo, "timestamp" to ServerValue.TIMESTAMP))

        FirebaseDatabase.getInstance().reference.child("usuarios").child(correoKey).child("totalGastado").runTransaction(object : Transaction.Handler {
            override fun doTransaction(mutableData: MutableData): Transaction.Result {
                mutableData.value = (mutableData.getValue(Int::class.java) ?: 0) + costo
                return Transaction.success(mutableData)
            }
            override fun onComplete(e: DatabaseError?, c: Boolean, s: DataSnapshot?) {}
        })
    }

    private fun configurarPresencia(userRef: DatabaseReference) {
        userRef.child("enlinea").setValue(true)
        userRef.child("enlinea").onDisconnect().setValue(false)
        userRef.child("ultimaConexion").onDisconnect().setValue(ServerValue.TIMESTAMP)
    }

    private fun manejarUsuarioEliminado(context: Context) {
        FirebaseAuth.getInstance().signOut()
        if (context is Activity) { context.runOnUiThread { Toast.makeText(context, "Cuenta inhabilitada.", Toast.LENGTH_LONG).show(); context.finish() } }
    }

    fun obtenerDatosUsuario(email: String, onSuccess: (String, String, Int, Boolean) -> Unit, onFailure: (Exception) -> Unit): ValueEventListener {
        val ref = FirebaseDatabase.getInstance().reference.child("usuarios").child(codificarCorreo(email))
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                if (snapshot.exists()) onSuccess(snapshot.child("nombre").getValue(String::class.java) ?: "Usuario", snapshot.child("correo").getValue(String::class.java) ?: "", snapshot.child("castv").getValue(Int::class.java) ?: 0, snapshot.child("enlinea").getValue(Boolean::class.java) ?: false)
            }
            override fun onCancelled(error: DatabaseError) { onFailure(error.toException()) }
        }
        ref.addValueEventListener(listener)
        return listener
    }
    fun mostrarSelectorFechaHora(
        context: android.content.Context,
        onDateTimeSelected: (fechaDb: String, horaDb: Int) -> Unit
    ) {
        val colorTextoLogo = android.graphics.Color.parseColor("#C5A059") // Dorado
        val colorFondoPrincipal = android.graphics.Color.parseColor("#2A2A2A") // Gris Oscuro
        val colorTarjetaSelectores = android.graphics.Color.parseColor("#1F1F1F") // Gris más oscuro
        val colorTextoNegro = android.graphics.Color.BLACK

        // 1. Calcular el límite mínimo de tiempo (Hora actual + 12 horas)
        val calendarMin = Calendar.getInstance().apply {
            add(Calendar.HOUR_OF_DAY, 12)
        }
        val minHourAllowed = calendarMin.get(Calendar.HOUR_OF_DAY)

        // Preparar listas de fechas a partir de la fecha mínima calculada
        val calendarTemp = calendarMin.clone() as Calendar
        val listaMostrar = ArrayList<String>()
        val listaGuardar = ArrayList<String>()

        val sdfMostrar = SimpleDateFormat("EEEE d 'de' MMMM", Locale("es", "ES"))
        val sdfGuardar = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())

        val calendarNow = Calendar.getInstance()
        val calendarTomorrow = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, 1) }

        // Generar 7 días disponibles
        for (i in 0..6) {
            val esHoy = calendarTemp.get(Calendar.YEAR) == calendarNow.get(Calendar.YEAR) &&
                    calendarTemp.get(Calendar.DAY_OF_YEAR) == calendarNow.get(Calendar.DAY_OF_YEAR)

            val esManana = calendarTemp.get(Calendar.YEAR) == calendarTomorrow.get(Calendar.YEAR) &&
                    calendarTemp.get(Calendar.DAY_OF_YEAR) == calendarTomorrow.get(Calendar.DAY_OF_YEAR)

            val prefijo = when {
                esHoy -> "Hoy - "
                esManana -> "Mañana - "
                else -> ""
            }

            listaMostrar.add(prefijo + sdfMostrar.format(calendarTemp.time).replaceFirstChar { it.uppercase() })
            listaGuardar.add(sdfGuardar.format(calendarTemp.time))
            calendarTemp.add(Calendar.DAY_OF_YEAR, 1)
        }

        // 2. Contenedor principal
        val mainLayout = android.widget.LinearLayout(context).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            setPadding(40, 30, 40, 20)
            setBackgroundColor(colorFondoPrincipal)
        }

        val scrollView = android.widget.ScrollView(context).apply {
            layoutParams = android.view.ViewGroup.LayoutParams(
                android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                android.view.ViewGroup.LayoutParams.WRAP_CONTENT
            )
            setBackgroundColor(colorFondoPrincipal)
            addView(mainLayout)
        }

        // Título
        val titulo = android.widget.TextView(context).apply {
            text = "💎 PROGRAMAR TRANSMISIÓN"
            textSize = 18f
            setTextColor(colorTextoLogo)
            gravity = android.view.Gravity.CENTER
            setTypeface(null, android.graphics.Typeface.BOLD)
            setPadding(0, 10, 0, 15)
        }
        mainLayout.addView(titulo)

        // Descripción
        val descripcion = android.widget.TextView(context).apply {
            text = "Por políticas del servicio, las activaciones deben programarse con un mínimo de 12 horas de anticipación."
            textSize = 14f
            setTextColor(android.graphics.Color.WHITE)
            gravity = android.view.Gravity.CENTER
            setPadding(20, 0, 20, 25)
        }
        mainLayout.addView(descripcion)

        // Tarjeta contenedora de los selectores
        val tarjetaSelectores = android.widget.LinearLayout(context).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            gravity = android.view.Gravity.CENTER
            setPadding(30, 25, 30, 25)

            val shape = android.graphics.drawable.GradientDrawable().apply {
                setColor(colorTarjetaSelectores)
                cornerRadius = 10f
            }
            background = shape
        }

        // --- CABECERAS DE COLUMNAS (Alineación corregida con pesos de 1.7 y 1.0) ---
        val layoutHeaders = android.widget.LinearLayout(context).apply {
            orientation = android.widget.LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER
        }

        val headerFechaParams = android.widget.LinearLayout.LayoutParams(
            0,
            android.widget.LinearLayout.LayoutParams.WRAP_CONTENT,
            1.7f // 63% del espacio asignado al encabezado de fecha
        )
        val headerHoraParams = android.widget.LinearLayout.LayoutParams(
            0,
            android.widget.LinearLayout.LayoutParams.WRAP_CONTENT,
            1.0f // 37% del espacio asignado al encabezado de hora
        )

        val lblFecha = android.widget.TextView(context).apply {
            text = "FECHA DE INICIO"
            setTextColor(colorTextoLogo)
            textSize = 12f
            setTypeface(null, android.graphics.Typeface.BOLD)
            gravity = android.view.Gravity.CENTER
        }
        val lblHora = android.widget.TextView(context).apply {
            text = "HORA DE INICIO"
            setTextColor(colorTextoLogo)
            textSize = 12f
            setTypeface(null, android.graphics.Typeface.BOLD)
            gravity = android.view.Gravity.CENTER
        }
        layoutHeaders.addView(lblFecha, headerFechaParams)
        layoutHeaders.addView(lblHora, headerHoraParams)
        tarjetaSelectores.addView(layoutHeaders)

        // Fila de los Pickers
        val layoutPickers = android.widget.LinearLayout(context).apply {
            orientation = android.widget.LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER
            setPadding(0, 15, 0, 10)
        }

        // Fondos de Enfoque (Focus)
        val backgroundNormal = android.graphics.drawable.GradientDrawable().apply {
            setColor(android.graphics.Color.parseColor("#E0E0E0"))
            cornerRadius = 8f
        }

        val backgroundEnfocado = android.graphics.drawable.GradientDrawable().apply {
            setColor(android.graphics.Color.WHITE)
            cornerRadius = 8f
            setStroke(4, colorTextoLogo)
        }

        // Selector de Fecha
        val pickerFecha = android.widget.NumberPicker(context).apply {
            minValue = 0
            maxValue = listaMostrar.size - 1
            displayedValues = listaMostrar.toTypedArray()
            wrapSelectorWheel = false
            descendantFocusability = android.view.ViewGroup.FOCUS_BLOCK_DESCENDANTS
            isFocusable = true
        }

        // Selector de Hora
        val pickerHora = android.widget.NumberPicker(context).apply {
            wrapSelectorWheel = true
            descendantFocusability = android.view.ViewGroup.FOCUS_BLOCK_DESCENDANTS
            isFocusable = true
        }

        // Función auxiliar para convertir el formato de 24 horas a 12 horas AM/PM
        fun obtenerFormato12Horas(hora24: Int): String {
            val amPm = if (hora24 >= 12) "PM" else "AM"
            val hora12 = when {
                hora24 == 0 -> 12
                hora24 > 12 -> hora24 - 12
                else -> hora24
            }
            return String.format(Locale.getDefault(), "%02d:00 %s", hora12, amPm)
        }

        // Lógica para actualizar dinámicamente el rango de horas en formato AM/PM
        fun actualizarRangoHoras(indexFechaSeleccionada: Int) {
            pickerHora.displayedValues = null

            if (indexFechaSeleccionada == 0) {
                pickerHora.minValue = minHourAllowed
                pickerHora.maxValue = 23

                val horasFiltradas = Array(24 - minHourAllowed) { i ->
                    obtenerFormato12Horas(i + minHourAllowed)
                }
                pickerHora.displayedValues = horasFiltradas
            } else {
                pickerHora.minValue = 0
                pickerHora.maxValue = 23

                val horasCompletas = Array(24) { i ->
                    obtenerFormato12Horas(i)
                }
                pickerHora.displayedValues = horasCompletas
            }

            establecerColorTextoPicker(pickerHora, colorTextoNegro)
        }

        actualizarRangoHoras(0)
        pickerFecha.setOnValueChangedListener { _, _, newVal ->
            actualizarRangoHoras(newVal)
        }

        establecerColorTextoPicker(pickerFecha, colorTextoNegro)

        // Contenedor visual para Fecha (Con fondo normal/enfocado)
        val wrapperFecha = android.widget.LinearLayout(context).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            setPadding(10, 10, 10, 10)
            background = backgroundNormal
        }
        wrapperFecha.addView(pickerFecha)

        pickerFecha.setOnFocusChangeListener { _, hasFocus ->
            wrapperFecha.background = if (hasFocus) backgroundEnfocado else backgroundNormal
        }

        // Contenedor visual para Hora (Con fondo normal/enfocado)
        val wrapperHora = android.widget.LinearLayout(context).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            setPadding(10, 10, 10, 10)
            background = backgroundNormal
        }
        wrapperHora.addView(pickerHora)

        pickerHora.setOnFocusChangeListener { _, hasFocus ->
            wrapperHora.background = if (hasFocus) backgroundEnfocado else backgroundNormal
        }

        // --- PARÁMETROS DE ESPACIO ASIMÉTRICOS (Para expandir la fecha) ---
        val fechaParams = android.widget.LinearLayout.LayoutParams(
            0,
            android.widget.LinearLayout.LayoutParams.WRAP_CONTENT,
            1.7f // Le otorgamos el 63% del ancho disponible para que quepa la fecha sin cortarse
        ).apply {
            setMargins(15, 0, 15, 0)
        }

        val horaParams = android.widget.LinearLayout.LayoutParams(
            0,
            android.widget.LinearLayout.LayoutParams.WRAP_CONTENT,
            1.0f // 37% de ancho para la hora ("12:00 PM" entra perfectamente en este espacio)
        ).apply {
            setMargins(15, 0, 15, 0)
        }

        // Añadir los contenedores con las proporciones corregidas
        layoutPickers.addView(wrapperFecha, fechaParams)
        layoutPickers.addView(wrapperHora, horaParams)
        tarjetaSelectores.addView(layoutPickers)

        mainLayout.addView(tarjetaSelectores)

        val espacioSeparador = android.widget.Space(context).apply {
            minimumHeight = 30
        }
        mainLayout.addView(espacioSeparador)

        // 4. Diálogo de alerta
        val builder = androidx.appcompat.app.AlertDialog.Builder(context)
            .setView(scrollView)
            .setPositiveButton("CONFIRMAR", null)
            .setNegativeButton("CANCELAR", null)

        val alertDialog = builder.create()
        alertDialog.window?.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(colorFondoPrincipal))

        alertDialog.setOnShowListener {
            val btnConfirmar = alertDialog.getButton(androidx.appcompat.app.AlertDialog.BUTTON_POSITIVE)
            val btnCancelar = alertDialog.getButton(androidx.appcompat.app.AlertDialog.BUTTON_NEGATIVE)

            btnConfirmar.apply {
                setTextColor(colorTextoLogo)
                textSize = 15f
                setTypeface(null, android.graphics.Typeface.BOLD)
            }

            btnCancelar.apply {
                setTextColor(android.graphics.Color.parseColor("#F87171"))
                textSize = 15f
                setTypeface(null, android.graphics.Typeface.BOLD)
            }

            val focusSelector = com.creativem.fulltv.R.drawable.focus_selector
            listOf(btnConfirmar, btnCancelar).forEach { button ->
                button.setBackgroundResource(focusSelector)
                button.isFocusable = true
                button.isFocusableInTouchMode = true
                button.setPadding(35, 15, 35, 15)
            }

            btnConfirmar.setOnClickListener {
                val fechaSeleccionada = listaGuardar[pickerFecha.value]
                val horaSeleccionada = pickerHora.value // Retorna un entero entre 0 y 23
                onDateTimeSelected(fechaSeleccionada, horaSeleccionada)
                alertDialog.dismiss()
            }

            btnCancelar.setOnClickListener {
                alertDialog.dismiss()
            }

            pickerFecha.requestFocus()
        }

        alertDialog.show()
    }

    private fun establecerColorTextoPicker(numberPicker: android.widget.NumberPicker, color: Int) {
        // 1. Intentar el método oficial para Android 10 (API 29) o superior
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            try {
                numberPicker.textColor = color
            } catch (e: Exception) {
                // Si falla por modificaciones del fabricante en la TV, recurre a la reflexión
                aplicarColorPorReflexion(numberPicker, color)
            }
        } else {
            // 2. Usar reflexión para versiones anteriores (Android 9 o inferior)
            aplicarColorPorReflexion(numberPicker, color)
        }
    }

    @SuppressLint("SoonBlockedPrivateApi")
    private fun aplicarColorPorReflexion(numberPicker: android.widget.NumberPicker, color: Int) {
        // Paso A: Forzar color negro en el campo de texto central (EditText)
        val count = numberPicker.childCount
        for (i in 0 until count) {
            val child = numberPicker.getChildAt(i)
            if (child is android.widget.EditText) {
                try {
                    child.setTextColor(color)
                    child.highlightColor = android.graphics.Color.TRANSPARENT
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }

        // Paso B: Forzar color negro en el pincel del carrusel en movimiento (mSelectorWheelPaint)
        try {
            val selectorWheelPaintField = android.widget.NumberPicker::class.java.getDeclaredField("mSelectorWheelPaint")
            selectorWheelPaintField.isAccessible = true
            (selectorWheelPaintField.get(numberPicker) as? android.graphics.Paint)?.color = color
            numberPicker.invalidate() // Obliga al selector a redibujarse con el nuevo color
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}