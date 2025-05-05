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

package com.duckduckgo.app.browser.certificates.remoteconfig

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import com.duckduckgo.anvil.annotations.ContributesRemoteFeature
import com.duckduckgo.app.di.AppCoroutineScope
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.feature.toggles.api.RemoteFeatureStoreNamed
import com.duckduckgo.feature.toggles.api.Toggle
import com.duckduckgo.feature.toggles.api.Toggle.DefaultFeatureValue
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
    featureName = "sslCertificates",
    toggleStore = SSLCertificatesFeatureStore::class,
)
interface SSLCertificatesFeature {

    /**
     * @return `true` when the remote config has the global "sslCertificates" feature flag enabled
     * If the remote feature is not present defaults to `true`
     */
    @Toggle.DefaultValue(DefaultFeatureValue.TRUE)
    fun self(): Toggle

    /**
     * @return `true` when the remote config has the "allowBypass" flag enabled
     * If the remote feature is not present defaults to `false`
     */
    @Toggle.DefaultValue(DefaultFeatureValue.FALSE)
    fun allowBypass(): Toggle
}

@ContributesBinding(AppScope::class)
@SingleInstanceIn(AppScope::class)
@RemoteFeatureStoreNamed(SSLCertificatesFeature::class)
class SSLCertificatesFeatureStore @Inject constructor(
    @AppCoroutineScope private val coroutineScope: CoroutineScope,
    private val dispatcherProvider: DispatcherProvider,
    private val context: Context,
    moshi: Moshi,
) : Toggle.Store {

    private val preferences: SharedPreferences by lazy { context.getSharedPreferences(FILENAME, Context.MODE_PRIVATE) }

    private val stateAdapter: JsonAdapter<State> by lazy {
        moshi.newBuilder().add(KotlinJsonAdapterFactory()).build().adapter(State::class.java)
    }

    override fun set(
        key: String,
        state: State,
    ) {
        preferences.save(key, state)
    }

    override fun get(key: String): State? {
        return preferences.getString(key, null)?.let {
            stateAdapter.fromJson(it)
        }
    }

    private fun SharedPreferences.save(key: String, state: State) {
        coroutineScope.launch(dispatcherProvider.io()) {
            edit(commit = true) { putString(key, stateAdapter.toJson(state)) }
        }
    }

    companion object {
        // This is the backwards compatible value
        const val FILENAME = "com.duckduckgo.feature.toggle.sslCertificates"
    }
}
