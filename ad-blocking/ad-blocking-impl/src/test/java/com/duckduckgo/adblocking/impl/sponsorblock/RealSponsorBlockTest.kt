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

package com.duckduckgo.adblocking.impl.sponsorblock

import android.annotation.SuppressLint
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.duckduckgo.feature.toggles.api.FakeFeatureToggleFactory
import com.duckduckgo.feature.toggles.api.Toggle.State
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@SuppressLint("DenyListedApi") // setRawStoredState
@RunWith(AndroidJUnit4::class)
class RealSponsorBlockTest {

    private val feature = FakeFeatureToggleFactory.create(SponsorBlockFeature::class.java)
    private val segmentsProvider = FakeSegmentsProvider()
    private val sponsorBlock = RealSponsorBlock(feature, segmentsProvider)

    @Before
    fun setup() {
        setEnabled(true)
    }

    @Test
    fun whenEnabledThenConfigIsEnabledWithManualSkipMode() {
        assertEquals(SponsorBlockConfig(enabled = true, skipMode = SponsorBlockSkipMode.MANUAL), sponsorBlock.config())
    }

    @Test
    fun whenDisabledThenConfigIsNotEnabled() {
        setEnabled(false)

        assertFalse(sponsorBlock.config().enabled)
    }

    @Test
    fun whenValidIdThenReturnsSegmentsSortedByStart() = runTest {
        val late = SponsorBlockSegment(start = 60.0, end = 70.0, category = "sponsor", actionType = "skip")
        val early = SponsorBlockSegment(start = 5.0, end = 15.0, category = "sponsor", actionType = "skip")
        segmentsProvider.segments = listOf(late, early)

        assertEquals(listOf(early, late), sponsorBlock.getSegments(VALID_ID))
        assertEquals(VALID_ID, segmentsProvider.requestedIds.single())
    }

    @Test
    fun whenMalformedIdThenReturnsEmptyWithoutLookup() = runTest {
        listOf("", "short", "dQw4w9WgXcQX", "dQw4w9WgXc!", "../../etc/pa").forEach { id ->
            assertTrue(sponsorBlock.getSegments(id).isEmpty())
        }
        assertTrue(segmentsProvider.requestedIds.isEmpty())
    }

    @Test
    fun whenDisabledThenGetSegmentsReturnsEmptyWithoutLookup() = runTest {
        setEnabled(false)

        assertTrue(sponsorBlock.getSegments(VALID_ID).isEmpty())
        assertTrue(segmentsProvider.requestedIds.isEmpty())
    }

    @Test
    fun whenProviderThrowsThenReturnsEmpty() = runTest {
        segmentsProvider.error = RuntimeException("boom")

        assertTrue(sponsorBlock.getSegments(VALID_ID).isEmpty())
    }

    @Test(expected = CancellationException::class)
    fun whenProviderIsCancelledThenCancellationPropagates() = runTest {
        segmentsProvider.error = CancellationException("cancelled")

        sponsorBlock.getSegments(VALID_ID)
    }

    private fun setEnabled(enabled: Boolean) {
        feature.self().setRawStoredState(State(enable = true))
        feature.mvpEnabled().setRawStoredState(State(enable = enabled))
    }

    private class FakeSegmentsProvider : SponsorBlockSegmentsProvider {
        var segments: List<SponsorBlockSegment> = emptyList()
        var error: Exception? = null
        val requestedIds = mutableListOf<String>()

        override suspend fun getSegments(videoId: String): List<SponsorBlockSegment> {
            requestedIds += videoId
            error?.let { throw it }
            return segments
        }
    }

    private companion object {
        const val VALID_ID = "dQw4w9WgXcQ"
    }
}
