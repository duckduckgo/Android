/*
 * Copyright (c) 2025 DuckDuckGo
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

package com.duckduckgo.pir.impl.dashboard.messaging

enum class PirDashboardWebMessages(val messageName: String) {
    HANDSHAKE("handshake"),
    GET_CURRENT_USER_PROFILE("getCurrentUserProfile"),
    INITIAL_SCAN_STATUS("initialScanStatus"),
    GET_FEATURE_CONFIG("getFeatureConfig"),
    OPEN_SEND_FEEDBACK_MODAL("openSendFeedbackModal"),
    SAVE_PROFILE("saveProfile"),
    ADD_NAME_TO_CURRENT_USER_PROFILE("addNameToCurrentUserProfile"),
    DELETE_USER_PROFILE_DATA("deleteUserProfileData"),
    REMOVE_NAME_AT_INDEX_FROM_CURRENT_USER_PROFILE("removeNameAtIndexFromCurrentUserProfile"),
    SET_NAME_AT_INDEX_IN_CURRENT_USER_PROFILE("setNameAtIndexInCurrentUserProfile"),
    SET_BIRTH_YEAR_FOR_CURRENT_USER_PROFILE("setBirthYearForCurrentUserProfile"),
    ADD_ADDRESS_TO_CURRENT_USER_PROFILE("addAddressToCurrentUserProfile"),
    REMOVE_ADDRESS_AT_INDEX_FROM_CURRENT_USER_PROFILE("removeAddressAtIndexFromCurrentUserProfile"),
    SET_ADDRESS_AT_INDEX_IN_CURRENT_USER_PROFILE("setAddressAtIndexInCurrentUserProfile"),
    START_SCAN_AND_OPT_OUT("startScanAndOptOut"),
    SCAN_AND_OPT_OUT_STATUS_CHANGED("scanAndOptOutStatusChanged"),
    MAINTENANCE_SCAN_STATUS("maintenanceScanStatus"),
    GET_DATA_BROKERS("getDataBrokers"),
    GET_BACKGROUND_AGENT_METADATA("getBackgroundAgentMetadata"),
    GET_VPN_EXCLUSION_SETTING("getVpnExclusionSetting"),
    SET_VPN_EXCLUSION_SETTING("setVpnExclusionSetting"),
    REMOVE_OPT_OUT_FROM_DASHBOARD("removeOptOutFromDashboard"),
}
