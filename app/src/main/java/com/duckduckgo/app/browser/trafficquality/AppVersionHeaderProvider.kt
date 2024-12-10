/*
 * Copyright (c) 2024 DuckDuckGo
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

package com.duckduckgo.app.browser.trafficquality

import com.duckduckgo.appbuildconfig.api.AppBuildConfig
import com.duckduckgo.di.scopes.AppScope
import com.squareup.anvil.annotations.ContributesBinding
import javax.inject.Inject

interface AppVersionHeaderProvider {
    fun provide(isStub: Boolean): String
}

@ContributesBinding(AppScope::class)
class RealAppVersionHeaderProvider @Inject constructor(
    private val appBuildConfig: AppBuildConfig,

) : AppVersionHeaderProvider {
    override fun provide(isStub: Boolean): String {
        return if (isStub) {
            APP_VERSION_QUALITY_DEFAULT_VALUE
        } else {
            appBuildConfig.versionName
        }
    }

    companion object {
        const val APP_VERSION_QUALITY_DEFAULT_VALUE = "other_versions"
    }
}
