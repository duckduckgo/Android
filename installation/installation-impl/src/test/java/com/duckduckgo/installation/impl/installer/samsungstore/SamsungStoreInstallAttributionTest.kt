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

package com.duckduckgo.installation.impl.installer.samsungstore

import com.duckduckgo.app.statistics.AtbInitializerListener
import com.duckduckgo.appbuildconfig.api.AppBuildConfig
import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.experiments.api.VariantManager
import com.duckduckgo.feature.toggles.api.Toggle
import com.duckduckgo.installation.impl.installer.InstallSourceExtractor
import com.duckduckgo.installation.impl.installer.samsungstore.SamsungStoreInstallAttribution.Companion.ORIGIN
import com.duckduckgo.installation.impl.installer.samsungstore.SamsungStoreInstallAttribution.Companion.REINSTALL_VARIANT
import com.duckduckgo.installation.impl.installer.samsungstore.SamsungStoreInstallAttribution.Companion.SAMSUNG_STORE_PACKAGE
import com.duckduckgo.installation.impl.installer.samsungstore.SamsungStoreInstallAttribution.Companion.VARIANT
import com.duckduckgo.referral.api.AppReferrer
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.whenever

class SamsungStoreInstallAttributionTest {

    @get:Rule
    var coroutinesTestRule = CoroutineTestRule()

    private val feature: SamsungStoreInstallAttributionFeature = mock()
    private val toggle: Toggle = mock()
    private val installSourceExtractor: InstallSourceExtractor = mock()
    private val appBuildConfig: AppBuildConfig = mock()
    private val variantManager: VariantManager = mock()
    private val appReferrer: AppReferrer = mock()

    private lateinit var testee: SamsungStoreInstallAttribution

    @Before
    fun setup() {
        whenever(feature.self()).thenReturn(toggle)
        whenever(toggle.isEnabled()).thenReturn(true)
        runTest { whenever(appBuildConfig.isAppReinstall()).thenReturn(false) }

        testee = SamsungStoreInstallAttribution(
            feature = feature,
            installSourceExtractor = installSourceExtractor,
            appBuildConfig = appBuildConfig,
            variantManager = variantManager,
            appReferrer = appReferrer,
            dispatcherProvider = coroutinesTestRule.testDispatcherProvider,
        )
    }

    @Test
    fun whenInstalledFromGalaxyStoreThenVariantAndOriginAreSet() = runTest {
        whenever(installSourceExtractor.extract()).thenReturn(SAMSUNG_STORE_PACKAGE)

        testee.beforeAtbInit()

        verify(variantManager).updateAppReferrerVariant(VARIANT)
        verify(appReferrer).setOriginAttributeCampaign(ORIGIN)
    }

    @Test
    fun whenReturningUserFromGalaxyStoreThenReinstallVariantAndOriginAreSet() = runTest {
        whenever(installSourceExtractor.extract()).thenReturn(SAMSUNG_STORE_PACKAGE)
        whenever(appBuildConfig.isAppReinstall()).thenReturn(true)

        testee.beforeAtbInit()

        verify(variantManager).updateAppReferrerVariant(REINSTALL_VARIANT)
        verify(appReferrer).setOriginAttributeCampaign(ORIGIN)
    }

    @Test
    fun reservedVariantsAreTwoCharacterKeysLikeTheExistingOnes() {
        assertEquals(2, VARIANT.length)
        assertEquals(2, REINSTALL_VARIANT.length)
        assertNotEquals(VARIANT, REINSTALL_VARIANT)
    }

    @Test
    fun whenInstalledFromOtherSourceThenNothingIsTagged() = runTest {
        whenever(installSourceExtractor.extract()).thenReturn("com.android.vending")

        testee.beforeAtbInit()

        verifyNoInteractions(variantManager, appReferrer)
    }

    @Test
    fun whenInstallSourceIsUnknownThenNothingIsTagged() = runTest {
        whenever(installSourceExtractor.extract()).thenReturn(null)

        testee.beforeAtbInit()

        verifyNoInteractions(variantManager, appReferrer)
    }

    @Test
    fun whenExtractorThrowsThenNothingIsTagged() = runTest {
        whenever(installSourceExtractor.extract()).thenThrow(IllegalStateException("package manager died"))

        testee.beforeAtbInit()

        verifyNoInteractions(variantManager, appReferrer)
    }

    @Test
    fun whenFeatureIsDisabledThenInstallSourceIsNotEvenRead() = runTest {
        whenever(toggle.isEnabled()).thenReturn(false)

        testee.beforeAtbInit()

        verifyNoInteractions(installSourceExtractor, variantManager, appReferrer)
    }

    @Test
    fun priorityRunsAfterReinstallListenerAndAfterAura() {
        assertTrue(PRIORITY_SAMSUNG_STORE_INSTALL_ATTRIBUTION > AtbInitializerListener.PRIORITY_REINSTALL_LISTENER)
        assertTrue(PRIORITY_SAMSUNG_STORE_INSTALL_ATTRIBUTION > AtbInitializerListener.PRIORITY_AURA_EXPERIMENT_MANAGER)
    }
}
