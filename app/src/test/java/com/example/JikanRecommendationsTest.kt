package com.example

import com.example.data.network.model.JikanEntryDto
import com.example.data.network.model.JikanImageFormatDto
import com.example.data.network.model.JikanImagesDto
import com.example.data.network.model.JikanRecommendationDto
import com.example.data.network.model.JikanRecommendationsResponse
import com.example.data.network.model.MalRecommendationItem
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Unit tests for Jikan (MyAnimeList) API DTO models & recommendations mapping.
 */
class JikanRecommendationsTest {

    @Test
    fun `test Jikan search and recommendations mapping`() {
        val images = JikanImagesDto(
            jpg = JikanImageFormatDto(
                largeImageUrl = "https://cdn.myanimelist.net/images/manga/3/222222l.jpg",
                imageUrl = "https://cdn.myanimelist.net/images/manga/3/222222.jpg"
            )
        )
        assertEquals("https://cdn.myanimelist.net/images/manga/3/222222l.jpg", images.resolveUrl())

        val entry = JikanEntryDto(
            malId = 121496,
            url = "https://myanimelist.net/manga/121496/Solo_Leveling",
            images = images,
            title = "Solo Leveling"
        )
        val rec = JikanRecommendationDto(entry = entry, votes = 85)
        val recResponse = JikanRecommendationsResponse(data = listOf(rec))

        val domainRec = MalRecommendationItem(
            malId = entry.malId,
            title = entry.title ?: "",
            imageUrl = entry.images?.resolveUrl() ?: "",
            votes = rec.votes ?: 0
        )
        assertEquals(121496, domainRec.malId)
        assertEquals("Solo Leveling", domainRec.title)
        assertEquals(85, domainRec.votes)
        assertEquals("https://cdn.myanimelist.net/images/manga/3/222222l.jpg", domainRec.imageUrl)
        assertEquals(1, recResponse.data?.size)
    }
}
