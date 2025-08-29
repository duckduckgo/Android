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
import com.duckduckgo.app.browser.defaultbrowsing.prompts.AdditionalDefaultBrowserPrompts.Command
import com.duckduckgo.app.browser.defaultbrowsing.prompts.AdditionalDefaultBrowserPrompts.Command.OpenMessageDialog
import com.duckduckgo.app.browser.defaultbrowsing.prompts.AdditionalDefaultBrowserPrompts.SetAsDefaultActionTrigger
import com.duckduckgo.app.browser.defaultbrowsing.prompts.AdditionalDefaultBrowserPrompts.SetAsDefaultActionTrigger.MENU
import com.duckduckgo.app.browser.defaultbrowsing.prompts.AdditionalDefaultBrowserPrompts.SetAsDefaultActionTrigger.MESSAGE
import com.duckduckgo.app.browser.defaultbrowsing.prompts.AdditionalDefaultBrowserPrompts.SetAsDefaultActionTrigger.PROMPT
import com.duckduckgo.app.browser.defaultbrowsing.prompts.AdditionalDefaultBrowserPrompts.SetAsDefaultActionTrigger.UNKNOWN
import com.duckduckgo.app.browser.defaultbrowsing.prompts.store.DefaultBrowserPromptsAppUsageRepository
import com.duckduckgo.app.browser.defaultbrowsing.prompts.store.DefaultBrowserPromptsDataStore
import com.duckduckgo.app.browser.defaultbrowsing.prompts.store.DefaultBrowserPromptsDataStore.Stage
import com.duckduckgo.app.browser.defaultbrowsing.prompts.store.DefaultBrowserPromptsDataStore.Stage.NOT_STARTED
import com.duckduckgo.app.browser.defaultbrowsing.prompts.store.DefaultBrowserPromptsDataStore.Stage.STAGE_1
import com.duckduckgo.app.browser.defaultbrowsing.prompts.store.DefaultBrowserPromptsDataStore.Stage.STAGE_2
import com.duckduckgo.app.browser.defaultbrowsing.prompts.store.DefaultBrowserPromptsDataStore.Stage.STAGE_3
import com.duckduckgo.app.browser.defaultbrowsing.prompts.store.DefaultBrowserPromptsDataStore.Stage.STARTED
import com.duckduckgo.app.browser.defaultbrowsing.prompts.store.DefaultBrowserPromptsDataStore.Stage.STOPPED
import com.duckduckgo.app.di.AppCoroutineScope
import com.duckduckgo.app.global.DefaultRoleBrowserDialog
import com.duckduckgo.app.lifecycle.MainProcessLifecycleObserver
import com.duckduckgo.app.onboarding.store.AppStage
import com.duckduckgo.app.onboarding.store.UserStageStore
import com.duckduckgo.app.pixels.AppPixelName
import com.duckduckgo.app.statistics.pixels.Pixel
import com.duckduckgo.browser.api.UserBrowserProperties
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.di.scopes.AppScope
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
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.firstOrNull
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
    boundType = AdditionalDefaultBrowserPrompts::class,
)
@SingleInstanceIn(scope = AppScope::class)
class AdditionalDefaultBrowserPromptsImpl @Inject constructor(
    @AppCoroutineScope private val appCoroutineScope: CoroutineScope,
    private val dispatchers: DispatcherProvider,
    private val applicationContext: Context,
    private val defaultBrowserPromptsFeatureToggles: DefaultBrowserPromptsFeatureToggles,
    private val defaultBrowserDetector: DefaultBrowserDetector,
    private val defaultRoleBrowserDialog: DefaultRoleBrowserDialog,
    private val defaultBrowserPromptsAppUsageRepository: DefaultBrowserPromptsAppUsageRepository,
    private val userStageStore: UserStageStore,
    private val defaultBrowserPromptsDataStore: DefaultBrowserPromptsDataStore,
    private val stageEvaluator: DefaultBrowserPromptsFlowStageEvaluator,
    private val userBrowserProperties: UserBrowserProperties,
    private val pixel: Pixel,
    moshi: Moshi,
) : AdditionalDefaultBrowserPrompts, MainProcessLifecycleObserver, PrivacyConfigCallbackPlugin {

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

    override val showSetAsDefaultMessage: StateFlow<Boolean> = defaultBrowserPromptsDataStore.stage
        .combine(defaultBrowserPromptsDataStore.showSetAsDefaultMessage) { stage, showMessage ->
            stage == STAGE_3 && showMessage
        }
        .stateIn(
            scope = appCoroutineScope,
            started = SharingStarted.Lazily,
            initialValue = false,
        )

    /**
     * Model used to parse remote config setting. All values are integer strings, for example "1" or "20".
     */
    @VisibleForTesting
    data class FeatureSettingsConfigModel(
        val newUserActiveDaysUntilStage1: String,
        val newUserActiveDaysUntilStage2: String,
        val newUserActiveDaysUntilStage3: String,
        val existingUserActiveDaysUntilStage1: String,
        val existingUserActiveDaysUntilStage3: String,
    )

    private data class FeatureSettings(
        val newUserActiveDaysUntilStage1: Int,
        val newUserActiveDaysUntilStage2: Int,
        val newUserActiveDaysUntilStage3: Int,
        val existingUserActiveDaysUntilStage1: Int,
        val existingUserActiveDaysUntilStage3: Int,
    )

    private val featureSettingsJsonAdapter = moshi.adapter(FeatureSettingsConfigModel::class.java)

    /**
     * Caches deserialized [Toggle.getSettings] for [DefaultBrowserPromptsFeatureToggles.defaultBrowserPrompts25].
     *
     * Since we're re-evaluating on every process' resume,
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
            evaluate()
        }
    }

    override fun onPrivacyConfigDownloaded() {
        appCoroutineScope.launch {
            featureSettings = defaultBrowserPromptsFeatureToggles.parseFeatureSettings()
            evaluate()
        }
    }

    override fun onUserMessageInteraction(doNotShowAgain: Boolean) {
        appCoroutineScope.launch {
            defaultBrowserPromptsDataStore.storeShowSetAsDefaultMessageState(false)
            if (doNotShowAgain) {
                defaultBrowserPromptsDataStore.storeStage(STOPPED)
            }
        }
    }

    private suspend fun evaluate() = evaluationMutex.withLock {
        val isEnabled =
            defaultBrowserPromptsFeatureToggles.self().isEnabled() && defaultBrowserPromptsFeatureToggles.defaultBrowserPrompts25().isEnabled()
        logcat { "evaluate: default browser remote flag enabled = $isEnabled" }
        if (!isEnabled) {
            return
        }

        val isStopped = defaultBrowserPromptsDataStore.stage.firstOrNull() == STOPPED
        logcat { "evaluate: has stopped = $isStopped" }
        if (isStopped) {
            return
        }

        val userType = getUserType()
        if (userType == DefaultBrowserPromptsDataStore.UserType.UNKNOWN) {
            logcat { "evaluate: user stage is unknown, skipping evaluation" }
            return
        }

        defaultBrowserPromptsAppUsageRepository.recordAppUsedNow()

        val isDefaultBrowser = defaultBrowserDetector.isDefaultBrowser()
        logcat { "evaluate: is default browser = $isDefaultBrowser" }

        val currentStage = defaultBrowserPromptsDataStore.stage.firstOrNull()
        logcat { "evaluate: current stage = $currentStage" }

        val newStage = if (isDefaultBrowser) {
            if (currentStage == STAGE_3) {
                fireConversionPixel(MESSAGE)
            }
            logcat { "evaluate: DuckDuckGo is default browser. Set the stage to STOPPED." }
            STOPPED
        } else if (currentStage == NOT_STARTED) {
            logcat { "evaluate: new stage is STARTED" }
            STARTED
        } else {
            logcat { "evaluate: current stage is other than NOT_STARTED and DuckDuckGo is NOT default browser." }
            val appActiveDaysUsedSinceStart = defaultBrowserPromptsAppUsageRepository.getActiveDaysUsedSinceStart().getOrElse { throwable ->
                logcat(ERROR) { throwable.asLog() }
                return
            }

            logcat { "evaluate: active days used since flow started = $appActiveDaysUsedSinceStart" }

            val configSettings = featureSettings ?: run {
                // If feature settings weren't cached before, deserialize and cache them now.
                val parsedSettings = defaultBrowserPromptsFeatureToggles.parseFeatureSettings()
                featureSettings = parsedSettings
                parsedSettings
            } ?: run {
                logcat(ERROR) { "Failed to obtain feature settings." }
                return
            }

            when (userType) {
                DefaultBrowserPromptsDataStore.UserType.NEW -> {
                    logcat { "evaluate: user is new" }
                    getNewStageForNewUser(currentStage, appActiveDaysUsedSinceStart, configSettings)
                }
                DefaultBrowserPromptsDataStore.UserType.EXISTING -> {
                    logcat { "evaluate: user is existing" }
                    getNewStageForExistingUser(currentStage, appActiveDaysUsedSinceStart, configSettings)
                }
                else -> {
                    logcat { "evaluate: user type is not known, skipping evaluation" }
                    return
                }
            }
        }

        if (newStage != null) {
            defaultBrowserPromptsDataStore.storeStage(newStage)

            val action = stageEvaluator.evaluate(newStage)
            logcat { "evaluate: action = $action show message dialog = ${action.showMessageDialog}" }

            if (action.showMessageDialog) {
                _commands.send(OpenMessageDialog)
            }
            defaultBrowserPromptsDataStore.storeShowSetAsDefaultPopupMenuItemState(action.showSetAsDefaultPopupMenuItem)
            defaultBrowserPromptsDataStore.storeHighlightPopupMenuState(action.highlightPopupMenu)
            defaultBrowserPromptsDataStore.storeShowSetAsDefaultMessageState(action.showMessage)
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
        launchBestSelectionWindow(trigger = PROMPT)
    }

    override fun onMessageDialogDoNotAskAgainButtonClicked() {
        fireInteractionPixel(AppPixelName.SET_AS_DEFAULT_PROMPT_DO_NOT_ASK_AGAIN_CLICK)
        appCoroutineScope.launch {
            // The user does not want to see the prompt again, so we jump to stage 2.
            defaultBrowserPromptsDataStore.storeStage(STAGE_2)
        }
    }

    override fun onSystemDefaultBrowserDialogShown() {
        browserSelectionWindowFallbackDeferred = appCoroutineScope.async {
            delay(FALLBACK_TO_DEFAULT_APPS_SCREEN_THRESHOLD_MILLIS)
        }
        fireInteractionPixel(AppPixelName.SET_AS_DEFAULT_SYSTEM_DIALOG_IMPRESSION)
    }

    override fun onSystemDefaultBrowserDialogSuccess(trigger: SetAsDefaultActionTrigger) {
        appCoroutineScope.launch {
            when (trigger) {
                PROMPT, MENU -> fireConversionPixel(trigger)
                MESSAGE -> {
                    // no-op
                    // the user selected the default browser from the message dialog and this is handled in evaluate()
                }
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
        fireInteractionPixel(AppPixelName.SET_AS_DEFAULT_SYSTEM_DIALOG_DISMISSED)
    }

    override fun onSystemDefaultAppsActivityClosed(trigger: SetAsDefaultActionTrigger) {
        if (defaultBrowserDetector.isDefaultBrowser()) {
            appCoroutineScope.launch {
                when (trigger) {
                    PROMPT, MENU -> fireConversionPixel(trigger)
                    MESSAGE -> {
                        // no-op
                        // the user selected the default browser from the message dialog and this is handled in evaluate()
                    }
                    UNKNOWN -> {
                        logcat(ERROR) { "Trigger for default apps result wasn't provided." }
                    }
                }
            }
        }
    }

    private suspend fun getUserType(): DefaultBrowserPromptsDataStore.UserType {
        val storedUserType = defaultBrowserPromptsDataStore.userType.firstOrNull()

        if (storedUserType == null) {
            return DefaultBrowserPromptsDataStore.UserType.UNKNOWN
        }

        val userAppStage = userStageStore.getUserAppStage()
        if (userAppStage != AppStage.ESTABLISHED) {
            return DefaultBrowserPromptsDataStore.UserType.UNKNOWN
        }

        if (storedUserType == DefaultBrowserPromptsDataStore.UserType.UNKNOWN) {
            val daysSinceInstalled = userBrowserProperties.daysSinceInstalled()
            val userType = if (daysSinceInstalled >= 4) {
                logcat { "evaluate: setting initial user stage to EXISTING" }
                DefaultBrowserPromptsDataStore.UserType.EXISTING
            } else {
                logcat { "evaluate: setting initial user stage to NEW" }
                DefaultBrowserPromptsDataStore.UserType.NEW
            }
            defaultBrowserPromptsDataStore.storeUserType(userType)
            return userType
        }

        return storedUserType
    }

    private fun getNewStageForNewUser(
        currentStage: Stage?,
        appActiveDaysUsedSinceStart: Long,
        configSettings: FeatureSettings,
    ): Stage? {
        return when (currentStage) {
            STARTED -> {
                if (appActiveDaysUsedSinceStart >= configSettings.newUserActiveDaysUntilStage1) {
                    logcat { "evaluate: user is new, go from STARTED to STAGE_1." }
                    STAGE_1
                } else {
                    null
                }
            }

            STAGE_1 -> {
                if (appActiveDaysUsedSinceStart >= configSettings.newUserActiveDaysUntilStage2) {
                    logcat { "evaluate: user is new, go from STAGE_1 to STAGE_2." }
                    STAGE_2
                } else {
                    null
                }
            }

            STAGE_2 -> {
                if (appActiveDaysUsedSinceStart >= configSettings.newUserActiveDaysUntilStage3) {
                    logcat { "evaluate: user is new, go from STAGE_2 to STAGE_3." }
                    STAGE_3
                } else {
                    null
                }
            }

            STAGE_3 -> {
                logcat {
                    "evaluate: user is new, and remains in STAGE_3 because " +
                        "they haven't set the browser as the default or opted to stop receiving the prompts."
                }
                STAGE_3
            }

            else -> null
        }
    }

    private fun getNewStageForExistingUser(
        currentStage: Stage?,
        appActiveDaysUsedSinceStart: Long,
        configSettings: FeatureSettings,
    ): Stage? {
        return when (currentStage) {
            STARTED -> {
                if (appActiveDaysUsedSinceStart >= configSettings.existingUserActiveDaysUntilStage1) {
                    logcat { "evaluate: user is existing, go from STARTED to STAGE_1." }
                    STAGE_1
                } else {
                    null
                }
            }

            STAGE_1 -> {
                if (appActiveDaysUsedSinceStart >= configSettings.existingUserActiveDaysUntilStage3) {
                    logcat { "evaluate: user is existing, skip STAGE_2 and go from STAGE_1 to STAGE_3." }
                    STAGE_3
                } else {
                    null
                }
            }

            STAGE_2 -> {
                logcat { "evaluate: user is existing, STAGE_2 should not be reached" }
                null
            }

            STAGE_3 -> {
                logcat {
                    "evaluate: user is existing, and remains in STAGE_3 because " +
                        "they haven't set the browser as the default or opted to stop receiving the prompts."
                }
                STAGE_3
            }

            else -> null
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
        defaultBrowserPrompts25().getSettings()?.let { settings ->
            try {
                featureSettingsJsonAdapter.fromJson(settings)?.toFeatureSettings()
            } catch (e: Exception) {
                logcat(ERROR) { e.asLog() }
                null
            }
        }
    }

    private fun fireConversionPixel(trigger: SetAsDefaultActionTrigger) = appCoroutineScope.launch {
        val stage = defaultBrowserPromptsDataStore.stage.firstOrNull().toString().lowercase()
        val triggerValue = trigger.toString().lowercase()
        logcat { "fireConversionPixel: pixelName = ${AppPixelName.SET_AS_DEFAULT_SYSTEM_DIALOG_CLICK} - stage = $stage - trigger = $triggerValue" }
        pixel.fire(
            pixel = AppPixelName.SET_AS_DEFAULT_SYSTEM_DIALOG_CLICK,
            parameters = mapOf(
                PIXEL_PARAM_KEY_STAGE to stage,
                PIXEL_PARAM_KEY_TRIGGER to triggerValue,
            ),
        )
    }

    private fun fireInteractionPixel(pixelName: AppPixelName) = appCoroutineScope.launch {
        val stage = defaultBrowserPromptsDataStore.stage.firstOrNull().toString().lowercase()
        logcat { "fireInteractionPixel pixelName = $pixelName - stage = $stage" }
        pixel.fire(
            pixel = pixelName,
            parameters = mapOf(
                PIXEL_PARAM_KEY_STAGE to stage,
            ),
        )
    }

    @Throws(NumberFormatException::class)
    private fun FeatureSettingsConfigModel.toFeatureSettings() = FeatureSettings(
        newUserActiveDaysUntilStage1 = newUserActiveDaysUntilStage1.toInt(),
        newUserActiveDaysUntilStage2 = newUserActiveDaysUntilStage2.toInt(),
        newUserActiveDaysUntilStage3 = newUserActiveDaysUntilStage3.toInt(),
        existingUserActiveDaysUntilStage1 = existingUserActiveDaysUntilStage1.toInt(),
        existingUserActiveDaysUntilStage3 = existingUserActiveDaysUntilStage3.toInt(),
    )

    companion object {
        const val FALLBACK_TO_DEFAULT_APPS_SCREEN_THRESHOLD_MILLIS = 500L
        const val PIXEL_PARAM_KEY_STAGE = "stage"
        const val PIXEL_PARAM_KEY_TRIGGER = "trigger"
    }
}
