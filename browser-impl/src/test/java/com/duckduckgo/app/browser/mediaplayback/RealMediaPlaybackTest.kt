/*
 * Copyright (c) 2025 DuckDuckGo
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

package com.duckduckgo.app.browser.mediaplayback

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.duckduckgo.app.browser.mediaplayback.store.MediaPlaybackRepository
import com.duckduckgo.feature.toggles.api.FakeFeatureToggleFactory
import com.duckduckgo.feature.toggles.api.FeatureException
import com.duckduckgo.feature.toggles.api.Toggle
import junit.framework.TestCase.assertFalse
import junit.framework.TestCase.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.CopyOnWriteArrayList

@RunWith(AndroidJUnit4::class)
class RealMediaPlaybackTest {

    private lateinit var mediaPlaybackFeature: MediaPlaybackFeature
    private lateinit var mediaPlaybackRepository: FakeMediaPlaybackRepository
    private lateinit var realMediaPlayback: RealMediaPlayback

    @Before
    fun before() {
        mediaPlaybackFeature = FakeFeatureToggleFactory.create(MediaPlaybackFeature::class.java)
        mediaPlaybackRepository = FakeMediaPlaybackRepository()
        realMediaPlayback = RealMediaPlayback(mediaPlaybackFeature, mediaPlaybackRepository)
    }

    @Test
    fun `when url is exempted domain then return false`() {
        mediaPlaybackRepository.setExemptedDomains(listOf("duckduckgo.com"))

        assertFalse(realMediaPlayback.doesMediaPlaybackRequireUserGestureForUrl("https://duckduckgo.com"))
        assertFalse(realMediaPlayback.doesMediaPlaybackRequireUserGestureForUrl("https://www.duckduckgo.com"))
        assertFalse(realMediaPlayback.doesMediaPlaybackRequireUserGestureForUrl("https://subdomain.duckduckgo.com"))
    }

    @Test
    fun `when feature is enabled and url not in exceptions then return true`() {
        mediaPlaybackFeature.self().setRawStoredState(Toggle.State(enable = true, exceptions = emptyList()))
        mediaPlaybackRepository.setExceptions(listOf(FeatureException("foo.com", "reason")))

        assertTrue(realMediaPlayback.doesMediaPlaybackRequireUserGestureForUrl("https://example.com"))
    }

    @Test
    fun `when feature is enabled and url in exceptions then return false`() {
        mediaPlaybackFeature.self().setRawStoredState(Toggle.State(enable = true, exceptions = emptyList()))
        mediaPlaybackRepository.setExceptions(listOf(FeatureException("example.com", "reason")))

        assertFalse(realMediaPlayback.doesMediaPlaybackRequireUserGestureForUrl("https://example.com"))
        assertFalse(realMediaPlayback.doesMediaPlaybackRequireUserGestureForUrl("https://example.com/path?query=value"))
    }

    @Test
    fun `when feature is disabled then return false`() {
        mediaPlaybackFeature.self().setRawStoredState(Toggle.State(enable = false, exceptions = emptyList()))
        mediaPlaybackRepository.setExceptions(emptyList())

        assertFalse(realMediaPlayback.doesMediaPlaybackRequireUserGestureForUrl("https://example.com"))
    }

    @Test
    fun `when domain is in exceptions and is subdomain then return true`() {
        mediaPlaybackFeature.self().setRawStoredState(Toggle.State(enable = true, exceptions = emptyList()))
        mediaPlaybackRepository.setExceptions(listOf(FeatureException("example.com", "reason")))

        assertTrue(realMediaPlayback.doesMediaPlaybackRequireUserGestureForUrl("https://subdomain.example.com"))
    }

    private class FakeMediaPlaybackRepository : MediaPlaybackRepository {
        private val _exceptions = CopyOnWriteArrayList<FeatureException>()
        private val _exemptedDomains = CopyOnWriteArrayList<String>()

        override val exceptions: CopyOnWriteArrayList<FeatureException>
            get() = _exceptions

        override val exemptedDomains: CopyOnWriteArrayList<String>
            get() = _exemptedDomains

        fun setExceptions(exceptions: List<FeatureException>) {
            _exceptions.clear()
            _exceptions.addAll(exceptions)
        }

        fun setExemptedDomains(domains: List<String>) {
            _exemptedDomains.clear()
            _exemptedDomains.addAll(domains)
        }
    }
}
