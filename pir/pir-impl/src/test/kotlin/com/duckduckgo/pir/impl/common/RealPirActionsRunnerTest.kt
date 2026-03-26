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

package com.duckduckgo.pir.impl.common

import android.content.Context
import android.webkit.WebView
import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.pir.impl.common.BrokerStepsParser.BrokerStep
import com.duckduckgo.pir.impl.common.BrokerStepsParser.BrokerStep.ScanStep
import com.duckduckgo.pir.impl.common.BrokerStepsParser.BrokerStepActions.ScanStepActions
import com.duckduckgo.pir.impl.common.PirJob.RunType
import com.duckduckgo.pir.impl.common.actions.PirActionsRunnerStateEngine
import com.duckduckgo.pir.impl.common.actions.PirActionsRunnerStateEngine.Event
import com.duckduckgo.pir.impl.common.actions.PirActionsRunnerStateEngine.SideEffect
import com.duckduckgo.pir.impl.common.actions.PirActionsRunnerStateEngineFactory
import com.duckduckgo.pir.impl.models.Broker
import com.duckduckgo.pir.impl.models.ProfileQuery
import com.duckduckgo.pir.impl.scripts.BrokerActionProcessor
import com.duckduckgo.pir.impl.scripts.models.PirError
import com.duckduckgo.pir.impl.scripts.models.PirSuccessResponse
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.yield
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.whenever

@OptIn(ExperimentalCoroutinesApi::class)
class RealPirActionsRunnerTest {

    @get:Rule
    val coroutineRule = CoroutineTestRule()

    private lateinit var testee: RealPirActionsRunner

    private val mockContext: Context = mock()
    private val mockPirDetachedWebViewProvider: PirDetachedWebViewProvider = mock()
    private val mockBrokerActionProcessor: BrokerActionProcessor = mock()
    private val mockNativeBrokerActionHandler: NativeBrokerActionHandler = mock()
    private val mockEngineFactory: PirActionsRunnerStateEngineFactory = mock()
    private val mockEngine: PirActionsRunnerStateEngine = mock()
    private val mockWebView: WebView = mock()

    private val sideEffectFlow = MutableSharedFlow<SideEffect>(extraBufferCapacity = 10)

    private val testRunType = RunType.MANUAL
    private val testPirScript = "test-pir-script"
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

    private val testSuccessResponse = PirSuccessResponse.NavigateResponse(
        actionID = "test-action",
        actionType = "navigate",
        response = PirSuccessResponse.NavigateResponse.ResponseData(url = "https://test.com"),
    )

    @Before
    fun setUp() {
        whenever(mockEngine.sideEffect).thenReturn(sideEffectFlow)
        whenever(mockEngineFactory.create(any(), any(), any())).thenReturn(mockEngine)
        testee = RealPirActionsRunner(
            dispatcherProvider = coroutineRule.testDispatcherProvider,
            pirDetachedWebViewProvider = mockPirDetachedWebViewProvider,
            brokerActionProcessor = mockBrokerActionProcessor,
            nativeBrokerActionHandler = mockNativeBrokerActionHandler,
            engineFactory = mockEngineFactory,
            coroutineScope = coroutineRule.testScope,
            runType = testRunType,
            context = mockContext,
            pirScriptToLoad = testPirScript,
        )
    }

    @Test
    fun whenStartWithEmptyBrokerStepsThenReturnsSuccessImmediately() = runTest {
        val result = testee.start(testProfileQuery, emptyList())

        assertTrue(result.isSuccess)
        verifyNoInteractions(mockPirDetachedWebViewProvider)
        verifyNoInteractions(mockBrokerActionProcessor)
        verifyNoInteractions(mockEngineFactory)
    }

    @Test
    fun whenStartWithBrokerStepsThenCreatesDetachedWebView() = runTest {
        whenever(mockPirDetachedWebViewProvider.createInstance(any(), any(), any(), any()))
            .thenReturn(mockWebView)

        // Start the runner asynchronously
        val deferred = async {
            testee.start(testProfileQuery, testBrokerSteps)
        }

        // Allow the async block to reach the flow collection point
        yield()

        // Emit completion to allow the start to finish
        sideEffectFlow.tryEmit(SideEffect.CompleteExecution)

        // Wait for start to complete
        deferred.await()

        verify(mockPirDetachedWebViewProvider).createInstance(
            eq(mockContext),
            eq(testPirScript),
            any(),
            any(),
        )
    }

    @Test
    fun whenStartWithBrokerStepsThenRegistersBrokerActionProcessor() = runTest {
        whenever(mockPirDetachedWebViewProvider.createInstance(any(), any(), any(), any()))
            .thenReturn(mockWebView)

        val deferred = async {
            testee.start(testProfileQuery, testBrokerSteps)
        }

        yield()

        sideEffectFlow.tryEmit(SideEffect.CompleteExecution)

        deferred.await()

        verify(mockBrokerActionProcessor).register(mockWebView, testee)
    }

