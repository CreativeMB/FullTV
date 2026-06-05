package com.creativem.tvfullurl.adapter

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.BitmapFactory
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.recyclerview.widget.RecyclerView
import com.creativem.tvfullurl.R
import com.creativem.tvfullurl.modelo.User
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class CastvAdapter(
    private val userList: MutableList<User>,
    private val onEditClick: (userId: String, newPoints: Int) -> Unit,
    private val onDeleteClick: (userId: String) -> Unit
) : RecyclerView.Adapter<CastvAdapter.UserViewHolder>() {

    private var filteredList: List<User> = userList

    inner class UserViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val userName: TextView = itemView.findViewById(R.id.userName)
        val userEmail: TextView = itemView.findViewById(R.id.userEmail)
        val userFecha: TextView = itemView.findViewById(R.id.fechaCreacion)
        val userCastv: TextView = itemView.findViewById(R.id.userCastv)
        val editImage: ImageView = itemView.findViewById(R.id.editImage)
        val deleteImage: ImageView = itemView.findViewById(R.id.deleteImage)
        val userCastvGasto: TextView = itemView.findViewById(R.id.userCastvGasto)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): UserViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_castv, parent, false)
        return UserViewHolder(view)
    }

    fun filter(query: String) {
        filteredList = if (query.isEmpty()) {
            userList
        } else {
            userList.filter { user ->
                user.nombre.contains(query, ignoreCase = true)
            }
        }
        notifyDataSetChanged()
    }

    override fun onBindViewHolder(holder: UserViewHolder, position: Int) {
        val user = filteredList[position]

        // --- NUEVO: RESALTADO VISUAL PARA PAGOS PENDIENTES ---
        val tienePagoPendiente = user.estado.equals("pendiente", ignoreCase = true) && !user.urlpagos.isNullOrEmpty()

        if (tienePagoPendiente) {
            // Fondo dorado/ámbar oscuro sutil para destacar el pago pendiente
            holder.itemView.setBackgroundColor(android.graphics.Color.parseColor("#74B3AF"))
        } else {
            // Fondo transparente por defecto para los usuarios normales
            holder.itemView.setBackgroundColor(android.graphics.Color.TRANSPARENT)
        }
        // Mostrar nombre y color según si está en línea
        holder.userName.text = user.nombre
        if (user.isOnline) {
            holder.userName.setTextColor(holder.itemView.context.getColor(R.color.online_color))
        } else {
            holder.userName.setTextColor(holder.itemView.context.getColor(R.color.offline_color))
        }
        holder.userCastvGasto.setText(user.totalGastado.toString())

        holder.userEmail.text = user.correo
        holder.userFecha.text = "${parsearFecha(user.ultimaConexion)}"
        holder.userCastv.text = "Castv: ${user.castv}"

        // Copiar email al portapapeles
        holder.userEmail.setOnClickListener {
            val email = user.correo
            if (email.isNotEmpty()) {
                val clipboard = holder.itemView.context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                val clip = ClipData.newPlainText("Email", email)
                clipboard.setPrimaryClip(clip)
                Toast.makeText(holder.itemView.context, "Correo copiado al portapapeles", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(holder.itemView.context, "El correo electrónico no está disponible", Toast.LENGTH_SHORT).show()
            }
        }

        // Editar puntos / Verificar comprobante de pago
        holder.editImage.setOnClickListener {
            val urlComprobante = user.urlpagos ?: ""
            val paqueteComprado = user.paquete ?: ""

            if (urlComprobante.isNotEmpty()) {
                // Si el usuario ya subió su comprobante, abrimos la vista de verificación del pago
                mostrarDialogoVerificacion(holder.itemView.context, user) { nuevosPuntos ->
                    onEditClick(user.userId, nuevosPuntos)
                }
            } else {
                // Si no tiene compras pendientes, mostramos el selector de planes manual tradicional
                mostrarSelectorDePlanes(holder.itemView.context, user.userId) { nuevosPuntos ->
                    onEditClick(user.userId, nuevosPuntos)
                }
            }
        }

        // Eliminar usuario
        holder.deleteImage.setOnClickListener {
            val context = holder.itemView.context
            android.app.AlertDialog.Builder(context)
                .setTitle("Confirmar eliminación")
                .setMessage("¿Estás seguro de que deseas eliminar a ${user.nombre}?")
                .setPositiveButton("Eliminar") { _, _ ->
                    onDeleteClick(user.userId)
                }
                .setNegativeButton("Cancelar", null)
                .show()
        }
    }

    // --- NUEVO DIÁLOGO: VER COMPROBANTE Y APROBAR / RECHAZAR ---
    private fun mostrarDialogoVerificacion(context: Context, user: User, onUpdate: (Int) -> Unit) {
        val layout = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(40, 30, 40, 30)
            gravity = Gravity.CENTER
        }

        val txtDetalles = TextView(context).apply {
            text = "Usuario: ${user.nombre}\nPaquete comprado: ${user.paquete.uppercase()}"
            textSize = 15f
            setPadding(0, 0, 0, 10)
            gravity = Gravity.CENTER
        }
        layout.addView(txtDetalles)

        // Texto indicativo para que el administrador sepa que puede tocar la imagen
        val txtInstruccion = TextView(context).apply {
            text = "🔍 (Toca la imagen para ver en pantalla completa)"
            textSize = 12f
            setTextColor(android.graphics.Color.GRAY)
            setPadding(0, 0, 0, 15)
            gravity = Gravity.CENTER
        }
        layout.addView(txtInstruccion)

        // Variable para almacenar el bitmap en memoria una vez descargado
        var comprobanteBitmap: android.graphics.Bitmap? = null

        // ImageView para cargar y mostrar la foto del comprobante en el diálogo
        val imgComprobante = ImageView(context).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                650 // Alto de la vista previa
            )
            scaleType = ImageView.ScaleType.FIT_CENTER

            // Carga asíncrona de la imagen de Cloudinary
            val urlString = user.urlpagos ?: ""
            if (urlString.isNotEmpty()) {
                Thread {
                    try {
                        val stream = java.net.URL(urlString).openStream()
                        val bitmap = BitmapFactory.decodeStream(stream)
                        comprobanteBitmap = bitmap // Guardamos el bitmap en memoria

                        (context as android.app.Activity).runOnUiThread {
                            setImageBitmap(bitmap)
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }.start()
            }

            // --- CLIC PARA ABRIR PANTALLA COMPLETA ---
            setOnClickListener {
                val bitmapParaMostrar = comprobanteBitmap
                if (bitmapParaMostrar != null) {
                    // Crear un diálogo nativo de pantalla completa absoluta
                    val fullscreenDialog = android.app.Dialog(context, android.R.style.Theme_Black_NoTitleBar_Fullscreen).apply {
                        val fullImageView = ImageView(context).apply {
                            layoutParams = ViewGroup.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT,
                                ViewGroup.LayoutParams.MATCH_PARENT
                            )
                            scaleType = ImageView.ScaleType.FIT_CENTER
                            setImageBitmap(bitmapParaMostrar)
                            setBackgroundColor(android.graphics.Color.BLACK) // Fondo negro para resaltar el comprobante

                            // Al tocar la imagen a pantalla completa, se cierra y regresa al diálogo de decisión
                            setOnClickListener { dismiss() }
                        }
                        setContentView(fullImageView)
                    }
                    fullscreenDialog.show()
                } else {
                    Toast.makeText(context, "Cargando imagen, por favor espera...", Toast.LENGTH_SHORT).show()
                }
            }
        }
        layout.addView(imgComprobante)

        // Determinar automáticamente los puntos a sumar según el plan
        val puntosAsignar = when (user.paquete) {
            "Bronce" -> 50
            "Plata" -> 120
            "Oro" -> 250
            else -> 0
        }

        androidx.appcompat.app.AlertDialog.Builder(context)
            .setTitle("Verificar Pago del Cliente")
            .setView(layout)
            .setPositiveButton("Aprobar ($puntosAsignar pts)") { _, _ ->
                // Flujo de aprobación exitosa: suma puntos y limpia el registro
                onUpdate(puntosAsignar)
            }
            .setNegativeButton("Rechazar (0 pts)") { _, _ ->
                // Flujo de rechazo: no altera puntos, solo limpia el registro
                onUpdate(0)
            }
            .setNeutralButton("Cancelar", null)
            .show()
    }
    // Selector manual tradicional (Se conserva para asignaciones libres)
    private fun mostrarSelectorDePlanes(context: Context, userId: String, onUpdate: (Int) -> Unit) {
        val planes = arrayOf("Bronce: 50 Castv", "Plata: 120 Castv", "Oro: 250 Castv")
        val valores = intArrayOf(50, 120, 250)
        var seleccionado = 0

        androidx.appcompat.app.AlertDialog.Builder(context)
            .setTitle("Seleccionar Paquete")
            .setSingleChoiceItems(planes, 0) { _, which ->
                seleccionado = which
            }
            .setPositiveButton("Aplicar") { _, _ ->
                onUpdate(valores[seleccionado])
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    override fun getItemCount(): Int = filteredList.size

    private fun parsearFecha(fecha: Any?): String {
        return when (fecha) {
            is Long -> {
                val sdf = SimpleDateFormat("dd/MM/yyyy hh:mm a", Locale.getDefault())
                sdf.format(Date(fecha))
            }
            is String -> fecha
            else -> "Sin fecha"
        }
    }
}