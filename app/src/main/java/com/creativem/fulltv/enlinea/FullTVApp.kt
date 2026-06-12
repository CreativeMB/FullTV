package com.creativem.fulltv.enlinea

import android.app.Application
import android.util.Log
import com.yausername.youtubedl_android.YoutubeDL

class FullTVApp : Application() {

    override fun onCreate() {
        super.onCreate()

        // 👉 AÑADE LA INICIALIZACIÓN DEL EXTRACTOR AQUÍ
        try {
            YoutubeDL.getInstance().init(this)
            Log.i("FullTVApp", "YoutubeDL inicializado correctamente en la App")
        } catch (e: Exception) {
            Log.e("FullTVApp", "Fallo al inicializar YoutubeDL", e)
        }


        // Registrar el manejador del ciclo de vida para controlar el estado "en línea"
        registerActivityLifecycleCallbacks(ActivityLifecycleHandler())
    }
}
