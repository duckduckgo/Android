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
package com.duckduckgo.nextsteps.impl

import android.content.SharedPreferences
import androidx.core.content.edit
import com.duckduckgo.data.store.api.SharedPreferencesProvider
import com.duckduckgo.di.scopes.AppScope
import com.squareup.anvil.annotations.ContributesBinding
import javax.inject.Inject

/** Device-local state of the Next Steps section: card order, front card impressions and dismissed cards. */
interface NextStepsItemsStore {
    fun itemOrder(): List<String>

    fun saveItemOrder(ids: List<String>)

    fun dismissedItemIds(): Set<String>

    fun addDismissedItemId(id: String)

    fun frontImpressions(itemId: String): Int

    fun incrementFrontImpressions(itemId: String)
}

@ContributesBinding(AppScope::class)
class RealNextStepsItemsStore @Inject constructor(
    private val sharedPreferencesProvider: SharedPreferencesProvider,
) : NextStepsItemsStore {

    private val preferences: SharedPreferences by lazy {
        sharedPreferencesProvider.getSharedPreferences(FILENAME, multiprocess = false, migrate = false)
    }

    override fun itemOrder(): List<String> =
        preferences.getString(KEY_ORDER, null)?.split(ORDER_SEPARATOR)?.filter { it.isNotEmpty() }.orEmpty()

    override fun saveItemOrder(ids: List<String>) {
        preferences.edit { putString(KEY_ORDER, ids.joinToString(ORDER_SEPARATOR)) }
    }

    override fun dismissedItemIds(): Set<String> = preferences.getStringSet(KEY_DISMISSED, null)?.toSet().orEmpty()

    override fun addDismissedItemId(id: String) {
        preferences.edit { putStringSet(KEY_DISMISSED, dismissedItemIds() + id) }
    }

    override fun frontImpressions(itemId: String): Int =
        if (preferences.getString(KEY_FRONT_ITEM_ID, null) == itemId) preferences.getInt(KEY_FRONT_IMPRESSIONS, 0) else 0

    override fun incrementFrontImpressions(itemId: String) {
        val impressions = frontImpressions(itemId) + 1
        preferences.edit {
            putString(KEY_FRONT_ITEM_ID, itemId)
            putInt(KEY_FRONT_IMPRESSIONS, impressions)
        }
    }

    private companion object {
        const val FILENAME = "com.duckduckgo.nextsteps.items"
        const val KEY_ORDER = "KEY_ORDER"
        const val KEY_DISMISSED = "KEY_DISMISSED"
        const val KEY_FRONT_ITEM_ID = "KEY_FRONT_ITEM_ID"
        const val KEY_FRONT_IMPRESSIONS = "KEY_FRONT_IMPRESSIONS"
        const val ORDER_SEPARATOR = "\n"
    }
}
