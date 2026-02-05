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

package com.duckduckgo.remote.messaging.impl.modal.evaluator

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.os.SystemClock
import com.duckduckgo.app.onboarding.OnboardingFlowChecker
import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.feature.toggles.api.FakeFeatureToggleFactory
import com.duckduckgo.feature.toggles.api.Toggle.State
import com.duckduckgo.modalcoordinator.api.ModalEvaluator
import com.duckduckgo.navigation.api.GlobalActivityStarter
import com.duckduckgo.remote.messaging.api.Action
import com.duckduckgo.remote.messaging.api.CardItem
import com.duckduckgo.remote.messaging.api.CardItemType
import com.duckduckgo.remote.messaging.api.Content
import com.duckduckgo.remote.messaging.api.RemoteMessage
import com.duckduckgo.remote.messaging.api.RemoteMessagingRepository
import com.duckduckgo.remote.messaging.api.Surface
import com.duckduckgo.remote.messaging.impl.RemoteMessagingFeatureToggles
import com.duckduckgo.remote.messaging.impl.store.ModalSurfaceStore
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.MockedStatic
import org.mockito.Mockito.mockStatic
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

@SuppressLint("DenyListedApi")
class RemoteMessageModalSurfaceEvaluatorImplTest {

    @get:Rule
    var coroutinesTestRule = CoroutineTestRule()

    private val mockRemoteMessagingRepository: RemoteMessagingRepository = mock()
    private val mockModalSurfaceStore: ModalSurfaceStore = mock()
    private val mockGlobalActivityStarter: GlobalActivityStarter = mock()
    private val mockApplicationContext: Context = mock()
    private var fakeRemoteMessagingFeatureToggles: RemoteMessagingFeatureToggles = FakeFeatureToggleFactory.create(
        RemoteMessagingFeatureToggles::class.java,
    )
    private val mockOnboardingFlowChecker: OnboardingFlowChecker = mock()
    private val mockIntent: Intent = mock()

    private lateinit var testee: RemoteMessageModalSurfaceEvaluatorImpl
    private lateinit var mockSystemClock: MockedStatic<SystemClock>

    @Before
    fun setUp() {
        // Mock the static SystemClock.elapsedRealtime() call
        mockSystemClock = mockStatic(SystemClock::class.java)
        mockSystemClock.`when`<Long> { SystemClock.elapsedRealtime() }.thenReturn(1000L * 60 * 60 * 10) // e.g., 10 hours

        testee = RemoteMessageModalSurfaceEvaluatorImpl(
            appCoroutineScope = coroutinesTestRule.testScope,
            remoteMessagingRepository = mockRemoteMessagingRepository,
            modalSurfaceStore = mockModalSurfaceStore,
            globalActivityStarter = mockGlobalActivityStarter,
            dispatchers = coroutinesTestRule.testDispatcherProvider,
            applicationContext = mockApplicationContext,
            remoteMessagingFeatureToggles = fakeRemoteMessagingFeatureToggles,
            onboardingFlowChecker = mockOnboardingFlowChecker,
        )
    }

    @After
    fun tearDown() {
        // Close the static mock after each test to avoid test interference
        mockSystemClock.close()
    }

    @Test
    fun whenRemoteMessagingFeatureIsDisabledThenEvaluationIsSkipped() = runTest {
        fakeRemoteMessagingFeatureToggles.self().setRawStoredState(State(false))

        val result = testee.evaluate()

        assertEquals(ModalEvaluator.EvaluationResult.Skipped, result)
        verify(mockRemoteMessagingRepository, never()).message()
    }

    @Test
    fun whenModalSurfaceFeatureIsDisabledThenEvaluationIsSkipped() = runTest {
        fakeRemoteMessagingFeatureToggles.self().setRawStoredState(State(true))
        fakeRemoteMessagingFeatureToggles.remoteMessageModalSurface().setRawStoredState(State(false))

        val result = testee.evaluate()

        assertEquals(ModalEvaluator.EvaluationResult.Skipped, result)
        verify(mockRemoteMessagingRepository, never()).message()
    }

