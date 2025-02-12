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

package com.duckduckgo.malicioussiteprotection.impl.domain

import android.net.Uri
import com.duckduckgo.app.di.AppCoroutineScope
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.malicioussiteprotection.api.MaliciousSiteProtection
import com.duckduckgo.malicioussiteprotection.api.MaliciousSiteProtection.Feed
import com.duckduckgo.malicioussiteprotection.api.MaliciousSiteProtection.IsMaliciousResult
import com.duckduckgo.malicioussiteprotection.api.MaliciousSiteProtection.IsMaliciousResult.ConfirmedResult
import com.duckduckgo.malicioussiteprotection.api.MaliciousSiteProtection.MaliciousStatus
import com.duckduckgo.malicioussiteprotection.api.MaliciousSiteProtection.MaliciousStatus.Malicious
import com.duckduckgo.malicioussiteprotection.api.MaliciousSiteProtection.MaliciousStatus.Safe
import com.duckduckgo.malicioussiteprotection.impl.MaliciousSiteProtectionRCFeature
import com.duckduckgo.malicioussiteprotection.impl.data.MaliciousSiteRepository
import com.squareup.anvil.annotations.ContributesBinding
import java.security.MessageDigest
import java.util.regex.Pattern
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import timber.log.Timber

@ContributesBinding(AppScope::class, MaliciousSiteProtection::class)
class RealMaliciousSiteProtection @Inject constructor(
    private val dispatchers: DispatcherProvider,
    @AppCoroutineScope private val appCoroutineScope: CoroutineScope,
    private val maliciousSiteRepository: MaliciousSiteRepository,
    private val messageDigest: MessageDigest,
    private val maliciousSiteProtectionRCFeature: MaliciousSiteProtectionRCFeature,
) : MaliciousSiteProtection {

    private val timber = Timber.tag("MaliciousSiteProtection")

    override fun isFeatureEnabled(): Boolean {
        return maliciousSiteProtectionRCFeature.isFeatureEnabled()
    }

    override suspend fun isMalicious(
        url: Uri,
        confirmationCallback: (confirmedResult: MaliciousStatus) -> Unit,
    ): IsMaliciousResult {
        timber.d("isMalicious $url")

        if (!maliciousSiteProtectionRCFeature.isFeatureEnabled()) {
            timber.d("should not block (feature disabled) $url")
            return ConfirmedResult(Safe)
        }

        val hostname = url.host ?: return ConfirmedResult(Safe)
        val hash = messageDigest
            .digest(hostname.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
        val hashPrefix = hash.substring(0, 8)

        if (!maliciousSiteRepository.containsHashPrefix(hashPrefix)) {
            timber.d("should not block (no hash) $hashPrefix,  $url")
            return ConfirmedResult(Safe)
        }
        maliciousSiteRepository.getFilters(hash)?.forEach { filterSet ->
            filterSet.filters.firstOrNull {
                Pattern.compile(it.regex).matcher(url.toString()).find()
            }?.let {
                timber.d("should block $url")
                return ConfirmedResult(Malicious(filterSet.feed))
            }
        }
        appCoroutineScope.launch(dispatchers.io()) {
            try {
                val result = matches(hashPrefix, url, hostname, hash)?.let { feed: Feed ->
                    Malicious(feed)
                } ?: Safe
                confirmationCallback(result)
            } catch (e: Exception) {
                timber.e(e, "shouldBlock $url")
                confirmationCallback(Safe)
            }
        }
        return IsMaliciousResult.WaitForConfirmation
    }

    private suspend fun matches(
        hashPrefix: String,
        url: Uri,
        hostname: String,
        hash: String,
    ): Feed? {
        val matches = maliciousSiteRepository.matches(hashPrefix.substring(0, 4))
        return matches.firstOrNull { match ->
            Pattern.compile(match.regex).matcher(url.toString()).find() &&
                (hostname == match.hostname) &&
                (hash == match.hash)
        }?.feed
    }
}
