/*
 * Copyright (c) 2022 DuckDuckGo
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

package com.duckduckgo.app.browser

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

@RunWith(AndroidJUnit4::class)
class MajorWebViewVersionProviderTest {
    private val rawWebViewVersionProvider: RawWebViewVersionProvider = mock()
    private val testee = MajorWebViewVersionProvider(
        rawWebViewVersionProvider,
    )

    @Test
    fun whenWebViewVersionIsEmptyThenReturnUnknown() {
        whenever(rawWebViewVersionProvider.get()).thenReturn("")

        assertEquals("unknown", testee.get())
    }

    @Test
    fun whenWebViewVersionIsNullThenReturnUnknown() {
        whenever(rawWebViewVersionProvider.get()).thenReturn(null)

        assertEquals("unknown", testee.get())
    }

    @Test
    fun whenWebViewVersionAvailableThenReturnMajorVersionOnly() {
        whenever(rawWebViewVersionProvider.get()).thenReturn("91.1.12.1234.423")

        assertEquals("91", testee.get())
    }
}
