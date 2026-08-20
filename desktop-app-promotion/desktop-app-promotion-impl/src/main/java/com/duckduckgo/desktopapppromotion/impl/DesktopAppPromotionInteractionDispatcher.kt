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

package com.duckduckgo.desktopapppromotion.impl

import com.duckduckgo.common.utils.plugins.PluginPoint
import com.duckduckgo.desktopapppromotion.api.DesktopAppPromotionInteractionHandler
import com.duckduckgo.desktopapppromotion.api.DesktopAppPromotionInteractionHandler.Interaction
import com.duckduckgo.di.scopes.AppScope
import com.squareup.anvil.annotations.ContributesBinding
import javax.inject.Inject

/**
 * Routes an interaction to the one handler a launch named. Shared by the ViewModel and the share
 * broadcast receiver so both resolve handlers the same way.
 */
interface DesktopAppPromotionInteractionDispatcher {
    suspend fun dispatch(
        handlerId: String?,
        interaction: Interaction,
    )
}

@ContributesBinding(AppScope::class)
class RealDesktopAppPromotionInteractionDispatcher @Inject constructor(
    private val handlers: PluginPoint<DesktopAppPromotionInteractionHandler>,
) : DesktopAppPromotionInteractionDispatcher {

    override suspend fun dispatch(
        handlerId: String?,
        interaction: Interaction,
    ) {
        // Exact match only: a launch with no handler, or one naming a handler nobody contributed,
        // must not reach another caller's handler.
        val id = handlerId ?: return
        handlers.getPlugins()
            .firstOrNull { it.handlerId == id }
            ?.onInteraction(interaction)
    }
}
