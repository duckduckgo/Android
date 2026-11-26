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

package com.duckduckgo.onboarding.api

import com.duckduckgo.onboarding.api.OnboardingSingleChoiceDataPlugin.Option

/**
 * The Duck.ai model choice shown in onboarding. The pick is not stored anywhere: onboarding holds it
 * and hands it back to [chatUrl] when it opens the user's first chat, so later chats start on the
 * default model.
 */
interface OnboardingDuckAiModelChoice {
    /** Warms up whatever [options] reads. Called while the step is still several screens away. */
    suspend fun prefetch()

    /** Display order, first entry is the default. Empty when the choice is unavailable. */
    suspend fun options(): List<Option>

    /**
     * Returns a Duck.ai URL that submits [prompt] on the model behind [option], one of [options].
     * With a null [option], or one whose model is no longer available, the prompt runs on the default model.
     */
    suspend fun chatUrl(prompt: String, option: Option?): String
}
