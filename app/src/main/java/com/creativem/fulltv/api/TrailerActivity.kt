package com.creativem.fulltv.api

import android.annotation.SuppressLint
import android.os.Bundle
import android.view.WindowManager
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.appcompat.app.AppCompatActivity
import com.creativem.fulltv.R

class TrailerActivity : AppCompatActivity() {

    private lateinit var webView: WebView

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Forzar pantalla completa absoluta en la TV
        window.setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN)
        supportActionBar?.hide()

        setContentView(R.layout.activity_trailer)

        val youtubeKey = intent.getStringExtra("EXTRA_TRAILER_KEY") ?: ""

        webView = findViewById(R.id.webViewTrailer)

        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            mediaPlaybackRequiresUserGesture = false // Clave para auto-reproducción
            useWideViewPort = true
            loadWithOverviewMode = true

            // Firma de computadora para evadir el bloqueo de Android WebView
            userAgentString = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/121.0.0.0 Safari/537.36"
        }

        webView.webChromeClient = WebChromeClient()

        webView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)

                // 🟢 SOLUCIÓN AL SONIDO (Unmute):
                // Como el documento principal y el iframe comparten el mismo dominio ("youtube-nocookie.com"),
                // el navegador nos permite entrar mediante JavaScript directamente al video y encender el sonido.
                val jsUnmute = """
                    (function() {
                        var iframe = document.getElementById('player');
                        if (iframe) {
                            var innerDoc = iframe.contentDocument || iframe.contentWindow.document;
                            if (innerDoc) {
                                var video = innerDoc.querySelector('video');
                                if (video) {
                                    video.muted = false;
                                    video.volume = 1.0;
                                    video.play();
                                }
                            }
                        }
                    })();
                """.trimIndent()

                // Esperamos 1.5 segundos para que cargue el reproductor interno y forzamos el sonido
                webView.postDelayed({
                    view?.evaluateJavascript(jsUnmute, null)
                }, 1500)
            }
        }

        // 🟢 SOLUCIÓN AL ERROR 153 Y PANTALLA COMPLETA:
        // Metemos el reproductor dentro de un iframe que ocupa el 100% de la pantalla sin bordes ni barras.
        val html = """
            <!DOCTYPE html>
            <html>
            <head>
                <meta name="viewport" content="width=device-width, initial-scale=1.0">
                <style>
                    body, html { margin: 0; padding: 0; width: 100%; height: 100%; overflow: hidden; background-color: black; }
                    iframe { width: 100%; height: 100%; border: none; }
                </style>
            </head>
            <body>
                <iframe id="player" src="https://www.youtube-nocookie.com/embed/$youtubeKey?autoplay=1&mute=0&controls=1&rel=0&showinfo=0&enablejsapi=1" 
                        allow="autoplay; encrypted-media" allowfullscreen></iframe>
            </body>
            </html>
        """.trimIndent()

        // Cargamos el HTML forzando la procedencia oficial para validar la configuración (Evita error 153)
        webView.loadDataWithBaseURL("https://www.youtube-nocookie.com", html, "text/html", "UTF-8", null)
    }

    override fun onBackPressed() {
        webView.loadUrl("about:blank")
        super.onBackPressed()
        finish()
    }

    override fun onDestroy() {
        webView.destroy()
        super.onDestroy()
    }
}