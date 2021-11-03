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

package com.duckduckgo.mobile.android.vpn.pixels

import com.duckduckgo.app.statistics.pixels.Pixel

/**
 * Prefix all pixels with:
 * - m_atp_ev_ -> when the pixel is an event eg. enable/disable, press, etc
 * - m_atp_imp -> when the pixel is an impression eg. notification/screen/pop-up shows
 *
 * Suffix the pixels with:
 * - _u -> when the pixel is a once-ever pixel
 * - _d -> when the pixel is a first-in-day pixel
 * - _c -> when the pixel is a every-occurrence pixel
 *
 * Give pixels readable names but try to abbreviate, eg:
 * - enable -> ena
 * - disable -> dis
 * - ...
 *
 * Good source to check for abbrev. is this website https://www.allacronyms.com/
 */
enum class DeviceShieldPixelNames(override val pixelName: String) : Pixel.PixelName {
    ATP_INSTALLED_UNIQUE("m_atp_install_u"),

    ATP_ENABLE_UPON_SEARCH_DAILY("m_atp_ev_ena_on_search_d"),
    ATP_DISABLE_UPON_SEARCH_DAILY("m_atp_ev_dis_on_search_d"),
    ATP_ENABLE_UPON_APP_LAUNCH("m_atp_ev_ena_on_launch_c"),
    ATP_ENABLE_UPON_APP_LAUNCH_DAILY("m_atp_ev_ena_on_launch_d"),
    ATP_DISABLE_UPON_APP_LAUNCH("m_atp_ev_dis_on_launch_c"),
    ATP_DISABLE_UPON_APP_LAUNCH_DAILY("m_atp_ev_dis_on_launch_d"),
    ATP_ENABLE_DAILY("m_atp_ev_ena_d"),
    ATP_DISABLE_DAILY("m_atp_ev_dis_d"),
    ATP_LAST_DAY_ENABLE_COUNT_DAILY("m_atp_ev_ena_count_d"),
    ATP_LAST_DAY_DISABLE_COUNT_DAILY("m_atp_ev_dis_count_d"),

    ATP_ENABLE_UNIQUE("m_atp_ev_ena_u"),
    ATP_ENABLE_FROM_REMINDER_NOTIFICATION_UNIQUE("m_atp_ev_reminder_not_u"),
    ATP_ENABLE_FROM_REMINDER_NOTIFICATION_DAILY("m_atp_ev_reminder_not_d"),
    ATP_ENABLE_FROM_REMINDER_NOTIFICATION("m_atp_ev_reminder_not_c"),
    ATP_ENABLE_FROM_ONBOARDING_UNIQUE("m_atp_ev_onboarding_u"),
    ATP_ENABLE_FROM_ONBOARDING_DAILY("m_atp_ev_onboarding_d"),
    ATP_ENABLE_FROM_ONBOARDING("m_atp_ev_onboarding_c"),
    ATP_ENABLE_FROM_SETTINGS_TILE_UNIQUE("m_atp_ev_quick_settings_u"),
    ATP_ENABLE_FROM_SETTINGS_TILE_DAILY("m_atp_ev_quick_settings_d"),
    ATP_ENABLE_FROM_SETTINGS_TILE("m_atp_ev_quick_settings_c"),
    ATP_ENABLE_FROM_SUMMARY_TRACKER_ACTIVITY_UNIQUE("m_atp_ev_trkr_act_u"),
    ATP_ENABLE_FROM_SUMMARY_TRACKER_ACTIVITY_DAILY("m_atp_ev_trkr_act_d"),
    ATP_ENABLE_FROM_SUMMARY_TRACKER_ACTIVITY("m_atp_ev_trkr_act_c"),

    ATP_DISABLE_FROM_SETTINGS_TILE_DAILY("m_atp_ev_dis_quick_settings_d"),
    ATP_DISABLE_FROM_SETTINGS_TILE("m_atp_ev_dis_quick_settings_c"),
    ATP_DISABLE_FROM_SUMMARY_TRACKER_ACTIVITY_DAILY("m_atp_ev_dis_trkr_act_d"),
    ATP_DISABLE_FROM_SUMMARY_TRACKER_ACTIVITY("m_atp_ev_dis_trkr_act_c"),

