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

package com.duckduckgo.autofill.store

import android.annotation.SuppressLint
import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit

interface InternalTestUserStore {
    var isVerifiedInternalTestUser: Boolean
}

@SuppressLint("DenyListedApi")
class RealInternalTestUserStore constructor(
    private val context: Context,
) : InternalTestUserStore {
    private val preferences: SharedPreferences by lazy { context.getSharedPreferences(FILENAME, Context.MODE_PRIVATE) }

    override var isVerifiedInternalTestUser: Boolean
        get() = preferences.getBoolean(KEY_IS_VERIFIED_INTERNAL_TEST_USER, false)
        set(value) {
            preferences.edit {
                putBoolean(KEY_IS_VERIFIED_INTERNAL_TEST_USER, value)
            }
        }

    companion object {
        private const val FILENAME = "com.duckduckgo.autofill.store.InternalTestUserStore"
        private const val KEY_IS_VERIFIED_INTERNAL_TEST_USER = "KEY_IS_VERIFIED_INTERNAL_TEST_USER"
    }
}
