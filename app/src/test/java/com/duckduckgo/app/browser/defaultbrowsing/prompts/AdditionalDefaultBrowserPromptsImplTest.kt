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
import com.duckduckgo.app.browser.defaultbrowsing.prompts.AdditionalDefaultBrowserPrompts.Command
import com.duckduckgo.app.browser.defaultbrowsing.prompts.AdditionalDefaultBrowserPrompts.SetAsDefaultActionTrigger.MENU
import com.duckduckgo.app.browser.defaultbrowsing.prompts.AdditionalDefaultBrowserPrompts.SetAsDefaultActionTrigger.PROMPT
import com.duckduckgo.app.browser.defaultbrowsing.prompts.AdditionalDefaultBrowserPromptsImpl.Companion.FALLBACK_TO_DEFAULT_APPS_SCREEN_THRESHOLD_MILLIS
import com.duckduckgo.app.browser.defaultbrowsing.prompts.AdditionalDefaultBrowserPromptsImpl.Companion.PIXEL_PARAM_KEY_STAGE
import com.duckduckgo.app.browser.defaultbrowsing.prompts.AdditionalDefaultBrowserPromptsImpl.FeatureSettingsConfigModel
import com.duckduckgo.app.browser.defaultbrowsing.prompts.store.DefaultBrowserPromptsAppUsageRepository
import com.duckduckgo.app.browser.defaultbrowsing.prompts.store.DefaultBrowserPromptsDataStore
import com.duckduckgo.app.browser.defaultbrowsing.prompts.store.DefaultBrowserPromptsDataStore.Stage
import com.duckduckgo.app.browser.defaultbrowsing.prompts.store.DefaultBrowserPromptsDataStore.UserType
import com.duckduckgo.app.global.DefaultRoleBrowserDialog
import com.duckduckgo.app.onboarding.store.AppStage
import com.duckduckgo.app.onboarding.store.UserStageStore
import com.duckduckgo.app.pixels.AppPixelName.SET_AS_DEFAULT_IN_MENU_CLICK
import com.duckduckgo.app.pixels.AppPixelName.SET_AS_DEFAULT_PROMPT_CLICK
import com.duckduckgo.app.pixels.AppPixelName.SET_AS_DEFAULT_PROMPT_DISMISSED
import com.duckduckgo.app.pixels.AppPixelName.SET_AS_DEFAULT_PROMPT_DO_NOT_ASK_AGAIN_CLICK
import com.duckduckgo.app.pixels.AppPixelName.SET_AS_DEFAULT_PROMPT_IMPRESSION
import com.duckduckgo.app.statistics.pixels.Pixel
import com.duckduckgo.browser.api.UserBrowserProperties
import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.feature.toggles.api.Toggle
import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.Moshi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.Mock
import org.mockito.Mockito.atMostOnce
import org.mockito.Mockito.mock
import org.mockito.MockitoAnnotations
import org.mockito.kotlin.any
import org.mockito.kotlin.never
import org.mockito.kotlin.spy
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

@OptIn(ExperimentalCoroutinesApi::class)
class AdditionalDefaultBrowserPromptsImplTest {

    @get:Rule
    var coroutinesTestRule = CoroutineTestRule()

    @Mock private lateinit var appContextMock: Context

    @Mock private lateinit var lifecycleOwnerMock: LifecycleOwner

    @Mock private lateinit var featureTogglesMock: DefaultBrowserPromptsFeatureToggles

    @Mock private lateinit var additionalPromptsToggleMock: Toggle

    @Mock private lateinit var selfToggleMock: Toggle

    private val additionalPromptsFeatureSettingsFake = "fake feature settings JSON"

    @Mock private lateinit var defaultRoleBrowserDialogMock: DefaultRoleBrowserDialog

    @Mock private lateinit var defaultBrowserDetectorMock: DefaultBrowserDetector

    @Mock private lateinit var defaultBrowserPromptsAppUsageRepositoryMock: DefaultBrowserPromptsAppUsageRepository

    @Mock private lateinit var userStageStoreMock: UserStageStore

    @Mock private lateinit var stageEvaluatorMock: DefaultBrowserPromptsFlowStageEvaluator

    @Mock private lateinit var userBrowserPropertiesMock: UserBrowserProperties

