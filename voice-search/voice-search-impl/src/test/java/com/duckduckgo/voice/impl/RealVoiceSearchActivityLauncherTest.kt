/*
 * Copyright (c) 2022 DuckDuckGo
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.duckduckgo.voice.impl

import android.app.Activity
import com.duckduckgo.app.statistics.pixels.Pixel
import com.duckduckgo.duckchat.api.DuckAiFeatureState
import com.duckduckgo.voice.api.VoiceSearchLauncher.Event
import com.duckduckgo.voice.api.VoiceSearchLauncher.Source.BROWSER
import com.duckduckgo.voice.api.VoiceSearchLauncher.Source.WIDGET
import com.duckduckgo.voice.api.VoiceSearchLauncher.VoiceRecognitionResult
import com.duckduckgo.voice.api.VoiceSearchLauncher.VoiceSearchMode
import com.duckduckgo.voice.impl.ActivityResultLauncherWrapper.Action
import com.duckduckgo.voice.impl.fakes.FakeActivityResultLauncherWrapper
import com.duckduckgo.voice.impl.listeningmode.VoiceSearchActivity
import com.duckduckgo.voice.impl.listeningmode.ui.VoiceSearchBackgroundBlurRenderer
import com.duckduckgo.voice.store.VoiceSearchRepository
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.mockito.Mock
import org.mockito.MockitoAnnotations
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class RealVoiceSearchActivityLauncherTest {
    @Mock
    private lateinit var blurRenderer: VoiceSearchBackgroundBlurRenderer

    @Mock
    private lateinit var pixel: Pixel

    private lateinit var activityResultLauncherWrapper: FakeActivityResultLauncherWrapper

    private lateinit var testee: RealVoiceSearchActivityLauncher

    @Mock
    private lateinit var voiceSearchRepository: VoiceSearchRepository

    @Mock
    private lateinit var dialogLauncher: VoiceSearchPermissionDialogsLauncher

    @Mock
    private lateinit var duckAiFeatureState: DuckAiFeatureState

    private val showVoiceSearchToggleFlow = MutableStateFlow(true)

    @Before
    fun setUp() {
        MockitoAnnotations.openMocks(this)
        whenever(duckAiFeatureState.showVoiceSearchToggle).thenReturn(showVoiceSearchToggleFlow)
        activityResultLauncherWrapper = FakeActivityResultLauncherWrapper()
        testee = RealVoiceSearchActivityLauncher(
            blurRenderer,
            pixel,
            activityResultLauncherWrapper,
            voiceSearchRepository,
            dialogLauncher,
            duckAiFeatureState,
        )
    }

    @Test
    fun whenResultFromVoiceSearchBrowserIsOKAndNotEmptyThenEmitVoiceRecognitionSuccess() {
        var lastKnownEvent: Event? = null
        testee.registerResultsCallback(mock(), mock(), BROWSER) {
            lastKnownEvent = it
        }

        val lastKnownRequest = activityResultLauncherWrapper.lastKnownRequest as ActivityResultLauncherWrapper.Request.ResultFromVoiceSearch
        lastKnownRequest.onResult(Activity.RESULT_OK, "Result", VoiceSearchMode.SEARCH)

        verify(pixel).fire(VoiceSearchPixelNames.VOICE_SEARCH_DONE, mapOf("source" to "browser"))
        assertEquals(Event.VoiceRecognitionSuccess(VoiceRecognitionResult.SearchResult("Result")), lastKnownEvent)
    }

    @Test
    fun whenResultFromVoiceSearchBrowserIsErrorThenEmitVoiceRecognitionError() {
        var lastKnownEvent: Event? = null
        testee.registerResultsCallback(mock(), mock(), BROWSER) {
            lastKnownEvent = it
        }

        val lastKnownRequest = activityResultLauncherWrapper.lastKnownRequest as ActivityResultLauncherWrapper.Request.ResultFromVoiceSearch
        lastKnownRequest.onResult(VoiceSearchActivity.VOICE_SEARCH_ERROR, "1", VoiceSearchMode.SEARCH)
        verify(pixel).fire(VoiceSearchPixelNames.VOICE_SEARCH_ERROR, mapOf("error" to "1"))

        assertNull(lastKnownEvent)
    }

    @Test
    fun whenResultFromVoiceSearchWidgetIsOKAndNotEmptyThenEmitVoiceRecognitionSuccess() {
        var lastKnownEvent: Event? = null
        testee.registerResultsCallback(mock(), mock(), WIDGET) {
            lastKnownEvent = it
        }

        val lastKnownRequest = activityResultLauncherWrapper.lastKnownRequest as ActivityResultLauncherWrapper.Request.ResultFromVoiceSearch
        lastKnownRequest.onResult(Activity.RESULT_OK, "Result", VoiceSearchMode.SEARCH)

        verify(pixel).fire(VoiceSearchPixelNames.VOICE_SEARCH_DONE, mapOf("source" to "widget"))
        assertEquals(Event.VoiceRecognitionSuccess(VoiceRecognitionResult.SearchResult("Result")), lastKnownEvent)
    }

    @Test
    fun whenResultFromVoiceSearchIsOKAndEmptyThenEmitSearchCancelled() {
        var lastKnownEvent: Event? = null
        testee.registerResultsCallback(mock(), mock(), BROWSER) {
            lastKnownEvent = it
        }

        val lastKnownRequest = activityResultLauncherWrapper.lastKnownRequest as ActivityResultLauncherWrapper.Request.ResultFromVoiceSearch
        lastKnownRequest.onResult(Activity.RESULT_OK, "", VoiceSearchMode.SEARCH)

        verify(pixel, never()).fire(VoiceSearchPixelNames.VOICE_SEARCH_DONE, mapOf("source" to "browser"))
        assertEquals(Event.SearchCancelled, lastKnownEvent)
    }

    @Test
    fun whenResultFromVoiceSearchIsCancelledThenEmitSearchCancelled() {
        var lastKnownEvent: Event? = null
        testee.registerResultsCallback(mock(), mock(), BROWSER) {
            lastKnownEvent = it
        }

        val lastKnownRequest = activityResultLauncherWrapper.lastKnownRequest as ActivityResultLauncherWrapper.Request.ResultFromVoiceSearch
        lastKnownRequest.onResult(Activity.RESULT_CANCELED, "Result", VoiceSearchMode.SEARCH)

        verify(pixel, never()).fire(VoiceSearchPixelNames.VOICE_SEARCH_DONE, mapOf("source" to "browser"))
        verify(voiceSearchRepository).dismissVoiceSearch()
        assertEquals(Event.SearchCancelled, lastKnownEvent)
    }

    @Test
    fun whenResultFromVoiceSearchIsCancelledSeveralTimesThenShowDialog() {
        var lastKnownEvent: Event? = null
        testee.registerResultsCallback(mock(), mock(), BROWSER) {
            lastKnownEvent = it
        }
        whenever(voiceSearchRepository.countVoiceSearchDismissed()).thenReturn(3)

        val lastKnownRequest = activityResultLauncherWrapper.lastKnownRequest as ActivityResultLauncherWrapper.Request.ResultFromVoiceSearch
        lastKnownRequest.onResult(Activity.RESULT_CANCELED, "Result", VoiceSearchMode.SEARCH)

        verify(pixel, never()).fire(VoiceSearchPixelNames.VOICE_SEARCH_DONE, mapOf("source" to "browser"))
        verify(dialogLauncher).showRemoveVoiceSearchDialog(any(), any(), any())
        assertEquals(Event.SearchCancelled, lastKnownEvent)
    }

    @Test
    fun whenResultFromVoiceSearchIsCancelledLessThanTwoTimesThenDoNotShowDialog() {
        var lastKnownEvent: Event? = null
        testee.registerResultsCallback(mock(), mock(), BROWSER) {
            lastKnownEvent = it
        }
        whenever(voiceSearchRepository.countVoiceSearchDismissed()).thenReturn(1)

        val lastKnownRequest = activityResultLauncherWrapper.lastKnownRequest as ActivityResultLauncherWrapper.Request.ResultFromVoiceSearch
        lastKnownRequest.onResult(Activity.RESULT_CANCELED, "Result", VoiceSearchMode.SEARCH)

        verify(pixel, never()).fire(VoiceSearchPixelNames.VOICE_SEARCH_DONE, mapOf("source" to "browser"))
        verify(dialogLauncher, never()).showRemoveVoiceSearchDialog(any(), any(), any())
        assertEquals(Event.SearchCancelled, lastKnownEvent)
    }

    @Test
    fun whenResultFromVoiceSearchIsOkThenResetDismissedCounter() {
        testee.registerResultsCallback(mock(), mock(), BROWSER) { }
        whenever(voiceSearchRepository.countVoiceSearchDismissed()).thenReturn(1)

        val lastKnownRequest = activityResultLauncherWrapper.lastKnownRequest as ActivityResultLauncherWrapper.Request.ResultFromVoiceSearch
        lastKnownRequest.onResult(Activity.RESULT_OK, "Result", VoiceSearchMode.SEARCH)

        verify(voiceSearchRepository).resetVoiceSearchDismissed()
        verify(voiceSearchRepository, never()).dismissVoiceSearch()
    }

    @Test
    fun whenResultFromVoiceSearchWithSearchModeAndToggleEnabledThenFireSerpDonePixel() {
        showVoiceSearchToggleFlow.value = true
        testee.registerResultsCallback(mock(), mock(), BROWSER) { }

        val lastKnownRequest = activityResultLauncherWrapper.lastKnownRequest as ActivityResultLauncherWrapper.Request.ResultFromVoiceSearch
        lastKnownRequest.onResult(Activity.RESULT_OK, "Result", VoiceSearchMode.SEARCH)

        verify(pixel).fire(VoiceSearchPixelNames.VOICE_SEARCH_SERP_DONE, mapOf("source" to "browser"))
    }

    @Test
    fun whenResultFromVoiceSearchWithSearchModeAndToggleDisabledThenDoNotFireSerpDonePixel() {
        showVoiceSearchToggleFlow.value = false
        testee.registerResultsCallback(mock(), mock(), BROWSER) { }

        val lastKnownRequest = activityResultLauncherWrapper.lastKnownRequest as ActivityResultLauncherWrapper.Request.ResultFromVoiceSearch
        lastKnownRequest.onResult(Activity.RESULT_OK, "Result", VoiceSearchMode.SEARCH)

        verify(pixel, never()).fire(VoiceSearchPixelNames.VOICE_SEARCH_SERP_DONE, mapOf("source" to "browser"))
    }

    @Test
    fun whenResultFromVoiceSearchWithDuckAiModeAndToggleEnabledThenFireAiChatDonePixel() {
        showVoiceSearchToggleFlow.value = true
        testee.registerResultsCallback(mock(), mock(), BROWSER) { }

        val lastKnownRequest = activityResultLauncherWrapper.lastKnownRequest as ActivityResultLauncherWrapper.Request.ResultFromVoiceSearch
        lastKnownRequest.onResult(Activity.RESULT_OK, "Result", VoiceSearchMode.DUCK_AI)

        verify(pixel).fire(VoiceSearchPixelNames.VOICE_SEARCH_AICHAT_DONE, mapOf("source" to "browser"))
    }

    @Test
    fun whenResultFromVoiceSearchWithDuckAiModeAndToggleDisabledThenDoNotFireAiChatDonePixel() {
        showVoiceSearchToggleFlow.value = false
        testee.registerResultsCallback(mock(), mock(), BROWSER) { }

        val lastKnownRequest = activityResultLauncherWrapper.lastKnownRequest as ActivityResultLauncherWrapper.Request.ResultFromVoiceSearch
        lastKnownRequest.onResult(Activity.RESULT_OK, "Result", VoiceSearchMode.DUCK_AI)

        verify(pixel, never()).fire(VoiceSearchPixelNames.VOICE_SEARCH_AICHAT_DONE, mapOf("source" to "browser"))
    }

    @Test
    fun whenBrowserVoiceSearchLaunchedThenEmitStartedPixelAndCallLaunchVoiceSearch() {
        whenever(voiceSearchRepository.getLastSelectedMode()).thenReturn(VoiceSearchMode.SEARCH)
        testee.registerResultsCallback(mock(), mock(), BROWSER) { }

        testee.launch(mock(), null)

        verify(pixel).fire(VoiceSearchPixelNames.VOICE_SEARCH_STARTED, mapOf("source" to "browser"))
        assertEquals(Action.LaunchVoiceSearch(VoiceSearchMode.SEARCH), activityResultLauncherWrapper.lastKnownAction)
    }

    @Test
    fun whenWidgetVoiceSearchLaunchedThenEmitStartedPixelAndCallLaunchVoiceSearch() {
        whenever(voiceSearchRepository.getLastSelectedMode()).thenReturn(VoiceSearchMode.SEARCH)
        testee.registerResultsCallback(mock(), mock(), WIDGET) { }

        testee.launch(mock(), null)

        verify(pixel).fire(VoiceSearchPixelNames.VOICE_SEARCH_STARTED, mapOf("source" to "widget"))
        assertEquals(Action.LaunchVoiceSearch(VoiceSearchMode.SEARCH), activityResultLauncherWrapper.lastKnownAction)
    }

    @Test
    fun whenLaunchWithNoInitialModeAndToggleEnabledThenUseLastSelectedMode() {
        showVoiceSearchToggleFlow.value = true
        whenever(voiceSearchRepository.getLastSelectedMode()).thenReturn(VoiceSearchMode.DUCK_AI)
        testee.registerResultsCallback(mock(), mock(), BROWSER) { }

        testee.launch(mock(), null)

        verify(voiceSearchRepository).getLastSelectedMode()
        assertEquals(Action.LaunchVoiceSearch(VoiceSearchMode.DUCK_AI), activityResultLauncherWrapper.lastKnownAction)
    }

    @Test
    fun whenLaunchWithNoInitialModeAndToggleDisabledThenUseSearchMode() {
        showVoiceSearchToggleFlow.value = false
        whenever(voiceSearchRepository.getLastSelectedMode()).thenReturn(VoiceSearchMode.DUCK_AI)
        testee.registerResultsCallback(mock(), mock(), BROWSER) { }

        testee.launch(mock(), null)

        verify(voiceSearchRepository, never()).getLastSelectedMode()
        assertEquals(Action.LaunchVoiceSearch(VoiceSearchMode.SEARCH), activityResultLauncherWrapper.lastKnownAction)
    }

    @Test
    fun whenLaunchWithInitialModeAndToggleDisabledThenUseInitialMode() {
        showVoiceSearchToggleFlow.value = false
        testee.registerResultsCallback(mock(), mock(), BROWSER) { }

        testee.launch(mock(), VoiceSearchMode.DUCK_AI)

        verify(voiceSearchRepository, never()).getLastSelectedMode()
        assertEquals(Action.LaunchVoiceSearch(VoiceSearchMode.DUCK_AI), activityResultLauncherWrapper.lastKnownAction)
    }
}
