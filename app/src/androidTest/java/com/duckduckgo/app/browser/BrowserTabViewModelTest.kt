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
import android.arch.lifecycle.MutableLiveData
import android.arch.lifecycle.Observer
import android.arch.persistence.room.Room
import android.support.test.InstrumentationRegistry
import android.view.MenuItem
import android.view.View
import com.duckduckgo.app.InstantSchedulersRule
import com.duckduckgo.app.autocomplete.api.AutoCompleteApi
import com.duckduckgo.app.bookmarks.db.BookmarkEntity
import com.duckduckgo.app.bookmarks.db.BookmarksDao
import com.duckduckgo.app.browser.BrowserTabViewModel.Command
import com.duckduckgo.app.browser.BrowserTabViewModel.Command.DisplayMessage
import com.duckduckgo.app.browser.BrowserTabViewModel.Command.Navigate
import com.duckduckgo.app.browser.LongPressHandler.RequiredAction.DownloadFile
import com.duckduckgo.app.browser.LongPressHandler.RequiredAction.OpenInNewTab
import com.duckduckgo.app.browser.defaultBrowsing.DefaultBrowserDetector
import com.duckduckgo.app.browser.defaultBrowsing.DefaultBrowserNotification
import com.duckduckgo.app.browser.favicon.FaviconDownloader
import com.duckduckgo.app.browser.omnibar.OmnibarEntryConverter
import com.duckduckgo.app.browser.session.WebViewSessionStorage
import com.duckduckgo.app.global.db.AppConfigurationDao
import com.duckduckgo.app.global.db.AppConfigurationEntity
import com.duckduckgo.app.global.db.AppDatabase
import com.duckduckgo.app.global.model.SiteFactory
import com.duckduckgo.app.privacy.db.NetworkLeaderboardDao
import com.duckduckgo.app.privacy.db.NetworkLeaderboardEntry
import com.duckduckgo.app.privacy.db.SiteVisitedEntity
import com.duckduckgo.app.privacy.model.PrivacyGrade
import com.duckduckgo.app.privacy.store.TermsOfServiceStore
import com.duckduckgo.app.settings.db.SettingsDataStore
import com.duckduckgo.app.statistics.VariantManager
import com.duckduckgo.app.statistics.api.StatisticsUpdater
import com.duckduckgo.app.tabs.model.TabRepository
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

    @get:Rule
    @Suppress("unused")
    val schedulers = InstantSchedulersRule()

    @Mock
    private lateinit var mockNetworkLeaderboardDao: NetworkLeaderboardDao

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

    @Mock
    private lateinit var mockDefaultBrowserDetector: DefaultBrowserDetector

    @Mock
    private lateinit var mockDefaultBrowserNotification: DefaultBrowserNotification

    @Mock
    private lateinit var mockTabsRepository: TabRepository

    @Mock
    private lateinit var webViewSessionStorage: WebViewSessionStorage

    @Mock
    private lateinit var variantManager: VariantManager

    @Mock
    private lateinit var mockFaviconDownloader: FaviconDownloader

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

        val siteFactory = SiteFactory(mockTermsOfServiceStore, TrackerNetworks())

        whenever(mockTabsRepository.retrieveSiteData(any())).thenReturn(MutableLiveData())

        testee = BrowserTabViewModel(
            statisticsUpdater = mockStatisticsUpdater,
            queryUrlConverter = mockOmnibarConverter,
            duckDuckGoUrlDetector = DuckDuckGoUrlDetector(),
            siteFactory = siteFactory,
            tabRepository = mockTabsRepository,
            networkLeaderboardDao = mockNetworkLeaderboardDao,
            autoCompleteApi = mockAutoCompleteApi,
            appSettingsPreferencesStore = mockSettingsStore,
            bookmarksDao = bookmarksDao,
            defaultBrowserNotification = mockDefaultBrowserNotification,
            defaultBrowserDetector = mockDefaultBrowserDetector,
            longPressHandler = mockLongPressHandler,
            appConfigurationDao = appConfigurationDao,
            webViewSessionStorage = webViewSessionStorage,
            specialUrlDetector = SpecialUrlDetector(),
            faviconDownloader = mockFaviconDownloader,
            variantManager = variantManager
        )

        testee.loadData("abc", null)
        testee.url.observeForever(mockQueryObserver)
        testee.command.observeForever(mockCommandObserver)

        whenever(mockOmnibarConverter.convertQueryToUrl(any())).thenReturn("duckduckgo.com")

    }

    @After
    fun after() {
        testee.onCleared()
        db.close()
        testee.url.removeObserver(mockQueryObserver)
        testee.command.removeObserver(mockCommandObserver)
    }

    @Test
    fun whenOpenInNewBackgroundRequestedThenTabRepositoryUpdatedAndCommandIssued() {
        val url = "http://www.example.com"
        testee.openInNewBackgroundTab(url)

        verify(mockCommandObserver, atLeastOnce()).onChanged(commandCaptor.capture())
        assertTrue(commandCaptor.lastValue is Command.OpenInNewBackgroundTab)

        verify(mockTabsRepository).addNewTabAfterExistingTab(url, "abc")
    }

    @Test
    fun whenViewBecomesVisibleWithActiveSiteThenKeyboardHidden() {
        testee.url.value = "http://exmaple.com"
        testee.onViewVisible()
        verify(mockCommandObserver, atLeastOnce()).onChanged(commandCaptor.capture())
        assertTrue(commandCaptor.lastValue is Command.HideKeyboard)
    }

    @Test
    fun whenViewBecomesVisibleWithoutActiveSiteThenKeyboardShown() {
        testee.url.value = null
        testee.onViewVisible()
        verify(mockCommandObserver, atLeastOnce()).onChanged(commandCaptor.capture())
        assertTrue(commandCaptor.lastValue is Command.ShowKeyboard)
    }

    @Test
    fun whenSubmittedQueryHasWhitespaceItIsTrimmed() {
        testee.onUserSubmittedQuery(" nytimes.com ")
        assertEquals("nytimes.com", omnibarViewState().omnibarText)
    }

    @Test
    fun whenUrlPresentThenAddBookmarkButtonEnabled() {
        testee.urlChanged("www.example.com")
        assertTrue(browserViewState().canAddBookmarks)
    }

    @Test
    fun whenNoUrlThenAddBookmarkButtonDisabled() {
        testee.urlChanged(null)
        assertFalse(browserViewState().canAddBookmarks)
    }

    @Test
    fun whenBookmarkAddedThenDaoIsUpdatedAndUserNotified() {
        testee.onBookmarkSaved(null, "A title", "www.example.com")
        verify(bookmarksDao).insert(BookmarkEntity(title = "A title", url = "www.example.com"))
        verify(mockCommandObserver, atLeastOnce()).onChanged(commandCaptor.capture())
        assertTrue(commandCaptor.lastValue is DisplayMessage)
    }

    @Test
    fun whenTrackerDetectedThenNetworkLeaderboardUpdated() {
        val event = TrackingEvent("http://www.example.com", "http://www.tracker.com/tracker.js", TrackerNetwork("Network1", "www.tracker.com"), false)
        testee.trackerDetected(event)
        verify(mockNetworkLeaderboardDao).insert(NetworkLeaderboardEntry("Network1", "www.example.com"))
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
        assertTrue(loadingViewState().isLoading)
    }

    @Test
    fun whenViewModelNotifiedThatWebViewHasFinishedLoadingThenViewStateIsUpdated() {
        testee.loadingFinished(null, false, false)
        assertFalse(loadingViewState().isLoading)
    }

    @Test
    fun whenLoadingFinishedWithUrlThenSiteVisitedEntryAddedToLeaderboardDao() {
        testee.url.value = "http://example.com/abc"
        testee.loadingFinished(null, false, false)
        verify(mockNetworkLeaderboardDao).insert(SiteVisitedEntity("example.com"))
    }

    @Test
    fun whenLoadingFinishedWithUrlThenOmnibarTextUpdatedToMatch() {
        val exampleUrl = "http://example.com/abc"
        testee.loadingFinished(exampleUrl, false, false)
        assertEquals(exampleUrl, omnibarViewState().omnibarText)
    }

    @Test
    fun whenLoadingFinishedWithQueryUrlThenOmnibarTextUpdatedToShowQuery() {
        val queryUrl = "http://duckduckgo.com?q=test"
        testee.loadingFinished(queryUrl, false, false)
        assertEquals("test", omnibarViewState().omnibarText)
    }

    @Test
    fun whenLoadingFinishedWithNoUrlThenOmnibarTextUpdatedToMatch() {
        val exampleUrl = "http://example.com/abc"
        testee.urlChanged(exampleUrl)
        testee.loadingFinished(null, false, false)
        assertEquals(exampleUrl, omnibarViewState().omnibarText)
    }

    @Test
    fun whenLoadingFinishedWithNoUrlThenSiteVisitedEntryNotAddedToLeaderboardDao() {
        testee.loadingFinished(null, false, false)
        verify(mockNetworkLeaderboardDao, never()).insert(SiteVisitedEntity("example.com"))
    }

    @Test
    fun whenTrackerDetectedThenSiteVisitedEntryAddedToLeaderboardDao() {
        testee.trackerDetected(TrackingEvent("http://example.com/abc", "http://tracker.com", TrackerNetwork("Network", "http:// netwotk.com"), true))
        verify(mockNetworkLeaderboardDao).insert(SiteVisitedEntity("example.com"))
    }

    @Test
    fun whenViewModelNotifiedThatUrlGotFocusThenViewStateIsUpdated() {
        testee.onOmnibarInputStateChanged("", true)
        assertTrue(omnibarViewState().isEditing)
    }

    @Test
    fun whenViewModelNotifiedThatUrlLostFocusThenViewStateIsUpdated() {
        testee.onOmnibarInputStateChanged("", false)
        assertFalse(omnibarViewState().isEditing)
    }

    @Test
    fun whenNoOmnibarTextEverEnteredThenViewStateHasEmptyString() {
        assertEquals("", omnibarViewState().omnibarText)
    }

    @Test
    fun whenUrlChangedThenViewStateIsUpdated() {
        testee.urlChanged("duckduckgo.com")
        assertEquals("duckduckgo.com", omnibarViewState().omnibarText)
    }

    @Test
    fun whenUrlChangedWithDuckDuckGoUrlContainingQueryThenUrlRewrittenToContainQuery() {
        testee.urlChanged("http://duckduckgo.com?q=test")
        assertEquals("test", omnibarViewState().omnibarText)
    }

    @Test
    fun whenUrlChangedWithDuckDuckGoUrlContainingQueryThenAtbRefreshed() {
        testee.urlChanged("http://duckduckgo.com?q=test")
        verify(mockStatisticsUpdater).refreshRetentionAtb()
    }

    @Test
    fun whenUrlChangedWithDuckDuckGoUrlNotContainingQueryThenFullUrlShown() {
        testee.urlChanged("http://duckduckgo.com")
        assertEquals("http://duckduckgo.com", omnibarViewState().omnibarText)
    }

    @Test
    fun whenUrlChangedWithNonDuckDuckGoUrlThenFullUrlShown() {
        testee.urlChanged("http://example.com")
        assertEquals("http://example.com", omnibarViewState().omnibarText)
    }

    @Test
    fun whenViewModelGetsProgressUpdateThenViewStateIsUpdated() {
        testee.progressChanged(0, false, false)
        assertEquals(0, loadingViewState().progress)

        testee.progressChanged(50, false, false)
        assertEquals(50, loadingViewState().progress)

        testee.progressChanged(100, false, false)
        assertEquals(100, loadingViewState().progress)
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
        assertFalse(browserViewState().showPrivacyGrade)
    }

    @Test
    fun whenUrlUpdatedAfterConfigDownloadThenPrivacyGradeIsShown() {
        testee.appConfigurationObserver.onChanged(AppConfigurationEntity(appConfigurationDownloaded = true))
        testee.urlChanged((""))
        assertTrue(browserViewState().showPrivacyGrade)
    }

    @Test
    fun whenUrlUpdatedBeforeConfigDownloadThenPrivacyGradeIsShown() {
        testee.appConfigurationObserver.onChanged(AppConfigurationEntity(appConfigurationDownloaded = false))
        testee.urlChanged((""))
        assertFalse(browserViewState().showPrivacyGrade)
    }

    @Test
    fun whenOmnibarInputDoesNotHaveFocusAndAppConfigDownloadedAndBrowserShownThenPrivacyGradeIsShown() {
        testee.onUserSubmittedQuery("foo")
        testee.appConfigurationObserver.onChanged(AppConfigurationEntity(appConfigurationDownloaded = true))
        testee.onOmnibarInputStateChanged(query = "", hasFocus = false)
        assertTrue(browserViewState().showPrivacyGrade)
    }

    @Test
    fun whenOmnibarInputDoesNotHaveFocusAndAppConfigDownloadedButBrowserNotShownThenPrivacyGradeIsHidden() {
        testee.appConfigurationObserver.onChanged(AppConfigurationEntity(appConfigurationDownloaded = true))
        testee.onOmnibarInputStateChanged(query = "", hasFocus = false)
        assertFalse(browserViewState().showPrivacyGrade)
    }

    @Test
    fun whenOmnibarInputDoesNotHaveFocusAndAppConfigNotDownloadedThenPrivacyGradeIsNotShown() {
        testee.appConfigurationObserver.onChanged(AppConfigurationEntity(appConfigurationDownloaded = false))
        testee.onOmnibarInputStateChanged("", false)
        assertFalse(browserViewState().showPrivacyGrade)
    }

    @Test
    fun whenOmnibarInputHasFocusThenPrivacyGradeIsNotShown() {
        testee.onOmnibarInputStateChanged("", true)
        assertFalse(browserViewState().showPrivacyGrade)
    }

    @Test
    fun whenInitialisedThenFireButtonIsShown() {
        assertTrue(browserViewState().showFireButton)
    }

    @Test
    fun whenOmnibarInputDoesNotHaveFocusAndHasQueryThenFireButtonIsShown() {
        testee.onOmnibarInputStateChanged("query", false)
        assertTrue(browserViewState().showFireButton)
    }

    @Test
    fun whenOmnibarInputDoesNotHaveFocusOrQueryThenFireButtonIsShown() {
        testee.onOmnibarInputStateChanged("", false)
        assertTrue(browserViewState().showFireButton)
    }

    @Test
    fun whenOmnibarInputHasFocusAndNoQueryThenFireButtonIsShown() {
        testee.onOmnibarInputStateChanged("", true)
        assertTrue(browserViewState().showFireButton)
    }

    @Test
    fun whenOmnibarInputHasFocusAndQueryThenFireButtonIsHidden() {
        testee.onOmnibarInputStateChanged("query", true)
        assertFalse(browserViewState().showFireButton)
    }

    @Test
    fun whenInitialisedThenTabsButtonIsShown() {
        assertTrue(browserViewState().showTabsButton)
    }

    @Test
    fun whenOmnibarInputDoesNotHaveFocusOrQueryThenTabsButtonIsShown() {
        testee.onOmnibarInputStateChanged("", false)
        assertTrue(browserViewState().showTabsButton)
    }

    @Test
    fun whenOmnibarInputDoesNotHaveFocusAndHasQueryThenTabsButtonIsShown() {
        testee.onOmnibarInputStateChanged("query", false)
        assertTrue(browserViewState().showTabsButton)
    }

    @Test
    fun whenOmnibarInputHasFocusAndNoQueryThenTabsButtonIsShown() {
        testee.onOmnibarInputStateChanged("", true)
        assertTrue(browserViewState().showTabsButton)
    }

    @Test
    fun whenOmnibarInputHasFocusAndQueryThenTabsButtonIsHidden() {
        testee.onOmnibarInputStateChanged("query", true)
        assertFalse(browserViewState().showTabsButton)
    }

    @Test
    fun whenInitialisedThenMenuButtonIsShown() {
        assertTrue(browserViewState().showMenuButton)
    }

    @Test
    fun whenOmnibarInputDoesNotHaveFocusOrQueryThenMenuButtonIsShown() {
        testee.onOmnibarInputStateChanged("", false)
        assertTrue(browserViewState().showMenuButton)
    }

    @Test
    fun whenOmnibarInputDoesNotHaveFocusAndHasQueryThenMenuButtonIsShown() {
        testee.onOmnibarInputStateChanged("query", false)
        assertTrue(browserViewState().showMenuButton)
    }

    @Test
    fun whenOmnibarInputHasFocusAndNoQueryThenMenuButtonIsShown() {
        testee.onOmnibarInputStateChanged("", true)
        assertTrue(browserViewState().showMenuButton)
    }

    @Test
    fun whenOmnibarInputHasFocusAndQueryThenMenuButtonIsHidden() {
        testee.onOmnibarInputStateChanged("query", true)
        assertFalse(browserViewState().showMenuButton)
    }

    @Test
    fun whenEnteringQueryWithAutoCompleteEnabledThenAutoCompleteSuggestionsShown() {
        doReturn(true).whenever(mockSettingsStore).autoCompleteSuggestionsEnabled
        testee.onOmnibarInputStateChanged("foo", true)
        assertTrue(autoCompleteViewState().showSuggestions)
    }

    @Test
    fun whenEnteringQueryWithAutoCompleteDisabledThenAutoCompleteSuggestionsNotShown() {
        doReturn(false).whenever(mockSettingsStore).autoCompleteSuggestionsEnabled
        testee.onOmnibarInputStateChanged("foo", true)
        assertFalse(autoCompleteViewState().showSuggestions)
    }

    @Test
    fun whenEnteringEmptyQueryWithAutoCompleteEnabledThenAutoCompleteSuggestionsNotShown() {
        doReturn(true).whenever(mockSettingsStore).autoCompleteSuggestionsEnabled
        testee.onOmnibarInputStateChanged("", true)
        assertFalse(autoCompleteViewState().showSuggestions)
    }

    @Test
    fun whenEnteringEmptyQueryWithAutoCompleteDisabledThenAutoCompleteSuggestionsNotShown() {
        doReturn(false).whenever(mockSettingsStore).autoCompleteSuggestionsEnabled
        testee.onOmnibarInputStateChanged("", true)
        assertFalse(autoCompleteViewState().showSuggestions)
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
        assertTrue(browserViewState().isFullScreen)
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
        assertFalse(browserViewState().isFullScreen)
    }

    @Test
    fun whenViewModelInitialisedThenFullScreenFlagIsDisabled() {
        assertFalse(browserViewState().isFullScreen)
    }

    @Test
    fun whenUserSelectsDownloadImageOptionFromContextMenuThenDownloadFileCommandIssued() {
        whenever(mockLongPressHandler.userSelectedMenuItem(anyString(), any()))
            .thenReturn(DownloadFile("example.com"))

        val mockMenuItem: MenuItem = mock()
        testee.userSelectedItemFromLongPressMenu("example.com", mockMenuItem)
        verify(mockCommandObserver, Mockito.atLeastOnce()).onChanged(commandCaptor.capture())
        assertTrue(commandCaptor.lastValue is Command.DownloadImage)

        val lastCommand = commandCaptor.lastValue as Command.DownloadImage
        assertEquals("example.com", lastCommand.url)
    }

    @Test
    fun whenUserTypesSearchTermThenViewStateUpdatedToDenoteUserIsFindingInPage() {
        testee.userFindingInPage("foo")
        assertTrue(findInPageViewState().visible)
    }

    @Test
    fun whenUserTypesSearchTermThenViewStateUpdatedToContainSearchTerm() {
        testee.userFindingInPage("foo")
        assertEquals("foo", findInPageViewState().searchTerm)
    }

    @Test
    fun whenUserDismissesFindInPageThenViewStateUpdatedToDenoteUserIsNotFindingInPage() {
        testee.dismissFindInView()
        assertFalse(findInPageViewState().visible)
    }

    @Test
    fun whenUserDismissesFindInPageThenViewStateUpdatedToClearSearchTerm() {
        testee.userFindingInPage("foo")
        testee.dismissFindInView()
        assertEquals("", findInPageViewState().searchTerm)
    }

    @Test
    fun whenUserSelectsDesktopSiteThenDesktopModeStateUpdated() {
        testee.desktopSiteModeToggled("http://example.com", desktopSiteRequested = true)
        verify(mockCommandObserver, Mockito.atLeastOnce()).onChanged(commandCaptor.capture())
        assertTrue(browserViewState().isDesktopBrowsingMode)
    }

    @Test
    fun whenUserSelectsMobileSiteThenMobileModeStateUpdated() {
        testee.desktopSiteModeToggled("http://example.com", desktopSiteRequested = false)
        assertFalse(browserViewState().isDesktopBrowsingMode)
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
    fun whenUserSelectsOpenTabThenTabCommandSent() {
        whenever(mockLongPressHandler.userSelectedMenuItem(any(), any())).thenReturn(OpenInNewTab("http://example.com"))
        val mockMenItem: MenuItem = mock()
        testee.userSelectedItemFromLongPressMenu("http://example.com", mockMenItem)
        val command = captureCommands().value as Command.OpenInNewTab
        assertEquals("http://example.com", command.query)
    }

    @Test
    fun whenUserSelectsToShareLinkThenShareLinkCommandSent() {
        testee.userSharingLink("foo")
        val command = captureCommands().value as Command.ShareLink
        assertEquals("foo", command.url)
    }

    @Test
    fun whenOnSiteAndBrokenSiteSelectedThenBrokenSiteFeedbackCommandSentWithUrl() {
        testee.urlChanged("foo.com")
        testee.onBrokenSiteSelected()
        val command = captureCommands().value as Command.BrokenSiteFeedback
        assertEquals("foo.com", command.url)
    }

    @Test
    fun whenNoSiteAndBrokenSiteSelectedThenBrokenSiteFeedbackCommandSentWithoutUrl() {
        testee.onBrokenSiteSelected()
        val command = captureCommands().value as Command.BrokenSiteFeedback
        assertNull(command.url)
    }

    @Test
    fun whenUserSelectsToShareLinkWithNullUrlThenShareLinkCommandNotSent() {
        testee.userSharingLink(null)
        verify(mockCommandObserver, never()).onChanged(any())
    }

    @Test
    fun whenWebSessionRestoredThenGlobalLayoutSwitchedToShowingBrowser() {
        testee.onWebSessionRestored()
        assertFalse(globalLayoutViewState().isNewTabState)
    }

    @Test
    fun whenWebViewSessionIsToBeSavedThenUnderlyingSessionStoredCalled() {
        testee.saveWebViewState(null, "")
        verify(webViewSessionStorage).saveSession(anyOrNull(), anyString())
    }

    @Test
    fun whenRestoringWebViewSessionNotRestorableThenPreviousUrlLoaded() {
        whenever(mockOmnibarConverter.convertQueryToUrl("foo.com")).thenReturn("foo.com")
        whenever(webViewSessionStorage.restoreSession(anyOrNull(), anyString())).thenReturn(false)
        testee.restoreWebViewState(null, "foo.com")
        assertEquals("foo.com", testee.url.value)
    }

    @Test
    fun whenRestoringWebViewSessionNotRestorableAndNoPreviousUrlThenNoUrlLoaded() {
        whenever(webViewSessionStorage.restoreSession(anyOrNull(), anyString())).thenReturn(false)
        testee.restoreWebViewState(null, "")
        assertNull(testee.url.value)
    }

    @Test
    fun whenWebViewSessionRestorableThenSessionRestored() {
        whenever(webViewSessionStorage.restoreSession(anyOrNull(), anyString())).thenReturn(true)
        testee.restoreWebViewState(null, "")
        assertFalse(globalLayoutViewState().isNewTabState)
    }

    private fun captureCommands(): ArgumentCaptor<Command> {
        verify(mockCommandObserver, Mockito.atLeastOnce()).onChanged(commandCaptor.capture())
        return commandCaptor
    }

    private fun browserViewState() = testee.browserViewState.value!!
    private fun omnibarViewState() = testee.omnibarViewState.value!!
    private fun loadingViewState() = testee.loadingViewState.value!!
    private fun autoCompleteViewState() = testee.autoCompleteViewState.value!!
    private fun findInPageViewState() = testee.findInPageViewState.value!!
    private fun globalLayoutViewState() = testee.globalLayoutState.value!!
}
