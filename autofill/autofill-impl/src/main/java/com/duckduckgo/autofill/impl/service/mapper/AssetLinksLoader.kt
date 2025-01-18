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

package com.duckduckgo.autofill.impl.service.mapper

import com.duckduckgo.common.utils.normalizeScheme
import com.duckduckgo.di.scopes.AppScope
import com.squareup.anvil.annotations.ContributesBinding
import dagger.SingleInstanceIn
import javax.inject.Inject
import timber.log.Timber

interface AssetLinksLoader {
    suspend fun getValidTargetApps(domain: String): Map<String, List<String>>
}

@SingleInstanceIn(AppScope::class)
@ContributesBinding(AppScope::class)
class RealAssetLinksLoader @Inject constructor(
    private val assetLinksService: AssetLinksService,
) : AssetLinksLoader {
    override suspend fun getValidTargetApps(domain: String): Map<String, List<String>> {
        return kotlin.runCatching {
            assetLinksService.getAssetLinks("${domain.normalizeScheme()}+$ASSET_LINKS_PATH").filter {
                (it.relation.contains(LOGIN_CREDENTIALS_RELATION) || it.relation.contains(HANDLE_URLS_RELATION)) &&
                    !it.target.package_name.isNullOrEmpty() &&
                    !it.target.sha256_cert_fingerprints.isNullOrEmpty() &&
                    it.target.package_name == APP_NAMESPACE
            }.associate { it.target.package_name!! to it.target.sha256_cert_fingerprints!! }
        }.getOrElse {
            Timber.e(it, "Autofill-mapping: Failed to obtain assetlinks for: $domain")
            emptyMap()
        }
    }

    companion object {
        private const val ASSET_LINKS_PATH = "/.well-known/assetlinks.json"
        private const val LOGIN_CREDENTIALS_RELATION = "delegate_permission/common.get_login_creds"
        private const val HANDLE_URLS_RELATION = "delegate_permission/common.handle_all_urls"
        private const val APP_NAMESPACE = "android_app"
    }
}
