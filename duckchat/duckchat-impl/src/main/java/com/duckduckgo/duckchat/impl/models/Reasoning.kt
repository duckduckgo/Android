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

package com.duckduckgo.duckchat.impl.models

enum class ReasoningEffort(val rawValue: String) {
    NONE("none"),
    MINIMAL("minimal"),
    LOW("low"),
    MEDIUM("medium"),
    HIGH("high"),
    ;

    companion object {
        fun from(raw: String?): ReasoningEffort? = entries.firstOrNull { it.rawValue == raw }
    }
}

enum class ReasoningMode(
    val rawValue: String,
    internal val candidateEfforts: List<ReasoningEffort>,
) {
    FAST("fast", listOf(ReasoningEffort.NONE, ReasoningEffort.MINIMAL)),
    REASONING("reasoning", listOf(ReasoningEffort.LOW)),
    EXTENDED_REASONING("extended_reasoning", listOf(ReasoningEffort.HIGH, ReasoningEffort.MEDIUM)),
    ;

    companion object {
        fun from(raw: String?): ReasoningMode? = entries.firstOrNull { it.rawValue == raw }
    }
}

data class AvailableReasoningMode(
    val mode: ReasoningMode,
    val effort: ReasoningEffort,
)

object ReasoningResolver {

    fun availableModes(supported: List<ReasoningEffort>): List<AvailableReasoningMode> =
        ReasoningMode.entries.mapNotNull { mode ->
            mode.candidateEfforts.firstOrNull { it in supported }?.let { effort ->
                AvailableReasoningMode(mode, effort)
            }
        }

    fun resolveMode(
        persisted: ReasoningMode?,
        available: List<AvailableReasoningMode>,
    ): ReasoningMode? = when {
        available.isEmpty() -> null
        persisted != null && available.any { it.mode == persisted } -> persisted
        else -> available.first().mode
    }

    fun effortFor(
        mode: ReasoningMode?,
        available: List<AvailableReasoningMode>,
    ): ReasoningEffort? = available.firstOrNull { it.mode == mode }?.effort
}
