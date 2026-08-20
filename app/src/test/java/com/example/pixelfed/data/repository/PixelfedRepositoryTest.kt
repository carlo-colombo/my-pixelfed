package com.example.pixelfed.data.repository

import com.example.pixelfed.data.api.StatusItem
import com.example.pixelfed.data.api.TagItem
import org.junit.Assert.assertEquals
import org.junit.Test

class PixelfedRepositoryTest {

    @Test
    fun testExtractTopTagsFromStatuses_rankingAndLimit() {
        val statuses = listOf(
            StatusItem(tags = listOf(TagItem("photography"), TagItem("nature"), TagItem("sunset"))),
            StatusItem(tags = listOf(TagItem("photography"), TagItem("nature"))),
            StatusItem(tags = listOf(TagItem("photography"), TagItem("travel"))),
            StatusItem(content = "Enjoying #sunset and #nature with #travel")
        )

        val topTags = PixelfedRepository.extractTopTagsFromStatuses(statuses, topCount = 20)

        // Counts:
        // photography: 3
        // nature: 3
        // travel: 2
        // sunset: 2
        assertEquals(4, topTags.size)
        // photography and nature tied for top (3 each), ordered alphabetically
        assertEquals("nature", topTags[0])
        assertEquals("photography", topTags[1])
        // sunset and travel tied for 3rd (2 each), ordered alphabetically
        assertEquals("sunset", topTags[2])
        assertEquals("travel", topTags[3])
    }

    @Test
    fun testExtractTopTagsFromStatuses_limitsTo20() {
        val statuses = (1..30).map { i ->
            StatusItem(tags = listOf(TagItem("tag$i")))
        }

        val topTags = PixelfedRepository.extractTopTagsFromStatuses(statuses, topCount = 20)

        assertEquals(20, topTags.size)
    }

    @Test
    fun testExtractTopTagsFromStatuses_handlesEmptyAndDuplicates() {
        val statuses = listOf(
            StatusItem(tags = listOf(TagItem("  #nature  "), TagItem("NATURE"), TagItem(""))),
            StatusItem(content = "#nature #NATURE #nature")
        )

        val topTags = PixelfedRepository.extractTopTagsFromStatuses(statuses, topCount = 20)

        assertEquals(1, topTags.size)
        assertEquals("nature", topTags[0])
    }

    @Test
    fun testParseTokenResponseBody_handlesValidAndInvalidJson() {
        // Valid token response
        val validTokenJson = """{"access_token":"token_12345","token_type":"Bearer","scope":"read write"}"""
        val token = PixelfedRepository.parseTokenResponseBody(validTokenJson)
        assertEquals("token_12345", token)

        // Missing access_token
        val missingTokenJson = """{"error":"invalid_grant"}"""
        val nullToken = PixelfedRepository.parseTokenResponseBody(missingTokenJson)
        assertEquals(null, nullToken)
    }

    @Test
    fun testParseRegistrationResponseBody_handlesValidAndInvalidJson() {
        // Valid JSON with client_id and client_secret
        val validJson = """{"id":123,"client_id":"id_abc","client_secret":"sec_123"}"""
        val (clientId, clientSecret) = PixelfedRepository.parseRegistrationResponseBody(validJson)
        assertEquals("id_abc", clientId)
        assertEquals("sec_123", clientSecret)

        // Numeric / boolean / primitive client_id and client_secret
        val numericJson = """{"client_id":12345,"client_secret":true}"""
        val (numId, numSecret) = PixelfedRepository.parseRegistrationResponseBody(numericJson)
        assertEquals("12345", numId)
        assertEquals("true", numSecret)

        // Missing fields
        val missingJson = """{"id":123}"""
        val (nullId, nullSecret) = PixelfedRepository.parseRegistrationResponseBody(missingJson)
        assertEquals(null, nullId)
        assertEquals(null, nullSecret)

        // Non-JSON / HTML
        val html = "<html><body>500 Error</body></html>"
        val (htmlId, htmlSecret) = PixelfedRepository.parseRegistrationResponseBody(html)
        assertEquals(null, htmlId)
        assertEquals(null, htmlSecret)
    }

    @Test
    fun testParseErrorResponseBody_handlesVariousJsonStructuresAndPrimitives() {
        // Standard error + error_description
        val json1 = """{"error":"invalid_client","error_description":"Client registration failed"}"""
        assertEquals("invalid_client: Client registration failed", PixelfedRepository.parseErrorResponseBody(json1))

        // Message field
        val json2 = """{"message":"The given data was invalid."}"""
        assertEquals("The given data was invalid.", PixelfedRepository.parseErrorResponseBody(json2))

        // Array body (no exception thrown)
        val json3 = """["An error occurred", "Details"]"""
        assertEquals("""["An error occurred", "Details"]""", PixelfedRepository.parseErrorResponseBody(json3))

        // Non-JSON string / HTML
        val html = "<html><body>500 Internal Server Error</body></html>"
        assertEquals(html, PixelfedRepository.parseErrorResponseBody(html))
    }
}
