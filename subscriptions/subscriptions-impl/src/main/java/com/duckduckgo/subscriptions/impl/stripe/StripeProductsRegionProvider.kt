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

package com.duckduckgo.subscriptions.impl.stripe

import com.duckduckgo.di.scopes.AppScope
import dev.zacsweers.metro.ContributesBinding
import javax.inject.Inject

interface StripeProductsRegionProvider {
    /** Returns the region to request Stripe products for. */
    suspend fun region(): String
}

@ContributesBinding(AppScope::class)
class RealStripeProductsRegionProvider @Inject constructor() : StripeProductsRegionProvider {

    //us hardcoded until Stripe International launches
    override suspend fun region(): String = US_REGION

    companion object {
        const val US_REGION = "us"
    }
}