    @Test
    fun whenOnboardingIsNotCompleteThenEvaluationIsSkipped() = runTest {
        givenFeatureTogglesEnabled()
        whenever(mockOnboardingFlowChecker.isOnboardingComplete()).thenReturn(false)

        val result = testee.evaluate()

        assertEquals(ModalEvaluator.EvaluationResult.Skipped, result)
        verify(mockRemoteMessagingRepository, never()).message()
    }

    @Test
    fun whenBackgroundTimeThresholdNotMetThenEvaluationIsSkipped() = runTest {
        givenFeatureTogglesEnabled()
        whenever(mockOnboardingFlowChecker.isOnboardingComplete()).thenReturn(true)
        whenever(mockModalSurfaceStore.getBackgroundedTimestamp()).thenReturn(null)

        val result = testee.evaluate()

        assertEquals(ModalEvaluator.EvaluationResult.Skipped, result)
        verify(mockRemoteMessagingRepository, never()).message()
    }

    @Test
    fun whenNoMessageAvailableThenEvaluationIsSkipped() = runTest {
        givenFeatureTogglesEnabled()
        givenOnboardingComplete()
        givenBackgroundThresholdMet()
        whenever(mockRemoteMessagingRepository.message()).thenReturn(null)

        val result = testee.evaluate()

        assertEquals(ModalEvaluator.EvaluationResult.Skipped, result)
    }

    @Test
    fun whenMessageDoesNotHaveModalSurfaceThenEvaluationIsSkipped() = runTest {
        givenFeatureTogglesEnabled()
        givenOnboardingComplete()
        givenBackgroundThresholdMet()
        val message = createRemoteMessage(surfaces = listOf(Surface.NEW_TAB_PAGE))
        whenever(mockRemoteMessagingRepository.message()).thenReturn(message)

        val result = testee.evaluate()

        assertEquals(ModalEvaluator.EvaluationResult.Skipped, result)
        verify(mockGlobalActivityStarter, never()).startIntent(any(), any<GlobalActivityStarter.ActivityParams>())
    }

    @Test
    fun whenMessageHasModalSurfaceButIntentIsNullThenEvaluationIsSkipped() = runTest {
        givenFeatureTogglesEnabled()
        givenOnboardingComplete()
        givenBackgroundThresholdMet()
        givenNoLastShownMessageId()
        val message = createRemoteMessage(surfaces = listOf(Surface.MODAL))
        whenever(mockRemoteMessagingRepository.message()).thenReturn(message)
        whenever(mockGlobalActivityStarter.startIntent(any(), any<GlobalActivityStarter.ActivityParams>())).thenReturn(null)

        val result = testee.evaluate()

        assertEquals(ModalEvaluator.EvaluationResult.Skipped, result)
        verify(mockApplicationContext, never()).startActivity(any())
    }

    @Test
    fun whenMessageHasModalSurfaceAndIntentAvailableThenActivityIsLaunchedAndModalShown() = runTest {
        givenFeatureTogglesEnabled()
        givenOnboardingComplete()
        givenBackgroundThresholdMet()
        givenNoLastShownMessageId()
        val message = createRemoteMessage(surfaces = listOf(Surface.MODAL))
        whenever(mockRemoteMessagingRepository.message()).thenReturn(message)
        whenever(mockGlobalActivityStarter.startIntent(any(), any<GlobalActivityStarter.ActivityParams>())).thenReturn(mockIntent)

        val result = testee.evaluate()

        assertEquals(ModalEvaluator.EvaluationResult.ModalShown, result)
        coroutinesTestRule.testScope.testScheduler.advanceUntilIdle()
        verify(mockApplicationContext).startActivity(mockIntent)
        verify(mockIntent).setFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
    }

