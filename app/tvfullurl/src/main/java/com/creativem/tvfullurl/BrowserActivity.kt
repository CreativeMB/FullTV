package com.creativem.tvfullurl

import android.os.Bundle
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.EditText
import androidx.appcompat.app.AppCompatActivity

class BrowserActivity : AppCompatActivity() {

    private lateinit var webView: WebView
    private lateinit var etUrl: EditText
    private lateinit var btnGo: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_browser)

        webView = findViewById(R.id.webView)
        etUrl = findViewById(R.id.etUrl)
        btnGo = findViewById(R.id.btnGo)

        // Configuración de WebView
        val settings = webView.settings
        settings.javaScriptEnabled = true // Necesario para la mayoría de webs modernas
        settings.domStorageEnabled = true // Permite cargar sitios con almacenamiento local

        // Importante: WebViewClient evita que los enlaces se abran en Chrome externo
        webView.webViewClient = WebViewClient()

        // Permite manejar el progreso de carga y otros eventos visuales
        webView.webChromeClient = WebChromeClient()

        // Carga una página inicial
        webView.loadUrl("https://www.google.com")

        btnGo.setOnClickListener {
            val url = etUrl.text.toString()
            if (url.isNotEmpty()) {
                // Asegurarse de que la URL tenga el protocolo http/https
                val finalUrl = if (url.startsWith("http")) url else "https://$url"
                webView.loadUrl(finalUrl)
            }
        }
    }

    // Permitir volver atrás en el historial del navegador con el botón físico del móvil
    override fun onBackPressed() {
        if (webView.canGoBack()) {
            webView.goBack()
        } else {
            super.onBackPressed()
        }
    }
}