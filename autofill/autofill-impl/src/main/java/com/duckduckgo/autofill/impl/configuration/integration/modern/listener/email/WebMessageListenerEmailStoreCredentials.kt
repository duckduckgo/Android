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

package com.duckduckgo.autofill.impl.configuration.integration.modern.listener.email

import android.annotation.SuppressLint
import android.net.Uri
import android.webkit.WebView
import androidx.webkit.JavaScriptReplyProxy
import androidx.webkit.WebMessageCompat
import com.duckduckgo.app.di.AppCoroutineScope
import com.duckduckgo.autofill.api.email.EmailManager
import com.duckduckgo.autofill.impl.configuration.integration.modern.listener.AutofillWebMessageListener
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.di.scopes.FragmentScope
import com.squareup.anvil.annotations.ContributesMultibinding
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import dagger.SingleInstanceIn
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import timber.log.Timber

@SingleInstanceIn(FragmentScope::class)
@ContributesMultibinding(FragmentScope::class)
@SuppressLint("RequiresFeature")
class WebMessageListenerEmailStoreCredentials @Inject constructor(
    @AppCoroutineScope private val appCoroutineScope: CoroutineScope,
    private val dispatchers: DispatcherProvider,
    private val emailManager: EmailManager,
) : AutofillWebMessageListener() {

    override val key: String
        get() = "ddgEmailProtectionStoreUserData"

    override val origins: Set<String>
        get() = duckDuckGoOriginOnly

    private val moshi by lazy { Moshi.Builder().add(KotlinJsonAdapterFactory()).build() }
    private val requestParser by lazy { moshi.adapter(IncomingMessage::class.java) }

    override fun onPostMessage(
        webView: WebView,
        message: WebMessageCompat,
        sourceOrigin: Uri,
        isMainFrame: Boolean,
        reply: JavaScriptReplyProxy,
    ) {
        if (!EmailProtectionUrl.isEmailProtectionUrl(webView.url)) return

        appCoroutineScope.launch(dispatchers.io()) {
            parseIncomingMessage(message.data.toString())?.let {
                emailManager.storeCredentials(it.token, it.userName, it.cohort)
                Timber.i("Saved email protection credentials for user %s", it.userName)
            }
        }
    }

    private fun parseIncomingMessage(message: String): IncomingMessage? {
        return kotlin.runCatching {
            return requestParser.fromJson(message)
        }.onFailure { Timber.w(it, "Failed to parse incoming email protection save message") }.getOrNull()
    }

    private data class IncomingMessage(
        val token: String,
        val userName: String,
        val cohort: String,
    )
}
