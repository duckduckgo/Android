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

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.duckduckgo.app.privacy.db.UserAllowListRepository
import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.privacy.config.api.Drm
import com.duckduckgo.privacy.config.api.UnprotectedTemporary
import com.duckduckgo.site.permissions.impl.SitePermissionsRepository
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
import com.duckduckgo.site.permissions.impl.drmblock.DrmBlock
import com.duckduckgo.site.permissions.store.sitepermissions.SitePermissionAskSettingType
import com.duckduckgo.site.permissions.store.sitepermissions.SitePermissionsEntity
import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.whenever
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RealDrmPolicyManagerTest {

    @get:Rule
    var coroutineRule = CoroutineTestRule()

    private val mockSitePermissionsRepository: SitePermissionsRepository = mock()
    private val drmSessionStore = DrmSessionStore()
    private val mockDrmBlock: DrmBlock = mock()
    private val mockDrm: Drm = mock()
    private val mockUserAllowListRepository: UserAllowListRepository = mock()
    private val mockUnprotectedTemporary: UnprotectedTemporary = mock()

    private val testee = RealDrmPolicyManager(
        mockSitePermissionsRepository,
        drmSessionStore,
        mockDrmBlock,
        mockDrm,
        mockUserAllowListRepository,
        mockUnprotectedTemporary,
    )

    private val url = "https://www.netflix.com/watch"
    private val domain = "www.netflix.com"
    private val tabId = "tabId"

    @Before
    fun before() {
        whenever(mockSitePermissionsRepository.askDrmEnabled).thenReturn(true)
    }

    @Test
    fun whenGlobalDrmAskDisabledThenDenyWithGlobalOff() = runTest {
        whenever(mockSitePermissionsRepository.askDrmEnabled).thenReturn(false)

        assertEquals(DrmPolicyDecision(DENY, GLOBAL_OFF), testee.decide(url, tabId))
    }

    @Test
    fun whenSiteSettingIsDenyAlwaysThenDenyWithUserDenyAlways() = runTest {
        givenSiteSetting(SitePermissionAskSettingType.DENY_ALWAYS)

        assertEquals(DrmPolicyDecision(DENY, USER_DENY_ALWAYS), testee.decide(url, tabId))
    }

    @Test
    fun whenSiteSettingIsAllowAlwaysThenGrantWithUserAllowAlways() = runTest {
        givenSiteSetting(SitePermissionAskSettingType.ALLOW_ALWAYS)

        assertEquals(DrmPolicyDecision(GRANT, USER_ALLOW_ALWAYS), testee.decide(url, tabId))
    }

    @Test
    fun whenSessionChoiceExistsForTabThenSessionDecides() = runTest {
        drmSessionStore.save(tabId, domain, false)

        assertEquals(DrmPolicyDecision(DENY, SESSION), testee.decide(url, tabId))
    }

    @Test
    fun whenTabIdIsNullThenSessionChoiceIsNotConsulted() = runTest {
        drmSessionStore.save(tabId, domain, false)

        assertEquals(DrmPolicyDecision(PROMPT, NO_RULE), testee.decide(url, tabId = null))
    }

    @Test
    fun whenOtherTabHasSessionChoiceThenSessionIsNotConsulted() = runTest {
        drmSessionStore.save("otherTabId", domain, false)

        assertEquals(DrmPolicyDecision(PROMPT, NO_RULE), testee.decide(url, tabId))
    }

    @Test
    fun whenDrmBlockedByBlockListThenDenyWithBlockList() = runTest {
        whenever(mockDrmBlock.isDrmBlockedForUrl(url)).thenReturn(true)

        assertEquals(DrmPolicyDecision(DENY, BLOCK_LIST), testee.decide(url, tabId))
    }

    @Test
    fun whenUriInUserAllowListThenGrantWithProtectionsOff() = runTest {
        whenever(mockUserAllowListRepository.isUriInUserAllowList(any())).thenReturn(true)

        assertEquals(DrmPolicyDecision(GRANT, PROTECTIONS_OFF), testee.decide(url, tabId))
    }

    @Test
    fun whenUrlInUnprotectedTemporaryThenGrantWithProtectionsOff() = runTest {
        whenever(mockUnprotectedTemporary.isAnException(url)).thenReturn(true)

        assertEquals(DrmPolicyDecision(GRANT, PROTECTIONS_OFF), testee.decide(url, tabId))
    }

    @Test
    fun whenBlockListDomainIsAlsoUnprotectedThenGrantWithProtectionsOff() = runTest {
        // DrmBlock's composite already returns false for unprotected sites, so the block rule never fires here.
        whenever(mockDrmBlock.isDrmBlockedForUrl(url)).thenReturn(false)
        whenever(mockUserAllowListRepository.isUriInUserAllowList(any())).thenReturn(true)
        whenever(mockDrm.isDrmAllowedForUrl(url)).thenReturn(true)

        assertEquals(DrmPolicyDecision(GRANT, PROTECTIONS_OFF), testee.decide(url, tabId))
    }

    @Test
    fun whenUrlInAllowListThenGrantWithAllowList() = runTest {
        whenever(mockDrm.isDrmAllowedForUrl(url)).thenReturn(true)

        assertEquals(DrmPolicyDecision(GRANT, ALLOW_LIST), testee.decide(url, tabId))
    }

    @Test
    fun whenNoRuleMatchesThenPromptWithNoRule() = runTest {
        assertEquals(DrmPolicyDecision(PROMPT, NO_RULE), testee.decide(url, tabId))
    }

    @Test
    fun whenDenyAlwaysStoredOnWwwDomainAndRequestIsForBareDomainThenDenyWithUserDenyAlways() = runTest {
        val entity = SitePermissionsEntity(domain = domain, askDrmSetting = SitePermissionAskSettingType.DENY_ALWAYS.name)
        whenever(mockSitePermissionsRepository.getSitePermissionsForWebsite(domain)).thenReturn(entity)
        whenever(mockDrm.isDrmAllowedForUrl("https://netflix.com")).thenReturn(true)

        assertEquals(DrmPolicyDecision(DENY, USER_DENY_ALWAYS), testee.decide("https://netflix.com", tabId))
    }

    @Test
    fun whenDenyAlwaysStoredOnBareDomainAndRequestIsForWwwDomainThenDenyWithUserDenyAlways() = runTest {
        val entity = SitePermissionsEntity(domain = "netflix.com", askDrmSetting = SitePermissionAskSettingType.DENY_ALWAYS.name)
        whenever(mockSitePermissionsRepository.getSitePermissionsForWebsite("netflix.com")).thenReturn(entity)
        whenever(mockDrm.isDrmAllowedForUrl(url)).thenReturn(true)

        assertEquals(DrmPolicyDecision(DENY, USER_DENY_ALWAYS), testee.decide(url, tabId))
    }

    @Test
    fun whenDenyAlwaysStoredOnParentDomainAndRequestIsFromSubdomainThenDenyWithUserDenyAlways() = runTest {
        val entity = SitePermissionsEntity(domain = "foxnews.com", askDrmSetting = SitePermissionAskSettingType.DENY_ALWAYS.name)
        whenever(mockSitePermissionsRepository.getSitePermissionsForWebsite("foxnews.com")).thenReturn(entity)
        whenever(mockDrm.isDrmAllowedForUrl("https://static.foxnews.com/")).thenReturn(true)

        assertEquals(DrmPolicyDecision(DENY, USER_DENY_ALWAYS), testee.decide("https://static.foxnews.com/", tabId))
    }

    @Test
    fun whenSettingStoredOnSubdomainThenParentDomainRequestIsUnaffected() = runTest {
        val entity = SitePermissionsEntity(domain = "static.foxnews.com", askDrmSetting = SitePermissionAskSettingType.DENY_ALWAYS.name)
        whenever(mockSitePermissionsRepository.getSitePermissionsForWebsite("static.foxnews.com")).thenReturn(entity)

        assertEquals(DrmPolicyDecision(PROMPT, NO_RULE), testee.decide("https://foxnews.com/", tabId))
    }

    @Test
    fun whenUrlIsMalformedThenPromptWithNoRule() = runTest {
        assertEquals(DrmPolicyDecision(PROMPT, NO_RULE), testee.decide("not a url", tabId))
    }

    @Test
    fun whenUrlIsIpAddressThenPromptWithNoRule() = runTest {
        assertEquals(DrmPolicyDecision(PROMPT, NO_RULE), testee.decide("https://192.168.0.1", tabId))
    }

    @Test
    fun whenUrlHostIsPublicSuffixThenPromptWithNoRule() = runTest {
        assertEquals(DrmPolicyDecision(PROMPT, NO_RULE), testee.decide("https://co.uk", tabId))
    }

    private suspend fun givenSiteSetting(setting: SitePermissionAskSettingType) {
        val entity = SitePermissionsEntity(domain = domain, askDrmSetting = setting.name)
        whenever(mockSitePermissionsRepository.getSitePermissionsForWebsite(domain)).thenReturn(entity)
    }
}
