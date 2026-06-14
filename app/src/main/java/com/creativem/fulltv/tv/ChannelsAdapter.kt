package com.creativem.fulltv.tv

import android.graphics.Color
import android.os.Build
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.DecelerateInterpolator
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.DecodeFormat
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.request.RequestOptions
import com.creativem.fulltv.R
import com.creativem.fulltv.principal.Modelo

class ChannelsAdapter(
    private var channelList: List<Modelo>,
    private val onItemClick: (Modelo) -> Unit,
    private val onFocusChange: (Modelo) -> Unit,
    private val onLongClick: (Modelo) -> Unit // NUEVO: Callback para favoritos
) : RecyclerView.Adapter<ChannelsAdapter.ChannelViewHolder>() {

    // Cache local de IDs favoritos para pintar la estrella rápido
    private var favoriteIds: Set<String> = emptySet()

    private val glideOptions = RequestOptions()
        .format(DecodeFormat.PREFER_RGB_565)
        .diskCacheStrategy(DiskCacheStrategy.ALL)
        .placeholder(R.drawable.pelifondo)
        .error(R.drawable.pelifondo)
        .dontAnimate()

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ChannelViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_tv, parent, false)
        return ChannelViewHolder(view)
    }

    override fun onBindViewHolder(holder: ChannelViewHolder, position: Int) {
        val canal = channelList[position]

        holder.txtTitle.text = canal.title
        holder.txtStatus.text = "GRATIS"
        holder.txtBadge.text = "TV"

        // MOSTRAR/OCULTAR ESTRELLA
        if (favoriteIds.contains(canal.id)) {
            holder.imgStar.visibility = View.VISIBLE
        } else {
            holder.imgStar.visibility = View.GONE
        }

        Glide.with(holder.itemView.context)
            .load(canal.imageUrl)
            .apply(glideOptions)
            .into(holder.imgMovie)

        // IMPORTANTE: Hacemos que el ítem pueda recibir el foco directamente por código
        holder.itemView.isFocusable = true
        // LLAMAMOS A TU COLOR "cine_dorado" AQUÍ:
        val colorDorado = androidx.core.content.ContextCompat.getColor(holder.itemView.context, R.color.cine_dorado)
        // CREAMOS EL BORDE Y FONDO VISUAL POR CÓDIGO (Reemplaza al XML)
        val focusedBackground = android.graphics.drawable.GradientDrawable().apply {
            shape = android.graphics.drawable.GradientDrawable.RECTANGLE
            setStroke(8, colorDorado) // Grosor del borde (8 píxeles) y color Cyan
            setColor(Color.parseColor("#3300FFFF")) // Fondo un poco transparente
            cornerRadius = 8f // Bordes redondeados (ajústalo a tu gusto)
        }

        holder.itemView.setOnFocusChangeListener { view, hasFocus ->
            if (hasFocus) {
                // Ejecutamos el callback para actualizar el fondo de la Activity
                onFocusChange(canal)

                // 1. Animación suave de agrandado y elevación
                // Usamos translationZ para que el ítem "flote" sobre los demás
                view.animate()
                    .scaleX(1.15f) // Un poco más de escala suele verse mejor en TV
                    .scaleY(1.15f)
                    .translationZ(15f)
                    .setDuration(250)
                    .setInterpolator(DecelerateInterpolator()) // Movimiento más natural
                    .start()

                // 2. Color de texto resaltado
                holder.txtTitle.setTextColor(colorDorado)
                holder.txtTitle.isSelected = true // Activa el Marquee (texto corriendo) si lo tienes configurado

                // 3. Efecto de Borde
                // Si el itemView es un CardView, es mejor usar 'foreground' para no tapar las esquinas redondeadas
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    view.foreground = focusedBackground
                } else {
                    view.background = focusedBackground
                }

            } else {
                // 1. Restaurar valores originales
                view.animate()
                    .scaleX(1.0f)
                    .scaleY(1.0f)
                    .translationZ(0f)
                    .setDuration(250)
                    .start()

                // 2. Restaurar texto
                holder.txtTitle.setTextColor(Color.WHITE)
                holder.txtTitle.isSelected = false // Detiene el Marquee

                // 3. Quitar el borde
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    view.foreground = null
                } else {
                    view.background = null
                }
            }
        }

        // CLIC NORMAL
        holder.itemView.setOnClickListener { onItemClick(canal) }

        // CLIC SOSTENIDO
        holder.itemView.setOnLongClickListener {
            onLongClick(canal)
            true
        }
    }

    override fun getItemCount(): Int = channelList.size

    class ChannelViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val imgMovie: ImageView = view.findViewById(R.id.imgMovie)
        val txtTitle: TextView = view.findViewById(R.id.txtMovieTitle)
        val txtStatus: TextView = view.findViewById(R.id.txtStatus)
        val txtBadge: TextView = view.findViewById(R.id.txtBadge)
        val imgStar: ImageView = view.findViewById(R.id.imgStar) // Asegúrate de añadirlo al XML
    }

    // Actualiza tanto la lista como los IDs favoritos
    fun updateList(newList: List<Modelo>, favIds: Set<String>) {
        // Reemplazamos la referencia por una nueva lista limpia
        this.channelList = newList.toList()
        this.favoriteIds = favIds
        notifyDataSetChanged()
    }
}