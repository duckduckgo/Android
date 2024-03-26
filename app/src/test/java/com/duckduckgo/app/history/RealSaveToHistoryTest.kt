package com.duckduckgo.app.history

import com.duckduckgo.app.browser.DuckDuckGoUrlDetector
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class RealSaveToHistoryTest {

    private val mockHistoryRepository: HistoryRepository = mock()
    private val mockDuckDuckGoUrlDetector: DuckDuckGoUrlDetector = mock()
    private val testScope = TestScope()

    val testee = RealSaveToHistory(mockHistoryRepository, mockDuckDuckGoUrlDetector, testScope)

    @Test
    fun whenUrlIsSerpThenSaveToHistoryWithQueryAndSerpIsTrue() {
        whenever(mockDuckDuckGoUrlDetector.isDuckDuckGoQueryUrl(any())).thenReturn(true)
        whenever(mockDuckDuckGoUrlDetector.extractQuery(any())).thenReturn("query")

        testScope.launch {
            testee.saveToHistory("url", "title")

            verify(mockHistoryRepository).saveToHistory(eq("url"), eq("title"), eq("query"), eq(true))
        }
    }

    @Test
    fun whenSerpUrlDoesNotHaveQueryThenSaveToHistoryWithQueryAndSerpIsTrue() {
        whenever(mockDuckDuckGoUrlDetector.isDuckDuckGoQueryUrl(any())).thenReturn(true)
        whenever(mockDuckDuckGoUrlDetector.extractQuery(any())).thenReturn(null)

        testScope.launch {
            testee.saveToHistory("url", "title")

            verify(mockHistoryRepository).saveToHistory(eq("url"), eq("title"), eq(null), eq(false))
        }
    }

    @Test
    fun whenNotSerpUrlThenSaveToHistoryWithoutQueryAndSerpIsFalse() {
        whenever(mockDuckDuckGoUrlDetector.isDuckDuckGoQueryUrl(any())).thenReturn(false)

        testScope.launch {
            testee.saveToHistory("url", "title")

            verify(mockHistoryRepository).saveToHistory(eq("url"), eq("title"), eq(null), eq(false))
        }
    }
}
