package cl.duoc.louaraya.presentation.navigation

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import cl.duoc.louaraya.domain.usecase.GetProductByIdUseCase
import cl.duoc.louaraya.domain.usecase.GetProductsUseCase
import cl.duoc.louaraya.presentation.catalog.CatalogScreen
import cl.duoc.louaraya.presentation.catalog.CatalogViewModel
import cl.duoc.louaraya.presentation.catalog.CatalogViewModelFactory
import cl.duoc.louaraya.presentation.productdetail.ProductDetailScreen
import cl.duoc.louaraya.presentation.productdetail.ProductDetailViewModel
import cl.duoc.louaraya.presentation.productdetail.ProductDetailViewModelFactory

/**
 * Rutas de navegación de la aplicación.
 */
object LouArayaRoutes {
    const val CATALOG = "catalog"
    const val PRODUCT_ID_ARG = "productId"
    const val PRODUCT_DETAIL = "product/{$PRODUCT_ID_ARG}"

    fun productDetail(productId: Long): String = "product/$productId"
}

/**
 * Grafo de navegación. Solo recibe Use Cases; no conoce Room ni repositorios.
 *
 * Cada ViewModel queda asociado a su entrada del back stack, por lo que el
 * catálogo conserva su estado mientras se visualiza el detalle.
 */
@Composable
fun LouArayaNavHost(
    getProductsUseCase: GetProductsUseCase,
    getProductByIdUseCase: GetProductByIdUseCase,
    modifier: Modifier = Modifier
) {
    val navController = rememberNavController()

    NavHost(
        navController = navController,
        startDestination = LouArayaRoutes.CATALOG,
        modifier = modifier
    ) {
        composable(LouArayaRoutes.CATALOG) {
            val catalogViewModel: CatalogViewModel = viewModel(
                factory = CatalogViewModelFactory(getProductsUseCase)
            )
            Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                CatalogScreen(
                    viewModel = catalogViewModel,
                    onProductClick = { productId ->
                        navController.navigate(LouArayaRoutes.productDetail(productId))
                    },
                    modifier = Modifier.padding(innerPadding)
                )
            }
        }

        composable(
            route = LouArayaRoutes.PRODUCT_DETAIL,
            arguments = listOf(
                navArgument(LouArayaRoutes.PRODUCT_ID_ARG) { type = NavType.LongType }
            )
        ) { backStackEntry ->
            val productId = requireNotNull(backStackEntry.arguments)
                .getLong(LouArayaRoutes.PRODUCT_ID_ARG)
            val detailViewModel: ProductDetailViewModel = viewModel(
                factory = ProductDetailViewModelFactory(productId, getProductByIdUseCase)
            )
            ProductDetailScreen(
                viewModel = detailViewModel,
                onBack = { navController.popBackStack() }
            )
        }
    }
}
