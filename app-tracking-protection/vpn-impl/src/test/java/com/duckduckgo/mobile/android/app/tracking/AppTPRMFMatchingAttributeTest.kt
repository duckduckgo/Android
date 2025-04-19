package com.duckduckgo.mobile.android.app.tracking

import com.duckduckgo.remote.messaging.api.JsonMatchingAttribute
import com.duckduckgo.remote.messaging.api.MatchingAttribute
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class AppTPRMFMatchingAttributeTest {

    private val appTrackingProtection: AppTrackingProtection = mock()

    private val attribute = AppTPRMFMatchingAttribute(appTrackingProtection)

    @Test
    fun evaluateWithWrongAttributeThenNull() = runTest {
        whenever(appTrackingProtection.isOnboarded()).thenReturn(false)
        assertNull(attribute.evaluate(FakeMatchingAttribute))

        whenever(appTrackingProtection.isOnboarded()).thenReturn(true)
        assertNull(attribute.evaluate(FakeMatchingAttribute))
    }

    @Test
    fun evaluateWithAppTPMatchingAttributeThenValue() = runTest {
        whenever(appTrackingProtection.isOnboarded()).thenReturn(false)
        assertTrue(attribute.evaluate(AppTPMatchingAttribute(false))!!)

        whenever(appTrackingProtection.isOnboarded()).thenReturn(true)
        assertTrue(attribute.evaluate(AppTPMatchingAttribute(true))!!)

        whenever(appTrackingProtection.isOnboarded()).thenReturn(false)
        assertFalse(attribute.evaluate(AppTPMatchingAttribute(true))!!)

        whenever(appTrackingProtection.isOnboarded()).thenReturn(true)
        assertFalse(attribute.evaluate(AppTPMatchingAttribute(false))!!)
    }

    @Test
    fun mapAppTPMatchingAttributeKeyThenReturnAppTPMatchingAttribute() = runTest {
        assertNull(attribute.map("atpOnboarded", JsonMatchingAttribute(value = null)))
        assertEquals(AppTPMatchingAttribute(true), attribute.map("atpOnboarded", JsonMatchingAttribute(value = true)))
        assertEquals(AppTPMatchingAttribute(false), attribute.map("atpOnboarded", JsonMatchingAttribute(value = false)))
    }

    @Test
    fun mapNoAppTPMatchingAttributeKeyThenReturnNull() = runTest {
        assertNull(attribute.map("wrong", JsonMatchingAttribute(value = null)))
        assertNull(attribute.map("wrong", JsonMatchingAttribute(value = true)))
        assertNull(attribute.map("wrong", JsonMatchingAttribute(value = false)))
    }
}

private object FakeMatchingAttribute : MatchingAttribute
