package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.util.PagePreloaderManager
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class PagePreloaderManagerTest {

    @Test
    fun `preloadPages executes without throwing exception for empty and valid page lists`() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val testDispatcher = StandardTestDispatcher(testScheduler)
        val testScope = TestScope(testDispatcher)

        val pages = listOf(
            "https://example.com/page1.jpg",
            "https://example.com/page2.jpg",
            "https://example.com/page3.jpg",
            "https://example.com/page4.jpg",
            "https://example.com/page5.jpg"
        )

        // 1. Test empty page list
        PagePreloaderManager.preloadPages(
            context = context,
            scope = testScope,
            pages = emptyList(),
            currentIndex = 0
        )
        advanceUntilIdle()

        // 2. Test valid page list sequential preload
        PagePreloaderManager.preloadPages(
            context = context,
            scope = testScope,
            pages = pages,
            currentIndex = 1,
            forwardCount = 2,
            backwardCount = 1,
            nextChapterFirstPages = listOf("https://example.com/next1.jpg")
        )
        advanceUntilIdle()

        assertNotNull(PagePreloaderManager)
    }
}
