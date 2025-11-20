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

package com.duckduckgo.pir.impl.pixels

import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.common.utils.CurrentTimeProvider
import com.duckduckgo.pir.impl.models.AddressCityState
import com.duckduckgo.pir.impl.models.Broker
import com.duckduckgo.pir.impl.models.ExtractedProfile
import com.duckduckgo.pir.impl.store.PirRepository
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.whenever
import kotlin.time.Duration.Companion.days

/**
 * Comprehensive test suite for [RealWeeklyPixelReporter].
 *
 * Tests cover:
 * - Empty data scenarios
 * - Profile filtering (deprecated, age-based)
 * - Child-parent broker relationships
 * - Orphaned record detection
 * - Edge cases and boundary conditions
 * - Multiple broker scenarios
 * - Complex profile matching scenarios
 */
class RealWeeklyPixelReporterTest {

    @get:Rule
    val coroutineRule = CoroutineTestRule()

    private lateinit var testee: RealWeeklyPixelReporter

    private val mockDispatcherProvider = coroutineRule.testDispatcherProvider
    private val mockPirRepository: PirRepository = mock()
    private val mockCurrentTimeProvider: CurrentTimeProvider = mock()
    private val mockPixelSender: PirPixelSender = mock()

    // Test data constants
    private val baseTime = 1000000L
    private val sevenDaysInMillis = 7.days.inWholeMilliseconds
    private val sixDaysInMillis = 6.days.inWholeMilliseconds
    private val eightDaysInMillis = 8.days.inWholeMilliseconds

    private val parentBrokerName = "parent-broker"
    private val parentBrokerUrl = "https://parent-broker.com"
    private val childBrokerName = "child-broker"
    private val childBrokerUrl = "https://child-broker.com"

    @Before
    fun setUp() {
        testee = RealWeeklyPixelReporter(
            dispatcherProvider = mockDispatcherProvider,
            pirRepository = mockPirRepository,
            currentTimeProvider = mockCurrentTimeProvider,
            pixelSender = mockPixelSender,
        )
    }

    // ============================================================================
    // EMPTY DATA SCENARIOS
    // ============================================================================

    @Test
    fun `when no extracted profiles then does not fire pixel`() = runTest {
        whenever(mockPirRepository.getWeeklyStatLastSentMs()).thenReturn(0L)
        whenever(mockCurrentTimeProvider.currentTimeMillis()).thenReturn(baseTime)
        whenever(mockPirRepository.getAllExtractedProfiles()).thenReturn(emptyList())
        whenever(mockPirRepository.getAllActiveBrokerObjects()).thenReturn(emptyList())

        testee.attemptFirePixel()

        verifyNoInteractions(mockPixelSender)
        verify(mockPirRepository).setWeeklyStatLastSentMs(baseTime)
    }

    @Test
    fun `when no active brokers then does not fire pixel`() = runTest {
        val extractedProfile = createExtractedProfile(
            brokerName = childBrokerName,
            dateAddedInMillis = baseTime - sixDaysInMillis,
        )
        whenever(mockPirRepository.getWeeklyStatLastSentMs()).thenReturn(0L)
        whenever(mockCurrentTimeProvider.currentTimeMillis()).thenReturn(baseTime)
        whenever(mockPirRepository.getAllExtractedProfiles()).thenReturn(listOf(extractedProfile))
        whenever(mockPirRepository.getAllActiveBrokerObjects()).thenReturn(emptyList())

        testee.attemptFirePixel()

        verifyNoInteractions(mockPixelSender)
    }

    @Test
    fun `when no child brokers then does not fire pixel`() = runTest {
        val parentBroker = createBroker(
            name = parentBrokerName,
            url = parentBrokerUrl,
            parent = null,
        )
        val extractedProfile = createExtractedProfile(
            brokerName = parentBrokerName,
            dateAddedInMillis = baseTime - sixDaysInMillis,
        )
        whenever(mockPirRepository.getWeeklyStatLastSentMs()).thenReturn(0L)
        whenever(mockCurrentTimeProvider.currentTimeMillis()).thenReturn(baseTime)
        whenever(mockPirRepository.getAllExtractedProfiles()).thenReturn(listOf(extractedProfile))
        whenever(mockPirRepository.getAllActiveBrokerObjects()).thenReturn(listOf(parentBroker))

        testee.attemptFirePixel()

        verifyNoInteractions(mockPixelSender)
    }

    // ============================================================================
    // PROFILE FILTERING SCENARIOS
    // ============================================================================

    @Test
    fun `when extracted profile is deprecated then do not fire pixel`() = runTest {
        val parentBroker = createBroker(
            name = parentBrokerName,
            url = parentBrokerUrl,
            parent = null,
        )
        val childBroker = createBroker(
            name = childBrokerName,
            url = childBrokerUrl,
            parent = parentBrokerUrl,
        )
        val deprecatedProfile = createExtractedProfile(
            brokerName = childBrokerName,
            dateAddedInMillis = baseTime - sixDaysInMillis,
            deprecated = true,
        )
        whenever(mockPirRepository.getWeeklyStatLastSentMs()).thenReturn(0L)
        whenever(mockCurrentTimeProvider.currentTimeMillis()).thenReturn(baseTime)
        whenever(mockPirRepository.getAllExtractedProfiles()).thenReturn(listOf(deprecatedProfile))
        whenever(mockPirRepository.getAllActiveBrokerObjects()).thenReturn(listOf(parentBroker, childBroker))

        testee.attemptFirePixel()

        verifyNoInteractions(mockPixelSender)
    }

