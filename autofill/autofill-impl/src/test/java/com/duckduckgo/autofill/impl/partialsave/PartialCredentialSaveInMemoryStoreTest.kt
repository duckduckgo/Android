package com.duckduckgo.autofill.impl.partialsave

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.duckduckgo.autofill.impl.encoding.UrlUnicodeNormalizerImpl
import com.duckduckgo.autofill.impl.partialsave.PartialCredentialSaveInMemoryStore.Companion.MAX_VALIDITY_MS
import com.duckduckgo.autofill.impl.partialsave.PartialCredentialSaveInMemoryStore.Companion.TIME_WINDOW_FOR_BEING_RECENT_MS
import com.duckduckgo.autofill.impl.time.TimeProvider
import com.duckduckgo.autofill.impl.urlmatcher.AutofillDomainNameUrlMatcher
import com.duckduckgo.common.test.CoroutineTestRule
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

@RunWith(AndroidJUnit4::class)
class PartialCredentialSaveInMemoryStoreTest {

    @get:Rule
    val coroutineTestRule: CoroutineTestRule = CoroutineTestRule()

    private val mockTimeProvider: TimeProvider = mock()

    private val testee = PartialCredentialSaveInMemoryStore(
        urlMatcher = AutofillDomainNameUrlMatcher(unicodeNormalizer = UrlUnicodeNormalizerImpl()),
        timeProvider = mockTimeProvider,
        dispatchers = coroutineTestRule.testDispatcherProvider,
    )

    @Before
    fun setup() {
        setFixedTimestampForTimeProvider()
    }

    @Test
    fun whenNothingSavedThenReturnsNullUsername() = runTest {
        assertNull(testee.consumeUsernameFromBackFill(URL))
    }

    @Test
    fun whenEntrySavedALongTimeAgoWithMatchingEtldThenNullUsername() = runTest {
        runWithSimulatedTimestamp(0L) {
            testee.saveUsername(URL, "user")
        }
        assertNull(testee.consumeUsernameFromBackFill(URL))
    }

    @Test
    fun whenEntrySaved3MinutesAnd1SecondAgoWithMatchingEtldThenNullUsername() = runTest {
        // 1 second too long to be considered valid
        val timestampTooOldToBeValid = timestampExactlyOnValidityWindow() - TimeUnit.SECONDS.toMillis(1)
        runWithSimulatedTimestamp(timestampTooOldToBeValid) {
            testee.saveUsername(URL, "user")
        }
        assertNull(testee.consumeUsernameFromBackFill(URL))
    }

    @Test
    fun whenEntrySaved2MinutesAnd59SecondAgoWithMatchingEtldThenUsernameReturned() = runTest {
        // still valid by only 1 second
        val timestampWithinValidityWindow = timestampExactlyOnValidityWindow() + TimeUnit.SECONDS.toMillis(1)
        runWithSimulatedTimestamp(timestampWithinValidityWindow) {
            testee.saveUsername(URL, "user")
        }
        assertEquals("user", testee.consumeUsernameFromBackFill(URL))
    }

    @Test
    fun whenEntrySavedExactly3MinutesAgoWithMatchingEtldThenUsernameReturned() = runTest {
        runWithSimulatedTimestamp(timestampExactlyOnValidityWindow()) {
            testee.saveUsername(URL, "user")
        }
        assertEquals("user", testee.consumeUsernameFromBackFill(URL))
    }

    @Test
    fun whenUsernameRetrievedThenStillAvailableAfter() = runTest {
        runWithSimulatedTimestamp(timestampExactlyOnValidityWindow()) {
            testee.saveUsername(URL, "user")
        }
        assertEquals("user", testee.consumeUsernameFromBackFill(URL))

        // check it's still there
        assertEquals("user", testee.consumeUsernameFromBackFill(URL))
    }

    @Test
    fun whenUsernameSavedButNotUsedThenRecentlyUsedCheckReturnsFalse() = runTest {
        runWithSimulatedTimestamp(timestampExactlyOnValidityWindow()) {
            testee.saveUsername(URL, "user")
        }
        assertFalse(testee.wasBackFilledRecently(URL, "user"))
    }

    @Test
    fun whenUsernameUsedRecentlyThenRecentlyUsedCheckReturnsTrue() = runTest {
        runWithSimulatedTimestamp(timestampExactlyOnValidityWindow()) {
            testee.saveUsername(URL, "user")
        }
        testee.consumeUsernameFromBackFill(URL)
        assertTrue(testee.wasBackFilledRecently(URL, "user"))
    }

    @Test
    fun whenUsernameUsedButNotRecentlyThenRecentlyUsedCheckReturnsFalse() = runTest {
        runWithSimulatedTimestamp(timestampExactlyOnValidityWindow()) {
            testee.saveUsername(URL, "user")
        }

        // outside window where we'd consider it 'recent'
        runWithSimulatedTimestamp(timestampExactlyOnUsedRecentlyWindow() - TimeUnit.SECONDS.toMillis(1)) {
            testee.consumeUsernameFromBackFill(URL)
        }

        assertFalse(testee.wasBackFilledRecently(URL, "user"))
    }

    @Test
    fun whenEntrySavedButEldPlusOneNotAMatchThenNotReturned() = runTest {
        runWithSimulatedTimestamp(timestampExactlyOnValidityWindow()) {
            testee.saveUsername("example.org", "user")
        }
        assertNull(testee.consumeUsernameFromBackFill("example.com"))
    }

    private suspend fun runWithSimulatedTimestamp(
        timestamp: Long,
        block: suspend () -> Unit,
    ) {
        whenever(mockTimeProvider.currentTimeMillis()).thenReturn(timestamp)
        block()
        setFixedTimestampForTimeProvider()
    }

    private fun timestampExactlyOnValidityWindow(): Long = CURRENT_TIME_MS - MAX_VALIDITY_MS
    private fun timestampExactlyOnUsedRecentlyWindow(): Long = CURRENT_TIME_MS - TIME_WINDOW_FOR_BEING_RECENT_MS

    private fun setFixedTimestampForTimeProvider() {
        whenever(mockTimeProvider.currentTimeMillis()).thenReturn(CURRENT_TIME_MS)
    }

    companion object {
        private const val URL = "example.com"
        private const val CURRENT_TIME_MS = 1733935378547L // 2024-12-11T16:43
    }
}
