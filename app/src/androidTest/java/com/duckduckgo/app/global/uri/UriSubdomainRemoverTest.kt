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

package com.duckduckgo.app.global.uri

import android.net.Uri
import androidx.core.net.toUri
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class UriSubdomainRemoverTest {

    @Test
    fun whenRemovingASubdomainWhenTwoAvailableThenOneIsReturned() {
        val converted = Uri.parse("https://a.example.com").removeSubdomain()
        assertEquals("https://example.com", converted)
    }

    @Test
    fun whenRemovingASubdomainWhenFiveAvailableThenFourAreReturned() {
        val converted = Uri.parse("https://a.b.c.d.example.com").removeSubdomain()
        assertEquals("https://b.c.d.example.com", converted)
    }

    @Test
    fun whenRemovingMultipleSubdomainCanKeepCalling() {
        val converted = Uri.parse("https://a.b.c.d.example.com")
            .removeSubdomain()!!
            .toUri().removeSubdomain()!!
            .toUri().removeSubdomain()
        assertEquals("https://d.example.com", converted)
    }

    @Test
    fun whenRemovingASubdomainWhenOnlyOneExistsThenReturnsNull() {
        val converted = Uri.parse("https://example.com").removeSubdomain()
        assertNull(converted)
    }

    @Test
    fun whenRemovingASubdomainWhenOnlyOneExistsButHasMultipartTldCoUkThenReturnsNull() {
        val converted = Uri.parse("https://co.uk").removeSubdomain()
        assertNull(converted)
    }

    @Test
    fun whenRemovingASubdomainWhenOnlyOneExistsButHasMultipartTldCoNzThenReturnsMultipartTld() {
        val converted = Uri.parse("https://co.za").removeSubdomain()
        assertNull(converted)
    }

    @Test
    fun whenRemovingASubdomainWhenOnlyOneExistsButHasRecentTldThenReturnsNull() {
        val converted = Uri.parse("https://example.dev").removeSubdomain()
        assertNull(converted)
    }

    @Test
    fun whenRemovingASubdomainWhenOnlyOneExistsButHasUnknownTldThenReturnsNull() {
        val converted = Uri.parse("https://example.nonexistent").removeSubdomain()
        assertNull(converted)
    }

    @Test
    fun whenRemovingASubdomainWhenUnknownTldThenReturnsNonExistantTld() {
        val converted = Uri.parse("https://foo.example.nonexistent").removeSubdomain()
        assertEquals("https://example.nonexistent", converted)
    }

    @Test
    fun whenRemovingSubdomainWhenUriIpAddressThenReturnsNull() {
        val converted = Uri.parse("127.0.0.1").removeSubdomain()
        assertNull(converted)
    }
}