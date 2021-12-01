/*
 * Copyright (c) 2021 DuckDuckGo
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

package com.duckduckgo.privacy.config.impl.observers

import android.content.Context
import androidx.annotation.WorkerThread
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleObserver
import androidx.lifecycle.OnLifecycleEvent
import com.duckduckgo.app.di.AppCoroutineScope
import com.duckduckgo.app.global.DispatcherProvider
import com.duckduckgo.di.scopes.AppObjectGraph
import com.duckduckgo.privacy.config.impl.PrivacyConfigPersister
import com.duckduckgo.privacy.config.impl.R
import com.duckduckgo.privacy.config.impl.models.JsonPrivacyConfig
import com.duckduckgo.privacy.config.impl.network.JSONObjectAdapter
import com.squareup.anvil.annotations.ContributesMultibinding
import com.squareup.moshi.Moshi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import javax.inject.Inject
import dagger.SingleInstanceIn

@WorkerThread
@SingleInstanceIn(AppObjectGraph::class)
@ContributesMultibinding(AppObjectGraph::class)
class LocalPrivacyConfigObserver @Inject constructor(
    private val context: Context,
    private val privacyConfigPersister: PrivacyConfigPersister,
    @AppCoroutineScope val coroutineScope: CoroutineScope,
    private val dispatcherProvider: DispatcherProvider
) : LifecycleObserver {

    @OnLifecycleEvent(Lifecycle.Event.ON_CREATE)
    fun storeLocalPrivacyConfig() {
        coroutineScope.launch(dispatcherProvider.io()) {
            loadPrivacyConfig()
        }
    }

    private suspend fun loadPrivacyConfig() {
        val privacyConfigJson = getPrivacyConfigFromFile()
        privacyConfigJson?.let {
            privacyConfigPersister.persistPrivacyConfig(it)
        }
    }

    private fun getPrivacyConfigFromFile(): JsonPrivacyConfig? {
        val moshi = Moshi.Builder().add(JSONObjectAdapter()).build()
        val json = context.resources.openRawResource(R.raw.privacy_config).bufferedReader().use { it.readText() }
        val adapter = moshi.adapter(JsonPrivacyConfig::class.java)
        return adapter.fromJson(json)
    }

}
