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
}