    @Mock private lateinit var moshiMock: Moshi

    @Mock private lateinit var featureSettingsJsonAdapterMock: JsonAdapter<FeatureSettingsConfigModel>

    @Mock private lateinit var systemDefaultBrowserDialogIntentMock: Intent

    @Mock private lateinit var pixelMock: Pixel

    private lateinit var dataStoreMock: DefaultBrowserPromptsDataStore

    private lateinit var fakeUserAppStageFlow: MutableSharedFlow<AppStage>

    @Before
    fun setUp() {
        MockitoAnnotations.openMocks(this)
        dataStoreMock = createDataStoreFake()
        whenever(featureTogglesMock.self()).thenReturn(selfToggleMock)
        whenever(featureTogglesMock.self().isEnabled()).thenReturn(true)
        whenever(featureTogglesMock.defaultBrowserPrompts25()).thenReturn(additionalPromptsToggleMock)
        whenever(featureTogglesMock.defaultBrowserPrompts25().isEnabled()).thenReturn(true)
        whenever(defaultRoleBrowserDialogMock.createIntent(appContextMock)).thenReturn(systemDefaultBrowserDialogIntentMock)
        whenever(moshiMock.adapter<FeatureSettingsConfigModel>(any())).thenReturn(featureSettingsJsonAdapterMock)
        fakeUserAppStageFlow = MutableSharedFlow()
        whenever(userStageStoreMock.userAppStageFlow()).thenReturn(fakeUserAppStageFlow)
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
            Command.OpenSystemDefaultBrowserDialog(systemDefaultBrowserDialogIntentMock, trigger = PROMPT),
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
        testee.onSystemDefaultBrowserDialogCanceled(trigger = PROMPT)
        testee.onSystemDefaultBrowserDialogCanceled(trigger = PROMPT) // verifies that repeated cancellation won't keep opening new screens

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
        testee.onSystemDefaultBrowserDialogCanceled(trigger = PROMPT)

        assertTrue(actualUpdates.isEmpty())
    }

    @Test
    fun `evaluate - if feature not enabled then don't enroll`() = runTest {
        val testee = createTestee()
        whenever(featureTogglesMock.self()).thenReturn(selfToggleMock)
        whenever(featureTogglesMock.self().isEnabled()).thenReturn(false)
        whenever(featureTogglesMock.defaultBrowserPrompts25()).thenReturn(additionalPromptsToggleMock)
        whenever(featureTogglesMock.defaultBrowserPrompts25().isEnabled()).thenReturn(false)
        whenever(userStageStoreMock.getUserAppStage()).thenReturn(AppStage.DAX_ONBOARDING)
        whenever(defaultBrowserDetectorMock.isDefaultBrowser()).thenReturn(false)

        testee.onResume(lifecycleOwnerMock)

        verify(dataStoreMock, never()).storeExperimentStage(any())
        assertEquals(Stage.NOT_STARTED, dataStoreMock.stage.first())
    }

    @Test
    fun `evaluate - if user new, not enrolled, browser not set as default, then don't enroll`() = runTest {
        val testee = createTestee()
        whenever(userStageStoreMock.getUserAppStage()).thenReturn(AppStage.NEW)
        whenever(defaultBrowserDetectorMock.isDefaultBrowser()).thenReturn(false)

        testee.onResume(lifecycleOwnerMock)

        verify(dataStoreMock, never()).storeExperimentStage(any())
        assertEquals(Stage.NOT_STARTED, dataStoreMock.stage.first())
    }

    @Test
    fun `evaluate - if user onboarding, browser not set as default, then don't enroll`() = runTest {
        val testee = createTestee()
        whenever(userStageStoreMock.getUserAppStage()).thenReturn(AppStage.DAX_ONBOARDING)
        whenever(defaultBrowserDetectorMock.isDefaultBrowser()).thenReturn(false)

        testee.onResume(lifecycleOwnerMock)

        verify(dataStoreMock, never()).storeExperimentStage(any())
        assertEquals(Stage.NOT_STARTED, dataStoreMock.stage.first())
    }

