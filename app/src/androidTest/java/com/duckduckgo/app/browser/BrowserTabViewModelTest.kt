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
import com.duckduckgo.app.CoroutineTestRule
import com.duckduckgo.app.InstantSchedulersRule
import com.duckduckgo.app.ValueCaptorObserver
import com.duckduckgo.app.autocomplete.api.AutoComplete.AutoCompleteResult
import com.duckduckgo.app.autocomplete.api.AutoComplete.AutoCompleteSuggestion.AutoCompleteBookmarkSuggestion
import com.duckduckgo.app.autocomplete.api.AutoComplete.AutoCompleteSuggestion.AutoCompleteSearchSuggestion
import com.duckduckgo.app.autocomplete.api.AutoCompleteApi
import com.duckduckgo.app.autocomplete.api.AutoCompleteService
import com.duckduckgo.app.bookmarks.db.BookmarkEntity
import com.duckduckgo.app.bookmarks.db.BookmarksDao
import com.duckduckgo.app.browser.BrowserTabViewModel.Command
import com.duckduckgo.app.browser.BrowserTabViewModel.Command.Navigate
import com.duckduckgo.app.browser.LongPressHandler.RequiredAction.DownloadFile
import com.duckduckgo.app.browser.LongPressHandler.RequiredAction.OpenInNewTab
import com.duckduckgo.app.browser.addtohome.AddToHomeCapabilityDetector
import com.duckduckgo.app.browser.defaultbrowsing.DefaultBrowserDetector
import com.duckduckgo.app.browser.favicon.FaviconDownloader
import com.duckduckgo.app.browser.model.BasicAuthenticationCredentials
import com.duckduckgo.app.browser.model.BasicAuthenticationRequest
import com.duckduckgo.app.browser.model.LongPressTarget
import com.duckduckgo.app.browser.omnibar.OmnibarEntryConverter
import com.duckduckgo.app.browser.session.WebViewSessionStorage
import com.duckduckgo.app.cta.db.DismissedCtaDao
import com.duckduckgo.app.cta.model.DismissedCta
import com.duckduckgo.app.cta.ui.CtaViewModel
import com.duckduckgo.app.cta.ui.DaxBubbleCta
import com.duckduckgo.app.cta.ui.DaxDialogCta
import com.duckduckgo.app.cta.ui.HomePanelCta
import com.duckduckgo.app.cta.ui.HomeTopPanelCta
import com.duckduckgo.app.global.db.AppDatabase
import com.duckduckgo.app.global.install.AppInstallStore
import com.duckduckgo.app.global.model.SiteFactory
import com.duckduckgo.app.onboarding.store.OnboardingStore
import com.duckduckgo.app.privacy.db.NetworkLeaderboardDao
import com.duckduckgo.app.privacy.model.PrivacyPractices
import com.duckduckgo.app.privacy.model.TestEntity
import com.duckduckgo.app.privacy.store.PrivacySettingsStore
import com.duckduckgo.app.settings.db.SettingsDataStore
import com.duckduckgo.app.statistics.Variant
import com.duckduckgo.app.statistics.VariantManager
import com.duckduckgo.app.statistics.VariantManager.Companion.DEFAULT_VARIANT
import com.duckduckgo.app.statistics.api.StatisticsUpdater
import com.duckduckgo.app.statistics.pixels.Pixel
import com.duckduckgo.app.surrogates.SurrogateResponse
import com.duckduckgo.app.survey.db.SurveyDao
import com.duckduckgo.app.survey.model.Survey
import com.duckduckgo.app.tabs.model.TabEntity
import com.duckduckgo.app.tabs.model.TabRepository
import com.duckduckgo.app.trackerdetection.EntityLookup
import com.duckduckgo.app.trackerdetection.model.TrackingEvent
import com.duckduckgo.app.usage.search.SearchCountDao
import com.duckduckgo.app.widget.ui.WidgetCapabilities
import com.nhaarman.mockitokotlin2.*
import io.reactivex.Observable
import io.reactivex.Single
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.TestCoroutineScope
import kotlinx.coroutines.test.runBlockingTest
import kotlinx.coroutines.withContext
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

@ExperimentalCoroutinesApi
class BrowserTabViewModelTest {

    @get:Rule
    var instantTaskExecutorRule = InstantTaskExecutorRule()

    @get:Rule
    val schedulers = InstantSchedulersRule()

    @ExperimentalCoroutinesApi
    @get:Rule
    var coroutineRule = CoroutineTestRule()

    @Mock
    private lateinit var mockEntityLookup: EntityLookup

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
    private lateinit var mockBookmarksDao: BookmarksDao

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
    private lateinit var mockOnboardingStore: OnboardingStore

    @Mock
    private lateinit var mockAutoCompleteService: AutoCompleteService

    @Mock
    private lateinit var mockWidgetCapabilities: WidgetCapabilities

    @Mock
    private lateinit var mockPrivacySettingsStore: PrivacySettingsStore

    @Mock
    private lateinit var mockDefaultBrowserDetector: DefaultBrowserDetector

    private lateinit var mockAutoCompleteApi: AutoCompleteApi

    private lateinit var ctaViewModel: CtaViewModel

    @Captor
    private lateinit var commandCaptor: ArgumentCaptor<Command>

    private lateinit var db: AppDatabase

    private lateinit var testee: BrowserTabViewModel

    private val selectedTabLiveData = MutableLiveData<TabEntity>()

