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

package com.duckduckgo.pir.impl.common

import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.pir.impl.PirRemoteFeatures
import com.duckduckgo.pir.impl.models.Broker
import com.duckduckgo.user.agent.api.UserAgentProvider
import dev.zacsweers.metro.ContributesBinding
import kotlinx.coroutines.withContext
import logcat.logcat
import javax.inject.Inject

/**
 * The user agent a PIR WebView runs a broker with.
 *
 * @param clientHints the client hints matching [userAgent], or null to keep the WebView's own.
 */
data class PirWebViewUserAgent(
    val userAgent: String,
    val clientHints: PirClientHints? = null,
)

interface PirBrokerUserAgentResolver {
    /**
     * Returns the user agent the PIR WebView should use while running steps for [broker]: the
     * broker's own user agent when it defines one, otherwise the default PIR user agent.
     */
    suspend fun resolve(broker: Broker): PirWebViewUserAgent
}

@ContributesBinding(AppScope::class)
class RealPirBrokerUserAgentResolver @Inject constructor(
    private val userAgentProvider: UserAgentProvider,
    private val pirRemoteFeatures: PirRemoteFeatures,
    private val dispatcherProvider: DispatcherProvider,
) : PirBrokerUserAgentResolver {

    override suspend fun resolve(broker: Broker): PirWebViewUserAgent = withContext(dispatcherProvider.io()) {
        val brokerUserAgent = broker.userAgent?.takeIf { it.isNotBlank() }
        if (brokerUserAgent != null && pirRemoteFeatures.customWebViewUserAgent().isEnabled()) {
            val clientHints = if (pirRemoteFeatures.customWebViewUserAgentClientHints().isEnabled()) {
                PirClientHintsParser.parse(brokerUserAgent)
            } else {
                null
            }
            logcat { "PIR-RUNNER: Using broker user agent for ${broker.name}: $brokerUserAgent, client hints: $clientHints" }
            PirWebViewUserAgent(brokerUserAgent, clientHints)
        } else {
            PirWebViewUserAgent(userAgentProvider.userAgent())
        }
    }
}
