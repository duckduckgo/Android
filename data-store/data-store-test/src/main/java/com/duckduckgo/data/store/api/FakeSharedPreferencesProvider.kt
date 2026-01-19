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

package com.duckduckgo.data.store.api

import android.content.SharedPreferences
import com.duckduckgo.common.test.api.InMemorySharedPreferences

class FakeSharedPreferencesProvider : SharedPreferencesProvider {
    override fun getSharedPreferences(name: String, multiprocess: Boolean, migrate: Boolean): SharedPreferences {
        return InMemorySharedPreferences()
    }

    override fun getEncryptedSharedPreferences(
        name: String,
        multiprocess: Boolean,
    ): SharedPreferences {
        return getSharedPreferences(name, multiprocess)
    }

    override suspend fun getMigratedEncryptedSharedPreferences(name: String): SharedPreferences? {
        return getSharedPreferences(name, true)
    }
}
