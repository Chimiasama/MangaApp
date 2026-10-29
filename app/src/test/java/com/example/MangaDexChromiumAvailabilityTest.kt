package com.example

import com.example.data.network.MangaDexChromiumTransport
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.net.URL

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class MangaDexChromiumAvailabilityTest {
    @org.junit.Before fun olderRuntime() {
        org.robolectric.util.ReflectionHelpers.setStaticField(android.os.Build.VERSION::class.java, "SDK_INT", 28)
    }
    @org.junit.After fun restoreRuntime() {
        org.robolectric.util.ReflectionHelpers.setStaticField(android.os.Build.VERSION::class.java, "SDK_INT", 36)
    }

    @Test fun `older Android runtime guard retains the existing fallback`() {
        assertFalse(MangaDexChromiumTransport.isSupported())
        assertNull(MangaDexChromiumTransport.openConnection(URL("https://api.mangadex.org/manga")))
        assertTrue(MangaDexChromiumTransport.description().contains("indisponível"))
    }

    @Test fun `alternative transport never accepts cleartext URLs`() {
        assertThrows(IllegalArgumentException::class.java) {
            MangaDexChromiumTransport.openConnection(URL("http://api.mangadex.org/manga"))
        }
    }
}
