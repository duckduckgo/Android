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

package com.duckduckgo.adblocking.impl

import androidx.core.net.toUri
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RealAdBlockingExtensionDomainMatcherTest {

    private val matcher = RealAdBlockingExtensionDomainMatcher()

    @Test
    fun whenUrlIsYouTubeThenMatches() {
        assertTrue(matcher.matches("https://youtube.com/watch?v=123"))
    }

    @Test
    fun whenUrlIsWwwYouTubeThenMatches() {
        assertTrue(matcher.matches("https://www.youtube.com/watch?v=123"))
    }

    @Test
    fun whenUrlIsMobileYouTubeThenMatches() {
        assertTrue(matcher.matches("https://m.youtube.com/watch?v=123"))
    }

    @Test
    fun whenUrlIsYouTubeNoCookieThenMatches() {
        assertTrue(matcher.matches("https://youtube-nocookie.com/embed/123"))
    }

    @Test
    fun whenUrlIsWwwYouTubeNoCookieThenMatches() {
        assertTrue(matcher.matches("https://www.youtube-nocookie.com/embed/123"))
    }

    @Test
    fun whenUrlIsNonMatchingHostThenDoesNotMatch() {
        assertFalse(matcher.matches("https://example.com/watch?v=123"))
    }

    @Test
    fun whenUrlOnlyContainsDomainAsPrefixThenDoesNotMatch() {
        assertFalse(matcher.matches("https://youtube.com.evil.com/watch?v=123"))
    }

    @Test
    fun whenUrlOnlyContainsDomainAsSuffixThenDoesNotMatch() {
        assertFalse(matcher.matches("https://notyoutube.com/watch?v=123"))
    }

    @Test
    fun whenUrlIsNullThenDoesNotMatch() {
        assertFalse(matcher.matches(null))
    }

    @Test
    fun whenUrlIsMalformedThenDoesNotMatch() {
        assertFalse(matcher.matches("not a url"))
    }

    @Test
    fun whenUriIsYouTubeThenMatches() {
        assertTrue(matcher.matches("https://www.youtube.com/watch?v=123".toUri()))
    }

    @Test
    fun whenUriIsNonMatchingHostThenDoesNotMatch() {
        assertFalse(matcher.matches("https://example.com/watch?v=123".toUri()))
    }
}
