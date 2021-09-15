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

enum class DeviceShieldPixelNames(override val pixelName: String) : Pixel.PixelName {
    DS_INSTALLED_UNIQUE("m_apptb_i"),

    DS_ENABLE_UPON_SEARCH_DAILY("m_apptb_e_sd"),
    DS_DISABLE_UPON_SEARCH_DAILY("m_apptb_d_sd"),
    DS_ENABLE_UPON_APP_LAUNCH("m_apptb_e_al"),
    DS_ENABLE_UPON_APP_LAUNCH_DAILY("m_apptb_e_ald"),
    DS_DISABLE_UPON_APP_LAUNCH("m_apptb_d_al"),
    DS_DISABLE_UPON_APP_LAUNCH_DAILY("m_apptb_d_ald"),
    DS_ENABLE_DAILY("m_apptb_e_d"),
    DS_DISABLE_DAILY("m_apptb_d_d"),
    DS_LAST_DAY_ENABLE_COUNT_DAILY("m_apptb_ld_ec_d"),
    DS_LAST_DAY_DISABLE_COUNT_DAILY("m_apptb_ld_dc_d"),

    DS_ENABLE_UNIQUE("m_apptb_e"),
    ENABLE_DS_FROM_REMINDER_NOTIFICATION_UNIQUE("m_apptb_e_rnu"),
    ENABLE_DS_FROM_REMINDER_NOTIFICATION_DAILY("m_apptb_e_rnd"),
    ENABLE_DS_FROM_REMINDER_NOTIFICATION("m_apptb_e_rn"),
    ENABLE_DS_FROM_ONBOARDING_UNIQUE("m_apptb_e_obu"),
    ENABLE_DS_FROM_ONBOARDING_DAILY("m_apptb_e_obd"),
    ENABLE_DS_FROM_ONBOARDING("m_apptb_e_ob"),
    ENABLE_DS_FROM_SETTINGS_TILE_UNIQUE("m_apptb_e_tu"),
    ENABLE_DS_FROM_SETTINGS_TILE_DAILY("m_apptb_e_td"),
    ENABLE_DS_FROM_SETTINGS_TILE("m_apptb_e_t"),
    ENABLE_DS_FROM_SUMMARY_TRACKER_ACTIVITY_UNIQUE("m_apptb_e_stau"),
    ENABLE_DS_FROM_SUMMARY_TRACKER_ACTIVITY_DAILY("m_apptb_e_stad"),
    ENABLE_DS_FROM_SUMMARY_TRACKER_ACTIVITY("m_apptb_e_sta"),

    DISABLE_DS_FROM_SETTINGS_TILE_DAILY("m_apptb_d_td"),
    DISABLE_DS_FROM_SETTINGS_TILE("m_apptb_d_t"),
    DISABLE_DS_FROM_SUMMARY_TRACKER_ACTIVITY_DAILY("m_apptb_d_stad"),
    DISABLE_DS_FROM_SUMMARY_TRACKER_ACTIVITY("m_apptb_d_sta"),

    DID_SHOW_DAILY_NOTIFICATION("m_apptb_dn_%s_s"),
    DID_PRESS_DAILY_NOTIFICATION("m_apptb_dn_%s_p"),
    DID_SHOW_WEEKLY_NOTIFICATION("m_apptb_wn_%s_s"),
    DID_PRESS_WEEKLY_NOTIFICATION("m_apptb_wn_%s_p"),
    DID_PRESS_ONGOING_NOTIFICATION_DAILY("m_apptb_on_pd"),
    DID_PRESS_ONGOING_NOTIFICATION("m_apptb_on_p"),
    DID_PRESS_REMINDER_NOTIFICATION_DAILY("m_apptb_rn_pd"),
    DID_PRESS_REMINDER_NOTIFICATION("m_apptb_rn_p"),
    DID_SHOW_REMINDER_NOTIFICATION_DAILY("m_apptb_rn_sd"),
    DID_SHOW_REMINDER_NOTIFICATION("m_apptb_rn_s"),