    @Test
    fun `evaluate - if user established, browser already set as default, then don't enroll and marked stopped`() = runTest {
        val testee = createTestee()
        whenever(userStageStoreMock.getUserAppStage()).thenReturn(AppStage.ESTABLISHED)
        whenever(defaultBrowserDetectorMock.isDefaultBrowser()).thenReturn(true)

        testee.onResume(lifecycleOwnerMock)

        verify(dataStoreMock, atMostOnce()).storeExperimentStage(Stage.STOPPED)
        assertEquals(Stage.STOPPED, dataStoreMock.stage.first())
    }

    @Test
    fun `evaluate - if user established, browser not set as default, then set user type existing and enroll`() = runTest {
        val testee = createTestee()
        whenever(userStageStoreMock.getUserAppStage()).thenReturn(AppStage.ESTABLISHED)
        whenever(defaultBrowserDetectorMock.isDefaultBrowser()).thenReturn(false)
        mockFeatureSettings()
        whenever(userBrowserPropertiesMock.daysSinceInstalled()).thenReturn(5)
        whenever(defaultBrowserPromptsAppUsageRepositoryMock.getActiveDaysUsedSinceEnrollment()).thenReturn(Result.success(0))
        val action = DefaultBrowserPromptsFlowStageAction(
            showMessageDialog = false,
            showSetAsDefaultPopupMenuItem = false,
            highlightPopupMenu = false,
            showMessage = false,
        )
        whenever(stageEvaluatorMock.evaluate(Stage.STARTED)).thenReturn(action)

        testee.onResume(lifecycleOwnerMock)

        verify(dataStoreMock).storeUserType(UserType.EXISTING)
        verify(dataStoreMock).storeExperimentStage(Stage.STARTED)
        verify(stageEvaluatorMock).evaluate(Stage.STARTED)
    }

    @Test
    fun `evaluate - if enrolled, browser not set as default, and not enough active days until stage 1, then do nothing`() = runTest {
        val dataStoreMock = createDataStoreFake(
            initialStage = Stage.STARTED,
        )
        val testee = createTestee(
            defaultBrowserPromptsDataStore = dataStoreMock,
        )
        whenever(userStageStoreMock.getUserAppStage()).thenReturn(AppStage.ESTABLISHED)
        whenever(defaultBrowserDetectorMock.isDefaultBrowser()).thenReturn(false)
        mockFeatureSettings()
        whenever(defaultBrowserPromptsAppUsageRepositoryMock.getActiveDaysUsedSinceEnrollment()).thenReturn(Result.success(0))

        testee.onResume(lifecycleOwnerMock)

        verify(dataStoreMock, never()).storeExperimentStage(any())
        verify(stageEvaluatorMock, never()).evaluate(any())
        verify(pixelMock, never()).fire(any<Pixel.PixelName>(), any(), any(), any())
        verify(pixelMock, never()).fire(any<String>(), any(), any(), any())
    }

    @Test
    fun `evaluate - if enrolled, browser not set as default, and enough active days until stage 1, then move to stage 1`() = runTest {
        val dataStoreMock = createDataStoreFake(
            initialStage = Stage.STARTED,
        )
        val testee = createTestee(
            defaultBrowserPromptsDataStore = dataStoreMock,
        )
        whenever(userStageStoreMock.getUserAppStage()).thenReturn(AppStage.ESTABLISHED)
        whenever(defaultBrowserDetectorMock.isDefaultBrowser()).thenReturn(false)
        mockFeatureSettings()
        whenever(defaultBrowserPromptsAppUsageRepositoryMock.getActiveDaysUsedSinceEnrollment()).thenReturn(Result.success(1))
        val action = DefaultBrowserPromptsFlowStageAction(
            showMessageDialog = true,
            showSetAsDefaultPopupMenuItem = false,
            highlightPopupMenu = false,
            showMessage = false,
        )
        whenever(stageEvaluatorMock.evaluate(Stage.STAGE_1)).thenReturn(action)

        testee.onResume(lifecycleOwnerMock)

        verify(dataStoreMock).storeExperimentStage(Stage.STAGE_1)
        verify(stageEvaluatorMock).evaluate(Stage.STAGE_1)
    }

