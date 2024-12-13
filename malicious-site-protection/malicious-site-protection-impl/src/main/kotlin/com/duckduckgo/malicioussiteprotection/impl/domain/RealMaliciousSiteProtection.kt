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
import com.duckduckgo.malicioussiteprotection.api.MaliciousSiteProtection.IsMaliciousResult
import com.duckduckgo.malicioussiteprotection.impl.data.MaliciousSiteRepository
import com.squareup.anvil.annotations.ContributesBinding
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import timber.log.Timber
import java.security.MessageDigest
import java.util.regex.Pattern
import javax.inject.Inject

@ContributesBinding(AppScope::class, MaliciousSiteProtection::class)
class RealMaliciousSiteProtection @Inject constructor(
    private val dispatchers: DispatcherProvider,
    @AppCoroutineScope private val appCoroutineScope: CoroutineScope,
    private val maliciousSiteRepository: MaliciousSiteRepository,
    private val messageDigest: MessageDigest,
) : MaliciousSiteProtection {

    override suspend fun isMalicious(url: Uri, confirmationCallback: (isMalicious: Boolean) -> Unit): IsMaliciousResult {
        Timber.tag("MaliciousSiteProtection").d("isMalicious $url")

        val hostname = url.host ?: return IsMaliciousResult.SAFE
        val hash = messageDigest
            .digest(hostname.toByteArray())
            .joinToString("") { "%02x".format(it) }
        val hashPrefix = hash.substring(0, 8)

        if (!maliciousSiteRepository.containsHashPrefix(hashPrefix)) {
            Timber.d("\uD83D\uDFE2 Cris: should not block (no hash) $hashPrefix,  $url")
            return IsMaliciousResult.SAFE
        }
        maliciousSiteRepository.getFilter(hash)?.let {
            if (Pattern.compile(it.regex).matcher(url.toString()).find()) {
                Timber.d("\uD83D\uDFE2 Cris: shouldBlock $url")
                return IsMaliciousResult.MALICIOUS
            }
        }
        appCoroutineScope.launch(dispatchers.io()) {
            confirmationCallback(matches(hashPrefix, url, hostname, hash))
        }
        return IsMaliciousResult.WAIT_FOR_CONFIRMATION
    }

    private suspend fun matches(
        hashPrefix: String,
        url: Uri,
        hostname: String,
        hash: String,
    ): Boolean {
        val matches = maliciousSiteRepository.matches(hashPrefix)
        return matches.any { match ->
            Pattern.compile(match.regex).matcher(url.toString()).find() &&
                (hostname == match.hostname) &&
                (hash == match.hash)
        }.also { matched ->
            Timber.d("\uD83D\uDFE2 Cris: should block $matched")
        }
    }
}
