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

package com.duckduckgo.mobile.android.vpn.feature

import android.content.SharedPreferences
import android.content.SharedPreferences.OnSharedPreferenceChangeListener
import androidx.core.content.edit
import com.duckduckgo.anvil.annotations.ContributesRemoteFeature
import com.duckduckgo.app.di.AppCoroutineScope
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.feature.toggles.api.RemoteFeatureStoreNamed
import com.duckduckgo.feature.toggles.api.Toggle
import com.duckduckgo.feature.toggles.api.Toggle.DefaultValue
import com.duckduckgo.feature.toggles.api.Toggle.State
import com.duckduckgo.mobile.android.vpn.feature.settings.ExceptionListsSettingStore
import com.duckduckgo.mobile.android.vpn.prefs.VpnSharedPreferencesProvider
import com.squareup.anvil.annotations.ContributesBinding
import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import dagger.SingleInstanceIn
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@ContributesRemoteFeature(
    scope = AppScope::class,
    featureName = "appTrackerProtection",
    toggleStore = AppTpRemoteFeaturesStore::class,
    settingsStore = ExceptionListsSettingStore::class,
)
interface AppTpRemoteFeatures {
    @Toggle.DefaultValue(true)
    fun self(): Toggle

    @Toggle.DefaultValue(true)
    fun restartOnConnectivityLoss(): Toggle

    @DefaultValue(true)
    fun setSearchDomains(): Toggle // kill switch
}

@ContributesBinding(AppScope::class)
@SingleInstanceIn(AppScope::class)
@RemoteFeatureStoreNamed(AppTpRemoteFeatures::class)
class AppTpRemoteFeaturesStore @Inject constructor(
    @AppCoroutineScope private val coroutineScope: CoroutineScope,
    private val dispatcherProvider: DispatcherProvider,
    private val vpnSharedPreferencesProvider: VpnSharedPreferencesProvider,
    moshi: Moshi,
) : Toggle.Store {

    private val togglesCache = ConcurrentHashMap<String, State>()

    private val preferences: SharedPreferences by lazy {
        vpnSharedPreferencesProvider.getSharedPreferences(PREFS_FILENAME, multiprocess = true, migrate = false)
    }
    private val stateAdapter: JsonAdapter<State> by lazy {
        moshi.newBuilder().add(KotlinJsonAdapterFactory()).build().adapter(State::class.java)
    }
    private val listener = OnSharedPreferenceChangeListener { preferences, key ->
        coroutineScope.launch(dispatcherProvider.io()) {
            key?.let {
                val state = preferences.load(key)
                if (state == null) {
                    togglesCache.remove(key)
                } else {
                    togglesCache[key] = state
                }
            }
        }
    }

    init {
        coroutineScope.launch(dispatcherProvider.io()) {
            preferences.load()
            preferences.registerOnSharedPreferenceChangeListener(listener)
        }
    }

    override fun set(key: String, state: State) {
        togglesCache[key] = state
        preferences.save(key, state)
    }

    override fun get(key: String): State? {
        return togglesCache[key]
    }

    private fun SharedPreferences.save(key: String, state: State) {
        coroutineScope.launch(dispatcherProvider.io()) {
            edit { putString(key, stateAdapter.toJson(state)) }
        }
    }

    private suspend fun SharedPreferences.load() = withContext(dispatcherProvider.io()) {
        togglesCache.clear()
        preferences.all.keys.forEach { key ->
            load(key)?.let {
                togglesCache[key] = it
            }
        }
    }

    private fun SharedPreferences.load(key: String): State? {
        val stringState = getString(key, null)
        return if (stringState == null) {
            null
        } else {
            stateAdapter.fromJson(stringState)
        }
    }

    companion object {
        private const val PREFS_FILENAME = "com.duckduckgo.vpn.atp.remote.features.v1"
    }
}