    @Test
    fun whenMessageWasAlreadyShownThenEvaluationIsSkipped() = runTest {
        givenFeatureTogglesEnabled()
        givenOnboardingComplete()
        givenBackgroundThresholdMet()
        val messageId = "test-message-id"
        val message = createRemoteMessage(id = messageId, surfaces = listOf(Surface.MODAL))
        whenever(mockRemoteMessagingRepository.message()).thenReturn(message)
        whenever(mockModalSurfaceStore.getLastShownRemoteMessageId()).thenReturn(messageId)

        val result = testee.evaluate()

        assertEquals(ModalEvaluator.EvaluationResult.Skipped, result)
        verify(mockGlobalActivityStarter, never()).startIntent(any(), any<GlobalActivityStarter.ActivityParams>())
    }

    @Test
    fun whenMessageWasAlreadyShownThenMessageIsDismissed() = runTest {
        givenFeatureTogglesEnabled()
        givenOnboardingComplete()
        givenBackgroundThresholdMet()
        val messageId = "test-message-id"
        val message = createRemoteMessage(id = messageId, surfaces = listOf(Surface.MODAL))
        whenever(mockRemoteMessagingRepository.message()).thenReturn(message)
        whenever(mockModalSurfaceStore.getLastShownRemoteMessageId()).thenReturn(messageId)

        testee.evaluate()

        verify(mockRemoteMessagingRepository).dismissMessage(messageId)
    }

    @Test
    fun whenDifferentMessageIdThenMessageIsShown() = runTest {
        givenFeatureTogglesEnabled()
        givenOnboardingComplete()
        givenBackgroundThresholdMet()
        val previousMessageId = "old-message-id"
        val newMessageId = "new-message-id"
        val message = createRemoteMessage(id = newMessageId, surfaces = listOf(Surface.MODAL))
        whenever(mockRemoteMessagingRepository.message()).thenReturn(message)
        whenever(mockModalSurfaceStore.getLastShownRemoteMessageId()).thenReturn(previousMessageId)
        whenever(mockGlobalActivityStarter.startIntent(any(), any<GlobalActivityStarter.ActivityParams>())).thenReturn(mockIntent)

        val result = testee.evaluate()

        assertEquals(ModalEvaluator.EvaluationResult.ModalShown, result)
        coroutinesTestRule.testScope.testScheduler.advanceUntilIdle()
        verify(mockApplicationContext).startActivity(mockIntent)
    }

    @Test
    fun whenMessageIsShownThenMessageIdIsRecorded() = runTest {
        givenFeatureTogglesEnabled()
        givenOnboardingComplete()
        givenBackgroundThresholdMet()
        givenNoLastShownMessageId()
        val message = createRemoteMessage(id = "test-message-id", surfaces = listOf(Surface.MODAL))
        whenever(mockRemoteMessagingRepository.message()).thenReturn(message)
        whenever(mockGlobalActivityStarter.startIntent(any(), any<GlobalActivityStarter.ActivityParams>())).thenReturn(mockIntent)

        testee.evaluate()

        verify(mockModalSurfaceStore).recordLastShownRemoteMessage(message)
    }

    @Test
    fun whenBackgroundThresholdMetThenTimestampIsCleared() = runTest {
        givenFeatureTogglesEnabled()
        givenOnboardingComplete()
        givenBackgroundThresholdMet()
        givenNoLastShownMessageId()
        val message = createRemoteMessage(surfaces = listOf(Surface.MODAL))
        whenever(mockRemoteMessagingRepository.message()).thenReturn(message)
        whenever(mockGlobalActivityStarter.startIntent(any(), any<GlobalActivityStarter.ActivityParams>())).thenReturn(mockIntent)

        testee.evaluate()

        verify(mockModalSurfaceStore).clearBackgroundTimestamp()
    }

