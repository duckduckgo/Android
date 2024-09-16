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

package com.duckduckgo.sync.store

import android.content.Context
import android.content.SharedPreferences

class TestSharedPrefsProvider(val context: Context) : SharedPrefsProvider {
    override fun getEncryptedSharedPrefs(fileName: String): SharedPreferences? {
        return context.getSharedPreferences(fileName, Context.MODE_PRIVATE)
    }

    override fun getSharedPrefs(fileName: String): SharedPreferences {
        return context.getSharedPreferences(fileName, Context.MODE_PRIVATE)
    }
}
