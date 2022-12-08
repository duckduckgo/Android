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

package com.duckduckgo.mobile.android.vpn.processor.requestingapp

import android.content.pm.PackageManager
import com.duckduckgo.mobile.android.vpn.apps.VpnExclusionList
import timber.log.Timber

private const val UNKNOWN = "unknown"

interface AppNameResolver {
    fun getAppNameForPackageId(packageId: String): OriginatingApp
    fun getPackageIdForUid(uid: Int): String

    data class OriginatingApp(
        val packageId: String,
        val appName: String,
    ) {
        override fun toString(): String = "package=$packageId ($appName)"

        fun isDdg(): Boolean {
            return VpnExclusionList.isDdgApp(packageId)
        }

        fun isUnknown(): Boolean {
            return UNKNOWN.equals(appName, ignoreCase = true)
        }

        companion object {
            fun unknown(): OriginatingApp {
                return OriginatingApp(UNKNOWN, UNKNOWN)
            }
        }
    }
}

internal class RealAppNameResolver(private val packageManager: PackageManager) : AppNameResolver {

    override fun getAppNameForPackageId(packageId: String): AppNameResolver.OriginatingApp {
        val stripped = packageId.substringBefore(":")
        return try {
            val appName = packageManager.getApplicationLabel(packageManager.getApplicationInfo(stripped, PackageManager.GET_META_DATA)) as String
            AppNameResolver.OriginatingApp(packageId, appName)
        } catch (e: PackageManager.NameNotFoundException) {
            Timber.e("Failed to find app name for: $stripped. ${e.message}")
            AppNameResolver.OriginatingApp(packageId, UNKNOWN)
        }
    }

    override fun getPackageIdForUid(uid: Int): String {
        val packages: Array<String>?

        try {
            packages = packageManager.getPackagesForUid(uid)
        } catch (e: SecurityException) {
            Timber.e(e, "Failed to get package ID for UID: $uid due to security violation.")
            return UNKNOWN
        }

        if (packages.isNullOrEmpty()) {
            Timber.w("Failed to get package ID for UID: $uid")
            return UNKNOWN
        }

        if (packages.size > 1) {
            val sb = StringBuilder(String.format("Found %d packages for uid:%d", packages.size, uid))
            packages.forEach {
                sb.append(String.format("\npackage: %s", it))
            }
            Timber.d(sb.toString())
        }

        return packages.first()
    }
}
