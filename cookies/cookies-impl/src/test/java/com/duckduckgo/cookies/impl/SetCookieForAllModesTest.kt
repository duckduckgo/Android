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

package com.duckduckgo.cookies.impl

import android.webkit.CookieManager
import com.duckduckgo.browsermode.api.BrowserMode
import com.duckduckgo.cookies.api.CookieManagerProvider
import com.duckduckgo.cookies.api.setCookieForAllModes
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class SetCookieForAllModesTest {

    private val regularManager: CookieManager = mock()
    private val fireManager: CookieManager = mock()
    private val provider: CookieManagerProvider = mock()

    @Test
    fun whenEachModeHasADistinctManagerThenEachIsWrittenAndFlushedOnce() {
        whenever(provider.forMode(BrowserMode.REGULAR)).thenReturn(regularManager)
        whenever(provider.forMode(BrowserMode.FIRE)).thenReturn(fireManager)

        provider.setCookieForAllModes(URL, COOKIE)

        verify(regularManager).setCookie(URL, COOKIE)
        verify(regularManager).flush()
        verify(fireManager).setCookie(URL, COOKIE)
        verify(fireManager).flush()
    }

    @Test
    fun whenAllModesResolveToTheSameManagerThenItIsWrittenAndFlushedOnce() {
        // Fire unavailable / cold off-main: forMode(FIRE) == forMode(REGULAR), so distinct() must collapse to one write.
        whenever(provider.forMode(any())).thenReturn(regularManager)

        provider.setCookieForAllModes(URL, COOKIE)

        verify(regularManager, times(1)).setCookie(URL, COOKIE)
        verify(regularManager, times(1)).flush()
    }

    @Test
    fun whenAModeResolvesToNullThenItIsSkippedAndTheRestAreStillWritten() {
        whenever(provider.forMode(BrowserMode.REGULAR)).thenReturn(regularManager)
        whenever(provider.forMode(BrowserMode.FIRE)).thenReturn(null)

        provider.setCookieForAllModes(URL, COOKIE)

        verify(regularManager).setCookie(URL, COOKIE)
        verify(regularManager).flush()
        verify(fireManager, never()).setCookie(any(), any())
    }

    companion object {
        private const val URL = "https://example.com"
        private const val COOKIE = "name=value"
    }
}
