package com.creativem.fulltv.principal

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.text.HtmlCompat
import androidx.fragment.app.Fragment
import com.creativem.fulltv.R

class NosotrosFragment : Fragment() {

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.nosotros, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        requireActivity().window.decorView.systemUiVisibility =
            View.SYSTEM_UI_FLAG_FULLSCREEN or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY

        val col1: TextView = view.findViewById(R.id.col1)
        val col2: TextView = view.findViewById(R.id.col2)
        val col3: TextView = view.findViewById(R.id.col3)

        col1.text = HtmlCompat.fromHtml(getString(R.string.columna_1), HtmlCompat.FROM_HTML_MODE_LEGACY)
        col2.text = HtmlCompat.fromHtml(getString(R.string.columna_2), HtmlCompat.FROM_HTML_MODE_LEGACY)
        col3.text = HtmlCompat.fromHtml(getString(R.string.columna_3), HtmlCompat.FROM_HTML_MODE_LEGACY)
    }
}
