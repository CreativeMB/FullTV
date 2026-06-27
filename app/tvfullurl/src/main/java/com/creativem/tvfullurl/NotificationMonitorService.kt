package com.creativem.tvfullurl

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.net.Uri
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener

class NotificationMonitorService : Service() {

    private val databaseRef = FirebaseDatabase.getInstance().reference.child("movies")
    private val solicitudesConocidas = mutableSetOf<String>()
    private var esPrimeraCarga = true

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        crearCanalesNotificacion()

        // Iniciamos el servicio en primer plano con una notificación constante
        try {
            startForeground(999, crearNotificacionServicio())
        } catch (e: Exception) {
            Log.e("SERVICIO_MONITOREO", "Error al iniciar startForeground: ${e.message}")
        }

        iniciarMonitoreoRealtime()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_STICKY
    }

    private fun iniciarMonitoreoRealtime() {
        databaseRef.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val solicitudesCargaActual = mutableListOf<Pair<String, String>>()

                for (child in snapshot.children) {
                    val solicitudesNode = child.child("solicitudes")
                    val title = child.child("title").getValue(String::class.java) ?: "Sin título"

                    if (solicitudesNode.exists() && solicitudesNode.hasChildren()) {
                        for (solicitudChild in solicitudesNode.children) {
                            val solId = solicitudChild.child("id").getValue(String::class.java) ?: solicitudChild.key ?: ""
                            solicitudesCargaActual.add(Pair(solId, title))
                        }
                    }
                }

                if (!esPrimeraCarga) {
                    val nuevasSolicitudes = solicitudesCargaActual.filter { it.first !in solicitudesConocidas }
                    for (nueva in nuevasSolicitudes) {
                        mostrarNotificacionAdmin(nueva.second)
                    }
                } else {
                    esPrimeraCarga = false
                }

                solicitudesConocidas.clear()
                solicitudesConocidas.addAll(solicitudesCargaActual.map { it.first })
            }

            override fun onCancelled(error: DatabaseError) {
                Log.e("SERVICIO_MONITOREO", "Error de conexión: ${error.message}")
            }
        })
    }

    private fun crearNotificacionServicio(): Notification {
        val pendingIntent = obtenerPendingIntentDeInicio()


        return NotificationCompat.Builder(this, "CANAL_SERVICIO_SILENCIOSO")
            .setSmallIcon(R.drawable.icono_notificacion) // Se muestra solo tu icono
            .setContentTitle(" ")  // Espacio en blanco para reducir el tamaño al mínimo
            .setContentText("")    // Sin texto de descripción
            .setShowWhen(false)    // Oculta la hora/reloj de la notificación
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_MIN) // Mínima prioridad
            .setSilent(true)       // Silencio absoluto
            .build()
    }

    @SuppressLint("MissingPermission")
    private fun mostrarNotificacionAdmin(tituloPelicula: String) {
        val pendingIntent = obtenerPendingIntentDeInicio()

        // Ruta del sonido para compatibilidad con versiones anteriores a Android 8.0
        val soundUri = Uri.parse("android.resource://$packageName/${R.raw.pedido}")

        val builder = NotificationCompat.Builder(this, "CANAL_ADMIN_PEDIDOS_V2") // Apunta al nuevo canal V2
            .setSmallIcon(R.drawable.icono_notificacion)
            .setContentTitle("🔔 ¡Activar pelicula!")
            .setContentText("$tituloPelicula")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .setSound(soundUri) // Soporte para versiones antiguas de Android

        try {
            with(androidx.core.app.NotificationManagerCompat.from(this)) {
                // Generamos un ID único basado en el texto del título de la película.
                // Usamos kotlin.math.abs para asegurar que el número siempre sea positivo.
                val notificationId = kotlin.math.abs(tituloPelicula.hashCode())

                notify(notificationId, builder.build())
            }
        } catch (e: Exception) {
            Log.e("SERVICIO_MONITOREO", "Error al lanzar notificación: ${e.message}")
        }
    }

    private fun crearCanalesNotificacion() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

            val canalServicio = NotificationChannel(
                "CANAL_SERVICIO_SILENCIOSO",
                "Servicio de Monitoreo",
                NotificationManager.IMPORTANCE_LOW
            )
            manager.createNotificationChannel(canalServicio)
            val soundUri = Uri.parse("android.resource://$packageName/${R.raw.pedido}")

            // 3. Atributos de audio requeridos para el canal
            val audioAttributes = AudioAttributes.Builder()
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                .build()
            // 4. Creamos un nuevo ID de canal ("CANAL_ADMIN_PEDIDOS_V2") para forzar el cambio de sonido
            val canalAlertas = NotificationChannel(
                "CANAL_ADMIN_PEDIDOS_V2",
                "Alertas de Nuevos Pedidos",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                // Asignamos el sonido personalizado al canal
                setSound(soundUri, audioAttributes)
                enableLights(true)
                enableVibration(true)
            }
            manager.createNotificationChannel(canalAlertas)
        }
    }

    // Obtiene de manera segura el PendingIntent para abrir la aplicación, sin importar su nombre de paquete o clase
    private fun obtenerPendingIntentDeInicio(): PendingIntent? {
        val launchIntent = packageManager.getLaunchIntentForPackage(packageName) ?: return null
        launchIntent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP

        val flagsPendingIntent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }
        return PendingIntent.getActivity(this, 0, launchIntent, flagsPendingIntent)
    }

}