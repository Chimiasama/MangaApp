package com.example

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.view.View
import android.view.WindowManager
import androidx.activity.OnBackPressedDispatcher
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.test.espresso.Espresso
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.ui.screens.BrowseScreen
import com.example.ui.viewmodel.BrowseUiState
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith

/** Real Android IME checks, in addition to the deterministic focus/back tests. */
@RunWith(AndroidJUnit4::class)
class BrowseKeyboardTest {
    @get:Rule val compose = createComposeRule()
    private lateinit var view: View
    private lateinit var back: OnBackPressedDispatcher
    private var cleared = 0
    private var previousHardwareImeSetting = "0"

    private fun shell(command: String): String {
        val descriptor = InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand(command)
        return android.os.ParcelFileDescriptor.AutoCloseInputStream(descriptor).bufferedReader().use { it.readText().trim() }
    }
    @Before fun allowSoftwareKeyboardOnEmulator() {
        previousHardwareImeSetting = shell("settings get secure show_ime_with_hard_keyboard")
        shell("settings put secure show_ime_with_hard_keyboard 1")
    }
    @After fun restoreKeyboardSetting() {
        if (previousHardwareImeSetting == "null") shell("settings delete secure show_ime_with_hard_keyboard")
        else shell("settings put secure show_ime_with_hard_keyboard $previousHardwareImeSetting")
    }
    private fun Context.activity(): Activity = when (this) {
        is Activity -> this
        is ContextWrapper -> baseContext.activity()
        else -> error("No host Activity")
    }
    private fun showSearch() {
        compose.setContent {
            val currentView = LocalView.current
            val dispatcher = LocalOnBackPressedDispatcherOwner.current!!.onBackPressedDispatcher
            SideEffect {
                view = currentView
                back = dispatcher
                view.context.activity().window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
            }
            MaterialTheme {
                BrowseScreen(state = BrowseUiState(searchQuery = "dragon ball", selectedGenre = "Shounen"),
                    onClearFilters = { cleared++ }, onMangaClick = {}, onFavoriteToggle = {}, favoriteUrls = emptySet())
            }
        }
        compose.onNodeWithTag("search_input").performClick()
        awaitIme(true)
    }
    private fun awaitIme(visible: Boolean) {
        compose.waitUntil(10_000) {
            var matches = false
            InstrumentationRegistry.getInstrumentation().runOnMainSync {
                matches = ViewCompat.getRootWindowInsets(view)?.isVisible(WindowInsetsCompat.Type.ime()) == visible
            }
            matches
        }
        compose.waitForIdle()
    }
    private fun assertSearchPreserved() {
        compose.onNodeWithTag("search_input").assertTextContains("dragon ball")
        compose.onNodeWithText("Shounen").assertExists()
        compose.runOnIdle { assertEquals(0, cleared) }
    }
    @Test fun systemBackHidesRealKeyboardWithoutClearingSearch() {
        showSearch()
        val editingHeight = compose.onNodeWithTag("browse_screen").fetchSemanticsNode().boundsInRoot.height
        Espresso.pressBack()
        awaitIme(false)
        assertSearchPreserved()
        val readingHeight = compose.onNodeWithTag("browse_screen").fetchSemanticsNode().boundsInRoot.height
        assertTrue("Closing the IME must restore result space", readingHeight > editingHeight)
    }
    @Test fun appDeliveredBackAndExplicitHideBothDismissRealKeyboard() {
        showSearch()
        // Reproduce an OEM delivering Back to the app while its IME is still visible.
        compose.runOnIdle { back.onBackPressed() }
        awaitIme(false)
        compose.onNodeWithTag("search_input").assertIsNotFocused()
        assertSearchPreserved()
        compose.onNodeWithTag("search_input").performClick()
        awaitIme(true)
        compose.onNodeWithTag("hide_search_keyboard").performClick()
        awaitIme(false)
        compose.onNodeWithTag("search_input").assertIsNotFocused()
        assertSearchPreserved()
    }
}
