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

class MangaDexCatalogFiltersTest {
    private val requests = mutableListOf<HttpUrl>()
    private fun source(): MangaDexSource {
        val client = OkHttpClient.Builder().addInterceptor { chain ->
            requests += chain.request().url
            val json = """{"data":[{"id":"safe","attributes":{"title":{"en":"Action title"},"tags":[{"attributes":{"name":{"en":"Action"}}}],"publicationDemographic":"shounen","status":"ongoing","originalLanguage":"ja","contentRating":"safe"}}]}"""
            Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(200).message("OK")
                .body(json.toResponseBody("application/json".toMediaType())).build()
        }.build()
        val api = Retrofit.Builder().baseUrl("https://example.test/").client(client)
            .addConverterFactory(MoshiConverterFactory.create(Moshi.Builder().add(KotlinJsonAdapterFactory()).build()))
            .build().create(MangaDexCatalogFilterApi::class.java)
        return MangaDexSource(catalogApi = api)
    }
    @Test fun `blank title excludes actual ninja tag on MangaDex without excluding all martial arts`() = runBlocking {
        val filter = CatalogFilters(excluded = setOf("theme:ninja"))
        val result = source().getFilteredCatalog("", "all", "all", filter)
        val url = requests.single()
        assertNull(url.queryParameter("title"))
        assertEquals(listOf(CatalogTaxonomy.byId.getValue("theme:ninja").mangaDexTagId), url.queryParameterValues("excludedTags[]"))
        assertEquals("OR", url.queryParameter("excludedTagsMode"))
        assertEquals(listOf("safe", "suggestive"), url.queryParameterValues("contentRating[]"))
        assertTrue(filter.matches(result.single()))
        assertEquals("ja", result.single().originalLanguage)
    }
    @Test fun `OR between separate API fields is not accidentally sent as an AND restriction`() = runBlocking {
        source().getFilteredCatalog("", "all", "all", CatalogFilters(
            included = setOf("genre:action", "status:completed"), includeMode = IncludeMode.ANY))
        assertTrue(requests.single().queryParameterValues("includedTags[]").isEmpty())
        assertTrue(requests.single().queryParameterValues("status[]").isEmpty())
    }
    @Test fun `tag any uses OR while exclusions always use OR`() = runBlocking {
        source().getFilteredCatalog("dragon", "pt-br", "all", CatalogFilters(
            included = setOf("genre:action", "theme:magic"), excluded = setOf("theme:ninja", "theme:samurai"), includeMode = IncludeMode.ANY))
        val url = requests.single()
        assertEquals("OR", url.queryParameter("includedTagsMode"))
        assertEquals("OR", url.queryParameter("excludedTagsMode"))
        assertEquals(2, url.queryParameterValues("includedTags[]").size)
        assertEquals(2, url.queryParameterValues("excludedTags[]").size)
        assertEquals("dragon", url.queryParameter("title"))
    }
    @Test fun `all mode combines publication demographic and original language without using chapter language as origin`() = runBlocking {
        source().getFilteredCatalog("", "en", "all", CatalogFilters(
            included = setOf("genre:action", "public:shounen", "status:ongoing", "origin:zh")))
        val url = requests.single()
        assertEquals("AND", url.queryParameter("includedTagsMode"))
        assertEquals(listOf("shounen"), url.queryParameterValues("publicationDemographic[]"))
        assertEquals(listOf("ongoing"), url.queryParameterValues("status[]"))
        assertEquals(listOf("zh", "zh-hk"), url.queryParameterValues("originalLanguage[]"))
        assertEquals(listOf("en"), url.queryParameterValues("availableTranslatedLanguage[]"))
    }
}