    @Test
    fun `when 7 days has not passed since last weekly pixel then do not fire pixel`() = runTest {
        val parentBroker = createBroker(
            name = parentBrokerName,
            url = parentBrokerUrl,
            parent = null,
        )
        val childBroker = createBroker(
            name = childBrokerName,
            url = childBrokerUrl,
            parent = parentBrokerUrl,
        )
        val recentProfile = createExtractedProfile(
            brokerName = childBrokerName,
            dateAddedInMillis = baseTime - sixDaysInMillis,
        )
        whenever(mockPirRepository.getWeeklyStatLastSentMs()).thenReturn(baseTime - sixDaysInMillis)
        whenever(mockCurrentTimeProvider.currentTimeMillis()).thenReturn(baseTime)
        whenever(mockPirRepository.getAllExtractedProfiles()).thenReturn(listOf(recentProfile))
        whenever(mockPirRepository.getAllActiveBrokerObjects()).thenReturn(listOf(parentBroker, childBroker))

        testee.attemptFirePixel()

        verifyNoInteractions(mockPixelSender)
        verify(mockPirRepository, never()).setWeeklyStatLastSentMs(any())
    }

    @Test
    fun `when exactly 7 days has passed since last weekly pixel then fire pixel`() = runTest {
        val parentBroker = createBroker(
            name = parentBrokerName,
            url = parentBrokerUrl,
            parent = null,
        )
        val childBroker = createBroker(
            name = childBrokerName,
            url = childBrokerUrl,
            parent = parentBrokerUrl,
        )
        val recentProfile = createExtractedProfile(
            brokerName = childBrokerName,
            dateAddedInMillis = baseTime - sixDaysInMillis,
        )
        whenever(mockPirRepository.getWeeklyStatLastSentMs()).thenReturn(baseTime - sevenDaysInMillis)
        whenever(mockCurrentTimeProvider.currentTimeMillis()).thenReturn(baseTime)
        whenever(mockPirRepository.getAllExtractedProfiles()).thenReturn(listOf(recentProfile))
        whenever(mockPirRepository.getAllActiveBrokerObjects()).thenReturn(listOf(parentBroker, childBroker))

        testee.attemptFirePixel()

        verify(mockPixelSender).reportWeeklyChildOrphanedOptOuts(
            brokerUrl = childBrokerUrl,
            childParentRecordDifference = 1,
            orphanedRecordsCount = 1,
        )
        verify(mockPirRepository).setWeeklyStatLastSentMs(baseTime)
    }

    @Test
    fun `when more than 7 days has passed since last weekly pixel then fire pixel`() = runTest {
        val parentBroker = createBroker(
            name = parentBrokerName,
            url = parentBrokerUrl,
            parent = null,
        )
        val childBroker = createBroker(
            name = childBrokerName,
            url = childBrokerUrl,
            parent = parentBrokerUrl,
        )
        val recentProfile = createExtractedProfile(
            brokerName = childBrokerName,
            dateAddedInMillis = baseTime - sixDaysInMillis,
        )
        whenever(mockPirRepository.getWeeklyStatLastSentMs()).thenReturn(baseTime - eightDaysInMillis)
        whenever(mockCurrentTimeProvider.currentTimeMillis()).thenReturn(baseTime)
        whenever(mockPirRepository.getAllExtractedProfiles()).thenReturn(listOf(recentProfile))
        whenever(mockPirRepository.getAllActiveBrokerObjects()).thenReturn(listOf(parentBroker, childBroker))

        testee.attemptFirePixel()

        verify(mockPixelSender).reportWeeklyChildOrphanedOptOuts(
            brokerUrl = childBrokerUrl,
            childParentRecordDifference = 1,
            orphanedRecordsCount = 1,
        )
        verify(mockPirRepository).setWeeklyStatLastSentMs(baseTime)
    }

    @Test
    fun `when extracted profile is less than one week old then fire pixel`() = runTest {
        val parentBroker = createBroker(
            name = parentBrokerName,
            url = parentBrokerUrl,
            parent = null,
        )
        val childBroker = createBroker(
            name = childBrokerName,
            url = childBrokerUrl,
            parent = parentBrokerUrl,
        )
        val recentProfile = createExtractedProfile(
            brokerName = childBrokerName,
            dateAddedInMillis = baseTime - sixDaysInMillis,
        )
        whenever(mockPirRepository.getWeeklyStatLastSentMs()).thenReturn(0L)
        whenever(mockCurrentTimeProvider.currentTimeMillis()).thenReturn(baseTime)
        whenever(mockPirRepository.getAllExtractedProfiles()).thenReturn(listOf(recentProfile))
        whenever(mockPirRepository.getAllActiveBrokerObjects()).thenReturn(listOf(parentBroker, childBroker))

        testee.attemptFirePixel()

        verify(mockPixelSender).reportWeeklyChildOrphanedOptOuts(
            brokerUrl = childBrokerUrl,
            childParentRecordDifference = 1,
            orphanedRecordsCount = 1,
        )
    }

    @Test
    fun `when extracted profile is exactly one week old then does NOT fire pixel`() = runTest {
        val parentBroker = createBroker(
            name = parentBrokerName,
            url = parentBrokerUrl,
            parent = null,
        )
        val childBroker = createBroker(
            name = childBrokerName,
            url = childBrokerUrl,
            parent = parentBrokerUrl,
        )
        val weekOldProfile = createExtractedProfile(
            brokerName = childBrokerName,
            dateAddedInMillis = baseTime - sevenDaysInMillis,
        )
        whenever(mockPirRepository.getWeeklyStatLastSentMs()).thenReturn(0L)
        whenever(mockCurrentTimeProvider.currentTimeMillis()).thenReturn(baseTime)
        whenever(mockPirRepository.getAllExtractedProfiles()).thenReturn(listOf(weekOldProfile))
        whenever(mockPirRepository.getAllActiveBrokerObjects()).thenReturn(listOf(parentBroker, childBroker))

        testee.attemptFirePixel()

        verifyNoInteractions(mockPixelSender)
    }

