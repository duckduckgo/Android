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

package com.duckduckgo.autofill.impl.email.incontext.store

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.di.scopes.AppScope
import com.squareup.anvil.annotations.ContributesBinding
import dagger.SingleInstanceIn
import kotlinx.coroutines.withContext
import javax.inject.Inject

interface EmailProtectionInContextDataStore {
    suspend fun timestampUserChoseNeverAskAgain(): Long?
    suspend fun onUserChoseNeverAskAgain()
    suspend fun resetNeverAskAgainChoice()

    suspend fun updateMaximumPermittedDaysSinceInstallation(installDays: Int)
    suspend fun getMaximumPermittedDaysSinceInstallation(): Int
}

@SingleInstanceIn(AppScope::class)
@ContributesBinding(AppScope::class)
class RealEmailProtectionInContextDataStore @Inject constructor(
    private val context: Context,
    private val dispatchers: DispatcherProvider,
) : EmailProtectionInContextDataStore {

    private val preferences: SharedPreferences by lazy { context.getSharedPreferences(FILENAME, Context.MODE_PRIVATE) }

    override suspend fun timestampUserChoseNeverAskAgain(): Long? = withContext(dispatchers.io()) {
        val timestamp = preferences.getLong(KEY_USER_CHOSE_NEVER_ASK_AGAIN, -1)
        return@withContext if (timestamp == -1L) null else timestamp
    }

    override suspend fun onUserChoseNeverAskAgain() {
        update { putLong(KEY_USER_CHOSE_NEVER_ASK_AGAIN, System.currentTimeMillis()) }
    }

    override suspend fun resetNeverAskAgainChoice() {
        update { remove(KEY_USER_CHOSE_NEVER_ASK_AGAIN) }
    }

    override suspend fun updateMaximumPermittedDaysSinceInstallation(installDays: Int) {
        update { putInt(KEY_MAXIMUM_REQUIRED_INSTALL_DAYS, installDays) }
    }

    override suspend fun getMaximumPermittedDaysSinceInstallation(): Int = withContext(dispatchers.io()) {
        preferences.getInt(KEY_MAXIMUM_REQUIRED_INSTALL_DAYS, -1)
    }

    private suspend inline fun update(crossinline block: SharedPreferences.Editor.() -> Unit) {
        withContext(dispatchers.io()) {
            preferences.edit { block() }
        }
    }

    companion object {
        const val FILENAME = "com.duckduckgo.autofill.impl.email.incontext.store.RealInContextEmailProtectionDataStore"
        const val KEY_USER_CHOSE_NEVER_ASK_AGAIN = "NEVER_ASK_AGAIN"
        const val KEY_MAXIMUM_REQUIRED_INSTALL_DAYS = "REQUIRED_INSTALL_DAYS"
    }
}
