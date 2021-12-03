/*
 * Copyright (c) 2021 DuckDuckGo
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

package com.duckduckgo.mobile.android.vpn.trackers

import androidx.annotation.WorkerThread
import com.duckduckgo.mobile.android.vpn.dao.VpnAppTrackerBlockingDao
import kotlinx.coroutines.flow.Flow
import timber.log.Timber

@WorkerThread
interface AppTrackerRepository {
    fun findTracker(hostname: String, packageName: String): AppTrackerType

    fun getAppExclusionList(): List<AppTrackerExcludedPackage>

    fun getAppExclusionListFlow(): Flow<List<AppTrackerExcludedPackage>>

    fun getManualAppExclusionList(): List<AppTrackerManualExcludedApp>

    fun getManualAppExclusionListFlow(): Flow<List<AppTrackerManualExcludedApp>>

    fun manuallyExcludedApp(packageName: String)

    fun manuallyEnabledApp(packageName: String)

    fun restoreDefaultProtectedList()
}

class RealAppTrackerRepository(private val vpnAppTrackerBlockingDao: VpnAppTrackerBlockingDao) :
    AppTrackerRepository {

    private val smartTVBlockingList =
        listOf<String>(
            "api-mf1.meta.ndmdhs.com",
            "b02.black.ndmdhs.com",
            "bravia.dl.playstation.net",
            "call.me.sel.sony.com",
            "flingo.tv",
            "sonybivstatic-a.akamaihd.net",
            "facemap.foldlife.net",
            "bdcore-apr-lb.bda.ndmdhs.com",
            "tvsideviewandroidv2-cfgdst-ore-pro.bda.ndmdhs.com",
            "api.cid.samba.tv",
            "#platform.cid.samba.tv #See Toshiba",
            "preferences.cid.samba.tv",
            "2mdn.net",
            "ad.71i.de",
            "adv.ettoday.net",
            "advertising.com",
            "cdn.smartclip.net",
            "cert-test.sandbox.google.com",
            "database01p.anixe.net",
            "de.ioam.de",
            "googleads.g.doubleclick.net",
            "nbc-jite.nbcuni.com",
            "redbutton-adproxy-lb-prod.redbutton.de",
            "redbutton-lb-prod.redbutton.de",
            "redbutton.sim-technik.de",
            "script.ioam.de",
            "start.digitaltext.rtl.de",
            "stats-irl.sxp.smartclip.net",
            "trvdp.com",
            "tv-static.scdn.co",
            "#tv.deezer.com # Breaks Deezer's smart-TV apps",
            "xml.opera.com",
            "0077777700140002.myhomescreen.tv",
            "cert-test.sandbox.google.com",
            "collect.myhomescreen.tv",
            "usage.myhomescreen.tv",
            "collect-us-fy2014.myhomescreen.tv",
            "collect-us-fy2015.myhomescreen.tv",
            "collect-us-fy2016.myhomescreen.tv",
            "collect-us-fy2017.myhomescreen.tv",
            "collect-us-fy2018.myhomescreen.tv",
            "collect-us-fy2019.myhomescreen.tv",
            "collect-us-fy2020.myhomescreen.tv",
            "collect-eu-fy2014.myhomescreen.tv",
            "collect-eu-fy2015.myhomescreen.tv",
            "collect-eu-fy2017.myhomescreen.tv",
            "collect-eu-fy2016.myhomescreen.tv",
            "collect-eu-fy2018.myhomescreen.tv",
            "collect-eu-fy2019.myhomescreen.tv",
            "collect-eu-fy2020.myhomescreen.tv",
            "usage-eu-fy2014.myhomescreen.tv",
            "usage-eu-fy2015.myhomescreen.tv",
            "usage-eu-fy2016.myhomescreen.tv",
            "usage-eu-fy2017.myhomescreen.tv",
            "usage-eu-fy2018.myhomescreen.tv",
            "usage-eu-fy2019.myhomescreen.tv",
            "usage-eu-fy2020.myhomescreen.tv",
            "usage-us-fy2014.myhomescreen.tv",
            "usage-us-fy2015.myhomescreen.tv",
            "usage-us-fy2016.myhomescreen.tv",
            "usage-us-fy2017.myhomescreen.tv",
            "usage-us-fy2018.myhomescreen.tv",
            "usage-us-fy2019.myhomescreen.tv",
            "usage-us-fy2020.myhomescreen.tv",
            "mhc-sec-eu.myhomescreen.tv",
            "x2.vindicosuite.com")

    override fun findTracker(hostname: String, packageName: String): AppTrackerType {
        val tracker = vpnAppTrackerBlockingDao.getTrackerBySubdomain(hostname)
        if (tracker == null) {
            return findSmartTVTracker(hostname)
        } else {
            val entityName = vpnAppTrackerBlockingDao.getEntityByAppPackageId(packageName)
            if (firstPartyTracker(tracker, entityName)) {
                return AppTrackerType.FirstParty(tracker)
            }

            return AppTrackerType.ThirdParty(tracker)
        }
    }

    private fun findSmartTVTracker(hostname: String): AppTrackerType {
        return if (smartTVBlockingList.contains(hostname)) {
            Timber.d("Found specific Smart TV Tracker")
            AppTrackerType.ThirdParty(
                AppTracker(
                    hostname,
                    1279484251,
                    TrackerOwner("Smart TV", "Smart TV"),
                    TrackerApp(0, 100.0),
                    false))
        } else {
            AppTrackerType.NotTracker
        }
    }

    private fun firstPartyTracker(tracker: AppTracker, entityName: AppTrackerPackage?): Boolean {
        if (entityName == null) return false
        return tracker.owner.name == entityName.entityName
    }

    override fun getAppExclusionList(): List<AppTrackerExcludedPackage> {
        return vpnAppTrackerBlockingDao.getAppExclusionList()
    }

    override fun getAppExclusionListFlow(): Flow<List<AppTrackerExcludedPackage>> {
        return vpnAppTrackerBlockingDao.getAppExclusionListFlow()
    }

    override fun getManualAppExclusionList(): List<AppTrackerManualExcludedApp> {
        return vpnAppTrackerBlockingDao.getManualAppExclusionList()
    }

    override fun getManualAppExclusionListFlow(): Flow<List<AppTrackerManualExcludedApp>> {
        return vpnAppTrackerBlockingDao.getManualAppExclusionListFlow()
    }

    override fun manuallyExcludedApp(packageName: String) {
        vpnAppTrackerBlockingDao.insertIntoManualAppExclusionList(
            AppTrackerManualExcludedApp(packageName, false))
    }

    override fun manuallyEnabledApp(packageName: String) {
        vpnAppTrackerBlockingDao.insertIntoManualAppExclusionList(
            AppTrackerManualExcludedApp(packageName, true))
    }

    override fun restoreDefaultProtectedList() {
        vpnAppTrackerBlockingDao.deleteManualAppExclusionList()
    }
}
