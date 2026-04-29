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

package com.duckduckgo.pir.internal.settings

import androidx.core.net.toUri
import com.duckduckgo.common.utils.extensions.toTldPlusOne
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.pir.impl.dashboard.PirDashboardUrlProvider
import com.duckduckgo.pir.impl.dashboard.messaging.PirDashboardWebConstants
import com.squareup.anvil.annotations.ContributesBinding
import dagger.SingleInstanceIn
import javax.inject.Inject

@SingleInstanceIn(AppScope::class)
@ContributesBinding(
    scope = AppScope::class,
    rank = ContributesBinding.RANK_HIGHEST,
)
class InternalPirDashboardUrlProvider @Inject constructor(
    private val store: PirInternalSettingsDataStore,
) : PirDashboardUrlProvider {
    override fun getUrl(): String {
        return store.customDashboardUrl ?: PirDashboardWebConstants.DEFAULT_WEB_UI_URL
    }

    override fun getAllowedDomains(): List<String> {
        return buildList {
            add(PirDashboardWebConstants.ALLOWED_DOMAIN)
            store.customDashboardUrl?.let { url ->
                url.toUri().host?.toTldPlusOne()?.let { add(it) }
            }
        }
    }
}
