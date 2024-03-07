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

package com.duckduckgo.mobile.android.app.tracking

import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.duckduckgo.mobile.android.vpn.AppTpVpnFeature
import com.duckduckgo.mobile.android.vpn.VpnFeaturesRegistry
import com.duckduckgo.mobile.android.vpn.dao.VpnAppTrackerBlockingDao
import com.duckduckgo.mobile.android.vpn.processor.requestingapp.AppNameResolver
import com.duckduckgo.mobile.android.vpn.processor.tcp.tracker.AppTrackerRecorder
import com.duckduckgo.mobile.android.vpn.trackers.*
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.whenever

@RunWith(AndroidJUnit4::class)
class AppTrackerDetectorTest {
    private val appTrackerRepository: AppTrackerRepository = mock()
    private val appNameResolver: AppNameResolver = mock()
    private val appTrackerRecorder: AppTrackerRecorder = mock()
    private val vpnAppTrackerBlockingDao: VpnAppTrackerBlockingDao = mock()
    private val packageManager: PackageManager = mock()
    private val vpnFeaturesRegistry: VpnFeaturesRegistry = mock()

    private lateinit var appTrackerDetector: AppTrackerDetector

    @Before
    fun setup() {
        whenever(appNameResolver.getAppNameForPackageId(APP_ORIGINATING_APP.packageId)).thenReturn(APP_ORIGINATING_APP)
        whenever(appNameResolver.getAppNameForPackageId(AppNameResolver.OriginatingApp.unknown().packageId))
            .thenReturn(AppNameResolver.OriginatingApp.unknown())
        whenever(packageManager.getApplicationInfo(any(), eq(0))).thenReturn(ApplicationInfo())
        runBlocking { whenever(vpnFeaturesRegistry.isFeatureRegistered(AppTpVpnFeature.APPTP_VPN)).thenReturn(true) }

        appTrackerDetector = RealAppTrackerDetector(
            appTrackerRepository,
            appNameResolver,
            appTrackerRecorder,
            vpnAppTrackerBlockingDao,
            packageManager,
            vpnFeaturesRegistry,
            InstrumentationRegistry.getInstrumentation().targetContext,
        )
    }

    @Test
    fun whenEvaluateThirdPartyTrackerThenReturnTracker() {
        whenever(appTrackerRepository.findTracker(TEST_APP_TRACKER.hostname, APP_PACKAGE_ID))
            .thenReturn(AppTrackerType.ThirdParty(TEST_APP_TRACKER))
        whenever(appNameResolver.getPackageIdForUid(APP_UID)).thenReturn(APP_ORIGINATING_APP.packageId)

        whenever(vpnAppTrackerBlockingDao.getRuleByTrackerDomain(TEST_APP_TRACKER.hostname)).thenReturn(null)

        assertEquals(
            AppTrackerDetector.AppTracker(
                TEST_APP_TRACKER.hostname,
                APP_UID,
                TEST_APP_TRACKER.owner.displayName,
                APP_ORIGINATING_APP.packageId,
                APP_ORIGINATING_APP.appName,
            ),
            appTrackerDetector.evaluate(TEST_APP_TRACKER.hostname, APP_UID),
        )
    }

    @Test
    fun whenEvaluateThirdPartyTrackerAndSystemAppAndNotInExclusionListAndReturnNull() {
        whenever(appTrackerRepository.findTracker(TEST_APP_TRACKER.hostname, APP_PACKAGE_ID))
            .thenReturn(AppTrackerType.ThirdParty(TEST_APP_TRACKER))
        whenever(appNameResolver.getPackageIdForUid(APP_UID)).thenReturn(APP_ORIGINATING_APP.packageId)

        whenever(vpnAppTrackerBlockingDao.getRuleByTrackerDomain(TEST_APP_TRACKER.hostname)).thenReturn(null)
        whenever(packageManager.getApplicationInfo(APP_PACKAGE_ID, 0))
            .thenReturn(ApplicationInfo().apply { flags = ApplicationInfo.FLAG_SYSTEM })

        assertNull(appTrackerDetector.evaluate(TEST_APP_TRACKER.hostname, APP_UID))
    }

