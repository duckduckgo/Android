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

package com.duckduckgo.subscriptions.internal

import android.content.SharedPreferences
import androidx.core.content.edit
import com.duckduckgo.app.di.AppCoroutineScope
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.data.store.api.SharedPreferencesProvider
import com.duckduckgo.di.scopes.AppScope
import com.squareup.anvil.annotations.ContributesBinding
import dagger.SingleInstanceIn
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import javax.inject.Inject

interface SubscriptionsInternalStore {
    fun setBaseUrl(url: String?)
    fun getBaseUrl(): String?
}

@ContributesBinding(AppScope::class)
@SingleInstanceIn(AppScope::class)
class SharedPreferencesSubscriptionsInternalStore @Inject constructor(
    @AppCoroutineScope private val coroutineScope: CoroutineScope,
    private val dispatcherProvider: DispatcherProvider,
    private val sharedPreferencesProvider: SharedPreferencesProvider,
) : SubscriptionsInternalStore {

    private val preferences: SharedPreferences by lazy {
        sharedPreferencesProvider.getSharedPreferences(PREFS_FILENAME, multiprocess = false, migrate = false)
    }

    override fun setBaseUrl(url: String?) {
        coroutineScope.launch(dispatcherProvider.io()) {
            preferences.edit(commit = true) { putString("baseUrl", url) }
        }
    }

    override fun getBaseUrl(): String? = preferences.getString("baseUrl", null)

    companion object {
        private const val PREFS_FILENAME = "com.duckduckgo.subscriptions.internal"
    }
}