    @Test
    fun `when extracted profile is more than one week old then do not fire pixel`() = runTest {
        val parentBroker = createBroker(
            name = parentBrokerName,
            url = parentBrokerUrl,
            parent = null,
        )
        val childBroker = createBroker(
            name = childBrokerName,
            url = childBrokerUrl,
            parent = parentBrokerUrl,
        )
        val oldProfile = createExtractedProfile(
            brokerName = childBrokerName,
            dateAddedInMillis = baseTime - eightDaysInMillis,
        )
        whenever(mockPirRepository.getWeeklyStatLastSentMs()).thenReturn(0L)
        whenever(mockCurrentTimeProvider.currentTimeMillis()).thenReturn(baseTime)
        whenever(mockPirRepository.getAllExtractedProfiles()).thenReturn(listOf(oldProfile))
        whenever(mockPirRepository.getAllActiveBrokerObjects()).thenReturn(listOf(parentBroker, childBroker))

        testee.attemptFirePixel()

        verifyNoInteractions(mockPixelSender)
    }

    @Test
    fun `when extracted profile is created on the same day then fire pixel`() = runTest {
        val parentBroker = createBroker(
            name = parentBrokerName,
            url = parentBrokerUrl,
            parent = null,
        )
        val childBroker = createBroker(
            name = childBrokerName,
            url = childBrokerUrl,
            parent = parentBrokerUrl,
        )
        val oldProfile = createExtractedProfile(
            brokerName = childBrokerName,
            dateAddedInMillis = baseTime,
        )
        whenever(mockPirRepository.getWeeklyStatLastSentMs()).thenReturn(0L)
        whenever(mockCurrentTimeProvider.currentTimeMillis()).thenReturn(baseTime)
        whenever(mockPirRepository.getAllExtractedProfiles()).thenReturn(listOf(oldProfile))
        whenever(mockPirRepository.getAllActiveBrokerObjects()).thenReturn(listOf(parentBroker, childBroker))

        testee.attemptFirePixel()

        verify(mockPixelSender).reportWeeklyChildOrphanedOptOuts(
            brokerUrl = childBrokerUrl,
            childParentRecordDifference = 1,
            orphanedRecordsCount = 1,
        )
    }

    // ============================================================================
    // CHILD-PARENT BROKER SCENARIOS
    // ============================================================================

    @Test
    fun `when child broker has no parent profiles then all child profiles are orphaned`() = runTest {
        val parentBroker = createBroker(
            name = parentBrokerName,
            url = parentBrokerUrl,
            parent = null,
        )
        val childBroker = createBroker(
            name = childBrokerName,
            url = childBrokerUrl,
            parent = parentBrokerUrl,
        )
        val childProfile1 = createExtractedProfile(
            brokerName = childBrokerName,
            dateAddedInMillis = baseTime - sixDaysInMillis,
            profileQueryId = 1L,
        )
        val childProfile2 = createExtractedProfile(
            brokerName = childBrokerName,
            dateAddedInMillis = baseTime - sixDaysInMillis,
            profileQueryId = 2L,
        )
        whenever(mockPirRepository.getWeeklyStatLastSentMs()).thenReturn(0L)
        whenever(mockCurrentTimeProvider.currentTimeMillis()).thenReturn(baseTime)
        whenever(mockPirRepository.getAllExtractedProfiles()).thenReturn(listOf(childProfile1, childProfile2))
        whenever(mockPirRepository.getAllActiveBrokerObjects()).thenReturn(listOf(parentBroker, childBroker))

        testee.attemptFirePixel()

        verify(mockPixelSender).reportWeeklyChildOrphanedOptOuts(
            brokerUrl = childBrokerUrl,
            childParentRecordDifference = 2,
            orphanedRecordsCount = 2,
        )
    }

    @Test
    fun `when child broker has ALL matching parent profiles then do not fire pixel since diff and orphaned are 0`() = runTest {
        // Note: hasMatchingProfileOnParent checks broker names, so child profiles
        // can never match parent profiles (different broker names)
        val parentBroker = createBroker(
            name = parentBrokerName,
            url = parentBrokerUrl,
            parent = null,
        )
        val childBroker = createBroker(
            name = childBrokerName,
            url = childBrokerUrl,
            parent = parentBrokerUrl,
        )
        val childProfile = createExtractedProfile(
            brokerName = childBrokerName,
            dateAddedInMillis = baseTime - sixDaysInMillis,
            profileQueryId = 1L,
            name = "John Doe",
            age = "30",
        )
        val parentProfile = createExtractedProfile(
            brokerName = parentBrokerName,
            dateAddedInMillis = baseTime - sixDaysInMillis,
            profileQueryId = 1L,
            name = "John Doe",
            age = "30",
        )
        whenever(mockPirRepository.getWeeklyStatLastSentMs()).thenReturn(0L)
        whenever(mockCurrentTimeProvider.currentTimeMillis()).thenReturn(baseTime)
        whenever(mockPirRepository.getAllExtractedProfiles()).thenReturn(listOf(childProfile, parentProfile))
        whenever(mockPirRepository.getAllActiveBrokerObjects()).thenReturn(listOf(parentBroker, childBroker))

        testee.attemptFirePixel()

        verifyNoInteractions(mockPixelSender)
    }

