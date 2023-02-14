/*
 * Copyright (c) 2021 DuckDuckGo
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

package com.duckduckgo.mobile.android.vpn.pixels

import com.duckduckgo.mobile.android.vpn.pixels.DeviceShieldPixelNames.ATP_DID_PRESS_NOTIFY_ME_BUTTON
import com.duckduckgo.mobile.android.vpn.pixels.DeviceShieldPixelNames.ATP_DID_PRESS_NOTIFY_ME_DISMISS_BUTTON
import org.junit.Assert.assertTrue
import org.junit.Test

class DeviceShieldPixelNamesTest {
    @Test
    fun allAppTrackingProtectionPixelsShallBePrefixed() {
        DeviceShieldPixelNames.values()
            // These 2 pixels have the same names across browser and AppTP and should not have the "m_atp" prefix.
            .filter { it != ATP_DID_PRESS_NOTIFY_ME_BUTTON && it != ATP_DID_PRESS_NOTIFY_ME_DISMISS_BUTTON }
            .map { it.pixelName }.forEach { pixel ->
                assertTrue(pixel.startsWith("m_atp"))
            }
    }
}
