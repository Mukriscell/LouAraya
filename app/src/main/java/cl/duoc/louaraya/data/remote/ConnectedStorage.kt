package cl.duoc.louaraya.data.remote

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.AtomicFile
import cl.duoc.louaraya.domain.model.LocalSnapshot
import com.google.gson.Gson
import java.io.File
import java.security.KeyStore
import java.security.MessageDigest
import java.util.UUID
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

internal data class ConnectedSession(val token: String, val userId: Long) {
    val authorization get() = "Bearer $token"
}

internal interface ConnectedStorage {
    fun session(): ConnectedSession?
    fun saveSession(session: ConnectedSession?)
    fun snapshot(userId: Long): LocalSnapshot?
    fun saveSnapshot(userId: Long, snapshot: LocalSnapshot)
    fun clearSnapshot(userId: Long)
    fun requestKey(fingerprint: String): String
    fun clearRequestKey(fingerprint: String)
}

/** A device-specific key protects both the token and the last server snapshot from backups or copying. */
internal class AndroidConnectedStorage(context: Context, scope: String, private val gson: Gson) : ConnectedStorage {
    private val directory = File(context.noBackupFilesDir, "control3/$scope").apply { mkdirs() }
    private val alias = "louaraya-control3-$scope"

    @Synchronized override fun session(): ConnectedSession? = read("session", ConnectedSession::class.java)
    @Synchronized override fun saveSession(session: ConnectedSession?) {
        if (session == null) file("session").delete() else write("session", session)
    }

    @Synchronized override fun snapshot(userId: Long): LocalSnapshot? = read("snapshot-$userId", LocalSnapshot::class.java)
    @Synchronized override fun saveSnapshot(userId: Long, snapshot: LocalSnapshot) = write("snapshot-$userId", snapshot)
    @Synchronized override fun clearSnapshot(userId: Long) = file("snapshot-$userId").delete()

    @Synchronized override fun requestKey(fingerprint: String): String {
        val name = "pending-$fingerprint"
        read(name, String::class.java)?.let { return it }
        return UUID.randomUUID().toString().also { write(name, it) }
    }

    @Synchronized override fun clearRequestKey(fingerprint: String) = file("pending-$fingerprint").delete()

    private fun file(name: String) = AtomicFile(File(directory, name))

    private fun <T> read(name: String, type: Class<T>): T? = runCatching {
        val bytes = file(name).readFully()
        require(bytes.size > IV_SIZE)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, bytes, 0, IV_SIZE))
        cipher.updateAAD(name.toByteArray(Charsets.UTF_8))
        val plaintext = cipher.doFinal(bytes, IV_SIZE, bytes.size - IV_SIZE)
        gson.fromJson(plaintext.toString(Charsets.UTF_8), type)
    }.getOrNull()

    private fun write(name: String, data: Any) {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, key())
        check(cipher.iv.size == IV_SIZE)
        cipher.updateAAD(name.toByteArray(Charsets.UTF_8))
        val bytes = cipher.iv + cipher.doFinal(gson.toJson(data).toByteArray(Charsets.UTF_8))
        val target = file(name)
        val stream = target.startWrite()
        try {
            stream.write(bytes)
            target.finishWrite(stream)
        } catch (error: Exception) {
            target.failWrite(stream)
            throw error
        }
    }

    private fun key(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey(alias, null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").run {
            init(KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .build())
            generateKey()
        }
    }

    private companion object {
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val IV_SIZE = 12
    }
}

internal fun connectionDigest(value: String): String = MessageDigest.getInstance("SHA-256")
    .digest(value.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
