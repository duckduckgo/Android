/*
 * Copyright (c) 2021 DuckDuckGo
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

package com.duckduckgo.app.browser.serviceworker

import android.webkit.WebResourceRequest
import androidx.test.filters.SdkSuppress
import com.duckduckgo.app.CoroutineTestRule
import com.duckduckgo.app.browser.RequestInterceptor
import com.duckduckgo.app.global.exception.UncaughtExceptionRepository
import com.duckduckgo.app.runBlocking
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.whenever
import kotlinx.coroutines.ExperimentalCoroutinesApi
import org.junit.Before
import org.junit.Rule
import org.junit.Test

@ExperimentalCoroutinesApi
@SdkSuppress(minSdkVersion = 24)
class BrowserServiceWorkerClientTest {

    @get:Rule
    var coroutinesTestRule = CoroutineTestRule()

    private val requestInterceptor: RequestInterceptor = mock()
    private val uncaughtExceptionRepository: UncaughtExceptionRepository = mock()

    private lateinit var testee: BrowserServiceWorkerClient

    @Before
    fun setup() {
        testee = BrowserServiceWorkerClient(requestInterceptor, uncaughtExceptionRepository)
    }

    @Test
    fun whenShouldInterceptRequestAndOriginHeaderExistThenSendItToInterceptor() = coroutinesTestRule.runBlocking {
        val webResourceRequest: WebResourceRequest = mock()
        whenever(webResourceRequest.requestHeaders).thenReturn(mapOf("Origin" to "example.com"))

        testee.shouldInterceptRequest(webResourceRequest)

        verify(requestInterceptor).shouldInterceptFromServiceWorker(webResourceRequest, "example.com")
    }

    @Test
    fun whenShouldInterceptRequestAndOriginHeaderDoesNotExistButRefererExistThenSendItToInterceptor() = coroutinesTestRule.runBlocking {
        val webResourceRequest: WebResourceRequest = mock()
        whenever(webResourceRequest.requestHeaders).thenReturn(mapOf("Referer" to "example.com"))

        testee.shouldInterceptRequest(webResourceRequest)

        verify(requestInterceptor).shouldInterceptFromServiceWorker(webResourceRequest, "example.com")
    }

    @Test
    fun whenShouldInterceptRequestAndNoOriginOrRefererHeadersExistThenSendNullToInterceptor() = coroutinesTestRule.runBlocking {
        val webResourceRequest: WebResourceRequest = mock()
        whenever(webResourceRequest.requestHeaders).thenReturn(mapOf())

        testee.shouldInterceptRequest(webResourceRequest)

        verify(requestInterceptor).shouldInterceptFromServiceWorker(webResourceRequest, null)
    }

}
