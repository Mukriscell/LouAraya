package cl.duoc.louaraya

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import cl.duoc.louaraya.presentation.navigation.LouArayaNavHost
import cl.duoc.louaraya.ui.theme.LouArayaTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val container = (application as LouArayaApplication).container
        setContent {
            LouArayaTheme {
                LouArayaNavHost(
                    getProductsUseCase = container.getProductsUseCase,
                    getProductByIdUseCase = container.getProductByIdUseCase,
                    createCommercialRequestUseCase = container.createCommercialRequestUseCase,
                    getCommercialRequestsUseCase = container.getCommercialRequestsUseCase,
                    getCommercialRequestByIdUseCase = container.getCommercialRequestByIdUseCase
                )
            }
        }
    }
}