    @Test
    fun whenEvaluateThirdPartyTrackerAndoverriddenSystemAppAndNotInExclusionListAndReturnTracker() {
        whenever(appTrackerRepository.findTracker(TEST_APP_TRACKER.hostname, APP_PACKAGE_ID))
            .thenReturn(AppTrackerType.ThirdParty(TEST_APP_TRACKER))
        whenever(appNameResolver.getPackageIdForUid(APP_UID)).thenReturn(APP_ORIGINATING_APP.packageId)

        whenever(vpnAppTrackerBlockingDao.getRuleByTrackerDomain(TEST_APP_TRACKER.hostname)).thenReturn(null)
        whenever(packageManager.getApplicationInfo(APP_PACKAGE_ID, 0))
            .thenReturn(ApplicationInfo().apply { flags = ApplicationInfo.FLAG_SYSTEM })

        // overridden system app
        whenever(appTrackerRepository.getSystemAppOverrideList()).thenReturn(
            listOf(AppTrackerSystemAppOverridePackage(APP_PACKAGE_ID)),
        )

        assertEquals(
            AppTrackerDetector.AppTracker(
                TEST_APP_TRACKER.hostname,
                APP_UID,
                TEST_APP_TRACKER.owner.displayName,
                APP_ORIGINATING_APP.packageId,
                APP_ORIGINATING_APP.appName,
            ),
            appTrackerDetector.evaluate(TEST_APP_TRACKER.hostname, APP_UID),
        )
    }

    @Test
    fun whenEvaluateThirdPartyTrackerAndSystemAppAndInExclusionListAndReturnNull() {
        whenever(appTrackerRepository.findTracker(TEST_APP_TRACKER.hostname, APP_PACKAGE_ID))
            .thenReturn(AppTrackerType.ThirdParty(TEST_APP_TRACKER))
        whenever(appNameResolver.getPackageIdForUid(APP_UID)).thenReturn(APP_ORIGINATING_APP.packageId)

        whenever(vpnAppTrackerBlockingDao.getRuleByTrackerDomain(TEST_APP_TRACKER.hostname)).thenReturn(null)
        whenever(packageManager.getApplicationInfo(APP_PACKAGE_ID, 0))
            .thenReturn(ApplicationInfo().apply { flags = ApplicationInfo.FLAG_SYSTEM })
        // in exclusion list
        whenever(appTrackerRepository.getAppExclusionList()).thenReturn(
            listOf(AppTrackerExcludedPackage(APP_PACKAGE_ID, reason = "")),
        )

        assertNull(appTrackerDetector.evaluate(TEST_APP_TRACKER.hostname, APP_UID))
    }

    @Test
    fun whenEvaluateThirdPartyTrackerAndOverriddenSystemAppAndInExclusionListAndReturnNull() {
        whenever(appTrackerRepository.findTracker(TEST_APP_TRACKER.hostname, APP_PACKAGE_ID))
            .thenReturn(AppTrackerType.ThirdParty(TEST_APP_TRACKER))
        whenever(appNameResolver.getPackageIdForUid(APP_UID)).thenReturn(APP_ORIGINATING_APP.packageId)

        whenever(vpnAppTrackerBlockingDao.getRuleByTrackerDomain(TEST_APP_TRACKER.hostname)).thenReturn(null)
        whenever(packageManager.getApplicationInfo(APP_PACKAGE_ID, 0))
            .thenReturn(ApplicationInfo().apply { flags = ApplicationInfo.FLAG_SYSTEM })
        // overridden system app
        whenever(appTrackerRepository.getSystemAppOverrideList()).thenReturn(
            listOf(AppTrackerSystemAppOverridePackage(APP_PACKAGE_ID)),
        )
        // in exclusion list
        whenever(appTrackerRepository.getAppExclusionList()).thenReturn(
            listOf(AppTrackerExcludedPackage(APP_PACKAGE_ID, reason = "")),
        )

        assertNull(appTrackerDetector.evaluate(TEST_APP_TRACKER.hostname, APP_UID))
    }

