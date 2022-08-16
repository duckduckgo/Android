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
import com.duckduckgo.autoconsent.api.AutoconsentCallback
import com.duckduckgo.autoconsent.impl.MessageHandlerPlugin
import com.duckduckgo.autoconsent.store.AutoconsentSettingsRepository
import com.duckduckgo.di.scopes.AppScope
import com.squareup.anvil.annotations.ContributesMultibinding
import javax.inject.Inject

@ContributesMultibinding(AppScope::class)
class PopUpFoundMessageHandlerPlugin @Inject constructor(private val repository: AutoconsentSettingsRepository) : MessageHandlerPlugin {

    override fun process(messageType: String, jsonString: String, webView: WebView, autoconsentCallback: AutoconsentCallback) {
        if (supportedTypes.contains(messageType)) {
            if (repository.userSetting) {
                return
            }
            autoconsentCallback.onFirstPopUpHandled()
        }
    }

    override val supportedTypes: List<String> = listOf("popupFound")
}
