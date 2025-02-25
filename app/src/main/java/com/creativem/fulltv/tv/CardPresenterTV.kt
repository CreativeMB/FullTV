package com.creativem.fulltv.tv

import android.content.Intent
import android.graphics.Color
import android.view.ViewGroup
import android.widget.TextView
import androidx.leanback.widget.ImageCardView
import androidx.leanback.widget.Presenter
import com.bumptech.glide.Glide
import com.creativem.fulltv.R
import com.creativem.fulltv.data.Movie
import java.lang.reflect.Field

class CardPresenterTV : Presenter() {

    override fun onCreateViewHolder(parent: ViewGroup): ViewHolder {
        val cardView = ImageCardView(parent.context).apply {
            isFocusable = true
            isFocusableInTouchMode = true
            setMainImageDimensions(180, 140)
        }
        return ViewHolder(cardView)
    }

    override fun onBindViewHolder(viewHolder: ViewHolder, item: Any) {
        if (item !is Movie) return // Previene errores si item no es una película

        val cardView = viewHolder.view as ImageCardView

        cardView.apply {
            titleText = item.title

            val titleTextView: TextView = getTextViewFromCard(cardView, "mTitleView")
            // Modificar el tamaño y color del texto
            titleTextView.apply {
                textSize = 16f  // Cambiar el tamaño del texto
                maxLines = 3
                setTextColor(Color.WHITE)  // Cambiar el color del texto
            }

            // Cargar imagen con Glide
            Glide.with(context)
                .load(item.imageUrl)
                .centerCrop()
                .placeholder(R.drawable.icono) // Imagen por defecto mientras carga
                .error(R.drawable.icono) // Imagen en caso de error
                .into(mainImageView)

            setOnClickListener {
                val context = it.context
                val intent = Intent(context, PlayertvActivity::class.java).apply {
                    putExtra("EXTRA_STREAM_URL", item.streamUrl)
                    putExtra("EXTRA_MOVIE_TITLE", item.title)
                }
                context.startActivity(intent)
            }
        }
    }

    override fun onUnbindViewHolder(viewHolder: ViewHolder) {
        val cardView = viewHolder.view as ImageCardView
        cardView.mainImage = null // Libera la imagen para optimizar memoria
    }
    // Función para obtener el TextView de ImageCardView usando reflexión
    private fun getTextViewFromCard(cardView: ImageCardView, fieldName: String): TextView {
        val field: Field = cardView.javaClass.getDeclaredField(fieldName)
        field.isAccessible = true  // Permitir acceso a campos privados
        return field.get(cardView) as TextView
    }
}