    @Test
    fun whenEvaluateThirdPartyTrackerAndOverriddenSystemAppThenReturnTracker() {
        whenever(appTrackerRepository.findTracker(TEST_APP_TRACKER.hostname, APP_PACKAGE_ID))
            .thenReturn(AppTrackerType.ThirdParty(TEST_APP_TRACKER))
        whenever(appNameResolver.getPackageIdForUid(APP_UID)).thenReturn(APP_ORIGINATING_APP.packageId)

        whenever(vpnAppTrackerBlockingDao.getRuleByTrackerDomain(TEST_APP_TRACKER.hostname)).thenReturn(null)
        whenever(packageManager.getApplicationInfo(APP_PACKAGE_ID, 0))
            .thenReturn(ApplicationInfo().apply { flags = ApplicationInfo.FLAG_SYSTEM })
        whenever(appTrackerRepository.getSystemAppOverrideList()).thenReturn(
            listOf(AppTrackerSystemAppOverridePackage(APP_PACKAGE_ID)),
        )

        assertEquals(
            AppTrackerDetector.AppTracker(
                TEST_APP_TRACKER.hostname,
                APP_UID,
                TEST_APP_TRACKER.owner.displayName,
                APP_ORIGINATING_APP.packageId,
                APP_ORIGINATING_APP.appName,
            ),
            appTrackerDetector.evaluate(TEST_APP_TRACKER.hostname, APP_UID),
        )
    }

    @Test
    fun whenEvaluateThirdPartyTrackerAndManuallyUnprotectedThenReturnNull() {
        whenever(appTrackerRepository.findTracker(TEST_APP_TRACKER.hostname, APP_PACKAGE_ID))
            .thenReturn(AppTrackerType.ThirdParty(TEST_APP_TRACKER))
        whenever(appNameResolver.getPackageIdForUid(APP_UID)).thenReturn(APP_ORIGINATING_APP.packageId)

        whenever(vpnAppTrackerBlockingDao.getRuleByTrackerDomain(TEST_APP_TRACKER.hostname)).thenReturn(null)
        whenever(appTrackerRepository.getManualAppExclusionList()).thenReturn(
            listOf(AppTrackerManualExcludedApp(APP_PACKAGE_ID, false)),
        )

        assertNull(appTrackerDetector.evaluate(TEST_APP_TRACKER.hostname, APP_UID))
    }

    @Test
    fun whenEvaluateThirdPartyTrackerAndManuallyProtectedThenReturnTracker() {
        whenever(appTrackerRepository.findTracker(TEST_APP_TRACKER.hostname, APP_PACKAGE_ID))
            .thenReturn(AppTrackerType.ThirdParty(TEST_APP_TRACKER))
        whenever(appNameResolver.getPackageIdForUid(APP_UID)).thenReturn(APP_ORIGINATING_APP.packageId)

        whenever(vpnAppTrackerBlockingDao.getRuleByTrackerDomain(TEST_APP_TRACKER.hostname)).thenReturn(null)
        whenever(appTrackerRepository.getManualAppExclusionList()).thenReturn(
            listOf(AppTrackerManualExcludedApp(APP_PACKAGE_ID, true)),
        )

        assertEquals(
            AppTrackerDetector.AppTracker(
                TEST_APP_TRACKER.hostname,
                APP_UID,
                TEST_APP_TRACKER.owner.displayName,
                APP_ORIGINATING_APP.packageId,
                APP_ORIGINATING_APP.appName,
            ),
            appTrackerDetector.evaluate(TEST_APP_TRACKER.hostname, APP_UID),
        )
    }

