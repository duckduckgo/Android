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

package com.duckduckgo.app.browser.defaultbrowsing.prompts

import android.content.Context
import android.content.Intent
import androidx.lifecycle.LifecycleOwner
import com.duckduckgo.app.browser.defaultbrowsing.DefaultBrowserDetector
import com.duckduckgo.app.browser.defaultbrowsing.DefaultBrowserSystemSettings
import com.duckduckgo.app.browser.defaultbrowsing.prompts.DefaultBrowserPromptsExperiment.Command
import com.duckduckgo.app.browser.defaultbrowsing.prompts.DefaultBrowserPromptsExperiment.SetAsDefaultActionTrigger.DIALOG
import com.duckduckgo.app.browser.defaultbrowsing.prompts.DefaultBrowserPromptsExperiment.SetAsDefaultActionTrigger.MENU
import com.duckduckgo.app.browser.defaultbrowsing.prompts.DefaultBrowserPromptsExperimentImpl.Companion.FALLBACK_TO_DEFAULT_APPS_SCREEN_THRESHOLD_MILLIS
import com.duckduckgo.app.browser.defaultbrowsing.prompts.DefaultBrowserPromptsExperimentImpl.Companion.PIXEL_PARAM_KEY_STAGE
import com.duckduckgo.app.browser.defaultbrowsing.prompts.DefaultBrowserPromptsExperimentImpl.Companion.PIXEL_PARAM_KEY_VARIANT
import com.duckduckgo.app.browser.defaultbrowsing.prompts.DefaultBrowserPromptsExperimentImpl.FeatureSettings
import com.duckduckgo.app.browser.defaultbrowsing.prompts.DefaultBrowserPromptsFeatureToggles.AdditionalPromptsCohortName
import com.duckduckgo.app.browser.defaultbrowsing.prompts.store.DefaultBrowserPromptsDataStore
import com.duckduckgo.app.browser.defaultbrowsing.prompts.store.DefaultBrowserPromptsDataStore.ExperimentStage
import com.duckduckgo.app.global.DefaultRoleBrowserDialog
import com.duckduckgo.app.onboarding.store.AppStage
import com.duckduckgo.app.onboarding.store.UserStageStore
import com.duckduckgo.app.pixels.AppPixelName.SET_AS_DEFAULT_IN_MENU_CLICK
import com.duckduckgo.app.pixels.AppPixelName.SET_AS_DEFAULT_PROMPT_CLICK
import com.duckduckgo.app.pixels.AppPixelName.SET_AS_DEFAULT_PROMPT_DISMISSED
import com.duckduckgo.app.pixels.AppPixelName.SET_AS_DEFAULT_PROMPT_IMPRESSION
import com.duckduckgo.app.statistics.pixels.Pixel
import com.duckduckgo.app.usage.app.AppDaysUsedRepository
import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.common.utils.plugins.PluginPoint
import com.duckduckgo.feature.toggles.api.MetricsPixel
import com.duckduckgo.feature.toggles.api.PixelDefinition
import com.duckduckgo.feature.toggles.api.Toggle
import com.duckduckgo.feature.toggles.api.Toggle.State.Cohort
import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.Moshi
import java.time.Instant
import java.util.Date
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.Mock
import org.mockito.Mockito.mock
import org.mockito.MockitoAnnotations
import org.mockito.kotlin.any
import org.mockito.kotlin.never
import org.mockito.kotlin.spy
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

@OptIn(ExperimentalCoroutinesApi::class)
class DefaultBrowserPromptsExperimentImplTest {

    @get:Rule
    var coroutinesTestRule = CoroutineTestRule()

    @Mock private lateinit var appContextMock: Context

    @Mock private lateinit var lifecycleOwnerMock: LifecycleOwner

    @Mock private lateinit var featureTogglesMock: DefaultBrowserPromptsFeatureToggles

    @Mock private lateinit var additionalPromptsToggleMock: Toggle

    private val additionalPromptsFeatureSettingsFake = "fake feature settings JSON"

    @Mock private lateinit var defaultRoleBrowserDialogMock: DefaultRoleBrowserDialog

    @Mock private lateinit var defaultBrowserDetectorMock: DefaultBrowserDetector

    @Mock private lateinit var appDaysUsedRepositoryMock: AppDaysUsedRepository

    @Mock private lateinit var userStageStoreMock: UserStageStore

    @Mock private lateinit var experimentStageEvaluatorPluginPointMock: PluginPoint<DefaultBrowserPromptsExperimentStageEvaluator>

    @Mock private lateinit var moshiMock: Moshi

    @Mock private lateinit var featureSettingsJsonAdapterMock: JsonAdapter<FeatureSettings>

    @Mock private lateinit var systemDefaultBrowserDialogIntentMock: Intent

    @Mock private lateinit var metricsMock: DefaultBrowserPromptsExperimentMetrics

    @Mock private lateinit var pixelMock: Pixel

    @Mock private lateinit var stageImpressionForStage1MetricMock: MetricsPixel
    private val stageImpressionForStage1Pixels = listOf(
        PixelDefinition("stageImpressionForStage1Pixels", mapOf("stageImpressionForStage1PixelsParam" to "set")),
    )

    @Mock private lateinit var stageImpressionForStage2MetricMock: MetricsPixel
    private val stageImpressionForStage2Pixels = listOf(
        PixelDefinition("stageImpressionForStage2Pixels", mapOf("stageImpressionForStage2PixelsParam" to "set")),
    )

    @Mock private lateinit var defaultSetForStage1Metric: MetricsPixel
    private val defaultSetForStage1MetricPixels = listOf(
        PixelDefinition("defaultSetForStage1MetricPixels", mapOf("defaultSetForStage1MetricPixelsParam" to "set")),
    )

    @Mock private lateinit var defaultSetForStage2Metric: MetricsPixel
    private val defaultSetForStage2MetricPixels = listOf(
        PixelDefinition("defaultSetForStage2MetricPixels", mapOf("defaultSetForStage2MetricPixelsParam" to "set")),
    )

    private lateinit var dataStoreMock: DefaultBrowserPromptsDataStore

    private val fakeEnrollmentDateETString = "2025-01-16T00:00-05:00[America/New_York]"
    private val fakeEnrollmentDate = Date.from(Instant.ofEpochMilli(1737003600000))

    private lateinit var fakeUserAppStageFlow: MutableSharedFlow<AppStage>

