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

package com.duckduckgo.pir.impl.common.actions

import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.common.utils.plugins.PluginPoint
import com.duckduckgo.pir.impl.common.BrokerStepsParser.BrokerStep
import com.duckduckgo.pir.impl.common.BrokerStepsParser.BrokerStep.ScanStep
import com.duckduckgo.pir.impl.common.BrokerStepsParser.BrokerStepActions.ScanStepActions
import com.duckduckgo.pir.impl.common.PirJob.RunType
import com.duckduckgo.pir.impl.common.actions.EventHandler.Next
import com.duckduckgo.pir.impl.common.actions.PirActionsRunnerStateEngine.Event
import com.duckduckgo.pir.impl.common.actions.PirActionsRunnerStateEngine.PirStageStatus
import com.duckduckgo.pir.impl.common.actions.PirActionsRunnerStateEngine.SideEffect
import com.duckduckgo.pir.impl.common.actions.PirActionsRunnerStateEngine.State
import com.duckduckgo.pir.impl.models.Broker
import com.duckduckgo.pir.impl.models.ProfileQuery
import com.duckduckgo.pir.impl.pixels.PirStage
import com.duckduckgo.pir.impl.scripts.models.PirError
import com.duckduckgo.pir.impl.scripts.models.PirError.ActionError.JsActionFailed
import com.duckduckgo.pir.impl.scripts.models.PirScriptRequestData.UserProfile
import com.duckduckgo.pir.impl.scripts.models.PirSuccessResponse
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

@OptIn(ExperimentalCoroutinesApi::class)
class RealPirActionsRunnerStateEngineTest {

    @get:Rule
    val coroutineRule = CoroutineTestRule()

    private lateinit var testee: RealPirActionsRunnerStateEngine

    private val mockEventHandlers: PluginPoint<EventHandler> = mock()
    private val mockEventHandler: EventHandler = mock()

    private val testRunType = RunType.MANUAL
    private val testBrokerName = "test-broker"
    private val testUrl = "https://test-broker.com"

    private val testBroker = Broker(
        name = testBrokerName,
        fileName = "test-broker.json",
        url = testUrl,
        version = "1.0",
        parent = null,
        addedDatetime = 1000L,
        removedAt = 0L,
    )

    private val testProfileQuery = ProfileQuery(
        id = 123L,
        firstName = "John",
        lastName = "Doe",
        city = "New York",
        state = "NY",
        addresses = emptyList(),
        birthYear = 1990,
        fullName = "John Doe",
        age = 33,
        deprecated = false,
    )

    private val testScanStep = ScanStep(
        broker = testBroker,
        step = ScanStepActions(
            stepType = "scan",
            actions = emptyList(),
            scanType = "data",
        ),
    )

    private val testBrokerSteps: List<BrokerStep> = listOf(testScanStep)

    private val testState = State(
        runType = testRunType,
        brokerStepsToExecute = testBrokerSteps,
        profileQuery = testProfileQuery,
        stageStatus = PirStageStatus(
            currentStage = PirStage.OTHER,
            stageStartMs = 0L,
        ),
    )

    @Before
    fun setUp() {
        whenever(mockEventHandlers.getPlugins()).thenReturn(emptyList())
    }

    @Test
    fun whenEventDispatchedAndHandlerFoundThenHandlerIsInvoked() = runTest {
        val testEvent = Event.Started
        val nextState = testState.copy(currentBrokerStepIndex = 1)
        val next = Next(nextState = nextState)

        whenever(mockEventHandler.event).thenReturn(Event.Started::class)
        whenever(mockEventHandler.invoke(any(), any())).thenReturn(next)
        whenever(mockEventHandlers.getPlugins()).thenReturn(listOf(mockEventHandler))

        testee = createEngine()

        testee.dispatch(testEvent)
        advanceUntilIdle()

        verify(mockEventHandler).invoke(any(), any())
    }

    @Test
    fun whenEventDispatchedAndNoHandlerFoundThenNoException() = runTest {
        val testEvent = Event.Started
        whenever(mockEventHandlers.getPlugins()).thenReturn(emptyList())

        testee = createEngine()

        testee.dispatch(testEvent)
        advanceUntilIdle()

        // No exception should be thrown
    }

