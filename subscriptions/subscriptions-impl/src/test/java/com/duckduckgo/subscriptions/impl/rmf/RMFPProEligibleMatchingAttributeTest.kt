package com.duckduckgo.subscriptions.impl.rmf

import com.duckduckgo.remote.messaging.api.JsonMatchingAttribute
import com.duckduckgo.subscriptions.api.Subscriptions
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class RMFPProEligibleMatchingAttributeTest {

    private val subscriptions: Subscriptions = mock()

    private val attribute = RMFPProEligibleMatchingAttribute(subscriptions)

    @Test
    fun evaluateWithWrongAttributeThenNull() = runTest {
        whenever(subscriptions.isEligible()).thenReturn(false)
        assertNull(attribute.evaluate(FakeStringMatchingAttribute { "" }))

        whenever(subscriptions.isEligible()).thenReturn(true)
        assertNull(attribute.evaluate(FakeStringMatchingAttribute { "" }))

        assertNull(attribute.map("wrong", JsonMatchingAttribute(value = false)))
        assertNull(attribute.map("wrong", JsonMatchingAttribute(value = true)))
    }

    @Test
    fun evaluateWithProEligibleMatchingAttributeThenValue() = runTest {
        whenever(subscriptions.isEligible()).thenReturn(false)
        assertTrue(attribute.evaluate(attribute.map("pproEligible", JsonMatchingAttribute(value = false))!!)!!)

        whenever(subscriptions.isEligible()).thenReturn(true)
        assertTrue(attribute.evaluate(attribute.map("pproEligible", JsonMatchingAttribute(value = true))!!)!!)

        whenever(subscriptions.isEligible()).thenReturn(false)
        assertFalse(attribute.evaluate(attribute.map("pproEligible", JsonMatchingAttribute(value = true))!!)!!)

        whenever(subscriptions.isEligible()).thenReturn(true)
        assertFalse(attribute.evaluate(attribute.map("pproEligible", JsonMatchingAttribute(value = false))!!)!!)
    }

    @Test
    fun mapNoProEligibleMatchingAttributeKeyThenReturnNull() = runTest {
        assertNull(attribute.map("wrong", JsonMatchingAttribute(value = null)))
        assertNull(attribute.map("wrong", JsonMatchingAttribute(value = true)))
        assertNull(attribute.map("wrong", JsonMatchingAttribute(value = false)))
    }
}
