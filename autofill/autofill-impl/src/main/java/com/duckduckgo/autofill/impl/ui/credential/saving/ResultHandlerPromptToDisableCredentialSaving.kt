/*
 * Copyright (c) 2023 DuckDuckGo
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

package com.duckduckgo.autofill.impl.ui.credential.saving

import android.content.Context
import android.os.Bundle
import android.view.View
import android.webkit.WebView
import androidx.annotation.VisibleForTesting
import androidx.appcompat.app.AlertDialog.Builder
import androidx.fragment.app.Fragment
import com.duckduckgo.app.di.AppCoroutineScope
import com.duckduckgo.app.statistics.pixels.Pixel
import com.duckduckgo.autofill.api.AutofillEventListener
import com.duckduckgo.autofill.api.AutofillFeature
import com.duckduckgo.autofill.api.AutofillFragmentResultsPlugin
import com.duckduckgo.autofill.api.AutofillScreenLaunchSource.DisableInSettingsPrompt
import com.duckduckgo.autofill.api.AutofillScreens.AutofillPasswordsManagementScreen
import com.duckduckgo.autofill.api.AutofillScreens.AutofillSettingsScreen
import com.duckduckgo.autofill.api.CredentialSavePickerDialog
import com.duckduckgo.autofill.impl.AutofillFireproofDialogSuppressor
import com.duckduckgo.autofill.impl.R
import com.duckduckgo.autofill.impl.R.string
import com.duckduckgo.autofill.impl.pixel.AutofillPixelNames
import com.duckduckgo.autofill.impl.store.InternalAutofillStore
import com.duckduckgo.autofill.impl.ui.credential.saving.declines.AutofillDeclineCounter
import com.duckduckgo.autofill.store.AutofillPrefsStore
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.navigation.api.GlobalActivityStarter
import com.google.android.material.snackbar.Snackbar
import com.squareup.anvil.annotations.ContributesBinding
import com.squareup.anvil.annotations.ContributesMultibinding
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import logcat.LogPriority.INFO
import logcat.logcat
import javax.inject.Inject

@ContributesMultibinding(AppScope::class)
class ResultHandlerPromptToDisableCredentialSaving @Inject constructor(
    private val autofillFireproofDialogSuppressor: AutofillFireproofDialogSuppressor,
    private val behavior: DisableAutofillPromptBehaviorFactory,
    private val autofillPrefsStore: AutofillPrefsStore,
    @AppCoroutineScope private val appCoroutineScope: CoroutineScope,
    private val dispatchers: DispatcherProvider,
) : AutofillFragmentResultsPlugin {

    override suspend fun processResult(
        result: Bundle,
        context: Context,
        tabId: String,
        fragment: Fragment,
        autofillCallback: AutofillEventListener,
        webView: WebView?,
    ) {
        logcat { "${this::class.java.simpleName}: processing result" }

        autofillFireproofDialogSuppressor.autofillSaveOrUpdateDialogVisibilityChanged(visible = false)

        behavior.createBehavior(context, fragment, autofillCallback)?.showPrompt().also {
            recordTimestampWhenLastPromptedToDisable()
        }
    }

    private fun recordTimestampWhenLastPromptedToDisable() {
        appCoroutineScope.launch(dispatchers.io()) {
            autofillPrefsStore.timestampUserLastPromptedToDisableAutofill = System.currentTimeMillis()
        }
    }

    override fun resultKey(tabId: String): String {
        return CredentialSavePickerDialog.resultKeyShouldPromptToDisableAutofill(tabId)
    }
}

interface DisableAutofillPromptBehaviorFactory {
    fun createBehavior(
        context: Context,
        fragment: Fragment,
        autofillCallback: AutofillEventListener,
    ): DisableAutofillPromptBehavior?
}

@ContributesBinding(AppScope::class)
class BehaviorFactory @Inject constructor(
    private val pixel: Pixel,
    private val autofillFeature: AutofillFeature,
    private val dispatchers: DispatcherProvider,
    private val declineCounter: AutofillDeclineCounter,
    private val autofillStore: InternalAutofillStore,
    @com.duckduckgo.app.di.AppCoroutineScope private val appCoroutineScope: CoroutineScope,
    private val globalActivityStarter: GlobalActivityStarter,
) : DisableAutofillPromptBehaviorFactory {
    override fun createBehavior(
        context: Context,
        fragment: Fragment,
        autofillCallback: AutofillEventListener,
    ): DisableAutofillPromptBehavior? {
        return if (autofillFeature.showDisableDialogAutofillPrompt().isEnabled()) {
            AskToDisableDialog(context, pixel, dispatchers, declineCounter, autofillStore, autofillCallback, appCoroutineScope)
        } else {
            val view: View = fragment.view ?: return null
            DisableInSettingsSnackbar(context, pixel, view, globalActivityStarter, autofillFeature)
        }
    }
}

interface DisableAutofillPromptBehavior {
    fun showPrompt()
}

class DisableInSettingsSnackbar(
    private val context: Context,
    private val pixel: Pixel,
    private val view: View,
    private val globalActivityStarter: GlobalActivityStarter,
    private val autofillFeature: AutofillFeature,
) : DisableAutofillPromptBehavior {
    override fun showPrompt() {
        pixel.fire(AutofillPixelNames.AUTOFILL_DECLINE_PROMPT_TO_DISABLE_AUTOFILL_SNACKBAR_SHOWN)
        Snackbar.make(view, R.string.autofillDisableInSettingsSnackbarText, 4_000)
            .setAction(R.string.autofillDisableInSettingsSnackbarAction) { _ ->
                pixel.fire(AutofillPixelNames.AUTOFILL_DECLINE_PROMPT_TO_DISABLE_AUTOFILL_SNACKBAR_OPEN_SETTINGS)
                if (autofillFeature.settingsScreen().isEnabled()) {
                    globalActivityStarter.start(context, AutofillSettingsScreen(DisableInSettingsPrompt))
                } else {
                    globalActivityStarter.start(context, AutofillPasswordsManagementScreen(DisableInSettingsPrompt))
                }
            }.show()
    }
}

class AskToDisableDialog(
    private val context: Context,
    private val pixel: Pixel,
    private val dispatchers: DispatcherProvider,
    private val declineCounter: AutofillDeclineCounter,
    private val autofillStore: InternalAutofillStore,
    private val autofillCallback: AutofillEventListener,
    private val appCoroutineScope: CoroutineScope,
) : DisableAutofillPromptBehavior {

    override fun showPrompt() {
        pixel.fire(AutofillPixelNames.AUTOFILL_DECLINE_PROMPT_TO_DISABLE_AUTOFILL_SHOWN)

        Builder(context)
            .setTitle(context.getString(string.autofillDisableAutofillPromptTitle))
            .setMessage(context.getString(string.autofillDisableAutofillPromptMessage))
            .setPositiveButton(context.getString(string.autofillDisableAutofillPromptPositiveButton)) { _, _ -> onKeepUsingAutofill() }
            .setNegativeButton(context.getString(string.autofillDisableAutofillPromptNegativeButton)) { _, _ ->
                onDisableAutofill(autofillCallback)
            }
            .setOnCancelListener { onCancelledPromptToDisableAutofill() }
            .show()
    }

    private fun onCancelledPromptToDisableAutofill() {
        pixel.fire(AutofillPixelNames.AUTOFILL_DECLINE_PROMPT_TO_DISABLE_AUTOFILL_DISMISSED)
    }

    @VisibleForTesting
    fun onKeepUsingAutofill() {
        logcat(INFO) { "User selected to keep using autofill; will not prompt to disable again" }
        appCoroutineScope.launch(dispatchers.io()) {
            declineCounter.disableDeclineCounter()
        }
        pixel.fire(AutofillPixelNames.AUTOFILL_DECLINE_PROMPT_TO_DISABLE_AUTOFILL_KEEP_USING)
    }

    @VisibleForTesting
    fun onDisableAutofill(callback: AutofillEventListener) {
        appCoroutineScope.launch(dispatchers.io()) {
            autofillStore.autofillEnabled = false
            declineCounter.disableDeclineCounter()

            withContext(dispatchers.main()) {
                callback.onAutofillStateChange()
            }

            logcat(INFO) { "Autofill disabled at user request" }
        }
        pixel.fire(AutofillPixelNames.AUTOFILL_DECLINE_PROMPT_TO_DISABLE_AUTOFILL_DISABLE)
    }
}
