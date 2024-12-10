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
import com.duckduckgo.app.di.IsMainProcess
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.malicioussiteprotection.api.MaliciousSiteProtection
import com.duckduckgo.malicioussiteprotection.impl.MaliciousSiteProtectionFeature
import com.duckduckgo.malicioussiteprotection.impl.data.MaliciousSiteRepository
import com.duckduckgo.privacy.config.api.PrivacyConfigCallbackPlugin
import com.squareup.anvil.annotations.ContributesBinding
import com.squareup.anvil.annotations.ContributesMultibinding
import java.security.MessageDigest
import java.util.regex.Pattern
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.json.JSONObject
import timber.log.Timber

@ContributesBinding(AppScope::class, MaliciousSiteProtection::class)
@ContributesMultibinding(AppScope::class, PrivacyConfigCallbackPlugin::class)
class RealMaliciousSiteProtection @Inject constructor(
    private val dispatchers: DispatcherProvider,
    private val maliciousSiteProtectionFeature: MaliciousSiteProtectionFeature,
    @IsMainProcess private val isMainProcess: Boolean,
    @AppCoroutineScope private val appCoroutineScope: CoroutineScope,
    private val maliciousSiteRepository: MaliciousSiteRepository,
    private val messageDigest: MessageDigest,
) : MaliciousSiteProtection, PrivacyConfigCallbackPlugin {

    private var _isFeatureEnabled = false
    override val isFeatureEnabled: Boolean
        get() = _isFeatureEnabled

    private var hashPrefixUpdateFrequency = 20L
    private var filterSetUpdateFrequency = 720L

    init {
        if (isMainProcess) {
            loadToMemory()
        }
    }

    override fun onPrivacyConfigDownloaded() {
        loadToMemory()
    }

    private fun loadToMemory() {
        appCoroutineScope.launch(dispatchers.io()) {
            _isFeatureEnabled = maliciousSiteProtectionFeature.self().isEnabled()
            maliciousSiteProtectionFeature.self().getSettings()?.let {
                JSONObject(it).let { settings ->
                    hashPrefixUpdateFrequency = settings.getLong("hashPrefixUpdateFrequency")
                    filterSetUpdateFrequency = settings.getLong("filterSetUpdateFrequency")
                }
            }
        }
    }

    override suspend fun isMalicious(url: Uri, onSiteBlockedAsync: () -> Unit): Boolean {
        Timber.tag("MaliciousSiteProtection").d("isMalicious $url")

        val hostname = url.host ?: return false
        val hash = messageDigest
            .digest(hostname.toByteArray())
            .joinToString("") { "%02x".format(it) }
        val hashPrefix = hash.substring(0, 8)

        if (!maliciousSiteRepository.containsHashPrefix(hashPrefix)) {
            Timber.d("\uD83D\uDFE2 Cris: should not block (no hash) $hashPrefix,  $url")
            return false
        }
        maliciousSiteRepository.getFilter(hash)?.let {
            if (Pattern.matches(it.regex, url.toString())) {
                Timber.d("\uD83D\uDFE2 Cris: shouldBlock $url")
                return true
            }
        }
        appCoroutineScope.launch(dispatchers.io()) {
            matches(hashPrefix, url, hostname, hash).let { matches ->
                if (matches) {
                    onSiteBlockedAsync()
                }
            }
        }
        return false
    }

    private suspend fun matches(
        hashPrefix: String,
        url: Uri,
        hostname: String,
        hash: String,
    ): Boolean {
        val matches = maliciousSiteRepository.matches(hashPrefix)
        return matches.any { match ->
            Pattern.matches(match.regex, url.toString()) &&
                (hostname == match.hostname) &&
                (hash == match.hash)
        }.also { matched ->
            Timber.d("\uD83D\uDFE2 Cris: should block $matched")
        }
    }
}
