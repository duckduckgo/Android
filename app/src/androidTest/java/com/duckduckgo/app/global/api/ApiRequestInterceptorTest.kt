/*
 * Copyright (c) 2018 DuckDuckGo
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

package com.duckduckgo.app.global.api

import android.webkit.WebSettings
import androidx.test.platform.app.InstrumentationRegistry
import com.duckduckgo.app.browser.useragent.UserAgentProvider
import com.duckduckgo.app.global.device.ContextDeviceInfo
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class ApiRequestInterceptorTest {

    private lateinit var testee: ApiRequestInterceptor

    private lateinit var userAgentProvider: UserAgentProvider

    @Before
    fun before() {
        userAgentProvider = UserAgentProvider(
            WebSettings.getDefaultUserAgent(InstrumentationRegistry.getInstrumentation().context),
            ContextDeviceInfo(InstrumentationRegistry.getInstrumentation().context)
        )

        testee = ApiRequestInterceptor(
            InstrumentationRegistry.getInstrumentation().context,
            userAgentProvider
        )
    }

    @Test
    fun whenAPIRequestIsMadeThenUserAgentIsAdded() {
        val packageName = InstrumentationRegistry.getInstrumentation().context.applicationInfo.packageName

        val response = testee.intercept(FakeChain("http://example.com"))

        val regex = "ddg_android/.*\\($packageName; Android API .*\\)".toRegex()
        val result = response.request.header(Header.USER_AGENT)!!
        assertTrue(result.matches(regex))
    }

//    @Test
//    fun whenAPIRequestIsRqPixelThenOverrideHeader() {
//        val fakeChain = FakeChain("https://improving.duckduckgo.com/t/rq_0")
//
//        val response = testee.intercept(fakeChain)
//        val header = response.request.header(Header.USER_AGENT)!!
//        val regex = "Mozilla/.* \\(Linux; Android.*\\) AppleWebKit/.* \\(KHTML, like Gecko\\) Version/.* Chrome/.* Mobile DuckDuckGo/.* Safari/.*".toRegex()
//        assertTrue(header.matches(regex))
//    }
}
