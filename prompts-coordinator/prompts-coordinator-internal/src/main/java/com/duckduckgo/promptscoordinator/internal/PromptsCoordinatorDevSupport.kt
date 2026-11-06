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

package com.duckduckgo.promptscoordinator.internal

/**
 * Test hooks for the prompts coordinator, for internal builds only.
 */
interface PromptsCoordinatorDevSupport {

    /**
     * Clears every gate that suppresses modals after one has been shown: the coordinator's quiet gap
     * and the internal 24-hour window used when the coordinator is disabled. The next app resume
     * evaluates modals again.
     */
    suspend fun resetModalCooldown()
}
