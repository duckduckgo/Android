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

package com.duckduckgo.sync.impl.ui.v2

import app.cash.turbine.test
import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.sync.impl.ui.v2.IntroAnimationViewModel.Command.ExpandScannerCutout
import com.duckduckgo.sync.impl.ui.v2.IntroAnimationViewModel.Command.PlayIntroAnimation
import com.duckduckgo.sync.impl.ui.v2.IntroAnimationViewModel.Command.RequestCameraPermission
import com.duckduckgo.sync.impl.ui.v2.IntroAnimationViewModel.Command.ResumeCamera
import com.duckduckgo.sync.impl.ui.v2.IntroAnimationViewModel.ViewMode
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class IntroAnimationViewModelTest {
    @get:Rule
    val coroutineTestRule: CoroutineTestRule = CoroutineTestRule()

    private val cameraAccess = mock<CameraAccess>()

    // Created lazily because the view model reads the camera hardware state at construction
    private val testee by lazy { IntroAnimationViewModel(cameraAccess) }

    @Test
    fun `when camera hardware is available then the intro is shown`() = runTest {
        whenever(cameraAccess.isHardwareAvailable()).thenReturn(true)

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

        assertEquals(ViewMode.NoCameraAvailable, testee.viewState.value.viewMode)
    }

    @Test
    fun `when the animation start is requested then the play command is sent`() = runTest {
        whenever(cameraAccess.isHardwareAvailable()).thenReturn(true)

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

        testee.onAnimationFinished()

        assertEquals(ViewMode.Intro, testee.viewState.value.viewMode)
        assertTrue(testee.viewState.value.animationFinished)
    }

    @Test
    fun `when the animation finishes with camera permission granted then the camera is shown`() = runTest {
        whenever(cameraAccess.isHardwareAvailable()).thenReturn(true)
        whenever(cameraAccess.isPermissionGranted()).thenReturn(true)

        testee.onAnimationFinished()

        assertEquals(ViewMode.Camera, testee.viewState.value.viewMode)
    }

    @Test
    fun `when the scan button is clicked without camera permission then the permission request command is sent`() = runTest {
        whenever(cameraAccess.isHardwareAvailable()).thenReturn(true)
        whenever(cameraAccess.isPermissionGranted()).thenReturn(false)

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

        testee.onScanButtonClicked()

        assertEquals(ViewMode.Camera, testee.viewState.value.viewMode)
        assertTrue(testee.viewState.value.animationFinished)
    }

    @Test
    fun `when the scan button is clicked without camera permission then the animation is treated as finished`() = runTest {
        whenever(cameraAccess.isHardwareAvailable()).thenReturn(true)
        whenever(cameraAccess.isPermissionGranted()).thenReturn(false)

        testee.onScanButtonClicked()

        assertEquals(ViewMode.Intro, testee.viewState.value.viewMode)
        assertTrue(testee.viewState.value.animationFinished)
    }

    @Test
    fun `when the permission request is granted then the camera is shown`() = runTest {
        whenever(cameraAccess.isHardwareAvailable()).thenReturn(true)
        whenever(cameraAccess.isPermissionGranted()).thenReturn(true)

        testee.onCameraPermissionResult()

        assertEquals(ViewMode.Camera, testee.viewState.value.viewMode)
    }

    @Test
    fun `when the permission request is denied then the no camera permission screen is shown`() = runTest {
        whenever(cameraAccess.isHardwareAvailable()).thenReturn(true)
        whenever(cameraAccess.isPermissionGranted()).thenReturn(false)

        testee.onCameraPermissionResult()

        assertEquals(ViewMode.NoCameraPermission, testee.viewState.value.viewMode)
    }

    @Test
    fun `when permission is granted in settings after a denial then refreshing shows the camera`() = runTest {
        whenever(cameraAccess.isHardwareAvailable()).thenReturn(true)
        whenever(cameraAccess.isPermissionGranted()).thenReturn(false)
        testee.onCameraPermissionResult()

        whenever(cameraAccess.isPermissionGranted()).thenReturn(true)
        testee.refreshCameraPermissionState()

        assertEquals(ViewMode.Camera, testee.viewState.value.viewMode)
    }

    @Test
    fun `when permission is granted in settings after the animation finished then refreshing shows the camera`() = runTest {
        whenever(cameraAccess.isHardwareAvailable()).thenReturn(true)
        whenever(cameraAccess.isPermissionGranted()).thenReturn(false)
        testee.onAnimationFinished()

        whenever(cameraAccess.isPermissionGranted()).thenReturn(true)
        testee.refreshCameraPermissionState()

        assertEquals(ViewMode.Camera, testee.viewState.value.viewMode)
    }

    @Test
    fun `when permission is granted while the animation is still running then refreshing keeps the intro`() = runTest {
        whenever(cameraAccess.isHardwareAvailable()).thenReturn(true)
        whenever(cameraAccess.isPermissionGranted()).thenReturn(true)

        testee.refreshCameraPermissionState()

        assertEquals(ViewMode.Intro, testee.viewState.value.viewMode)
    }

    @Test
    fun `when permission is not granted then refreshing keeps the current screen`() = runTest {
        whenever(cameraAccess.isHardwareAvailable()).thenReturn(true)
        whenever(cameraAccess.isPermissionGranted()).thenReturn(false)

        testee.refreshCameraPermissionState()

        assertEquals(ViewMode.Intro, testee.viewState.value.viewMode)
    }

    @Test
    fun `when there is no camera hardware then no event changes the screen`() = runTest {
        whenever(cameraAccess.isHardwareAvailable()).thenReturn(false)
        whenever(cameraAccess.isPermissionGranted()).thenReturn(true)

        testee.onAnimationFinished()
        testee.onScanButtonClicked()
        testee.onCameraPermissionResult()
        testee.refreshCameraPermissionState()

        assertEquals(ViewMode.NoCameraAvailable, testee.viewState.value.viewMode)
        assertFalse(testee.viewState.value.animationFinished)
    }

    @Test
    fun `when the scan button is clicked with camera permission granted then the camera resume and cutout expand commands are sent`() = runTest {
        whenever(cameraAccess.isHardwareAvailable()).thenReturn(true)
        whenever(cameraAccess.isPermissionGranted()).thenReturn(true)

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

        testee.commands.test {
            testee.refreshCameraPermissionState()
            expectNoEvents()

            cancel()
        }
    }
}

private inline fun <reified T> assertIs(value: Any?) {
    assertTrue("Expected ${T::class.simpleName} but was ${value?.let { it::class.simpleName }}", value is T)
}