    @Test
    fun `evaluate - if enrolled and new user, browser not set as default, and not enough active days until stage 2, then do nothing`() = runTest {
        val dataStoreMock = createDataStoreFake(
            initialStage = Stage.STAGE_1,
        )
        val testee = createTestee(
            defaultBrowserPromptsDataStore = dataStoreMock,
        )
        whenever(userStageStoreMock.getUserAppStage()).thenReturn(AppStage.ESTABLISHED)
        whenever(defaultBrowserDetectorMock.isDefaultBrowser()).thenReturn(false)
        mockFeatureSettings(
            newUserActiveDaysUntilStage1 = 1,
            newUserActiveDaysUntilStage2 = 4,
            newUserActiveDaysUntilStage3 = 6,
        )
        whenever(userBrowserPropertiesMock.daysSinceInstalled()).thenReturn(2)
        whenever(defaultBrowserPromptsAppUsageRepositoryMock.getActiveDaysUsedSinceEnrollment()).thenReturn(Result.success(2))

        testee.onResume(lifecycleOwnerMock)

        verify(dataStoreMock, never()).storeExperimentStage(any())
        verify(stageEvaluatorMock, never()).evaluate(any())
        verify(pixelMock, never()).fire(any<Pixel.PixelName>(), any(), any(), any())
        verify(pixelMock, never()).fire(any<String>(), any(), any(), any())
    }

    @Test
    fun `evaluate - if enrolled and existing user, browser not set as default, and not enough active days until stage 2, then do nothing`() =
        runTest {
            val dataStoreMock = createDataStoreFake(
                initialStage = Stage.STAGE_1,
            )
            val testee = createTestee(
                defaultBrowserPromptsDataStore = dataStoreMock,
            )
            whenever(userStageStoreMock.getUserAppStage()).thenReturn(AppStage.ESTABLISHED)
            whenever(defaultBrowserDetectorMock.isDefaultBrowser()).thenReturn(false)
            mockFeatureSettings(
                existingUserActiveDaysUntilStage1 = 1,
                existingUserActiveDaysUntilStage3 = 6,
            )
            whenever(userBrowserPropertiesMock.daysSinceInstalled()).thenReturn(5)
            whenever(defaultBrowserPromptsAppUsageRepositoryMock.getActiveDaysUsedSinceEnrollment()).thenReturn(Result.success(2))

            testee.onResume(lifecycleOwnerMock)

            verify(dataStoreMock, never()).storeExperimentStage(any())
            verify(stageEvaluatorMock, never()).evaluate(any())
            verify(pixelMock, never()).fire(any<Pixel.PixelName>(), any(), any(), any())
            verify(pixelMock, never()).fire(any<String>(), any(), any(), any())
        }

    @Test
    fun `evaluate - if enrolled and new user, browser not set as default, and enough active days until stage 2, then move to stage 2`() = runTest {
        val dataStoreMock = createDataStoreFake(
            initialStage = Stage.STAGE_1,
        )
        val testee = createTestee(
            defaultBrowserPromptsDataStore = dataStoreMock,
        )
        whenever(userStageStoreMock.getUserAppStage()).thenReturn(AppStage.ESTABLISHED)
        whenever(defaultBrowserDetectorMock.isDefaultBrowser()).thenReturn(false)
        mockFeatureSettings()
        whenever(defaultBrowserPromptsAppUsageRepositoryMock.getActiveDaysUsedSinceEnrollment()).thenReturn(Result.success(3))
        val action = DefaultBrowserPromptsFlowStageAction(
            showMessageDialog = true,
            showSetAsDefaultPopupMenuItem = false,
            highlightPopupMenu = false,
            showMessage = false,
        )
        whenever(userBrowserPropertiesMock.daysSinceInstalled()).thenReturn(1)
        whenever(stageEvaluatorMock.evaluate(Stage.STAGE_2)).thenReturn(action)

        testee.onResume(lifecycleOwnerMock)

        verify(dataStoreMock).storeExperimentStage(Stage.STAGE_2)
        verify(stageEvaluatorMock).evaluate(Stage.STAGE_2)
    }

