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

package com.duckduckgo.app.browser.privacypass

import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import androidx.core.net.toUri
import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.privacypass.api.PrivacyPassChallenge
import com.duckduckgo.privacypass.api.PrivacyPassManager
import com.duckduckgo.privacypass.api.PrivacyPassResult
import junit.framework.TestCase.assertEquals
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class PrivacyPassHttpErrorHandlerTest {

    @get:Rule
    var coroutinesTestRule = CoroutineTestRule()

    private val fakePrivacyPassManager = FakePrivacyPassManager()
    private lateinit var appScope: CoroutineScope

    private lateinit var testee: RealPrivacyPassHttpErrorHandler

    @Before
    fun setup() {
        appScope = CoroutineScope(coroutinesTestRule.testDispatcher)
        testee = RealPrivacyPassHttpErrorHandler(
            appCoroutineScope = appScope,
            dispatcherProvider = coroutinesTestRule.testDispatcherProvider,
            privacyPassManager = fakePrivacyPassManager,
        )
        fakePrivacyPassManager.reset()
    }

    @Test
    fun whenMainFrameGetWithPrivateTokenChallengeThenChallengeHandlerInvoked() =
        runTest {
            val mockWebView: WebView = mock()
            val challengedUrl = "https://example.com/protected"
            val wwwAuthenticate = "PrivateToken challenge=:dGVzdA==:"
            val request: WebResourceRequest = mock()
            val errorResponse = createErrorResponse(
                statusCode = 401,
                headers = mapOf("WWW-Authenticate" to wwwAuthenticate),
            )

            fakePrivacyPassManager.isPrivateTokenChallengeResult = true
            fakePrivacyPassManager.handleChallengeResult = PrivacyPassResult.Failure("disabled")
            whenever(mockWebView.url).thenReturn(challengedUrl)
            whenever(request.isForMainFrame).thenReturn(true)
            whenever(request.method).thenReturn("GET")
            whenever(request.requestHeaders).thenReturn(emptyMap())

            testee.handle(mockWebView, request, errorResponse, challengedUrl)
            coroutinesTestRule.testScope.testScheduler.advanceUntilIdle()

            assertEquals(1, fakePrivacyPassManager.challengeChecks.size)
            assertEquals(1, fakePrivacyPassManager.handleCalls.size)
            assertEquals(challengedUrl, fakePrivacyPassManager.handleCalls.single().first)
            assertEquals(wwwAuthenticate, fakePrivacyPassManager.handleCalls.single().second)
        }

    @Test
    fun whenRetryHeaderAlreadyPresentThenChallengeHandlerNotInvoked() =
        runTest {
            val challengedUrl = "https://example.com/protected"
            val wwwAuthenticate = "PrivateToken challenge=:dGVzdA==:"
            val request: WebResourceRequest = mock()
            val errorResponse = createErrorResponse(
                statusCode = 401,
                headers = mapOf("WWW-Authenticate" to wwwAuthenticate),
            )

            fakePrivacyPassManager.isPrivateTokenChallengeResult = true
            whenever(request.isForMainFrame).thenReturn(true)
            whenever(request.method).thenReturn("GET")
            whenever(request.requestHeaders).thenReturn(mapOf("X-DuckDuckGo-PrivacyPass-Retry" to "1"))

            testee.handle(null, request, errorResponse, challengedUrl)
            coroutinesTestRule.testScope.testScheduler.advanceUntilIdle()

            assertEquals(0, fakePrivacyPassManager.handleCalls.size)
        }

    @Test
    fun whenChallengeHandlingSucceedsThenRetryLoadUsesAuthorizationAndLoopGuardHeaders() =
        runTest {
            val mockWebView: WebView = mock()
            val challengedUrl = "https://example.com/protected"
            val wwwAuthenticate = "PrivateToken challenge=:dGVzdA==:"
            val authorizationHeader = "PrivateToken token=:dG9rZW4=:"
            val request: WebResourceRequest = mock()
            val errorResponse = createErrorResponse(
                statusCode = 401,
                headers = mapOf("WWW-Authenticate" to wwwAuthenticate),
            )

            fakePrivacyPassManager.isPrivateTokenChallengeResult = true
            fakePrivacyPassManager.handleChallengeResult = PrivacyPassResult.Success(authorizationHeader)
            whenever(mockWebView.url).thenReturn(challengedUrl)
            whenever(request.isForMainFrame).thenReturn(true)
            whenever(request.method).thenReturn("GET")
            whenever(request.requestHeaders).thenReturn(emptyMap())

            testee.handle(mockWebView, request, errorResponse, challengedUrl)
            coroutinesTestRule.testScope.testScheduler.advanceUntilIdle()

            assertEquals(1, fakePrivacyPassManager.challengeChecks.size)
            assertEquals(1, fakePrivacyPassManager.handleCalls.size)
            val headersCaptor = argumentCaptor<Map<String, String>>()
            verify(mockWebView).loadUrl(eq(challengedUrl), headersCaptor.capture())
            assertEquals(authorizationHeader, headersCaptor.firstValue["Authorization"])
            assertEquals("1", headersCaptor.firstValue["X-DuckDuckGo-PrivacyPass-Retry"])
            assertEquals(challengedUrl, headersCaptor.firstValue["Referer"])
        }

    private class FakePrivacyPassManager : PrivacyPassManager {
        var isPrivateTokenChallengeResult: Boolean = false
        var handleChallengeResult: PrivacyPassResult = PrivacyPassResult.Failure("not configured")
        val challengeChecks = mutableListOf<Pair<Int, Map<String, String>>>()
        val handleCalls = mutableListOf<Pair<String, String>>()

        override fun isReady(): Boolean = true

        override fun isPrivateTokenChallenge(
            statusCode: Int,
            headers: Map<String, String>,
        ): Boolean {
            challengeChecks.add(statusCode to headers)
            return isPrivateTokenChallengeResult
        }

        override suspend fun handlePrivateTokenChallenge(
            originalUrl: String,
            wwwAuthenticateHeader: String,
        ): PrivacyPassResult {
            handleCalls.add(originalUrl to wwwAuthenticateHeader)
            return handleChallengeResult
        }

        override fun parseChallenge(wwwAuthenticateHeader: String): PrivacyPassChallenge? = null

        fun reset() {
            isPrivateTokenChallengeResult = false
            handleChallengeResult = PrivacyPassResult.Failure("not configured")
            challengeChecks.clear()
            handleCalls.clear()
        }
    }

    private fun createErrorResponse(
        statusCode: Int,
        headers: Map<String, String>,
    ): WebResourceResponse {
        val response: WebResourceResponse = mock()
        whenever(response.statusCode).thenReturn(statusCode)
        whenever(response.responseHeaders).thenReturn(headers)
        return response
    }

}
