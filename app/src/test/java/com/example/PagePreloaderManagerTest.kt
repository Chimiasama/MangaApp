package com.example

import android.content.Context
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import androidx.test.core.app.ApplicationProvider
import coil.Coil
import coil.ImageLoader
import coil.decode.DataSource
import coil.intercept.Interceptor
import coil.request.SuccessResult
import com.example.util.PagePreloaderManager
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.Before
import org.junit.After
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.test.resetMain
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.Collections
import java.util.concurrent.atomic.AtomicInteger

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class PagePreloaderManagerTest {
    @Before fun mainDispatcher() { Dispatchers.setMain(Dispatchers.Unconfined) }
    @After fun restoreDispatcher() { Dispatchers.resetMain() }
    @Test fun `pages and next chapter images finish strictly in reading order`() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val events = Collections.synchronizedList(mutableListOf<String>())
        val active = AtomicInteger()
        var maximumActive = 0
        Coil.setImageLoader(ImageLoader.Builder(context).components {
            add(Interceptor { chain ->
                val url = chain.request.data.toString()
                maximumActive = maxOf(maximumActive, active.incrementAndGet())
                events += "start:$url"
                delay(5)
                events += "end:$url"
                active.decrementAndGet()
                SuccessResult(BitmapDrawable(context.resources, Bitmap.createBitmap(2, 2, Bitmap.Config.ARGB_8888)), chain.request, DataSource.MEMORY)
            })
        }.build())
        assertNull(PagePreloaderManager.preloadPages(context, this, emptyList(), 0))
        val job = PagePreloaderManager.preloadPages(context, this, listOf("0", "1", "2", "3"), 2,
            forwardCount = 2, backwardCount = 1, nextChapterFirstPages = listOf("next-a", "next-b", "next-c"))
        withTimeout(10_000) { job!!.join() }
        assertEquals(1, maximumActive)
        assertEquals(listOf("2", "3", "1", "next-a", "next-b").flatMap { listOf("start:$it", "end:$it") }, events.toList())
    }
    @Test fun `new preload cancels an obsolete sequence instead of downloading distant pages`() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val started = CompletableDeferred<Unit>()
        val finished = Collections.synchronizedList(mutableListOf<String>())
        Coil.setImageLoader(ImageLoader.Builder(context).components {
            add(Interceptor { chain ->
                val url = chain.request.data.toString()
                if (url == "old") { started.complete(Unit); awaitCancellation() }
                finished += url
                SuccessResult(BitmapDrawable(context.resources, Bitmap.createBitmap(2, 2, Bitmap.Config.ARGB_8888)), chain.request, DataSource.MEMORY)
            })
        }.build())
        val old = PagePreloaderManager.preloadPages(context, this, listOf("old", "distant"), 0)
        withTimeout(10_000) { started.await() }
        val current = PagePreloaderManager.preloadPages(context, this, listOf("current"), 0)
        withTimeout(10_000) { current!!.join(); old!!.join() }
        assertTrue(old!!.isCancelled)
        assertEquals(listOf("current"), finished.toList())
    }
}
