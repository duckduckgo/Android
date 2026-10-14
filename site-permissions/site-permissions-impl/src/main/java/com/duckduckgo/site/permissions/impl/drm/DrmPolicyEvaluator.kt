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

import com.duckduckgo.site.permissions.impl.drm.DrmPolicyAction.DENY
import com.duckduckgo.site.permissions.impl.drm.DrmPolicyAction.GRANT
import com.duckduckgo.site.permissions.impl.drm.DrmPolicyAction.PROMPT
import com.duckduckgo.site.permissions.impl.drm.DrmPolicyReason.ALLOW_LIST
import com.duckduckgo.site.permissions.impl.drm.DrmPolicyReason.BLOCK_LIST
import com.duckduckgo.site.permissions.impl.drm.DrmPolicyReason.GLOBAL_OFF
import com.duckduckgo.site.permissions.impl.drm.DrmPolicyReason.NO_RULE
import com.duckduckgo.site.permissions.impl.drm.DrmPolicyReason.PROTECTIONS_OFF
import com.duckduckgo.site.permissions.impl.drm.DrmPolicyReason.SESSION
import com.duckduckgo.site.permissions.impl.drm.DrmPolicyReason.USER_ALLOW_ALWAYS
import com.duckduckgo.site.permissions.impl.drm.DrmPolicyReason.USER_DENY_ALWAYS
import com.duckduckgo.site.permissions.store.sitepermissions.SitePermissionAskSettingType.ALLOW_ALWAYS
import com.duckduckgo.site.permissions.store.sitepermissions.SitePermissionAskSettingType.DENY_ALWAYS

/*
 * First match wins. isBlockedByBlockList comes from a composite that is already false for
 * protections-off sites, so a block-listed domain with protections disabled grants at PROTECTIONS_OFF.
 */
internal fun DrmPolicyContext.evaluate(): DrmPolicyDecision = when {
    !isGlobalAskEnabled -> DrmPolicyDecision(DENY, GLOBAL_OFF)
    siteSetting == DENY_ALWAYS -> DrmPolicyDecision(DENY, USER_DENY_ALWAYS)
    siteSetting == ALLOW_ALWAYS -> DrmPolicyDecision(GRANT, USER_ALLOW_ALWAYS)
    sessionChoice != null -> DrmPolicyDecision(if (sessionChoice) GRANT else DENY, SESSION)
    isBlockedByBlockList -> DrmPolicyDecision(DENY, BLOCK_LIST)
    isSiteUnprotected -> DrmPolicyDecision(GRANT, PROTECTIONS_OFF)
    isAllowedByAllowList -> DrmPolicyDecision(GRANT, ALLOW_LIST)
    else -> DrmPolicyDecision(PROMPT, NO_RULE)
}
