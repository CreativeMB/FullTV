package com.creativem.fulltv

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide

class ActoresAdapter(private val actores: List<CastMember>) :
    RecyclerView.Adapter<ActoresAdapter.ActorViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ActorViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_actor, parent, false)
        return ActorViewHolder(view)
    }

    override fun onBindViewHolder(holder: ActorViewHolder, position: Int) {
        val actor = actores[position]
        holder.nombre.text = actor.name
        holder.personaje.text = actor.character

        val url = "https://image.tmdb.org/t/p/w200${actor.profile_path}"
        Glide.with(holder.itemView)
            .load(url)
            .placeholder(R.drawable.icono)
            .into(holder.foto)
    }

    override fun getItemCount() = actores.size

    class ActorViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val foto: ImageView = view.findViewById(R.id.imgActor)
        val nombre: TextView = view.findViewById(R.id.txtNombre)
        val personaje: TextView = view.findViewById(R.id.txtPersonaje)
    }
}