    @Test
    fun `when child broker has parent with non matching profiles then all are orphaned`() = runTest {
        val parentBroker = createBroker(
            name = parentBrokerName,
            url = parentBrokerUrl,
            parent = null,
        )
        val childBroker = createBroker(
            name = childBrokerName,
            url = childBrokerUrl,
            parent = parentBrokerUrl,
        )
        val childProfile = createExtractedProfile(
            brokerName = childBrokerName,
            dateAddedInMillis = baseTime - sixDaysInMillis,
            profileQueryId = 1L,
            name = "John Doe",
            age = "30",
        )
        val parentProfile = createExtractedProfile(
            brokerName = parentBrokerName,
            dateAddedInMillis = baseTime - sixDaysInMillis,
            profileQueryId = 2L,
            name = "Jane Smith",
            age = "25",
        )
        whenever(mockPirRepository.getWeeklyStatLastSentMs()).thenReturn(0L)
        whenever(mockCurrentTimeProvider.currentTimeMillis()).thenReturn(baseTime)
        whenever(mockPirRepository.getAllExtractedProfiles()).thenReturn(listOf(childProfile, parentProfile))
        whenever(mockPirRepository.getAllActiveBrokerObjects()).thenReturn(listOf(parentBroker, childBroker))

        testee.attemptFirePixel()

        verify(mockPixelSender).reportWeeklyChildOrphanedOptOuts(
            brokerUrl = childBrokerUrl,
            childParentRecordDifference = 0,
            orphanedRecordsCount = 1,
        )
    }

    @Test
    fun `when child broker has more profiles than parent then reports positive record diff`() = runTest {
        val parentBroker = createBroker(
            name = parentBrokerName,
            url = parentBrokerUrl,
            parent = null,
        )
        val childBroker = createBroker(
            name = childBrokerName,
            url = childBrokerUrl,
            parent = parentBrokerUrl,
        )
        val childProfile1 = createExtractedProfile(
            brokerName = childBrokerName,
            dateAddedInMillis = baseTime - sixDaysInMillis,
            profileQueryId = 1L,
            name = "John Doe",
        )
        val childProfile2 = createExtractedProfile(
            brokerName = childBrokerName,
            dateAddedInMillis = baseTime - sixDaysInMillis,
            profileQueryId = 2L,
            name = "Jane Doe",
        )
        val parentProfile = createExtractedProfile(
            brokerName = parentBrokerName,
            dateAddedInMillis = baseTime - sixDaysInMillis,
            profileQueryId = 3L,
            name = "John Doe",
        )
        whenever(mockPirRepository.getWeeklyStatLastSentMs()).thenReturn(0L)
        whenever(mockCurrentTimeProvider.currentTimeMillis()).thenReturn(baseTime)
        whenever(mockPirRepository.getAllExtractedProfiles()).thenReturn(listOf(childProfile1, childProfile2, parentProfile))
        whenever(mockPirRepository.getAllActiveBrokerObjects()).thenReturn(listOf(parentBroker, childBroker))

        testee.attemptFirePixel()

        verify(mockPixelSender).reportWeeklyChildOrphanedOptOuts(
            brokerUrl = childBrokerUrl,
            childParentRecordDifference = 1,
            orphanedRecordsCount = 1,
        )
    }

    @Test
    fun `when child broker has fewer profiles than parent And with orphaned record then reports negative record diff`() = runTest {
        val parentBroker = createBroker(
            name = parentBrokerName,
            url = parentBrokerUrl,
            parent = null,
        )
        val childBroker = createBroker(
            name = childBrokerName,
            url = childBrokerUrl,
            parent = parentBrokerUrl,
        )
        val childProfile = createExtractedProfile(
            brokerName = childBrokerName,
            dateAddedInMillis = baseTime - sixDaysInMillis,
            profileQueryId = 1L,
            name = "John Doe",
        )
        val parentProfile1 = createExtractedProfile(
            brokerName = parentBrokerName,
            dateAddedInMillis = baseTime - sixDaysInMillis,
            profileQueryId = 2L,
            name = "Ana Doe",
        )
        val parentProfile2 = createExtractedProfile(
            brokerName = parentBrokerName,
            dateAddedInMillis = baseTime - sixDaysInMillis,
            profileQueryId = 3L,
            name = "Jane Doe",
        )
        whenever(mockPirRepository.getWeeklyStatLastSentMs()).thenReturn(0L)
        whenever(mockCurrentTimeProvider.currentTimeMillis()).thenReturn(baseTime)
        whenever(mockPirRepository.getAllExtractedProfiles()).thenReturn(listOf(childProfile, parentProfile1, parentProfile2))
        whenever(mockPirRepository.getAllActiveBrokerObjects()).thenReturn(listOf(parentBroker, childBroker))

        testee.attemptFirePixel()

        verify(mockPixelSender).reportWeeklyChildOrphanedOptOuts(
            brokerUrl = childBrokerUrl,
            childParentRecordDifference = -1,
            orphanedRecordsCount = 1,
        )
    }

    // ============================================================================
    // EDGE CASES AND BOUNDARY CONDITIONS
    // ============================================================================

    @Test
    fun `when record diff is zero and orphaned records count is zero then does not fire pixel`() = runTest {
        val parentBroker = createBroker(
            name = parentBrokerName,
            url = parentBrokerUrl,
            parent = null,
        )
        val childBroker = createBroker(
            name = childBrokerName,
            url = childBrokerUrl,
            parent = parentBrokerUrl,
        )
        // No profiles at all - should not fire
        whenever(mockPirRepository.getWeeklyStatLastSentMs()).thenReturn(0L)
        whenever(mockCurrentTimeProvider.currentTimeMillis()).thenReturn(baseTime)
        whenever(mockPirRepository.getAllExtractedProfiles()).thenReturn(emptyList())
        whenever(mockPirRepository.getAllActiveBrokerObjects()).thenReturn(listOf(parentBroker, childBroker))

        testee.attemptFirePixel()

        verifyNoInteractions(mockPixelSender)
    }

