package com.duckduckgo.autofill.impl.ui.credential.management.survey

import com.duckduckgo.app.statistics.pixels.Pixel
import com.duckduckgo.autofill.impl.pixel.AutofillPixelNames
import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.common.utils.DispatcherProvider
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify

class SurveyInPasswordsPromotionViewModelTest {

    @get:Rule
    val coroutineTestRule: CoroutineTestRule = CoroutineTestRule()

    private val autofillSurvey: AutofillSurvey = mock()
    private val pixel: Pixel = mock()
    private val dispatchers: DispatcherProvider = coroutineTestRule.testDispatcherProvider

    private val testee = SurveyInPasswordsPromotionViewModel(autofillSurvey, pixel, dispatchers)

    @Test
    fun whenPromoShownThenCorrectPixelFired() = runTest {
        testee.onPromoShown()
        verify(pixel).fire(AutofillPixelNames.AUTOFILL_SURVEY_AVAILABLE_PROMPT_DISPLAYED)
    }

    @Test
    fun whenSurveyLaunchedThenSurveyMarkedAsUsed() = runTest {
        testee.onUserChoseToOpenSurvey("surveyId-1".asSurvey())
        verify(autofillSurvey).recordSurveyAsUsed("surveyId-1")
    }

    @Test
    fun whenSurveyPromptDismissedThenSurveyMarkedAsUsed() = runTest {
        testee.onSurveyPromptDismissed("surveyId-1")
        verify(autofillSurvey).recordSurveyAsUsed("surveyId-1")
    }

    private fun String.asSurvey() = SurveyDetails(this, "https://example.com/survey")
}