    @Before
    fun setUp() {
        MockitoAnnotations.openMocks(this)
        dataStoreMock = createDataStoreFake()

        whenever(featureTogglesMock.defaultBrowserAdditionalPrompts202501()).thenReturn(additionalPromptsToggleMock)
        whenever(defaultRoleBrowserDialogMock.createIntent(appContextMock)).thenReturn(systemDefaultBrowserDialogIntentMock)
        whenever(moshiMock.adapter<FeatureSettings>(any())).thenReturn(featureSettingsJsonAdapterMock)
        fakeUserAppStageFlow = MutableSharedFlow()
        whenever(userStageStoreMock.userAppStageFlow()).thenReturn(fakeUserAppStageFlow)
        runBlocking {
            mockMetrics()
        }
    }

    @Test
    fun `when initialized, then don't highlight popup menu`() = runTest {
        val testee = createTestee()
        assertFalse(testee.highlightPopupMenu.first())
    }

    @Test
    fun `when initialized, then don't show popup menu item`() = runTest {
        val testee = createTestee()
        assertFalse(testee.showSetAsDefaultPopupMenuItem.first())
    }

    @Test
    fun `when popup menu opened, then remove the highlight`() = runTest {
        val dataStoreMock = createDataStoreFake(
            initialHighlightPopupMenuIcon = true,
        )
        val testee = createTestee(
            defaultBrowserPromptsDataStore = dataStoreMock,
        )
        val expectedUpdates = listOf(
            false, // initial impl value
            true, // initial data store value
            false, // update
        )
        val actualUpdates = mutableListOf<Boolean>()
        coroutinesTestRule.testScope.launch {
            testee.highlightPopupMenu.toList(actualUpdates)
        }
        assertEquals(2, actualUpdates.size) // initial values expected immediately

        testee.onPopupMenuLaunched()

        assertEquals(expectedUpdates, actualUpdates)
    }

    @Test
    fun `when popup menu item clicked, then launch browser selection system dialog`() = runTest {
        val testee = createTestee()
        val expectedUpdates = listOf<Command>(
            Command.OpenSystemDefaultBrowserDialog(systemDefaultBrowserDialogIntentMock, trigger = MENU),
        )
        val actualUpdates = mutableListOf<Command>()
        coroutinesTestRule.testScope.launch {
            testee.commands.toList(actualUpdates)
        }
        assertTrue(actualUpdates.isEmpty())

        testee.onSetAsDefaultPopupMenuItemSelected()

        assertEquals(expectedUpdates, actualUpdates)
    }

    /**
     * fixme to verify that the correct intent is passed,
     *  we need to refactor [DefaultBrowserSystemSettings] to not use a companion object function,
     *  or use a different test lib to mock the companion object function
     */
    @Test
    fun `when popup menu item clicked and dialog fails, then launch default apps screen as fallback`() = runTest {
        val testee = createTestee()
        whenever(defaultRoleBrowserDialogMock.createIntent(appContextMock)).thenReturn(null)
        val actualUpdates = mutableListOf<Command>()
        coroutinesTestRule.testScope.launch {
            testee.commands.toList(actualUpdates)
        }
        assertTrue(actualUpdates.isEmpty())

        testee.onSetAsDefaultPopupMenuItemSelected()

        assertEquals(1, actualUpdates.size)
        assertTrue(actualUpdates[0] is Command.OpenSystemDefaultAppsActivity)
    }

    @Test
    fun `when message dialog confirmation clicked, then launch browser selection system dialog`() = runTest {
        val testee = createTestee()
        val expectedUpdates = listOf<Command>(
            Command.OpenSystemDefaultBrowserDialog(systemDefaultBrowserDialogIntentMock, trigger = DIALOG),
        )
        val actualUpdates = mutableListOf<Command>()
        coroutinesTestRule.testScope.launch {
            testee.commands.toList(actualUpdates)
        }
        assertTrue(actualUpdates.isEmpty())

        testee.onMessageDialogConfirmationButtonClicked()

        assertEquals(expectedUpdates, actualUpdates)
    }

    /**
     * fixme to verify that the correct intent is passed,
     *  we need to refactor [DefaultBrowserSystemSettings] to not use a companion object function,
     *  or use a different test lib to mock the companion object function
     */
    @Test
    fun `when message dialog confirmation clicked and dialog fails, then launch default apps screen as fallback`() = runTest {
        val testee = createTestee()
        whenever(defaultRoleBrowserDialogMock.createIntent(appContextMock)).thenReturn(null)
        val actualUpdates = mutableListOf<Command>()
        coroutinesTestRule.testScope.launch {
            testee.commands.toList(actualUpdates)
        }
        assertTrue(actualUpdates.isEmpty())

        testee.onMessageDialogConfirmationButtonClicked()

        assertEquals(1, actualUpdates.size)
        assertTrue(actualUpdates[0] is Command.OpenSystemDefaultAppsActivity)
    }

    /**
     * Details in this [Asana task](https://app.asana.com/0/0/1208996977455495/f).
     *
     * fixme to verify that the correct intent is passed,
     *  we need to refactor [DefaultBrowserSystemSettings] to not use a companion object function,
     *  or use a different test lib to mock the companion object function
     */
    @Test
    fun `when system default browser dialog canceled quickly, then open default apps screen instead`() = runTest {
        val testee = createTestee()
        val actualUpdates = mutableListOf<Command>()
        coroutinesTestRule.testScope.launch {
            testee.commands.toList(actualUpdates)
        }
        assertTrue(actualUpdates.isEmpty())

        testee.onSystemDefaultBrowserDialogShown()
        advanceTimeBy(FALLBACK_TO_DEFAULT_APPS_SCREEN_THRESHOLD_MILLIS - 1) // canceled before threshold
        testee.onSystemDefaultBrowserDialogCanceled(trigger = DIALOG)
        testee.onSystemDefaultBrowserDialogCanceled(trigger = DIALOG) // verifies that repeated cancellation won't keep opening new screens

        assertEquals(1, actualUpdates.size)
        assertTrue(actualUpdates[0] is Command.OpenSystemDefaultAppsActivity)
    }

