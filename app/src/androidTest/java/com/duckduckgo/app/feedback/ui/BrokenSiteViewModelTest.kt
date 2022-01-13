package com.duckduckgo.app.feedback.ui

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.lifecycle.Observer
import com.duckduckgo.app.InstantSchedulersRule
import com.duckduckgo.app.brokensite.BrokenSiteViewModel
import com.duckduckgo.app.brokensite.BrokenSiteViewModel.Command
import com.duckduckgo.app.brokensite.api.BrokenSiteSender
import com.duckduckgo.app.brokensite.model.BrokenSite
import com.duckduckgo.app.pixels.AppPixelName
import com.duckduckgo.app.statistics.pixels.Pixel
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.Mockito.never
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
        MockitoAnnotations.openMocks(this)
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
        selectAndAcceptCategory()
        assertTrue(viewState.submitAllowed)
    }

    @Test
    fun whenCategoryChangedButNotSelectedThenCannotSubmit() {
        testee.onCategoryIndexChanged(0)
        assertFalse(viewState.submitAllowed)
    }

    @Test
    fun whenNoCategorySelectedThenCannotSubmit() {
        selectAndAcceptCategory(-1)
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
    fun whenCategoryAcceptedAndIncorrectIndexThenReturnNullCategory() {
        selectAndAcceptCategory(-1)
        assertNull(viewState.categorySelected)
    }

    @Test
    fun whenCategoryAcceptedAndCorrectIndexThenReturnCategory() {
        val indexSelected = 0
        selectAndAcceptCategory(indexSelected)

        val categoryExpected = testee.categories[indexSelected]
        assertEquals(categoryExpected, viewState.categorySelected)
    }

    @Test
    fun whenCanSubmitBrokenSiteAndUrlNotNullAndSubmitPressedThenReportAndPixelSubmitted() {
        testee.setInitialBrokenSite(url, "", "", false)
        selectAndAcceptCategory()
        testee.onSubmitPressed("webViewVersion")

        val brokenSiteExpected = BrokenSite(
            category = testee.categories[0].key,
            siteUrl = url,
            upgradeHttps = false,
            blockedTrackers = "",
            surrogates = "",
            webViewVersion = "webViewVersion",
            siteType = BrokenSiteViewModel.DESKTOP_SITE
        )

        verify(mockPixel).fire(AppPixelName.BROKEN_SITE_REPORTED, mapOf("url" to url))
        verify(mockBrokenSiteSender).submitBrokenSiteFeedback(brokenSiteExpected)
        verify(mockCommandObserver).onChanged(Command.ConfirmAndFinish)
    }

    @Test
    fun whenCanSubmitBrokenSiteAndUrlIsEmptyAndSubmitPressedThenDoNotSubmit() {
        val nullUrl = ""
        testee.setInitialBrokenSite(nullUrl, "", "", false)
        selectAndAcceptCategory()
        testee.onSubmitPressed("webViewVersion")

        val brokenSiteExpected = BrokenSite(
            category = testee.categories[0].key,
            siteUrl = nullUrl,
            upgradeHttps = false,
            blockedTrackers = "",
            surrogates = "",
            webViewVersion = "webViewVersion",
            siteType = BrokenSiteViewModel.DESKTOP_SITE
        )

        verify(mockPixel, never()).fire(AppPixelName.BROKEN_SITE_REPORTED, mapOf("url" to nullUrl))
        verify(mockBrokenSiteSender, never()).submitBrokenSiteFeedback(brokenSiteExpected)
        verify(mockCommandObserver).onChanged(Command.ConfirmAndFinish)
    }

    @Test
    fun whenUrlIsDesktopThenSendDesktopParameter() {
        testee.setInitialBrokenSite(url, "", "", false)
        selectAndAcceptCategory()

        val brokenSiteExpected = testee.getBrokenSite("")
        assertEquals(BrokenSiteViewModel.DESKTOP_SITE, brokenSiteExpected.siteType)
    }

    @Test
    fun whenUrlIsMobileThenSendMobileParameter() {
        val url = "http://m.example.com"
        testee.setInitialBrokenSite(url, "", "", false)
        selectAndAcceptCategory()

        val brokenSiteExpected = testee.getBrokenSite("")
        assertEquals(BrokenSiteViewModel.MOBILE_SITE, brokenSiteExpected.siteType)
    }

    @Test
    fun whenGetBrokenSiteThenReturnCorrectCategory() {
        val url = "http://m.example.com"
        val categoryIndex = 0
        testee.setInitialBrokenSite(url, "", "", false)
        selectAndAcceptCategory(categoryIndex)

        val categoryExpected = testee.categories[categoryIndex].key
        val brokenSiteExpected = testee.getBrokenSite("")
        assertEquals(categoryExpected, brokenSiteExpected.category)
    }

    @Test
    fun whenCancelSelectionThenAssignOldIndexValue() {
        testee.setInitialBrokenSite("", "", "", false)
        selectAndAcceptCategory(0)
        testee.onCategoryIndexChanged(1)
        testee.onCategorySelectionCancelled()

        assertEquals(0, testee.indexSelected)
    }

    @Test
    fun whenCancelSelectionAndNoPreviousValueThenAssignMinusOne() {
        testee.setInitialBrokenSite("", "", "", false)
        testee.onCategoryIndexChanged(1)
        testee.onCategorySelectionCancelled()

        assertEquals(-1, testee.indexSelected)
    }

    private fun selectAndAcceptCategory(indexSelected: Int = 0) {
        testee.onCategoryIndexChanged(indexSelected)
        testee.onCategoryAccepted()
    }

    companion object Constants {
        private const val url = "http://example.com"
    }
}
