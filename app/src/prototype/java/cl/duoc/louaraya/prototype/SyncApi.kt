package cl.duoc.louaraya.prototype

import cl.duoc.louaraya.domain.model.RequestStatus
import cl.duoc.louaraya.domain.model.UserRole
import okhttp3.Credentials
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Path
import java.net.URI
import java.util.concurrent.TimeUnit

internal data class SyncProfile(val alias: String, val role: UserRole)
internal data class SyncProduct(val id: Long, val name: String, val price: Long)
internal data class SyncRequest(
    val id: Long,
    val product: String,
    val owner: String,
    val quantity: Int,
    val total: Long,
    val status: RequestStatus,
)
internal data class SyncSnapshot(
    val profile: SyncProfile,
    val products: List<SyncProduct>,
    val requests: List<SyncRequest>,
)
internal data class SyncDraft(val key: String, val productId: Long, val quantity: Int)

internal interface SyncApi {
    @GET("snapshot")
    suspend fun snapshot(@Header("Authorization") credentials: String): SyncSnapshot

    @POST("requests")
    suspend fun create(@Header("Authorization") credentials: String, @Body draft: SyncDraft): SyncRequest

    @POST("requests/{id}/confirm")
    suspend fun confirm(@Header("Authorization") credentials: String, @Path("id") id: Long): SyncRequest
}

internal class SyncSession(address: String, alias: String, password: String) {
    private val credentials = Credentials.basic(alias, password)
    private val api = Retrofit.Builder()
        .baseUrl(serverAddress(address))
        .client(client)
        .addConverterFactory(GsonConverterFactory.create())
        .build()
        .create(SyncApi::class.java)

    suspend fun snapshot() = api.snapshot(credentials)
    suspend fun create(draft: SyncDraft) = api.create(credentials, draft)
    suspend fun confirm(id: Long) = api.confirm(credentials, id)

    companion object {
        private val client = OkHttpClient.Builder()
            .callTimeout(8, TimeUnit.SECONDS)
            .followRedirects(false)
            .followSslRedirects(false)
            .build()

        private fun serverAddress(address: String): String {
            val input = address.trim()
            if (input.startsWith("https://")) {
                val url = runCatching { URI(input) }.getOrNull()
                require(url != null && !url.host.isNullOrBlank() && url.rawUserInfo == null &&
                    url.rawPath in listOf("", "/") && url.rawQuery == null && url.rawFragment == null &&
                    (url.port == -1 || url.port in 1..65535)) {
                    "Escribe la dirección HTTPS del servidor, sin ruta."
                }
                return input.removeSuffix("/") + "/api/"
            }
            val host = input.removePrefix("http://").removeSuffix("/")
            val ip = host.split(".").mapNotNull { it.toIntOrNull() }
            val localIp = ip.size == 4 && ip.all { it in 0..255 } &&
                ip.joinToString(".") == host &&
                (ip[0] == 10 || ip[0] == 192 && ip[1] == 168 || ip[0] == 172 && ip[1] in 16..31)
            require(localIp || host == "localhost" || host == "127.0.0.1") {
                "Escribe la dirección HTTPS del servidor, una IP privada o localhost por USB."
            }
            return "http://$host:8765/api/"
        }
    }
}
