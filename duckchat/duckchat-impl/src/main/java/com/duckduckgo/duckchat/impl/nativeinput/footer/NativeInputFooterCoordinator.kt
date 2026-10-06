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

package com.duckduckgo.duckchat.impl.nativeinput.footer

import android.content.Context
import android.view.View
import com.duckduckgo.common.utils.plugins.ActivePluginPoint
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import javax.inject.Inject

class NativeInputFooterCoordinator @Inject constructor(
    private val plugins: ActivePluginPoint<NativeInputFooterPlugin>,
) {
    /** [footers] are the visible footers, highest priority (lowest value) first. */
    data class State(
        val footers: List<NativeInputFooter> = emptyList(),
        val blocksComposer: Boolean = false,
    ) {
        val rows: List<View> get() = footers.map { it.view }
    }

    private class Entry(
        val priority: Int,
        val category: String,
        val footer: NativeInputFooter,
    )

    fun state(
        context: Context,
        hostContext: StateFlow<NativeInputFooterContext>,
        host: NativeInputFooterHost,
    ): Flow<State> = flow {
        val footers = plugins.getPlugins()
            .map { plugin -> Entry(plugin.priority, plugin.category, plugin.createFooter(context, hostContext, host)) }

        if (footers.isEmpty()) {
            emit(State())
            return@flow
        }

        emitAll(
            combine(footers.map { it.footer.state }) { states ->
                // One footer per category, the highest priority among the visible ones; the rest stack in priority order.
                val shown = states.indices
                    .filter { states[it].visible }
                    .groupBy { footers[it].category }
                    .values
                    .map { sameCategory -> sameCategory.minBy { footers[it].priority } }
                    .sortedBy { footers[it].priority }

                State(
                    footers = shown.map { footers[it].footer },
                    blocksComposer = shown.any { states[it].blocksComposer },
                )
            },
        )
    }
}
