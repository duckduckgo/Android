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

package com.duckduckgo.modalcoordinator.impl.store

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.modalcoordinator.impl.di.ModalEvaluatorCompletion
import com.squareup.anvil.annotations.ContributesBinding
import dagger.SingleInstanceIn
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.withContext
import javax.inject.Inject

interface ModalEvaluatorCompletionStore {
    /**
     * Records completion timestamp for any evaluator
     */
    suspend fun recordCompletion()

    /**
     * Checks if evaluator is blocked by 24-hour window due to a modal being shown.
     * @return true if blocked (within 24 hours of last completion)
     */
    suspend fun isBlockedBy24HourWindow(): Boolean
}

@ContributesBinding(AppScope::class)
@SingleInstanceIn(AppScope::class)
class ModalEvaluatorCompletionStoreImpl @Inject constructor(
    @ModalEvaluatorCompletion private val store: DataStore<Preferences>,
    private val dispatchers: DispatcherProvider,
) : ModalEvaluatorCompletionStore {

    override suspend fun recordCompletion() {
        withContext(dispatchers.io()) {
            val key = getKeyCompletionTimestamp()
            store.edit { it[key] = System.currentTimeMillis() }
        }
    }

    override suspend fun isBlockedBy24HourWindow(): Boolean {
        return withContext(dispatchers.io()) {
            val lastCompletion = getLastCompletionTimestamp() ?: return@withContext false
            val timeSinceCompletion = System.currentTimeMillis() - lastCompletion
            return@withContext timeSinceCompletion < TWENTY_FOUR_HOURS_MILLIS
        }
    }

    private suspend fun getLastCompletionTimestamp(): Long? {
        return store.data.firstOrNull()?.get(getKeyCompletionTimestamp())
    }

    private fun getKeyCompletionTimestamp(): Preferences.Key<Long> {
        return longPreferencesKey(KEY_NAME_COMPLETION_TIMESTAMP)
    }

    companion object {
        private const val KEY_NAME_COMPLETION_TIMESTAMP = "modal_evaluator_completion_timestamp"
        private const val TWENTY_FOUR_HOURS_MILLIS = 24 * 60 * 60 * 1000L
    }
}