    @Test
    fun `evaluate - if enrolled and existing user, browser not set as default, and enough active days until stage 3, then move to stage 3`() =
        runTest {
            val dataStoreMock = createDataStoreFake(
                initialStage = Stage.STAGE_1,
            )
            val testee = createTestee(
                defaultBrowserPromptsDataStore = dataStoreMock,
            )
            whenever(userStageStoreMock.getUserAppStage()).thenReturn(AppStage.ESTABLISHED)
            whenever(defaultBrowserDetectorMock.isDefaultBrowser()).thenReturn(false)
            mockFeatureSettings()
            whenever(defaultBrowserPromptsAppUsageRepositoryMock.getActiveDaysUsedSinceEnrollment()).thenReturn(Result.success(4))
            val action = DefaultBrowserPromptsFlowStageAction(
                showMessageDialog = true,
                showSetAsDefaultPopupMenuItem = false,
                highlightPopupMenu = false,
                showMessage = false,
            )
            whenever(userBrowserPropertiesMock.daysSinceInstalled()).thenReturn(5)
            whenever(stageEvaluatorMock.evaluate(Stage.STAGE_3)).thenReturn(action)

            testee.onResume(lifecycleOwnerMock)

            verify(dataStoreMock).storeExperimentStage(Stage.STAGE_3)
            verify(stageEvaluatorMock).evaluate(Stage.STAGE_3)
        }

    @Test
    fun `evaluate - if stage 1 and browser set as default, then set stage to STOPPED and evaluate to clean`() = runTest {
        val dataStoreMock = createDataStoreFake(
            initialStage = Stage.STAGE_1,
        )
        val testee = createTestee(
            defaultBrowserPromptsDataStore = dataStoreMock,
        )
        whenever(userStageStoreMock.getUserAppStage()).thenReturn(AppStage.ESTABLISHED)
        whenever(defaultBrowserDetectorMock.isDefaultBrowser()).thenReturn(true)

        testee.onResume(lifecycleOwnerMock)

        verify(dataStoreMock).storeExperimentStage(Stage.STOPPED)
        verify(stageEvaluatorMock).evaluate(Stage.STOPPED)
    }

    @Test
    fun `evaluate - if stage 2 and browser set as default,  then set stage to STOPPED and evaluate to clean`() = runTest {
        val dataStoreMock = createDataStoreFake(
            initialStage = Stage.STAGE_2,
        )
        val testee = createTestee(
            defaultBrowserPromptsDataStore = dataStoreMock,
        )
        whenever(userStageStoreMock.getUserAppStage()).thenReturn(AppStage.ESTABLISHED)
        whenever(defaultBrowserDetectorMock.isDefaultBrowser()).thenReturn(true)

        testee.onResume(lifecycleOwnerMock)

        verify(dataStoreMock).storeExperimentStage(Stage.STOPPED)
        verify(stageEvaluatorMock).evaluate(Stage.STOPPED)
    }

    @Test
    fun `evaluate - if stopped and browser set as default, then don't do anything`() = runTest {
        val dataStoreMock = createDataStoreFake(
            initialStage = Stage.STOPPED,
        )
        val testee = createTestee(
            defaultBrowserPromptsDataStore = dataStoreMock,
        )
        whenever(userStageStoreMock.getUserAppStage()).thenReturn(AppStage.ESTABLISHED)
        whenever(defaultBrowserDetectorMock.isDefaultBrowser()).thenReturn(true)

        testee.onResume(lifecycleOwnerMock)

        verify(dataStoreMock, never()).storeExperimentStage(any())
        verify(stageEvaluatorMock, never()).evaluate(any())
    }

    @Test
    fun `evaluate - if stage changes and show dialog action produced, then propagate it`() = runTest {
        val dataStoreMock = createDataStoreFake(
            initialStage = Stage.STARTED,
        )
        val testee = createTestee(
            defaultBrowserPromptsDataStore = dataStoreMock,
        )
        whenever(userStageStoreMock.getUserAppStage()).thenReturn(AppStage.ESTABLISHED)
        whenever(defaultBrowserDetectorMock.isDefaultBrowser()).thenReturn(false)
        mockFeatureSettings()
        val action = DefaultBrowserPromptsFlowStageAction(
            showMessageDialog = true,
            showSetAsDefaultPopupMenuItem = false,
            highlightPopupMenu = false,
            showMessage = false,
        )
        whenever(defaultBrowserPromptsAppUsageRepositoryMock.getActiveDaysUsedSinceEnrollment()).thenReturn(Result.success(1))
        whenever(stageEvaluatorMock.evaluate(Stage.STAGE_1)).thenReturn(action)

        testee.onResume(lifecycleOwnerMock)
        val command = testee.commands.first()

        assertEquals(Command.OpenMessageDialog, command)
    }

