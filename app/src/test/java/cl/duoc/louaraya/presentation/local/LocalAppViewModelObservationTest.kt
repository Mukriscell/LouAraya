package cl.duoc.louaraya.presentation.local

import android.app.Application
import androidx.lifecycle.ViewModelStore
import androidx.room.Room
import cl.duoc.louaraya.data.local.LouArayaDatabase
import cl.duoc.louaraya.data.local.seed.DemoSeedCallback
import cl.duoc.louaraya.data.repository.RoomLocalRepository
import cl.duoc.louaraya.domain.model.RequestIntention
import cl.duoc.louaraya.domain.model.UserRole
import cl.duoc.louaraya.domain.model.UserProfile
import cl.duoc.louaraya.domain.repository.LocalRepository
import cl.duoc.louaraya.domain.usecase.FormValidationException
import cl.duoc.louaraya.domain.usecase.LocalUseCases
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.test.resetMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class LocalAppViewModelObservationTest {
    private lateinit var database: LouArayaDatabase

    @Before fun setUp() {
        Dispatchers.setMain(Dispatchers.Unconfined)
        database = Room.inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication(), LouArayaDatabase::class.java)
            .addCallback(DemoSeedCallback())
            .build()
    }

    @After fun tearDown() {
        database.close()
        Dispatchers.resetMain()
    }

    @Test fun viewModelTracksRoomWritesAndScopesSnapshotsToTheCurrentProfile() = runBlocking {
        val roomRepository = RoomLocalRepository(database)
        val viewModel = LocalAppViewModel(LocalUseCases(roomRepository))
        val store = ViewModelStore().apply { put("local-app", viewModel) }
        val externalUseCases = LocalUseCases(RoomLocalRepository(database))

        try {
            awaitState(viewModel) { it.initialized && !it.busy }
            viewModel.execute(operation = { signIn("cliente.demo", "LouDemo2026!") })
            val firstClient = awaitState(viewModel) { !it.busy && it.snapshot.profile?.alias == "cliente.demo" }.snapshot.profile!!
            val product = externalUseCases.snapshot().products.first()
            val firstRequest = externalUseCases.createRequest(product.id, RequestIntention.RESERVATION, product.price, "Primera reserva", null)
            val firstClientUpdate = awaitState(viewModel) {
                it.snapshot.profile?.id == firstClient.id && it.snapshot.requests.singleOrNull()?.id == firstRequest.id
            }
            assertEquals(firstClient.id, firstClientUpdate.snapshot.requests.single().ownerId)

            val secondClient = externalUseCases.register("Cliente reactivo", "cliente.reactivo", "ClaveReactiva2026!", 1, "")
            val secondRequest = externalUseCases.createRequest(product.id, RequestIntention.RESERVATION, product.price, "Segunda reserva", null)
            val secondClientUpdate = awaitState(viewModel) {
                it.snapshot.profile?.id == secondClient.id && it.snapshot.requests.singleOrNull()?.id == secondRequest.id
            }
            assertEquals(secondClient.id, secondClientUpdate.snapshot.requests.single().ownerId)

            externalUseCases.signIn("admin.demo", "LouDemo2026!")
            val adminUpdate = awaitState(viewModel) {
                it.snapshot.profile?.role == UserRole.ADMIN && it.snapshot.requests.size == 2
            }
            assertEquals(setOf(firstClient.id, secondClient.id), adminUpdate.snapshot.requests.map { it.ownerId }.toSet())

            viewModel.execute(operation = { throw FormValidationException(mapOf("alias" to "Revisa el alias.")) })
            awaitState(viewModel) { !it.busy && it.fieldErrors["alias"] == "Revisa el alias." }
            externalUseCases.signOut()
            val signedOut = awaitState(viewModel) { it.snapshot.profile == null && it.snapshot.requests.isEmpty() }
            assertEquals("Revisa el alias.", signedOut.fieldErrors["alias"])
            assertFalse(signedOut.busy)

            externalUseCases.signIn("admin.demo", "LouDemo2026!")
            val restoredAdmin = awaitState(viewModel) { it.snapshot.profile?.role == UserRole.ADMIN && it.snapshot.requests.size == 2 }
            assertEquals("Revisa el alias.", restoredAdmin.fieldErrors["alias"])
            assertTrue(restoredAdmin.initialized)
        } finally {
            store.clear()
        }
    }

    @Test fun committedRequestRemainsSuccessfulWhenItsReloadFailsAndRecoversWithoutResubmitting() = runBlocking {
        val repository = FailingReadsRepository(RoomLocalRepository(database))
        val viewModel = LocalAppViewModel(LocalUseCases(repository))
        val store = ViewModelStore().apply { put("local-app", viewModel) }
        try {
            awaitState(viewModel) { it.initialized && !it.busy }
            viewModel.execute { signIn("cliente.demo", "LouDemo2026!") }
            val product = awaitState(viewModel) { !it.busy && it.snapshot.profile != null }.snapshot.products.first()
            var callbacks = 0
            var requestId = 0L
            val completed = CompletableDeferred<Unit>()
            viewModel.execute(message = "Solicitud guardada", onSuccess = { callbacks++; completed.complete(Unit) }) {
                requestId = createRequest(product.id, RequestIntention.RESERVATION, product.price, "Prueba de recuperación", null).id
                repository.failReads = true
            }
            val stale = awaitState(viewModel) { !it.busy && it.readError != null }
            withTimeout(15_000) { completed.await() }
            assertEquals(null, stale.error)
            assertTrue(stale.fieldErrors.isEmpty())
            assertEquals("Solicitud guardada", stale.message)
            assertEquals(1, callbacks)
            assertEquals(requestId, RoomLocalRepository(database).requests(null).single().id)

            repository.failReads = false
            val recovered = awaitState(viewModel) { it.readError == null && it.snapshot.requests.singleOrNull()?.id == requestId }
            assertEquals("Solicitud guardada", recovered.message)
            assertEquals(1, callbacks)
            assertEquals(1, recovered.snapshot.requests.size)
        } finally { store.clear() }
    }

    @Test fun firstSnapshotFailureCanRecoverInsteadOfRemainingOnTheLoadingScreen() = runBlocking {
        val repository = FailingReadsRepository(RoomLocalRepository(database)).apply { failReads = true }
        val viewModel = LocalAppViewModel(LocalUseCases(repository))
        val store = ViewModelStore().apply { put("local-app", viewModel) }
        try {
            val failed = awaitState(viewModel) { !it.busy && it.readError != null }
            assertFalse(failed.initialized)
            assertEquals("No fue posible cargar los datos.", failed.error)
            repository.failReads = false
            val recovered = awaitState(viewModel) { it.initialized && it.readError == null }
            assertEquals(null, recovered.error)
            assertEquals(null, recovered.snapshot.profile)
        } finally { store.clear() }
    }

    @Test fun queuedNavigationRecordsAreNotDroppedWhenAnotherRecordClaimsTheIdleState() = runBlocking {
        val useCases = LocalUseCases(RoomLocalRepository(database))
        useCases.initialize()
        val client = useCases.signIn("cliente.demo", "LouDemo2026!")
        val viewModel = LocalAppViewModel(useCases)
        val store = ViewModelStore().apply { put("local-app", viewModel) }
        val operationGate = CompletableDeferred<Unit>()
        val firstStarted = CompletableDeferred<Unit>()
        val firstGate = CompletableDeferred<Unit>()
        val secondFinished = CompletableDeferred<Unit>()
        try {
            awaitState(viewModel) { it.initialized && !it.busy }
            viewModel.execute { operationGate.await() }
            viewModel.recordNavigation(client.id) { firstStarted.complete(Unit); firstGate.await() }
            viewModel.recordNavigation(client.id) { secondFinished.complete(Unit) }
            operationGate.complete(Unit)
            withTimeout(15_000) { firstStarted.await() }
            firstGate.complete(Unit)
            withTimeout(15_000) { secondFinished.await() }
            awaitState(viewModel) { !it.busy }
            Unit
        } finally { operationGate.complete(Unit); firstGate.complete(Unit); store.clear() }
    }

    @Test fun failedInitializationMustBeRetriedBeforeObservingSnapshots() = runBlocking {
        var failInitialization = true
        val repository = object : LocalRepository by RoomLocalRepository(database) {
            override suspend fun initialize() {
                check(!failInitialization) { "No fue posible preparar el almacenamiento." }
                RoomLocalRepository(database).initialize()
            }
        }
        val viewModel = LocalAppViewModel(LocalUseCases(repository))
        val store = ViewModelStore().apply { put("local-app", viewModel) }
        try {
            val failed = awaitState(viewModel) { !it.busy && it.error != null }
            assertFalse(failed.initialized)
            assertEquals("No fue posible preparar el almacenamiento.", failed.error)
            failInitialization = false
            viewModel.initialize()
            awaitState(viewModel) { it.initialized && !it.busy }
            assertEquals(2, repository.profiles().size)
        } finally { store.clear() }
    }

    private class FailingReadsRepository(private val delegate: LocalRepository) : LocalRepository by delegate {
        @Volatile var failReads = false
        override suspend fun session(): UserProfile? {
            check(!failReads) { "Fallo de lectura simulado" }
            return delegate.session()
        }
    }

    private suspend fun awaitState(viewModel: LocalAppViewModel, predicate: (LocalAppState) -> Boolean): LocalAppState =
        withTimeout(15_000) { viewModel.uiState.first(predicate) }
}
