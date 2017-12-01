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

package com.duckduckgo.app.global

import com.duckduckgo.app.global.UriString.Companion.sameOrSubdomain
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UriStringTest {

    @Test
    fun whenUrlsHaveSameDomainThenSameOrSubdomainIsTrue() {
        assertTrue(sameOrSubdomain("http://example.com/index.html", "http://example.com/home.html"))
    }

    @Test
    fun whenUrlIsSubdomainThenSameOrSubdomainIsTrue() {
        assertTrue(sameOrSubdomain("http://subdomain.example.com/index.html", "http://example.com/home.html"))
    }

    @Test
    fun whenUrlIsAParentDomainThenSameOrSubdomainIsFalse() {
        assertFalse(sameOrSubdomain("http://example.com/index.html", "http://parent.example.com/home.html"))
    }

    @Test
    fun whenChildUrlIsMalformedThenSameOrSubdomainIsFalse() {
        assertFalse(sameOrSubdomain("??.example.com/index.html", "http://example.com/home.html"))
    }

    @Test
    fun whenParentUrlIsMalformedThenSameOrSubdomainIsFalse() {
        assertFalse(sameOrSubdomain("http://example.com/index.html", "??.example.com/home.html"))
    }

}