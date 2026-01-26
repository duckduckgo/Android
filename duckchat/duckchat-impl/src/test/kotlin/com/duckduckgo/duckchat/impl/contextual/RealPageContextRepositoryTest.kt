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

package com.duckduckgo.duckchat.impl.contextual

import com.duckduckgo.duckchat.impl.store.DuckChatDataStore
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class RealPageContextRepositoryTest {

    private val fakeDataStore = FakeDuckChatDataStore()
    private val repository = RealPageContextRepository(fakeDataStore)

    @Test
    fun flowEmitsUpdatesForTab() = runTest {
        val flow = repository.getPageContext("tab-1")

        repository.update("tab-1", "data-1")
        val updated = flow.first { it != null && it.serializedPageData == "data-1" }
        assertEquals("data-1", updated.serializedPageData)
        assertEquals("tab-1", updated.tabId)
        assertEquals(false, updated.isCleared)
    }

    @Test
    fun flowEmitsNullAfterClear() = runTest {
        val flow = repository.getPageContext("tab-1")
        repository.update("tab-1", "data-1")
        flow.first { it != null && it.serializedPageData == "data-1" }

        repository.clear("tab-1")

        val cleared = flow.first { it?.isCleared == true }
        assertEquals("tab-1", cleared?.tabId)
        assertEquals(true, cleared?.isCleared)
    }

    @Test
    fun flowIsolationAcrossTabs() = runTest {
        val tab1Flow = repository.getPageContext("tab-1")
        val tab2Flow = repository.getPageContext("tab-2")

        repository.update("tab-1", "data-1")
        repository.update("tab-2", "data-2")

        assertEquals("data-1", tab1Flow.first { it?.tabId == "tab-1" }?.serializedPageData)
        assertEquals("data-2", tab2Flow.first { it?.tabId == "tab-2" }?.serializedPageData)
    }
}

private class FakeDuckChatDataStore : DuckChatDataStore {
    private val updates = MutableSharedFlow<PageContextData?>(extraBufferCapacity = 1)

    override suspend fun setDuckChatPageContext(tabId: String, serializedPageData: String) {
        updates.tryEmit(PageContextData(tabId, serializedPageData, System.currentTimeMillis(), isCleared = false))
    }

    override suspend fun clearDuckChatPageContext(tabId: String) {
        updates.tryEmit(PageContextData(tabId, "", System.currentTimeMillis(), isCleared = true))
    }

    override fun observeDuckChatPageContext() = updates

    // Unused test methods
    override suspend fun setDuckChatUserEnabled(enabled: Boolean) = unsupported()
    override suspend fun setInputScreenUserSetting(enabled: Boolean) = unsupported()
    override suspend fun setCosmeticInputScreenUserSetting(enabled: Boolean) = unsupported()
    override suspend fun setShowInBrowserMenu(showDuckChat: Boolean) = unsupported()
    override suspend fun setShowInAddressBar(showDuckChat: Boolean) = unsupported()
    override suspend fun setFullScreenModeUserSetting(enabled: Boolean) = unsupported()
    override suspend fun setShowInVoiceSearch(showToggle: Boolean) = unsupported()
    override suspend fun setAutomaticPageContextAttachment(enabled: Boolean) = unsupported()
    override fun observeDuckChatUserEnabled(): Flow<Boolean> = unsupportedFlow<Boolean>()
    override fun observeInputScreenUserSettingEnabled(): Flow<Boolean> = unsupportedFlow<Boolean>()
    override fun observeCosmeticInputScreenUserSettingEnabled(): Flow<Boolean?> = unsupportedFlow<Boolean?>()
    override fun observeAutomaticContextAttachmentUserSettingEnabled(): Flow<Boolean> = unsupportedFlow<Boolean>()
    override fun observeShowInBrowserMenu(): Flow<Boolean> = unsupportedFlow<Boolean>()
    override fun observeShowInAddressBar(): Flow<Boolean> = unsupportedFlow<Boolean>()
    override fun observeShowInVoiceSearch(): Flow<Boolean> = unsupportedFlow<Boolean>()
    override suspend fun isDuckChatUserEnabled(): Boolean = unsupported()
    override suspend fun isInputScreenUserSettingEnabled(): Boolean = unsupported()
    override suspend fun isFullScreenUserSettingEnabled(): Boolean = unsupported()
    override suspend fun isCosmeticInputScreenUserSettingEnabled(): Boolean = unsupported()
    override suspend fun getShowInBrowserMenu(): Boolean = unsupported()
    override suspend fun getShowInAddressBar(): Boolean = unsupported()
    override suspend fun getShowInVoiceSearch(): Boolean = unsupported()
    override suspend fun fetchAndClearUserPreferences(): String? = unsupported()
    override suspend fun updateUserPreferences(userPreferences: String?) = unsupported()
    override suspend fun registerOpened() = unsupported()
    override suspend fun wasOpenedBefore(): Boolean = unsupported()
    override suspend fun lastSessionTimestamp(): Long = unsupported()
    override suspend fun sessionDeltaTimestamp(): Long = unsupported()
    override suspend fun setAppBackgroundTimestamp(timestamp: Long?) = unsupported()
    override suspend fun getAppBackgroundTimestamp(): Long? = unsupported()
    override suspend fun setAIChatHistoryEnabled(enabled: Boolean) = unsupported()
    override suspend fun isAIChatHistoryEnabled(): Boolean = unsupported()

    private fun <T> unsupported(): T = throw UnsupportedOperationException()
    private fun <T> unsupportedFlow(): Flow<T> = throw UnsupportedOperationException()
}
