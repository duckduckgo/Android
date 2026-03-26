package com.duckduckgo.autofill.impl.reporting

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.duckduckgo.autofill.impl.encoding.UrlUnicodeNormalizerImpl
import com.duckduckgo.autofill.impl.reporting.remoteconfig.AutofillSiteBreakageReportingFeature
import com.duckduckgo.autofill.impl.reporting.remoteconfig.AutofillSiteBreakageReportingFeatureRepository
import com.duckduckgo.autofill.impl.time.TimeProvider
import com.duckduckgo.autofill.impl.urlmatcher.AutofillDomainNameUrlMatcher
import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.feature.toggles.api.FakeFeatureToggleFactory
import com.duckduckgo.feature.toggles.api.Toggle.State
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.util.concurrent.TimeUnit.DAYS

@RunWith(AndroidJUnit4::class)
class AutofillBreakageReportCanShowRulesImplTest {

    @get:Rule
    val coroutineTestRule: CoroutineTestRule = CoroutineTestRule()

    private val urlMatcher = AutofillDomainNameUrlMatcher(UrlUnicodeNormalizerImpl())
    private val dataStore: AutofillSiteBreakageReportingDataStore = mock()
    private val remoteFeature = FakeFeatureToggleFactory.create(AutofillSiteBreakageReportingFeature::class.java)
    private val exceptionsRepository: AutofillSiteBreakageReportingFeatureRepository = mock()
    private val timeProvider: TimeProvider = mock()

    private val testee = AutofillBreakageReportCanShowRulesImpl(
        reportBreakageFeature = remoteFeature,
        dispatchers = coroutineTestRule.testDispatcherProvider,
        urlMatcher = urlMatcher,
        dataStore = dataStore,
        exceptionsRepository = exceptionsRepository,
        timeProvider = timeProvider,
    )

    @Before
    fun setup() = runTest {
        remoteFeature.self().setRawStoredState(State(enable = true))
        whenever(exceptionsRepository.exceptions).thenReturn(emptyList())
        whenever(dataStore.getMinimumNumberOfDaysBeforeReportPromptReshown()).thenReturn(10)
        whenever(timeProvider.currentTimeMillis()).thenReturn(System.currentTimeMillis())
    }

    @Test
    fun whenFeatureIsDisabledThenCannotShowPrompt() = runTest {
        remoteFeature.self().setRawStoredState(State(enable = false))
        assertFalse(testee.canShowForSite(aSite()))
    }

    @Test
    fun whenETldPlusOneNotExtractableThenCannotShowPrompt() = runTest {
        assertFalse(testee.canShowForSite(""))
    }

    @Test
    fun whenSiteInExceptionsListThenCannotShowPrompt() = runTest {
        whenever(exceptionsRepository.exceptions).thenReturn(listOf("example.com"))
        assertFalse(testee.canShowForSite("example.com"))
    }

    @Test
    fun whenSiteWasRecentlySubmittedAlreadyThenCannotShowPrompt() = runTest {
        whenever(dataStore.getTimestampLastFeedbackSent(any())).thenReturn(10L)
        whenever(timeProvider.currentTimeMillis()).thenReturn(20L)
        assertFalse(testee.canShowForSite("example.com"))
    }

    @Test
    fun whenSiteWasSubmittedAtThresholdThenCannotShowPrompt() = runTest {
        whenever(dataStore.getTimestampLastFeedbackSent(any())).thenReturn(0L)
        whenever(dataStore.getMinimumNumberOfDaysBeforeReportPromptReshown()).thenReturn(42)
        whenever(timeProvider.currentTimeMillis()).thenReturn(DAYS.toMillis(42))
        assertFalse(testee.canShowForSite("example.com"))
    }

    @Test
    fun whenSiteWasSubmittedAtOneMsBeyondThresholdThenCanShowPrompt() = runTest {
        whenever(dataStore.getTimestampLastFeedbackSent(any())).thenReturn(0L)
        whenever(dataStore.getMinimumNumberOfDaysBeforeReportPromptReshown()).thenReturn(42)
        whenever(timeProvider.currentTimeMillis()).thenReturn(DAYS.toMillis(42) + 1)
        assertTrue(testee.canShowForSite("example.com"))
    }

    @Test
    fun whenAnotherSiteWasRecentlySubmittedThenCanShowPrompt() = runTest {
        whenever(dataStore.getTimestampLastFeedbackSent("foo.com")).thenReturn(10L)
        whenever(timeProvider.currentTimeMillis()).thenReturn(20L)
        assertTrue(testee.canShowForSite("example.com"))
    }

    @Test
    fun whenSiteWasSubmittedBeforeButNotRecentlyThenCanShowPrompt() = runTest {
        whenever(dataStore.getTimestampLastFeedbackSent(any())).thenReturn(0L)
        assertTrue(testee.canShowForSite("example.com"))
    }

    @Test
    fun whenNoGoodReasonNotToThenCanShowPrompt() = runTest {
        assertTrue(testee.canShowForSite("example.com"))
    }

    private fun aSite(): String = "example.com"
}
