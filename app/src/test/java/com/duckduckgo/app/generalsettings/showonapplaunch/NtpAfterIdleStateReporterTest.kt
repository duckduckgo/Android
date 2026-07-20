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

package com.duckduckgo.app.generalsettings.showonapplaunch

import com.duckduckgo.app.pixels.remoteconfig.AndroidBrowserConfigFeature
import com.duckduckgo.app.settings.db.SettingsDataStore
import com.duckduckgo.app.statistics.pixels.Pixel
import com.duckduckgo.app.statistics.pixels.Pixel.PixelType.Daily
import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.feature.toggles.api.FakeFeatureToggleFactory
import com.duckduckgo.feature.toggles.api.Toggle
import com.duckduckgo.newtabpage.api.NtpAfterIdleManager
import kotlinx.coroutines.flow.flowOf
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class NtpAfterIdleStateReporterTest {

    @get:Rule val coroutineTestRule = CoroutineTestRule()

    private val pixel: Pixel = mock()
    private val settingsDataStore: SettingsDataStore = mock()
    private val ntpAfterIdleManager: NtpAfterIdleManager = mock()
    private val feature = FakeFeatureToggleFactory.create(AndroidBrowserConfigFeature::class.java)

    private val testee =
        NtpAfterIdleStateReporter(
            pixel,
            coroutineTestRule.testScope,
            coroutineTestRule.testDispatcherProvider,
            settingsDataStore,
            ntpAfterIdleManager,
            RealIdleThresholdResolver(feature),
        )

    @Test
    fun whenReturnToLastTabEnabledThenFiresEnabledDailyPixel() {
        stubState(returnToLastTabEnabled = true, idleThresholdSeconds = 300L)

        testee.onSearchRetentionAtbRefreshed("v1", "v2")

        verify(pixel).fire(NtpAfterIdleStatePixelName.RETURN_TO_LAST_TAB_ENABLED_DAILY, type = Daily())
    }

    @Test
    fun whenReturnToLastTabDisabledThenFiresDisabledDailyPixel() {
        stubState(returnToLastTabEnabled = false, idleThresholdSeconds = 300L)

        testee.onSearchRetentionAtbRefreshed("v1", "v2")

        verify(pixel).fire(NtpAfterIdleStatePixelName.RETURN_TO_LAST_TAB_DISABLED_DAILY, type = Daily())
    }

    @Test
    fun whenIdleThresholdSetThenFiresMatchingTimeoutDailyPixel() {
        stubState(returnToLastTabEnabled = true, idleThresholdSeconds = 1800L)

        testee.onSearchRetentionAtbRefreshed("v1", "v2")

        verify(pixel).fire(NtpAfterIdleStatePixelName.IDLE_TIMEOUT_1800_DAILY, type = Daily())
    }

    @Test
    fun whenIdleThresholdIsZeroThenFiresAlwaysDailyPixel() {
        stubState(returnToLastTabEnabled = true, idleThresholdSeconds = 0L)

        testee.onSearchRetentionAtbRefreshed("v1", "v2")

        verify(pixel).fire(NtpAfterIdleStatePixelName.IDLE_TIMEOUT_ALWAYS_DAILY, type = Daily())
    }

    @Test
    fun whenIdleThresholdUnsetThenFiresDefaultTimeoutDailyPixel() {
        stubState(returnToLastTabEnabled = true, idleThresholdSeconds = null)

        testee.onSearchRetentionAtbRefreshed("v1", "v2")

        // FirstScreenHandlerImpl.DEFAULT_IDLE_THRESHOLD_SECONDS = 300L
        verify(pixel).fire(NtpAfterIdleStatePixelName.IDLE_TIMEOUT_300_DAILY, type = Daily())
    }

    @Test
    fun whenIdleThresholdUnsetButRemoteDefaultSetThenFiresRemoteDefaultBucket() {
        feature.showNTPAfterIdleReturn().setRawStoredState(
            Toggle.State(enable = true, settings = """{"defaultIdleThresholdSeconds":1800}"""),
        )
        stubState(returnToLastTabEnabled = true, idleThresholdSeconds = null)

        testee.onSearchRetentionAtbRefreshed("v1", "v2")

        verify(pixel).fire(NtpAfterIdleStatePixelName.IDLE_TIMEOUT_1800_DAILY, type = Daily())
    }

    private fun stubState(returnToLastTabEnabled: Boolean, idleThresholdSeconds: Long?) {
        whenever(ntpAfterIdleManager.returnToLastTabEnabled).thenReturn(flowOf(returnToLastTabEnabled))
        whenever(settingsDataStore.userSelectedIdleThresholdSeconds).thenReturn(idleThresholdSeconds)
    }
}
