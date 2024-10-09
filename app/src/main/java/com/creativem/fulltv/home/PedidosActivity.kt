package com.creativem.fulltv.home

import android.content.Intent
import android.os.Bundle
import android.speech.RecognizerIntent
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.creativem.fulltv.R
import com.google.firebase.firestore.FirebaseFirestore

class PedidosActivity : AppCompatActivity() {
    private lateinit var texpedido: EditText
    private lateinit var butpedido: Button
    private lateinit var butMicrofono: Button
    private val firestore = FirebaseFirestore.getInstance()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.pedidos) // Asegúrate de tener este layout creado

        // Inicializar vistas
        texpedido = findViewById(R.id.texpedido)
        butpedido = findViewById(R.id.butpedido)
        butMicrofono = findViewById(R.id.butMicrofono)

        // Configurar el click del botón para enviar texto
        butpedido.setOnClickListener {
            enviarTextoAFirestore()
        }

        // Configurar el click del botón para activar el micrófono
        butMicrofono.setOnClickListener {
            iniciarReconocimientoVoz()
        }
    }

    private fun enviarTextoAFirestore() {
        val texto = texpedido.text.toString().trim()

        if (texto.isNotEmpty()) {
            // Crear un nuevo objeto de datos
            val datos = hashMapOf("title" to texto)

            // Enviar datos a Firestore
            firestore.collection("pedidosmovies")
                .add(datos)
                .addOnSuccessListener {
                    Toast.makeText(this, "Pedido enviado exitosamente", Toast.LENGTH_SHORT).show()
                    texpedido.text.clear() // Limpiar el campo de texto
                    finish()
                }
                .addOnFailureListener { e ->
                    Toast.makeText(this, "Error al enviar el texto: ${e.message}", Toast.LENGTH_SHORT).show()
                }
        } else {
            Toast.makeText(this, "Por favor, ingresa un texto", Toast.LENGTH_SHORT).show()
        }
    }

    private fun iniciarReconocimientoVoz() {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_PROMPT, "Habla ahora...")
        }

        try {
            startActivityForResult(intent, RECONOCIMIENTO_VOZ)
        } catch (e: Exception) {
            Toast.makeText(this, "Error al iniciar el reconocimiento de voz: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode == RECONOCIMIENTO_VOZ && resultCode == RESULT_OK) {
            val resultados = data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
            resultados?.let {
                texpedido.setText(it[0]) // Establecer el texto en el EditText
            }
        }
    }

    companion object {
        private const val RECONOCIMIENTO_VOZ = 1001 // Código de solicitud para el reconocimiento de voz
    }
}