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

import android.view.MenuItem
import android.view.View
import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.Observer
import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry.getInstrumentation
import com.duckduckgo.app.InstantSchedulersRule
import com.duckduckgo.app.autocomplete.api.AutoCompleteApi
import com.duckduckgo.app.bookmarks.db.BookmarkEntity
import com.duckduckgo.app.bookmarks.db.BookmarksDao
import com.duckduckgo.app.browser.BrowserTabViewModel.Command
import com.duckduckgo.app.browser.BrowserTabViewModel.Command.DisplayMessage
import com.duckduckgo.app.browser.BrowserTabViewModel.Command.Navigate
import com.duckduckgo.app.browser.LongPressHandler.RequiredAction.DownloadFile
import com.duckduckgo.app.browser.LongPressHandler.RequiredAction.OpenInNewTab
import com.duckduckgo.app.browser.addtohome.AddToHomeCapabilityDetector
import com.duckduckgo.app.browser.favicon.FaviconDownloader
import com.duckduckgo.app.browser.omnibar.OmnibarEntryConverter
import com.duckduckgo.app.browser.session.WebViewSessionStorage
import com.duckduckgo.app.cta.db.DismissedCtaDao
import com.duckduckgo.app.cta.ui.CtaViewModel
import com.duckduckgo.app.feedback.db.SurveyDao
import com.duckduckgo.app.global.db.AppConfigurationDao
import com.duckduckgo.app.global.db.AppConfigurationEntity
import com.duckduckgo.app.global.db.AppDatabase
import com.duckduckgo.app.global.install.AppInstallStore
import com.duckduckgo.app.global.model.SiteFactory
import com.duckduckgo.app.privacy.db.NetworkLeaderboardDao
import com.duckduckgo.app.privacy.db.NetworkLeaderboardEntry
import com.duckduckgo.app.privacy.db.SiteVisitedEntity
import com.duckduckgo.app.privacy.model.PrivacyPractices
import com.duckduckgo.app.privacy.store.PrevalenceStore
import com.duckduckgo.app.settings.db.SettingsDataStore
import com.duckduckgo.app.statistics.api.StatisticsUpdater
import com.duckduckgo.app.statistics.pixels.Pixel
import com.duckduckgo.app.tabs.model.TabRepository
import com.duckduckgo.app.trackerdetection.model.TrackerNetwork
import com.duckduckgo.app.trackerdetection.model.TrackerNetworks
import com.duckduckgo.app.trackerdetection.model.TrackingEvent
import com.duckduckgo.app.usage.search.SearchCountDao
import com.duckduckgo.app.widget.ui.WidgetCapabilities
import com.nhaarman.mockitokotlin2.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.*
import org.mockito.ArgumentMatchers.anyString
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import java.util.concurrent.TimeUnit

class BrowserTabViewModelTest {

    @get:Rule
    @Suppress("unused")
    var instantTaskExecutorRule = InstantTaskExecutorRule()

    @get:Rule
    @Suppress("unused")
    val schedulers = InstantSchedulersRule()

    @Mock
    private lateinit var mockPrevalenceStore: PrevalenceStore

    @Mock
    private lateinit var mockTrackerNetworks: TrackerNetworks

    @Mock
    private lateinit var mockNetworkLeaderboardDao: NetworkLeaderboardDao

    @Mock
    private lateinit var mockStatisticsUpdater: StatisticsUpdater

    @Mock
    private lateinit var mockCommandObserver: Observer<Command>

    @Mock
    private lateinit var mockPrivacyPractices: PrivacyPractices

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
    private lateinit var mockTabsRepository: TabRepository

    @Mock
    private lateinit var webViewSessionStorage: WebViewSessionStorage

    @Mock
    private lateinit var mockFaviconDownloader: FaviconDownloader

    @Mock
    private lateinit var mockAddToHomeCapabilityDetector: AddToHomeCapabilityDetector

    @Mock
    private lateinit var mockSurveyDao: SurveyDao

    @Mock
    private lateinit var mockDismissedCtaDao: DismissedCtaDao