    @Test
    fun `when system default browser dialog is not canceled quickly, then do nothing`() = runTest {
        val testee = createTestee()
        val actualUpdates = mutableListOf<Command>()
        coroutinesTestRule.testScope.launch {
            testee.commands.toList(actualUpdates)
        }
        assertTrue(actualUpdates.isEmpty())

        testee.onSystemDefaultBrowserDialogShown()
        advanceTimeBy(FALLBACK_TO_DEFAULT_APPS_SCREEN_THRESHOLD_MILLIS + 1) // canceled after threshold
        testee.onSystemDefaultBrowserDialogCanceled(trigger = DIALOG)

        assertTrue(actualUpdates.isEmpty())
    }

    @Test
    fun `evaluate - if user new, not enrolled, browser not set as default, then don't enroll`() = runTest {
        val testee = createTestee()
        whenever(userStageStoreMock.getUserAppStage()).thenReturn(AppStage.NEW)
        whenever(defaultBrowserDetectorMock.isDefaultBrowser()).thenReturn(false)
        whenever(additionalPromptsToggleMock.getCohort()).thenReturn(null)

        testee.onResume(lifecycleOwnerMock)

        verify(dataStoreMock, never()).storeExperimentStage(any())
        assertEquals(ExperimentStage.NOT_ENROLLED, dataStoreMock.experimentStage.first())
    }

    @Test
    fun `evaluate - if user onboarding, not enrolled, browser not set as default, then don't enroll`() = runTest {
        val testee = createTestee()
        whenever(userStageStoreMock.getUserAppStage()).thenReturn(AppStage.DAX_ONBOARDING)
        whenever(defaultBrowserDetectorMock.isDefaultBrowser()).thenReturn(false)
        whenever(additionalPromptsToggleMock.getCohort()).thenReturn(null)

        testee.onResume(lifecycleOwnerMock)

        verify(dataStoreMock, never()).storeExperimentStage(any())
        assertEquals(ExperimentStage.NOT_ENROLLED, dataStoreMock.experimentStage.first())
    }

    @Test
    fun `evaluate - if user established, not enrolled, browser already set as default, then don't enroll`() = runTest {
        val testee = createTestee()
        whenever(userStageStoreMock.getUserAppStage()).thenReturn(AppStage.ESTABLISHED)
        whenever(defaultBrowserDetectorMock.isDefaultBrowser()).thenReturn(true)
        whenever(additionalPromptsToggleMock.getCohort()).thenReturn(null)

        testee.onResume(lifecycleOwnerMock)

        verify(dataStoreMock, never()).storeExperimentStage(any())
        assertEquals(ExperimentStage.NOT_ENROLLED, dataStoreMock.experimentStage.first())
    }

    @Test
    fun `evaluate - if user established, not enrolled, browser not set as default, but no cohort assigned, then don't enroll`() = runTest {
        val testee = createTestee()
        whenever(userStageStoreMock.getUserAppStage()).thenReturn(AppStage.ESTABLISHED)
        whenever(defaultBrowserDetectorMock.isDefaultBrowser()).thenReturn(false)
        whenever(additionalPromptsToggleMock.getCohort()).thenReturn(null)
        whenever(additionalPromptsToggleMock.isEnabled(any())).thenReturn(false)

        testee.onResume(lifecycleOwnerMock)

        AdditionalPromptsCohortName.entries.forEach {
            verify(additionalPromptsToggleMock).isEnabled(it)
        }
        verify(dataStoreMock, never()).storeExperimentStage(any())
        assertEquals(ExperimentStage.NOT_ENROLLED, dataStoreMock.experimentStage.first())
    }

    @Test
    fun `evaluate - if user established, not enrolled, browser not set as default, and cohort assigned, then enroll`() = runTest {
        val testee = createTestee()
        whenever(userStageStoreMock.getUserAppStage()).thenReturn(AppStage.ESTABLISHED)
        whenever(defaultBrowserDetectorMock.isDefaultBrowser()).thenReturn(false)
        mockActiveCohort(cohortName = AdditionalPromptsCohortName.VARIANT_2)
        mockFeatureSettings(
            activeDaysUntilStage1 = 1,
            activeDaysUntilStage2 = 3,
            activeDaysUntilStop = 5,
        )
        val evaluatorMock = mockStageEvaluator(
            forNewStage = ExperimentStage.ENROLLED,
            forCohortName = AdditionalPromptsCohortName.VARIANT_2,
            returnsAction = DefaultBrowserPromptsExperimentStageAction(
                showMessageDialog = false,
                showSetAsDefaultPopupMenuItem = false,
                highlightPopupMenu = false,
            ),
        )
        whenever(experimentStageEvaluatorPluginPointMock.getPlugins()).thenReturn(setOf(evaluatorMock))

        testee.onResume(lifecycleOwnerMock)

        verify(dataStoreMock).storeExperimentStage(ExperimentStage.ENROLLED)
        verify(evaluatorMock).evaluate(ExperimentStage.ENROLLED)
    }

    @Test
    fun `evaluate - if user stage changes, not enrolled, browser not set as default, and cohort assigned, then enroll`() = runTest {
        val testee = createTestee()
        whenever(userStageStoreMock.getUserAppStage()).thenReturn(AppStage.DAX_ONBOARDING)
        whenever(defaultBrowserDetectorMock.isDefaultBrowser()).thenReturn(false)
        mockActiveCohort(cohortName = AdditionalPromptsCohortName.VARIANT_2)
        mockFeatureSettings(
            activeDaysUntilStage1 = 1,
            activeDaysUntilStage2 = 3,
            activeDaysUntilStop = 5,
        )
        val evaluatorMock = mockStageEvaluator(
            forNewStage = ExperimentStage.ENROLLED,
            forCohortName = AdditionalPromptsCohortName.VARIANT_2,
            returnsAction = DefaultBrowserPromptsExperimentStageAction(
                showMessageDialog = false,
                showSetAsDefaultPopupMenuItem = false,
                highlightPopupMenu = false,
            ),
        )
        whenever(experimentStageEvaluatorPluginPointMock.getPlugins()).thenReturn(setOf(evaluatorMock))

        testee.onResume(lifecycleOwnerMock)

        // verify not enrolled because user is onboarding
        verify(dataStoreMock, never()).storeExperimentStage(any())
        assertEquals(ExperimentStage.NOT_ENROLLED, dataStoreMock.experimentStage.first())

        whenever(userStageStoreMock.getUserAppStage()).thenReturn(AppStage.ESTABLISHED)
        fakeUserAppStageFlow.emit(AppStage.ESTABLISHED)

        verify(dataStoreMock).storeExperimentStage(ExperimentStage.ENROLLED)
        verify(evaluatorMock).evaluate(ExperimentStage.ENROLLED)
    }

