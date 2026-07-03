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

package com.duckduckgo.autoconsent.impl.store

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.duckduckgo.autoconsent.api.CookiePopUpPreference
import com.duckduckgo.autoconsent.impl.remoteconfig.AutoconsentFeature
import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.feature.toggles.api.FakeFeatureToggleFactory
import com.duckduckgo.feature.toggles.api.Toggle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RealAutoconsentSettingsDataStoreTest {

    @get:Rule
    val coroutineRule = CoroutineTestRule()

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val feature = FakeFeatureToggleFactory.create(AutoconsentFeature::class.java)

    @Before
    fun setup() {
        context.getSharedPreferences(PREFS_FILENAME, Context.MODE_PRIVATE).edit().clear().commit()
        feature.onByDefault().setRawStoredState(Toggle.State(enable = true))
        feature.cookiePopUpPreferenceSetting().setRawStoredState(Toggle.State(enable = true))
    }

    @Test
    fun whenLegacyUserSettingIsFalseThenMigratesToDoNotBlock() = runTest {
        preferences().edit().putBoolean(LEGACY_USER_SETTING_KEY, false).commit()

        assertEquals(CookiePopUpPreference.off, createDataStore().cookiePopUpPreference)
    }

    @Test
    fun whenLegacyUserSettingIsTrueThenMigratesToBlockStandard() = runTest {
        preferences().edit().putBoolean(LEGACY_USER_SETTING_KEY, true).commit()

        assertEquals(CookiePopUpPreference.`default`, createDataStore().cookiePopUpPreference)
    }

    @Test
    fun whenNoLegacySettingAndOnByDefaultThenMigratesToBlockStandard() = runTest {
        assertEquals(CookiePopUpPreference.`default`, createDataStore().cookiePopUpPreference)
    }

    @Test
    fun whenNoLegacySettingAndOffByDefaultThenMigratesToDoNotBlock() = runTest {
        feature.onByDefault().setRawStoredState(Toggle.State(enable = false))

        assertEquals(CookiePopUpPreference.off, createDataStore().cookiePopUpPreference)
    }

    @Test
    fun whenReadBeforeRemoteConfigThenPreferenceUpdatesAfterCacheInvalidation() = runTest {
        feature.onByDefault().setRawStoredState(Toggle.State(enable = false))
        val dataStore = createDataStore()

        assertEquals(CookiePopUpPreference.off, dataStore.cookiePopUpPreference)
        assertFalse(preferences().contains(COOKIE_POP_UP_PREFERENCE_KEY))

        feature.onByDefault().setRawStoredState(Toggle.State(enable = true))
        dataStore.invalidateCache()

        assertEquals(CookiePopUpPreference.`default`, dataStore.cookiePopUpPreference)
        assertFalse(preferences().contains(COOKIE_POP_UP_PREFERENCE_KEY))
    }

    @Test
    fun whenPreferenceAlreadyStoredThenMigrationIsIdempotent() = runTest {
        val dataStore = createDataStore()
        dataStore.cookiePopUpPreference = CookiePopUpPreference.max
        preferences().edit().putBoolean(LEGACY_USER_SETTING_KEY, false).commit()

        assertEquals(CookiePopUpPreference.max, createDataStore().cookiePopUpPreference)
    }

    @Test
    fun whenUserSettingSetToTrueThenLegacySettingIsTrue() {
        val dataStore = createDataStore()
        dataStore.userSetting = true

        assertTrue(dataStore.userSetting)
    }

    @Test
    fun whenUserSettingSetToFalseThenLegacySettingIsFalse() {
        val dataStore = createDataStore()
        dataStore.userSetting = false

        assertFalse(dataStore.userSetting)
    }

    private fun preferences() = context.getSharedPreferences(PREFS_FILENAME, Context.MODE_PRIVATE)

    private fun createDataStore(): RealAutoconsentSettingsDataStore {
        return RealAutoconsentSettingsDataStore(
            context = context,
            autoconsentFeature = feature,
            appCoroutineScope = coroutineRule.testScope,
            dispatcherProvider = coroutineRule.testDispatcherProvider,
        )
    }

    companion object {
        private const val PREFS_FILENAME = "com.duckduckgo.autoconsent.store.settings"
        private const val LEGACY_USER_SETTING_KEY = "AutoconsentUserSetting"
        private const val COOKIE_POP_UP_PREFERENCE_KEY = "AutoconsentCookiePopUpPreference"
    }
}
