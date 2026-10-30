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

package com.duckduckgo.app.onboarding.orchestrator

import androidx.core.app.NotificationManagerCompat
import com.duckduckgo.app.browser.defaultbrowsing.DefaultBrowserDetector
import com.duckduckgo.appbuildconfig.api.AppBuildConfig
import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.onboarding.api.LinearOnboardingHost
import com.duckduckgo.onboarding.api.LinearOnboardingState.Completed
import com.duckduckgo.onboarding.api.LinearOnboardingState.InProgress
import com.duckduckgo.onboarding.api.LinearOnboardingState.Skipped
import com.duckduckgo.onboarding.impl.LinearOnboardingOrchestratorImpl
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.whenever

class DeviceMigrationPlanProviderTest {

    @get:Rule
    val coroutineRule = CoroutineTestRule()

    private val defaultBrowserDetector: DefaultBrowserDetector = mock()
    private val appBuildConfig: AppBuildConfig = mock()
    private val notificationManager: NotificationManagerCompat = mock()

    private val orchestrator = LinearOnboardingOrchestratorImpl()
    private lateinit var provider: DeviceMigrationPlanProvider

    @Before
    fun setup() {
        whenever(appBuildConfig.sdkInt).thenReturn(33)
        whenever(notificationManager.areNotificationsEnabled()).thenReturn(false)
        whenever(defaultBrowserDetector.isDefaultBrowser()).thenReturn(false)
        provider = DeviceMigrationPlanProvider(
            defaultBrowserDetector = defaultBrowserDetector,
            appBuildConfig = appBuildConfig,
            notificationManager = notificationManager,
            dispatchers = coroutineRule.testDispatcherProvider,
        )
    }

    @Test
    fun `when built then the plan has the migration id and its steps in order, all hosted in the onboarding activity`() {
        val plan = provider.buildPlan(onCompleted = {})

        assertEquals(DeviceMigrationPlanProvider.MIGRATION_PLAN_ID, plan.id)
        assertEquals(
            listOf(
                NewUserOnboardingStepIds.MIGRATION_INTRO_ANIMATION,
                NewUserOnboardingStepIds.MIGRATION_NOTIFICATION_PERMISSION,
                NewUserOnboardingStepIds.MIGRATION_DEFAULT_BROWSER,
                NewUserOnboardingStepIds.MIGRATION_ALL_SET,
            ),
            plan.steps.map { it.id },
        )
        assertTrue(plan.steps.all { it.host == LinearOnboardingHost.OnboardingActivity })
    }

    @Test
    fun `when built then no step carries an onboarding pixel`() {
        val plan = provider.buildPlan(onCompleted = {})

        assertTrue(plan.steps.all { (it as NewUserOnboardingActivityStep).pixelName == null })
    }

    @Test
    fun `when built then building has no side effects`() {
        provider.buildPlan(onCompleted = {})

        verifyNoInteractions(defaultBrowserDetector, appBuildConfig, notificationManager)
    }

    @Test
    fun `when walked through then each step resolves its dialog and the last one completes the plan`() = runTest {
        var completedCalls = 0
        orchestrator.startPlan(provider.buildPlan(onCompleted = { completedCalls++ }))

        assertStep(NewUserOnboardingStepIds.MIGRATION_INTRO_ANIMATION)
        assertEquals(NewUserOnboardingActivityDialog.IntroAnimation(false), currentDialog())
        orchestrator.onEvent(NewUserOnboardingEvent.IntroAnimationFinished)

        assertStep(NewUserOnboardingStepIds.MIGRATION_NOTIFICATION_PERMISSION)
        assertEquals(NewUserOnboardingActivityDialog.NotificationPermission, currentDialog())
        orchestrator.onEvent(NewUserOnboardingEvent.NotificationPermissionFinished(granted = true))

        assertStep(NewUserOnboardingStepIds.MIGRATION_DEFAULT_BROWSER)
        assertEquals(NewUserOnboardingActivityDialog.MigrationDefaultBrowser, currentDialog())
        orchestrator.onEvent(NewUserOnboardingEvent.DefaultBrowserPromptFinished(isDefaultBrowser = true))

        assertStep(NewUserOnboardingStepIds.MIGRATION_ALL_SET)
        assertEquals(NewUserOnboardingActivityDialog.MigrationAllSet, currentDialog())
        orchestrator.onEvent(NewUserOnboardingEvent.ContinueClicked)

        assertEquals(Completed(rootPlanId = DeviceMigrationPlanProvider.MIGRATION_PLAN_ID), orchestrator.state.value)
        assertEquals(1, completedCalls)
    }

    @Test
    fun `when notifications are already granted then the notification step is skipped`() = runTest {
        whenever(notificationManager.areNotificationsEnabled()).thenReturn(true)

        startAndFinishIntro()

        assertStep(NewUserOnboardingStepIds.MIGRATION_DEFAULT_BROWSER)
    }

