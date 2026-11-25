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
package com.duckduckgo.app.browser.newaddressbaroption

import androidx.lifecycle.LifecycleOwner
import com.duckduckgo.app.di.AppCoroutineScope
import com.duckduckgo.app.lifecycle.MainProcessLifecycleObserver
import com.duckduckgo.app.onboarding.store.AppStage
import com.duckduckgo.app.onboarding.store.OnboardingStore
import com.duckduckgo.app.onboarding.store.UserStageStore
import com.duckduckgo.app.statistics.store.StatisticsDataStore
import com.duckduckgo.common.utils.CurrentTimeProvider
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.duckchat.api.DuckChat
import com.duckduckgo.duckchat.api.DuckChatInputModeState
import com.duckduckgo.duckchat.api.nativeinput.NativeInputState.InputMode
import dev.zacsweers.metro.ContributesIntoSet
import dev.zacsweers.metro.binding
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import logcat.LogPriority.ERROR
import logcat.asLog
import logcat.logcat
import javax.inject.Inject
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/** Everything the cohort rule reads, captured once at cold start. */
data class CohortInputs(
    val appStage: AppStage,
    /** The remote Duck.ai feature is on and the user has not turned Duck.ai off. */
    val duckAiEnabled: Boolean,
    /** The user has sent a Duck.ai prompt (a Duck.ai retention ATB exists). */
    val hasPromptedDuckAi: Boolean,
    /** The input screen setting was ever written, true or false. */
    val inputScreenSettingWritten: Boolean,
    val pickerWasShown: Boolean,
    val pickerDisplayCount: Int,
    val legacyPickerWasShown: Boolean,
    /** The address bar choice stored during onboarding, or null when none was stored. */
    val onboardingSelection: Boolean?,
)

/** True when the user uses Duck.ai but sits on Search only because nothing ever asked them. */
fun CohortInputs.isNeverAskedDuckAiUser(): Boolean =
    appStage == AppStage.ESTABLISHED &&
        duckAiEnabled &&
        hasPromptedDuckAi &&
        !inputScreenSettingWritten &&
        !pickerWasShown &&
        pickerDisplayCount == 0 &&
        !legacyPickerWasShown &&
        onboardingSelection != false

/**
 * Turns Search & Duck.ai on, once and silently, for existing Duck.ai users who were never asked. Evaluates on every
 * main-process start until it flips the user, asynchronously right after the process starts; the address bar picker waits
 * on [DuckAiToggleMigrationGate] until each start's evaluation has decided.
 */
