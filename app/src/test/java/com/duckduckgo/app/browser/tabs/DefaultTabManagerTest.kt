package com.duckduckgo.app.browser.tabs

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.duckduckgo.app.browser.SkipUrlConversionOnNewTabFeature
import com.duckduckgo.app.browser.omnibar.OmnibarEntryConverter
import com.duckduckgo.app.tabs.model.TabEntity
import com.duckduckgo.app.tabs.model.TabRepository
import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.feature.toggles.api.FakeFeatureToggleFactory
import com.duckduckgo.feature.toggles.api.Toggle.State
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

@RunWith(AndroidJUnit4::class)
class DefaultTabManagerTest {

    @get:Rule
    val coroutineTestRule = CoroutineTestRule()

    private val tabRepository: TabRepository = mock()
    private val omnibarEntryConverter: OmnibarEntryConverter = mock()
    private val skipUrlConversionOnNewTabFeature = FakeFeatureToggleFactory.create(SkipUrlConversionOnNewTabFeature::class.java)

    private lateinit var testee: DefaultTabManager

    @Before
    fun setup() {
        skipUrlConversionOnNewTabFeature.self().setRawStoredState(State(enable = false))

        testee = DefaultTabManager(
            tabRepository = tabRepository,
            dispatchers = coroutineTestRule.testDispatcherProvider,
            queryUrlConverter = omnibarEntryConverter,
            skipUrlConversionOnNewTabFeature = skipUrlConversionOnNewTabFeature,
        )

        testee.registerCallbacks({})
    }

    @Test
    fun whenOnSelectedTabChangedThenSelectedTabIdIsUpdated() {
        val tabId = "tabId"
        testee.onSelectedTabChanged(tabId)

        assertEquals(tabId, testee.getSelectedTabId())
    }

    @Test
    fun whenOnTabsChangedThenOnTabsUpdatedCalledWithNewTabs() = runTest {
        val tabId = "tabId"
        val tabId2 = "tabId2"
        val tabs = listOf(tabId, tabId2)
        val onTabsUpdated: (List<String>) -> Unit = mock()

        testee.registerCallbacks(onTabsUpdated)
        testee.onSelectedTabChanged(tabId)
        testee.onTabsChanged(tabs)

        verify(onTabsUpdated).invoke(tabs)
    }

    @Test
    fun whenOnTabsChangedAndNoTabsThenAddDefaultTabCalled() = runTest {
        testee.onTabsChanged(emptyList())

        verify(tabRepository).addDefaultTab()
    }

    @Test
    fun whenRequestAndWaitForNewTabThenReturnNewTabEntity() = runTest {
        val tabId = "tabId"
        val tabEntity = TabEntity(tabId = tabId, position = 0)
        whenever(tabRepository.flowTabs).thenReturn(flowOf(listOf(tabEntity, tabEntity.copy(tabId = "tabId2"))))
        whenever(tabRepository.add()).thenReturn(tabId)

        val result = testee.requestAndWaitForNewTab()

        assertEquals(tabEntity, result)
    }

    @Test
    fun whenSwitchToTabThenSelectTabCalled() = runTest {
        val tabId = "tabId"
        val tabEntity = TabEntity(tabId = tabId, position = 0)
        whenever(tabRepository.getSelectedTab()).thenReturn(null)
        whenever(tabRepository.getTab(tabId)).thenReturn(tabEntity)

        testee.switchToTab(tabId)

        verify(tabRepository).select(tabId)
    }

    @Test
    fun whenSwitchToSameTabThenSelectTabNotCalled() = runTest {
        val tabId = "tabId"
        val tabEntity = TabEntity(tabId = tabId, position = 0)
        whenever(tabRepository.getSelectedTab()).thenReturn(tabEntity)
        whenever(tabRepository.getTab(tabId)).thenReturn(tabEntity)

        testee.switchToTab(tabId)

        verify(tabRepository, never()).select(tabId)
    }

