package com.duckduckgo.autofill.impl.ui.credential.management.survey

import com.duckduckgo.autofill.impl.jsbridge.request.AutofillJsonRequestParserTest
import com.duckduckgo.common.test.FileUtilities
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class AutofillSurveyJsonParserImplTest {

    private val testee = AutofillSurveyJsonParserImpl()

    @Test
    fun whenNoSurveysDefinedInJsonThenNoSurveysParsed() = runTest {
        val result = testee.parseJson("autofillSurvey_no_surveys_available".readFile())
        assertEquals(0, result.size)
    }

    @Test
    fun whenOneSurveyDefinedInJsonThenOneSurveyParsed() = runTest {
        val result = testee.parseJson("autofillSurvey_one_survey_available".readFile())
        assertEquals(1, result.size)
    }

    @Test
    fun whenTwoSurveysDefinedInJsonThenSurveysParsedInOrder() = runTest {
        val result = testee.parseJson("autofillSurvey_two_surveys_available".readFile())
        assertEquals(2, result.size)
        result[0].also {
            assertEquals("survey-id-1", it.id)
            assertEquals("https://example.com?q=1", it.url)
        }
        result[1].also {
            assertEquals("survey-id-2", it.id)
            assertEquals("https://example.com?q=2", it.url)
        }
    }

    @Test
    fun whenSurveyJsonIsCorruptThenReturnsEmptyList() = runTest {
        val result = testee.parseJson("autofillSurvey_survey_is_corrupt".readFile())
        assertEquals(0, result.size)
    }

    @Test
    fun whenSurveyBlockIsMissingThenReturnsEmptyList() = runTest {
        val result = testee.parseJson("autofillSurvey_missing_surveys_field".readFile())
        assertEquals(0, result.size)
    }

    private fun String.readFile(): String {
        return FileUtilities.loadText(
            AutofillJsonRequestParserTest::class.java.classLoader!!,
            "json/survey/$this.json",
        )
    }
}
