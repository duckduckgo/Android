/*
 * Copyright (c) 2022 DuckDuckGo
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

package com.duckduckgo.mobile.android.vpn.ui.onboarding

import android.content.SharedPreferences
import androidx.core.content.edit
import com.duckduckgo.common.test.api.InMemorySharedPreferences
import com.duckduckgo.data.store.api.SharedPreferencesProvider
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.time.Instant
import java.util.concurrent.TimeUnit

class SharedPreferencesVpnStoreTest {

    private val sharedPreferencesProvider = mock<SharedPreferencesProvider>()

    private lateinit var sharedPreferencesVpnStore: SharedPreferencesVpnStore
    private lateinit var preferences: SharedPreferences

    @Before
    fun setup() {
        preferences = InMemorySharedPreferences()
        whenever(
            sharedPreferencesProvider.getSharedPreferences(eq("com.duckduckgo.android.atp.onboarding.store"), eq(true), eq(true)),
        ).thenReturn(preferences)

        sharedPreferencesVpnStore = SharedPreferencesVpnStore(sharedPreferencesProvider)
    }

    @Test
    fun whenOnboardingDidShowThenSetPreferenceValueToTrue() {
        assertFalse(sharedPreferencesVpnStore.didShowOnboarding())

        sharedPreferencesVpnStore.onboardingDidShow()

        assertTrue(sharedPreferencesVpnStore.didShowOnboarding())
    }

    @Test
    fun whenOnboardingDidNotShowThenSetPreferenceValueToFalse() {
        sharedPreferencesVpnStore.onboardingDidShow()

        assertTrue(sharedPreferencesVpnStore.didShowOnboarding())

        sharedPreferencesVpnStore.onboardingDidNotShow()

        assertFalse(sharedPreferencesVpnStore.didShowOnboarding())
    }

    @Test
    fun whenAppTpEnabledCtaDidShowThenSetPreferenceValueToTrue() {
        assertFalse(sharedPreferencesVpnStore.didShowAppTpEnabledCta())

        sharedPreferencesVpnStore.appTpEnabledCtaDidShow()

        assertTrue(sharedPreferencesVpnStore.didShowAppTpEnabledCta())
    }

    @Test
    fun whenIsOnboardingSessionCalledWithoutBeingSetThenReturnFalse() {
        assertTrue(sharedPreferencesVpnStore.getAndSetOnboardingSession())
        assertNotEquals(-1, preferences.getLong("KEY_APP_TP_ONBOARDING_BANNER_EXPIRY_TIMESTAMP", -1))
        assertTrue(sharedPreferencesVpnStore.getAndSetOnboardingSession())
        preferences.edit {
            putLong(
                "KEY_APP_TP_ONBOARDING_BANNER_EXPIRY_TIMESTAMP",
                Instant.now().toEpochMilli().minus(TimeUnit.DAYS.toMillis(1)),
            )
        }
        assertFalse(sharedPreferencesVpnStore.getAndSetOnboardingSession())
    }
}
