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
import android.util.LruCache
import com.duckduckgo.app.di.AppCoroutineScope
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.malicioussiteprotection.api.MaliciousSiteProtection
import com.duckduckgo.malicioussiteprotection.api.MaliciousSiteProtection.Feed
import com.duckduckgo.malicioussiteprotection.api.MaliciousSiteProtection.Feed.SCAM
import com.duckduckgo.malicioussiteprotection.api.MaliciousSiteProtection.IsMaliciousResult
import com.duckduckgo.malicioussiteprotection.api.MaliciousSiteProtection.IsMaliciousResult.ConfirmedResult
import com.duckduckgo.malicioussiteprotection.api.MaliciousSiteProtection.MaliciousStatus
import com.duckduckgo.malicioussiteprotection.api.MaliciousSiteProtection.MaliciousStatus.Ignored
import com.duckduckgo.malicioussiteprotection.api.MaliciousSiteProtection.MaliciousStatus.Malicious
import com.duckduckgo.malicioussiteprotection.api.MaliciousSiteProtection.MaliciousStatus.Safe
import com.duckduckgo.malicioussiteprotection.impl.MaliciousSiteProtectionRCFeature
import com.duckduckgo.malicioussiteprotection.impl.data.MaliciousSiteRepository
import com.duckduckgo.malicioussiteprotection.impl.models.MatchesResult
import com.duckduckgo.malicioussiteprotection.impl.models.MatchesResult.Result
import com.duckduckgo.malicioussiteprotection.impl.remoteconfig.MaliciousSiteProtectionRCRepository
import com.squareup.anvil.annotations.ContributesBinding
import dagger.SingleInstanceIn
import java.security.MessageDigest
import java.util.regex.Pattern
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import timber.log.Timber

class WriteInProgressException : Exception("Write in progress")

@ContributesBinding(AppScope::class, MaliciousSiteProtection::class)
interface InternalMaliciousSiteProtection : MaliciousSiteProtection {
    suspend fun loadFilters(vararg feeds: Feed): kotlin.Result<Unit>
    suspend fun loadHashPrefixes(vararg feeds: Feed): kotlin.Result<Unit>
}

