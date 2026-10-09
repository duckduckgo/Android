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

package com.duckduckgo.credentialexchange.impl

import com.duckduckgo.anvil.annotations.ContributesRemoteFeature
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.feature.toggles.api.Toggle
import com.duckduckgo.feature.toggles.api.Toggle.DefaultFeatureValue

/**
 * Remote flags for the OS credential exchange.
 */
@ContributesRemoteFeature(
    scope = AppScope::class,
    featureName = "credentialExchange",
)
interface CredentialExchangeFeature {

    /**
     * Kill switch for the whole mechanism, across every caller.
     *
     * @return `true` when the remote config has the "credentialExchange" feature enabled.
     */
    @Toggle.DefaultValue(DefaultFeatureValue.INTERNAL)
    fun self(): Toggle

    /**
     * Rollout control for importing passwords through the OS credential exchange.
     *
     * @return `true` when the remote config has the "canImportPasswords" sub-feature of "credentialExchange" enabled.
     */
    @Toggle.DefaultValue(DefaultFeatureValue.INTERNAL)
    fun canImportPasswords(): Toggle
}
