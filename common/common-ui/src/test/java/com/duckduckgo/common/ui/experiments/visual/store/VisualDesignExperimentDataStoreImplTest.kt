/*
 * Copyright (c) 2025 DuckDuckGo
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

package com.duckduckgo.common.ui.experiments.visual.store

import app.cash.turbine.test
import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.common.ui.experiments.visual.ExperimentalUIThemingFeature
import com.duckduckgo.feature.toggles.api.FeatureTogglesInventory
import com.duckduckgo.feature.toggles.api.Toggle
import com.duckduckgo.feature.toggles.api.Toggle.FeatureName
import kotlinx.coroutines.test.runTest
import org.junit.Assert
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.Mock
import org.mockito.MockitoAnnotations
import org.mockito.kotlin.whenever

class VisualDesignExperimentDataStoreImplTest {

    @get:Rule
    var coroutineRule = CoroutineTestRule()

    @Mock
    private lateinit var experimentalUIThemingFeature: ExperimentalUIThemingFeature

    @Mock
    private lateinit var visualDesignFeatureToggle: Toggle

    @Mock
    private lateinit var duckChatPoCToggle: Toggle

    @Mock
    private lateinit var senseOfProtectionNewUserExperimentApr25Toggle: Toggle

    @Mock
    private lateinit var senseOfProtectionExistingUserExperimentApr25: Toggle

    @Mock
    private lateinit var senseOfProtectionNewUserExperimentMay25Toggle: Toggle

    @Mock
    private lateinit var senseOfProtectionExistingUserExperimentMay25: Toggle

    @Mock
    private lateinit var senseOfProtectionNewUserExperiment27May25: Toggle

    @Mock
    private lateinit var senseOfProtectionExistingUserExperiment27May25: Toggle

    @Mock
    private lateinit var defaultBrowserAdditionalPrompts202501: Toggle

    @Mock
    private lateinit var unrelatedToggle: Toggle

    @Mock
    private lateinit var togglesInventory: FeatureTogglesInventory

    @Before
    fun setUp() {
        MockitoAnnotations.openMocks(this)

        whenever(experimentalUIThemingFeature.visualUpdatesFeature()).thenReturn(visualDesignFeatureToggle)
        whenever(experimentalUIThemingFeature.duckAIPoCFeature()).thenReturn(duckChatPoCToggle)

        whenever(senseOfProtectionNewUserExperimentApr25Toggle.featureName()).thenReturn(
            FeatureName("SenseOfProtectionToggles", "senseOfProtectionNewUserExperimentApr25"),
        )
        whenever(senseOfProtectionExistingUserExperimentApr25.featureName()).thenReturn(
            FeatureName("SenseOfProtectionToggles", "senseOfProtectionExistingUserExperimentApr25"),
        )
        whenever(senseOfProtectionNewUserExperimentMay25Toggle.featureName()).thenReturn(
            FeatureName("SenseOfProtectionToggles", "senseOfProtectionNewUserExperimentMay25"),
        )
        whenever(senseOfProtectionExistingUserExperimentMay25.featureName()).thenReturn(
            FeatureName("SenseOfProtectionToggles", "senseOfProtectionExistingUserExperimentMay25"),
        )
        whenever(senseOfProtectionNewUserExperiment27May25.featureName()).thenReturn(
            FeatureName("SenseOfProtectionToggles", "senseOfProtectionNewUserExperiment27May25"),
        )
        whenever(senseOfProtectionExistingUserExperiment27May25.featureName()).thenReturn(
            FeatureName("SenseOfProtectionToggles", "senseOfProtectionExistingUserExperiment27May25"),
        )
        whenever(defaultBrowserAdditionalPrompts202501.featureName()).thenReturn(
            FeatureName("DefaultBrowserPromptsFeatureToggles", "defaultBrowserAdditionalPrompts202501"),
        )
        whenever(unrelatedToggle.featureName()).thenReturn(
            FeatureName("unrelatedToggleParent", "unrelatedToggle"),
        )
    }

    private fun whenVisualExperimentEnabled(enabled: Boolean) {
        whenever(visualDesignFeatureToggle.isEnabled()).thenReturn(enabled)
        whenever(duckChatPoCToggle.isEnabled()).thenReturn(enabled)
    }

    @Test
    fun `when experiment FF disabled, experiment disabled`() = runTest {
        whenever(togglesInventory.getAllActiveExperimentToggles()).thenReturn(emptyList())
        whenVisualExperimentEnabled(false)

        val testee = createTestee()
        testee.isExperimentEnabled.test {
            Assert.assertFalse(awaitItem())
        }

        testee.anyConflictingExperimentEnabled.test {
            Assert.assertFalse(awaitItem())
        }
    }

    @Test
    fun `when experiment FF enabled and no conflicting experiments, experiment enabled`() = runTest {
        whenever(togglesInventory.getAllActiveExperimentToggles()).thenReturn(emptyList())
        whenVisualExperimentEnabled(true)

        val testee = createTestee()
        val result = testee.isExperimentEnabled.value

        Assert.assertTrue(result)
        Assert.assertFalse(testee.anyConflictingExperimentEnabled.value)
    }

    @Test
    fun `when experiment FF enabled and senseOfProtectionNewUserExperimentApr25Toggle active, experiment disabled`() = runTest {
        whenever(togglesInventory.getAllActiveExperimentToggles()).thenReturn(listOf(senseOfProtectionNewUserExperimentApr25Toggle))
        whenVisualExperimentEnabled(true)

        val testee = createTestee()

        Assert.assertFalse(testee.isExperimentEnabled.value)
        Assert.assertTrue(testee.anyConflictingExperimentEnabled.value)
    }

    @Test
    fun `when experiment FF enabled and senseOfProtectionNewUserExperimentMay25Toggle active, experiment disabled`() = runTest {
        whenever(togglesInventory.getAllActiveExperimentToggles()).thenReturn(listOf(senseOfProtectionNewUserExperimentMay25Toggle))
        whenVisualExperimentEnabled(true)

        val testee = createTestee()

        Assert.assertFalse(testee.isExperimentEnabled.value)
        Assert.assertTrue(testee.anyConflictingExperimentEnabled.value)
    }

    @Test
    fun `when experiment FF enabled and senseOfProtectionExistingUserExperimentApr25 active, experiment disabled`() = runTest {
        whenever(togglesInventory.getAllActiveExperimentToggles()).thenReturn(listOf(senseOfProtectionExistingUserExperimentApr25))
        whenVisualExperimentEnabled(true)

        val testee = createTestee()

        Assert.assertFalse(testee.isExperimentEnabled.value)
        Assert.assertTrue(testee.anyConflictingExperimentEnabled.value)
    }

    @Test
    fun `when experiment FF enabled and senseOfProtectionExistingUserExperimentMay25 active, experiment disabled`() = runTest {
        whenever(togglesInventory.getAllActiveExperimentToggles()).thenReturn(listOf(senseOfProtectionExistingUserExperimentMay25))
        whenVisualExperimentEnabled(true)

        val testee = createTestee()

        Assert.assertFalse(testee.isExperimentEnabled.value)
        Assert.assertTrue(testee.anyConflictingExperimentEnabled.value)
    }

    @Test
    fun `when experiment FF enabled and senseOfProtectionNewUserExperiment27May25 active, experiment disabled`() = runTest {
        whenever(togglesInventory.getAllActiveExperimentToggles()).thenReturn(listOf(senseOfProtectionNewUserExperiment27May25))
        whenVisualExperimentEnabled(true)

        val testee = createTestee()

        Assert.assertFalse(testee.isExperimentEnabled.value)
        Assert.assertTrue(testee.anyConflictingExperimentEnabled.value)
    }

    @Test
    fun `when experiment FF enabled and senseOfProtectionExistingUserExperiment27May25 active, experiment disabled`() = runTest {
        whenever(togglesInventory.getAllActiveExperimentToggles()).thenReturn(listOf(senseOfProtectionExistingUserExperiment27May25))
        whenVisualExperimentEnabled(true)

        val testee = createTestee()

        Assert.assertFalse(testee.isExperimentEnabled.value)
        Assert.assertTrue(testee.anyConflictingExperimentEnabled.value)
    }

    @Test
    fun `when experiment FF enabled and defaultBrowserAdditionalPrompts202501 active, experiment disabled`() = runTest {
        whenever(togglesInventory.getAllActiveExperimentToggles()).thenReturn(listOf(defaultBrowserAdditionalPrompts202501))
        whenVisualExperimentEnabled(true)

        val testee = createTestee()

        Assert.assertFalse(testee.isExperimentEnabled.value)
        Assert.assertTrue(testee.anyConflictingExperimentEnabled.value)
    }

    @Test
    fun `when visual designs FF is enabled in new config, visual design enabled`() = runTest {
        whenever(togglesInventory.getAllActiveExperimentToggles()).thenReturn(listOf(unrelatedToggle))
        whenVisualExperimentEnabled(false)

        val testee = createTestee()

        Assert.assertFalse(testee.isExperimentEnabled.value)
        Assert.assertFalse(testee.anyConflictingExperimentEnabled.value)

        whenVisualExperimentEnabled(true)
        testee.onPrivacyConfigDownloaded()

        Assert.assertTrue(testee.isExperimentEnabled.value)
        Assert.assertFalse(testee.anyConflictingExperimentEnabled.value)
    }

    @Test
    fun `when conflicting experiment is activated, experiment disabled`() = runTest {
        whenever(togglesInventory.getAllActiveExperimentToggles()).thenReturn(listOf(unrelatedToggle))
        whenVisualExperimentEnabled(true)

        val testee = createTestee()
        Assert.assertTrue(testee.isExperimentEnabled.value)
        Assert.assertFalse(testee.anyConflictingExperimentEnabled.value)

        whenever(togglesInventory.getAllActiveExperimentToggles()).thenReturn(listOf(unrelatedToggle, defaultBrowserAdditionalPrompts202501))
        testee.onPrivacyConfigDownloaded()

        Assert.assertFalse(testee.isExperimentEnabled.value)
        Assert.assertTrue(testee.anyConflictingExperimentEnabled.value)
    }

    @Test
    fun `when Duck AI PoC FF enabled and experiment enabled, Duck AI PoC enabled`() = runTest {
        whenever(togglesInventory.getAllActiveExperimentToggles()).thenReturn(emptyList())
        whenever(visualDesignFeatureToggle.isEnabled()).thenReturn(true)
        whenever(duckChatPoCToggle.isEnabled()).thenReturn(true)

        val testee = createTestee()

        Assert.assertTrue(testee.isDuckAIPoCEnabled.value)
    }

    @Test
    fun `when Duck AI PoC FF enabled and experiment disabled, Duck AI PoC disabled`() = runTest {
        whenever(togglesInventory.getAllActiveExperimentToggles()).thenReturn(emptyList())
        whenever(visualDesignFeatureToggle.isEnabled()).thenReturn(false)
        whenever(duckChatPoCToggle.isEnabled()).thenReturn(true)

        val testee = createTestee()

        Assert.assertFalse(testee.isDuckAIPoCEnabled.value)
    }

    @Test
    fun `when Duck AI PoC FF disabled but experiment enabled, Duck AI PoC disabled`() = runTest {
        whenever(togglesInventory.getAllActiveExperimentToggles()).thenReturn(emptyList())
        whenever(visualDesignFeatureToggle.isEnabled()).thenReturn(true)
        whenever(duckChatPoCToggle.isEnabled()).thenReturn(false)

        val testee = createTestee()

        Assert.assertFalse(testee.isDuckAIPoCEnabled.value)
    }

    private fun createTestee(): VisualDesignExperimentDataStoreImpl {
        return VisualDesignExperimentDataStoreImpl(
            appCoroutineScope = coroutineRule.testScope,
            experimentalUIThemingFeature = experimentalUIThemingFeature,
            featureTogglesInventory = togglesInventory,
        )
    }
}