    DID_SHOW_DAILY_NOTIFICATION("m_atp_imp_dly_not_%s"),
    DID_PRESS_DAILY_NOTIFICATION("m_atp_ev_dly_not_%s_press"),
    DID_SHOW_WEEKLY_NOTIFICATION("m_atp_imp_wkly_not_%s"),
    DID_PRESS_WEEKLY_NOTIFICATION("m_atp_ev_wkly_not_%s_press"),
    DID_PRESS_ONGOING_NOTIFICATION_DAILY("m_atp_ev_ong_not_press_d"),
    DID_PRESS_ONGOING_NOTIFICATION("m_atp_ev_ong_not_press_c"),
    DID_PRESS_REMINDER_NOTIFICATION_DAILY("m_atp_ev_reminder_not_press_d"),
    DID_PRESS_REMINDER_NOTIFICATION("m_atp_ev_reminder_not_press_c"),
    DID_SHOW_REMINDER_NOTIFICATION_DAILY("m_atp_imp_reminder_not_d"),
    DID_SHOW_REMINDER_NOTIFICATION("m_atp_imp_reminder_not_c"),

    DID_SHOW_NEW_TAB_SUMMARY_UNIQUE("m_atp_imp_new_tab_u"),
    DID_SHOW_NEW_TAB_SUMMARY_DAILY("m_atp_imp_new_tab_d"),
    DID_SHOW_NEW_TAB_SUMMARY("m_atp_imp_new_tab_c"),
    DID_PRESS_NEW_TAB_SUMMARY_DAILY("m_atp_ev_new_tab_press_d"),
    DID_PRESS_NEW_TAB_SUMMARY("m_atp_ev_new_tab_press_c"),

    DID_SHOW_SUMMARY_TRACKER_ACTIVITY_UNIQUE("m_atp_imp_trkr_act_u"),
    DID_SHOW_SUMMARY_TRACKER_ACTIVITY_DAILY("m_atp_imp_trkr_act_d"),
    DID_SHOW_SUMMARY_TRACKER_ACTIVITY("m_atp_imp_trkr_act_c"),

    DID_SHOW_DETAILED_TRACKER_ACTIVITY_UNIQUE("m_atp_imp_trkr_act_dtl_u"),
    DID_SHOW_DETAILED_TRACKER_ACTIVITY_DAILY("m_atp_imp_trkr_act_dtl_d"),
    DID_SHOW_DETAILED_TRACKER_ACTIVITY("m_atp_imp_trkr_act_dtl_c"),

    ATP_START_ERROR_DAILY("m_atp_ev_start_err_d"),
    ATP_START_ERROR("m_atp_ev_start_err_c"),

    ATP_AUTOMATIC_RESTART_DAILY("m_atp_ev_restart_d"),
    ATP_AUTOMATIC_RESTART("m_atp_ev_restart_c"),

    ATP_KILLED("m_atp_ev_kill"),
    ATP_KILLED_BY_SYSTEM_DAILY("m_atp_ev_sys_kill_d"),
    ATP_KILLED_BY_SYSTEM("m_atp_ev_sys_kill_c"),
    ATP_KILLED_VPN_REVOKED_DAILY("m_atp_ev_revoke_kill_d"),
    ATP_KILLED_VPN_REVOKED("m_atp_ev_revoke_kill_c"),

    ATP_TRACKER_BLOCKED("m_atp_ev_trkr_blkd_c"),

    ATP_DID_SHOW_PRIVACY_REPORT_ARTICLE("m_atp_imp_article_c"),

    ATP_TUN_INTERFACE_DOWN_DAILY("m_atp_ev_tun_down_d"),
    ATP_TUN_INTERFACE_DOWN("m_atp_ev_tun_down_c"),

