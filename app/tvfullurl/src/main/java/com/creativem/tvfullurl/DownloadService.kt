package com.creativem.tvfullurl

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.*
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream

class DownloadService : Service() {

    private val client = OkHttpClient()
    private val serviceJob = Job()
    private val serviceScope = CoroutineScope(Dispatchers.IO + serviceJob)

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val url = intent?.getStringExtra("url") ?: return START_NOT_STICKY

        val channelId = "DownloadChannel"
        createNotificationChannel(channelId)

        val notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle("Descargando...")
            .setContentText("En progreso")
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setOngoing(true)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                1,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            )
        } else {
            startForeground(1, notification)
        }

        serviceScope.launch {
            downloadFile(url)
            stopSelf()
        }

        return START_NOT_STICKY
    }

    private fun createNotificationChannel(channelId: String) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "Descargas",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    private fun sendProgress(url: String, progress: Int) {
        val intent = Intent("DOWNLOAD_PROGRESS")
        intent.putExtra("url", url)
        intent.putExtra("progress", progress)
        sendBroadcast(intent)
    }

    private fun sendCompleted(url: String, path: String) {
        val intent = Intent("DOWNLOAD_COMPLETED")
        intent.putExtra("url", url)
        intent.putExtra("path", path)
        sendBroadcast(intent)
    }

    private fun sendError(url: String, error: String) {
        val intent = Intent("DOWNLOAD_ERROR")
        intent.putExtra("url", url)
        intent.putExtra("error", error)
        sendBroadcast(intent)
    }

    private fun downloadFile(url: String) {
        try {
            Log.d("DownloadService", "Iniciando descarga: $url")

            val request = Request.Builder().url(url).build()
            val response = client.newCall(request).execute()

            if (!response.isSuccessful) {
                sendError(url, "Respuesta no válida: ${response.code}")
                Log.e("DownloadService", "Fallo: ${response.code}")
                return
            }

            val body = response.body ?: return
            val contentLength = body.contentLength()
            Log.d("DownloadService", "Tamaño total: $contentLength")

            val fileName = "video_${System.currentTimeMillis()}.mp4"
            val outputDir = getExternalFilesDir(null)
            val outputFile = File(outputDir, fileName)

            val buffer = ByteArray(8 * 1024)
            var downloaded = 0L
            var read: Int

            body.byteStream().use { input ->
                FileOutputStream(outputFile).use { output ->
                    while (input.read(buffer).also { read = it } != -1) {
                        output.write(buffer, 0, read)
                        downloaded += read

                        if (contentLength > 0) {
                            val progress = ((downloaded * 100) / contentLength).toInt()
                            sendProgress(url, progress)
                            Log.d("DownloadService", "Progreso: $progress%")
                        } else {
                            Log.d("DownloadService", "Descargando... $downloaded bytes")
                            sendProgress(url, 0)
                        }
                    }
                }
            }

            sendCompleted(url, outputFile.absolutePath)
            Log.d("DownloadService", "Descarga completada: ${outputFile.absolutePath}")

            val successNotification = NotificationCompat.Builder(this, "DownloadChannel")
                .setContentTitle("✅ Descarga completa")
                .setContentText("Guardado en: ${outputFile.absolutePath}")
                .setSmallIcon(android.R.drawable.stat_sys_download_done)
                .build()

            val manager = getSystemService(NotificationManager::class.java)
            manager.notify(2, successNotification)

        } catch (e: Exception) {
            sendError(url, e.localizedMessage ?: "Error desconocido")
            Log.e("DownloadService", "Error: ${e.localizedMessage}")

            val errorNotification = NotificationCompat.Builder(this, "DownloadChannel")
                .setContentTitle("❌ Error en la descarga")
                .setContentText(e.localizedMessage)
                .setSmallIcon(android.R.drawable.stat_notify_error)
                .build()

            val manager = getSystemService(NotificationManager::class.java)
            manager.notify(3, errorNotification)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceJob.cancel()
    }
}