    @Test
    fun whenMultipleEventsDispatchedThenAllAreHandled() = runTest {
        val event1 = Event.Started
        val event2 = Event.ExecuteNextBrokerStep
        var invocationCount = 0

        val handler1: EventHandler = mock()
        val handler2: EventHandler = mock()

        whenever(handler1.event).thenReturn(Event.Started::class)
        whenever(handler1.invoke(any(), any())).thenAnswer {
            invocationCount++
            Next(nextState = testState)
        }

        whenever(handler2.event).thenReturn(Event.ExecuteNextBrokerStep::class)
        whenever(handler2.invoke(any(), any())).thenAnswer {
            invocationCount++
            Next(nextState = testState)
        }

        whenever(mockEventHandlers.getPlugins()).thenReturn(listOf(handler1, handler2))

        testee = createEngine()

        testee.dispatch(event1)
        advanceUntilIdle()
        testee.dispatch(event2)
        advanceUntilIdle()

        assertEquals(2, invocationCount)
    }

    @Test
    fun whenEventHandlerReturnsNextEventThenNextEventIsDispatched() = runTest {
        val firstEvent = Event.Started
        val nextEvent = Event.ExecuteNextBrokerStep
        var secondHandlerInvoked = false

        val handler1: EventHandler = mock()
        val handler2: EventHandler = mock()

        whenever(handler1.event).thenReturn(Event.Started::class)
        whenever(handler1.invoke(any(), any())).thenReturn(
            Next(
                nextState = testState,
                nextEvent = nextEvent,
            ),
        )

        whenever(handler2.event).thenReturn(Event.ExecuteNextBrokerStep::class)
        whenever(handler2.invoke(any(), any())).thenAnswer {
            secondHandlerInvoked = true
            Next(nextState = testState)
        }

        whenever(mockEventHandlers.getPlugins()).thenReturn(listOf(handler1, handler2))

        testee = createEngine()

        testee.dispatch(firstEvent)
        advanceUntilIdle()

        assertTrue(secondHandlerInvoked)
    }

    @Test
    fun whenEventHandlerReturnsNextEventChainThenAllEventsAreProcessed() = runTest {
        val invocationOrder = mutableListOf<String>()

        val handler1: EventHandler = mock()
        val handler2: EventHandler = mock()
        val handler3: EventHandler = mock()

        whenever(handler1.event).thenReturn(Event.Started::class)
        whenever(handler1.invoke(any(), any())).thenAnswer {
            invocationOrder.add("handler1")
            Next(
                nextState = testState,
                nextEvent = Event.ExecuteNextBrokerStep,
            )
        }

        whenever(handler2.event).thenReturn(Event.ExecuteNextBrokerStep::class)
        whenever(handler2.invoke(any(), any())).thenAnswer {
            invocationOrder.add("handler2")
            Next(
                nextState = testState,
                nextEvent = Event.ExecuteBrokerStepAction(UserProfile(testProfileQuery)),
            )
        }

        whenever(handler3.event).thenReturn(Event.ExecuteBrokerStepAction::class)
        whenever(handler3.invoke(any(), any())).thenAnswer {
            invocationOrder.add("handler3")
            Next(nextState = testState)
        }

        whenever(mockEventHandlers.getPlugins()).thenReturn(listOf(handler1, handler2, handler3))

        testee = createEngine()

        testee.dispatch(Event.Started)
        advanceUntilIdle()

        assertEquals(listOf("handler1", "handler2", "handler3"), invocationOrder)
    }

    @Test
    fun whenDifferentEventTypesDispatchedThenCorrectHandlerIsInvoked() = runTest {
        val startedHandler: EventHandler = mock()
        val errorHandler: EventHandler = mock()
        var startedHandlerInvoked = false
        var errorHandlerInvoked = false

        whenever(startedHandler.event).thenReturn(Event.Started::class)
        whenever(startedHandler.invoke(any(), any())).thenAnswer {
            startedHandlerInvoked = true
            Next(nextState = testState)
        }

        whenever(errorHandler.event).thenReturn(Event.ErrorReceived::class)
        whenever(errorHandler.invoke(any(), any())).thenAnswer {
            errorHandlerInvoked = true
            Next(nextState = testState)
        }

        whenever(mockEventHandlers.getPlugins()).thenReturn(listOf(startedHandler, errorHandler))

        testee = createEngine()

        testee.dispatch(Event.Started)
        advanceUntilIdle()

        assertTrue(startedHandlerInvoked)
        assertTrue(!errorHandlerInvoked)

        startedHandlerInvoked = false

        testee.dispatch(Event.ErrorReceived(PirError.Unknown("test")))
        advanceUntilIdle()

        assertTrue(!startedHandlerInvoked)
        assertTrue(errorHandlerInvoked)
    }

