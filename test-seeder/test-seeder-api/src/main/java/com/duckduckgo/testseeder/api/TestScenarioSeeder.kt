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

package com.duckduckgo.testseeder.api

interface TestScenarioSeeder {
    suspend fun seedIfNeeded(
        isMaestroExtra: String?,
        scenarioKey: String?,
        omnibarPosition: String?,
        nativeInputToggle: String?,
        inputScreenWithAI: String?,
    )

    companion object {
        const val EXTRA_IS_MAESTRO = "isMaestro"
        const val EXTRA_TEST_SCENARIO = "testScenario"
        const val EXTRA_OMNIBAR_POSITION = "omnibarPosition"
        const val EXTRA_NATIVE_INPUT_TOGGLE = "nativeInputToggle"
        const val EXTRA_INPUT_WITH_AI_TOGGLE = "inputWithAiToggle"
    }
}
