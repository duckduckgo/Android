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

package com.duckduckgo.app.trackerdetection

import android.net.Uri
import androidx.core.net.toUri
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.duckduckgo.app.pixels.remoteconfig.CachedEntityLookupRCWrapper
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import javax.inject.Provider

@RunWith(AndroidJUnit4::class)
class EntityLookupDelegateTest {

    private val mockLegacy: TdsEntityLookup = mock()
    private val mockCached: CachedTdsEntityLookup = mock()

    private fun delegateWithFlag(enabled: Boolean) = EntityLookupDelegate(
        legacy = Provider { mockLegacy },
        cached = Provider { mockCached },
        flag = object : CachedEntityLookupRCWrapper {
            override val enabled: Boolean = enabled
        },
    )

    @Test
    fun whenFlagOnRefreshForwardsToCached() {
        val testee = delegateWithFlag(enabled = true)

        testee.refresh()

        verify(mockCached).refresh()
        verify(mockLegacy, never()).refresh()
    }

    @Test
    fun whenFlagOffRefreshForwardsToLegacy() {
        val testee = delegateWithFlag(enabled = false)

        testee.refresh()

        verify(mockLegacy).refresh()
        verify(mockCached, never()).refresh()
    }

    @Test
    fun whenFlagChangesMidSessionDelegateKeepsFirstImpl() {
        var enabled = true
        val flag = object : CachedEntityLookupRCWrapper {
            override val enabled: Boolean get() = enabled
        }
        val testee = EntityLookupDelegate(
            legacy = Provider { mockLegacy },
            cached = Provider { mockCached },
            flag = flag,
        )

        testee.refresh()
        enabled = false
        testee.refresh()

        verify(mockCached, times(2)).refresh()
        verify(mockLegacy, never()).refresh()
    }

    @Test
    fun whenEntityForUrlStringWithFlagOnForwardsToCached() {
        val testee = delegateWithFlag(enabled = true)

        testee.entityForUrl("http://tracker.com")

        verify(mockCached).entityForUrl("http://tracker.com")
        verify(mockLegacy, never()).entityForUrl(any<String>())
    }

    @Test
    fun whenEntityForUrlUriWithFlagOffForwardsToLegacy() {
        val testee = delegateWithFlag(enabled = false)
        val uri = "http://tracker.com".toUri()

        testee.entityForUrl(uri)

        verify(mockLegacy).entityForUrl(uri)
        verify(mockCached, never()).entityForUrl(any<Uri>())
    }

    @Test
    fun whenEntityForNameWithFlagOnForwardsToCached() {
        val testee = delegateWithFlag(enabled = true)

        testee.entityForName("Acme")

        verify(mockCached).entityForName("Acme")
        verify(mockLegacy, never()).entityForName(any())
    }
}
