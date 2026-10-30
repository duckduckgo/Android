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
import com.duckduckgo.app.onboarding.store.AppStage
import com.duckduckgo.app.onboarding.store.UserStageStore
import com.duckduckgo.app.onboarding.ui.OnboardingSkipper
import com.duckduckgo.appbuildconfig.api.AppBuildConfig
import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.devicemigration.api.DeviceMigration
import com.duckduckgo.onboarding.api.LinearOnboardingEvent
import com.duckduckgo.onboarding.api.LinearOnboardingHost
import com.duckduckgo.onboarding.api.LinearOnboardingOrchestrator
import com.duckduckgo.onboarding.api.LinearOnboardingPlan
import com.duckduckgo.onboarding.api.LinearOnboardingState
import com.duckduckgo.onboarding.api.LinearOnboardingState.Completed
import com.duckduckgo.onboarding.api.LinearOnboardingState.Skipped
import com.duckduckgo.onboarding.api.LinearOnboardingStep
import com.duckduckgo.onboarding.api.LinearOnboardingTransition
import com.duckduckgo.onboarding.impl.LinearOnboardingOrchestratorImpl
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class PendingOnboardingStarterTest {

    @get:Rule
    val coroutineRule = CoroutineTestRule()

    private val defaultBrowserDetector: DefaultBrowserDetector = mock()
    private val appBuildConfig: AppBuildConfig = mock()
    private val notificationManager: NotificationManagerCompat = mock()
    private val newUserPlanProvider: NewUserOnboardingPlanProvider = mock()
    private val userStageStore: UserStageStore = mock()
    private val onboardingSkipper: OnboardingSkipper = mock()
    private val deviceMigration = FakeDeviceMigration()

    private var orchestrator: LinearOnboardingOrchestrator = LinearOnboardingOrchestratorImpl()
    private lateinit var testee: PendingOnboardingStarter

    @Before
    fun setup() {
        whenever(appBuildConfig.sdkInt).thenReturn(33)
        whenever(notificationManager.areNotificationsEnabled()).thenReturn(false)
        whenever(defaultBrowserDetector.isDefaultBrowser()).thenReturn(false)
        runBlocking {
            whenever(newUserPlanProvider.buildRootPlan(any(), any())).thenReturn(newUserPlan())
        }
        givenStage(AppStage.ESTABLISHED)
        testee = newStarter()
    }

    @Test
    fun `when an established user has the flow pending then only the migration plan runs and the stage is never touched`() = runTest {
        deviceMigration.migrated = true
        deviceMigration.pending = true

        val started = testee.startPending()

        assertEquals(DeviceMigrationPlanProvider.MIGRATION_PLAN_ID, started?.rootPlanId)
        finishMigrationPlan()
        assertEquals(Completed(rootPlanId = DeviceMigrationPlanProvider.MIGRATION_PLAN_ID), orchestrator.state.value)
        assertFalse(deviceMigration.pending)

        assertNull(testee.startPending())
        verify(newUserPlanProvider, never()).buildRootPlan(any(), any())
        verify(userStageStore, never()).stageCompleted(any())
        verify(userStageStore, never()).moveToStage(any())
        verify(onboardingSkipper, never()).markOnboardingAsCompleted()
    }

    @Test
    fun `when a new user has the flow pending then the migration plan runs first and the new user plan after it`() = runTest {
        givenStage(AppStage.NEW)
        deviceMigration.migrated = true
        deviceMigration.pending = true

        assertEquals(DeviceMigrationPlanProvider.MIGRATION_PLAN_ID, testee.startPending()?.rootPlanId)
        verify(newUserPlanProvider, never()).buildRootPlan(any(), any())
        finishMigrationPlan()

        assertEquals(NewUserOnboardingPlanProvider.ROOT_PLAN_ID, testee.startPending()?.rootPlanId)
    }

    @Test
    fun `when a user in dax onboarding has the flow pending then only the migration plan runs`() = runTest {
        givenStage(AppStage.DAX_ONBOARDING)
        deviceMigration.migrated = true
        deviceMigration.pending = true

        assertEquals(DeviceMigrationPlanProvider.MIGRATION_PLAN_ID, testee.startPending()?.rootPlanId)
        finishMigrationPlan()

        assertNull(testee.startPending())
        verify(newUserPlanProvider, never()).buildRootPlan(any(), any())
        verify(userStageStore, never()).stageCompleted(any())
        verify(userStageStore, never()).moveToStage(any())
    }

    @Test
    fun `when the previous phone never opened the app then the migration plan runs first and the new user plan after it`() = runTest {
        givenStage(AppStage.NEW)
        deviceMigration.migrated = true
        deviceMigration.pending = true
        deviceMigration.sourceNewInstall = true

        assertEquals(DeviceMigrationPlanProvider.MIGRATION_PLAN_ID, testee.startPending()?.rootPlanId)
        finishMigrationPlan()

        assertEquals(NewUserOnboardingPlanProvider.ROOT_PLAN_ID, testee.startPending()?.rootPlanId)
    }

    @Test
    fun `when the migration plan is skipped then the flow is no longer pending`() = runTest {
        deviceMigration.migrated = true
        deviceMigration.pending = true
        testee.startPending()

        orchestrator.onEvent(NewUserOnboardingEvent.SkipNewUserOnboardingDevOptionClicked)

        assertEquals(Skipped(rootPlanId = DeviceMigrationPlanProvider.MIGRATION_PLAN_ID), orchestrator.state.value)
        assertFalse(deviceMigration.pending)
        assertNull(testee.startPending())
    }

    @Test
    fun `when not migrated and a new user then the new user plan starts`() = runTest {
        givenStage(AppStage.NEW)

        assertEquals(NewUserOnboardingPlanProvider.ROOT_PLAN_ID, testee.startPending()?.rootPlanId)
    }

    @Test
    fun `when not migrated and established then nothing starts`() = runTest {
        assertNull(testee.startPending())

        assertEquals(LinearOnboardingState.NotStarted, orchestrator.state.value)
        verify(newUserPlanProvider, never()).buildRootPlan(any(), any())
    }

    @Test
    fun `when the process dies before the flow finishes then the migration plan starts again`() = runTest {
        deviceMigration.migrated = true
        deviceMigration.pending = true
        testee.startPending()
        orchestrator.onEvent(NewUserOnboardingEvent.IntroAnimationFinished)

        orchestrator = LinearOnboardingOrchestratorImpl()
        val afterProcessDeath = newStarter()

        val restarted = afterProcessDeath.startPending()
        assertEquals(DeviceMigrationPlanProvider.MIGRATION_PLAN_ID, restarted?.rootPlanId)
        assertEquals(NewUserOnboardingStepIds.MIGRATION_INTRO_ANIMATION, restarted?.currentStep?.id)
    }

    @Test
    fun `when the migration plan is already running then it is returned without restarting`() = runTest {
        deviceMigration.migrated = true
        deviceMigration.pending = true
        testee.startPending()
        orchestrator.onEvent(NewUserOnboardingEvent.IntroAnimationFinished)
        val running = orchestrator.state.value

        assertSame(running, testee.startPending())
    }

    @Test
    fun `when the new user plan is already running then it is returned without building another`() = runTest {
        givenStage(AppStage.NEW)
        val running = testee.startPending()

        assertSame(running, testee.startPending())
        verify(newUserPlanProvider, times(1)).buildRootPlan(any(), any())
    }

    @Test
    fun whenForeignRunInProgressAndNewUserThenNullAndNothingStarted() = runTest {
        givenStage(AppStage.NEW)
        val foreign = givenForeignRunInProgress()

        assertNull(testee.startPending())

        assertSame(foreign, orchestrator.state.value)
        verify(newUserPlanProvider, never()).buildRootPlan(any(), any())
    }

    @Test
    fun whenForeignRunInProgressAndFlowPendingThenNullAndStillPending() = runTest {
        deviceMigration.migrated = true
        deviceMigration.pending = true
        val foreign = givenForeignRunInProgress()

        assertNull(testee.startPending())

        assertSame(foreign, orchestrator.state.value)
        assertTrue(deviceMigration.pending)
    }

    private suspend fun givenForeignRunInProgress(): LinearOnboardingState {
        orchestrator.startPlan(singleStepPlan(id = "subscription_onboarding"))
        return orchestrator.state.value
    }

    private fun newStarter() = PendingOnboardingStarter(
        orchestrator = orchestrator,
        deviceMigration = deviceMigration,
        deviceMigrationPlanProvider = DeviceMigrationPlanProvider(
            defaultBrowserDetector = defaultBrowserDetector,
            appBuildConfig = appBuildConfig,
            notificationManager = notificationManager,
            dispatchers = coroutineRule.testDispatcherProvider,
        ),
        userStageStore = userStageStore,
        newUserOnboardingPlanBootstrapper = NewUserOnboardingPlanBootstrapper(
            orchestrator = orchestrator,
            planProvider = newUserPlanProvider,
            userStageStore = userStageStore,
            onboardingSkipper = onboardingSkipper,
        ),
    )

    private suspend fun finishMigrationPlan() {
        orchestrator.onEvent(NewUserOnboardingEvent.IntroAnimationFinished)
        orchestrator.onEvent(NewUserOnboardingEvent.NotificationPermissionFinished(granted = null))
        orchestrator.onEvent(NewUserOnboardingEvent.DefaultBrowserPromptFinished(isDefaultBrowser = false))
        orchestrator.onEvent(NewUserOnboardingEvent.ContinueClicked)
    }

    private fun givenStage(stage: AppStage) {
        runBlocking { whenever(userStageStore.getUserAppStage()).thenReturn(stage) }
    }

    private fun newUserPlan() = singleStepPlan(id = NewUserOnboardingPlanProvider.ROOT_PLAN_ID)

    private fun singleStepPlan(id: String) = LinearOnboardingPlan(
        id = id,
        steps = listOf(
            object : LinearOnboardingStep {
                override val id = "${id}_step"
                override val host = LinearOnboardingHost.OnboardingActivity
                override val precondition: suspend () -> Boolean = { true }
                override val transition: suspend (LinearOnboardingEvent) -> LinearOnboardingTransition = { LinearOnboardingTransition.Stay }
            },
        ),
    )

    private class FakeDeviceMigration : DeviceMigration {
        var migrated = false
        var pending = false
        var sourceNewInstall: Boolean? = null

        override suspend fun isMigratedInstall(): Boolean = migrated
        override suspend fun isMigrationFlowPending(): Boolean = pending
        override fun sourceDeviceIsNewInstall(): Boolean? = sourceNewInstall
        override suspend fun markMigrationFlowCompleted() {
            pending = false
        }
        override fun previousDeviceHadSync(): Boolean = false
    }
}
