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

package com.duckduckgo.app.generalsettings.showonapplaunch.rmf

import com.duckduckgo.app.pixels.remoteconfig.AndroidBrowserConfigFeature
import com.duckduckgo.browsermode.api.BrowserMode
import com.duckduckgo.feature.toggles.api.FakeFeatureToggleFactory
import com.duckduckgo.feature.toggles.api.Toggle
import com.duckduckgo.newtabpage.api.EscapeHatchTarget
import com.duckduckgo.newtabpage.api.EscapeHatchTargetResolver
import com.duckduckgo.newtabpage.api.NtpAfterIdleManager
import com.duckduckgo.remote.messaging.api.MessageTrigger
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class AfterIdleMessageTriggerProviderTest {

    private val ntpAfterIdleManager: NtpAfterIdleManager = mock()
    private val escapeHatchTargetResolver: EscapeHatchTargetResolver = mock()
    private val feature = FakeFeatureToggleFactory.create(AndroidBrowserConfigFeature::class.java)

    private val testee = AfterIdleMessageTriggerProvider(ntpAfterIdleManager, feature, escapeHatchTargetResolver)

    @Test
    fun whenBothFlagsEnabledAndAfterIdleReturnWithReturnTargetThenAfterIdleTrigger() = runTest {
        enableBothFlags()
        whenever(ntpAfterIdleManager.isAfterIdleReturn).thenReturn(MutableStateFlow(true))
        whenever(escapeHatchTargetResolver.resolve()).thenReturn(EscapeHatchTarget("tab1", BrowserMode.REGULAR))

        assertEquals(MessageTrigger.AFTER_IDLE, testee.activeTrigger().firstOrNull())
    }

    @Test
    fun whenAfterIdleReturnButNoReturnTargetThenNull() = runTest {
        // e.g. cold launch onto an existing NTP: after-idle, but nothing real to return to.
        enableBothFlags()
        whenever(ntpAfterIdleManager.isAfterIdleReturn).thenReturn(MutableStateFlow(true))
        whenever(escapeHatchTargetResolver.resolve()).thenReturn(null)

        assertNull(testee.activeTrigger().firstOrNull())
    }

    @Test
    fun whenNotAfterIdleReturnThenNull() = runTest {
        enableBothFlags()
        whenever(ntpAfterIdleManager.isAfterIdleReturn).thenReturn(MutableStateFlow(false))

        assertNull(testee.activeTrigger().firstOrNull())
    }

    @Test
    fun whenShowNtpAfterIdleReturnFlagDisabledThenNull() = runTest {
        feature.showNTPAfterIdleReturn().setRawStoredState(Toggle.State(enable = false))
        feature.ntpAsDefaultAfterIdleReturn().setRawStoredState(Toggle.State(enable = true))

        assertNull(testee.activeTrigger().firstOrNull())
    }

    @Test
    fun whenNtpAsDefaultFlagDisabledThenNull() = runTest {
        feature.showNTPAfterIdleReturn().setRawStoredState(Toggle.State(enable = true))
        feature.ntpAsDefaultAfterIdleReturn().setRawStoredState(Toggle.State(enable = false))

        assertNull(testee.activeTrigger().firstOrNull())
    }

    private fun enableBothFlags() {
        feature.showNTPAfterIdleReturn().setRawStoredState(Toggle.State(enable = true))
        feature.ntpAsDefaultAfterIdleReturn().setRawStoredState(Toggle.State(enable = true))
    }
}