    @Test
    fun `evaluate - if enrolled, browser not set as default, and not enough active days until stage 1, then do nothing`() = runTest {
        val dataStoreMock = createDataStoreFake(
            initialExperimentStage = ExperimentStage.ENROLLED,
        )
        val testee = createTestee(
            defaultBrowserPromptsDataStore = dataStoreMock,
        )
        whenever(userStageStoreMock.getUserAppStage()).thenReturn(AppStage.ESTABLISHED)
        whenever(defaultBrowserDetectorMock.isDefaultBrowser()).thenReturn(false)
        mockActiveCohort(cohortName = AdditionalPromptsCohortName.VARIANT_2)
        mockFeatureSettings(
            activeDaysUntilStage1 = 1,
            activeDaysUntilStage2 = 3,
            activeDaysUntilStop = 5,
        )
        whenever(appDaysUsedRepositoryMock.getNumberOfDaysAppUsedSinceDate(fakeEnrollmentDate)).thenReturn(0)
        val evaluatorMock = mockStageEvaluator(
            forNewStage = ExperimentStage.ENROLLED,
            forCohortName = AdditionalPromptsCohortName.VARIANT_2,
            returnsAction = mock(),
        )
        whenever(experimentStageEvaluatorPluginPointMock.getPlugins()).thenReturn(setOf(evaluatorMock))

        testee.onResume(lifecycleOwnerMock)

        verify(dataStoreMock, never()).storeExperimentStage(any())
        verify(evaluatorMock, never()).evaluate(any())
        verify(pixelMock, never()).fire(any<Pixel.PixelName>(), any(), any(), any())
        verify(pixelMock, never()).fire(any<String>(), any(), any(), any())
    }

    @Test
    fun `evaluate - if enrolled, browser not set as default, and enough active days until stage 1, then move to stage 1`() = runTest {
        val dataStoreMock = createDataStoreFake(
            initialExperimentStage = ExperimentStage.ENROLLED,
        )
        val testee = createTestee(
            defaultBrowserPromptsDataStore = dataStoreMock,
        )
        whenever(userStageStoreMock.getUserAppStage()).thenReturn(AppStage.ESTABLISHED)
        whenever(defaultBrowserDetectorMock.isDefaultBrowser()).thenReturn(false)
        mockActiveCohort(cohortName = AdditionalPromptsCohortName.VARIANT_2)
        mockFeatureSettings(
            activeDaysUntilStage1 = 1,
            activeDaysUntilStage2 = 3,
            activeDaysUntilStop = 5,
        )
        whenever(appDaysUsedRepositoryMock.getNumberOfDaysAppUsedSinceDate(fakeEnrollmentDate)).thenReturn(1)
        val evaluatorMock = mockStageEvaluator(
            forNewStage = ExperimentStage.STAGE_1,
            forCohortName = AdditionalPromptsCohortName.VARIANT_2,
            returnsAction = DefaultBrowserPromptsExperimentStageAction(
                showMessageDialog = true,
                showSetAsDefaultPopupMenuItem = false,
                highlightPopupMenu = false,
            ),
        )
        whenever(experimentStageEvaluatorPluginPointMock.getPlugins()).thenReturn(setOf(evaluatorMock))

        testee.onResume(lifecycleOwnerMock)

        verify(dataStoreMock).storeExperimentStage(ExperimentStage.STAGE_1)
        verify(evaluatorMock).evaluate(ExperimentStage.STAGE_1)
        stageImpressionForStage1Pixels.forEach {
            verify(pixelMock).fire(it.pixelName, it.params)
        }
    }

    @Test
    fun `evaluate - if enrolled, browser not set as default, and not enough active days until stage 2, then do nothing`() = runTest {
        val dataStoreMock = createDataStoreFake(
            initialExperimentStage = ExperimentStage.STAGE_1,
        )
        val testee = createTestee(
            defaultBrowserPromptsDataStore = dataStoreMock,
        )
        whenever(userStageStoreMock.getUserAppStage()).thenReturn(AppStage.ESTABLISHED)
        whenever(defaultBrowserDetectorMock.isDefaultBrowser()).thenReturn(false)
        mockActiveCohort(cohortName = AdditionalPromptsCohortName.VARIANT_2)
        mockFeatureSettings(
            activeDaysUntilStage1 = 1,
            activeDaysUntilStage2 = 3,
            activeDaysUntilStop = 5,
        )
        whenever(appDaysUsedRepositoryMock.getNumberOfDaysAppUsedSinceDate(fakeEnrollmentDate)).thenReturn(2)
        val evaluatorMock = mockStageEvaluator(
            forNewStage = ExperimentStage.STAGE_1,
            forCohortName = AdditionalPromptsCohortName.VARIANT_2,
            returnsAction = mock(),
        )
        whenever(experimentStageEvaluatorPluginPointMock.getPlugins()).thenReturn(setOf(evaluatorMock))

        testee.onResume(lifecycleOwnerMock)

        verify(dataStoreMock, never()).storeExperimentStage(any())
        verify(evaluatorMock, never()).evaluate(any())
        verify(pixelMock, never()).fire(any<Pixel.PixelName>(), any(), any(), any())
        verify(pixelMock, never()).fire(any<String>(), any(), any(), any())
    }

