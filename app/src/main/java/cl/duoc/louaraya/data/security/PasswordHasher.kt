package cl.duoc.louaraya.data.security

import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec


class PasswordHasher {
    fun salt(): String = ByteArray(16).also { SecureRandom().nextBytes(it) }.hex()
    fun hash(password: String, salt: String): String {
        val spec = PBEKeySpec(password.toCharArray(), salt.unhex(), 210_000, 256)
        return try { SecretKeyFactory.getInstance("PBKDF2WithHmacSHA1").generateSecret(spec).encoded.hex() }
        finally { spec.clearPassword() }
    }
    fun verify(password: String, salt: String, expected: String): Boolean =
        MessageDigest.isEqual(hash(password, salt).unhex(), expected.unhex())
    private fun ByteArray.hex() = joinToString("") { "%02x".format(it.toInt() and 255) }
    private fun String.unhex() = chunked(2).map { it.toInt(16).toByte() }.toByteArray()
}
