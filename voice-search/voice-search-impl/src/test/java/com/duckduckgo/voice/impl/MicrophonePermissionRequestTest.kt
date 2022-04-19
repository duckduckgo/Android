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

import com.duckduckgo.app.statistics.pixels.Pixel
import com.duckduckgo.voice.store.VoiceSearchRepository
import com.duckduckgo.voice.impl.ActivityResultLauncherWrapper.Action.LaunchPermissionRequest
import com.duckduckgo.voice.impl.fakes.FakeActivityResultLauncherWrapper
import com.duckduckgo.voice.impl.fakes.FakeVoiceSearchPermissionDialogsLauncher
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.Mock
import org.mockito.MockitoAnnotations
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class MicrophonePermissionRequestTest {
    @Mock
    private lateinit var pixel: Pixel

    @Mock
    private lateinit var voiceSearchRepository: VoiceSearchRepository

    private lateinit var voiceSearchPermissionDialogsLauncher: FakeVoiceSearchPermissionDialogsLauncher

    private lateinit var activityResultLauncherWrapper: FakeActivityResultLauncherWrapper

    private lateinit var testee: MicrophonePermissionRequest

    @Before
    fun setUp() {
        MockitoAnnotations.openMocks(this)
        voiceSearchPermissionDialogsLauncher = FakeVoiceSearchPermissionDialogsLauncher()
        activityResultLauncherWrapper = FakeActivityResultLauncherWrapper()
        testee = MicrophonePermissionRequest(
            pixel,
            voiceSearchRepository,
            voiceSearchPermissionDialogsLauncher,
            activityResultLauncherWrapper
        )
    }

    @Test
    fun whenPermissionRequestResultIsTrueThenInvokeOnPermissionsGranted() {
        var permissionGranted = false
        testee.registerResultsCallback(mock(), mock()) {
            permissionGranted = true
        }

        val lastKnownRequest = activityResultLauncherWrapper.lastKnownRequest as ActivityResultLauncherWrapper.Request.Permission
        lastKnownRequest.onResult(true)

        assertTrue(permissionGranted)
    }

    @Test
    fun whenPermissionRequestResultIsFalseThenOnPermissionsGrantedNotInvoked() {
        var permissionGranted = false
        testee.registerResultsCallback(mock(), mock()) {
            permissionGranted = true
        }

        val lastKnownRequest = activityResultLauncherWrapper.lastKnownRequest as ActivityResultLauncherWrapper.Request.Permission
        lastKnownRequest.onResult(false)

        assertFalse(permissionGranted)
    }

    @Test
    fun whenPermissionDeclinedForeverThenLaunchNoMicAccessDialog() {
        whenever(voiceSearchRepository.getHasPermissionDeclinedForever()).thenReturn(true)

        testee.registerResultsCallback(mock(), mock()) { }
        testee.launch(mock())

        assertFalse(voiceSearchPermissionDialogsLauncher.rationaleDialogShown)
        assertTrue(voiceSearchPermissionDialogsLauncher.noMicAccessDialogShown)
    }

    @Test
    fun whenRationalDialogNotYetAcceptedThenLaunchRationalDialog() {
        whenever(voiceSearchRepository.getHasPermissionDeclinedForever()).thenReturn(false)
        whenever(voiceSearchRepository.getHasAcceptedRationaleDialog()).thenReturn(false)

        testee.registerResultsCallback(mock(), mock()) { }
        testee.launch(mock())

        assertTrue(voiceSearchPermissionDialogsLauncher.rationaleDialogShown)
        assertFalse(voiceSearchPermissionDialogsLauncher.noMicAccessDialogShown)
    }

    @Test
    fun whenRationalDialogAcceptedThenLaunchPermisionRequestFlow() {
        whenever(voiceSearchRepository.getHasPermissionDeclinedForever()).thenReturn(false)
        whenever(voiceSearchRepository.getHasAcceptedRationaleDialog()).thenReturn(true)

        testee.registerResultsCallback(mock(), mock()) { }
        testee.launch(mock())

        assertFalse(voiceSearchPermissionDialogsLauncher.rationaleDialogShown)
        assertFalse(voiceSearchPermissionDialogsLauncher.noMicAccessDialogShown)
        assertEquals(LaunchPermissionRequest, activityResultLauncherWrapper.lastKnownAction)
    }

    @Test
    fun whenRationalDialogShownThenRationalAcceptedInvokedThenFilePixelAndLaunchPermission() {
        whenever(voiceSearchRepository.getHasPermissionDeclinedForever()).thenReturn(false)
        whenever(voiceSearchRepository.getHasAcceptedRationaleDialog()).thenReturn(false)
        testee.registerResultsCallback(mock(), mock()) { }
        testee.launch(mock())

        voiceSearchPermissionDialogsLauncher.boundOnRationaleAccepted.invoke()

        verify(pixel).fire(VoiceSearchPixelNames.VOICE_SEARCH_PRIVACY_DIALOG_ACCEPTED)
        verify(voiceSearchRepository).acceptRationaleDialog()
        assertEquals(LaunchPermissionRequest, activityResultLauncherWrapper.lastKnownAction)
    }

    @Test
    fun whenRationalDialogShownThenRationalCancelledInvokedThenFilePixelAndLaunchPermission() {
        whenever(voiceSearchRepository.getHasPermissionDeclinedForever()).thenReturn(false)
        whenever(voiceSearchRepository.getHasAcceptedRationaleDialog()).thenReturn(false)
        testee.registerResultsCallback(mock(), mock()) { }
        testee.launch(mock())

        voiceSearchPermissionDialogsLauncher.boundOnRationaleDeclined.invoke()

        verify(pixel).fire(VoiceSearchPixelNames.VOICE_SEARCH_PRIVACY_DIALOG_REJECTED)
    }
}
