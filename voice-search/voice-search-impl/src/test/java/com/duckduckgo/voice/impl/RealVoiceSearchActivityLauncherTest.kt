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
import com.duckduckgo.voice.api.VoiceSearchLauncher.Event
import com.duckduckgo.voice.api.VoiceSearchLauncher.Source.BROWSER
import com.duckduckgo.voice.api.VoiceSearchLauncher.Source.WIDGET
import com.duckduckgo.voice.impl.ActivityResultLauncherWrapper.Action.LaunchVoiceSearch
import com.duckduckgo.voice.impl.fakes.FakeActivityResultLauncherWrapper
import com.duckduckgo.voice.impl.listeningmode.ui.VoiceSearchBackgroundBlurRenderer
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.mockito.Mock
import org.mockito.MockitoAnnotations
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify

class RealVoiceSearchActivityLauncherTest {
    @Mock
    private lateinit var blurRenderer: VoiceSearchBackgroundBlurRenderer

    @Mock
    private lateinit var pixel: Pixel

    private lateinit var activityResultLauncherWrapper: FakeActivityResultLauncherWrapper

    private lateinit var testee: RealVoiceSearchActivityLauncher

    @Before
    fun setUp() {
        MockitoAnnotations.openMocks(this)
        activityResultLauncherWrapper = FakeActivityResultLauncherWrapper()
        testee = RealVoiceSearchActivityLauncher(
            blurRenderer,
            pixel,
            activityResultLauncherWrapper
        )
    }

    @Test
    fun whenResultFromVoiceSearchBrowserIsOKAndNotEmptyThenEmitVoiceRecognitionSuccess() {
        var lastKnownEvent: Event? = null
        testee.registerResultsCallback(mock(), mock(), BROWSER) {
            lastKnownEvent = it
        }

        val lastKnownRequest = activityResultLauncherWrapper.lastKnownRequest as ActivityResultLauncherWrapper.Request.ResultFromVoiceSearch
        lastKnownRequest.onResult(Activity.RESULT_OK, "Result")

        verify(pixel).fire(VoiceSearchPixelNames.VOICE_SEARCH_DONE, mapOf("source" to "browser"))
        assertEquals(Event.VoiceRecognitionSuccess("Result"), lastKnownEvent)
    }

    @Test
    fun whenResultFromVoiceSearchWidgetIsOKAndNotEmptyThenEmitVoiceRecognitionSuccess() {
        var lastKnownEvent: Event? = null
        testee.registerResultsCallback(mock(), mock(), WIDGET) {
            lastKnownEvent = it
        }

        val lastKnownRequest = activityResultLauncherWrapper.lastKnownRequest as ActivityResultLauncherWrapper.Request.ResultFromVoiceSearch
        lastKnownRequest.onResult(Activity.RESULT_OK, "Result")

        verify(pixel).fire(VoiceSearchPixelNames.VOICE_SEARCH_DONE, mapOf("source" to "widget"))
        assertEquals(Event.VoiceRecognitionSuccess("Result"), lastKnownEvent)
    }

    @Test
    fun whenResultFromVoiceSearchIsOKAndEmptyThenEmitSearchCancelled() {
        var lastKnownEvent: Event? = null
        testee.registerResultsCallback(mock(), mock(), BROWSER) {
            lastKnownEvent = it
        }

        val lastKnownRequest = activityResultLauncherWrapper.lastKnownRequest as ActivityResultLauncherWrapper.Request.ResultFromVoiceSearch
        lastKnownRequest.onResult(Activity.RESULT_OK, "")

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
        lastKnownRequest.onResult(Activity.RESULT_CANCELED, "Result")

        verify(pixel, never()).fire(VoiceSearchPixelNames.VOICE_SEARCH_DONE, mapOf("source" to "browser"))
        assertEquals(Event.SearchCancelled, lastKnownEvent)
    }

    @Test
    fun whenBrowserVoiceSearchLaunchedThenEmitStartedPixelAndCallLaunchVoiceSearch() {
        testee.registerResultsCallback(mock(), mock(), BROWSER) { }

        testee.launch(mock())

        verify(pixel).fire(VoiceSearchPixelNames.VOICE_SEARCH_STARTED, mapOf("source" to "browser"))
        assertEquals(LaunchVoiceSearch, activityResultLauncherWrapper.lastKnownAction)
    }

    @Test
    fun whenWidgetVoiceSearchLaunchedThenEmitStartedPixelAndCallLaunchVoiceSearch() {
        testee.registerResultsCallback(mock(), mock(), WIDGET) { }

        testee.launch(mock())

        verify(pixel).fire(VoiceSearchPixelNames.VOICE_SEARCH_STARTED, mapOf("source" to "widget"))
        assertEquals(LaunchVoiceSearch, activityResultLauncherWrapper.lastKnownAction)
    }
}
