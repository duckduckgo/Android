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

package com.duckduckgo.settings.impl.store

import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.settings.api.Settings
import com.duckduckgo.settings.impl.UpdatedSettingsFeature
import com.squareup.anvil.annotations.ContributesBinding
import dagger.SingleInstanceIn
import javax.inject.Inject

@ContributesBinding(AppScope::class)
@SingleInstanceIn(AppScope::class)
class RealSettings @Inject constructor(
    private val updatedSettingsFeature: UpdatedSettingsFeature,
) : Settings {

    override val isNewSettingsEnabled: Boolean
        get() = updatedSettingsFeature.self().isEnabled()
}
