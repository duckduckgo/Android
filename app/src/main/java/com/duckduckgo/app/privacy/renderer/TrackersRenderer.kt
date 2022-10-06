/*
 * Copyright (c) 2018 DuckDuckGo
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

package com.duckduckgo.app.privacy.renderer

import android.content.Context
import androidx.annotation.DrawableRes
import com.duckduckgo.app.browser.R
import com.duckduckgo.app.privacy.db.NetworkLeaderboardEntry
import java.util.*

class TrackersRenderer {

    fun trackersText(
        context: Context,
        trackersBlockedCount: Int,
        specialDomainsLoadedCount: Int
    ): String {
        val resource = if (trackersBlockedCount > 0) {
            R.string.trackersBlockedText
        } else if (trackersBlockedCount == 0 && specialDomainsLoadedCount > 0) {
            R.string.trackersNotBlockedText
        } else {
            R.string.trackersNoFoundText
        }
        return context.resources.getString(resource)
    }

    fun domainsLoadedText(
        context: Context,
        domainsLoadedCount: Int
    ): String {
        val resource = if (domainsLoadedCount > 0) {
            R.string.domainsLoadedText
        } else {
            R.string.domainsNotLoadedText
        }
        return context.resources.getString(resource)
    }

    fun majorNetworksText(
        context: Context,
        majorNetworkCount: Int
    ): String {
        val resource = if (majorNetworkCount > 0) R.string.majorNetworksFound else R.string.majorNetworksNoFound
        return context.resources.getString(resource)
    }

    @DrawableRes
    fun networksIcon(
        trackersBlockedCount: Int,
        specialDomainsLoadedCount: Int,
        toggleEnabled: Boolean?,
        largeIcon: Boolean = false
    ): Int {
        return when {
            toggleEnabled == false && trackersBlockedCount + specialDomainsLoadedCount > 0 ->
                if (largeIcon) R.drawable.networks_icon_bad_large else R.drawable.networks_icon_bad
            toggleEnabled == true && specialDomainsLoadedCount > 0 && trackersBlockedCount == 0 ->
                if (largeIcon) R.drawable.networks_icon_neutral_large else R.drawable.networks_icon_neutral
            else ->
                if (largeIcon) R.drawable.networks_icon_good_large else R.drawable.networks_icon_good
        }
    }

    @DrawableRes
    fun networkPillIcon(
        context: Context,
        networkName: String
    ): Int? {
        return networkIcon(context, networkName, "network_pill_")
    }

    @DrawableRes
    fun networkLogoIcon(
        context: Context,
        networkName: String
    ): Int? {
        return networkIcon(context, networkName, "network_logo_")
    }

    private fun networkIcon(
        context: Context,
        networkName: String,
        prefix: String
    ): Int? {
        val drawable = "$prefix$networkName"
            .replace(" ", "_")
            .replace(".", "")
            .replace(",", "")
            .lowercase(Locale.ROOT)
        val resource = context.resources.getIdentifier(drawable, "drawable", context.packageName)
        return if (resource != 0) resource else null
    }

    fun networkPercentage(
        network: NetworkLeaderboardEntry,
        totalDomainsVisited: Int
    ): String? {
        if (totalDomainsVisited == 0 || network.count == 0) return ""
        val to100 = ((network.count / totalDomainsVisited.toFloat()) * 100).toInt()
        return "$to100%"
    }

    @DrawableRes
    fun successFailureIcon(count: Int): Int = when (count) {
        0 -> R.drawable.icon_success
        else -> R.drawable.icon_fail
    }
}
