package com.duckduckgo.app.feedback.ui

import android.arch.core.executor.testing.InstantTaskExecutorRule
import android.arch.lifecycle.Observer
import com.duckduckgo.app.InstantSchedulersRule
import com.duckduckgo.app.feedback.api.FeedbackSender
import com.duckduckgo.app.feedback.ui.FeedbackViewModel.*
import com.nhaarman.mockito_kotlin.*
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.Mock
import org.mockito.MockitoAnnotations

class FeedbackViewModelTest {

    @get:Rule
    @Suppress("unused")
    var instantTaskExecutorRule = InstantTaskExecutorRule()

    @get:Rule
    @Suppress("unused")
    val schedulers = InstantSchedulersRule()

    @Mock
    private lateinit var mockFeedbackSender: FeedbackSender

    @Mock
    private lateinit var mockCommandObserver: Observer<FeedbackViewModel.Command>

    private lateinit var testee: FeedbackViewModel

    private val viewState: FeedbackViewModel.ViewState
        get() = testee.viewState.value!!

    @Before
    fun before() {
        MockitoAnnotations.initMocks(this)
        testee = FeedbackViewModel(mockFeedbackSender)
        testee.command.observeForever(mockCommandObserver)
    }

    @Test
    fun whenInitializedThenCannotSubmit() {
        assertFalse(viewState.submitAllowed)
    }

    @Test
    fun whenBrokenUrlSwitchedOnWithNoUrlThenUrlFocused() {
        testee.onBrokenSiteUrlChanged(null)
        testee.onBrokenSiteChanged(true)
        verify(mockCommandObserver).onChanged(Command.FocusUrl)
    }

    @Test
    fun whenBrokenUrlSwitchedOnWithUrlThenMessageFocused() {
        testee.onBrokenSiteUrlChanged("http://example.com")
        testee.onBrokenSiteChanged(true)
        verify(mockCommandObserver).onChanged(Command.FocusMessage)
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

        verify(mockFeedbackSender).submitBrokenSiteFeedback(null, url)
        verify(mockCommandObserver).onChanged(Command.ConfirmAndFinish)
    }

    @Test
    fun whenCannotSubmitBrokenUrlAndSubmitPressedThenFeedbackNotSubmitted() {
        testee.onBrokenSiteChanged(true)
        testee.onSubmitPressed()
        verify(mockFeedbackSender, never()).submitBrokenSiteFeedback(any(), any())
        verify(mockCommandObserver, never()).onChanged(Command.ConfirmAndFinish)
    }

    @Test
    fun whenCanSubmitMessageAndSubmitPressedThenFeedbackSubmitted() {
        val message = "Message"
        testee.onBrokenSiteChanged(false)
        testee.onFeedbackMessageChanged(message)
        testee.onSubmitPressed()
        verify(mockFeedbackSender).submitGeneralFeedback(message)
        verify(mockCommandObserver).onChanged(Command.ConfirmAndFinish)
    }

    @Test
    fun whenCannotSubmitMessageAndSubmitPressedThenFeedbackNotSubmitted() {
        testee.onBrokenSiteChanged(false)
        testee.onSubmitPressed()
        verify(mockFeedbackSender, never()).submitGeneralFeedback(any())
        verify(mockCommandObserver, never()).onChanged(Command.ConfirmAndFinish)
    }

}