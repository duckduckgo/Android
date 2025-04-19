/*
 * Copyright (c) 2023 DuckDuckGo
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

package com.duckduckgo.networkprotection.impl.pixels

import com.duckduckgo.app.statistics.pixels.Pixel

/**
 * Prefix all pixels with:
 * - m_netp_ev_ -> when the pixel is an event eg. enable/disable, press, etc
 * - m_netp_imp -> when the pixel is an impression eg. notification/screen/pop-up shows
 *
 * Suffix the pixels with:
 * - _u -> when the pixel is a once-ever pixel
 * - _d -> when the pixel is a first-in-day pixel
 * - _c -> when the pixel is a every-occurrence pixel
 */
enum class NetworkProtectionPixelNames(
    override val pixelName: String,
    val enqueue: Boolean = false,
) : Pixel.PixelName {
    NETP_ENABLE_ON_SEARCH_DAILY("m_netp_ev_enabled_on_search_d"),
    NETP_ENABLE_ON_SEARCH("m_netp_ev_enabled_on_search_c"),
    NETP_ENABLE_DAILY("m_netp_ev_enabled_d", enqueue = true),
    NETP_ENABLE_UNIQUE("m_netp_ev_enabled_u", enqueue = true),
    NETP_DISABLE_DAILY("m_netp_ev_disabled_d"),
    NETP_BACKEND_API_ERROR_DEVICE_REGISTRATION_FAILED("m_netp_ev_backend_api_error_device_registration_failed_c", enqueue = true),
    NETP_BACKEND_API_ERROR_DEVICE_REGISTRATION_FAILED_DAILY("m_netp_ev_backend_api_error_device_registration_failed_d", enqueue = true),
    NETP_WG_ERROR_INVALID_STATE("m_netp_ev_wireguard_error_invalid_state_c", enqueue = true),
    NETP_WG_ERROR_INVALID_STATE_DAILY("m_netp_ev_wireguard_error_invalid_state_d", enqueue = true),
    NETP_WG_ERROR_CANT_START_WG_BACKEND("m_netp_ev_wireguard_error_cannot_start_wireguard_backend_c", enqueue = true),
    NETP_WG_ERROR_CANT_START_WG_BACKEND_DAILY("m_netp_ev_wireguard_error_cannot_start_wireguard_backend_d", enqueue = true),
    NETP_WG_ERROR_FAILED_TO_LOAD_WG_LIBRARY("m_netp_ev_wireguard_error_unable_to_load_wireguard_library_c", enqueue = true),
    NETP_WG_ERROR_FAILED_TO_LOAD_WG_LIBRARY_DAILY("m_netp_ev_wireguard_error_unable_to_load_wireguard_library_d", enqueue = true),
    NETP_REPORT_TERRIBLE_LATENCY("m_netp_ev_terrible_latency_c"),
    NETP_REPORT_POOR_LATENCY("m_netp_ev_poor_latency_c"),
    NETP_REPORT_MODERATE_LATENCY("m_netp_ev_moderate_latency_c"),
    NETP_REPORT_GOOD_LATENCY("m_netp_ev_good_latency_c"),
    NETP_REPORT_EXCELLENT_LATENCY("m_netp_ev_excellent_latency_c"),
    NETP_REPORT_LATENCY_ERROR_DAILY("m_netp_ev_latency_error_d"),
    NETP_REKEY_COMPLETED("m_netp_ev_rekey_completed_c", enqueue = true),
    NETP_REKEY_COMPLETED_DAILY("m_netp_ev_rekey_completed_d", enqueue = true),
    NETP_VPN_CONFLICT_SHOWN("m_netp_imp_vpn_conflict_dialog_c"),
    NETP_VPN_CONFLICT_SHOWN_DAILY("m_netp_imp_vpn_conflict_dialog_d"),
    NETP_ALWAYSON_CONFLICT_SHOWN("m_netp_imp_always_on_conflict_dialog_c"),
    NETP_ALWAYSON_CONFLICT_SHOWN_DAILY("m_netp_imp_always_on_conflict_dialog_d"),
    NETP_ALWAYSON_PROMOTION_SHOWN("m_netp_imp_always_on_promotion_dialog_c"),
    NETP_ALWAYSON_PROMOTION_SHOWN_DAILY("m_netp_imp_always_on_promotion_dialog_d"),
    NETP_ALWAYSON_PROMOTION_OPEN_SETTINGS("m_netp_ev_open_settings_from_always_on_promotion_dialog_c"),
    NETP_ALWAYSON_PROMOTION_OPEN_SETTINGS_DAILY("m_netp_ev_open_settings_from_always_on_promotion_dialog_d"),
    NETP_ALWAYSON_LOCKDOWN_SHOWN("m_netp_imp_always_on_lockdown_dialog_c"),
    NETP_ALWAYSON_LOCKDOWN_SHOWN_DAILY("m_netp_imp_always_on_lockdown_dialog_d"),
    NETP_ALWAYSON_LOCKDOWN_OPEN_SETTINGS("m_netp_ev_open_settings_from_always_on_lockdown_dialog_c"),
    NETP_ALWAYSON_LOCKDOWN_OPEN_SETTINGS_DAILY("m_netp_ev_open_settings_from_always_on_lockdown_dialog_d"),
    NETP_EXCLUSION_LIST_SHOWN("m_netp_imp_exclusion_list_c"),
    NETP_EXCLUSION_LIST_SHOWN_DAILY("m_netp_imp_exclusion_list_d"),
    NETP_EXCLUSION_LIST_APP_ADDED("m_netp_ev_exclusion_list_app_added_c"),
    NETP_EXCLUSION_LIST_SKIP_REPORT_AFTER_EXCLUDING("m_netp_ev_skip_report_after_excluding_app_c"),
    NETP_EXCLUSION_LIST_SKIP_REPORT_AFTER_EXCLUDING_DAILY("m_netp_ev_skip_report_after_excluding_app_d"),
    NETP_EXCLUSION_LIST_APP_REMOVED("m_netp_ev_exclusion_list_app_removed_c"),
    NETP_EXCLUSION_LIST_RESTORE_DEFAULTS("m_netp_ev_exclusion_list_restore_defaults_c"),
    NETP_EXCLUSION_LIST_RESTORE_DEFAULTS_DAILY("m_netp_ev_exclusion_list_restore_defaults_d"),
    NETP_EXCLUSION_LIST_LAUNCH_BREAKAGE_REPORT("m_netp_ev_exclusion_list_launch_breakage_report_c"),
    NETP_EXCLUSION_LIST_LAUNCH_BREAKAGE_REPORT_DAILY("m_netp_ev_exclusion_list_launch_breakage_report_d"),
    NETP_FAQS_SHOWN("m_netp_imp_faqs_c"),
    NETP_FAQS_SHOWN_DAILY("m_netp_imp_faqs_d"),
    NETP_GEOSWITCHING_PAGE_SHOWN("m_netp_imp_geoswitching_c", enqueue = true),
    NETP_GEOSWITCHING_SET_NEAREST("m_netp_ev_geoswitching_set_nearest_c", enqueue = true),
    NETP_GEOSWITCHING_SET_NEAREST_DAILY("m_netp_ev_geoswitching_set_nearest_d", enqueue = true),
    NETP_GEOSWITCHING_SET_CUSTOM("m_netp_ev_geoswitching_set_custom_c", enqueue = true),
    NETP_GEOSWITCHING_SET_CUSTOM_DAILY("m_netp_ev_geoswitching_set_custom_d", enqueue = true),
    NETP_GEOSWITCHING_NO_AVAILABLE_LOCATIONS("m_netp_ev_geoswitching_no_locations_c"),
    NETP_GEOSWITCHING_NO_AVAILABLE_LOCATIONS_DAILY("m_netp_ev_geoswitching_no_locations_d"),
    NETP_PRIVATE_DNS_SET_DAILY("m_netp_ev_private_dns_set_d", enqueue = true),
    NETP_PRIVATE_DNS_SET_VPN_START_FAILED_DAILY("m_netp_ev_private_dns_set_vpn_start_failed_d", enqueue = true),
    NETP_ENABLE_ATTEMPT("m_netp_ev_enable_attempt_c", enqueue = true),
    NETP_ENABLE_ATTEMPT_SUCCESS("m_netp_ev_enable_attempt_success_c", enqueue = true),
    NETP_ENABLE_ATTEMPT_FAILURE("m_netp_ev_enable_attempt_failure_c", enqueue = true),
    NETP_TUNNEL_FAILURE_DAILY("m_netp_ev_tunnel_failure_d", enqueue = true),
    NETP_TUNNEL_FAILURE("m_netp_ev_tunnel_failure_c", enqueue = true),
    NETP_TUNNEL_FAILURE_RECOVERED("m_netp_ev_tunnel_failure_recovered_c", enqueue = true),
    VPN_SNOOZE_CANCELED("m_vpn_ev_snooze_canceled_c", enqueue = true),
    VPN_SNOOZE_CANCELED_DAILY("m_vpn_ev_snooze_canceled_d", enqueue = true),
    NETP_SETTINGS_PRESSED("m_netp_ev_setting_pressed_c"),
    NETP_FAILURE_RECOVERY_STARTED("m_netp_ev_failure_recovery_started_c", enqueue = true),
    NETP_FAILURE_RECOVERY_STARTED_DAILY("m_netp_ev_failure_recovery_started_d", enqueue = true),
    NETP_FAILURE_RECOVERY_FAILED("m_netp_ev_failure_recovery_failed_c", enqueue = true),
    NETP_FAILURE_RECOVERY_FAILED_DAILY("m_netp_ev_failure_recovery_failed_d", enqueue = true),
    NETP_FAILURE_RECOVERY_COMPLETED_SERVER_UNHEALTHY("m_netp_ev_failure_recovery_completed_server_unhealthy_c", enqueue = true),
    NETP_FAILURE_RECOVERY_COMPLETED_SERVER_UNHEALTHY_DAILY("m_netp_ev_failure_recovery_completed_server_unhealthy_d", enqueue = true),
    NETP_FAILURE_RECOVERY_COMPLETED_SERVER_HEALTHY("m_netp_ev_failure_recovery_completed_server_healthy_c", enqueue = true),
    NETP_FAILURE_RECOVERY_COMPLETED_SERVER_HEALTHY_DAILY("m_netp_ev_failure_recovery_completed_server_healthy_d", enqueue = true),
    NETP_FAILURE_RECOVERY_COMPLETED_SERVER_HEALTHY_NEW_TUN_ADDRESS(
        "m_netp_ev_failure_recovery_completed_new_tun_address_server_healthy_c",
        enqueue = true,
    ),
    NETP_FAILURE_RECOVERY_COMPLETED_SERVER_HEALTHY_NEW_TUN_ADDRESS_DAILY(
        "m_netp_ev_failure_recovery_completed_new_tun_address_server_healthy_d",
        enqueue = true,
    ),
    NETP_ACCESS_REVOKED_DIALOG_SHOWN("m_netp_ev_vpn_access_revoked_dialog_shown_c", enqueue = true),
    NETP_ACCESS_REVOKED_DIALOG_SHOWN_DAILY("m_netp_ev_vpn_access_revoked_dialog_shown_d", enqueue = true),
    NETP_PRIVACY_PRO_PROMOTION_DIALOG_SHOWN("m_netp_ev_privacy_pro_promotion_dialog_shown_c", enqueue = true),
    NETP_PRIVACY_PRO_PROMOTION_DIALOG_SHOWN_DAILY("m_netp_ev_privacy_pro_promotion_dialog_shown_d", enqueue = true),
    NETP_BETA_STOPPED_WHEN_PRIVACY_PRO_UPDATED_AND_ENABLED("m_netp_ev_vpn_beta_stopped_when_privacy_pro_enabled_c", enqueue = true),
    NETP_BETA_STOPPED_WHEN_PRIVACY_PRO_UPDATED_AND_ENABLED_DAILY("m_netp_ev_vpn_beta_stopped_when_privacy_pro_enabled_d", enqueue = true),
    NETP_ENABLE_FROM_SETTINGS_TILE("m_netp_ev_enable_from_settings_tile_c", enqueue = true),
    NETP_ENABLE_FROM_SETTINGS_TILE_UNIQUE("m_netp_ev_enable_from_settings_tile_u", enqueue = true),
    NETP_ENABLE_FROM_SETTINGS_TILE_DAILY("m_netp_ev_enable_from_settings_tile_d", enqueue = true),
    NETP_DISABLE_FROM_SETTINGS_TILE("m_netp_ev_disable_from_settings_tile_c", enqueue = true),
    NETP_DISABLE_FROM_SETTINGS_TILE_DAILY("m_netp_ev_disable_from_settings_tile_d", enqueue = true),
    NETP_VPN_SCREEN_SHOWN("m_netp_imp_vpn_screen_c"),
    NETP_VPN_SCREEN_SHOWN_DAILY("m_netp_imp_vpn_screen_d"),
    NETP_VPN_SETTINGS_SHOWN("m_netp_imp_vpn_settings_screen_c"),
    NETP_VPN_SETTINGS_SHOWN_DAILY("m_netp_imp_vpn_settings_screen_d"),
    NETP_PAUSE_ON_CALL_ENABLED("m_netp_ev_enabled_pause_vpn_during_calls_c"),
    NETP_PAUSE_ON_CALL_ENABLED_DAILY("m_netp_ev_enabled_pause_vpn_during_calls_d"),
    NETP_PAUSE_ON_CALL_DISABLED("m_netp_ev_disabled_pause_vpn_during_calls_c"),
    NETP_PAUSE_ON_CALL_DISABLED_DAILY("m_netp_ev_disabled_pause_vpn_during_calls_d"),
    NETP_EXCLUDE_SYSTEM_APPS_ENABLED("m_netp_ev_exclude_system_apps_enabled_c", enqueue = true),
    NETP_EXCLUDE_SYSTEM_APPS_ENABLED_DAILY("m_netp_ev_exclude_system_apps_enabled_d", enqueue = true),
    NETP_EXCLUDE_SYSTEM_APPS_DISABLED("m_netp_ev_exclude_system_apps_disabled_c", enqueue = true),
    NETP_EXCLUDE_SYSTEM_APPS_DISABLED_DAILY("m_netp_ev_exclude_system_apps_disabled_d", enqueue = true),
    NETP_SERVER_MIGRATION_ATTEMPT("m_netp_ev_server_migration_attempt_c", enqueue = true),
    NETP_SERVER_MIGRATION_ATTEMPT_DAILY("m_netp_ev_server_migration_attempt_d", enqueue = true),
    NETP_SERVER_MIGRATION_ATTEMPT_SUCCESS("m_netp_ev_server_migration_attempt_success_c", enqueue = true),
    NETP_SERVER_MIGRATION_ATTEMPT_SUCCESS_DAILY("m_netp_ev_server_migration_attempt_success_d", enqueue = true),
    NETP_SERVER_MIGRATION_ATTEMPT_FAILED("m_netp_ev_server_migration_attempt_failed_c", enqueue = true),
    NETP_SERVER_MIGRATION_ATTEMPT_FAILED_DAILY("m_netp_ev_server_migration_attempt_failed_d", enqueue = true),
    NETP_UPDATE_CUSTOM_DNS("m_netp_ev_update_dns_custom_c", enqueue = true),
    NETP_UPDATE_CUSTOM_DNS_DAILY("m_netp_ev_update_dns_custom_d", enqueue = true),
    NETP_UPDATE_DEFAULT_DNS("m_netp_ev_update_dns_default_c", enqueue = true),
    NETP_UPDATE_DEFAULT_DNS_DAILY("m_netp_ev_update_dns_default_d", enqueue = true),
    NETP_EXCLUDE_PROMPT_SHOWN("m_netp_exclude-prompt_shown_c"),
    NETP_EXCLUDE_PROMPT_SHOWN_DAILY("m_netp_exclude-prompt_shown_d"),
    NETP_EXCLUDE_PROMPT_EXCLUDE_APP_CLICKED("m_netp_exclude-prompt_exclude-app_clicked_c"),
    NETP_EXCLUDE_PROMPT_EXCLUDE_APP_CLICKED_DAILY("m_netp_exclude-prompt_exclude-app_clicked_d"),
    NETP_EXCLUDE_PROMPT_DISABLE_VPN_CLICKED("m_netp_exclude-prompt_disable-vpn_clicked_c"),
    NETP_EXCLUDE_PROMPT_DISABLE_VPN_CLICKED_DAILY("m_netp_exclude-prompt_disable-vpn_clicked_d"),
    NETP_EXCLUDE_PROMPT_DONT_ASK_AGAIN_CLICKED("m_netp_exclude-prompt_dont-ask-again_clicked_c"),
    NETP_AUTO_EXCLUDE_PROMPT_SHOWN_VPN_SCREEN("m_netp_auto-exclude_prompt_vpn-screen_shown_c", enqueue = true),
    NETP_AUTO_EXCLUDE_PROMPT_SHOWN_VPN_SCREEN_DAILY("m_netp_auto-exclude_prompt_vpn-screen_shown_d", enqueue = true),
    NETP_AUTO_EXCLUDE_PROMPT_SHOWN_EXCLUSION_SCREEN("m_netp_auto-exclude_prompt_exclusion-list_shown_c", enqueue = true),
    NETP_AUTO_EXCLUDE_PROMPT_SHOWN_EXCLUSION_SCREEN_DAILY("m_netp_auto-exclude_prompt_exclusion-list_shown_d", enqueue = true),
    NETP_AUTO_EXCLUDE_PROMPT_EXCLUDE_APPS("m_netp_auto-exclude_prompt_exclude-apps_c", enqueue = true),
    NETP_AUTO_EXCLUDE_PROMPT_EXCLUDE_APPS_DAILY("m_netp_auto-exclude_prompt_exclude-apps_d", enqueue = true),
    NETP_AUTO_EXCLUDE_PROMPT_NO_ACTION("m_netp_auto-exclude_prompt_no-action_c", enqueue = true),
    NETP_AUTO_EXCLUDE_PROMPT_NO_ACTION_DAILY("m_netp_auto-exclude_prompt_no-action_d", enqueue = true),
    NETP_AUTO_EXCLUDE_PROMPT_ENABLED("m_netp_auto-exclude_prompt_enabled_c", enqueue = true),
    NETP_AUTO_EXCLUDE_PROMPT_ENABLED_DAILY("m_netp_auto-exclude_prompt_enabled_d", enqueue = true),
    NETP_AUTO_EXCLUDE_ENABLED_VIA_EXCLUSION_LIST("m_netp_auto-exclude_exclusion-list_enabled_c", enqueue = true),
    NETP_AUTO_EXCLUDE_ENABLED_VIA_EXCLUSION_LIST_DAILY("m_netp_auto-exclude_exclusion-list_enabled_d", enqueue = true),
    NETP_AUTO_EXCLUDE_DISABLED_VIA_EXCLUSION_LIST("m_netp_auto-exclude_exclusion-list_disabled_c", enqueue = true),
    NETP_AUTO_EXCLUDE_DISABLED_VIA_EXCLUSION_LIST_DAILY("m_netp_auto-exclude_exclusion-list_disabled_d", enqueue = true),
}
