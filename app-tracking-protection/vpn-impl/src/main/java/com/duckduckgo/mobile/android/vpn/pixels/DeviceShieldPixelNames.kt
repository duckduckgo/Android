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
enum class DeviceShieldPixelNames(override val pixelName: String, val enqueue: Boolean = false) : Pixel.PixelName {
    ATP_ENABLE_UPON_SEARCH_DAILY("m_atp_ev_enabled_on_search_d"),
    ATP_DISABLE_UPON_SEARCH_DAILY("m_atp_ev_disabled_on_search_d"),
    ATP_ENABLE_UPON_APP_LAUNCH("m_atp_ev_enabled_on_launch_c"),
    ATP_ENABLE_UPON_APP_LAUNCH_DAILY("m_atp_ev_enabled_on_launch_d"),
    ATP_DISABLE_UPON_APP_LAUNCH("m_atp_ev_disabled_on_launch_c"),
    ATP_DISABLE_UPON_APP_LAUNCH_DAILY("m_atp_ev_disabled_on_launch_d"),
    ATP_ENABLE_DAILY("m_atp_ev_enabled_d"),
    ATP_DISABLE_DAILY("m_atp_ev_disabled_d"),

    ATP_ENABLE_UNIQUE("m_atp_ev_enabled_u"),
    ATP_ENABLE_MONTHLY("m_atp_ev_enabled_monthly"),
    ATP_ENABLE_FROM_REMINDER_NOTIFICATION_UNIQUE("m_atp_ev_enabled_reminder_notification_u"),
    ATP_ENABLE_FROM_REMINDER_NOTIFICATION_DAILY("m_atp_ev_enabled_reminder_notification_d"),
    ATP_ENABLE_FROM_REMINDER_NOTIFICATION("m_atp_ev_enabled_reminder_notification_c"),
    ATP_ENABLE_FROM_ONBOARDING_UNIQUE("m_atp_ev_enabled_onboarding_u"),
    ATP_ENABLE_FROM_ONBOARDING_DAILY("m_atp_ev_enabled_onboarding_d"),
    ATP_ENABLE_FROM_ONBOARDING("m_atp_ev_enabled_onboarding_c"),
    ATP_ENABLE_FROM_DAX_ONBOARDING("m_atp_ev_enabled_dax_onboarding_u"),
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
    ATP_DID_SHOW_PRIVACY_REPORT_ARTICLE_DAILY("m_atp_imp_article_d"),

    ATP_DID_SHOW_ONBOARDING_FAQ("m_atp_imp_onboarding_faq_c"),
    ATP_DID_SHOW_ONBOARDING_FAQ_DAILY("m_atp_imp_onboarding_faq_d"),

    ATP_ESTABLISH_TUN_INTERFACE_ERROR_DAILY("m_atp_ev_establish_tun_error_d"),
    ATP_ESTABLISH_TUN_INTERFACE_ERROR("m_atp_ev_establish_tun_error_c"),
    ATP_ESTABLISH_NULL_TUN_INTERFACE_ERROR_DAILY("m_atp_ev_establish_null_tun_error_d"),
    ATP_ESTABLISH_NULL_TUN_INTERFACE_ERROR("m_atp_ev_establish_null_tun_error_c"),

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
    ATP_APP_BREAKAGE_REPORT_DAILY("m_atp_breakage_report_d"),
    ATP_APP_BREAKAGE_REPORT_UNIQUE("m_atp_breakage_report_u"),
    ATP_APP_CPU_MONITOR_REPORT("m_atp_ev_cpu_usage_above_%d"),

    ATP_DID_SHOW_REPORT_BREAKAGE_APP_LIST("m_atp_imp_report_breakage_c"),
    ATP_DID_SHOW_REPORT_BREAKAGE_APP_LIST_DAILY("m_atp_imp_report_breakage_d"),
    ATP_DID_SHOW_REPORT_BREAKAGE_SINGLE_CHOICE_FORM("m_atp_imp_report_breakage_login_c"),
    ATP_DID_SHOW_REPORT_BREAKAGE_SINGLE_CHOICE_FORM_DAILY("m_atp_imp_report_breakage_login_d"),

    ATP_DID_SHOW_DISABLE_TRACKING_PROTECTION_DIALOG("m_atp_imp_disable_protection_dialog_c"),
    ATP_DID_CHOOSE_DISABLE_TRACKING_PROTECTION_DIALOG("m_atp_ev_selected_disable_protection_c", enqueue = true),
    ATP_DID_CHOOSE_DISABLE_ONE_APP_PROTECTION_DIALOG("m_atp_ev_selected_disable_app_protection_c"),
    ATP_DID_CHOOSE_CANCEL_APP_PROTECTION_DIALOG("m_atp_ev_selected_cancel_app_protection_c"),

