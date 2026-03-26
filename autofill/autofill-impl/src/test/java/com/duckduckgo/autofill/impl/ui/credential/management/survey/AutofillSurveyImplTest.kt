package com.duckduckgo.autofill.impl.ui.credential.management.survey

import androidx.core.net.toUri
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.duckduckgo.appbuildconfig.api.AppBuildConfig
import com.duckduckgo.autofill.impl.engagement.store.AutofillEngagementBucketing
import com.duckduckgo.autofill.impl.store.InternalAutofillStore
import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.feature.toggles.api.FakeFeatureToggleFactory
import com.duckduckgo.feature.toggles.api.Toggle.State
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.util.*

@RunWith(AndroidJUnit4::class)
class AutofillSurveyImplTest {

    @get:Rule
    val coroutineTestRule: CoroutineTestRule = CoroutineTestRule()

    private val autofillSurveyStore: AutofillSurveyStore = mock()
    private val appBuildConfig: AppBuildConfig = mock()
    private val autofillStore: InternalAutofillStore = mock()
    private val surveysFeature = FakeFeatureToggleFactory.create(AutofillSurveysFeature::class.java)
    private val passwordBucketing: AutofillEngagementBucketing = mock()
    private val testee: AutofillSurveyImpl = AutofillSurveyImpl(
        statisticsStore = mock(),
        userBrowserProperties = mock(),
        appBuildConfig = appBuildConfig,
        appDaysUsedRepository = mock(),
        dispatchers = coroutineTestRule.testDispatcherProvider,
        autofillSurveyStore = autofillSurveyStore,
        internalAutofillStore = autofillStore,
        surveysFeature = surveysFeature,
        passwordBucketing = passwordBucketing,
    )

    @Before
    fun setup() {
        whenever(appBuildConfig.deviceLocale).thenReturn(Locale("en"))

        coroutineTestRule.testScope.runTest {
            surveysFeature.self().setRawStoredState(State(enable = true))
            whenever(autofillSurveyStore.availableSurveys()).thenReturn(
                listOf(
                    SurveyDetails("autofill-2024-04-26", "https://example.com/survey"),
                ),
            )
            configureCredentialCount(0)
        }
    }

    @Test
    fun whenNoSurveyAvailableThenFirstUnusedSurveyReturnsNull() = runTest {
        whenever(autofillSurveyStore.availableSurveys()).thenReturn(emptyList())
        assertNull(testee.firstUnusedSurvey())
    }

    @Test
    fun whenSurveyHasNotBeenShownBeforeThenFirstUnusedSurveyReturnsIt() = runTest {
        whenever(autofillSurveyStore.hasSurveyBeenTaken("autofill-2024-04-26")).thenReturn(false)
        val survey = testee.firstUnusedSurvey()
        assertEquals("autofill-2024-04-26", survey!!.id)
    }

    @Test
    fun whenSurveyHasNotBeenShownBeforeButLocaleNotEnglishThenFirstUnusedSurveyDoesNotReturnIt() = runTest {
        whenever(appBuildConfig.deviceLocale).thenReturn(Locale("fr"))
        whenever(autofillSurveyStore.hasSurveyBeenTaken("autofill-2024-04-26")).thenReturn(false)
        assertNull(testee.firstUnusedSurvey())
    }

    @Test
    fun whenSurveyHasBeenShownBeforeThenFirstUnusedSurveyDoesNotReturnIt() = runTest {
        whenever(autofillSurveyStore.hasSurveyBeenTaken("autofill-2024-04-26")).thenReturn(true)
        assertNull(testee.firstUnusedSurvey())
    }

    @Test
    fun whenSurveyRecordedAsUsedThenPersisted() = runTest {
        testee.recordSurveyAsUsed("surveyId-1")
        verify(autofillSurveyStore).recordSurveyWasShown("surveyId-1")
    }

    @Test
    fun whenSurveyLaunchedThenSavedPasswordQueryParamAdded() = runTest {
        whenever(passwordBucketing.bucketNumberOfCredentials(any())).thenReturn("fromBucketing")
        val survey = getAvailableSurvey()
        val savedPasswordsBucket = survey.url.toUri().getQueryParameter("saved_passwords")
        assertEquals("fromBucketing", savedPasswordsBucket)
    }

    private suspend fun getAvailableSurvey(): SurveyDetails {
        whenever(autofillSurveyStore.hasSurveyBeenTaken("autofill-2024-04-26")).thenReturn(false)
        return testee.firstUnusedSurvey()!!
    }

    private suspend fun configureCredentialCount(count: Int?) {
        if (count == null) {
            whenever(autofillStore.getCredentialCount()).thenReturn(null)
        } else {
            whenever(autofillStore.getCredentialCount()).thenReturn(flowOf(count))
        }
    }
}
