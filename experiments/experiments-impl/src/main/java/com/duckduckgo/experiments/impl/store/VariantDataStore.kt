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

package com.duckduckgo.experiments.impl.store

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import com.duckduckgo.di.scopes.AppScope
import com.squareup.anvil.annotations.ContributesBinding
import javax.inject.Inject

interface VariantDataStore {
    var variantInitialised: Boolean
}

@ContributesBinding(AppScope::class)
class VariantSharedPreferences @Inject constructor(
    private val context: Context,
) : VariantDataStore {

    private val preferences: SharedPreferences by lazy { context.getSharedPreferences(FILENAME, Context.MODE_PRIVATE) }

    override var variantInitialised: Boolean
        get() = preferences.getBoolean(KEY_VARIANT_INITIALISED, false)
        set(value) = preferences.edit { putBoolean(KEY_VARIANT_INITIALISED, value) }

    companion object {
        private const val FILENAME = "com.duckduckgo.app.experimentVariants"
        private const val KEY_VARIANT_INITIALISED = "VARIANT_INITIALISED"
    }
}
