package com.creativem.fulltv.adapter

import android.content.Intent
import android.os.CountDownTimer
import android.view.ViewGroup
import androidx.leanback.widget.ImageCardView
import androidx.leanback.widget.Presenter
import com.bumptech.glide.Glide
import com.creativem.fulltv.home.PlayerActivity
import com.creativem.fulltv.R
import com.creativem.fulltv.data.Movie
import java.util.concurrent.TimeUnit
import android.graphics.Color
import android.widget.TextView
import java.lang.reflect.Field

class CardPresenter: Presenter(){
    override fun onCreateViewHolder(parent: ViewGroup): ViewHolder {
        val cardView = ImageCardView(parent.context).apply {
            isFocusable = true
            isFocusableInTouchMode = true
            setMainImageDimensions(190, 260)
        }
        return CardViewHolder(cardView)
    }

    override fun onBindViewHolder(viewHolder: Presenter.ViewHolder, item: Any) {
        val movie = item as? Movie ?: return
        val cardViewHolder = viewHolder as CardViewHolder
        val cardView = cardViewHolder.view as ImageCardView

        val casText = "$"
        cardView.titleText = movie.title
        cardView.contentText = "$casText${movie.year}"

        // Usar reflexión para obtener las vistas internas de ImageCardView
        try {
            val titleTextView: TextView = getTextViewFromCard(cardView, "mTitleView")
            val contentTextView: TextView = getTextViewFromCard(cardView, "mContentView")

            // Modificar el tamaño y color del texto
            titleTextView.apply {
                textSize = 9f  // Cambiar el tamaño del texto
                setTextColor(Color.WHITE)  // Cambiar el color del texto
            }

            contentTextView.apply {
                textSize = 8f  // Cambiar el tamaño del texto
                setTextColor(Color.GREEN)  // Cambiar el color del texto
            }
        } catch (e: Exception) {
            e.printStackTrace()  // Manejo de errores
        }

        // Cancelar cualquier temporizador anterior en este ViewHolder
        cardViewHolder.countDownTimer?.cancel()

        // Cargar la imagen con Glide
        Glide.with(viewHolder.view.context)
            .load(movie.imageUrl)
            .centerCrop()
            .error(R.drawable.icono)
            .into(cardView.mainImageView)

        val createdAtMillis = movie.createdAt?.toDate()?.time ?: 0
        val countdownDurationMillis = TimeUnit.MINUTES.toMillis(movie.countdownMinutes.toLong())
        val currentTime = System.currentTimeMillis()
        val timeElapsed = currentTime - createdAtMillis

        if (movie.countdownMinutes <= 0 || timeElapsed >= countdownDurationMillis) {
            cardView.contentText = "$casText${movie.year} | Min-00:00"
            cardView.setInfoAreaBackgroundColor(Color.parseColor("#006064"))
        } else {
            val remainingTimeMillis = countdownDurationMillis - timeElapsed
            cardViewHolder.countDownTimer = object : CountDownTimer(remainingTimeMillis, 1000) {
                override fun onTick(millisUntilFinished: Long) {
                    val minutesRemaining = TimeUnit.MILLISECONDS.toMinutes(millisUntilFinished)
                    val secondsRemaining = TimeUnit.MILLISECONDS.toSeconds(millisUntilFinished) % 60
                    cardView.contentText = "$casText${movie.year} | Min-%02d:%02d".format(minutesRemaining, secondsRemaining)
                    // Cambiar el fondo del área de información a rojo mientras el temporizador está activo
                    cardView.setInfoAreaBackgroundColor(Color.parseColor("#001f3f"))
                }

                override fun onFinish() {
                    cardView.contentText = "$casText${movie.year} | Min-00:00"
                    cardView.setInfoAreaBackgroundColor(Color.parseColor("#3E2723"))
                }
            }.start()
        }

        cardView.setOnClickListener {
            val context = viewHolder.view.context
            val intent = Intent(context, PlayerActivity::class.java)
            intent.putExtra("EXTRA_STREAM_URL", movie.streamUrl)
            context.startActivity(intent)
        }
    }

    override fun onUnbindViewHolder(viewHolder: Presenter.ViewHolder) {
        val cardViewHolder = viewHolder as CardViewHolder
        cardViewHolder.countDownTimer?.cancel()
        cardViewHolder.countDownTimer = null
    }

    inner class CardViewHolder(view: ImageCardView) : ViewHolder(view) {
        var countDownTimer: CountDownTimer? = null
    }
    // Función para obtener el TextView de ImageCardView usando reflexión
    private fun getTextViewFromCard(cardView: ImageCardView, fieldName: String): TextView {
        val field: Field = cardView.javaClass.getDeclaredField(fieldName)
        field.isAccessible = true  // Permitir acceso a campos privados
        return field.get(cardView) as TextView
    }
}