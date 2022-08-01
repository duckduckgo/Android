/*
 * Copyright (c) 2022 DuckDuckGo
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

package com.duckduckgo.autoconsent.impl.handlers

import android.webkit.WebView
import androidx.fragment.app.DialogFragment
import com.duckduckgo.app.di.AppCoroutineScope
import com.duckduckgo.app.global.DispatcherProvider
import com.duckduckgo.autoconsent.api.AutoconsentCallback
import com.duckduckgo.autoconsent.impl.MessageHandlerPlugin
import com.duckduckgo.autoconsent.impl.adapters.JSONObjectAdapter
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.mobile.android.ui.view.DaxDialogListener
import com.duckduckgo.mobile.android.ui.view.TypewriterDaxDialog
import com.squareup.anvil.annotations.ContributesMultibinding
import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.Moshi
import dagger.SingleInstanceIn
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import javax.inject.Inject

@ContributesMultibinding(AppScope::class)
@SingleInstanceIn(AppScope::class)
class PopUpFoundMessageHandlerPlugin @Inject constructor(
    @AppCoroutineScope val appCoroutineScope: CoroutineScope,
    private val dispatcherProvider: DispatcherProvider
) : MessageHandlerPlugin {

    private val moshi = Moshi.Builder().add(JSONObjectAdapter()).build()

    override fun process(messageType: String, jsonString: String, webView: WebView, autoconsentCallback: AutoconsentCallback) {
        if (messageType == type) {
            if (true == false) { // ToDo if autoconsent already enabled do nothing
                return
            }
            appCoroutineScope.launch(dispatcherProvider.main()) {
                autoconsentCallback.onFirstPopUpHandled(getDialogFragment(webView))
            }
        }
    }

    override val type: String = "popupFound"

    fun getDialogFragment(webView: WebView): DialogFragment {
        val dialog = TypewriterDaxDialog.newInstance(
            daxText = "Looks like this site has a cookie consent pop-up. Want me to handle these for you? I can try to minimize cookies, maximize privacy, and hide pop-ups like these",
            primaryButtonText = "Manage Cookie Pop-ups",
            secondaryButtonText = "No Thanks",
            hideButtonText = "",
            showHideButton = false,
            dismissible = true
        )
        val listener = object : DaxDialogListener {
            override fun onDaxDialogDismiss() {
                // NOP
            }

            override fun onDaxDialogPrimaryCtaClick() {
                webView.evaluateJavascript("javascript:${ReplyHandler.constructReply("""{ "type": "optOut" }""")}", null)
            }

            override fun onDaxDialogSecondaryCtaClick() {
                // NOP
            }

            override fun onDaxDialogHideClick() {
                // NOP
            }
        }
        dialog.setDaxDialogListener(listener)
        return dialog.getDaxDialog()
    }

    private fun parseMessage(jsonString: String): PopUpFoundMessage? {
        val jsonAdapter: JsonAdapter<PopUpFoundMessage> = moshi.adapter(PopUpFoundMessage::class.java)
        return jsonAdapter.fromJson(jsonString)
    }

    data class PopUpFoundMessage(val type: String, val cmp: String, val url: String)
}
