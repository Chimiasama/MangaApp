package com.example

import com.example.data.network.model.ComickCoverDto
import com.example.data.network.model.ComickPageImageDto
import com.example.data.network.model.ComickSearchItemDto
import com.example.data.network.model.ConsumetMangaItemDto
import com.example.source.ComickSource
import com.example.source.ConsumetMangaSource
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests validating Phase 2 source additions:
 * - ConsumetMangaSource (Aggregator)
 * - ComickSource (Webtoons & Manhuas)
 * - DTO resolution (image CDNs, slugs, cover fallbacks)
 */
class PhaseTwoSourcesTest {

    @Test
    fun `test ConsumetMangaSource metadata and fallback catalog`() = runBlocking {
        val source = ConsumetMangaSource()
        assertEquals("consumet", source.id)
        assertNotNull(source.name)
        assertFalse(source.isNsfw)

        // Verifies fallback popular mangas return valid items
        val fallbackPopular = source.getPopularManga()
        assertTrue(fallbackPopular.isNotEmpty())
        assertTrue(fallbackPopular.all { it.sourceId == "consumet" })
        assertTrue(fallbackPopular.any { it.title.contains("Solo Leveling") })
    }

    @Test
    fun `test ComickSource metadata and fallback catalog`() = runBlocking {
        val source = ComickSource()
        assertEquals("comick", source.id)
        assertNotNull(source.name)
        assertFalse(source.isNsfw)

        // Verifies fallback popular mangas return valid items
        val fallbackPopular = source.getPopularManga()
        assertTrue(fallbackPopular.isNotEmpty())
        assertTrue(fallbackPopular.all { it.sourceId == "comick" })
        assertTrue(fallbackPopular.any { it.title.contains("Solo Leveling") })
    }

    @Test
    fun `test Comick cover url resolution with b2key`() {
        val itemWithB2Key = ComickSearchItemDto(
            hid = "abc123",
            slug = "solo-leveling",
            title = "Solo Leveling",
            mdCovers = listOf(ComickCoverDto(b2key = "cover_hash_123.jpg", w = 600, h = 800))
        )
        assertEquals("https://meo.comick.pictures/cover_hash_123.jpg", itemWithB2Key.resolveCoverUrl())

        val itemWithDirectUrl = ComickSearchItemDto(
            hid = "abc123",
            slug = "solo-leveling",
            title = "Solo Leveling",
            coverUrl = "https://custom.cdn.com/cover.jpg",
            mdCovers = emptyList()
        )
        assertEquals("https://custom.cdn.com/cover.jpg", itemWithDirectUrl.resolveCoverUrl())
    }

    @Test
    fun `test Comick page image url resolution`() {
        val pageWithB2Key = ComickPageImageDto(b2key = "page_01.jpg")
        assertEquals("https://meo.comick.pictures/page_01.jpg", pageWithB2Key.resolveImageUrl())

        val pageWithUrl = ComickPageImageDto(url = "https://images.comick.pictures/page_02.png")
        assertEquals("https://images.comick.pictures/page_02.png", pageWithUrl.resolveImageUrl())
    }

    @Test
    fun `test Consumet DTO creation`() {
        val item = ConsumetMangaItemDto(
            id = "manga-123",
            title = "Tower of God",
            image = "https://example.com/tog.jpg",
            description = "A tower where wishes are granted.",
            status = "Ongoing"
        )
        assertEquals("manga-123", item.id)
        assertEquals("Tower of God", item.title)
        assertEquals("Ongoing", item.status)
    }
}
