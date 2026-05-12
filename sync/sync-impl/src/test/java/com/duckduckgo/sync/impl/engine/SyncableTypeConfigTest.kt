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

package com.duckduckgo.sync.impl.engine

import com.duckduckgo.sync.api.engine.SyncableType
import junit.framework.Assert.assertFalse
import junit.framework.Assert.assertTrue
import org.junit.Test

internal class SyncableTypeConfigTest {

    @Test
    fun allSyncableTypesHaveConfig() {
        SyncableType.entries.forEach { type ->
            SyncableTypeConfig.forType(type)
        }
    }

    @Test
    fun bookmarksSupportGetAndPatch() {
        assertTrue(SyncableType.BOOKMARKS.supports(SyncHttpMethod.GET))
        assertTrue(SyncableType.BOOKMARKS.supports(SyncHttpMethod.PATCH))
    }

    @Test
    fun credentialsSupportGetAndPatch() {
        assertTrue(SyncableType.CREDENTIALS.supports(SyncHttpMethod.GET))
        assertTrue(SyncableType.CREDENTIALS.supports(SyncHttpMethod.PATCH))
    }

    @Test
    fun settingsSupportGetAndPatch() {
        assertTrue(SyncableType.SETTINGS.supports(SyncHttpMethod.GET))
        assertTrue(SyncableType.SETTINGS.supports(SyncHttpMethod.PATCH))
    }

    @Test
    fun duckAiChatsSupportsPatchOnly() {
        assertFalse(SyncableType.DUCK_AI_CHATS.supports(SyncHttpMethod.GET))
        assertTrue(SyncableType.DUCK_AI_CHATS.supports(SyncHttpMethod.PATCH))
    }
}
