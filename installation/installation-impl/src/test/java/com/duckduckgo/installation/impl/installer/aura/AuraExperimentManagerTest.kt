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

package com.duckduckgo.installation.impl.installer.com.duckduckgo.installation.impl.installer.aura

import com.duckduckgo.app.statistics.store.StatisticsDataStore
import com.duckduckgo.browser.api.referrer.AppReferrer
import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.feature.toggles.api.Toggle
import com.duckduckgo.installation.impl.installer.InstallSourceExtractor
import com.duckduckgo.installation.impl.installer.aura.AuraExperimentFeature
import com.duckduckgo.installation.impl.installer.aura.AuraExperimentListJsonParser
import com.duckduckgo.installation.impl.installer.aura.AuraExperimentManager
import com.duckduckgo.installation.impl.installer.aura.Packages
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.*

class AuraExperimentManagerTest {

    @get:Rule
    var coroutinesTestRule = CoroutineTestRule()

    private val auraExperimentFeature: AuraExperimentFeature = mock()
    private val auraExperimentListJsonParser: AuraExperimentListJsonParser = mock()
    private val installSourceExtractor: InstallSourceExtractor = mock()
    private val statisticsDataStore: StatisticsDataStore = mock()
    private val appReferrer: AppReferrer = mock()
    private val toggle: Toggle = mock()

    private lateinit var testee: AuraExperimentManager

    @Before
    fun setup() {
        testee = AuraExperimentManager(
            auraExperimentFeature,
            auraExperimentListJsonParser,
            installSourceExtractor,
            statisticsDataStore,
            appReferrer,
            coroutinesTestRule.testDispatcherProvider,
        )
        whenever(auraExperimentFeature.self()).thenReturn(toggle)
    }

    @Test
    fun whenFeatureIsDisabledThenInitializeDoesNothing() = runTest {
        whenever(toggle.isEnabled()).thenReturn(false)

        testee.beforeAtbInit()

        verifyNoInteractions(auraExperimentListJsonParser, installSourceExtractor, statisticsDataStore, appReferrer)
    }

    @Test
    fun whenFeatureIsEnabledAndInstallSourceIsNullThenInitializeDoesNothing() = runTest {
        whenever(toggle.isEnabled()).thenReturn(true)
        whenever(installSourceExtractor.extract()).thenReturn(null)

        testee.beforeAtbInit()

        verifyNoInteractions(auraExperimentListJsonParser, statisticsDataStore, appReferrer)
    }

    @Test
    fun whenFeatureIsEnabledAndInstallSourceNotInPackagesThenInitializeDoesNothing() = runTest {
        whenever(toggle.isEnabled()).thenReturn(true)
        whenever(installSourceExtractor.extract()).thenReturn("x.y.z")
        whenever(toggle.getSettings()).thenReturn("json")
        whenever(auraExperimentListJsonParser.parseJson("json")).thenReturn(Packages(list = listOf("a.b.c")))

        testee.beforeAtbInit()

        verifyNoInteractions(statisticsDataStore, appReferrer)
    }

    @Test
    fun whenFeatureIsEnabledAndInstallSourceInPackagesThenSetVariantAndOrigin() = runTest {
        whenever(toggle.isEnabled()).thenReturn(true)
        whenever(installSourceExtractor.extract()).thenReturn("a.b.c")
        whenever(toggle.getSettings()).thenReturn("json")
        whenever(auraExperimentListJsonParser.parseJson("json")).thenReturn(Packages(list = listOf("a.b.c")))

        testee.beforeAtbInit()

        verify(statisticsDataStore).variant = AuraExperimentManager.VARIANT
        verify(appReferrer).setOriginAttributeCampaign(AuraExperimentManager.ORIGIN)
    }
}