    DID_SHOW_NEW_TAB_SUMMARY_UNIQUE("m_apptb_nt_su"),
    DID_SHOW_NEW_TAB_SUMMARY_DAILY("m_apptb_nt_sd"),
    DID_SHOW_NEW_TAB_SUMMARY("m_apptb_nt_s"),
    DID_PRESS_NEW_TAB_SUMMARY_DAILY("m_apptb_nt_pd"),
    DID_PRESS_NEW_TAB_SUMMARY("m_apptb_nt_p"),

    DID_SHOW_SUMMARY_TRACKER_ACTIVITY_UNIQUE("m_apptb_sta_su"),
    DID_SHOW_SUMMARY_TRACKER_ACTIVITY_DAILY("m_apptb_sta_sd"),
    DID_SHOW_SUMMARY_TRACKER_ACTIVITY("m_apptb_sta_s"),

    DID_SHOW_DETAILED_TRACKER_ACTIVITY_UNIQUE("m_apptb_dta_su"),
    DID_SHOW_DETAILED_TRACKER_ACTIVITY_DAILY("m_apptb_dta_sd"),
    DID_SHOW_DETAILED_TRACKER_ACTIVITY("m_apptb_dta_s"),

    DS_START_ERROR_DAILY("m_apptb_sed"),
    DS_START_ERROR("m_apptb_se"),

    DS_AUTOMATIC_RESTART_DAILY("m_apptb_ard"),
    DS_AUTOMATIC_RESTART("m_apptb_ar"),

    DS_KILLED("m_apptb_k"),
    DS_KILLED_BY_SYSTEM_DAILY("m_apptb_k_sd"),
    DS_KILLED_BY_SYSTEM("m_apptb_k_s"),
    DS_KILLED_VPN_REVOKED_DAILY("m_apptb_k_vd"),
    DS_KILLED_VPN_REVOKED("m_apptb_k_v"),

    DS_TRACKER_BLOCKED("m_apptb_tb"),

    DS_PRIVACY_REPORT_ARTICLE_SHOWED("m_apptb_pra_s"),

    DS_TUN_INTERFACE_DOWN_DAILY("m_apptb_tun_dd"),
    DS_TUN_INTERFACE_DOWN("m_apptb_tun_dc"),

    VPN_PROCESS_EXPENDABLE_LOW("m_apptb_pe_l"),
    VPN_PROCESS_EXPENDABLE_LOW_DAILY("m_apptb_pe_ld"),
    VPN_PROCESS_EXPENDABLE_MODERATE("m_apptb_pe_m"),
    VPN_PROCESS_EXPENDABLE_MODERATE_DAILY("m_apptb_pe_md"),
    VPN_PROCESS_EXPENDABLE_COMPLETE("m_apptb_pe_c"),
    VPN_PROCESS_EXPENDABLE_COMPLETE_DAILY("m_apptb_pe_cd"),

    VPN_PROCESS_MEMORY_LOW("m_apptb_m_l"),
    VPN_PROCESS_MEMORY_LOW_DAILY("m_apptb_m_ld"),
    VPN_PROCESS_MEMORY_MODERATE("m_apptb_m_m"),
    VPN_PROCESS_MEMORY_MODERATE_DAILY("m_apptb_m_md"),
    VPN_PROCESS_MEMORY_CRITICAL("m_apptb_m_c"),
    VPN_PROCESS_MEMORY_CRITICAL_DAILY("m_apptb_m_cd"),

    APP_EXCLUSION_DISABLE_APP("m_apptb_ae_d"),
    APP_EXCLUSION_ENABLE_APP("m_apptb_ae_e"),
    APP_EXCLUSION_RESTORE_PROTECTION_LIST("m_apptb_ae_rp"),
    APP_EXCLUSION_LAUNCH_FEEDBACK("m_apptb_ae_lf"),
    ;
}

object DeviceShieldPixelParameter {
    const val PACKAGE_NAME = "packageName"
    const val EXCLUDING_REASON = "reason"
}
