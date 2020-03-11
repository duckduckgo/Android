package com.duckduckgo.app.feedback.ui

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.lifecycle.Observer
import com.duckduckgo.app.InstantSchedulersRule
import com.duckduckgo.app.brokensite.BrokenSiteViewModel
import com.duckduckgo.app.brokensite.BrokenSiteViewModel.Command
import com.duckduckgo.app.brokensite.api.BrokenSiteSender
import com.duckduckgo.app.brokensite.model.BrokenSite
import com.duckduckgo.app.statistics.pixels.Pixel
import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.never
import com.nhaarman.mockitokotlin2.verify
import org.junit.After
import org.junit.Assert.assertEquals
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

    private val mockCommandObserver: Observer<Command> = mock()

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
    fun whenCategorySelectedThenCanSubmit() {
        testee.onCategoryIndexChanged(0)
        testee.onCategoryAccepted()
        assertTrue(viewState.submitAllowed)
    }

    @Test
    fun whenCategoryChangedButNotSelectedThenCannotSubmit() {
        testee.onCategoryIndexChanged(0)
        assertFalse(viewState.submitAllowed)
    }

    @Test
    fun whenNoCategorySelectedThenCannotSubmit() {
        testee.onCategoryIndexChanged(-1)
        testee.onCategoryAccepted()
        assertFalse(viewState.submitAllowed)
    }

    @Test
    fun whenCategorySelectedButNotChangedThenReturnOldCategory() {
        testee.onCategoryIndexChanged(0)
        testee.onCategoryAccepted()
        testee.onCategoryIndexChanged(1)
        assertEquals(0, viewState.indexSelected)
    }

    @Test
    fun whenCanSubmitBrokenSiteAndUrlNotNullAndSubmitPressedThenReportAndPixelSubmitted() {
        testee.setInitialBrokenSite(url, null, false)
        testee.onCategoryIndexChanged(0)
        testee.onSubmitPressed("webViewVersion")

        val brokenSiteExpected = BrokenSite(
            category = testee.categories[0].key,
            siteUrl = url,
            upgradeHttps = false,
            blockedTrackers = "",
            webViewVersion = "webViewVersion",
            siteType = BrokenSiteViewModel.DESKTOP
        )

        verify(mockPixel).fire(Pixel.PixelName.BROKEN_SITE_REPORTED, mapOf("url" to url))
        verify(mockBrokenSiteSender).submitBrokenSiteFeedback(brokenSiteExpected)
        verify(mockCommandObserver).onChanged(Command.ConfirmAndFinish)
    }

    @Test
    fun whenCanSubmitBrokenSiteAndUrlNullAndSubmitPressedThenReportAndPixelNotSubmitted() {
        testee.setInitialBrokenSite(null, null, false)
        testee.onCategoryIndexChanged(0)
        testee.onSubmitPressed("webViewVersion")

        verify(mockBrokenSiteSender, never()).submitBrokenSiteFeedback(any())
        verify(mockPixel, never()).fire(Pixel.PixelName.BROKEN_SITE_REPORTED, mapOf("url" to null))
        verify(mockCommandObserver).onChanged(Command.ConfirmAndFinish)
    }

    companion object Constants {
        private const val url = "http://example.com"
        private const val message = "Feedback message"
    }

}