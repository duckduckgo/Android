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
 */
enum class DeviceShieldPixelNames(override val pixelName: String) : Pixel.PixelName {
    ATP_ENABLE_UPON_SEARCH_DAILY("m_atp_ev_enabled_on_search_d"),
    ATP_DISABLE_UPON_SEARCH_DAILY("m_atp_ev_disabled_on_search_d"),
    ATP_ENABLE_UPON_APP_LAUNCH("m_atp_ev_enabled_on_launch_c"),
    ATP_ENABLE_UPON_APP_LAUNCH_DAILY("m_atp_ev_enabled_on_launch_d"),
    ATP_DISABLE_UPON_APP_LAUNCH("m_atp_ev_disabled_on_launch_c"),
    ATP_DISABLE_UPON_APP_LAUNCH_DAILY("m_atp_ev_disabled_on_launch_d"),
    ATP_ENABLE_DAILY("m_atp_ev_enabled_d"),
    ATP_DISABLE_DAILY("m_atp_ev_disabled_d"),
    ATP_LAST_DAY_ENABLE_COUNT_DAILY("m_atp_ev_enabled_count_d"),
    ATP_LAST_DAY_DISABLE_COUNT_DAILY("m_atp_ev_disabled_count_d"),

    ATP_ENABLE_UNIQUE("m_atp_ev_enabled_u"),
    ATP_ENABLE_FROM_REMINDER_NOTIFICATION_UNIQUE("m_atp_ev_enabled_reminder_notification_u"),
    ATP_ENABLE_FROM_REMINDER_NOTIFICATION_DAILY("m_atp_ev_enabled_reminder_notification_d"),
    ATP_ENABLE_FROM_REMINDER_NOTIFICATION("m_atp_ev_enabled_reminder_notification_c"),
    ATP_ENABLE_FROM_ONBOARDING_UNIQUE("m_atp_ev_enabled_onboarding_u"),
    ATP_ENABLE_FROM_ONBOARDING_DAILY("m_atp_ev_enabled_onboarding_d"),
    ATP_ENABLE_FROM_ONBOARDING("m_atp_ev_enabled_onboarding_c"),
    ATP_ENABLE_FROM_SETTINGS_TILE_UNIQUE("m_atp_ev_enabled_quick_settings_u"),
    ATP_ENABLE_FROM_SETTINGS_TILE_DAILY("m_atp_ev_enabled_quick_settings_d"),
    ATP_ENABLE_FROM_SETTINGS_TILE("m_atp_ev_quick_enabled_settings_c"),
    ATP_ENABLE_FROM_SUMMARY_TRACKER_ACTIVITY_UNIQUE("m_atp_ev_enabled_tracker_activity_u"),
    ATP_ENABLE_FROM_SUMMARY_TRACKER_ACTIVITY_DAILY("m_atp_ev_enabled_tracker_activity_d"),
    ATP_ENABLE_FROM_SUMMARY_TRACKER_ACTIVITY("m_atp_ev_enabled_tracker_activity_c"),

    ATP_DISABLE_FROM_SETTINGS_TILE_DAILY("m_atp_ev_disabled_quick_settings_d"),
    ATP_DISABLE_FROM_SETTINGS_TILE("m_atp_ev_disabled_quick_settings_c"),
    ATP_DISABLE_FROM_SUMMARY_TRACKER_ACTIVITY_DAILY("m_atp_ev_disabled_tracker_activity_d"),
    ATP_DISABLE_FROM_SUMMARY_TRACKER_ACTIVITY("m_atp_ev_disabled_tracker_activity_c"),

    DID_SHOW_DAILY_NOTIFICATION("m_atp_imp_daily_notification_%s"),
    DID_PRESS_DAILY_NOTIFICATION("m_atp_ev_daily_notification_%s_press"),
    DID_SHOW_WEEKLY_NOTIFICATION("m_atp_imp_weekly_notification_%s"),
    DID_PRESS_WEEKLY_NOTIFICATION("m_atp_ev_weekly_notification_%s_press"),
    DID_PRESS_ONGOING_NOTIFICATION_DAILY("m_atp_ev_ongoing_notification_press_d"),
    DID_PRESS_ONGOING_NOTIFICATION("m_atp_ev_ongoing_notification_press_c"),
    DID_PRESS_REMINDER_NOTIFICATION_DAILY("m_atp_ev_reminder_notification_press_d"),
    DID_PRESS_REMINDER_NOTIFICATION("m_atp_ev_reminder_notification_press_c"),
    DID_SHOW_REMINDER_NOTIFICATION_DAILY("m_atp_imp_reminder_notification_d"),
    DID_SHOW_REMINDER_NOTIFICATION("m_atp_imp_reminder_notification_c"),

