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
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.creativem.fulltv.ApiPeliculaActivity
import java.lang.reflect.Field

class CardPresenter: Presenter(){

    override fun onCreateViewHolder(parent: ViewGroup): ViewHolder {
        val context = parent.context

        val frameLayout = FrameLayout(context).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            isFocusable = true
            isFocusableInTouchMode = true
        }

        val cardView = ImageCardView(context).apply {
            isFocusable = true
            isFocusableInTouchMode = true
            setMainImageDimensions(200, 280)
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                setMargins(12, 12, 12, 12)
            }
        }

        val etiquetaValida = TextView(context).apply {
            text = ""
            setTextColor(Color.WHITE)
            textSize = 12f
            setPadding(8, 4, 8, 4)
            gravity = Gravity.CENTER
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.TOP or Gravity.END
            ).apply {
                setMargins(0, 8, 8, 0)
            }
            visibility = View.GONE
        }

        frameLayout.addView(cardView)
        frameLayout.addView(etiquetaValida)

        frameLayout.setOnFocusChangeListener { view, hasFocus ->
            view.background = if (hasFocus)
                ContextCompat.getDrawable(context, R.drawable.card_focused_background)
            else
                null
            val scale = if (hasFocus) 1.1f else 1f
            view.animate().scaleX(scale).scaleY(scale).setDuration(150).start()
        }

        return CardViewHolder(frameLayout, cardView, etiquetaValida)
    }

    override fun onBindViewHolder(viewHolder: ViewHolder, item: Any?) {
        val holder = viewHolder as CardViewHolder
        val cardView = holder.cardView
        val etiquetaValida = holder.etiquetaValida
        val movie = item as? Movie ?: return

        val casText = "CasTV $"
        cardView.titleText = movie.title
        cardView.contentText = "$casText${movie.year}"
        etiquetaValida.visibility = View.GONE

        try {
            val titleTextView: TextView = getTextViewFromCard(cardView, "mTitleView")
            val contentTextView: TextView = getTextViewFromCard(cardView, "mContentView")

            titleTextView.apply {
                textSize = 12f
                maxLines = 1
                setTextColor(Color.WHITE)
                isSingleLine = true
                ellipsize = TextUtils.TruncateAt.MARQUEE
                marqueeRepeatLimit = -1
                isFocusable = true
                isFocusableInTouchMode = true
                setHorizontallyScrolling(true)
                setOnFocusChangeListener { v, hasFocus -> v.isSelected = hasFocus }
            }

            contentTextView.apply {
                textSize = 14f
                maxLines = 1
                setTextColor(Color.GREEN)
                isSingleLine = true
                ellipsize = TextUtils.TruncateAt.MARQUEE
                marqueeRepeatLimit = -1
                isFocusable = true
                isFocusableInTouchMode = true
                setHorizontallyScrolling(true)
                setOnFocusChangeListener { v, hasFocus -> v.isSelected = hasFocus }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        holder.countDownTimer?.cancel()

        cardView.mainImageView?.let { imageView ->
            imageView.scaleType = ImageView.ScaleType.FIT_XY
            imageView.adjustViewBounds = false

            Glide.with(cardView.context)
                .load(movie.imageUrl)
                .placeholder(R.drawable.icono)
                .error(R.drawable.icono)
                .into(imageView)
        }

        val createdAtMillis = movie.createdAt.toDate().time
        val countdownDurationMillis = TimeUnit.MINUTES.toMillis(movie.countdownMinutes.toLong())
        val currentTime = System.currentTimeMillis()
        val timeElapsed = currentTime - createdAtMillis

        val esValida = Validacioneslista.yaCargado() &&
                Validacioneslista.obtenerPeliculasValidas().any { it.streamUrl == movie.streamUrl }

        if (movie.countdownMinutes <= 0 || timeElapsed >= countdownDurationMillis) {
            if (esValida) {
                cardView.contentText = "Abierta al público"
                cardView.setInfoAreaBackgroundColor(Color.parseColor("#006064"))
                etiquetaValida.text = "Gratis ✅"
                etiquetaValida.setBackgroundColor(Color.parseColor("#006064"))
                etiquetaValida.visibility = View.VISIBLE
            } else {
                cardView.contentText = "$casText${movie.year}"
                cardView.setInfoAreaBackgroundColor(Color.parseColor("#880E4F"))
                etiquetaValida.text = "Alquilar 💳"
                etiquetaValida.setBackgroundColor(Color.parseColor("#880E4F"))
                etiquetaValida.visibility = View.VISIBLE
            }
        } else {
            val remainingTimeMillis = countdownDurationMillis - timeElapsed
            holder.countDownTimer = object : CountDownTimer(remainingTimeMillis, 1000) {
                override fun onTick(millisUntilFinished: Long) {
                    val h = TimeUnit.MILLISECONDS.toHours(millisUntilFinished)
                    val m = TimeUnit.MILLISECONDS.toMinutes(millisUntilFinished) % 60
                    val s = TimeUnit.MILLISECONDS.toSeconds(millisUntilFinished) % 60
                    cardView.contentText = "%02d:%02d:%02d".format(h, m, s)
                    cardView.setInfoAreaBackgroundColor(Color.parseColor("#001f3f"))

                    etiquetaValida.text = "Alquilada \uD83C\uDFAC"
                    etiquetaValida.setBackgroundColor(Color.parseColor("#001f3f"))
                    etiquetaValida.visibility = View.VISIBLE
                }

                override fun onFinish() {
                    cardView.contentText = "00:00:00"
                    cardView.setInfoAreaBackgroundColor(Color.parseColor("#3E2723"))
                }
            }.start()
        }

        holder.view.setOnClickListener {
            val context = cardView.context
            val intent = Intent(context, ApiPeliculaActivity::class.java)
            intent.putExtra("EXTRA_TITLE", movie.title)
            intent.putExtra("EXTRA_STREAM_URL", movie.streamUrl)
            context.startActivity(intent)
        }



//        holder.view.setOnClickListener {
//            val context = cardView.context
//            val intent = Intent(context, PlayerPeliculas::class.java)
//            intent.putExtra("EXTRA_STREAM_URL", movie.streamUrl)
//            context.startActivity(intent)
//        }
    }

    override fun onUnbindViewHolder(viewHolder: ViewHolder) {
        val holder = viewHolder as CardViewHolder
        holder.countDownTimer?.cancel()
        holder.countDownTimer = null
    }

    inner class CardViewHolder(
        view: FrameLayout,
        val cardView: ImageCardView,
        val etiquetaValida: TextView
    ) : ViewHolder(view) {
        var countDownTimer: CountDownTimer? = null
    }

    private fun getTextViewFromCard(cardView: ImageCardView, fieldName: String): TextView {
        val field: Field = cardView.javaClass.getDeclaredField(fieldName)
        field.isAccessible = true
        return field.get(cardView) as TextView
    }
}