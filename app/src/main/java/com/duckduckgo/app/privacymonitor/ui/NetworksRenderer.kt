/*
 * Copyright (c) 2017 DuckDuckGo
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

package com.duckduckgo.app.privacymonitor.ui

import android.content.Context
import android.support.annotation.DrawableRes
import com.duckduckgo.app.browser.R


class NetworksRenderer {

    fun networksText(context: Context, networkCount: Int, allTrackersBlocked: Boolean): String {
        val resource = if (allTrackersBlocked) R.plurals.networksBlocked else R.plurals.networksFound
        return context.resources.getQuantityString(resource, networkCount, networkCount)
    }

    @DrawableRes
    fun networksBanner(allTrackersBlocked: Boolean): Int {
        return if (allTrackersBlocked) R.drawable.networks_banner_good else R.drawable.networks_banner_bad
    }

    @DrawableRes
    fun networksIcon(allTrackersBlocked: Boolean): Int {
        return if (allTrackersBlocked) R.drawable.networks_icon_good else R.drawable.networks_icon_bad
    }

    @DrawableRes
    fun networkPillIcon(context: Context, networkName: String): Int? {
        val drawable = "network_pill_$networkName"
                .replace(" ", "")
                .replace(".", "")
                .toLowerCase()
        val resource = context.resources.getIdentifier(drawable, "drawable", context.packageName)
        return if (resource != 0) resource else null
    }
}
