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
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import com.duckduckgo.mobile.android.R as CommonR

class OnboardingAcceptNonOptOutCookiesPreferencePluginImplTest {

    private val context: Context = mock {
        on { getString(R.string.autoconsent_onboarding_accept_non_opt_out_cookies_primary) } doReturn "Accept some cookies"
        on {
            getString(R.string.autoconsent_onboarding_accept_non_opt_out_cookies_secondary)
        } doReturn "Hides more pop-ups by accepting cookies that can't be rejected"
    }
    private val autoconsent: Autoconsent = mock()
    private val autoconsentFeature: AutoconsentFeature = FakeFeatureToggleFactory.create(AutoconsentFeature::class.java)

    private val testee = OnboardingAcceptNonOptOutCookiesPreferencePluginImpl(
        context = context,
        autoconsent = autoconsent,
        autoconsentFeature = autoconsentFeature,
    )

    @Before
    fun setup() {
        autoconsentFeature.self().setRawStoredState(Toggle.State(enable = true))
        autoconsentFeature.cookiePopUpPreferenceSetting().setRawStoredState(Toggle.State(enable = true))
    }

    @Test
    fun `when plugin contributed then it answers for the accept non opt out cookies preference`() {
        assertEquals(OnboardingBooleanPreferencePlugin.Id.AcceptNonOptOutCookies, testee.id)
    }

    @Test
    fun `when the cookie pop up preference setting is on then it names and illustrates the preference`() = runTest {
        assertEquals(
            Preference(
                primaryText = "Accept some cookies",
                secondaryText = "Hides more pop-ups by accepting cookies that can't be rejected",
                iconRes = CommonR.drawable.cookie_color_24,
            ),
            testee.getPreference(),
        )
    }

    @Test
    fun `when the cookie pop up preference setting is off then the preference is withheld`() = runTest {
        autoconsentFeature.cookiePopUpPreferenceSetting().setRawStoredState(Toggle.State(enable = false))

        assertNull(testee.getPreference())
    }

    @Test
    fun `when autoconsent is killed remotely then the preference is withheld`() = runTest {
        autoconsentFeature.self().setRawStoredState(Toggle.State(enable = false))

        assertNull(testee.getPreference())
    }

    @Test
    fun `when preference applied on then click accept is enabled`() = runTest {
        testee.apply(true)

        verify(autoconsent).changeClickAcceptEnabled(true)
    }

    @Test
    fun `when preference applied off then click accept is disabled`() = runTest {
        testee.apply(false)

        verify(autoconsent).changeClickAcceptEnabled(false)
    }
}
