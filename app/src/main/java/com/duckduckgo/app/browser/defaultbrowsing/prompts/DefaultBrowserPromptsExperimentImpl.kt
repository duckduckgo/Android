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
import com.duckduckgo.app.browser.defaultbrowsing.prompts.DefaultBrowserPromptsFeatureToggles.AdditionalPromptsCohortName
import com.duckduckgo.app.browser.defaultbrowsing.prompts.store.DefaultBrowserPromptsDataStore
import com.duckduckgo.app.browser.defaultbrowsing.prompts.store.DefaultBrowserPromptsDataStore.ExperimentStage.CONVERTED
import com.duckduckgo.app.browser.defaultbrowsing.prompts.store.DefaultBrowserPromptsDataStore.ExperimentStage.ENROLLED
import com.duckduckgo.app.browser.defaultbrowsing.prompts.store.DefaultBrowserPromptsDataStore.ExperimentStage.NOT_ENROLLED
import com.duckduckgo.app.browser.defaultbrowsing.prompts.store.DefaultBrowserPromptsDataStore.ExperimentStage.STAGE_1
import com.duckduckgo.app.browser.defaultbrowsing.prompts.store.DefaultBrowserPromptsDataStore.ExperimentStage.STAGE_2
import com.duckduckgo.app.browser.defaultbrowsing.prompts.store.DefaultBrowserPromptsDataStore.ExperimentStage.STOPPED
import com.duckduckgo.app.di.AppCoroutineScope
import com.duckduckgo.app.global.DefaultRoleBrowserDialog
import com.duckduckgo.app.lifecycle.MainProcessLifecycleObserver
import com.duckduckgo.app.usage.app.AppDaysUsedRepository
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.common.utils.plugins.PluginPoint
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.feature.toggles.api.Toggle
import com.duckduckgo.privacy.config.api.PrivacyConfigCallbackPlugin
import com.squareup.anvil.annotations.ContributesBinding
import com.squareup.anvil.annotations.ContributesMultibinding
import com.squareup.moshi.Moshi
import dagger.SingleInstanceIn
import java.time.ZonedDateTime
import java.util.Date
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
import kotlinx.coroutines.withContext
import timber.log.Timber

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
    private val appDaysUsedRepository: AppDaysUsedRepository,
    private val defaultBrowserPromptsDataStore: DefaultBrowserPromptsDataStore,
    private val experimentStageEvaluatorPluginPoint: PluginPoint<DefaultBrowserPromptsExperimentStageEvaluator>,
    moshi: Moshi,
) : DefaultBrowserPromptsExperiment, MainProcessLifecycleObserver, PrivacyConfigCallbackPlugin {

    private val _commands = Channel<Command>(capacity = Channel.CONFLATED)
    override val commands: Flow<Command> = _commands.receiveAsFlow()

    override val showOverflowMenuItem: StateFlow<Boolean> = defaultBrowserPromptsDataStore.showOverflowMenuItem.stateIn(
        scope = appCoroutineScope,
        started = SharingStarted.Lazily,
        initialValue = false,
    )

    override val highlightOverflowMenu: StateFlow<Boolean> = defaultBrowserPromptsDataStore.highlightOverflowMenuIcon.stateIn(
        scope = appCoroutineScope,
        started = SharingStarted.Lazily,
        initialValue = false,
    )

    @VisibleForTesting
    data class FeatureSettings(
        val activeDaysUntilStage1: Int,
        val activeDaysUntilStage2: Int,
        val activeDaysUntilStop: Int,
    )

    private val featureSettingsJsonAdapter = moshi.adapter(FeatureSettings::class.java)

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

    override fun onResume(owner: LifecycleOwner) {
        super.onResume(owner)
        appCoroutineScope.launch {
            evaluate()
        }
    }

    override fun onPrivacyConfigDownloaded() {
        appCoroutineScope.launch {
            featureSettings = defaultBrowserPromptsFeatureToggles.parseFeatureSettings()
            evaluate()
        }
    }

    private suspend fun evaluate() {
        val isEnrolled = defaultBrowserPromptsFeatureToggles.defaultBrowserAdditionalPrompts202501().getCohort() != null
        val isDefaultBrowser = defaultBrowserDetector.isDefaultBrowser()
        val isEligible = isEnrolled || !isDefaultBrowser
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
            /**
             * The [appDaysUsedRepository] expects a [Date] but the experiment framework stores the enrollment date as [ZonedDateTime],
             * so we're doing a conversion here.
             */
            val enrollmentDateGMT = defaultBrowserPromptsFeatureToggles.getEnrollmentDate() ?: run {
                Timber.e("Missing enrollment date even though cohort is assigned.")
                return
            }

            val configSettings = featureSettings ?: run {
                // If feature settings weren't cached before, deserialize and cache them now.
                val parsedSettings = defaultBrowserPromptsFeatureToggles.parseFeatureSettings()
                featureSettings = parsedSettings
                parsedSettings
            } ?: run {
                Timber.e("Failed to obtain feature settings.")
                return
            }

            when (currentExperimentStage) {
                NOT_ENROLLED -> ENROLLED

                ENROLLED -> {
                    if (appDaysUsedRepository.getNumberOfDaysAppUsedSinceDate(enrollmentDateGMT) >= configSettings.activeDaysUntilStage1) {
                        STAGE_1
                    } else {
                        null
                    }
                }

                STAGE_1 -> {
                    if (appDaysUsedRepository.getNumberOfDaysAppUsedSinceDate(enrollmentDateGMT) >= configSettings.activeDaysUntilStage2) {
                        STAGE_2
                    } else {
                        null
                    }
                }

                STAGE_2 -> {
                    if (appDaysUsedRepository.getNumberOfDaysAppUsedSinceDate(enrollmentDateGMT) >= configSettings.activeDaysUntilStop) {
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

            val action = experimentStageEvaluatorPluginPoint.getPlugins().first { it.targetCohort == activeCohortName }.evaluate(newExperimentStage)
            if (action.showMessageDialog) {
                _commands.send(OpenMessageDialog)
            }
            defaultBrowserPromptsDataStore.storeShowOverflowMenuItemState(action.showOverflowMenuItem)
            defaultBrowserPromptsDataStore.storeHighlightOverflowMenuIconState(action.highlightOverflowMenu)
        }
    }

    override fun onOverflowMenuOpened() {
        appCoroutineScope.launch {
            defaultBrowserPromptsDataStore.storeHighlightOverflowMenuIconState(highlight = false)
        }
    }

    override fun onOverflowMenuItemClicked() {
        appCoroutineScope.launch {
            launchBestSelectionWindow()
        }
    }

    override fun onMessageDialogShown() {
    }

    override fun onMessageDialogDismissed() {
    }

    override fun onMessageDialogConfirmationButtonClicked() {
        appCoroutineScope.launch {
            launchBestSelectionWindow()
        }
    }

    override fun onMessageDialogNotNowButtonClicked() {
    }

    override fun onSystemDefaultBrowserDialogShown() {
        browserSelectionWindowFallbackDeferred = appCoroutineScope.async {
            delay(FALLBACK_TO_DEFAULT_APPS_SCREEN_THRESHOLD_MILLIS)
        }
    }

    override fun onSystemDefaultBrowserDialogSuccess() {
    }

    override fun onSystemDefaultBrowserDialogCanceled() {
        appCoroutineScope.launch {
            if (browserSelectionWindowFallbackDeferred?.isActive == true) {
                browserSelectionWindowFallbackDeferred?.cancel()
                launchSystemDefaultAppsActivity()
            }
        }
    }

    override fun onSystemDefaultAppsActivityOpened() {
    }

    override fun onSystemDefaultAppsActivityClosed() {
    }

    private suspend fun launchBestSelectionWindow() {
        val command = defaultRoleBrowserDialog.createIntent(applicationContext)?.let {
            Command.OpenSystemDefaultBrowserDialog(intent = it)
        }
        if (command != null) {
            _commands.send(command)
        } else {
            launchSystemDefaultAppsActivity()
        }
    }

    private suspend fun launchSystemDefaultAppsActivity() {
        val command = Command.OpenSystemDefaultAppsActivity(DefaultBrowserSystemSettings.intent())
        _commands.send(command)
    }

    private suspend fun DefaultBrowserPromptsFeatureToggles.parseFeatureSettings(): FeatureSettings? = withContext(dispatchers.io()) {
        defaultBrowserAdditionalPrompts202501().getSettings()?.let { settings ->
            try {
                featureSettingsJsonAdapter.fromJson(settings)
            } catch (e: Exception) {
                Timber.e(e)
                null
            }
        }
    }

    private fun DefaultBrowserPromptsFeatureToggles.getEnrollmentDate(): Date? =
        defaultBrowserAdditionalPrompts202501().getCohort()?.enrollmentDateET?.let { enrollmentZonedDateET ->
            val instant = ZonedDateTime.parse(enrollmentZonedDateET).toInstant()
            return Date.from(instant)
        }

    private fun DefaultBrowserPromptsFeatureToggles.getOrAssignCohort(): AdditionalPromptsCohortName? {
        for (cohort in AdditionalPromptsCohortName.entries) {
            if (defaultBrowserAdditionalPrompts202501().isEnabled(cohort)) {
                return cohort
            }
        }
        return null
    }

    companion object {
        const val FALLBACK_TO_DEFAULT_APPS_SCREEN_THRESHOLD_MILLIS = 500L
    }
}
