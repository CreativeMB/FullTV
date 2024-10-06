package com.creativem.fulltv.home

import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.creativem.fulltv.R
import com.google.firebase.firestore.FirebaseFirestore

class PedidosActivity : AppCompatActivity() {
    private lateinit var texpedido: EditText
    private lateinit var butpedido: Button
    private val firestore = FirebaseFirestore.getInstance()
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.pedidos) // Asegúrate de tener este layout creado
        // Inicializar vistas
        texpedido = findViewById(R.id.texpedido)
        butpedido = findViewById(R.id.butpedido)

        // Configurar el click del botón
        butpedido.setOnClickListener {
            enviarTextoAFirestore()
        }
    }

    private fun enviarTextoAFirestore() {
        val texto = texpedido.text.toString().trim()

        if (texto.isNotEmpty()) {
            // Crear un nuevo objeto de datos
            val datos = hashMapOf(
                "contenido" to texto
            )

            // Enviar datos a Firestore
            firestore.collection("pedidosmovies")
                .add(datos)
                .addOnSuccessListener {
                    Toast.makeText(this, "Pedido enviado exitosamente", Toast.LENGTH_SHORT).show()
                    texpedido.text.clear() // Limpiar el campo de texto
                }
                .addOnFailureListener { e ->
                    Toast.makeText(this, "Error al enviar el texto: ${e.message}", Toast.LENGTH_SHORT).show()
                }
        } else {
            Toast.makeText(this, "Por favor, ingresa un texto", Toast.LENGTH_SHORT).show()
        }
    }
}