    @Test
    fun whenEvaluateThirdPartyTrackerAndInExclusionListThenReturnNull() {
        whenever(appTrackerRepository.findTracker(TEST_APP_TRACKER.hostname, APP_PACKAGE_ID))
            .thenReturn(AppTrackerType.ThirdParty(TEST_APP_TRACKER))
        whenever(appNameResolver.getPackageIdForUid(APP_UID)).thenReturn(APP_ORIGINATING_APP.packageId)

        whenever(vpnAppTrackerBlockingDao.getRuleByTrackerDomain(TEST_APP_TRACKER.hostname)).thenReturn(null)
        whenever(appTrackerRepository.getAppExclusionList()).thenReturn(
            listOf(AppTrackerExcludedPackage(APP_PACKAGE_ID, reason = "")),
        )

        assertNull(appTrackerDetector.evaluate(TEST_APP_TRACKER.hostname, APP_UID))
    }

    @Test
    fun whenEvaluateThirdPartyTrackerAndManuallyProtectedAndInExclusionListThenReturnTracker() {
        whenever(appTrackerRepository.findTracker(TEST_APP_TRACKER.hostname, APP_PACKAGE_ID))
            .thenReturn(AppTrackerType.ThirdParty(TEST_APP_TRACKER))
        whenever(appNameResolver.getPackageIdForUid(APP_UID)).thenReturn(APP_ORIGINATING_APP.packageId)

        whenever(vpnAppTrackerBlockingDao.getRuleByTrackerDomain(TEST_APP_TRACKER.hostname)).thenReturn(null)
        whenever(appTrackerRepository.getManualAppExclusionList()).thenReturn(
            listOf(AppTrackerManualExcludedApp(APP_PACKAGE_ID, true)),
        )
        whenever(appTrackerRepository.getAppExclusionList()).thenReturn(
            listOf(AppTrackerExcludedPackage(APP_PACKAGE_ID, reason = "")),
        )

        assertEquals(
            AppTrackerDetector.AppTracker(
                TEST_APP_TRACKER.hostname,
                APP_UID,
                TEST_APP_TRACKER.owner.displayName,
                APP_ORIGINATING_APP.packageId,
                APP_ORIGINATING_APP.appName,
            ),
            appTrackerDetector.evaluate(TEST_APP_TRACKER.hostname, APP_UID),
        )
    }

    @Test
    fun whenEvaluateThirdPartyTrackerFromUnknownAppThenReturnNull() {
        val packageId = AppNameResolver.OriginatingApp.unknown().packageId

        whenever(appTrackerRepository.findTracker(TEST_APP_TRACKER.hostname, packageId))
            .thenReturn(AppTrackerType.ThirdParty(TEST_APP_TRACKER))
        whenever(appNameResolver.getPackageIdForUid(APP_UID)).thenReturn(packageId)
        whenever(vpnAppTrackerBlockingDao.getRuleByTrackerDomain(TEST_APP_TRACKER.hostname)).thenReturn(null)

        assertNull(appTrackerDetector.evaluate(TEST_APP_TRACKER.hostname, APP_UID))
    }

    @Test
    fun whenEvaluateThirdPartyTrackerFromDdgAppThenReturnNull() {
        // This test case is just in case we pass the DDG traffic through the VPN. Our app doesn't embed trackers but web trackers might be detected
        // as app trackers.

        val packageId = "com.duckduckgo.mobile.android.vpn.test"

        whenever(appTrackerRepository.findTracker(TEST_APP_TRACKER.hostname, packageId))
            .thenReturn(AppTrackerType.ThirdParty(TEST_APP_TRACKER))
        whenever(appNameResolver.getPackageIdForUid(APP_UID)).thenReturn(packageId)

        whenever(vpnAppTrackerBlockingDao.getRuleByTrackerDomain(TEST_APP_TRACKER.hostname)).thenReturn(null)

        assertNull(appTrackerDetector.evaluate(TEST_APP_TRACKER.hostname, APP_UID))
    }

