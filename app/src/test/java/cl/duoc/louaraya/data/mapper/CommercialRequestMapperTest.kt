package cl.duoc.louaraya.data.mapper

import cl.duoc.louaraya.domain.model.CampaignCategory
import cl.duoc.louaraya.domain.model.RequestIntention
import cl.duoc.louaraya.domain.model.RequestStatus
import cl.duoc.louaraya.domain.usecase.TestData
import org.junit.Assert.*
import org.junit.Test

class CommercialRequestMapperTest {
    @Test
    fun roundTripPreservesAllStatesIntentionsAndCategories() {
        for (status in RequestStatus.entries) {
            for (intention in RequestIntention.entries) {
                for (category in CampaignCategory.entries) {
                    val request = TestData.request1.copy(status = status, intention = intention,
                        campaignCategory = category, comment = "Ambientación ficticia")
                    val entity = request.toEntity()
                    assertEquals(status.name, entity.status)
                    assertEquals(intention.name, entity.intention)
                    assertEquals(category.name, entity.campaignCategory)
                    assertEquals(request, entity.toDomain())
                }
            }
        }
    }

    @Test
    fun nullableFieldsSurviveRoundTrip() {
        assertEquals(TestData.request1, TestData.request1.toEntity().toDomain())
    }

    @Test(expected = IllegalArgumentException::class)
    fun invalidStoredStatusIsNotSilentlyChanged() {
        TestData.request1.toEntity().copy(status = "UNKNOWN").toDomain()
    }
}