    @Test
    fun whenEventHandlerReturnsSideEffectThenSideEffectIsEmitted() = runTest {
        val testSideEffect = SideEffect.LoadUrl("https://example.com")
        val testEvent = Event.Started

        whenever(mockEventHandler.event).thenReturn(Event.Started::class)
        whenever(mockEventHandler.invoke(any(), any())).thenReturn(
            Next(
                nextState = testState,
                sideEffect = testSideEffect,
            ),
        )
        whenever(mockEventHandlers.getPlugins()).thenReturn(listOf(mockEventHandler))

        testee = createEngine()

        val sideEffects = mutableListOf<SideEffect>()
        val job = launch {
            testee.sideEffect.take(1).toList(sideEffects)
        }

        testee.dispatch(testEvent)
        advanceUntilIdle()
        job.join()

        assertEquals(1, sideEffects.size)
        assertEquals(testSideEffect, sideEffects[0])
    }

    @Test
    fun whenEventHandlerReturnsNoSideEffectThenNoSideEffectIsEmitted() = runTest {
        val testEvent = Event.Started

        whenever(mockEventHandler.event).thenReturn(Event.Started::class)
        whenever(mockEventHandler.invoke(any(), any())).thenReturn(
            Next(
                nextState = testState,
                sideEffect = null,
            ),
        )
        whenever(mockEventHandlers.getPlugins()).thenReturn(listOf(mockEventHandler))

        testee = createEngine()

        var sideEffectEmitted = false
        val job = launch {
            val result = testee.sideEffect.firstOrNull()
            assertTrue(result != null)
            sideEffectEmitted = true
        }

        testee.dispatch(testEvent)
        advanceTimeBy(100)

        assertTrue(!sideEffectEmitted)
        job.cancel()
    }

    @Test
    fun whenMultipleSideEffectsReturnedThenAllAreEmitted() = runTest {
        val sideEffect1 = SideEffect.LoadUrl("https://example1.com")
        val sideEffect2 = SideEffect.LoadUrl("https://example2.com")

        val handler1: EventHandler = mock()
        val handler2: EventHandler = mock()

        whenever(handler1.event).thenReturn(Event.Started::class)
        whenever(handler1.invoke(any(), any())).thenReturn(
            Next(
                nextState = testState,
                sideEffect = sideEffect1,
            ),
        )

        whenever(handler2.event).thenReturn(Event.ExecuteNextBrokerStep::class)
        whenever(handler2.invoke(any(), any())).thenReturn(
            Next(
                nextState = testState,
                sideEffect = sideEffect2,
            ),
        )

        whenever(mockEventHandlers.getPlugins()).thenReturn(listOf(handler1, handler2))

        testee = createEngine()

        val sideEffects = mutableListOf<SideEffect>()
        val job = launch {
            testee.sideEffect.take(2).toList(sideEffects)
        }

        testee.dispatch(Event.Started)
        advanceUntilIdle()
        testee.dispatch(Event.ExecuteNextBrokerStep)
        advanceUntilIdle()
        job.join()

        assertEquals(2, sideEffects.size)
        assertEquals(sideEffect1, sideEffects[0])
        assertEquals(sideEffect2, sideEffects[1])
    }

    @Test
    fun whenEventHandlerReturnsSideEffectAndNextEventThenBothAreProcessed() = runTest {
        val testSideEffect = SideEffect.LoadUrl("https://example.com")
        val nextEvent = Event.ExecuteNextBrokerStep
        var nextEventHandled = false

        val handler1: EventHandler = mock()
        val handler2: EventHandler = mock()

        whenever(handler1.event).thenReturn(Event.Started::class)
        whenever(handler1.invoke(any(), any())).thenReturn(
            Next(
                nextState = testState,
                sideEffect = testSideEffect,
                nextEvent = nextEvent,
            ),
        )

        whenever(handler2.event).thenReturn(Event.ExecuteNextBrokerStep::class)
        whenever(handler2.invoke(any(), any())).thenAnswer {
            nextEventHandled = true
            Next(nextState = testState)
        }

        whenever(mockEventHandlers.getPlugins()).thenReturn(listOf(handler1, handler2))

        testee = createEngine()

        val sideEffects = mutableListOf<SideEffect>()
        val job = launch {
            testee.sideEffect.take(1).toList(sideEffects)
        }

        testee.dispatch(Event.Started)
        advanceUntilIdle()
        job.join()

        assertEquals(1, sideEffects.size)
        assertEquals(testSideEffect, sideEffects[0])
        assertTrue(nextEventHandled)
    }

