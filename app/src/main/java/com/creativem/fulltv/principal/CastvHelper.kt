package com.creativem.fulltv.principal

import android.app.Activity
import android.content.Context
import android.util.Log
import android.widget.Toast
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*
import java.text.SimpleDateFormat
import java.util.*

object CastvHelper {

    private const val TAG = "CastvHelper"

    /**
     * 🔐 Codifica el correo para usarlo como clave en Realtime Database.
     * Reemplaza caracteres no permitidos (@ y .) por guiones bajos.
     */
    private fun codificarCorreo(correo: String): String {
        return correo.replace(".", "_").replace("@", "_")
    }

    /**
     * ✅ Gestiona el registro y la validación de presencia del usuario.
     */
    fun nuevosusuarios(
        context: Context,
        nombre: String,
        email: String?
    ) {
        if (email.isNullOrBlank()) {
            Log.e(TAG, "Correo electrónico nulo o vacío")
            return
        }

        val correoKey = codificarCorreo(email)
        val database = FirebaseDatabase.getInstance()
        val userRef = database.reference.child("usuarios").child(correoKey)

        userRef.get().addOnSuccessListener { snapshot ->
            if (snapshot.exists()) {
                val estado = snapshot.child("estado").getValue(String::class.java)

                if (estado == "eliminado") {
                    manejarUsuarioEliminado(context)
                } else {
                    Log.d(TAG, "Usuario existente: Actualizando presencia")
                    configurarPresencia(userRef)
                }
            } else {
                crearNuevoUsuario(context, userRef, nombre, email)
            }
        }.addOnFailureListener { e ->
            Log.e(TAG, "Error al acceder a la base de datos", e)
        }
    }

    /**
     * ✍️ Escribe un nuevo usuario en la base de datos por primera vez.
     */
    private fun crearNuevoUsuario(
        context: Context,
        userRef: DatabaseReference,
        nombre: String,
        email: String
    ) {
        val userId = FirebaseAuth.getInstance().currentUser?.uid ?: ""
        val fechaFormateada = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(Date())

        val user = mapOf(
            "nombre" to nombre,
            "correo" to email,
            "castv" to 250, // Créditos iniciales gratis
            "userId" to userId,
            "estado" to "activo",
            "enlinea" to true,
            "fechaCreacion" to fechaFormateada
        )

        userRef.setValue(user)
            .addOnSuccessListener {
                Log.d(TAG, "✅ Usuario creado exitosamente")
                configurarPresencia(userRef)
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "❌ Error al crear nodo de usuario", e)
            }
    }
    /**
     * 💰 Registra el consumo de créditos de un usuario en Firebase
     */
    fun registrarConsumo(email: String, nombrePelicula: String, costo: Int) {
        val correoKey = codificarCorreo(email)
        val database = FirebaseDatabase.getInstance()

        // 1. Creamos una entrada única en la rama "historial_consumos"
        val consumoRef = database.reference.child("historial_consumos").push()

        val datosConsumo = mapOf(
            "usuarioCorreo" to email,
            "pelicula" to nombrePelicula,
            "creditosGastados" to costo,
            "timestamp" to ServerValue.TIMESTAMP
        )

        consumoRef.setValue(datosConsumo).addOnSuccessListener {
            Log.d(TAG, "Consumo registrado: $costo créditos en $nombrePelicula")
        }

        // 2. Opcional: Actualizamos también un total acumulado en el nodo del usuario
        // Esto facilita ver en la otra app cuánto ha gastado un usuario en total
        val userRef = database.reference.child("usuarios").child(correoKey)
        userRef.child("totalGastado").runTransaction(object : Transaction.Handler {
            override fun doTransaction(mutableData: MutableData): Transaction.Result {
                val actual = mutableData.getValue(Int::class.java) ?: 0
                mutableData.value = actual + costo
                return Transaction.success(mutableData)
            }
            override fun onComplete(error: DatabaseError?, committed: Boolean, snapshot: DataSnapshot?) {
                if (error != null) Log.e(TAG, "Error actualizando totalGastado", error.toException())
            }
        })
    }
    /**
     * 🟢 Configura el sistema de presencia (Online/Offline) usando onDisconnect.
     */
    private fun configurarPresencia(userRef: DatabaseReference) {
        // Marcamos como conectado ahora
        userRef.child("enlinea").setValue(true)

        // Instrucciones para cuando el usuario pierda la conexión o cierre la app
        userRef.child("enlinea").onDisconnect().setValue(false)
        userRef.child("ultimaConexion").onDisconnect().setValue(ServerValue.TIMESTAMP)
    }

    /**
     * 🚫 Cierra la sesión y finaliza la actividad si el usuario está baneado/eliminado.
     */
    private fun manejarUsuarioEliminado(context: Context) {
        FirebaseAuth.getInstance().signOut()
        if (context is Activity) {
            context.runOnUiThread {
                Toast.makeText(context, "Tu cuenta ha sido inhabilitada.", Toast.LENGTH_LONG).show()
                context.finish()
            }
        }
    }

    /**
     * 📡 Escucha cambios en tiempo real de los datos del usuario.
     * @return ValueEventListener para que pueda ser removido en el onDestroy de la Activity.
     */
    fun obtenerDatosUsuario(
        email: String,
        onSuccess: (nombre: String, correo: String, castv: Int, enlinea: Boolean) -> Unit,
        onFailure: (Exception) -> Unit
    ): ValueEventListener {
        val correoKey = codificarCorreo(email)
        val ref = FirebaseDatabase.getInstance().reference.child("usuarios").child(correoKey)

        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                if (!snapshot.exists()) {
                    Log.w(TAG, "Los datos del usuario no existen en la ruta")
                    return
                }

                val nombre = snapshot.child("nombre").getValue(String::class.java) ?: "Usuario"
                val correo = snapshot.child("correo").getValue(String::class.java) ?: ""
                val castv = snapshot.child("castv").getValue(Int::class.java) ?: 0
                val enlinea = snapshot.child("enlinea").getValue(Boolean::class.java) ?: false

                onSuccess(nombre, correo, castv, enlinea)
            }

            override fun onCancelled(error: DatabaseError) {
                onFailure(error.toException())
            }
        }

        ref.addValueEventListener(listener)
        return listener
    }
    /**
     * Muestra un diálogo diseñado para Android TV que permite seleccionar una fecha (próximos 7 días)
     * y una hora exacta de activación usando selectores adaptados al control remoto.
     */
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