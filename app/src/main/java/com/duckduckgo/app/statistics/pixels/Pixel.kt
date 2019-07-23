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

import com.duckduckgo.app.global.AppUrl
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

        ONBOARDING_DEFAULT_BROWSER_SETTINGS_LAUNCHED("m_odb_l"),
        ONBOARDING_DEFAULT_BROWSER_SKIPPED("m_odb_s"),

        ONBOARDING_TRACKER_BLOCKING_USER_OPTED_IN("m_otbi_y"),
        ONBOARDING_TRACKER_BLOCKING_USER_DECLINED("m_otbi_n"),
        ONBOARDING_TRACKER_BLOCKING_OPT_OUT_DIALOG_CONTINUE_ANYWAY("m_otbd_c"),
        ONBOARDING_TRACKER_BLOCKING_OPT_OUT_DIALOG_CANCELLED("m_otbd_x"),
        ONBOARDING_TRACKER_BLOCKING_FINAL_ONBOARDING_STATE_ENABLED("m_otbf_y"),
        ONBOARDING_TRACKER_BLOCKING_FINAL_ONBOARDING_STATE_DISABLED("m_otbf_n"),

        PRIVACY_DASHBOARD_OPENED("mp"),
        PRIVACY_DASHBOARD_SCORECARD("mp_c"),
        PRIVACY_DASHBOARD_ENCRYPTION("mp_e"),
        PRIVACY_DASHBOARD_GLOBAL_STATS("mp_s"),
        PRIVACY_DASHBOARD_PRIVACY_PRACTICES("mp_p"),
        PRIVACY_DASHBOARD_NETWORKS("mp_n"),

        TRACKER_BLOCKER_HISTORICAL_ON(pixelName = "m_tb_on_h"),
        TRACKER_BLOCKER_HISTORICAL_OFF(pixelName = "m_tb_off_h"),
        TRACKER_BLOCKER_DASHBOARD_TURNED_ON(pixelName = "m_tb_on_pd"),
        TRACKER_BLOCKER_DASHBOARD_TURNED_OFF(pixelName = "m_tb_off_pd"),

        DEFAULT_BROWSER_SET("m_db_s"),
        DEFAULT_BROWSER_UNSET("m_db_u"),
        WIDGETS_ADDED(pixelName = "m_w_a"),
        WIDGETS_DELETED(pixelName = "m_w_d"),
        WIDGET_LAUNCHED(pixelName = "m_w_l"),

        LONG_PRESS("mlp"),
        LONG_PRESS_DOWNLOAD_IMAGE("mlp_i"),
        LONG_PRESS_NEW_TAB("mlp_t"),
        LONG_PRESS_NEW_BACKGROUND_TAB("mlp_b"),
        LONG_PRESS_SHARE("mlp_s"),
        LONG_PRESS_COPY_URL("mlp_c"),
        LONG_PRESS_OPEN_IMAGE_IN_BACKGROUND_TAB("mlp_ibt"),

        SETTINGS_OPENED("ms"),
        SETTINGS_THEME_TOGGLED_LIGHT("ms_tl"),
        SETTINGS_THEME_TOGGLED_DARK("ms_td"),

        SURVEY_CTA_SHOWN(pixelName = "mus_cs"),
        SURVEY_CTA_DISMISSED(pixelName = "mus_cd"),
        SURVEY_CTA_LAUNCHED(pixelName = "mus_cl"),
        SURVEY_SURVEY_DISMISSED(pixelName = "mus_sd"),

        NOTIFICATION_SHOWN("mnot_s"),
        NOTIFICATION_LAUNCHED("mnot_l"),
        NOTIFICATION_CANCELLED("mnot_c"),

        NOTIFICATIONS_ENABLED("mnot_e"),
        NOTIFICATIONS_DISABLED("mnot_d"),

        AUTOMATIC_CLEAR_DATA_WHAT_SHOWN("macwhat_s"),
        AUTOMATIC_CLEAR_DATA_WHAT_OPTION_NONE("macwhat_n"),
        AUTOMATIC_CLEAR_DATA_WHAT_OPTION_TABS("macwhat_t"),
        AUTOMATIC_CLEAR_DATA_WHAT_OPTION_TABS_AND_DATA("macwhat_d"),

        AUTOMATIC_CLEAR_DATA_WHEN_SHOWN("macwhen_s"),
        AUTOMATIC_CLEAR_DATA_WHEN_OPTION_APP_EXIT_ONLY("macwhen_x"),
        AUTOMATIC_CLEAR_DATA_WHEN_OPTION_APP_EXIT_OR_5_MINS("macwhen_5"),
        AUTOMATIC_CLEAR_DATA_WHEN_OPTION_APP_EXIT_OR_15_MINS("macwhen_15"),
        AUTOMATIC_CLEAR_DATA_WHEN_OPTION_APP_EXIT_OR_30_MINS("macwhen_30"),
        AUTOMATIC_CLEAR_DATA_WHEN_OPTION_APP_EXIT_OR_60_MINS("macwhen_60"),

        APP_ENJOYMENT_DIALOG_SHOWN("mrp_e_d%d_ds"),
        APP_ENJOYMENT_DIALOG_USER_ENJOYING("mrp_e_d%d_y"),
        APP_ENJOYMENT_DIALOG_USER_NOT_ENJOYING("mrp_e_d%d_n"),
        APP_ENJOYMENT_DIALOG_USER_CANCELLED("mrp_e_d%d_c"),

        APP_RATING_DIALOG_SHOWN("mrp_r_d%d_ds"),
        APP_RATING_DIALOG_USER_GAVE_RATING("mrp_r_d%d_y"),
        APP_RATING_DIALOG_USER_DECLINED_RATING("mrp_r_d%d_n"),
        APP_RATING_DIALOG_USER_CANCELLED("mrp_r_d%d_c"),

        APP_FEEDBACK_DIALOG_SHOWN("mrp_f_d%d_ds"),
        APP_FEEDBACK_DIALOG_USER_GAVE_FEEDBACK("mrp_f_d%d_y"),
        APP_FEEDBACK_DIALOG_USER_DECLINED_FEEDBACK("mrp_f_d%d_n"),
        APP_FEEDBACK_DIALOG_USER_CANCELLED("mrp_f_d%d_c"),

        FEEDBACK_POSITIVE_SUBMISSION("mfbs_%s_submit"),
        FEEDBACK_NEGATIVE_SUBMISSION("mfbs_%s_%s_%s")
    }

    fun fire(pixel: PixelName, parameters: Map<String, String?> = emptyMap(), includeLocale: Boolean = false)
    fun fire(pixelName: String, parameters: Map<String, String?> = emptyMap(), includeLocale: Boolean = false)
    fun fireCompletable(pixelName: String, parameters: Map<String, String?>): Completable

}

