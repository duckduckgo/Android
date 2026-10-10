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

package com.duckduckgo.pir.internal.settings

import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.pir.impl.freemium.PirFreemiumDebugSettings
import com.squareup.anvil.annotations.ContributesBinding
import javax.inject.Inject

@ContributesBinding(
    scope = AppScope::class,
    rank = ContributesBinding.RANK_HIGHEST,
)
class InternalPirFreemiumDebugSettings @Inject constructor(
    private val store: PirInternalSettingsDataStore,
) : PirFreemiumDebugSettings {
    override val isEligibilityForced: Boolean
        get() = store.isFreemiumEligibilityForced
}
