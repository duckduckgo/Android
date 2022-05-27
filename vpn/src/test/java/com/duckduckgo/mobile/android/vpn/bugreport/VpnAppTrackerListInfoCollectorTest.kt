/*
 * Copyright (c) 2022 DuckDuckGo
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

package com.duckduckgo.mobile.android.vpn.bugreport

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.duckduckgo.mobile.android.vpn.apps.AppCategory
import com.duckduckgo.mobile.android.vpn.apps.AppCategoryDetector
import com.duckduckgo.mobile.android.vpn.dao.VpnAppTrackerBlockingDao
import com.duckduckgo.mobile.android.vpn.store.VpnDatabase
import com.duckduckgo.mobile.android.vpn.trackers.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
class VpnAppTrackerListInfoCollectorTest {

    private val vpnDatabase = mock<VpnDatabase>()
    private val vpnAppTrackerBlockingDao = mock<VpnAppTrackerBlockingDao>()

    private val appTrackerRepository = mock<AppTrackerRepository>()
    private val appCategoryDetector = mock<AppCategoryDetector>()

    private lateinit var collector: VpnAppTrackerListInfoCollector

    @Before
    fun setup() {
        whenever(vpnDatabase.vpnAppTrackerBlockingDao()).thenReturn(vpnAppTrackerBlockingDao)
        whenever(appTrackerRepository.getManualAppExclusionList()).thenReturn(listOf())

        collector = VpnAppTrackerListInfoCollector(vpnDatabase, appTrackerRepository, appCategoryDetector)
    }

    @Test
    fun whenCollectStateAndBlocklistEtagNotPesentThenJsonEtagIsEmpty() = runTest {
        val jsonObject = collector.collectVpnRelatedState(PACKAGE_ID)

        assertEquals("", jsonObject.get("appTrackerListEtag"))

    }

    @Test
    fun whenCollectStateAndBlocklistEtagPesentThenJsonEtagHasValue() = runTest {
        whenever(vpnAppTrackerBlockingDao.getTrackerBlocklistMetadata()).thenReturn(AppTrackerMetadata(0, "etag"))
        val jsonObject = collector.collectVpnRelatedState(PACKAGE_ID)

        assertEquals("etag", jsonObject.get("appTrackerListEtag"))

    }

    @Test
    fun whenCollectStateAndExclusionListEtagNotPresentThenJsonEtagIsEmpty() = runTest {
        val jsonObject = collector.collectVpnRelatedState(PACKAGE_ID)

        assertEquals("", jsonObject.get("appExclusionListEtag"))

    }

    @Test
    fun whenCollectStateAndExclusionListEtagPresentThenJsonEtagHasValue() = runTest {
        whenever(vpnAppTrackerBlockingDao.getExclusionListMetadata()).thenReturn(AppTrackerExclusionListMetadata(0, "etag"))
        val jsonObject = collector.collectVpnRelatedState(PACKAGE_ID)

        assertEquals("etag", jsonObject.get("appExclusionListEtag"))

    }

    @Test
    fun whenCollectStateAndExclusionRuleListEtagNotPresentThenJsonEtagIsEmpty() = runTest {
        val jsonObject = collector.collectVpnRelatedState(PACKAGE_ID)

        assertEquals("", jsonObject.get("appExceptionRuleListEtag"))

    }

    @Test
    fun whenCollectStateAndExclusionRuleListEtagPresentThenJsonEtagHasValue() = runTest {
        whenever(vpnAppTrackerBlockingDao.getTrackerExceptionRulesMetadata()).thenReturn(AppTrackerExceptionRuleMetadata(0, "etag"))
        val jsonObject = collector.collectVpnRelatedState(PACKAGE_ID)

        assertEquals("etag", jsonObject.get("appExceptionRuleListEtag"))

    }

    @Test
    fun whenCollectStateAndAppGameAndInExclusionListThenReturnUnprotectedByDefaultTrue() = runTest {
        whenever(appCategoryDetector.getAppCategory(PACKAGE_ID)).thenReturn(AppCategory.Game)
        whenever(appTrackerRepository.getAppExclusionList()).thenReturn(listOf(AppTrackerExcludedPackage(PACKAGE_ID)))
        val jsonObject = collector.collectVpnRelatedState(PACKAGE_ID)

        assertEquals("true", jsonObject.get("reportedAppUnprotectedByDefault"))
    }

    @Test
    fun whenCollectStateAndAppInExclusionListThenReturnUnprotectedByDefaultTrue() = runTest {
        whenever(appCategoryDetector.getAppCategory(PACKAGE_ID)).thenReturn(AppCategory.Undefined)
        whenever(appTrackerRepository.getAppExclusionList()).thenReturn(listOf(AppTrackerExcludedPackage(PACKAGE_ID)))
        val jsonObject = collector.collectVpnRelatedState(PACKAGE_ID)

        assertEquals("true", jsonObject.get("reportedAppUnprotectedByDefault"))
    }

    @Test
    fun whenCollectStateAndAppGameThenReturnUnprotectedByDefaultTrue() = runTest {
        whenever(appCategoryDetector.getAppCategory(PACKAGE_ID)).thenReturn(AppCategory.Game)
        whenever(appTrackerRepository.getAppExclusionList()).thenReturn(listOf())
        val jsonObject = collector.collectVpnRelatedState(PACKAGE_ID)

        assertEquals("true", jsonObject.get("reportedAppUnprotectedByDefault"))
    }

    @Test
    fun whenCollectStateAndAppNotGameOrInExclusionListThenReturnUnprotectedByDefaultFalse() = runTest {
        val jsonObject = collector.collectVpnRelatedState(PACKAGE_ID)

        assertEquals("false", jsonObject.get("reportedAppUnprotectedByDefault"))
    }

    @Test
    fun whenCollectStateAndProtectionOverridenThenOverridenDefaultProtectionTrue() = runTest {
        whenever(appTrackerRepository.getManualAppExclusionList()).thenReturn(listOf((AppTrackerManualExcludedApp(PACKAGE_ID, true))))
        val jsonObject = collector.collectVpnRelatedState(PACKAGE_ID)

        assertEquals("true", jsonObject.get("overridenDefaultProtection"))
    }

    @Test
    fun whenCollectStateAndProtectionOverridenThenOverridenDefaultProtectionFalse() = runTest {
        whenever(appTrackerRepository.getManualAppExclusionList()).thenReturn(listOf((AppTrackerManualExcludedApp("other.package.id", true))))
        val jsonObject = collector.collectVpnRelatedState(PACKAGE_ID)

        assertEquals("false", jsonObject.get("overridenDefaultProtection"))
    }

    companion object {
        private const val PACKAGE_ID = "com.package.id"
    }
}
