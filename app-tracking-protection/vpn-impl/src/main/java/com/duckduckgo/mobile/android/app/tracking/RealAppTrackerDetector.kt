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

import android.util.LruCache
import com.duckduckgo.di.scopes.VpnScope
import com.duckduckgo.mobile.android.vpn.apps.VpnExclusionList
import com.duckduckgo.mobile.android.vpn.dao.VpnAppTrackerBlockingDao
import com.duckduckgo.mobile.android.vpn.model.TrackingApp
import com.duckduckgo.mobile.android.vpn.model.VpnTracker
import com.duckduckgo.mobile.android.vpn.processor.requestingapp.AppNameResolver
import com.duckduckgo.mobile.android.vpn.processor.tcp.tracker.AppTrackerRecorder
import com.duckduckgo.mobile.android.vpn.store.VpnDatabase
import com.duckduckgo.mobile.android.vpn.trackers.AppTrackerRepository
import com.duckduckgo.mobile.android.vpn.trackers.AppTrackerType
import com.squareup.anvil.annotations.ContributesTo
import dagger.Module
import dagger.Provides
import logcat.logcat

class RealAppTrackerDetector constructor(
    private val appTrackerRepository: AppTrackerRepository,
    private val appNameResolver: AppNameResolver,
    private val appTrackerRecorder: AppTrackerRecorder,
    private val vpnAppTrackerBlockingDao: VpnAppTrackerBlockingDao,
) : AppTrackerDetector {

    // cache packageId -> app name
    private val appNamesCache = LruCache<String, AppNameResolver.OriginatingApp>(100)

    override fun evaluate(domain: String, uid: Int): AppTrackerDetector.AppTracker? {
        // `null` means unknown package ID, do not block
        val packageId = appNameResolver.getPackageIdForUid(uid) ?: return null

        if (VpnExclusionList.isDdgApp(packageId)) {
            logcat { "shouldAllowDomain: DDG app is always allowed" }
            return null
        }

        when (val type = appTrackerRepository.findTracker(domain, packageId)) {
            AppTrackerType.NotTracker -> return null
            is AppTrackerType.FirstParty -> return null
            is AppTrackerType.ThirdParty -> {
                if (isTrackerInExceptionRules(packageId = packageId, hostname = domain)) return null

                val trackingApp = appNamesCache[packageId] ?: appNameResolver.getAppNameForPackageId(packageId)
                appNamesCache.put(packageId, trackingApp)

                // if the app name is unknown, do not block
                if (trackingApp.isUnknown()) return null

                VpnTracker(
                    trackerCompanyId = type.tracker.trackerCompanyId,
                    company = type.tracker.owner.name,
                    companyDisplayName = type.tracker.owner.displayName,
                    domain = type.tracker.hostname,
                    trackingApp = TrackingApp(trackingApp.packageId, trackingApp.appName),
                ).run {
                    appTrackerRecorder.insertTracker(this)
                }
                return AppTrackerDetector.AppTracker(
                    domain = type.tracker.hostname,
                    uid = uid,
                    trackerCompanyDisplayName = type.tracker.owner.displayName,
                    trackingAppId = trackingApp.packageId,
                    trackingAppName = trackingApp.appName,
                )
            }
        }
    }

    private fun isTrackerInExceptionRules(
        packageId: String,
        hostname: String,
    ): Boolean {
        return vpnAppTrackerBlockingDao.getRuleByTrackerDomain(hostname)?.let { rule ->
            logcat { "isTrackerInExceptionRules: found rule $rule for $hostname" }
            return rule.packageNames.contains(packageId)
        } ?: false
    }
}

@ContributesTo(VpnScope::class)
@Module
object AppTrackerDetectorModule {
    @Provides
    fun provideAppTrackerDetector(
        appTrackerRepository: AppTrackerRepository,
        appNameResolver: AppNameResolver,
        appTrackerRecorder: AppTrackerRecorder,
        vpnDatabase: VpnDatabase,
    ): AppTrackerDetector {
        return RealAppTrackerDetector(
            appTrackerRepository = appTrackerRepository,
            appNameResolver = appNameResolver,
            appTrackerRecorder = appTrackerRecorder,
            vpnAppTrackerBlockingDao = vpnDatabase.vpnAppTrackerBlockingDao(),
        )
    }
}
