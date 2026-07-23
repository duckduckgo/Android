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

package com.duckduckgo.promptscoordinator.impl

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.common.utils.CurrentTimeProvider
import com.duckduckgo.feature.toggles.api.FakeFeatureToggleFactory
import com.duckduckgo.feature.toggles.api.Toggle.State
import com.duckduckgo.promptscoordinator.api.PromptType
import com.duckduckgo.remote.messaging.api.Content
import com.duckduckgo.remote.messaging.api.RemoteMessage
import com.duckduckgo.remote.messaging.api.RemoteMessageModel
import com.duckduckgo.remote.messaging.api.Surface
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.io.File
import java.util.concurrent.TimeUnit

@RunWith(AndroidJUnit4::class)
class RealPromptsCoordinatorTest {

    @get:Rule
    var coroutinesTestRule = CoroutineTestRule()

    private val feature = FakeFeatureToggleFactory.create(PromptsCoordinatorFeature::class.java)
    private val remoteMessageModel: RemoteMessageModel = mock()
    private val currentTimeProvider: CurrentTimeProvider = mock()

    private lateinit var testDataStoreFile: File
    private lateinit var testDataStore: DataStore<Preferences>
    private lateinit var testee: RealPromptsCoordinator

    private var now: Long = START_TIME

    @Before
    fun setUp() {
        feature.self().setRawStoredState(State(true))
        whenever(currentTimeProvider.currentTimeMillis()).thenAnswer { now }
        whenever(remoteMessageModel.getActiveMessage()).thenReturn(null)

        testDataStoreFile = File.createTempFile("prompts_coordinator_test", ".preferences_pb")
        testDataStore = PreferenceDataStoreFactory.create(
            scope = coroutinesTestRule.testScope,
            produceFile = { testDataStoreFile },
        )
        testee = createTestee()
    }

    @After
    fun tearDown() {
        testDataStoreFile.delete()
    }

    private fun createTestee() = RealPromptsCoordinator(
        feature = feature,
        remoteMessageModel = remoteMessageModel,
        store = testDataStore,
        currentTimeProvider = currentTimeProvider,
        dispatchers = coroutinesTestRule.testDispatcherProvider,
        appCoroutineScope = coroutinesTestRule.testScope,
    )

    @Test
    fun whenFeatureDisabledThenAllClaimsAreGrantedWithoutState() = runTest {
        feature.self().setRawStoredState(State(false))

        assertTrue(testee.tryClaim(PromptType.MODAL))
        assertTrue(testee.tryClaim(PromptType.RMF))
        // Pass-through claims take no ownership: nothing to release, nothing stamped.
        assertFalse(testee.isEnabled())
    }

    @Test
    fun whenSurfaceFreeAndGapElapsedThenModalClaimIsGranted() = runTest {
        assertTrue(testee.tryClaim(PromptType.MODAL))
    }

    @Test
    fun whenSurfaceClaimedByModalThenOtherClaimsAreRefused() = runTest {
        assertTrue(testee.tryClaim(PromptType.MODAL))

        assertFalse(testee.tryClaim(PromptType.RMF))
        // No stacking: a second modal claim is refused too.
        assertFalse(testee.tryClaim(PromptType.MODAL))
    }

    @Test
    fun whenSurfaceClaimedByRmfThenModalClaimIsRefusedButRmfReclaimIsGranted() = runTest {
        assertTrue(testee.tryClaim(PromptType.RMF))

        assertFalse(testee.tryClaim(PromptType.MODAL))
        // The single persistent RMF card re-renders on every NTP render: re-claims are granted.
        assertTrue(testee.tryClaim(PromptType.RMF))
    }

    @Test
    fun whenNtpRmfMessageIsActiveThenModalClaimIsRefused() = runTest {
        whenever(remoteMessageModel.getActiveMessage()).thenReturn(remoteMessage(Surface.NEW_TAB_PAGE))

        assertFalse(testee.tryClaim(PromptType.MODAL))
        // RMF itself is not blocked by its own active message.
        assertTrue(testee.tryClaim(PromptType.RMF))
    }

