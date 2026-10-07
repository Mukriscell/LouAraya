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
import cl.duoc.louaraya.domain.usecase.CreateCommercialRequestUseCase
import cl.duoc.louaraya.domain.usecase.GetCommercialRequestsUseCase
import cl.duoc.louaraya.domain.usecase.GetCommercialRequestByIdUseCase
import cl.duoc.louaraya.domain.image.ProductImageStorage
import cl.duoc.louaraya.domain.usecase.UpdateProductImageUseCase
import cl.duoc.louaraya.presentation.admin.AdminProductCatalogScreen
import cl.duoc.louaraya.presentation.admin.AdminProductImageScreen
import cl.duoc.louaraya.presentation.admin.AdminProductImageViewModel
import cl.duoc.louaraya.presentation.admin.AdminProductImageViewModelFactory
import cl.duoc.louaraya.presentation.common.RefreshOnReturn
import cl.duoc.louaraya.presentation.common.currentLouArayaWindowSizeClass
import cl.duoc.louaraya.presentation.createrequest.CreateCommercialRequestScreen
import cl.duoc.louaraya.presentation.createrequest.CreateCommercialRequestViewModel
import cl.duoc.louaraya.presentation.createrequest.CreateCommercialRequestViewModelFactory
import cl.duoc.louaraya.presentation.requests.CommercialRequestsScreen
import cl.duoc.louaraya.presentation.requests.CommercialRequestsViewModel
import cl.duoc.louaraya.presentation.requests.CommercialRequestsViewModelFactory
import cl.duoc.louaraya.presentation.requestdetail.CommercialRequestDetailScreen
import cl.duoc.louaraya.presentation.requestdetail.CommercialRequestDetailViewModel
import cl.duoc.louaraya.presentation.requestdetail.CommercialRequestDetailViewModelFactory
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
    const val CREATE_REQUEST = "product/{$PRODUCT_ID_ARG}/request"
    const val REQUESTS = "requests"
    const val ADMIN_PRODUCTS = "admin/products"
    const val ADMIN_PRODUCT_ID_ARG = "adminProductId"
    const val ADMIN_EDIT_IMAGE = "admin/product/{$ADMIN_PRODUCT_ID_ARG}/image"
    const val REQUEST_ID_ARG = "requestId"
    const val REQUEST_DETAIL = "requests/{$REQUEST_ID_ARG}"

    fun productDetail(productId: Long): String = "product/$productId"
    fun createRequest(productId: Long): String = "product/$productId/request"
    fun requestDetail(requestId: Long): String = "requests/$requestId"
    fun adminEditImage(productId: Long): String = "admin/product/$productId/image"
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
    createCommercialRequestUseCase: CreateCommercialRequestUseCase,
    getCommercialRequestsUseCase: GetCommercialRequestsUseCase,
    getCommercialRequestByIdUseCase: GetCommercialRequestByIdUseCase,
    productImageStorage: ProductImageStorage,
    updateProductImageUseCase: UpdateProductImageUseCase,
    modifier: Modifier = Modifier
) {
    val windowSizeClass = currentLouArayaWindowSizeClass()
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
            RefreshOnReturn(catalogViewModel::loadProducts)
            Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                CatalogScreen(
                    viewModel = catalogViewModel,
                    onProductClick = { productId ->
                        navController.navigate(LouArayaRoutes.productDetail(productId))
                    },
                    onViewRequests = { navController.navigate(LouArayaRoutes.REQUESTS) },
                    modifier = Modifier.padding(innerPadding),
                    windowSizeClass = windowSizeClass,
                    imageStorage = productImageStorage,
                    onAdmin = { navController.navigate(LouArayaRoutes.ADMIN_PRODUCTS) }
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
            RefreshOnReturn(detailViewModel::loadProduct)
            ProductDetailScreen(
                viewModel = detailViewModel,
                onBack = { navController.popBackStack() },
                onCreateRequest = { navController.navigate(LouArayaRoutes.createRequest(it)) },
                windowSizeClass = windowSizeClass,
                imageStorage = productImageStorage
            )
        }

        composable(
            route = LouArayaRoutes.CREATE_REQUEST,
            arguments = listOf(navArgument(LouArayaRoutes.PRODUCT_ID_ARG) { type = NavType.LongType })
        ) { entry ->
            val productId = requireNotNull(entry.arguments).getLong(LouArayaRoutes.PRODUCT_ID_ARG)
            val createViewModel: CreateCommercialRequestViewModel = viewModel(
                factory = CreateCommercialRequestViewModelFactory(
                    productId, getProductByIdUseCase, createCommercialRequestUseCase
                )
            )
            CreateCommercialRequestScreen(
                viewModel = createViewModel,
                onBack = { navController.popBackStack() },
                windowSizeClass = windowSizeClass,
                onViewRequests = {
                    navController.navigate(LouArayaRoutes.REQUESTS) {
                        // El formulario completado no reaparece al volver desde el listado.
                        popUpTo(LouArayaRoutes.CATALOG)
                        launchSingleTop = true
                    }
                }
            )
        }

        composable(LouArayaRoutes.REQUESTS) {
            val requestsViewModel: CommercialRequestsViewModel = viewModel(
                factory = CommercialRequestsViewModelFactory(getCommercialRequestsUseCase)
            )
            CommercialRequestsScreen(
                viewModel = requestsViewModel,
                onBack = { navController.popBackStack() },
                onRequestClick = { navController.navigate(LouArayaRoutes.requestDetail(it)) },
                windowSizeClass = windowSizeClass
            )
        }

        composable(LouArayaRoutes.ADMIN_PRODUCTS) {
            val adminCatalogViewModel: CatalogViewModel = viewModel(
                factory = CatalogViewModelFactory(getProductsUseCase)
            )
            RefreshOnReturn(adminCatalogViewModel::loadProducts)
            AdminProductCatalogScreen(
                viewModel = adminCatalogViewModel,
                imageStorage = productImageStorage,
                onBack = { navController.popBackStack() },
                onSelectProduct = { navController.navigate(LouArayaRoutes.adminEditImage(it)) },
                windowSizeClass = windowSizeClass
            )
        }

        composable(
            route = LouArayaRoutes.ADMIN_EDIT_IMAGE,
            arguments = listOf(navArgument(LouArayaRoutes.ADMIN_PRODUCT_ID_ARG) { type = NavType.LongType })
        ) { entry ->
            val productId = requireNotNull(entry.arguments).getLong(LouArayaRoutes.ADMIN_PRODUCT_ID_ARG)
            val imageViewModel: AdminProductImageViewModel = viewModel(
                factory = AdminProductImageViewModelFactory(
                    productId, getProductByIdUseCase, updateProductImageUseCase, productImageStorage
                )
            )
            AdminProductImageScreen(
                viewModel = imageViewModel,
                imageStorage = productImageStorage,
                onBack = { navController.popBackStack() },
                windowSizeClass = windowSizeClass
            )
        }

        composable(
            route = LouArayaRoutes.REQUEST_DETAIL,
            arguments = listOf(navArgument(LouArayaRoutes.REQUEST_ID_ARG) { type = NavType.LongType })
        ) { entry ->
            val requestId = requireNotNull(entry.arguments).getLong(LouArayaRoutes.REQUEST_ID_ARG)
            val requestViewModel: CommercialRequestDetailViewModel = viewModel(
                factory = CommercialRequestDetailViewModelFactory(
                    requestId, getCommercialRequestByIdUseCase, getProductByIdUseCase
                )
            )
            CommercialRequestDetailScreen(
                viewModel = requestViewModel,
                onBack = { navController.popBackStack() },
                onProductClick = { navController.navigate(LouArayaRoutes.productDetail(it)) },
                windowSizeClass = windowSizeClass
            )
        }
    }
}