    ATP_DID_SHOW_VPN_CONFLICT_DIALOG("m_atp_imp_vpn_conflict_dialog_c"),
    ATP_DID_CHOOSE_DISMISS_VPN_CONFLICT_DIALOG_DAILY("m_atp_ev_vpn_conflict_dialog_dismiss_d"),
    ATP_DID_CHOOSE_DISMISS_VPN_CONFLICT_DIALOG("m_atp_ev_vpn_conflict_dialog_dismiss_c"),
    ATP_DID_CHOOSE_OPEN_SETTINGS_VPN_CONFLICT_DIALOG_DAILY("m_atp_ev_vpn_conflict_dialog_open_settings_d"),
    ATP_DID_CHOOSE_OPEN_SETTINGS_VPN_CONFLICT_DIALOG("m_atp_ev_vpn_conflict_dialog_open_settings_c"),
    ATP_DID_CHOOSE_CONTINUE_VPN_CONFLICT_DIALOG("m_atp_ev_vpn_conflict_dialog_continue_c"),
    ATP_DID_CHOOSE_CONTINUE_VPN_CONFLICT_DIALOG_DAILY("m_atp_ev_vpn_conflict_dialog_continue_d"),

    ATP_DID_SHOW_EXCLUSION_LIST_ACTIVITY_UNIQUE("m_atp_imp_exclusion_list_activity_u"),
    ATP_DID_SHOW_EXCLUSION_LIST_ACTIVITY_DAILY("m_atp_imp_exclusion_list_activity_d"),
    ATP_DID_SHOW_EXCLUSION_LIST_ACTIVITY("m_atp_imp_exclusion_list_activity_c"),
    ATP_DID_OPEN_EXCLUSION_LIST_ACTIVITY_FROM_MANAGE_APPS_PROTECTION("m_atp_ev_exclusion_list_activity_open_apps_c"),
    ATP_DID_OPEN_EXCLUSION_LIST_ACTIVITY_FROM_MANAGE_APPS_PROTECTION_DAILY("m_atp_ev_exclusion_list_activity_open_apps_d"),
    ATP_DID_OPEN_EXCLUSION_LIST_ACTIVITY_FROM_MANAGE_APPS_PROTECTION_UNIQUE("m_atp_ev_exclusion_list_activity_open_apps_u"),

    ATP_DID_SHOW_COMPANY_TRACKERS_ACTIVITY_UNIQUE("m_atp_imp_company_trackers_activity_u"),
    ATP_DID_SHOW_COMPANY_TRACKERS_ACTIVITY_DAILY("m_atp_imp_company_trackers_activity_d"),
    ATP_DID_SHOW_COMPANY_TRACKERS_ACTIVITY("m_atp_imp_company_trackers_activity_c"),

    ATP_DID_SHOW_MANAGE_RECENT_APP_SETTINGS_ACTIVITY_UNIQUE("m_atp_imp_manage_recent_app_settings_activity_u"),
    ATP_DID_SHOW_MANAGE_RECENT_APP_SETTINGS_ACTIVITY_DAILY("m_atp_imp_manage_recent_app_settings_activity_d"),
    ATP_DID_SHOW_MANAGE_RECENT_APP_SETTINGS_ACTIVITY("m_atp_imp_manage_recent_app_settings_activity_c"),

    ATP_DID_SHOW_REMOVE_TRACKING_PROTECTION_FEATURE_DIALOG_UNIQUE("m_atp_imp_remove_tracking_protection_feature_dialog_u"),
    ATP_DID_SHOW_REMOVE_TRACKING_PROTECTION_FEATURE_DIALOG_DAILY("m_atp_imp_remove_tracking_protection_feature_dialog_d"),
    ATP_DID_SHOW_REMOVE_TRACKING_PROTECTION_FEATURE_DIALOG("m_atp_imp_remove_tracking_protection_feature_dialog_c"),
    ATP_DID_CHOOSE_REMOVE_TRACKING_PROTECTION_DIALOG_DAILY("m_atp_ev_selected_remove_tracking_protection_feature_d"),
    ATP_DID_CHOOSE_REMOVE_TRACKING_PROTECTION_DIALOG("m_atp_ev_selected_remove_tracking_protection_feature_c"),
    ATP_DID_CHOOSE_CANCEL_TRACKING_PROTECTION_DIALOG_DAILY("m_atp_ev_selected_cancel_tracking_protection_feature_d"),
    ATP_DID_CHOOSE_CANCEL_TRACKING_PROTECTION_DIALOG("m_atp_ev_selected_cancel_tracking_protection_feature_c"),

