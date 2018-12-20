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

import com.duckduckgo.app.global.device.DeviceInfo
import com.duckduckgo.app.statistics.VariantManager
import com.duckduckgo.app.statistics.api.PixelService
import com.duckduckgo.app.statistics.store.StatisticsDataStore
import io.reactivex.Completable
import io.reactivex.schedulers.Schedulers
import timber.log.Timber
import javax.inject.Inject

interface Pixel {

    enum class PixelName(val pixelName: String) {

        APP_LAUNCH("ml"),
        FORGET_ALL_EXECUTED("mf"),

        PRIVACY_DASHBOARD_OPENED("mp"),
        PRIVACY_DASHBOARD_SCORECARD("mp_c"),
        PRIVACY_DASHBOARD_ENCRYPTION("mp_e"),
        PRIVACY_DASHBOARD_GLOBAL_STATS("mp_s"),
        PRIVACY_DASHBOARD_PRIVACY_PRACTICES("mp_p"),
        PRIVACY_DASHBOARD_NETWORKS("mp_n"),

        DEFAULT_BROWSER_INFO_VIEWED("mdb_v"),
        DEFAULT_BROWSER_SET("mdb_s"),
        DEFAULT_BROWSER_NOT_SET("mdb_n"),

        LONG_PRESS("mlp"),
        LONG_PRESS_DOWNLOAD_IMAGE("mlp_i"),
        LONG_PRESS_NEW_TAB("mlp_t"),
        LONG_PRESS_NEW_BACKGROUND_TAB("mlp_b"),
        LONG_PRESS_SHARE("mlp_s"),
        LONG_PRESS_COPY_URL("mlp_c"),

        SETTINGS_OPENED("ms"),
        SETTINGS_THEME_TOGGLED_LIGHT("ms_tl"),
        SETTINGS_THEME_TOGGLED_DARK("ms_td"),

        HTTPS_UPGRADE_SITE_ERROR("ehd"),
        HTTPS_UPGRADE_SITE_SUMMARY("ehs"),

        SURVEY_CTA_SHOWN(pixelName = "mus_cs"),
        SURVEY_CTA_DISMISSED(pixelName = "mus_cd"),
        SURVEY_CTA_LAUNCHED(pixelName = "mus_cl"),
        SURVEY_SURVEY_DISMISSED(pixelName = "mus_sd"),

        ADD_WIDGET_CTA_SHOWN(pixelName = "maw_cs"),
        ADD_WIDGET_CTA_DISMISSED(pixelName = "maw_cd"),
        ADD_WIDGET_CTA_LAUNCHED_LEGACY(pixelName = "maw_cll"),
        ADD_WIDGET_CTA_LAUNCHED_MODERN(pixelName = "maw_clm"),
        ADD_WIDGET_INSTALL_LEGACY(pixelName = "maw_il"),
        ADD_WIDGET_INSTALL_MODERN(pixelName = "maw_im")
    }

    object PixelParameter {
        const val URL = "url"
        const val ERROR_CODE = "error_code"
        const val TOTAL_COUNT = "total"
        const val FAILURE_COUNT = "failures"
        const val APP_VERSION = "app_version"
    }

    fun fire(pixel: PixelName, parameters: Map<String, String?> = emptyMap())

    fun fireCompletable(pixel: Pixel.PixelName, parameters: Map<String, String?>): Completable

}

class ApiBasedPixel @Inject constructor(
    private val api: PixelService,
    private val statisticsDataStore: StatisticsDataStore,
    private val variantManager: VariantManager,
    private val deviceInfo: DeviceInfo
) : Pixel {

    override fun fire(pixel: Pixel.PixelName, parameters: Map<String, String?>) {
        fireCompletable(pixel, parameters)
            .subscribeOn(Schedulers.io())
            .subscribe({
                Timber.v("Pixel sent: ${pixel.pixelName}")
            }, {
                Timber.w("Pixel failed: ${pixel.pixelName}", it)
            })
    }

    override fun fireCompletable(pixel: Pixel.PixelName, parameters: Map<String, String?>): Completable {
        val atb = statisticsDataStore.atb?.formatWithVariant(variantManager.getVariant()) ?: ""
        return api.fire(pixel.pixelName, deviceInfo.formFactor().description, atb, parameters)
    }
}
