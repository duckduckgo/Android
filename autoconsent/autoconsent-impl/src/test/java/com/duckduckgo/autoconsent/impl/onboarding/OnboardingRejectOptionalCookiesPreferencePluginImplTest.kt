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
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
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
    fun whenPluginContributedThenItAnswersForTheRejectOptionalCookiesPreference() {
        assertEquals(OnboardingBooleanPreferencePlugin.Id.RejectOptionalCookies, testee.id)
    }

    @Test
    fun whenRowIsDescribedThenAutoconsentNamesAndIllustratesIt() {
        assertEquals("Reject optional cookies", testee.primaryText)
        assertEquals("Maximizes privacy and closes cookie pop-ups", testee.secondaryText)
        assertEquals(CommonR.drawable.cookie_blocked_color_24, testee.iconRes)
    }

    @Test
    fun whenAutoconsentIsEnabledRemotelyThenPluginIsActive() = runTest {
        autoconsentFeature.self().setRawStoredState(Toggle.State(enable = true))

        assertTrue(testee.isActive())
    }

    @Test
    fun whenAutoconsentIsKilledRemotelyThenPluginIsNotActive() = runTest {
        autoconsentFeature.self().setRawStoredState(Toggle.State(enable = false))

        assertFalse(testee.isActive())
    }

    @Test
    fun whenPreferenceAppliedOnThenAutoconsentIsEnabled() = runTest {
        testee.apply(true)

        verify(autoconsent).changeSetting(true)
    }

    @Test
    fun whenPreferenceAppliedOffThenAutoconsentIsDisabled() = runTest {
        testee.apply(false)

        verify(autoconsent).changeSetting(false)
    }
}
