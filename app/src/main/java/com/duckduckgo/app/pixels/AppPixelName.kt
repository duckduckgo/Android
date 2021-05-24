/*
 * Copyright (c) 2021 DuckDuckGo
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

package com.duckduckgo.app.pixels

import com.duckduckgo.app.statistics.pixels.Pixel

enum class AppPixelName(override val pixelName: String) : Pixel.PixelName {
    APP_LAUNCH("ml"),

    FORGET_ALL_PRESSED_BROWSING("mf_bp"),
    FORGET_ALL_PRESSED_TABSWITCHING("mf_tp"),
    FORGET_ALL_EXECUTED("mf"),
    FORGET_ALL_AUTO_RESTART("m_f_r"),
    FORGET_ALL_AUTO_RESTART_WITH_INTENT("m_f_ri"),

    BROKEN_SITE_REPORTED("m_bsr"),
    BROKEN_SITE_REPORT("epbf"),

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
    PRIVACY_DASHBOARD_WHITELIST_ADD("mp_wla"),
    PRIVACY_DASHBOARD_WHITELIST_REMOVE("mp_wlr"),
    PRIVACY_DASHBOARD_MANAGE_WHITELIST("mp_mw"),
    PRIVACY_DASHBOARD_REPORT_BROKEN_SITE("mp_rb"),

    BROWSER_MENU_WHITELIST_ADD("mb_wla"),
    BROWSER_MENU_WHITELIST_REMOVE("mb_wlr"),

    DEFAULT_BROWSER_SET("m_db_s"),
    DEFAULT_BROWSER_NOT_SET("m_db_ns"),
    DEFAULT_BROWSER_UNSET("m_db_u"),
    DEFAULT_BROWSER_DIALOG_NOT_SHOWN("m_dbd_ns"),

    WIDGET_CTA_SHOWN("m_wc_s"),
    WIDGET_CTA_LAUNCHED("m_wc_l"),
    WIDGET_CTA_DISMISSED("m_wc_d"),
    WIDGET_LEGACY_CTA_SHOWN("m_wlc_s"),
    WIDGET_LEGACY_CTA_LAUNCHED("m_wlc_l"),
    WIDGET_LEGACY_CTA_DISMISSED("m_wlc_d"),
    WIDGETS_ADDED(pixelName = "m_w_a"),
    WIDGETS_DELETED(pixelName = "m_w_d"),

    APP_NOTIFICATION_LAUNCH(pixelName = "m_n_l"),
    APP_WIDGET_LAUNCH(pixelName = "m_w_l"),
    APP_ASSIST_LAUNCH(pixelName = "m_a_l"),
    APP_SYSTEM_SEARCH_BOX_LAUNCH(pixelName = "m_ssb_l"),
    INTERSTITIAL_LAUNCH_BROWSER_QUERY(pixelName = "m_i_lbq"),
    INTERSTITIAL_LAUNCH_DEVICE_APP(pixelName = "m_i_sda"),
    INTERSTITIAL_LAUNCH_DAX(pixelName = "m_i_ld"),
    INTERSTITIAL_ONBOARDING_SHOWN(pixelName = "m_io_s"),
    INTERSTITIAL_ONBOARDING_DISMISSED(pixelName = "m_io_d"),
    INTERSTITIAL_ONBOARDING_LESS_PRESSED(pixelName = "m_io_l"),
    INTERSTITIAL_ONBOARDING_MORE_PRESSED(pixelName = "m_io_m"),

    LONG_PRESS("mlp"),
    LONG_PRESS_DOWNLOAD_IMAGE("mlp_i"),
    LONG_PRESS_NEW_TAB("mlp_t"),
    LONG_PRESS_NEW_BACKGROUND_TAB("mlp_b"),
    LONG_PRESS_SHARE("mlp_s"),
    LONG_PRESS_COPY_URL("mlp_c"),
    LONG_PRESS_OPEN_IMAGE_IN_BACKGROUND_TAB("mlp_ibt"),

    DOWNLOAD_FILE_DEFAULT_GUESSED_NAME("m_df_dgn"),

    SETTINGS_OPENED("ms"),
    SETTINGS_THEME_TOGGLED_LIGHT("ms_tl"),
    SETTINGS_THEME_TOGGLED_DARK("ms_td"),
    SETTINGS_MANAGE_WHITELIST("ms_mw"),
    SETTINGS_DO_NOT_SELL_SHOWN("ms_dns"),
    SETTINGS_DO_NOT_SELL_ON("ms_dns_on"),
    SETTINGS_DO_NOT_SELL_OFF("ms_dns_off"),

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
    AUTOCOMPLETE_SEARCH_SELECTION("m_aut_s_s"),

    SERP_REQUERY("rq_%s"),

    CHANGE_APP_ICON_OPENED("m_ic"),

    MENU_ACTION_POPUP_OPENED("m_nav_pm_o"),
    MENU_ACTION_FIRE_PRESSED("m_nav_f_p"),
    MENU_ACTION_REFRESH_PRESSED("m_nav_r_p"),
    MENU_ACTION_NEW_TAB_PRESSED("m_nav_nt_p"),
    MENU_ACTION_BOOKMARKS_PRESSED("m_nav_b_p"),
    MENU_ACTION_NAVIGATE_FORWARD_PRESSED("m_nav_nf_p"),
    MENU_ACTION_NAVIGATE_BACK_PRESSED("m_nav_nb_p"),
    MENU_ACTION_ADD_BOOKMARK_PRESSED("m_nav_ab_p"),
    MENU_ACTION_SHARE_PRESSED("m_nav_sh_p"),
    MENU_ACTION_FIND_IN_PAGE_PRESSED("m_nav_fip_p"),
    MENU_ACTION_ADD_TO_HOME_PRESSED("m_nav_ath_p"),
    MENU_ACTION_DESKTOP_SITE_ENABLE_PRESSED("m_nav_dse_p"),
    MENU_ACTION_DESKTOP_SITE_DISABLE_PRESSED("m_nav_dsd_p"),
    MENU_ACTION_REPORT_BROKEN_SITE_PRESSED("m_nav_rbs_p"),
    MENU_ACTION_SETTINGS_PRESSED("m_nav_s_p"),

    COOKIE_DATABASE_EXCEPTION_OPEN_ERROR("m_cdb_e_oe"),
    COOKIE_DATABASE_EXCEPTION_DELETE_ERROR("m_cdb_e_de"),

    FIREPROOF_WEBSITE_ADDED("m_fw_a"),
    FIREPROOF_WEBSITE_REMOVE("m_fw_r"),
    FIREPROOF_LOGIN_DIALOG_SHOWN("m_fw_ld_s"),
    FIREPROOF_WEBSITE_LOGIN_ADDED("m_fw_l_a"),
    FIREPROOF_WEBSITE_LOGIN_DISMISS("m_fw_l_d"),
    FIREPROOF_WEBSITE_DELETED("m_fw_d"),
    FIREPROOF_LOGIN_TOGGLE_ENABLED("m_fw_d_e"),
    FIREPROOF_LOGIN_TOGGLE_DISABLED("m_fw_d_d"),
    FIREPROOF_WEBSITE_UNDO("m_fw_u"),
    FIREPROOF_LOGIN_DISABLE_DIALOG_SHOWN("m_fw_dd_s"),
    FIREPROOF_LOGIN_DISABLE_DIALOG_DISABLE("m_fw_dd_d"),
    FIREPROOF_LOGIN_DISABLE_DIALOG_CANCEL("m_fw_dd_c"),

    SHORTCUT_ADDED("m_sho_a"),
    SHORTCUT_OPENED("m_sho_o"),

    PRECISE_LOCATION_SYSTEM_DIALOG_ENABLE("m_pc_syd_e"),
    PRECISE_LOCATION_SYSTEM_DIALOG_LATER("m_pc_syd_l"),
    PRECISE_LOCATION_SYSTEM_DIALOG_NEVER("m_pc_syd_n"),
    PRECISE_LOCATION_SETTINGS_LOCATION_PERMISSION_ENABLE("m_pc_s_l_e"),
    PRECISE_LOCATION_SETTINGS_LOCATION_PERMISSION_DISABLE("m_pc_s_l_d"),
    PRECISE_LOCATION_SITE_DIALOG_ALLOW_ALWAYS("m_pc_sd_aa"),
    PRECISE_LOCATION_SITE_DIALOG_ALLOW_ONCE("m_pc_sd_ao"),
    PRECISE_LOCATION_SITE_DIALOG_DENY_ALWAYS("m_pc_sd_da"),
    PRECISE_LOCATION_SITE_DIALOG_DENY_ONCE("m_pc_sd_do"),

    FIRE_DIALOG_PROMOTED_CLEAR_PRESSED("m_fdp_p"),
    FIRE_DIALOG_CLEAR_PRESSED("m_fd_p"),
    FIRE_DIALOG_PROMOTED_CANCEL("m_fdp_c"),
    FIRE_DIALOG_CANCEL("m_fd_c"),
    FIRE_DIALOG_ANIMATION("m_fd_a"),

    FIRE_ANIMATION_SETTINGS_OPENED("m_fas_o"),
    FIRE_ANIMATION_NEW_SELECTED("m_fas_s"),

    EMAIL_TOOLTIP_DISMISSED("m_e_t_d"),
    EMAIL_USE_ALIAS("m_e_ua"),
    EMAIL_USE_ADDRESS("m_e_uad"),

    FAVICON_WRONG_URL_ERROR("m_fwu_r"),

    BOOKMARK_IMPORT_SUCCESS("m_bi_s"),
    BOOKMARK_IMPORT_ERROR("m_bi_e"),
    BOOKMARK_EXPORT_SUCCESS("m_be_a"),
    BOOKMARK_EXPORT_ERROR("m_be_e"),

    ENCRYPTED_IO_EXCEPTION("m_e_io_e"),
    ENCRYPTED_GENERAL_EXCEPTION("m_e_g_e")
}