    DID_SHOW_NEW_TAB_SUMMARY_UNIQUE("m_atp_imp_new_tab_u"),
    DID_SHOW_NEW_TAB_SUMMARY_DAILY("m_atp_imp_new_tab_d"),
    DID_SHOW_NEW_TAB_SUMMARY("m_atp_imp_new_tab_c"),
    DID_PRESS_NEW_TAB_SUMMARY_DAILY("m_atp_ev_new_tab_press_d"),
    DID_PRESS_NEW_TAB_SUMMARY("m_atp_ev_new_tab_press_c"),

    DID_SHOW_SUMMARY_TRACKER_ACTIVITY_UNIQUE("m_atp_imp_tracker_activity_u"),
    DID_SHOW_SUMMARY_TRACKER_ACTIVITY_DAILY("m_atp_imp_tracker_activity_d"),
    DID_SHOW_SUMMARY_TRACKER_ACTIVITY("m_atp_imp_tracker_activity_c"),

    DID_SHOW_DETAILED_TRACKER_ACTIVITY_UNIQUE("m_atp_imp_tracker_activity_detail_u"),
    DID_SHOW_DETAILED_TRACKER_ACTIVITY_DAILY("m_atp_imp_tracker_activity_detail_d"),
    DID_SHOW_DETAILED_TRACKER_ACTIVITY("m_atp_imp_tracker_activity_detail_c"),

    ATP_START_ERROR_DAILY("m_atp_ev_start_error_d"),
    ATP_START_ERROR("m_atp_ev_start_error_c"),

    ATP_AUTOMATIC_RESTART_DAILY("m_atp_ev_restart_d"),
    ATP_AUTOMATIC_RESTART("m_atp_ev_restart_c"),

    ATP_KILLED("m_atp_ev_kill"),
    ATP_KILLED_BY_SYSTEM_DAILY("m_atp_ev_sys_kill_d"),
    ATP_KILLED_BY_SYSTEM("m_atp_ev_sys_kill_c"),
    ATP_KILLED_VPN_REVOKED_DAILY("m_atp_ev_revoke_kill_d"),
    ATP_KILLED_VPN_REVOKED("m_atp_ev_revoke_kill_c"),

    ATP_DID_SHOW_PRIVACY_REPORT_ARTICLE("m_atp_imp_article_c"),

    ATP_DID_SHOW_ONBOARDING_FAQ("m_atp_imp_onboarding_faq_c"),

    ATP_ESTABLISH_TUN_INTERFACE_ERROR_DAILY("m_atp_ev_establish_tun_error_d"),
    ATP_ESTABLISH_TUN_INTERFACE_ERROR("m_atp_ev_establish_tun_error_c"),

    ATP_PROCESS_EXPENDABLE_LOW_DAILY("m_atp_ev_expen_memory_low_d"),
    ATP_PROCESS_EXPENDABLE_MODERATE_DAILY("m_atp_ev_expen_memory_moderate_d"),
    ATP_PROCESS_EXPENDABLE_COMPLETE_DAILY("m_atp_ev_expen_memory_complete_d"),

    ATP_PROCESS_MEMORY_LOW_DAILY("m_atp_ev_memory_low_d"),
    ATP_PROCESS_MEMORY_MODERATE_DAILY("m_atp_ev_memory_moderate_d"),
    ATP_PROCESS_MEMORY_CRITICAL_DAILY("m_atp_ev_memory_critical_d"),

    ATP_RESTORE_APP_PROTECTION_LIST("m_atp_ev_restore_protection_c"),
    ATP_RESTORE_APP_PROTECTION_LIST_DAILY("m_atp_ev_restore_protection_d"),
    ATP_LAUNCH_FEEDBACK("m_atp_ev_launch_feedback_c"),
    ATP_LAUNCH_FEEDBACK_DAILY("m_atp_ev_launch_feedback_d"),

    ATP_DID_SUBMIT_DISABLE_APP_PROTECTION_DIALOG("m_atp_ev_submit_disable_app_protection_dialog_c"),
    ATP_DID_SUBMIT_DISABLE_APP_PROTECTION_DIALOG_DAILY("m_atp_ev_submit_disable_app_protection_dialog_d"),
    ATP_DID_SKIP_DISABLE_APP_PROTECTION_DIALOG("m_atp_ev_skip_disable_app_protection_dialog_c"),
    ATP_DID_SKIP_DISABLE_APP_PROTECTION_DIALOG_DAILY("m_atp_ev_skip_disable_app_protection_dialog_d"),
    ATP_DID_REPORT_ISSUES_FROM_TRACKER_ACTIVITY("m_atp_imp_tracker_activity_report_issues_c"),
    ATP_DID_REPORT_ISSUES_FROM_TRACKER_ACTIVITY_DAILY("m_atp_imp_tracker_activity_report_issues_d"),

