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

package com.duckduckgo.networkprotection.internal.network

import android.content.SharedPreferences
import com.duckduckgo.data.store.api.SharedPreferencesProvider
import javax.inject.Inject

class NetPInternalMtuProvider @Inject constructor(
    private val sharedPreferencesProvider: SharedPreferencesProvider,
) {
    private val preferences: SharedPreferences by lazy {
        sharedPreferencesProvider.getSharedPreferences(
            FILENAME,
            multiprocess = true,
            migrate = false,
        )
    }

    internal fun getMtu(): Int {
        return preferences.getInt(MTU_SIZE, 1280)
    }

    internal fun setMtu(mtu: Int?) {
        if (mtu == null) {
            preferences.edit().remove(MTU_SIZE).apply()
        } else {
            preferences.edit().putInt(MTU_SIZE, mtu).apply()
        }
    }
}

private const val FILENAME = "com.duckduckgo.netp.internal.mtu.v1"
private const val MTU_SIZE = "MTU_SIZE"