    @Test
    fun `when below Android 13 then the notification step is skipped`() = runTest {
        whenever(appBuildConfig.sdkInt).thenReturn(32)

        startAndFinishIntro()

        assertStep(NewUserOnboardingStepIds.MIGRATION_DEFAULT_BROWSER)
    }

    @Test
    fun `when DuckDuckGo is already the default browser then the default browser step is skipped`() = runTest {
        whenever(defaultBrowserDetector.isDefaultBrowser()).thenReturn(true)

        startAndReachDefaultBrowserStepPosition()

        assertStep(NewUserOnboardingStepIds.MIGRATION_ALL_SET)
    }

    @Test
    fun whenNotDefaultEvenIfDialogCountExhaustedThenDefaultStepShown() = runTest {
        // The provider has no access to the role-dialog shown counter, so only the detector decides.
        whenever(defaultBrowserDetector.isDefaultBrowser()).thenReturn(false)

        startAndReachDefaultBrowserStepPosition()

        assertStep(NewUserOnboardingStepIds.MIGRATION_DEFAULT_BROWSER)
    }

    @Test
    fun `when the default browser prompt finishes without becoming default then the step advances`() = runTest {
        startAndReachDefaultBrowserStepPosition()

        orchestrator.onEvent(NewUserOnboardingEvent.DefaultBrowserPromptFinished(isDefaultBrowser = false))

        assertStep(NewUserOnboardingStepIds.MIGRATION_ALL_SET)
    }

    @Test
    fun `when the default browser step is skipped then the step advances`() = runTest {
        startAndReachDefaultBrowserStepPosition()

        orchestrator.onEvent(NewUserOnboardingEvent.MigrationDefaultBrowserSkipped)

        assertStep(NewUserOnboardingStepIds.MIGRATION_ALL_SET)
    }

    @Test
    fun `when setting the default browser is requested then the step waits for the prompt's outcome`() = runTest {
        startAndReachDefaultBrowserStepPosition()

        orchestrator.onEvent(NewUserOnboardingEvent.MigrationSetDefaultBrowserRequested)

        assertStep(NewUserOnboardingStepIds.MIGRATION_DEFAULT_BROWSER)
    }

    @Test
    fun `when the notification prompt finishes with any outcome then the step advances`() = runTest {
        listOf(true, false, null).forEach { granted ->
            val orchestrator = LinearOnboardingOrchestratorImpl()
            orchestrator.startPlan(provider.buildPlan(onCompleted = {}))
            orchestrator.onEvent(NewUserOnboardingEvent.IntroAnimationFinished)

            orchestrator.onEvent(NewUserOnboardingEvent.NotificationPermissionFinished(granted = granted))

            assertEquals(
                NewUserOnboardingStepIds.MIGRATION_DEFAULT_BROWSER,
                (orchestrator.state.value as InProgress).currentStep.id,
            )
        }
    }

    @Test
    fun `when an unrelated event arrives then the step stays`() = runTest {
        orchestrator.startPlan(provider.buildPlan(onCompleted = {}))

        orchestrator.onEvent(NewUserOnboardingEvent.ContinueClicked)

        assertStep(NewUserOnboardingStepIds.MIGRATION_INTRO_ANIMATION)
    }

    @Test
    fun `when the dev skip is used then the plan is skipped and the callback still runs`() = runTest {
        var finishedCalls = 0
        orchestrator.startPlan(provider.buildPlan(onCompleted = { finishedCalls++ }))
        orchestrator.onEvent(NewUserOnboardingEvent.IntroAnimationFinished)

        orchestrator.onEvent(NewUserOnboardingEvent.SkipNewUserOnboardingDevOptionClicked)

        assertEquals(Skipped(rootPlanId = DeviceMigrationPlanProvider.MIGRATION_PLAN_ID), orchestrator.state.value)
        assertEquals(1, finishedCalls)
    }

    private suspend fun startAndFinishIntro() {
        orchestrator.startPlan(provider.buildPlan(onCompleted = {}))
        orchestrator.onEvent(NewUserOnboardingEvent.IntroAnimationFinished)
    }

    private suspend fun startAndReachDefaultBrowserStepPosition() {
        whenever(notificationManager.areNotificationsEnabled()).thenReturn(true)
        startAndFinishIntro()
    }

    private fun assertStep(id: String) {
        val state = orchestrator.state.value
        assertTrue("expected InProgress on '$id' but was $state", state is InProgress)
        assertEquals(id, (state as InProgress).currentStep.id)
    }

    private suspend fun currentDialog(): NewUserOnboardingActivityDialog =
        ((orchestrator.state.value as InProgress).currentStep as NewUserOnboardingActivityStep).resolveDialog()
}
