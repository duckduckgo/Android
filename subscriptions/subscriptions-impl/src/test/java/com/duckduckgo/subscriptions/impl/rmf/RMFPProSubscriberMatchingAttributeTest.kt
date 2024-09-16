package com.duckduckgo.subscriptions.impl.rmf

import com.duckduckgo.remote.messaging.api.JsonMatchingAttribute
import com.duckduckgo.subscriptions.api.Subscriptions
import kotlinx.coroutines.test.runTest
import org.junit.Assert
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class RMFPProSubscriberMatchingAttributeTest {

    private val subscriptions: Subscriptions = mock()

    private val attribute = RMFPProSubscriberMatchingAttribute(subscriptions)

    @Test
    fun evaluateWithWrongAttributeThenNull() = runTest {
        whenever(subscriptions.getAccessToken()).thenReturn(null)
        Assert.assertNull(attribute.evaluate(FakeStringMatchingAttribute { "" }))

        whenever(subscriptions.getAccessToken()).thenReturn("token")
        Assert.assertNull(attribute.evaluate(FakeStringMatchingAttribute { "" }))

        Assert.assertNull(attribute.map("wrong", JsonMatchingAttribute(value = false)))
        Assert.assertNull(attribute.map("wrong", JsonMatchingAttribute(value = true)))
    }

    @Test
    fun evaluateWithProEligibleMatchingAttributeThenValue() = runTest {
        whenever(subscriptions.getAccessToken()).thenReturn(null)
        Assert.assertTrue(attribute.evaluate(attribute.map("pproSubscriber", JsonMatchingAttribute(value = false))!!)!!)

        whenever(subscriptions.getAccessToken()).thenReturn("token")
        Assert.assertTrue(attribute.evaluate(attribute.map("pproSubscriber", JsonMatchingAttribute(value = true))!!)!!)

        whenever(subscriptions.getAccessToken()).thenReturn(null)
        Assert.assertFalse(attribute.evaluate(attribute.map("pproSubscriber", JsonMatchingAttribute(value = true))!!)!!)

        whenever(subscriptions.getAccessToken()).thenReturn("token")
        Assert.assertFalse(attribute.evaluate(attribute.map("pproSubscriber", JsonMatchingAttribute(value = false))!!)!!)
    }

    @Test
    fun mapNoProEligibleMatchingAttributeKeyThenReturnNull() = runTest {
        Assert.assertNull(attribute.map("wrong", JsonMatchingAttribute(value = null)))
        Assert.assertNull(attribute.map("wrong", JsonMatchingAttribute(value = true)))
        Assert.assertNull(attribute.map("wrong", JsonMatchingAttribute(value = false)))
    }
}