    @Test
    fun `evaluate new user - if stage changes and show menu item action produced, then propagate it`() = runTest {
        val dataStoreMock = createDataStoreFake(
            initialStage = Stage.STAGE_1,
        )
        val testee = createTestee(
            defaultBrowserPromptsDataStore = dataStoreMock,
        )
        whenever(userStageStoreMock.getUserAppStage()).thenReturn(AppStage.ESTABLISHED)
        whenever(defaultBrowserDetectorMock.isDefaultBrowser()).thenReturn(false)
        mockFeatureSettings()
        val action = DefaultBrowserPromptsFlowStageAction(
            showMessageDialog = false,
            showSetAsDefaultPopupMenuItem = true,
            highlightPopupMenu = false,
            showMessage = false,
        )
        whenever(defaultBrowserPromptsAppUsageRepositoryMock.getActiveDaysUsedSinceEnrollment()).thenReturn(Result.success(3))
        whenever(userBrowserPropertiesMock.daysSinceInstalled()).thenReturn(1)
        whenever(stageEvaluatorMock.evaluate(Stage.STAGE_2)).thenReturn(action)

        testee.onResume(lifecycleOwnerMock)
        val result = testee.showSetAsDefaultPopupMenuItem.first()

        assertTrue(result)
        verify(dataStoreMock).storeShowSetAsDefaultPopupMenuItemState(show = true)
    }

    @Test
    fun `evaluate new user - if stage changes and highlight menu action produced, then propagate it`() = runTest {
        val dataStoreMock = createDataStoreFake(
            initialStage = Stage.STAGE_1,
        )
        val testee = createTestee(
            defaultBrowserPromptsDataStore = dataStoreMock,
        )
        whenever(userStageStoreMock.getUserAppStage()).thenReturn(AppStage.ESTABLISHED)
        whenever(defaultBrowserDetectorMock.isDefaultBrowser()).thenReturn(false)
        mockFeatureSettings()
        val action = DefaultBrowserPromptsFlowStageAction(
            showMessageDialog = false,
            showSetAsDefaultPopupMenuItem = false,
            highlightPopupMenu = true,
            showMessage = false,
        )
        whenever(defaultBrowserPromptsAppUsageRepositoryMock.getActiveDaysUsedSinceEnrollment()).thenReturn(Result.success(3))
        whenever(userBrowserPropertiesMock.daysSinceInstalled()).thenReturn(1)
        whenever(stageEvaluatorMock.evaluate(Stage.STAGE_2)).thenReturn(action)

        testee.onResume(lifecycleOwnerMock)
        val result = testee.highlightPopupMenu.first()

        assertTrue(result)
        verify(dataStoreMock).storeHighlightPopupMenuState(highlight = true)
    }

    @Test
    fun `evaluate - if changing stage but evaluator for a cohort not found, then abort and clean up`() = runTest {
        val dataStoreMock = createDataStoreFake(
            initialStage = Stage.STAGE_1,
        )
        val testee = createTestee(
            defaultBrowserPromptsDataStore = dataStoreMock,
        )
        whenever(userStageStoreMock.getUserAppStage()).thenReturn(AppStage.ESTABLISHED)
        whenever(defaultBrowserDetectorMock.isDefaultBrowser()).thenReturn(false)
        mockFeatureSettings()
        whenever(defaultBrowserPromptsAppUsageRepositoryMock.getActiveDaysUsedSinceEnrollment()).thenReturn(Result.success(3))
        whenever(stageEvaluatorMock.evaluate(any())).thenReturn(mock())

        testee.onResume(lifecycleOwnerMock)

        verify(dataStoreMock).storeShowSetAsDefaultPopupMenuItemState(show = false)
        verify(dataStoreMock).storeHighlightPopupMenuState(highlight = false)
    }

    @Test
    fun `if message dialog shown, then send a pixel`() = runTest {
        val expectedParams = mapOf(
            PIXEL_PARAM_KEY_STAGE to "stage_1",
        )
        val dataStoreMock = createDataStoreFake(
            initialStage = Stage.STAGE_1,
        )
        val testee = createTestee(
            defaultBrowserPromptsDataStore = dataStoreMock,
        )

        testee.onMessageDialogShown()

        verify(pixelMock).fire(SET_AS_DEFAULT_PROMPT_IMPRESSION, expectedParams)
    }

