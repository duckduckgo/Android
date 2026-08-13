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

import com.duckduckgo.app.browser.defaultbrowsing.DefaultBrowserDetector
import com.duckduckgo.app.cta.db.DismissedCtaDao
import com.duckduckgo.app.global.DefaultRoleBrowserDialog
import com.duckduckgo.app.onboarding.CustomAiOnboardingResolver
import com.duckduckgo.app.onboarding.CustomAiOnboardingStore
import com.duckduckgo.app.onboarding.DuckAiOnboardingAvailability
import com.duckduckgo.app.onboarding.DuckAiOnboardingDemo
import com.duckduckgo.app.onboarding.OnboardingPromptsExperimentManager
import com.duckduckgo.app.onboarding.SegmentedOnboardingExperimentManager
import com.duckduckgo.app.onboarding.store.OnboardingStore
import com.duckduckgo.app.onboarding.ui.page.OnboardingPixelSender
import com.duckduckgo.app.pixels.remoteconfig.AndroidBrowserConfigFeature
import com.duckduckgo.app.settings.db.SettingsDataStore
import com.duckduckgo.app.statistics.pixels.Pixel
import com.duckduckgo.app.widget.ui.WidgetCapabilities
import com.duckduckgo.appbuildconfig.api.AppBuildConfig
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.duckchat.api.DuckChat
import com.duckduckgo.duckchat.impl.wideevents.InputScreenOnboardingWideEvent
import com.duckduckgo.feature.toggles.api.Toggle
import com.duckduckgo.sync.api.SyncAutoRestore
import kotlinx.coroutines.runBlocking
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

/**
 * Shared mock graph for the plan-builder and plan-provider tests. Holds one mock per production
 * dependency, stubs the initial-user defaults (no restore, no reinstall, no experiments), and wires the
 * real [NewUserOnboardingSteps]/[NewUserOnboardingPlans]/builders on top. Tests override individual
 * stubs to steer a scenario.
 */
internal class NewUserOnboardingPlanHarness(private val dispatchers: DispatcherProvider) {

    val syncAutoRestore: SyncAutoRestore = mock()
    val appBuildConfig: AppBuildConfig = mock()
    val defaultRoleBrowserDialog: DefaultRoleBrowserDialog = mock()
    val settingsDataStore: SettingsDataStore = mock()
    val onboardingStore: OnboardingStore = mock()
    val duckChat: DuckChat = mock()
    val androidBrowserConfigFeature: AndroidBrowserConfigFeature = mock()
    val duckAiAvailability: DuckAiOnboardingAvailability = mock()
    val onboardingPixelSender: OnboardingPixelSender = mock()
    val inputScreenOnboardingWideEvent: InputScreenOnboardingWideEvent = mock()
    val defaultBrowserDetector: DefaultBrowserDetector = mock()
    val widgetCapabilities: WidgetCapabilities = mock()
    val pixel: Pixel = mock()
    val splitOmnibarToggle: Toggle = mock()
    val splitOmnibarWelcomeToggle: Toggle = mock()
    val dismissedCtaDao: DismissedCtaDao = mock()
    val customAiOnboardingStore: CustomAiOnboardingStore = mock()
    val customAiOnboardingResolver: CustomAiOnboardingResolver = mock()
    val duckAiOnboardingDemo: DuckAiOnboardingDemo = mock()
    val homeScreenPromptsExperiment: OnboardingPromptsExperimentManager = mock()
    val segmentedOnboardingExperiment: SegmentedOnboardingExperimentManager = mock()

    init {
        whenever(androidBrowserConfigFeature.splitOmnibar()).thenReturn(splitOmnibarToggle)
        whenever(androidBrowserConfigFeature.splitOmnibarWelcomePage()).thenReturn(splitOmnibarWelcomeToggle)
        whenever(splitOmnibarToggle.isEnabled()).thenReturn(false)
        whenever(splitOmnibarWelcomeToggle.isEnabled()).thenReturn(false)
        whenever(defaultRoleBrowserDialog.shouldShowDialog()).thenReturn(true)
        whenever(defaultBrowserDetector.isDefaultBrowser()).thenReturn(false)
        whenever(widgetCapabilities.hasInstalledWidgets).thenReturn(false)
        runBlocking {
            whenever(syncAutoRestore.canRestore()).thenReturn(false)
            whenever(appBuildConfig.isAppReinstall()).thenReturn(false)
            whenever(duckAiAvailability.isDuckAiOnboardingEnabled()).thenReturn(false)
            whenever(customAiOnboardingResolver.resolve()).thenReturn(false)
            whenever(homeScreenPromptsExperiment.enroll())
                .thenReturn(OnboardingPromptsExperimentManager.OnboardingPromptExperimentVariant.CONTROL)
            whenever(segmentedOnboardingExperiment.enroll()).thenReturn(null)
        }
    }

    val steps = NewUserOnboardingSteps(
        syncAutoRestore = syncAutoRestore,
        appBuildConfig = appBuildConfig,
        defaultRoleBrowserDialog = defaultRoleBrowserDialog,
        settingsDataStore = settingsDataStore,
        onboardingStore = onboardingStore,
        duckChat = duckChat,
        androidBrowserConfigFeature = androidBrowserConfigFeature,
        onboardingPixelSender = onboardingPixelSender,
        inputScreenOnboardingWideEvent = inputScreenOnboardingWideEvent,
        defaultBrowserDetector = defaultBrowserDetector,
        widgetCapabilities = widgetCapabilities,
        pixel = pixel,
        dispatchers = dispatchers,
    )

    val plans = NewUserOnboardingPlans(
        onboardingPixelSender = onboardingPixelSender,
        onboardingSteps = steps,
    )

    fun defaultPlanBuilder() = DefaultOnboardingPlanBuilder(
        steps = steps,
        plans = plans,
        syncAutoRestore = syncAutoRestore,
        duckAiOnboardingAvailability = duckAiAvailability,
        onboardingStore = onboardingStore,
        onboardingPixelSender = onboardingPixelSender,
        widgetCapabilities = widgetCapabilities,
        pixel = pixel,
        dispatchers = dispatchers,
    )

    fun customAiPlanBuilder() = CustomAiOnboardingPlanBuilder(
        steps = steps,
        plans = plans,
        duckChat = duckChat,
        onboardingStore = onboardingStore,
        duckAiOnboardingDemo = duckAiOnboardingDemo,
        customAiOnboardingStore = customAiOnboardingStore,
        dismissedCtaDao = dismissedCtaDao,
        onboardingPixelSender = onboardingPixelSender,
        pixel = pixel,
        dispatchers = dispatchers,
    )

    fun segmentedPlanBuilder() = SegmentedOnboardingPlanBuilder(
        steps = steps,
        plans = plans,
    )

    fun provider() = NewUserOnboardingPlanProvider(
        appBuildConfig = appBuildConfig,
        customAiOnboardingResolver = customAiOnboardingResolver,
        onboardingPromptsExperimentManager = homeScreenPromptsExperiment,
        segmentedOnboardingExperimentManager = segmentedOnboardingExperiment,
        defaultPlanBuilder = defaultPlanBuilder(),
        customAiPlanBuilder = customAiPlanBuilder(),
        segmentedPlanBuilder = segmentedPlanBuilder(),
        dispatchers = dispatchers,
    )
}