    @Test
    fun whenCompleteExecutionSideEffectEmittedThenItIsReceived() = runTest {
        val testSideEffect = SideEffect.CompleteExecution

        whenever(mockEventHandler.event).thenReturn(Event.Started::class)
        whenever(mockEventHandler.invoke(any(), any())).thenReturn(
            Next(
                nextState = testState,
                sideEffect = testSideEffect,
            ),
        )
        whenever(mockEventHandlers.getPlugins()).thenReturn(listOf(mockEventHandler))

        testee = createEngine()

        val sideEffects = mutableListOf<SideEffect>()
        val job = launch {
            testee.sideEffect.take(1).toList(sideEffects)
        }

        testee.dispatch(Event.Started)
        advanceUntilIdle()
        job.join()

        assertEquals(1, sideEffects.size)
        assertEquals(SideEffect.CompleteExecution, sideEffects[0])
    }

    @Test
    fun whenHandlerReturnsUpdatedStateThenStateIsUpdatedForNextEvent() = runTest {
        val updatedState = testState.copy(currentBrokerStepIndex = 1, currentActionIndex = 2)
        val capturedStates = mutableListOf<State>()

        val handler1: EventHandler = mock()
        val handler2: EventHandler = mock()

        whenever(handler1.event).thenReturn(Event.Started::class)
        whenever(handler1.invoke(any(), any())).thenAnswer { invocation ->
            val state = invocation.getArgument<State>(0)
            capturedStates.add(state)
            Next(
                nextState = updatedState,
                nextEvent = Event.ExecuteNextBrokerStep,
            )
        }

        whenever(handler2.event).thenReturn(Event.ExecuteNextBrokerStep::class)
        whenever(handler2.invoke(any(), any())).thenAnswer { invocation ->
            val state = invocation.getArgument<State>(0)
            capturedStates.add(state)
            Next(nextState = state)
        }

        whenever(mockEventHandlers.getPlugins()).thenReturn(listOf(handler1, handler2))

        testee = createEngine()

        testee.dispatch(Event.Started)
        advanceUntilIdle()

        assertEquals(2, capturedStates.size)
        assertEquals(0, capturedStates[0].currentBrokerStepIndex)
        assertEquals(1, capturedStates[1].currentBrokerStepIndex)
        assertEquals(2, capturedStates[1].currentActionIndex)
    }

    @Test
    fun whenStageStatusChangesAcrossEventsThenNewStageIsUsed() = runTest {
        val updatedStageStatus = PirStageStatus(
            currentStage = PirStage.CAPTCHA_PARSE,
            stageStartMs = 5000L,
        )
        val updatedState = testState.copy(stageStatus = updatedStageStatus)
        val capturedStates = mutableListOf<State>()

        val handler1: EventHandler = mock()
        val handler2: EventHandler = mock()

        whenever(handler1.event).thenReturn(Event.Started::class)
        whenever(handler1.invoke(any(), any())).thenAnswer { invocation ->
            val state = invocation.getArgument<State>(0)
            capturedStates.add(state)
            Next(
                nextState = updatedState,
                nextEvent = Event.ExecuteNextBrokerStep,
            )
        }

        whenever(handler2.event).thenReturn(Event.ExecuteNextBrokerStep::class)
        whenever(handler2.invoke(any(), any())).thenAnswer { invocation ->
            val state = invocation.getArgument<State>(0)
            capturedStates.add(state)
            Next(nextState = state)
        }

        whenever(mockEventHandlers.getPlugins()).thenReturn(listOf(handler1, handler2))

        testee = createEngine()

        testee.dispatch(Event.Started)
        advanceUntilIdle()

        assertEquals(2, capturedStates.size)
        assertEquals(PirStage.OTHER, capturedStates[0].stageStatus.currentStage)
        assertEquals(PirStage.CAPTCHA_PARSE, capturedStates[1].stageStatus.currentStage)
        assertEquals(5000L, capturedStates[1].stageStatus.stageStartMs)
    }

