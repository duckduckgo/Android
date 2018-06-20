package com.duckduckgo.app.feedback.ui

import android.arch.core.executor.testing.InstantTaskExecutorRule
import com.duckduckgo.app.InstantSchedulersRule
import com.duckduckgo.app.feedback.api.FeedbackSender
import com.nhaarman.mockito_kotlin.*
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class FeedbackViewModelTest {

    @get:Rule
    @Suppress("unused")
    var instantTaskExecutorRule = InstantTaskExecutorRule()

    @get:Rule
    @Suppress("unused")
    val schedulers = InstantSchedulersRule()

    private var mockFedbackSender: FeedbackSender = mock()

    private lateinit var testee: FeedbackViewModel

    private val viewState: FeedbackViewModel.ViewState
        get() = testee.viewState.value!!

    @Before
    fun before() {
        testee = FeedbackViewModel(mockFedbackSender)
    }

    @Test
    fun whenInitializedThenCannotSubmit() {
        assertFalse(viewState.submitAllowed)
    }

    @Test
    fun whenBrokenUrlOnWithUrlThenCanSubmit() {
        testee.onBrokenSiteChanged(true)
        testee.onBrokenSiteUrlChanged("http://example.com")
        assertTrue(viewState.submitAllowed)
    }

    @Test
    fun whenBrokenUrlOnWithNullUrlThenCannotSubmit() {
        testee.onBrokenSiteChanged(true)
        testee.onBrokenSiteUrlChanged(null)
        assertFalse(viewState.submitAllowed)
    }

    @Test
    fun whenBrokenUrlOnWithBlankUrlThenCannotSubmit() {
        testee.onBrokenSiteChanged(true)
        testee.onBrokenSiteUrlChanged(" ")
        assertFalse(viewState.submitAllowed)
    }

    @Test
    fun whenBrokenUrlOffWithMessageThenCanSubmit() {
        testee.onBrokenSiteChanged(false)
        testee.onFeedbackMessageChanged("Feedback message")
        assertTrue(viewState.submitAllowed)
    }

    @Test
    fun whenBrokenUrlOffWithNullMessageThenCannotSubmit() {
        testee.onBrokenSiteChanged(false)
        testee.onFeedbackMessageChanged(null)
        assertFalse(viewState.submitAllowed)
    }

    @Test
    fun whenBrokenUrlOffWithBlankMessageThenCannotSubmit() {
        testee.onBrokenSiteChanged(false)
        testee.onFeedbackMessageChanged(" ")
        assertFalse(viewState.submitAllowed)
    }

    @Test
    fun whenCanSubmitBrokenUrlAndSubmitPressedThenFeedbackSubmitted() {
        val url = "http://example.com"
        testee.onBrokenSiteChanged(true)
        testee.onBrokenSiteUrlChanged(url)
        testee.onSubmitPressed()

        verify(mockFedbackSender).submitBrokenSiteFeedback(null, url)
    }

    @Test
    fun whenCannotSubmitBrokenUrlAndSubmitPressedThenFeedbackNotSubmitted() {
        testee.onBrokenSiteChanged(true)
        testee.onSubmitPressed()
        verify(mockFedbackSender, never()).submitBrokenSiteFeedback(any(), any())
    }

    @Test
    fun whenCanSubmitMessageAndSubmitPressedThenFeedbackSubmitted() {
        val message = "Message"
        testee.onBrokenSiteChanged(false)
        testee.onFeedbackMessageChanged(message)
        testee.onSubmitPressed()
        verify(mockFedbackSender).submitGeneralFeedback(message)
    }

    @Test
    fun whenCannotSubmitMessageAndSubmitPressedThenFeedbackNotSubmitted() {
        testee.onBrokenSiteChanged(false)
        testee.onSubmitPressed()
        verify(mockFedbackSender, never()).submitGeneralFeedback(any())
    }

}