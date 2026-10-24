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
import com.duckduckgo.common.utils.DefaultDispatcherProvider
import com.duckduckgo.feature.toggles.api.Toggle
import com.squareup.moshi.Moshi
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.yield
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.doSuspendableAnswer
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
    private val mockSelfToggle: Toggle = mock()
    private val mockProtectVariantsToggle: Toggle = mock()
    private val moshi: Moshi = Moshi.Builder().build()

    @get:Rule
    val coroutineTestRule: CoroutineTestRule = CoroutineTestRule()

    @Before
    fun before() {
        whenever(mockReinstallerVariantProtectionFeature.self()).thenReturn(mockSelfToggle)
        whenever(mockReinstallerVariantProtectionFeature.protectVariants()).thenReturn(mockProtectVariantsToggle)
        whenever(mockSelfToggle.isEnabled()).thenReturn(false)
        whenever(mockProtectVariantsToggle.isEnabled()).thenReturn(false)

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
        whenever(mockSelfToggle.isEnabled()).thenReturn(false)
        whenever(mockProtectVariantsToggle.isEnabled()).thenReturn(true)
        whenever(mockProtectVariantsToggle.getSettings()).thenReturn("""{"variants":["de","df"]}""")

        testee.beforeAtbInit()

        verify(mockStatisticsDataStore).variant = REINSTALL_VARIANT
    }

    @Test
    fun whenIsAppReinstallAndProtectVariantsDisabledThenUpdateVariantEvenIfMatchingExistingValue() = runTest {
        whenever(mockAppBuildConfig.isAppReinstall()).thenReturn(true)
        whenever(mockStatisticsDataStore.variant).thenReturn("de")
        whenever(mockSelfToggle.isEnabled()).thenReturn(true)
        whenever(mockProtectVariantsToggle.isEnabled()).thenReturn(false)
        whenever(mockProtectVariantsToggle.getSettings()).thenReturn("""{"variants":["de","df"]}""")

        testee.beforeAtbInit()

        verify(mockStatisticsDataStore).variant = REINSTALL_VARIANT
    }

    @Test
    fun whenIsAppReinstallAndProtectionEnabledAndCurrentVariantIsProtectedThenDoNotOverwrite() = runTest {
        whenever(mockAppBuildConfig.isAppReinstall()).thenReturn(true)
        whenever(mockStatisticsDataStore.variant).thenReturn("de")
        whenever(mockSelfToggle.isEnabled()).thenReturn(true)
        whenever(mockProtectVariantsToggle.isEnabled()).thenReturn(true)
        whenever(mockProtectVariantsToggle.getSettings()).thenReturn("""{"variants":["de","df"]}""")

        testee.beforeAtbInit()

        verify(mockStatisticsDataStore, never()).variant = REINSTALL_VARIANT
    }

    @Test
    fun whenIsAppReinstallAndProtectionEnabledAndCurrentVariantIsNotProtectedThenOverwrite() = runTest {
        whenever(mockAppBuildConfig.isAppReinstall()).thenReturn(true)
        whenever(mockStatisticsDataStore.variant).thenReturn("mq")
        whenever(mockSelfToggle.isEnabled()).thenReturn(true)
        whenever(mockProtectVariantsToggle.isEnabled()).thenReturn(true)
        whenever(mockProtectVariantsToggle.getSettings()).thenReturn("""{"variants":["de","df"]}""")

        testee.beforeAtbInit()

        verify(mockStatisticsDataStore).variant = REINSTALL_VARIANT
    }

    @Test
    fun whenIsAppReinstallAndProtectionEnabledAndCurrentVariantIsNullThenOverwrite() = runTest {
        whenever(mockAppBuildConfig.isAppReinstall()).thenReturn(true)
        whenever(mockStatisticsDataStore.variant).thenReturn(null)
        whenever(mockSelfToggle.isEnabled()).thenReturn(true)
        whenever(mockProtectVariantsToggle.isEnabled()).thenReturn(true)
        whenever(mockProtectVariantsToggle.getSettings()).thenReturn("""{"variants":["de","df"]}""")

        testee.beforeAtbInit()

        verify(mockStatisticsDataStore).variant = REINSTALL_VARIANT
    }

    @Test
    fun whenIsAppReinstallAndProtectionEnabledButSettingsAreNullThenOverwrite() = runTest {
        whenever(mockAppBuildConfig.isAppReinstall()).thenReturn(true)
        whenever(mockStatisticsDataStore.variant).thenReturn("de")
        whenever(mockSelfToggle.isEnabled()).thenReturn(true)
        whenever(mockProtectVariantsToggle.isEnabled()).thenReturn(true)
        whenever(mockProtectVariantsToggle.getSettings()).thenReturn(null)

        testee.beforeAtbInit()

        verify(mockStatisticsDataStore).variant = REINSTALL_VARIANT
    }

    @Test
    fun whenIsAppReinstallAndProtectionEnabledButProtectedListIsEmptyThenOverwrite() = runTest {
        whenever(mockAppBuildConfig.isAppReinstall()).thenReturn(true)
        whenever(mockStatisticsDataStore.variant).thenReturn("de")
        whenever(mockSelfToggle.isEnabled()).thenReturn(true)
        whenever(mockProtectVariantsToggle.isEnabled()).thenReturn(true)
        whenever(mockProtectVariantsToggle.getSettings()).thenReturn("""{"variants":[]}""")

        testee.beforeAtbInit()

        verify(mockStatisticsDataStore).variant = REINSTALL_VARIANT
    }

    @Test
    fun whenReinstallCheckRunsPastTheTimeLimitThenVariantForReturningUserIsStillSet() = runTest {
        whenever(mockAppBuildConfig.isAppReinstall()).doSuspendableAnswer {
            delay(testee.beforeAtbInitTimeoutMillis() * 2)
            true
        }

        val result = withTimeoutOrNull(testee.beforeAtbInitTimeoutMillis()) { testee.beforeAtbInit() }

        assertNull(result)
        verify(mockStatisticsDataStore).variant = REINSTALL_VARIANT
    }

    @Test
    fun whenTimeLimitIsReachedBeforeTheReinstallCheckStartsThenCheckStillRunsAndVariantIsSet() = runBlocking {
        val realDispatcherTestee = ReinstallAtbListener(
            mockBackupDataStore,
            mockStatisticsDataStore,
            mockAppBuildConfig,
            DefaultDispatcherProvider(),
            mockReinstallerVariantProtectionFeature,
            moshi,
        )
        whenever(mockBackupDataStore.clearBackupPreferences()).doAnswer { Thread.sleep(SLOW_BLOCKING_WORK_MS) }
        // Like the real implementation, which switches context, the check notices cancellation as soon as it starts
        whenever(mockAppBuildConfig.isAppReinstall()).doSuspendableAnswer {
            yield()
            true
        }

        val result = withTimeoutOrNull(SHORT_TIME_LIMIT_MS) { realDispatcherTestee.beforeAtbInit() }

        assertNull(result)
        verify(mockStatisticsDataStore).variant = REINSTALL_VARIANT
    }

    @Test
    fun whenReinstallStepOnlyBlocksPastTheTimeLimitThenTheTimeLimitIsNotReported() = runBlocking {
        val realDispatcherProvider = DefaultDispatcherProvider()
        val realDispatcherTestee = ReinstallAtbListener(
            mockBackupDataStore,
            mockStatisticsDataStore,
            mockAppBuildConfig,
            realDispatcherProvider,
            mockReinstallerVariantProtectionFeature,
            moshi,
        )
        whenever(mockAppBuildConfig.isAppReinstall()).doSuspendableAnswer {
            Thread.sleep(SLOW_BLOCKING_WORK_MS)
            true
        }

        // Started on the listener's own dispatcher, as AtbInitializer does, so entering the step doesn't switch threads
        val result = withContext(realDispatcherProvider.io()) {
            withTimeoutOrNull(SHORT_TIME_LIMIT_MS) { realDispatcherTestee.beforeAtbInit() }
        }

        assertEquals(Unit, result)
        verify(mockStatisticsDataStore).variant = REINSTALL_VARIANT
    }

    private companion object {
        const val SHORT_TIME_LIMIT_MS = 50L
        const val SLOW_BLOCKING_WORK_MS = 300L
    }
}
