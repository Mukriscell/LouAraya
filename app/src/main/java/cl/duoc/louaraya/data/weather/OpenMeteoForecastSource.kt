package cl.duoc.louaraya.data.weather

import cl.duoc.louaraya.domain.model.FairEvent
import cl.duoc.louaraya.domain.model.FairForecast
import cl.duoc.louaraya.domain.model.FairForecastResult
import cl.duoc.louaraya.domain.model.FairForecastResult.Period
import cl.duoc.louaraya.domain.model.FairForecastResult.Reason
import cl.duoc.louaraya.domain.model.FairForecastSource
import com.google.gson.JsonParseException
import com.google.gson.annotations.SerializedName
import okhttp3.OkHttpClient
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import retrofit2.http.Query
import java.io.IOException
import java.text.ParsePosition
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.TimeUnit

internal interface OpenMeteoApi {
    @GET("v1/forecast")
    suspend fun dailyForecast(
        @Query("latitude") latitude: Double,
        @Query("longitude") longitude: Double,
        @Query("start_date") startDate: String,
        @Query("end_date") endDate: String,
        @Query("daily") daily: String = "temperature_2m_min,temperature_2m_max,precipitation_probability_max",
        @Query("timezone") timeZone: String = FAIR_TIME_ZONE,
        @Query("temperature_unit") temperatureUnit: String = "celsius"
    ): Response<OpenMeteoResponse>
}

internal data class OpenMeteoResponse(val daily: OpenMeteoDaily?)

internal data class OpenMeteoDaily(
    val time: List<String>?,
    @SerializedName("temperature_2m_min") val minimums: List<Double?>?,
    @SerializedName("temperature_2m_max") val maximums: List<Double?>?,
    @SerializedName("precipitation_probability_max") val probabilities: List<Int?>?
) {
    fun forecastOn(date: String): FairForecast? {
        val index = time?.indexOf(date) ?: return null
        if (index < 0) return null
        val minimum = minimums?.getOrNull(index) ?: return null
        val maximum = maximums?.getOrNull(index) ?: return null
        val probability = probabilities?.getOrNull(index) ?: return null
        if (!minimum.isFinite() || !maximum.isFinite() || minimum > maximum || probability !in 0..100) {
            return null
        }
        return FairForecast(minimum, maximum, probability)
    }
}

internal class OpenMeteoForecastSource(
    private val api: OpenMeteoApi,
    private val now: () -> Long = System::currentTimeMillis
) : FairForecastSource {
    override suspend fun load(event: FairEvent): FairForecastResult {
        if (event.latitude !in -90.0..90.0 || event.longitude !in -180.0..180.0) {
            return unavailable(Reason.INVALID_LOCATION)
        }

        val today = SimpleDateFormat("yyyy-MM-dd", Locale.ROOT).apply {
            timeZone = TimeZone.getTimeZone(FAIR_TIME_ZONE)
        }.format(Date(now()))
        val daysAway = daysUntilFair(event.date, today)
        val useFairDate = daysAway != null && daysAway in 0L until FORECAST_DAYS
        val date = if (useFairDate) event.date else today
        val period = if (useFairDate) Period.FAIR_DATE else Period.VENUE_TODAY

        return try {
            val response = api.dailyForecast(event.latitude, event.longitude, date, date)
            if (!response.isSuccessful) {
                response.errorBody()?.close()
                return unavailable(Reason.SERVICE_ERROR)
            }
            val forecast = response.body()?.daily?.forecastOn(date)
            if (forecast == null) unavailable(Reason.NO_DATA) else FairForecastResult.Available(forecast, date, period)
        } catch (_: IOException) {
            unavailable(Reason.SERVICE_ERROR)
        } catch (_: JsonParseException) {
            unavailable(Reason.SERVICE_ERROR)
        }
    }

    private fun unavailable(reason: Reason) = FairForecastResult.Unavailable(reason)

    companion object {
        val publicApi: FairForecastSource by lazy {
            val http = OkHttpClient.Builder()
                .callTimeout(8, TimeUnit.SECONDS)
                .build()
            val api = Retrofit.Builder()
                .baseUrl("https://api.open-meteo.com/")
                .client(http)
                .addConverterFactory(GsonConverterFactory.create())
                .build()
                .create(OpenMeteoApi::class.java)
            OpenMeteoForecastSource(api)
        }
    }
}

// The fairs are in continental Chile. Compare calendar days in that zone, even across DST.
private const val FAIR_TIME_ZONE = "America/Santiago"
private const val FORECAST_DAYS = 16L
private const val MILLIS_PER_DAY = 86_400_000L
private val isoDate = Regex("[0-9]{4}-[0-9]{2}-[0-9]{2}")

private fun daysUntilFair(date: String, today: String): Long? {
    if (!isoDate.matches(date)) return null
    // Parse both local dates at UTC midnight to avoid variable-length days in Chile.
    val formatter = SimpleDateFormat("yyyy-MM-dd", Locale.ROOT).apply {
        isLenient = false
        timeZone = TimeZone.getTimeZone("UTC")
    }
    val position = ParsePosition(0)
    val target = formatter.parse(date, position)?.takeIf { position.index == date.length } ?: return null
    val current = requireNotNull(formatter.parse(today))
    return (target.time - current.time) / MILLIS_PER_DAY
}
