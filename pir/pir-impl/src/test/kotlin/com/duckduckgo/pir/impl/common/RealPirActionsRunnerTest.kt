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
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
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
    private val mockEmailDataResolver: EmailDataResolver = mock()
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

    private val testSuccessResponse = PirSuccessResponse.NavigateResponse(
        actionID = "test-action",
        actionType = "navigate",
        response = PirSuccessResponse.NavigateResponse.ResponseData(url = "https://test.com"),
    )

    @Before
    fun setUp() {
        whenever(mockEngine.sideEffect).thenReturn(sideEffectFlow)
        whenever(mockEngineFactory.create(any(), any(), any())).thenReturn(mockEngine)
        whenever(mockPirDetachedWebViewProvider.createInstance(any(), any(), any(), any(), any()))
            .thenReturn(mockWebView)
        whenever(mockPirDetachedWebViewProvider.setupWebView(any(), any(), any(), any(), any()))
            .thenReturn(mockWebView)
        testee = RealPirActionsRunner(
            dispatcherProvider = coroutineRule.testDispatcherProvider,
            pirDetachedWebViewProvider = mockPirDetachedWebViewProvider,
            brokerActionProcessor = mockBrokerActionProcessor,
            nativeBrokerActionHandler = mockNativeBrokerActionHandler,
            emailDataResolver = mockEmailDataResolver,
            engineFactory = mockEngineFactory,
            coroutineScope = coroutineRule.testScope,
            runType = testRunType,
            context = mockContext,
            pirScriptToLoad = testPirScript,
        )
    }

    @Test
    fun whenExecuteThenCreatesDetachedWebViewAndRegistersProcessor() = runTest {
        val deferred = async {
            testee.execute(testProfileQuery, testScanStep)
        }

        yield()

        sideEffectFlow.tryEmit(SideEffect.CompleteExecution)

        deferred.await()

        verify(mockPirDetachedWebViewProvider).createInstance(
            eq(mockContext),
            eq(testPirScript),
            any(),
            any(),
            any(),
        )
        verify(mockBrokerActionProcessor).register(mockWebView, testee)
    }

    @Test
    fun whenExecuteThenCreatesEngineAndDispatchesStarted() = runTest {
        val deferred = async {
            testee.execute(testProfileQuery, testScanStep)
        }

        yield()

        sideEffectFlow.tryEmit(SideEffect.CompleteExecution)

        deferred.await()

        verify(mockEngineFactory).create(testRunType, testScanStep, testProfileQuery)
        verify(mockEngine).dispatch(Event.Started)
    }

    @Test
    fun whenExecuteAndCompleteExecutionReceivedThenReturnsSuccess() = runTest {
        val deferred = async {
            testee.execute(testProfileQuery, testScanStep)
        }

        yield()

        sideEffectFlow.tryEmit(SideEffect.CompleteExecution)

        val result = deferred.await()

        assertTrue(result.isSuccess)
    }

    @Test
    fun whenExecuteCompletesThenOwnedWebViewIsDestroyed() = runTest {
        val deferred = async {
            testee.execute(testProfileQuery, testScanStep)
        }

        yield()

        sideEffectFlow.tryEmit(SideEffect.CompleteExecution)

        deferred.await()

        verify(mockWebView).stopLoading()
        verify(mockWebView).clearCache(true)
        verify(mockWebView).destroy()
    }

    @Test
    fun whenExecuteIsCancelledMidStepThenOwnedWebViewIsStillDestroyed() = runTest {
        val deferred = async {
            testee.execute(testProfileQuery, testScanStep)
        }

        yield()

        // Cancelling the coroutine driving the step is how a stopped scan worker aborts a runner.
        // The step teardown drops the runner's WebView reference, so the distributor's following
        // stop() cannot destroy it - if teardown skips the destroy, nothing else can do it.
        deferred.cancel()
        deferred.join()
        testee.stop()

        verify(mockWebView).destroy()
    }

    @Test
    fun whenExecutedTwiceThenEachStepGetsItsOwnWebView() = runTest {
        repeat(2) {
            val deferred = async {
                testee.execute(testProfileQuery, testScanStep)
            }
            yield()
            sideEffectFlow.tryEmit(SideEffect.CompleteExecution)
            deferred.await()
        }

        verify(mockPirDetachedWebViewProvider, times(2)).createInstance(any(), any(), any(), any(), any())
        verify(mockBrokerActionProcessor, times(2)).register(eq(mockWebView), any())
        verify(mockWebView, times(2)).destroy()
    }

    @Test
    fun whenExecuteOnThenSetsUpCallerWebViewAndDoesNotDestroyIt() = runTest {
        val deferred = async {
            testee.executeOn(mockWebView, testProfileQuery, testScanStep)
        }

        yield()

        sideEffectFlow.tryEmit(SideEffect.CompleteExecution)

        deferred.await()

        verify(mockPirDetachedWebViewProvider).setupWebView(
            eq(mockWebView),
            eq(testPirScript),
            any(),
            any(),
            any(),
        )
        verify(mockWebView, never()).destroy()
        verify(mockWebView, never()).clearCache(any())
    }

    @Test
    fun whenOnSuccessThenDispatchesJsActionSuccessEvent() = runTest {
        val deferred = async {
            testee.execute(testProfileQuery, testScanStep)
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

        val deferred = async {
            testee.execute(testProfileQuery, testScanStep)
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

        val deferred = async {
            testee.execute(testProfileQuery, testScanStep)
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

        val deferred = async {
            testee.execute(testProfileQuery, testScanStep)
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

        val deferred = async {
            testee.execute(testProfileQuery, testScanStep)
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
        val job = async {
            testee.execute(testProfileQuery, testScanStep)
        }

        yield()

        testee.stop()

        job.cancel()

        verify(mockWebView).stopLoading()
        verify(mockWebView).evaluateJavascript("window.stop();", null)
        verify(mockWebView).destroy()
    }

    @Test
    fun whenOnLoadingCompleteWithUrlThenDispatchesLoadUrlComplete() = runTest {
        val loadedUrl = "https://example.com"

        val deferred = async {
            testee.execute(testProfileQuery, testScanStep)
        }

        yield()

        val callbackCaptor = argumentCaptor<(String?) -> Unit>()
        verify(mockPirDetachedWebViewProvider).createInstance(any(), any(), callbackCaptor.capture(), any(), any())
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
        val deferred = async {
            testee.execute(testProfileQuery, testScanStep)
        }

        yield()

        val callbackCaptor = argumentCaptor<(String?) -> Unit>()
        verify(mockPirDetachedWebViewProvider).createInstance(any(), any(), callbackCaptor.capture(), any(), any())
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

        val deferred = async {
            testee.execute(testProfileQuery, testScanStep)
        }

        yield()

        val callbackCaptor = argumentCaptor<(String?) -> Unit>()
        verify(mockPirDetachedWebViewProvider).createInstance(any(), any(), any(), callbackCaptor.capture(), any())
        callbackCaptor.firstValue.invoke(failedUrl)

        sideEffectFlow.tryEmit(SideEffect.CompleteExecution)

        deferred.await()

        val eventCaptor = argumentCaptor<Event>()
        verify(mockEngine, times(2)).dispatch(eventCaptor.capture())

        assertTrue(eventCaptor.allValues[1] is Event.LoadUrlFailed)
        assertEquals(failedUrl, (eventCaptor.allValues[1] as Event.LoadUrlFailed).url)
    }

    @Test
    fun whenRenderProcessGoneThenExecuteThrowsPirRendererGoneExceptionWithDidCrash() = runTest {
        val rendererGoneCaptor = argumentCaptor<(Boolean) -> Unit>()
        whenever(
            mockPirDetachedWebViewProvider.createInstance(any(), any(), any(), any(), rendererGoneCaptor.capture()),
        ).thenReturn(mockWebView)

        var thrown: Throwable? = null
        val deferred = async {
            try {
                testee.execute(testProfileQuery, testScanStep)
            } catch (e: Throwable) {
                thrown = e
            }
        }

        // Allow execute() to reach awaitResult and register the continuation.
        yield()

        // Simulate the shared renderer being reclaimed by the system (not a crash).
        rendererGoneCaptor.firstValue.invoke(false)

        deferred.await()

        assertTrue(thrown is PirRendererGoneException)
        assertTrue(!(thrown as PirRendererGoneException).didCrash)
    }

    @Test
    fun whenRendererGoneAfterCompleteExecutionThenIsSafeNoOp() = runTest {
        val rendererGoneCaptor = argumentCaptor<(Boolean) -> Unit>()
        whenever(
            mockPirDetachedWebViewProvider.createInstance(any(), any(), any(), any(), rendererGoneCaptor.capture()),
        ).thenReturn(mockWebView)

        val deferred = async {
            testee.execute(testProfileQuery, testScanStep)
        }

        yield()

        // The run completes successfully first.
        sideEffectFlow.tryEmit(SideEffect.CompleteExecution)

        val result = deferred.await()
        assertTrue(result.isSuccess)

        // A renderer-gone signal arriving for this (now-completed) run's WebView must be a no-op:
        // it must not throw, and must not retroactively affect the already-resolved result.
        rendererGoneCaptor.firstValue.invoke(true)

        assertTrue(result.isSuccess)
    }
}
