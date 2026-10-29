package cl.duoc.louaraya.domain.image


fun interface QrImageDecoder {
    suspend fun decode(sourceUri: String): String
}

class QrImageReadException(message: String) : Exception(message)
