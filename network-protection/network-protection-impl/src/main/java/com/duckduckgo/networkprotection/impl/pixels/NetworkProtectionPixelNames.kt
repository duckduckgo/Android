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
    NETP_ENABLE_DAILY("m_netp_ev_enabled_d"),
    NETP_DISABLE_DAILY("m_netp_ev_disabled_d"),
    NETP_BACKEND_API_ERROR_DEVICE_REGISTRATION_FAILED("m_netp_ev_backend_api_error_device_registration_failed_c", enqueue = true),
    NETP_BACKEND_API_ERROR_DEVICE_REGISTRATION_FAILED_DAILY("m_netp_ev_backend_api_error_device_registration_failed_d", enqueue = true),
    NETP_VPN_CONNECTIVITY_LOST("m_netp_ev_vpn_connectivity_lost_c", enqueue = true),
    NETP_VPN_CONNECTIVITY_LOST_DAILY("m_netp_ev_vpn_connectivity_lost_d", enqueue = true),
    NETP_VPN_RECONNECT_FAILED("m_netp_ev_vpn_reconnect_failed_c", enqueue = true),
    NETP_VPN_RECONNECT_FAILED_DAILY("m_netp_ev_vpn_reconnect_failed_d", enqueue = true),
    NETP_WG_ERROR_INVALID_STATE("m_netp_ev_wireguard_error_invalid_state_c", enqueue = true),
    NETP_WG_ERROR_INVALID_STATE_DAILY("m_netp_ev_wireguard_error_invalid_state_d", enqueue = true),
    NETP_WG_ERROR_CANT_START_WG_BACKEND("m_netp_ev_wireguard_error_cannot_start_wireguard_backend_c", enqueue = true),
    NETP_WG_ERROR_CANT_START_WG_BACKEND_DAILY("m_netp_ev_wireguard_error_cannot_start_wireguard_backend_d", enqueue = true),
    NETP_WG_ERROR_FAILED_TO_LOAD_WG_LIBRARY("m_netp_ev_wireguard_error_unable_to_load_wireguard_library_c", enqueue = true),
    NETP_WG_ERROR_FAILED_TO_LOAD_WG_LIBRARY_DAILY("m_netp_ev_wireguard_error_unable_to_load_wireguard_library_d", enqueue = true),
    NETP_LATENCY_REPORT("m_netp_ev_latency_c"),
    NETP_REKEY_COMPLETED("m_netp_ev_rekey_completed_c"),
    NETP_REKEY_COMPLETED_DAILY("m_netp_ev_rekey_completed_d"),
}
