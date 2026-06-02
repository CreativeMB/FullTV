package com.creativem.fulltv.tv

import android.annotation.SuppressLint
import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.creativem.fulltv.R
import com.creativem.fulltv.principal.Modelo

class TvMenuAdapter(
    private val context: Context,
    private var tvList: MutableList<Modelo>,
    private val clickListener: (Modelo) -> Unit
) : RecyclerView.Adapter<TvMenuAdapter.TvViewHolder>() {

    private var selectedPosition = RecyclerView.NO_POSITION

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TvViewHolder {
        val view = LayoutInflater.from(context).inflate(R.layout.item_menu_tv, parent, false)
        return TvViewHolder(view)
    }

    override fun onBindViewHolder(holder: TvViewHolder, @SuppressLint("RecyclerView") position: Int) {
        val tvItem = tvList[position]
        holder.title.text = tvItem.title

        if (!tvItem.imageUrl.isNullOrEmpty()) {
            Glide.with(holder.imageView.context)
                .load(tvItem.imageUrl)
                .placeholder(R.drawable.icono)
                .error(R.drawable.icono)
                .into(holder.imageView)
        } else {
            holder.imageView.setImageResource(R.drawable.icono)
        }

        holder.itemView.setOnFocusChangeListener { _, hasFocus ->
            holder.itemView.setBackgroundColor(
                if (hasFocus) ContextCompat.getColor(context, R.color.colorhover2)
                else ContextCompat.getColor(context, R.color.colorNotSelected)
            )

            if (hasFocus) {
                // Llama a la función en PlayerTv si el context es una instancia válida
                (context as? PlayerTv)?.reiniciarTemporizadorMenu()
            }
        }



        // ✅ Click para seleccionar y enviar la película
        holder.itemView.setOnClickListener {
            val previousSelected = selectedPosition
            selectedPosition = position
            notifyItemChanged(previousSelected) // Actualiza el anterior
            notifyItemChanged(selectedPosition) // Actualiza el nuevo

            clickListener(tvItem)
        }


        // ✅ Cambia color al recibir foco (para control remoto/teclado)
        holder.itemView.setOnFocusChangeListener { _, hasFocus ->
            holder.itemView.setBackgroundColor(
                if (hasFocus) ContextCompat.getColor(context, R.color.colorhover2)
                else ContextCompat.getColor(context, R.color.colorNotSelected)
            )
        }
    }

    override fun getItemCount(): Int = tvList.size

    // ✅ Método para actualizar la lista de películas
    fun updateData(newTvList: List<Modelo>) {
        tvList.clear()
        tvList.addAll(newTvList)
        notifyDataSetChanged()
    }

    class TvViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val imageView: ImageView = itemView.findViewById(R.id.imagenPelicula)
        val title: TextView = itemView.findViewById(R.id.tvTitle)
    }
}
