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

package com.duckduckgo.app.browser.useragent

import android.os.Build
import com.duckduckgo.app.global.device.DeviceInfo


/**
 * Example Default User Agent (From Chrome):
 * Mozilla/5.0 (Linux; Android 8.1.0; Nexus 6P Build/OPM3.171019.014) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/64.0.3282.137 Mobile Safari/537.36
 *
 * Example Default Desktop User Agent (From Chrome):
 * Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/64.0.3282.137 Safari/537.36
 */
class UserAgentProvider constructor(private val defaultUserAgent: String, private val device: DeviceInfo) {

    /**
     * Returns a modified UA string which omits the user's device make and model
     * If the user is requesting a desktop site, we add generic X11 Linux indicator, but include the real architecture
     * If the user is requesting a mobile site, we add Linux Android indicator, and include the real Android OS version
     *
     * We include everything from the original UA string from AppleWebKit onwards (omitting if missing)
     */
    fun getUserAgent(desktopSiteRequested: Boolean = false): String {

        val platform = if (desktopSiteRequested) desktopUaPrefix() else mobileUaPrefix()
        val userAgentStringSuffix = "${getWebKitVersionOnwards(desktopSiteRequested)} ${getApplicationSuffix()}"

        return "$MOZILLA_PREFIX ($platform)$userAgentStringSuffix"
    }

    private fun mobileUaPrefix() = "Linux; Android ${Build.VERSION.RELEASE}"

    private fun desktopUaPrefix() = "X11; Linux ${System.getProperty("os.arch")}"

    private fun getWebKitVersionOnwards(desktopSiteRequested: Boolean): String {
        val matches = WEB_KIT_REGEX.find(defaultUserAgent) ?: return ""
        var result = matches.groupValues[0]
        if (desktopSiteRequested) {
            result = result.replace(" Mobile ", " ")
        }
        return " $result"
    }

    private fun getApplicationSuffix(): String {
        return "DuckDuckGo/${device.majorAppVersion}"
    }

    companion object {
        private val WEB_KIT_REGEX = Regex("AppleWebKit/.*")

        private const val MOZILLA_PREFIX = "Mozilla/5.0"
    }

}