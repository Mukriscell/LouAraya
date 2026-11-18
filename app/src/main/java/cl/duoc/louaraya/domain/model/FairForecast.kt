package cl.duoc.louaraya.domain.model

internal data class FairForecast(
    val minimumCelsius: Double,
    val maximumCelsius: Double,
    val maximumPrecipitationProbability: Int
)

internal sealed interface FairForecastResult {
    data class Available(val forecast: FairForecast, val date: String, val period: Period) : FairForecastResult
    data class Unavailable(val reason: Reason) : FairForecastResult

    enum class Period { FAIR_DATE, VENUE_TODAY }

    enum class Reason {
        INVALID_LOCATION, NO_DATA, SERVICE_ERROR
    }
}

internal fun interface FairForecastSource {
    suspend fun load(event: FairEvent): FairForecastResult
}
