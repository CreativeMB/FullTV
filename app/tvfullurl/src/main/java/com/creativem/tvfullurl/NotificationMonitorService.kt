package com.creativem.tvfullurl

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
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
        startForeground(999, crearNotificacionServicio())
        iniciarMonitoreoRealtime()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // START_STICKY hace que el servicio se reinicie automáticamente si Android lo detiene por memoria
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
        val intent = Intent(this, requireActivityClassDynamic())
        val flagsPendingIntent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }
        val pendingIntent = PendingIntent.getActivity(this, 0, intent, flagsPendingIntent)

        return NotificationCompat.Builder(this, "CANAL_SERVICIO_SILENCIOSO")
            .setContentTitle("Monitoreo de Pedidos Activo")
            .setContentText("Buscando solicitudes en segundo plano...")
            .setSmallIcon(R.drawable.baseline_people_alt_24)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .build()
    }

    @SuppressLint("MissingPermission")
    private fun mostrarNotificacionAdmin(tituloPelicula: String) {
        val intent = Intent(this, requireActivityClassDynamic()).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }

        val flagsPendingIntent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }

        val pendingIntent = PendingIntent.getActivity(this, 0, intent, flagsPendingIntent)

        val builder = NotificationCompat.Builder(this, "CANAL_ADMIN_PEDIDOS")
            .setSmallIcon(R.drawable.baseline_people_alt_24)
            .setContentTitle("🔔 ¡Nuevo Pedido Recibido!")
            .setContentText("Se ha solicitado la película: '$tituloPelicula'")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)

        try {
            with(androidx.core.app.NotificationManagerCompat.from(this)) {
                notify(System.currentTimeMillis().toInt(), builder.build())
            }
        } catch (e: Exception) {
            Log.e("SERVICIO_MONITOREO", "Error al lanzar notificación: ${e.message}")
        }
    }

    private fun crearCanalesNotificacion() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

            // Canal del Servicio Persistente (Silencioso para no molestar)
            val canalServicio = NotificationChannel(
                "CANAL_SERVICIO_SILENCIOSO",
                "Servicio de Monitoreo",
                NotificationManager.IMPORTANCE_MIN
            )
            manager.createNotificationChannel(canalServicio)

            // Canal para las alertas de nuevos pedidos (Prioridad alta)
            val canalAlertas = NotificationChannel(
                "CANAL_ADMIN_PEDIDOS",
                "Alertas de Nuevos Pedidos",
                NotificationManager.IMPORTANCE_HIGH
            )
            manager.createNotificationChannel(canalAlertas)
        }
    }

    // Busca de forma segura la actividad de inicio
    private fun requireActivityClassDynamic(): Class<*> {
        return try {
            Class.forName("com.creativem.tvfullurl.MainActivity") // ⚠️ Reemplaza por la ruta exacta de tu Actividad Principal
        } catch (e: Exception) {
            try {
                Class.forName("com.creativem.tvfullurl.BrowserActivity")
            } catch (ex: Exception) {
                this.javaClass
            }
        }
    }
}