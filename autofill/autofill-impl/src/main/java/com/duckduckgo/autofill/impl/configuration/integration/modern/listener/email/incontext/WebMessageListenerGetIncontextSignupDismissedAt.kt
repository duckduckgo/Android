/*
 * Copyright (c) 2024 DuckDuckGo
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

package com.duckduckgo.autofill.impl.configuration.integration.modern.listener.email.incontext

import android.annotation.SuppressLint
import android.net.Uri
import android.webkit.WebView
import androidx.webkit.JavaScriptReplyProxy
import androidx.webkit.WebMessageCompat
import com.duckduckgo.app.di.AppCoroutineScope
import com.duckduckgo.autofill.impl.configuration.integration.modern.listener.AutofillWebMessageListener
import com.duckduckgo.autofill.impl.email.incontext.availability.EmailProtectionInContextRecentInstallChecker
import com.duckduckgo.autofill.impl.email.incontext.store.EmailProtectionInContextDataStore
import com.duckduckgo.autofill.impl.jsbridge.response.AutofillResponseWriter
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.di.scopes.FragmentScope
import com.squareup.anvil.annotations.ContributesMultibinding
import dagger.SingleInstanceIn
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import timber.log.Timber

@SingleInstanceIn(FragmentScope::class)
@ContributesMultibinding(FragmentScope::class)
@SuppressLint("RequiresFeature")
class WebMessageListenerGetIncontextSignupDismissedAt @Inject constructor(
    @AppCoroutineScope private val appCoroutineScope: CoroutineScope,
    private val dispatchers: DispatcherProvider,
    private val autofillResponseWriter: AutofillResponseWriter,
    private val inContextDataStore: EmailProtectionInContextDataStore,
    private val recentInstallChecker: EmailProtectionInContextRecentInstallChecker,
) : AutofillWebMessageListener() {

    override val key: String
        get() = "ddgGetIncontextSignupDismissedAt"

    override fun onPostMessage(
        webView: WebView,
        message: WebMessageCompat,
        sourceOrigin: Uri,
        isMainFrame: Boolean,
        reply: JavaScriptReplyProxy,
    ) {
        kotlin.runCatching {
            job += appCoroutineScope.launch(dispatchers.io()) {
                reply.postMessage(generateResponse())
            }
        }.onFailure {
            Timber.e(it, "Error while processing autofill web message for %s", key)
        }
    }

    private suspend fun generateResponse(): String {
        val permanentDismissalTime = inContextDataStore.timestampUserChoseNeverAskAgain()
        val installedRecently = recentInstallChecker.isRecentInstall()
        return autofillResponseWriter.generateResponseForEmailProtectionInContextSignup(installedRecently, permanentDismissalTime)
    }
}
