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

package com.duckduckgo.adblocking.impl.sponsorblock

import com.duckduckgo.di.scopes.AppScope
import dev.zacsweers.metro.ContributesBinding
import javax.inject.Inject

interface SponsorBlockHashPrefixGate {
    /** Whether any video whose ID hash starts with [hashPrefix] has segments; when false, the lookup skips the network. */
    suspend fun hasSegments(hashPrefix: String): Boolean
}

// TODO: replace with a gate backed by the local hash prefix list.
@ContributesBinding(AppScope::class)
class AlwaysHitSponsorBlockHashPrefixGate @Inject constructor() : SponsorBlockHashPrefixGate {
    override suspend fun hasSegments(hashPrefix: String): Boolean = true
}
