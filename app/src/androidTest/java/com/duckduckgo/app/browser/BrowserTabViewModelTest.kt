/*
 * Copyright (c) 2017 DuckDuckGo
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.duckduckgo.app.browser

import android.arch.core.executor.testing.InstantTaskExecutorRule
import android.arch.lifecycle.LiveData
import android.arch.lifecycle.MutableLiveData
import android.arch.lifecycle.Observer
import android.arch.persistence.room.Room
import android.net.Uri
import android.support.test.InstrumentationRegistry
import android.view.MenuItem
import android.view.View
import com.duckduckgo.app.autocomplete.api.AutoCompleteApi
import com.duckduckgo.app.bookmarks.db.BookmarkEntity
import com.duckduckgo.app.bookmarks.db.BookmarksDao
import com.duckduckgo.app.browser.BrowserTabViewModel.Command
import com.duckduckgo.app.browser.BrowserTabViewModel.Command.Navigate
import com.duckduckgo.app.browser.omnibar.OmnibarEntryConverter
import com.duckduckgo.app.global.db.AppConfigurationDao
import com.duckduckgo.app.global.db.AppConfigurationEntity
import com.duckduckgo.app.global.db.AppDatabase
import com.duckduckgo.app.privacy.db.NetworkLeaderboardDao
import com.duckduckgo.app.privacy.db.NetworkLeaderboardEntry
import com.duckduckgo.app.privacy.db.NetworkPercent
import com.duckduckgo.app.privacy.model.PrivacyGrade
import com.duckduckgo.app.privacy.store.TermsOfServiceStore
import com.duckduckgo.app.settings.db.SettingsDataStore
import com.duckduckgo.app.statistics.api.StatisticsUpdater
import com.duckduckgo.app.tabs.TabDataRepository
import com.duckduckgo.app.trackerdetection.model.TrackerNetwork
import com.duckduckgo.app.trackerdetection.model.TrackerNetworks
import com.duckduckgo.app.trackerdetection.model.TrackingEvent
import com.nhaarman.mockito_kotlin.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.*
import org.mockito.ArgumentMatchers.anyString
import org.mockito.Mockito.never
import org.mockito.Mockito.verify

class BrowserTabViewModelTest {

    @get:Rule
    @Suppress("unused")
    var instantTaskExecutorRule = InstantTaskExecutorRule()

    private var lastNetworkLeaderboardEntry: NetworkLeaderboardEntry? = null

    private val testNetworkLeaderboardDao: NetworkLeaderboardDao = object : NetworkLeaderboardDao {
        override fun insert(leaderboardEntry: NetworkLeaderboardEntry) {
            lastNetworkLeaderboardEntry = leaderboardEntry
        }

        override fun networkPercents(): LiveData<Array<NetworkPercent>> {
            return MutableLiveData<Array<NetworkPercent>>()
        }
    }

    @Mock
    private lateinit var mockStatisticsUpdater: StatisticsUpdater

    @Mock
    private lateinit var mockQueryObserver: Observer<String>

    @Mock
    private lateinit var mockCommandObserver: Observer<Command>

    @Mock
    private lateinit var mockTermsOfServiceStore: TermsOfServiceStore

    @Mock
    private lateinit var mockSettingsStore: SettingsDataStore

    @Mock
    private lateinit var mockAutoCompleteApi: AutoCompleteApi

    @Mock
    private lateinit var bookmarksDao: BookmarksDao

    @Mock
    private lateinit var mockLongPressHandler: LongPressHandler

    @Mock
    private lateinit var mockOmnibarConverter: OmnibarEntryConverter

    @Captor
    private lateinit var commandCaptor: ArgumentCaptor<Command>

    private lateinit var db: AppDatabase
    private lateinit var appConfigurationDao: AppConfigurationDao

    private lateinit var testee: BrowserTabViewModel

    @Before
    fun before() {
        MockitoAnnotations.initMocks(this)

        db = Room.inMemoryDatabaseBuilder(InstrumentationRegistry.getContext(), AppDatabase::class.java)
                .allowMainThreadQueries()
                .build()
        appConfigurationDao = db.appConfigurationDao()

        testee = BrowserTabViewModel(
                statisticsUpdater = mockStatisticsUpdater,
                queryUrlConverter = mockOmnibarConverter,
                duckDuckGoUrlDetector = DuckDuckGoUrlDetector(),
                termsOfServiceStore = mockTermsOfServiceStore,
                trackerNetworks = TrackerNetworks(),
                tabRepository = TabDataRepository(),
                networkLeaderboardDao = testNetworkLeaderboardDao,
                autoCompleteApi = mockAutoCompleteApi,
                appSettingsPreferencesStore = mockSettingsStore,
                bookmarksDao = bookmarksDao,
                longPressHandler = mockLongPressHandler,
                appConfigurationDao = appConfigurationDao)

        testee.url.observeForever(mockQueryObserver)
        testee.command.observeForever(mockCommandObserver)

        whenever(mockOmnibarConverter.convertQueryToUri(any())).thenReturn(Uri.parse("duckduckgo.com"))

    }

    @After
    fun after() {
        testee.onCleared()
        db.close()
        testee.url.removeObserver(mockQueryObserver)
        testee.command.removeObserver(mockCommandObserver)
    }

    @Test
    fun whenSubmittedQueryHasWhitespaceItIsTrimmed() {
        testee.onUserSubmittedQuery(" nytimes.com ")
        verify(mockOmnibarConverter).isWebUrl("nytimes.com")
        assertEquals("nytimes.com", testee.viewState.value!!.omnibarText)
    }

    @Test
    fun whenUrlPresentThenAddBookmarkButtonEnabled() {
        testee.urlChanged("www.example.com")
        assertTrue(testee.viewState.value!!.canAddBookmarks)
    }

    @Test
    fun whenNoUrlThenAddBookmarkButtonDisabled() {
        testee.urlChanged(null)
        assertFalse(testee.viewState.value!!.canAddBookmarks)
    }

    @Test
    fun whenBookmarkAddedThenDaoIsUpdated() {
        testee.addBookmark("A title", "www.example.com")
        verify(bookmarksDao).insert(BookmarkEntity(title = "A title", url = "www.example.com"))
    }

    @Test
    fun whenTrackerDetectedThenNetworkLeaderboardUpdated() {
        testee.trackerDetected(TrackingEvent("http://www.example.com", "http://www.tracker.com/tracker.js", TrackerNetwork("Network1", "www.tracker.com"), false))
        assertNotNull(lastNetworkLeaderboardEntry)
        assertEquals(lastNetworkLeaderboardEntry!!.domainVisited, "www.example.com")
        assertEquals(lastNetworkLeaderboardEntry!!.networkName, "Network1")
    }

    @Test
    fun whenEmptyInputQueryThenNoQueryMadeAvailableToActivity() {
        testee.onUserSubmittedQuery("")
        verify(mockQueryObserver, never()).onChanged(ArgumentMatchers.anyString())
    }

    @Test
    fun whenBlankInputQueryThenNoQueryMadeAvailableToActivity() {
        testee.onUserSubmittedQuery("     ")
        verify(mockQueryObserver, never()).onChanged(ArgumentMatchers.anyString())
    }

    @Test
    fun whenNonEmptyInputThenQueryMadeAvailableToActivity() {
        testee.onUserSubmittedQuery("foo")
        verify(mockQueryObserver).onChanged(ArgumentMatchers.anyString())
    }

    @Test
    fun whenViewModelNotifiedThatWebViewIsLoadingThenViewStateIsUpdated() {
        testee.loadingStarted()
        assertTrue(testee.viewState.value!!.isLoading)
    }

    @Test
    fun whenViewModelNotifiedThatWebViewHasFinishedLoadingThenViewStateIsUpdated() {
        testee.loadingFinished()
        assertFalse(testee.viewState.value!!.isLoading)
    }

    @Test
    fun whenViewModelNotifiedThatUrlGotFocusThenViewStateIsUpdated() {
        testee.onOmnibarInputStateChanged("", true)
        assertTrue(testee.viewState.value!!.isEditing)
    }

    @Test
    fun whenViewModelNotifiedThatUrlLostFocusThenViewStateIsUpdated() {
        testee.onOmnibarInputStateChanged("", false)
        assertFalse(testee.viewState.value!!.isEditing)
    }

    @Test
    fun whenNoOmnibarTextEverEnteredThenViewStateHasEmptyString() {
        assertEquals("", testee.viewState.value!!.omnibarText)
    }

    @Test
    fun whenUrlChangedThenViewStateIsUpdated() {
        testee.urlChanged("duckduckgo.com")
        assertEquals("duckduckgo.com", testee.viewState.value!!.omnibarText)
    }

    @Test
    fun whenUrlChangedWithDuckDuckGoUrlContainingQueryThenUrlRewrittenToContainQuery() {
        testee.urlChanged("http://duckduckgo.com?q=test")
        assertEquals("test", testee.viewState.value!!.omnibarText)
    }

    @Test
    fun whenUrlChangedWithDuckDuckGoUrlContainingQueryThenAtbRefreshed() {
        testee.urlChanged("http://duckduckgo.com?q=test")
        verify(mockStatisticsUpdater).refreshRetentionAtb()
    }

    @Test
    fun whenUrlChangedWithDuckDuckGoUrlNotContainingQueryThenFullUrlShown() {
        testee.urlChanged("http://duckduckgo.com")
        assertEquals("http://duckduckgo.com", testee.viewState.value!!.omnibarText)
    }

    @Test
    fun whenUrlChangedWithNonDuckDuckGoUrlThenFullUrlShown() {
        testee.urlChanged("http://example.com")
        assertEquals("http://example.com", testee.viewState.value!!.omnibarText)
    }

    @Test
    fun whenViewModelGetsProgressUpdateThenViewStateIsUpdated() {
        testee.progressChanged(0)
        assertEquals(0, testee.viewState.value!!.progress)

        testee.progressChanged(50)
        assertEquals(50, testee.viewState.value!!.progress)

        testee.progressChanged(100)
        assertEquals(100, testee.viewState.value!!.progress)
    }

    @Test
    fun whenLoadingStartedThenPrivacyGradeIsCleared() {
        testee.loadingStarted()
        assertNull(testee.privacyGrade.value)
    }

    @Test
    fun whenUrlChangedThenPrivacyGradeIsReset() {
        testee.urlChanged("https://example.com")
        assertEquals(PrivacyGrade.B, testee.privacyGrade.value)
    }

    @Test
    fun whenEnoughTrackersDetectedThenPrivacyGradeIsUpdated() {
        testee.urlChanged("https://example.com")
        for (i in 1..10) {
            testee.trackerDetected(TrackingEvent("https://example.com", "", null, false))
        }
        assertEquals(PrivacyGrade.C, testee.privacyGrade.value)
    }

    @Test
    fun whenInitialisedThenPrivacyGradeIsNotShown() {
        assertFalse(testee.viewState.value!!.showPrivacyGrade)
    }

    @Test
    fun whenUrlUpdatedAfterConfigDownloadThenPrivacyGradeIsShown() {
        testee.appConfigurationObserver.onChanged(AppConfigurationEntity(appConfigurationDownloaded = true))
        testee.urlChanged((""))
        assertTrue(testee.viewState.value!!.showPrivacyGrade)
    }

    @Test
    fun whenUrlUpdatedBeforeConfigDownloadThenPrivacyGradeIsShown() {
        testee.appConfigurationObserver.onChanged(AppConfigurationEntity(appConfigurationDownloaded = false))
        testee.urlChanged((""))
        assertFalse(testee.viewState.value!!.showPrivacyGrade)
    }

    @Test
    fun whenOmnibarInputDoesNotHaveFocusAndAppConfigDownloadedAndBrowserShownThenPrivacyGradeIsShown() {
        testee.onUserSubmittedQuery("foo")
        testee.appConfigurationObserver.onChanged(AppConfigurationEntity(appConfigurationDownloaded = true))
        testee.onOmnibarInputStateChanged(query = "", hasFocus = false)
        assertTrue(testee.viewState.value!!.showPrivacyGrade)
    }

    @Test
    fun whenOmnibarInputDoesNotHaveFocusAndAppConfigDownloadedButBrowserNotShownThenPrivacyGradeIsHidden() {
        testee.appConfigurationObserver.onChanged(AppConfigurationEntity(appConfigurationDownloaded = true))
        testee.onOmnibarInputStateChanged(query = "", hasFocus = false)
        assertFalse(testee.viewState.value!!.showPrivacyGrade)
    }

    @Test
    fun whenOmnibarInputDoesNotHaveFocusAndAppConfigNotDownloadedThenPrivacyGradeIsNotShown() {
        testee.appConfigurationObserver.onChanged(AppConfigurationEntity(appConfigurationDownloaded = false))
        testee.onOmnibarInputStateChanged("", false)
        assertFalse(testee.viewState.value!!.showPrivacyGrade)
    }

    @Test
    fun whenOmnibarInputHasFocusThenPrivacyGradeIsNotShown() {
        testee.onOmnibarInputStateChanged("", true)
        assertFalse(testee.viewState.value!!.showPrivacyGrade)
    }

    @Test
    fun whenInitialisedThenFireButtonIsShown() {
        assertTrue(testee.viewState.value!!.showFireButton)
    }

    @Test
    fun whenOmnibarInputDoesNotHaveFocusThenFireButtonIsShown() {
        testee.onOmnibarInputStateChanged("", false)
        assertTrue(testee.viewState.value!!.showFireButton)
    }

    @Test
    fun whenOmnibarInputHasFocusThenFireButtonIsShown() {
        testee.onOmnibarInputStateChanged("", true)
        assertTrue(testee.viewState.value!!.showFireButton)
    }

    @Test
    fun whenEnteringQueryWithAutoCompleteEnabledThenAutoCompleteSuggestionsShown() {
        doReturn(true).whenever(mockSettingsStore).autoCompleteSuggestionsEnabled
        testee.onOmnibarInputStateChanged("foo", true)
        assertTrue(testee.viewState.value!!.autoComplete.showSuggestions)
    }

    @Test
    fun whenEnteringQueryWithAutoCompleteDisabledThenAutoCompleteSuggestionsNotShown() {
        doReturn(false).whenever(mockSettingsStore).autoCompleteSuggestionsEnabled
        testee.onOmnibarInputStateChanged("foo", true)
        assertFalse(testee.viewState.value!!.autoComplete.showSuggestions)
    }

    @Test
    fun whenEnteringEmptyQueryWithAutoCompleteEnabledThenAutoCompleteSuggestionsNotShown() {
        doReturn(true).whenever(mockSettingsStore).autoCompleteSuggestionsEnabled
        testee.onOmnibarInputStateChanged("", true)
        assertFalse(testee.viewState.value!!.autoComplete.showSuggestions)
    }

    @Test
    fun whenEnteringEmptyQueryWithAutoCompleteDisabledThenAutoCompleteSuggestionsNotShown() {
        doReturn(false).whenever(mockSettingsStore).autoCompleteSuggestionsEnabled
        testee.onOmnibarInputStateChanged("", true)
        assertFalse(testee.viewState.value!!.autoComplete.showSuggestions)
    }

    @Test
    fun whenEnteringEmptyQueryThenHideKeyboardCommandNotIssued() {
        testee.onUserSubmittedQuery("")
        verify(mockCommandObserver, never()).onChanged(Mockito.any(Command.HideKeyboard.javaClass))
    }

    @Test
    fun whenEnteringNonEmptyQueryThenHideKeyboardCommandIssued() {
        testee.onUserSubmittedQuery("foo")
        verify(mockCommandObserver, Mockito.atLeastOnce()).onChanged(commandCaptor.capture())
        assertTrue(commandCaptor.value == Command.HideKeyboard)
    }

    @Test
    fun whenNotifiedEnteringFullScreenThenViewStateUpdatedWithFullScreenFlag() {
        val stubView = View(InstrumentationRegistry.getTargetContext())
        testee.goFullScreen(stubView)
        assertTrue(testee.viewState.value!!.isFullScreen)
    }

    @Test
    fun whenNotifiedEnteringFullScreenThenEnterFullScreenCommandIssued() {
        val stubView = View(InstrumentationRegistry.getTargetContext())
        testee.goFullScreen(stubView)
        verify(mockCommandObserver, Mockito.atLeastOnce()).onChanged(commandCaptor.capture())
        assertTrue(commandCaptor.lastValue is Command.ShowFullScreen)
    }

    @Test
    fun whenNotifiedLeavingFullScreenThenViewStateUpdatedWithFullScreenFlagDisabled() {
        testee.exitFullScreen()
        assertFalse(testee.viewState.value!!.isFullScreen)
    }

    @Test
    fun whenViewModelInitialisedThenFullScreenFlagIsDisabled() {
        assertFalse(testee.viewState.value!!.isFullScreen)
    }

    @Test
    fun whenUserSelectsDownloadImageOptionFromContextMenuThenDownloadFileCommandIssued() {
        whenever(mockLongPressHandler.userSelectedMenuItem(anyString(), any()))
                .thenReturn(LongPressHandler.RequiredAction.DownloadFile("example.com"))

        val mockMenuItem : MenuItem = mock()
        testee.userSelectedItemFromLongPressMenu("example.com", mockMenuItem)
        verify(mockCommandObserver, Mockito.atLeastOnce()).onChanged(commandCaptor.capture())
        assertTrue(commandCaptor.lastValue is Command.DownloadImage)

        val lastCommand = commandCaptor.lastValue as Command.DownloadImage
        assertEquals("example.com", lastCommand.url)
    }

    @Test
    fun whenUserTypesSearchTermThenViewStateUpdatedToDenoteUserIsFindingInPage() {
        testee.userFindingInPage("foo")
        assertTrue(testee.viewState.value!!.findInPage.visible)
    }

    @Test
    fun whenUserTypesSearchTermThenViewStateUpdatedToContainSearchTerm() {
        testee.userFindingInPage("foo")
        assertEquals("foo", testee.viewState.value!!.findInPage.searchTerm)
    }

    @Test
    fun whenUserDismissesFindInPageThenViewStateUpdatedToDenoteUserIsNotFindingInPage() {
        testee.dismissFindInView()
        assertFalse(testee.viewState.value!!.findInPage.visible)
    }

    @Test
    fun whenUserDismissesFindInPageThenViewStateUpdatedToClearSearchTerm() {
        testee.userFindingInPage("foo")
        testee.dismissFindInView()
        assertEquals("", testee.viewState.value!!.findInPage.searchTerm)
    }

    @Test
    fun whenUserSelectsDesktopSiteThenDesktopModeStateUpdated() {
        testee.desktopSiteModeToggled("http://example.com", desktopSiteRequested = true)
        verify(mockCommandObserver, Mockito.atLeastOnce()).onChanged(commandCaptor.capture())
        assertTrue(testee.viewState.value!!.isDesktopBrowsingMode)
    }

    @Test
    fun whenUserSelectsMobileSiteThenMobileModeStateUpdated() {
        testee.desktopSiteModeToggled("http://example.com", desktopSiteRequested = false)
        assertFalse(testee.viewState.value!!.isDesktopBrowsingMode)
    }

    @Test
    fun whenUserSelectsDesktopSiteWhenOnMobileSpecificSiteThenUrlModified() {
        testee.desktopSiteModeToggled("http://m.example.com", desktopSiteRequested = true)
        verify(mockCommandObserver, Mockito.atLeastOnce()).onChanged(commandCaptor.capture())
        val ultimateCommand = commandCaptor.lastValue as Navigate
        assertEquals("http://example.com", ultimateCommand.url)
    }

    @Test
    fun whenUserSelectsDesktopSiteWhenNotOnMobileSpecificSiteThenUrlNotModified() {
        testee.desktopSiteModeToggled("http://example.com", desktopSiteRequested = true)
        verify(mockCommandObserver, Mockito.atLeastOnce()).onChanged(commandCaptor.capture())
        val ultimateCommand = commandCaptor.lastValue
        assertTrue(ultimateCommand == Command.Refresh)
    }

    @Test
    fun whenUserSelectsMobileSiteWhenOnMobileSpecificSiteThenUrlNotModified() {
        testee.desktopSiteModeToggled("http://m.example.com", desktopSiteRequested = false)
        verify(mockCommandObserver, Mockito.atLeastOnce()).onChanged(commandCaptor.capture())
        val ultimateCommand = commandCaptor.lastValue
        assertTrue(ultimateCommand == Command.Refresh)
    }

    @Test
    fun whenUserSelectsMobileSiteWhenNotOnMobileSpecificSiteThenUrlNotModified() {
        testee.desktopSiteModeToggled("http://example.com", desktopSiteRequested = false)
        verify(mockCommandObserver, Mockito.atLeastOnce()).onChanged(commandCaptor.capture())
        val ultimateCommand = commandCaptor.lastValue
        assertTrue(ultimateCommand == Command.Refresh)
    }

    @Test
    fun whenUserSelectsToShareLinkThenShareLinkCommandSent() {
        testee.userSharingLink("foo")
        val command = captureCommands().value as Command.ShareLink
        assertEquals("foo", command.url)
    }

    @Test
    fun whenUserSelectsToShareLinkWithNullUrlThenShareLinkCommandNotSent() {
        testee.userSharingLink(null)
        val allCommands = captureCommands().allValues
        assertEquals(0, allCommands.count{ it is Command.ShareLink })
    }

    private fun captureCommands() : ArgumentCaptor<Command> {
        verify(mockCommandObserver, Mockito.atLeastOnce()).onChanged(commandCaptor.capture())
        return commandCaptor
    }
}
