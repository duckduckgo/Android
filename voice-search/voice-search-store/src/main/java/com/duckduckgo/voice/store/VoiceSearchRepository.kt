/*
 * Copyright (c) 2022 DuckDuckGo
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

package com.duckduckgo.voice.store

import com.duckduckgo.voice.api.VoiceSearchLauncher.VoiceSearchMode
import com.duckduckgo.voice.api.VoiceSearchStatusListener

interface VoiceSearchRepository {
    fun declinePermissionForever()
    fun acceptRationaleDialog()
    fun saveLoggedAvailability()
    fun getHasPermissionDeclinedForever(): Boolean
    fun getHasAcceptedRationaleDialog(): Boolean
    fun getHasLoggedAvailability(): Boolean
    fun isVoiceSearchUserEnabled(default: Boolean): Boolean
    fun setVoiceSearchUserEnabled(enabled: Boolean)
    fun countVoiceSearchDismissed(): Int
    fun dismissVoiceSearch()
    fun resetVoiceSearchDismissed()
    fun getLastSelectedMode(): VoiceSearchMode
    fun setLastSelectedMode(mode: VoiceSearchMode)
}

class RealVoiceSearchRepository constructor(
    private val dataStore: VoiceSearchDataStore,
    private val voiceSearchStatusListener: VoiceSearchStatusListener,
) : VoiceSearchRepository {
    override fun declinePermissionForever() {
        dataStore.permissionDeclinedForever = true
    }

    override fun acceptRationaleDialog() {
        dataStore.userAcceptedRationaleDialog = true
    }

    override fun saveLoggedAvailability() {
        dataStore.availabilityLogged = true
    }

    override fun getHasPermissionDeclinedForever(): Boolean = dataStore.permissionDeclinedForever

    override fun getHasAcceptedRationaleDialog(): Boolean = dataStore.userAcceptedRationaleDialog

    override fun getHasLoggedAvailability(): Boolean = dataStore.availabilityLogged

    override fun isVoiceSearchUserEnabled(default: Boolean): Boolean = dataStore.isVoiceSearchEnabled(default)

    override fun setVoiceSearchUserEnabled(enabled: Boolean) {
        dataStore.setVoiceSearchEnabled(enabled)
        voiceSearchStatusListener.voiceSearchStatusChanged()
    }

    override fun countVoiceSearchDismissed(): Int {
        return dataStore.countVoiceSearchDismissed
    }

    override fun dismissVoiceSearch() {
        dataStore.countVoiceSearchDismissed = dataStore.countVoiceSearchDismissed + 1
    }

    override fun resetVoiceSearchDismissed() {
        dataStore.countVoiceSearchDismissed = 0
    }

    override fun getLastSelectedMode(): VoiceSearchMode {
        return dataStore.lastSelectedMode
    }

    override fun setLastSelectedMode(mode: VoiceSearchMode) {
        dataStore.lastSelectedMode = mode
    }
}
