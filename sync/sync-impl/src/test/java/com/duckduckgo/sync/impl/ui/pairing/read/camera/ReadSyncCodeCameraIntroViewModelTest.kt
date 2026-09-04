/*
 * Copyright (c) 2026 DuckDuckGo
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

package com.duckduckgo.sync.impl.ui.pairing.read.camera

import app.cash.turbine.test
import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.sync.impl.pixels.SyncPixels
import com.duckduckgo.sync.impl.ui.pairing.read.camera.ReadSyncCodeCameraIntroViewModel.Command.ExpandScannerCutout
import com.duckduckgo.sync.impl.ui.pairing.read.camera.ReadSyncCodeCameraIntroViewModel.Command.OpenPermissionSettings
import com.duckduckgo.sync.impl.ui.pairing.read.camera.ReadSyncCodeCameraIntroViewModel.Command.PlayIntroAnimation
import com.duckduckgo.sync.impl.ui.pairing.read.camera.ReadSyncCodeCameraIntroViewModel.Command.RequestCameraPermission
import com.duckduckgo.sync.impl.ui.pairing.read.camera.ReadSyncCodeCameraIntroViewModel.Command.ResumeCamera
import com.duckduckgo.sync.impl.ui.pairing.read.camera.ReadSyncCodeCameraIntroViewModel.ViewMode
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoMoreInteractions
import org.mockito.kotlin.whenever

class ReadSyncCodeCameraIntroViewModelTest {
    @get:Rule
    val coroutineTestRule: CoroutineTestRule = CoroutineTestRule()

    private val cameraAccess = mock<CameraAccess>()
    private val syncPixels = mock<SyncPixels>()

    // The view model reads the camera hardware/permission state at construction, so callers
    // must stub cameraAccess before calling this.
    private fun createTestee() = ReadSyncCodeCameraIntroViewModel(cameraAccess, syncPixels)

    @Test
    fun `when camera hardware is available then the intro is shown`() = runTest {
        whenever(cameraAccess.isHardwareAvailable()).thenReturn(true)
        val testee = createTestee()

        testee.viewState.test {
            val viewState = awaitItem()
            assertEquals(ViewMode.Intro, viewState.viewMode)
            assertFalse(viewState.animationFinished)

            cancel()
        }
    }

    @Test
    fun `when camera hardware is not available then the no camera available screen is shown`() = runTest {
        whenever(cameraAccess.isHardwareAvailable()).thenReturn(false)
        val testee = createTestee()

        assertEquals(ViewMode.NoCameraAvailable, testee.viewState.value.viewMode)
    }

    @Test
    fun `when the animation start is requested then the play command is sent`() = runTest {
        whenever(cameraAccess.isHardwareAvailable()).thenReturn(true)
        val testee = createTestee()

        testee.commands.test {
            testee.requestAnimationStart()
            assertIs<PlayIntroAnimation>(awaitItem())

            cancel()
        }
    }

    @Test
    fun `when the animation start is requested after the animation finished then no play command is sent`() = runTest {
        whenever(cameraAccess.isHardwareAvailable()).thenReturn(true)
        whenever(cameraAccess.isPermissionGranted()).thenReturn(false)
        val testee = createTestee()

        testee.onAnimationFinished()

        testee.commands.test {
            testee.requestAnimationStart()
            expectNoEvents()

            cancel()
        }
    }

    @Test
    fun `when the animation start is requested without camera hardware then no play command is sent`() = runTest {
        whenever(cameraAccess.isHardwareAvailable()).thenReturn(false)
        val testee = createTestee()

        testee.commands.test {
            testee.requestAnimationStart()
            expectNoEvents()

            cancel()
        }
    }

    @Test
    fun `when the animation finishes without camera permission then the intro remains shown`() = runTest {
        whenever(cameraAccess.isHardwareAvailable()).thenReturn(true)
        whenever(cameraAccess.isPermissionGranted()).thenReturn(false)
        val testee = createTestee()

        testee.onAnimationFinished()

        assertEquals(ViewMode.Intro, testee.viewState.value.viewMode)
        assertTrue(testee.viewState.value.animationFinished)
    }

    @Test
    fun `when the animation finishes with camera permission granted then the camera is shown`() = runTest {
        whenever(cameraAccess.isHardwareAvailable()).thenReturn(true)
        whenever(cameraAccess.isPermissionGranted()).thenReturn(true)
        val testee = createTestee()

        testee.onAnimationFinished()

        assertEquals(ViewMode.Camera, testee.viewState.value.viewMode)
    }

    @Test
    fun `when the scan button is clicked without camera permission then the permission request command is sent`() = runTest {
        whenever(cameraAccess.isHardwareAvailable()).thenReturn(true)
        whenever(cameraAccess.isPermissionGranted()).thenReturn(false)
        val testee = createTestee()

        testee.commands.test {
            testee.onScanButtonClicked()
            assertIs<RequestCameraPermission>(awaitItem())

            cancel()
        }
    }

    @Test
    fun `when the scan button is clicked with camera permission granted then the camera is shown`() = runTest {
        whenever(cameraAccess.isHardwareAvailable()).thenReturn(true)
        whenever(cameraAccess.isPermissionGranted()).thenReturn(true)
        val testee = createTestee()

        testee.onScanButtonClicked()

        assertEquals(ViewMode.Camera, testee.viewState.value.viewMode)
        assertTrue(testee.viewState.value.animationFinished)
    }

    @Test
    fun `when the scan button is clicked without camera permission then the animation is treated as finished`() = runTest {
        whenever(cameraAccess.isHardwareAvailable()).thenReturn(true)
        whenever(cameraAccess.isPermissionGranted()).thenReturn(false)
        val testee = createTestee()

        testee.onScanButtonClicked()

        assertEquals(ViewMode.Intro, testee.viewState.value.viewMode)
        assertTrue(testee.viewState.value.animationFinished)
    }

    @Test
    fun `when the permission request is granted then the camera is shown`() = runTest {
        whenever(cameraAccess.isHardwareAvailable()).thenReturn(true)
        whenever(cameraAccess.isPermissionGranted()).thenReturn(true)
        val testee = createTestee()

        testee.onCameraPermissionResult()

        assertEquals(ViewMode.Camera, testee.viewState.value.viewMode)
    }

    @Test
    fun `when the permission request is denied then the no camera permission screen is shown`() = runTest {
        whenever(cameraAccess.isHardwareAvailable()).thenReturn(true)
        whenever(cameraAccess.isPermissionGranted()).thenReturn(false)
        val testee = createTestee()

        testee.onCameraPermissionResult()

        assertEquals(ViewMode.NoCameraPermission, testee.viewState.value.viewMode)
    }

    @Test
    fun `when permission is granted in settings after a denial then refreshing shows the camera`() = runTest {
        whenever(cameraAccess.isHardwareAvailable()).thenReturn(true)
        whenever(cameraAccess.isPermissionGranted()).thenReturn(false)
        val testee = createTestee()
        testee.onCameraPermissionResult()

        whenever(cameraAccess.isPermissionGranted()).thenReturn(true)
        testee.refreshCameraPermissionState()

        assertEquals(ViewMode.Camera, testee.viewState.value.viewMode)
    }

    @Test
    fun `when permission is granted in settings after the animation finished then refreshing shows the camera`() = runTest {
        whenever(cameraAccess.isHardwareAvailable()).thenReturn(true)
        whenever(cameraAccess.isPermissionGranted()).thenReturn(false)
        val testee = createTestee()
        testee.onAnimationFinished()

        whenever(cameraAccess.isPermissionGranted()).thenReturn(true)
        testee.refreshCameraPermissionState()

        assertEquals(ViewMode.Camera, testee.viewState.value.viewMode)
    }

    @Test
    fun `when permission is granted while the animation is still running then refreshing keeps the intro`() = runTest {
        whenever(cameraAccess.isHardwareAvailable()).thenReturn(true)
        whenever(cameraAccess.isPermissionGranted()).thenReturn(true)
        val testee = createTestee()

        testee.refreshCameraPermissionState()

        assertEquals(ViewMode.Intro, testee.viewState.value.viewMode)
    }

    @Test
    fun `when permission is not granted then refreshing keeps the current screen`() = runTest {
        whenever(cameraAccess.isHardwareAvailable()).thenReturn(true)
        whenever(cameraAccess.isPermissionGranted()).thenReturn(false)
        val testee = createTestee()

        testee.refreshCameraPermissionState()

        assertEquals(ViewMode.Intro, testee.viewState.value.viewMode)
    }

    @Test
    fun `when there is no camera hardware then no event changes the screen`() = runTest {
        whenever(cameraAccess.isHardwareAvailable()).thenReturn(false)
        whenever(cameraAccess.isPermissionGranted()).thenReturn(true)
        val testee = createTestee()

        testee.onAnimationFinished()
        testee.onScanButtonClicked()
        testee.onCameraPermissionResult()
        testee.refreshCameraPermissionState()
        testee.onGoToPermissionSettingsClicked()

        assertEquals(ViewMode.NoCameraAvailable, testee.viewState.value.viewMode)
        assertFalse(testee.viewState.value.animationFinished)
    }

    @Test
    fun `when the scan button is clicked with camera permission granted then the camera resume and cutout expand commands are sent`() = runTest {
        whenever(cameraAccess.isHardwareAvailable()).thenReturn(true)
        whenever(cameraAccess.isPermissionGranted()).thenReturn(true)
        val testee = createTestee()

        testee.commands.test {
            testee.onScanButtonClicked()
            assertIs<ResumeCamera>(awaitItem())
            assertIs<ExpandScannerCutout>(awaitItem())

            cancel()
        }
    }

    @Test
    fun `when the scan button is clicked without camera permission then only the permission request command is sent`() = runTest {
        whenever(cameraAccess.isHardwareAvailable()).thenReturn(true)
        whenever(cameraAccess.isPermissionGranted()).thenReturn(false)
        val testee = createTestee()

        testee.commands.test {
            testee.onScanButtonClicked()
            assertIs<RequestCameraPermission>(awaitItem())
            expectNoEvents()

            cancel()
        }
    }

    @Test
    fun `when the permission request is granted then the camera resume and cutout expand commands are sent`() = runTest {
        whenever(cameraAccess.isHardwareAvailable()).thenReturn(true)
        whenever(cameraAccess.isPermissionGranted()).thenReturn(true)
        val testee = createTestee()

        testee.commands.test {
            testee.onCameraPermissionResult()
            assertIs<ResumeCamera>(awaitItem())
            assertIs<ExpandScannerCutout>(awaitItem())

            cancel()
        }
    }

    @Test
    fun `when the permission request is denied then no camera commands are sent`() = runTest {
        whenever(cameraAccess.isHardwareAvailable()).thenReturn(true)
        whenever(cameraAccess.isPermissionGranted()).thenReturn(false)
        val testee = createTestee()

        testee.commands.test {
            testee.onCameraPermissionResult()
            expectNoEvents()

            cancel()
        }
    }

    @Test
    fun `when the animation finishes with camera permission granted then the camera resume and cutout expand commands are sent`() = runTest {
        whenever(cameraAccess.isHardwareAvailable()).thenReturn(true)
        whenever(cameraAccess.isPermissionGranted()).thenReturn(true)
        val testee = createTestee()

        testee.commands.test {
            testee.onAnimationFinished()
            assertIs<ResumeCamera>(awaitItem())
            assertIs<ExpandScannerCutout>(awaitItem())

            cancel()
        }
    }

    @Test
    fun `when refreshing shows the camera then the camera resume and cutout expand commands are sent`() = runTest {
        whenever(cameraAccess.isHardwareAvailable()).thenReturn(true)
        whenever(cameraAccess.isPermissionGranted()).thenReturn(false)
        val testee = createTestee()
        testee.onCameraPermissionResult()

        whenever(cameraAccess.isPermissionGranted()).thenReturn(true)

        testee.commands.test {
            testee.refreshCameraPermissionState()
            assertIs<ResumeCamera>(awaitItem())
            assertIs<ExpandScannerCutout>(awaitItem())

            cancel()
        }
    }

    @Test
    fun `when refreshing while the camera is already shown then the camera resume and cutout expand commands are sent again`() = runTest {
        whenever(cameraAccess.isHardwareAvailable()).thenReturn(true)
        whenever(cameraAccess.isPermissionGranted()).thenReturn(true)
        val testee = createTestee()

        testee.commands.test {
            testee.onScanButtonClicked()
            assertIs<ResumeCamera>(awaitItem())
            assertIs<ExpandScannerCutout>(awaitItem())

            testee.refreshCameraPermissionState()
            assertIs<ResumeCamera>(awaitItem())
            assertIs<ExpandScannerCutout>(awaitItem())

            cancel()
        }
    }

    @Test
    fun `when refreshing while the intro is still running then no camera commands are sent`() = runTest {
        whenever(cameraAccess.isHardwareAvailable()).thenReturn(true)
        whenever(cameraAccess.isPermissionGranted()).thenReturn(true)
        val testee = createTestee()

        testee.commands.test {
            testee.refreshCameraPermissionState()
            expectNoEvents()

            cancel()
        }
    }

    @Test
    fun `when permission result is granted and already granted on init then pixel reports granted before and after`() = runTest {
        whenever(cameraAccess.isHardwareAvailable()).thenReturn(true)
        whenever(cameraAccess.isPermissionGranted()).thenReturn(true)
        val testee = createTestee()

        testee.onCameraPermissionResult()

        verify(syncPixels).fireScannerCameraPermissionState(beforeRequesting = true, afterRequesting = true)
    }

    @Test
    fun `when permission result is granted and not granted on init then pixel reports not granted before and granted after`() = runTest {
        whenever(cameraAccess.isHardwareAvailable()).thenReturn(true)
        whenever(cameraAccess.isPermissionGranted()).thenReturn(false)
        val testee = createTestee()

        whenever(cameraAccess.isPermissionGranted()).thenReturn(true)
        testee.onCameraPermissionResult()

        verify(syncPixels).fireScannerCameraPermissionState(beforeRequesting = false, afterRequesting = true)
    }

    @Test
    fun `when permission result is denied and not granted on init then pixel reports not granted before and after`() = runTest {
        whenever(cameraAccess.isHardwareAvailable()).thenReturn(true)
        whenever(cameraAccess.isPermissionGranted()).thenReturn(false)
        val testee = createTestee()

        testee.onCameraPermissionResult()

        verify(syncPixels).fireScannerCameraPermissionState(beforeRequesting = false, afterRequesting = false)
    }

    @Test
    fun `when the animation finishes with permission granted on init then pixel reports granted before and after`() = runTest {
        whenever(cameraAccess.isHardwareAvailable()).thenReturn(true)
        whenever(cameraAccess.isPermissionGranted()).thenReturn(true)
        val testee = createTestee()

        testee.onAnimationFinished()

        verify(syncPixels).fireScannerCameraPermissionState(beforeRequesting = true, afterRequesting = true)
    }

    @Test
    fun `when the scan button is clicked with permission granted on init then pixel reports granted before and after`() = runTest {
        whenever(cameraAccess.isHardwareAvailable()).thenReturn(true)
        whenever(cameraAccess.isPermissionGranted()).thenReturn(true)
        val testee = createTestee()

        testee.onScanButtonClicked()

        verify(syncPixels).fireScannerCameraPermissionState(beforeRequesting = true, afterRequesting = true)
    }

    @Test
    fun `when the animation finishes without camera permission then no permission state pixel is fired`() = runTest {
        whenever(cameraAccess.isHardwareAvailable()).thenReturn(true)
        whenever(cameraAccess.isPermissionGranted()).thenReturn(false)
        val testee = createTestee()

        testee.onAnimationFinished()

        verifyNoMoreInteractions(syncPixels)
    }

    @Test
    fun `when the camera activates again after reporting the permission state then the pixel is not fired again`() = runTest {
        whenever(cameraAccess.isHardwareAvailable()).thenReturn(true)
        whenever(cameraAccess.isPermissionGranted()).thenReturn(true)
        val testee = createTestee()

        testee.onAnimationFinished()
        testee.refreshCameraPermissionState()
        testee.onScanButtonClicked()

        verify(syncPixels).fireScannerCameraPermissionState(beforeRequesting = true, afterRequesting = true)
        verifyNoMoreInteractions(syncPixels)
    }

    @Test
    fun `when permission is granted in settings without any request then pixel reports not granted before and granted after`() = runTest {
        whenever(cameraAccess.isHardwareAvailable()).thenReturn(true)
        whenever(cameraAccess.isPermissionGranted()).thenReturn(false)
        val testee = createTestee()
        testee.onAnimationFinished()

        whenever(cameraAccess.isPermissionGranted()).thenReturn(true)
        testee.refreshCameraPermissionState()

        assertEquals(ViewMode.Camera, testee.viewState.value.viewMode)
        verify(syncPixels).fireScannerCameraPermissionState(beforeRequesting = false, afterRequesting = true)
    }

    @Test
    fun `when permission is granted in settings after a denial without going to them from this screen then the pixel is not fired again`() = runTest {
        whenever(cameraAccess.isHardwareAvailable()).thenReturn(true)
        whenever(cameraAccess.isPermissionGranted()).thenReturn(false)
        val testee = createTestee()
        testee.onCameraPermissionResult()

        whenever(cameraAccess.isPermissionGranted()).thenReturn(true)
        testee.refreshCameraPermissionState()

        verify(syncPixels).fireScannerCameraPermissionState(beforeRequesting = false, afterRequesting = false)
        verifyNoMoreInteractions(syncPixels)
    }

    @Test
    fun `when permission is granted in settings after going to them from a denial then pixel reports the grant`() = runTest {
        whenever(cameraAccess.isHardwareAvailable()).thenReturn(true)
        whenever(cameraAccess.isPermissionGranted()).thenReturn(false)
        val testee = createTestee()
        testee.onCameraPermissionResult()
        testee.onGoToPermissionSettingsClicked()

        whenever(cameraAccess.isPermissionGranted()).thenReturn(true)
        testee.refreshCameraPermissionState()

        verify(syncPixels).fireScannerCameraPermissionState(beforeRequesting = false, afterRequesting = false)
        verify(syncPixels).fireScannerCameraPermissionState(beforeRequesting = false, afterRequesting = true)
    }

    @Test
    fun `when the user returns from settings without granting the permission then no additional pixel is fired`() = runTest {
        whenever(cameraAccess.isHardwareAvailable()).thenReturn(true)
        whenever(cameraAccess.isPermissionGranted()).thenReturn(false)
        val testee = createTestee()
        testee.onCameraPermissionResult()
        testee.onGoToPermissionSettingsClicked()

        testee.refreshCameraPermissionState()

        verify(syncPixels).fireScannerCameraPermissionState(beforeRequesting = false, afterRequesting = false)
        verifyNoMoreInteractions(syncPixels)
    }

    @Test
    fun `when the go to settings button is clicked then the open permission settings command is sent`() = runTest {
        whenever(cameraAccess.isHardwareAvailable()).thenReturn(true)
        whenever(cameraAccess.isPermissionGranted()).thenReturn(false)
        val testee = createTestee()

        testee.commands.test {
            testee.onGoToPermissionSettingsClicked()
            assertIs<OpenPermissionSettings>(awaitItem())

            cancel()
        }
    }

    @Test
    fun `when the go to settings button is clicked without camera hardware then no command is sent`() = runTest {
        whenever(cameraAccess.isHardwareAvailable()).thenReturn(false)
        val testee = createTestee()

        testee.commands.test {
            testee.onGoToPermissionSettingsClicked()
            expectNoEvents()

            cancel()
        }
    }

    @Test
    fun `when refreshing the camera permission state then no permission state pixel is fired`() = runTest {
        whenever(cameraAccess.isHardwareAvailable()).thenReturn(true)
        whenever(cameraAccess.isPermissionGranted()).thenReturn(false)
        val testee = createTestee()

        whenever(cameraAccess.isPermissionGranted()).thenReturn(true)
        testee.refreshCameraPermissionState()

        verifyNoMoreInteractions(syncPixels)
    }

    @Test
    fun `when there is no camera hardware then no permission state pixel is fired`() = runTest {
        whenever(cameraAccess.isHardwareAvailable()).thenReturn(false)
        whenever(cameraAccess.isPermissionGranted()).thenReturn(true)
        val testee = createTestee()

        testee.onAnimationFinished()
        testee.onScanButtonClicked()
        testee.onCameraPermissionResult()
        testee.refreshCameraPermissionState()
        testee.onGoToPermissionSettingsClicked()

        verifyNoMoreInteractions(syncPixels)
    }
}

private inline fun <reified T> assertIs(value: Any?) {
    assertTrue("Expected ${T::class.simpleName} but was ${value?.let { it::class.simpleName }}", value is T)
}
