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

package com.duckduckgo.networkprotection.impl

import android.content.SharedPreferences
import androidx.core.content.edit
import com.duckduckgo.anvil.annotations.ContributesRemoteFeature
import com.duckduckgo.app.di.AppCoroutineScope
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.data.store.api.SharedPreferencesProvider
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.feature.toggles.api.RemoteFeatureStoreNamed
import com.duckduckgo.feature.toggles.api.Toggle
import com.duckduckgo.feature.toggles.api.Toggle.DefaultValue
import com.duckduckgo.feature.toggles.api.Toggle.State
import com.squareup.anvil.annotations.ContributesBinding
import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import dagger.SingleInstanceIn
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

@ContributesRemoteFeature(
    scope = AppScope::class,
    featureName = "networkProtection",
    toggleStore = VpnRemoteFeaturesStore::class,
    settingsStore = VpnRemoteSettingsStore::class,
)
interface VpnRemoteFeatures {
    @Toggle.DefaultValue(true)
    fun self(): Toggle

    @DefaultValue(true)
    fun showExcludeAppPrompt(): Toggle // kill switch

    @DefaultValue(true)
    fun allowBlockMalware(): Toggle // kill switch
}

@ContributesBinding(AppScope::class)
@RemoteFeatureStoreNamed(VpnRemoteFeatures::class)
@SingleInstanceIn(AppScope::class)
class VpnRemoteFeaturesStore @Inject constructor(
    @AppCoroutineScope private val coroutineScope: CoroutineScope,
    private val dispatcherProvider: DispatcherProvider,
    private val sharedPreferencesProvider: SharedPreferencesProvider,
    moshi: Moshi,
) : Toggle.Store {

    private val stateAdapter: JsonAdapter<State> by lazy {
        moshi.newBuilder().add(KotlinJsonAdapterFactory()).build().adapter(State::class.java)
    }

    private val preferences: SharedPreferences? by lazy {
        runCatching {
            sharedPreferencesProvider.getSharedPreferences(PREFS_FILENAME, multiprocess = true, migrate = false)
        }.getOrNull()
    }

    override fun set(
        key: String,
        state: State,
    ) {
        preferences?.save(key, state)
    }

    override fun get(key: String): State? {
        return preferences?.getString(key, null)?.let {
            stateAdapter.fromJson(it)
        }
    }

    private fun SharedPreferences.save(
        key: String,
        state: State,
    ) {
        coroutineScope.launch(dispatcherProvider.io()) {
            edit(commit = true) { putString(key, stateAdapter.toJson(state)) }
        }
    }

    companion object {
        private const val PREFS_FILENAME = "com.duckduckgo.vpn.remote.features.v1"
    }
}
