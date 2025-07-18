package com.creativem.fulltv.enlinea

import android.app.Application

class FullTVApp : Application() {

    override fun onCreate() {
        super.onCreate()

        // Registrar el manejador del ciclo de vida para controlar el estado "en línea"
        registerActivityLifecycleCallbacks(ActivityLifecycleHandler())
    }
}