    ATP_PROCESS_EXPENDABLE_LOW("m_atp_ev_expen_mem_low_c"),
    ATP_PROCESS_EXPENDABLE_LOW_DAILY("m_atp_ev_expen_mem_low_d"),
    ATP_PROCESS_EXPENDABLE_MODERATE("m_atp_ev_expen_mem_moderate_c"),
    ATP_PROCESS_EXPENDABLE_MODERATE_DAILY("m_atp_ev_expen_mem_moderate_d"),
    ATP_PROCESS_EXPENDABLE_COMPLETE("m_atp_ev_expen_mem_complete_c"),
    ATP_PROCESS_EXPENDABLE_COMPLETE_DAILY("m_atp_ev_expen_mem_complete_d"),

    ATP_PROCESS_MEMORY_LOW("m_atp_ev_mem_low_c"),
    ATP_PROCESS_MEMORY_LOW_DAILY("m_atp_ev_mem_low_d"),
    ATP_PROCESS_MEMORY_MODERATE("m_atp_ev_mem_moderate_c"),
    ATP_PROCESS_MEMORY_MODERATE_DAILY("m_atp_ev_mem_moderate_d"),
    ATP_PROCESS_MEMORY_CRITICAL("m_atp_ev_mem_critical_c"),
    ATP_PROCESS_MEMORY_CRITICAL_DAILY("m_atp_ev_mem_critical_d"),

    ATP_DISABLE_APP_PROTECTION("m_atp_ev_dis_protection_c"),
    ATP_DISABLE_APP_PROTECTION_DAILY("m_atp_ev_dis_protection_d"),
    ATP_ENABLE_APP_PROTECTION_APP("m_atp_ev_ena_protection_c"),
    ATP_ENABLE_APP_PROTECTION_APP_DAILY("m_atp_ev_ena_protection_d"),
    ATP_RESTORE_APP_PROTECTION_LIST("m_atp_ev_restore_protection_c"),
    ATP_RESTORE_APP_PROTECTION_LIST_DAILY("m_atp_ev_restore_protection_d"),
    ATP_LAUNCH_FEEDBACK("m_atp_ev_launch_fdbk_c"),
    ATP_LAUNCH_FEEDBACK_DAILY("m_atp_ev_launch_fdbk_d"),

    ATP_APP_BREAKAGE_REPORT("m_atp_breakage_report"),

    ATP_ENCRYPTED_IO_EXCEPTION("m_atp_ev_encrypted_io_err_c"),
    ATP_ENCRYPTED_GENERAL_EXCEPTION("m_atp_ev_encrypted_err_c"),

    ATP_APP_PROTECTION_DIALOG_REPORTING_SKIPPED("m_atp_ev_protection_dialog_skip_c"),

    ATP_DID_SHOW_REPORT_BREAKAGE_APP_LIST("m_atp_imp_report_bkg_c"),
    ATP_DID_SHOW_REPORT_BREAKAGE_APP_LIST_DAILY("m_atp_imp_report_bkg_d"),
    ATP_DID_SHOW_REPORT_BREAKAGE_TEXT_FORM("m_atp_imp_report_bkg_desc_c"),
    ATP_DID_SHOW_REPORT_BREAKAGE_TEXT_FORM_DAILY("m_atp_imp_report_bkg_desc_d"),
    ATP_DID_SHOW_REPORT_BREAKAGE_SINGLE_CHOICE_FORM("m_atp_imp_report_bkg_login_c"),
    ATP_DID_SHOW_REPORT_BREAKAGE_SINGLE_CHOICE_FORM_DAILY("m_atp_imp_report_bkg_login_d"),

    ATP_DID_SHOW_DISABLE_TRACKING_PROTECTION_DIALOG("m_atp_imp_dis_protection_c"),
    ATP_DID_CHOOSE_DISABLE_TRACKING_PROTECTION_DIALOG("m_atp_ev_dis_protection_c"),
    ATP_DID_CHOOSE_DISABLE_ONE_APP_PROTECTION_DIALOG("m_atp_ev_dis_app_protection_c"),
    ATP_DID_CHOOSE_CANCEL_APP_PROTECTION_DIALOG("m_atp_ev_cancel_app_protection_c"),
    ;
}
