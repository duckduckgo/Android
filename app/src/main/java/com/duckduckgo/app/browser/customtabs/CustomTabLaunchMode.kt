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

package com.duckduckgo.app.browser.customtabs

import android.content.Intent

enum class CustomTabLaunchMode {
    /** Flag off: the tab's hidden task can be trimmed by the system once the user switches apps. */
    LEGACY,

    /** Caller launched without NEW_TASK: the tab stacks on top of the caller, like Chrome. */
    CALLER_TASK,

    /** Caller launched with NEW_TASK into a new task: the tab gets its own document task, away from DDG's browser. */
    DOCUMENT_TASK,

    /** Caller launched with NEW_TASK into DDG's task: that task moves to the back, so closing the tab returns to the caller. */
    DOCUMENT_TASK_HIDE_BROWSER_TASK,
    ;

    val intentFlags: Int
        get() = when (this) {
            LEGACY -> Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK or Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS
            CALLER_TASK -> 0
            DOCUMENT_TASK, DOCUMENT_TASK_HIDE_BROWSER_TASK -> Intent.FLAG_ACTIVITY_NEW_DOCUMENT or Intent.FLAG_ACTIVITY_MULTIPLE_TASK
        }

    companion object {
        fun resolve(
            keepOnAppSwitchEnabled: Boolean,
            launchedWithNewTask: Boolean,
            isTaskRoot: Boolean,
        ): CustomTabLaunchMode = when {
            !keepOnAppSwitchEnabled -> LEGACY
            !launchedWithNewTask -> CALLER_TASK
            isTaskRoot -> DOCUMENT_TASK
            else -> DOCUMENT_TASK_HIDE_BROWSER_TASK
        }

        fun fromName(name: String?): CustomTabLaunchMode = entries.firstOrNull { it.name == name } ?: LEGACY
    }
}
