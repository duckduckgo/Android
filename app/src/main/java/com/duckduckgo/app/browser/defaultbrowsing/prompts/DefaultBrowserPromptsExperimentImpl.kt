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
import androidx.annotation.VisibleForTesting
import androidx.lifecycle.LifecycleOwner
import com.duckduckgo.app.browser.defaultbrowsing.DefaultBrowserDetector
import com.duckduckgo.app.browser.defaultbrowsing.DefaultBrowserSystemSettings
import com.duckduckgo.app.browser.defaultbrowsing.prompts.DefaultBrowserPromptsExperiment.Command
import com.duckduckgo.app.browser.defaultbrowsing.prompts.DefaultBrowserPromptsExperiment.Command.OpenMessageDialog
import com.duckduckgo.app.browser.defaultbrowsing.prompts.DefaultBrowserPromptsExperiment.SetAsDefaultActionTrigger
import com.duckduckgo.app.browser.defaultbrowsing.prompts.DefaultBrowserPromptsExperiment.SetAsDefaultActionTrigger.DIALOG
import com.duckduckgo.app.browser.defaultbrowsing.prompts.DefaultBrowserPromptsExperiment.SetAsDefaultActionTrigger.MENU
import com.duckduckgo.app.browser.defaultbrowsing.prompts.DefaultBrowserPromptsExperiment.SetAsDefaultActionTrigger.UNKNOWN
import com.duckduckgo.app.browser.defaultbrowsing.prompts.DefaultBrowserPromptsFeatureToggles.AdditionalPromptsCohortName
import com.duckduckgo.app.browser.defaultbrowsing.prompts.store.DefaultBrowserPromptsDataStore
import com.duckduckgo.app.browser.defaultbrowsing.prompts.store.DefaultBrowserPromptsDataStore.ExperimentStage
import com.duckduckgo.app.browser.defaultbrowsing.prompts.store.DefaultBrowserPromptsDataStore.ExperimentStage.CONVERTED
import com.duckduckgo.app.browser.defaultbrowsing.prompts.store.DefaultBrowserPromptsDataStore.ExperimentStage.ENROLLED
import com.duckduckgo.app.browser.defaultbrowsing.prompts.store.DefaultBrowserPromptsDataStore.ExperimentStage.NOT_ENROLLED
import com.duckduckgo.app.browser.defaultbrowsing.prompts.store.DefaultBrowserPromptsDataStore.ExperimentStage.STAGE_1
import com.duckduckgo.app.browser.defaultbrowsing.prompts.store.DefaultBrowserPromptsDataStore.ExperimentStage.STAGE_2
import com.duckduckgo.app.browser.defaultbrowsing.prompts.store.DefaultBrowserPromptsDataStore.ExperimentStage.STOPPED
import com.duckduckgo.app.browser.defaultbrowsing.prompts.store.ExperimentAppUsageRepository
import com.duckduckgo.app.di.AppCoroutineScope
import com.duckduckgo.app.global.DefaultRoleBrowserDialog
import com.duckduckgo.app.lifecycle.MainProcessLifecycleObserver
import com.duckduckgo.app.onboarding.store.AppStage
import com.duckduckgo.app.onboarding.store.UserStageStore
import com.duckduckgo.app.pixels.AppPixelName
import com.duckduckgo.app.statistics.pixels.Pixel
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.common.utils.plugins.PluginPoint
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.feature.toggles.api.MetricsPixel
import com.duckduckgo.feature.toggles.api.Toggle
import com.duckduckgo.privacy.config.api.PrivacyConfigCallbackPlugin
import com.squareup.anvil.annotations.ContributesBinding
import com.squareup.anvil.annotations.ContributesMultibinding
import com.squareup.moshi.Moshi
import dagger.SingleInstanceIn
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import logcat.LogPriority.ERROR
import logcat.asLog
import logcat.logcat

/**
 * Introduced by [this Asana task](https://app.asana.com/0/1208671518894266/1207295380941379/f).
 *
 * For more information refer to the diagrams in [com.duckduckgo.app.browser.defaultbrowsing.prompts.diagrams].
 */
