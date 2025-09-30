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

import android.content.Intent
import com.duckduckgo.di.scopes.ActivityScope
import com.duckduckgo.duckchat.api.inputscreen.InputScreenActivityParams
import com.duckduckgo.duckchat.impl.DuckChatInternal
import com.duckduckgo.navigation.api.getActivityParams
import com.squareup.anvil.annotations.ContributesBinding
import dagger.SingleInstanceIn
import javax.inject.Inject

interface InputScreenConfigResolver {
    val isTopOmnibar: Boolean

    fun onInputScreenCreated(intent: Intent)

    fun useTopBar(): Boolean
}

@ContributesBinding(scope = ActivityScope::class)
@SingleInstanceIn(scope = ActivityScope::class)
class InputScreenConfigResolverImpl @Inject constructor(
    private val duckChatInternal: DuckChatInternal,
) : InputScreenConfigResolver {

    companion object {
        fun useTopBar(isTopOmnibar: Boolean, duckChatInternal: DuckChatInternal): Boolean {
            return isTopOmnibar || !duckChatInternal.inputScreenBottomBarEnabled.value
        }
    }

    private var _isTopOmnibar = true

    override val isTopOmnibar: Boolean
        get() = _isTopOmnibar

    override fun onInputScreenCreated(intent: Intent) {
        val params = intent.getActivityParams(InputScreenActivityParams::class.java)
        _isTopOmnibar = params?.isTopOmnibar ?: true
    }

    override fun useTopBar(): Boolean = useTopBar(
        isTopOmnibar = isTopOmnibar,
        duckChatInternal = duckChatInternal,
    )
}
