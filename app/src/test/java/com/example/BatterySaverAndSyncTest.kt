package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.preferences.UserPreferencesRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class BatterySaverAndSyncTest {

    private lateinit var context: Context
    private lateinit var preferencesRepository: UserPreferencesRepository

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        preferencesRepository = UserPreferencesRepository(context)
        try {
            val config = androidx.work.Configuration.Builder()
                .setMinimumLoggingLevel(android.util.Log.DEBUG)
                .build()
            androidx.work.WorkManager.initialize(context, config)
        } catch (_: Exception) {}
    }

    @Test
    fun `test battery saver preferences toggling and dim percentage clamping`() = runBlocking {
        // Initial defaults
        val initialEco = preferencesRepository.batterySaverEnabled.first()
        val initialDim = preferencesRepository.batterySaverDimPercent.first()

        assertEquals(0, initialDim)
        assertFalse(initialEco)

        // Toggle battery saver
        preferencesRepository.setBatterySaverEnabled(true)
        assertTrue(preferencesRepository.batterySaverEnabled.first())

        // Set dim percentage
        preferencesRepository.setBatterySaverDimPercent(35)
        assertEquals(35, preferencesRepository.batterySaverDimPercent.first())

        // Dim percentage should clamp to 60 max
        preferencesRepository.setBatterySaverDimPercent(90)
        assertEquals(60, preferencesRepository.batterySaverDimPercent.first())
    }
}
