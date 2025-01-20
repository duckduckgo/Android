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

import android.content.Context
import com.duckduckgo.appbuildconfig.api.AppBuildConfig
import com.duckduckgo.autofill.store.targets.DomainTargetAppDao
import com.duckduckgo.autofill.store.targets.DomainTargetAppEntity
import com.duckduckgo.autofill.store.targets.TargetApp
import com.duckduckgo.common.utils.CurrentTimeProvider
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.common.utils.extractDomain
import com.duckduckgo.common.utils.normalizeScheme
import com.duckduckgo.di.scopes.AppScope
import com.squareup.anvil.annotations.ContributesBinding
import dagger.SingleInstanceIn
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import kotlinx.coroutines.withContext
import logcat.logcat
import okhttp3.HttpUrl.Companion.toHttpUrl

interface AppToDomainMapper {
    suspend fun getAssociatedDomains(appPackage: String): List<String>
}

@SingleInstanceIn(AppScope::class)
@ContributesBinding(AppScope::class)
class RealAppToDomainMapper @Inject constructor(
    private val domainTargetAppDao: DomainTargetAppDao,
    private val context: Context,
    private val appBuildConfig: AppBuildConfig,
    private val assetLinksLoader: AssetLinksLoader,
    private val dispatcherProvider: DispatcherProvider,
    private val currentTimeProvider: CurrentTimeProvider,
) : AppToDomainMapper {
    override suspend fun getAssociatedDomains(appPackage: String): List<String> {
        return context.packageManager.getSHA256HexadecimalFingerprintCompat(appPackage, appBuildConfig)?.let { fingerprint ->
            logcat { "Autofill-mapping: Getting domains for $appPackage : $fingerprint" }
            attemptToGetFromDataset(appPackage, fingerprint).apply {
                if (this.isEmpty()) { // TODO: optionally add kill switch for this - in case formats for assetlinks breaks/ changes
                    attemptToGetFromAssetLinks(appPackage, fingerprint)
                }
            }
        }?.apply {
            this.map {
                it.extractDomain()
            }
        }?.distinct() ?: emptyList()
    }

    private fun attemptToGetFromDataset(
        appPackage: String,
        fingerprint: String,
    ): List<String> {
        logcat { "Autofill-mapping: Attempting to get domains from dataset" }
        return domainTargetAppDao.getDomainsForApp(packageName = appPackage, fingerprint).also {
            logcat { "Autofill-mapping: domains from dataset for $appPackage: $it" }
        }
    }

    private suspend fun attemptToGetFromAssetLinks(
        appPackage: String,
        fingerprint: String,
    ): List<String> {
        val domain = appPackage.split('.').asReversed().joinToString(".").normalizeScheme().toHttpUrl().topPrivateDomain()
        return domain?.run {
            logcat { "Autofill-mapping: Attempting to get asset links for: $domain" }
            val validTargetApp = assetLinksLoader.getValidTargetApps(this).also {
                logcat { "Autofill-mapping: Valid target apps from assetlinks of $domain: $it" }
            }.filter {
                it.key == appPackage && fingerprint.contains(fingerprint)
            }
            if (validTargetApp.isNotEmpty()) {
                logcat { "Autofill-mapping: Valid asset links targets found for $appPackage in $domain" }
                persistMatch(domain, validTargetApp)
                listOf(domain)
            } else {
                logcat { "Autofill-mapping: No valid asset links target found for $appPackage in $domain" }
                emptyList()
            }
        } ?: emptyList()
    }

    private suspend fun persistMatch(
        domain: String,
        validTargets: Map<String, List<String>>,
    ) = withContext(dispatcherProvider.io()) {
        runCatching {
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
            logcat { "Autofill-mapping: Failed to persist data for $domain" }
        }
    }

    companion object {
        private const val EXPIRY_IN_DAYS = 30L
    }
}
