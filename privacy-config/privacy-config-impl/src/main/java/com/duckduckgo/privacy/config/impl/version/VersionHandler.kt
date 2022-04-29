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

package com.duckduckgo.privacy.config.impl.version

import android.content.Context
import android.content.pm.PackageManager.NameNotFoundException
import com.duckduckgo.di.scopes.AppScope
import com.squareup.anvil.annotations.ContributesBinding
import java.lang.NumberFormatException
import javax.inject.Inject

interface VersionHandler {
    fun isSupportedVersion(minSupportedVersion: String?): Boolean
}

@ContributesBinding(AppScope::class)
class RealVersionHandler @Inject constructor(val context: Context) : VersionHandler {
    private fun getAppVersion(): String? {
        return try {
            context.packageManager
                .getPackageInfo(context.packageName, 0).versionName
        } catch (e: NameNotFoundException) {
            null
        }
    }

    override fun isSupportedVersion(minSupportedVersion: String?): Boolean {

        if (minSupportedVersion == null) return true

        getAppVersion()?.let { appVersion ->
            val splitAppVersion = appVersion.split(".")
            val splitMinSupportedVersion = minSupportedVersion.split(".")

            if (splitAppVersion.size < VERSION_LENGTH || splitMinSupportedVersion.size < VERSION_LENGTH) return false
            if (appVersion == minSupportedVersion) return true

            try {
                for (i in 0 until VERSION_LENGTH) {
                    if (splitAppVersion[i].toInt() > splitMinSupportedVersion[i].toInt()) {
                        return true
                    } else if (splitAppVersion[i].toInt() < splitMinSupportedVersion[i].toInt()) {
                        return false
                    }
                }
            } catch (e: NumberFormatException) {
                return false
            }
        }
        return false
    }

    companion object {
        const val VERSION_LENGTH = 3
    }
}
