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
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlin.math.max

class CardPresenter: Presenter(){
    override fun onCreateViewHolder(parent: ViewGroup): ViewHolder {
        val cardView = ImageCardView(parent.context).apply {
            isFocusable = true
            isFocusableInTouchMode = true
            setMainImageDimensions(160, 220)
        }
        return ViewHolder(cardView)
    }

    override fun onBindViewHolder(viewHolder: ViewHolder, item: Any?) {
        val movie = item as? Movie ?: return
        val cardView = viewHolder.view as ImageCardView

        val casText = "$"
        cardView.titleText = movie.title
        cardView.contentText = "$casText${movie.year}"

        // Cargar la imagen con Glide
        Glide.with(viewHolder.view.context)
            .load(movie.imageUrl)
            .centerCrop()
            .error(R.drawable.icono) // Imagen de error si no se puede cargar
            .into(cardView.mainImageView)

        // Obtener el tiempo creado desde Firestore (Timestamp)
        val createdAtMillis = movie.createdAt?.toDate()?.time ?: 0 // Asegúrate de que `createdAt` es un Timestamp
        val countdownDurationMillis = TimeUnit.MINUTES.toMillis(movie.countdownMinutes.toLong())

        // Obtener el tiempo actual
        val currentTime = System.currentTimeMillis()

        // Calcular cuánto tiempo ha pasado desde la creación
        val timeElapsed = currentTime - createdAtMillis

        // Imprimir valores para depuración
        println("Current Time: $currentTime")
        println("Created At: $createdAtMillis")
        println("Countdown Duration: $countdownDurationMillis")
        println("Time Elapsed: $timeElapsed")

        // Verificar si el tiempo transcurrido es mayor o igual al tiempo de countdown
        if (movie.countdownMinutes <= 0 || timeElapsed >= countdownDurationMillis) {
            // Si ha pasado el tiempo o el countdown es 0, mostrar cero
            cardView.contentText = "$casText${movie.year} Min-00:00"
        } else {
            // Calcular el tiempo restante
            val remainingTimeMillis = countdownDurationMillis - timeElapsed

            // Imprimir el tiempo restante para depuración
            println("Remaining Time (ms): $remainingTimeMillis")

            // Iniciar el temporizador solo si remainingTimeMillis es mayor que 0
            if (remainingTimeMillis > 0) {
                object : CountDownTimer(remainingTimeMillis, 1000) {
                    override fun onTick(millisUntilFinished: Long) {
                        val minutesRemaining = TimeUnit.MILLISECONDS.toMinutes(millisUntilFinished)
                        val secondsRemaining = TimeUnit.MILLISECONDS.toSeconds(millisUntilFinished) % 60

                        // Actualizar el contentText para incluir la cuenta regresiva
                        cardView.contentText = "$casText${movie.year} Min -%02d:%02d".format(minutesRemaining, secondsRemaining)
                    }

                    override fun onFinish() {
                        cardView.contentText = "$casText${movie.year} Min-00:00"
                    }
                }.start()
            } else {
                // Si el tiempo restante es cero o negativo, mostrar 00:00
                cardView.contentText = "$casText${movie.year} Min-00:00"
            }
        }

        // Configurar el clic de la tarjeta
        cardView.setOnClickListener {
            val context = viewHolder.view.context
            val intent = Intent(context, PlayerActivity::class.java)

            // Usar la clave correcta para pasar la URL del stream
            intent.putExtra("EXTRA_STREAM_URL", movie.streamUrl)

            context.startActivity(intent)
        }
    }

    override fun onUnbindViewHolder(viewHolder: ViewHolder) {}
}