package com.creativem.fulltv.tv

import android.content.Context
import android.content.Intent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.creativem.fulltv.R
import com.creativem.fulltv.data.Movie

class TvAdapter(
    private val context: Context,
    private var tvList: MutableList<Movie>,
    private val clickListener: (Movie) -> Unit // ✅ Función lambda para manejar clics
) : RecyclerView.Adapter<TvAdapter.TvViewHolder>() {

    private var selectedPosition = RecyclerView.NO_POSITION

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TvViewHolder {
        val view = LayoutInflater.from(context).inflate(R.layout.item_tv, parent, false)
        return TvViewHolder(view)


    }

    override fun onBindViewHolder(holder: TvViewHolder, position: Int) {
        val tvItem = tvList[position]
        holder.title.text = tvItem.title

        // ✅ Cargar la imagen en miniatura con Glide
        Glide.with(context)
            .load(tvItem.imageUrl) // URL de la imagen
            .placeholder(R.drawable.icono) // Imagen temporal mientras carga
            .error(R.drawable.icono) // Imagen de error si falla la carga
            .into(holder.thumbnail)

        // ✅ Cambiar color de fondo si el ítem está seleccionado
        holder.itemView.setBackgroundColor(
            if (position == selectedPosition)
                ContextCompat.getColor(context, R.color.colorhover2)
            else
                ContextCompat.getColor(context, R.color.colorNotSelected)
        )

        // ✅ Detectar clic para cambiar el color de fondo y actualizar `selectedPosition`
        holder.itemView.setOnClickListener {
            notifyItemChanged(selectedPosition) // Restablece el ítem previamente seleccionado
            selectedPosition = position // Actualiza la nueva posición seleccionada
            notifyItemChanged(selectedPosition) // Notifica el cambio en el nuevo ítem seleccionado

            clickListener(tvItem) // Llamamos la función de clic con el objeto Movie
        }

        // ✅ Detectar cuando el ítem gana o pierde el foco (para navegación con teclado/control remoto)
        holder.itemView.setOnFocusChangeListener { _, hasFocus ->
            holder.itemView.setBackgroundColor(
                if (hasFocus) ContextCompat.getColor(context, R.color.colorhover2)
                else ContextCompat.getColor(context, R.color.colorNotSelected)
            )
        }
    }


    override fun getItemCount(): Int = tvList.size

    // ✅ Agregamos esta función para actualizar la lista de películas dinámicamente
    fun updateData(newTvList: List<Movie>) {
        tvList.clear()  // Limpiar lista actual
        tvList.addAll(newTvList)  // Agregar nuevos elementos
        notifyDataSetChanged()  // Notificar cambios al RecyclerView
    }

    class TvViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val thumbnail: ImageView = itemView.findViewById(R.id.imgThumbnail)
        val title: TextView = itemView.findViewById(R.id.tvTitle)
    }
}
