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

    ONBOARDING_DAX_CTA_SHOWN("m_odc_s"),
    ONBOARDING_DAX_ALL_CTA_HIDDEN("m_odc_h"),
    ONBOARDING_DAX_CTA_OK_BUTTON("m_odc_ok"),
    ONBOARDING_DAX_CTA_CANCEL_BUTTON("m_onboarding_dax_cta_cancel"),

    BROWSER_MENU_ALLOWLIST_ADD("mb_wla"),
    BROWSER_MENU_ALLOWLIST_REMOVE("mb_wlr"),

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
    FAVORITE_WIDGET_CONFIGURATION_SHOWN(pixelName = "m_sfw_cs"),
    FAVORITES_WIDGETS_LIGHT(pixelName = "m_sfw_l"),
    FAVORITES_WIDGETS_DARK(pixelName = "m_sfw_dk"),
    FAVORITES_WIDGETS_SYSTEM(pixelName = "m_sfw_sd"),

    FAVORITES_ONBOARDING_CTA_SHOWN("m_fo_s"),
    FAVORITE_OMNIBAR_ITEM_PRESSED("m_fav_o"),
    FAVORITE_HOMETAB_ITEM_PRESSED("m_fav_ht"),
    FAVORITE_BOOKMARKS_ITEM_PRESSED("m_fav_b"),
    FAVORITE_SYSTEM_SEARCH_ITEM_PRESSED("m_fav_ss"),

    APP_NOTIFICATION_LAUNCH(pixelName = "m_n_l"),
    APP_WIDGET_LAUNCH(pixelName = "m_w_l"),
    APP_FAVORITES_SEARCHBAR_WIDGET_LAUNCH(pixelName = "m_sfbw_l"),
    APP_FAVORITES_ITEM_WIDGET_LAUNCH(pixelName = "m_sfiw_l"),
    APP_EMPTY_VIEW_WIDGET_LAUNCH(pixelName = "m_sfew_l"),
    APP_ASSIST_LAUNCH(pixelName = "m_a_l"),
    APP_SYSTEM_SEARCH_BOX_LAUNCH(pixelName = "m_ssb_l"),
    APP_THIRD_PARTY_LAUNCH(pixelName = "m_third_party_launch"),
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

    SETTINGS_OPENED("ms"),
    SETTINGS_THEME_OPENED("ms_t_o"),
    SETTINGS_THEME_TOGGLED_LIGHT("ms_tl"),
    SETTINGS_THEME_TOGGLED_DARK("ms_td"),
    SETTINGS_THEME_TOGGLED_SYSTEM_DEFAULT("ms_tsd"),
    SETTINGS_MANAGE_ALLOWLIST("ms_mw"),
    SETTINGS_DO_NOT_SELL_SHOWN("ms_dns"),
    SETTINGS_DO_NOT_SELL_ON("ms_dns_on"),
    SETTINGS_DO_NOT_SELL_OFF("ms_dns_off"),
    SETTINGS_NOTIFICATIONS_PRESSED("ms_notifications_setting_pressed"),
    SETTINGS_APP_LINKS_PRESSED("ms_app_links_setting_pressed"),
    SETTINGS_APP_LINKS_ASK_EVERY_TIME_SELECTED("ms_app_links_ask_every_time_setting_selected"),
    SETTINGS_APP_LINKS_ALWAYS_SELECTED("ms_app_links_always_setting_selected"),
    SETTINGS_APP_LINKS_NEVER_SELECTED("ms_app_links_never_setting_selected"),
    SETTINGS_ADD_HOME_SCREEN_WIDGET_CLICKED("ms_add_home_screen_widget_clicked"),
    SETTINGS_DEFAULT_BROWSER_PRESSED("ms_default_browser_pressed"),
    SETTINGS_PRIVATE_SEARCH_PRESSED("ms_private_search_setting_pressed"),
    SETTINGS_WEB_TRACKING_PROTECTION_PRESSED("ms_web_tracking_protection_setting_pressed"),
    SETTINGS_ACCESSIBILITY_PRESSED("ms_accessibility_setting_pressed"),
    SETTINGS_ABOUT_PRESSED("ms_about_setting_pressed"),
    SETTINGS_SYNC_PRESSED("ms_sync_pressed"),
    SETTINGS_PERMISSIONS_PRESSED("ms_permissions_setting_pressed"),
    SETTINGS_APPEARANCE_PRESSED("ms_appearance_setting_pressed"),
    SETTINGS_APP_ICON_PRESSED("ms_app_icon_setting_pressed"),
    SETTINGS_MAC_APP_PRESSED("ms_mac_app_setting_pressed"),
    SETTINGS_WINDOWS_APP_PRESSED("ms_windows_app_setting_pressed"),
    SETTINGS_EMAIL_PROTECTION_PRESSED("ms_email_protection_setting_pressed"),
    SETTINGS_APPTP_PRESSED("ms_apptp_setting_pressed"),
    SETTINGS_NETP_PRESSED("ms_netp_setting_pressed"),
    SETTINGS_GPC_PRESSED("ms_gpc_pressed"),
    SETTINGS_FIREPROOF_WEBSITES_PRESSED("ms_fireproof_websites_pressed"),
    SETTINGS_AUTOMATICALLY_CLEAR_WHAT_PRESSED("ms_automatically_clear_what_pressed"),
    SETTINGS_AUTOMATICALLY_CLEAR_WHEN_PRESSED("ms_automatically_clear_when_pressed"),
    SETTINGS_SITE_PERMISSIONS_PRESSED("ms_site_permissions_pressed"),
    SETTINGS_ABOUT_DDG_LEARN_MORE_PRESSED("ms_about_ddg_learn_more_pressed"),
    SETTINGS_ABOUT_DDG_PRIVACY_POLICY_PRESSED("ms_about_ddg_privacy_policy_pressed"),
    SETTINGS_ABOUT_DDG_VERSION_EASTER_EGG_PRESSED("ms_about_ddg_version_easter_egg_pressed"),
    SETTINGS_ABOUT_DDG_SHARE_FEEDBACK_PRESSED("ms_about_ddg_share_feedback_pressed"),
    SETTINGS_ABOUT_DDG_NETP_UNLOCK_PRESSED("ms_about_ddg_netp_unlock_pressed"),
    SETTINGS_PRIVATE_SEARCH_MORE_SEARCH_SETTINGS_PRESSED("ms_private_search_more_search_settings_pressed"),
    SETTINGS_COOKIE_POPUP_PROTECTION_PRESSED("ms_cookie_popup_protection_setting_pressed"),
    SETTINGS_FIRE_BUTTON_PRESSED("ms_fire_button_setting_pressed"),

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

    APP_LINKS_SNACKBAR_SHOWN("m_app_links_snackbar_shown"),
    APP_LINKS_SNACKBAR_OPEN_ACTION_PRESSED("m_app_links_snackbar_open_action_pressed"),

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
    MENU_ACTION_EDIT_BOOKMARK_PRESSED("m_nav_eb_p"),
    MENU_ACTION_ADD_FAVORITE_PRESSED("m_nav_af_p"),
    MENU_ACTION_REMOVE_FAVORITE_PRESSED("m_nav_rf_p"),
    MENU_ACTION_SHARE_PRESSED("m_nav_sh_p"),
    MENU_ACTION_FIND_IN_PAGE_PRESSED("m_nav_fip_p"),
    MENU_ACTION_PRINT_PRESSED("m_nav_print_menu_item_pressed"),
    MENU_ACTION_ADD_TO_HOME_PRESSED("m_nav_ath_p"),
    MENU_ACTION_DESKTOP_SITE_ENABLE_PRESSED("m_nav_dse_p"),
    MENU_ACTION_DESKTOP_SITE_DISABLE_PRESSED("m_nav_dsd_p"),
    MENU_ACTION_REPORT_BROKEN_SITE_PRESSED("m_nav_rbs_p"),
    MENU_ACTION_SETTINGS_PRESSED("m_nav_s_p"),
    MENU_ACTION_APP_LINKS_OPEN_PRESSED("m_nav_app_links_open_menu_item_pressed"),
    MENU_ACTION_DOWNLOADS_PRESSED("m_nav_downloads_menu_item_pressed"),

    FIREPROOF_WEBSITE_ADDED("m_fw_a"),
    FIREPROOF_WEBSITE_REMOVE("m_fw_r"),
    FIREPROOF_LOGIN_DIALOG_SHOWN("m_fw_ld_s"),
    FIREPROOF_WEBSITE_LOGIN_ADDED("m_fw_l_a"),
    FIREPROOF_WEBSITE_LOGIN_DISMISS("m_fw_l_d"),
    FIREPROOF_WEBSITE_DELETED("m_fw_d"),
    FIREPROOF_WEBSITE_ALL_DELETED("m_fireproof_website_all_deleted"),
    FIREPROOF_WEBSITE_UNDO("m_fw_u"),
    FIREPROOF_REMOVE_WEBSITE_UNDO("m_remove_fireproofing_snackbar_undo"),
    FIREPROOF_LOGIN_DISABLE_DIALOG_SHOWN("m_fw_dd_s"),
    FIREPROOF_LOGIN_DISABLE_DIALOG_DISABLE("m_fw_dd_d"),
    FIREPROOF_LOGIN_DISABLE_DIALOG_CANCEL("m_fw_dd_c"),
    FIREPROOF_SETTING_SELECTION_ALWAYS("m_fireproof_setting_selection_always"),
    FIREPROOF_SETTING_SELECTION_NEVER("m_fireproof_setting_selection_never"),
    FIREPROOF_SETTING_SELECTION_ASK_EVERYTIME("m_fireproof_setting_selection_ask_every_time"),
    FIREPROOF_AUTOMATIC_DIALOG_ALWAYS("m_fireproof_automatic_dialog_always"),
    FIREPROOF_AUTOMATIC_DIALOG_FIREPROOF_SITE("m_fireproof_automatic_dialog_fireproof_site"),
    FIREPROOF_AUTOMATIC_DIALOG_NOT_NOW("m_fireproof_automatic_dialog_not_now"),

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

    EMAIL_ENABLED("email_enabled"),
    EMAIL_DISABLED("email_disabled"),
    DUCK_EMAIL_OVERRIDE_PIXEL("m_sync_duck_address_override"),

    EMAIL_COPIED_TO_CLIPBOARD("email_generated_button"),
    EMAIL_DID_SHOW_WAITLIST_DIALOG("email_did_show_waitlist_dialog"),
    EMAIL_DID_PRESS_WAITLIST_DIALOG_NOTIFY_ME("email_did_press_waitlist_dialog_notify_me"),
    EMAIL_DID_PRESS_WAITLIST_DIALOG_NO_THANKS("email_did_press_waitlist_dialog_dismiss"),

    ENCRYPTED_IO_EXCEPTION("m_e_io_e"),
    ENCRYPTED_GENERAL_EXCEPTION("m_e_g_e"),

    REMOTE_MESSAGE_DISMISSED("m_remote_message_dismissed"),
    REMOTE_MESSAGE_SHOWN("m_remote_message_shown"),
    REMOTE_MESSAGE_SHOWN_UNIQUE("m_remote_message_shown_unique"),
    REMOTE_MESSAGE_PRIMARY_ACTION_CLICKED("m_remote_message_primary_action_clicked"),
    REMOTE_MESSAGE_SECONDARY_ACTION_CLICKED("m_remote_message_secondary_action_clicked"),
    REMOTE_MESSAGE_ACTION_CLICKED("m_remote_message_action_clicked"),
    REMOTE_MESSAGE_SHARED("m_remote_message_share"),
}
