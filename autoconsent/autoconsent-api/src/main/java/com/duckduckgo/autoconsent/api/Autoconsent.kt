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

package com.duckduckgo.autoconsent.api

import android.webkit.WebView

/** Public interface for the Autoconsent (CMP) feature */
interface Autoconsent {
    /**
     * This method injects the JS code needed to run autoconsent. It requires a [WebView] instance and the URL where the code will be injected.
     */
    fun injectAutoconsent(webView: WebView, url: String)

    /**
     * This method adds the JS interface for autoconsent to create a bridge between JS and our client.
     * It requires a [WebView] instance and an [AutoconsentCallback].
     */
    fun addJsInterface(webView: WebView, autoconsentCallback: AutoconsentCallback)

    /**
     * This method enables or disables autoconsent setting depending on the value passed.
     */
    fun changeSetting(setting: Boolean)

    /**
     * @return `true` if autoconsent was enabled by the user, `false` otherwise.
     */
    fun isSettingEnabled(): Boolean

    /**
     * @return `true` if autoconsent is enabled in remote config and enabled by the user, `false` otherwise.
     */
    fun isAutoconsentEnabled(): Boolean

    /**
     * This method sends and opt out message to autoconsent on the given [WebView] instance to set the opt out mode.
     */
    fun setAutoconsentOptOut(webView: WebView)

    /**
     * This method sets autoconsent to opt in mode.
     */
    fun setAutoconsentOptIn()

    /**
     * This method stores a value so autoconsent knows the first pop-up was already handled.
     */
    fun firstPopUpHandled()
}

/**
 * Public interface for the Autoconsent callback.
 * It is required to be implemented and passed when calling addJsInterface and provides a useful set of callbacks.
 */
interface AutoconsentCallback {
    /**
     * This method is called whenever a popup is handled for the first time.
     */
    fun onFirstPopUpHandled()

    /**
     * This method is called whenever a popup is handled but not for the first time.
     */
    fun onPopUpHandled(isCosmetic: Boolean)

    /**
     * This method is called whenever autoconsent has a result to be sent
     */
    fun onResultReceived(consentManaged: Boolean, optOutFailed: Boolean, selfTestFailed: Boolean, isCosmetic: Boolean?)
}

/** List of [AutoconsentFeatureName] that belong to the Autoconsent feature */
enum class AutoconsentFeatureName(val value: String) {
    Autoconsent("autoconsent"),
}