    @Test
    fun `evaluate - if enrolled, browser not set as default, and enough active days until stage 2, then move to stage 2`() = runTest {
        val dataStoreMock = createDataStoreFake(
            initialExperimentStage = ExperimentStage.STAGE_1,
        )
        val testee = createTestee(
            defaultBrowserPromptsDataStore = dataStoreMock,
        )
        whenever(userStageStoreMock.getUserAppStage()).thenReturn(AppStage.ESTABLISHED)
        whenever(defaultBrowserDetectorMock.isDefaultBrowser()).thenReturn(false)
        mockActiveCohort(cohortName = AdditionalPromptsCohortName.VARIANT_2)
        mockFeatureSettings(
            activeDaysUntilStage1 = 1,
            activeDaysUntilStage2 = 3,
            activeDaysUntilStop = 5,
        )
        whenever(appDaysUsedRepositoryMock.getNumberOfDaysAppUsedSinceDate(fakeEnrollmentDate)).thenReturn(3)
        val evaluatorMock = mockStageEvaluator(
            forNewStage = ExperimentStage.STAGE_2,
            forCohortName = AdditionalPromptsCohortName.VARIANT_2,
            returnsAction = DefaultBrowserPromptsExperimentStageAction(
                showMessageDialog = true,
                showSetAsDefaultPopupMenuItem = false,
                highlightPopupMenu = false,
            ),
        )
        whenever(experimentStageEvaluatorPluginPointMock.getPlugins()).thenReturn(setOf(evaluatorMock))

        testee.onResume(lifecycleOwnerMock)

        verify(dataStoreMock).storeExperimentStage(ExperimentStage.STAGE_2)
        verify(evaluatorMock).evaluate(ExperimentStage.STAGE_2)
        stageImpressionForStage2Pixels.forEach {
            verify(pixelMock).fire(it.pixelName, it.params)
        }
    }

    @Test
    fun `evaluate - if enrolled, browser not set as default, and not enough active days until stop, then do nothing`() = runTest {
        val dataStoreMock = createDataStoreFake(
            initialExperimentStage = ExperimentStage.STAGE_2,
        )
        val testee = createTestee(
            defaultBrowserPromptsDataStore = dataStoreMock,
        )
        whenever(userStageStoreMock.getUserAppStage()).thenReturn(AppStage.ESTABLISHED)
        whenever(defaultBrowserDetectorMock.isDefaultBrowser()).thenReturn(false)
        mockActiveCohort(cohortName = AdditionalPromptsCohortName.VARIANT_2)
        mockFeatureSettings(
            activeDaysUntilStage1 = 1,
            activeDaysUntilStage2 = 3,
            activeDaysUntilStop = 5,
        )
        whenever(appDaysUsedRepositoryMock.getNumberOfDaysAppUsedSinceDate(fakeEnrollmentDate)).thenReturn(4)
        val evaluatorMock = mockStageEvaluator(
            forNewStage = ExperimentStage.STAGE_2,
            forCohortName = AdditionalPromptsCohortName.VARIANT_2,
            returnsAction = mock(),
        )
        whenever(experimentStageEvaluatorPluginPointMock.getPlugins()).thenReturn(setOf(evaluatorMock))

        testee.onResume(lifecycleOwnerMock)

        verify(dataStoreMock, never()).storeExperimentStage(any())
        verify(evaluatorMock, never()).evaluate(any())
        verify(pixelMock, never()).fire(any<Pixel.PixelName>(), any(), any(), any())
        verify(pixelMock, never()).fire(any<String>(), any(), any(), any())
    }

    @Test
    fun `evaluate - if enrolled, browser not set as default, and enough active days until stop, then move to stopped`() = runTest {
        val dataStoreMock = createDataStoreFake(
            initialExperimentStage = ExperimentStage.STAGE_2,
        )
        val testee = createTestee(
            defaultBrowserPromptsDataStore = dataStoreMock,
        )
        whenever(userStageStoreMock.getUserAppStage()).thenReturn(AppStage.ESTABLISHED)
        whenever(defaultBrowserDetectorMock.isDefaultBrowser()).thenReturn(false)
        mockActiveCohort(cohortName = AdditionalPromptsCohortName.VARIANT_2)
        mockFeatureSettings(
            activeDaysUntilStage1 = 1,
            activeDaysUntilStage2 = 3,
            activeDaysUntilStop = 5,
        )
        whenever(appDaysUsedRepositoryMock.getNumberOfDaysAppUsedSinceDate(fakeEnrollmentDate)).thenReturn(5)
        val evaluatorMock = mockStageEvaluator(
            forNewStage = ExperimentStage.STOPPED,
            forCohortName = AdditionalPromptsCohortName.VARIANT_2,
            returnsAction = DefaultBrowserPromptsExperimentStageAction(
                showMessageDialog = true,
                showSetAsDefaultPopupMenuItem = false,
                highlightPopupMenu = false,
            ),
        )
        whenever(experimentStageEvaluatorPluginPointMock.getPlugins()).thenReturn(setOf(evaluatorMock))

        testee.onResume(lifecycleOwnerMock)

        verify(dataStoreMock).storeExperimentStage(ExperimentStage.STOPPED)
        verify(evaluatorMock).evaluate(ExperimentStage.STOPPED)
    }

    @Test
    fun `evaluate - if stage 1 and browser set as default, then convert`() = runTest {
        val dataStoreMock = createDataStoreFake(
            initialExperimentStage = ExperimentStage.STAGE_1,
        )
        val testee = createTestee(
            defaultBrowserPromptsDataStore = dataStoreMock,
        )
        whenever(userStageStoreMock.getUserAppStage()).thenReturn(AppStage.ESTABLISHED)
        whenever(defaultBrowserDetectorMock.isDefaultBrowser()).thenReturn(true)
        mockActiveCohort(cohortName = AdditionalPromptsCohortName.VARIANT_2)
        val evaluatorMock = mockStageEvaluator(
            forNewStage = ExperimentStage.CONVERTED,
            forCohortName = AdditionalPromptsCohortName.VARIANT_2,
            returnsAction = DefaultBrowserPromptsExperimentStageAction(
                showMessageDialog = false,
                showSetAsDefaultPopupMenuItem = false,
                highlightPopupMenu = false,
            ),
        )
        whenever(experimentStageEvaluatorPluginPointMock.getPlugins()).thenReturn(setOf(evaluatorMock))

        testee.onResume(lifecycleOwnerMock)

        verify(dataStoreMock).storeExperimentStage(ExperimentStage.CONVERTED)
        verify(evaluatorMock).evaluate(ExperimentStage.CONVERTED)
        defaultSetForStage1MetricPixels.forEach {
            verify(pixelMock).fire(it.pixelName, it.params)
        }
    }

