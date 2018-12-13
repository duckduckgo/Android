package com.duckduckgo.app.feedback.ui

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.lifecycle.Observer
import com.duckduckgo.app.InstantSchedulersRule
import com.duckduckgo.app.feedback.api.FeedbackSender
import com.duckduckgo.app.feedback.ui.FeedbackViewModel.Command
import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.never
import com.nhaarman.mockitokotlin2.verify
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
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

    @After
    fun after() {
        testee.command.removeObserver(mockCommandObserver)
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
        testee.onBrokenSiteUrlChanged(url)
        testee.onBrokenSiteChanged(true)
        verify(mockCommandObserver).onChanged(Command.FocusMessage)
    }

    @Test
    fun whenBrokenUrlOnWithUrlAndMessageThenCanSubmit() {
        testee.onBrokenSiteChanged(true)
        testee.onBrokenSiteUrlChanged(url)
        testee.onFeedbackMessageChanged(message)
        assertTrue(viewState.submitAllowed)
    }

    @Test
    fun whenBrokenUrlOnWithNullUrlThenCannotSubmit() {
        testee.onBrokenSiteChanged(true)
        testee.onBrokenSiteUrlChanged(null)
        testee.onFeedbackMessageChanged(message)
        assertFalse(viewState.submitAllowed)
    }

    @Test
    fun whenBrokenUrlOnWithNullMessageThenCannotSubmit() {
        testee.onBrokenSiteChanged(true)
        testee.onBrokenSiteUrlChanged(url)
        testee.onFeedbackMessageChanged(null)
        assertFalse(viewState.submitAllowed)
    }

    @Test
    fun whenBrokenUrlOnWithBlankUrlThenCannotSubmit() {
        testee.onBrokenSiteChanged(true)
        testee.onBrokenSiteUrlChanged(" ")
        testee.onFeedbackMessageChanged(message)
        assertFalse(viewState.submitAllowed)
    }

    @Test
    fun whenBrokenUrlOnWithBlankMessageThenCannotSubmit() {
        testee.onBrokenSiteChanged(true)
        testee.onBrokenSiteUrlChanged(url)
        testee.onFeedbackMessageChanged(" ")
        assertFalse(viewState.submitAllowed)
    }

    @Test
    fun whenBrokenUrlOffWithMessageThenCanSubmit() {
        testee.onBrokenSiteChanged(false)
        testee.onFeedbackMessageChanged(message)
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
    fun whenCanSubmitBrokenSiteAndSubmitPressedThenFeedbackSubmitted() {
        testee.onBrokenSiteChanged(true)
        testee.onBrokenSiteUrlChanged(url)
        testee.onFeedbackMessageChanged(message)
        testee.onSubmitPressed()

        verify(mockFeedbackSender).submitBrokenSiteFeedback(message, url)
        verify(mockCommandObserver).onChanged(Command.ConfirmAndFinish)
    }

    @Test
    fun whenCannotSubmitBrokenSiteAndSubmitPressedThenFeedbackNotSubmitted() {
        testee.onBrokenSiteChanged(true)
        testee.onSubmitPressed()
        verify(mockFeedbackSender, never()).submitBrokenSiteFeedback(any(), any())
        verify(mockCommandObserver, never()).onChanged(Command.ConfirmAndFinish)
    }

    @Test
    fun whenCanSubmitMessageAndSubmitPressedThenFeedbackSubmitted() {
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

    companion object Constants {
        private const val url = "http://example.com"
        private const val message = "Feedback message"
    }

}