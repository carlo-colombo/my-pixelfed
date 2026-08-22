package com.example.pixelfed.data.repository

import com.example.pixelfed.data.api.StatusItem
import com.example.pixelfed.data.api.TagItem
import org.junit.Assert.assertEquals
import org.junit.Test

class PixelfedRepositoryTest {

    @Test
    fun testExtractTopTagsFromStatuses_rankingAndLimit() {
        val statuses = listOf(
            StatusItem(content = "#photography #animalphotography #canaryislands #travelphotography Lobos"),
            StatusItem(content = "ooking chicken with #earth heat at #Lanzarote - Timanfaya park - #photography #BlueSkyArtShow #travelphotography #canaryislands"),
            StatusItem(content = "#Glass jar #BlueSkyArtShow #photography"),
            StatusItem(content = "Kotor Kitten #growing #blackandwhite #classicmono #BlueSkyArtShow #catsofpixelfed #catphotography #photography #cat"),
            StatusItem(content = "#urbangaze #wien long exposure"),
            StatusItem(content = "Shinjuku - #busy view from the top - #photography #japan #travelphotography #urbangaze #BlueSkyArtShow"),
            StatusItem(content = "Black and White of the Iseo Lake - #classicmono #photography #blackandwhite #photography-bw #italy #northernitaly")
        )

        val topTags = PixelfedRepository.extractTopTagsFromStatuses(statuses, topCount = 20)

        // photography appears in 6 statuses -> top tag
        assertEquals("photography", topTags[0])
        // blueskyartshow appears in 4 statuses -> second
        assertEquals("blueskyartshow", topTags[1])
        // travelphotography appears in 3 statuses -> third
        assertEquals("travelphotography", topTags[2])
        // Check photography-bw with hyphen is extracted
        assert(topTags.contains("photography-bw"))
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
    fun testExtractTopTagsFromStatuses_concatenatesExtractedAndStaticTags() {
        val statuses = listOf(
            // Status 1: extracted from content -> #landscape, #photography. static in status item -> #photography, #sunset
            StatusItem(
                content = "Beautiful view #landscape #photography",
                tags = listOf(TagItem("photography"), TagItem("sunset"))
            ),
            // Status 2: extracted from content -> #landscape. static passed as staticTags arg -> #travel
            StatusItem(
                content = "Mountain trip #landscape"
            )
        )

        val staticTagsParam = listOf("#travel", "landscape")

        // Status 1 concatenated tags before distinct: [landscape, photography] + [travel, landscape, photography, sunset] -> distinct: [landscape, photography, travel, sunset]
        // Status 2 concatenated tags before distinct: [landscape] + [travel, landscape] -> distinct: [landscape, travel]
        // Counts across statuses:
        // landscape: 2
        // travel: 2
        // photography: 1
        // sunset: 1

        val topTags = PixelfedRepository.extractTopTagsFromStatuses(statuses, topCount = 20, staticTags = staticTagsParam)

        assertEquals(4, topTags.size)
        assertEquals("landscape", topTags[0])
        assertEquals("travel", topTags[1])
        assertEquals("photography", topTags[2])
        assertEquals("sunset", topTags[3])
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