    @Test
    fun whenComplexEventFlowWithStateAndSideEffectsThenAllProcessedCorrectly() = runTest {
        val invocationOrder = mutableListOf<String>()
        val returnedSideEffects = mutableListOf<SideEffect?>()

        val handler1: EventHandler = mock()
        val handler2: EventHandler = mock()
        val handler3: EventHandler = mock()

        whenever(handler1.event).thenReturn(Event.Started::class)
        whenever(handler1.invoke(any(), any())).thenAnswer {
            invocationOrder.add("started")
            Next(
                nextState = testState.copy(currentBrokerStepIndex = 0),
                sideEffect = SideEffect.LoadUrl("https://step1.com"),
                nextEvent = Event.LoadUrlComplete("https://step1.com"),
            ).also { returnedSideEffects.add(it.sideEffect) }
        }

        whenever(handler2.event).thenReturn(Event.LoadUrlComplete::class)
        whenever(handler2.invoke(any(), any())).thenAnswer {
            invocationOrder.add("loadComplete")
            Next(
                nextState = testState.copy(currentBrokerStepIndex = 1),
                nextEvent = Event.ExecuteNextBrokerStep,
            ).also { returnedSideEffects.add(it.sideEffect) }
        }

        whenever(handler3.event).thenReturn(Event.ExecuteNextBrokerStep::class)
        whenever(handler3.invoke(any(), any())).thenAnswer {
            invocationOrder.add("executeNext")
            Next(
                nextState = testState.copy(currentBrokerStepIndex = 2),
                sideEffect = SideEffect.CompleteExecution,
            ).also { returnedSideEffects.add(it.sideEffect) }
        }

        whenever(mockEventHandlers.getPlugins()).thenReturn(listOf(handler1, handler2, handler3))

        testee = createEngine()

        testee.dispatch(Event.Started)
        advanceUntilIdle()

        assertEquals(listOf("started", "loadComplete", "executeNext"), invocationOrder)
        assertEquals(3, returnedSideEffects.size)
        assertTrue(returnedSideEffects[0] is SideEffect.LoadUrl)
        assertNull(returnedSideEffects[1])
        assertTrue(returnedSideEffects[2] is SideEffect.CompleteExecution)
    }

    @Test
    fun whenErrorEventReceivedInMiddleOfFlowThenProcessedCorrectly() = runTest {
        val invocationOrder = mutableListOf<String>()

        val handler1: EventHandler = mock()
        val handler2: EventHandler = mock()

        whenever(handler1.event).thenReturn(Event.Started::class)
        whenever(handler1.invoke(any(), any())).thenAnswer {
            invocationOrder.add("started")
            Next(
                nextState = testState,
                nextEvent = Event.ErrorReceived(PirError.Unknown("test error")),
            )
        }

        whenever(handler2.event).thenReturn(Event.ErrorReceived::class)
        whenever(handler2.invoke(any(), any())).thenAnswer {
            invocationOrder.add("errorReceived")
            Next(nextState = testState)
        }

        whenever(mockEventHandlers.getPlugins()).thenReturn(listOf(handler1, handler2))

        testee = createEngine()

        testee.dispatch(Event.Started)
        advanceUntilIdle()

        assertEquals(listOf("started", "errorReceived"), invocationOrder)
    }

    @Test
    fun whenNoEventHandlersRegisteredThenEventIsIgnored() = runTest {
        whenever(mockEventHandlers.getPlugins()).thenReturn(emptyList())
        testee = createEngine()

        testee.dispatch(Event.Started)
        advanceUntilIdle()
    }

    @Test
    fun whenMultipleHandlersForSameEventTypeThenFirstMatchingHandlerIsUsed() = runTest {
        var firstHandlerInvoked = false
        var secondHandlerInvoked = false

        val handler1: EventHandler = mock()
        val handler2: EventHandler = mock()

        whenever(handler1.event).thenReturn(Event.Started::class)
        whenever(handler1.invoke(any(), any())).thenAnswer {
            firstHandlerInvoked = true
            Next(nextState = testState)
        }

        whenever(handler2.event).thenReturn(Event.Started::class)
        whenever(handler2.invoke(any(), any())).thenAnswer {
            secondHandlerInvoked = true
            Next(nextState = testState)
        }

        whenever(mockEventHandlers.getPlugins()).thenReturn(listOf(handler1, handler2))

        testee = createEngine()

        testee.dispatch(Event.Started)
        advanceUntilIdle()

        assertTrue(firstHandlerInvoked)
        assertTrue(!secondHandlerInvoked)
    }