    @Test
    fun `if message dialog canceled, then send a pixel`() = runTest {
        val expectedParams = mapOf(
            PIXEL_PARAM_KEY_STAGE to "stage_1",
        )
        val dataStoreMock = createDataStoreFake(
            initialStage = Stage.STAGE_1,
        )
        val testee = createTestee(
            defaultBrowserPromptsDataStore = dataStoreMock,
        )

        testee.onMessageDialogCanceled()

        verify(pixelMock).fire(SET_AS_DEFAULT_PROMPT_DISMISSED, expectedParams)
    }

    @Test
    fun `if message dialog do not ask again clicked, then send a pixel`() = runTest {
        val expectedParams = mapOf(
            PIXEL_PARAM_KEY_STAGE to "stage_1",
        )
        val dataStoreMock = createDataStoreFake(
            initialStage = Stage.STAGE_1,
        )
        val testee = createTestee(
            defaultBrowserPromptsDataStore = dataStoreMock,
        )

        testee.onMessageDialogDoNotAskAgainButtonClicked()

        verify(pixelMock).fire(SET_AS_DEFAULT_PROMPT_DO_NOT_ASK_AGAIN_CLICK, expectedParams)
    }

    @Test
    fun `if message dialog confirmation clicked, then send a pixel`() = runTest {
        val expectedParams = mapOf(
            PIXEL_PARAM_KEY_STAGE to "stage_1",
        )
        val dataStoreMock = createDataStoreFake(
            initialStage = Stage.STAGE_1,
        )
        val testee = createTestee(
            defaultBrowserPromptsDataStore = dataStoreMock,
        )

        testee.onMessageDialogConfirmationButtonClicked()

        verify(pixelMock).fire(SET_AS_DEFAULT_PROMPT_CLICK, expectedParams)
    }

    @Test
    fun `if menu item clicked, then send a pixel`() = runTest {
        val expectedParams = mapOf(
            PIXEL_PARAM_KEY_STAGE to "stage_1",
        )
        val dataStoreMock = createDataStoreFake(
            initialStage = Stage.STAGE_1,
        )
        val testee = createTestee(
            defaultBrowserPromptsDataStore = dataStoreMock,
        )

        testee.onSetAsDefaultPopupMenuItemSelected()

        verify(pixelMock).fire(SET_AS_DEFAULT_IN_MENU_CLICK, expectedParams)
    }

    @Test
    fun `when resumed and user established, record app usage`() = runTest {
        whenever(userStageStoreMock.getUserAppStage()).thenReturn(AppStage.ESTABLISHED)
        val testee = createTestee()

        testee.onResume(lifecycleOwnerMock)

        verify(defaultBrowserPromptsAppUsageRepositoryMock).recordAppUsedNow()
    }

    private fun createTestee(
        appCoroutineScope: CoroutineScope = coroutinesTestRule.testScope,
        dispatchers: DispatcherProvider = coroutinesTestRule.testDispatcherProvider,
        applicationContext: Context = appContextMock,
        defaultBrowserPromptsFeatureToggles: DefaultBrowserPromptsFeatureToggles = featureTogglesMock,
        defaultBrowserDetector: DefaultBrowserDetector = defaultBrowserDetectorMock,
        defaultRoleBrowserDialog: DefaultRoleBrowserDialog = defaultRoleBrowserDialogMock,
        defaultBrowserPromptsAppUsageRepository: DefaultBrowserPromptsAppUsageRepository = defaultBrowserPromptsAppUsageRepositoryMock,
        userStageStore: UserStageStore = userStageStoreMock,
        defaultBrowserPromptsDataStore: DefaultBrowserPromptsDataStore = dataStoreMock,
        experimentStageEvaluator: DefaultBrowserPromptsFlowStageEvaluator = stageEvaluatorMock,
        userBrowserProperties: UserBrowserProperties = userBrowserPropertiesMock,
        pixel: Pixel = pixelMock,
        moshi: Moshi = moshiMock,
    ) = AdditionalDefaultBrowserPromptsImpl(
        appCoroutineScope = appCoroutineScope,
        dispatchers = dispatchers,
        applicationContext = applicationContext,
        defaultBrowserPromptsFeatureToggles = defaultBrowserPromptsFeatureToggles,
        defaultBrowserDetector = defaultBrowserDetector,
        defaultRoleBrowserDialog = defaultRoleBrowserDialog,
        defaultBrowserPromptsAppUsageRepository = defaultBrowserPromptsAppUsageRepository,
        userStageStore = userStageStore,
        defaultBrowserPromptsDataStore = defaultBrowserPromptsDataStore,
        stageEvaluator = experimentStageEvaluator,
        userBrowserProperties = userBrowserProperties,
        pixel = pixel,
        moshi = moshi,
    )