    @Test
    fun whenModalSurfaceOnlyMessageIsActiveThenModalClaimIsGranted() = runTest {
        // A MODAL-surface message is shown *through* the Modal Coordinator; it must not block the
        // very claim it needs.
        whenever(remoteMessageModel.getActiveMessage()).thenReturn(remoteMessage(Surface.MODAL))

        assertTrue(testee.tryClaim(PromptType.MODAL))
    }

    @Test
    fun whenModalClaimDoneThenRmfGapOpensAfterTenMinutes() = runTest {
        assertTrue(testee.tryClaim(PromptType.MODAL))
        testee.onClaimDone(PromptType.MODAL)
        coroutinesTestRule.testScope.testScheduler.advanceUntilIdle()

        now += TimeUnit.MINUTES.toMillis(9)
        assertFalse(testee.tryClaim(PromptType.RMF))

        now += TimeUnit.MINUTES.toMillis(1)
        assertTrue(testee.tryClaim(PromptType.RMF))
    }

    @Test
    fun whenRmfClaimDoneThenModalGapOpensAfterTwentyFourHours() = runTest {
        assertTrue(testee.tryClaim(PromptType.RMF))
        testee.onClaimDone(PromptType.RMF)
        coroutinesTestRule.testScope.testScheduler.advanceUntilIdle()

        now += TimeUnit.HOURS.toMillis(23)
        assertFalse(testee.tryClaim(PromptType.MODAL))

        now += TimeUnit.HOURS.toMillis(1)
        assertTrue(testee.tryClaim(PromptType.MODAL))
    }

    @Test
    fun whenModalClaimDoneThenNextModalWaitsTwentyFourHours() = runTest {
        assertTrue(testee.tryClaim(PromptType.MODAL))
        testee.onClaimDone(PromptType.MODAL)
        coroutinesTestRule.testScope.testScheduler.advanceUntilIdle()

        now += TimeUnit.HOURS.toMillis(12)
        assertFalse(testee.tryClaim(PromptType.MODAL))

        now += TimeUnit.HOURS.toMillis(12)
        assertTrue(testee.tryClaim(PromptType.MODAL))
    }

    @Test
    fun whenClaimCancelledThenSurfaceFreesWithoutStampingTheGap() = runTest {
        assertTrue(testee.tryClaim(PromptType.MODAL))
        testee.onClaimCancelled(PromptType.MODAL)
        coroutinesTestRule.testScope.testScheduler.advanceUntilIdle()

        // No gap started: both surfaces can claim immediately.
        assertTrue(testee.tryClaim(PromptType.RMF))
    }

    @Test
    fun whenDoneReportedByNonOwnerThenClaimIsUnaffected() = runTest {
        assertTrue(testee.tryClaim(PromptType.MODAL))

        testee.onClaimDone(PromptType.RMF)
        coroutinesTestRule.testScope.testScheduler.advanceUntilIdle()

        // MODAL still owns the surface, and no gap was stamped by the stale report.
        assertFalse(testee.tryClaim(PromptType.RMF))
        testee.onClaimCancelled(PromptType.MODAL)
        coroutinesTestRule.testScope.testScheduler.advanceUntilIdle()
        assertTrue(testee.tryClaim(PromptType.RMF))
    }

    @Test
    fun whenGapStampedThenItSurvivesANewInstanceReadingTheSameStore() = runTest {
        assertTrue(testee.tryClaim(PromptType.MODAL))
        testee.onClaimDone(PromptType.MODAL)
        coroutinesTestRule.testScope.testScheduler.advanceUntilIdle()

        // Simulates process death: in-memory owner and cache reset, timestamp persisted.
        val recreated = createTestee()
        now += TimeUnit.MINUTES.toMillis(5)
        assertFalse(recreated.tryClaim(PromptType.RMF))

        now += TimeUnit.MINUTES.toMillis(5)
        assertTrue(recreated.tryClaim(PromptType.RMF))
    }

    private fun remoteMessage(vararg surfaces: Surface) = RemoteMessage(
        id = "id1",
        content = Content.Small("", ""),
        matchingRules = emptyList(),
        exclusionRules = emptyList(),
        surfaces = surfaces.toList(),
    )

    companion object {
        // Far from epoch so a fresh store (timestamp 0) never blocks the first prompt.
        private val START_TIME = TimeUnit.DAYS.toMillis(1000)
    }
}