@SingleInstanceIn(AppScope::class)
@ContributesBinding(AppScope::class, InternalMaliciousSiteProtection::class)
class RealMaliciousSiteProtection @Inject constructor(
    private val dispatchers: DispatcherProvider,
    @AppCoroutineScope private val appCoroutineScope: CoroutineScope,
    private val maliciousSiteRepository: MaliciousSiteRepository,
    private val maliciousSiteProtectionRCRepository: MaliciousSiteProtectionRCRepository,
    private val messageDigest: MessageDigest,
    private val maliciousSiteProtectionRCFeature: MaliciousSiteProtectionRCFeature,
    private val urlCanonicalization: UrlCanonicalization,
) : InternalMaliciousSiteProtection {

    private val timber = Timber.tag("MaliciousSiteProtection")
    private val lruCache = LruCache<String, MaliciousStatus>(10)

    override fun isFeatureEnabled(): Boolean {
        return maliciousSiteProtectionRCFeature.isFeatureEnabled()
    }

    override suspend fun isMalicious(
        url: Uri,
        confirmationCallback: (confirmedResult: MaliciousStatus) -> Unit,
    ): IsMaliciousResult {
        timber.d("isMalicious $url, ${this.hashCode()}")

        if (!maliciousSiteProtectionRCFeature.isFeatureEnabled()) {
            timber.d("should not block (feature disabled) $url")
            return ConfirmedResult(Ignored)
        }

        val canonicalUri = urlCanonicalization.canonicalizeUrl(url)
        val canonicalUriString = canonicalUri.toString()

        val hostname = canonicalUri.host ?: return ConfirmedResult(Safe)

        if (maliciousSiteProtectionRCFeature.isCachingEnabled()) {
            lruCache.get(canonicalUriString)?.let {
                timber.d("Cached result for $canonicalUriString")
                return ConfirmedResult(it)
            }
        }

        if (maliciousSiteProtectionRCRepository.isExempted(hostname)) {
            timber.d("should not block (exempted) $hostname")
            cacheResult(canonicalUriString, Safe)
            return ConfirmedResult(Safe)
        }

        val hash = generateHash(hostname)
        val hashPrefix = hash.substring(0, 8)

        try {
            maliciousSiteRepository.getFeedForHashPrefix(hashPrefix).let {
                if (it == null) {
                    timber.d("should not block (no hash) $hashPrefix,  $canonicalUri")
                    cacheResult(canonicalUriString, Safe)
                    return ConfirmedResult(Safe)
                } else if (it == SCAM && !maliciousSiteProtectionRCFeature.scamProtectionEnabled()) {
                    timber.d("should not block (scam protection disabled) $canonicalUri")
                    cacheResult(canonicalUriString, Ignored)
                    return ConfirmedResult(Ignored)
                }
            }
            maliciousSiteRepository.getFilters(hash)?.let { filterSet ->
                filterSet.filters.let {
                    if (Pattern.compile(it.regex).matcher(canonicalUriString).find()) {
                        timber.d("should block $canonicalUriString")
                        cacheResult(canonicalUriString, Malicious(filterSet.feed))
                        return ConfirmedResult(Malicious(filterSet.feed))
                    }
                }
            }
        } catch (e: WriteInProgressException) {
            timber.d("Write in progress, ignoring")
            // We don't want to cache these
            return ConfirmedResult(Ignored)
        }
        appCoroutineScope.launch(dispatchers.io()) {
            try {
                val result = when (val matches = maliciousSiteRepository.matches(hashPrefix.substring(0, 4))) {
                    is Result -> matches.matches.firstOrNull { match ->
                        Pattern.compile(match.regex).matcher(canonicalUriString).find() &&
                            (hostname == match.hostname) &&
                            (hash == match.hash)
                    }?.feed?.let { feed: Feed ->
                        if (feed == SCAM && !maliciousSiteProtectionRCFeature.scamProtectionEnabled()) return@let Ignored
                        return@let Malicious(feed)
                    } ?: Safe
                    is MatchesResult.Ignored -> Ignored
                }

                when (result) {
                    is Malicious -> {
                        cacheResult(canonicalUriString, result)
                        timber.d("should block (matches) $canonicalUriString, result: ${result.feed}")
                    }
                    is Safe -> {
                        cacheResult(canonicalUriString, result)
                        timber.d("should not block (no match) $canonicalUriString")
                    }
                    is Ignored -> timber.d("should not block (ignored) $canonicalUriString")
                }
                confirmationCallback(result)
            } catch (e: Exception) {
                timber.e(e, "shouldBlock $canonicalUriString")
                confirmationCallback(Safe)
            }
        }
        timber.d("wait for confirmation $canonicalUriString")
        return IsMaliciousResult.WaitForConfirmation
    }

    override suspend fun loadFilters(vararg feeds: Feed): kotlin.Result<Unit> {
        return maliciousSiteRepository.loadFilters(*feeds).also {
            lruCache.evictAll()
        }
    }

    override suspend fun loadHashPrefixes(vararg feeds: Feed): kotlin.Result<Unit> {
        return maliciousSiteRepository.loadHashPrefixes(*feeds).also {
            lruCache.evictAll()
        }
    }

    private fun cacheResult(
        canonicalUriString: String,
        result: MaliciousStatus,
    ) {
        if (maliciousSiteProtectionRCFeature.isCachingEnabled()) {
            lruCache.put(canonicalUriString, result)
        }
    }

    private fun generateHash(hostname: String): String {
        val digestBytes = messageDigest.digest(hostname.toByteArray(Charsets.UTF_8))
        val sb = StringBuilder(digestBytes.size * 2)
        for (byte in digestBytes) {
            val value = byte.toInt() and 0xFF
            sb.append(Character.forDigit(value shr 4, 16))
            sb.append(Character.forDigit(value and 0x0F, 16))
        }
        return sb.toString()
    }
}
