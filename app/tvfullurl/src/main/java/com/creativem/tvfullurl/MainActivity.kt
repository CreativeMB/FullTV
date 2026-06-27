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
        binding = PantallaPrincipalBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val navHostFragment = supportFragmentManager.findFragmentById(R.id.fragmentContainer) as NavHostFragment
        val navController = navHostFragment.navController

        // Configuración por defecto de la barra de navegación inferior
        binding.bottomNavigationView.setupWithNavController(navController)

        // 🟢 SOLUCIÓN: Interceptamos el clic en los botones de abajo
        binding.bottomNavigationView.setOnItemSelectedListener { item ->
            val currentDestination = navController.currentDestination?.id

            // Si el usuario pulsa la pestaña "Editar" (editarPeliculaFragment)
            // y actualmente está en la pantalla "Nueva" (nuevaPeliculaFragment)
            if (item.itemId == R.id.editarPeliculaFragment && currentDestination == R.id.nuevaPeliculaFragment) {

                // Verificamos si la pantalla tiene el argumento "movieId" (lo que significa que está EDITANDO)
                val arguments = navController.currentBackStackEntry?.arguments
                val estaEditando = arguments?.getString("movieId") != null

                if (estaEditando) {
                    // Forzamos el retroceso a la lista de pedidos (EditarPeliculaFragment) sin guardar nada
                    navController.popBackStack(R.id.editarPeliculaFragment, false)
                    return@setOnItemSelectedListener true
                }
            }

            // Si es cualquier otra pestaña o no está editando, dejamos que Jetpack Navigation actúe normalmente
            NavigationUI.onNavDestinationSelected(item, navController)
            true
        }
    }
}