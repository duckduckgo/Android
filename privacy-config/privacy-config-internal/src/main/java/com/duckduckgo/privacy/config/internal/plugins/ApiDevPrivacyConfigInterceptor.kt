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

package com.duckduckgo.privacy.config.internal.plugins

import android.webkit.URLUtil
import com.duckduckgo.app.global.api.ApiInterceptorPlugin
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.privacy.config.api.PRIVACY_REMOTE_CONFIG_URL
import com.duckduckgo.privacy.config.internal.store.DevPrivacyConfigSettingsDataStore
import com.squareup.anvil.annotations.ContributesMultibinding
import okhttp3.Interceptor
import okhttp3.Response
import java.net.URI
import javax.inject.Inject

@ContributesMultibinding(
    scope = AppScope::class,
    boundType = ApiInterceptorPlugin::class,
)
class ApiDevPrivacyConfigInterceptor @Inject constructor(
    private val devSettingsDataStore: DevPrivacyConfigSettingsDataStore,
) : ApiInterceptorPlugin, Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request().newBuilder()

        val url = chain.request().url
        val storedUrl = devSettingsDataStore.remotePrivacyConfigUrl
        val isCustomSettingEnabled = devSettingsDataStore.useCustomPrivacyConfigUrl
        val validHost = runCatching { URI(storedUrl).host.isNotEmpty() }.getOrDefault(false)
        val canUrlBeChanged = isCustomSettingEnabled && !storedUrl.isNullOrEmpty() && URLUtil.isValidUrl(storedUrl) && validHost

        if (url.toString().contains(PRIVACY_REMOTE_CONFIG_URL) && canUrlBeChanged) {
            request.url(storedUrl!!)
            return chain.proceed(request.build())
        }

        return chain.proceed(chain.request())
    }

    override fun getInterceptor(): Interceptor {
        return this
    }
}
