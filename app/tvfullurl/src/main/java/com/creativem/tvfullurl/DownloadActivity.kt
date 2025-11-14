package com.creativem.tvfullurl

import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

class DownloadActivity : AppCompatActivity() {

    private lateinit var adapter: VideoDownloadAdapter
    private val videoList = mutableListOf<VideoDownload>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_download)

        val recyclerView = findViewById<RecyclerView>(R.id.recyclerDownloads)
        adapter = VideoDownloadAdapter(videoList)
        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = adapter

        val inputUrl = findViewById<EditText>(R.id.inputUrl)
        val btnDownload = findViewById<Button>(R.id.btnDownload)

        btnDownload.setOnClickListener {
            val url = inputUrl.text.toString().trim()
            if (url.isNotEmpty()) {
                val video = VideoDownload(url, "Iniciando...", 0)
                adapter.addVideo(video)

                val intent = Intent(this, DownloadService::class.java)
                intent.putExtra("url", url)
                startService(intent)
            }
        }
    }

    private val progressReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val url = intent?.getStringExtra("url") ?: return
            when (intent.action) {
                "DOWNLOAD_PROGRESS" -> {
                    val progress = intent.getIntExtra("progress", 0)
                    adapter.updateProgress(url, progress, "Descargando")
                }
                "DOWNLOAD_COMPLETED" -> {
                    adapter.updateProgress(url, 100, "Completado ✅")
                }
                "DOWNLOAD_ERROR" -> {
                    val error = intent.getStringExtra("error") ?: "Error desconocido"
                    adapter.updateProgress(url, 0, "Error ❌: $error")
                }
            }
        }
    }

    @SuppressLint("UnspecifiedRegisterReceiverFlag")
    override fun onResume() {
        super.onResume()
        val filter = IntentFilter().apply {
            addAction("DOWNLOAD_PROGRESS")
            addAction("DOWNLOAD_COMPLETED")
            addAction("DOWNLOAD_ERROR")
        }
        registerReceiver(progressReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
    }

    override fun onPause() {
        super.onPause()
        unregisterReceiver(progressReceiver)
    }
}
