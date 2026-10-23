package com.example.data.sync

import com.example.data.network.AniListApiService
import com.example.data.network.RetrofitClient
import com.example.data.network.model.AniListViewer
import com.example.data.network.model.GraphQLRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap

/**
 * Manages OAuth/Personal Token authentication and reading progress synchronization
 * with the AniList GraphQL API (https://graphql.anilist.co).
 */
class AniListSyncManager(
    private val apiService: AniListApiService = RetrofitClient.aniListApiService
) {

    private val titleToMediaIdCache = ConcurrentHashMap<String, Int>()

    /**
     * Validates the given Bearer token and returns the authenticated user profile.
     */
    suspend fun validateToken(token: String): Result<AniListViewer> = withContext(Dispatchers.IO) {
        val cleanToken = token.trim().removePrefix("Bearer ").trim()
        if (cleanToken.isBlank()) {
            return@withContext Result.failure(IllegalArgumentException("Token não pode ser vazio."))
        }

        val query = """
            query {
              Viewer {
                id
                name
                avatar {
                  large
                  medium
                }
              }
            }
        """.trimIndent()

        try {
            val response = apiService.getViewer(
                authHeader = "Bearer $cleanToken",
                request = GraphQLRequest(query = query)
            )
            val viewer = response.data?.Viewer
            if (viewer != null) {
                Result.success(viewer)
            } else {
                Result.failure(Exception("Usuário não encontrado ou token inválido."))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Finds the AniList Media ID for the specified manga title.
     */
    suspend fun findMediaId(title: String): Int? = withContext(Dispatchers.IO) {
        val cleanTitle = cleanMangaTitle(title)
        titleToMediaIdCache[cleanTitle]?.let { return@withContext it }

        val query = """
            query (${'$'}search: String) {
              Media (search: ${'$'}search, type: MANGA) {
                id
                title {
                  romaji
                  english
                }
                chapters
                status
              }
            }
        """.trimIndent()

        try {
            val response = apiService.searchMedia(
                request = GraphQLRequest(
                    query = query,
                    variables = mapOf("search" to cleanTitle)
                )
            )
            val mediaId = response.data?.Media?.id
            if (mediaId != null) {
                titleToMediaIdCache[cleanTitle] = mediaId
            }
            mediaId
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Synchronizes reading progress for the specified manga to AniList.
     */
    suspend fun syncProgress(token: String, title: String, chapterNumber: Float): Result<Int> = withContext(Dispatchers.IO) {
        val cleanToken = token.trim().removePrefix("Bearer ").trim()
        if (cleanToken.isBlank()) {
            return@withContext Result.failure(IllegalArgumentException("Token AniList não configurado."))
        }

        val mediaId = findMediaId(title)
            ?: return@withContext Result.failure(Exception("Mangá não localizado no catálogo AniList."))

        val targetProgress = chapterNumber.toInt().coerceAtLeast(1)

        val mutation = """
            mutation (${'$'}mediaId: Int, ${'$'}progress: Int, ${'$'}status: MediaListStatus) {
              SaveMediaListEntry (mediaId: ${'$'}mediaId, progress: ${'$'}progress, status: ${'$'}status) {
                id
                mediaId
                progress
                status
              }
            }
        """.trimIndent()

        try {
            val response = apiService.saveMediaListEntry(
                authHeader = "Bearer $cleanToken",
                request = GraphQLRequest(
                    query = mutation,
                    variables = mapOf(
                        "mediaId" to mediaId,
                        "progress" to targetProgress,
                        "status" to "CURRENT"
                    )
                )
            )
            val savedProgress = response.data?.SaveMediaListEntry?.progress ?: targetProgress
            Result.success(savedProgress)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun cleanMangaTitle(rawTitle: String): String {
        return rawTitle
            .replace(Regex("""(?i)\(pt-br\)|\[pt-br\]|\(color\)|\[color\]|manhwa|webtoon"""), "")
            .trim()
    }
}
