//package com.creativem.fulltv.tv
//
//import android.content.Intent
//import android.graphics.Color
//import android.text.TextUtils
//import android.util.Log
//import android.view.Gravity
//import android.view.View
//import android.view.ViewGroup
//import android.widget.FrameLayout
//import android.widget.ImageView
//import android.widget.TextView
//import android.widget.Toast
//import androidx.core.content.ContextCompat
//import androidx.leanback.widget.ImageCardView
//import androidx.leanback.widget.Presenter
//import com.bumptech.glide.Glide
//import com.creativem.fulltv.R
//import com.creativem.fulltv.principal.Modelo
//import java.lang.reflect.Field
//
//class CardPresenterTV : Presenter() {
//    override fun onCreateViewHolder(parent: ViewGroup): ViewHolder {
//        val context = parent.context
//
//        // FrameLayout contenedor
//        val frameLayout = FrameLayout(context).apply {
//            layoutParams = ViewGroup.LayoutParams(
//                ViewGroup.LayoutParams.WRAP_CONTENT,
//                ViewGroup.LayoutParams.WRAP_CONTENT
//            )
//            isFocusable = true
//            isFocusableInTouchMode = true
//        }
//
//        // CardView
//        val cardView = ImageCardView(context).apply {
//            isFocusable = true
//            isFocusableInTouchMode = true
//            setMainImageDimensions(200, 220)
//            layoutParams = FrameLayout.LayoutParams(
//                ViewGroup.LayoutParams.WRAP_CONTENT,
//                ViewGroup.LayoutParams.WRAP_CONTENT
//            ).apply {
//                setMargins(12, 12, 12, 12)
//            }
//        }
//
//        // Etiqueta "Gratis ✅"
//        val etiquetaGratis = TextView(context).apply {
//            text = "TV Gratis ✅"
//            cardView.setInfoAreaBackgroundColor(Color.parseColor("#006064"))
//            setTextColor(Color.WHITE)
//            textSize = 12f
//            setPadding(8, 4, 8, 4)
//            gravity = Gravity.CENTER
//            setBackgroundColor(Color.parseColor("#006064"))
//            layoutParams = FrameLayout.LayoutParams(
//                ViewGroup.LayoutParams.WRAP_CONTENT,
//                ViewGroup.LayoutParams.WRAP_CONTENT,
//                Gravity.TOP or Gravity.END
//            ).apply {
//                setMargins(0, 8, 8, 0)
//            }
//            visibility = View.GONE
//        }
//
//        // Agregar vistas al FrameLayout
//        frameLayout.addView(cardView)
//        frameLayout.addView(etiquetaGratis)
//
//        // Efecto de foco
//        frameLayout.setOnFocusChangeListener { view, hasFocus ->
//            view.background = if (hasFocus)
//                ContextCompat.getDrawable(context, R.drawable.card_focused_background)
//            else
//                null
//            val scale = if (hasFocus) 1.1f else 1f
//            view.animate().scaleX(scale).scaleY(scale).setDuration(150).start()
//        }
//
//        return CardViewHolder(frameLayout, cardView, etiquetaGratis)
//    }
//
//    override fun onBindViewHolder(viewHolder: ViewHolder, item: Any) {
//        if (item !is Modelo) return
//        val holder = viewHolder as CardViewHolder
//        val cardView = holder.cardView
//        val etiquetaGratis = holder.etiquetaGratis
//
//        cardView.titleText = item.title
//
//        val titleTextView: TextView = getTextViewFromCard(cardView, "mTitleView")
//        titleTextView.apply {
//            textSize = 18f
//            maxLines = 1
//            setTextColor(Color.WHITE)
//            isSingleLine = true
//            ellipsize = TextUtils.TruncateAt.MARQUEE
//            marqueeRepeatLimit = -1
//            isFocusable = true
//            isFocusableInTouchMode = true
//            setHorizontallyScrolling(true)
//            setOnFocusChangeListener { v, hasFocus -> v.isSelected = hasFocus }
//        }
//
//        cardView.mainImageView?.apply {
//            scaleType = ImageView.ScaleType.FIT_XY
//            adjustViewBounds = false
//
//            Glide.with(context)
//                .load(item.imageUrl)
//                .placeholder(R.drawable.pelifondo)
//                .error(R.drawable.icono)
//                .into(this)
//        }
//
//        // Simulación de validez (reemplaza esta lógica según tu criterio)
//        val esGratis = true // ⚠️ Ajusta esto según tu lógica real
//        etiquetaGratis.visibility = if (esGratis) View.VISIBLE else View.GONE
//
//        holder.view.setOnClickListener {
//            val context = it.context
//            val intent = Intent(context, PlayerTv::class.java).apply {
//                // Usamos nombres consistentes con el resto de la App
//                putExtra("EXTRA_STREAM_URL", item.streamUrl)
//                putExtra("EXTRA_MOVIE_TITLE", item.title)
//                putExtra("EXTRA_MOVIE_IMAGE_URL", item.imageUrl)
//
//                // OPCIONAL: Si tu TV también usa saldo o fechas, agrégalos:
//                putExtra("EXTRA_MOVIE_CASTV", item.castv)
//                putExtra("EXTRA_CREATED_AT", item.createdAt / 1000)
//            }
//            context.startActivity(intent)
//        }
//
//
//    }
//
//    override fun onUnbindViewHolder(viewHolder: ViewHolder) {
//        val holder = viewHolder as CardViewHolder
//        holder.cardView.mainImage = null
//    }
//
//    // ViewHolder extendido con acceso a la etiqueta
//    inner class CardViewHolder(
//        view: FrameLayout,
//        val cardView: ImageCardView,
//        val etiquetaGratis: TextView
//    ) : ViewHolder(view)
//
//    private fun getTextViewFromCard(cardView: ImageCardView, fieldName: String): TextView {
//        val field: Field = cardView.javaClass.getDeclaredField(fieldName)
//        field.isAccessible = true
//        return field.get(cardView) as TextView
//    }
//}