    @Test
    fun whenEventDispatchedBeforeEngineInitializedThenEventIsStillProcessed() = runTest {
        var handlerInvoked = false

        whenever(mockEventHandler.event).thenReturn(Event.Started::class)
        whenever(mockEventHandler.invoke(any(), any())).thenAnswer {
            handlerInvoked = true
            Next(nextState = testState)
        }
        whenever(mockEventHandlers.getPlugins()).thenReturn(listOf(mockEventHandler))

        testee = createEngine()
        testee.dispatch(Event.Started)
        advanceUntilIdle()

        assertTrue(handlerInvoked)
    }

    @Test
    fun whenRapidEventDispatchesThenAllEventsAreProcessed() = runTest {
        var invocationCount = 0

        whenever(mockEventHandler.event).thenReturn(Event.ExecuteNextBrokerStep::class)
        whenever(mockEventHandler.invoke(any(), any())).thenAnswer {
            invocationCount++
            Next(nextState = testState)
        }
        whenever(mockEventHandlers.getPlugins()).thenReturn(listOf(mockEventHandler))

        testee = createEngine()

        repeat(10) {
            testee.dispatch(Event.ExecuteNextBrokerStep)
        }
        advanceUntilIdle()

        assertEquals(10, invocationCount)
    }

    @Test
    fun whenDifferentComplexEventTypesThenCorrectHandling() = runTest {
        val jsActionSuccessHandler: EventHandler = mock()
        val brokerStepCompletedHandler: EventHandler = mock()
        val loadUrlFailedHandler: EventHandler = mock()

        val handlerInvocations = mutableListOf<String>()

        whenever(jsActionSuccessHandler.event).thenReturn(Event.JsActionSuccess::class)
        whenever(jsActionSuccessHandler.invoke(any(), any())).thenAnswer {
            handlerInvocations.add("jsActionSuccess")
            Next(nextState = testState)
        }

        whenever(brokerStepCompletedHandler.event).thenReturn(Event.BrokerStepCompleted::class)
        whenever(brokerStepCompletedHandler.invoke(any(), any())).thenAnswer {
            handlerInvocations.add("brokerStepCompleted")
            Next(nextState = testState)
        }

        whenever(loadUrlFailedHandler.event).thenReturn(Event.LoadUrlFailed::class)
        whenever(loadUrlFailedHandler.invoke(any(), any())).thenAnswer {
            handlerInvocations.add("loadUrlFailed")
            Next(nextState = testState)
        }

        whenever(mockEventHandlers.getPlugins()).thenReturn(
            listOf(
                jsActionSuccessHandler,
                brokerStepCompletedHandler,
                loadUrlFailedHandler,
            ),
        )

        testee = createEngine()

        testee.dispatch(
            Event.JsActionSuccess(
                PirSuccessResponse.NavigateResponse(
                    actionID = "test",
                    actionType = "navigate",
                    response = PirSuccessResponse.NavigateResponse.ResponseData(url = "https://test.com"),
                ),
            ),
        )
        advanceUntilIdle()

        testee.dispatch(
            Event.BrokerStepCompleted(
                needsEmailConfirmation = false,
                stepStatus = Event.BrokerStepCompleted.StepStatus.Success,
            ),
        )
        advanceUntilIdle()

        testee.dispatch(Event.LoadUrlFailed("https://test.com"))
        advanceUntilIdle()

        assertEquals(3, handlerInvocations.size)
        assertEquals("jsActionSuccess", handlerInvocations[0])
        assertEquals("brokerStepCompleted", handlerInvocations[1])
        assertEquals("loadUrlFailed", handlerInvocations[2])
    }

    @Test
    fun whenRetryAwaitCaptchaSolutionEventThenHandlerInvoked() = runTest {
        var handlerInvoked = false
        val retryEvent = Event.RetryAwaitCaptchaSolution(
            actionId = "captcha-1",
            brokerName = testBrokerName,
            transactionID = "tx-123",
            attempt = 1,
        )

        whenever(mockEventHandler.event).thenReturn(Event.RetryAwaitCaptchaSolution::class)
        whenever(mockEventHandler.invoke(any(), any())).thenAnswer {
            handlerInvoked = true
            Next(nextState = testState)
        }
        whenever(mockEventHandlers.getPlugins()).thenReturn(listOf(mockEventHandler))

        testee = createEngine()

        testee.dispatch(retryEvent)
        advanceUntilIdle()

        assertTrue(handlerInvoked)
    }

