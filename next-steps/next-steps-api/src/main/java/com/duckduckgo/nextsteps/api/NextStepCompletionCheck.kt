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
package com.duckduckgo.nextsteps.api

/**
 * Reports whether the setup step behind a next steps item is already done, so an item the user
 * has effectively completed elsewhere (e.g. set as default during onboarding) is never shown.
 *
 * Contributed by the module that owns the step, with @ContributesMultibinding(AppScope::class).
 */
interface NextStepCompletionCheck {
    /** Matches the item's `id`, e.g. "setup_default_browser". */
    val itemId: String

    /**
     * Returns true or false, depending on whether the item with itemId is already completed or not
     */
    suspend fun isComplete(): Boolean
}