    @Test
    fun whenOpenNewTabWithParametersQueryThenAddTabWithAppropriateParameters() = runTest {
        val query = "query"
        val url = "http://example.com"
        whenever(omnibarEntryConverter.convertQueryToUrl(query)).thenReturn(url)

        testee.openNewTab()

        verify(tabRepository).add()

        testee.openNewTab(query)

        verify(tabRepository).add(url = url, skipHome = false)

        testee.openNewTab(query, skipHome = true)

        verify(tabRepository).add(url = url, skipHome = true)
    }

    @Test
    fun whenOpenNewTabWithSourceTabThenAddTabFromSource() = runTest {
        val sourceTabId = "sourceTabId"

        testee.openNewTab(sourceTabId = sourceTabId)

        verify(tabRepository).addFromSourceTab(sourceTabId = sourceTabId, skipHome = false)

        testee.openNewTab(sourceTabId = sourceTabId, skipHome = true)

        verify(tabRepository).addFromSourceTab(sourceTabId = sourceTabId, skipHome = true)
    }

    @Test
    fun whenGetTabByIdThenReturnTabEntity() = runTest {
        val tabId = "tabId"
        val tabEntity = TabEntity(tabId = tabId, position = 0)
        whenever(tabRepository.getTab(tabId)).thenReturn(tabEntity)

        val result = testee.getTabById(tabId)

        assertEquals(tabEntity, result)
    }

    @Test
    fun whenOpenNewTabWithSkipUrlConversionFeatureEnabledThenQueryNotConverted() = runTest {
        val query = "query"
        skipUrlConversionOnNewTabFeature.self().setRawStoredState(State(enable = true))

        testee.openNewTab(query)

        verify(tabRepository).add(url = query, skipHome = false)
        verify(omnibarEntryConverter, never()).convertQueryToUrl(query)
    }

    @Test
    fun whenRequestAndWaitForNewTabAndTabIsFoundImmediatelyThenReturnTabEntity() = runTest {
        val tabId = "tabId"
        val tabEntity = TabEntity(tabId = tabId, position = 0)
        whenever(tabRepository.flowTabs).thenReturn(flowOf(listOf(tabEntity)))
        whenever(tabRepository.add()).thenReturn(tabId)

        val result = testee.requestAndWaitForNewTab()

        assertEquals(tabEntity, result)
    }

    @Test
    fun whenRequestAndWaitForNewTabAndTabIsFoundAfterDelayThenReturnTabEntity() = runTest {
        val tabId = "tabId"
        val tabEntity = TabEntity(tabId = tabId, position = 0)

        val flow = flow {
            emit(emptyList())
            delay(500) // Delay less than the timeout
            emit(listOf(tabEntity))
        }
        whenever(tabRepository.flowTabs).thenReturn(flow)
        whenever(tabRepository.add()).thenReturn(tabId)

        val result = testee.requestAndWaitForNewTab()

        assertEquals(tabEntity, result)
    }

    @Test
    fun whenRequestAndWaitForNewTabAndTimeoutOccursThenThrowException() = runTest {
        val tabId = "tabId"

        val flow = flow {
            while(true) {
                emit(emptyList<TabEntity>())
                delay(300)
            }
        }
        whenever(tabRepository.flowTabs).thenReturn(flow)
        whenever(tabRepository.add()).thenReturn(tabId)

        try {
            testee.requestAndWaitForNewTab()
            fail("Expected IllegalStateException was not thrown")
        } catch (e: IllegalStateException) {
            assertEquals("A new tab failed to be created within 1 second", e.message)
        }
    }

    @Test
    fun whenRequestAndWaitForNewTabAndFlowCompletesWithoutFindingTabThenThrowException() = runTest {
        val tabId = "tabId"
        val otherTabId = "otherTabId"
        val otherTabEntity = TabEntity(tabId = otherTabId, position = 0)

        val flow = flowOf(listOf(otherTabEntity))
        whenever(tabRepository.flowTabs).thenReturn(flow)
        whenever(tabRepository.add()).thenReturn(tabId)

        try {
            testee.requestAndWaitForNewTab()
            fail("Expected IllegalStateException was not thrown")
        } catch (e: IllegalStateException) {
            assertEquals("Tabs flow completed before finding the new tab", e.message)
        }
    }
}