    @Test
    fun whenCaptchaInfoReceivedEventThenHandlerInvoked() = runTest {
        var handlerInvoked = false
        val captchaEvent = Event.CaptchaInfoReceived(transactionID = "tx-123")

        whenever(mockEventHandler.event).thenReturn(Event.CaptchaInfoReceived::class)
        whenever(mockEventHandler.invoke(any(), any())).thenAnswer {
            handlerInvoked = true
            Next(nextState = testState)
        }
        whenever(mockEventHandlers.getPlugins()).thenReturn(listOf(mockEventHandler))

        testee = createEngine()

        testee.dispatch(captchaEvent)
        advanceUntilIdle()

        assertTrue(handlerInvoked)
    }

    @Test
    fun whenRetryGetCaptchaSolutionEventThenHandlerInvoked() = runTest {
        var handlerInvoked = false
        val retryEvent = Event.RetryGetCaptchaSolution(
            actionId = "captcha-1",
            responseData = null,
        )

        whenever(mockEventHandler.event).thenReturn(Event.RetryGetCaptchaSolution::class)
        whenever(mockEventHandler.invoke(any(), any())).thenAnswer {
            handlerInvoked = true
            Next(nextState = testState)
        }
        whenever(mockEventHandlers.getPlugins()).thenReturn(listOf(mockEventHandler))

        testee = createEngine()

        testee.dispatch(retryEvent)
        advanceUntilIdle()

        assertTrue(handlerInvoked)
    }

    @Test
    fun whenBrokerActionFailedEventThenHandlerInvoked() = runTest {
        var handlerInvoked = false
        val failedEvent = Event.BrokerActionFailed(
            error = JsActionFailed("action-1", "Failed"),
            allowRetry = true,
        )

        whenever(mockEventHandler.event).thenReturn(Event.BrokerActionFailed::class)
        whenever(mockEventHandler.invoke(any(), any())).thenAnswer {
            handlerInvoked = true
            Next(nextState = testState)
        }
        whenever(mockEventHandlers.getPlugins()).thenReturn(listOf(mockEventHandler))

        testee = createEngine()

        testee.dispatch(failedEvent)
        advanceUntilIdle()

        assertTrue(handlerInvoked)
    }

    @Test
    fun whenConditionExpectationSucceededEventThenHandlerInvoked() = runTest {
        var handlerInvoked = false
        val conditionEvent = Event.ConditionExpectationSucceeded(
            conditionActions = emptyList(),
        )

        whenever(mockEventHandler.event).thenReturn(Event.ConditionExpectationSucceeded::class)
        whenever(mockEventHandler.invoke(any(), any())).thenAnswer {
            handlerInvoked = true
            Next(nextState = testState)
        }
        whenever(mockEventHandlers.getPlugins()).thenReturn(listOf(mockEventHandler))

        testee = createEngine()

        testee.dispatch(conditionEvent)
        advanceUntilIdle()

        assertTrue(handlerInvoked)
    }

    @Test
    fun whenPushJsActionSideEffectEmittedThenItIsReceived() = runTest {
        val testAction = com.duckduckgo.pir.impl.scripts.models.BrokerAction.Navigate(
            id = "action-1",
            url = "https://test.com",
        )
        val testSideEffect = SideEffect.PushJsAction(
            actionId = "action-1",
            action = testAction,
            pushDelay = 100L,
            requestParamsData = UserProfile(testProfileQuery),
        )

        whenever(mockEventHandler.event).thenReturn(Event.Started::class)
        whenever(mockEventHandler.invoke(any(), any())).thenReturn(
            Next(
                nextState = testState,
                sideEffect = testSideEffect,
            ),
        )
        whenever(mockEventHandlers.getPlugins()).thenReturn(listOf(mockEventHandler))

        testee = createEngine()

        val sideEffects = mutableListOf<SideEffect>()
        val job = launch {
            testee.sideEffect.take(1).toList(sideEffects)
        }

        testee.dispatch(Event.Started)
        advanceUntilIdle()
        job.join()

        assertEquals(1, sideEffects.size)
        assertTrue(sideEffects[0] is SideEffect.PushJsAction)
        assertEquals("action-1", (sideEffects[0] as SideEffect.PushJsAction).actionId)
    }

