package com.duckduckgo.duckchat.impl.ui.inputscreen

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.duckduckgo.browser.api.autocomplete.AutoComplete
import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.history.api.NavigationHistory
import com.duckduckgo.savedsites.api.SavedSitesRepository
import com.duckduckgo.voice.api.VoiceSearchAvailability
import kotlinx.coroutines.CoroutineScope
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.mock

@RunWith(AndroidJUnit4::class)
class InputScreenViewModelTest {

    @get:Rule
    val coroutineRule = CoroutineTestRule()

    private val autoComplete: AutoComplete = mock()
    private val history: NavigationHistory = mock()
    private val savedSitesRepository: SavedSitesRepository = mock()
    private val appCoroutineScope: CoroutineScope = mock()
    private val inputScreenDataStore: InputScreenDataStore = mock()
    private val voiceSearchAvailability: VoiceSearchAvailability = mock()

    private lateinit var viewModel: InputScreenViewModel

    @Before
    fun setup() {
        viewModel = InputScreenViewModel(
            autoComplete = autoComplete,
            dispatchers = coroutineRule.testDispatcherProvider,
            history = history,
            savedSitesRepository = savedSitesRepository,
            appCoroutineScope = appCoroutineScope,
            inputScreenDataStore = inputScreenDataStore,
            voiceSearchAvailability = voiceSearchAvailability,
        )
    }

    @Test
    fun `when onSearchSelected then set forceWebSearchButtonVisible to false`() {
        viewModel.onSearchSelected()
        assertFalse(viewModel.visibilityState.value.forceWebSearchButtonVisible)
    }

    @Test
    fun `when onChatSelected then set forceWebSearchButtonVisible to true`() {
        viewModel.onChatSelected()
        assertTrue(viewModel.visibilityState.value.forceWebSearchButtonVisible)
    }
}
