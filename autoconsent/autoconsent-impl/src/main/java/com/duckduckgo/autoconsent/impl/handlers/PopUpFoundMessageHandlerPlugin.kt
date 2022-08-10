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
import com.duckduckgo.autoconsent.store.AutoconsentSettingsRepository
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.mobile.android.ui.view.DaxDialogListener
import com.duckduckgo.mobile.android.ui.view.TypewriterDaxDialog
import com.squareup.anvil.annotations.ContributesMultibinding
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import javax.inject.Inject

@ContributesMultibinding(AppScope::class)
class PopUpFoundMessageHandlerPlugin @Inject constructor(
    @AppCoroutineScope val appCoroutineScope: CoroutineScope,
    private val dispatcherProvider: DispatcherProvider,
    private val repository: AutoconsentSettingsRepository,
) : MessageHandlerPlugin {

    override fun process(messageType: String, jsonString: String, webView: WebView, autoconsentCallback: AutoconsentCallback) {
        if (supportedTypes.contains(messageType)) {
            if (repository.userSetting) {
                return
            }
            appCoroutineScope.launch(dispatcherProvider.main()) {
                repository.firstPopupHandled = true
                autoconsentCallback.onFirstPopUpHandled(getDialogFragment(webView), TAG)
            }
        }
    }

    override val supportedTypes: List<String> = listOf("popupFound")

    private fun getDialogFragment(webView: WebView): DialogFragment {
        val dialog = TypewriterDaxDialog.newInstance(
            daxText = "Looks like this site has a cookie consent pop-up. Want me to handle these for you?",
            primaryButtonText = "Manage Cookie Pop-ups",
            secondaryButtonText = "No Thanks",
            hideButtonText = "",
            showHideButton = false,
            dismissible = true
        )
        val listener = object : DaxDialogListener {
            override fun onDaxDialogDismiss() {
                // NOOP
            }

            override fun onDaxDialogPrimaryCtaClick() {
                repository.userSetting = true
                webView.evaluateJavascript("javascript:${ReplyHandler.constructReply("""{ "type": "optOut" }""")}", null)
            }

            override fun onDaxDialogSecondaryCtaClick() {
                repository.userSetting = false
            }

            override fun onDaxDialogHideClick() {
                // NOOP
            }
        }
        dialog.setDaxDialogListener(listener)
        return dialog.getDaxDialog()
    }

    companion object {
        const val TAG = "autoconsentDaxDialog"
    }
}
