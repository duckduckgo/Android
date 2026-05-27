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

package com.duckduckgo.app.browser.mode

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.duckduckgo.browsermode.api.BrowserMode
import com.duckduckgo.browsermode.api.FireModeAvailability
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

@RunWith(AndroidJUnit4::class)
class WebStorageProviderTest {

    private val fireModeAvailability: FireModeAvailability = mock()
    private val testee = WebStorageProvider(fireModeAvailability)

    @Test
    fun `forMode returns default WebStorage when multi-profile is unavailable`() {
        whenever(fireModeAvailability.isAvailable()).thenReturn(false)

        assertNotNull(testee.forMode(BrowserMode.REGULAR))
        assertNotNull(testee.forMode(BrowserMode.FIRE))
    }
}
