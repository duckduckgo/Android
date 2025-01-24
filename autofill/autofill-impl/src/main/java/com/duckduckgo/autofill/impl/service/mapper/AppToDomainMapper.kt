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

import com.duckduckgo.autofill.store.targets.DomainTargetAppDao
import com.duckduckgo.autofill.store.targets.DomainTargetAppEntity
import com.duckduckgo.autofill.store.targets.TargetApp
import com.duckduckgo.common.utils.CurrentTimeProvider
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.common.utils.normalizeScheme
import com.duckduckgo.di.scopes.AppScope
import com.squareup.anvil.annotations.ContributesBinding
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import timber.log.Timber

interface AppToDomainMapper {
    /**
     * Returns a list of domains whose credentials can be associated to the [appPackage]
     * You DO NOT need to set any dispatcher to call this suspend function
     */
    suspend fun getAssociatedDomains(appPackage: String): List<String>
}

@ContributesBinding(AppScope::class)
class RealAppToDomainMapper @Inject constructor(
    private val domainTargetAppDao: DomainTargetAppDao,
    private val appFingerprintProvider: AppFingerprintProvider,
    private val assetLinksLoader: AssetLinksLoader,
    private val dispatcherProvider: DispatcherProvider,
    private val currentTimeProvider: CurrentTimeProvider,
) : AppToDomainMapper {
    override suspend fun getAssociatedDomains(appPackage: String): List<String> {
        return withContext(dispatcherProvider.io()) {
            Timber.d("Autofill-mapping: Getting domains for $appPackage")
            val fingerprints = appFingerprintProvider.getSHA256HexadecimalFingerprint(appPackage)
            if (fingerprints.isNotEmpty()) {
                attemptToGetFromDataset(appPackage, fingerprints).run {
                    this.ifEmpty {
                        attemptToGetFromAssetLinks(appPackage, fingerprints)
                    }
                }
            } else {
                emptyList()
            }.distinct()
        }
    }

    private fun attemptToGetFromDataset(
        appPackage: String,
        fingerprints: List<String>,
    ): List<String> {
        Timber.d("Autofill-mapping: Attempting to get domains from dataset")
        return domainTargetAppDao.getDomainsForApp(packageName = appPackage, fingerprints = fingerprints).also {
            Timber.d("Autofill-mapping: domains from dataset for $appPackage: ${it.size}")
        }
    }

    private suspend fun attemptToGetFromAssetLinks(
        appPackage: String,
        fingerprints: List<String>,
    ): List<String> {
        val domain = kotlin.runCatching {
            appPackage.split('.').asReversed().joinToString(".").normalizeScheme().toHttpUrl().topPrivateDomain()
        }.getOrNull()
        return domain?.run {
            Timber.d("Autofill-mapping: Attempting to get asset links for: $domain")
            val validTargetApp = assetLinksLoader.getValidTargetApps(this).filter { target ->
                target.key == appPackage && target.value.any { it in fingerprints }
            }
            if (validTargetApp.isNotEmpty()) {
                Timber.d("Autofill-mapping: Valid asset links targets found for $appPackage in $domain")
                persistMatch(domain, validTargetApp)
                listOf(domain)
            } else {
                Timber.d("Autofill-mapping: No valid asset links target found for $appPackage in $domain")
                emptyList()
            }
        } ?: emptyList()
    }

    private fun persistMatch(
        domain: String,
        validTargets: Map<String, List<String>>,
    ) = runCatching {
        val toPersist = mutableListOf<DomainTargetAppEntity>()
        validTargets.forEach { (packageName, fingerprints) ->
            fingerprints.forEach { fingerprint ->
                toPersist.add(
                    DomainTargetAppEntity(
                        domain = domain,
                        targetApp = TargetApp(
                            packageName = packageName,
                            sha256CertFingerprints = fingerprint,
                        ),
                        dataExpiryInMillis = currentTimeProvider.currentTimeMillis() + TimeUnit.DAYS.toMillis(EXPIRY_IN_DAYS),
                    ),
                )
            }
        }
        domainTargetAppDao.insertAllMapping(toPersist)
    }.onFailure {
        // IF it fails for any reason, caching fails but the app should not.
        Timber.e("Autofill-mapping: Failed to persist data for $domain")
    }

    companion object {
        private const val EXPIRY_IN_DAYS = 30L
    }
}