@ContributesIntoSet(
    scope = AppScope::class,
    binding = binding<MainProcessLifecycleObserver>(),
)
class DuckAiToggleMigrationObserver @Inject constructor(
    private val feature: DuckAiToggleMigrationFeature,
    private val store: DuckAiToggleMigrationStore,
    private val duckChat: DuckChat,
    private val inputModeState: DuckChatInputModeState,
    private val userStageStore: UserStageStore,
    private val statisticsDataStore: StatisticsDataStore,
    private val pickerDataStore: NewAddressBarPickerDataStore,
    private val legacyPickerStore: LegacyAddressBarOptionStore,
    private val onboardingStore: OnboardingStore,
    private val pixels: DuckAiToggleMigrationPixels,
    private val gate: DuckAiToggleMigrationGate,
    private val currentTimeProvider: CurrentTimeProvider,
    @AppCoroutineScope private val appCoroutineScope: CoroutineScope,
    private val dispatchers: DispatcherProvider,
) : MainProcessLifecycleObserver {

    override fun onCreate(owner: LifecycleOwner) {
        appCoroutineScope.launch(dispatchers.io()) {
            try {
                logFailure { if (feature.migration().isEnabled()) migrateIfNeeded() }
            } finally {
                gate.open()
            }
            logFailure {
                // Watch only while the flip is in effect. Migrated with the setting unwritten or off means the flip never
                // landed (a failed write, or recovery not run), and a picker or Settings answer then is not a revert.
                val migrated = store.state() as? MigrationState.Migrated
                if (migrated != null && inputModeState.getSelectedInputMode() == InputMode.SEARCH_AND_DUCK_AI) awaitRevert(migrated)
            }
        }
    }

    // A failure leaves the stored state as it was, and the next cold start picks up from there.
    private suspend fun logFailure(block: suspend () -> Unit) {
        try {
            block()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            logcat(ERROR) { "DuckAiToggleMigration: failed: ${e.asLog()}" }
        }
    }

    private suspend fun migrateIfNeeded() {
        when (val state = store.state()) {
            // Skipped is re-evaluated: every explicit "no" fails a cohort condition for good, so only a user who is still
            // never asked and has since become eligible (for example, started prompting Duck.ai) can flip later.
            MigrationState.NotEvaluated, MigrationState.Skipped -> if (readCohortInputs().isNeverAskedDuckAiUser()) {
                store.setState(MigrationState.Migrated(atMillis = currentTimeProvider.currentTimeMillis()))
                turnOnSearchAndDuckAi()
            } else if (state != MigrationState.Skipped) {
                store.setState(MigrationState.Skipped)
            }
            // The state is stored before the setting, so a crash in between leaves it unwritten. Finish the flip only if
            // the user is still in the cohort: the picker or Settings may have reached them since.
            is MigrationState.Migrated -> if (!duckChat.isInputScreenEverEnabled()) {
                if (readCohortInputs().isNeverAskedDuckAiUser()) turnOnSearchAndDuckAi() else store.setState(MigrationState.Skipped)
            }
            MigrationState.Reverted, MigrationState.Unrecognized -> Unit
        }
    }

    private suspend fun turnOnSearchAndDuckAi() {
        logcat { "DuckAiToggleMigration: turning on Search & Duck.ai" }
        duckChat.setInputScreenUserSetting(true)
        // The picker would otherwise ask a question this user no longer needs to answer. Marked after the setting write,
        // so a crash before it leaves the picker untouched and recovery still sees the user in the cohort.
        pickerDataStore.setAsShown()
        pixels.reportFlipped()
    }

    private suspend fun awaitRevert(migrated: MigrationState.Migrated) {
        // Both flows can emit before their values load, and the capability also drops when the remote Duck.ai feature goes
        // off. The stored selection holds only the user's own settings, so it decides whether the user undid the flip.
        merge(duckChat.observeInputScreenUserSettingEnabled(), inputModeState.inputModeCapability)
            .firstOrNull { inputModeState.getSelectedInputMode() == InputMode.SEARCH_ONLY } ?: return
        // The user switched one of their settings off: Duck.ai still enabled means it was the address bar toggle.
        val how = if (duckChat.isEnabled()) MigrationRevert.TOGGLE_OFF else MigrationRevert.DUCKAI_OFF
        store.setState(MigrationState.Reverted)
        pixels.reportReverted(millisSinceFlip = currentTimeProvider.currentTimeMillis() - migrated.atMillis, how = how)
    }

    // DuckChat.isEnabled() reads a cache that DuckChat fills asynchronously at process start, so an early false may only
    // mean "not loaded yet". Wait briefly for it; a user it still reads false for is skipped and re-evaluated next start.
    private suspend fun isDuckAiEnabled(): Boolean = withTimeoutOrNull(DUCK_AI_ENABLED_TIMEOUT) {
        while (!duckChat.isEnabled()) delay(DUCK_AI_ENABLED_POLL)
    } != null

    private suspend fun readCohortInputs() = CohortInputs(
        appStage = userStageStore.getUserAppStage(),
        duckAiEnabled = isDuckAiEnabled(),
        hasPromptedDuckAi = statisticsDataStore.duckaiRetentionAtb != null,
        inputScreenSettingWritten = duckChat.isInputScreenEverEnabled(),
        pickerWasShown = pickerDataStore.wasShown(),
        pickerDisplayCount = pickerDataStore.displayCount(),
        legacyPickerWasShown = legacyPickerStore.wasShown(),
        onboardingSelection = onboardingStore.getInputScreenSelection(),
    ).also { logcat { "DuckAiToggleMigration: $it" } }

    private companion object {
        val DUCK_AI_ENABLED_TIMEOUT = 3.seconds
        val DUCK_AI_ENABLED_POLL = 50.milliseconds
    }
}
