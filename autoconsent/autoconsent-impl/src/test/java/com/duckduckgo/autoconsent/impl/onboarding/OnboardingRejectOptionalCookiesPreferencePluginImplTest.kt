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

package com.duckduckgo.autoconsent.impl.onboarding

import android.content.Context
import com.duckduckgo.autoconsent.api.Autoconsent
import com.duckduckgo.autoconsent.impl.R
import com.duckduckgo.autoconsent.impl.remoteconfig.AutoconsentFeature
import com.duckduckgo.feature.toggles.api.FakeFeatureToggleFactory
import com.duckduckgo.feature.toggles.api.Toggle
import com.duckduckgo.onboarding.api.OnboardingBooleanPreferencePlugin
import com.duckduckgo.onboarding.api.OnboardingBooleanPreferencePlugin.Preference
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import com.duckduckgo.mobile.android.R as CommonR

class OnboardingRejectOptionalCookiesPreferencePluginImplTest {

    private val context: Context = mock {
        on { getString(R.string.autoconsent_onboarding_reject_optional_cookies_primary) } doReturn "Reject optional cookies"
        on { getString(R.string.autoconsent_onboarding_reject_optional_cookies_secondary) } doReturn "Maximizes privacy and closes cookie pop-ups"
    }
    private val autoconsent: Autoconsent = mock()
    private val autoconsentFeature: AutoconsentFeature = FakeFeatureToggleFactory.create(AutoconsentFeature::class.java)

    private val testee = OnboardingRejectOptionalCookiesPreferencePluginImpl(
        context = context,
        autoconsent = autoconsent,
        autoconsentFeature = autoconsentFeature,
    )

    @Test
    fun `when plugin contributed then it answers for the reject optional cookies preference`() {
        assertEquals(OnboardingBooleanPreferencePlugin.Id.RejectOptionalCookies, testee.id)
    }

    @Test
    fun `when autoconsent is enabled remotely then it names and illustrates the preference`() = runTest {
        autoconsentFeature.self().setRawStoredState(Toggle.State(enable = true))

        assertEquals(
            Preference(
                primaryText = "Reject optional cookies",
                secondaryText = "Maximizes privacy and closes cookie pop-ups",
                iconRes = CommonR.drawable.cookie_blocked_color_24,
            ),
            testee.getPreference(),
        )
    }

    @Test
    fun `when autoconsent is killed remotely then the preference is withheld`() = runTest {
        autoconsentFeature.self().setRawStoredState(Toggle.State(enable = false))

        assertNull(testee.getPreference())
    }

    @Test
    fun `when preference applied on and autoconsent is off then autoconsent is enabled`() = runTest {
        whenever(autoconsent.isSettingEnabled()).thenReturn(false)

        testee.apply(true)

        verify(autoconsent).changeSetting(true)
    }

    @Test
    fun `when preference applied off and autoconsent is on then autoconsent is disabled`() = runTest {
        whenever(autoconsent.isSettingEnabled()).thenReturn(true)

        testee.apply(false)

        verify(autoconsent).changeSetting(false)
    }

    @Test
    fun `when preference applied to the value that already applies then nothing is persisted`() = runTest {
        whenever(autoconsent.isSettingEnabled()).thenReturn(true)

        testee.apply(true)

        verify(autoconsent, never()).changeSetting(any())
    }

    @Test
    fun `when preference applied to the value the default already gives then nothing is persisted`() = runTest {
        whenever(autoconsent.isSettingEnabled()).thenReturn(false)

        testee.apply(false)

        verify(autoconsent, never()).changeSetting(any())
    }
}
