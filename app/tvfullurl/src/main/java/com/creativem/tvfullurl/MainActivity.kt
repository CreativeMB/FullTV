package com.creativem.tvfullurl

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.navigation.fragment.NavHostFragment
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

        // 🟢 SOLUCIÓN PRINCIPAL: Solo necesitas esta línea para que todas las pestañas funcionen.
        // Al no sobrescribir el listener, la navegación de "Editar", "Nueva" y demás volverá a servir.
        binding.bottomNavigationView.setupWithNavController(navController)
    }
}