package com.creativem.fulltv.tv

import android.os.Bundle
import android.util.Log
import android.view.View
import androidx.core.content.ContextCompat
import androidx.leanback.app.RowsSupportFragment
import androidx.leanback.widget.*
import com.creativem.fulltv.R
import com.creativem.fulltv.data.Movie
import com.google.firebase.firestore.FirebaseFirestore

class TvFragment : RowsSupportFragment() {

    private val db = FirebaseFirestore.getInstance()
    private val channels = ArrayObjectAdapter(ListRowPresenter())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        loadTvChannels() // Cargar los canales antes de asignar el adapter
        adapter = channels
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Establecer el color de fondo del fragmento
        view.setBackgroundColor(ContextCompat.getColor(requireContext(), R.color.colorPrimary))
    }

    private fun loadTvChannels() {
        val listRowAdapter = ArrayObjectAdapter(CardPresenterTV())

        db.collection("tv") // Cargar desde la colección "fragment_tv" en Firebase
            .orderBy("createdAt")
            .get()
            .addOnSuccessListener { documents ->
                for (document in documents) {
                    val channel = document.toObject(Movie::class.java) // Mapeo a Movie
                    listRowAdapter.add(channel)
                }
                if (listRowAdapter.size() > 0) {
                    val header = HeaderItem(0, "Canales en Vivo")
                    channels.add(ListRow(header, listRowAdapter))
                }
            }
            .addOnFailureListener { e ->
                Log.e("TvFragment", "Error cargando canales", e)
            }
    }
}
