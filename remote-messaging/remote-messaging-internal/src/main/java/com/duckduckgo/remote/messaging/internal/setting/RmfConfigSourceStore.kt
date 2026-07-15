/*
 * Copyright (c) 2026 DuckDuckGo
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

package com.duckduckgo.remote.messaging.internal.setting

import android.content.SharedPreferences
import androidx.core.content.edit
import com.duckduckgo.data.store.api.SharedPreferencesProvider
import com.duckduckgo.di.scopes.AppScope
import com.squareup.anvil.annotations.ContributesBinding
import javax.inject.Inject

enum class RmfConfigMode {
    PRODUCTION,
    STAGING,
    PR_NUMBER,
    CUSTOM_URL,
}

/** Internal-only source selection for the RMF config download. Local, never remote-controlled. */
interface RmfConfigSourceStore {
    var mode: RmfConfigMode
    var prNumber: String
    var customUrl: String
}

@ContributesBinding(AppScope::class)
class RealRmfConfigSourceStore @Inject constructor(
    private val sharedPreferencesProvider: SharedPreferencesProvider,
) : RmfConfigSourceStore {

    private val preferences: SharedPreferences by lazy {
        sharedPreferencesProvider.getSharedPreferences(FILENAME, multiprocess = false, migrate = false)
    }

    override var mode: RmfConfigMode
        get() = preferences.getString(KEY_MODE, null)
            ?.let { runCatching { RmfConfigMode.valueOf(it) }.getOrNull() }
            ?: RmfConfigMode.PRODUCTION
        set(value) = preferences.edit { putString(KEY_MODE, value.name) }

    override var prNumber: String
        get() = preferences.getString(KEY_PR_NUMBER, null).orEmpty()
        set(value) = preferences.edit { putString(KEY_PR_NUMBER, value) }

    override var customUrl: String
        get() = preferences.getString(KEY_CUSTOM_URL, null).orEmpty()
        set(value) = preferences.edit { putString(KEY_CUSTOM_URL, value) }

    companion object {
        private const val FILENAME = "com.duckduckgo.remotemessaging.internal.configsource"
        private const val KEY_MODE = "KEY_MODE"
        private const val KEY_PR_NUMBER = "KEY_PR_NUMBER"
        private const val KEY_CUSTOM_URL = "KEY_CUSTOM_URL"
    }
}
