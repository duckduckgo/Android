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
    data class State(
        val view: View? = null,
        val blocksComposer: Boolean = false,
    )

    fun state(
        context: Context,
        hostContext: StateFlow<NativeInputFooterContext>,
    ): Flow<State> = flow {
        val footers = plugins.getPlugins()
            .map { plugin -> plugin.priority to plugin.createFooter(context, hostContext) }

        if (footers.isEmpty()) {
            emit(State())
            return@flow
        }

        emitAll(
            combine(footers.map { it.second.state }) { states ->
                val selectedIndex = states.indices
                    .filter { states[it].visible }
                    .minByOrNull { footers[it].first }

                if (selectedIndex == null) {
                    State()
                } else {
                    State(
                        view = footers[selectedIndex].second.view,
                        blocksComposer = states[selectedIndex].blocksComposer,
                    )
                }
            },
        )
    }
}
