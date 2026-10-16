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
import com.duckduckgo.app.browser.customtabs.CustomTabLaunchMode.CALLER_TASK
import com.duckduckgo.app.browser.customtabs.CustomTabLaunchMode.DOCUMENT_TASK
import com.duckduckgo.app.browser.customtabs.CustomTabLaunchMode.DOCUMENT_TASK_HIDE_BROWSER_TASK
import com.duckduckgo.app.browser.customtabs.CustomTabLaunchMode.LEGACY
import org.junit.Assert.assertEquals
import org.junit.Test

class CustomTabLaunchModeTest {

    @Test
    fun whenFeatureDisabledThenResolvesLegacyRegardlessOfTask() {
        listOf(true, false).forEach { launchedWithNewTask ->
            listOf(true, false).forEach { isTaskRoot ->
                assertEquals(LEGACY, CustomTabLaunchMode.resolve(keepOnAppSwitchEnabled = false, launchedWithNewTask, isTaskRoot))
            }
        }
    }

    @Test
    fun whenFeatureEnabledAndLaunchedWithoutNewTaskThenResolvesCallerTask() {
        assertEquals(CALLER_TASK, CustomTabLaunchMode.resolve(keepOnAppSwitchEnabled = true, launchedWithNewTask = false, isTaskRoot = false))
        assertEquals(CALLER_TASK, CustomTabLaunchMode.resolve(keepOnAppSwitchEnabled = true, launchedWithNewTask = false, isTaskRoot = true))
    }

    @Test
    fun whenFeatureEnabledAndDispatcherIsRootOfOwnTaskThenResolvesDocumentTask() {
        assertEquals(DOCUMENT_TASK, CustomTabLaunchMode.resolve(keepOnAppSwitchEnabled = true, launchedWithNewTask = true, isTaskRoot = true))
    }

    @Test
    fun whenFeatureEnabledAndDispatcherJoinedBrowserTaskThenResolvesDocumentTaskHidingBrowserTask() {
        assertEquals(
            DOCUMENT_TASK_HIDE_BROWSER_TASK,
            CustomTabLaunchMode.resolve(keepOnAppSwitchEnabled = true, launchedWithNewTask = true, isTaskRoot = false),
        )
    }

    @Test
    fun whenIntentFlagsRequestedThenMatchLaunchMode() {
        assertEquals(
            Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK or Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS,
            LEGACY.intentFlags,
        )
        assertEquals(0, CALLER_TASK.intentFlags)
        val documentFlags = Intent.FLAG_ACTIVITY_NEW_DOCUMENT or Intent.FLAG_ACTIVITY_MULTIPLE_TASK
        assertEquals(documentFlags, DOCUMENT_TASK.intentFlags)
        assertEquals(documentFlags, DOCUMENT_TASK_HIDE_BROWSER_TASK.intentFlags)
    }

    @Test
    fun whenNameIsKnownThenFromNameReturnsMode() {
        CustomTabLaunchMode.entries.forEach { assertEquals(it, CustomTabLaunchMode.fromName(it.name)) }
    }

    @Test
    fun whenNameIsMissingOrUnknownThenFromNameFallsBackToLegacy() {
        assertEquals(LEGACY, CustomTabLaunchMode.fromName(null))
        assertEquals(LEGACY, CustomTabLaunchMode.fromName("UNKNOWN"))
    }
}
