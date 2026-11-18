package cl.duoc.louaraya.push

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CampaignPushPayloadTest {
    @Test fun acceptsOnlySinglePositiveNoticeIdentifierInDecryptedContent() {
        assertEquals(42L, campaignNoticeId("""{"noticeId":42}""".toByteArray(), true))
        assertEquals(Long.MAX_VALUE, campaignNoticeId(" { \n \"noticeId\" : ${Long.MAX_VALUE} } \t".toByteArray(), true))
        assertNull(campaignNoticeId("""{"noticeId":42}""".toByteArray(), false))
        for (invalid in listOf(
            """{"noticeId":0}""", """{"noticeId":-5}""", """{"noticeId":"42"}""",
            """{"noticeId":1.0}""", """{"noticeId":9223372036854775808}""",
            """{"noticeId":42,"title":"unexpected"}""", """{"noticeId":42,"noticeId":43}""",
            """{"noticeId":42}""" + " ".repeat(97)
        )) assertNull(invalid, campaignNoticeId(invalid.toByteArray(), true))
    }
}