    @Test
    fun whenStartWithBrokerStepsThenCreatesEngineAndDispatchesStarted() = runTest {
        whenever(mockPirDetachedWebViewProvider.createInstance(any(), any(), any(), any()))
            .thenReturn(mockWebView)

        val deferred = async {
            testee.start(testProfileQuery, testBrokerSteps)
        }

        yield()

        sideEffectFlow.tryEmit(SideEffect.CompleteExecution)

        deferred.await()

        verify(mockEngineFactory).create(testRunType, testBrokerSteps, testProfileQuery)
        verify(mockEngine).dispatch(Event.Started)
    }

    @Test
    fun whenStartAndCompleteExecutionReceivedThenReturnsSuccess() = runTest {
        whenever(mockPirDetachedWebViewProvider.createInstance(any(), any(), any(), any()))
            .thenReturn(mockWebView)

        val deferred = async {
            testee.start(testProfileQuery, testBrokerSteps)
        }

        yield()

        sideEffectFlow.tryEmit(SideEffect.CompleteExecution)

        val result = deferred.await()

        assertTrue(result.isSuccess)
    }

    @Test
    fun whenStartOnWithEmptyBrokerStepsThenReturnsSuccessImmediately() = runTest {
        val result = testee.startOn(mockWebView, testProfileQuery, emptyList())

        assertTrue(result.isSuccess)
        verifyNoInteractions(mockPirDetachedWebViewProvider)
        verifyNoInteractions(mockBrokerActionProcessor)
        verifyNoInteractions(mockEngineFactory)
    }

    @Test
    fun whenStartOnWithBrokerStepsThenSetupsWebView() = runTest {
        whenever(mockPirDetachedWebViewProvider.setupWebView(any(), any(), any(), any()))
            .thenReturn(mockWebView)

        val deferred = async {
            testee.startOn(mockWebView, testProfileQuery, testBrokerSteps)
        }

        yield()

        sideEffectFlow.tryEmit(SideEffect.CompleteExecution)

        deferred.await()

        verify(mockPirDetachedWebViewProvider).setupWebView(
            eq(mockWebView),
            eq(testPirScript),
            any(),
            any(),
        )
    }

    @Test
    fun whenOnSuccessThenDispatchesJsActionSuccessEvent() = runTest {
        whenever(mockPirDetachedWebViewProvider.createInstance(any(), any(), any(), any()))
            .thenReturn(mockWebView)

        val deferred = async {
            testee.start(testProfileQuery, testBrokerSteps)
        }

        yield()

        testee.onSuccess(testSuccessResponse)

        sideEffectFlow.tryEmit(SideEffect.CompleteExecution)

        deferred.await()

        val eventCaptor = argumentCaptor<Event>()
        verify(mockEngine, times(2)).dispatch(eventCaptor.capture())

        assertTrue(eventCaptor.allValues[1] is Event.JsActionSuccess)
        assertEquals(testSuccessResponse, (eventCaptor.allValues[1] as Event.JsActionSuccess).pirSuccessResponse)
    }

    @Test
    fun whenOnErrorWithActionFailedThenDispatchesBrokerActionFailedWithRetry() = runTest {
        val testError = PirError.ActionError.JsActionFailed("action-123", "Action execution failed")
        whenever(mockPirDetachedWebViewProvider.createInstance(any(), any(), any(), any()))
            .thenReturn(mockWebView)

        val deferred = async {
            testee.start(testProfileQuery, testBrokerSteps)
        }

        yield()

        testee.onError(testError)

        sideEffectFlow.tryEmit(SideEffect.CompleteExecution)

        deferred.await()

        val eventCaptor = argumentCaptor<Event>()
        verify(mockEngine, times(2)).dispatch(eventCaptor.capture())

        assertTrue(eventCaptor.allValues[1] is Event.BrokerActionFailed)
        val failedEvent = eventCaptor.allValues[1] as Event.BrokerActionFailed
        assertEquals(testError, failedEvent.error)
        assertTrue(failedEvent.allowRetry)
    }

    @Test
    fun whenOnErrorWithJsErrorThenDispatchesErrorReceived() = runTest {
        val testError = PirError.JsError.ActionError("Javascript error")
        whenever(mockPirDetachedWebViewProvider.createInstance(any(), any(), any(), any()))
            .thenReturn(mockWebView)

        val deferred = async {
            testee.start(testProfileQuery, testBrokerSteps)
        }

        yield()

        testee.onError(testError)

        sideEffectFlow.tryEmit(SideEffect.CompleteExecution)

        deferred.await()

        val eventCaptor = argumentCaptor<Event>()
        verify(mockEngine, times(2)).dispatch(eventCaptor.capture())

        assertTrue(eventCaptor.allValues[1] is Event.ErrorReceived)
        assertEquals(testError, (eventCaptor.allValues[1] as Event.ErrorReceived).error)
    }

