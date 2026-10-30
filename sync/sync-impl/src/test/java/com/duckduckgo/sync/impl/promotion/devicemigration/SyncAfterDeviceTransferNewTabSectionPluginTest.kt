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

package com.duckduckgo.sync.impl.promotion.devicemigration

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SyncAfterDeviceTransferNewTabSectionPluginTest {

    private val prompt = FakePrompt()
    private val testee = SyncAfterDeviceTransferNewTabSectionPlugin(prompt)

    @Test
    fun whenThePromptShouldShowThenTheSectionIsEnabled() = runTest {
        prompt.show = true

        assertTrue(testee.isUserEnabled())
    }

    @Test
    fun whenThePromptShouldNotShowThenTheSectionIsDisabled() = runTest {
        prompt.show = false

        assertFalse(testee.isUserEnabled())
    }

    private class FakePrompt : SyncAfterDeviceTransferPrompt {
        var show = false
        override suspend fun shouldShow(): Boolean = show
        override suspend fun dismiss() = Unit
    }
}