class ApiBasedPixel @Inject constructor(
    private val api: PixelService,
    private val statisticsDataStore: StatisticsDataStore,
    private val variantManager: VariantManager,
    private val deviceInfo: DeviceInfo
) : Pixel {

    override fun fire(pixel: Pixel.PixelName, parameters: Map<String, String?>, includeLocale: Boolean) {
        fire(pixel.pixelName, parameters, includeLocale)
    }

    override fun fire(pixelName: String, parameters: Map<String, String?>, includeLocale: Boolean) {
        val locale = if (includeLocale) localeMap else emptyMap()

        fireCompletable(pixelName, parameters.plus(locale))
            .subscribeOn(Schedulers.io())
            .subscribe({
                Timber.v("Pixel sent: $pixelName")
            }, {
                Timber.w("Pixel failed: $pixelName")
            })
    }

    private val localeMap
        get() = mapOf(
            AppUrl.ParamKey.COUNTRY to deviceInfo.country,
            AppUrl.ParamKey.LANGUAGE to deviceInfo.language
        )

    override fun fireCompletable(pixelName: String, parameters: Map<String, String?>): Completable {
        val atb = statisticsDataStore.atb?.formatWithVariant(variantManager.getVariant()) ?: ""
        return api.fire(pixelName, deviceInfo.formFactor().description, atb, parameters)
    }
}
