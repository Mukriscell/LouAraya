package cl.duoc.louaraya

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.ui.Modifier
import cl.duoc.louaraya.presentation.catalog.CatalogScreen
import cl.duoc.louaraya.presentation.catalog.CatalogViewModel
import cl.duoc.louaraya.presentation.catalog.CatalogViewModelFactory
import cl.duoc.louaraya.ui.theme.LouArayaTheme

class MainActivity : ComponentActivity() {

    private val catalogViewModel: CatalogViewModel by viewModels {
        val container = (application as LouArayaApplication).container
        CatalogViewModelFactory(container.getProductsUseCase)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            LouArayaTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    CatalogScreen(
                        viewModel = catalogViewModel,
                        modifier = Modifier.padding(innerPadding)
                    )
                }
            }
        }
    }
}