    @Test
    fun whenBackgroundThresholdNotMetDueToInsufficientTimeThenTimestampIsNotCleared() = runTest {
        givenFeatureTogglesEnabled()
        givenOnboardingComplete()
        // Set background timestamp to just 1 hour ago (threshold is 4 hours)
        val oneHourAgo = SystemClock.elapsedRealtime() - (1 * 60 * 60 * 1000L)
        whenever(mockModalSurfaceStore.getBackgroundedTimestamp()).thenReturn(oneHourAgo)

        val result = testee.evaluate()

        assertEquals(ModalEvaluator.EvaluationResult.Skipped, result)
        verify(mockModalSurfaceStore, never()).clearBackgroundTimestamp()
    }

    @Test
    fun whenMessageHasMultipleSurfacesIncludingModalThenActivityIsLaunched() = runTest {
        givenFeatureTogglesEnabled()
        givenOnboardingComplete()
        givenBackgroundThresholdMet()
        givenNoLastShownMessageId()
        val message = createRemoteMessage(surfaces = listOf(Surface.MODAL, Surface.NEW_TAB_PAGE))
        whenever(mockRemoteMessagingRepository.message()).thenReturn(message)
        whenever(mockGlobalActivityStarter.startIntent(any(), any<GlobalActivityStarter.ActivityParams>())).thenReturn(mockIntent)

        val result = testee.evaluate()

        assertEquals(ModalEvaluator.EvaluationResult.ModalShown, result)
        coroutinesTestRule.testScope.testScheduler.advanceUntilIdle()
        verify(mockApplicationContext).startActivity(mockIntent)
    }

    @Test
    fun evaluatorHasCorrectPriority() {
        assertEquals(1, testee.priority)
    }

    @Test
    fun evaluatorHasCorrectId() {
        assertEquals("remote_message_modal", testee.evaluatorId)
    }

    private fun givenFeatureTogglesEnabled() {
        fakeRemoteMessagingFeatureToggles.self().setRawStoredState(State(true))
        fakeRemoteMessagingFeatureToggles.remoteMessageModalSurface().setRawStoredState(State(true))
    }

    private suspend fun givenOnboardingComplete() {
        whenever(mockOnboardingFlowChecker.isOnboardingComplete()).thenReturn(true)
    }

    private suspend fun givenBackgroundThresholdMet() {
        // Set background timestamp to 5 hours ago (threshold is 4 hours)
        val fiveHoursAgo = SystemClock.elapsedRealtime() - (5 * 60 * 60 * 1000L)
        whenever(mockModalSurfaceStore.getBackgroundedTimestamp()).thenReturn(fiveHoursAgo)
    }

    private suspend fun givenNoLastShownMessageId() {
        whenever(mockModalSurfaceStore.getLastShownRemoteMessageId()).thenReturn(null)
    }

    private fun createRemoteMessage(
        id: String = "test-message-id",
        surfaces: List<Surface> = listOf(Surface.MODAL),
    ): RemoteMessage {
        return mock<RemoteMessage>().apply {
            whenever(this.id).thenReturn(id)
            whenever(this.surfaces).thenReturn(surfaces)
            whenever(this.content).thenReturn(
                Content.CardsList(
                    titleText = "test-title",
                    descriptionText = "test-description",
                    placeholder = Content.Placeholder.ANNOUNCE,
                    primaryActionText = "test-primary-action-text",
                    primaryAction = Action.Dismiss,
                    listItems = listOf(
                        CardItem.ListItem(
                            id = "item1",
                            type = CardItemType.TWO_LINE_LIST_ITEM,
                            placeholder = Content.Placeholder.DDG_ANNOUNCE,
                            titleText = "Card 1",
                            descriptionText = "Description 1",
                            primaryAction = Action.Dismiss,
                            matchingRules = emptyList(),
                            exclusionRules = emptyList(),
                        ),
                    ),
                ),
            )
        }
    }
}
