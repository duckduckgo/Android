/*
 * Copyright (c) 2018 DuckDuckGo
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

import android.annotation.SuppressLint
import com.duckduckgo.app.statistics.api.PixelSender
import com.duckduckgo.di.scopes.AppScope
import com.squareup.anvil.annotations.ContributesBinding
import io.reactivex.schedulers.Schedulers
import javax.inject.Inject
import timber.log.Timber

interface Pixel {

    interface PixelName {
        val pixelName: String
    }

    enum class StatisticsPixelName(override val pixelName: String) : PixelName {
        APPLICATION_CRASH_GLOBAL("m_d_ac_g"),
        BROWSER_DAILY_ACTIVE_FEATURE_STATE("m_browser_feature_daily_active_user_d"),
    }

    object PixelParameter {
        const val APP_VERSION = "appVersion"
        const val URL = "url"
        const val BOOKMARK_CAPABLE = "bc"
        const val SHOWED_BOOKMARKS = "sb"
        const val DEFAULT_BROWSER_BEHAVIOUR_TRIGGERED = "bt"
        const val DEFAULT_BROWSER_SET_FROM_ONBOARDING = "fo"
        const val DEFAULT_BROWSER_SET_ORIGIN = "dbo"
        const val CTA_SHOWN = "cta"
        const val SERP_QUERY_CHANGED = "1"
        const val SERP_QUERY_NOT_CHANGED = "0"
        const val FIRE_BUTTON_STATE = "fb"
        const val FAVORITE_MENU_ITEM_STATE = "fmi"
        const val FIRE_ANIMATION = "fa"
        const val FIRE_EXECUTED = "fe"
        const val BOOKMARK_COUNT = "bco"
        const val COHORT = "cohort"
        const val LAST_USED_DAY = "duck_address_last_used"
        const val WEBVIEW_VERSION = "webview_version"
        const val OS_VERSION = "os_version"
        const val DEFAULT_BROWSER = "default_browser"
        const val EMAIL = "email"
        const val MESSAGE_SHOWN = "message"
        const val ACTION_SUCCESS = "success"
        const val SYNC = "sync"
    }

    object PixelValues {
        const val DEFAULT_BROWSER_SETTINGS = "s"
        const val DEFAULT_BROWSER_DIALOG = "d"
        const val DEFAULT_BROWSER_DIALOG_DISMISSED = "dd"
        const val DEFAULT_BROWSER_JUST_ONCE_MAX = "jom"
        const val DEFAULT_BROWSER_EXTERNAL = "e"
        const val DAX_INITIAL_CTA = "i"
        const val DAX_END_CTA = "e"
        const val DAX_SERP_CTA = "s"
        const val DAX_NETWORK_CTA_1 = "n"
        const val DAX_TRACKERS_BLOCKED_CTA = "t"
        const val DAX_NO_TRACKERS_CTA = "nt"
        const val DAX_FIRE_DIALOG_CTA = "fd"
        const val DAX_AUTOCONSENT_CTA = "autoconsent"

        const val FIRE_ANIMATION_INFERNO = "fai"
        const val FIRE_ANIMATION_AIRSTREAM = "faas"
        const val FIRE_ANIMATION_WHIRLPOOL = "fawp"
        const val FIRE_ANIMATION_NONE = "fann"
    }

    fun fire(
        pixel: PixelName,
        parameters: Map<String, String> = emptyMap(),
        encodedParameters: Map<String, String> = emptyMap(),
    )

    fun fire(
        pixelName: String,
        parameters: Map<String, String> = emptyMap(),
        encodedParameters: Map<String, String> = emptyMap(),
    )

    fun enqueueFire(
        pixel: PixelName,
        parameters: Map<String, String> = emptyMap(),
        encodedParameters: Map<String, String> = emptyMap(),
    )

    fun enqueueFire(
        pixelName: String,
        parameters: Map<String, String> = emptyMap(),
        encodedParameters: Map<String, String> = emptyMap(),
    )
}

@ContributesBinding(AppScope::class)
class RxBasedPixel @Inject constructor(
    private val pixelSender: PixelSender,
) : Pixel {
    override fun fire(
        pixel: Pixel.PixelName,
        parameters: Map<String, String>,
        encodedParameters: Map<String, String>,
    ) {
        fire(pixel.pixelName, parameters, encodedParameters)
    }

    @SuppressLint("CheckResult")
    override fun fire(
        pixelName: String,
        parameters: Map<String, String>,
        encodedParameters: Map<String, String>,
    ) {
        pixelSender
            .sendPixel(pixelName, parameters, encodedParameters)
            .subscribeOn(Schedulers.io())
            .subscribe(
                { Timber.v("Pixel sent: $pixelName with params: $parameters $encodedParameters") },
                {
                    Timber.w(
                        it,
                        "Pixel failed: $pixelName with params: $parameters $encodedParameters",
                    )
                },
            )
    }

    /**
     * Sends a pixel. If delivery fails, the pixel will be retried again in the future. As this
     * method stores the pixel to disk until successful delivery, check with privacy triage if the
     * pixel has additional parameters that they would want to validate.
     */
    override fun enqueueFire(
        pixel: Pixel.PixelName,
        parameters: Map<String, String>,
        encodedParameters: Map<String, String>,
    ) {
        enqueueFire(pixel.pixelName, parameters, encodedParameters)
    }

    @SuppressLint("CheckResult")
    /** See comment in {@link #enqueueFire(PixelName, Map<String, String>, Map<String, String>)}. */
    override fun enqueueFire(
        pixelName: String,
        parameters: Map<String, String>,
        encodedParameters: Map<String, String>,
    ) {
        pixelSender
            .enqueuePixel(pixelName, parameters, encodedParameters)
            .subscribeOn(Schedulers.io())
            .subscribe(
                {
                    Timber.v(
                        "Pixel enqueued: $pixelName with params: $parameters $encodedParameters",
                    )
                },
                {
                    Timber.w(
                        it,
                        "Pixel failed: $pixelName with params: $parameters $encodedParameters",
                    )
                },
            )
    }
}
