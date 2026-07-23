/*
 * Copyright (c) 2025 DuckDuckGo
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

package com.duckduckgo.app.onboarding.ui.page.configdriven

import android.animation.Animator
import android.view.View
import com.duckduckgo.app.onboarding.orchestrator.NewUserOnboardingEvent

/**
 * What a [DialogBinder]/[StatefulDialogBinder] hands back to the engine after [DialogBinder.bind].
 * Binders never start animations themselves — [entrance] declares lazy `() -> Animator` factories the
 * engine plays (and can end()/cancel()); animators returned by those factories must leave their views in
 * final visible state even if end() is called before they were ever started.
 */
class ContentHandle(
    val title: DialogTitleController?,
    val fadeTargets: List<View>,
    val entrance: (EntranceScope.() -> Unit)? = null,
    val result: (() -> NewUserOnboardingEvent)? = null,
    val unbind: () -> Unit = {},
)

/** Lets a screen declare bespoke intro animators without ever calling start() on them itself. */
interface EntranceScope {
    /** Runs after the engine has faded in [ContentHandle.fadeTargets]. */
    fun afterFade(animator: () -> Animator)

    /** Runs after the engine has finished (or skipped) [ContentHandle.title]'s typing animation. */
    fun afterTitle(animator: () -> Animator)
}
