package com.creativem.fulltv.tv

import com.creativem.fulltv.principal.Modelo

object TvRepository {
    // 🟢 Constantes globales de tu Firebase Realtime Database
    var lastPlayedChannel: Modelo? = null
    const val FIREBASE_DB_URL = "https://corario-16991-default-rtdb.firebaseio.com/"
    const val FIREBASE_PATH = "tv/urliptv"

    // 🟢 Variable estática en memoria para compartir los canales entre TvActivity y PlayerTv
    @Volatile
    var channelListMaster: List<Modelo> = emptyList()
}