    private fun createDataStoreFake(
        initialStage: Stage = Stage.NOT_STARTED,
        userType: UserType = UserType.UNKNOWN,
        initialShowPopupMenuItem: Boolean = false,
        initialHighlightPopupMenuIcon: Boolean = false,
        initialShowSetAsDefaultMessage: Boolean = false,
    ) = spy(
        DefaultBrowserPromptsDataStoreMock(
            initialStage,
            userType,
            initialShowPopupMenuItem,
            initialHighlightPopupMenuIcon,
            initialShowSetAsDefaultMessage,
        ),
    )

    private fun mockFeatureSettings(
        newUserActiveDaysUntilStage1: Int = 1,
        newUserActiveDaysUntilStage2: Int = 3,
        newUserActiveDaysUntilStage3: Int = 4,
        existingUserActiveDaysUntilStage1: Int = 1,
        existingUserActiveDaysUntilStage3: Int = 4,
    ) {
        val settings = FeatureSettingsConfigModel(
            newUserActiveDaysUntilStage1 = newUserActiveDaysUntilStage1.toString(),
            newUserActiveDaysUntilStage2 = newUserActiveDaysUntilStage2.toString(),
            newUserActiveDaysUntilStage3 = newUserActiveDaysUntilStage3.toString(),
            existingUserActiveDaysUntilStage1 = existingUserActiveDaysUntilStage1.toString(),
            existingUserActiveDaysUntilStage3 = existingUserActiveDaysUntilStage3.toString(),
        )
        whenever(additionalPromptsToggleMock.getSettings()).thenReturn(additionalPromptsFeatureSettingsFake)
        whenever(featureSettingsJsonAdapterMock.fromJson(additionalPromptsFeatureSettingsFake)).thenReturn(settings)
    }
}

class DefaultBrowserPromptsDataStoreMock(
    initialStage: Stage,
    userType: UserType,
    initialShowPopupMenuItem: Boolean,
    initialHighlightPopupMenuIcon: Boolean,
    initialShowSetAsDefaultMessage: Boolean,
) : DefaultBrowserPromptsDataStore {

    private val _experimentStage = MutableStateFlow(initialStage)
    override val stage: Flow<Stage> = _experimentStage.asStateFlow()

    private val _userType = MutableStateFlow(userType)
    override val userType: Flow<UserType> = _userType.asStateFlow()

    private val _showPopupMenuItem = MutableStateFlow(initialShowPopupMenuItem)
    override val showSetAsDefaultPopupMenuItem: Flow<Boolean> = _showPopupMenuItem.asStateFlow()

    private val _highlightPopupMenuIcon = MutableStateFlow(initialHighlightPopupMenuIcon)
    override val highlightPopupMenu: Flow<Boolean> = _highlightPopupMenuIcon.asStateFlow()

    private val _showSetAsDefaultMessage = MutableStateFlow(initialShowSetAsDefaultMessage)
    override val showSetAsDefaultMessage: Flow<Boolean> = _showSetAsDefaultMessage.asStateFlow()

    override suspend fun storeExperimentStage(stage: Stage) {
        _experimentStage.value = stage
    }

    override suspend fun storeUserType(userType: UserType) {
        _userType.value = userType
    }

    override suspend fun storeShowSetAsDefaultPopupMenuItemState(show: Boolean) {
        _showPopupMenuItem.value = show
    }

    override suspend fun storeHighlightPopupMenuState(highlight: Boolean) {
        _highlightPopupMenuIcon.value = highlight
    }

    override suspend fun storeShowSetAsDefaultMessageState(show: Boolean) {
        _showSetAsDefaultMessage.value = show
    }
}