@ContributesMultibinding(
    scope = AppScope::class,
    boundType = MainProcessLifecycleObserver::class,
)
@ContributesMultibinding(
    AppScope::class,
    boundType = PrivacyConfigCallbackPlugin::class,
)
@ContributesBinding(
    scope = AppScope::class,
    boundType = DefaultBrowserPromptsExperiment::class,
)
@SingleInstanceIn(scope = AppScope::class)
class DefaultBrowserPromptsExperimentImpl @Inject constructor(
    @AppCoroutineScope private val appCoroutineScope: CoroutineScope,
    private val dispatchers: DispatcherProvider,
    private val applicationContext: Context,
    private val defaultBrowserPromptsFeatureToggles: DefaultBrowserPromptsFeatureToggles,
    private val defaultBrowserDetector: DefaultBrowserDetector,
    private val defaultRoleBrowserDialog: DefaultRoleBrowserDialog,
    private val experimentAppUsageRepository: ExperimentAppUsageRepository,
    private val userStageStore: UserStageStore,
    private val defaultBrowserPromptsDataStore: DefaultBrowserPromptsDataStore,
    private val experimentStageEvaluatorPluginPoint: PluginPoint<DefaultBrowserPromptsExperimentStageEvaluator>,
    private val metrics: DefaultBrowserPromptsExperimentMetrics,
    private val pixel: Pixel,
    moshi: Moshi,
) : DefaultBrowserPromptsExperiment, MainProcessLifecycleObserver, PrivacyConfigCallbackPlugin {

    private val evaluationMutex = Mutex()

    private val _commands = Channel<Command>(capacity = Channel.CONFLATED)
    override val commands: Flow<Command> = _commands.receiveAsFlow()

    override val showSetAsDefaultPopupMenuItem: StateFlow<Boolean> = defaultBrowserPromptsDataStore.showSetAsDefaultPopupMenuItem.stateIn(
        scope = appCoroutineScope,
        started = SharingStarted.Lazily,
        initialValue = false,
    )

    override val highlightPopupMenu: StateFlow<Boolean> = defaultBrowserPromptsDataStore.highlightPopupMenu.stateIn(
        scope = appCoroutineScope,
        started = SharingStarted.Lazily,
        initialValue = false,
    )

    /**
     * Model used to parse remote config setting. All values are integer strings, for example "1" or "20".
     */
    @VisibleForTesting
    data class FeatureSettingsConfigModel(
        val activeDaysUntilStage1: String,
        val activeDaysUntilStage2: String,
        val activeDaysUntilStop: String,
    )

    private data class FeatureSettings(
        val activeDaysUntilStage1: Int,
        val activeDaysUntilStage2: Int,
        val activeDaysUntilStop: Int,
    )

    private val featureSettingsJsonAdapter = moshi.adapter(FeatureSettingsConfigModel::class.java)

    /**
     * Caches deserialized [Toggle.getSettings] for [DefaultBrowserPromptsFeatureToggles.defaultBrowserAdditionalPrompts202501].
     *
     * Since we're re-evaluating the experiment on every process' resume,
     * this allows us to avoid constantly deserializing the same value.
     *
     * The value is recomputed on first launch, and on each subsequent privacy config change via [onPrivacyConfigDownloaded].
     */
    private var featureSettings: FeatureSettings? = null

    /**
     * Provides a workaround for cases where the default system browser selection dialog is not available.
     *
     * If this [Deferred] is active when [onSystemDefaultBrowserDialogCanceled] is called,
     * then it means that we should fallback to opening the default apps activity instead.
     *
     * If this [Deferred] is complete or cancelled, it means that we should ignore the dialog cancellation as it was likely intentional by the user.
     *
     * More context in [this Asana task](https://app.asana.com/0/0/1208996977455495/f).
     */
    private var browserSelectionWindowFallbackDeferred: Deferred<Unit>? = null

    init {
        appCoroutineScope.launch {
            userStageStore.userAppStageFlow().collect {
                evaluate()
            }
        }
    }

    override fun onResume(owner: LifecycleOwner) {
        super.onResume(owner)
        appCoroutineScope.launch {
            experimentAppUsageRepository.recordAppUsedNow()
            evaluate()
        }
    }

    override fun onPrivacyConfigDownloaded() {
        appCoroutineScope.launch {
            featureSettings = defaultBrowserPromptsFeatureToggles.parseFeatureSettings()
            evaluate()
        }
    }

    private suspend fun evaluate() = evaluationMutex.withLock {
        val isOnboardingComplete = userStageStore.getUserAppStage() == AppStage.ESTABLISHED
        val isEnrolled = defaultBrowserPromptsFeatureToggles.defaultBrowserAdditionalPrompts202501().getCohort() != null
        val isDefaultBrowser = defaultBrowserDetector.isDefaultBrowser()
        val isEligible = isOnboardingComplete && (isEnrolled || !isDefaultBrowser)
        if (!isEligible) {
            return
        }

        val hasConvertedBefore = defaultBrowserPromptsDataStore.experimentStage.first() == CONVERTED
        val isStopped = defaultBrowserPromptsDataStore.experimentStage.first() == STOPPED
        if (hasConvertedBefore || isStopped) {
            return
        }

        val activeCohortName = defaultBrowserPromptsFeatureToggles.getOrAssignCohort()
        val currentExperimentStage = defaultBrowserPromptsDataStore.experimentStage.first()
        if (activeCohortName == null && currentExperimentStage == NOT_ENROLLED) {
            // The user wasn't enrolled before and wasn't enrolled now either.
            return
        }

        val newExperimentStage = if (activeCohortName == null) {
            // If experiment was underway but we lost the cohort name, it means that the experiment was remotely disabled.
            STOPPED
        } else if (isDefaultBrowser) {
            CONVERTED
        } else {
            val appActiveDaysUsedSinceEnrollment = experimentAppUsageRepository.getActiveDaysUsedSinceEnrollment(
                defaultBrowserPromptsFeatureToggles.defaultBrowserAdditionalPrompts202501(),
            ).getOrElse { throwable ->
                logcat(ERROR) { throwable.asLog() }
                return
            }

            val configSettings = featureSettings ?: run {
                // If feature settings weren't cached before, deserialize and cache them now.
                val parsedSettings = defaultBrowserPromptsFeatureToggles.parseFeatureSettings()
                featureSettings = parsedSettings
                parsedSettings
            } ?: run {
                logcat(ERROR) { "Failed to obtain feature settings." }
                return
            }

            when (currentExperimentStage) {
                NOT_ENROLLED -> ENROLLED

                ENROLLED -> {
                    if (appActiveDaysUsedSinceEnrollment >= configSettings.activeDaysUntilStage1) {
                        STAGE_1
                    } else {
                        null
                    }
                }

                STAGE_1 -> {
                    if (appActiveDaysUsedSinceEnrollment >= configSettings.activeDaysUntilStage2) {
                        STAGE_2
                    } else {
                        null
                    }
                }

                STAGE_2 -> {
                    if (appActiveDaysUsedSinceEnrollment >= configSettings.activeDaysUntilStop) {
                        STOPPED
                    } else {
                        null
                    }
                }

                STOPPED, CONVERTED -> null
            }
        }

        if (newExperimentStage != null) {
            defaultBrowserPromptsDataStore.storeExperimentStage(newExperimentStage)

            when (newExperimentStage) {
                STAGE_1 -> {
                    metrics.getStageImpressionForStage1()?.fire()
                }

                STAGE_2 -> {
                    metrics.getStageImpressionForStage2()?.fire()
                }

                CONVERTED -> {
                    fireConversionPixels(currentExperimentStage)
                }

                else -> {
                    // no-op
                }
            }

            val action = experimentStageEvaluatorPluginPoint.getPlugins()
                .firstOrNull { it.targetCohort == activeCohortName }?.evaluate(newExperimentStage)
                ?: DefaultBrowserPromptsExperimentStageAction.disableAll // if there's no matching evaluator, or cohort is null or disabled, clean up

            if (action.showMessageDialog) {
                _commands.send(OpenMessageDialog)
            }
            defaultBrowserPromptsDataStore.storeShowSetAsDefaultPopupMenuItemState(action.showSetAsDefaultPopupMenuItem)
            defaultBrowserPromptsDataStore.storeHighlightPopupMenuState(action.highlightPopupMenu)
        }
    }

    override fun onPopupMenuLaunched() {
        appCoroutineScope.launch {
            defaultBrowserPromptsDataStore.storeHighlightPopupMenuState(highlight = false)
        }
    }

    override fun onSetAsDefaultPopupMenuItemSelected() {
        fireInteractionPixel(AppPixelName.SET_AS_DEFAULT_IN_MENU_CLICK)
        launchBestSelectionWindow(trigger = MENU)
    }

    override fun onMessageDialogShown() {
        fireInteractionPixel(AppPixelName.SET_AS_DEFAULT_PROMPT_IMPRESSION)
    }

    override fun onMessageDialogCanceled() {
        fireInteractionPixel(AppPixelName.SET_AS_DEFAULT_PROMPT_DISMISSED)
    }

    override fun onMessageDialogConfirmationButtonClicked() {
        fireInteractionPixel(AppPixelName.SET_AS_DEFAULT_PROMPT_CLICK)
        launchBestSelectionWindow(trigger = DIALOG)
    }

    override fun onMessageDialogNotNowButtonClicked() {
        fireInteractionPixel(AppPixelName.SET_AS_DEFAULT_PROMPT_DISMISSED)
    }

    override fun onSystemDefaultBrowserDialogShown() {
        browserSelectionWindowFallbackDeferred = appCoroutineScope.async {
            delay(FALLBACK_TO_DEFAULT_APPS_SCREEN_THRESHOLD_MILLIS)
        }
    }

    override fun onSystemDefaultBrowserDialogSuccess(trigger: SetAsDefaultActionTrigger) {
        appCoroutineScope.launch {
            when (trigger) {
                DIALOG -> metrics.getDefaultSetViaDialog()?.fire()
                MENU -> metrics.getDefaultSetViaMenu()?.fire()
                UNKNOWN -> {
                    logcat(ERROR) { "Trigger for default browser dialog result wasn't provided." }
                }
            }
        }
    }

    override fun onSystemDefaultBrowserDialogCanceled(trigger: SetAsDefaultActionTrigger) {
        if (browserSelectionWindowFallbackDeferred?.isActive == true) {
            browserSelectionWindowFallbackDeferred?.cancel()
            launchSystemDefaultAppsActivity(trigger)
        }
    }

    override fun onSystemDefaultAppsActivityClosed(trigger: SetAsDefaultActionTrigger) {
        if (defaultBrowserDetector.isDefaultBrowser()) {
            appCoroutineScope.launch {
                when (trigger) {
                    DIALOG -> metrics.getDefaultSetViaDialog()?.fire()
                    MENU -> metrics.getDefaultSetViaMenu()?.fire()
                    UNKNOWN -> {
                        logcat(ERROR) { "Trigger for default apps result wasn't provided." }
                    }
                }
            }
        }
    }

    private fun launchBestSelectionWindow(trigger: SetAsDefaultActionTrigger) {
        val command = defaultRoleBrowserDialog.createIntent(applicationContext)?.let {
            Command.OpenSystemDefaultBrowserDialog(intent = it, trigger)
        }
        if (command != null) {
            _commands.trySend(command)
        } else {
            launchSystemDefaultAppsActivity(trigger)
        }
    }

    private fun launchSystemDefaultAppsActivity(trigger: SetAsDefaultActionTrigger) {
        val command = Command.OpenSystemDefaultAppsActivity(DefaultBrowserSystemSettings.intent(), trigger)
        _commands.trySend(command)
    }

    private suspend fun DefaultBrowserPromptsFeatureToggles.parseFeatureSettings(): FeatureSettings? = withContext(dispatchers.io()) {
        defaultBrowserAdditionalPrompts202501().getSettings()?.let { settings ->
            try {
                featureSettingsJsonAdapter.fromJson(settings)?.toFeatureSettings()
            } catch (e: Exception) {
                logcat(ERROR) { e.asLog() }
                null
            }
        }
    }

    private suspend fun DefaultBrowserPromptsFeatureToggles.getOrAssignCohort(): AdditionalPromptsCohortName? {
        for (cohort in AdditionalPromptsCohortName.entries) {
            defaultBrowserAdditionalPrompts202501().enroll()
            if (defaultBrowserAdditionalPrompts202501().isEnrolledAndEnabled(cohort)) {
                return cohort
            }
        }
        return null
    }

    private suspend fun fireConversionPixels(currentExperimentStage: ExperimentStage) {
        when (currentExperimentStage) {
            STAGE_1 -> {
                metrics.getDefaultSetForStage1()?.fire()
            }

            STAGE_2 -> {
                metrics.getDefaultSetForStage2()?.fire()
            }

            else -> {
                // no-op
            }
        }
    }

    private fun fireInteractionPixel(pixelName: AppPixelName) = appCoroutineScope.launch {
        val variant = defaultBrowserPromptsFeatureToggles.defaultBrowserAdditionalPrompts202501().getCohort()?.name?.lowercase() ?: ""
        val stage = defaultBrowserPromptsDataStore.experimentStage.first().toString().lowercase()
        pixel.fire(
            pixel = pixelName,
            parameters = mapOf(
                PIXEL_PARAM_KEY_VARIANT to variant,
                PIXEL_PARAM_KEY_STAGE to stage,
            ),
        )
    }

    private fun MetricsPixel.fire() = getPixelDefinitions().forEach {
        pixel.fire(it.pixelName, it.params)
    }

    @Throws(NumberFormatException::class)
    private fun FeatureSettingsConfigModel.toFeatureSettings() = FeatureSettings(
        activeDaysUntilStage1 = activeDaysUntilStage1.toInt(),
        activeDaysUntilStage2 = activeDaysUntilStage2.toInt(),
        activeDaysUntilStop = activeDaysUntilStop.toInt(),
    )

    companion object {
        const val FALLBACK_TO_DEFAULT_APPS_SCREEN_THRESHOLD_MILLIS = 500L
        const val PIXEL_PARAM_KEY_VARIANT = "expVar"
        const val PIXEL_PARAM_KEY_STAGE = "expStage"
    }
}
