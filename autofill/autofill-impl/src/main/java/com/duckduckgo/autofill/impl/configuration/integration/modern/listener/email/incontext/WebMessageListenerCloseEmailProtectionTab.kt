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
import androidx.webkit.WebViewCompat.WebMessageListener
import com.duckduckgo.app.di.AppCoroutineScope
import com.duckduckgo.autofill.impl.configuration.integration.modern.listener.AutofillWebMessageListener.Companion.duckDuckGoOriginOnly
import com.duckduckgo.autofill.impl.configuration.integration.modern.listener.email.EmailProtectionUrl
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.di.scopes.FragmentScope
import com.squareup.anvil.annotations.ContributesMultibinding
import dagger.SingleInstanceIn
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

@SingleInstanceIn(FragmentScope::class)
@ContributesMultibinding(FragmentScope::class)
@SuppressLint("RequiresFeature")
class WebMessageListenerCloseEmailProtectionTab @Inject constructor(
    @AppCoroutineScope private val appCoroutineScope: CoroutineScope,
    private val dispatchers: DispatcherProvider,
) : WebMessageListener {

    lateinit var callback: CloseEmailProtectionTabCallback

    val key: String
        get() = "ddgCloseEmailProtectionTab"

    val origins: Set<String>
        get() = duckDuckGoOriginOnly

    override fun onPostMessage(
        webView: WebView,
        message: WebMessageCompat,
        sourceOrigin: Uri,
        isMainFrame: Boolean,
        reply: JavaScriptReplyProxy,
    ) {
        if (!EmailProtectionUrl.isEmailProtectionUrl(webView.url)) return

        appCoroutineScope.launch(dispatchers.io()) {
            callback.closeNativeInContextEmailProtectionSignup()
        }
    }

    interface CloseEmailProtectionTabCallback {
        suspend fun closeNativeInContextEmailProtectionSignup()
    }
}