    @Test
    fun whenOnErrorWithCaptchaSolutionFailedThenDispatchesBrokerActionFailedWithoutRetry() = runTest {
        val testError = PirError.ActionError.CaptchaSolutionFailed("action-123", "Captcha solution failed")
        whenever(mockPirDetachedWebViewProvider.createInstance(any(), any(), any(), any()))
            .thenReturn(mockWebView)

        val deferred = async {
            testee.start(testProfileQuery, testBrokerSteps)
        }

        yield()

        testee.onError(testError)

        sideEffectFlow.tryEmit(SideEffect.CompleteExecution)

        deferred.await()

        val eventCaptor = argumentCaptor<Event>()
        verify(mockEngine, times(2)).dispatch(eventCaptor.capture())

        assertTrue(eventCaptor.allValues[1] is Event.BrokerActionFailed)
        val failedEvent = eventCaptor.allValues[1] as Event.BrokerActionFailed
        assertEquals(testError, failedEvent.error)
        assertTrue(!failedEvent.allowRetry)
    }

    @Test
    fun whenOnErrorWithUnknownErrorThenDoesNotDispatchEvent() = runTest {
        val testError = PirError.Unknown("Unknown error")
        whenever(mockPirDetachedWebViewProvider.createInstance(any(), any(), any(), any()))
            .thenReturn(mockWebView)

        val deferred = async {
            testee.start(testProfileQuery, testBrokerSteps)
        }

        yield()

        testee.onError(testError)

        sideEffectFlow.tryEmit(SideEffect.CompleteExecution)

        deferred.await()

        val eventCaptor = argumentCaptor<Event>()
        verify(mockEngine, times(1)).dispatch(eventCaptor.capture())

        assertTrue(eventCaptor.allValues[0] is Event.Started)
    }

    @Test
    fun whenStopThenCleansUpWebView() = runTest {
        whenever(mockPirDetachedWebViewProvider.createInstance(any(), any(), any(), any()))
            .thenReturn(mockWebView)

        val job = async {
            testee.start(testProfileQuery, testBrokerSteps)
        }

        yield()

        testee.stop()

        job.cancel()

        verify(mockWebView).stopLoading()
        verify(mockWebView).loadUrl("about:blank")
        verify(mockWebView).evaluateJavascript("window.stop();", null)
        verify(mockWebView).destroy()
    }

    @Test
    fun whenOnLoadingCompleteWithUrlThenDispatchesLoadUrlComplete() = runTest {
        val loadedUrl = "https://example.com"
        whenever(mockPirDetachedWebViewProvider.createInstance(any(), any(), any(), any()))
            .thenReturn(mockWebView)

        val deferred = async {
            testee.start(testProfileQuery, testBrokerSteps)
        }

        yield()

        val callbackCaptor = argumentCaptor<(String?) -> Unit>()
        verify(mockPirDetachedWebViewProvider).createInstance(any(), any(), callbackCaptor.capture(), any())
        callbackCaptor.firstValue.invoke(loadedUrl)

        sideEffectFlow.tryEmit(SideEffect.CompleteExecution)

        deferred.await()

        val eventCaptor = argumentCaptor<Event>()
        verify(mockEngine, times(2)).dispatch(eventCaptor.capture())

        assertTrue(eventCaptor.allValues[1] is Event.LoadUrlComplete)
        assertEquals(loadedUrl, (eventCaptor.allValues[1] as Event.LoadUrlComplete).url)
    }

    @Test
    fun whenOnLoadingCompleteWithNullUrlThenDoesNotDispatchEvent() = runTest {
        whenever(mockPirDetachedWebViewProvider.createInstance(any(), any(), any(), any()))
            .thenReturn(mockWebView)

        val deferred = async {
            testee.start(testProfileQuery, testBrokerSteps)
        }

        yield()

        val callbackCaptor = argumentCaptor<(String?) -> Unit>()
        verify(mockPirDetachedWebViewProvider).createInstance(any(), any(), callbackCaptor.capture(), any())
        callbackCaptor.firstValue.invoke(null)

        sideEffectFlow.tryEmit(SideEffect.CompleteExecution)

        deferred.await()

        val eventCaptor = argumentCaptor<Event>()
        verify(mockEngine, times(1)).dispatch(eventCaptor.capture())

        assertTrue(eventCaptor.allValues[0] is Event.Started)
    }

    @Test
    fun whenOnLoadingFailedWithUrlThenDispatchesLoadUrlFailed() = runTest {
        val failedUrl = "https://example.com"
        whenever(mockPirDetachedWebViewProvider.createInstance(any(), any(), any(), any()))
            .thenReturn(mockWebView)

        val deferred = async {
            testee.start(testProfileQuery, testBrokerSteps)
        }

        yield()

        val callbackCaptor = argumentCaptor<(String?) -> Unit>()
        verify(mockPirDetachedWebViewProvider).createInstance(any(), any(), any(), callbackCaptor.capture())
        callbackCaptor.firstValue.invoke(failedUrl)

        sideEffectFlow.tryEmit(SideEffect.CompleteExecution)

        deferred.await()

        val eventCaptor = argumentCaptor<Event>()
        verify(mockEngine, times(2)).dispatch(eventCaptor.capture())

        assertTrue(eventCaptor.allValues[1] is Event.LoadUrlFailed)
        assertEquals(failedUrl, (eventCaptor.allValues[1] as Event.LoadUrlFailed).url)
    }
}
