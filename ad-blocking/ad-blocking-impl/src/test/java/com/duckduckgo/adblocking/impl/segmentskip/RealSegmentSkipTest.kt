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

package com.duckduckgo.adblocking.impl.segmentskip

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
class RealSegmentSkipTest {

    private val feature = FakeFeatureToggleFactory.create(SegmentSkipFeature::class.java)
    private val segmentsProvider = FakeSegmentsProvider()
    private val userPreferences = FakeUserPreferences()
    private val gate = FakeHashPrefixGate()
    private val segmentSkip = RealSegmentSkip(feature, segmentsProvider, gate, userPreferences)

    @Before
    fun setup() {
        setEnabled(true)
    }

    @Test
    fun whenEnabledThenConfigIsEnabledWithManualSkipMode() {
        assertEquals(SegmentSkipConfig(enabled = true, skipMode = SegmentSkipSkipMode.MANUAL), segmentSkip.config())
    }

    @Test
    fun whenDisabledThenConfigIsNotEnabled() {
        setEnabled(false)

        assertFalse(segmentSkip.config().enabled)
    }

    @Test
    fun whenModeIsAlwaysSkipThenConfigIsEnabledWithAutoSkipMode() {
        userPreferences.mode.value = SegmentSkipMode.ALWAYS_SKIP

        assertEquals(SegmentSkipConfig(enabled = true, skipMode = SegmentSkipSkipMode.AUTO), segmentSkip.config())
    }

    @Test
    fun whenModeIsOffThenConfigIsNotEnabled() {
        userPreferences.mode.value = SegmentSkipMode.OFF

        assertFalse(segmentSkip.config().enabled)
    }

    @Test
    fun whenModeIsOffThenGetSegmentsReturnsEmptyWithoutLookup() = runTest {
        userPreferences.mode.value = SegmentSkipMode.OFF

        assertTrue(segmentSkip.getSegments(VALID_ID).isEmpty())
        assertTrue(segmentsProvider.requestedIds.isEmpty())
    }

    @Test
    fun whenValidIdThenReturnsSegmentsSortedByStart() = runTest {
        val late = SegmentSkipSegment(start = 60.0, end = 70.0, category = "sponsor", actionType = "skip", uuid = "late")
        val early = SegmentSkipSegment(start = 5.0, end = 15.0, category = "sponsor", actionType = "skip", uuid = "early")
        segmentsProvider.segments = listOf(late, early)

        assertEquals(listOf(early, late), segmentSkip.getSegments(VALID_ID))
        assertEquals(VALID_ID, segmentsProvider.requestedIds.single())
        assertEquals("5f6b0b4e", gate.checkedPrefixes.single())
        assertEquals("5f6b", segmentsProvider.requestedPrefixes.single())
    }

    @Test
    fun whenDatasetDoesNotContainVideoThenProviderIsNotCalled() = runTest {
        gate.hit = false
        assertTrue(segmentSkip.getSegments(VALID_ID).isEmpty())
        assertEquals(listOf("5f6b0b4e"), gate.checkedPrefixes)
        assertTrue(segmentsProvider.requestedIds.isEmpty())
    }

    @Test
    fun whenBlankIdThenReturnsEmptyWithoutLookup() = runTest {
        listOf("", "   ").forEach { id ->
            assertTrue(segmentSkip.getSegments(id).isEmpty())
        }
        assertTrue(segmentsProvider.requestedIds.isEmpty())
    }

    @Test
    fun whenDisabledThenGetSegmentsReturnsEmptyWithoutLookup() = runTest {
        setEnabled(false)

        assertTrue(segmentSkip.getSegments(VALID_ID).isEmpty())
        assertTrue(segmentsProvider.requestedIds.isEmpty())
    }

    @Test
    fun whenProviderThrowsThenReturnsEmpty() = runTest {
        segmentsProvider.error = RuntimeException("boom")

        assertTrue(segmentSkip.getSegments(VALID_ID).isEmpty())
    }

    @Test(expected = CancellationException::class)
    fun whenProviderIsCancelledThenCancellationPropagates() = runTest {
        segmentsProvider.error = CancellationException("cancelled")

        segmentSkip.getSegments(VALID_ID)
    }

    private fun setEnabled(enabled: Boolean) {
        feature.self().setRawStoredState(State(enable = true))
        feature.mvpEnabled().setRawStoredState(State(enable = enabled))
    }

    private class FakeUserPreferences : SegmentSkipUserPreferences {
        val mode = MutableStateFlow(SegmentSkipMode.ALWAYS_ASK)

        override fun modeFlow(): Flow<SegmentSkipMode> = mode
        override fun cachedMode(): SegmentSkipMode = mode.value
        override fun consentFlow(): Flow<Boolean> = kotlinx.coroutines.flow.flowOf(false)
        override fun cachedConsent(): Boolean = false
        override suspend fun setMode(mode: SegmentSkipMode, userConsent: Boolean): Boolean {
            val changed = this.mode.value != mode
            this.mode.value = mode
            return changed
        }
    }

    private class FakeHashPrefixGate : SegmentSkipHashPrefixGate {
        var hit = true
        val checkedPrefixes = mutableListOf<String>()
        override suspend fun hasSegments(hashPrefix: String): Boolean {
            checkedPrefixes += hashPrefix
            return hit
        }
    }

    private class FakeSegmentsProvider : SegmentSkipSegmentsProvider {
        var segments: List<SegmentSkipSegment> = emptyList()
        var error: Exception? = null
        val requestedIds = mutableListOf<String>()
        val requestedPrefixes = mutableListOf<String>()

        override fun clearCache() = Unit

        override suspend fun getSegments(videoId: String, hashPrefix: String): List<SegmentSkipSegment> {
            requestedIds += videoId
            requestedPrefixes += hashPrefix
            error?.let { throw it }
            return segments
        }
    }

    private companion object {
        const val VALID_ID = "dQw4w9WgXcQ"
    }
}
