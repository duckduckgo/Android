/*
 * Copyright (c) 2024 DuckDuckGo
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

package com.duckduckgo.app.browser.trafficquality

import com.duckduckgo.app.browser.trafficquality.Result.Allowed
import com.duckduckgo.app.browser.trafficquality.Result.NotAllowed
import com.duckduckgo.app.browser.trafficquality.remote.FeaturesRequestHeaderStore
import com.duckduckgo.appbuildconfig.api.AppBuildConfig
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.time.LocalDateTime
import java.time.ZoneId

class CustomHeaderAllowedCheckerTest {

    private val appBuildConfig: AppBuildConfig = mock()
    private val featuresRequestHeaderStore: FeaturesRequestHeaderStore = mock()

    private lateinit var testee: CustomHeaderAllowedChecker

    @Before
    fun setup() {
        testee = RealCustomHeaderGracePeriodChecker(appBuildConfig, featuresRequestHeaderStore)
        whenever(appBuildConfig.versionCode).thenReturn(currentVersion)
    }

    @Test
    fun whenNoConfigAvailableThenNotAllowed() = runTest {
        whenever(featuresRequestHeaderStore.getConfig()).thenReturn(emptyList())

        val result = testee.isAllowed()

        assert(result is NotAllowed)
    }

    @Test
    fun whenNoConfigForCurrentVersionAvailableThenNotAllowed() = runTest {
        whenever(featuresRequestHeaderStore.getConfig()).thenReturn(listOf(configEnabledForAnotherVersion))

        val result = testee.isAllowed()

        assert(result is NotAllowed)
    }

    @Test
    fun whenAskingAtStartOfGracePeriodThenIsAllowed() = runTest {
        givenBuildDateDaysAgo(5)
        whenever(featuresRequestHeaderStore.getConfig()).thenReturn(listOf(configEnabledForCurrentVersion))

        val result = testee.isAllowed()

        assert(result is Allowed)
    }

    @Test
    fun whenAskingDuringGracePeriodThenReturnIsAllowed() = runTest {
        givenBuildDateDaysAgo(8)
        whenever(featuresRequestHeaderStore.getConfig()).thenReturn(listOf(configEnabledForCurrentVersion))

        val result = testee.isAllowed()

        assert(result is Allowed)
    }

    @Test
    fun whenAskingAtTheEndOfGracePeriodThenIsAllowed() = runTest {
        givenBuildDateDaysAgo(10)
        whenever(featuresRequestHeaderStore.getConfig()).thenReturn(listOf(configEnabledForCurrentVersion))

        val result = testee.isAllowed()

        assert(result is Allowed)
    }

    @Test
    fun whenItsTooEarlyToLogThenIsNotAllowed() = runTest {
        givenBuildDateDaysAgo(1)

        whenever(featuresRequestHeaderStore.getConfig()).thenReturn(listOf(configEnabledForCurrentVersion))

        val result = testee.isAllowed()

        assert(result is NotAllowed)
    }

    @Test
    fun whenItsTooLateToLogThenIsNotAllowed() = runTest {
        givenBuildDateDaysAgo(20)

        whenever(featuresRequestHeaderStore.getConfig()).thenReturn(listOf(configEnabledForCurrentVersion))

        val result = testee.isAllowed()

        assert(result is NotAllowed)
    }

    private fun givenBuildDateDaysAgo(days: Long) {
        val daysAgo = LocalDateTime.now().minusDays(days).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        whenever(appBuildConfig.buildDateTimeMillis).thenReturn(daysAgo)
    }
}