    @Test
    fun whenGetEmailForProfileSideEffectEmittedThenItIsReceived() = runTest {
        val testSideEffect = SideEffect.GetEmailForProfile(
            actionId = "action-1",
            brokerName = testBrokerName,
            extractedProfile = mock(),
            profileQuery = testProfileQuery,
        )

        whenever(mockEventHandler.event).thenReturn(Event.Started::class)
        whenever(mockEventHandler.invoke(any(), any())).thenReturn(
            Next(
                nextState = testState,
                sideEffect = testSideEffect,
            ),
        )
        whenever(mockEventHandlers.getPlugins()).thenReturn(listOf(mockEventHandler))

        testee = createEngine()

        val sideEffects = mutableListOf<SideEffect>()
        val job = launch {
            testee.sideEffect.take(1).toList(sideEffects)
        }

        testee.dispatch(Event.Started)
        advanceUntilIdle()
        job.join()

        assertEquals(1, sideEffects.size)
        assertTrue(sideEffects[0] is SideEffect.GetEmailForProfile)
    }

    @Test
    fun whenGetCaptchaSolutionSideEffectEmittedThenItIsReceived() = runTest {
        val testSideEffect = SideEffect.GetCaptchaSolution(
            actionId = "action-1",
            responseData = null,
            isRetry = false,
        )

        whenever(mockEventHandler.event).thenReturn(Event.Started::class)
        whenever(mockEventHandler.invoke(any(), any())).thenReturn(
            Next(
                nextState = testState,
                sideEffect = testSideEffect,
            ),
        )
        whenever(mockEventHandlers.getPlugins()).thenReturn(listOf(mockEventHandler))

        testee = createEngine()

        val sideEffects = mutableListOf<SideEffect>()
        val job = launch {
            testee.sideEffect.take(1).toList(sideEffects)
        }

        testee.dispatch(Event.Started)
        advanceUntilIdle()
        job.join()

        assertEquals(1, sideEffects.size)
        assertTrue(sideEffects[0] is SideEffect.GetCaptchaSolution)
    }

    @Test
    fun whenAwaitCaptchaSolutionSideEffectEmittedThenItIsReceived() = runTest {
        val testSideEffect = SideEffect.AwaitCaptchaSolution(
            actionId = "action-1",
            brokerName = testBrokerName,
            transactionID = "tx-123",
            pollingIntervalSeconds = 5,
            retries = 50,
            attempt = 0,
        )

        whenever(mockEventHandler.event).thenReturn(Event.Started::class)
        whenever(mockEventHandler.invoke(any(), any())).thenReturn(
            Next(
                nextState = testState,
                sideEffect = testSideEffect,
            ),
        )
        whenever(mockEventHandlers.getPlugins()).thenReturn(listOf(mockEventHandler))

        testee = createEngine()

        val sideEffects = mutableListOf<SideEffect>()
        val job = launch {
            testee.sideEffect.take(1).toList(sideEffects)
        }

        testee.dispatch(Event.Started)
        advanceUntilIdle()
        job.join()

        assertEquals(1, sideEffects.size)
        assertTrue(sideEffects[0] is SideEffect.AwaitCaptchaSolution)
    }

    @Test
    fun whenEvaluateJsSideEffectEmittedThenItIsReceived() = runTest {
        val testSideEffect = SideEffect.EvaluateJs(callback = "window.test()")

        whenever(mockEventHandler.event).thenReturn(Event.Started::class)
        whenever(mockEventHandler.invoke(any(), any())).thenReturn(
            Next(
                nextState = testState,
                sideEffect = testSideEffect,
            ),
        )
        whenever(mockEventHandlers.getPlugins()).thenReturn(listOf(mockEventHandler))

        testee = createEngine()

        val sideEffects = mutableListOf<SideEffect>()
        val job = launch {
            testee.sideEffect.take(1).toList(sideEffects)
        }

        testee.dispatch(Event.Started)
        advanceUntilIdle()
        job.join()

        assertEquals(1, sideEffects.size)
        assertTrue(sideEffects[0] is SideEffect.EvaluateJs)
        assertEquals("window.test()", (sideEffects[0] as SideEffect.EvaluateJs).callback)
    }

    private fun createEngine(
        eventHandlers: PluginPoint<EventHandler> = mockEventHandlers,
        runType: RunType = testRunType,
        brokerSteps: List<BrokerStep> = testBrokerSteps,
        profileQuery: ProfileQuery = testProfileQuery,
    ): RealPirActionsRunnerStateEngine {
        return RealPirActionsRunnerStateEngine(
            eventHandlers = eventHandlers,
            coroutineScope = coroutineRule.testScope,
            dispatcherProvider = coroutineRule.testDispatcherProvider,
            runType = runType,
            brokerSteps = brokerSteps,
            profileQuery = profileQuery,
        )
    }
}
