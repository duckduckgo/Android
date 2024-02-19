/*
 * Copyright (c) 2023 DuckDuckGo
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

package com.duckduckgo.autofill.impl.encoding

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class UrlUnicodeNormalizerImplTest {

    private val testee = UrlUnicodeNormalizerImpl()

    @Test
    fun whenNormalizingToAsciiAndContainsNonAsciiThenOutputIdnaEncoded() {
        assertEquals("xn--7ca.com", testee.normalizeAscii("ç.com"))
    }

    @Test
    fun whenNormalizingToAsciiAndOnlyContainsAsciiThenThenInputAndOutputIdentical() {
        assertEquals("c.com", testee.normalizeAscii("c.com"))
    }

    @Test
    fun whenNormalizingToUnicodeAndContainsNonAsciiThenOutputContainsNonAscii() {
        assertEquals("ç.com", testee.normalizeUnicode("xn--7ca.com"))
    }

    @Test
    fun whenNormalizingToUnicodeAndOnlyContainsAsciiThenThenInputAndOutputIdentical() {
        assertEquals("c.com", testee.normalizeUnicode("c.com"))
    }
}
