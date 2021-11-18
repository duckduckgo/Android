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

class AppNameResolver(private val packageManager: PackageManager) {

    fun getAppNameForPackageId(packageId: String): OriginatingApp {
        val stripped = packageId.substringBefore(":")
        return try {
            val appName = packageManager.getApplicationLabel(packageManager.getApplicationInfo(stripped, PackageManager.GET_META_DATA)) as String
            OriginatingApp(packageId, appName)
        } catch (e: PackageManager.NameNotFoundException) {
            Timber.e("Failed to find app name for: $stripped. ${e.message}")
            OriginatingApp(packageId, OriginatingAppPackageIdentifierStrategy.UNKNOWN)
        }
    }

    data class OriginatingApp(val packageId: String, val appName: String) {
        override fun toString(): String = "package=$packageId ($appName)"

        fun isDdg(): Boolean {
            return VpnExclusionList.isDdgApp(packageId)
        }

        fun isUnknown(): Boolean {
            return OriginatingAppPackageIdentifierStrategy.UNKNOWN.equals(appName, ignoreCase = true)
        }
    }

}
