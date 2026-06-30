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

package com.duckduckgo.sync.impl.promotion.chat

import android.net.Uri
import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.duckchat.api.DuckChat
import com.duckduckgo.sync.FakeDeviceSyncState
import com.duckduckgo.sync.impl.promotion.FakeSyncPromotionDataStore
import com.duckduckgo.sync.impl.promotion.SyncPromotionDataStore.PromotionType
import junit.framework.TestCase.assertEquals
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class RealChatSyncPromotionTest {
    @get:Rule
    val coroutineTestRule: CoroutineTestRule = CoroutineTestRule()

    private val dataStore = FakeSyncPromotionDataStore()
    private val syncState = FakeDeviceSyncState()
    private val duckChat = FakeDuckChat()

    private val testee = RealChatSyncPromotion(
        dataStore,
        syncState,
        duckChat,
        coroutineTestRule.testDispatcherProvider,
    )

    @Test
    fun `when all preconditions are met can show promotion`() = runTest {
        configurePromotionToShow()

        assertTrue(testee.canShowPromotion())
    }

    @Test
    fun `when promotion is accepted cannot show promotion`() = runTest {
        configurePromotionToShow()

        testee.recordPromotionAccepted()
        assertFalse(testee.canShowPromotion())
    }

    @Test
    fun `when promotion is dismissed cannot show promotion`() = runTest {
        configurePromotionToShow()

        testee.recordPromotionDismissed()
        assertFalse(testee.canShowPromotion())
    }

    @Test
    fun `when promotion impression cap is reached cannot show promotion`() = runTest {
        configurePromotionToShow()

        repeat(RealChatSyncPromotion.MAX_IMPRESSION_COUNT - 1) {
            testee.incrementImpressionCount()
        }
        assertTrue(testee.canShowPromotion())

        testee.incrementImpressionCount()
        assertFalse(testee.canShowPromotion())
    }

    @Test
    fun `when sync is disabled cannot show promotion`() = runTest {
        configurePromotionToShow()

        syncState.isFeatureEnabled = false
        assertFalse(testee.canShowPromotion())
    }

    @Test
    fun `when duck chat sync is disabled cannot show promotion`() = runTest {
        configurePromotionToShow()

        syncState.isDuckChatSyncFeatureEnabled = false
        assertFalse(testee.canShowPromotion())
    }

    @Test
    fun `when user is signed in is disabled cannot show promotion`() = runTest {
        configurePromotionToShow()

        syncState.isUserSignedInOnDevice = true
        assertFalse(testee.canShowPromotion())
    }

    @Test
    fun `when duck chat history is disabled cannot show promotion`() = runTest {
        configurePromotionToShow()

        duckChat.hasUserEnabledHistory = false
        assertFalse(testee.canShowPromotion())
    }

    @Test
    fun `when duck chat has no history is disabled cannot show promotion`() = runTest {
        configurePromotionToShow()

        duckChat.hasChatSuggestions.value = false
        assertFalse(testee.canShowPromotion())
    }

    @Test
    fun `when incrementImpressionCount then record promo impression`() = runTest {
        testee.incrementImpressionCount()
        assertEquals(1L, dataStore.getPromoImpressionCount(PromotionType.ChatTabPage))

        testee.incrementImpressionCount()
        assertEquals(2L, dataStore.getPromoImpressionCount(PromotionType.ChatTabPage))
    }

    @Test
    fun `when recordPromotionAccepted then record promo dismissed`() = runTest {
        testee.recordPromotionAccepted()
        assertTrue(dataStore.hasPromoBeenDismissed(PromotionType.ChatTabPage))
    }

    @Test
    fun `when recordPromotionDismissed then record promo dismissed`() = runTest {
        testee.recordPromotionDismissed()
        assertTrue(dataStore.hasPromoBeenDismissed(PromotionType.ChatTabPage))
    }

    private suspend fun configurePromotionToShow() {
        dataStore.clearPromoHistory(PromotionType.ChatTabPage)
        syncState.isFeatureEnabled = true
        syncState.isDuckChatSyncFeatureEnabled = true
        syncState.isUserSignedInOnDevice = false
        duckChat.hasUserEnabledHistory = true
        duckChat.hasChatSuggestions.value = true
    }
}

private class FakeDuckChat : DuckChat {
    var hasUserEnabledHistory = false
    val hasChatSuggestions = MutableStateFlow(false)

    override fun isEnabled(): Boolean = false

    override fun openDuckChat() = Unit

    override fun openDuckChatWithAutoPrompt(query: String) = Unit

    override fun openDuckChatWithPrefill(query: String) = Unit

    override fun getDuckChatUrl(
        query: String,
        autoPrompt: Boolean,
        sidebar: Boolean,
    ): String = ""

    override fun getDuckChatSettingsUrl(): String = ""

    override fun isDuckChatUrl(uri: Uri): Boolean = false

    override suspend fun wasOpenedBefore(): Boolean = false

    override suspend fun setInputScreenUserSetting(enabled: Boolean) = Unit

    override suspend fun isInputScreenEverEnabled(): Boolean = false

    override suspend fun setCosmeticInputScreenUserSetting(enabled: Boolean) = Unit

    override fun observeInputScreenUserSettingEnabled(): Flow<Boolean> = emptyFlow()

    override fun observeCosmeticInputScreenUserSettingEnabled(): Flow<Boolean?> = emptyFlow()

    override fun observeAutomaticContextAttachmentUserSettingEnabled(): Flow<Boolean> = emptyFlow()

    override fun observeNativeInputFieldUserSettingEnabled(): Flow<Boolean> = emptyFlow()

    override fun observeNativeChatInputEnabled(): Flow<Boolean> = emptyFlow()

    override suspend fun isStandaloneMigrationCompleted(): Boolean = false

    override suspend fun setChatSuggestionsUserSetting(enabled: Boolean) = Unit

    override fun observeChatSuggestionsUserSettingEnabled(): Flow<Boolean> = emptyFlow()

    override fun openVoiceDuckChat() = Unit

    override fun isVoiceChatSessionActive(tabId: String): Boolean = false

    override val activeVoiceChatSessions: Flow<Set<String>> get() = emptyFlow()

    override fun observeTriggerVoiceChatSessionEnd(): Flow<String> = emptyFlow()

    override fun endVoiceChatSession(tabId: String) = Unit

    override suspend fun isChatHistoryAvailable(): Boolean = false

    override suspend fun hasUserEnabledChatHistory(): Boolean = hasUserEnabledHistory

    override fun observeHasChatSuggestions(): Flow<Boolean> = hasChatSuggestions

    override suspend fun onAddressBarPickerDuckAiSelected() = Unit
}
