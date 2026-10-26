package com.example

import android.content.Context
import android.graphics.BitmapFactory
import androidx.test.core.app.ApplicationProvider
import com.example.source.*
import kotlinx.coroutines.runBlocking
import okhttp3.Request
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Explicit network check: never included in the regular offline test suite. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class LiveWebSourceCheck {
    @Test fun mangapillSearchDetailsChaptersAndFirstImage() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val json = context.assets.open("source-presets/mangapill.json").bufferedReader().use { it.readText() }
        val source = DynamicJsoupSource(SourceWorkspace(context).parseRules(json).single())
        val results = source.searchManga("dragon ball")
        val book = results.first { it.title.equals("Dragon Ball", true) }
        val detail = source.getMangaDetails(book.url)
        assertEquals("Dragon Ball", detail.title)
        val chapters = source.getChapters(book.url)
        assertTrue(chapters.isNotEmpty())
        val chapter = chapters.maxBy { it.chapterNumber }
        val pages = source.getPages(chapter.chapterUrl)
        assertTrue(pages.isNotEmpty())
        SourceHttp.client.newCall(Request.Builder().url(pages.first()).build()).execute().use { response ->
            assertTrue("First page HTTP ${response.code}", response.isSuccessful)
            val bytes = response.body!!.bytes()
            val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
            assertTrue("First image must decode", options.outWidth > 0 && options.outHeight > 0)
        }
        println("LIVE WEB MangaPill: results=${results.size}; title=${detail.title}; chapters=${chapters.size}; selected=${chapter.title}; pages=${pages.size}; first image decoded")
    }
}