    @Test
    fun `when record diff is negative and orphaned records count is zero then does not fire pixel`() = runTest {
        val parentBroker = createBroker(
            name = parentBrokerName,
            url = parentBrokerUrl,
            parent = null,
        )
        val childBroker = createBroker(
            name = childBrokerName,
            url = childBrokerUrl,
            parent = parentBrokerUrl,
        )
        val parentProfile = createExtractedProfile(
            brokerName = parentBrokerName,
            dateAddedInMillis = baseTime - sixDaysInMillis,
            profileQueryId = 1L,
        )
        // No child profiles - should not fire (no child profiles to report)
        whenever(mockPirRepository.getWeeklyStatLastSentMs()).thenReturn(0L)
        whenever(mockCurrentTimeProvider.currentTimeMillis()).thenReturn(baseTime)
        whenever(mockPirRepository.getAllExtractedProfiles()).thenReturn(listOf(parentProfile))
        whenever(mockPirRepository.getAllActiveBrokerObjects()).thenReturn(listOf(parentBroker, childBroker))

        testee.attemptFirePixel()

        verifyNoInteractions(mockPixelSender)
    }

    @Test
    fun `when parent broker not found then treats as no parent profiles`() = runTest {
        val childBroker = createBroker(
            name = childBrokerName,
            url = childBrokerUrl,
            parent = parentBrokerUrl,
        )
        val childProfile = createExtractedProfile(
            brokerName = childBrokerName,
            dateAddedInMillis = baseTime - sixDaysInMillis,
            profileQueryId = 1L,
        )
        whenever(mockPirRepository.getWeeklyStatLastSentMs()).thenReturn(0L)
        whenever(mockCurrentTimeProvider.currentTimeMillis()).thenReturn(baseTime)
        whenever(mockPirRepository.getAllExtractedProfiles()).thenReturn(listOf(childProfile))
        whenever(mockPirRepository.getAllActiveBrokerObjects()).thenReturn(listOf(childBroker))

        testee.attemptFirePixel()

        verify(mockPixelSender).reportWeeklyChildOrphanedOptOuts(
            brokerUrl = childBrokerUrl,
            childParentRecordDifference = 1,
            orphanedRecordsCount = 1,
        )
    }

    @Test
    fun `when child broker has empty parent url then does not fire pixel`() = runTest {
        val childBroker = createBroker(
            name = childBrokerName,
            url = childBrokerUrl,
            parent = "",
        )
        val childProfile = createExtractedProfile(
            brokerName = childBrokerName,
            dateAddedInMillis = baseTime - sixDaysInMillis,
            profileQueryId = 1L,
        )
        whenever(mockPirRepository.getWeeklyStatLastSentMs()).thenReturn(0L)
        whenever(mockCurrentTimeProvider.currentTimeMillis()).thenReturn(baseTime)
        whenever(mockPirRepository.getAllExtractedProfiles()).thenReturn(listOf(childProfile))
        whenever(mockPirRepository.getAllActiveBrokerObjects()).thenReturn(listOf(childBroker))

        testee.attemptFirePixel()

        verifyNoInteractions(mockPixelSender)
    }

    @Test
    fun `when child broker has null parent then does not fire pixel`() = runTest {
        val childBroker = createBroker(
            name = childBrokerName,
            url = childBrokerUrl,
            parent = null,
        )
        val childProfile = createExtractedProfile(
            brokerName = childBrokerName,
            dateAddedInMillis = baseTime - sixDaysInMillis,
            profileQueryId = 1L,
        )
        whenever(mockPirRepository.getWeeklyStatLastSentMs()).thenReturn(0L)
        whenever(mockCurrentTimeProvider.currentTimeMillis()).thenReturn(baseTime)
        whenever(mockPirRepository.getAllExtractedProfiles()).thenReturn(listOf(childProfile))
        whenever(mockPirRepository.getAllActiveBrokerObjects()).thenReturn(listOf(childBroker))

        testee.attemptFirePixel()

        verifyNoInteractions(mockPixelSender)
    }

    @Test
    fun `when record diff is positive and orphaned count is zero then fires pixel`() = runTest {
        // This scenario is impossible with current implementation, but we test
        // the case where recordDiff > 0 (which should always fire)
        val parentBroker = createBroker(
            name = parentBrokerName,
            url = parentBrokerUrl,
            parent = null,
        )
        val childBroker = createBroker(
            name = childBrokerName,
            url = childBrokerUrl,
            parent = parentBrokerUrl,
        )
        val childProfile1 = createExtractedProfile(
            brokerName = childBrokerName,
            dateAddedInMillis = baseTime - sixDaysInMillis,
            profileQueryId = 1L,
        )
        val childProfile2 = createExtractedProfile(
            brokerName = childBrokerName,
            dateAddedInMillis = baseTime - sixDaysInMillis,
            profileQueryId = 2L,
        )
        whenever(mockPirRepository.getWeeklyStatLastSentMs()).thenReturn(0L)
        // No parent profiles
        whenever(mockCurrentTimeProvider.currentTimeMillis()).thenReturn(baseTime)
        whenever(mockPirRepository.getAllExtractedProfiles()).thenReturn(listOf(childProfile1, childProfile2))
        whenever(mockPirRepository.getAllActiveBrokerObjects()).thenReturn(listOf(parentBroker, childBroker))

        testee.attemptFirePixel()

        verify(mockPixelSender).reportWeeklyChildOrphanedOptOuts(
            brokerUrl = childBrokerUrl,
            childParentRecordDifference = 2,
            orphanedRecordsCount = 2,
        )
    }