    ATP_APP_BREAKAGE_REPORT("m_atp_breakage_report"),
    ATP_APP_BREAKAGE_REPORT_UNIQUE("m_atp_breakage_report_u"),
    ATP_APP_HEALTH_MONITOR_REPORT("m_atp_health_monitor_report"),
    ATP_APP_HEALTH_ALERT_DAILY("m_atp_health_alert_%s_d"),
    ATP_APP_BAD_HEALTH_RESOLVED_BY_RESTART("m_atp_bad_health_resolved_by_restart_c"),
    ATP_APP_BAD_HEALTH_RESOLVED_BY_RESTART_DAILY("m_atp_bad_health_resolved_by_restart_d"),
    ATP_APP_BAD_HEALTH_RESOLVED_ITSELF("m_atp_bad_health_resolved_itself_c"),
    ATP_APP_BAD_HEALTH_RESOLVED_ITSELF_DAILY("m_atp_bad_health_resolved_itself_d"),
    ATP_DID_RESTART_VPN_ON_BAD_HEALTH("m_atp_did_restart_vpn_on_bad_health_c"),
    ATP_DID_RESTART_VPN_ON_BAD_HEALTH_DAILY("m_atp_did_restart_vpn_on_bad_health_d"),
    ATP_DID_RESTART_VPN_PROCESS_ON_BAD_HEALTH("m_atp_did_restart_vpn_process_on_bad_health_c"),
    ATP_DID_RESTART_VPN_PROCESS_ON_BAD_HEALTH_DAILY("m_atp_did_restart_vpn_process_on_bad_health_d"),

    ATP_ENCRYPTED_IO_EXCEPTION("m_atp_ev_encrypted_io_error_c"),
    ATP_ENCRYPTED_GENERAL_EXCEPTION("m_atp_ev_encrypted_error_c"),

    ATP_DID_SHOW_REPORT_BREAKAGE_APP_LIST("m_atp_imp_report_breakage_c"),
    ATP_DID_SHOW_REPORT_BREAKAGE_APP_LIST_DAILY("m_atp_imp_report_breakage_d"),
    ATP_DID_SHOW_REPORT_BREAKAGE_TEXT_FORM("m_atp_imp_report_breakage_desc_c"),
    ATP_DID_SHOW_REPORT_BREAKAGE_TEXT_FORM_DAILY("m_atp_imp_report_breakage_desc_d"),
    ATP_DID_SHOW_REPORT_BREAKAGE_SINGLE_CHOICE_FORM("m_atp_imp_report_breakage_login_c"),
    ATP_DID_SHOW_REPORT_BREAKAGE_SINGLE_CHOICE_FORM_DAILY("m_atp_imp_report_breakage_login_d"),

    ATP_DID_SHOW_DISABLE_TRACKING_PROTECTION_DIALOG("m_atp_imp_disable_protection_dialog_c"),
    ATP_DID_CHOOSE_DISABLE_TRACKING_PROTECTION_DIALOG("m_atp_ev_selected_disable_protection_c"),
    ATP_DID_CHOOSE_DISABLE_ONE_APP_PROTECTION_DIALOG("m_atp_ev_selected_disable_app_protection_c"),
    ATP_DID_CHOOSE_CANCEL_APP_PROTECTION_DIALOG("m_atp_ev_selected_cancel_app_protection_c"),

    ATP_DID_SHOW_WAITLIST_DIALOG("m_atp_imp_waitlist_dialog_c"),
    ATP_DID_PRESS_WAITLIST_DIALOG_NOTIFY_ME("m_atp_ev_waitlist_dialog_notify_me_c"),
    ATP_DID_PRESS_WAITLIST_DIALOG_DISMISS("m_atp_ev_waitlist_dialog_dismiss_c"),

    ATP_RECEIVED_UNKNOWN_PACKET_PROTOCOL("m_atp_ev_unknown_packet_%d_c"),

    ATP_DID_SHOW_VPN_CONFLICT_DIALOG("m_atp_imp_vpn_conflict_dialog_c"),
    ATP_DID_CHOOSE_DISMISS_VPN_CONFLICT_DIALOG("m_atp_ev_vpn_conflict_dialog_dismiss_c"),
    ATP_DID_CHOOSE_OPEN_SETTINGS_VPN_CONFLICT_DIALOG("m_atp_ev_vpn_conflict_dialog_open_settings_c"),
    ;
}
