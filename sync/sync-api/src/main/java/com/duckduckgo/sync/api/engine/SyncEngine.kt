/*
 * Copyright (c) 2023 DuckDuckGo
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

package com.duckduckgo.sync.api.engine

interface SyncEngine {

    /**
     * Entry point to the Sync Engine
     * See Tech Design: Sync Updating/Polling Strategy https://app.asana.com/0/481882893211075/1204040479708519/f
     */
    fun triggerSync(trigger: SyncTrigger)

    /**
     * Sync Feature has been disabled / device has been removed
     * This is an opportunity for Features to do some local cleanup if needed
     */
    fun onSyncDisabled()

    /**
     * Represent each possible trigger fo
     * See Tech Design: Sync Engine https://app.asana.com/0/481882893211075/1204303361994831/f
     * [BACKGROUND_SYNC] -> Sync triggered by a Background Worker
     * [APP_OPEN] -> Sync triggered after App is opened
     * [FEATURE_READ] -> Sync triggered when a feature screen is opened (Bookmarks screen, etc...)
     * [DATA_CHANGE] -> Sync triggered because data associated to a Syncable object has changed (new bookmark added)
     * [ACCOUNT_CREATION] -> Sync triggered after creating a new Sync account
     * [ACCOUNT_LOGIN] -> Sync triggered after login into an already existing sync account
     */
    enum class SyncTrigger {
        BACKGROUND_SYNC,
        APP_OPEN,
        FEATURE_READ,
        DATA_CHANGE,
        ACCOUNT_CREATION,
        ACCOUNT_LOGIN,
    }
}