    @Test
    fun `evaluate - if stage 2 and browser set as default, then convert`() = runTest {
        val dataStoreMock = createDataStoreFake(
            initialExperimentStage = ExperimentStage.STAGE_2,
        )
        val testee = createTestee(
            defaultBrowserPromptsDataStore = dataStoreMock,
        )
        whenever(userStageStoreMock.getUserAppStage()).thenReturn(AppStage.ESTABLISHED)
        whenever(defaultBrowserDetectorMock.isDefaultBrowser()).thenReturn(true)
        mockActiveCohort(cohortName = AdditionalPromptsCohortName.VARIANT_2)
        val evaluatorMock = mockStageEvaluator(
            forNewStage = ExperimentStage.CONVERTED,
            forCohortName = AdditionalPromptsCohortName.VARIANT_2,
            returnsAction = DefaultBrowserPromptsExperimentStageAction(
                showMessageDialog = false,
                showSetAsDefaultPopupMenuItem = false,
                highlightPopupMenu = false,
            ),
        )
        whenever(experimentStageEvaluatorPluginPointMock.getPlugins()).thenReturn(setOf(evaluatorMock))

        testee.onResume(lifecycleOwnerMock)

        verify(dataStoreMock).storeExperimentStage(ExperimentStage.CONVERTED)
        verify(evaluatorMock).evaluate(ExperimentStage.CONVERTED)
        defaultSetForStage2MetricPixels.forEach {
            verify(pixelMock).fire(it.pixelName, it.params)
        }
    }

    @Test
    fun `evaluate - if stopped and browser set as default, then don't convert`() = runTest {
        val dataStoreMock = createDataStoreFake(
            initialExperimentStage = ExperimentStage.STOPPED,
        )
        val testee = createTestee(
            defaultBrowserPromptsDataStore = dataStoreMock,
        )
        whenever(userStageStoreMock.getUserAppStage()).thenReturn(AppStage.ESTABLISHED)
        whenever(defaultBrowserDetectorMock.isDefaultBrowser()).thenReturn(true)
        mockActiveCohort(cohortName = AdditionalPromptsCohortName.VARIANT_2)
        val evaluatorMock = mockStageEvaluator(
            forNewStage = ExperimentStage.CONVERTED,
            forCohortName = AdditionalPromptsCohortName.VARIANT_2,
            returnsAction = DefaultBrowserPromptsExperimentStageAction(
                showMessageDialog = false,
                showSetAsDefaultPopupMenuItem = false,
                highlightPopupMenu = false,
            ),
        )
        whenever(experimentStageEvaluatorPluginPointMock.getPlugins()).thenReturn(setOf(evaluatorMock))

        testee.onResume(lifecycleOwnerMock)

        verify(dataStoreMock, never()).storeExperimentStage(any())
        verify(evaluatorMock, never()).evaluate(any())
    }

    @Test
    fun `evaluate - if converted and browser set as default, then don't convert again`() = runTest {
        val dataStoreMock = createDataStoreFake(
            initialExperimentStage = ExperimentStage.CONVERTED,
        )
        val testee = createTestee(
            defaultBrowserPromptsDataStore = dataStoreMock,
        )
        whenever(userStageStoreMock.getUserAppStage()).thenReturn(AppStage.ESTABLISHED)
        whenever(defaultBrowserDetectorMock.isDefaultBrowser()).thenReturn(true)
        mockActiveCohort(cohortName = AdditionalPromptsCohortName.VARIANT_2)
        val evaluatorMock = mockStageEvaluator(
            forNewStage = ExperimentStage.CONVERTED,
            forCohortName = AdditionalPromptsCohortName.VARIANT_2,
            returnsAction = DefaultBrowserPromptsExperimentStageAction(
                showMessageDialog = false,
                showSetAsDefaultPopupMenuItem = false,
                highlightPopupMenu = false,
            ),
        )
        whenever(experimentStageEvaluatorPluginPointMock.getPlugins()).thenReturn(setOf(evaluatorMock))

        testee.onResume(lifecycleOwnerMock)

        verify(dataStoreMock, never()).storeExperimentStage(any())
        verify(evaluatorMock, never()).evaluate(any())
    }

    @Test
    fun `evaluate - if stage changes and show dialog action produced, then propagate it`() = runTest {
        val dataStoreMock = createDataStoreFake(
            initialExperimentStage = ExperimentStage.ENROLLED,
        )
        val testee = createTestee(
            defaultBrowserPromptsDataStore = dataStoreMock,
        )
        whenever(userStageStoreMock.getUserAppStage()).thenReturn(AppStage.ESTABLISHED)
        whenever(defaultBrowserDetectorMock.isDefaultBrowser()).thenReturn(false)
        mockActiveCohort(cohortName = AdditionalPromptsCohortName.VARIANT_2)
        mockFeatureSettings(
            activeDaysUntilStage1 = 1,
            activeDaysUntilStage2 = 3,
            activeDaysUntilStop = 5,
        )
        whenever(appDaysUsedRepositoryMock.getNumberOfDaysAppUsedSinceDate(fakeEnrollmentDate)).thenReturn(1)
        val evaluatorMock = mockStageEvaluator(
            forNewStage = ExperimentStage.STAGE_1,
            forCohortName = AdditionalPromptsCohortName.VARIANT_2,
            returnsAction = DefaultBrowserPromptsExperimentStageAction(
                showMessageDialog = true,
                showSetAsDefaultPopupMenuItem = false,
                highlightPopupMenu = false,
            ),
        )
        whenever(experimentStageEvaluatorPluginPointMock.getPlugins()).thenReturn(setOf(evaluatorMock))

        testee.onResume(lifecycleOwnerMock)
        val command = testee.commands.first()

        assertEquals(Command.OpenMessageDialog, command)
    }

    @Test
    fun `evaluate - if stage changes and show menu item action produced, then propagate it`() = runTest {
        val dataStoreMock = createDataStoreFake(
            initialExperimentStage = ExperimentStage.STAGE_1,
        )
        val testee = createTestee(
            defaultBrowserPromptsDataStore = dataStoreMock,
        )
        whenever(userStageStoreMock.getUserAppStage()).thenReturn(AppStage.ESTABLISHED)
        whenever(defaultBrowserDetectorMock.isDefaultBrowser()).thenReturn(false)
        mockActiveCohort(cohortName = AdditionalPromptsCohortName.VARIANT_2)
        mockFeatureSettings(
            activeDaysUntilStage1 = 1,
            activeDaysUntilStage2 = 3,
            activeDaysUntilStop = 5,
        )
        whenever(appDaysUsedRepositoryMock.getNumberOfDaysAppUsedSinceDate(fakeEnrollmentDate)).thenReturn(3)
        val evaluatorMock = mockStageEvaluator(
            forNewStage = ExperimentStage.STAGE_2,
            forCohortName = AdditionalPromptsCohortName.VARIANT_2,
            returnsAction = DefaultBrowserPromptsExperimentStageAction(
                showMessageDialog = false,
                showSetAsDefaultPopupMenuItem = true,
                highlightPopupMenu = false,
            ),
        )
        whenever(experimentStageEvaluatorPluginPointMock.getPlugins()).thenReturn(setOf(evaluatorMock))

        testee.onResume(lifecycleOwnerMock)
        val result = testee.showSetAsDefaultPopupMenuItem.first()

        assertTrue(result)
        verify(dataStoreMock).storeShowSetAsDefaultPopupMenuItemState(show = true)
    }

