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
import com.duckduckgo.duckplayer.impl.DuckPlayerFeatureRepository.UserValues
import com.squareup.anvil.annotations.ContributesBinding
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

interface DuckPlayerFeatureRepository {
    fun getDuckPlayerRC(): String

    fun setDuckPlayerRC(jsonString: String)

    suspend fun getUserValues(): UserValues

    fun setUserValues(userValues: UserValues)

    data class UserValues(
        val overlayInteracted: Boolean,
        val privatePlayerMode: PrivatePlayerMode,
    )
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
                duckPlayerDataStore.getDuckPlayerRC()
        }
    }

    override fun getDuckPlayerRC(): String {
        return duckPlayerRC
    }

    override fun setDuckPlayerRC(jsonString: String) {
        appCoroutineScope.launch(dispatcherProvider.io()) {
            duckPlayerDataStore.setDuckPlayerRC(jsonString)
            loadToMemory()
        }
    }

    override fun setUserValues(
        userValues: UserValues,
    ) {
        appCoroutineScope.launch(dispatcherProvider.io()) {
            duckPlayerDataStore.setOverlayInteracted(userValues.overlayInteracted)
            duckPlayerDataStore.setPrivatePlayerMode(userValues.privatePlayerMode.toString())
        }
    }

    override suspend fun getUserValues(): UserValues {
        return UserValues(
            overlayInteracted = duckPlayerDataStore.getOverlayInteracted(),
            privatePlayerMode = when (duckPlayerDataStore.getPrivatePlayerMode()) {
                PrivatePlayerMode.Enabled.value -> PrivatePlayerMode.Enabled
                PrivatePlayerMode.Disabled.value -> PrivatePlayerMode.Disabled
                else -> PrivatePlayerMode.AlwaysAsk
            },
        )
    }
}
