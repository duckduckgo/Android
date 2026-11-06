/*
 * Copyright (c) 2026 DuckDuckGo
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

package com.duckduckgo.site.permissions.impl.drm

import com.duckduckgo.site.permissions.store.sitepermissions.SitePermissionAskSettingType

enum class DrmPolicyAction {
    GRANT,
    DENY,
    PROMPT,
}

enum class DrmPolicyReason {
    GLOBAL_OFF,
    USER_DENY_ALWAYS,
    USER_ALLOW_ALWAYS,
    SESSION,
    BLOCK_LIST,
    PROTECTIONS_OFF,
    ALLOW_LIST,
    NO_RULE,
}

data class DrmPolicyDecision(
    val action: DrmPolicyAction,
    val reason: DrmPolicyReason,
)

data class DrmPolicyContext(
    val isGlobalAskEnabled: Boolean,
    val siteSetting: SitePermissionAskSettingType?,
    val sessionChoice: Boolean?,
    val isBlockedByBlockList: Boolean,
    val isSiteUnprotected: Boolean,
    val isAllowedByAllowList: Boolean,
)

interface DrmPolicyManager {
    suspend fun decide(url: String, tabId: String? = null): DrmPolicyDecision
}
