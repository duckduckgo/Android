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

package com.duckduckgo.app.statistics.pixels

import com.duckduckgo.app.statistics.pixels.Pixel.PixelType.Count

/** Primary interface for sending anonymous analytics events (pixels). */
interface Pixel {

    interface PixelName {
        val pixelName: String
    }

    object PixelParameter {
        const val APP_VERSION = "appVersion"
        const val URL = "url"
        const val BOOKMARK_CAPABLE = "bc"
        const val FAVORITE_CAPABLE = "fc"
        const val HISTORY_CAPABLE = "hc"
        const val SWITCH_TO_TAB_CAPABLE = "switch_to_tab_capable"
        const val SHOWED_BOOKMARKS = "sb"
        const val SHOWED_FAVORITES = "sf"
        const val SHOWED_HISTORY = "sh"
        const val SHOWED_SWITCH_TO_TAB = "showed_switch_to_tab"
        const val DEFAULT_BROWSER_BEHAVIOUR_TRIGGERED = "bt"
        const val DEFAULT_BROWSER_SET_FROM_ONBOARDING = "fo"
        const val DEFAULT_BROWSER_SET_ORIGIN = "dbo"
        const val CTA_SHOWN = "cta"
        const val SERP_QUERY_CHANGED = "1"
        const val SERP_QUERY_NOT_CHANGED = "0"
        const val FIRE_BUTTON_STATE = "fb"
        const val FIRE_ANIMATION = "fa"
        const val FIRE_EXECUTED = "fe"
        const val BOOKMARK_COUNT = "bco"
        const val COHORT = "cohort"
        const val LAST_USED_DAY = "duck_address_last_used"
        const val WEBVIEW_VERSION = "webview_version"
        const val WEBVIEW_FULL_VERSION = "webview_full_version"
        const val OS_VERSION = "os_version"
        const val DEFAULT_BROWSER = "default_browser"
        const val EMAIL = "email"
        const val MESSAGE_SHOWN = "message"
        const val ACTION_SUCCESS = "success"
        const val SYNC = "sync"
        const val VOICE_SEARCH = "voice_search"
        const val LOCALE = "locale"
        const val FROM_ONBOARDING = "from_onboarding"
        const val ADDRESS_BAR = "address_bar"
        const val LAUNCH_SCREEN = "launch_screen"
        const val TAB_COUNT = "tab_count"
        const val TAB_ACTIVE_7D = "tab_active_7d"
        const val TAB_INACTIVE_1W = "tab_inactive_1w"
        const val TAB_INACTIVE_2W = "tab_inactive_2w"
        const val TAB_INACTIVE_3W = "tab_inactive_3w"
        const val TRACKERS_ANIMATION_SHOWN_DURING_ONBOARDING = "duringOnboarding"
        const val AFTER_CIRCLES_ANIMATION = "after_circles_animation"
        const val AFTER_BURST_ANIMATION = "after_burst_animation"
    }

    object PixelValues {
        const val DEFAULT_BROWSER_SETTINGS = "s"
        const val DEFAULT_BROWSER_DIALOG = "d"
        const val DEFAULT_BROWSER_DIALOG_DISMISSED = "dd"
        const val DEFAULT_BROWSER_JUST_ONCE_MAX = "jom"
        const val DEFAULT_BROWSER_EXTERNAL = "e"
        const val DAX_INITIAL_CTA = "i"
        const val DAX_INITIAL_VISIT_SITE_CTA = "visit_site"
        const val DAX_END_CTA = "e"
        const val DAX_ONBOARDING_END_CTA = "end"
        const val DAX_SERP_CTA = "s"
        const val DAX_NETWORK_CTA_1 = "n"
        const val DAX_TRACKERS_BLOCKED_CTA = "t"
        const val DAX_NO_TRACKERS_CTA = "nt"
        const val DAX_FIRE_DIALOG_CTA = "fd"
        const val DAX_AUTOCONSENT_CTA = "autoconsent"
        const val DAX_PRIVACY_PRO = "privacy_pro"
        const val FIRE_ANIMATION_INFERNO = "fai"
        const val FIRE_ANIMATION_AIRSTREAM = "faas"
        const val FIRE_ANIMATION_WHIRLPOOL = "fawp"
        const val FIRE_ANIMATION_NONE = "fann"
    }

