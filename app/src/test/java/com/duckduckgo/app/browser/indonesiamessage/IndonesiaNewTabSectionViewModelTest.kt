package com.duckduckgo.app.browser.indonesiamessage

import android.content.Context
import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.cash.turbine.test
import com.duckduckgo.app.browser.indonesiamessage.IndonesiaNewTabSectionViewModel.Companion.MAX_DAYS_MESSAGE_SHOWN
import com.duckduckgo.app.browser.indonesiamessage.IndonesiaNewTabSectionViewModel.Companion.MCC_INDONESIA
import com.duckduckgo.app.browser.indonesiamessage.IndonesiaNewTabSectionViewModel.Companion.MCC_UNDEFINED
import com.duckduckgo.app.browser.indonesiamessage.IndonesiaNewTabSectionViewModel.ViewState
import com.duckduckgo.common.test.CoroutineTestRule
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

@RunWith(AndroidJUnit4::class)
class IndonesiaNewTabSectionViewModelTest {
    @get:Rule
    @Suppress("unused")
    var instantTaskExecutorRule = InstantTaskExecutorRule()

    @get:Rule
    var coroutinesTestRule = CoroutineTestRule()

    private val mockIndonesiaNewTabSectionDataStore: IndonesiaNewTabSectionDataStore = mock()
    private val mockApplicationContext: Context = InstrumentationRegistry.getInstrumentation().targetContext

    private lateinit var testee: IndonesiaNewTabSectionViewModel

    @Before
    fun before() {
        testee = IndonesiaNewTabSectionViewModel(
            mockIndonesiaNewTabSectionDataStore,
            coroutinesTestRule.testDispatcherProvider,
            mockApplicationContext,
        )
    }

    @Test
    fun whenOnStartCalledAndMccIsIndonesiaThenUpdateStateWithShowMessageTrue() = runTest {
        mockApplicationContext.resources.configuration.mcc = MCC_INDONESIA
        whenever(mockIndonesiaNewTabSectionDataStore.showMessage).thenReturn(flowOf(true))
        testee.onStart(mock())

        verify(mockIndonesiaNewTabSectionDataStore).updateShowMessage(MAX_DAYS_MESSAGE_SHOWN)

        testee.viewState.test {
            assertEquals(ViewState(true), awaitItem())
            cancelAndConsumeRemainingEvents()
        }
    }

    @Test
    fun whenOnStartCalledAndMccIsUndefinedThenUpdateStateWithShowMessageFalse() = runTest {
        mockApplicationContext.resources.configuration.mcc = MCC_UNDEFINED
        whenever(mockIndonesiaNewTabSectionDataStore.showMessage).thenReturn(flowOf(true))
        testee.onStart(mock())

        verify(mockIndonesiaNewTabSectionDataStore).updateShowMessage(MAX_DAYS_MESSAGE_SHOWN)

        testee.viewState.test {
            assertEquals(ViewState(false), awaitItem())
            cancelAndConsumeRemainingEvents()
        }
    }

    @Test
    fun whenOnMessageDismissedThenDismissMessageCalledOnDataStore() = runTest {
        testee.onMessageDismissed()

        verify(mockIndonesiaNewTabSectionDataStore).dismissMessage()
    }
}
