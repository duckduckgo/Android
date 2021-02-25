/*
 * Copyright (c) 2020 DuckDuckGo
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

import androidx.annotation.VisibleForTesting
import com.duckduckgo.mobile.android.vpn.dao.VpnPreferencesDao
import com.duckduckgo.mobile.android.vpn.model.VpnPreferences
import com.duckduckgo.mobile.android.vpn.model.VpnTrackerCompany
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

class TrackerListProvider(
    private val vpnPreferencesDao: VpnPreferencesDao
) {

    data class Tracker(val id: Int, val trackerCompanyId: Int, val hostname: String)

    fun trackerList(): List<Tracker> {
        val facebookCompanyId = TRACKER_GROUP_COMPANIES.firstOrNull { it.company.equals("facebook", true) }?.trackerCompanyId ?: -1

        return TRACKER_HOST_NAMES.filter { it.trackerCompanyId != facebookCompanyId || getIncludeFacebookDomains() }
    }

    fun setIncludeFacebookDomains(included: Boolean) = runBlocking(Dispatchers.IO) {
        launch { vpnPreferencesDao.insert(VpnPreferences(VPN_PREFERENCE_INCLUDE_FB_DOMAINS, included)) }
    }

    private fun getIncludeFacebookDomains(): Boolean = runBlocking(Dispatchers.IO) {
        return@runBlocking vpnPreferencesDao.get(VPN_PREFERENCE_INCLUDE_FB_DOMAINS)?.value ?: true
    }

    companion object {
        private const val VPN_PREFERENCE_INCLUDE_FB_DOMAINS = "VPN_PREFERENCE_INCLUDE_FB_DOMAINS"

        @VisibleForTesting
        val TRACKER_HOST_NAMES = listOf(
            // Google
            Tracker(1, 1, "doubleclick.net"),
            Tracker(2, 1, "googlesyndication.com"),
            Tracker(3, 1, "google-analytics.com"),
            Tracker(4, 1, "googleadservices.com"),
            Tracker(5, 1, "googletagservices.com"),
            Tracker(6, 1, "googletagmanager.com"),
            // Amazon
            Tracker(7, 2, "amazon-adsystem.com"),
            // Facebook
            Tracker(8, 3, "graph.facebook.com"),
            Tracker(9, 3, "www.facebook.com"),
            Tracker(10, 3, "m.facebook.com"),
            Tracker(11, 3, "edge-chat.facebook.com"),
            Tracker(12, 3, "facebook.com"),
            Tracker(13, 3, "connect.facebook.net"),
            Tracker(14, 3, "scontent.xx.fbcdn.net"),
            Tracker(15, 3, "cx.atdmt.com"),
            Tracker(16, 3, "static.xx.fbcdn.net"),
            Tracker(17, 3, "fbsbx.com"),
            Tracker(18, 3, "platform-lookaside.fbsbx.com"),
            Tracker(19, 3, "scontent-ort2-2.xx.fbcdn.net"),
            Tracker(20, 3, "scontent.cdninstagram.com"),
            Tracker(21, 3, "fbcdn.net"),
            Tracker(22, 3, "www.instagram.com"),
            Tracker(23, 3, "scontent-ort2-1.xx.fbcdn.net"),
            Tracker(24, 3, "scontent.fmia1-2.fna.fbcdn.net"),
            Tracker(25, 3, "facebook.net"),
            Tracker(26, 3, "atdmt.com"),
            Tracker(27, 3, "instagram.com"),
        )

        val UNDEFINED_TRACKER_COMPANY = VpnTrackerCompany(0, "Tracking LLC")

        val TRACKER_GROUP_COMPANIES = listOf(
            UNDEFINED_TRACKER_COMPANY,
            VpnTrackerCompany(1, "Google"),
            VpnTrackerCompany(2, "Amazon"),
            VpnTrackerCompany(3, "Facebook")
        )
    }
}
