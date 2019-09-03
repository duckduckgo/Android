package com.duckduckgo.app.feedback.ui

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.lifecycle.Observer
import com.duckduckgo.app.InstantSchedulersRule
import com.duckduckgo.app.brokensite.BrokenSiteViewModel
import com.duckduckgo.app.brokensite.BrokenSiteViewModel.Command
import com.duckduckgo.app.brokensite.api.BrokenSiteSender
import com.duckduckgo.app.statistics.pixels.Pixel
import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.never
import com.nhaarman.mockitokotlin2.verify
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.MockitoAnnotations

class BrokenSiteViewModelTest {

    @get:Rule
    @Suppress("unused")
    var instantTaskExecutorRule = InstantTaskExecutorRule()

    @get:Rule
    @Suppress("unused")
    val schedulers = InstantSchedulersRule()

    private val mockPixel: Pixel = mock()

    private val mockBrokenSiteSender: BrokenSiteSender = mock()

    private val mockCommandObserver: Observer<BrokenSiteViewModel.Command> = mock()

    private lateinit var testee: BrokenSiteViewModel

    private val viewState: BrokenSiteViewModel.ViewState
        get() = testee.viewState.value!!

    @Before
    fun before() {
        MockitoAnnotations.initMocks(this)
        testee = BrokenSiteViewModel(mockPixel, mockBrokenSiteSender)
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
    fun whenNoUrlProvidedThenUrlFocused() {
        testee.setInitialBrokenSite(null)
        verify(mockCommandObserver).onChanged(Command.FocusUrl)
    }

    @Test
    fun whenUrlProvidedThenMessageFocused() {
        testee.setInitialBrokenSite(url)
        verify(mockCommandObserver).onChanged(Command.FocusMessage)
    }

    @Test
    fun whenUrlAndMessageNotEmptyThenCanSubmit() {
        testee.onBrokenSiteUrlChanged(url)
        testee.onFeedbackMessageChanged(message)
        assertTrue(viewState.submitAllowed)
    }

    @Test
    fun whenNullUrlThenCannotSubmit() {
        testee.onBrokenSiteUrlChanged(null)
        testee.onFeedbackMessageChanged(message)
        assertFalse(viewState.submitAllowed)
    }

    @Test
    fun whenEmptyUrlThenCannotSubmit() {
        testee.onBrokenSiteUrlChanged(" ")
        testee.onFeedbackMessageChanged(message)
        assertFalse(viewState.submitAllowed)
    }

    @Test
    fun whenNullMessageThenCannotSubmit() {
        testee.onBrokenSiteUrlChanged(url)
        testee.onFeedbackMessageChanged(null)
        assertFalse(viewState.submitAllowed)
    }

    @Test
    fun whenEmptyMessageThenCannotSubmit() {
        testee.onBrokenSiteUrlChanged(url)
        testee.onFeedbackMessageChanged(" ")
        assertFalse(viewState.submitAllowed)
    }

    @Test
    fun whenCanSubmitBrokenSiteAndSubmitPressedThenFeedbackAndPixelSubmitted() {
        testee.onBrokenSiteUrlChanged(url)
        testee.onFeedbackMessageChanged(message)
        testee.onSubmitPressed()

        verify(mockPixel).fire(Pixel.PixelName.BROKEN_SITE_REPORTED, mapOf("url" to url))
        verify(mockBrokenSiteSender).submitBrokenSiteFeedback(message, url)
        verify(mockCommandObserver).onChanged(Command.ConfirmAndFinish)
    }

    @Test
    fun whenCannotSubmitBrokenSiteAndSubmitPressedThenFeedbackNotSubmitted() {
        testee.onSubmitPressed()
        verify(mockBrokenSiteSender, never()).submitBrokenSiteFeedback(any(), any())
        verify(mockCommandObserver, never()).onChanged(Command.ConfirmAndFinish)
    }

    companion object Constants {
        private const val url = "http://example.com"
        private const val message = "Feedback message"
    }

}