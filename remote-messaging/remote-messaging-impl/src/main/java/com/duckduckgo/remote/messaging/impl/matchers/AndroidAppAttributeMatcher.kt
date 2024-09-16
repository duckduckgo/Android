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

package com.duckduckgo.remote.messaging.impl.matchers

import com.duckduckgo.appbuildconfig.api.AppBuildConfig
import com.duckduckgo.browser.api.AppProperties
import com.duckduckgo.remote.messaging.api.AttributeMatcherPlugin
import com.duckduckgo.remote.messaging.api.MatchingAttribute
import com.duckduckgo.remote.messaging.impl.models.*

class AndroidAppAttributeMatcher(
    private val appProperties: AppProperties,
    private val appBuildConfig: AppBuildConfig,
) : AttributeMatcherPlugin {
    override suspend fun evaluate(matchingAttribute: MatchingAttribute): Boolean? {
        return when (matchingAttribute) {
            is Flavor -> {
                matchingAttribute.matches(appBuildConfig.flavor.toString())
            }
            is AppId -> {
                matchingAttribute.matches(appBuildConfig.applicationId)
            }
            is AppVersion -> {
                matchingAttribute.matches(appBuildConfig.versionName)
            }
            is Atb -> {
                matchingAttribute.matches(appProperties.atb())
            }
            is AppAtb -> {
                matchingAttribute.matches(appProperties.appAtb())
            }
            is SearchAtb -> {
                matchingAttribute.matches(appProperties.searchAtb())
            }
            is ExpVariant -> {
                matchingAttribute.matches(appProperties.expVariant())
            }
            is InstalledGPlay -> {
                matchingAttribute.matches(appProperties.installedGPlay())
            }
            else -> return null
        }
    }
}
