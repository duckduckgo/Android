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
import com.duckduckgo.autoconsent.impl.adapters.JSONObjectAdapter
import com.duckduckgo.autoconsent.impl.pixels.AutoConsentPixel
import com.duckduckgo.autoconsent.impl.pixels.AutoconsentPixelManager
import com.duckduckgo.autoconsent.impl.store.AutoconsentSettingsRepository
import com.duckduckgo.di.scopes.AppScope
import com.squareup.anvil.annotations.ContributesMultibinding
import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.Moshi
import logcat.logcat
import javax.inject.Inject

@ContributesMultibinding(AppScope::class)
class PopUpFoundMessageHandlerPlugin @Inject constructor(
    private val repository: AutoconsentSettingsRepository,
    private val pixelManager: AutoconsentPixelManager,
) : MessageHandlerPlugin {

    private val moshi = Moshi.Builder().add(JSONObjectAdapter()).build()

    override fun process(messageType: String, jsonString: String, webView: WebView, autoconsentCallback: AutoconsentCallback) {
        try {
            if (supportedTypes.contains(messageType)) {
                val message: PopUpFoundMessage = parseMessage(jsonString) ?: return

                pixelManager.fireDailyPixel(AutoConsentPixel.AUTOCONSENT_POPUP_FOUND_DAILY)

                if (repository.userSetting) return
                if (message.cmp.endsWith(IGNORE_CMP_SUFFIX, ignoreCase = true)) return

                autoconsentCallback.onFirstPopUpHandled()
            }
        } catch (e: Exception) {
            logcat { e.localizedMessage }
        }
    }

    override val supportedTypes: List<String> = listOf("popupFound")

    private fun parseMessage(jsonString: String): PopUpFoundMessage? {
        val jsonAdapter: JsonAdapter<PopUpFoundMessage> = moshi.adapter(PopUpFoundMessage::class.java)
        return jsonAdapter.fromJson(jsonString)
    }

    data class PopUpFoundMessage(val type: String, val cmp: String, val url: String)

    companion object {
        private const val IGNORE_CMP_SUFFIX = "-top"
    }
}
