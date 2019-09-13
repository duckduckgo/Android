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

@file:Suppress("RemoveExplicitTypeArguments")

package com.duckduckgo.app.browser

import android.view.MenuItem
import android.view.View
import android.webkit.HttpAuthHandler
import android.webkit.WebView
import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.Observer
import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry.getInstrumentation
import com.duckduckgo.app.InstantSchedulersRule
import com.duckduckgo.app.autocomplete.api.AutoCompleteApi
import com.duckduckgo.app.autocomplete.api.AutoCompleteApi.AutoCompleteSuggestion.AutoCompleteBookmarkSuggestion
import com.duckduckgo.app.autocomplete.api.AutoCompleteApi.AutoCompleteSuggestion.AutoCompleteSearchSuggestion
import com.duckduckgo.app.bookmarks.db.BookmarkEntity
import com.duckduckgo.app.bookmarks.db.BookmarksDao
import com.duckduckgo.app.browser.BrowserTabViewModel.Command
import com.duckduckgo.app.browser.BrowserTabViewModel.Command.Navigate
import com.duckduckgo.app.browser.LongPressHandler.RequiredAction.DownloadFile
import com.duckduckgo.app.browser.LongPressHandler.RequiredAction.OpenInNewTab
import com.duckduckgo.app.browser.addtohome.AddToHomeCapabilityDetector
import com.duckduckgo.app.browser.favicon.FaviconDownloader
import com.duckduckgo.app.browser.model.BasicAuthenticationCredentials
import com.duckduckgo.app.browser.model.BasicAuthenticationRequest
import com.duckduckgo.app.browser.model.LongPressTarget
import com.duckduckgo.app.browser.omnibar.OmnibarEntryConverter
import com.duckduckgo.app.browser.session.WebViewSessionStorage
import com.duckduckgo.app.cta.db.DismissedCtaDao
import com.duckduckgo.app.cta.ui.CtaViewModel
import com.duckduckgo.app.global.db.AppConfigurationDao
import com.duckduckgo.app.global.db.AppConfigurationEntity
import com.duckduckgo.app.global.db.AppDatabase
import com.duckduckgo.app.global.install.AppInstallStore
import com.duckduckgo.app.global.model.SiteFactory
import com.duckduckgo.app.privacy.db.NetworkLeaderboardDao
import com.duckduckgo.app.privacy.model.PrivacyPractices
import com.duckduckgo.app.privacy.store.PrevalenceStore
import com.duckduckgo.app.settings.db.SettingsDataStore
import com.duckduckgo.app.statistics.Variant
import com.duckduckgo.app.statistics.VariantManager
import com.duckduckgo.app.statistics.VariantManager.Companion.DEFAULT_VARIANT
import com.duckduckgo.app.statistics.api.StatisticsUpdater
import com.duckduckgo.app.statistics.pixels.Pixel
import com.duckduckgo.app.survey.db.SurveyDao
import com.duckduckgo.app.tabs.model.TabRepository
import com.duckduckgo.app.trackerdetection.model.TrackerNetwork
import com.duckduckgo.app.trackerdetection.model.TrackerNetworks
import com.duckduckgo.app.trackerdetection.model.TrackingEvent
import com.duckduckgo.app.usage.search.SearchCountDao
import com.duckduckgo.app.widget.ui.WidgetCapabilities
import com.nhaarman.mockitokotlin2.*
import kotlinx.coroutines.runBlocking
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
    private lateinit var mockVariantManager: VariantManager

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
        runBlocking<Unit> {
            whenever(mockTabsRepository.retrieveSiteData(any())).thenReturn(MutableLiveData())
            whenever(mockPrivacyPractices.privacyPracticesFor(any())).thenReturn(PrivacyPractices.UNKNOWN)
            whenever(mockAppInstallStore.installTimestamp).thenReturn(System.currentTimeMillis() - TimeUnit.DAYS.toMillis(1))
        }

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
            searchCountDao = mockSearchCountDao,
            pixel = mockPixel,
            variantManager = mockVariantManager
        )

        testee.loadData("abc", null, false)
        testee.command.observeForever(mockCommandObserver)

        whenever(mockOmnibarConverter.convertQueryToUrl(any())).thenReturn("duckduckgo.com")
        whenever(mockVariantManager.getVariant()).thenReturn(DEFAULT_VARIANT)
    }

    @After
    fun after() {
        testee.onCleared()
        db.close()
        testee.command.removeObserver(mockCommandObserver)
    }

    @Test
    fun whenSearchUrlSharedThenAtbAndSourceParametersAreRemoved() {
        loadUrl("https://duckduckgo.com/?q=test&atb=v117-1&t=ddg_test")
        testee.onShareSelected()
        verify(mockCommandObserver, atLeastOnce()).onChanged(commandCaptor.capture())
        assertTrue(commandCaptor.lastValue is Command.ShareLink)

        val shareLink = commandCaptor.lastValue as Command.ShareLink
        assertEquals("https://duckduckgo.com/?q=test", shareLink.url)
    }

    @Test
    fun whenNonSearchUrlSharedThenUrlIsUnchanged() {
        val url = "https://duckduckgo.com/about?atb=v117-1&t=ddg_test"
        loadUrl(url)
        testee.onShareSelected()
        verify(mockCommandObserver, atLeastOnce()).onChanged(commandCaptor.capture())
        assertTrue(commandCaptor.lastValue is Command.ShareLink)

        val shareLink = commandCaptor.lastValue as Command.ShareLink
        assertEquals(url, shareLink.url)
    }

    @Test
    fun whenOpenInNewBackgroundRequestedThenTabRepositoryUpdatedAndCommandIssued() = runBlocking<Unit> {
        val url = "http://www.example.com"
        testee.openInNewBackgroundTab(url)

        verify(mockCommandObserver, atLeastOnce()).onChanged(commandCaptor.capture())
        assertTrue(commandCaptor.lastValue is Command.OpenInNewBackgroundTab)

        verify(mockTabsRepository).addNewTabAfterExistingTab(url, "abc")
    }

    @Test
    fun whenViewBecomesVisibleAndBrowserShowingThenKeyboardHidden() {
        setBrowserShowing(true)
        testee.onViewVisible()
        verify(mockCommandObserver, atLeastOnce()).onChanged(commandCaptor.capture())
        assertTrue(commandCaptor.allValues.contains(Command.HideKeyboard))
    }

    @Test
    fun whenViewBecomesVisibleAndHomeShowingThenKeyboardShown() {
        setBrowserShowing(false)
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
    fun whenBrowsingAndUrlPresentThenAddBookmarkButtonEnabled() {
        loadUrl("www.example.com", isBrowserShowing = true)
        assertTrue(browserViewState().canAddBookmarks)
    }

    @Test
    fun whenBrowsingAndNoUrlThenAddBookmarkButtonDisabled() {
        loadUrl(null, isBrowserShowing = true)
        assertFalse(browserViewState().canAddBookmarks)
    }

    @Test
    fun whenNotBrowsingAndUrlPresentThenAddBookmarkButtonDisabled() {
        loadUrl("www.example.com", isBrowserShowing = false)
        assertFalse(browserViewState().canAddBookmarks)
    }

    @Test
    fun whenBookmarkEditedThenDaoIsUpdated() = runBlocking<Unit> {
        testee.editBookmark(0, "A title", "www.example.com")
        verify(bookmarksDao).update(BookmarkEntity(title = "A title", url = "www.example.com"))
    }

    @Test
    fun whenBookmarkAddedThenDaoIsUpdatedAndUserNotified() = runBlocking<Unit> {
        loadUrl("www.example.com", "A title")

        testee.onBookmarkAddRequested()
        verify(bookmarksDao).insert(BookmarkEntity(title = "A title", url = "www.example.com"))
        verify(mockCommandObserver, atLeastOnce()).onChanged(commandCaptor.capture())
        assertTrue(commandCaptor.lastValue is Command.ShowBookmarkAddedConfirmation)
    }

    @Test
    fun whenTrackerDetectedThenNetworkLeaderboardUpdated() {
        val event = TrackingEvent("http://www.example.com", "http://www.tracker.com/tracker.js", TrackerNetwork("Network1", "www.tracker.com"), false)
        testee.trackerDetected(event)
        verify(mockNetworkLeaderboardDao).incrementNetworkCount("Network1")
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
    fun whenBrowsingAndUrlLoadedThenSiteVisitedEntryAddedToLeaderboardDao() {
        loadUrl("http://example.com/abc", isBrowserShowing = true)
        verify(mockNetworkLeaderboardDao).incrementSitesVisited()
    }

    @Test
    fun whenBrowsingAndUrlClearedThenSiteVisitedEntryNotAddedToLeaderboardDao() {
        loadUrl(null, isBrowserShowing = true)
        verify(mockNetworkLeaderboardDao, never()).incrementSitesVisited()
    }

    @Test
    fun whenNotBrowsingAndUrlLoadedThenSiteVisitedEntryNotAddedToLeaderboardDao() {
        loadUrl("http://example.com/abc", isBrowserShowing = false)
        verify(mockNetworkLeaderboardDao, never()).incrementSitesVisited()
    }

    @Test
    fun whenBrowsingAndUrlLoadedThenUrlTitleAndOmnibarTextUpdatedToMatch() {
        val exampleUrl = "http://example.com/abc"
        val exampleTitle = "Title"
        loadUrl(exampleUrl, title = exampleTitle, isBrowserShowing = true)
        assertEquals(exampleUrl, testee.url)
        assertEquals(exampleUrl, omnibarViewState().omnibarText)
        assertEquals(exampleTitle, testee.title)
    }

    @Test
    fun whenNotBrowsingAndUrlLoadedThenUrlAndTitleNullAndOmnibarTextRemainsBlank() {
        loadUrl("http://example.com/abc", "Title", isBrowserShowing = false)
        assertEquals(null, testee.url)
        assertEquals("", omnibarViewState().omnibarText)
        assertEquals(null, testee.title)
    }

    @Test
    fun whenBrowsingAndUrlIsUpdatedThenUrlAndOmnibarTextUpdatedToMatch() {
        val originalUrl = "http://example.com/"
        val currentUrl = "http://example.com/current"
        loadUrl(originalUrl, isBrowserShowing = true)
        updateUrl(originalUrl, currentUrl, true)
        assertEquals(currentUrl, testee.url)
        assertEquals(currentUrl, omnibarViewState().omnibarText)
    }

    @Test
    fun whenNotBrowsingAndUrlIsUpdatedThenUrlAndOmnibarTextRemainUnchanged() {
        val originalUrl = "http://example.com/"
        val currentUrl = "http://example.com/current"
        loadUrl(originalUrl, isBrowserShowing = true)
        updateUrl(originalUrl, currentUrl, false)
        assertEquals(originalUrl, testee.url)
        assertEquals(originalUrl, omnibarViewState().omnibarText)
    }

    @Test
    fun whenBrowsingAndUrlLoadedWithQueryUrlThenOmnibarTextUpdatedToShowQuery() {
        val queryUrl = "http://duckduckgo.com?q=test"
        loadUrl(queryUrl, isBrowserShowing = true)
        assertEquals("test", omnibarViewState().omnibarText)
    }

    @Test
    fun whenNotBrowsingAndUrlLoadedWithQueryUrlThenOmnibarTextextRemainsBlank() {
        loadUrl("http://duckduckgo.com?q=test", isBrowserShowing = false)
        assertEquals("", omnibarViewState().omnibarText)
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
    fun whenDuckDuckGoUrlContainingQueryLoadedThenUrlRewrittenToContainQuery() {
        loadUrl("http://duckduckgo.com?q=test")
        assertEquals("test", omnibarViewState().omnibarText)
    }

    @Test
    fun whenDuckDuckGoUrlContainingQueryLoadedThenAtbRefreshed() {
        loadUrl("http://duckduckgo.com?q=test")
        verify(mockStatisticsUpdater).refreshSearchRetentionAtb()
    }

    @Test
    fun whenDuckDuckGoUrlNotContainingQueryLoadedThenFullUrlShown() {
        loadUrl("http://duckduckgo.com")
        assertEquals("http://duckduckgo.com", omnibarViewState().omnibarText)
    }

    @Test
    fun whenNonDuckDuckGoUrlLoadedThenFullUrlShown() {
        loadUrl("http://example.com")
        assertEquals("http://example.com", omnibarViewState().omnibarText)
    }

    @Test
    fun whenBrowsingAndViewModelGetsProgressUpdateThenViewStateIsUpdated() {
        setBrowserShowing(true)

        testee.progressChanged(0)
        assertEquals(0, loadingViewState().progress)
        assertEquals(true, loadingViewState().isLoading)

        testee.progressChanged(50)
        assertEquals(50, loadingViewState().progress)
        assertEquals(true, loadingViewState().isLoading)

        testee.progressChanged(100)
        assertEquals(100, loadingViewState().progress)
        assertEquals(false, loadingViewState().isLoading)
    }

    @Test
    fun whenNotBrowserAndViewModelGetsProgressUpdateThenViewStateIsNotUpdated() {
        setBrowserShowing(false)
        testee.progressChanged(10)
        assertEquals(0, loadingViewState().progress)
        assertEquals(false, loadingViewState().isLoading)
    }

    @Test
    fun whenProgressChangesToFinishedAndImprovedTabUxVariantActiveThenWebViewPreviewGenerated() {
        whenever(mockVariantManager.getVariant()).thenReturn(
            Variant("mx", 1.0, listOf(VariantManager.VariantFeature.TabSwitcherGrid), filterBy = { true })
        )
        updateUrl("https://example.com", "https://example.com", true)
        testee.progressChanged(100)
        val command = captureCommands().lastValue as Command.GenerateWebViewPreviewImage
        assertFalse(command.forceImmediate)
    }

    @Test
    fun whenProgressChangesToFinishedAndImprovedTabUxVariantNotActiveThenWebViewPreviewNotGenerated() {
        whenever(mockVariantManager.getVariant()).thenReturn(DEFAULT_VARIANT)
        updateUrl("https://example.com", "https://example.com", true)
        testee.progressChanged(100)
        verify(mockCommandObserver, never()).onChanged(any<Command.GenerateWebViewPreviewImage>())
    }

    @Test
    fun whenUrlClearedThenPrivacyGradeIsCleared() = runBlocking<Unit> {
        loadUrl("https://duckduckgo.com")
        assertNotNull(testee.privacyGrade.value)
        loadUrl(null)
        assertNull(testee.privacyGrade.value)
    }

    @Test
    fun whenUrlLoadedThenPrivacyGradeIsReset() = runBlocking<Unit> {
        loadUrl("https://duckduckgo.com")
        assertNotNull(testee.privacyGrade.value)
    }

    @Test
    fun whenEnoughTrackersDetectedThenPrivacyGradeIsUpdated() {
        val grade = testee.privacyGrade.value
        loadUrl("https://example.com")
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
        loadUrl("")
        assertTrue(browserViewState().showPrivacyGrade)
    }

    @Test
    fun whenUrlUpdatedBeforeConfigDownloadThenPrivacyGradeIsShown() {
        testee.appConfigurationObserver.onChanged(AppConfigurationEntity(appConfigurationDownloaded = false))
        loadUrl("")
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
        whenever(mockLongPressHandler.userSelectedMenuItem(any(), any()))
            .thenReturn(DownloadFile("example.com"))

        val mockMenuItem: MenuItem = mock()
        val longPressTarget = LongPressTarget(url = "example.com", type = WebView.HitTestResult.SRC_ANCHOR_TYPE)
        testee.userSelectedItemFromLongPressMenu(longPressTarget, mockMenuItem)
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
        loadUrl("http://example.com")
        testee.onDesktopSiteModeToggled(true)
        verify(mockCommandObserver, atLeastOnce()).onChanged(commandCaptor.capture())
        assertTrue(browserViewState().isDesktopBrowsingMode)
    }

    @Test
    fun whenUserSelectsMobileSiteThenMobileModeStateUpdated() {
        loadUrl("http://example.com")
        testee.onDesktopSiteModeToggled(false)
        assertFalse(browserViewState().isDesktopBrowsingMode)
    }

    @Test
    fun whenHomeShowingAndNeverBrowsedThenForwardButtonInactive() {
        setupNavigation(isBrowsing = false)
        assertFalse(browserViewState().canGoForward)
    }

    @Test
    fun whenHomeShowingByPressingBackOnBrowserThenForwardButtonActive() {
        setupNavigation(isBrowsing = true)
        testee.onUserPressedBack()
        assertFalse(browserViewState().browserShowing)
        assertTrue(browserViewState().canGoForward)
    }

    @Test
    fun whenBrowserShowingAndCanGoForwardThenForwardButtonActive() {
        setupNavigation(isBrowsing = true, canGoForward = true)
        assertTrue(browserViewState().canGoForward)
    }

    @Test
    fun whenBrowserShowingAndCannotGoForwardThenForwardButtonInactive() {
        setupNavigation(isBrowsing = true, canGoForward = false)
        assertFalse(browserViewState().canGoForward)
    }

    @Test
    fun whenHomeShowingThenBackButtonInactiveEvenIfBrowserCanGoBack() {
        setupNavigation(isBrowsing = false, canGoBack = false)
        assertFalse(browserViewState().canGoBack)

        setupNavigation(isBrowsing = false, canGoBack = true)
        assertFalse(browserViewState().canGoBack)
    }

    @Test
    fun whenBrowserShowingAndCanGoBackThenBackButtonActive() {
        setupNavigation(isBrowsing = true, canGoBack = true)
        assertTrue(browserViewState().canGoBack)
    }

    @Test
    fun whenBrowserShowingAndCannotGoBackAndSkipHomeThenBackButtonInactive() {
        setupNavigation(skipHome = true, isBrowsing = true, canGoBack = false)
        assertFalse(browserViewState().canGoBack)
    }

    @Test
    fun whenBrowserShowingAndCannotGoBackAndNotSkipHomeThenBackButtonActive() {
        setupNavigation(skipHome = false, isBrowsing = true, canGoBack = false)
        assertTrue(browserViewState().canGoBack)
    }

    @Test
    fun whenUserBrowsingPressesForwardThenNavigatesForward() {
        setBrowserShowing(true)
        testee.onUserPressedForward()
        assertTrue(captureCommands().lastValue == Command.NavigateForward)
    }

    @Test
    fun whenUserOnHomePressesForwardThenBrowserShownAndPageRefreshed() {
        setBrowserShowing(false)
        testee.onUserPressedForward()
        assertTrue(browserViewState().browserShowing)
        assertTrue(captureCommands().lastValue == Command.Refresh)
    }

    @Test
    fun whenUserBrowsingPressesBackAndBrowserCanGoBackThenNavigatesToPreviousPageAndHandledTrue() {
        setupNavigation(isBrowsing = true, canGoBack = true, stepsToPreviousPage = 2)
        assertTrue(testee.onUserPressedBack())

        val backCommand = captureCommands().lastValue as Command.NavigateBack
        assertNotNull(backCommand)
        assertEquals(2, backCommand.steps)
    }

    @Test
    fun whenUserBrowsingPressesBackAndBrowserCannotGoBackAndHomeNotSkippedThenHomeShownAndHandledTrue() {
        setupNavigation(skipHome = false, isBrowsing = true, canGoBack = false)
        assertTrue(testee.onUserPressedBack())
        assertFalse(browserViewState().browserShowing)
        assertEquals("", omnibarViewState().omnibarText)
    }

    @Test
    fun whenUserBrowsingPressesBackAndBrowserCannotGoBackAndHomeIsSkippedThenHandledFalse() {
        setupNavigation(skipHome = true, isBrowsing = false, canGoBack = false)
        assertFalse(testee.onUserPressedBack())
    }

    @Test
    fun whenUserOnHomePressesBackThenReturnsHandledFalse() {
        setBrowserShowing(false)
        assertFalse(testee.onUserPressedBack())
    }

    @Test
    fun whenUserSelectsDesktopSiteWhenOnMobileSpecificSiteThenUrlModified() {
        loadUrl("http://m.example.com")
        testee.onDesktopSiteModeToggled(true)
        verify(mockCommandObserver, atLeastOnce()).onChanged(commandCaptor.capture())
        val ultimateCommand = commandCaptor.lastValue as Navigate
        assertEquals("http://example.com", ultimateCommand.url)
    }

    @Test
    fun whenUserSelectsDesktopSiteWhenNotOnMobileSpecificSiteThenUrlNotModified() {
        loadUrl("http://example.com")
        testee.onDesktopSiteModeToggled(true)
        verify(mockCommandObserver, atLeastOnce()).onChanged(commandCaptor.capture())
        val ultimateCommand = commandCaptor.lastValue
        assertTrue(ultimateCommand == Command.Refresh)
    }

    @Test
    fun whenUserSelectsMobileSiteWhenOnMobileSpecificSiteThenUrlNotModified() {
        loadUrl("http://m.example.com")
        testee.onDesktopSiteModeToggled(false)
        verify(mockCommandObserver, atLeastOnce()).onChanged(commandCaptor.capture())
        val ultimateCommand = commandCaptor.lastValue
        assertTrue(ultimateCommand == Command.Refresh)
    }

    @Test
    fun whenUserSelectsMobileSiteWhenNotOnMobileSpecificSiteThenUrlNotModified() {
        loadUrl("http://example.com")
        testee.onDesktopSiteModeToggled(false)
        verify(mockCommandObserver, atLeastOnce()).onChanged(commandCaptor.capture())
        val ultimateCommand = commandCaptor.lastValue
        assertTrue(ultimateCommand == Command.Refresh)
    }

    @Test
    fun whenUserSelectsOpenTabThenTabCommandSent() {
        whenever(mockLongPressHandler.userSelectedMenuItem(any(), any())).thenReturn(OpenInNewTab("http://example.com"))
        val mockMenItem: MenuItem = mock()
        val longPressTarget = LongPressTarget(url = "http://example.com", type = WebView.HitTestResult.SRC_ANCHOR_TYPE)
        testee.userSelectedItemFromLongPressMenu(longPressTarget, mockMenItem)
        val command = captureCommands().value as Command.OpenInNewTab
        assertEquals("http://example.com", command.query)
    }

    @Test
    fun whenSiteLoadedAndUserSelectsToAddBookmarkThenAddBookmarkCommandSentWithUrlAndTitle() = runBlocking<Unit> {
        loadUrl("foo.com")
        testee.titleReceived("Foo Title")
        testee.onBookmarkAddRequested()
        val command = captureCommands().value as Command.ShowBookmarkAddedConfirmation
        assertEquals("foo.com", command.url)
        assertEquals("Foo Title", command.title)
    }

    @Test
    fun whenNoSiteAndUserSelectsToAddBookmarkThenBookmarkAddedWithBlankTitleAndUrl() = runBlocking<Unit> {
        whenever(bookmarksDao.insert(any())).thenReturn(1)
        testee.onBookmarkAddRequested()
        verify(bookmarksDao).insert(BookmarkEntity(title = "", url = ""))
        val command = captureCommands().value as Command.ShowBookmarkAddedConfirmation
        assertEquals(1, command.bookmarkId)
        assertEquals("", command.title)
        assertEquals("", command.url)
    }

    @Test
    fun whenUserSelectsToShareLinkThenShareLinkCommandSent() {
        loadUrl("foo.com")
        testee.onShareSelected()
        val command = captureCommands().value as Command.ShareLink
        assertEquals("foo.com", command.url)
    }

    @Test
    fun whenOnSiteAndBrokenSiteSelectedThenBrokenSiteFeedbackCommandSentWithUrl() = runBlocking<Unit> {
        loadUrl("foo.com", isBrowserShowing = true)
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
        loadUrl(null)
        testee.onShareSelected()
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

    @Test
    fun whenUrlNullThenSetBrowserNotShowing() {
        testee.loadData("id", null, false)
        testee.determineShowBrowser()
        assertEquals(false, testee.browserViewState.value?.browserShowing)
    }

    @Test
    fun whenUrlBlankThenSetBrowserNotShowing() {
        testee.loadData("id", "  ", false)
        testee.determineShowBrowser()
        assertEquals(false, testee.browserViewState.value?.browserShowing)
    }

    @Test
    fun whenUrlPresentThenSetBrowserShowing() {
        testee.loadData("id", "https://example.com", false)
        testee.determineShowBrowser()
        assertEquals(true, testee.browserViewState.value?.browserShowing)
    }

    @Test
    fun whenAuthenticationIsRequiredThenRequiresAuthenticationCommandSent() {
        val mockHandler = mock<HttpAuthHandler>()
        val siteURL = "http://example.com/requires-auth"
        val authenticationRequest = BasicAuthenticationRequest(mockHandler, "example.com", "test realm", siteURL)
        testee.requiresAuthentication(authenticationRequest)
        verify(mockCommandObserver, atLeastOnce()).onChanged(commandCaptor.capture())

        val command = commandCaptor.lastValue
        assertTrue(command is Command.RequiresAuthentication)

        val requiresAuthCommand = command as Command.RequiresAuthentication
        assertSame(authenticationRequest, requiresAuthCommand.request)
    }

    @Test
    fun whenHandleAuthenticationThenHandlerCalledWithParameters() {
        val mockHandler = mock<HttpAuthHandler>()
        val username = "user"
        val password = "password"
        val authenticationRequest = BasicAuthenticationRequest(mockHandler, "example.com", "test realm", "")
        val credentials = BasicAuthenticationCredentials(username = username, password = password)
        testee.handleAuthentication(request = authenticationRequest, credentials = credentials)

        verify(mockHandler, atLeastOnce()).proceed(username, password)
    }

    @Test
    fun whenBookmarkSuggestionSubmittedThenAutoCompleteBookmarkSelectionPixelSent() = runBlocking {
        whenever(bookmarksDao.hasBookmarks()).thenReturn(true)
        testee.autoCompleteViewState.value = autoCompleteViewState().copy(searchResults = AutoCompleteApi.AutoCompleteResult("", emptyList(), true))
        testee.fireAutocompletePixel(AutoCompleteBookmarkSuggestion("example", "Example", "https://example.com"))

        verify(mockPixel).fire(Pixel.PixelName.AUTOCOMPLETE_BOOKMARK_SELECTION, pixelParams(showedBookmarks = true, bookmarkCapable = true))
    }

    @Test
    fun whenSearchSuggestionSubmittedWithBookmarksThenAutoCompleteSearchSelectionPixelSent() = runBlocking {
        whenever(bookmarksDao.hasBookmarks()).thenReturn(true)
        testee.autoCompleteViewState.value = autoCompleteViewState().copy(searchResults = AutoCompleteApi.AutoCompleteResult("", emptyList(), true))
        testee.fireAutocompletePixel(AutoCompleteSearchSuggestion("example", false))

        verify(mockPixel).fire(Pixel.PixelName.AUTOCOMPLETE_SEARCH_SELECTION, pixelParams(showedBookmarks = true, bookmarkCapable = true))
    }

    @Test
    fun whenSearchSuggestionSubmittedWithoutBookmarksThenAutoCompleteSearchSelectionPixelSent() = runBlocking {
        whenever(bookmarksDao.hasBookmarks()).thenReturn(false)
        testee.autoCompleteViewState.value = autoCompleteViewState().copy(searchResults = AutoCompleteApi.AutoCompleteResult("", emptyList(), false))
        testee.fireAutocompletePixel(AutoCompleteSearchSuggestion("example", false))

        verify(mockPixel).fire(Pixel.PixelName.AUTOCOMPLETE_SEARCH_SELECTION, pixelParams(showedBookmarks = false, bookmarkCapable = false))
    }

    @Test
    fun whenUserSelectToEditQueryThenMoveCaretToTheEnd() {
        testee.onUserSelectedToEditQuery("foo")
        assertTrue(omnibarViewState().shouldMoveCaretToEnd)
    }

    @Test
    fun whenUserSubmitsQueryThenCaretDoesNotMoveToTheEnd() {
        testee.onUserSubmittedQuery("foo")
        assertFalse(omnibarViewState().shouldMoveCaretToEnd)
    }

    private fun pixelParams(showedBookmarks: Boolean, bookmarkCapable: Boolean) = mapOf(
        Pixel.PixelParameter.SHOWED_BOOKMARKS to showedBookmarks.toString(),
        Pixel.PixelParameter.BOOKMARK_CAPABLE to bookmarkCapable.toString()
    )

    private fun setBrowserShowing(isBrowsing: Boolean) {
        testee.browserViewState.value = browserViewState().copy(browserShowing = isBrowsing)
    }

    private fun loadUrl(url: String?, title: String? = null, isBrowserShowing: Boolean = true) {
        setBrowserShowing(isBrowserShowing)
        testee.navigationStateChanged(buildWebNavigation(originalUrl = url, currentUrl = url, title = title))
    }

    private fun updateUrl(originalUrl: String?, currentUrl: String?, isBrowserShowing: Boolean) {
        setBrowserShowing(isBrowserShowing)
        testee.navigationStateChanged(buildWebNavigation(originalUrl = originalUrl, currentUrl = currentUrl))
    }

    private fun setupNavigation(
        skipHome: Boolean = false,
        isBrowsing: Boolean,
        canGoForward: Boolean = false,
        canGoBack: Boolean = false,
        stepsToPreviousPage: Int = 0
    ) {
        testee.skipHome = skipHome
        setBrowserShowing(isBrowsing)
        val nav = buildWebNavigation(canGoForward = canGoForward, canGoBack = canGoBack, stepsToPreviousPage = stepsToPreviousPage)
        testee.navigationStateChanged(nav)
    }

    private fun captureCommands(): ArgumentCaptor<Command> {
        verify(mockCommandObserver, atLeastOnce()).onChanged(commandCaptor.capture())
        return commandCaptor
    }

    private fun buildWebNavigation(
        currentUrl: String? = null,
        originalUrl: String? = null,
        title: String? = null,
        canGoForward: Boolean = false,
        canGoBack: Boolean = false,
        stepsToPreviousPage: Int = 0
    ): WebNavigationState {
        val nav: WebNavigationState = mock()
        whenever(nav.originalUrl).thenReturn(originalUrl)
        whenever(nav.currentUrl).thenReturn(currentUrl)
        whenever(nav.title).thenReturn(title)
        whenever(nav.canGoForward).thenReturn(canGoForward)
        whenever(nav.canGoBack).thenReturn(canGoBack)
        whenever(nav.stepsToPreviousPage).thenReturn(stepsToPreviousPage)
        return nav
    }

    private fun browserViewState() = testee.browserViewState.value!!
    private fun omnibarViewState() = testee.omnibarViewState.value!!
    private fun loadingViewState() = testee.loadingViewState.value!!
    private fun autoCompleteViewState() = testee.autoCompleteViewState.value!!
    private fun findInPageViewState() = testee.findInPageViewState.value!!
    private fun globalLayoutViewState() = testee.globalLayoutState.value!!
}