    ATP_REPORT_DEVICE_CONNECTIVITY_ERROR("m_atp_report_no_device_connectivity_c", enqueue = true),
    ATP_REPORT_DEVICE_CONNECTIVITY_ERROR_DAILY("m_atp_report_no_device_connectivity_d", enqueue = true),
    ATP_REPORT_VPN_CONNECTIVITY_ERROR("m_atp_report_no_vpn_connectivity_c", enqueue = true),
    ATP_REPORT_VPN_CONNECTIVITY_ERROR_DAILY("m_atp_report_no_vpn_connectivity_d", enqueue = true),

    ATP_REPORT_LOOPBACK_DNS_SET_ERROR("m_atp_report_loopback_dns_error_c"),
    ATP_REPORT_LOOPBACK_DNS_SET_ERROR_DAILY("m_atp_report_loopback_dns_error_d"),
    ATP_REPORT_ANY_LOCAL_ADDR_DNS_SET_ERROR("m_atp_report_anylocal_dns_error_c"),
    ATP_REPORT_ANY_LOCAL_ADDR_DNS_SET_DAILY("m_atp_report_anylocal_dns_error_d"),
    ATP_REPORT_DNS_SET_ERROR("m_atp_report_dns_error_c"),
    ATP_REPORT_DNS_SET_ERROR_DAILY("m_atp_report_dns_error_d"),
    ATP_REPORT_BLOCKLIST_STATS_DAILY("m_atp_report_blocklist_stats_d"),

    ATP_DID_SHOW_PROMOTE_ALWAYS_ON_DIALOG_UNIQUE("m_atp_imp_promote_always_on_dialog_u"),
    ATP_DID_SHOW_PROMOTE_ALWAYS_ON_DIALOG_DAILY("m_atp_imp_promote_always_on_dialog_d"),
    ATP_DID_SHOW_PROMOTE_ALWAYS_ON_DIALOG("m_atp_imp_promote_always_on_dialog_c"),
    ATP_DID_CHOOSE_OPEN_SETTINGS_PROMOTE_ALWAYS_ON_DIALOG_DAILY("m_atp_ev_selected_settings_promote_always_on_d"),
    ATP_DID_CHOOSE_OPEN_SETTINGS_PROMOTE_ALWAYS_ON_DIALOG("m_atp_ev_selected_settings_promote_always_on_c"),

    ATP_DID_ENABLE_APP_PROTECTION_FROM_ALL("m_atp_imp_enable_app_protection_all_c"),
    ATP_DID_ENABLE_APP_PROTECTION_FROM_DETAIL("m_atp_imp_enable_app_protection_detail_c"),
    ATP_DID_DISABLE_APP_PROTECTION_FROM_ALL("m_atp_imp_disable_app_protection_all_c"),
    ATP_DID_DISABLE_APP_PROTECTION_FROM_DETAIL("m_atp_imp_disable_app_protection_detail_c"),

    ATP_REPORT_ALWAYS_ON_ENABLED_DAILY("m_atp_ev_always_on_enabled_d"),
    ATP_REPORT_ALWAYS_ON_LOCKDOWN_ENABLED_DAILY("m_atp_ev_always_on_lockdown_enabled_d"),

    ATP_REPORT_UNPROTECTED_APPS_BUCKET("m_atp_unprotected_apps_bucket_%d_c"),
    ATP_REPORT_UNPROTECTED_APPS_BUCKET_DAILY("m_atp_unprotected_apps_bucket_%d_d"),

    ATP_TDS_EXPERIMENT_DOWNLOAD_FAILED("m_atp_tds_experiment_download_failed"),

    ATP_DID_PRESS_APPTP_ENABLED_CTA_BUTTON("m_atp_ev_apptp_enabled_cta_button_press"),

    ATP_REPORT_VPN_NETWORK_STACK_CREATE_ERROR("m_atp_ev_apptp_create_network_stack_error_c"),
    ATP_REPORT_VPN_NETWORK_STACK_CREATE_ERROR_DAILY("m_atp_ev_apptp_create_network_stack_error_d"),

    ATP_REPORT_TUNNEL_THREAD_STOP_TIMEOUT("m_atp_ev_apptp_tunnel_thread_stop_timeout_c", enqueue = true),
    ATP_REPORT_TUNNEL_THREAD_STOP_TIMEOUT_DAILY("m_atp_ev_apptp_tunnel_thread_stop_timeout_d", enqueue = true),

    ATP_REPORT_TUNNEL_THREAD_STOP_CRASH("m_atp_ev_apptp_tunnel_thread_crash_c", enqueue = true),
    ATP_REPORT_TUNNEL_THREAD_CRASH_DAILY("m_atp_ev_apptp_tunnel_thread_crash_d", enqueue = true),

    REPORT_VPN_ALWAYS_ON_TRIGGERED("m_vpn_ev_always_on_triggered_c"),
    REPORT_VPN_ALWAYS_ON_TRIGGERED_DAILY("m_vpn_ev_always_on_triggered_d"),

