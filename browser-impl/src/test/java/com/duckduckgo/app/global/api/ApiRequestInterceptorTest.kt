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

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.duckduckgo.app.fakes.UserAgentFake
import com.duckduckgo.app.fakes.UserAllowListRepositoryFake
import com.duckduckgo.appbuildconfig.api.AppBuildConfig
import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.common.test.api.FakeChain
import com.duckduckgo.common.utils.device.ContextDeviceInfo
import com.duckduckgo.common.utils.plugins.PluginPoint
import com.duckduckgo.feature.toggles.api.FeatureToggle
import com.duckduckgo.feature.toggles.api.FeatureToggleFake
import com.duckduckgo.user.agent.api.UserAgentInterceptor
import com.duckduckgo.user.agent.api.UserAgentProvider
import com.duckduckgo.user.agent.impl.RealUserAgentProvider
import com.duckduckgo.user.agent.impl.UserAgent
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

@RunWith(AndroidJUnit4::class)
class ApiRequestInterceptorTest {

    @get:Rule
    var coroutinesTestRule = CoroutineTestRule()

    private lateinit var testee: ApiRequestInterceptor
    private lateinit var userAgentProvider: UserAgentProvider
    private val appBuildConfig: AppBuildConfig = mock()
    private val fakeUserAgent: UserAgent = UserAgentFake()
    private val fakeToggle: FeatureToggle = FeatureToggleFake()
    private val fakeUserAllowListRepository = UserAllowListRepositoryFake()

    @Before
    fun before() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        whenever(appBuildConfig.versionName).thenReturn("name")

        userAgentProvider = RealUserAgentProvider(
            { "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.120 Mobile Safari/537.36" },
            ContextDeviceInfo(context),
            provideUserAgentOverridePluginPoint(),
            fakeUserAgent,
            fakeToggle,
            fakeUserAllowListRepository,
        )

        testee = ApiRequestInterceptor(
            context,
            userAgentProvider,
            appBuildConfig,
        )
    }

    @Test
    fun whenAPIRequestIsMadeThenUserAgentIsAdded() {
        val packageName = ApplicationProvider.getApplicationContext<android.content.Context>().applicationInfo.packageName

        val response = testee.intercept(FakeChain("http://example.com"))

        val regex = "ddg_android/.*\\($packageName; Android API .*\\)".toRegex()
        val result = response.request.header(Header.USER_AGENT)!!
        assertTrue(result.matches(regex))
    }

    @Test
    fun whenAPIRequestIsRqPixelThenOverrideHeader() {
        val fakeChain = FakeChain("https://improving.duckduckgo.com/t/rq_0")

        val response = testee.intercept(fakeChain)
        val header = response.request.header(Header.USER_AGENT)!!
        val regex =
            "Mozilla/.* \\(Linux; Android.*\\) AppleWebKit/.* \\(KHTML, like Gecko\\) Chrome/.* Mobile Safari/.*".toRegex()
        assertTrue(header.matches(regex))
    }

    fun provideUserAgentOverridePluginPoint(): PluginPoint<UserAgentInterceptor> {
        return object : PluginPoint<UserAgentInterceptor> {
            override fun getPlugins(): Collection<UserAgentInterceptor> {
                return listOf()
            }
        }
    }
}
