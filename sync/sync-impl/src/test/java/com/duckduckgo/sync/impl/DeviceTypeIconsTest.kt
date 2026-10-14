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

package com.duckduckgo.sync.impl

import org.junit.Assert.assertEquals
import org.junit.Test

class DeviceTypeIconsTest {

    @Test
    fun whenBrowserThenListIconIsGlobe() {
        assertEquals(com.duckduckgo.mobile.android.R.drawable.ic_globe_24, DeviceType("Browser").iconRes())
    }

    @Test
    fun whenPhoneThenListIconIsMobile() {
        assertEquals(R.drawable.ic_device_mobile_24, DeviceType("phone").iconRes())
    }

    @Test
    fun whenDesktopThenListIconIsDesktop() {
        assertEquals(R.drawable.ic_device_desktop_24, DeviceType("desktop").iconRes())
    }

    @Test
    fun whenUnknownThenListIconIsGeneric() {
        assertEquals(com.duckduckgo.mobile.android.R.drawable.ic_device_all_24, DeviceType("").iconRes())
    }

    @Test
    fun whenBrowserThenHeaderImageIsBrowser() {
        assertEquals(R.drawable.browser_v2_synced_feature_128, DeviceType("Browser").headerImageRes())
    }

    @Test
    fun whenPhoneThenHeaderImageIsMobile() {
        assertEquals(R.drawable.ic_header_synced_device_mobile, DeviceType("phone").headerImageRes())
    }

    @Test
    fun whenDesktopThenHeaderImageIsDesktop() {
        assertEquals(R.drawable.ic_header_synced_device_desktop, DeviceType("desktop").headerImageRes())
    }

    @Test
    fun whenUnknownThenHeaderImageIsMobile() {
        assertEquals(R.drawable.ic_header_synced_device_mobile, DeviceType("").headerImageRes())
    }
}
