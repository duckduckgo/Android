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
import com.duckduckgo.voice.api.VoiceSearchLauncher.VoiceSearchMode
import com.duckduckgo.voice.impl.ActivityResultLauncherWrapper.Action.LaunchPermissionRequest
import com.duckduckgo.voice.impl.fakes.FakeActivityResultLauncherWrapper
import com.duckduckgo.voice.impl.fakes.FakeVoiceSearchPermissionDialogsLauncher
import com.duckduckgo.voice.store.VoiceSearchRepository
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.Mock
import org.mockito.MockitoAnnotations
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class MicrophonePermissionRequestTest {
    @Mock
    private lateinit var voiceSearchRepository: VoiceSearchRepository

    @Mock
    private lateinit var permissionRationale: PermissionRationale

    private lateinit var voiceSearchPermissionDialogsLauncher: FakeVoiceSearchPermissionDialogsLauncher

    private lateinit var activityResultLauncherWrapper: FakeActivityResultLauncherWrapper

    private lateinit var testee: MicrophonePermissionRequest

    @Before
    fun setUp() {
        MockitoAnnotations.openMocks(this)
        voiceSearchPermissionDialogsLauncher = FakeVoiceSearchPermissionDialogsLauncher()
        activityResultLauncherWrapper = FakeActivityResultLauncherWrapper()
        testee = MicrophonePermissionRequest(
            voiceSearchRepository,
            voiceSearchPermissionDialogsLauncher,
            activityResultLauncherWrapper,
            permissionRationale,
        )
    }

    @Test
    fun whenPermissionGrantedThenInvokeOnPermissionsGranted() {
        var permissionGranted = false
        testee.registerResultsCallback(
            mock(),
            mock(),
            onPermissionsGranted = { permissionGranted = true },
            mock(),
        )

        val lastKnownRequest = activityResultLauncherWrapper.lastKnownRequest as ActivityResultLauncherWrapper.Request.Permission
        lastKnownRequest.onResult(true)

        assertTrue(permissionGranted)
    }

    @Test
    fun whenLaunchThenGoStraightToSystemPrompt() {
        testee.registerResultsCallback(mock(), mock(), mock()) { }
        testee.launch(mock(), VoiceSearchMode.SEARCH)

        assertEquals(LaunchPermissionRequest, activityResultLauncherWrapper.lastKnownAction)
    }

    @Test
    fun whenFirstDenialThenShowSnackbarRatherThanDialog() {
        whenever(permissionRationale.shouldShow(any())).thenReturn(true)

        testee.registerResultsCallback(mock(), mock(), mock()) { }
        val lastKnownRequest = activityResultLauncherWrapper.lastKnownRequest as ActivityResultLauncherWrapper.Request.Permission
        lastKnownRequest.onResult(false)

        assertTrue(voiceSearchPermissionDialogsLauncher.micPermissionDeniedSnackbarShown)
        assertFalse(voiceSearchPermissionDialogsLauncher.micAccessDeniedDialogShown)
    }

    @Test
    fun whenSnackbarAllowSelectedThenRequestPermissionAgain() {
        whenever(permissionRationale.shouldShow(any())).thenReturn(true)

        testee.registerResultsCallback(mock(), mock(), mock()) { }
        val lastKnownRequest = activityResultLauncherWrapper.lastKnownRequest as ActivityResultLauncherWrapper.Request.Permission
        lastKnownRequest.onResult(false)
        voiceSearchPermissionDialogsLauncher.boundSnackbarAllowSelected.invoke()

        assertEquals(LaunchPermissionRequest, activityResultLauncherWrapper.lastKnownAction)
    }

    @Test
    fun whenFirstDenialThenRequestReportedAsAborted() {
        // The caller pauses the WebView before launching, so it needs to hear that nothing will open.
        whenever(permissionRationale.shouldShow(any())).thenReturn(true)
        var aborted = false

        testee.registerResultsCallback(mock(), mock(), mock(), onRequestAborted = { aborted = true })
        val lastKnownRequest = activityResultLauncherWrapper.lastKnownRequest as ActivityResultLauncherWrapper.Request.Permission
        lastKnownRequest.onResult(false)

        assertTrue(aborted)
    }

    @Test
    fun whenDeniedForeverThenShowMicAccessDeniedDialog() {
        whenever(permissionRationale.shouldShow(any())).thenReturn(false)

        testee.registerResultsCallback(mock(), mock(), mock()) { }
        val lastKnownRequest = activityResultLauncherWrapper.lastKnownRequest as ActivityResultLauncherWrapper.Request.Permission
        lastKnownRequest.onResult(false)

        assertTrue(voiceSearchPermissionDialogsLauncher.micAccessDeniedDialogShown)
        assertFalse(voiceSearchPermissionDialogsLauncher.micPermissionDeniedSnackbarShown)
    }

    @Test
    fun whenDeniedForeverAndHideVoiceSearchSelectedThenDisableVoiceSearch() {
        whenever(permissionRationale.shouldShow(any())).thenReturn(false)
        var disableVoiceSearch = false

        testee.registerResultsCallback(mock(), mock(), mock()) { disableVoiceSearch = true }
        val lastKnownRequest = activityResultLauncherWrapper.lastKnownRequest as ActivityResultLauncherWrapper.Request.Permission
        lastKnownRequest.onResult(false)
        voiceSearchPermissionDialogsLauncher.boundHideVoiceSearchSelected.invoke()

        verify(voiceSearchRepository).setVoiceSearchUserEnabled(eq(false))
        assertTrue(disableVoiceSearch)
    }

    @Test
    fun whenDeniedForeverAndActivityIsGoneThenDoNotShowMicAccessDeniedDialog() {
        whenever(permissionRationale.shouldShow(any())).thenReturn(false)
        val goneActivity = mock<Activity>()
        whenever(goneActivity.isDestroyed).thenReturn(true)

        testee.registerResultsCallback(mock(), goneActivity, mock()) { }
        val lastKnownRequest = activityResultLauncherWrapper.lastKnownRequest as ActivityResultLauncherWrapper.Request.Permission
        lastKnownRequest.onResult(false)

        assertFalse(voiceSearchPermissionDialogsLauncher.micAccessDeniedDialogShown)
    }

    @Test
    fun whenDeniedForeverInDuckAiModeThenDialogDoesNotOfferToHideVoiceSearch() {
        whenever(permissionRationale.shouldShow(any())).thenReturn(false)

        testee.registerResultsCallback(mock(), mock(), mock()) { }
        testee.launch(mock(), VoiceSearchMode.DUCK_AI)
        val lastKnownRequest = activityResultLauncherWrapper.lastKnownRequest as ActivityResultLauncherWrapper.Request.Permission
        lastKnownRequest.onResult(false)

        assertTrue(voiceSearchPermissionDialogsLauncher.micAccessDeniedDialogShown)
        assertFalse(voiceSearchPermissionDialogsLauncher.micAccessDeniedDialogOfferedHideVoiceSearch)
    }

    @Test
    fun whenDeniedForeverInSearchModeThenDialogOffersToHideVoiceSearch() {
        whenever(permissionRationale.shouldShow(any())).thenReturn(false)

        testee.registerResultsCallback(mock(), mock(), mock()) { }
        testee.launch(mock(), VoiceSearchMode.SEARCH)
        val lastKnownRequest = activityResultLauncherWrapper.lastKnownRequest as ActivityResultLauncherWrapper.Request.Permission
        lastKnownRequest.onResult(false)

        assertTrue(voiceSearchPermissionDialogsLauncher.micAccessDeniedDialogOfferedHideVoiceSearch)
    }

    @Test
    fun whenDeniedForeverDialogCancelledThenRequestReportedAsAborted() {
        whenever(permissionRationale.shouldShow(any())).thenReturn(false)
        var aborted = false

        testee.registerResultsCallback(mock(), mock(), mock(), onRequestAborted = { aborted = true })
        val lastKnownRequest = activityResultLauncherWrapper.lastKnownRequest as ActivityResultLauncherWrapper.Request.Permission
        lastKnownRequest.onResult(false)
        assertFalse(aborted)

        voiceSearchPermissionDialogsLauncher.boundMicAccessDeniedCancelled.invoke()

        assertTrue(aborted)
    }

    @Test
    fun whenPermissionGrantedThenRequestNotReportedAsAborted() {
        var aborted = false

        testee.registerResultsCallback(mock(), mock(), mock(), onRequestAborted = { aborted = true })
        val lastKnownRequest = activityResultLauncherWrapper.lastKnownRequest as ActivityResultLauncherWrapper.Request.Permission
        lastKnownRequest.onResult(true)

        assertFalse(aborted)
    }
}
