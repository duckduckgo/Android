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

package com.duckduckgo.browsermode.impl.profile

import android.webkit.CookieManager
import com.duckduckgo.browsermode.api.BrowserMode
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertSame
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.stub

class CookieManagerProviderTest {

    private val profileManager: RealWebViewProfileManager = mock()
    private val testee = CookieManagerProvider(profileManager)

    @Test
    fun `forMode delegates to profile manager getCookieManager`() = runTest {
        val regularCookies: CookieManager = mock()
        val fireCookies: CookieManager = mock()
        profileManager.stub {
            onBlocking { getCookieManager(BrowserMode.REGULAR) }.thenReturn(regularCookies)
            onBlocking { getCookieManager(BrowserMode.FIRE) }.thenReturn(fireCookies)
        }

        assertSame(regularCookies, testee.forMode(BrowserMode.REGULAR))
        assertSame(fireCookies, testee.forMode(BrowserMode.FIRE))
    }
}
