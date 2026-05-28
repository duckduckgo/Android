/*
 * Copyright (c) 2024 DuckDuckGo
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

package com.duckduckgo.experiments.impl.reinstalls

import com.duckduckgo.app.statistics.store.StatisticsDataStore
import com.duckduckgo.appbuildconfig.api.AppBuildConfig
import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.feature.toggles.api.Toggle
import com.squareup.moshi.Moshi
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class ReinstallAtbListenerTest {

    private lateinit var testee: ReinstallAtbListener

    private val mockBackupDataStore: BackupServiceDataStore = mock()
    private val mockStatisticsDataStore: StatisticsDataStore = mock()
    private val mockAppBuildConfig: AppBuildConfig = mock()
    private val mockReinstallerVariantProtectionFeature: ReinstallerVariantProtectionFeature = mock()
    private val mockProtectionToggle: Toggle = mock()
    private val moshi: Moshi = Moshi.Builder().build()

    @get:Rule
    val coroutineTestRule: CoroutineTestRule = CoroutineTestRule()

    @Before
    fun before() {
        whenever(mockReinstallerVariantProtectionFeature.self()).thenReturn(mockProtectionToggle)
        whenever(mockProtectionToggle.isEnabled()).thenReturn(false)

        testee = ReinstallAtbListener(
            mockBackupDataStore,
            mockStatisticsDataStore,
            mockAppBuildConfig,
            coroutineTestRule.testDispatcherProvider,
            mockReinstallerVariantProtectionFeature,
            moshi,
        )
    }

    @Test
    fun whenBeforeAtbInitIsCalledThenClearBackupServiceSharedPreferences() = runTest {
        whenever(mockAppBuildConfig.isAppReinstall()).thenReturn(false)

        testee.beforeAtbInit()

        verify(mockBackupDataStore).clearBackupPreferences()
    }

    @Test
    fun whenIsAppReinstallThenUpdateVariantForReturningUser() = runTest {
        whenever(mockAppBuildConfig.isAppReinstall()).thenReturn(true)

        testee.beforeAtbInit()

        verify(mockStatisticsDataStore).variant = REINSTALL_VARIANT
    }

    @Test
    fun whenIsNotAppReinstallThenVariantForReturningUserIsNotSet() = runTest {
        whenever(mockAppBuildConfig.isAppReinstall()).thenReturn(false)

        testee.beforeAtbInit()

        verify(mockStatisticsDataStore, never()).variant = REINSTALL_VARIANT
    }

    @Test
    fun whenIsAppReinstallAndProtectionFeatureDisabledThenUpdateVariantEvenIfMatchingExistingValue() = runTest {
        whenever(mockAppBuildConfig.isAppReinstall()).thenReturn(true)
        whenever(mockStatisticsDataStore.variant).thenReturn("de")
        whenever(mockProtectionToggle.isEnabled()).thenReturn(false)
        whenever(mockProtectionToggle.getSettings()).thenReturn("""{"variants":["de","df"]}""")

        testee.beforeAtbInit()

        verify(mockStatisticsDataStore).variant = REINSTALL_VARIANT
    }

    @Test
    fun whenIsAppReinstallAndProtectionEnabledAndCurrentVariantIsProtectedThenDoNotOverwrite() = runTest {
        whenever(mockAppBuildConfig.isAppReinstall()).thenReturn(true)
        whenever(mockStatisticsDataStore.variant).thenReturn("de")
        whenever(mockProtectionToggle.isEnabled()).thenReturn(true)
        whenever(mockProtectionToggle.getSettings()).thenReturn("""{"variants":["de","df"]}""")

        testee.beforeAtbInit()

        verify(mockStatisticsDataStore, never()).variant = REINSTALL_VARIANT
    }

    @Test
    fun whenIsAppReinstallAndProtectionEnabledAndCurrentVariantIsNotProtectedThenOverwrite() = runTest {
        whenever(mockAppBuildConfig.isAppReinstall()).thenReturn(true)
        whenever(mockStatisticsDataStore.variant).thenReturn("mq")
        whenever(mockProtectionToggle.isEnabled()).thenReturn(true)
        whenever(mockProtectionToggle.getSettings()).thenReturn("""{"variants":["de","df"]}""")

        testee.beforeAtbInit()

        verify(mockStatisticsDataStore).variant = REINSTALL_VARIANT
    }

    @Test
    fun whenIsAppReinstallAndProtectionEnabledAndCurrentVariantIsNullThenOverwrite() = runTest {
        whenever(mockAppBuildConfig.isAppReinstall()).thenReturn(true)
        whenever(mockStatisticsDataStore.variant).thenReturn(null)
        whenever(mockProtectionToggle.isEnabled()).thenReturn(true)
        whenever(mockProtectionToggle.getSettings()).thenReturn("""{"variants":["de","df"]}""")

        testee.beforeAtbInit()

        verify(mockStatisticsDataStore).variant = REINSTALL_VARIANT
    }

    @Test
    fun whenIsAppReinstallAndProtectionEnabledButSettingsAreNullThenOverwrite() = runTest {
        whenever(mockAppBuildConfig.isAppReinstall()).thenReturn(true)
        whenever(mockStatisticsDataStore.variant).thenReturn("de")
        whenever(mockProtectionToggle.isEnabled()).thenReturn(true)
        whenever(mockProtectionToggle.getSettings()).thenReturn(null)

        testee.beforeAtbInit()

        verify(mockStatisticsDataStore).variant = REINSTALL_VARIANT
    }

    @Test
    fun whenIsAppReinstallAndProtectionEnabledButProtectedListIsEmptyThenOverwrite() = runTest {
        whenever(mockAppBuildConfig.isAppReinstall()).thenReturn(true)
        whenever(mockStatisticsDataStore.variant).thenReturn("de")
        whenever(mockProtectionToggle.isEnabled()).thenReturn(true)
        whenever(mockProtectionToggle.getSettings()).thenReturn("""{"variants":[]}""")

        testee.beforeAtbInit()

        verify(mockStatisticsDataStore).variant = REINSTALL_VARIANT
    }
}
