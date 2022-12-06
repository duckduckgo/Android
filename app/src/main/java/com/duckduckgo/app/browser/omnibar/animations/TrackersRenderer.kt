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

package com.duckduckgo.app.browser.omnibar.animations

import android.content.Context
import androidx.annotation.DrawableRes
import java.util.*

class TrackersRenderer {
    @DrawableRes
    fun networkLogoIcon(
        context: Context,
        networkName: String,
    ): Int? {
        return networkIcon(context, networkName, "network_logo_")
    }

    private fun networkIcon(
        context: Context,
        networkName: String,
        prefix: String,
    ): Int? {
        val drawable = "$prefix$networkName"
            .replace(" ", "_")
            .replace(".", "")
            .replace(",", "")
            .lowercase(Locale.ROOT)
        val resource = context.resources.getIdentifier(drawable, "drawable", context.packageName)
        return if (resource != 0) resource else null
    }
}
