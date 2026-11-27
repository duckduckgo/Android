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

package com.duckduckgo.duckchat.impl.nativeinput.footer.usagewarnings

import com.duckduckgo.duckchat.api.nativeinput.NativeInputState
import com.duckduckgo.duckchat.impl.models.ModelState

/**
 * The active tab's model as the usage footers see it, for telling a user's model switch from the app's own moves.
 * [isAppManaged] is true while `resetModelForNewChats` is on: the manager then moves the global selection itself
 * (fetch, over-limit default, reset), and a user's pick lands on the tab instead.
 */
data class ModelSelection(
    val selectedModelId: String?,
    val isNewChat: Boolean,
    val pickedModelId: String?,
    val isAppManaged: Boolean,
) {
    /** The model the user switched away from since [previous], or null when nothing changed or the app moved it. */
    fun switchedFrom(previous: ModelSelection?): String? {
        val previousModelId = previous?.selectedModelId ?: return null
        if (selectedModelId == null || selectedModelId == previousModelId) return null
        if (!isAppManaged) return previousModelId
        val isOwnPick = isNewChat && previous.isNewChat && pickedModelId != null && pickedModelId != previous.pickedModelId
        return previousModelId.takeIf { isOwnPick }
    }

    companion object {
        /** [modelState] is the tab's own, already resolved with `forTab`. */
        fun of(modelState: ModelState, inputState: NativeInputState) = ModelSelection(
            selectedModelId = modelState.selectedModelId,
            isNewChat = inputState.chatId == null,
            pickedModelId = inputState.newChatModelId,
            isAppManaged = modelState.newChatDefault != null,
        )
    }
}
