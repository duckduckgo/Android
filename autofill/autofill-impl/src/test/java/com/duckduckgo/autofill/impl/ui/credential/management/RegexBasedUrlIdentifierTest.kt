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

package com.duckduckgo.autofill.impl.ui.credential.management

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RegexBasedUrlIdentifierTest {

    private val testee = RegexBasedUrlIdentifier()

    @Test
    fun whenUrlIsNullThenNotClassedAsUrl() {
        assertFalse(testee.isLikelyAUrl(null))
    }

    @Test
    fun whenUrlIsEmptyStringThenNotClassedAsUrl() {
        assertFalse(testee.isLikelyAUrl(""))
    }

    @Test
    fun whenUrlIsBlankStringThenNotClassedAsUrl() {
        assertFalse(testee.isLikelyAUrl("   "))
    }

    @Test
    fun whenUrlIsAnIpAddressThenIsClassedAsUrl() {
        assertTrue(testee.isLikelyAUrl("192.168.1.100"))
    }

    @Test
    fun whenUrlIsSimpleUrlThenIsClassedAsUrl() {
        assertTrue(testee.isLikelyAUrl("example.com"))
    }

    @Test
    fun whenUrlHasPortThenIsClassedAsUrl() {
        assertTrue(testee.isLikelyAUrl("example.com:1234"))
    }
}
