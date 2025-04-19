package com.duckduckgo.autofill.impl.ui.credential.management.survey

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.duckduckgo.common.test.CoroutineTestRule
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.mock

@RunWith(AndroidJUnit4::class)
class AutofillSurveyStoreImplTest {

    @get:Rule
    val coroutineTestRule: CoroutineTestRule = CoroutineTestRule()
    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    private val surveyJsonParser: AutofillSurveyJsonParser = mock()

    private val testee = AutofillSurveyStoreImpl(
        context = context,
        dispatchers = coroutineTestRule.testDispatcherProvider,
        surveyJsonParser = surveyJsonParser,
    )

    @Test
    fun whenSurveyIdNeverRecordedBeforeThenReturnsFalse() = runTest {
        assertFalse(testee.hasSurveyBeenTaken("surveyId-1"))
    }

    @Test
    fun whenAnotherSurveyIdWasRecordedButNotThisOneThenReturnsFalse() = runTest {
        testee.recordSurveyWasShown("surveyId-1")
        assertFalse(testee.hasSurveyBeenTaken("surveyId-2"))
    }

    @Test
    fun whenSurveyRecordedBeforeThenReturnsTrue() = runTest {
        testee.recordSurveyWasShown("surveyId-1")
        assertTrue(testee.hasSurveyBeenTaken("surveyId-1"))
    }

    @Test
    fun whenMultipleSurveysRecordedAndQueriedOneInListThenReturnsTrue() = runTest {
        testee.recordSurveyWasShown("surveyId-1")
        testee.recordSurveyWasShown("surveyId-2")
        testee.recordSurveyWasShown("surveyId-3")
        assertTrue(testee.hasSurveyBeenTaken("surveyId-2"))
    }
}
