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

package com.duckduckgo.mobile.android.vpn.feature.settings

import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.mobile.android.vpn.AppTpVpnFeature
import com.duckduckgo.mobile.android.vpn.VpnFeaturesRegistry
import com.duckduckgo.mobile.android.vpn.dao.VpnAppTrackerBlockingDao
import com.duckduckgo.mobile.android.vpn.dao.VpnAppTrackerSystemAppsOverridesDao
import com.duckduckgo.mobile.android.vpn.store.VpnDatabase
import com.duckduckgo.mobile.android.vpn.trackers.AppTrackerExceptionRule
import com.duckduckgo.mobile.android.vpn.trackers.AppTrackerExcludedPackage
import com.duckduckgo.mobile.android.vpn.trackers.AppTrackerSystemAppOverridePackage
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.whenever

@ExperimentalCoroutinesApi
class ExceptionListsSettingStoreTest {

    @get:Rule
    var coroutineRule = CoroutineTestRule()

    private lateinit var exceptionListsSettingStore: ExceptionListsSettingStore

    private val mockVpnFeaturesRegistry: VpnFeaturesRegistry = mock()
    private val mockVpnDatabase: VpnDatabase = mock()
    private val mockVpnAppTrackerBlockingDao: VpnAppTrackerBlockingDao = mock()
    private val mockVpnAppTrackerSystemAppsOverridesDao: VpnAppTrackerSystemAppsOverridesDao = mock()

    /**
     * Test JSON that contains additional fields as well so we can test that they are safely ignored
     */
    private val testJson = """
        {
            "exceptionLists": {
                "appTrackerAllowList": [
                    {
                        "domain": "api.apptentive.com",
                        "defaultTTL": 1,
                        "packageNames": [
                            {
                                "packageName": "com.subway.mobile.subwayapp03",
                                "allowTTL": 2,
                                "randomNote": "note"
                            }
                        ]
                    }
                ],
                "unprotectedApps": [
                    {
                        "packageName": "com.android.chrome",
                        "reason": "Loads websites"
                    }
                ],
                "unhideSystemApps": [
                    "com.android.vending"
                ]
            }
        }
    """

    @Before
    fun setup() {
        exceptionListsSettingStore = ExceptionListsSettingStore(
            mockVpnDatabase,
            coroutineRule.testScope,
            mockVpnFeaturesRegistry,
            coroutineRule.testDispatcherProvider,
        )

        whenever(mockVpnDatabase.vpnAppTrackerBlockingDao()).thenReturn(mockVpnAppTrackerBlockingDao)
        whenever(mockVpnDatabase.vpnSystemAppsOverridesDao()).thenReturn(mockVpnAppTrackerSystemAppsOverridesDao)
    }

    @Test
    fun whenEmptyJsonStoreNothing() {
        exceptionListsSettingStore.store("{}")
        verifyNoInteractions(mockVpnDatabase)
    }

    @Test
    fun whenInvalidJsonStoreNothingAndDoNotCrash() {
        exceptionListsSettingStore.store("")
        verifyNoInteractions(mockVpnDatabase)
    }

    @Test
    fun whenValidJSONUpdatesDB() = runTest {
        exceptionListsSettingStore = ExceptionListsSettingStore(
            mockVpnDatabase,
            coroutineRule.testScope,
            mockVpnFeaturesRegistry,
            coroutineRule.testDispatcherProvider,
        )

        val packageNames = listOf("com.subway.mobile.subwayapp03")
        val trackerExceptionRules = ArrayList<AppTrackerExceptionRule>(1)
        trackerExceptionRules.add(AppTrackerExceptionRule("api.apptentive.com", packageNames))

        val excludedApps = ArrayList<AppTrackerExcludedPackage>(1)
        excludedApps.add(AppTrackerExcludedPackage("com.android.chrome", "Loads websites"))

        val systemOverrides = ArrayList<AppTrackerSystemAppOverridePackage>(1)
        systemOverrides.add(AppTrackerSystemAppOverridePackage("com.android.vending"))

        exceptionListsSettingStore.store(testJson)
        verify(mockVpnAppTrackerBlockingDao).updateTrackerExceptionRules(trackerExceptionRules)
        verify(mockVpnAppTrackerBlockingDao).updateExclusionList(excludedApps)
        verify(mockVpnAppTrackerSystemAppsOverridesDao).upsertSystemAppOverrides(systemOverrides)

        advanceUntilIdle()
        verify(mockVpnFeaturesRegistry).refreshFeature(AppTpVpnFeature.APPTP_VPN)
    }
}
