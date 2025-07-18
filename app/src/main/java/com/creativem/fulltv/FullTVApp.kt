package com.creativem.fulltv


import android.app.Application


class FullTVApp : Application() {

    override fun onCreate() {
        super.onCreate()

        // Iniciar gestión automática del estado en línea/desconectado
        UsuarioEstadoManager.iniciar()
    }
}