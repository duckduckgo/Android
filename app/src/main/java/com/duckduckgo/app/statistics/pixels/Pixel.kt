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
import com.duckduckgo.app.statistics.pixels.Pixel.PixelName
import com.duckduckgo.app.statistics.pixels.Pixel.PixelParameter
import com.duckduckgo.app.statistics.store.StatisticsDataStore
import io.reactivex.Completable
import io.reactivex.schedulers.Schedulers
import timber.log.Timber
import javax.inject.Inject

interface Pixel {

    enum class PixelName(val pixelName: String) {

        APP_LAUNCH("ml"),

        FORGET_ALL_PRESSED_BROWSING("mf_bp"),
        FORGET_ALL_PRESSED_TABSWITCHING("mf_tp"),
        FORGET_ALL_EXECUTED("mf"),

        APPLICATION_CRASH("m_d_ac"),
        APPLICATION_CRASH_GLOBAL("m_d_ac_g"),
        APPLICATION_CRASH_WEBVIEW_SHOULD_INTERCEPT("m_d_ac_wi"),
        APPLICATION_CRASH_WEBVIEW_PAGE_STARTED("m_d_ac_wps"),
        APPLICATION_CRASH_WEBVIEW_PAGE_FINISHED("m_d_ac_wpf"),
        APPLICATION_CRASH_WEBVIEW_OVERRIDE_REQUEST("m_d_ac_wo"),
        APPLICATION_CRASH_WEBVIEW_HTTP_AUTH_REQUEST("m_d_ac_wh"),
        APPLICATION_CRASH_WEBVIEW_SHOW_CUSTOM_VIEW("m_d_ac_wcs"),
        APPLICATION_CRASH_WEBVIEW_HIDE_CUSTOM_VIEW("m_d_ac_wch"),
        APPLICATION_CRASH_WEBVIEW_ON_PROGRESS_CHANGED("m_d_ac_wpc"),
        APPLICATION_CRASH_WEBVIEW_RECEIVED_PAGE_TITLE("m_d_ac_wpt"),
        APPLICATION_CRASH_WEBVIEW_SHOW_FILE_CHOOSER("m_d_ac_wfc"),

        WEB_RENDERER_GONE_CRASH("m_d_wrg_c"),
        WEB_RENDERER_GONE_KILLED("m_d_wrg_k"),
        BROKEN_SITE_REPORTED("m_bsr"),

        ONBOARDING_DEFAULT_BROWSER_VISUALIZED("m_odb_v"),
        ONBOARDING_DEFAULT_BROWSER_LAUNCHED("m_odb_l"),
        ONBOARDING_DEFAULT_BROWSER_SKIPPED("m_odb_s"),
        ONBOARDING_DEFAULT_BROWSER_SELECTED_JUST_ONCE("m_odb_jo"),

        ONBOARDING_DAX_CTA_SHOWN("m_odc_s"),
        ONBOARDING_DAX_ALL_CTA_HIDDEN("m_odc_h"),
        ONBOARDING_DAX_CTA_OK_BUTTON("m_odc_ok"),

        PRIVACY_DASHBOARD_OPENED("mp"),
        PRIVACY_DASHBOARD_SCORECARD("mp_c"),
        PRIVACY_DASHBOARD_ENCRYPTION("mp_e"),
        PRIVACY_DASHBOARD_GLOBAL_STATS("mp_s"),
        PRIVACY_DASHBOARD_PRIVACY_PRACTICES("mp_p"),
        PRIVACY_DASHBOARD_NETWORKS("mp_n"),

        HTTPS_NO_LOOKUP("m_https_nl"),
        HTTPS_LOCAL_UPGRADE("m_https_lu"),
        HTTPS_SERVICE_REQUEST_UPGRADE("m_https_sru"),
        HTTPS_SERVICE_CACHE_UPGRADE("m_https_scu"),
        HTTPS_SERVICE_REQUEST_NO_UPGRADE("m_https_srn"),
        HTTPS_SERVICE_CACHE_NO_UPGRADE("m_https_scn"),

        TRACKER_BLOCKER_DASHBOARD_TURNED_ON(pixelName = "m_tb_on_pd"),
        TRACKER_BLOCKER_DASHBOARD_TURNED_OFF(pixelName = "m_tb_off_pd"),

        DEFAULT_BROWSER_SET("m_db_s"),
        DEFAULT_BROWSER_NOT_SET("m_db_ns"),
        DEFAULT_BROWSER_UNSET("m_db_u"),

        WIDGET_CTA_SHOWN("m_wc_s"),
        WIDGET_CTA_LAUNCHED("m_wc_l"),
        WIDGET_CTA_DISMISSED("m_wc_d"),
        WIDGET_LEGACY_CTA_SHOWN("m_wlc_s"),
        WIDGET_LEGACY_CTA_LAUNCHED("m_wlc_l"),
        WIDGET_LEGACY_CTA_DISMISSED("m_wlc_d"),
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
        FEEDBACK_NEGATIVE_SUBMISSION("mfbs_%s_%s_%s"),

        AUTOCOMPLETE_BOOKMARK_SELECTION("m_aut_s_b"),
        AUTOCOMPLETE_SEARCH_SELECTION("m_aut_s_s")
    }

    object PixelParameter {
        const val APP_VERSION = "appVersion"
        const val URL = "url"
        const val COUNT = "count"
        const val EXCEPTION_MESSAGE = "m"
        const val BOOKMARK_CAPABLE = "bc"
        const val SHOWED_BOOKMARKS = "sb"
        const val DEFAULT_BROWSER_BEHAVIOUR_TRIGGERED = "bt"
        const val DEFAULT_BROWSER_SET_FROM_ONBOARDING = "fo"
        const val DEFAULT_BROWSER_SET_ORIGIN = "dbo"
        const val CTA_SHOWN = "cta"
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
        const val DAX_NETWORK_CTA_2 = "n2"
        const val DAX_TRACKERS_BLOCKED_CTA = "t"
        const val DAX_NO_TRACKERS_CTA = "nt"
    }

    fun fire(pixel: PixelName, parameters: Map<String, String?> = emptyMap())
    fun fire(pixelName: String, parameters: Map<String, String?> = emptyMap())
    fun fireCompletable(pixelName: String, parameters: Map<String, String?>): Completable
}

class ApiBasedPixel @Inject constructor(
    private val api: PixelService,
    private val statisticsDataStore: StatisticsDataStore,
    private val variantManager: VariantManager,
    private val deviceInfo: DeviceInfo
) : Pixel {

    override fun fire(pixel: PixelName, parameters: Map<String, String?>) {
        fire(pixel.pixelName, parameters)
    }

    override fun fire(pixelName: String, parameters: Map<String, String?>) {
        fireCompletable(pixelName, parameters)
            .subscribeOn(Schedulers.io())
            .subscribe({
                Timber.v("Pixel sent: $pixelName with params: $parameters")
            }, {
                Timber.w("Pixel failed: $pixelName with params: $parameters")
            })
    }

    override fun fireCompletable(pixelName: String, parameters: Map<String, String?>): Completable {
        val defaultParameters = mapOf(PixelParameter.APP_VERSION to deviceInfo.appVersion)
        val fullParameters = defaultParameters.plus(parameters)
        val atb = statisticsDataStore.atb?.formatWithVariant(variantManager.getVariant()) ?: ""
        return api.fire(pixelName, deviceInfo.formFactor().description, atb, fullParameters)
    }
}
