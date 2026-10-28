package com.example

import com.example.data.network.MangaDexCatalogFilterApi
import com.example.source.*
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.runBlocking
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import org.junit.Assert.*
import org.junit.Test
import java.io.IOException

class HistoricCatalogTest {
    private val calls = mutableListOf<HttpUrl>()
    private fun source(failSecondPage: Boolean = false, transientFirstFailure: Boolean = false): MangaDexSource {
        var attempts = 0
        val client = OkHttpClient.Builder().addInterceptor { chain ->
            val url = chain.request().url
            calls += url
            attempts++
            val offset = url.queryParameter("offset")!!.toInt()
            if (transientFirstFailure && attempts == 1) throw IOException("transient")
            val code = if (failSecondPage && offset > 0) 503 else 200
            val data = if (offset == 0) (0..99).joinToString(",") { i ->
                val year = when (i) { 0 -> 1812; 1 -> 1900; else -> 1947 }
                """{"id":"m$i","attributes":{"title":{"en":"Work $i"},"year":$year,"contentRating":"safe"}}"""
            } else """{"id":"end","attributes":{"title":{"en":"Work 1950"},"year":1950}},{"id":"outside","attributes":{"title":{"en":"Later"},"year":1951}}"""
            Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(code).message("status")
                .body("""{"data":[$data],"total":1000}""".toResponseBody("application/json".toMediaType())).build()
        }.build()
        val api = Retrofit.Builder().baseUrl("https://example.test/").client(client)
            .addConverterFactory(MoshiConverterFactory.create(Moshi.Builder().add(KotlinJsonAdapterFactory()).build()))
            .build().create(MangaDexCatalogFilterApi::class.java)
        return MangaDexSource(catalogApi = api)
    }
    @Test fun `1900 to 1950 pages by chronological year stops at 1951 and includes both boundaries`() = runBlocking {
        val result = source().getCatalogManga("", "all", "1900-1950")
        assertEquals(100, result.size)
        assertTrue(result.all { it.year?.let { year -> year in 1900..1950 } == true })
        assertTrue(result.any { it.year == 1900 })
        assertTrue(result.any { it.year == 1950 })
        assertEquals(listOf("0", "100"), calls.map { it.queryParameter("offset") })
        assertTrue(calls.all { it.queryParameter("order[year]") == "asc" && it.queryParameter("year") == null && it.queryParameter("title") == null })
        assertTrue(calls.all { it.queryParameterValues("availableTranslatedLanguage[]").isEmpty() })
        assertTrue(calls.all { it.queryParameter("order[followedCount]") == null })
    }
    @Test fun `transient catalog failure is retried without an empty result`() = runBlocking {
        val result = source(transientFirstFailure = true).getCatalogManga("", "all", "1900-1950")
        assertEquals(100, result.size)
        assertEquals(listOf("0", "0", "100"), calls.map { it.queryParameter("offset") })
    }
    @Test fun `failed later page retains historic records and signals incomplete result`() = runBlocking {
        try { source(failSecondPage = true).getCatalogManga("", "all", "1900-1950"); fail("Should report partial failure") }
        catch (partial: SourceCatalogPartialException) {
            assertEquals(99, partial.items.size)
            assertTrue(partial.items.all { it.year == 1900 || it.year == 1947 })
        }
        assertEquals(3, calls.count { it.queryParameter("offset") == "100" })
    }
    @Test fun `chronological range keeps exclusions and selected chapter language`() = runBlocking {
        source().getFilteredCatalog("", "pt-br", "1900-1950", CatalogFilters(excluded = setOf("theme:ninja")))
        val ninja = CatalogTaxonomy.byId.getValue("theme:ninja").mangaDexTagId
        assertTrue(calls.all { it.queryParameterValues("excludedTags[]") == listOf(ninja) })
        assertTrue(calls.all { it.queryParameterValues("availableTranslatedLanguage[]") == listOf("pt-br", "pt") })
    }
}