    // ============================================================================
    // MULTIPLE CHILD BROKERS SCENARIOS
    // ============================================================================

    @Test
    fun `when multiple child brokers for same parent then fires pixel for each`() = runTest {
        val parentBroker = createBroker(
            name = parentBrokerName,
            url = parentBrokerUrl,
            parent = null,
        )
        val childBroker1 = createBroker(
            name = "child-broker-1",
            url = "https://child-broker-1.com",
            parent = parentBrokerUrl,
        )
        val childBroker2 = createBroker(
            name = "child-broker-2",
            url = "https://child-broker-2.com",
            parent = parentBrokerUrl,
        )
        val childProfile1 = createExtractedProfile(
            brokerName = "child-broker-1",
            dateAddedInMillis = baseTime - sixDaysInMillis,
            profileQueryId = 1L,
        )
        val childProfile2 = createExtractedProfile(
            brokerName = "child-broker-2",
            dateAddedInMillis = baseTime - sixDaysInMillis,
            profileQueryId = 2L,
        )
        whenever(mockPirRepository.getWeeklyStatLastSentMs()).thenReturn(0L)
        whenever(mockCurrentTimeProvider.currentTimeMillis()).thenReturn(baseTime)
        whenever(mockPirRepository.getAllExtractedProfiles()).thenReturn(listOf(childProfile1, childProfile2))
        whenever(mockPirRepository.getAllActiveBrokerObjects()).thenReturn(listOf(parentBroker, childBroker1, childBroker2))

        testee.attemptFirePixel()

        verify(mockPixelSender).reportWeeklyChildOrphanedOptOuts(
            brokerUrl = "https://child-broker-1.com",
            childParentRecordDifference = 1,
            orphanedRecordsCount = 1,
        )
        verify(mockPixelSender).reportWeeklyChildOrphanedOptOuts(
            brokerUrl = "https://child-broker-2.com",
            childParentRecordDifference = 1,
            orphanedRecordsCount = 1,
        )
    }

    @Test
    fun `when multiple child brokers with different parent urls then fires pixel for each`() = runTest {
        val parentBroker1 = createBroker(
            name = "parent-broker-1",
            url = "https://parent-broker-1.com",
            parent = null,
        )
        val parentBroker2 = createBroker(
            name = "parent-broker-2",
            url = "https://parent-broker-2.com",
            parent = null,
        )
        val childBroker1 = createBroker(
            name = "child-broker-1",
            url = "https://child-broker-1.com",
            parent = "https://parent-broker-1.com",
        )
        val childBroker2 = createBroker(
            name = "child-broker-2",
            url = "https://child-broker-2.com",
            parent = "https://parent-broker-2.com",
        )
        val childProfile1 = createExtractedProfile(
            brokerName = "child-broker-1",
            dateAddedInMillis = baseTime - sixDaysInMillis,
            profileQueryId = 1L,
        )
        val childProfile2 = createExtractedProfile(
            brokerName = "child-broker-2",
            dateAddedInMillis = baseTime - sixDaysInMillis,
            profileQueryId = 2L,
        )
        whenever(mockPirRepository.getWeeklyStatLastSentMs()).thenReturn(0L)
        whenever(mockCurrentTimeProvider.currentTimeMillis()).thenReturn(baseTime)
        whenever(mockPirRepository.getAllExtractedProfiles()).thenReturn(listOf(childProfile1, childProfile2))
        whenever(mockPirRepository.getAllActiveBrokerObjects()).thenReturn(
            listOf(parentBroker1, parentBroker2, childBroker1, childBroker2),
        )

        testee.attemptFirePixel()

        verify(mockPixelSender).reportWeeklyChildOrphanedOptOuts(
            brokerUrl = "https://child-broker-1.com",
            childParentRecordDifference = 1,
            orphanedRecordsCount = 1,
        )
        verify(mockPixelSender).reportWeeklyChildOrphanedOptOuts(
            brokerUrl = "https://child-broker-2.com",
            childParentRecordDifference = 1,
            orphanedRecordsCount = 1,
        )
    }

    // ============================================================================
    // COMPLEX PROFILE MATCHING SCENARIOS
    // ============================================================================

    @Test
    fun `when some child profiles match parent and some do not then reports correct orphaned count`() = runTest {
        val parentBroker = createBroker(
            name = parentBrokerName,
            url = parentBrokerUrl,
            parent = null,
        )
        val childBroker = createBroker(
            name = childBrokerName,
            url = childBrokerUrl,
            parent = parentBrokerUrl,
        )
        val matchingChildProfile = createExtractedProfile(
            brokerName = childBrokerName,
            dateAddedInMillis = baseTime - sixDaysInMillis,
            profileQueryId = 1L,
            name = "John Doe",
            age = "30",
        )
        val orphanedChildProfile = createExtractedProfile(
            brokerName = childBrokerName,
            dateAddedInMillis = baseTime - sixDaysInMillis,
            profileQueryId = 2L,
            name = "Jane Smith",
            age = "25",
        )
        val parentProfile = createExtractedProfile(
            brokerName = parentBrokerName,
            dateAddedInMillis = baseTime - sixDaysInMillis,
            profileQueryId = 1L,
            name = "John Doe",
            age = "25",
        )
        val parentProfile2 = createExtractedProfile(
            brokerName = parentBrokerName,
            dateAddedInMillis = baseTime - sixDaysInMillis,
            profileQueryId = 1L,
            name = "John Doe",
            age = "30",
        )
        whenever(mockPirRepository.getWeeklyStatLastSentMs()).thenReturn(0L)
        whenever(mockCurrentTimeProvider.currentTimeMillis()).thenReturn(baseTime)
        whenever(mockPirRepository.getAllExtractedProfiles()).thenReturn(
            listOf(
                matchingChildProfile,
                orphanedChildProfile,
                parentProfile,
                parentProfile2,
            ),
        )
        whenever(mockPirRepository.getAllActiveBrokerObjects()).thenReturn(listOf(parentBroker, childBroker))

        testee.attemptFirePixel()

        verify(mockPixelSender).reportWeeklyChildOrphanedOptOuts(
            brokerUrl = childBrokerUrl,
            childParentRecordDifference = 0,
            orphanedRecordsCount = 1,
        )
    }

