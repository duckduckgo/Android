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

package com.duckduckgo.user.agent.impl.remoteconfig

data class ClientBrandHintDomain(
    val domain: String,
    val brand: ClientBrandsHints,
)

data class ClientBrandHintSettings(
    val domains: List<ClientBrandHintDomain>,
)

/** Public enum class for the client hint brand list */
enum class ClientBrandsHints {
    DDG, CHROME, WEBVIEW;

    fun getBrand(): String {
        return when (this) {
            DDG -> "DuckDuckGo"
            CHROME -> "Google Chrome"
            WEBVIEW -> "Android WebView"
        }
    }

    companion object {
        fun from(name: String): ClientBrandsHints {
            return when (name) {
                DDG.name -> DDG
                CHROME.name -> CHROME
                WEBVIEW.name -> WEBVIEW
                else -> DDG
            }
        }
    }
}

sealed class BrandingChange {
    data object None : BrandingChange()
    data class Change(val branding: ClientBrandsHints) : BrandingChange()
}