    @Before
    fun before() {
        MockitoAnnotations.initMocks(this)

        db = Room.inMemoryDatabaseBuilder(getInstrumentation().targetContext, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()

        mockAutoCompleteApi = AutoCompleteApi(mockAutoCompleteService, mockBookmarksDao)

        ctaViewModel = CtaViewModel(
            mockAppInstallStore,
            mockPixel,
            mockSurveyDao,
            mockWidgetCapabilities,
            mockDismissedCtaDao,
            mockVariantManager,
            mockSettingsStore,
            mockOnboardingStore,
            mockPrivacySettingsStore,
            mockDefaultBrowserDetector
        )

        val siteFactory = SiteFactory(mockPrivacyPractices, mockEntityLookup)

        whenever(mockOmnibarConverter.convertQueryToUrl(any())).thenReturn("duckduckgo.com")
        whenever(mockVariantManager.getVariant()).thenReturn(DEFAULT_VARIANT)
        whenever(mockTabsRepository.liveSelectedTab).thenReturn(selectedTabLiveData)
        whenever(mockTabsRepository.retrieveSiteData(any())).thenReturn(MutableLiveData())
        whenever(mockPrivacyPractices.privacyPracticesFor(any())).thenReturn(PrivacyPractices.UNKNOWN)
        whenever(mockAppInstallStore.installTimestamp).thenReturn(System.currentTimeMillis() - TimeUnit.DAYS.toMillis(1))
        whenever(mockPrivacySettingsStore.privacyOn).thenReturn(true)

        testee = BrowserTabViewModel(
            statisticsUpdater = mockStatisticsUpdater,
            queryUrlConverter = mockOmnibarConverter,
            duckDuckGoUrlDetector = DuckDuckGoUrlDetector(),
            siteFactory = siteFactory,
            tabRepository = mockTabsRepository,
            networkLeaderboardDao = mockNetworkLeaderboardDao,
            autoComplete = mockAutoCompleteApi,
            appSettingsPreferencesStore = mockSettingsStore,
            bookmarksDao = mockBookmarksDao,
            longPressHandler = mockLongPressHandler,
            webViewSessionStorage = webViewSessionStorage,
            specialUrlDetector = SpecialUrlDetectorImpl(),
            faviconDownloader = mockFaviconDownloader,
            addToHomeCapabilityDetector = mockAddToHomeCapabilityDetector,
            ctaViewModel = ctaViewModel,
            searchCountDao = mockSearchCountDao,
            pixel = mockPixel,
            variantManager = mockVariantManager,
            dispatchers = coroutineRule.testDispatcherProvider,
            defaultBrowserDetector = mockDefaultBrowserDetector,
            installStore = mockAppInstallStore
        )

        testee.loadData("abc", null, false)
        testee.command.observeForever(mockCommandObserver)
    }

    @ExperimentalCoroutinesApi
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
    fun whenOpenInNewBackgroundRequestedThenTabRepositoryUpdatedAndCommandIssued() = ruleRunBlockingTest {
        val url = "http://www.example.com"
        testee.openInNewBackgroundTab(url)

        verify(mockCommandObserver, atLeastOnce()).onChanged(commandCaptor.capture())
        assertTrue(commandCaptor.lastValue is Command.OpenInNewBackgroundTab)

        verify(mockTabsRepository).addNewTabAfterExistingTab(url, "abc")
    }

    @Test
    fun whenViewIsResumedAndBrowserShowingThenKeyboardHidden() {
        setBrowserShowing(true)
        testee.onViewResumed()
        verify(mockCommandObserver, atLeastOnce()).onChanged(commandCaptor.capture())
        assertTrue(commandCaptor.allValues.contains(Command.HideKeyboard))
    }

    @Test
    fun whenViewIsResumedAndHomeShowingThenKeyboardShown() {
        setBrowserShowing(false)
        testee.onViewResumed()
        verify(mockCommandObserver, atLeastOnce()).onChanged(commandCaptor.capture())
        assertTrue(commandCaptor.allValues.contains(Command.ShowKeyboard))
    }

    @Test
    fun whenViewBecomesVisibleAndHomeShowingThenRefreshCtaIsCalled() {
        ruleRunBlockingTest {
            setBrowserShowing(false)
            val observer = ValueCaptorObserver<BrowserTabViewModel.CtaViewState>()
            testee.ctaViewState.observeForever(observer)

            testee.onViewVisible()

            testee.ctaViewState.removeObserver(observer)
            assertTrue(observer.hasReceivedValue)
        }
    }

    @Test
    fun whenViewBecomesVisibleAndBrowserShowingThenRefreshCtaIsNotCalled() {
        ruleRunBlockingTest {
            setBrowserShowing(true)
            val observer = ValueCaptorObserver<BrowserTabViewModel.CtaViewState>()
            testee.ctaViewState.observeForever(observer)

            testee.onViewVisible()

            testee.ctaViewState.removeObserver(observer)
            assertFalse(observer.hasReceivedValue)
        }
    }

    @Test
    fun whenInvalidatedGlobalLayoutRestoredThenErrorIsShown() {
        givenInvalidatedGlobalLayout()
        setBrowserShowing(true)
        testee.onViewResumed()
        assertCommandIssued<Command.ShowErrorWithAction>()
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
    fun whenBookmarkEditedThenDaoIsUpdated() = ruleRunBlockingTest {
        testee.editBookmark(0, "A title", "www.example.com")
        verify(mockBookmarksDao).update(BookmarkEntity(title = "A title", url = "www.example.com"))
    }

    @Test
    fun whenBookmarkAddedThenDaoIsUpdatedAndUserNotified() = ruleRunBlockingTest {
        loadUrl("www.example.com", "A title")

        testee.onBookmarkAddRequested()
        verify(mockBookmarksDao).insert(BookmarkEntity(title = "A title", url = "www.example.com"))
        verify(mockCommandObserver, atLeastOnce()).onChanged(commandCaptor.capture())
        assertTrue(commandCaptor.lastValue is Command.ShowBookmarkAddedConfirmation)
    }

    @Test
    fun whenTrackerDetectedThenNetworkLeaderboardUpdated() {
        val networkEntity = TestEntity("Network1", "Network1", 10.0)
        val event = TrackingEvent("http://www.example.com", "http://www.tracker.com/tracker.js", emptyList(), networkEntity, false)
        testee.trackerDetected(event)
        verify(mockNetworkLeaderboardDao).incrementNetworkCount("Network1")
    }

    @Test
    fun whenEmptyInputQueryThenQueryNavigateCommandNotSubmittedToActivity() {
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
    fun whenInvalidatedGlobalLayoutAndNonEmptyInputThenOpenInNewTab() {
        givenOneActiveTabSelected()
        givenInvalidatedGlobalLayout()
        testee.onUserSubmittedQuery("foo")
        assertCommandIssued<Command.OpenInNewTab>()
    }

    @Test
    fun whenInvalidatedGlobalLayoutAndNonEmptyInputThenCloseCurrentTab() {
        givenOneActiveTabSelected()
        givenInvalidatedGlobalLayout()

        testee.onUserSubmittedQuery("foo")

        ruleRunBlockingTest {
            verify(mockTabsRepository).delete(selectedTabLiveData.value!!)
        }
    }

    @Test
    fun whenBrowsingAndUrlLoadedThenSiteVisitedEntryAddedToLeaderboardDao() = ruleRunBlockingTest {
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
    fun whenViewModelNotifiedThatUrlGotFocusThenViewStateIsUpdated() = ruleRunBlockingTest {
        withContext(Dispatchers.Main) {
            testee.onOmnibarInputStateChanged("", true, hasQueryChanged = false)
            assertTrue(omnibarViewState().isEditing)
        }
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

        testee.progressChanged(50)
        assertEquals(50, loadingViewState().progress)
        assertEquals(true, loadingViewState().isLoading)

        testee.progressChanged(100)
        assertEquals(100, loadingViewState().progress)
        assertEquals(false, loadingViewState().isLoading)
    }

    @Test
    fun whenBrowsingAndViewModelGetsProgressUpdateLowerThan50ThenViewStateIsUpdatedTo50() {
        setBrowserShowing(true)

        testee.progressChanged(0)
        assertEquals(50, loadingViewState().progress)
        assertEquals(true, loadingViewState().isLoading)

        testee.progressChanged(15)
        assertEquals(50, loadingViewState().progress)
        assertEquals(true, loadingViewState().isLoading)
    }

    @Test
    fun whenNotBrowserAndViewModelGetsProgressUpdateThenViewStateIsNotUpdated() {
        setBrowserShowing(false)
        testee.progressChanged(10)
        assertEquals(0, loadingViewState().progress)
        assertEquals(false, loadingViewState().isLoading)
    }

    @Test
    fun whenUrlClearedThenPrivacyGradeIsCleared() = ruleRunBlockingTest {
        withContext(Dispatchers.Main) {
            loadUrl("https://duckduckgo.com")
            assertNotNull(testee.privacyGrade.value)
            loadUrl(null)
            assertNull(testee.privacyGrade.value)
        }
    }

    @Test
    fun whenUrlLoadedThenPrivacyGradeIsReset() = ruleRunBlockingTest {
        withContext(Dispatchers.Main) {
            loadUrl("https://duckduckgo.com")
            assertNotNull(testee.privacyGrade.value)
        }
    }

    @Test
    fun whenEnoughTrackersDetectedThenPrivacyGradeIsUpdated() {
        val grade = testee.privacyGrade.value
        loadUrl("https://example.com")
        val entity = TestEntity("Network1", "Network1", 10.0)
        for (i in 1..10) {
            testee.trackerDetected(TrackingEvent("https://example.com", "", null, entity, false))
        }
        assertNotEquals(grade, testee.privacyGrade.value)
    }

    @Test
    fun whenInitialisedThenPrivacyGradeIsNotShown() {
        assertFalse(browserViewState().showPrivacyGrade)
    }

    @Test
    fun whenUrlUpdatedThenPrivacyGradeIsShown() {
        loadUrl("")
        assertTrue(browserViewState().showPrivacyGrade)
    }

    @Test
    fun whenBrowserNotShownAndOmnibarInputDoesNotHaveFocusThenPrivacyGradeIsNotShown() {
        testee.onOmnibarInputStateChanged(query = "", hasFocus = false, hasQueryChanged = false)
        assertFalse(browserViewState().showPrivacyGrade)
    }

    @Test
    fun whenBrowserShownAndOmnibarInputDoesNotHaveFocusThenPrivacyGradeIsShown() {
        testee.onUserSubmittedQuery("foo")
        testee.onOmnibarInputStateChanged(query = "", hasFocus = false, hasQueryChanged = false)
        assertTrue(browserViewState().showPrivacyGrade)
    }

    @Test
    fun whenBrowserNotShownAndOmnibarInputHasFocusThenPrivacyGradeIsNotShown() {
        testee.onOmnibarInputStateChanged("", true, hasQueryChanged = false)
        assertFalse(browserViewState().showPrivacyGrade)
    }

    @Test
    fun whenBrowserShownAndOmnibarInputHasFocusThenPrivacyGradeIsShown() {
        testee.onUserSubmittedQuery("foo")
        testee.onOmnibarInputStateChanged("", true, hasQueryChanged = false)
        assertTrue(browserViewState().showPrivacyGrade)
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
        whenever(mockBookmarksDao.bookmarksByQuery("%foo%")).thenReturn(Single.just(emptyList()))
        whenever(mockAutoCompleteService.autoComplete("foo")).thenReturn(Observable.just(emptyList()))
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
    fun whenHomeShowingByPressingBackOnInvalidatedBrowserThenForwardButtonInactive() {
        setupNavigation(isBrowsing = true)
        givenInvalidatedGlobalLayout()
        testee.onUserPressedBack()
        assertFalse(browserViewState().browserShowing)
        assertFalse(browserViewState().canGoForward)
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
    fun whenRefreshRequestedWithInvalidatedGlobalLayoutThenOpenCurrentUrlInNewTab() {
        givenOneActiveTabSelected()
        givenInvalidatedGlobalLayout()

        testee.onRefreshRequested()

        assertCommandIssued<Command.OpenInNewTab>()
    }

    @Test
    fun whenRefreshRequestedWithInvalidatedGlobalLayoutThenCloseCurrentTab() {
        givenOneActiveTabSelected()
        givenInvalidatedGlobalLayout()

        testee.onRefreshRequested()

        ruleRunBlockingTest {
            verify(mockTabsRepository).delete(selectedTabLiveData.value!!)
        }
    }

    @Test
    fun whenRefreshRequestedWithBrowserGlobalLayoutThenRefresh() {
        testee.onRefreshRequested()
        assertCommandIssued<Command.Refresh>()
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
    fun whenSiteLoadedAndUserSelectsToAddBookmarkThenAddBookmarkCommandSentWithUrlAndTitle() = ruleRunBlockingTest {
        loadUrl("foo.com")
        testee.titleReceived("Foo Title")
        testee.onBookmarkAddRequested()
        val command = captureCommands().value as Command.ShowBookmarkAddedConfirmation
        assertEquals("foo.com", command.url)
        assertEquals("Foo Title", command.title)
    }

    @Test
    fun whenNoSiteAndUserSelectsToAddBookmarkThenBookmarkAddedWithBlankTitleAndUrl() = ruleRunBlockingTest {
        whenever(mockBookmarksDao.insert(any())).thenReturn(1)
        testee.onBookmarkAddRequested()
        verify(mockBookmarksDao).insert(BookmarkEntity(title = "", url = ""))
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
    fun whenOnSiteAndBrokenSiteSelectedThenBrokenSiteFeedbackCommandSentWithUrl() = ruleRunBlockingTest {
        loadUrl("foo.com", isBrowserShowing = true)
        testee.onBrokenSiteSelected()
        val command = captureCommands().value as Command.BrokenSiteFeedback
        assertEquals("foo.com", command.url)
    }

    @Test
    fun whenNoSiteAndBrokenSiteSelectedThenBrokenSiteFeedbackCommandSentWithoutUrl() {
        testee.onBrokenSiteSelected()
        val command = captureCommands().value as Command.BrokenSiteFeedback
        assertEquals("", command.url)
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
        assertFalse(browserGlobalLayoutViewState().isNewTabState)
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
        assertFalse(browserGlobalLayoutViewState().isNewTabState)
    }

    @Test
    fun whenUrlNullThenSetBrowserNotShowing() = ruleRunBlockingTest {
        withContext(Dispatchers.Main) {
            testee.loadData("id", null, false)
            testee.determineShowBrowser()
            assertEquals(false, testee.browserViewState.value?.browserShowing)
        }
    }

    @Test
    fun whenUrlBlankThenSetBrowserNotShowing() = ruleRunBlockingTest {
        withContext(Dispatchers.Main) {
            testee.loadData("id", "  ", false)
            testee.determineShowBrowser()
            assertEquals(false, testee.browserViewState.value?.browserShowing)
        }
    }

    @Test
    fun whenUrlPresentThenSetBrowserShowing() = ruleRunBlockingTest {
        withContext(Dispatchers.Main) {
            testee.loadData("id", "https://example.com", false)
            testee.determineShowBrowser()
            assertEquals(true, testee.browserViewState.value?.browserShowing)
        }
    }

    @Test
    fun whenOpenInNewTabThenOpenInNewTabCommandWithCorrectUrlSent() {
        val url = "https://example.com"
        testee.openInNewTab(url)
        verify(mockCommandObserver).onChanged(commandCaptor.capture())

        val command = commandCaptor.lastValue
        assertTrue(command is Command.OpenInNewTab)
        command as Command.OpenInNewTab
        assertEquals(url, command.query)
    }

    @Test
    fun whenRecoveringFromProcessGoneThenShowErrorWithAction() {
        testee.recoverFromRenderProcessGone()
        assertCommandIssued<Command.ShowErrorWithAction>()
    }

    @Test
    fun whenUserClicksOnErrorActionThenOpenCurrentUrlInNewTab() {
        givenOneActiveTabSelected()
        testee.recoverFromRenderProcessGone()
        verify(mockCommandObserver, atLeastOnce()).onChanged(commandCaptor.capture())
        val showErrorWithAction = commandCaptor.value as Command.ShowErrorWithAction

        showErrorWithAction.action()

        assertCommandIssued<Command.OpenInNewTab> {
            assertEquals("https://example.com", query)
        }
    }

    @Test
    fun whenUserClicksOnErrorActionThenOpenCurrentTabIsClosed() {
        givenOneActiveTabSelected()
        testee.recoverFromRenderProcessGone()
        verify(mockCommandObserver, atLeastOnce()).onChanged(commandCaptor.capture())
        val showErrorWithAction = commandCaptor.value as Command.ShowErrorWithAction

        showErrorWithAction.action()

        ruleRunBlockingTest {
            verify(mockTabsRepository).delete(selectedTabLiveData.value!!)
        }
    }

    @Test
    fun whenRecoveringFromProcessGoneThenGlobalLayoutIsInvalidated() {
        testee.recoverFromRenderProcessGone()

        assertTrue(globalLayoutViewState() is BrowserTabViewModel.GlobalLayoutViewState.Invalidated)
    }

    @Test
    fun whenRecoveringFromProcessGoneThenLoadingIsReset() {
        testee.recoverFromRenderProcessGone()

        assertEquals(loadingViewState(), BrowserTabViewModel.LoadingViewState())
    }

    @Test
    fun whenRecoveringFromProcessGoneThenFindInPageIsReset() {
        testee.recoverFromRenderProcessGone()

        assertEquals(findInPageViewState(), BrowserTabViewModel.FindInPageViewState())
    }

    @Test
    fun whenRecoveringFromProcessGoneThenExpectedBrowserOptionsAreDisabled() {
        setupNavigation(skipHome = true, isBrowsing = true, canGoForward = true, canGoBack = true, stepsToPreviousPage = 1)

        testee.recoverFromRenderProcessGone()

        assertFalse(browserViewState().canGoBack)
        assertFalse(browserViewState().canGoForward)
        assertFalse(browserViewState().canReportSite)
        assertFalse(browserViewState().canChangeBrowsingMode)
        assertFalse(findInPageViewState().canFindInPage)
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
        whenever(mockBookmarksDao.hasBookmarks()).thenReturn(true)
        val suggestion = AutoCompleteBookmarkSuggestion("example", "Example", "https://example.com")
        testee.autoCompleteViewState.value = autoCompleteViewState().copy(searchResults = AutoCompleteResult("", listOf(suggestion)))
        testee.fireAutocompletePixel(suggestion)
        verify(mockPixel).fire(Pixel.PixelName.AUTOCOMPLETE_BOOKMARK_SELECTION, pixelParams(showedBookmarks = true, bookmarkCapable = true))
    }

    @Test
    fun whenSearchSuggestionSubmittedWithBookmarksThenAutoCompleteSearchSelectionPixelSent() = runBlocking {
        whenever(mockBookmarksDao.hasBookmarks()).thenReturn(true)
        val suggestions = listOf(AutoCompleteSearchSuggestion("", false), AutoCompleteBookmarkSuggestion("", "", ""))
        testee.autoCompleteViewState.value = autoCompleteViewState().copy(searchResults = AutoCompleteResult("", suggestions))
        testee.fireAutocompletePixel(AutoCompleteSearchSuggestion("example", false))

        verify(mockPixel).fire(Pixel.PixelName.AUTOCOMPLETE_SEARCH_SELECTION, pixelParams(showedBookmarks = true, bookmarkCapable = true))
    }

    @Test
    fun whenSearchSuggestionSubmittedWithoutBookmarksThenAutoCompleteSearchSelectionPixelSent() = runBlocking {
        whenever(mockBookmarksDao.hasBookmarks()).thenReturn(false)
        testee.autoCompleteViewState.value = autoCompleteViewState().copy(searchResults = AutoCompleteResult("", emptyList()))
        testee.fireAutocompletePixel(AutoCompleteSearchSuggestion("example", false))

        verify(mockPixel).fire(Pixel.PixelName.AUTOCOMPLETE_SEARCH_SELECTION, pixelParams(showedBookmarks = false, bookmarkCapable = false))
    }

    @Test
    fun whenUserSelectToEditQueryThenMoveCaretToTheEnd() = ruleRunBlockingTest {
        withContext(Dispatchers.Main) {
            testee.onUserSelectedToEditQuery("foo")
            assertTrue(omnibarViewState().shouldMoveCaretToEnd)
        }
    }

    @Test
    fun whenUserSubmitsQueryThenCaretDoesNotMoveToTheEnd() {
        testee.onUserSubmittedQuery("foo")
        assertFalse(omnibarViewState().shouldMoveCaretToEnd)
    }

    @Test
    fun whenUserRequestedToOpenNewTabThenGenerateWebViewPreviewImage() {
        testee.userRequestedOpeningNewTab()
        verify(mockCommandObserver, atLeastOnce()).onChanged(commandCaptor.capture())
        val command = commandCaptor.firstValue
        assertTrue(command is Command.GenerateWebViewPreviewImage)
    }

    @Test
    fun whenUserRequestedToOpenNewTabThenNewTabCommandIssued() {
        testee.userRequestedOpeningNewTab()
        verify(mockCommandObserver, atLeastOnce()).onChanged(commandCaptor.capture())
        val command = commandCaptor.lastValue
        assertTrue(command is Command.LaunchNewTab)
    }

    @Test
    fun whenCloseCurrentTabSelectedThenTabDeletedFromRepository() = runBlocking {
        givenOneActiveTabSelected()
        testee.closeCurrentTab()
        verify(mockTabsRepository).delete(selectedTabLiveData.value!!)
    }

    @Test
    fun whenUserPressesBackAndSkippingHomeThenWebViewPreviewGenerated() {
        setupNavigation(isBrowsing = true, canGoBack = false, skipHome = true)
        testee.onUserPressedBack()
        assertCommandIssued<Command.GenerateWebViewPreviewImage>()
    }

    @Test
    fun whenUserPressesBackAndNotSkippingHomeThenWebViewPreviewNotGenerated() {
        setupNavigation(isBrowsing = true, canGoBack = false, skipHome = false)
        testee.onUserPressedBack()
        verify(mockCommandObserver, never()).onChanged(commandCaptor.capture())
    }

    @Test
    fun whenScheduledSurveyChangesAndInstalledDaysMatchThenCtaIsSurvey() {
        testee.onSurveyChanged(Survey("abc", "http://example.com", daysInstalled = 1, status = Survey.Status.SCHEDULED))
        assertTrue(testee.ctaViewState.value!!.cta is HomePanelCta.Survey)
    }

    @Test
    fun whenScheduledSurveyChangesAndInstalledDaysDontMatchThenCtaIsNull() {
        testee.onSurveyChanged(Survey("abc", "http://example.com", daysInstalled = 2, status = Survey.Status.SCHEDULED))
        assertNull(testee.ctaViewState.value!!.cta)
    }

    @Test
    fun whenScheduledSurveyIsNullThenCtaIsNotSurvey() {
        testee.onSurveyChanged(null)
        assertFalse(testee.ctaViewState.value!!.cta is HomePanelCta.Survey)
    }

    @Test
    fun whenSurveyCtaDismissedAndNoOtherCtaPossibleCtaIsNull() {
        testee.onSurveyChanged(Survey("abc", "http://example.com", daysInstalled = 1, status = Survey.Status.SCHEDULED))
        testee.onUserDismissedCta(testee.ctaViewState.value!!.cta!!)
        assertNull(testee.ctaViewState.value!!.cta)
    }

    @Test
    fun whenSurveyCtaDismissedAndWidgetCtaIsPossibleThenNextCtaIsWidget() {
        whenever(mockWidgetCapabilities.supportsStandardWidgetAdd).thenReturn(true)
        whenever(mockWidgetCapabilities.supportsAutomaticWidgetAdd).thenReturn(true)
        whenever(mockWidgetCapabilities.hasInstalledWidgets).thenReturn(false)

        testee.onSurveyChanged(Survey("abc", "http://example.com", daysInstalled = 1, status = Survey.Status.SCHEDULED))
        testee.onUserDismissedCta(testee.ctaViewState.value!!.cta!!)
        assertEquals(HomePanelCta.AddWidgetAuto, testee.ctaViewState.value!!.cta)
    }

    @Test
    fun whenCtaRefreshedAndAutoAddSupportedAndWidgetNotInstalledThenCtaIsAutoWidget() = ruleRunBlockingTest {
        whenever(mockWidgetCapabilities.supportsStandardWidgetAdd).thenReturn(true)
        whenever(mockWidgetCapabilities.supportsAutomaticWidgetAdd).thenReturn(true)
        whenever(mockWidgetCapabilities.hasInstalledWidgets).thenReturn(false)
        testee.refreshCta()
        assertEquals(HomePanelCta.AddWidgetAuto, testee.ctaViewState.value!!.cta)
    }

    @Test
    fun whenCtaRefreshedAndAutoAddSupportedAndWidgetAlreadyInstalledThenCtaIsNull() = ruleRunBlockingTest {
        whenever(mockWidgetCapabilities.supportsStandardWidgetAdd).thenReturn(true)
        whenever(mockWidgetCapabilities.supportsAutomaticWidgetAdd).thenReturn(true)
        whenever(mockWidgetCapabilities.hasInstalledWidgets).thenReturn(true)
        testee.refreshCta()
        assertNull(testee.ctaViewState.value!!.cta)
    }

    @Test
    fun whenCtaRefreshedAndOnlyStandardAddSupportedAndWidgetNotInstalledThenCtaIsInstructionsWidget() = ruleRunBlockingTest {
        givenExpectedCtaAddWidgetInstructions()
        testee.refreshCta()
        assertEquals(HomePanelCta.AddWidgetInstructions, testee.ctaViewState.value!!.cta)
    }

    @Test
    fun whenCtaRefreshedAndOnlyStandardAddSupportedAndWidgetAlreadyInstalledThenCtaIsNull() = ruleRunBlockingTest {
        whenever(mockWidgetCapabilities.supportsStandardWidgetAdd).thenReturn(true)
        whenever(mockWidgetCapabilities.supportsAutomaticWidgetAdd).thenReturn(false)
        whenever(mockWidgetCapabilities.hasInstalledWidgets).thenReturn(true)
        testee.refreshCta()
        assertNull(testee.ctaViewState.value!!.cta)
    }

    @Test
    fun whenCtaRefreshedAndStandardAddNotSupportedAndWidgetNotInstalledThenCtaIsNull() = ruleRunBlockingTest {
        whenever(mockWidgetCapabilities.supportsStandardWidgetAdd).thenReturn(false)
        whenever(mockWidgetCapabilities.supportsAutomaticWidgetAdd).thenReturn(false)
        whenever(mockWidgetCapabilities.hasInstalledWidgets).thenReturn(false)
        testee.refreshCta()
        assertNull(testee.ctaViewState.value!!.cta)
    }

    @Test
    fun whenCtaRefreshedAndStandardAddNotSupportedAndWidgetAlreadyInstalledThenCtaIsNull() = ruleRunBlockingTest {
        whenever(mockWidgetCapabilities.supportsStandardWidgetAdd).thenReturn(false)
        whenever(mockWidgetCapabilities.supportsAutomaticWidgetAdd).thenReturn(false)
        whenever(mockWidgetCapabilities.hasInstalledWidgets).thenReturn(true)
        testee.refreshCta()
        assertNull(testee.ctaViewState.value!!.cta)
    }

    @Test
    fun whenCtaRefreshedAndIsNewTabIsFalseThenReturnNull() = ruleRunBlockingTest {
        setBrowserShowing(true)
        whenever(mockWidgetCapabilities.supportsStandardWidgetAdd).thenReturn(true)
        whenever(mockWidgetCapabilities.supportsAutomaticWidgetAdd).thenReturn(true)
        whenever(mockWidgetCapabilities.hasInstalledWidgets).thenReturn(false)
        testee.refreshCta()
        assertNull(testee.ctaViewState.value!!.cta)
    }

    @Test
    fun whenCtaShownThenFirePixel() {
        val cta = HomePanelCta.Survey(Survey("abc", "http://example.com", daysInstalled = 1, status = Survey.Status.SCHEDULED))
        testee.ctaViewState.value = BrowserTabViewModel.CtaViewState(cta = cta)

        testee.onCtaShown()
        verify(mockPixel).fire(cta.shownPixel!!, cta.pixelShownParameters())
    }

    @Test
    fun whenManualCtaShownThenFirePixel() {
        val cta = HomePanelCta.Survey(Survey("abc", "http://example.com", daysInstalled = 1, status = Survey.Status.SCHEDULED))

        testee.onManualCtaShown(cta)
        verify(mockPixel).fire(cta.shownPixel!!, cta.pixelShownParameters())
    }

    @Test
    fun whenRegisterDaxBubbleCtaDismissedThenRegisterInDatabase() {
        val cta = DaxBubbleCta.DaxIntroCta(mockOnboardingStore, mockAppInstallStore)
        testee.ctaViewState.value = BrowserTabViewModel.CtaViewState(cta = cta)

        testee.registerDaxBubbleCtaDismissed()
        verify(mockDismissedCtaDao).insert(DismissedCta(cta.ctaId))
    }

    @Test
    fun whenUserClickedCtaButtonThenFirePixel() {
        val cta = DaxBubbleCta.DaxIntroCta(mockOnboardingStore, mockAppInstallStore)
        testee.onUserClickCtaOkButton(cta)
        verify(mockPixel).fire(cta.okPixel!!, cta.pixelOkParameters())
    }

    @Test
    fun whenUserClickedSurveyCtaButtonThenLaunchSurveyCommand() {
        val cta = HomePanelCta.Survey(Survey("abc", "http://example.com", daysInstalled = 1, status = Survey.Status.SCHEDULED))
        testee.onUserClickCtaOkButton(cta)
        assertCommandIssued<Command.LaunchSurvey>()
    }

    @Test
    fun whenUserClickedAddWidgetCtaButtonThenLaunchAddWidgetCommand() {
        val cta = HomePanelCta.AddWidgetAuto
        testee.onUserClickCtaOkButton(cta)
        assertCommandIssued<Command.LaunchAddWidget>()
    }

    @Test
    fun whenUserClickedLegacyAddWidgetCtaButtonThenLaunchLegacyAddWidgetCommand() {
        val cta = HomePanelCta.AddWidgetInstructions
        testee.onUserClickCtaOkButton(cta)
        assertCommandIssued<Command.LaunchLegacyAddWidget>()
    }

    @Test
    fun whenUserClickedDefaultBrowserCtaButtonWithoutDefaultBrowserThenLaunchDialogCommand() {
        whenever(mockDefaultBrowserDetector.hasDefaultBrowser()).thenReturn(false)
        val cta = DaxDialogCta.DefaultBrowserCta(mockDefaultBrowserDetector, mock(), mock())
        testee.onUserClickCtaOkButton(cta)
        assertCommandIssued<Command.OpenDefaultBrowserDialog>()
    }

    @Test
    fun whenUserClickedDefaultBrowserCtaButtonWithDefaultBrowserThenOpenDefaultBrowserSettings() {
        whenever(mockDefaultBrowserDetector.hasDefaultBrowser()).thenReturn(true)
        val cta = DaxDialogCta.DefaultBrowserCta(mockDefaultBrowserDetector, mock(), mock())
        testee.onUserClickCtaOkButton(cta)
        assertCommandIssued<Command.OpenDefaultBrowserSettings>()
    }

    @Test
    fun whenUserClickedDaxSearchWidgetCtaButtonWithWidgetCapabilitiesThenLaunchAddWidget() {
        whenever(mockWidgetCapabilities.supportsAutomaticWidgetAdd).thenReturn(true)
        val cta = DaxDialogCta.SearchWidgetCta(mockWidgetCapabilities, mock(), mock())
        testee.onUserClickCtaOkButton(cta)
        assertCommandIssued<Command.LaunchAddWidget>()
    }

    @Test
    fun whenUserClickedDaxSearchWidgetCtaButtonWithoutWidgetCapabilitiesThenLaunchLegacyAddWidget() {
        whenever(mockWidgetCapabilities.supportsAutomaticWidgetAdd).thenReturn(false)
        val cta = DaxDialogCta.SearchWidgetCta(mockWidgetCapabilities, mock(), mock())
        testee.onUserClickCtaOkButton(cta)
        assertCommandIssued<Command.LaunchLegacyAddWidget>()
    }

    @Test
    fun whenUserDismissedCtaThenFirePixel() {
        val cta = HomePanelCta.Survey(Survey("abc", "http://example.com", daysInstalled = 1, status = Survey.Status.SCHEDULED))
        testee.onUserDismissedCta(cta)
        verify(mockPixel).fire(cta.cancelPixel!!, cta.pixelCancelParameters())
    }

    @Test
    fun whenUserDismissedCtaThenRegisterInDatabase() {
        val cta = HomePanelCta.AddWidgetAuto
        testee.onUserDismissedCta(cta)
        verify(mockDismissedCtaDao).insert(DismissedCta(cta.ctaId))
    }

    @Test
    fun whenUserDismissedSurveyCtaThenDoNotRegisterInDatabase() {
        val cta = HomePanelCta.Survey(Survey("abc", "http://example.com", daysInstalled = 1, status = Survey.Status.SCHEDULED))
        testee.onUserDismissedCta(cta)
        verify(mockDismissedCtaDao, never()).insert(DismissedCta(cta.ctaId))
    }

    @Test
    fun whenUserDismissedSurveyCtaThenCancelScheduledSurveys() {
        val cta = HomePanelCta.Survey(Survey("abc", "http://example.com", daysInstalled = 1, status = Survey.Status.SCHEDULED))
        testee.onUserDismissedCta(cta)
        verify(mockSurveyDao).cancelScheduledSurveys()
    }

    @Test
    fun whenUserDismissedHOmeTopPanelCtaAndVariantIsNotConceptTestThenRefreshCta() {
        val cta = HomeTopPanelCta.CovidCta()
        whenever(mockDismissedCtaDao.exists(cta.ctaId)).thenReturn(true)
        testee.onUserDismissedCta(cta)
        verify(mockDismissedCtaDao).insert(DismissedCta(cta.ctaId))
        assertNotEquals(HomeTopPanelCta.CovidCta, testee.ctaViewState.value!!.cta)
    }

    @Test
    fun whenUserDismissedHOmeTopPanelCtaAndVariantIsConceptTestThenReturnEmtpyCta() {
        whenever(mockVariantManager.getVariant()).thenReturn(
            Variant("test", features = listOf(VariantManager.VariantFeature.ConceptTest), filterBy = { true })
        )
        val cta = HomeTopPanelCta.CovidCta()
        testee.onUserDismissedCta(cta)
        assertNull(testee.ctaViewState.value!!.cta)
    }

    @Test
    fun whenUserFailedSettingDdgAsDefaultBrowserFromSettingsThenFirePixelAndUpdateInstallStore() {
        whenever(mockDefaultBrowserDetector.isDefaultBrowser()).thenReturn(false)

        testee.onUserTriedToSetAsDefaultBrowserFromSettings()

        verify(mockAppInstallStore).defaultBrowser = false
        verify(mockPixel).fire(Pixel.PixelName.DEFAULT_BROWSER_NOT_SET, defaultBrowserPixelParams(Pixel.PixelValues.DEFAULT_BROWSER_SETTINGS))
    }

    @Test
    fun whenUserHasSetDdgAsDefaultBrowserFromSettingsThenFirePixelAndUpdateInstallStore() {
        whenever(mockDefaultBrowserDetector.isDefaultBrowser()).thenReturn(true)

        testee.onUserTriedToSetAsDefaultBrowserFromSettings()

        verify(mockAppInstallStore).defaultBrowser = true
        verify(mockPixel).fire(Pixel.PixelName.DEFAULT_BROWSER_SET, defaultBrowserPixelParams(Pixel.PixelValues.DEFAULT_BROWSER_SETTINGS))
    }

    @Test
    fun whenUserFailedSettingDdgAsDefaultBrowserFromDialogOnceThenOpenDefaultBrowserDialogAgain() {
        whenever(mockDefaultBrowserDetector.isDefaultBrowser()).thenReturn(false)

        testee.onUserTriedToSetAsDefaultBrowserFromDialog()

        assertCommandIssued<Command.OpenDefaultBrowserDialog>()
    }

    @Test
    fun whenUserFailedSettingDdgAsDefaultBrowserFromDialogTwiceThenFirePixelAndUpdateInstallStore() {
        whenever(mockDefaultBrowserDetector.isDefaultBrowser()).thenReturn(false)

        testee.onUserTriedToSetAsDefaultBrowserFromDialog()
        testee.onUserTriedToSetAsDefaultBrowserFromDialog()

        verify(mockAppInstallStore, times(2)).defaultBrowser = false
        verify(mockPixel).fire(
            Pixel.PixelName.DEFAULT_BROWSER_NOT_SET,
            defaultBrowserPixelParams(Pixel.PixelValues.DEFAULT_BROWSER_JUST_ONCE_MAX)
        )
    }

    @Test
    fun whenUserHasSetDdgAsDefaultBrowserFromDialogThenFirePixelAndUpdateInstallStore() {
        whenever(mockDefaultBrowserDetector.isDefaultBrowser()).thenReturn(true)

        testee.onUserTriedToSetAsDefaultBrowserFromDialog()

        verify(mockAppInstallStore).defaultBrowser = true
        verify(mockPixel).fire(Pixel.PixelName.DEFAULT_BROWSER_SET, defaultBrowserPixelParams(Pixel.PixelValues.DEFAULT_BROWSER_DIALOG))
    }

    @Test
    fun whenUserDismissesDefaultBrowserDialogThenFirePixelAndUpdateInstallStore() {
        whenever(mockDefaultBrowserDetector.isDefaultBrowser()).thenReturn(false)

        testee.onUserDismissedDefaultBrowserDialog()

        verify(mockAppInstallStore).defaultBrowser = false
        verify(mockPixel).fire(
            Pixel.PixelName.DEFAULT_BROWSER_NOT_SET,
            defaultBrowserPixelParams(Pixel.PixelValues.DEFAULT_BROWSER_DIALOG_DISMISSED)
        )
    }

    @Test
    fun whenDefaultBrowserDialogDismissedAfterSelectingDefaultExternalBrowserAlwThenFirePixelAndUpdateInstallStore() {
        whenever(mockDefaultBrowserDetector.isDefaultBrowser()).thenReturn(false)
        whenever(mockDefaultBrowserDetector.hasDefaultBrowser()).thenReturn(true)

        testee.onUserDismissedDefaultBrowserDialog()

        verify(mockAppInstallStore).defaultBrowser = false
        verify(mockPixel).fire(
            Pixel.PixelName.DEFAULT_BROWSER_NOT_SET,
            defaultBrowserPixelParams(Pixel.PixelValues.DEFAULT_BROWSER_EXTERNAL)
        )
    }

    @Test
    fun whenSurrogateDetectedThenSiteUpdated() {
        givenOneActiveTabSelected()
        val surrogate = SurrogateResponse()
        testee.surrogateDetected(surrogate)
        assertTrue(testee.siteLiveData.value?.surrogates?.size == 1)
    }

    @Test
    fun whenUpgradedToHttpsThenSiteUpgradedHttpsReturnsTrue() {
        val url = "http://www.example.com"
        selectedTabLiveData.value = TabEntity("TAB_ID", url, "", skipHome = false, viewed = true, position = 0)
        testee.upgradedToHttps()
        loadUrl("https://www.example.com")
        assertTrue(testee.siteLiveData.value?.upgradedHttps!!)
    }

    @Test
    fun whenNotUpgradedToHttpsThenSiteUpgradedHttpsReturnsFalse() {
        givenOneActiveTabSelected()
        assertFalse(testee.siteLiveData.value?.upgradedHttps!!)
    }

    @Test
    fun whenOnBrokenSiteSelectedOpenBokenSiteFeedback() {
        testee.onBrokenSiteSelected()
        assertCommandIssued<Command.BrokenSiteFeedback>()
    }

    @Test
    fun whenOnBrokenSiteSelectedAndNoHttpsUpgradedThenReturnHttpsUpgradedFalse() {
        testee.onBrokenSiteSelected()

        val command = captureCommands().lastValue
        assertTrue(command is Command.BrokenSiteFeedback)

        val brokenSiteFeedback = command as Command.BrokenSiteFeedback
        assertFalse(brokenSiteFeedback.httpsUpgraded)
    }

    @Test
    fun whenOnBrokenSiteSelectedAndNoTrackersThenReturnBlockedTrackersEmptyString() {
        givenOneActiveTabSelected()

        testee.onBrokenSiteSelected()

        val command = captureCommands().lastValue
        assertTrue(command is Command.BrokenSiteFeedback)

        val brokenSiteFeedback = command as Command.BrokenSiteFeedback
        assertEquals("", brokenSiteFeedback.blockedTrackers)
    }

    @Test
    fun whenOnBrokenSiteSelectedAndTrackersBlockedThenReturnBlockedTrackers() {
        givenOneActiveTabSelected()
        val event = TrackingEvent("http://www.example.com", "http://www.tracker.com/tracker.js", emptyList(), null, false)
        val anotherEvent = TrackingEvent("http://www.example.com/test", "http://www.anothertracker.com/tracker.js", emptyList(), null, false)

        testee.trackerDetected(event)
        testee.trackerDetected(anotherEvent)
        testee.onBrokenSiteSelected()

        val command = captureCommands().lastValue
        assertTrue(command is Command.BrokenSiteFeedback)

        val brokenSiteFeedback = command as Command.BrokenSiteFeedback
        assertEquals("www.tracker.com,www.anothertracker.com", brokenSiteFeedback.blockedTrackers)
    }

    @Test
    fun whenOnBrokenSiteSelectedAndSameHostTrackersBlockedThenDoNotReturnDuplicatedBlockedTrackers() {
        givenOneActiveTabSelected()
        val event = TrackingEvent("http://www.example.com", "http://www.tracker.com/tracker.js", emptyList(), null, false)
        val anotherEvent = TrackingEvent("http://www.example.com/test", "http://www.tracker.com/tracker2.js", emptyList(), null, false)

        testee.trackerDetected(event)
        testee.trackerDetected(anotherEvent)
        testee.onBrokenSiteSelected()

        val command = captureCommands().lastValue
        assertTrue(command is Command.BrokenSiteFeedback)

        val brokenSiteFeedback = command as Command.BrokenSiteFeedback
        assertEquals("www.tracker.com", brokenSiteFeedback.blockedTrackers)
    }

    @Test
    fun whenOnBrokenSiteSelectedAndNoSurrogatesThenReturnSurrogatesEmptyString() {
        givenOneActiveTabSelected()

        testee.onBrokenSiteSelected()

        val command = captureCommands().lastValue
        assertTrue(command is Command.BrokenSiteFeedback)

        val brokenSiteFeedback = command as Command.BrokenSiteFeedback
        assertEquals("", brokenSiteFeedback.surrogates)
    }

    @Test
    fun whenOnBrokenSiteSelectedAndSurrogatesThenReturnSurrogates() {
        givenOneActiveTabSelected()
        val surrogate = SurrogateResponse(true, "surrogate.com/test.js", "", "")
        val anotherSurrogate = SurrogateResponse(true, "anothersurrogate.com/test.js", "", "")

        testee.surrogateDetected(surrogate)
        testee.surrogateDetected(anotherSurrogate)
        testee.onBrokenSiteSelected()

        val command = captureCommands().lastValue
        assertTrue(command is Command.BrokenSiteFeedback)

        val brokenSiteFeedback = command as Command.BrokenSiteFeedback
        assertEquals("surrogate.com,anothersurrogate.com", brokenSiteFeedback.surrogates)
    }

    @Test
    fun whenOnBrokenSiteSelectedAndSameHostSurrogatesThenDoNotReturnDuplicatedSurrogates() {
        givenOneActiveTabSelected()
        val surrogate = SurrogateResponse(true, "surrogate.com/test.js", "", "")
        val anotherSurrogate = SurrogateResponse(true, "surrogate.com/test2.js", "", "")

        testee.surrogateDetected(surrogate)
        testee.surrogateDetected(anotherSurrogate)
        testee.onBrokenSiteSelected()

        val command = captureCommands().lastValue
        assertTrue(command is Command.BrokenSiteFeedback)

        val brokenSiteFeedback = command as Command.BrokenSiteFeedback
        assertEquals("surrogate.com", brokenSiteFeedback.surrogates)
    }

    @Test
    fun whenUserClickedTopCtaButtonAndCtaIsCovidCtaThenSubmitQuery() {
        val cta = HomeTopPanelCta.CovidCta()
        testee.onUserClickTopCta(cta)
        assertEquals(cta.searchTerm, omnibarViewState().omnibarText)
    }

    private inline fun <reified T : Command> assertCommandIssued(instanceAssertions: T.() -> Unit = {}) {
        verify(mockCommandObserver, atLeastOnce()).onChanged(commandCaptor.capture())
        val issuedCommand = commandCaptor.allValues.find { it is T }
        assertNotNull(issuedCommand)
        (issuedCommand as T).apply { instanceAssertions() }
    }

    private fun pixelParams(showedBookmarks: Boolean, bookmarkCapable: Boolean) = mapOf(
        Pixel.PixelParameter.SHOWED_BOOKMARKS to showedBookmarks.toString(),
        Pixel.PixelParameter.BOOKMARK_CAPABLE to bookmarkCapable.toString()
    )

    private fun defaultBrowserPixelParams(origin: String) = mapOf(
        Pixel.PixelParameter.DEFAULT_BROWSER_SET_FROM_ONBOARDING to false.toString(),
        Pixel.PixelParameter.DEFAULT_BROWSER_SET_ORIGIN to origin
    )


    private fun givenExpectedCtaAddWidgetInstructions() {
        setBrowserShowing(false)
        whenever(mockWidgetCapabilities.supportsStandardWidgetAdd).thenReturn(true)
        whenever(mockWidgetCapabilities.supportsAutomaticWidgetAdd).thenReturn(false)
        whenever(mockWidgetCapabilities.hasInstalledWidgets).thenReturn(false)
    }

    private fun givenInvalidatedGlobalLayout() {
        testee.globalLayoutState.value = BrowserTabViewModel.GlobalLayoutViewState.Invalidated
    }

    private fun givenOneActiveTabSelected() {
        selectedTabLiveData.value = TabEntity("TAB_ID", "https://example.com", "", skipHome = false, viewed = true, position = 0)
        testee.loadData("TAB_ID", "https://example.com", false)
    }

    private fun setBrowserShowing(isBrowsing: Boolean) {
        testee.browserViewState.value = browserViewState().copy(browserShowing = isBrowsing)
    }

    private fun loadUrl(url: String?, title: String? = null, isBrowserShowing: Boolean = true) {
        setBrowserShowing(isBrowserShowing)
        testee.navigationStateChanged(buildWebNavigation(originalUrl = url, currentUrl = url, title = title))
    }

    @Suppress("SameParameterValue")
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
    private fun browserGlobalLayoutViewState() = testee.globalLayoutState.value!! as BrowserTabViewModel.GlobalLayoutViewState.Browser

    private fun ruleRunBlockingTest(block: suspend TestCoroutineScope.() -> Unit) =
        coroutineRule.testDispatcher.runBlockingTest(block)
}