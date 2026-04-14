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

package com.duckduckgo.app.anr.internal.setting

import com.duckduckgo.data.store.api.SharedPreferencesProvider
import com.duckduckgo.di.scopes.AppScope
import dagger.SingleInstanceIn
import javax.inject.Inject

@SingleInstanceIn(AppScope::class)
class CrashpadUploadConfig @Inject constructor(sharedPreferencesProvider: SharedPreferencesProvider) {

    private val prefs by lazy { sharedPreferencesProvider.getSharedPreferences("crashpad_upload_config") }

    var uploadUrl: String
        get() = prefs.getString(KEY_UPLOAD_URL, "") ?: ""
        set(value) { prefs.edit().putString(KEY_UPLOAD_URL, value).apply() }

    var noRateLimit: Boolean
        get() = prefs.getBoolean(KEY_NO_RATE_LIMIT, false)
        set(value) { prefs.edit().putBoolean(KEY_NO_RATE_LIMIT, value).apply() }

    companion object {
        private const val KEY_UPLOAD_URL = "upload_url"
        private const val KEY_NO_RATE_LIMIT = "no_rate_limit"
    }
}
