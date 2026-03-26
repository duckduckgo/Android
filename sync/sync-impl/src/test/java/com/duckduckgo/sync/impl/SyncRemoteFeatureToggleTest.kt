/*
 * Copyright (c) 2023 DuckDuckGo
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

package com.duckduckgo.sync.impl

import android.annotation.SuppressLint
import android.content.Context
import android.content.SharedPreferences
import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.core.app.NotificationManagerCompat
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.duckduckgo.appbuildconfig.api.AppBuildConfig
import com.duckduckgo.appbuildconfig.api.BuildFlavor
import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.feature.toggles.api.FakeFeatureToggleFactory
import com.duckduckgo.feature.toggles.api.Toggle.State
import com.duckduckgo.sync.store.SharedPrefsProvider
import com.duckduckgo.sync.store.SyncSharedPrefsStore
import kotlinx.coroutines.test.TestScope
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

@SuppressLint("DenyListedApi")
@RunWith(AndroidJUnit4::class)
class SyncRemoteFeatureToggleTest {

    @get:Rule
    var instantTaskExecutorRule = InstantTaskExecutorRule()

    @get:Rule
    var coroutinesTestRule = CoroutineTestRule()

    var appBuildConfig: AppBuildConfig = mock()

    private val context = InstrumentationRegistry.getInstrumentation().context
    private val notificationManager = NotificationManagerCompat.from(context)
    private val sharedPrefsProvider = TestSharedPrefsProvider(context)
    private val store = SyncSharedPrefsStore(sharedPrefsProvider, TestScope(), coroutinesTestRule.testDispatcherProvider)
    private val syncFeature = FakeFeatureToggleFactory.create(SyncFeature::class.java, appVersionProvider = { appBuildConfig.versionCode })

    private lateinit var testee: SyncRemoteFeatureToggle

    @Before
    fun setup() {
        syncFeature.self().setRawStoredState(State(enable = true))
    }

    @Test
    fun whenFeatureDisabledThenInternalBuildShowSyncTrue() {
        whenever(appBuildConfig.flavor).thenReturn(BuildFlavor.INTERNAL)
        syncFeature.self().setRawStoredState(State(enable = false))
        givenSyncRemoteFeatureToggle(syncFeature)

        assertTrue(testee.showSync())
    }

    @Test
    fun whenFeatureDisabledThenShowSyncIsFalse() {
        whenever(appBuildConfig.flavor).thenReturn(BuildFlavor.PLAY)
        syncFeature.self().setRawStoredState(State(enable = false))
        givenSyncRemoteFeatureToggle(syncFeature)

        assertFalse(testee.showSync())
    }

    @Test
    fun whenShowSyncDisabledThenAllFeaturesDisabled() {
        syncFeature.level0ShowSync().setRawStoredState(State(enable = false))
        givenSyncRemoteFeatureToggle(syncFeature)

        assertFalse(testee.allowDataSyncing())
        assertFalse(testee.allowSetupFlows())
        assertFalse(testee.allowCreateAccount())
    }

    @Test
    fun whenAllowDataSyncingFalseThenAllowDataSyncingFalse() {
        syncFeature.level1AllowDataSyncing().setRawStoredState(State(enable = false))
        givenSyncRemoteFeatureToggle(syncFeature)

        assertFalse(testee.allowDataSyncing())
    }

    @Test
    fun whenAllowDataSyncEnabledButNotForThisVersionThenAllowDataSyncingOnNewerVersionTrue() {
        whenever(appBuildConfig.versionCode).thenReturn(1)
        syncFeature.level1AllowDataSyncing().setRawStoredState(State(enable = true, minSupportedVersion = 2))
        givenSyncRemoteFeatureToggle(syncFeature)

        assertFalse(testee.allowDataSyncing())
        assertTrue(testee.allowDataSyncingOnNewerVersion())
    }

    @Test
    fun whenAllowDataSyncingFalseThenSetupFlowsAndCreateAccountDisabled() {
        syncFeature.level1AllowDataSyncing().setRawStoredState(State(enable = false))
        givenSyncRemoteFeatureToggle(syncFeature)

        assertFalse(testee.allowSetupFlows())
        assertFalse(testee.allowCreateAccount())
    }

    @Test
    fun whenAllowSetupFlowsFalseThenAllowDataSyncingEnabled() {
        syncFeature.level2AllowSetupFlows().setRawStoredState(State(enable = false))
        givenSyncRemoteFeatureToggle(syncFeature)

        assertTrue(testee.allowDataSyncing())
    }

    @Test
    fun whenAllowSetupFlowsEnabledButNotForThisVersionThenAllowSetupFlowsOnNewerVersionTrue() {
        whenever(appBuildConfig.versionCode).thenReturn(1)
        syncFeature.level2AllowSetupFlows().setRawStoredState(State(enable = true, minSupportedVersion = 2))
        givenSyncRemoteFeatureToggle(syncFeature)

        assertFalse(testee.allowSetupFlows())
        assertTrue(testee.allowSetupFlowsOnNewerVersion())
    }

    @Test
    fun whenAllowSetupFlowsFalseThenSetupFlowsAndCreateAccountDisabled() {
        syncFeature.level2AllowSetupFlows().setRawStoredState(State(enable = false))
        givenSyncRemoteFeatureToggle(syncFeature)

        assertFalse(testee.allowSetupFlows())
        assertFalse(testee.allowCreateAccount())
    }

    @Test
    fun whenAllowCreateAccountFalseThenAllowCreateAccountFalse() {
        syncFeature.level3AllowCreateAccount().setRawStoredState(State(enable = false))
        givenSyncRemoteFeatureToggle(syncFeature)

        assertFalse(testee.allowCreateAccount())
    }

    @Test
    fun whenAllowCreateAccountTrueButNotForThisVersionThenAllowCreateAccountOnNewerVersionTrue() {
        whenever(appBuildConfig.versionCode).thenReturn(1)
        syncFeature.level3AllowCreateAccount().setRawStoredState(State(enable = true, minSupportedVersion = 2))
        givenSyncRemoteFeatureToggle(syncFeature)

        assertFalse(testee.allowCreateAccount())
        assertTrue(testee.allowCreateAccountOnNewerVersion())
    }

    @Test
    fun whenAllowCreateAccountFalseThenDataSyncingAndSetupFlowsEnabled() {
        syncFeature.level3AllowCreateAccount().setRawStoredState(State(enable = false))
        givenSyncRemoteFeatureToggle(syncFeature)

        assertTrue(testee.allowDataSyncing())
        assertTrue(testee.allowSetupFlows())
    }

    @Test
    fun whenAllSyncLevelsEnabledAndAiChatSyncEnabledThenAllowAiChatSyncTrue() {
        syncFeature.aiChatSync().setRawStoredState(State(enable = true))
        givenSyncRemoteFeatureToggle(syncFeature)

        assertTrue(testee.allowAiChatSync())
    }

    @Test
    fun whenAllSyncLevelsEnabledAndAiChatSyncDisabledThenAllowAiChatSyncFalse() {
        syncFeature.aiChatSync().setRawStoredState(State(enable = false))
        givenSyncRemoteFeatureToggle(syncFeature)

        assertFalse(testee.allowAiChatSync())
    }

    @Test
    fun whenShowSyncDisabledThenAllowAiChatSyncFalse() {
        syncFeature.level0ShowSync().setRawStoredState(State(enable = false))
        syncFeature.aiChatSync().setRawStoredState(State(enable = true))
        givenSyncRemoteFeatureToggle(syncFeature)

        assertFalse(testee.allowAiChatSync())
    }

    @Test
    fun whenAllowDataSyncingDisabledThenAllowAiChatSyncFalse() {
        syncFeature.level1AllowDataSyncing().setRawStoredState(State(enable = false))
        syncFeature.aiChatSync().setRawStoredState(State(enable = true))
        givenSyncRemoteFeatureToggle(syncFeature)

        assertFalse(testee.allowAiChatSync())
    }

    @Test
    fun whenAllowSetupFlowsDisabledThenAllowAiChatSyncFalse() {
        syncFeature.level2AllowSetupFlows().setRawStoredState(State(enable = false))
        syncFeature.aiChatSync().setRawStoredState(State(enable = true))
        givenSyncRemoteFeatureToggle(syncFeature)

        assertFalse(testee.allowAiChatSync())
    }

    @Test
    fun whenAllowCreateAccountDisabledThenAllowAiChatSyncFalse() {
        syncFeature.level3AllowCreateAccount().setRawStoredState(State(enable = false))
        syncFeature.aiChatSync().setRawStoredState(State(enable = true))
        givenSyncRemoteFeatureToggle(syncFeature)

        assertFalse(testee.allowAiChatSync())
    }

    private fun givenSyncRemoteFeatureToggle(syncFeature: SyncFeature) {
        testee = SyncRemoteFeatureToggle(
            context = context,
            syncFeature = syncFeature,
            appBuildConfig = appBuildConfig,
            notificationManager = notificationManager,
            syncNotificationBuilder = mock(),
            syncStore = store,
            appCoroutineScope = coroutinesTestRule.testScope,
            coroutineDispatcher = coroutinesTestRule.testDispatcherProvider,
        )
    }
}

@SuppressLint("DenyListedApi")
class TestSharedPrefsProvider(val context: Context) : SharedPrefsProvider {
    override fun getEncryptedSharedPrefs(fileName: String): SharedPreferences? {
        return context.getSharedPreferences(fileName, Context.MODE_PRIVATE)
    }

    override fun getSharedPrefs(fileName: String): SharedPreferences {
        return context.getSharedPreferences(fileName, Context.MODE_PRIVATE)
    }
}
