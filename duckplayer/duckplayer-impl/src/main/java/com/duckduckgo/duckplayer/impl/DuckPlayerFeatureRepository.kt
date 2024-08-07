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

package com.duckduckgo.duckplayer.impl

import com.duckduckgo.app.di.AppCoroutineScope
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.duckplayer.api.DuckPlayer.UserPreferences
import com.duckduckgo.duckplayer.api.PrivatePlayerMode.AlwaysAsk
import com.duckduckgo.duckplayer.api.PrivatePlayerMode.Disabled
import com.duckduckgo.duckplayer.api.PrivatePlayerMode.Enabled
import com.squareup.anvil.annotations.ContributesBinding
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

interface DuckPlayerFeatureRepository {
    fun getDuckPlayerRemoteConfigJson(): String

    fun setDuckPlayerRemoteConfigJson(jsonString: String)

    suspend fun getUserPreferences(): UserPreferences

    fun observeUserPreferences(): Flow<UserPreferences>

    fun setUserPreferences(userPreferences: UserPreferences)
    suspend fun storeDuckPlayerDisabledHelpPageLink(duckPlayerDisabledHelpPageLink: String)

    suspend fun getDuckPlayerDisabledHelpPageLink(): String
}

@ContributesBinding(AppScope::class)
class RealDuckPlayerFeatureRepository @Inject constructor(
    private val duckPlayerDataStore: DuckPlayerDataStore,
    @AppCoroutineScope private val appCoroutineScope: CoroutineScope,
    private val dispatcherProvider: DispatcherProvider,
) : DuckPlayerFeatureRepository {

    private var duckPlayerRC = ""

    init {
        loadToMemory()
    }

    private fun loadToMemory() {
        appCoroutineScope.launch(dispatcherProvider.io()) {
            duckPlayerRC =
                duckPlayerDataStore.getDuckPlayerRemoteConfigJson()
        }
    }

    override fun getDuckPlayerRemoteConfigJson(): String {
        return duckPlayerRC
    }

    override fun setDuckPlayerRemoteConfigJson(jsonString: String) {
        appCoroutineScope.launch(dispatcherProvider.io()) {
            duckPlayerDataStore.setDuckPlayerRemoteConfigJson(jsonString)
            loadToMemory()
        }
    }

    override fun setUserPreferences(
        userPreferences: UserPreferences,
    ) {
        appCoroutineScope.launch(dispatcherProvider.io()) {
            duckPlayerDataStore.setOverlayInteracted(userPreferences.overlayInteracted)
            duckPlayerDataStore.setPrivatePlayerMode(userPreferences.privatePlayerMode.value)
        }
    }

    override fun observeUserPreferences(): Flow<UserPreferences> {
        return duckPlayerDataStore.observePrivatePlayerMode()
            .combine(duckPlayerDataStore.observeOverlayInteracted()) { privatePlayerMode, overlayInteracted ->
                UserPreferences(
                    overlayInteracted = overlayInteracted,
                    privatePlayerMode = when (privatePlayerMode) {
                        Enabled.value -> Enabled
                        Disabled.value -> Disabled
                        else -> AlwaysAsk
                    },
                )
            }
    }

    override suspend fun getUserPreferences(): UserPreferences {
        return UserPreferences(
            overlayInteracted = duckPlayerDataStore.getOverlayInteracted(),
            privatePlayerMode = when (duckPlayerDataStore.getPrivatePlayerMode()) {
                Enabled.value -> Enabled
                Disabled.value -> Disabled
                else -> AlwaysAsk
            },
        )
    }

    override suspend fun storeDuckPlayerDisabledHelpPageLink(duckPlayerDisabledHelpPageLink: String) {
        duckPlayerDataStore.storeDuckPlayerDisabledHelpPageLink(duckPlayerDisabledHelpPageLink)
    }

    override suspend fun getDuckPlayerDisabledHelpPageLink(): String {
        return duckPlayerDataStore.getDuckPlayerDisabledHelpPageLink()
    }
}
