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

import android.webkit.WebStorage
import com.duckduckgo.browsermode.api.BrowserMode
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertSame
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.stub

class WebStorageProviderTest {

    private val profileManager: RealWebViewProfileManager = mock()
    private val testee = WebStorageProvider(profileManager)

    @Test
    fun `forMode delegates to profile manager getWebStorage`() = runTest {
        val regularStorage: WebStorage = mock()
        val fireStorage: WebStorage = mock()
        profileManager.stub {
            onBlocking { getWebStorage(BrowserMode.REGULAR) }.thenReturn(regularStorage)
            onBlocking { getWebStorage(BrowserMode.FIRE) }.thenReturn(fireStorage)
        }

        assertSame(regularStorage, testee.forMode(BrowserMode.REGULAR))
        assertSame(fireStorage, testee.forMode(BrowserMode.FIRE))
    }
}