    @Test
    fun `evaluate - if stage changes and highlight menu action produced, then propagate it`() = runTest {
        val dataStoreMock = createDataStoreFake(
            initialExperimentStage = ExperimentStage.STAGE_1,
        )
        val testee = createTestee(
            defaultBrowserPromptsDataStore = dataStoreMock,
        )
        whenever(userStageStoreMock.getUserAppStage()).thenReturn(AppStage.ESTABLISHED)
        whenever(defaultBrowserDetectorMock.isDefaultBrowser()).thenReturn(false)
        mockActiveCohort(
            cohortName = AdditionalPromptsCohortName.VARIANT_2,
        )
        mockFeatureSettings(
            activeDaysUntilStage1 = 1,
            activeDaysUntilStage2 = 3,
            activeDaysUntilStop = 5,
        )
        whenever(appDaysUsedRepositoryMock.getNumberOfDaysAppUsedSinceDate(fakeEnrollmentDate)).thenReturn(3)
        val evaluatorMock = mockStageEvaluator(
            forNewStage = ExperimentStage.STAGE_2,
            forCohortName = AdditionalPromptsCohortName.VARIANT_2,
            returnsAction = DefaultBrowserPromptsExperimentStageAction(
                showMessageDialog = false,
                showSetAsDefaultPopupMenuItem = false,
                highlightPopupMenu = true,
            ),
        )
        whenever(experimentStageEvaluatorPluginPointMock.getPlugins()).thenReturn(setOf(evaluatorMock))

        testee.onResume(lifecycleOwnerMock)
        val result = testee.highlightPopupMenu.first()

        assertTrue(result)
        verify(dataStoreMock).storeHighlightPopupMenuState(highlight = true)
    }

    @Test
    fun `if message dialog shown, then send a pixel`() = runTest {
        val expectedParams = mapOf(
            PIXEL_PARAM_KEY_VARIANT to "variant_2",
            PIXEL_PARAM_KEY_STAGE to "stage_1",
        )
        val dataStoreMock = createDataStoreFake(
            initialExperimentStage = ExperimentStage.STAGE_1,
        )
        val testee = createTestee(
            defaultBrowserPromptsDataStore = dataStoreMock,
        )
        mockActiveCohort(
            cohortName = AdditionalPromptsCohortName.VARIANT_2,
        )

        testee.onMessageDialogShown()

        verify(pixelMock).fire(SET_AS_DEFAULT_PROMPT_IMPRESSION, expectedParams)
    }

    @Test
    fun `if message dialog canceled, then send a pixel`() = runTest {
        val expectedParams = mapOf(
            PIXEL_PARAM_KEY_VARIANT to "variant_2",
            PIXEL_PARAM_KEY_STAGE to "stage_1",
        )
        val dataStoreMock = createDataStoreFake(
            initialExperimentStage = ExperimentStage.STAGE_1,
        )
        val testee = createTestee(
            defaultBrowserPromptsDataStore = dataStoreMock,
        )
        mockActiveCohort(
            cohortName = AdditionalPromptsCohortName.VARIANT_2,
        )

        testee.onMessageDialogCanceled()

        verify(pixelMock).fire(SET_AS_DEFAULT_PROMPT_DISMISSED, expectedParams)
    }

    @Test
    fun `if message dialog not now clicked, then send a pixel`() = runTest {
        val expectedParams = mapOf(
            PIXEL_PARAM_KEY_VARIANT to "variant_2",
            PIXEL_PARAM_KEY_STAGE to "stage_1",
        )
        val dataStoreMock = createDataStoreFake(
            initialExperimentStage = ExperimentStage.STAGE_1,
        )
        val testee = createTestee(
            defaultBrowserPromptsDataStore = dataStoreMock,
        )
        mockActiveCohort(
            cohortName = AdditionalPromptsCohortName.VARIANT_2,
        )

        testee.onMessageDialogNotNowButtonClicked()

        verify(pixelMock).fire(SET_AS_DEFAULT_PROMPT_DISMISSED, expectedParams)
    }

    @Test
    fun `if message dialog confirmation clicked, then send a pixel`() = runTest {
        val expectedParams = mapOf(
            PIXEL_PARAM_KEY_VARIANT to "variant_2",
            PIXEL_PARAM_KEY_STAGE to "stage_1",
        )
        val dataStoreMock = createDataStoreFake(
            initialExperimentStage = ExperimentStage.STAGE_1,
        )
        val testee = createTestee(
            defaultBrowserPromptsDataStore = dataStoreMock,
        )
        mockActiveCohort(
            cohortName = AdditionalPromptsCohortName.VARIANT_2,
        )

        testee.onMessageDialogConfirmationButtonClicked()

        verify(pixelMock).fire(SET_AS_DEFAULT_PROMPT_CLICK, expectedParams)
    }

    @Test
    fun `if menu item clicked, then send a pixel`() = runTest {
        val expectedParams = mapOf(
            PIXEL_PARAM_KEY_VARIANT to "variant_2",
            PIXEL_PARAM_KEY_STAGE to "stage_1",
        )
        val dataStoreMock = createDataStoreFake(
            initialExperimentStage = ExperimentStage.STAGE_1,
        )
        val testee = createTestee(
            defaultBrowserPromptsDataStore = dataStoreMock,
        )
        mockActiveCohort(
            cohortName = AdditionalPromptsCohortName.VARIANT_2,
        )

        testee.onSetAsDefaultPopupMenuItemSelected()

        verify(pixelMock).fire(SET_AS_DEFAULT_IN_MENU_CLICK, expectedParams)
    }

