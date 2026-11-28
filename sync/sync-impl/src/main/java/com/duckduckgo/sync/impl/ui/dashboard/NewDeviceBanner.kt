/*
 * Copyright (c) 2026 DuckDuckGo
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

package com.duckduckgo.sync.impl.ui.dashboard

import android.content.res.Resources
import com.duckduckgo.sync.impl.R
import com.duckduckgo.sync.impl.auditlog.PendingLogin

data class NewDeviceBanner(
    /** Newest first, one per name. */
    val deviceNames: List<String>,
    val moreCount: Int,
    val entryIds: Set<String>,
) {
    companion object {
        const val MAX_NAMES = 8

        fun from(pendingLogins: List<PendingLogin>): NewDeviceBanner? {
            if (pendingLogins.isEmpty()) return null
            // By name, not device ID: a device that logs in again gets a new ID.
            val names = pendingLogins.asReversed().map { it.deviceName }.distinct()
            return NewDeviceBanner(
                deviceNames = names.take(MAX_NAMES),
                moreCount = (names.size - MAX_NAMES).coerceAtLeast(0),
                entryIds = pendingLogins.mapTo(mutableSetOf()) { it.entryId },
            )
        }
    }
}

fun NewDeviceBanner.title(resources: Resources): String {
    val deviceCount = deviceNames.size + moreCount
    return resources.getQuantityString(R.plurals.syncNewDeviceBannerTitle, deviceCount)
}

fun NewDeviceBanner.body(resources: Resources): String {
    val names = when {
        moreCount > 0 -> resources.getQuantityString(
            R.plurals.syncNewDeviceBannerMore,
            moreCount,
            deviceNames.joinToString(NAME_SEPARATOR),
            moreCount,
        )
        deviceNames.size == 1 -> deviceNames.single()
        deviceNames.size == 2 -> resources.getString(R.string.syncNewDeviceBannerTwoNames, deviceNames[0], deviceNames[1])
        else -> resources.getString(R.string.syncNewDeviceBannerLastName, deviceNames.dropLast(1).joinToString(NAME_SEPARATOR), deviceNames.last())
    }
    return resources.getString(R.string.syncNewDeviceBannerBody, names)
}

private const val NAME_SEPARATOR = ", "
