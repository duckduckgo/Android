/*
 * Copyright (c) 2024 DuckDuckGo
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

package com.duckduckgo.newtabpage.impl.settings

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import com.duckduckgo.di.scopes.AppScope
import com.squareup.anvil.annotations.ContributesBinding
import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import dagger.SingleInstanceIn
import javax.inject.Inject

interface NewTabSettingsStore {
    var sectionSettings: List<String>
    var shortcutSettings: List<String>
}

@ContributesBinding(AppScope::class)
@SingleInstanceIn(AppScope::class)
class AppNewTabSettingsStore @Inject constructor(private val context: Context) : NewTabSettingsStore {

    private val preferences: SharedPreferences by lazy { context.getSharedPreferences(FILENAME, Context.MODE_PRIVATE) }
    private val stringListType = Types.newParameterizedType(List::class.java, String::class.java)
    private val stringListAdapter: JsonAdapter<List<String>> = Moshi.Builder().build().adapter(stringListType)

    override var sectionSettings: List<String>
        get() = toStringList(preferences.getString(KEY_NEW_TAB_SECTION_SETTINGS_ORDER, null))
        set(value) = preferences.edit(true) { putString(KEY_NEW_TAB_SECTION_SETTINGS_ORDER, fromStringList(value)) }

    override var shortcutSettings: List<String>
        get() = toStringList(preferences.getString(KEY_NEW_TAB_SHORTCUT_SETTINGS_ORDER, null))
        set(value) = preferences.edit(true) { putString(KEY_NEW_TAB_SHORTCUT_SETTINGS_ORDER, fromStringList(value)) }

    private fun toStringList(value: String?): List<String> {
        if (value != null) {
            return stringListAdapter.fromJson(value)!!
        } else {
            return emptyList()
        }
    }

    private fun fromStringList(value: List<String>): String {
        return stringListAdapter.toJson(value)
    }

    companion object {
        const val FILENAME = "com.duckduckgo.newtabpage.settings"
        private const val KEY_NEW_TAB_SECTION_SETTINGS_ORDER = "KEY_NEW_TAB_SECTION_SETTINGS_ORDER"
        private const val KEY_NEW_TAB_SHORTCUT_SETTINGS_ORDER = "KEY_NEW_TAB_SHORTCUT_SETTINGS_ORDER"
    }
}
