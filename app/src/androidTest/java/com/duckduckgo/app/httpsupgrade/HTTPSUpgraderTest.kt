/*
 * Copyright (c) 2017 DuckDuckGo
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

package com.duckduckgo.app.httpsupgrade

import android.net.Uri
import com.duckduckgo.app.httpsupgrade.db.HTTPSUpgradeDomainDAO
import com.nhaarman.mockito_kotlin.mock
import com.nhaarman.mockito_kotlin.whenever
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class HTTPSUpgraderTest {

    lateinit var testee: HTTPSUpgrader
    lateinit var mockDao: HTTPSUpgradeDomainDAO

    @Before
    fun before() {
        mockDao = mock()
        testee = HTTPSUpgrader(mockDao)
    }

    @Test
    fun whenGivenUriItIsUpgradedToHttps() {
        val input = Uri.parse("http://www.example.com/some/path/to/a/file.txt")
        val expected = Uri.parse("https://www.example.com/some/path/to/a/file.txt")
        assertEquals(expected, testee.upgrade(input))
    }

    @Test
    fun whenUriIsHttpAndInUpgradeListThenShouldUpgrade() {
        whenever(mockDao.contains("www.example.com")).thenReturn(true)
        assertTrue(testee.shouldUpgrade(Uri.parse("http://www.example.com")))
    }

    @Test
    fun whenUriIsHttpAndIsNotInUpgradeListThenShouldNotUpgrade() {
        assertFalse(testee.shouldUpgrade(Uri.parse("http://www.example.com")))
    }

    @Test
    fun whenUriIsHttpsThenShouldNotUpgrade() {
        assertFalse(testee.shouldUpgrade(Uri.parse("https://www.example.com")))
    }

}
