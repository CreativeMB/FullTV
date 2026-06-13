package com.creativem.tvfullurl.Fragment

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.*
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.recyclerview.widget.LinearLayoutManager
import com.creativem.cineflexurl.modelo.tv
import com.creativem.tvfullurl.ChannelAdapter
import com.creativem.tvfullurl.databinding.FragmentTvBinding
import com.google.firebase.database.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.URL

class TvFragment : Fragment() {

    private lateinit var binding: FragmentTvBinding
    private lateinit var database: DatabaseReference
    private var player: ExoPlayer? = null

    // channelList guarda los datos reales de Firebase
    private var channelList: MutableList<tv> = mutableListOf()
    private lateinit var channelAdapter: ChannelAdapter

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        binding = FragmentTvBinding.inflate(inflater, container, false)
        database = FirebaseDatabase.getInstance().getReference("tv")

        // 1. Inicializar ExoPlayer
        player = ExoPlayer.Builder(requireContext()).build()
        binding.playerView.player = player

        // 2. Configurar RecyclerView y Adaptador
        // Cambia esto en el onCreateView:
        channelAdapter = ChannelAdapter(channelList,
            onDeleteClick = { channel -> deleteMovie(channel) }, // Pasamos el objeto completo
            onItemClick = { channel -> playChannel(channel.streamUrl) }
        )

        binding.recyclerViewTV.layoutManager = LinearLayoutManager(context)
        binding.recyclerViewTV.adapter = channelAdapter

        // 3. Configurar Buscador (Filtro en tiempo real)
        binding.searchEditText.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                val query = s.toString().lowercase().trim()
                filterChannels(query)
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        // 4. Botón: Cargar lista desde URL (Añadir a lo existente)
        binding.btnImportM3u.setOnClickListener {
            val url = binding.urlEditText.text.toString().trim()
            if (url.isNotEmpty()) {
                loadM3UFromUrl(url)
            } else {
                Toast.makeText(context, "Por favor pega una URL válida", Toast.LENGTH_SHORT).show()
            }
        }

        // 5. Botón: Limpiar todo el nodo de TV (CON CONFIRMACIÓN)
        binding.btnClearAll.setOnClickListener {
            val builder = androidx.appcompat.app.AlertDialog.Builder(requireContext())
            builder.setTitle("¡Atención!")
            builder.setMessage("¿Estás seguro de que quieres borrar TODOS los canales de la lista? Esta acción no se puede deshacer.")

            // Si el moderador confirma
            builder.setPositiveButton("Sí, borrar todo") { dialog, _ ->
                database.removeValue().addOnSuccessListener {
                    Toast.makeText(context, "Lista vaciada completamente", Toast.LENGTH_SHORT).show()
                }
                dialog.dismiss()
            }

            // Si el moderador cancela
            builder.setNegativeButton("Cancelar") { dialog, _ ->
                dialog.dismiss()
            }

            val alert = builder.create()
            alert.show()
        }

        loadData()
        return binding.root
    }

    // Función de filtrado optimizada
    private fun filterChannels(query: String) {
        val filtered = if (query.isEmpty()) {
            channelList
        } else {
            channelList.filter { it.title.lowercase().contains(query) }
        }
        channelAdapter.updateList(filtered)
    }

    private fun loadM3UFromUrl(urlString: String) {
        binding.btnImportM3u.isEnabled = false
        Toast.makeText(context, "Procesando lista...", Toast.LENGTH_LONG).show()

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val content = URL(urlString).readText()
                val lines = content.lines()

                lines.forEachIndexed { index, line ->
                    if (line.startsWith("#EXTINF")) {
                        val title = line.substringAfterLast(",").trim()
                        val logoUrl = if (line.contains("tvg-logo=")) {
                            line.substringAfter("tvg-logo=\"").substringBefore("\"")
                        } else ""

                        val streamUrl = lines.getOrNull(index + 1)?.trim() ?: ""

                        if (streamUrl.startsWith("http")) {
                            val id = database.push().key ?: ""
                            database.child(id).setValue(tv(id, title, logoUrl, streamUrl))
                        }
                    }
                }

                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "Canales añadidos", Toast.LENGTH_SHORT).show()
                    binding.urlEditText.text?.clear()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "Error de red: ${e.message}", Toast.LENGTH_LONG).show()
                }
            } finally {
                withContext(Dispatchers.Main) { binding.btnImportM3u.isEnabled = true }
            }
        }
    }

    private fun playChannel(url: String) {
        player?.apply {
            setMediaItem(MediaItem.fromUri(url))
            prepare()
            play()
        }
    }

    private fun loadData() {
        // Usamos addValueEventListener para que la UI se actualice sola al borrar/añadir
        database.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                channelList.clear()
                snapshot.children.forEach {
                    it.getValue(tv::class.java)?.let { channel -> channelList.add(channel) }
                }

                // Actualizar el adaptador con la nueva lista de Firebase
                // Si hay algo en el buscador, respetamos el filtro
                val currentQuery = binding.searchEditText.text.toString()
                filterChannels(currentQuery)
            }
            override fun onCancelled(error: DatabaseError) {
                Toast.makeText(context, "Error al leer Firebase", Toast.LENGTH_SHORT).show()
            }
        })
    }

    private fun deleteMovie(channel: tv) {
        val builder = androidx.appcompat.app.AlertDialog.Builder(requireContext())
        builder.setTitle("Eliminar Canal")
        builder.setMessage("¿Estás seguro de que quieres eliminar \"${channel.title}\"?")

        builder.setPositiveButton("Eliminar") { dialog, _ ->
            // Aquí es donde realmente se borra de Firebase
            database.child(channel.id).removeValue().addOnSuccessListener {
                Toast.makeText(context, "Canal eliminado", Toast.LENGTH_SHORT).show()
            }
            dialog.dismiss()
        }

        builder.setNegativeButton("Cancelar") { dialog, _ ->
            dialog.dismiss()
        }

        val alert = builder.create()
        alert.show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        player?.release()
        player = null
    }
}