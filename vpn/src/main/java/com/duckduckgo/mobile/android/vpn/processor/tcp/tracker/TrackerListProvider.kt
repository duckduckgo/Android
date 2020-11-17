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

package com.duckduckgo.mobile.android.vpn.processor.tcp.tracker

import com.duckduckgo.mobile.android.vpn.model.VpnTrackerCompany

class TrackerListProvider {

    data class Tracker(val id: Int, val trackerCompanyId: Int, val hostname: String)

    fun trackerList(): List<Tracker> {
        return TRACKER_HOST_NAMES
    }

    companion object {
        val TRACKER_HOST_NAMES = listOf(
            Tracker(1, 1, "doubleclick.net"),
            Tracker(2, 1, "googlesyndication.com"),
            Tracker(3, 1, "google-analytics.com"),
            Tracker(4, 1, "googleadservices.com"),
            Tracker(5, 1, "googletagservices.com"),
            Tracker(6, 1, "googletagmanager.com"),
            Tracker(7, 2, "amazon-adsystem.com")
        )

        val UNDEFINED_TRACKER_COMPANY = VpnTrackerCompany(0, "Tracking LLC")

        val TRACKER_GROUP_COMPANIES = listOf(
            UNDEFINED_TRACKER_COMPANY,
            VpnTrackerCompany(1, "Google"),
            VpnTrackerCompany(2, "Amazon")
        )
    }
}