    sealed class PixelType {

        /**
         * Pixel is a every-occurrence pixel. Sent every time fire() is invoked.
         */
        data object Count : PixelType()

        /**
         * Pixel is a first-in-day pixel. Subsequent attempts to fire such pixel on a given calendar day (UTC) will be ignored.
         * By default, the pixel name will be used to avoid resending the pixel again, you can override this by adding your own tag instead.
         */
        data class Daily(val tag: String? = null) : PixelType()

        /**
         * Pixel is a once-ever pixel. Subsequent attempts to fire such pixel will be ignored.
         * By default, the pixel name will be used to avoid resending the pixel again, you can override this by adding your own tag instead.
         */
        data class Unique(val tag: String? = null) : PixelType()
    }

    /**
     * Sends a pixel with the specified name and parameters.
     *
     * The operation is asynchronous, making this method safe to call from any thread.
     *
     * @param pixel The name of the pixel event to be sent.
     * @param parameters A map of parameters to be included with the pixel event. These parameters are URL-encoded before being sent.
     * @param encodedParameters A map of parameters that are already URL-encoded. Use this when the parameters are pre-encoded.
     * @param type The type of pixel event to be sent.
     */
    fun fire(
        pixel: PixelName,
        parameters: Map<String, String> = emptyMap(),
        encodedParameters: Map<String, String> = emptyMap(),
        type: PixelType = Count,
    )

    /**
     * Sends a pixel with the specified name and parameters.
     *
     * The operation is asynchronous, making this method safe to call from any thread.
     *
     * @param pixelName The name of the pixel event to be sent.
     * @param parameters A map of parameters to be included with the pixel event. These parameters are URL-encoded before being sent.
     * @param encodedParameters A map of parameters that are already URL-encoded. Use this when the parameters are pre-encoded.
     * @param type The type of pixel event to be sent.
     */
    fun fire(
        pixelName: String,
        parameters: Map<String, String> = emptyMap(),
        encodedParameters: Map<String, String> = emptyMap(),
        type: PixelType = Count,
    )

    /**
     * Sends a pixel with the specified name and parameters. Unlike the `fire()` method, this method also persists the pixel in the local database,
     * allowing it to be retried in case of network issues or other failures.
     *
     * The operation is asynchronous, making this method safe to call from any thread.
     *
     * @param pixel The name of the pixel event to be sent.
     * @param parameters A map of parameters to be included with the pixel event. These parameters are URL-encoded before being sent.
     * @param encodedParameters A map of parameters that are already URL-encoded. Use this when the parameters are pre-encoded.
     */
    fun enqueueFire(
        pixel: PixelName,
        parameters: Map<String, String> = emptyMap(),
        encodedParameters: Map<String, String> = emptyMap(),
    )

    /**
     * Sends a pixel with the specified name and parameters. Unlike the `fire()` method, this method also persists the pixel in the local database,
     * allowing it to be retried in case of network issues or other failures.
     *
     * The operation is asynchronous, making this method safe to call from any thread.
     *
     * @param pixelName The name of the pixel event to be sent.
     * @param parameters A map of parameters to be included with the pixel event. These parameters are URL-encoded before being sent.
     * @param encodedParameters A map of parameters that are already URL-encoded. Use this when the parameters are pre-encoded.
     */
    fun enqueueFire(
        pixelName: String,
        parameters: Map<String, String> = emptyMap(),
        encodedParameters: Map<String, String> = emptyMap(),
    )
}
