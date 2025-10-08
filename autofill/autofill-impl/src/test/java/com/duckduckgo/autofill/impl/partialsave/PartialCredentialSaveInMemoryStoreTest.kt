package com.duckduckgo.autofill.impl.partialsave

import android.annotation.SuppressLint
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.duckduckgo.autofill.api.AutofillFeature
import com.duckduckgo.autofill.impl.encoding.UrlUnicodeNormalizerImpl
import com.duckduckgo.autofill.impl.partialsave.PartialCredentialSaveInMemoryStore.Companion.MAX_VALIDITY_MS
import com.duckduckgo.autofill.impl.partialsave.PartialCredentialSaveInMemoryStore.Companion.TIME_WINDOW_FOR_BEING_RECENT_MS
import com.duckduckgo.autofill.impl.time.TimeProvider
import com.duckduckgo.autofill.impl.urlmatcher.AutofillDomainNameUrlMatcher
import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.feature.toggles.api.FakeFeatureToggleFactory
import com.duckduckgo.feature.toggles.api.Toggle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.util.concurrent.TimeUnit

@RunWith(AndroidJUnit4::class)
class PartialCredentialSaveInMemoryStoreTest {

    @get:Rule
    val coroutineTestRule: CoroutineTestRule = CoroutineTestRule()

    private val mockTimeProvider: TimeProvider = mock()
    private val feature = FakeFeatureToggleFactory.create(AutofillFeature::class.java)

    private val testee = PartialCredentialSaveInMemoryStore(
        urlMatcher = AutofillDomainNameUrlMatcher(unicodeNormalizer = UrlUnicodeNormalizerImpl()),
        timeProvider = mockTimeProvider,
        dispatchers = coroutineTestRule.testDispatcherProvider,
        autofillFeature = feature,
    )

    @Before
    fun setup() {
        setFixedTimestampForTimeProvider()
        configureAutofillFeatureState(enabled = true)
    }

    @Test
    fun whenNothingSavedThenReturnsNullUsername() = runTest {
        assertNull(testee.getUsernameForBackFilling(URL))
    }

    @Test
    fun whenEntrySavedALongTimeAgoWithMatchingEtldThenNullUsername() = runTest {
        runWithSimulatedTimestamp(0L) {
            testee.saveUsername(URL, "user")
        }
        assertNull(testee.getUsernameForBackFilling(URL))
    }

    @Test
    fun whenEntrySaved3MinutesAnd1SecondAgoWithMatchingEtldThenNullUsername() = runTest {
        // 1 second too long to be considered valid
        val timestampTooOldToBeValid = timestampExactlyOnValidityWindow() - TimeUnit.SECONDS.toMillis(1)
        runWithSimulatedTimestamp(timestampTooOldToBeValid) {
            testee.saveUsername(URL, "user")
        }
        assertNull(testee.getUsernameForBackFilling(URL))
    }

    @Test
    fun whenEntrySaved2MinutesAnd59SecondAgoWithMatchingEtldThenUsernameReturned() = runTest {
        // still valid by only 1 second
        val timestampWithinValidityWindow = timestampExactlyOnValidityWindow() + TimeUnit.SECONDS.toMillis(1)
        runWithSimulatedTimestamp(timestampWithinValidityWindow) {
            testee.saveUsername(URL, "user")
        }
        assertEquals("user", testee.getUsernameForBackFilling(URL))
    }

    @Test
    fun whenEntrySavedExactly3MinutesAgoWithMatchingEtldThenUsernameReturned() = runTest {
        runWithSimulatedTimestamp(timestampExactlyOnValidityWindow()) {
            testee.saveUsername(URL, "user")
        }
        assertEquals("user", testee.getUsernameForBackFilling(URL))
    }

    @Test
    fun whenUsernameRetrievedThenStillAvailableAfter() = runTest {
        runWithSimulatedTimestamp(timestampExactlyOnValidityWindow()) {
            testee.saveUsername(URL, "user")
        }
        assertEquals("user", testee.getUsernameForBackFilling(URL))

        // check it's still there
        assertEquals("user", testee.getUsernameForBackFilling(URL))
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
        testee.getUsernameForBackFilling(URL)
        assertTrue(testee.wasBackFilledRecently(URL, "user"))
    }

    @Test
    fun whenUsernameUsedButNotRecentlyThenRecentlyUsedCheckReturnsFalse() = runTest {
        runWithSimulatedTimestamp(timestampExactlyOnValidityWindow()) {
            testee.saveUsername(URL, "user")
        }

        // outside window where we'd consider it 'recent'
        runWithSimulatedTimestamp(timestampExactlyOnUsedRecentlyWindow() - TimeUnit.SECONDS.toMillis(1)) {
            testee.getUsernameForBackFilling(URL)
        }

        assertFalse(testee.wasBackFilledRecently(URL, "user"))
    }

    @Test
    fun whenEntrySavedButEldPlusOneNotAMatchThenNotReturned() = runTest {
        runWithSimulatedTimestamp(timestampExactlyOnValidityWindow()) {
            testee.saveUsername("example.org", "user")
        }
        assertNull(testee.getUsernameForBackFilling("example.com"))
    }

    @Test
    fun whenFeatureDisabledThenNullUsernameReturned() = runTest {
        configureAutofillFeatureState(enabled = false)

        // this would be eligible for backFill except for feature flag being disabled
        runWithSimulatedTimestamp(timestampExactlyOnValidityWindow()) {
            testee.saveUsername(URL, "user")
        }
        assertNull(testee.getUsernameForBackFilling(URL))
    }

    @Test
    fun whenFeatureDisabledThenUsedRecentlyReturnsFalse() = runTest {
        configureAutofillFeatureState(enabled = false)

        // this would be eligible for backFill, and would return true for 'used recently'
        runWithSimulatedTimestamp(timestampExactlyOnValidityWindow()) {
            testee.saveUsername(URL, "user")
        }
        testee.getUsernameForBackFilling(URL)
        assertFalse(testee.wasBackFilledRecently(URL, "user"))
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

    @SuppressLint("DenyListedApi")
    private fun configureAutofillFeatureState(enabled: Boolean) {
        feature.partialFormSaves().setRawStoredState(Toggle.State(enable = enabled))
    }

    companion object {
        private const val URL = "example.com"
        private const val CURRENT_TIME_MS = 1733935378547L // 2024-12-11T16:43
    }
}
