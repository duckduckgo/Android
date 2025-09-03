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

import android.annotation.SuppressLint
import android.content.Context
import android.os.Bundle
import android.os.Parcelable
import android.webkit.WebView
import androidx.fragment.app.Fragment
import com.duckduckgo.app.di.AppCoroutineScope
import com.duckduckgo.appbuildconfig.api.AppBuildConfig
import com.duckduckgo.autofill.api.AutofillEventListener
import com.duckduckgo.autofill.api.AutofillFragmentResultsPlugin
import com.duckduckgo.autofill.api.EmailProtectionInContextSignUpDialog
import com.duckduckgo.autofill.api.EmailProtectionInContextSignUpDialog.EmailProtectionInContextSignUpResult
import com.duckduckgo.autofill.api.EmailProtectionInContextSignUpDialog.EmailProtectionInContextSignUpResult.*
import com.duckduckgo.autofill.impl.email.incontext.store.EmailProtectionInContextDataStore
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.di.scopes.AppScope
import com.squareup.anvil.annotations.ContributesMultibinding
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import logcat.LogPriority.INFO
import logcat.logcat

@ContributesMultibinding(AppScope::class)
class ResultHandlerInContextEmailProtectionPrompt @Inject constructor(
    @AppCoroutineScope private val appCoroutineScope: CoroutineScope,
    private val dispatchers: DispatcherProvider,
    private val dataStore: EmailProtectionInContextDataStore,
    private val appBuildConfig: AppBuildConfig,
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

        val userSelection = result.safeGetParcelable<EmailProtectionInContextSignUpResult>(EmailProtectionInContextSignUpDialog.KEY_RESULT) ?: return

        appCoroutineScope.launch(dispatchers.io()) {
            when (userSelection) {
                SignUp -> signUpSelected(autofillCallback)
                Cancel -> cancelled(autofillCallback)
                DoNotShowAgain -> doNotAskAgain(autofillCallback)
            }
        }
    }

    private suspend fun signUpSelected(autofillCallback: AutofillEventListener) {
        withContext(dispatchers.main()) {
            autofillCallback.onSelectedToSignUpForInContextEmailProtection()
        }
    }

    private suspend fun doNotAskAgain(autofillCallback: AutofillEventListener) {
        logcat(INFO) { "User selected to not show sign up for email protection again" }
        dataStore.onUserChoseNeverAskAgain()
        notifyEndOfFlow(autofillCallback)
    }

    private suspend fun cancelled(autofillCallback: AutofillEventListener) {
        logcat(INFO) { "User cancelled sign up for email protection" }
        notifyEndOfFlow(autofillCallback)
    }

    private suspend fun notifyEndOfFlow(autofillCallback: AutofillEventListener) {
        withContext(dispatchers.main()) {
            autofillCallback.onEndOfEmailProtectionInContextSignupFlow()
        }
    }

    override fun resultKey(tabId: String): String {
        return EmailProtectionInContextSignUpDialog.resultKey(tabId)
    }

    @Suppress("DEPRECATION")
    @SuppressLint("NewApi")
    private inline fun <reified T : Parcelable> Bundle.safeGetParcelable(key: String) =
        if (appBuildConfig.sdkInt >= 33) {
            getParcelable(key, T::class.java)
        } else {
            getParcelable(key)
        }
}
