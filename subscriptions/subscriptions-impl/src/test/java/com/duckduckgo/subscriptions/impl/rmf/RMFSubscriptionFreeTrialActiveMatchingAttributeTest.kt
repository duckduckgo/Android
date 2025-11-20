package com.duckduckgo.subscriptions.impl.rmf

import com.duckduckgo.remote.messaging.api.JsonMatchingAttribute
import com.duckduckgo.subscriptions.impl.repository.AuthRepository
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class RMFSubscriptionFreeTrialActiveMatchingAttributeTest {

    private val mockAuthRepository: AuthRepository = mock()
    private val attribute = RMFSubscriptionFreeTrialActiveMatchingAttribute(mockAuthRepository)

    @Test
    fun evaluateWithWrongAttributeThenNull() = runTest {
        whenever(mockAuthRepository.isFreeTrialActive()).thenReturn(true)

        assertNull(attribute.evaluate(FakeStringMatchingAttribute { "" }))
        assertNull(attribute.map("wrong", JsonMatchingAttribute(value = false)))
        assertNull(attribute.map("wrong", JsonMatchingAttribute(value = true)))
    }

    @Test
    fun whenRemoteValueIsTrueAndFreeTrialIsActiveThenEvaluateToTrue() = runTest {
        whenever(mockAuthRepository.isFreeTrialActive()).thenReturn(true)

        val result = attribute.evaluate(attribute.map("subscriptionFreeTrialActive", JsonMatchingAttribute(value = true))!!)

        assertTrue(result!!)
    }

    @Test
    fun whenRemoteValueIsTrueAndFreeTrialIsNotActiveThenEvaluateToFalse() = runTest {
        whenever(mockAuthRepository.isFreeTrialActive()).thenReturn(false)

        val result = attribute.evaluate(attribute.map("subscriptionFreeTrialActive", JsonMatchingAttribute(value = true))!!)

        assertFalse(result!!)
    }

    @Test
    fun whenRemoteValueIsFalseAndFreeTrialIsNotActiveThenEvaluateToTrue() = runTest {
        whenever(mockAuthRepository.isFreeTrialActive()).thenReturn(false)

        val result = attribute.evaluate(attribute.map("subscriptionFreeTrialActive", JsonMatchingAttribute(value = false))!!)

        assertTrue(result!!)
    }

    @Test
    fun whenRemoteValueIsFalseAndFreeTrialIsActiveThenEvaluateToFalse() = runTest {
        whenever(mockAuthRepository.isFreeTrialActive()).thenReturn(true)

        val result = attribute.evaluate(attribute.map("subscriptionFreeTrialActive", JsonMatchingAttribute(value = false))!!)

        assertFalse(result!!)
    }

    @Test
    fun mapNoMatchingAttributeKeyThenReturnNull() = runTest {
        assertNull(attribute.map("wrong", JsonMatchingAttribute(value = null)))
        assertNull(attribute.map("wrong", JsonMatchingAttribute(value = true)))
        assertNull(attribute.map("wrong", JsonMatchingAttribute(value = false)))
    }

    @Test
    fun mapNullValueThenReturnNull() = runTest {
        assertNull(attribute.map("subscriptionFreeTrialActive", JsonMatchingAttribute(value = null)))
    }
}
