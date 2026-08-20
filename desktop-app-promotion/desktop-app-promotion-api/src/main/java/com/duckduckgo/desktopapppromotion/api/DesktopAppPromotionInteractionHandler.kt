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

package com.duckduckgo.desktopapppromotion.api

/**
 * Lets a caller of the desktop-app promo screen react to what the user did on it, without the promo
 * screen knowing anything about the caller. Contribute an implementation from the module that owns
 * the side effect, and set [DesktopAppPromotionParams.handlerId] to its [handlerId] when launching.
 *
 * Handlers are resolved by an exact [handlerId] match, never notified as a group — a launch that
 * carries no `handlerId`, or one naming a handler nobody contributed, triggers nothing.
 */
interface DesktopAppPromotionInteractionHandler {

    /** Matches the [DesktopAppPromotionParams.handlerId] of the launch that produced the interaction. */
    val handlerId: String

    suspend fun onInteraction(interaction: Interaction)

    enum class Interaction {
        LINK_COPIED,

        /** The user picked a target in the share sheet, not merely opened it. */
        SHARE_COMPLETED,

        DISMISSED,
    }
}
