package cl.duoc.louaraya.data.weather

import cl.duoc.louaraya.domain.model.FairEvent
import cl.duoc.louaraya.domain.model.FairForecast
import cl.duoc.louaraya.domain.model.FairForecastResult.Available
import cl.duoc.louaraya.domain.model.FairForecastResult.Period
import cl.duoc.louaraya.domain.model.FairForecastResult.Reason
import cl.duoc.louaraya.domain.model.FairForecastResult.Unavailable
import kotlinx.coroutines.test.runTest
import okhttp3.Interceptor
import okhttp3.MediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody
import org.junit.Assert.assertEquals
import org.junit.Test
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.io.IOException
import java.time.Instant

class OpenMeteoForecastSourceTest {
    private val event = FairEvent(2, "Showroom Lou Araya", "2026-11-21", "Barrio Italia", -33.447, -70.625)

    // It is already November 19 in UTC, but still November 18 in Santiago.
    private val now = Instant.parse("2026-11-19T00:15:00Z").toEpochMilli()

    @Test
    fun requestsTheExactFairDateAndCoordinatesAndReadsTheMatchingDay() = runTest {
        val forecast = source { request ->
            val url = request.url()
            assertEquals("/v1/forecast", url.encodedPath())
            assertEquals("-33.447", url.queryParameter("latitude"))
            assertEquals("-70.625", url.queryParameter("longitude"))
            assertEquals(event.date, url.queryParameter("start_date"))
            assertEquals(event.date, url.queryParameter("end_date"))
            assertEquals("America/Santiago", url.queryParameter("timezone"))
            assertEquals("celsius", url.queryParameter("temperature_unit"))
            assertEquals(
                setOf("temperature_2m_min", "temperature_2m_max", "precipitation_probability_max"),
                url.queryParameter("daily")?.split(',')?.toSet()
            )
            json(
                request,
                """{"daily":{"time":["2026-11-20","2026-11-21"],"temperature_2m_min":[9.0,12.8],"temperature_2m_max":[19.0,22.0],"precipitation_probability_max":[11,31]}}"""
            )
        }.load(event)

        assertEquals(Available(FairForecast(12.8, 22.0, 31), event.date, Period.FAIR_DATE), forecast)
    }

    @Test
    fun usesTodaysLocalForecastForPastUnknownAndOutOfWindowFairs() = runTest {
        val today = "2026-11-18"
        val repository = source { request ->
            assertEquals(today, request.url().queryParameter("start_date"))
            assertEquals(today, request.url().queryParameter("end_date"))
            val body = """{"daily":{"time":["$today"],"temperature_2m_min":[9],"temperature_2m_max":[19],"precipitation_probability_max":[11]}}"""
            json(request, body)
        }

        val fallback = Available(FairForecast(9.0, 19.0, 11), today, Period.VENUE_TODAY)
        for (date in listOf("2026-11-17", "", "2026-11-31", "2026-12-04")) {
            assertEquals(fallback, repository.load(event.copy(date = date)))
        }
        assertEquals(fallback.copy(period = Period.FAIR_DATE), repository.load(event.copy(date = today)))
    }

    @Test
    fun skipsNetworkWithoutValidCoordinatesAndKeepsTheLastSupportedFairDay() = runTest {
        val invalid = source { error("Invalid coordinates must not be sent") }
        assertEquals(Unavailable(Reason.INVALID_LOCATION), invalid.load(event.copy(latitude = Double.NaN)))

        val finalDay = "2026-12-03"
        val repository = source { request ->
            assertEquals(finalDay, request.url().queryParameter("start_date"))
            json(request, "{}")
        }
        assertEquals(Unavailable(Reason.NO_DATA), repository.load(event.copy(date = finalDay)))
    }

    @Test
    fun doesNotShowMismatchedMissingOrOutOfRangeWeatherValues() = runTest {
        val noRain = """{"daily":{"time":["${event.date}"],"temperature_2m_min":[15],"temperature_2m_max":[20],"precipitation_probability_max":[null]}}"""
        val impossibleRain = noRain.replace("[null]", "[101]")
        val missingDay = noRain.replace(event.date, "2026-11-22")
        for (body in listOf(noRain, impossibleRain, missingDay, "null")) {
            assertEquals(Unavailable(Reason.NO_DATA), source { json(it, body) }.load(event))
        }
    }

    @Test
    fun reportsHttpNetworkAndParsingFailuresAsRetryable() = runTest {
        assertEquals(Unavailable(Reason.SERVICE_ERROR), source { json(it, "{}", code = 503) }.load(event))
        assertEquals(Unavailable(Reason.SERVICE_ERROR), source { throw IOException("Offline") }.load(event))
        assertEquals(Unavailable(Reason.SERVICE_ERROR), source { json(it, """{"daily":false}""") }.load(event))
        assertEquals(Unavailable(Reason.SERVICE_ERROR), source { throw IOException("Offline") }.load(event.copy(date = "")))
    }

    private fun source(responseFor: (Request) -> Response): OpenMeteoForecastSource {
        val http = OkHttpClient.Builder()
            .addInterceptor(Interceptor { chain -> responseFor(chain.request()) })
            .build()
        val api = Retrofit.Builder()
            .baseUrl("https://api.open-meteo.com/")
            .client(http)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(OpenMeteoApi::class.java)
        return OpenMeteoForecastSource(api) { now }
    }

    private fun json(request: Request, body: String, code: Int = 200): Response = Response.Builder()
        .request(request)
        .protocol(Protocol.HTTP_1_1)
        .code(code)
        .message(if (code == 200) "OK" else "Unavailable")
        .body(ResponseBody.create(MediaType.parse("application/json"), body))
        .build()
}
