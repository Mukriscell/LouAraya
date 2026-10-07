package cl.duoc.louaraya.domain.usecase

import cl.duoc.louaraya.domain.image.ProductImageStorage
import cl.duoc.louaraya.domain.repository.ProductRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdateProductImageUseCaseTest {
    @Test
    fun successUpdatesOnlyImageAndDeletesOldAfterCommit() = runTest {
        val repository = FakeProductRepository(listOf(TestData.product1.copy(imageReference = OLD)))
        val storage = FakeImageStorage()

        val reference = UpdateProductImageUseCase(repository, storage)(TestData.product1.id, "content://picker/new")

        assertEquals(NEW, reference)
        assertEquals(NEW, repository.getProductById(TestData.product1.id)?.imageReference)
        assertEquals(listOf(OLD), storage.deleted)
    }

    @Test
    fun databaseFailureCleansNewFileAndKeepsOldReference() = runTest {
        val base = FakeProductRepository(listOf(TestData.product1.copy(imageReference = OLD)))
        val repository = object : ProductRepository by base {
            override suspend fun updateImageReference(id: Long, imageReference: String): Boolean = false
        }
        val storage = FakeImageStorage()

        val result = runCatching {
            UpdateProductImageUseCase(repository, storage)(TestData.product1.id, "content://picker/new")
        }

        assertTrue(result.isFailure)
        assertEquals(OLD, base.getProductById(TestData.product1.id)?.imageReference)
        assertEquals(listOf(NEW), storage.deleted)
    }

    @Test
    fun cancellationAfterCopyCleansNewFile() = runTest {
        val base = FakeProductRepository(listOf(TestData.product1.copy(imageReference = OLD)))
        val repository = object : ProductRepository by base {
            override suspend fun updateImageReference(id: Long, imageReference: String): Boolean {
                throw CancellationException("test cancellation")
            }
        }
        val storage = FakeImageStorage()

        val result = runCatching {
            UpdateProductImageUseCase(repository, storage)(TestData.product1.id, "content://picker/new")
        }

        assertTrue(result.exceptionOrNull() is CancellationException)
        assertEquals(OLD, base.getProductById(TestData.product1.id)?.imageReference)
        assertEquals(listOf(NEW), storage.deleted)
    }

    @Test
    fun cancellationAfterCommittedUpdateKeepsNewFileAndDeletesOld() = runTest {
        val base = FakeProductRepository(listOf(TestData.product1.copy(imageReference = OLD)))
        val repository = object : ProductRepository by base {
            override suspend fun updateImageReference(id: Long, imageReference: String): Boolean {
                base.updateImageReference(id, imageReference)
                throw CancellationException("cancelled after commit")
            }
        }
        val storage = FakeImageStorage()

        runCatching { UpdateProductImageUseCase(repository, storage)(TestData.product1.id, "content://picker/new") }

        assertEquals(NEW, base.getProductById(TestData.product1.id)?.imageReference)
        assertEquals(listOf(OLD), storage.deleted)
    }

    @Test
    fun unknownCommitOutcomeKeepsNewFileWhenRereadFails() = runTest {
        val base = FakeProductRepository(listOf(TestData.product1.copy(imageReference = OLD)))
        var updateAttempted = false
        val repository = object : ProductRepository by base {
            override suspend fun updateImageReference(id: Long, imageReference: String): Boolean {
                base.updateImageReference(id, imageReference)
                updateAttempted = true
                throw CancellationException("cancelled after commit")
            }
            override suspend fun getProductById(id: Long) =
                if (updateAttempted) error("read unavailable") else base.getProductById(id)
        }
        val storage = FakeImageStorage()

        runCatching { UpdateProductImageUseCase(repository, storage)(TestData.product1.id, "content://picker/new") }

        assertEquals(NEW, base.getProductById(TestData.product1.id)?.imageReference)
        assertTrue(storage.deleted.isEmpty())
    }

    @Test
    fun imageCopyFailureLeavesOldReferenceAndFileUntouched() = runTest {
        val repository = FakeProductRepository(listOf(TestData.product1.copy(imageReference = OLD)))
        val storage = FakeImageStorage().apply { failCopy = true }

        val result = runCatching {
            UpdateProductImageUseCase(repository, storage)(TestData.product1.id, "content://picker/new")
        }

        assertTrue(result.isFailure)
        assertEquals(OLD, repository.getProductById(TestData.product1.id)?.imageReference)
        assertTrue(storage.deleted.isEmpty())
    }

    @Test
    fun missingProductDoesNotCopyImage() = runTest {
        val storage = FakeImageStorage()
        val result = runCatching {
            UpdateProductImageUseCase(FakeProductRepository(), storage)(77L, "content://picker/new")
        }
        assertTrue(result.isFailure)
        assertFalse(storage.saved)
    }

    private class FakeImageStorage : ProductImageStorage {
        val deleted = mutableListOf<String>()
        var saved = false
        var failCopy = false
        override suspend fun saveFromSource(sourceUri: String): String {
            saved = true
            check(!failCopy) { "copy failed" }
            return NEW
        }
        override suspend fun loadFromReference(reference: String, maxDimensionPx: Int): ByteArray? = null
        override suspend fun loadPreview(sourceUri: String, maxDimensionPx: Int): ByteArray? = null
        override suspend fun delete(reference: String) { deleted += reference }
    }

    private companion object {
        const val OLD = "product-images/old.jpg"
        const val NEW = "product-images/new.jpg"
    }
}
