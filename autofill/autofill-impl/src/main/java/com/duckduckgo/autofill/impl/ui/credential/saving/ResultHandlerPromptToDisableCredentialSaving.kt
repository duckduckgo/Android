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
import androidx.annotation.VisibleForTesting
import androidx.appcompat.app.AlertDialog.Builder
import androidx.fragment.app.Fragment
import com.duckduckgo.app.global.DispatcherProvider
import com.duckduckgo.app.statistics.pixels.Pixel
import com.duckduckgo.autofill.api.AutofillEventListener
import com.duckduckgo.autofill.api.AutofillFragmentResultsPlugin
import com.duckduckgo.autofill.api.CredentialSavePickerDialog
import com.duckduckgo.autofill.api.store.AutofillStore
import com.duckduckgo.autofill.impl.AutofillFireproofDialogSuppressor
import com.duckduckgo.autofill.impl.R.string
import com.duckduckgo.autofill.impl.pixel.AutofillPixelNames
import com.duckduckgo.autofill.impl.ui.credential.saving.declines.AutofillDeclineCounter
import com.duckduckgo.di.scopes.AppScope
import com.squareup.anvil.annotations.ContributesMultibinding
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber

@ContributesMultibinding(AppScope::class)
class ResultHandlerPromptToDisableCredentialSaving @Inject constructor(
    private val autofillFireproofDialogSuppressor: AutofillFireproofDialogSuppressor,
    private val pixel: Pixel,
    private val dispatchers: DispatcherProvider,
    private val declineCounter: AutofillDeclineCounter,
    private val autofillStore: AutofillStore,
    @com.duckduckgo.app.di.AppCoroutineScope private val appCoroutineScope: CoroutineScope,
) : AutofillFragmentResultsPlugin {

    override fun processResult(
        result: Bundle,
        context: Context,
        tabId: String,
        fragment: Fragment,
        autofillCallback: AutofillEventListener,
    ) {
        Timber.d("${this::class.java.simpleName}: processing result")

        autofillFireproofDialogSuppressor.autofillSaveOrUpdateDialogVisibilityChanged(visible = false)

        pixel.fire(AutofillPixelNames.AUTOFILL_DECLINE_PROMPT_TO_DISABLE_AUTOFILL_SHOWN)

        appCoroutineScope.launch(dispatchers.main()) {
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
    }

    private fun onCancelledPromptToDisableAutofill() {
        pixel.fire(AutofillPixelNames.AUTOFILL_DECLINE_PROMPT_TO_DISABLE_AUTOFILL_DISMISSED)
    }

    @VisibleForTesting
    fun onKeepUsingAutofill() {
        Timber.i("User selected to keep using autofill; will not prompt to disable again")
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

            Timber.i("Autofill disabled at user request")
        }
        pixel.fire(AutofillPixelNames.AUTOFILL_DECLINE_PROMPT_TO_DISABLE_AUTOFILL_DISABLE)
    }

    override fun resultKey(tabId: String): String {
        return CredentialSavePickerDialog.resultKeyShouldPromptToDisableAutofill(tabId)
    }
}
