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

package com.duckduckgo.duckchat.impl.clienttools

/** The tools a [ClientToolPermissionRequest.GrantAccess] prompt asks for, and which ones the user took out before answering. */
data class GrantAccessSelection(
    val tools: List<AskingTool>,
    private val removable: Boolean,
    val removed: Set<String> = emptySet(),
) {
    constructor(request: ClientToolPermissionRequest.GrantAccess) : this(request.tools, request.toolsRemovable)

    val remaining: List<AskingTool> get() = tools.filterNot { it.name in removed }

    val removedTools: List<AskingTool> get() = tools.filter { it.name in removed }

    val showsBoxes: Boolean get() = removable && tools.size >= 2

    /** The last remaining tool can't be taken out: Don't Allow is how the user says no to all of them. */
    val canRemove: Boolean get() = showsBoxes && remaining.size >= 2

    fun remove(toolName: String): GrantAccessSelection =
        if (canRemove && remaining.any { it.name == toolName }) copy(removed = removed + toolName) else this

    fun undo(): GrantAccessSelection = copy(removed = emptySet())

    companion object {
        /** Joins [names] as "A", "A and B" or "A, B and C", with [series] and [pair] doing the joining. */
        fun joinNames(
            names: List<String>,
            series: (String, String) -> String,
            pair: (String, String) -> String,
        ): String = when (names.size) {
            0 -> ""
            1 -> names.single()
            else -> pair(names.dropLast(1).reduce(series), names.last())
        }
    }
}

/** Remembers the selection of one pending prompt, so tools taken out stay out when its dialog is shown again. */
class GrantAccessSelections {
    private var current: Pair<PendingClientToolPermission, GrantAccessSelection>? = null

    /** The selection for [pending], starting with every tool when it is a different prompt, or null when it asks for one tool. */
    fun of(pending: PendingClientToolPermission): GrantAccessSelection? {
        current?.takeIf { it.first === pending }?.let { return it.second }
        val request = pending.request as? ClientToolPermissionRequest.GrantAccess ?: return null
        return GrantAccessSelection(request).also { current = pending to it }
    }

    fun update(
        pending: PendingClientToolPermission,
        change: (GrantAccessSelection) -> GrantAccessSelection,
    ) {
        val selection = of(pending) ?: return
        current = pending to change(selection)
    }
}
