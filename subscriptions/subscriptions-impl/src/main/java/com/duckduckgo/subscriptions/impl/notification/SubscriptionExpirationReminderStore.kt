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

package com.duckduckgo.subscriptions.impl.notification

import android.content.SharedPreferences
import androidx.core.content.edit
import com.duckduckgo.data.store.api.SharedPreferencesProvider
import com.duckduckgo.di.scopes.AppScope
import dagger.SingleInstanceIn
import javax.inject.Inject

@SingleInstanceIn(AppScope::class)
class SubscriptionExpirationReminderStore @Inject constructor(
    private val sharedPreferencesProvider: SharedPreferencesProvider,
) {
    private val preferences: SharedPreferences by lazy {
        sharedPreferencesProvider.getSharedPreferences(FILENAME)
    }

    var daysBeforeCancel: Int?
        get() = preferences.getInt(KEY_DAYS_BEFORE_CANCEL, -1).takeIf { it >= 0 }
        set(value) {
            preferences.edit {
                if (value != null) {
                    putInt(KEY_DAYS_BEFORE_CANCEL, value)
                } else {
                    remove(KEY_DAYS_BEFORE_CANCEL)
                }
            }
        }

    companion object {
        const val FILENAME = "com.duckduckgo.subscriptions.expiration.reminder"
        const val KEY_DAYS_BEFORE_CANCEL = "KEY_DAYS_BEFORE_CANCEL"
    }
}