    @Mock
    private lateinit var mockSearchCountDao: SearchCountDao

    @Mock
    private lateinit var mockAppInstallStore: AppInstallStore

    @Mock
    private lateinit var mockPixel: Pixel

    @Mock
    private lateinit var mockWidgetCapabilities: WidgetCapabilities

    private lateinit var ctaViewModel: CtaViewModel

    @Captor
    private lateinit var commandCaptor: ArgumentCaptor<Command>

    private lateinit var db: AppDatabase

    private lateinit var appConfigurationDao: AppConfigurationDao

    private lateinit var testee: BrowserTabViewModel

    @Before
    fun before() {
        MockitoAnnotations.initMocks(this)

        db = Room.inMemoryDatabaseBuilder(getInstrumentation().targetContext, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()

        appConfigurationDao = db.appConfigurationDao()

        ctaViewModel = CtaViewModel(
            mockAppInstallStore,
            mockPixel,
            mockSurveyDao,
            mockWidgetCapabilities,
            mockDismissedCtaDao
        )

        val siteFactory = SiteFactory(mockPrivacyPractices, mockTrackerNetworks, prevalenceStore = mockPrevalenceStore)
        whenever(mockTabsRepository.retrieveSiteData(any())).thenReturn(MutableLiveData())
        whenever(mockPrivacyPractices.privacyPracticesFor(any())).thenReturn(PrivacyPractices.UNKNOWN)
        whenever(mockAppInstallStore.installTimestamp).thenReturn(System.currentTimeMillis() - TimeUnit.DAYS.toMillis(1))

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
            longPressHandler = mockLongPressHandler,
            appConfigurationDao = appConfigurationDao,
            webViewSessionStorage = webViewSessionStorage,
            specialUrlDetector = SpecialUrlDetectorImpl(),
            faviconDownloader = mockFaviconDownloader,
            addToHomeCapabilityDetector = mockAddToHomeCapabilityDetector,
            ctaViewModel = ctaViewModel,
            searchCountDao = mockSearchCountDao
        )

        testee.loadData("abc", null)
        testee.command.observeForever(mockCommandObserver)

        whenever(mockOmnibarConverter.convertQueryToUrl(any())).thenReturn("duckduckgo.com")

    }

    @After
    fun after() {
        testee.onCleared()
        db.close()
        testee.command.removeObserver(mockCommandObserver)
    }

    @Test
    fun whenSearchUrlSharedThenAtbAndSourceParametersAreRemoved() {
        testee.userSharingLink("https://duckduckgo.com/?q=test&atb=v117-1&t=ddg_test")
        verify(mockCommandObserver, atLeastOnce()).onChanged(commandCaptor.capture())
        assertTrue(commandCaptor.lastValue is Command.ShareLink)

        val shareLink = commandCaptor.lastValue as Command.ShareLink
        assertEquals("https://duckduckgo.com/?q=test", shareLink.url)
    }

    @Test
    fun whenNonSearchUrlSharedThenUrlIsUnchanged() {
        val url = "https://duckduckgo.com/about?atb=v117-1&t=ddg_test"
        testee.userSharingLink(url)
        verify(mockCommandObserver, atLeastOnce()).onChanged(commandCaptor.capture())
        assertTrue(commandCaptor.lastValue is Command.ShareLink)

        val shareLink = commandCaptor.lastValue as Command.ShareLink
        assertEquals(url, shareLink.url)
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
        changeUrl("http://exmaple.com")
        testee.onViewVisible()
        verify(mockCommandObserver, atLeastOnce()).onChanged(commandCaptor.capture())
        assertTrue(commandCaptor.allValues.contains(Command.HideKeyboard))
    }

    @Test
    fun whenViewBecomesVisibleWithoutActiveSiteThenKeyboardShown() {
        changeUrl(null)
        testee.onViewVisible()
        verify(mockCommandObserver, atLeastOnce()).onChanged(commandCaptor.capture())
        assertTrue(commandCaptor.allValues.contains(Command.ShowKeyboard))
    }