    private fun createTestee(
        appCoroutineScope: CoroutineScope = coroutinesTestRule.testScope,
        dispatchers: DispatcherProvider = coroutinesTestRule.testDispatcherProvider,
        applicationContext: Context = appContextMock,
        defaultBrowserPromptsFeatureToggles: DefaultBrowserPromptsFeatureToggles = featureTogglesMock,
        defaultBrowserDetector: DefaultBrowserDetector = defaultBrowserDetectorMock,
        defaultRoleBrowserDialog: DefaultRoleBrowserDialog = defaultRoleBrowserDialogMock,
        appDaysUsedRepository: AppDaysUsedRepository = appDaysUsedRepositoryMock,
        userStageStore: UserStageStore = userStageStoreMock,
        defaultBrowserPromptsDataStore: DefaultBrowserPromptsDataStore = dataStoreMock,
        experimentStageEvaluatorPluginPoint: PluginPoint<DefaultBrowserPromptsExperimentStageEvaluator> = experimentStageEvaluatorPluginPointMock,
        metrics: DefaultBrowserPromptsExperimentMetrics = metricsMock,
        pixel: Pixel = pixelMock,
        moshi: Moshi = moshiMock,
    ) = DefaultBrowserPromptsExperimentImpl(
        appCoroutineScope = appCoroutineScope,
        dispatchers = dispatchers,
        applicationContext = applicationContext,
        defaultBrowserPromptsFeatureToggles = defaultBrowserPromptsFeatureToggles,
        defaultBrowserDetector = defaultBrowserDetector,
        defaultRoleBrowserDialog = defaultRoleBrowserDialog,
        appDaysUsedRepository = appDaysUsedRepository,
        userStageStore = userStageStore,
        defaultBrowserPromptsDataStore = defaultBrowserPromptsDataStore,
        experimentStageEvaluatorPluginPoint = experimentStageEvaluatorPluginPoint,
        metrics = metrics,
        pixel = pixel,
        moshi = moshi,
    )

    private fun createDataStoreFake(
        initialExperimentStage: ExperimentStage = ExperimentStage.NOT_ENROLLED,
        initialShowPopupMenuItem: Boolean = false,
        initialHighlightPopupMenuIcon: Boolean = false,
    ) = spy(
        DefaultBrowserPromptsDataStoreMock(
            initialExperimentStage,
            initialShowPopupMenuItem,
            initialHighlightPopupMenuIcon,
        ),
    )

    private fun mockActiveCohort(cohortName: AdditionalPromptsCohortName): Cohort {
        val cohort = Cohort(
            name = cohortName.name,
            weight = 1,
            enrollmentDateET = fakeEnrollmentDateETString,
        )
        whenever(additionalPromptsToggleMock.getCohort()).thenReturn(cohort)
        whenever(additionalPromptsToggleMock.isEnabled(cohortName)).thenReturn(true)

        return cohort
    }

    private fun mockFeatureSettings(
        activeDaysUntilStage1: Int,
        activeDaysUntilStage2: Int,
        activeDaysUntilStop: Int,
    ): FeatureSettings {
        val settings = FeatureSettings(
            activeDaysUntilStage1 = activeDaysUntilStage1,
            activeDaysUntilStage2 = activeDaysUntilStage2,
            activeDaysUntilStop = activeDaysUntilStop,
        )
        whenever(additionalPromptsToggleMock.getSettings()).thenReturn(additionalPromptsFeatureSettingsFake)
        whenever(featureSettingsJsonAdapterMock.fromJson(additionalPromptsFeatureSettingsFake)).thenReturn(settings)
        return settings
    }

    private suspend fun mockStageEvaluator(
        forNewStage: ExperimentStage,
        forCohortName: AdditionalPromptsCohortName,
        returnsAction: DefaultBrowserPromptsExperimentStageAction,
    ): DefaultBrowserPromptsExperimentStageEvaluator {
        val evaluatorMock: DefaultBrowserPromptsExperimentStageEvaluator = mock()
        whenever(evaluatorMock.targetCohort).thenReturn(forCohortName)
        whenever(evaluatorMock.evaluate(forNewStage)).thenReturn(returnsAction)
        return evaluatorMock
    }

    private suspend fun mockMetrics() {
        whenever(metricsMock.getStageImpressionForStage1()).thenReturn(stageImpressionForStage1MetricMock)
        whenever(stageImpressionForStage1MetricMock.getPixelDefinitions()).thenReturn(stageImpressionForStage1Pixels)

        whenever(metricsMock.getStageImpressionForStage2()).thenReturn(stageImpressionForStage2MetricMock)
        whenever(stageImpressionForStage2MetricMock.getPixelDefinitions()).thenReturn(stageImpressionForStage2Pixels)

        whenever(metricsMock.getDefaultSetForStage1()).thenReturn(defaultSetForStage1Metric)
        whenever(defaultSetForStage1Metric.getPixelDefinitions()).thenReturn(defaultSetForStage1MetricPixels)

        whenever(metricsMock.getDefaultSetForStage2()).thenReturn(defaultSetForStage2Metric)
        whenever(defaultSetForStage2Metric.getPixelDefinitions()).thenReturn(defaultSetForStage2MetricPixels)
    }
}

class DefaultBrowserPromptsDataStoreMock(
    initialExperimentStage: ExperimentStage,
    initialShowPopupMenuItem: Boolean,
    initialHighlightPopupMenuIcon: Boolean,
) : DefaultBrowserPromptsDataStore {

    private val _experimentStage = MutableStateFlow(initialExperimentStage)
    override val experimentStage: Flow<ExperimentStage> = _experimentStage.asStateFlow()

    private val _showPopupMenuItem = MutableStateFlow(initialShowPopupMenuItem)
    override val showSetAsDefaultPopupMenuItem: Flow<Boolean> = _showPopupMenuItem.asStateFlow()

    private val _highlightPopupMenuIcon = MutableStateFlow(initialHighlightPopupMenuIcon)
    override val highlightPopupMenu: Flow<Boolean> = _highlightPopupMenuIcon.asStateFlow()

    override suspend fun storeExperimentStage(stage: ExperimentStage) {
        _experimentStage.value = stage
    }

    override suspend fun storeShowSetAsDefaultPopupMenuItemState(show: Boolean) {
        _showPopupMenuItem.value = show
    }

    override suspend fun storeHighlightPopupMenuState(highlight: Boolean) {
        _highlightPopupMenuIcon.value = highlight
    }
}
