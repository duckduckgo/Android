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

package com.duckduckgo.duckchat.impl.ui

import com.duckduckgo.app.tabs.DuckChatLaunch
import com.duckduckgo.duckchat.impl.ui.nativeinput.views.shouldLaunchCameraCapture
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NativeInputModeWidgetCameraCaptureTest {

    @Test
    fun `launches camera for camera capture action in Duck ai mode`() {
        assertTrue(shouldLaunchCameraCapture(isDuckAiMode = true, launch = DuckChatLaunch.CameraCapture(), supportsImageUpload = true))
    }

    @Test
    fun `does not launch camera in browser mode`() {
        assertFalse(shouldLaunchCameraCapture(isDuckAiMode = false, launch = DuckChatLaunch.CameraCapture(), supportsImageUpload = true))
    }

    @Test
    fun `does not launch camera for other or no launch action`() {
        assertFalse(shouldLaunchCameraCapture(isDuckAiMode = true, launch = DuckChatLaunch.ImageGeneration(), supportsImageUpload = true))
        assertFalse(shouldLaunchCameraCapture(isDuckAiMode = true, launch = DuckChatLaunch.Open(), supportsImageUpload = true))
    }

    @Test
    fun `does not launch camera when image upload is not supported`() {
        assertFalse(shouldLaunchCameraCapture(isDuckAiMode = true, launch = DuckChatLaunch.CameraCapture(), supportsImageUpload = false))
    }
}
