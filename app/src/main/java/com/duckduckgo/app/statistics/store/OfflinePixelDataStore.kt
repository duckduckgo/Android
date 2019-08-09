/*
 * Copyright (c) 2019 DuckDuckGo
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

package com.duckduckgo.app.statistics.store

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import javax.inject.Inject


interface OfflinePixelDataStore {
    var webRendererGoneCrashCount: Int
    var webRendererGoneOtherCount: Int
}

class OfflinePixelSharedPreferences @Inject constructor(private val context: Context) : OfflinePixelDataStore {

    override var webRendererGoneCrashCount: Int
        get() = preferences.getInt(KEY_WEB_RENDERER_GONE_CRASH_COUNT, 0)
        set(value) = preferences.edit(true) { putInt(KEY_WEB_RENDERER_GONE_CRASH_COUNT, value) }

    override var webRendererGoneOtherCount: Int
        get() = preferences.getInt(KEY_WEB_RENDERER_GONE_OTHER_COUNT, 0)
        set(value) = preferences.edit(true) { putInt(KEY_WEB_RENDERER_GONE_OTHER_COUNT, value) }


    private val preferences: SharedPreferences
        get() = context.getSharedPreferences(FILENAME, Context.MODE_PRIVATE)

    companion object {
        const val FILENAME = "com.duckduckgo.app.statistics.offline.pixels"
        private const val KEY_WEB_RENDERER_GONE_CRASH_COUNT = "WEB_RENDERER_GONE_CRASH_COUNT"
        private const val KEY_WEB_RENDERER_GONE_OTHER_COUNT = "WEB_RENDERER_GONE_OTHER_COUNT"
    }
}