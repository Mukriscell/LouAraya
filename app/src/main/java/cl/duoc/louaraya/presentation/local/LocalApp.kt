package cl.duoc.louaraya.presentation.local

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.testTag
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.CreationExtras
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.NavBackStackEntry
import androidx.navigation.compose.*
import androidx.navigation.navArgument
import cl.duoc.louaraya.AppContainer
import cl.duoc.louaraya.domain.model.UserRole
import cl.duoc.louaraya.R
import androidx.navigation.NavGraph.Companion.findStartDestination
import cl.duoc.louaraya.presentation.admin.AdminProductImageScreen
import cl.duoc.louaraya.presentation.admin.AdminProductImageViewModel
import cl.duoc.louaraya.presentation.admin.AdminProductImageViewModelFactory
import cl.duoc.louaraya.presentation.common.currentLouArayaWindowSizeClass

internal val LocalBusy = compositionLocalOf { false }

@Composable
private fun landscapeKeyboardVisible(): Boolean =
    LocalConfiguration.current.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE &&
        WindowInsets.ime.getBottom(LocalDensity.current) > 0

@Composable
fun LouArayaApp(container: AppContainer, noticeRequest: Pair<Long, Long>? = null, consumeNotice: () -> Unit = {}) {
    val vm: LocalAppViewModel = viewModel(factory = object : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = LocalAppViewModel(container.localUseCases) as T
    })
    val state by vm.uiState.collectAsState()
    var splashComplete by rememberSaveable { mutableStateOf(false) }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, vm) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME && vm.uiState.value.initialized) vm.refresh()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    CompositionLocalProvider(LocalBusy provides state.busy) {
        if (state.initialized) LocalNotificationEffect(container.localNotificationCoordinator, deferDelivery = noticeRequest != null)
        if (!splashComplete) {
            LaunchSplash(onFinished = { splashComplete = true })
        } else if (!state.initialized) {
            Scaffold(topBar = { BrandHeader("LouAraya") }) { padding -> Box(Modifier.padding(padding)) {
                Page(maxWidth = 600.dp) {
                    Intro("Macramé · Hecho a mano", if (state.error == null) "Preparando el taller." else "No pudimos iniciar\nla aplicación.")
                    StateContent(if (state.error == null) "Un momento…" else "Volvamos a intentarlo.", state.error ?: "Estamos cargando tu espacio.", loading = state.error == null, action = if (state.error != null) "Reintentar" else null, onAction = vm::initialize)
                }
            } }
        } else {
            val profile = state.snapshot.profile
            key(profile?.id) {
                if (profile == null) AuthNavigation(container, vm, state)
                else MemberNavigation(container, vm, state, noticeRequest, consumeNotice)
            }
        }
    }
}

