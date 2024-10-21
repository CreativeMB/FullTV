package com.creativem.tvfullurl

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.NavigationUI
import androidx.navigation.ui.setupWithNavController
import com.creativem.tvfullurl.databinding.PantallaPrincipalBinding

class MainActivity : AppCompatActivity() {
    private lateinit var binding: PantallaPrincipalBinding
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
//        enableEdgeToEdge()
        binding= PantallaPrincipalBinding.inflate(layoutInflater)
        setContentView(binding.root)
        // 1. Encuentra el NavHostFragment (que contiene tu gráfico de navegación)
        val navHostFragment = supportFragmentManager.findFragmentById(R.id.fragmentContainer) as NavHostFragment
        // 2. Obtén el NavController de NavHostFragment
        val navController = navHostFragment.navController

        // 3. Configura el BottomNavigationView para que se sincronice con el NavController
        binding.bottomNavigationView.setupWithNavController(navController)
        binding.bottomNavigationView.setOnNavigationItemSelectedListener { menuItem ->
            if (menuItem.itemId == R.id.nuevaPeliculaFragment) {
                // Navega a ListaPeliculasFragment2 y luego a AgregarPeliculaFragment2 usando la acción global
                navController.navigate(R.id.action_global_self)
                navController.navigate(R.id.nuevaPeliculaFragment)
                true
            } else {
                // Maneja la navegación normal para otros elementos del menú
                NavigationUI.onNavDestinationSelected(menuItem, navController)
            }
        }
    }
}