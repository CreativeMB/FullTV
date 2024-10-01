package com.creativem.fulltv.ui.otras

import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.core.view.WindowCompat
import androidx.fragment.app.FragmentActivity
import com.creativem.fulltv.databinding.ActivityPruevaBinding

class Main : FragmentActivity() {
    private lateinit var binding: ActivityPruevaBinding
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivityPruevaBinding.inflate(layoutInflater)
        setContentView(binding.root)
        WindowCompat.setDecorFitsSystemWindows(window, false)
    }
}