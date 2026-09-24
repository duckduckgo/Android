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
import com.duckduckgo.app.statistics.pixels.Pixel
import com.duckduckgo.browser.api.install.AppInstall
import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.common.utils.CurrentTimeProvider
import com.duckduckgo.feature.toggles.api.FakeFeatureToggleFactory
import com.duckduckgo.feature.toggles.api.Toggle.State
import com.duckduckgo.promptscoordinator.api.PromptType
import com.duckduckgo.promptscoordinator.impl.exposure.PromptExposurePixelName
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.io.File
import java.util.concurrent.TimeUnit
import kotlin.time.Duration.Companion.days

@RunWith(AndroidJUnit4::class)
class RealPromptsCoordinatorTest {

    @get:Rule
    var coroutinesTestRule = CoroutineTestRule()

    private val feature = FakeFeatureToggleFactory.create(PromptsCoordinatorFeature::class.java)
    private val currentTimeProvider: CurrentTimeProvider = mock()
    private val pixel: Pixel = mock()
    private val appInstall: AppInstall = mock()

    private lateinit var testDataStoreFile: File
    private lateinit var testDataStore: DataStore<Preferences>
    private lateinit var testee: RealPromptsCoordinator

    private var now: Long = START_TIME

    @Before
    fun setUp() = runTest {
        feature.self().setRawStoredState(State(true))
        whenever(appInstall.getInstallAge()).thenReturn(3.days)
        whenever(currentTimeProvider.currentTimeMillis()).thenAnswer { now }

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
        store = testDataStore,
        currentTimeProvider = currentTimeProvider,
        dispatchers = coroutinesTestRule.testDispatcherProvider,
        pixel = pixel,
        appInstall = appInstall,
    )

    @Test
    fun whenFeatureDisabledThenAllClaimsAreGrantedWithoutState() = runTest {
        feature.self().setRawStoredState(State(false))

        assertTrue(testee.tryClaim(PromptType.MODAL))
        assertTrue(testee.tryClaim(PromptType.NTP_CARD))
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

        assertFalse(testee.tryClaim(PromptType.NTP_CARD))
        // No stacking: a second modal claim is refused too.
        assertFalse(testee.tryClaim(PromptType.MODAL))
    }

    @Test
    fun whenSurfaceClaimedByNtpCardThenModalClaimIsRefusedButNtpCardReclaimIsGranted() = runTest {
        assertTrue(testee.tryClaim(PromptType.NTP_CARD))

        assertFalse(testee.tryClaim(PromptType.MODAL))
        // The single persistent RMF card re-renders on every NTP render: re-claims are granted.
        assertTrue(testee.tryClaim(PromptType.NTP_CARD))
    }

    @Test
    fun whenModalClaimDoneThenNtpCardGapOpensAfterTenMinutes() = runTest {
        assertTrue(testee.tryClaim(PromptType.MODAL))
        testee.onClaimDone(PromptType.MODAL)
        coroutinesTestRule.testScope.testScheduler.advanceUntilIdle()

        now += TimeUnit.MINUTES.toMillis(9)
        assertFalse(testee.tryClaim(PromptType.NTP_CARD))

        now += TimeUnit.MINUTES.toMillis(1)
        assertTrue(testee.tryClaim(PromptType.NTP_CARD))
    }

    @Test
    fun whenNtpCardClaimDoneThenModalGapOpensAfterTwentyFourHours() = runTest {
        assertTrue(testee.tryClaim(PromptType.NTP_CARD))
        testee.onClaimDone(PromptType.NTP_CARD)
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
        assertTrue(testee.tryClaim(PromptType.NTP_CARD))
    }

    @Test
    fun whenDoneReportedByNonOwnerThenClaimIsUnaffected() = runTest {
        assertTrue(testee.tryClaim(PromptType.MODAL))

        testee.onClaimDone(PromptType.NTP_CARD)
        coroutinesTestRule.testScope.testScheduler.advanceUntilIdle()

        // MODAL still owns the surface, and no gap was stamped by the stale report.
        assertFalse(testee.tryClaim(PromptType.NTP_CARD))
        testee.onClaimCancelled(PromptType.MODAL)
        coroutinesTestRule.testScope.testScheduler.advanceUntilIdle()
        assertTrue(testee.tryClaim(PromptType.NTP_CARD))
    }

