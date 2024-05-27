package com.duckduckgo.autofill.impl.ui.credential.management.survey

import androidx.core.net.toUri
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.duckduckgo.appbuildconfig.api.AppBuildConfig
import com.duckduckgo.autofill.impl.store.InternalAutofillStore
import com.duckduckgo.common.test.CoroutineTestRule
import java.util.*
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

@RunWith(AndroidJUnit4::class)
class AutofillSurveyImplTest {

    @get:Rule
    val coroutineTestRule: CoroutineTestRule = CoroutineTestRule()

    private val autofillSurveyStore: AutofillSurveyStore = mock()
    private val appBuildConfig: AppBuildConfig = mock()
    private val autofillStore: InternalAutofillStore = mock()
    private val testee: AutofillSurveyImpl = AutofillSurveyImpl(
        statisticsStore = mock(),
        userBrowserProperties = mock(),
        appBuildConfig = appBuildConfig,
        appDaysUsedRepository = mock(),
        dispatchers = coroutineTestRule.testDispatcherProvider,
        autofillSurveyStore = autofillSurveyStore,
        internalAutofillStore = autofillStore,
    )

    @Before
    fun setup() {
        whenever(appBuildConfig.deviceLocale).thenReturn(Locale("en"))

        coroutineTestRule.testScope.runTest {
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
    fun whenSavedPasswordsLowestInNoneBucketThenCorrectQueryParamValueAdded() = runTest {
        configureCredentialCount(0)
        val survey = getAvailableSurvey()
        val savedPasswordsBucket = survey.url.toUri().getQueryParameter("saved_passwords")
        assertEquals("none", savedPasswordsBucket)
    }

    @Test
    fun whenSavedPasswordsHighestInNoneBucketThenCorrectQueryParamValueAdded() = runTest {
        configureCredentialCount(2)
        val survey = getAvailableSurvey()
        val savedPasswordsBucket = survey.url.toUri().getQueryParameter("saved_passwords")
        assertEquals("none", savedPasswordsBucket)
    }

    @Test
    fun whenSavedPasswordsLowestInSomeBucketThenCorrectQueryParamValueAdded() = runTest {
        configureCredentialCount(3)
        val survey = getAvailableSurvey()
        val savedPasswordsBucket = survey.url.toUri().getQueryParameter("saved_passwords")
        assertEquals("some", savedPasswordsBucket)
    }

    @Test
    fun whenSavedPasswordsHighestInSomeBucketThenCorrectQueryParamValueAdded() = runTest {
        configureCredentialCount(9)
        val survey = getAvailableSurvey()
        val savedPasswordsBucket = survey.url.toUri().getQueryParameter("saved_passwords")
        assertEquals("some", savedPasswordsBucket)
    }

    @Test
    fun whenSavedPasswordsLowestInManyBucketThenCorrectQueryParamValueAdded() = runTest {
        configureCredentialCount(10)
        val survey = getAvailableSurvey()
        val savedPasswordsBucket = survey.url.toUri().getQueryParameter("saved_passwords")
        assertEquals("many", savedPasswordsBucket)
    }

    @Test
    fun whenSavedPasswordsHighestInManyBucketThenCorrectQueryParamValueAdded() = runTest {
        configureCredentialCount(49)
        val survey = getAvailableSurvey()
        val savedPasswordsBucket = survey.url.toUri().getQueryParameter("saved_passwords")
        assertEquals("many", savedPasswordsBucket)
    }

    @Test
    fun whenSavedPasswordsLowestInLotsBucketThenCorrectQueryParamValueAdded() = runTest {
        configureCredentialCount(50)
        val survey = getAvailableSurvey()
        val savedPasswordsBucket = survey.url.toUri().getQueryParameter("saved_passwords")
        assertEquals("lots", savedPasswordsBucket)
    }

    @Test
    fun whenSavedPasswordsIsExtremelyLargeThenCorrectQueryParamValueAdded() = runTest {
        configureCredentialCount(Int.MAX_VALUE)
        val survey = getAvailableSurvey()
        val savedPasswordsBucket = survey.url.toUri().getQueryParameter("saved_passwords")
        assertEquals("lots", savedPasswordsBucket)
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

    /**
     *  passwordsSaved == null -> NUMBER_PASSWORD_BUCKET_NONE
     *             passwordsSaved < 3 -> NUMBER_PASSWORD_BUCKET_NONE
     *             passwordsSaved < 10 -> NUMBER_PASSWORD_BUCKET_SOME
     *             passwordsSaved < 50 -> NUMBER_PASSWORD_BUCKET_MANY
     *             else -> NUMBER_PASSWORD_BUCKET_LOTS
     */
}
