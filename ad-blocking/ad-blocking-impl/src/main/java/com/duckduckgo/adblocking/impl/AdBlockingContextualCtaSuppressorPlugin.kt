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

package com.duckduckgo.adblocking.impl

import android.net.Uri
import com.duckduckgo.adblocking.impl.domain.AdBlockingStatusChecker
import com.duckduckgo.adblocking.impl.remoteconfig.AdBlockingExtensionFeature
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.onboarding.api.cta.ContextualCtaSuppressorPlugin
import com.squareup.anvil.annotations.ContributesMultibinding
import javax.inject.Inject

@ContributesMultibinding(AppScope::class)
class AdBlockingContextualCtaSuppressorPlugin @Inject constructor(
    private val statusChecker: AdBlockingStatusChecker,
    private val feature: AdBlockingExtensionFeature,
    private val domainMatcher: AdBlockingExtensionDomainMatcher,
) : ContextualCtaSuppressorPlugin {

    override suspend fun canShowCta(url: Uri): Boolean {
        if (!feature.adBlockingUXImprovements().isEnabled()) return true
        if (!statusChecker.canInject()) return true
        return !domainMatcher.matches(url)
    }
}
