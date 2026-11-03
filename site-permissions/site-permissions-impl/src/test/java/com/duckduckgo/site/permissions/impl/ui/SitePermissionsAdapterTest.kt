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

package com.duckduckgo.site.permissions.impl.ui

import com.duckduckgo.site.permissions.impl.R
import com.duckduckgo.site.permissions.impl.ui.SitePermissionListItem.Divider
import com.duckduckgo.site.permissions.impl.ui.SitePermissionListItem.EmptySites
import com.duckduckgo.site.permissions.impl.ui.SitePermissionListItem.SiteAllowedItem
import com.duckduckgo.site.permissions.impl.ui.SitePermissionListItem.SitePermissionSetting
import com.duckduckgo.site.permissions.impl.ui.SitePermissionListItem.SitePermissionToggle
import com.duckduckgo.site.permissions.impl.ui.SitePermissionListItem.SitePermissionsDescription
import com.duckduckgo.site.permissions.impl.ui.SitePermissionListItem.SitePermissionsHeader
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SitePermissionsAdapterTest {

    @Test
    fun whenRedesignWithSitesThenSitePermissionsAndManageSitesSectionsShown() {
        val items = build(sites = listOf("example.com"), redesign = true)

        assertEquals(
            listOf(
                SitePermissionsHeader(R.string.settingsSitePermissions),
                SitePermissionSetting(R.string.sitePermissionsSettingsLocation, true),
                SitePermissionSetting(R.string.sitePermissionsSettingsCamera, false),
                SitePermissionSetting(R.string.sitePermissionsSettingsMicrophone, true),
                SitePermissionSetting(R.string.sitePermissionsSettingsDRM, false),
                SitePermissionsHeader(R.string.sitePermissionsSettingsAllowedSitesTitle),
                SiteAllowedItem("example.com"),
            ),
            items,
        )
    }

    @Test
    fun whenRedesignWithoutSitesThenManageSitesSectionHidden() {
        val items = build(sites = emptyList(), redesign = true)

        assertEquals(5, items.size)
        assertTrue(items.none { it == SitePermissionsHeader(R.string.sitePermissionsSettingsAllowedSitesTitle) || it is EmptySites })
    }

    @Test
    fun whenLegacyWithoutSitesThenDescriptionTogglesDividerAndEmptyStateShown() {
        val items = build(sites = emptyList(), redesign = false)

        assertTrue(items.first() is SitePermissionsDescription)
        assertEquals(SitePermissionsHeader(R.string.sitePermissionsSettingsEnablePermissionTitle), items[1])
        assertEquals(4, items.count { it is SitePermissionToggle })
        assertTrue(items[6] is Divider)
        assertEquals(SitePermissionsHeader(R.string.sitePermissionsSettingsAllowedSitesTitle), items[7])
        assertTrue(items.last() is EmptySites)
    }

    private fun build(sites: List<String>, redesign: Boolean) = buildSitePermissionItems(
        sites = sites,
        isLocationEnabled = true,
        isCameraEnabled = false,
        isMicEnabled = true,
        isDrmEnabled = false,
        permissionSettingsRedesign = redesign,
    )
}
