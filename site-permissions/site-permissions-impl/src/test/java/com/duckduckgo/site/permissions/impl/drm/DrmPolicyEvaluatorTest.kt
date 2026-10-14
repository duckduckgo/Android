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
import com.duckduckgo.site.permissions.store.sitepermissions.SitePermissionAskSettingType.ASK_EVERY_TIME
import com.duckduckgo.site.permissions.store.sitepermissions.SitePermissionAskSettingType.DENY_ALWAYS
import org.junit.Assert.assertEquals
import org.junit.Test

class DrmPolicyEvaluatorTest {

    private val noRuleContext = DrmPolicyContext(
        isGlobalAskEnabled = true,
        siteSetting = null,
        sessionChoice = null,
        isBlockedByBlockList = false,
        isSiteUnprotected = false,
        isAllowedByAllowList = false,
    )

    @Test
    fun whenGlobalToggleOffThenDenyWithGlobalOff() {
        val decision = noRuleContext.copy(isGlobalAskEnabled = false).evaluate()

        assertEquals(DrmPolicyDecision(DENY, GLOBAL_OFF), decision)
    }

    @Test
    fun whenSiteSettingDenyAlwaysThenDenyWithUserDenyAlways() {
        val decision = noRuleContext.copy(siteSetting = DENY_ALWAYS).evaluate()

        assertEquals(DrmPolicyDecision(DENY, USER_DENY_ALWAYS), decision)
    }

    @Test
    fun whenSiteSettingAllowAlwaysThenGrantWithUserAllowAlways() {
        val decision = noRuleContext.copy(siteSetting = ALLOW_ALWAYS).evaluate()

        assertEquals(DrmPolicyDecision(GRANT, USER_ALLOW_ALWAYS), decision)
    }

    @Test
    fun whenSiteSettingAskEveryTimeThenPromptWithNoRule() {
        val decision = noRuleContext.copy(siteSetting = ASK_EVERY_TIME).evaluate()

        assertEquals(DrmPolicyDecision(PROMPT, NO_RULE), decision)
    }

    @Test
    fun whenSessionChoiceAllowsThenGrantWithSession() {
        val decision = noRuleContext.copy(sessionChoice = true).evaluate()

        assertEquals(DrmPolicyDecision(GRANT, SESSION), decision)
    }

    @Test
    fun whenSessionChoiceDeniesThenDenyWithSession() {
        val decision = noRuleContext.copy(sessionChoice = false).evaluate()

        assertEquals(DrmPolicyDecision(DENY, SESSION), decision)
    }

    @Test
    fun whenBlockListMatchesThenDenyWithBlockList() {
        val decision = noRuleContext.copy(isBlockedByBlockList = true).evaluate()

        assertEquals(DrmPolicyDecision(DENY, BLOCK_LIST), decision)
    }

    @Test
    fun whenSiteUnprotectedThenGrantWithProtectionsOff() {
        val decision = noRuleContext.copy(isSiteUnprotected = true).evaluate()

        assertEquals(DrmPolicyDecision(GRANT, PROTECTIONS_OFF), decision)
    }

    @Test
    fun whenAllowListMatchesThenGrantWithAllowList() {
        val decision = noRuleContext.copy(isAllowedByAllowList = true).evaluate()

        assertEquals(DrmPolicyDecision(GRANT, ALLOW_LIST), decision)
    }

    @Test
    fun whenNoRuleMatchesThenPromptWithNoRule() {
        val decision = noRuleContext.evaluate()

        assertEquals(DrmPolicyDecision(PROMPT, NO_RULE), decision)
    }

    @Test
    fun whenDenyAlwaysAndAllowListMatchThenDenyWithUserDenyAlways() {
        val decision = noRuleContext.copy(siteSetting = DENY_ALWAYS, isAllowedByAllowList = true).evaluate()

        assertEquals(DrmPolicyDecision(DENY, USER_DENY_ALWAYS), decision)
    }

    @Test
    fun whenAllowAlwaysAndBlockListMatchThenGrantWithUserAllowAlways() {
        val decision = noRuleContext.copy(siteSetting = ALLOW_ALWAYS, isBlockedByBlockList = true).evaluate()

        assertEquals(DrmPolicyDecision(GRANT, USER_ALLOW_ALWAYS), decision)
    }

    @Test
    fun whenGlobalToggleOffAndAllowAlwaysThenDenyWithGlobalOff() {
        val decision = noRuleContext.copy(isGlobalAskEnabled = false, siteSetting = ALLOW_ALWAYS).evaluate()

        assertEquals(DrmPolicyDecision(DENY, GLOBAL_OFF), decision)
    }

    @Test
    fun whenSessionDenyAndAllowListMatchThenDenyWithSession() {
        val decision = noRuleContext.copy(sessionChoice = false, isAllowedByAllowList = true).evaluate()

        assertEquals(DrmPolicyDecision(DENY, SESSION), decision)
    }

    @Test
    fun whenSessionAllowAndBlockListMatchThenGrantWithSession() {
        val decision = noRuleContext.copy(sessionChoice = true, isBlockedByBlockList = true).evaluate()

        assertEquals(DrmPolicyDecision(GRANT, SESSION), decision)
    }

    @Test
    fun whenBlockListAndAllowListMatchThenDenyWithBlockList() {
        val decision = noRuleContext.copy(isBlockedByBlockList = true, isAllowedByAllowList = true).evaluate()

        assertEquals(DrmPolicyDecision(DENY, BLOCK_LIST), decision)
    }

    @Test
    fun whenSiteUnprotectedAndAllowListMatchThenGrantWithProtectionsOff() {
        val decision = noRuleContext.copy(isSiteUnprotected = true, isAllowedByAllowList = true).evaluate()

        assertEquals(DrmPolicyDecision(GRANT, PROTECTIONS_OFF), decision)
    }
}
