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
        } else {
            val remainingTimeMillis = countdownDurationMillis - timeElapsed
            cardViewHolder.countDownTimer = object : CountDownTimer(remainingTimeMillis, 1000) {
                override fun onTick(millisUntilFinished: Long) {
                    val minutesRemaining = TimeUnit.MILLISECONDS.toMinutes(millisUntilFinished)
                    val secondsRemaining = TimeUnit.MILLISECONDS.toSeconds(millisUntilFinished) % 60
                    cardView.contentText = "$casText${movie.year} | Min-%02d:%02d".format(minutesRemaining, secondsRemaining)
                }

                override fun onFinish() {
                    cardView.contentText = "$casText${movie.year} | Min-00:00"
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
}