    @Test
    fun whenEvaluateThirdPartyTrackerInExclusionListThenReturnTrackerNull() {
        val packageId = APP_PACKAGE_ID

        whenever(appTrackerRepository.findTracker(TEST_APP_TRACKER.hostname, packageId))
            .thenReturn(AppTrackerType.ThirdParty(TEST_APP_TRACKER))
        whenever(appNameResolver.getPackageIdForUid(APP_UID)).thenReturn(APP_ORIGINATING_APP.packageId)

        whenever(vpnAppTrackerBlockingDao.getRuleByTrackerDomain(TEST_APP_TRACKER.hostname)).thenReturn(
            AppTrackerExceptionRule(TEST_APP_TRACKER.hostname, listOf(packageId)),
        )

        assertNull(appTrackerDetector.evaluate(TEST_APP_TRACKER.hostname, APP_UID))
    }

    @Test
    fun whenEvaluateFirstPartyTrackerThenReturnNull() {
        whenever(appTrackerRepository.findTracker(TEST_APP_TRACKER.hostname, APP_PACKAGE_ID))
            .thenReturn(AppTrackerType.FirstParty(TEST_APP_TRACKER))
        whenever(appNameResolver.getPackageIdForUid(APP_UID)).thenReturn(APP_ORIGINATING_APP.packageId)

        whenever(vpnAppTrackerBlockingDao.getRuleByTrackerDomain(TEST_APP_TRACKER.hostname)).thenReturn(null)

        assertNull(appTrackerDetector.evaluate(TEST_APP_TRACKER.hostname, APP_UID))
    }

    @Test
    fun whenEvaluateNonTrackerThenReturnNull() {
        whenever(appTrackerRepository.findTracker(TEST_APP_TRACKER.hostname, APP_PACKAGE_ID)).thenReturn(AppTrackerType.NotTracker)
        whenever(appNameResolver.getPackageIdForUid(APP_UID)).thenReturn(APP_ORIGINATING_APP.packageId)

        whenever(vpnAppTrackerBlockingDao.getRuleByTrackerDomain(TEST_APP_TRACKER.hostname)).thenReturn(null)

        assertNull(appTrackerDetector.evaluate(TEST_APP_TRACKER.hostname, APP_UID))
    }

    @Test
    fun whenAppTpDisabledReturnNull() = runTest {
        whenever(vpnFeaturesRegistry.isFeatureRegistered(AppTpVpnFeature.APPTP_VPN)).thenReturn(false)

        val appTrackerDetectorDisabled = RealAppTrackerDetector(
            appTrackerRepository,
            appNameResolver,
            appTrackerRecorder,
            vpnAppTrackerBlockingDao,
            packageManager,
            vpnFeaturesRegistry,
            InstrumentationRegistry.getInstrumentation().targetContext,
        )

        assertNull(appTrackerDetectorDisabled.evaluate(TEST_APP_TRACKER.hostname, APP_UID))

        verifyNoInteractions(appNameResolver)
        verifyNoInteractions(appTrackerRepository)
    }

    @Test
    fun whenNullPackageIdThenEvaluateReturnsNull() {
        whenever(appNameResolver.getPackageIdForUid(APP_UID)).thenReturn(null)

        assertNull(appTrackerDetector.evaluate(TEST_APP_TRACKER.hostname, APP_UID))
    }

    companion object {
        private const val APP_UID = 55
        private const val APP_PACKAGE_ID = "com.app.name"
        private val APP_ORIGINATING_APP = AppNameResolver.OriginatingApp(APP_PACKAGE_ID, "testApp")
        private val TEST_APP_TRACKER = AppTracker(
            hostname = "api2.branch.com",
            trackerCompanyId = 0,
            owner = TrackerOwner(
                name = "Branch",
                displayName = "Branch",
            ),
            app = TrackerApp(0, 0.0),
            isCdn = false,
        )
    }
}
