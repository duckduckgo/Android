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

package com.duckduckgo.app.browser

import com.duckduckgo.app.pixels.remoteconfig.AndroidBrowserConfigFeature
import com.duckduckgo.common.utils.AppUrl
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.duckchat.api.DuckChat
import com.squareup.anvil.annotations.ContributesBinding
import javax.inject.Inject

/**
 * Decides which DuckDuckGo host a search query should be sent to.
 *
 * When the [AndroidBrowserConfigFeature.noAiSerpHost] flag is enabled and Duck.ai is disabled,
 * queries are routed to [AppUrl.Url.NO_AI_HOST] so the server can serve an AI-free SERP.
 */
interface DuckDuckGoSerpHostProvider {
    /** `true` when the noAiSerpHost flag is enabled AND Duck.ai is disabled. */
    fun shouldUseNoAiHost(): Boolean

    /** [AppUrl.Url.NO_AI_HOST] when [shouldUseNoAiHost], else [AppUrl.Url.HOST]. */
    fun searchHost(): String
}

@ContributesBinding(AppScope::class)
class RealDuckDuckGoSerpHostProvider @Inject constructor(
    private val duckChat: DuckChat,
    private val androidBrowserConfigFeature: AndroidBrowserConfigFeature,
) : DuckDuckGoSerpHostProvider {

    override fun shouldUseNoAiHost(): Boolean =
        androidBrowserConfigFeature.noAiSerpHost().isEnabled() && !duckChat.isEnabled()

    override fun searchHost(): String =
        if (shouldUseNoAiHost()) AppUrl.Url.NO_AI_HOST else AppUrl.Url.HOST
}
