package com.creativem.fulltv.adapter

import android.content.Intent
import android.view.ViewGroup
import androidx.leanback.widget.ImageCardView
import androidx.leanback.widget.Presenter
import com.bumptech.glide.Glide
import com.creativem.fulltv.home.PlayerActivity
import com.creativem.fulltv.R
import com.creativem.fulltv.data.Movie


class CardPresenter: Presenter(){
    override fun onCreateViewHolder(parent: ViewGroup): ViewHolder {
        val cardView = ImageCardView(parent.context).apply {
            isFocusable = true
            isFocusableInTouchMode = true
            setMainImageDimensions(200, 250)
            // cardLayoutTheme = R.style.DefaultCardTheme // Estilo opcional
        }
        return ViewHolder(cardView)
    }



    override fun onBindViewHolder(viewHolder: ViewHolder, item: Any?) {
        val movie = item as? Movie ?: return
        val cardView = viewHolder.view as ImageCardView

        cardView.titleText = movie.title
        cardView.contentText = movie.year

        // Carga de imagen con Glide
        Glide.with(viewHolder.view.context)
            .load(movie.imageUrl)
            .centerCrop()
            .error(R.drawable.icono) // Imagen por defecto si falla la carga
            .into(cardView.mainImageView)

        // Manejar clic en la tarjeta
        cardView.setOnClickListener {
            val context = viewHolder.view.context
            val intent = Intent(context, PlayerActivity::class.java)

            // Asegúrate de usar la clave correcta "EXTRA_STREAM_URL"
            intent.putExtra("EXTRA_STREAM_URL", movie.streamUrl)

            context.startActivity(intent)
        }
    }
     override fun onUnbindViewHolder(viewHolder: ViewHolder) {}
}