package com.example.data.network

import com.example.data.network.model.AniListMediaResponse
import com.example.data.network.model.AniListSaveEntryResponse
import com.example.data.network.model.AniListUserResponse
import com.example.data.network.model.GraphQLRequest
import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.Headers
import retrofit2.http.POST

/**
 * Retrofit interface for the AniList GraphQL API (https://graphql.anilist.co).
 */
interface AniListApiService {

    @Headers("Content-Type: application/json", "Accept: application/json")
    @POST("/")
    suspend fun getViewer(
        @Header("Authorization") authHeader: String,
        @Body request: GraphQLRequest
    ): AniListUserResponse

    @Headers("Content-Type: application/json", "Accept: application/json")
    @POST("/")
    suspend fun searchMedia(
        @Body request: GraphQLRequest
    ): AniListMediaResponse

    @Headers("Content-Type: application/json", "Accept: application/json")
    @POST("/")
    suspend fun saveMediaListEntry(
        @Header("Authorization") authHeader: String,
        @Body request: GraphQLRequest
    ): AniListSaveEntryResponse
}
