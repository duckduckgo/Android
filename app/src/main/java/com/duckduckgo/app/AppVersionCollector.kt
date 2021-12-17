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

package com.duckduckgo.app

import com.duckduckgo.app.browser.BuildConfig
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.mobile.android.vpn.state.VpnStateCollectorPlugin
import com.squareup.anvil.annotations.ContributesMultibinding
import javax.inject.Inject
import org.json.JSONObject

@ContributesMultibinding(AppScope::class)
class AppVersionCollector @Inject constructor() : VpnStateCollectorPlugin {
    override val collectorName: String
        get() = "appInfo"

    override suspend fun collectVpnRelatedState(appPackageId: String?): JSONObject {
        return JSONObject().apply { put("appVersion", BuildConfig.VERSION_NAME) }
    }
}
