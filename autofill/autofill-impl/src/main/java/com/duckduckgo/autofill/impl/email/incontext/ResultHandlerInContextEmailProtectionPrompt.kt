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

package com.duckduckgo.autofill.impl.email.incontext

import android.content.Context
import android.os.Bundle
import androidx.core.os.BundleCompat
import androidx.fragment.app.Fragment
import com.duckduckgo.app.di.AppCoroutineScope
import com.duckduckgo.autofill.api.AutofillEventListener
import com.duckduckgo.autofill.api.AutofillFragmentResultsPlugin
import com.duckduckgo.autofill.api.AutofillWebMessageRequest
import com.duckduckgo.autofill.api.EmailProtectionInContextSignUpDialog
import com.duckduckgo.autofill.api.EmailProtectionInContextSignUpDialog.Companion.KEY_RESULT
import com.duckduckgo.autofill.api.EmailProtectionInContextSignUpDialog.Companion.KEY_URL
import com.duckduckgo.autofill.api.EmailProtectionInContextSignUpDialog.EmailProtectionInContextSignUpResult
import com.duckduckgo.autofill.api.EmailProtectionInContextSignUpDialog.EmailProtectionInContextSignUpResult.Cancel
import com.duckduckgo.autofill.api.EmailProtectionInContextSignUpDialog.EmailProtectionInContextSignUpResult.DoNotShowAgain
import com.duckduckgo.autofill.api.EmailProtectionInContextSignUpDialog.EmailProtectionInContextSignUpResult.SignUp
import com.duckduckgo.autofill.impl.email.incontext.store.EmailProtectionInContextDataStore
import com.duckduckgo.autofill.impl.jsbridge.AutofillMessagePoster
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.di.scopes.FragmentScope
import com.squareup.anvil.annotations.ContributesMultibinding
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber

@ContributesMultibinding(FragmentScope::class)
class ResultHandlerInContextEmailProtectionPrompt @Inject constructor(
    @AppCoroutineScope private val appCoroutineScope: CoroutineScope,
    private val dispatchers: DispatcherProvider,
    private val dataStore: EmailProtectionInContextDataStore,
    private val messagePoster: AutofillMessagePoster,
) : AutofillFragmentResultsPlugin {
    override fun processResult(
        result: Bundle,
        context: Context,
        tabId: String,
        fragment: Fragment,
        autofillCallback: AutofillEventListener,
    ) {
        Timber.d("${this::class.java.simpleName}: processing result")

        val userSelection = BundleCompat.getParcelable(result, KEY_RESULT, EmailProtectionInContextSignUpResult::class.java) ?: return
        val autofillWebMessageRequest = BundleCompat.getParcelable(result, KEY_URL, AutofillWebMessageRequest::class.java) ?: return

        appCoroutineScope.launch(dispatchers.io()) {
            when (userSelection) {
                SignUp -> signUpSelected(autofillCallback, autofillWebMessageRequest)
                Cancel -> cancelled(autofillWebMessageRequest)
                DoNotShowAgain -> doNotAskAgain(autofillWebMessageRequest)
            }
        }
    }

    private suspend fun signUpSelected(
        autofillCallback: AutofillEventListener,
        autofillWebMessageRequest: AutofillWebMessageRequest,
    ) {
        withContext(dispatchers.main()) {
            autofillCallback.onSelectedToSignUpForInContextEmailProtection(autofillWebMessageRequest)
        }
    }

    private suspend fun doNotAskAgain(autofillWebMessageRequest: AutofillWebMessageRequest) {
        Timber.i("User selected to not show sign up for email protection again")
        dataStore.onUserChoseNeverAskAgain()
        notifyEndOfFlow(autofillWebMessageRequest)
    }

    private suspend fun cancelled(autofillWebMessageRequest: AutofillWebMessageRequest) {
        Timber.i("User cancelled sign up for email protection")
        notifyEndOfFlow(autofillWebMessageRequest)
    }

    private fun notifyEndOfFlow(autofillWebMessageRequest: AutofillWebMessageRequest) {
        val message = """
            {
                "success": {
                    "isSignedIn": false
                }
            }
        """.trimIndent()
        messagePoster.postMessage(message, autofillWebMessageRequest.requestId)
    }

    override fun resultKey(tabId: String): String {
        return EmailProtectionInContextSignUpDialog.resultKey(tabId)
    }
}
