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

package com.duckduckgo.duckchat.impl.repository

import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.duckchat.impl.store.DuckChatDataStore
import com.squareup.anvil.annotations.ContributesBinding
import dagger.SingleInstanceIn
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

interface DuckChatFeatureRepository {
    suspend fun setDuckChatUserEnabled(enabled: Boolean)

    suspend fun setInputScreenUserSetting(enabled: Boolean)

    suspend fun setShowInBrowserMenu(showDuckChat: Boolean)

    suspend fun setShowInAddressBar(showDuckChat: Boolean)

    suspend fun setShowButtonsOnTop(showOnTop: Boolean)

    fun observeDuckChatUserEnabled(): Flow<Boolean>

    fun observeInputScreenUserSettingEnabled(): Flow<Boolean>

    fun observeShowInBrowserMenu(): Flow<Boolean>

    fun observeShowInAddressBar(): Flow<Boolean>

    fun observeShowButtonsOnTop(): Flow<Boolean>

    suspend fun isDuckChatUserEnabled(): Boolean

    suspend fun isInputScreenUserSettingEnabled(): Boolean

    suspend fun shouldShowInBrowserMenu(): Boolean

    suspend fun shouldShowInAddressBar(): Boolean

    suspend fun shouldShowButtonsOnTop(): Boolean

    suspend fun registerOpened()

    suspend fun wasOpenedBefore(): Boolean

    suspend fun lastSessionTimestamp(): Long

    suspend fun sessionDeltaInMinutes(): Long
}

@SingleInstanceIn(AppScope::class)
@ContributesBinding(AppScope::class)
class RealDuckChatFeatureRepository @Inject constructor(
    private val duckChatDataStore: DuckChatDataStore,
    private val context: Context,
) : DuckChatFeatureRepository {
    override suspend fun setDuckChatUserEnabled(enabled: Boolean) {
        duckChatDataStore.setDuckChatUserEnabled(enabled)
    }

    override suspend fun setInputScreenUserSetting(enabled: Boolean) {
        duckChatDataStore.setInputScreenUserSetting(enabled)
    }

    override suspend fun setShowInBrowserMenu(showDuckChat: Boolean) {
        duckChatDataStore.setShowInBrowserMenu(showDuckChat)
    }

    override suspend fun setShowInAddressBar(showDuckChat: Boolean) {
        duckChatDataStore.setShowInAddressBar(showDuckChat)
    }

    override suspend fun setShowButtonsOnTop(showOnTop: Boolean) {
        duckChatDataStore.setShowButtonsOnTop(showOnTop)
    }

    override fun observeDuckChatUserEnabled(): Flow<Boolean> = duckChatDataStore.observeDuckChatUserEnabled()

    override fun observeInputScreenUserSettingEnabled(): Flow<Boolean> = duckChatDataStore.observeInputScreenUserSettingEnabled()

    override fun observeShowInBrowserMenu(): Flow<Boolean> = duckChatDataStore.observeShowInBrowserMenu()

    override fun observeShowInAddressBar(): Flow<Boolean> = duckChatDataStore.observeShowInAddressBar()

    override fun observeShowButtonsOnTop(): Flow<Boolean> = duckChatDataStore.observeShowButtonsOnTop()

    override suspend fun isDuckChatUserEnabled(): Boolean = duckChatDataStore.isDuckChatUserEnabled()

    override suspend fun isInputScreenUserSettingEnabled(): Boolean = duckChatDataStore.isInputScreenUserSettingEnabled()

    override suspend fun shouldShowInBrowserMenu(): Boolean = duckChatDataStore.getShowInBrowserMenu()

    override suspend fun shouldShowInAddressBar(): Boolean = duckChatDataStore.getShowInAddressBar()

    override suspend fun shouldShowButtonsOnTop(): Boolean = duckChatDataStore.getShowButtonsOnTop()

    override suspend fun registerOpened() {
        if (!duckChatDataStore.wasOpenedBefore()) {
            updateWidgets()
        }
        duckChatDataStore.registerOpened()
    }

    override suspend fun wasOpenedBefore(): Boolean = duckChatDataStore.wasOpenedBefore()

    override suspend fun lastSessionTimestamp(): Long = duckChatDataStore.lastSessionTimestamp()

    override suspend fun sessionDeltaInMinutes(): Long = duckChatDataStore.sessionDeltaTimestamp() / MS_TO_MINUTES

    private fun updateWidgets() {
        val intent = Intent(AppWidgetManager.ACTION_APPWIDGET_UPDATE)
        context.sendBroadcast(intent)
    }

    companion object {
        private const val MS_TO_MINUTES = 60000
    }
}