    @Test
    fun whenGapStampedThenItSurvivesANewInstanceReadingTheSameStore() = runTest {
        assertTrue(testee.tryClaim(PromptType.MODAL))
        testee.onClaimDone(PromptType.MODAL)
        coroutinesTestRule.testScope.testScheduler.advanceUntilIdle()

        // Simulates process death: in-memory owner and cache reset, timestamp persisted.
        val recreated = createTestee()
        now += TimeUnit.MINUTES.toMillis(5)
        assertFalse(recreated.tryClaim(PromptType.NTP_CARD))

        now += TimeUnit.MINUTES.toMillis(5)
        assertTrue(recreated.tryClaim(PromptType.NTP_CARD))
    }

    @Test
    fun whenSurfaceIsFreedWhileAClaimIsWaitingThenThatClaimIsGranted() = runTest {
        assertTrue(testee.tryClaim(PromptType.MODAL))

        val waiting = async { testee.tryClaim(PromptType.NTP_CARD) }
        // Let the claim reach the wait before the surface frees, so the wait is what grants it.
        coroutinesTestRule.testScope.testScheduler.runCurrent()
        testee.onClaimCancelled(PromptType.MODAL)

        assertTrue(waiting.await())
    }

    @Test
    fun whenTwoTypesWaitOnTheSameSurfaceThenExactlyOneIsGranted() = runTest {
        assertTrue(testee.tryClaim(PromptType.MODAL))

        val waitingCard = async { testee.tryClaim(PromptType.NTP_CARD) }
        val waitingModal = async { testee.tryClaim(PromptType.MODAL) }
        coroutinesTestRule.testScope.testScheduler.runCurrent()
        testee.onClaimCancelled(PromptType.MODAL)

        // Waiters are not a queue, so which one wins is undefined; what holds is that the released
        // surface goes to exactly one of them.
        assertTrue(waitingCard.await() != waitingModal.await())
    }

    @Test
    fun whenSeveralNtpCardClaimsWaitThenAllOfThemAreGranted() = runTest {
        assertTrue(testee.tryClaim(PromptType.MODAL))

        val firstCard = async { testee.tryClaim(PromptType.NTP_CARD) }
        val secondCard = async { testee.tryClaim(PromptType.NTP_CARD) }
        coroutinesTestRule.testScope.testScheduler.runCurrent()
        testee.onClaimCancelled(PromptType.MODAL)

        // Concurrent NTP renders never compete: whichever lands second takes the re-claim path.
        assertTrue(firstCard.await())
        assertTrue(secondCard.await())
    }

    @Test
    fun whenAShownPromptReleasesTheSurfaceWhileAClaimIsWaitingThenTheGapRefusesIt() = runTest {
        assertTrue(testee.tryClaim(PromptType.MODAL))

        val waiting = async { testee.tryClaim(PromptType.NTP_CARD) }
        coroutinesTestRule.testScope.testScheduler.runCurrent()
        testee.onClaimDone(PromptType.MODAL)

        // Waking up is not the same as winning: a modal that was actually shown stamps the quiet
        // gap as it releases, so the waiting card is refused by the gap that release just started.
        assertFalse(waiting.await())
    }

    @Test
    fun whenAClaimIsGrantedThenAReleaseReportCannotHandTheSurfaceToAModal() = runTest {
        assertTrue(testee.tryClaim(PromptType.NTP_CARD))
        testee.onClaimDone(PromptType.NTP_CARD)
        coroutinesTestRule.testScope.testScheduler.advanceUntilIdle()

        // The card goes away and a new message arrives once the gap has passed.
        now += TimeUnit.HOURS.toMillis(25)
        assertTrue(testee.tryClaim(PromptType.NTP_CARD))

        // The card cannot be told it holds the surface while a modal is let onto it too.
        assertFalse(testee.tryClaim(PromptType.MODAL))
    }

