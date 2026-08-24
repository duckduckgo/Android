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

package com.duckduckgo.app.onboarding

import com.duckduckgo.onboarding.api.OnboardingSingleChoiceDataPlugin
import com.duckduckgo.onboarding.api.OnboardingSingleChoiceDataPlugin.Id
import com.duckduckgo.onboarding.api.OnboardingSingleChoiceDataPlugin.Option

data class TestOption(
    override val id: String,
    override val label: String = id,
    override val iconRes: Int = 0,
) : Option

class FakeOnboardingSingleChoiceDataPlugin(
    override val id: Id = Id.DuckAiModelProvider,
    private val options: List<Option> = emptyList(),
) : OnboardingSingleChoiceDataPlugin {

    var prefetchCount = 0
        private set

    val applied = mutableListOf<Option>()

    override suspend fun prefetch() {
        prefetchCount++
    }

    override suspend fun options(): List<Option> = options

    override suspend fun apply(option: Option) {
        applied += option
    }
}
