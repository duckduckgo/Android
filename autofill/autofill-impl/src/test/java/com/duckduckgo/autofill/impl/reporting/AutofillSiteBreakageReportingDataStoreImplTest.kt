package com.duckduckgo.autofill.impl.reporting

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.duckduckgo.autofill.impl.time.TimeProvider
import com.duckduckgo.common.test.CoroutineTestRule
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

@RunWith(AndroidJUnit4::class)
class AutofillSiteBreakageReportingDataStoreImplTest {

    @get:Rule
    val coroutineTestRule: CoroutineTestRule = CoroutineTestRule()

    private val temporaryFolder = TemporaryFolder.builder().assureDeletion().build().also { it.create() }

    private val testDataStore: DataStore<Preferences> =
        PreferenceDataStoreFactory.create(
            scope = coroutineTestRule.testScope,
            produceFile = { temporaryFolder.newFile("temp.preferences_pb") },
        )

    private val fakeTimeProvider: TimeProvider = mock()

    private val testee = AutofillSiteBreakageReportingDataStoreImpl(
        store = testDataStore,
        timeProvider = fakeTimeProvider,
    )

    @Test
    fun whenNotUpdatedThenDefaultNumberOfDaysIsCorrect() = runTest {
        assertEquals(42, testee.getMinimumNumberOfDaysBeforeReportPromptReshown())
    }

    @Test
    fun whenUpdatedThenNumberOfDaysMatchesWhatWasSet() = runTest {
        testee.updateMinimumNumberOfDaysBeforeReportPromptReshown(10)
        assertEquals(10, testee.getMinimumNumberOfDaysBeforeReportPromptReshown())
    }

    @Test
    fun whenSiteNeverRecordedBeforeThenNoTimestampReturned() = runTest {
        assertNull(testee.getTimestampLastFeedbackSent("example.com"))
    }

    @Test
    fun whenSiteRecordedBeforeThenTimestampReturned() = runTest {
        testee.recordFeedbackSent("example.com")
        assertNotNull(testee.getTimestampLastFeedbackSent("example.com"))
    }

    @Test
    fun whenMultipleSitesRecordedThenCorrectTimestampReturned() = runTest {
        0L.setAsCurrentTime()
        testee.recordFeedbackSent("example.com")

        1L.setAsCurrentTime()
        testee.recordFeedbackSent("example.net")

        2L.setAsCurrentTime()
        testee.recordFeedbackSent("example.org")

        assertEquals(1L, testee.getTimestampLastFeedbackSent("example.net"))
    }

    @Test
    fun whenSiteRecordedAgainThenLastTimestampReturned() = runTest {
        val site = "example.com"

        0L.setAsCurrentTime()
        testee.recordFeedbackSent(site)

        1L.setAsCurrentTime()
        testee.recordFeedbackSent(site)

        assertEquals(1L, testee.getTimestampLastFeedbackSent(site))
    }

    private fun Long.setAsCurrentTime() {
        whenever(fakeTimeProvider.currentTimeMillis()).thenReturn(this)
    }
}
