package com.creativem.fulltv.peliculas

import android.content.Intent
import android.os.CountDownTimer
import android.view.ViewGroup
import androidx.leanback.widget.ImageCardView
import androidx.leanback.widget.Presenter
import com.bumptech.glide.Glide
import com.creativem.fulltv.R
import com.creativem.fulltv.principal.Movie
import java.util.concurrent.TimeUnit
import android.graphics.Color
import android.widget.FrameLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import java.lang.reflect.Field

class CardPresenter: Presenter(){
    override fun onCreateViewHolder(parent: ViewGroup): ViewHolder {
        val context = parent.context

        // Contenedor externo que tendrá el borde y escala
        val frameLayout = FrameLayout(context).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            isFocusable = true
            isFocusableInTouchMode = true
        }

        // Tarjeta real
        val cardView = ImageCardView(context).apply {
            isFocusable = true
            isFocusableInTouchMode = true
            setMainImageDimensions(250, 300)

            // 💡 Márgenes internos para mostrar borde exterior
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                setMargins(15, 15, 15, 15)
            }
        }

        frameLayout.addView(cardView)

        // Resaltado visual al enfocar
        frameLayout.setOnFocusChangeListener { view, hasFocus ->
            view.background = if (hasFocus)
                ContextCompat.getDrawable(context, R.drawable.card_focused_background)
            else
                null

            val scale = if (hasFocus) 1.1f else 1f
            view.animate().scaleX(scale).scaleY(scale).setDuration(150).start()
        }

        return CardViewHolder(frameLayout, cardView)
    }

    override fun onBindViewHolder(viewHolder: Presenter.ViewHolder, item: Any) {
        val holder = viewHolder as CardViewHolder
        val cardView = holder.cardView
        val movie = item as? Movie ?: return

        val casText = "$"
        cardView.titleText = movie.title
        cardView.contentText = "$casText${movie.year}"

        // Personalización de textos internos con reflexión
        try {
            val titleTextView: TextView = getTextViewFromCard(cardView, "mTitleView")
            val contentTextView: TextView = getTextViewFromCard(cardView, "mContentView")

            titleTextView.apply {
                textSize = 16f
                maxLines = 3
                setTextColor(Color.WHITE)
                setLines(3)
                setLineSpacing(1f, 1f)
                text = if (text.isNullOrEmpty()) "\n\n" else text
            }

            contentTextView.apply {
                textSize = 12f
                maxLines = 3
                setTextColor(Color.GREEN)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        // Cancelar temporizador anterior si existe
        holder.countDownTimer?.cancel()

        // Cargar imagen
        Glide.with(cardView.context)
            .load(movie.imageUrl)
            .centerCrop()
            .error(R.drawable.icono)
            .into(cardView.mainImageView)

        // Temporizador y colores del infoArea
        val createdAtMillis = movie.createdAt?.toDate()?.time ?: 0
        val countdownDurationMillis = TimeUnit.MINUTES.toMillis(movie.countdownMinutes.toLong())
        val currentTime = System.currentTimeMillis()
        val timeElapsed = currentTime - createdAtMillis

        if (movie.countdownMinutes <= 0 || timeElapsed >= countdownDurationMillis) {
            cardView.contentText = "$casText${movie.year} | Min-00:00"
            cardView.setInfoAreaBackgroundColor(Color.parseColor("#006064"))
        } else {
            val remainingTimeMillis = countdownDurationMillis - timeElapsed
            holder.countDownTimer = object : CountDownTimer(remainingTimeMillis, 1000) {
                override fun onTick(millisUntilFinished: Long) {
                    val minutesRemaining = TimeUnit.MILLISECONDS.toMinutes(millisUntilFinished)
                    val secondsRemaining = TimeUnit.MILLISECONDS.toSeconds(millisUntilFinished) % 60
                    cardView.contentText = "$casText${movie.year} | Min-%02d:%02d".format(
                        minutesRemaining,
                        secondsRemaining
                    )
                    cardView.setInfoAreaBackgroundColor(Color.parseColor("#001f3f"))
                }

                override fun onFinish() {
                    cardView.contentText = "$casText${movie.year} | Min-00:00"
                    cardView.setInfoAreaBackgroundColor(Color.parseColor("#3E2723"))
                }
            }.start()
        }

        holder.view.setOnClickListener {
            val context = cardView.context
            val intent = Intent(context, PlayerPeliculas::class.java)
            intent.putExtra("EXTRA_STREAM_URL", movie.streamUrl)
            context.startActivity(intent)
        }
    }

    override fun onUnbindViewHolder(viewHolder: Presenter.ViewHolder) {
        val holder = viewHolder as CardViewHolder
        holder.countDownTimer?.cancel()
        holder.countDownTimer = null
    }

    inner class CardViewHolder(view: FrameLayout, val cardView: ImageCardView) : ViewHolder(view) {
        var countDownTimer: CountDownTimer? = null
    }

    private fun getTextViewFromCard(cardView: ImageCardView, fieldName: String): TextView {
        val field: Field = cardView.javaClass.getDeclaredField(fieldName)
        field.isAccessible = true
        return field.get(cardView) as TextView
    }
}
