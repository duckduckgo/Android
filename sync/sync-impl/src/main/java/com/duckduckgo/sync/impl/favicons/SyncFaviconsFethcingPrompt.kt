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

package com.duckduckgo.sync.impl.favicons

import androidx.annotation.WorkerThread
import com.duckduckgo.sync.api.favicons.FaviconsFetchingPrompt
import com.duckduckgo.sync.api.favicons.FaviconsFetchingStore
import com.duckduckgo.sync.impl.Result
import com.duckduckgo.sync.impl.Result.Success
import com.duckduckgo.sync.impl.SyncAccountRepository
import logcat.logcat
import java.lang.Error

class SyncFaviconsFetchingPrompt(
    private val faviconsFetchingStore: FaviconsFetchingStore,
    private val syncAccountRepository: SyncAccountRepository,
) : FaviconsFetchingPrompt {

    // should show prompt when
    // Have Sync enabled.
    // Have at least 2 devices in the Sync account.
    // Visit bookmarks list OR new tab page.
    // Have favicons fetching disabled
    // Have at least 1 bookmark without favicon loaded on current screen.
    // https://app.asana.com/0/1157893581871903/1206440765317539
    @WorkerThread
    override fun shouldShow(): Boolean {
        if (faviconsFetchingStore.promptShown) {
            return false
        }

        if (faviconsFetchingStore.isFaviconsFetchingEnabled) {
            return false
        }

        return if (syncAccountRepository.isSignedIn()) {
            val result = syncAccountRepository.getConnectedDevices()
            logcat { "Sync: Connected Devices $result" }
            when (result) {
                is Result.Error -> false
                is Success -> result.data.size >= MIN_CONNECTED_DEVICES_FOR_PROMPT
            }
        } else {
            false
        }
    }

    override fun onPromptAnswered(fetchingEnabled: Boolean) {
        logcat { "Favicons: Feching en" }
        faviconsFetchingStore.promptShown = true
        faviconsFetchingStore.isFaviconsFetchingEnabled = fetchingEnabled
    }

    companion object {
        private const val MIN_CONNECTED_DEVICES_FOR_PROMPT = 2
    }
}
