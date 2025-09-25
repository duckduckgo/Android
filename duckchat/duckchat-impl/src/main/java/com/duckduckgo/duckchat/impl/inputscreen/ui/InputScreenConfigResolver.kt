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

package com.duckduckgo.duckchat.impl.inputscreen.ui

import com.duckduckgo.browser.api.omnibar.model.OmnibarPosition
import com.duckduckgo.browser.api.omnibar.settings.OmnibarSettings
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.duckchat.impl.DuckChatInternal
import com.squareup.anvil.annotations.ContributesBinding
import dagger.SingleInstanceIn
import javax.inject.Inject

interface InputScreenConfigResolver {
    fun useTopBar(): Boolean
}

@ContributesBinding(scope = AppScope::class)
@SingleInstanceIn(scope = AppScope::class)
class InputScreenConfigResolverImpl @Inject constructor(
    private val duckChatInternal: DuckChatInternal,
    private val omnibarSettings: OmnibarSettings,
) : InputScreenConfigResolver {

    override fun useTopBar(): Boolean {
        return omnibarSettings.omnibarPosition == OmnibarPosition.TOP || !duckChatInternal.inputScreenBottomBarEnabled.value
    }
}
