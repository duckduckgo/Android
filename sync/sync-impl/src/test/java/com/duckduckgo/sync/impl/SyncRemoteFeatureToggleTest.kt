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

import android.content.Context
import android.content.SharedPreferences
import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.core.app.NotificationManagerCompat
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.duckduckgo.appbuildconfig.api.AppBuildConfig
import com.duckduckgo.appbuildconfig.api.BuildFlavor
import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.feature.toggles.api.Toggle
import com.duckduckgo.feature.toggles.api.Toggle.State
import com.duckduckgo.sync.store.SharedPrefsProvider
import com.duckduckgo.sync.store.SyncSharedPrefsStore
import kotlinx.coroutines.test.TestScope
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

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

    private lateinit var testee: SyncRemoteFeatureToggle

    @Test
    fun whenFeatureDisabledThenInternalBuildShowSyncTrue() {
        whenever(appBuildConfig.flavor).thenReturn(BuildFlavor.INTERNAL)
        val syncFeature = TestSyncFeature(appBuildConfig).apply {
            sync = false
        }
        givenSyncRemoteFeatureToggle(syncFeature)

        assertTrue(testee.showSync())
    }

    @Test
    fun whenFeatureDisabledThenShowSyncIsFalse() {
        whenever(appBuildConfig.flavor).thenReturn(BuildFlavor.PLAY)
        val syncFeature = TestSyncFeature(appBuildConfig).apply {
            sync = false
        }
        givenSyncRemoteFeatureToggle(syncFeature)

        assertFalse(testee.showSync())
    }

    @Test
    fun whenShowSyncDisabledThenAllFeaturesDisabled() {
        val syncFeature = TestSyncFeature(appBuildConfig).apply {
            showSync = false
        }
        givenSyncRemoteFeatureToggle(syncFeature)

        assertFalse(testee.allowDataSyncing())
        assertFalse(testee.allowSetupFlows())
        assertFalse(testee.allowCreateAccount())
    }

    @Test
    fun whenAllowDataSyncingFalseThenAllowDataSyncingFalse() {
        val syncFeature = TestSyncFeature(appBuildConfig).apply {
            allowDataSyncing = false
        }
        givenSyncRemoteFeatureToggle(syncFeature)

        assertFalse(testee.allowDataSyncing())
    }

    @Test
    fun whenAllowDataSyncEnabledButNotForThisVersionThenAllowDataSyncingOnNewerVersionTrue() {
        whenever(appBuildConfig.versionCode).thenReturn(1)
        val syncFeature = TestSyncFeature(appBuildConfig).apply {
            allowDataSyncing = true
            minSupportedVersion = 2
        }
        givenSyncRemoteFeatureToggle(syncFeature)

        assertFalse(testee.allowDataSyncing())
        assertTrue(testee.allowDataSyncingOnNewerVersion())
    }

    @Test
    fun whenAllowDataSyncingFalseThenSetupFlowsAndCreateAccountDisabled() {
        val syncFeature = TestSyncFeature(appBuildConfig).apply {
            allowDataSyncing = false
        }
        givenSyncRemoteFeatureToggle(syncFeature)

        assertFalse(testee.allowSetupFlows())
        assertFalse(testee.allowCreateAccount())
    }

    @Test
    fun whenAllowSetupFlowsFalseThenAllowDataSyncingEnabled() {
        val syncFeature = TestSyncFeature(appBuildConfig).apply {
            allowSetupFlows = false
        }
        givenSyncRemoteFeatureToggle(syncFeature)

        assertTrue(testee.allowDataSyncing())
    }

    @Test
    fun whenAllowSetupFlowsEnabledButNotForThisVersionThenAllowSetupFlowsOnNewerVersionTrue() {
        whenever(appBuildConfig.versionCode).thenReturn(1)
        val syncFeature = TestSyncFeature(appBuildConfig).apply {
            allowSetupFlows = true
            minSupportedVersion = 2
        }
        givenSyncRemoteFeatureToggle(syncFeature)

        assertFalse(testee.allowSetupFlows())
        assertTrue(testee.allowSetupFlowsOnNewerVersion())
    }

    @Test
    fun whenAllowSetupFlowsFalseThenSetupFlowsAndCreateAccountDisabled() {
        val syncFeature = TestSyncFeature(appBuildConfig).apply {
            allowSetupFlows = false
        }
        givenSyncRemoteFeatureToggle(syncFeature)

        assertFalse(testee.allowSetupFlows())
        assertFalse(testee.allowCreateAccount())
    }

    @Test
    fun whenAllowCreateAccountFalseThenAllowCreateAccountFalse() {
        val syncFeature = TestSyncFeature(appBuildConfig).apply {
            allowCreateAccount = false
        }
        givenSyncRemoteFeatureToggle(syncFeature)

        assertFalse(testee.allowCreateAccount())
    }

    @Test
    fun whenAllowCreateAccountTrueButNotForThisVersionThenAllowCreateAccountOnNewerVersionTrue() {
        whenever(appBuildConfig.versionCode).thenReturn(1)
        val syncFeature = TestSyncFeature(appBuildConfig).apply {
            allowCreateAccount = true
            minSupportedVersion = 2
        }
        givenSyncRemoteFeatureToggle(syncFeature)

        assertFalse(testee.allowCreateAccount())
        assertTrue(testee.allowCreateAccountOnNewerVersion())
    }

    @Test
    fun whenAllowCreateAccountFalseThenDataSyncingAndSetupFlowsEnabled() {
        val syncFeature = TestSyncFeature(appBuildConfig).apply {
            allowCreateAccount = false
        }
        givenSyncRemoteFeatureToggle(syncFeature)

        assertTrue(testee.allowDataSyncing())
        assertTrue(testee.allowSetupFlows())
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

class TestSyncFeature(private val appBuildConfig: AppBuildConfig) : SyncFeature {
    var sync: Boolean = true
    var showSync: Boolean = true
    var allowDataSyncing: Boolean = true
    var allowSetupFlows: Boolean = true
    var allowCreateAccount: Boolean = true
    var minSupportedVersion: Int = 0
    override fun self(): TestToggle = TestToggle(sync, minSupportedVersion, appBuildConfig.versionCode)

    override fun level0ShowSync(): TestToggle = TestToggle(showSync, minSupportedVersion, appBuildConfig.versionCode)

    override fun level1AllowDataSyncing(): TestToggle = TestToggle(allowDataSyncing, minSupportedVersion, appBuildConfig.versionCode)

    override fun level2AllowSetupFlows(): TestToggle = TestToggle(allowSetupFlows, minSupportedVersion, appBuildConfig.versionCode)

    override fun level3AllowCreateAccount(): TestToggle = TestToggle(allowCreateAccount, minSupportedVersion, appBuildConfig.versionCode)
}

open class TestToggle(
    private val enabled: Boolean,
    private val minSupportedVersion: Int = 0,
    private val versionCode: Int = 0,
) : Toggle {
    override fun getRawStoredState(): Toggle.State? = State(
        remoteEnableState = enabled,
        minSupportedVersion = minSupportedVersion,
    )
    override fun setEnabled(state: Toggle.State) {}
    override fun isEnabled(): Boolean {
        return enabled && versionCode >= minSupportedVersion
    }
}

class TestSharedPrefsProvider(val context: Context) : SharedPrefsProvider {
    override fun getEncryptedSharedPrefs(fileName: String): SharedPreferences? {
        return context.getSharedPreferences(fileName, Context.MODE_PRIVATE)
    }

    override fun getSharedPrefs(fileName: String): SharedPreferences {
        return context.getSharedPreferences(fileName, Context.MODE_PRIVATE)
    }
}
