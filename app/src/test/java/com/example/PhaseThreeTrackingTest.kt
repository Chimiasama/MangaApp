package com.example

import com.example.data.network.model.AniListAvatar
import com.example.data.network.model.AniListMedia
import com.example.data.network.model.AniListMediaData
import com.example.data.network.model.AniListMediaListEntry
import com.example.data.network.model.AniListMediaResponse
import com.example.data.network.model.AniListSaveEntryData
import com.example.data.network.model.AniListSaveEntryResponse
import com.example.data.network.model.AniListTitle
import com.example.data.network.model.AniListUserData
import com.example.data.network.model.AniListUserResponse
import com.example.data.network.model.AniListViewer
import com.example.data.network.model.GraphQLRequest
import com.example.data.network.model.JikanEntryDto
import com.example.data.network.model.JikanImageFormatDto
import com.example.data.network.model.JikanImagesDto
import com.example.data.network.model.JikanMangaDataDto
import com.example.data.network.model.JikanRecommendationDto
import com.example.data.network.model.JikanRecommendationsResponse
import com.example.data.network.model.JikanSearchMangaResponse
import com.example.data.network.model.MalRecommendationItem
import com.example.data.sync.AniListSyncManager
import com.example.ui.viewmodel.SettingsUiState
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for Phase 3:
 * - AniList GraphQL API DTO models & validation
 * - Jikan (MyAnimeList) API DTO models & recommendations mapping
 * - SettingsUiState AniList authentication & auto-sync parameters
 */
class PhaseThreeTrackingTest {

    @Test
    fun `test AniList GraphQL DTO structure and viewer response parsing`() {
        val viewer = AniListViewer(
            id = 12345,
            name = "Chimiasama",
            avatar = AniListAvatar(
                large = "https://s4.anilist.co/user/avatar/large/12345.png",
                medium = "https://s4.anilist.co/user/avatar/medium/12345.png"
            )
        )
        val userResponse = AniListUserResponse(data = AniListUserData(Viewer = viewer))

        assertNotNull(userResponse.data?.Viewer)
        assertEquals("Chimiasama", userResponse.data?.Viewer?.name)
        assertEquals(12345, userResponse.data?.Viewer?.id)
        assertEquals("https://s4.anilist.co/user/avatar/large/12345.png", userResponse.data?.Viewer?.avatar?.large)
    }

    @Test
    fun `test AniList media search and progress mutation DTOs`() {
        val media = AniListMedia(
            id = 105398,
            title = AniListTitle(romaji = "Solo Leveling", english = "Only I Level Up"),
            chapters = 200,
            status = "FINISHED"
        )
        val mediaResponse = AniListMediaResponse(data = AniListMediaData(Media = media))
        assertEquals(105398, mediaResponse.data?.Media?.id)
        assertEquals("Solo Leveling", mediaResponse.data?.Media?.title?.romaji)

        val saveEntry = AniListMediaListEntry(
            id = 998877,
            mediaId = 105398,
            status = "CURRENT",
            progress = 42
        )
        val saveResponse = AniListSaveEntryResponse(data = AniListSaveEntryData(SaveMediaListEntry = saveEntry))
        assertEquals(42, saveResponse.data?.SaveMediaListEntry?.progress)
        assertEquals("CURRENT", saveResponse.data?.SaveMediaListEntry?.status)
    }

    @Test
    fun `test AniListSyncManager validation rejects empty token`() = runBlocking {
        val manager = AniListSyncManager()
        val emptyResult = manager.validateToken("   ")
        assertTrue(emptyResult.isFailure)

        val progressResult = manager.syncProgress("   ", "Solo Leveling", 12f)
        assertTrue(progressResult.isFailure)
    }

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
    }

    @Test
    fun `test SettingsUiState AniList connection state defaults`() {
        val state = SettingsUiState()
        assertNull(state.aniListToken)
        assertNull(state.aniListUsername)
        assertNull(state.aniListAvatar)
        assertTrue(state.aniListAutoSync)
        assertFalse(state.isCheckingAniList)
        assertNull(state.aniListErrorMessage)

        val connectedState = state.copy(
            aniListToken = "mock_secret_token",
            aniListUsername = "OtakuUser",
            aniListAvatar = "https://anilist.co/avatar.jpg"
        )
        assertEquals("mock_secret_token", connectedState.aniListToken)
        assertEquals("OtakuUser", connectedState.aniListUsername)
        assertTrue(connectedState.aniListAutoSync)
    }
}
