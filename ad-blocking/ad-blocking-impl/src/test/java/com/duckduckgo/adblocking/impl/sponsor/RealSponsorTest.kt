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

package com.duckduckgo.adblocking.impl.sponsor

import android.annotation.SuppressLint
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.duckduckgo.feature.toggles.api.FakeFeatureToggleFactory
import com.duckduckgo.feature.toggles.api.Toggle.State
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@SuppressLint("DenyListedApi") // setRawStoredState
@RunWith(AndroidJUnit4::class)
class RealSponsorTest {

    private val feature = FakeFeatureToggleFactory.create(SponsorFeature::class.java)
    private val segmentsProvider = FakeSegmentsProvider()
    private val userPreferences = FakeUserPreferences()
    private val sponsor = RealSponsor(feature, segmentsProvider, userPreferences)

    @Before
    fun setup() {
        setEnabled(true)
    }

    @Test
    fun whenEnabledThenConfigIsEnabledWithManualSkipMode() {
        assertEquals(SponsorConfig(enabled = true, skipMode = SponsorSkipMode.MANUAL), sponsor.config())
    }

    @Test
    fun whenDisabledThenConfigIsNotEnabled() {
        setEnabled(false)

        assertFalse(sponsor.config().enabled)
    }

    @Test
    fun whenModeIsAlwaysSkipThenConfigIsEnabledWithAutoSkipMode() {
        userPreferences.mode.value = SponsorMode.ALWAYS_SKIP

        assertEquals(SponsorConfig(enabled = true, skipMode = SponsorSkipMode.AUTO), sponsor.config())
    }

    @Test
    fun whenModeIsOffThenConfigIsNotEnabled() {
        userPreferences.mode.value = SponsorMode.OFF

        assertFalse(sponsor.config().enabled)
    }

    @Test
    fun whenModeIsOffThenGetSegmentsReturnsEmptyWithoutLookup() = runTest {
        userPreferences.mode.value = SponsorMode.OFF

        assertTrue(sponsor.getSegments(VALID_ID).isEmpty())
        assertTrue(segmentsProvider.requestedIds.isEmpty())
    }

    @Test
    fun whenValidIdThenReturnsSegmentsSortedByStart() = runTest {
        val late = SponsorSegment(start = 60.0, end = 70.0, category = "sponsor", actionType = "skip", uuid = "late")
        val early = SponsorSegment(start = 5.0, end = 15.0, category = "sponsor", actionType = "skip", uuid = "early")
        segmentsProvider.segments = listOf(late, early)

        assertEquals(listOf(early, late), sponsor.getSegments(VALID_ID))
        assertEquals(VALID_ID, segmentsProvider.requestedIds.single())
    }

    @Test
    fun whenBlankIdThenReturnsEmptyWithoutLookup() = runTest {
        listOf("", "   ").forEach { id ->
            assertTrue(sponsor.getSegments(id).isEmpty())
        }
        assertTrue(segmentsProvider.requestedIds.isEmpty())
    }

    @Test
    fun whenDisabledThenGetSegmentsReturnsEmptyWithoutLookup() = runTest {
        setEnabled(false)

        assertTrue(sponsor.getSegments(VALID_ID).isEmpty())
        assertTrue(segmentsProvider.requestedIds.isEmpty())
    }

    @Test
    fun whenProviderThrowsThenReturnsEmpty() = runTest {
        segmentsProvider.error = RuntimeException("boom")

        assertTrue(sponsor.getSegments(VALID_ID).isEmpty())
    }

    @Test(expected = CancellationException::class)
    fun whenProviderIsCancelledThenCancellationPropagates() = runTest {
        segmentsProvider.error = CancellationException("cancelled")

        sponsor.getSegments(VALID_ID)
    }

    private fun setEnabled(enabled: Boolean) {
        feature.self().setRawStoredState(State(enable = true))
        feature.mvpEnabled().setRawStoredState(State(enable = enabled))
    }

    private class FakeUserPreferences : SponsorUserPreferences {
        val mode = MutableStateFlow(SponsorMode.ALWAYS_ASK)

        override fun modeFlow(): Flow<SponsorMode> = mode
        override fun cachedMode(): SponsorMode = mode.value
        override suspend fun setMode(mode: SponsorMode) {
            this.mode.value = mode
        }
    }

    private class FakeSegmentsProvider : SponsorSegmentsProvider {
        var segments: List<SponsorSegment> = emptyList()
        var error: Exception? = null
        val requestedIds = mutableListOf<String>()

        override suspend fun getSegments(videoId: String): List<SponsorSegment> {
            requestedIds += videoId
            error?.let { throw it }
            return segments
        }
    }

    private companion object {
        const val VALID_ID = "dQw4w9WgXcQ"
    }
}
