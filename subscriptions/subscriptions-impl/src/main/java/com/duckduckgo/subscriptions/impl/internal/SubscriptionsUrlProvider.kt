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

package com.duckduckgo.subscriptions.impl.internal

import com.duckduckgo.di.scopes.AppScope
import com.squareup.anvil.annotations.ContributesBinding
import javax.inject.Inject

interface SubscriptionsUrlProvider {
    val buyUrl: String
    val welcomeUrl: String
    val activateUrl: String
    val manageUrl: String
}

@ContributesBinding(AppScope::class)
class RealSubscriptionsUrlProvider @Inject constructor(
    subscriptionsBaseUrl: SubscriptionsBaseUrl,
) : SubscriptionsUrlProvider {
    override val buyUrl: String = subscriptionsBaseUrl.subscriptionsBaseUrl

    override val welcomeUrl: String = "${subscriptionsBaseUrl.subscriptionsBaseUrl}/welcome"

    override val activateUrl: String = "${subscriptionsBaseUrl.subscriptionsBaseUrl}/activation-flow"

    override val manageUrl: String = "${subscriptionsBaseUrl.subscriptionsBaseUrl}/manage"
}
