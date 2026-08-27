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

package com.duckduckgo.adblocking.impl.onboarding

import android.content.Context
import com.duckduckgo.adblocking.impl.AdBlockingSettingsRepository
import com.duckduckgo.adblocking.impl.R
import com.duckduckgo.adblocking.impl.domain.AdBlockingState.Disabled
import com.duckduckgo.adblocking.impl.domain.AdBlockingState.Enabled
import com.duckduckgo.adblocking.impl.domain.AdBlockingState.Uninitialized
import com.duckduckgo.adblocking.impl.domain.AdBlockingStatusChecker
import com.duckduckgo.adblocking.impl.domain.SettingsPlacement
import com.duckduckgo.onboarding.api.OnboardingBooleanPreferencePlugin
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import com.duckduckgo.mobile.android.R as CommonR

class OnboardingAdBlockingPreferencePluginImplTest {

    private val context: Context = mock {
        on { getString(R.string.ad_blocking_onboarding_preference_primary) } doReturn "Block ads"
    }
    private val statusChecker: AdBlockingStatusChecker = mock()
    private val settingsRepository: AdBlockingSettingsRepository = mock()

    private val testee = OnboardingAdBlockingPreferencePluginImpl(
        context = context,
        statusChecker = statusChecker,
        settingsRepository = settingsRepository,
    )

    @Test
    fun `when plugin contributed then it answers for the ad blocking preference`() {
        assertEquals(OnboardingBooleanPreferencePlugin.Id.AdBlocking, testee.id)
    }

    @Test
    fun `when row is described then ad blocking names it and leaves the second line out`() {
        assertEquals("Block ads", testee.primaryText)
        assertNull(testee.secondaryText)
        assertEquals(CommonR.drawable.ads_blocked_color_24, testee.iconRes)
    }

    @Test
    fun `when settings show the entry under protections then plugin is active`() = runTest {
        whenever(statusChecker.settingsPlacementFlow()).thenReturn(flowOf(SettingsPlacement.Protections))

        assertTrue(testee.isActive())
    }

    @Test
    fun `when settings show the entry elsewhere then plugin is active`() = runTest {
        whenever(statusChecker.settingsPlacementFlow()).thenReturn(flowOf(SettingsPlacement.Other))

        assertTrue(testee.isActive())
    }

    @Test
    fun `when settings hide the entry then plugin is not active`() = runTest {
        whenever(statusChecker.settingsPlacementFlow()).thenReturn(flowOf(SettingsPlacement.Hidden))

        assertFalse(testee.isActive())
    }

    @Test
    fun `when settings placement is unknown then plugin is not active`() = runTest {
        whenever(statusChecker.settingsPlacementFlow()).thenReturn(emptyFlow())

        assertFalse(testee.isActive())
    }

    @Test
    fun `when ad blocking is on by remote default and preference applied on then nothing is persisted`() = runTest {
        whenever(statusChecker.observeState()).thenReturn(flowOf(Enabled.Default))

        testee.apply(true)

        verify(settingsRepository, never()).setEnabled(any())
    }

    @Test
    fun `when ad blocking is on by remote default and preference applied off then ad blocking is disabled`() = runTest {
        whenever(statusChecker.observeState()).thenReturn(flowOf(Enabled.Default))

        testee.apply(false)

        verify(settingsRepository).setEnabled(false)
    }

    @Test
    fun `when ad blocking is off and preference applied off then nothing is persisted`() = runTest {
        whenever(statusChecker.observeState()).thenReturn(flowOf(Disabled.Permanent))

        testee.apply(false)

        verify(settingsRepository, never()).setEnabled(any())
    }

    @Test
    fun `when ad blocking is off and preference applied on then ad blocking is enabled`() = runTest {
        whenever(statusChecker.observeState()).thenReturn(flowOf(Disabled.Permanent))

        testee.apply(true)

        verify(settingsRepository).setEnabled(true)
    }

    @Test
    fun `when user already enabled ad blocking and preference applied on then nothing is persisted`() = runTest {
        whenever(statusChecker.observeState()).thenReturn(flowOf(Enabled.UserEnabled))

        testee.apply(true)

        verify(settingsRepository, never()).setEnabled(any())
    }

    @Test
    fun `when user already enabled ad blocking and preference applied off then ad blocking is disabled`() = runTest {
        whenever(statusChecker.observeState()).thenReturn(flowOf(Enabled.UserEnabled))

        testee.apply(false)

        verify(settingsRepository).setEnabled(false)
    }

    @Test
    fun `when ad blocking is off for the session and preference applied off then ad blocking is disabled`() = runTest {
        whenever(statusChecker.observeState()).thenReturn(flowOf(Disabled.UntilRelaunch))

        testee.apply(false)

        verify(settingsRepository).setEnabled(false)
    }

    @Test
    fun `when ad blocking is off for the session and preference applied on then ad blocking is enabled`() = runTest {
        whenever(statusChecker.observeState()).thenReturn(flowOf(Disabled.UntilRelaunch))

        testee.apply(true)

        verify(settingsRepository).setEnabled(true)
    }

    @Test
    fun `when state has not resolved yet then the pick is persisted`() = runTest {
        whenever(statusChecker.observeState()).thenReturn(flowOf(Uninitialized))

        testee.apply(true)

        verify(settingsRepository).setEnabled(true)
    }

    @Test
    fun `when state never arrives then the pick is persisted`() = runTest {
        whenever(statusChecker.observeState()).thenReturn(emptyFlow())

        testee.apply(true)

        verify(settingsRepository).setEnabled(true)
    }
}
