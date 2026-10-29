package cl.duoc.louaraya.data.image

import android.content.Context
import android.graphics.Bitmap
import androidx.core.net.toUri
import androidx.lifecycle.SavedStateHandle
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import cl.duoc.louaraya.data.local.LouArayaDatabase
import cl.duoc.louaraya.data.local.seed.DemoSeedCallback
import cl.duoc.louaraya.data.repository.RoomLocalRepository
import cl.duoc.louaraya.domain.image.ProductImageStorage
import cl.duoc.louaraya.domain.usecase.LocalUseCases
import cl.duoc.louaraya.domain.usecase.UpdateAvatarPhotoUseCase
import cl.duoc.louaraya.presentation.local.AvatarPhotoViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.*
import org.junit.Test
import java.io.File

class AvatarPhotoPersistenceTest {
    private val context get() = ApplicationProvider.getApplicationContext<Context>()
    private fun main(block: () -> Unit) = InstrumentationRegistry.getInstrumentation().runOnMainSync(block)
    private suspend fun idle(vm: AvatarPhotoViewModel) = withTimeout(10_000) { while(vm.state.value.busy) delay(20) }
    private fun writeCapture(uri: String) {
        val bitmap = Bitmap.createBitmap(960, 1280, Bitmap.Config.ARGB_8888).apply { eraseColor(android.graphics.Color.rgb(166, 78, 50)) }
        context.contentResolver.openOutputStream(uri.toUri())!!.use { bitmap.compress(Bitmap.CompressFormat.JPEG, 95, it) }
        bitmap.recycle()
    }

    @Test fun avatarCaptureIsPrivateIsolatedAndBoundedAndRejectsPathTraversal() = runBlocking {
        val avatar = LocalProductImageStorage(context, "avatar-images", "avatar-captures", 640)
        val product = LocalProductImageStorage(context)
        val uri = avatar.createCameraCaptureUri()
        writeCapture(uri)
        product.cleanupAbandonedCameraCaptures(emptySet())
        assertNotNull(avatar.loadPreview(uri, 640))
        val reference = avatar.saveFromSource(uri)
        try {
            assertTrue(reference.startsWith("avatar-images/"))
            assertNull(product.loadFromReference(reference, 640))
            assertNull(avatar.loadFromReference("../louaraya.db", 640))
            val bytes = requireNotNull(avatar.loadFromReference(reference, 640))
            val bitmap = android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
            assertTrue(maxOf(bitmap.width, bitmap.height) <= 640)
            bitmap.recycle()
        } finally { avatar.delete(reference); avatar.deleteCameraCapture(uri) }
    }

    @Test fun restoredCaptureAndPreviewCanBeCancelledAndConfirmedWithoutLosingOldPhoto() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(context, LouArayaDatabase::class.java).addCallback(DemoSeedCallback()).build()
        val storage = LocalProductImageStorage(context, "avatar-images", "avatar-captures", 640)
        var saved: String? = null
        try {
            val cases = LocalUseCases(RoomLocalRepository(db)); cases.initialize()
            val user = cases.signIn("cliente.demo", "LouDemo2026!")
            val handle = SavedStateHandle()
            lateinit var vm: AvatarPhotoViewModel
            main { vm = AvatarPhotoViewModel(user.id, storage, UpdateAvatarPhotoUseCase(cases, storage), handle) }
            idle(vm)
            main { vm.takePhoto() }; idle(vm)
            val cancelled = requireNotNull(vm.state.value.pendingUri)
            main { vm.cameraResult(false) }; idle(vm)
            assertNull(vm.state.value.previewUri); assertNull(cases.profile()!!.avatarImageReference)
            assertNull(storage.loadPreview(cancelled, 100))
            main { vm.takePhoto() }; idle(vm)
            val capture = requireNotNull(vm.state.value.pendingUri); writeCapture(capture)

            main { vm = AvatarPhotoViewModel(user.id, storage, UpdateAvatarPhotoUseCase(cases, storage), handle) }
            idle(vm)
            assertNull(vm.state.value.launchUri)
            main { vm.cameraResult(true) }; idle(vm)
            assertEquals(capture, vm.state.value.previewUri)
            assertNull(cases.profile()!!.avatarImageReference)
            main { vm = AvatarPhotoViewModel(user.id, storage, UpdateAvatarPhotoUseCase(cases, storage), handle) }
            idle(vm); assertEquals(capture, vm.state.value.previewUri)
            main { vm.save() }; idle(vm)
            assertTrue(vm.state.value.saved)
            saved = requireNotNull(cases.profile()!!.avatarImageReference)
            assertNotNull(storage.loadFromReference(requireNotNull(saved), 640))
            assertNull(storage.loadPreview(capture, 640))
            cases.signOut(); cases.signIn("admin.demo", "LouDemo2026!")
            assertNull(cases.profile()!!.avatarImageReference)
            cases.signIn("cliente.demo", "LouDemo2026!")
            assertEquals(saved, LocalUseCases(RoomLocalRepository(db)).profile()!!.avatarImageReference)
        } finally { saved?.let { storage.delete(it) }; db.close() }
    }

    @Test fun accountChangeDuringFileSaveRollsBackNewFileAndDoesNotChangeEitherProfile() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(context, LouArayaDatabase::class.java).addCallback(DemoSeedCallback()).build()
        val real = LocalProductImageStorage(context, "avatar-images", "avatar-captures", 640)
        var created: String? = null
        val uri = real.createCameraCaptureUri(); writeCapture(uri)
        try {
            val cases = LocalUseCases(RoomLocalRepository(db)); cases.initialize()
            val user = cases.signIn("cliente.demo", "LouDemo2026!")
            val switching = object : ProductImageStorage by real {
                override suspend fun saveFromSource(sourceUri: String): String {
                    val reference = real.saveFromSource(sourceUri); created = reference
                    cases.signIn("admin.demo", "LouDemo2026!")
                    return reference
                }
            }
            assertTrue(runCatching { UpdateAvatarPhotoUseCase(cases, switching)(user.id, uri) }.isFailure)
            assertFalse(File(context.filesDir, requireNotNull(created)).exists())
            assertNull(cases.profile()!!.avatarImageReference)
            cases.signIn(user.alias, "LouDemo2026!")
            assertNull(cases.profile()!!.avatarImageReference)
        } finally { real.deleteCameraCapture(uri); created?.let { real.delete(it) }; db.close() }
    }
}