    REPORT_NOTIFY_START_FAILURE("m_vpn_ev_notify_start_failed_d"),
    REPORT_NOTIFY_START_FAILURE_DAILY("m_vpn_ev_notify_start_failed_c"),

    REPORT_TLS_PARSING_ERROR_CODE_DAILY("m_atp_tls_parsing_error_code_%d_d"),

    VPN_SNOOZE_STARTED("m_vpn_ev_snooze_started_c", enqueue = true),
    VPN_SNOOZE_STARTED_DAILY("m_vpn_ev_snooze_started_d", enqueue = true),
    VPN_SNOOZE_ENDED("m_vpn_ev_snooze_ended_c", enqueue = true),
    VPN_SNOOZE_ENDED_DAILY("m_vpn_ev_snooze_ended_d", enqueue = true),

    VPN_MOTO_G_FIX_DAILY("m_vpn_ev_moto_g_fix_d", enqueue = true),
    VPN_START_ATTEMPT("m_vpn_ev_start_attempt_c", enqueue = true),
    VPN_START_ATTEMPT_SUCCESS("m_vpn_ev_start_attempt_success_c", enqueue = true),
    VPN_START_ATTEMPT_FAILURE("m_vpn_ev_start_attempt_failure_c", enqueue = true),

    NEW_TAB_SECTION_TOGGLED_OFF("m_new_tab_page_customize_section_off_appTP"),
    NEW_TAB_SECTION_TOGGLED_ON("m_new_tab_page_customize_section_on_appTP"),

    APPTP_PPRO_UPSELL_ENABLED_BANNER_SHOWN("m_atp_ppro-upsell_banner-apptp-enabled_show_c"),
    APPTP_PPRO_UPSELL_ENABLED_BANNER_SHOWN_DAILY("m_atp_ppro-upsell_banner-apptp-enabled_show_d"),
    APPTP_PPRO_UPSELL_ENABLED_BANNER_SHOWN_UNIQUE("m_atp_ppro-upsell_banner-apptp-enabled_show_u"),

    APPTP_PPRO_UPSELL_ENABLED_BANNER_DISMISSED("m_atp_ppro-upsell_banner-apptp-enabled_dismissed_c"),

    APPTP_PPRO_UPSELL_ENABLED_BANNER_LINK_CLICKED("m_atp_ppro-upsell_banner-apptp-enabled_link_clicked_c"),
    APPTP_PPRO_UPSELL_ENABLED_BANNER_LINK_CLICKED_DAILY("m_atp_ppro-upsell_banner-apptp-enabled_link_clicked_d"),
    APPTP_PPRO_UPSELL_ENABLED_BANNER_LINK_CLICKED_UNIQUE("m_atp_ppro-upsell_banner-apptp-enabled_link_clicked_u"),

    APPTP_PPRO_UPSELL_DISABLED_INFO_SHOWN("m_atp_ppro-upsell_info-apptp-disabled_show_c"),
    APPTP_PPRO_UPSELL_DISABLED_INFO_SHOWN_DAILY("m_atp_ppro-upsell_info-apptp-disabled_show_d"),
    APPTP_PPRO_UPSELL_DISABLED_INFO_SHOWN_UNIQUE("m_atp_ppro-upsell_info-apptp-disabled_show_u"),

    APPTP_PPRO_UPSELL_DISABLED_INFO_LINK_CLICKED("m_atp_ppro-upsell_info-apptp-disabled_link_clicked_c"),
    APPTP_PPRO_UPSELL_DISABLED_INFO_LINK_CLICKED_DAILY("m_atp_ppro-upsell_info-apptp-disabled_link_clicked_d"),
    APPTP_PPRO_UPSELL_DISABLED_INFO_LINK_CLICKED_UNIQUE("m_atp_ppro-upsell_info-apptp-disabled_link_clicked_u"),

    APPTP_PPRO_UPSELL_REVOKED_INFO_SHOWN("m_atp_ppro-upsell_info-apptp-revoked_show_c"),
    APPTP_PPRO_UPSELL_REVOKED_INFO_SHOWN_DAILY("m_atp_ppro-upsell_info-apptp-revoked_show_d"),
    APPTP_PPRO_UPSELL_REVOKED_INFO_SHOWN_UNIQUE("m_atp_ppro-upsell_info-apptp-revoked_show_u"),

    APPTP_PPRO_UPSELL_REVOKED_INFO_LINK_CLICKED("m_atp_ppro-upsell_info-apptp-revoked_link_clicked_c"),
    APPTP_PPRO_UPSELL_REVOKED_INFO_LINK_CLICKED_DAILY("m_atp_ppro-upsell_info-apptp-revoked_link_clicked_d"),
    APPTP_PPRO_UPSELL_REVOKED_INFO_LINK_CLICKED_UNIQUE("m_atp_ppro-upsell_info-apptp-revoked_link_clicked_u"),
}
