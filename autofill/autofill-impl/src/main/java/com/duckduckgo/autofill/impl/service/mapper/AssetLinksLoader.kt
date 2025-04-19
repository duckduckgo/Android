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

import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.common.utils.normalizeScheme
import com.duckduckgo.di.scopes.AppScope
import com.squareup.anvil.annotations.ContributesBinding
import javax.inject.Inject
import kotlinx.coroutines.withContext
import timber.log.Timber

interface AssetLinksLoader {
    /**
     * Return a map of app packages to associated valid fingerprints from the [domain]'s assetlinks
     */
    suspend fun getValidTargetApps(domain: String): Map<String, List<String>>
}

@ContributesBinding(AppScope::class)
class RealAssetLinksLoader @Inject constructor(
    private val assetLinksService: AssetLinksService,
    private val dispatcherProvider: DispatcherProvider,
) : AssetLinksLoader {
    override suspend fun getValidTargetApps(domain: String): Map<String, List<String>> {
        return withContext(dispatcherProvider.io()) {
            kotlin.runCatching {
                assetLinksService.getAssetLinks("${domain.normalizeScheme()}$ASSET_LINKS_PATH").also {
                    Timber.d("Autofill-mapping: Assetlinks of $domain: ${it.size}")
                }.filter {
                    it.relation.any { relation -> relation in supportedRelations } &&
                        !it.target.package_name.isNullOrEmpty() &&
                        !it.target.sha256_cert_fingerprints.isNullOrEmpty() &&
                        it.target.namespace == APP_NAMESPACE
                }.associate { it.target.package_name!! to it.target.sha256_cert_fingerprints!! }
            }.getOrElse {
                // This can fail for a lot of reasons: invalid url from package name, absence of assetlinks, malformed assetlinks
                // If it does, we don't want to crash the app. We only want to return empty
                Timber.e(it, "Autofill-mapping: Failed to obtain assetlinks for: $domain")
                emptyMap()
            }
        }
    }

    companion object {
        private const val ASSET_LINKS_PATH = "/.well-known/assetlinks.json"
        private const val LOGIN_CREDENTIALS_RELATION = "delegate_permission/common.get_login_creds"
        private const val HANDLE_ALL_RELATION = "delegate_permission/common.handle_all_urls"
        private val supportedRelations = listOf(HANDLE_ALL_RELATION, LOGIN_CREDENTIALS_RELATION)
        private const val APP_NAMESPACE = "android_app"
    }
}
