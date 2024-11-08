/*
 * Copyright (c) 2024 DuckDuckGo
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

package com.duckduckgo.privacy.dashboard.impl

import com.duckduckgo.app.di.AppCoroutineScope
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.common.utils.formatters.time.DatabaseDateFormatter
import com.duckduckgo.di.scopes.AppScope
import com.squareup.anvil.annotations.ContributesBinding
import dagger.SingleInstanceIn
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import javax.inject.Inject

interface ToggleReportFeatureRepository {
    suspend fun setToggleReportRemoteConfigJson(jsonString: String)

    fun getToggleReportRemoteConfigJson(): String

    suspend fun storeDismissLogicEnabled(dismissLogicEnabled: Boolean)

    suspend fun storePromptLimitLogicEnabled(promptLimitLogicEnabled: Boolean)

    suspend fun storeDismissInterval(dismissInterval: Int)

    suspend fun storePromptInterval(promptInterval: Int)

    suspend fun storeMaxPromptCount(maxPromptCount: Int)

    suspend fun insertTogglePromptDismiss()

    suspend fun insertTogglePromptSend()

    fun shouldPrompt(): Boolean
}

@SingleInstanceIn(AppScope::class)
@ContributesBinding(AppScope::class)
class RealToggleReportFeatureRepository @Inject constructor(
    private val toggleReportDataStore: ToggleReportDataStore,
    @AppCoroutineScope private val appCoroutineScope: CoroutineScope,
    private val dispatcherProvider: DispatcherProvider,
) : ToggleReportFeatureRepository {

    override suspend fun setToggleReportRemoteConfigJson(jsonString: String) {
        appCoroutineScope.launch(dispatcherProvider.io()) {
            toggleReportDataStore.setToggleReportRemoteConfigJson(jsonString)
        }
    }

    override fun getToggleReportRemoteConfigJson(): String {
        return toggleReportDataStore.getToggleReportRemoteConfigJson()
    }

    override suspend fun storeDismissLogicEnabled(dismissLogicEnabled: Boolean) {
        toggleReportDataStore.storeDismissLogicEnabled(dismissLogicEnabled)
    }

    override suspend fun storePromptLimitLogicEnabled(promptLimitLogicEnabled: Boolean) {
        toggleReportDataStore.storePromptLimitLogicEnabled(promptLimitLogicEnabled)
    }

    override suspend fun storeDismissInterval(dismissInterval: Int) {
        toggleReportDataStore.storeDismissInterval(dismissInterval)
    }

    override suspend fun storePromptInterval(promptInterval: Int) {
        toggleReportDataStore.storePromptInterval(promptInterval)
    }

    override suspend fun storeMaxPromptCount(maxPromptCount: Int) {
        toggleReportDataStore.storeMaxPromptCount(maxPromptCount)
    }

    override suspend fun insertTogglePromptDismiss() {
        toggleReportDataStore.insertTogglePromptDismiss(DatabaseDateFormatter.iso8601())
    }

    override suspend fun insertTogglePromptSend() {
        toggleReportDataStore.insertTogglePromptSend(DatabaseDateFormatter.iso8601())
    }

    override fun shouldPrompt(): Boolean {
        return toggleReportDataStore.shouldPrompt()
    }
}
