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

package com.duckduckgo.promptscoordinator.api

/**
 * The kinds of app-originated prompts arbitrated by the [PromptsCoordinator].
 */
enum class PromptType {

    /**
     * A prompt scheduled by the Modal Coordinator (bottom sheets and modal activities, e.g. the
     * Add Widget promo, the Privacy Pro promo, or a remote message with a MODAL surface).
     */
    MODAL,

    /**
     * A Remote Messaging Framework inline card rendered on the New Tab Page.
     */
    RMF,

    /**
     * Reserved for a future app-originated prompt source that must mutually-exclude on the shared
     * New Tab Page surface but is neither a coordinated modal nor an RMF card. Its gap behaviour
     * is not yet defined; it currently claims with the same rules as [MODAL].
     */
    OTHER,
}
