package cl.duoc.louaraya.data.repository

import android.app.Application
import androidx.room.Room
import cl.duoc.louaraya.data.local.LouArayaDatabase
import cl.duoc.louaraya.data.local.seed.DemoSeedCallback
import cl.duoc.louaraya.domain.usecase.LocalUseCases
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class LocalSnapshotObservationTest {
    private lateinit var database: LouArayaDatabase
    private lateinit var useCases: LocalUseCases

    @Before fun setUp() {
        database = Room.inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication(), LouArayaDatabase::class.java)
            .addCallback(DemoSeedCallback())
            .build()
        useCases = LocalUseCases(RoomLocalRepository(database))
    }

    @After fun tearDown() { database.close() }

    @Test fun observedSnapshotsEmitInitialStateAndThenSessionChange() = runBlocking {
        useCases.initialize()
        val first = CompletableDeferred<cl.duoc.louaraya.domain.model.LocalSnapshot>()
        val next = CompletableDeferred<cl.duoc.louaraya.domain.model.LocalSnapshot>()
        val observer = launch {
            var emissions = 0
            useCases.observeSnapshots().collect { snapshot ->
                if (emissions++ == 0) first.complete(snapshot) else next.complete(snapshot)
            }
        }

        try {
            assertEquals(null, withTimeout(10_000) { first.await() }.profile)
            useCases.signIn("cliente.demo", "LouDemo2026!")
            assertEquals("cliente.demo", withTimeout(10_000) { next.await() }.profile?.alias)
        } finally {
            observer.cancelAndJoin()
        }
    }
}