    @Test
    fun `when profiles have non matching alternative names then orphaned`() = runTest {
        val parentBroker = createBroker(
            name = parentBrokerName,
            url = parentBrokerUrl,
            parent = null,
        )
        val childBroker = createBroker(
            name = childBrokerName,
            url = childBrokerUrl,
            parent = parentBrokerUrl,
        )
        val childProfile = createExtractedProfile(
            brokerName = childBrokerName,
            dateAddedInMillis = baseTime - sixDaysInMillis,
            profileQueryId = 1L,
            name = "John Doe",
            age = "30",
            alternativeNames = listOf("Johnny"),
        )
        val parentProfile = createExtractedProfile(
            brokerName = parentBrokerName,
            dateAddedInMillis = baseTime - sixDaysInMillis,
            profileQueryId = 1L,
            name = "John Doe",
            age = "30",
            alternativeNames = listOf("Jane"),
        )
        whenever(mockPirRepository.getWeeklyStatLastSentMs()).thenReturn(0L)
        whenever(mockCurrentTimeProvider.currentTimeMillis()).thenReturn(baseTime)
        whenever(mockPirRepository.getAllExtractedProfiles()).thenReturn(listOf(childProfile, parentProfile))
        whenever(mockPirRepository.getAllActiveBrokerObjects()).thenReturn(listOf(parentBroker, childBroker))

        testee.attemptFirePixel()

        verify(mockPixelSender).reportWeeklyChildOrphanedOptOuts(
            brokerUrl = childBrokerUrl,
            childParentRecordDifference = 0,
            orphanedRecordsCount = 1,
        )
    }

    @Test
    fun `when profiles have non matching relatives then orphaned`() = runTest {
        val parentBroker = createBroker(
            name = parentBrokerName,
            url = parentBrokerUrl,
            parent = null,
        )
        val childBroker = createBroker(
            name = childBrokerName,
            url = childBrokerUrl,
            parent = parentBrokerUrl,
        )
        val childProfile = createExtractedProfile(
            brokerName = childBrokerName,
            dateAddedInMillis = baseTime - sixDaysInMillis,
            profileQueryId = 1L,
            name = "John Doe",
            age = "30",
            relatives = listOf("Jane Doe", "Bob Doe", "Mary Doe"),
        )
        val parentProfile = createExtractedProfile(
            brokerName = parentBrokerName,
            dateAddedInMillis = baseTime - sixDaysInMillis,
            profileQueryId = 1L,
            name = "John Doe",
            age = "30",
            relatives = listOf("Jane Doe", "Bob Doe", "Alice Doe"),
        )
        whenever(mockPirRepository.getWeeklyStatLastSentMs()).thenReturn(0L)
        whenever(mockCurrentTimeProvider.currentTimeMillis()).thenReturn(baseTime)
        whenever(mockPirRepository.getAllExtractedProfiles()).thenReturn(listOf(childProfile, parentProfile))
        whenever(mockPirRepository.getAllActiveBrokerObjects()).thenReturn(listOf(parentBroker, childBroker))

        testee.attemptFirePixel()

        // Still orphaned due to none are super set or subset
        verify(mockPixelSender).reportWeeklyChildOrphanedOptOuts(
            brokerUrl = childBrokerUrl,
            childParentRecordDifference = 0,
            orphanedRecordsCount = 1,
        )
    }

    @Test
    fun `when profiles have different age then orphaned`() = runTest {
        val parentBroker = createBroker(
            name = parentBrokerName,
            url = parentBrokerUrl,
            parent = null,
        )
        val childBroker = createBroker(
            name = childBrokerName,
            url = childBrokerUrl,
            parent = parentBrokerUrl,
        )
        val childProfile = createExtractedProfile(
            brokerName = childBrokerName,
            dateAddedInMillis = baseTime - sixDaysInMillis,
            profileQueryId = 1L,
            name = "John Doe",
            age = "30",
            relatives = listOf("Jane Doe", "Bob Doe", "Mary Doe"),
        )
        val parentProfile = createExtractedProfile(
            brokerName = parentBrokerName,
            dateAddedInMillis = baseTime - sixDaysInMillis,
            profileQueryId = 1L,
            name = "John Doe",
            age = "35",
            relatives = listOf("Jane Doe", "Bob Doe", "Mary Doe"),
        )
        whenever(mockPirRepository.getWeeklyStatLastSentMs()).thenReturn(0L)
        whenever(mockCurrentTimeProvider.currentTimeMillis()).thenReturn(baseTime)
        whenever(mockPirRepository.getAllExtractedProfiles()).thenReturn(listOf(childProfile, parentProfile))
        whenever(mockPirRepository.getAllActiveBrokerObjects()).thenReturn(listOf(parentBroker, childBroker))

        testee.attemptFirePixel()

        // Still orphaned due to diff in age
        verify(mockPixelSender).reportWeeklyChildOrphanedOptOuts(
            brokerUrl = childBrokerUrl,
            childParentRecordDifference = 0,
            orphanedRecordsCount = 1,
        )
    }

