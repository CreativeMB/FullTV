package com.creativem.fulltv

import android.content.Intent
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.ui.graphics.Color
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.snackbar.Snackbar
import com.google.firebase.database.collection.LLRBNode
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase

class MoviesBottomSheetFragment : BottomSheetDialogFragment() {
    private lateinit var recyclerView: RecyclerView
    private lateinit var moviesAdapter: MovieAdapter
    private lateinit var firestore: FirebaseFirestore

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_movies_bottom_sheet, container, false)
        recyclerView = view.findViewById(R.id.recycler_view_movies)

        recyclerView.layoutManager = LinearLayoutManager(context, LinearLayoutManager.HORIZONTAL, false)

        firestore = Firebase.firestore

        moviesAdapter = MovieAdapter(mutableListOf()) { url ->
            if (url.isNotEmpty()) {
                val intent = Intent(requireContext(), PlayerActivity::class.java)
                intent.putExtra("EXTRA_STREAM_URL", url)
                startActivity(intent)
            } else {
                Snackbar.make(recyclerView, "La URL del stream está vacía", Snackbar.LENGTH_LONG).show()
            }
        }

        recyclerView.adapter = moviesAdapter
        loadMoviesFromFirestore()

        return view
    }

    private fun loadMoviesFromFirestore() {
        firestore.collection("movies")
            .orderBy("createdAt", com.google.firebase.firestore.Query.Direction.DESCENDING)
            .get()
            .addOnSuccessListener { snapshot ->
                for (document in snapshot.documents) {
                    val title = document.getString("title") ?: "Sin título"
                    val synopsis = document.getString("synopsis") ?: "Sin sinopsis"
                    val imageUrl = document.getString("imageUrl") ?: ""
                    val streamUrl = document.getString("streamUrl") ?: ""
                    val createdAt = document.getTimestamp("createdAt")

                    val movie = Movie(title, synopsis, imageUrl, streamUrl, createdAt!!)
                    moviesAdapter.addMovie(movie)
                }
            }
    }

    override fun onStart() {
        super.onStart()
        dialog?.window?.apply {
            setLayout(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        }
    }
}