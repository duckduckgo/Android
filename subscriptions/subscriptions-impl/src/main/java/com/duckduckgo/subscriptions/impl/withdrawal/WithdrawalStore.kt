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

package com.duckduckgo.subscriptions.impl.withdrawal

import android.content.SharedPreferences
import androidx.core.content.edit
import com.duckduckgo.data.store.api.SharedPreferencesProvider
import com.duckduckgo.di.scopes.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.SingleIn
import java.math.BigDecimal
import javax.inject.Inject

interface WithdrawalStore {
    fun getCompletedWithdrawal(): CompletedWithdrawal?
    fun setCompletedWithdrawal(withdrawal: CompletedWithdrawal)
    fun clear()
}

data class CompletedWithdrawal(
    val withdrawnAt: Long,
    val refundedAmount: BigDecimal,
    val currency: String,
)

@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class)
class RealWithdrawalStore @Inject constructor(
    private val sharedPreferencesProvider: SharedPreferencesProvider,
) : WithdrawalStore {

    private val preferences: SharedPreferences by lazy {
        sharedPreferencesProvider.getSharedPreferences(FILENAME)
    }

    override fun getCompletedWithdrawal(): CompletedWithdrawal? {
        val withdrawnAt = preferences.getLong(KEY_WITHDRAWN_AT, 0L).takeIf { it > 0L } ?: return null
        val refundedAmount = preferences.getString(KEY_REFUNDED_AMOUNT, null)?.toBigDecimalOrNull() ?: return null
        val currency = preferences.getString(KEY_CURRENCY, null) ?: return null
        return CompletedWithdrawal(withdrawnAt = withdrawnAt, refundedAmount = refundedAmount, currency = currency)
    }

    override fun setCompletedWithdrawal(withdrawal: CompletedWithdrawal) {
        preferences.edit {
            putLong(KEY_WITHDRAWN_AT, withdrawal.withdrawnAt)
            putString(KEY_REFUNDED_AMOUNT, withdrawal.refundedAmount.toPlainString())
            putString(KEY_CURRENCY, withdrawal.currency)
        }
    }

    override fun clear() {
        preferences.edit { clear() }
    }

    private companion object {
        private const val FILENAME = "com.duckduckgo.subscriptions.withdrawal"
        private const val KEY_WITHDRAWN_AT = "KEY_WITHDRAWN_AT"
        private const val KEY_REFUNDED_AMOUNT = "KEY_REFUNDED_AMOUNT"
        private const val KEY_CURRENCY = "KEY_CURRENCY"
    }
}
