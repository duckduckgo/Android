package com.duckduckgo.subscriptions.impl.withdrawal

import com.duckduckgo.data.store.api.FakeSharedPreferencesProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.math.BigDecimal

class RealWithdrawalStoreTest {

    private val store = RealWithdrawalStore(FakeSharedPreferencesProvider())

    @Test
    fun whenNothingSavedThenReturnNull() {
        assertNull(store.getCompletedWithdrawal())
    }

    @Test
    fun whenWithdrawalSavedThenReturnIt() {
        val withdrawal = CompletedWithdrawal(withdrawnAt = 1792065600000, refundedAmount = BigDecimal("9.99"), currency = "EUR")

        store.setCompletedWithdrawal(withdrawal)

        assertEquals(withdrawal, store.getCompletedWithdrawal())
    }

    @Test
    fun whenClearedThenReturnNull() {
        store.setCompletedWithdrawal(CompletedWithdrawal(withdrawnAt = 1792065600000, refundedAmount = BigDecimal("9.99"), currency = "EUR"))

        store.clear()

        assertNull(store.getCompletedWithdrawal())
    }
}
