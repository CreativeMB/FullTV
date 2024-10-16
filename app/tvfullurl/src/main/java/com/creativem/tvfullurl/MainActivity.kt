package com.creativem.tvfullurl

import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.setupWithNavController
import com.creativem.tvfullurl.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
//        enableEdgeToEdge()
        binding= ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        // 1. Encuentra el NavHostFragment (que contiene tu gráfico de navegación)
        val navHostFragment = supportFragmentManager.findFragmentById(R.id.fragmentContainer) as NavHostFragment
        // 2. Obtén el NavController de NavHostFragment
        val navController = navHostFragment.navController

        // 3. Configura el BottomNavigationView para que se sincronice con el NavController
        binding.bottomNavigationView.setupWithNavController(navController)
    }
}