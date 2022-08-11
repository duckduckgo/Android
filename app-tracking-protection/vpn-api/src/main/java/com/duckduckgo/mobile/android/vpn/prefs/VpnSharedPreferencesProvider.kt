/*
 * Copyright (c) 2022 DuckDuckGo
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

package com.duckduckgo.mobile.android.vpn.prefs

import android.content.SharedPreferences

interface VpnSharedPreferencesProvider {
    /**
     * Returns an instance of Shared preferences
     * @param name Name of the shared preferences
     * @param multiprocess `true` if the shared preferences will be accessed from several processes else `false`
     * @param migrate `true` if the shared preferences existed prior to use the [VpnSharedPreferencesProvider], else `false`
     */
    fun getSharedPreferences(name: String, multiprocess: Boolean = false, migrate: Boolean = false): SharedPreferences
}
