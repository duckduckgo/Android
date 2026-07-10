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

import android.annotation.SuppressLint
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.duckduckgo.autoconsent.impl.remoteconfig.AutoconsentFeature
import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.feature.toggles.api.FakeFeatureToggleFactory
import com.duckduckgo.feature.toggles.api.Toggle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
@SuppressLint("DenyListedApi")
class RealAutoconsentSettingsDataStoreTest {

    @get:Rule
    val coroutineRule = CoroutineTestRule()

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val feature = FakeFeatureToggleFactory.create(AutoconsentFeature::class.java)

    @Before
    fun setup() {
        context.getSharedPreferences(PREFS_FILENAME, Context.MODE_PRIVATE).edit().clear().commit()
        feature.onByDefault().setRawStoredState(Toggle.State(enable = true))
    }

    @Test
    fun whenNoStoredSettingAndOnByDefaultThenUserSettingIsTrue() = runTest {
        assertTrue(createDataStore().userSetting)
    }

    @Test
    fun whenNoStoredSettingAndOffByDefaultThenUserSettingIsFalse() = runTest {
        feature.onByDefault().setRawStoredState(Toggle.State(enable = false))

        assertFalse(createDataStore().userSetting)
    }

    @Test
    fun whenReadBeforeRemoteConfigThenUserSettingUpdatesAfterCacheInvalidation() = runTest {
        feature.onByDefault().setRawStoredState(Toggle.State(enable = false))
        val dataStore = createDataStore()

        assertFalse(dataStore.userSetting)
        assertFalse(preferences().contains(USER_SETTING_KEY))

        feature.onByDefault().setRawStoredState(Toggle.State(enable = true))
        dataStore.invalidateCache()

        assertTrue(dataStore.userSetting)
        assertFalse(preferences().contains(USER_SETTING_KEY))
    }

    @Test
    fun whenUserSettingAlreadyStoredThenDefaultIsNotApplied() = runTest {
        preferences().edit().putBoolean(USER_SETTING_KEY, false).commit()

        assertFalse(createDataStore().userSetting)
    }

    @Test
    fun whenUserSettingSetToTrueThenStoredValueIsTrue() {
        val dataStore = createDataStore()
        dataStore.userSetting = true

        assertTrue(dataStore.userSetting)
        assertTrue(preferences().getBoolean(USER_SETTING_KEY, false))
    }

    @Test
    fun whenUserSettingSetToFalseThenStoredValueIsFalse() {
        val dataStore = createDataStore()
        dataStore.userSetting = false

        assertFalse(dataStore.userSetting)
        assertFalse(preferences().getBoolean(USER_SETTING_KEY, true))
    }

    @Test
    fun whenClickAcceptEnabledNotStoredThenDefaultsToFalse() {
        assertFalse(createDataStore().clickAcceptEnabled)
    }

    @Test
    fun whenClickAcceptEnabledSetToTrueThenStoredValueIsTrue() {
        val dataStore = createDataStore()
        dataStore.clickAcceptEnabled = true

        assertTrue(dataStore.clickAcceptEnabled)
        assertTrue(preferences().getBoolean(CLICK_ACCEPT_ENABLED_KEY, false))
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
        private const val USER_SETTING_KEY = "AutoconsentUserSetting"
        private const val CLICK_ACCEPT_ENABLED_KEY = "AutoconsentClickAcceptEnabled"
    }
}