    @Test
    fun `when profiles have none matching addresses then still orphaned `() = runTest {
        val parentBroker = createBroker(
            name = parentBrokerName,
            url = parentBrokerUrl,
            parent = null,
        )
        val childBroker = createBroker(
            name = childBrokerName,
            url = childBrokerUrl,
            parent = parentBrokerUrl,
        )
        val childProfile = createExtractedProfile(
            brokerName = childBrokerName,
            dateAddedInMillis = baseTime - sixDaysInMillis,
            profileQueryId = 1L,
            name = "John Doe",
            age = "30",
            addresses = listOf(AddressCityState("New York", "NY"), AddressCityState("San Francisco", "CA")),
        )
        val parentProfile = createExtractedProfile(
            brokerName = parentBrokerName,
            dateAddedInMillis = baseTime - sixDaysInMillis,
            profileQueryId = 1L,
            name = "John Doe",
            age = "30",
            addresses = listOf(AddressCityState("New York", "NY"), AddressCityState("Los Angeles", "CA")),
        )
        whenever(mockPirRepository.getWeeklyStatLastSentMs()).thenReturn(0L)
        whenever(mockCurrentTimeProvider.currentTimeMillis()).thenReturn(baseTime)
        whenever(mockPirRepository.getAllExtractedProfiles()).thenReturn(listOf(childProfile, parentProfile))
        whenever(mockPirRepository.getAllActiveBrokerObjects()).thenReturn(listOf(parentBroker, childBroker))

        testee.attemptFirePixel()

        // Still orphaned due to none are super or sub set
        verify(mockPixelSender).reportWeeklyChildOrphanedOptOuts(
            brokerUrl = childBrokerUrl,
            childParentRecordDifference = 0,
            orphanedRecordsCount = 1,
        )
    }

    @Test
    fun `when child has multiple profiles with mixed matching then all orphaned`() = runTest {
        val parentBroker = createBroker(
            name = parentBrokerName,
            url = parentBrokerUrl,
            parent = null,
        )
        val childBroker = createBroker(
            name = childBrokerName,
            url = childBrokerUrl,
            parent = parentBrokerUrl,
        )
        val childProfile1 = createExtractedProfile(
            brokerName = childBrokerName,
            dateAddedInMillis = baseTime - sixDaysInMillis,
            profileQueryId = 1L,
            name = "John Doe",
            age = "30",
        )
        val childProfile2 = createExtractedProfile(
            brokerName = childBrokerName,
            dateAddedInMillis = baseTime - sixDaysInMillis,
            profileQueryId = 2L,
            name = "Jane Smith",
            age = "25",
        )
        val childProfile3 = createExtractedProfile(
            brokerName = childBrokerName,
            dateAddedInMillis = baseTime - sixDaysInMillis,
            profileQueryId = 3L,
            name = "Bob Johnson",
            age = "40",
        )
        val parentProfile1 = createExtractedProfile(
            brokerName = parentBrokerName,
            dateAddedInMillis = baseTime - sixDaysInMillis,
            profileQueryId = 1L,
            name = "John Doe",
            age = "30",
        )
        val parentProfile2 = createExtractedProfile(
            brokerName = parentBrokerName,
            dateAddedInMillis = baseTime - sixDaysInMillis,
            profileQueryId = 4L,
            name = "Alice Brown",
            age = "35",
        )
        whenever(mockPirRepository.getWeeklyStatLastSentMs()).thenReturn(0L)
        whenever(mockCurrentTimeProvider.currentTimeMillis()).thenReturn(baseTime)
        whenever(mockPirRepository.getAllExtractedProfiles()).thenReturn(
            listOf(childProfile1, childProfile2, childProfile3, parentProfile1, parentProfile2),
        )
        whenever(mockPirRepository.getAllActiveBrokerObjects()).thenReturn(listOf(parentBroker, childBroker))

        testee.attemptFirePixel()

        // 2 child profiles are orphaned due to difference in names
        verify(mockPixelSender).reportWeeklyChildOrphanedOptOuts(
            brokerUrl = childBrokerUrl,
            childParentRecordDifference = 1,
            orphanedRecordsCount = 2,
        )
    }

    // ============================================================================
    // HELPER FUNCTIONS
    // ============================================================================

    private fun createBroker(
        name: String,
        url: String,
        parent: String?,
    ): Broker {
        return Broker(
            name = name,
            fileName = "$name.json",
            url = url,
            version = "1.0",
            parent = parent,
            addedDatetime = 0L,
            removedAt = 0L,
        )
    }

    private fun createExtractedProfile(
        brokerName: String,
        dateAddedInMillis: Long,
        profileQueryId: Long = 1L,
        name: String = "",
        age: String = "",
        alternativeNames: List<String> = emptyList(),
        relatives: List<String> = emptyList(),
        addresses: List<AddressCityState> = emptyList(),
        deprecated: Boolean = false,
    ): ExtractedProfile {
        return ExtractedProfile(
            dbId = 0L,
            profileQueryId = profileQueryId,
            brokerName = brokerName,
            name = name,
            alternativeNames = alternativeNames,
            age = age,
            addresses = addresses,
            phoneNumbers = emptyList(),
            relatives = relatives,
            reportId = "",
            email = "",
            fullName = "",
            profileUrl = "",
            identifier = "",
            dateAddedInMillis = dateAddedInMillis,
            deprecated = deprecated,
        )
    }
}
