package com.duckduckgo.remote.messaging.impl.matchers

import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.remote.messaging.api.JsonMatchingAttribute
import com.duckduckgo.remote.messaging.api.RemoteMessage
import com.duckduckgo.remote.messaging.api.RemoteMessagingRepository
import com.duckduckgo.remote.messaging.impl.AppRemoteMessagingRepositoryTest.Companion.aRemoteMessage
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class ShownMessageMatcherTest {
    @get:Rule
    val coroutineTestRule: CoroutineTestRule = CoroutineTestRule()

    private val remoteMessagingRepository: RemoteMessagingRepository = mock()

    @Test
    fun whenMapKeyIsMessageShownThenReturnMatchingAttribute() {
        val matcher = ShownMessageMatcher(remoteMessagingRepository)
        val jsonMatchingAttribute = JsonMatchingAttribute(value = listOf("1", "2", "3"))
        val result = matcher.map("messageShown", jsonMatchingAttribute)
        assertTrue(result is ShownMessageMatchingAttribute)
        assertEquals(listOf("1", "2", "3"), (result as ShownMessageMatchingAttribute).messageIds)
    }

    @Test
    fun whenJsonMatchingAttributeValueIsWrongTypeThenReturnNull() = runTest {
        val matcher = ShownMessageMatcher(remoteMessagingRepository)
        val jsonMatchingAttribute = JsonMatchingAttribute(value = listOf(1, true, 23L))
        val result = matcher.map("messageShown", jsonMatchingAttribute)
        assertNull(result)
    }

    @Test
    fun whenJsonMatchingAttributeValueContainsWrongTypesThenReturnOnlyStringOnes() = runTest {
        val matcher = ShownMessageMatcher(remoteMessagingRepository)
        val jsonMatchingAttribute = JsonMatchingAttribute(value = listOf("1", true, 23L))
        val result = matcher.map("messageShown", jsonMatchingAttribute)
        assertEquals(listOf("1"), (result as ShownMessageMatchingAttribute).messageIds)
    }

    @Test
    fun whenJsonMatchingAttributeValueIsNullThenReturnNull() = runTest {
        val matcher = ShownMessageMatcher(remoteMessagingRepository)
        val jsonMatchingAttribute = JsonMatchingAttribute(value = null)
        val result = matcher.map("messageShown", jsonMatchingAttribute)
        assertNull(result)
    }

    @Test
    fun whenJsonMatchingAttributeValueIsEmptyThenReturnNull() = runTest {
        val matcher = ShownMessageMatcher(remoteMessagingRepository)
        val jsonMatchingAttribute = JsonMatchingAttribute(value = emptyList<String>())
        val result = matcher.map("messageShown", jsonMatchingAttribute)
        assertNull(result)
    }

    @Test
    fun whenJsonMatchingAttributeValueIsNotListThenReturnNull() = runTest {
        val matcher = ShownMessageMatcher(remoteMessagingRepository)
        val jsonMatchingAttribute = JsonMatchingAttribute(value = 1)
        val result = matcher.map("messageShown", jsonMatchingAttribute)
        assertNull(result)
    }

    @Test
    fun whenShownMessageIdMatchesThenReturnTrue() = runTest {
        givenMessageIdShown(listOf("1", "2", "3"))
        val matcher = ShownMessageMatcher(remoteMessagingRepository)
        val matchingAttribute = ShownMessageMatchingAttribute(listOf("1", "2", "3"))
        val result = matcher.evaluate(matchingAttribute)!!
        assertTrue(result)
    }

    @Test
    fun whenOneShownMessageMatchesThenReturnTrue() = runTest {
        givenMessageIdShown(listOf("1", "2", "3"))
        val matcher = ShownMessageMatcher(remoteMessagingRepository)
        val matchingAttribute = ShownMessageMatchingAttribute(listOf("0", "1", "4"))
        val result = matcher.evaluate(matchingAttribute)!!
        assertTrue(result)
    }

    @Test
    fun whenNoShownMessageMatchesThenReturnFalse() = runTest {
        givenMessageIdShown(listOf("1", "2", "3"))
        val matcher = ShownMessageMatcher(remoteMessagingRepository)
        val matchingAttribute = ShownMessageMatchingAttribute(listOf("0", "4", "5"))
        val result = matcher.evaluate(matchingAttribute)!!
        assertFalse(result)
    }

    @Test
    fun whenOnlyCurrentMessageIdMatchesThenReturnFalse() = runTest {
        givenCurrentActiveMessage(aRemoteMessage("1"))
        val matcher = ShownMessageMatcher(remoteMessagingRepository)
        val matchingAttribute = ShownMessageMatchingAttribute(listOf("1"))
        val result = matcher.evaluate(matchingAttribute)!!
        assertFalse(result)
    }

    @Test
    fun whenCurrentMessageAndOtherIdsMatchThenReturnTrue() = runTest {
        givenMessageIdShown(listOf("2", "3"))
        givenCurrentActiveMessage(aRemoteMessage("1"))
        val matcher = ShownMessageMatcher(remoteMessagingRepository)
        val matchingAttribute = ShownMessageMatchingAttribute(listOf("1", "2", "3"))
        val result = matcher.evaluate(matchingAttribute)!!
        assertTrue(result)
    }

    @Test(expected = AssertionError::class)
    fun whenEmptyListThenThrowAssertionError() = runTest {
        givenMessageIdShown(listOf("1", "2", "3"))
        val matcher = ShownMessageMatcher(remoteMessagingRepository)
        val matchingAttribute = ShownMessageMatchingAttribute(emptyList())
        matcher.evaluate(matchingAttribute)
    }

    private fun givenCurrentActiveMessage(message: RemoteMessage) {
        whenever(remoteMessagingRepository.message()).thenReturn(message)
    }

    private fun givenMessageIdShown(listOf: List<String>) {
        listOf.forEach {
            whenever(remoteMessagingRepository.didShow(it)).thenReturn(true)
        }
    }
}
