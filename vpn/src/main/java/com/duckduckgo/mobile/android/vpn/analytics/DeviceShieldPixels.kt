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

package com.duckduckgo.mobile.android.vpn.analytics

import com.duckduckgo.app.statistics.pixels.Pixel

enum class DeviceShieldPixels(override val pixelName: String) : Pixel.PixelName {
    DS_INSTALLED_UNIQUE("m_apptb_i"),

    DS_ENABLE_UPON_SEARCH_DAILY("m_apptb_e_sd"),
    DS_DISABLE_UPON_SEARCH_DAILY("m_apptb_d_sd"),
    DS_ENABLE_DAILY("m_apptb_e_d"),
    DS_DISABLE_DAILY("m_apptb_d_d"),

    DS_ENABLE_UNIQUE("m_apptb_e"),
    ENABLE_DS_FROM_NEW_TAB_UNIQUE("m_apptb_e_ntu"),
    ENABLE_DS_FROM_NEW_TAB_DAILY("m_apptb_e_ntd"),
    ENABLE_DS_FROM_NEW_TAB("m_apptb_e_nt"),
    ENABLE_DS_FROM_REMINDER_NOTIFICATION_UNIQUE("m_apptb_e_rnu"),
    ENABLE_DS_FROM_REMINDER_NOTIFICATION_DAILY("m_apptb_e_rnd"),
    ENABLE_DS_FROM_REMINDER_NOTIFICATION("m_apptb_e_rn"),
    ENABLE_DS_FROM_SETTINGS_UNIQUE("m_apptb_e_su"),
    ENABLE_DS_FROM_SETTINGS_DAILY("m_apptb_e_sd"),
    ENABLE_DS_FROM_SETTINGS("m_apptb_e_s"),
    ENABLE_DS_FROM_SETTINGS_TILE_UNIQUE("m_apptb_e_tu"),
    ENABLE_DS_FROM_SETTINGS_TILE_DAILY("m_apptb_e_td"),
    ENABLE_DS_FROM_SETTINGS_TILE("m_apptb_e_t"),
    ENABLE_DS_FROM_PRIVACY_REPORT_UNIQUE("m_apptb_e_pru"),
    ENABLE_DS_FROM_PRIVACY_REPORT_DAILY("m_apptb_e_prd"),
    ENABLE_DS_FROM_PRIVACY_REPORT("m_apptb_e_pr"),

    DISABLE_DS_FROM_SETTINGS_DAILY("m_apptb_d_sd"),
    DISABLE_DS_FROM_SETTINGS("m_apptb_d_s"),
    DISABLE_DS_FROM_SETTINGS_TILE_DAILY("m_apptb_d_td"),
    DISABLE_DS_FROM_SETTINGS_TILE("m_apptb_d_t"),

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

    DID_SHOW_PRIVACY_REPORT_UNIQUE("m_apptb_pr_su"),
    DID_SHOW_PRIVACY_REPORT_DAILY("m_apptb_pr_sd"),
    DID_SHOW_PRIVACY_REPORT("m_apptb_pr_s"),

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
    ;
}