@Composable
private fun AuthNavigation(container: AppContainer, vm: LocalAppViewModel, state: LocalAppState) {
    val nav = rememberNavController()
    val entry by nav.currentBackStackEntryAsState()
    val route = entry?.destination?.route ?: "welcome"
    val compactInput = landscapeKeyboardVisible()
    LaunchedEffect(route) { vm.dismissError() }
    val openLogin: () -> Unit = {
        if (route == "register" && nav.previousBackStackEntry?.destination?.route == "login") nav.popBackStack()
        else nav.navigate("login") { popUpTo("welcome"); launchSingleTop = true }
    }
    val openRegistration: () -> Unit = { nav.navigate("register") { launchSingleTop = true } }
    AuthMotionScope(route) {
    Scaffold(topBar = {
        if (!compactInput) AuthTopBar(route) { nav.popBackStack() }
    }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).imePadding()) {
            Feedback(state, vm::dismissError, vm::refresh)
            NavHost(nav, startDestination = "welcome", modifier = Modifier.weight(1f),
                enterTransition = { AtelierMotion.enter() }, exitTransition = { AtelierMotion.exit() },
                popEnterTransition = { AtelierMotion.enter(back = true) }, popExitTransition = { AtelierMotion.exit(back = true) }) {
                composable("welcome") { WelcomeScreen(openLogin, openRegistration) }
                composable("login") { LoginScreen(vm, openRegistration) }
                composable("register") {
                    val photoVm: RegistrationPhotoViewModel = viewModel(factory = object : ViewModelProvider.Factory {
                        @Suppress("UNCHECKED_CAST")
                        override fun <T : ViewModel> create(modelClass: Class<T>): T = RegistrationPhotoViewModel(container.avatarImageStorage) as T
                        @Suppress("UNCHECKED_CAST")
                        override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T = RegistrationPhotoViewModel(container.avatarImageStorage, extras.createSavedStateHandle()) as T
                    })
                    RegisterScreen(vm, photoVm, container.avatarImageStorage, openLogin)
                }
            }
        }
    }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MemberNavigation(container: AppContainer, vm: LocalAppViewModel, state: LocalAppState, noticeRequest: Pair<Long, Long>?, consumeNotice: () -> Unit) {
    val snapshot = state.snapshot
    val actor = requireNotNull(snapshot.profile)
    val admin = actor.role == UserRole.ADMIN
    val nav = rememberNavController()
    val entry by nav.currentBackStackEntryAsState()
    val route = entry?.destination?.route ?: "home"
    val quotationStepBack = remember { mutableStateMapOf<String, () -> Unit>() }
    val window = currentLouArayaWindowSizeClass()
    val imageStorage = container.productImageStorage
    val tabs = if (admin) listOf("home" to "Taller", "products" to "Piezas", "campaigns" to "Campañas", "requests" to "Solicitudes")
        else listOf("home" to "Inicio", "catalog" to "Piezas", "favorites" to "Guardados", "requests" to "Solicitudes")
    val topRoute = route in tabs.map { it.first }
    val tabRoutes = tabs.map { it.first }.toSet()
    fun navigateTopLevel(destination: String) {
        nav.navigate(destination) {
            popUpTo(nav.graph.findStartDestination().id) { saveState = true }
            launchSingleTop = true


            restoreState = destination != "home"
        }


        if (destination != "home" && nav.currentBackStackEntry?.destination?.route != destination) {
            nav.popBackStack(destination, inclusive = false)
        }
    }
    val title = when {
        route == "home" -> if (admin) "Tu taller" else "LouAraya"
        route.startsWith("product/") -> "La pieza"
        route.startsWith("stock/") -> "Bodega"
        route.startsWith("quote/") -> "Una pieza para ti"
        route.startsWith("request/") -> "Solicitud #${entry?.arguments?.getLong("id") ?: ""}"
        route.startsWith("edit-product/") -> if (entry?.arguments?.getLong("id") == 0L) "Nueva pieza" else "Editar producto"
        route.startsWith("edit-collection/") -> if (entry?.arguments?.getLong("id") == 0L) "Nueva colección" else "Editar colección"
        route.startsWith("edit-campaign/") -> if (entry?.arguments?.getLong("id") == 0L) "Nueva campaña" else "Editar campaña"
        route.startsWith("campaign-results/") -> "Resultados de campaña"
        route.startsWith("collection/") -> "Colección"
        route == "qr" -> "Escanear etiqueta"
        route == "map" -> "Encuéntranos"
        route == "notices" -> "Tus avisos"
        route == "profile" -> "Mi perfil"
        else -> tabs.firstOrNull { it.first == route }?.second ?: "Lou Araya"
    }
    val open: (String) -> Unit = { destination ->
        val path = destination.substringBefore('?')
        val journey = destination.substringAfter("?journey=", "").takeIf { it.isNotEmpty() }
        val tracked = !admin && (path.startsWith("campaign/") || path.startsWith("collection/") || path.startsWith("product/"))
        if (tracked) {
            val id = path.substringAfter('/').toLongOrNull()
            val campaign = if (path.startsWith("campaign/")) snapshot.campaigns.firstOrNull { it.id == id } else null
            val sourceKey = if (campaign != null) java.util.UUID.randomUUID().toString() else null
            val resolved = if (campaign != null) "collection/${campaign.collectionId}?journey=$sourceKey" else destination
            if (!path.startsWith("campaign/") || campaign != null) nav.navigate(resolved) { launchSingleTop = true }
            vm.recordNavigation(actor.id) {
                require(id != null) { "El destino no es válido." }
                when {
                    path.startsWith("campaign/") -> startCampaignJourney(id, requireNotNull(sourceKey), campaign?.collectionId)
                    path.startsWith("collection/") -> recordCollectionView(id, journey)
                    else -> recordProductView(id, journey)
                }
            }
        } else if (admin && path.startsWith("campaign/")) {
            snapshot.campaigns.firstOrNull { it.id == path.substringAfter('/').toLongOrNull() }?.let {
                nav.navigate("collection/${it.collectionId}") { launchSingleTop = true }
            }
        } else if (tabs.any { it.first == path }) navigateTopLevel(path)
        else nav.navigate(destination) { launchSingleTop = true }
    }
    val journeyArguments = listOf(navArgument("id") { type = NavType.LongType }, navArgument("journey") { type = NavType.StringType; nullable = true; defaultValue = null })
    fun returnFrom(screen: NavBackStackEntry) {
        if (nav.currentBackStackEntry?.id == screen.id && !nav.popBackStack()) navigateTopLevel("home")
    }
    fun openFrom(screen: NavBackStackEntry?, destination: String) {
        if (screen != null && nav.currentBackStackEntry?.id == screen.id) open(destination)
    }
    fun contextualOpen(journey: String?): (String) -> Unit = { destination ->
        open(if (journey != null && (destination.startsWith("product/") || destination.startsWith("quote/")) && !destination.contains('?')) "$destination?journey=$journey" else destination)
    }
    LaunchedEffect(route) { vm.dismissError() }
    val openNotice: (Long) -> Unit = { id ->
        val origin = nav.currentBackStackEntry
        var destination = ""
        vm.execute(onSuccess = { openFrom(origin, destination) }) { destination = openNotice(id) }
    }
    LaunchedEffect(noticeRequest, actor.id, state.busy) {
        if (noticeRequest != null && !state.busy && !vm.uiState.value.busy) {
            if (noticeRequest.first == actor.id) {
                val origin = nav.currentBackStackEntry
                var destination = ""
                vm.execute(onSuccess = { openFrom(origin, destination) }) {

                    try { destination = this.openNotice(noticeRequest.second) }
                    finally { consumeNotice() }
                }
            } else {
                vm.execute { error("Cambia al perfil destinatario para abrir este aviso.") }
                consumeNotice()
            }
        }
    }
    val ownHeader = route.startsWith("image/") || route == "avatar-photo"
    val compactInput = landscapeKeyboardVisible()
    Scaffold(contentWindowInsets = if (ownHeader) WindowInsets(0, 0, 0, 0) else if (compactInput) WindowInsets.systemBars else WindowInsets.navigationBars,
        topBar = {
            if (!ownHeader && !compactInput) BrandHeader(title,
                back = if (!topRoute) ({
                    val stepBack = entry?.id?.let { quotationStepBack[it] }
                    if (stepBack != null) stepBack()
                    else entry?.let(::returnFrom)
                }) else null,
                profile = if (route != "profile") ({ open("profile") }) else null,
                notices = if (topRoute) ({ open("notices") }) else null,
                unread = snapshot.notices.count { !it.read })
        },
        bottomBar = {
            if (!compactInput && (topRoute || route.startsWith("collection/"))) NavigationBar(containerColor = MaterialTheme.colorScheme.background, tonalElevation = 0.dp, modifier = Modifier.heightIn(min = 70.dp), windowInsets = WindowInsets.navigationBars) {
                tabs.forEach { (destination, label) ->
                    val selected = route == destination || (!admin && destination == "catalog" && route.startsWith("collection/"))
                    val iconScale by animateFloatAsState(if (selected) 1.12f else 1f, spring(dampingRatio = .7f), label = "tab-icon")
                    NavigationBarItem(modifier = Modifier.testTag("tab-$destination"), selected = selected, onClick = {
                        navigateTopLevel(destination)
                    }, icon = { DesignIcon(when (destination) { "home" -> R.drawable.la_house; "favorites" -> R.drawable.la_heart; "requests" -> R.drawable.la_clipboard_list; "products" -> R.drawable.la_leaf; "campaigns" -> R.drawable.la_megaphone; else -> R.drawable.la_search }, modifier = Modifier.graphicsLayer { scaleX = iconScale; scaleY = iconScale }) }, label = { Text(label, maxLines = 1, style = MaterialTheme.typography.bodySmall) }, colors = NavigationBarItemDefaults.colors(indicatorColor = MaterialTheme.colorScheme.primaryContainer, selectedIconColor = MaterialTheme.colorScheme.onSurface, selectedTextColor = MaterialTheme.colorScheme.primary, unselectedIconColor = MaterialTheme.colorScheme.secondary, unselectedTextColor = MaterialTheme.colorScheme.secondary))
                }
            }
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).imePadding()) {
            if (route != "qr") Feedback(state, vm::dismissError, vm::refresh)
            NavHost(nav, startDestination = "home", modifier = Modifier.weight(1f),
                enterTransition = { AtelierMotion.enter(tab = initialState.destination.route in tabRoutes && targetState.destination.route in tabRoutes) },
                exitTransition = { AtelierMotion.exit(tab = initialState.destination.route in tabRoutes && targetState.destination.route in tabRoutes) },
                popEnterTransition = { AtelierMotion.enter(back = true, tab = initialState.destination.route in tabRoutes && targetState.destination.route in tabRoutes) },
                popExitTransition = { AtelierMotion.exit(back = true, tab = initialState.destination.route in tabRoutes && targetState.destination.route in tabRoutes) }) {
                composable("home") { DashboardScreen(snapshot, imageStorage, open) }
                composable("catalog") { CatalogScreen(snapshot, imageStorage, vm, open) }
                composable("favorites") { if (!admin) CatalogScreen(snapshot, imageStorage, vm, open, favoritesOnly = true) }
                composable("products") { if (admin) ProductManagementScreen(snapshot, imageStorage, open) else Text("Acceso restringido") }
                composable("stock/{id}", arguments = listOf(navArgument("id") { type = NavType.LongType })) { back ->
                    val product = snapshot.products.firstOrNull { it.id == back.arguments?.getLong("id") }
                    if (!admin) Page { Text("Acceso restringido") }
                    else if (product == null) Page { StateContent("Producto no disponible", "Vuelve a la gestión de productos.", action = "Volver a productos") { open("products") } }
                    else StockScreen(product, snapshot.stockMovements.filter { it.productId == product.id }, vm)
                }
                composable("campaigns") { if (admin) CampaignManagementScreen(snapshot, open) else Text("Acceso restringido") }
                composable("profile") { ProfileScreen(actor, vm, open, container.avatarImageStorage) }
                composable("avatar-photo") { back ->
                    val photoVm: AvatarPhotoViewModel = viewModel(factory = object : ViewModelProvider.Factory {
                        private fun createPhoto(handle: SavedStateHandle) = AvatarPhotoViewModel(actor.id, container.avatarImageStorage, cl.duoc.louaraya.domain.usecase.UpdateAvatarPhotoUseCase(container.localUseCases, container.avatarImageStorage), handle)
                        @Suppress("UNCHECKED_CAST")
                        override fun <T : ViewModel> create(modelClass: Class<T>): T = createPhoto(SavedStateHandle()) as T
                        @Suppress("UNCHECKED_CAST")
                        override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T = createPhoto(extras.createSavedStateHandle()) as T
                    })
                    AvatarPhotoScreen(photoVm, container.avatarImageStorage, actor) { vm.refresh(); returnFrom(back) }
                }
                composable("requests") { RequestsScreen(snapshot, vm, open) }
                composable("notices") { NoticesScreen(snapshot.notices, actor.id, vm, openNotice, open) }
                composable("map") { FairMapScreen(snapshot.events) }
                composable("qr") { back ->
                    val imageVm: QrImageViewModel = viewModel(factory = object : ViewModelProvider.Factory {
                        @Suppress("UNCHECKED_CAST")
                        override fun <T : ViewModel> create(modelClass: Class<T>): T = QrImageViewModel(container.qrImageDecoder) as T
                    })
                    QrScannerScreen(vm, imageVm) { destination -> openFrom(back, destination) }
                }
                composable("product/{id}?journey={journey}", arguments = journeyArguments) { back ->
                    val product = snapshot.products.firstOrNull { it.id == back.arguments?.getLong("id") && (admin || it.active) }
                    if (state.readError != null) Page { StateContent("No pudimos abrir\nesta pieza.", "Vuelve a cargar sus detalles para continuar.", action = "Reintentar", onAction = vm::refresh); SecondaryAction("Volver al catálogo") { open("catalog") } }
                    else if (state.busy && snapshot.products.isEmpty()) Page { StateContent("Preparando\nlos detalles.", "Estamos cargando la información de esta pieza.", loading = true) }
                    else if (product == null) Page { StateContent("Esta pieza ya no\nestá disponible.", "El producto no existe o ya no está disponible. Puedes descubrir otros diseños en el catálogo.", action = "Volver al catálogo") { open("catalog") } }
                    else ProductScreen(product, snapshot, imageStorage, vm, contextualOpen(back.arguments?.getString("journey")), back.arguments?.getString("journey"))
                }
                composable("collection/{id}?journey={journey}", arguments = journeyArguments) { back ->
                    val collection = snapshot.collections.firstOrNull { it.id == back.arguments?.getLong("id") }
                    if (collection == null) Page("Colección no disponible") { Text("La colección no existe.") }
                    else CatalogScreen(snapshot, imageStorage, vm, contextualOpen(back.arguments?.getString("journey")), collection = collection, journeyId = back.arguments?.getString("journey"))
                }
                composable("quote/{id}?journey={journey}", arguments = journeyArguments) { back ->
                    val product = snapshot.products.firstOrNull { it.id == back.arguments?.getLong("id") && it.active }
                    if (product != null && !admin) QuoteScreen(product, vm, contextualOpen(back.arguments?.getString("journey")), back.arguments?.getString("journey"), imageStorage) { handler ->
                        if (handler == null) quotationStepBack.remove(back.id) else quotationStepBack[back.id] = handler
                    }
                    else Page("Solicitud no disponible") { Text("Selecciona una pieza desde el catálogo con un perfil cliente.") }
                }
                composable("request/{id}", arguments = listOf(navArgument("id") { type = NavType.LongType })) { back ->
                    val request = snapshot.requests.firstOrNull { it.id == back.arguments?.getLong("id") }
                    if (request == null) Page("Solicitud no disponible") { Text("Esta solicitud no pertenece a tu perfil o ya no existe.") }
                    else RequestScreen(request, snapshot, imageStorage, vm, open)
                }
                composable("campaign-results/{id}", arguments = listOf(navArgument("id") { type = NavType.LongType })) { back ->
                    if (admin) CampaignResultsScreen(snapshot, requireNotNull(back.arguments).getLong("id"), open)
                }
                composable("edit-product/{id}", arguments = listOf(navArgument("id") { type = NavType.LongType })) { back ->
                    if (admin) ProductEditorScreen(snapshot.products.firstOrNull { it.id == back.arguments?.getLong("id") }, vm, { returnFrom(back) }, open, imageStorage)
                }
                composable("edit-collection/{id}", arguments = listOf(navArgument("id") { type = NavType.LongType })) { back ->
                    if (admin) CollectionEditor(snapshot.collections.firstOrNull { it.id == back.arguments?.getLong("id") }, snapshot.products, vm) { returnFrom(back) }
                }
                composable("edit-campaign/{id}", arguments = listOf(navArgument("id") { type = NavType.LongType })) { back ->
                    if (admin) CampaignEditor(snapshot.campaigns.firstOrNull { it.id == back.arguments?.getLong("id") }, snapshot.collections, vm) { returnFrom(back) }
                }
                composable("image/{id}", arguments = listOf(navArgument("id") { type = NavType.LongType })) { back ->
                    if (admin) {
                        val id = requireNotNull(back.arguments).getLong("id")
                        val imageVm: AdminProductImageViewModel = viewModel(factory = AdminProductImageViewModelFactory(id, container.getProductByIdUseCase, container.updateProductImageUseCase, imageStorage))
                        AdminProductImageScreen(imageVm, imageStorage, { vm.refresh(); returnFrom(back) }, window)
                    }
                }
            }
        }
    }
}

@Composable
private fun Feedback(state: LocalAppState, dismiss: () -> Unit, retry: () -> Unit) {
    if (state.busy) LinearProgressIndicator(Modifier.fillMaxWidth())
    if (state.readError != null) Surface(color = MaterialTheme.colorScheme.errorContainer) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(state.readError, Modifier.weight(1f).padding(vertical = 12.dp))
            TextButton(onClick = retry, enabled = !state.busy) { Text("Reintentar") }
        }
    }
    AnimatedVisibility((state.error != null && state.fieldErrors.isEmpty()) || state.message != null,
        enter = fadeIn(tween(AtelierMotion.STANDARD)) + expandVertically(tween(AtelierMotion.STANDARD)),
        exit = fadeOut(tween(AtelierMotion.QUICK)) + shrinkVertically(tween(AtelierMotion.QUICK))) {
        Surface(color = if (state.error != null) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.secondaryContainer) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(state.error ?: state.message.orEmpty(), Modifier.weight(1f).padding(vertical = 12.dp))
                TextButton(onClick = dismiss) { Text("Cerrar") }
            }
        }
    }
}