    @Test
    fun whenSubmittedQueryHasWhitespaceItIsTrimmed() {
        testee.onUserSubmittedQuery(" nytimes.com ")
        assertEquals("nytimes.com", omnibarViewState().omnibarText)
    }

    @Test
    fun whenUrlPresentThenAddBookmarkButtonEnabled() {
        changeUrl("www.example.com")
        assertTrue(browserViewState().canAddBookmarks)
    }

    @Test
    fun whenNoUrlThenAddBookmarkButtonDisabled() {
        changeUrl(null)
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
    fun whenEmptyInputQueryThenQueryNavigateCommandNotSubmittedToActivityActivity() {
        testee.onUserSubmittedQuery("")
        verify(mockCommandObserver, never()).onChanged(commandCaptor.capture())
    }

    @Test
    fun whenBlankInputQueryThenQueryNavigateCommandNotSubmittedToActivity() {
        testee.onUserSubmittedQuery("     ")
        verify(mockCommandObserver, never()).onChanged(commandCaptor.capture())
    }

    @Test
    fun whenNonEmptyInputThenNavigateCommandSubmittedToActivity() {
        testee.onUserSubmittedQuery("foo")
        verify(mockCommandObserver, atLeastOnce()).onChanged(commandCaptor.capture())
        assertTrue(commandCaptor.lastValue is Navigate)
    }

    @Test
    fun whenViewModelNotifiedThatWebViewIsLoadingThenViewStateIsUpdated() {
        testee.loadingStarted("http://example.com")
        assertTrue(loadingViewState().isLoading)
    }

    @Test
    fun whenViewModelNotifiedThatWebViewHasFinishedLoadingThenViewStateIsUpdated() {
        testee.loadingFinished(null)
        assertFalse(loadingViewState().isLoading)
    }

    @Test
    fun whenLoadingFinishedAndInitialUrlNeverProgressedThenUrlUpdated() {
        val initialUrl = "http://foo.com/abc"
        val finalUrl = "http://bar.com/abc"
        testee.loadingStarted(initialUrl)
        testee.loadingFinished(finalUrl)
        assertEquals(finalUrl, testee.url)
    }

    @Test
    fun whenLoadingFinishedAndInitialUrlProgressedThenUrlNotUpdated() {
        val initialUrl = "http://foo.com/abc"
        val finalUrl = "http://foo.com/xyz"
        testee.loadingStarted(initialUrl)
        testee.progressChanged(initialUrl, 10)
        testee.loadingFinished(finalUrl)
        assertEquals(initialUrl, testee.url)
    }

    @Test
    fun whenLoadingFinishedWithUrlThenSiteVisitedEntryAddedToLeaderboardDao() {
        testee.loadingStarted("http://example.com/abc")
        testee.loadingFinished("http://example.com/abc")
        verify(mockNetworkLeaderboardDao).insert(SiteVisitedEntity("example.com"))
    }

    @Test
    fun whenLoadingFinishedWithUrlThenOmnibarTextUpdatedToMatch() {
        val exampleUrl = "http://example.com/abc"
        testee.loadingFinished(exampleUrl)
        assertEquals(exampleUrl, omnibarViewState().omnibarText)
    }

    @Test
    fun whenLoadingFinishedWithQueryUrlThenOmnibarTextUpdatedToShowQuery() {
        val queryUrl = "http://duckduckgo.com?q=test"
        testee.loadingFinished(queryUrl)
        assertEquals("test", omnibarViewState().omnibarText)
    }

    @Test
    fun whenLoadingFinishedWithNoUrlThenOmnibarTextUpdatedToMatch() {
        val exampleUrl = "http://example.com/abc"
        changeUrl(exampleUrl)
        testee.loadingFinished(null)
        assertEquals(exampleUrl, omnibarViewState().omnibarText)
    }

    @Test
    fun whenLoadingFinishedWithNoUrlThenSiteVisitedEntryNotAddedToLeaderboardDao() {
        testee.loadingFinished(null)
        verify(mockNetworkLeaderboardDao, never()).insert(SiteVisitedEntity("example.com"))
    }

    @Test
    fun whenTrackerDetectedThenSiteVisitedEntryAddedToLeaderboardDao() {
        testee.trackerDetected(TrackingEvent("http://example.com/abc", "http://tracker.com", TrackerNetwork("Network", "http:// netwotk.com"), true))
        verify(mockNetworkLeaderboardDao).insert(SiteVisitedEntity("example.com"))
    }

    @Test
    fun whenViewModelNotifiedThatUrlGotFocusThenViewStateIsUpdated() {
        testee.onOmnibarInputStateChanged("", true, hasQueryChanged = false)
        assertTrue(omnibarViewState().isEditing)
    }

    @Test
    fun whenViewModelNotifiedThatUrlLostFocusThenViewStateIsUpdated() {
        testee.onOmnibarInputStateChanged("", false, hasQueryChanged = false)
        assertFalse(omnibarViewState().isEditing)
    }

    @Test
    fun whenNoOmnibarTextEverEnteredThenViewStateHasEmptyString() {
        assertEquals("", omnibarViewState().omnibarText)
    }

    @Test
    fun whenUrlStartsLoadingWithProgressChangeThenUrlUpdated() {
        val url = "foo.com"
        testee.loadingStarted(url)
        testee.progressChanged(url, 10)
        assertEquals(url, testee.url)
    }

    @Test
    fun whenUrlStartsLoadingButProgressHasNotChangedThenUrlNotUpdated() {
        testee.loadingStarted("foo.com")
        assertNull(testee.url)
    }

    @Test
    fun whenUrlHasNotStartedLoadingAndProgressChangeThenUrlNotUpdated() {
        testee.progressChanged("foo.com", 10)
        assertNull(testee.url)
    }

    @Test
    fun whenUrlChangedThenViewStateIsUpdated() {
        changeUrl("duckduckgo.com")
        assertEquals("duckduckgo.com", omnibarViewState().omnibarText)
    }

    @Test
    fun whenUrlChangedWithDuckDuckGoUrlContainingQueryThenUrlRewrittenToContainQuery() {
        changeUrl("http://duckduckgo.com?q=test")
        assertEquals("test", omnibarViewState().omnibarText)
    }

    @Test
    fun whenUrlChangedWithDuckDuckGoUrlContainingQueryThenAtbRefreshed() {
        changeUrl("http://duckduckgo.com?q=test")
        verify(mockStatisticsUpdater).refreshRetentionAtb()
    }

    @Test
    fun whenUrlChangedWithDuckDuckGoUrlNotContainingQueryThenFullUrlShown() {
        changeUrl("http://duckduckgo.com")
        assertEquals("http://duckduckgo.com", omnibarViewState().omnibarText)
    }

    @Test
    fun whenUrlChangedWithNonDuckDuckGoUrlThenFullUrlShown() {
        changeUrl("http://example.com")
        assertEquals("http://example.com", omnibarViewState().omnibarText)
    }

    @Test
    fun whenViewModelGetsProgressUpdateThenViewStateIsUpdated() {
        testee.progressChanged("", 0)
        assertEquals(0, loadingViewState().progress)

        testee.progressChanged("", 50)
        assertEquals(50, loadingViewState().progress)

        testee.progressChanged("", 100)
        assertEquals(100, loadingViewState().progress)
    }

    @Test
    fun whenLoadingStartedThenPrivacyGradeIsCleared() {
        testee.loadingStarted("http://example.com")
        assertNull(testee.privacyGrade.value)
    }

    @Test
    fun whenUrlChangedThenPrivacyGradeIsReset() {
        val grade = testee.privacyGrade.value
        changeUrl("https://example.com")
        assertNotEquals(grade, testee.privacyGrade.value)
    }

    @Test
    fun whenEnoughTrackersDetectedThenPrivacyGradeIsUpdated() {
        val grade = testee.privacyGrade.value
        changeUrl("https://example.com")
        for (i in 1..10) {
            testee.trackerDetected(TrackingEvent("https://example.com", "", null, false))
        }
        assertNotEquals(grade, testee.privacyGrade.value)
    }

    @Test
    fun whenInitialisedThenPrivacyGradeIsNotShown() {
        assertFalse(browserViewState().showPrivacyGrade)
    }

    @Test
    fun whenUrlUpdatedAfterConfigDownloadThenPrivacyGradeIsShown() {
        testee.appConfigurationObserver.onChanged(AppConfigurationEntity(appConfigurationDownloaded = true))
        changeUrl("")
        assertTrue(browserViewState().showPrivacyGrade)
    }

    @Test
    fun whenUrlUpdatedBeforeConfigDownloadThenPrivacyGradeIsShown() {
        testee.appConfigurationObserver.onChanged(AppConfigurationEntity(appConfigurationDownloaded = false))
        changeUrl("")
        assertFalse(browserViewState().showPrivacyGrade)
    }

    @Test
    fun whenOmnibarInputDoesNotHaveFocusAndAppConfigDownloadedAndBrowserShownThenPrivacyGradeIsShown() {
        testee.onUserSubmittedQuery("foo")
        testee.appConfigurationObserver.onChanged(AppConfigurationEntity(appConfigurationDownloaded = true))
        testee.onOmnibarInputStateChanged(query = "", hasFocus = false, hasQueryChanged = false)
        assertTrue(browserViewState().showPrivacyGrade)
    }

    @Test
    fun whenOmnibarInputDoesNotHaveFocusAndAppConfigDownloadedButBrowserNotShownThenPrivacyGradeIsHidden() {
        testee.appConfigurationObserver.onChanged(AppConfigurationEntity(appConfigurationDownloaded = true))
        testee.onOmnibarInputStateChanged(query = "", hasFocus = false, hasQueryChanged = false)
        assertFalse(browserViewState().showPrivacyGrade)
    }

    @Test
    fun whenOmnibarInputDoesNotHaveFocusAndAppConfigNotDownloadedThenPrivacyGradeIsNotShown() {
        testee.appConfigurationObserver.onChanged(AppConfigurationEntity(appConfigurationDownloaded = false))
        testee.onOmnibarInputStateChanged("", false, hasQueryChanged = false)
        assertFalse(browserViewState().showPrivacyGrade)
    }

    @Test
    fun whenOmnibarInputHasFocusThenPrivacyGradeIsNotShown() {
        testee.onOmnibarInputStateChanged("", true, hasQueryChanged = false)
        assertFalse(browserViewState().showPrivacyGrade)
    }

    @Test
    fun whenInitialisedThenFireButtonIsShown() {
        assertTrue(browserViewState().showFireButton)
    }

    @Test
    fun whenOmnibarInputDoesNotHaveFocusAndHasQueryThenFireButtonIsShown() {
        testee.onOmnibarInputStateChanged("query", false, hasQueryChanged = false)
        assertTrue(browserViewState().showFireButton)
    }

    @Test
    fun whenOmnibarInputDoesNotHaveFocusOrQueryThenFireButtonIsShown() {
        testee.onOmnibarInputStateChanged("", false, hasQueryChanged = false)
        assertTrue(browserViewState().showFireButton)
    }

    @Test
    fun whenOmnibarInputHasFocusAndNoQueryThenFireButtonIsShown() {
        testee.onOmnibarInputStateChanged("", true, hasQueryChanged = false)
        assertTrue(browserViewState().showFireButton)
    }

    @Test
    fun whenOmnibarInputHasFocusAndQueryThenFireButtonIsHidden() {
        testee.onOmnibarInputStateChanged("query", true, hasQueryChanged = false)
        assertFalse(browserViewState().showFireButton)
    }

    @Test
    fun whenInitialisedThenTabsButtonIsShown() {
        assertTrue(browserViewState().showTabsButton)
    }

    @Test
    fun whenOmnibarInputDoesNotHaveFocusOrQueryThenTabsButtonIsShown() {
        testee.onOmnibarInputStateChanged("", false, hasQueryChanged = false)
        assertTrue(browserViewState().showTabsButton)
    }

    @Test
    fun whenOmnibarInputDoesNotHaveFocusAndHasQueryThenTabsButtonIsShown() {
        testee.onOmnibarInputStateChanged("query", false, hasQueryChanged = false)
        assertTrue(browserViewState().showTabsButton)
    }

    @Test
    fun whenOmnibarInputHasFocusAndNoQueryThenTabsButtonIsShown() {
        testee.onOmnibarInputStateChanged("", true, hasQueryChanged = false)
        assertTrue(browserViewState().showTabsButton)
    }

    @Test
    fun whenOmnibarInputHasFocusAndQueryThenTabsButtonIsHidden() {
        testee.onOmnibarInputStateChanged("query", true, hasQueryChanged = false)
        assertFalse(browserViewState().showTabsButton)
    }

    @Test
    fun whenInitialisedThenMenuButtonIsShown() {
        assertTrue(browserViewState().showMenuButton)
    }

    @Test
    fun whenOmnibarInputDoesNotHaveFocusOrQueryThenMenuButtonIsShown() {
        testee.onOmnibarInputStateChanged("", false, hasQueryChanged = false)
        assertTrue(browserViewState().showMenuButton)
    }

    @Test
    fun whenOmnibarInputDoesNotHaveFocusAndHasQueryThenMenuButtonIsShown() {
        testee.onOmnibarInputStateChanged("query", false, hasQueryChanged = false)
        assertTrue(browserViewState().showMenuButton)
    }

    @Test
    fun whenOmnibarInputHasFocusAndNoQueryThenMenuButtonIsShown() {
        testee.onOmnibarInputStateChanged("", true, hasQueryChanged = false)
        assertTrue(browserViewState().showMenuButton)
    }

    @Test
    fun whenOmnibarInputHasFocusAndQueryThenMenuButtonIsHidden() {
        testee.onOmnibarInputStateChanged("query", true, hasQueryChanged = false)
        assertFalse(browserViewState().showMenuButton)
    }

    @Test
    fun whenEnteringQueryWithAutoCompleteEnabledThenAutoCompleteSuggestionsShown() {
        doReturn(true).whenever(mockSettingsStore).autoCompleteSuggestionsEnabled
        testee.onOmnibarInputStateChanged("foo", true, hasQueryChanged = true)
        assertTrue(autoCompleteViewState().showSuggestions)
    }

    @Test
    fun whenOmnibarInputStateChangedWithAutoCompleteEnabledButNoQueryChangeThenAutoCompleteSuggestionsNotShown() {
        doReturn(true).whenever(mockSettingsStore).autoCompleteSuggestionsEnabled
        testee.onOmnibarInputStateChanged("foo", true, hasQueryChanged = false)
        assertFalse(autoCompleteViewState().showSuggestions)
    }

    @Test
    fun whenEnteringQueryWithAutoCompleteDisabledThenAutoCompleteSuggestionsNotShown() {
        doReturn(false).whenever(mockSettingsStore).autoCompleteSuggestionsEnabled
        testee.onOmnibarInputStateChanged("foo", true, hasQueryChanged = true)
        assertFalse(autoCompleteViewState().showSuggestions)
    }

    @Test
    fun whenEnteringEmptyQueryWithAutoCompleteEnabledThenAutoCompleteSuggestionsNotShown() {
        doReturn(true).whenever(mockSettingsStore).autoCompleteSuggestionsEnabled
        testee.onOmnibarInputStateChanged("", true, hasQueryChanged = true)
        assertFalse(autoCompleteViewState().showSuggestions)
    }

    @Test
    fun whenEnteringEmptyQueryWithAutoCompleteDisabledThenAutoCompleteSuggestionsNotShown() {
        doReturn(false).whenever(mockSettingsStore).autoCompleteSuggestionsEnabled
        testee.onOmnibarInputStateChanged("", true, hasQueryChanged = true)
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
        verify(mockCommandObserver, atLeastOnce()).onChanged(commandCaptor.capture())
        assertTrue(commandCaptor.allValues.any { it == Command.HideKeyboard })
    }

    @Test
    fun whenNotifiedEnteringFullScreenThenViewStateUpdatedWithFullScreenFlag() {
        val stubView = View(getInstrumentation().targetContext)
        testee.goFullScreen(stubView)
        assertTrue(browserViewState().isFullScreen)
    }

    @Test
    fun whenNotifiedEnteringFullScreenThenEnterFullScreenCommandIssued() {
        val stubView = View(getInstrumentation().targetContext)
        testee.goFullScreen(stubView)
        verify(mockCommandObserver, atLeastOnce()).onChanged(commandCaptor.capture())
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
        verify(mockCommandObserver, atLeastOnce()).onChanged(commandCaptor.capture())
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
        verify(mockCommandObserver, atLeastOnce()).onChanged(commandCaptor.capture())
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
        verify(mockCommandObserver, atLeastOnce()).onChanged(commandCaptor.capture())
        val ultimateCommand = commandCaptor.lastValue as Navigate
        assertEquals("http://example.com", ultimateCommand.url)
    }

    @Test
    fun whenUserSelectsDesktopSiteWhenNotOnMobileSpecificSiteThenUrlNotModified() {
        testee.desktopSiteModeToggled("http://example.com", desktopSiteRequested = true)
        verify(mockCommandObserver, atLeastOnce()).onChanged(commandCaptor.capture())
        val ultimateCommand = commandCaptor.lastValue
        assertTrue(ultimateCommand == Command.Refresh)
    }

    @Test
    fun whenUserSelectsMobileSiteWhenOnMobileSpecificSiteThenUrlNotModified() {
        testee.desktopSiteModeToggled("http://m.example.com", desktopSiteRequested = false)
        verify(mockCommandObserver, atLeastOnce()).onChanged(commandCaptor.capture())
        val ultimateCommand = commandCaptor.lastValue
        assertTrue(ultimateCommand == Command.Refresh)
    }

    @Test
    fun whenUserSelectsMobileSiteWhenNotOnMobileSpecificSiteThenUrlNotModified() {
        testee.desktopSiteModeToggled("http://example.com", desktopSiteRequested = false)
        verify(mockCommandObserver, atLeastOnce()).onChanged(commandCaptor.capture())
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
        changeUrl("foo.com")
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

        verify(mockCommandObserver, atLeastOnce()).onChanged(commandCaptor.capture())
        val command = commandCaptor.lastValue as Navigate
        assertEquals("foo.com", command.url)
    }

    @Test
    fun whenRestoringWebViewSessionNotRestorableAndNoPreviousUrlThenNoUrlLoaded() {
        whenever(webViewSessionStorage.restoreSession(anyOrNull(), anyString())).thenReturn(false)
        testee.restoreWebViewState(null, "")
        assertFalse(commandCaptor.allValues.any { it is Navigate })
    }

    @Test
    fun whenWebViewSessionRestorableThenSessionRestored() {
        whenever(webViewSessionStorage.restoreSession(anyOrNull(), anyString())).thenReturn(true)
        testee.restoreWebViewState(null, "")
        assertFalse(globalLayoutViewState().isNewTabState)
    }

    private fun changeUrl(url: String?) {
        testee.loadingStarted(url)
        testee.progressChanged(url, 100)
    }

    private fun captureCommands(): ArgumentCaptor<Command> {
        verify(mockCommandObserver, atLeastOnce()).onChanged(commandCaptor.capture())
        return commandCaptor
    }

    private fun browserViewState() = testee.browserViewState.value!!
    private fun omnibarViewState() = testee.omnibarViewState.value!!
    private fun loadingViewState() = testee.loadingViewState.value!!
    private fun autoCompleteViewState() = testee.autoCompleteViewState.value!!
    private fun findInPageViewState() = testee.findInPageViewState.value!!
    private fun globalLayoutViewState() = testee.globalLayoutState.value!!
}
