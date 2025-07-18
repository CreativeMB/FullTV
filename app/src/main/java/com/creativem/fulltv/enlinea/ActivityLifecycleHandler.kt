package com.creativem.fulltv.enlinea

import android.app.Activity
import android.app.Application
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log

class ActivityLifecycleHandler : Application.ActivityLifecycleCallbacks {

    private var actividadVisibleCount = 0
    private val handler = Handler(Looper.getMainLooper())

    override fun onActivityStarted(activity: Activity) {
        actividadVisibleCount++
        Log.d("LifecycleHandler", "START: ${activity.localClassName} - visibles: $actividadVisibleCount")

        if (actividadVisibleCount == 1) {
            Log.d("LifecycleHandler", "🎯 App pasó a primer plano. Marcando EN LÍNEA.")
            UsuarioEstadoManager.marcarUsuarioEnLineaDesdeApp(true)
        }
    }

    override fun onActivityStopped(activity: Activity) {
        actividadVisibleCount--
        if (actividadVisibleCount < 0) actividadVisibleCount = 0

        Log.d("LifecycleHandler", "STOP: ${activity.localClassName} - visibles: $actividadVisibleCount")

        handler.postDelayed({
            if (actividadVisibleCount == 0) {
                Log.d("LifecycleHandler", "📴 App en segundo plano. Marcando FUERA DE LÍNEA.")
                UsuarioEstadoManager.marcarUsuarioEnLineaDesdeApp(false)
            } else {
                Log.d("LifecycleHandler", "✅ Aún hay actividades visibles. NO marcar fuera de línea.")
            }
        }, 800)
    }

    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {}
    override fun onActivityResumed(activity: Activity) {}
    override fun onActivityPaused(activity: Activity) {}
    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}
    override fun onActivityDestroyed(activity: Activity) {}
}