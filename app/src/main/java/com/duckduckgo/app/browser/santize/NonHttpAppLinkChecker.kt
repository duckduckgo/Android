/*
 * Copyright (c) 2025 DuckDuckGo
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

package com.duckduckgo.app.browser.santize

import android.content.Intent
import com.duckduckgo.appbuildconfig.api.AppBuildConfig
import com.duckduckgo.di.scopes.FragmentScope
import com.squareup.anvil.annotations.ContributesBinding
import logcat.LogPriority.WARN
import logcat.logcat
import javax.inject.Inject

interface NonHttpAppLinkChecker {
    fun isPermitted(intent: Intent): Boolean
}

@ContributesBinding(FragmentScope::class)
class RealNonHttpAppLinkChecker @Inject constructor(
    private val appBuildConfig: AppBuildConfig,
) : NonHttpAppLinkChecker {

    override fun isPermitted(intent: Intent): Boolean {
        val internalContentFileProvider = CONTENT_PREFIX + appBuildConfig.applicationId + CONTENT_SUFFIX

        // check main data URI
        if (intent.dataString?.contains(internalContentFileProvider, ignoreCase = true) == true) {
            logcat(WARN) { "Refusing to open an internal content URI from external app" }
            return false
        }

        // Check clipData URIs
        intent.clipData?.let { clipData ->
            for (i in 0 until clipData.itemCount) {
                clipData.getItemAt(i).uri?.toString()?.let { uriString ->
                    if (uriString.contains(internalContentFileProvider, ignoreCase = true)) {
                        logcat(WARN) { "Refusing to open an internal content URI in clipData from external app" }
                        return false
                    }
                }
            }
        }

        return true
    }

    companion object {
        private const val CONTENT_PREFIX = "content://"
        private const val CONTENT_SUFFIX = ".provider"
    }
}
