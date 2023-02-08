/*
 * Copyright (c) 2019 DuckDuckGo
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

package com.duckduckgo.app.statistics.pixels

import com.duckduckgo.app.anr.AnrPixelName
import com.duckduckgo.app.pixels.AppPixelName
import com.duckduckgo.autofill.impl.pixel.AutofillPixelNames
import com.duckduckgo.mobile.android.vpn.pixels.DeviceShieldPixelNames
import com.duckduckgo.mobile.android.vpn.pixels.DeviceShieldPixelNames.ATP_DID_PRESS_NOTIFY_ME_BUTTON
import com.duckduckgo.mobile.android.vpn.pixels.DeviceShieldPixelNames.ATP_DID_PRESS_NOTIFY_ME_DISMISS_BUTTON
import org.junit.Assert.fail
import org.junit.Test

class PixelNameTest {

    @Test
    fun verifyNoDuplicatePixelNames() {
        val existingNames = mutableSetOf<String>()
        AppPixelName.values().forEach {
            if (!existingNames.add(it.pixelName)) {
                fail("Duplicate pixel name in AppPixelName: ${it.pixelName}")
            }
        }
        Pixel.StatisticsPixelName.values().forEach {
            if (!existingNames.add(it.pixelName)) {
                fail("Duplicate pixel name in StatisticsPixelName: ${it.pixelName}")
            }
        }
        DeviceShieldPixelNames.values().forEach {
            if (!existingNames.add(it.pixelName)) {
                // This if statement was added as we know these 2 pixels are duplicated in AppPixelName and DeviceShieldPixelNames.
                // These are temporary and the below condition will be removed along with the pixels.
                if (it != ATP_DID_PRESS_NOTIFY_ME_BUTTON && it != ATP_DID_PRESS_NOTIFY_ME_DISMISS_BUTTON) {
                    fail("Duplicate pixel name in DeviceShieldPixelNames: ${it.pixelName}")
                }
            }
        }
        AnrPixelName.values().forEach {
            if (!existingNames.add(it.pixelName)) {
                fail("Duplicate pixel name in AnrPixelName: ${it.pixelName}")
            }
        }
        AutofillPixelNames.values().forEach {
            if (!existingNames.add(it.pixelName)) {
                fail("Duplicate pixel name in AutofillPixelNames: ${it.pixelName}")
            }
        }
    }
}