    @Test
    fun whenNtpCardHoldsTheClaimThenItDoesNotSurviveANewInstance() = runTest {
        assertTrue(testee.tryClaim(PromptType.NTP_CARD))

        // Simulates process death. No claim outlives the process that took it: nothing would be left
        // to release it, so the surface would stay blocked for good. The gap is persisted separately
        // and still applies, so a restart cannot turn this into back-to-back prompts.
        val recreated = createTestee()

        assertTrue(recreated.tryClaim(PromptType.MODAL))
    }

    @Test
    fun whenModalHoldsTheClaimThenItDoesNotSurviveANewInstance() = runTest {
        assertTrue(testee.tryClaim(PromptType.MODAL))

        val recreated = createTestee()

        assertTrue(recreated.tryClaim(PromptType.NTP_CARD))
    }

    @Test
    fun whenFirstPromptDoneThenGapPixelFiresAsFirst() = runTest {
        assertTrue(testee.tryClaim(PromptType.MODAL))
        testee.onClaimDone(PromptType.MODAL)

        verifyGapPixel(gap = "first", type = "MODAL")
    }

    @Test
    fun whenPromptDoneAfterAPreviousOneThenGapPixelCarriesTheElapsedBucketAndType() = runTest {
        assertTrue(testee.tryClaim(PromptType.MODAL))
        testee.onClaimDone(PromptType.MODAL)

        now += TimeUnit.HOURS.toMillis(30)
        assertTrue(testee.tryClaim(PromptType.NTP_CARD))
        testee.onClaimDone(PromptType.NTP_CARD)

        verifyGapPixel(gap = "24_48h", type = "NTP_CARD")
    }

    @Test
    fun whenStampSurvivesARestartThenTheGapIsMeasuredFromThePersistedStamp() = runTest {
        assertTrue(testee.tryClaim(PromptType.MODAL))
        testee.onClaimDone(PromptType.MODAL)
        coroutinesTestRule.testScope.testScheduler.advanceUntilIdle()

        val recreated = createTestee()
        now += TimeUnit.MINUTES.toMillis(15)
        assertTrue(recreated.tryClaim(PromptType.NTP_CARD))
        recreated.onClaimDone(PromptType.NTP_CARD)

        verifyGapPixel(gap = "10_30m", type = "NTP_CARD")
    }

    @Test
    fun whenClockMovedBackThenNoGapPixelFiresButThePromptIsStillStamped() = runTest {
        assertTrue(testee.tryClaim(PromptType.NTP_CARD))
        testee.onClaimDone(PromptType.NTP_CARD)

        now += TimeUnit.MINUTES.toMillis(11)
        assertTrue(testee.tryClaim(PromptType.NTP_CARD))
        // The card is still displayed when the clock is set back past the previous stamp.
        now -= TimeUnit.MINUTES.toMillis(20)
        testee.onClaimDone(PromptType.NTP_CARD)

        // Only the first prompt's pixel: the second, negative gap is dropped.
        verify(pixel).fire(eq(PromptExposurePixelName.PROMPT_GAP), any(), any(), any())
        // Stamped at the moved-back time, so the modal cooldown runs from there.
        now += TimeUnit.HOURS.toMillis(24) - 1
        assertFalse(testee.tryClaim(PromptType.MODAL))
    }

    @Test
    fun whenClaimCancelledThenNoGapPixelFires() = runTest {
        assertTrue(testee.tryClaim(PromptType.MODAL))
        testee.onClaimCancelled(PromptType.MODAL)

        verify(pixel, never()).fire(any<Pixel.PixelName>(), any(), any(), any())
    }

    @Test
    fun whenInstallAgeUnknownThenNoGapPixelFires() = runTest {
        whenever(appInstall.getInstallAge()).thenReturn(null)

        assertTrue(testee.tryClaim(PromptType.MODAL))
        testee.onClaimDone(PromptType.MODAL)

        verify(pixel, never()).fire(any<Pixel.PixelName>(), any(), any(), any())
    }

    private fun verifyGapPixel(gap: String, type: String) {
        verify(pixel).fire(
            PromptExposurePixelName.PROMPT_GAP,
            mapOf("days_since_install" to "d0_6", "gap_bucket" to gap, "prompt_type" to type),
        )
    }

    companion object {
        // Far from epoch so a fresh store (timestamp 0) never blocks the first prompt.
        private val START_TIME = TimeUnit.DAYS.toMillis(1000)
    }
}
