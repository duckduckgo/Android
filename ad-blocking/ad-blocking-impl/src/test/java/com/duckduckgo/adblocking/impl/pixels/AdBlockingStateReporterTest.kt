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

package com.duckduckgo.adblocking.impl.pixels

import androidx.lifecycle.LifecycleOwner
import com.duckduckgo.adblocking.impl.AdBlockingPixelNames.AD_BLOCKING_STATE_DAILY
import com.duckduckgo.adblocking.impl.domain.AdBlockingState
import com.duckduckgo.adblocking.impl.domain.AdBlockingStatusChecker
import com.duckduckgo.app.statistics.pixels.Pixel
import com.duckduckgo.common.test.CoroutineTestRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

@OptIn(ExperimentalCoroutinesApi::class)
class AdBlockingStateReporterTest {

    @get:Rule
    val coroutineRule = CoroutineTestRule()

    private val statusChecker: AdBlockingStatusChecker = mock()
    private val pixel: Pixel = mock()
    private val owner: LifecycleOwner = mock()

    private val reporter = AdBlockingStateReporter(statusChecker, pixel, coroutineRule.testScope)

    @Test
    fun whenCanInjectAndUserEnabledThenBothParamsTrue() {
        whenever(statusChecker.observeCanInject()).thenReturn(flowOf(true))
        whenever(statusChecker.observeState()).thenReturn(flowOf(AdBlockingState.Enabled.UserEnabled))

        reporter.onResume(owner)

        verify(pixel).fire(
            AD_BLOCKING_STATE_DAILY,
            parameters = mapOf("is_enabled" to "true", "user_opted_in" to "true"),
            type = Pixel.PixelType.Daily(),
        )
    }

    @Test
    fun whenCanInjectByDefaultThenIsEnabledTrueButAnalyticsFalse() {
        whenever(statusChecker.observeCanInject()).thenReturn(flowOf(true))
        whenever(statusChecker.observeState()).thenReturn(flowOf(AdBlockingState.Enabled.Default))

        reporter.onResume(owner)

        verify(pixel).fire(
            AD_BLOCKING_STATE_DAILY,
            parameters = mapOf("is_enabled" to "true", "user_opted_in" to "false"),
            type = Pixel.PixelType.Daily(),
        )
    }

    @Test
    fun whenUserOptedInButRemoteConfigGatesInjectionThenIsEnabledFalseButAnalyticsTrue() {
        whenever(statusChecker.observeCanInject()).thenReturn(flowOf(false))
        whenever(statusChecker.observeState()).thenReturn(flowOf(AdBlockingState.Enabled.UserEnabled))

        reporter.onResume(owner)

        verify(pixel).fire(
            AD_BLOCKING_STATE_DAILY,
            parameters = mapOf("is_enabled" to "false", "user_opted_in" to "true"),
            type = Pixel.PixelType.Daily(),
        )
    }

    @Test
    fun whenDisabledThenBothParamsFalse() {
        whenever(statusChecker.observeCanInject()).thenReturn(flowOf(false))
        whenever(statusChecker.observeState()).thenReturn(flowOf(AdBlockingState.Disabled.Permanent))

        reporter.onResume(owner)

        verify(pixel).fire(
            AD_BLOCKING_STATE_DAILY,
            parameters = mapOf("is_enabled" to "false", "user_opted_in" to "false"),
            type = Pixel.PixelType.Daily(),
        )
    }
}
