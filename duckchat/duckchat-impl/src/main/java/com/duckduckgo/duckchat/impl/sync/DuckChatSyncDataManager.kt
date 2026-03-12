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

package com.duckduckgo.duckchat.impl.sync

import com.duckduckgo.app.di.AppCoroutineScope
import com.duckduckgo.appbuildconfig.api.AppBuildConfig
import com.duckduckgo.appbuildconfig.api.isInternalBuild
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.common.utils.checkMainThread
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.duckchat.impl.feature.DuckChatFeature
import com.duckduckgo.duckchat.impl.repository.DuckChatFeatureRepository
import com.duckduckgo.sync.api.engine.DeletableDataManager
import com.duckduckgo.sync.api.engine.DeletableType
import com.duckduckgo.sync.api.engine.SyncDeletionRequest
import com.duckduckgo.sync.api.engine.SyncDeletionResponse
import com.duckduckgo.sync.api.engine.SyncErrorResponse
import com.squareup.anvil.annotations.ContributesMultibinding
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import logcat.LogPriority
import logcat.logcat
import javax.inject.Inject

@ContributesMultibinding(scope = AppScope::class, boundType = DeletableDataManager::class)
class DuckChatSyncDataManager @Inject constructor(
    private val duckChatSyncRepository: DuckChatSyncRepository,
    private val duckChatFeatureRepository: DuckChatFeatureRepository,
    private val dispatchers: DispatcherProvider,
    private val appBuildConfig: AppBuildConfig,
    private val duckChatFeature: DuckChatFeature,
    @AppCoroutineScope private val appCoroutineScope: CoroutineScope,
) : DeletableDataManager {

    override fun getType(): DeletableType = DeletableType.DUCK_AI_CHATS

    override fun getDeletions(): SyncDeletionRequest? {
        if (appBuildConfig.isInternalBuild()) checkMainThread()

        return runBlocking(dispatchers.io()) {
            if (!duckChatFeature.supportsSyncChatsDeletion().isEnabled()) {
                logcat { "DuckChat-Sync: Duck AI chat sync disabled, skipping deletion check" }
                return@runBlocking null
            }

            if (!duckChatFeatureRepository.isAIChatHistoryEnabled()) {
                logcat { "DuckChat-Sync: Chat history disabled, skipping deletion check" }
                return@runBlocking null
            }

            val deletionTimestamp = duckChatSyncRepository.getLastDuckAiChatDeletionTimestamp()
            formatRequest(deletionTimestamp)
        }
    }

    override fun onSuccess(response: SyncDeletionResponse) {
        logcat { "DuckChat-Sync: Duck AI chats deletion sync successful" }
        response.untilTimestamp?.let { timestamp ->
            appCoroutineScope.launch(dispatchers.io()) {
                duckChatSyncRepository.clearDeletionTimestampIfMatches(timestamp)
            }
        }
    }

    override fun onError(syncErrorResponse: SyncErrorResponse) {
        // no-op, keep timestamp around for next time
    }

    private fun formatRequest(deletionTimestamp: String?): SyncDeletionRequest? {
        if (deletionTimestamp == null) {
            logcat(LogPriority.DEBUG) { "DuckChat-Sync: no need to inform sync of duck ai chat deletion, no timestamp available" }
            return null
        }

        logcat { "DuckChat-Sync: need to inform sync of duck ai chat deletion with timestamp: $deletionTimestamp" }

        return SyncDeletionRequest(
            type = DeletableType.DUCK_AI_CHATS,
            untilTimestamp = deletionTimestamp,
        )
    }
}
