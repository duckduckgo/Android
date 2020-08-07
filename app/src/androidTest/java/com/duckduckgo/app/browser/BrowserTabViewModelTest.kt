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
import com.duckduckgo.app.browser.favicon.FaviconDownloader
import com.duckduckgo.app.browser.logindetection.LoginDetected
import com.duckduckgo.app.browser.logindetection.NavigationAwareLoginDetector
import com.duckduckgo.app.browser.logindetection.NavigationEvent.LoginAttempt
import com.duckduckgo.app.browser.model.BasicAuthenticationCredentials
import com.duckduckgo.app.browser.model.BasicAuthenticationRequest
import com.duckduckgo.app.browser.model.LongPressTarget
import com.duckduckgo.app.browser.omnibar.OmnibarEntryConverter
import com.duckduckgo.app.browser.session.WebViewSessionStorage
import com.duckduckgo.app.cta.db.DismissedCtaDao
import com.duckduckgo.app.cta.model.CtaId
import com.duckduckgo.app.cta.model.DismissedCta
import com.duckduckgo.app.fire.fireproofwebsite.data.FireproofWebsiteDao
import com.duckduckgo.app.fire.fireproofwebsite.data.FireproofWebsiteEntity
import com.duckduckgo.app.fire.fireproofwebsite.data.FireproofWebsiteRepository
import com.duckduckgo.app.cta.ui.Cta
import com.duckduckgo.app.cta.ui.CtaViewModel
import com.duckduckgo.app.cta.ui.DaxBubbleCta
import com.duckduckgo.app.cta.ui.DaxDialogCta
import com.duckduckgo.app.cta.ui.HomePanelCta
import com.duckduckgo.app.cta.ui.UseOurAppCta
import com.duckduckgo.app.global.useourapp.UseOurAppDetector.Companion.USE_OUR_APP_DOMAIN
import com.duckduckgo.app.global.useourapp.UseOurAppDetector.Companion.USE_OUR_APP_SHORTCUT_URL
import com.duckduckgo.app.global.db.AppDatabase
import com.duckduckgo.app.global.install.AppInstallStore
import com.duckduckgo.app.global.model.Site
import com.duckduckgo.app.global.model.SiteFactory
import com.duckduckgo.app.global.events.db.UserEventKey
import com.duckduckgo.app.notification.model.UseOurAppNotification
import com.duckduckgo.app.global.events.db.UserEventEntity
import com.duckduckgo.app.global.events.db.UserEventsStore
import com.duckduckgo.app.global.useourapp.UseOurAppDetector
import com.duckduckgo.app.notification.db.NotificationDao
import com.duckduckgo.app.onboarding.store.AppStage
import com.duckduckgo.app.onboarding.store.OnboardingStore
import com.duckduckgo.app.onboarding.store.UserStageStore
import com.duckduckgo.app.privacy.db.NetworkLeaderboardDao
import com.duckduckgo.app.privacy.db.UserWhitelistDao
import com.duckduckgo.app.privacy.model.PrivacyGrade
import com.duckduckgo.app.privacy.model.PrivacyPractices
import com.duckduckgo.app.privacy.model.TestEntity
import com.duckduckgo.app.privacy.model.UserWhitelistedDomain
import com.duckduckgo.app.runBlocking
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
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runBlockingTest
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
    private lateinit var mockUserStageStore: UserStageStore

    @Mock
    private lateinit var mockUserWhitelistDao: UserWhitelistDao

    @Mock
    private lateinit var mockNavigationAwareLoginDetector: NavigationAwareLoginDetector

    @Mock
    private lateinit var mockUserEventsStore: UserEventsStore

    @Mock
    private lateinit var mockNotificationDao: NotificationDao

    private lateinit var mockAutoCompleteApi: AutoCompleteApi

    private lateinit var ctaViewModel: CtaViewModel

    @Captor
    private lateinit var commandCaptor: ArgumentCaptor<Command>

    private lateinit var db: AppDatabase

    private lateinit var testee: BrowserTabViewModel

    private lateinit var fireproofWebsiteDao: FireproofWebsiteDao

    private val selectedTabLiveData = MutableLiveData<TabEntity>()

    private val loginEventLiveData = MutableLiveData<LoginDetected>()

    @Before
    fun before() {
        MockitoAnnotations.initMocks(this)

        db = Room.inMemoryDatabaseBuilder(getInstrumentation().targetContext, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        fireproofWebsiteDao = db.fireproofWebsiteDao()

        mockAutoCompleteApi = AutoCompleteApi(mockAutoCompleteService, mockBookmarksDao)

        ctaViewModel = CtaViewModel(
            mockAppInstallStore,
            mockPixel,
            mockSurveyDao,
            mockWidgetCapabilities,
            mockDismissedCtaDao,
            mockUserWhitelistDao,
            mockVariantManager,
            mockSettingsStore,
            mockOnboardingStore,
            mockUserStageStore,
            mockUserEventsStore,
            UseOurAppDetector(mockUserEventsStore),
            coroutineRule.testDispatcherProvider
        )

        val siteFactory = SiteFactory(mockPrivacyPractices, mockEntityLookup)

        whenever(mockOmnibarConverter.convertQueryToUrl(any(), any())).thenReturn("duckduckgo.com")
        whenever(mockVariantManager.getVariant()).thenReturn(DEFAULT_VARIANT)
        whenever(mockTabsRepository.liveSelectedTab).thenReturn(selectedTabLiveData)
        whenever(mockNavigationAwareLoginDetector.loginEventLiveData).thenReturn(loginEventLiveData)
        whenever(mockTabsRepository.retrieveSiteData(any())).thenReturn(MutableLiveData())
        whenever(mockPrivacyPractices.privacyPracticesFor(any())).thenReturn(PrivacyPractices.UNKNOWN)
        whenever(mockAppInstallStore.installTimestamp).thenReturn(System.currentTimeMillis() - TimeUnit.DAYS.toMillis(1))
        whenever(mockUserWhitelistDao.contains(anyString())).thenReturn(false)

        testee = BrowserTabViewModel(
            statisticsUpdater = mockStatisticsUpdater,
            queryUrlConverter = mockOmnibarConverter,
            duckDuckGoUrlDetector = DuckDuckGoUrlDetector(),
            siteFactory = siteFactory,
            tabRepository = mockTabsRepository,
            userWhitelistDao = mockUserWhitelistDao,
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
            dispatchers = coroutineRule.testDispatcherProvider,
            fireproofWebsiteRepository = FireproofWebsiteRepository(fireproofWebsiteDao, coroutineRule.testDispatcherProvider),
            navigationAwareLoginDetector = mockNavigationAwareLoginDetector,
            userEventsStore = mockUserEventsStore,
            notificationDao = mockNotificationDao,
            useOurAppDetector = UseOurAppDetector(mockUserEventsStore),
            variantManager = mockVariantManager
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
    fun whenOpenInNewBackgroundRequestedThenTabRepositoryUpdatedAndCommandIssued() = coroutineRule.runBlocking {
        val url = "http://www.example.com"
        testee.openInNewBackgroundTab(url)

        verify(mockCommandObserver, atLeastOnce()).onChanged(commandCaptor.capture())
        assertTrue(commandCaptor.lastValue is Command.OpenInNewBackgroundTab)

        verify(mockTabsRepository).addNewTabAfterExistingTab(url, "abc")
    }

    @Test
    fun whenViewBecomesVisibleAndHomeShowingAndUserIsNotInUseOurAppOnboardingStageThenKeyboardShown() = coroutineRule.runBlocking {
        whenever(mockUserStageStore.getUserAppStage()).thenReturn(AppStage.ESTABLISHED)
        setBrowserShowing(false)

        testee.onViewVisible()
        verify(mockCommandObserver, atLeastOnce()).onChanged(commandCaptor.capture())
        assertTrue(commandCaptor.allValues.contains(Command.ShowKeyboard))
    }

    @Test
    fun whenViewBecomesVisibleAndHomeShowingAndUserIsInUseOurAppOnboardingStageThenKeyboardHidden() = coroutineRule.runBlocking {
        whenever(mockUserStageStore.getUserAppStage()).thenReturn(AppStage.USE_OUR_APP_ONBOARDING)
        setBrowserShowing(false)

        testee.onViewVisible()
        verify(mockCommandObserver, atLeastOnce()).onChanged(commandCaptor.capture())
        assertTrue(commandCaptor.allValues.contains(Command.HideKeyboard))
    }

    @Test
    fun whenViewBecomesVisibleAndBrowserShowingThenKeyboardHidden() {
        setBrowserShowing(true)
        testee.onViewVisible()
        verify(mockCommandObserver, atLeastOnce()).onChanged(commandCaptor.capture())
        assertTrue(commandCaptor.allValues.contains(Command.HideKeyboard))
    }

    @Test
    fun whenViewBecomesVisibleAndHomeShowingThenRefreshCtaIsCalled() {
        coroutineRule.runBlocking {
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
        coroutineRule.runBlocking {
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
        whenever(mockOmnibarConverter.convertQueryToUrl("nytimes.com", null)).thenReturn("nytimes.com")
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
    fun whenBookmarkEditedThenDaoIsUpdated() = coroutineRule.runBlocking {
        testee.editBookmark(0, "A title", "www.example.com")
        verify(mockBookmarksDao).update(BookmarkEntity(title = "A title", url = "www.example.com"))
    }

    @Test
    fun whenBookmarkAddedThenDaoIsUpdatedAndUserNotified() = coroutineRule.runBlocking {
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
        whenever(mockOmnibarConverter.convertQueryToUrl("foo", null)).thenReturn("foo.com")
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

        coroutineRule.runBlocking {
            verify(mockTabsRepository).delete(selectedTabLiveData.value!!)
        }
    }

    @Test
    fun whenBrowsingAndUrlLoadedThenSiteVisitedEntryAddedToLeaderboardDao() = coroutineRule.runBlocking {
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
    fun whenViewModelNotifiedThatUrlGotFocusThenViewStateIsUpdated() = coroutineRule.runBlocking {
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
    fun whenUrlClearedThenPrivacyGradeIsCleared() = coroutineRule.runBlocking {
        loadUrl("https://duckduckgo.com")
        assertNotNull(privacyGradeState().privacyGrade)
        loadUrl(null)
        assertNull(privacyGradeState().privacyGrade)
    }

    @Test
    fun whenUrlLoadedThenPrivacyGradeIsReset() = coroutineRule.runBlocking {
        loadUrl("https://duckduckgo.com")
        assertNotNull(privacyGradeState().privacyGrade)
    }

    @Test
    fun whenEnoughTrackersDetectedThenPrivacyGradeIsUpdated() {
        val grade = privacyGradeState().privacyGrade
        loadUrl("https://example.com")
        val entity = TestEntity("Network1", "Network1", 10.0)
        for (i in 1..10) {
            testee.trackerDetected(TrackingEvent("https://example.com", "", null, entity, false))
        }
        assertNotEquals(grade, privacyGradeState().privacyGrade)
    }

    @Test
    fun whenPrivacyGradeFinishedLoadingThenDoNotShowLoadingGrade() {
        testee.stopShowingEmptyGrade()
        assertFalse(privacyGradeState().showEmptyGrade)
    }

    @Test
    fun whenProgressChangesWhileBrowsingButSiteNotFullyLoadedThenPrivacyGradeShouldAnimateIsTrue() {
        setBrowserShowing(true)
        testee.progressChanged(50)
        assertTrue(privacyGradeState().shouldAnimate)
    }

    @Test
    fun whenProgressChangesWhileBrowsingAndSiteIsFullyLoadedThenPrivacyGradeShouldAnimateIsFalse() {
        setBrowserShowing(true)
        testee.progressChanged(100)
        assertFalse(privacyGradeState().shouldAnimate)
    }

    @Test
    fun whenProgressChangesAndPrivacyIsOnThenShowLoadingGradeIsAlwaysTrue() {
        setBrowserShowing(true)
        testee.progressChanged(50)
        assertTrue(privacyGradeState().showEmptyGrade)
        testee.progressChanged(100)
        assertTrue(privacyGradeState().showEmptyGrade)
    }

    @Test
    fun whenProgressChangesAndPrivacyIsOffButSiteNotFullyLoadedThenShowLoadingGradeIsTrue() {
        setBrowserShowing(true)
        testee.loadingViewState.value = loadingViewState().copy(privacyOn = false)
        testee.progressChanged(50)
        assertTrue(privacyGradeState().showEmptyGrade)
    }

    @Test
    fun whenProgressChangesAndPrivacyIsOffAndSiteIsFullyLoadedThenShowLoadingGradeIsFalse() {
        setBrowserShowing(true)
        testee.loadingViewState.value = loadingViewState().copy(privacyOn = false)
        testee.progressChanged(100)
        assertFalse(privacyGradeState().showEmptyGrade)
    }

    @Test
    fun whenProgressChangesButIsTheSameAsBeforeThenDoNotUpdateState() {
        setBrowserShowing(true)
        testee.progressChanged(100)
        testee.stopShowingEmptyGrade()
        testee.progressChanged(100)
        assertFalse(privacyGradeState().showEmptyGrade)
    }

    @Test
    fun whenNotShowingEmptyGradeAndPrivacyGradeIsNotUnknownThenIsEnableIsTrue() {
        val testee = BrowserTabViewModel.PrivacyGradeViewState(PrivacyGrade.A, shouldAnimate = false, showEmptyGrade = false)
        assertTrue(testee.isEnabled)
    }

    @Test
    fun whenPrivacyGradeIsUnknownThenIsEnableIsFalse() {
        val testee = BrowserTabViewModel.PrivacyGradeViewState(PrivacyGrade.UNKNOWN, shouldAnimate = false, showEmptyGrade = false)
        assertFalse(testee.isEnabled)
    }

    @Test
    fun whenShowEmptyGradeIsTrueThenIsEnableIsFalse() {
        val testee = BrowserTabViewModel.PrivacyGradeViewState(PrivacyGrade.A, shouldAnimate = false, showEmptyGrade = true)
        assertFalse(testee.isEnabled)
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
    fun whenOmnibarDoesNotHaveFocusThenShowEmptyGradeIsFalse() {
        testee.onOmnibarInputStateChanged(query = "", hasFocus = false, hasQueryChanged = false)
        assertFalse(privacyGradeState().showEmptyGrade)
    }

    @Test
    fun whenOmnibarDoesNotHaveFocusThenPrivacyGradeIsShownAndSearchIconIsHidden() {
        testee.onOmnibarInputStateChanged(query = "", hasFocus = false, hasQueryChanged = false)
        assertTrue(browserViewState().showPrivacyGrade)
        assertFalse(browserViewState().showSearchIcon)
    }

    @Test
    fun whenBrowserShownAndOmnibarInputDoesNotHaveFocusThenPrivacyGradeIsShownAndSearchIconIsHidden() {
        whenever(mockOmnibarConverter.convertQueryToUrl("foo", null)).thenReturn("foo.com")
        testee.onUserSubmittedQuery("foo")
        testee.onOmnibarInputStateChanged(query = "", hasFocus = false, hasQueryChanged = false)
        assertTrue(browserViewState().showPrivacyGrade)
        assertFalse(browserViewState().showSearchIcon)
    }

    @Test
    fun whenBrowserNotShownAndOmnibarInputHasFocusThenPrivacyGradeIsNotShown() {
        testee.onOmnibarInputStateChanged("", true, hasQueryChanged = false)
        assertFalse(browserViewState().showPrivacyGrade)
    }

    @Test
    fun whenBrowserShownAndOmnibarInputHasFocusThenSearchIconIsShownAndPrivacyGradeIsHidden() {
        whenever(mockOmnibarConverter.convertQueryToUrl("foo", null)).thenReturn("foo.com")
        testee.onUserSubmittedQuery("foo")
        testee.onOmnibarInputStateChanged("", true, hasQueryChanged = false)
        assertFalse(browserViewState().showPrivacyGrade)
        assertTrue(browserViewState().showSearchIcon)
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
        whenever(mockOmnibarConverter.convertQueryToUrl("foo", null)).thenReturn("foo.com")
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
    fun whenUserSelectsDownloadImageOptionFromContextMenuThenDownloadCommandIssuedWithoutRequirementForFurtherUserConfirmation() {
        whenever(mockLongPressHandler.userSelectedMenuItem(any(), any()))
            .thenReturn(DownloadFile("example.com"))

        val mockMenuItem: MenuItem = mock()
        val longPressTarget = LongPressTarget(url = "example.com", type = WebView.HitTestResult.SRC_ANCHOR_TYPE)
        testee.userSelectedItemFromLongPressMenu(longPressTarget, mockMenuItem)
        verify(mockCommandObserver, atLeastOnce()).onChanged(commandCaptor.capture())
        assertTrue(commandCaptor.lastValue is Command.DownloadImage)

        val lastCommand = commandCaptor.lastValue as Command.DownloadImage
        assertEquals("example.com", lastCommand.url)
        assertFalse(lastCommand.requestUserConfirmation)
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
    fun whenFindInPageShowingByPressingBackOnBrowserThenViewStateUpdatedInvisibleAndDoesNotGoToPreviousPage() {
        setupNavigation(isBrowsing = true, canGoBack = true)
        testee.onFindInPageSelected()
        testee.onUserPressedBack()

        assertFalse(findInPageViewState().visible)
        assertCommandIssued<Command.DismissFindInPage>()

        val issuedCommand = commandCaptor.allValues.find { it is Command.NavigateBack }
        assertNull(issuedCommand)
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

        coroutineRule.runBlocking {
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
    fun whenSiteLoadedAndUserSelectsToAddBookmarkThenAddBookmarkCommandSentWithUrlAndTitle() = coroutineRule.runBlocking {
        loadUrl("foo.com")
        testee.titleReceived("Foo Title")
        testee.onBookmarkAddRequested()
        val command = captureCommands().value as Command.ShowBookmarkAddedConfirmation
        assertEquals("foo.com", command.url)
        assertEquals("Foo Title", command.title)
    }

    @Test
    fun whenNoSiteAndUserSelectsToAddBookmarkThenBookmarkAddedWithBlankTitleAndUrl() = coroutineRule.runBlocking {
        whenever(mockBookmarksDao.insert(any())).thenReturn(1)
        testee.onBookmarkAddRequested()
        verify(mockBookmarksDao).insert(BookmarkEntity(title = "", url = ""))
        val command = captureCommands().value as Command.ShowBookmarkAddedConfirmation
        assertEquals(1, command.bookmarkId)
        assertEquals("", command.title)
        assertEquals("", command.url)
    }

    @Test
    fun whenUserTogglesNonWhitelistedSiteThenSiteAddedToWhitelistAndPixelSentAndPageRefreshed() = coroutineRule.runBlocking {
        whenever(mockUserWhitelistDao.contains("www.example.com")).thenReturn(false)
        loadUrl("http://www.example.com/home.html")
        testee.onWhitelistSelected()
        verify(mockUserWhitelistDao).insert(UserWhitelistedDomain("www.example.com"))
        verify(mockPixel).fire(Pixel.PixelName.BROWSER_MENU_WHITELIST_ADD)
        verify(mockCommandObserver).onChanged(Command.Refresh)
    }

    @Test
    fun whenUserTogglesWhitelsitedSiteThenSiteRemovedFromWhitelistAndPixelSentAndPageRefreshed() = coroutineRule.runBlocking {
        whenever(mockUserWhitelistDao.contains("www.example.com")).thenReturn(true)
        loadUrl("http://www.example.com/home.html")
        testee.onWhitelistSelected()
        verify(mockUserWhitelistDao).delete(UserWhitelistedDomain("www.example.com"))
        verify(mockPixel).fire(Pixel.PixelName.BROWSER_MENU_WHITELIST_REMOVE)
        verify(mockCommandObserver).onChanged(Command.Refresh)
    }

    @Test
    fun whenOnSiteAndBrokenSiteSelectedThenBrokenSiteFeedbackCommandSentWithUrl() = coroutineRule.runBlocking {
        loadUrl("foo.com", isBrowserShowing = true)
        testee.onBrokenSiteSelected()
        val command = captureCommands().value as Command.BrokenSiteFeedback
        assertEquals("foo.com", command.data.url)
    }

    @Test
    fun whenNoSiteAndBrokenSiteSelectedThenBrokenSiteFeedbackCommandSentWithoutUrl() {
        testee.onBrokenSiteSelected()
        val command = captureCommands().value as Command.BrokenSiteFeedback
        assertEquals("", command.data.url)
    }

    @Test
    fun whenUserSelectsToShareLinkThenShareLinkCommandSent() {
        loadUrl("foo.com")
        testee.onShareSelected()
        val command = captureCommands().value as Command.ShareLink
        assertEquals("foo.com", command.url)
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
    fun whenUrlNullThenSetBrowserNotShowing() = coroutineRule.runBlocking {
        testee.loadData("id", null, false)
        testee.determineShowBrowser()
        assertEquals(false, testee.browserViewState.value?.browserShowing)
    }

    @Test
    fun whenUrlBlankThenSetBrowserNotShowing() = coroutineRule.runBlocking {
        testee.loadData("id", "  ", false)
        testee.determineShowBrowser()
        assertEquals(false, testee.browserViewState.value?.browserShowing)
    }

    @Test
    fun whenUrlPresentThenSetBrowserShowing() = coroutineRule.runBlocking {
        testee.loadData("id", "https://example.com", false)
        testee.determineShowBrowser()
        assertEquals(true, testee.browserViewState.value?.browserShowing)
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

        coroutineRule.runBlocking {
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
        assertFalse(browserViewState().canFireproofSite)
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
    fun whenAuthenticationIsRequiredForSameHostThenNoChangesOnBrowser() {
        val mockHandler = mock<HttpAuthHandler>()
        val siteURL = "http://example.com/requires-auth"
        val authenticationRequest = BasicAuthenticationRequest(mockHandler, "example.com", "test realm", siteURL)

        loadUrl(url = "http://example.com", isBrowserShowing = true)
        testee.requiresAuthentication(authenticationRequest)

        assertCommandNotIssued<Command.HideWebContent>()
        assertEquals("http://example.com", omnibarViewState().omnibarText)
    }

    @Test
    fun whenAuthenticationIsRequiredForDifferentHostThenUpdateUrlAndHideWebContent() {
        val mockHandler = mock<HttpAuthHandler>()
        val siteURL = "http://example.com/requires-auth"
        val authenticationRequest = BasicAuthenticationRequest(mockHandler, "example.com", "test realm", siteURL)

        loadUrl(url = "http://another.website.com", isBrowserShowing = true)
        testee.requiresAuthentication(authenticationRequest)

        assertCommandIssued<Command.HideWebContent>()
        assertEquals(siteURL, omnibarViewState().omnibarText)
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
    fun whenAuthenticationDialogAcceptedThenShowWebContent() {
        val authenticationRequest = BasicAuthenticationRequest(mock(), "example.com", "test realm", "")
        val credentials = BasicAuthenticationCredentials(username = "user", password = "password")

        testee.handleAuthentication(request = authenticationRequest, credentials = credentials)

        assertCommandIssued<Command.ShowWebContent>()
    }

    @Test
    fun whenAuthenticationDialogCanceledThenShowWebContent() {
        val authenticationRequest = BasicAuthenticationRequest(mock(), "example.com", "test realm", "")

        testee.cancelAuthentication(request = authenticationRequest)

        assertCommandIssued<Command.ShowWebContent>()
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
    fun whenUserSelectToEditQueryThenMoveCaretToTheEnd() = coroutineRule.runBlocking {
        testee.onUserSelectedToEditQuery("foo")
        assertTrue(omnibarViewState().shouldMoveCaretToEnd)
    }

    @Test
    fun whenUserSubmitsQueryThenCaretDoesNotMoveToTheEnd() {
        whenever(mockOmnibarConverter.convertQueryToUrl("foo", null)).thenReturn("foo.com")
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
        assertFalse(commandCaptor.allValues.contains(Command.GenerateWebViewPreviewImage))
    }

    @Test
    fun whenUserPressesBackAndGoesToHomeThenKeyboardShown() {
        setupNavigation(isBrowsing = true, canGoBack = false, skipHome = false)
        testee.onUserPressedBack()
        verify(mockCommandObserver, atLeastOnce()).onChanged(commandCaptor.capture())
        assertTrue(commandCaptor.allValues.contains(Command.ShowKeyboard))
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
    fun whenCtaRefreshedAndAutoAddSupportedAndWidgetNotInstalledThenCtaIsAutoWidget() = coroutineRule.runBlocking {
        whenever(mockWidgetCapabilities.supportsStandardWidgetAdd).thenReturn(true)
        whenever(mockWidgetCapabilities.supportsAutomaticWidgetAdd).thenReturn(true)
        whenever(mockWidgetCapabilities.hasInstalledWidgets).thenReturn(false)
        testee.refreshCta()
        assertEquals(HomePanelCta.AddWidgetAuto, testee.ctaViewState.value!!.cta)
    }

    @Test
    fun whenCtaRefreshedAndAutoAddSupportedAndWidgetAlreadyInstalledThenCtaIsNull() = coroutineRule.runBlocking {
        whenever(mockWidgetCapabilities.supportsStandardWidgetAdd).thenReturn(true)
        whenever(mockWidgetCapabilities.supportsAutomaticWidgetAdd).thenReturn(true)
        whenever(mockWidgetCapabilities.hasInstalledWidgets).thenReturn(true)
        testee.refreshCta()
        assertNull(testee.ctaViewState.value!!.cta)
    }

    @Test
    fun whenCtaRefreshedAndOnlyStandardAddSupportedAndWidgetNotInstalledThenCtaIsInstructionsWidget() = coroutineRule.runBlocking {
        givenExpectedCtaAddWidgetInstructions()
        testee.refreshCta()
        assertEquals(HomePanelCta.AddWidgetInstructions, testee.ctaViewState.value!!.cta)
    }

    @Test
    fun whenCtaRefreshedAndOnlyStandardAddSupportedAndWidgetAlreadyInstalledThenCtaIsNull() = coroutineRule.runBlocking {
        whenever(mockWidgetCapabilities.supportsStandardWidgetAdd).thenReturn(true)
        whenever(mockWidgetCapabilities.supportsAutomaticWidgetAdd).thenReturn(false)
        whenever(mockWidgetCapabilities.hasInstalledWidgets).thenReturn(true)
        testee.refreshCta()
        assertNull(testee.ctaViewState.value!!.cta)
    }

    @Test
    fun whenCtaRefreshedAndStandardAddNotSupportedAndWidgetNotInstalledThenCtaIsNull() = coroutineRule.runBlocking {
        whenever(mockWidgetCapabilities.supportsStandardWidgetAdd).thenReturn(false)
        whenever(mockWidgetCapabilities.supportsAutomaticWidgetAdd).thenReturn(false)
        whenever(mockWidgetCapabilities.hasInstalledWidgets).thenReturn(false)
        testee.refreshCta()
        assertNull(testee.ctaViewState.value!!.cta)
    }

    @Test
    fun whenCtaRefreshedAndStandardAddNotSupportedAndWidgetAlreadyInstalledThenCtaIsNull() = coroutineRule.runBlocking {
        whenever(mockWidgetCapabilities.supportsStandardWidgetAdd).thenReturn(false)
        whenever(mockWidgetCapabilities.supportsAutomaticWidgetAdd).thenReturn(false)
        whenever(mockWidgetCapabilities.hasInstalledWidgets).thenReturn(true)
        testee.refreshCta()
        assertNull(testee.ctaViewState.value!!.cta)
    }

    @Test
    fun whenCtaRefreshedAndIsNewTabIsFalseThenReturnNull() = coroutineRule.runBlocking {
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
    fun whenRegisterDaxBubbleCtaDismissedThenRegisterInDatabase() = coroutineRule.runBlocking {
        val cta = DaxBubbleCta.DaxIntroCta(mockOnboardingStore, mockAppInstallStore)
        testee.ctaViewState.value = BrowserTabViewModel.CtaViewState(cta = cta)

        testee.registerDaxBubbleCtaDismissed()
        verify(mockDismissedCtaDao).insert(DismissedCta(cta.ctaId))
    }

    @Test
    fun whenUserClickedCtaButtonThenFirePixel() {
        val cta = DaxBubbleCta.DaxIntroCta(mockOnboardingStore, mockAppInstallStore)
        setCta(cta)
        testee.onUserClickCtaOkButton()
        verify(mockPixel).fire(cta.okPixel!!, cta.pixelOkParameters())
    }

    @Test
    fun whenUserClickedSurveyCtaButtonThenLaunchSurveyCommand() {
        val cta = HomePanelCta.Survey(Survey("abc", "http://example.com", daysInstalled = 1, status = Survey.Status.SCHEDULED))
        setCta(cta)
        testee.onUserClickCtaOkButton()
        assertCommandIssued<Command.LaunchSurvey>()
    }

    @Test
    fun whenUserClickedAddWidgetCtaButtonThenLaunchAddWidgetCommand() {
        val cta = HomePanelCta.AddWidgetAuto
        setCta(cta)
        testee.onUserClickCtaOkButton()
        assertCommandIssued<Command.LaunchAddWidget>()
    }

    @Test
    fun whenUserClickedLegacyAddWidgetCtaButtonThenLaunchLegacyAddWidgetCommand() {
        val cta = HomePanelCta.AddWidgetInstructions
        setCta(cta)
        testee.onUserClickCtaOkButton()
        assertCommandIssued<Command.LaunchLegacyAddWidget>()
    }

    @Test
    fun whenUserClickedUseOurAppCtaOkButtonThenLaunchAddHomeShortcutAndNavigateCommand() {
        whenever(mockOmnibarConverter.convertQueryToUrl(USE_OUR_APP_SHORTCUT_URL, null)).thenReturn(USE_OUR_APP_SHORTCUT_URL)
        val cta = UseOurAppCta()
        setCta(cta)
        testee.onUserClickCtaOkButton()
        assertCommandIssued<Command.AddHomeShortcut>()
        assertCommandIssued<Navigate>()
    }

    @Test
    fun whenSurveyCtaDismissedAndNoOtherCtaPossibleCtaIsNull() = coroutineRule.runBlocking {
        givenShownCtas(CtaId.DAX_INTRO, CtaId.DAX_END)
        testee.onSurveyChanged(Survey("abc", "http://example.com", daysInstalled = 1, status = Survey.Status.SCHEDULED))
        testee.onUserDismissedCta()
        assertNull(testee.ctaViewState.value!!.cta)
    }

    @Test
    fun whenSurveyCtaDismissedAndWidgetCtaIsPossibleThenNextCtaIsWidget() = coroutineRule.runBlocking {
        whenever(mockWidgetCapabilities.supportsStandardWidgetAdd).thenReturn(true)
        whenever(mockWidgetCapabilities.supportsAutomaticWidgetAdd).thenReturn(true)
        whenever(mockWidgetCapabilities.hasInstalledWidgets).thenReturn(false)

        testee.onSurveyChanged(Survey("abc", "http://example.com", daysInstalled = 1, status = Survey.Status.SCHEDULED))
        testee.onUserDismissedCta()
        assertEquals(HomePanelCta.AddWidgetAuto, testee.ctaViewState.value!!.cta)
    }

    @Test
    fun whenUserDismissedCtaThenFirePixel() = coroutineRule.runBlocking {
        val cta = HomePanelCta.Survey(Survey("abc", "http://example.com", daysInstalled = 1, status = Survey.Status.SCHEDULED))
        setCta(cta)
        testee.onUserDismissedCta()
        verify(mockPixel).fire(cta.cancelPixel!!, cta.pixelCancelParameters())
    }

    @Test
    fun whenUserClickedHideDaxDialogThenHideDaxDialogCommandSent() {
        val cta = DaxDialogCta.DaxSerpCta(mockOnboardingStore, mockAppInstallStore)
        setCta(cta)
        testee.onUserHideDaxDialog()
        val command = captureCommands().lastValue
        assertTrue(command is Command.DaxCommand.HideDaxDialog)
    }

    @Test
    fun whenUserDismissDaxTrackersBlockedDialogThenFinishTrackerAnimationCommandSent() {
        val cta = DaxDialogCta.DaxTrackersBlockedCta(mockOnboardingStore, mockAppInstallStore, emptyList(), "")
        setCta(cta)
        testee.onDaxDialogDismissed()
        val command = captureCommands().lastValue
        assertTrue(command is Command.DaxCommand.FinishTrackerAnimation)
    }

    @Test
    fun whenUserDismissDifferentThanDaxTrackersBlockedDialogThenFinishTrackerAnimationCommandNotSent() {
        val cta = DaxDialogCta.DaxSerpCta(mockOnboardingStore, mockAppInstallStore)
        setCta(cta)
        testee.onDaxDialogDismissed()
        verify(mockCommandObserver, never()).onChanged(commandCaptor.capture())
    }

    @Test
    fun whenUserDismissedCtaThenRegisterInDatabase() = coroutineRule.runBlocking {
        val cta = HomePanelCta.AddWidgetAuto
        setCta(cta)
        testee.onUserDismissedCta()
        verify(mockDismissedCtaDao).insert(DismissedCta(cta.ctaId))
    }

    @Test
    fun whenUserDismissedSurveyCtaThenDoNotRegisterInDatabase() = coroutineRule.runBlocking {
        val cta = HomePanelCta.Survey(Survey("abc", "http://example.com", daysInstalled = 1, status = Survey.Status.SCHEDULED))
        setCta(cta)
        testee.onUserDismissedCta()
        verify(mockDismissedCtaDao, never()).insert(DismissedCta(cta.ctaId))
    }

    @Test
    fun whenUserDismissedSurveyCtaThenCancelScheduledSurveys() = coroutineRule.runBlocking {
        val cta = HomePanelCta.Survey(Survey("abc", "http://example.com", daysInstalled = 1, status = Survey.Status.SCHEDULED))
        setCta(cta)
        testee.onUserDismissedCta()
        verify(mockSurveyDao).cancelScheduledSurveys()
    }

    @Test
    fun whenUserClickedSecondaryCtaButtonInUseOurAppCtaThenLaunchShowKeyboardCommand() {
        val cta = UseOurAppCta()
        setCta(cta)
        testee.onUserClickCtaSecondaryButton()
        assertCommandIssued<Command.ShowKeyboard>()
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
    fun whenOnBrokenSiteSelectedOpenBokenSiteFeedback() = runBlockingTest {
        testee.onBrokenSiteSelected()
        assertCommandIssued<Command.BrokenSiteFeedback>()
    }

    @Test
    fun whenHomeShowingByPressingBackThenFireproofWebsiteOptionMenuDisabled() {
        setupNavigation(isBrowsing = true)
        testee.onUserPressedBack()
        assertFalse(browserViewState().canFireproofSite)
    }

    @Test
    fun whenUserLoadsNotFireproofWebsiteThenFireproofWebsiteBrowserStateUpdated() {
        loadUrl("http://www.example.com/path", isBrowserShowing = true)
        assertTrue(browserViewState().canFireproofSite)
        assertFalse(browserViewState().isFireproofWebsite)
    }

    @Test
    fun whenUserLoadsFireproofWebsiteThenFireproofWebsiteBrowserStateUpdated() {
        givenFireproofWebsiteDomain("www.example.com")
        loadUrl("http://www.example.com/path", isBrowserShowing = true)
        assertTrue(browserViewState().isFireproofWebsite)
    }

    @Test
    fun whenUserLoadsFireproofWebsiteSubDomainThenFireproofWebsiteBrowserStateUpdated() {
        givenFireproofWebsiteDomain("example.com")
        loadUrl("http://mobile.example.com/path", isBrowserShowing = true)
        assertTrue(browserViewState().canFireproofSite)
        assertFalse(browserViewState().isFireproofWebsite)
    }

    @Test
    fun whenUrlClearedThenFireproofWebsiteOptionMenuDisabled() {
        loadUrl("http://www.example.com/path")
        assertTrue(browserViewState().canFireproofSite)
        loadUrl(null)
        assertFalse(browserViewState().canFireproofSite)
    }

    @Test
    fun whenUrlIsUpdatedWithNonFireproofWebsiteThenFireproofWebsiteBrowserStateUpdated() {
        givenFireproofWebsiteDomain("www.example.com")
        loadUrl("http://www.example.com/", isBrowserShowing = true)
        updateUrl("http://www.example.com/", "http://twitter.com/explore", true)
        assertTrue(browserViewState().canFireproofSite)
        assertFalse(browserViewState().isFireproofWebsite)
    }

    @Test
    fun whenUrlIsUpdatedWithFireproofWebsiteThenFireproofWebsiteBrowserStateUpdated() {
        givenFireproofWebsiteDomain("twitter.com")
        loadUrl("http://example.com/", isBrowserShowing = true)
        updateUrl("http://example.com/", "http://twitter.com/explore", true)
        assertTrue(browserViewState().isFireproofWebsite)
    }

    @Test
    fun whenUserClicksFireproofWebsiteOptionMenuThenShowConfirmationIsIssued() {
        loadUrl("http://mobile.example.com/", isBrowserShowing = true)
        testee.onFireproofWebsiteMenuClicked()
        assertCommandIssued<Command.ShowFireproofWebSiteConfirmation> {
            assertEquals("mobile.example.com", this.fireproofWebsiteEntity.domain)
        }
    }

    @Test
    fun whenUserClicksFireproofWebsiteOptionMenuThenFireproofWebsiteBrowserStateUpdated() {
        loadUrl("http://example.com/", isBrowserShowing = true)
        testee.onFireproofWebsiteMenuClicked()
        assertTrue(browserViewState().isFireproofWebsite)
    }

    @Test
    fun whenFireproofWebsiteAddedThenPixelSent() {
        loadUrl("http://example.com/", isBrowserShowing = true)
        testee.onFireproofWebsiteMenuClicked()
        verify(mockPixel).fire(Pixel.PixelName.FIREPROOF_WEBSITE_ADDED)
    }

    @Test
    fun whenUserRemovesFireproofWebsiteFromOptionMenuThenFireproofWebsiteBrowserStateUpdated() {
        givenFireproofWebsiteDomain("mobile.example.com")
        loadUrl("http://mobile.example.com/", isBrowserShowing = true)
        testee.onFireproofWebsiteMenuClicked()
        assertFalse(browserViewState().isFireproofWebsite)
    }

    @Test
    fun whenUserRemovesFireproofWebsiteFromOptionMenuThenPixelSent() {
        givenFireproofWebsiteDomain("mobile.example.com")
        loadUrl("http://mobile.example.com/", isBrowserShowing = true)
        testee.onFireproofWebsiteMenuClicked()
        verify(mockPixel).fire(Pixel.PixelName.FIREPROOF_WEBSITE_REMOVE)
    }

    @Test
    fun whenUserClicksOnFireproofWebsiteSnackbarUndoActionThenFireproofWebsiteIsRemoved() {
        loadUrl("http://example.com/", isBrowserShowing = true)
        testee.onFireproofWebsiteMenuClicked()
        assertCommandIssued<Command.ShowFireproofWebSiteConfirmation> {
            testee.onFireproofWebsiteSnackbarUndoClicked(this.fireproofWebsiteEntity)
        }
        assertTrue(browserViewState().canFireproofSite)
        assertFalse(browserViewState().isFireproofWebsite)
    }

    @Test
    fun whenUserClicksOnFireproofWebsiteSnackbarUndoActionThenPixelSent() {
        loadUrl("http://example.com/", isBrowserShowing = true)
        testee.onFireproofWebsiteMenuClicked()
        assertCommandIssued<Command.ShowFireproofWebSiteConfirmation> {
            testee.onFireproofWebsiteSnackbarUndoClicked(this.fireproofWebsiteEntity)
        }
        verify(mockPixel).fire(Pixel.PixelName.FIREPROOF_WEBSITE_UNDO)
    }

    @Test
    fun whenUserFireproofsWebsiteFromLoginDialogThenShowConfirmationIsIssuedWithExpectedDomain() {
        loadUrl("http://mobile.example.com/", isBrowserShowing = true)
        testee.onUserConfirmedFireproofDialog("login.example.com")
        assertCommandIssued<Command.ShowFireproofWebSiteConfirmation> {
            assertEquals("login.example.com", this.fireproofWebsiteEntity.domain)
        }
    }

    @Test
    fun whenUserFireproofsWebsiteFromLoginDialogThenPixelSent() {
        testee.onUserConfirmedFireproofDialog("login.example.com")
        verify(mockPixel).fire(Pixel.PixelName.FIREPROOF_WEBSITE_LOGIN_ADDED)
    }

    @Test
    fun whenUserDismissesFireproofWebsiteLoginDialogThenPixelSent() {
        testee.onUserDismissedFireproofLoginDialog()
        verify(mockPixel).fire(Pixel.PixelName.FIREPROOF_WEBSITE_LOGIN_DISMISS)
    }

    @Test
    fun whenLoginAttempDetectedThenNotifyNavigationAwareLoginDetector() {
        loadUrl("http://example.com/", isBrowserShowing = true)

        testee.loginDetected()

        verify(mockNavigationAwareLoginDetector).onEvent(LoginAttempt("http://example.com/"))
    }

    @Test
    fun whenLoginDetectedOnAFireproofedWebsiteThenDoNotAskToFireproofWebsite() {
        givenFireproofWebsiteDomain("example.com")
        loginEventLiveData.value = givenLoginDetected("example.com")
        assertCommandNotIssued<Command.AskToFireproofWebsite>()
    }

    @Test
    fun whenLoginDetectedThenAskToFireproofWebsite() {
        loginEventLiveData.value = givenLoginDetected("example.com")
        assertCommandIssued<Command.AskToFireproofWebsite> {
            assertEquals(FireproofWebsiteEntity("example.com"), this.fireproofWebsite)
        }
    }

    @Test
    fun whenLoginDetectedAndUrlIsUseOurAppThenRegisterUserEvent() = coroutineRule.runBlocking {
        whenever(mockUserEventsStore.getUserEvent(UserEventKey.USE_OUR_APP_FIREPROOF_DIALOG_SEEN)).thenReturn(null)
        loginEventLiveData.value = givenLoginDetected(USE_OUR_APP_SHORTCUT_URL)

        verify(mockUserEventsStore).registerUserEvent(UserEventKey.USE_OUR_APP_FIREPROOF_DIALOG_SEEN)
    }

    @Test
    fun whenLoginDetectedAndUrlIsNotUseOurAppThenDoNotRegisterUserEvent() = coroutineRule.runBlocking {
        whenever(mockUserEventsStore.getUserEvent(UserEventKey.USE_OUR_APP_FIREPROOF_DIALOG_SEEN)).thenReturn(null)
        loginEventLiveData.value = givenLoginDetected("example.com")

        verify(mockUserEventsStore, never()).registerUserEvent(UserEventKey.USE_OUR_APP_FIREPROOF_DIALOG_SEEN)
    }

    @Test
    fun whenLoginDetectedAndDialogAlreadySeenThenDoNotRegisterUserEvent() = coroutineRule.runBlocking {
        whenever(mockUserEventsStore.getUserEvent(UserEventKey.USE_OUR_APP_FIREPROOF_DIALOG_SEEN)).thenReturn(UserEventEntity(UserEventKey.USE_OUR_APP_FIREPROOF_DIALOG_SEEN))
        loginEventLiveData.value = givenLoginDetected(USE_OUR_APP_SHORTCUT_URL)

        verify(mockUserEventsStore, never()).registerUserEvent(UserEventKey.USE_OUR_APP_FIREPROOF_DIALOG_SEEN)
    }

    @Test
    fun whenUserBrowsingPressesBackThenCannotAddBookmark() {
        setupNavigation(skipHome = false, isBrowsing = true, canGoBack = false)
        assertTrue(testee.onUserPressedBack())
        assertFalse(browserViewState().canAddBookmarks)
    }

    @Test
    fun whenUserBrowsingPressesBackThenCannotSharePage() {
        setupNavigation(skipHome = false, isBrowsing = true, canGoBack = false)
        assertTrue(testee.onUserPressedBack())
        assertFalse(browserViewState().canSharePage)
    }

    @Test
    fun whenUserBrowsingPressesBackThenCannotReportSite() {
        setupNavigation(skipHome = false, isBrowsing = true, canGoBack = false)
        assertTrue(testee.onUserPressedBack())
        assertFalse(browserViewState().canReportSite)
    }

    @Test
    fun whenUserBrowsingPressesBackThenCannotAddToHome() {
        setupNavigation(skipHome = false, isBrowsing = true, canGoBack = false)
        assertTrue(testee.onUserPressedBack())
        assertFalse(browserViewState().addToHomeEnabled)
    }

    @Test
    fun whenUserBrowsingPressesBackThenCannotWhitelist() {
        setupNavigation(skipHome = false, isBrowsing = true, canGoBack = false)
        assertTrue(testee.onUserPressedBack())
        assertFalse(browserViewState().canWhitelist)
    }

    @Test
    fun whenUserBrowsingPressesBackThenCannotNavigateBack() {
        setupNavigation(skipHome = false, isBrowsing = true, canGoBack = false)
        assertTrue(testee.onUserPressedBack())
        assertFalse(browserViewState().canGoBack)
    }

    @Test
    fun whenUserBrowsingPressesBackThenCannotFindInPage() {
        setupNavigation(skipHome = false, isBrowsing = true, canGoBack = false)
        assertTrue(testee.onUserPressedBack())
        assertFalse(findInPageViewState().canFindInPage)
    }

    @Test
    fun whenUserBrowsingPressesBackThenCanGoForward() {
        setupNavigation(skipHome = false, isBrowsing = true, canGoBack = false)
        assertTrue(testee.onUserPressedBack())
        assertTrue(browserViewState().canGoForward)
    }

    @Test
    fun whenUserBrowsingPressesBackAndForwardThenCanAddBookmark() {
        setupNavigation(skipHome = false, isBrowsing = true, canGoBack = false)
        testee.onUserPressedBack()
        testee.onUserPressedForward()
        assertTrue(browserViewState().canAddBookmarks)
    }

    @Test
    fun whenUserBrowsingPressesBackAndForwardThenCanWhitelist() {
        setupNavigation(skipHome = false, isBrowsing = true, canGoBack = false)
        testee.onUserPressedBack()
        testee.onUserPressedForward()
        assertTrue(browserViewState().canWhitelist)
    }

    @Test
    fun whenUserBrowsingPressesBackAndForwardThenCanShare() {
        setupNavigation(skipHome = false, isBrowsing = true, canGoBack = false)
        testee.onUserPressedBack()
        testee.onUserPressedForward()
        assertTrue(browserViewState().canSharePage)
    }

    @Test
    fun whenUserBrowsingPressesBackAndForwardThenCanReportSite() {
        setupNavigation(skipHome = false, isBrowsing = true, canGoBack = false)
        testee.onUserPressedBack()
        testee.onUserPressedForward()
        assertTrue(browserViewState().canReportSite)
    }

    @Test
    fun whenUserBrowsingPressesBackAndForwardThenCanAddToHome() {
        setupNavigation(skipHome = false, isBrowsing = true, canGoBack = false)
        testee.onUserPressedBack()
        testee.onUserPressedForward()
        assertTrue(browserViewState().addToHomeEnabled)
    }

    @Test
    fun whenUserBrowsingPressesBackAndForwardThenCanFindInPage() {
        setupNavigation(skipHome = false, isBrowsing = true, canGoBack = false)
        testee.onUserPressedBack()
        testee.onUserPressedForward()
        assertTrue(findInPageViewState().canFindInPage)
    }

    @Test
    fun whenSERPRemovalFeatureIsActiveAndBrowsingDDGSiteAndPrivacyGradeIsVisibleThenShowDaxIconIsTrue() {
        val serpRemovalVariant = Variant("foo", 100.0, features = listOf(VariantManager.VariantFeature.SerpHeaderRemoval), filterBy = { true })
        whenever(mockVariantManager.getVariant()).thenReturn(serpRemovalVariant)
        val url = "https://duckduckgo.com?q=test%20search"
        loadUrl(url, isBrowserShowing = true)
        assertTrue(browserViewState().showDaxIcon)
    }

    @Test
    fun whenSERPRemovalFeatureIsActiveAndBrowsingNonDDGSiteAndPrivacyGradeIsVisibleThenShowDaxIconIsFalse() {
        val serpRemovalVariant = Variant("foo", 100.0, features = listOf(VariantManager.VariantFeature.SerpHeaderRemoval), filterBy = { true })
        whenever(mockVariantManager.getVariant()).thenReturn(serpRemovalVariant)
        val url = "https://example.com"
        loadUrl(url, isBrowserShowing = true)
        assertFalse(browserViewState().showDaxIcon)
    }

    @Test
    fun whenSERPRemovalFeatureIsInactiveAndBrowsingDDGSiteAndPrivacyGradeIsVisibleThenShowDaxIconIsFalse() {
        val url = "https://duckduckgo.com?q=test%20search"
        loadUrl(url, isBrowserShowing = true)
        assertFalse(browserViewState().showDaxIcon)
    }

    @Test
    fun whenSERPRemovalFeatureIsInactiveAndBrowsingNonDDGSiteAndPrivacyGradeIsVisibleThenShowDaxIconIsFalse() {
        val url = "https://example.com"
        loadUrl(url, isBrowserShowing = true)
        assertFalse(browserViewState().showDaxIcon)
    }

    @Test
    fun whenQueryIsNotHierarchicalThenUnsupportedOperationExceptionIsHandled() {
        whenever(mockOmnibarConverter.convertQueryToUrl("about:blank", null)).thenReturn("about:blank")
        testee.onUserSubmittedQuery("about:blank")
    }

    @Test
    fun whenViewReadyIfDomainSameAsUseOurAppAfterNotificationSeenThenPixelSent() = coroutineRule.runBlocking {
        givenUseOurAppSiteSelected()
        whenever(mockNotificationDao.exists(UseOurAppNotification.ID)).thenReturn(true)

        testee.onViewReady()

        verify(mockPixel).fire(Pixel.PixelName.UOA_VISITED_AFTER_NOTIFICATION)
    }

    @Test
    fun whenViewReadyIfDomainSameAsUseOurAppAfterShortcutAddedThenPixelSent() = coroutineRule.runBlocking {
        givenUseOurAppSiteSelected()
        whenever(mockUserEventsStore.getUserEvent(UserEventKey.USE_OUR_APP_SHORTCUT_ADDED)).thenReturn(UserEventEntity(UserEventKey.USE_OUR_APP_SHORTCUT_ADDED))

        testee.onViewReady()

        verify(mockPixel).fire(Pixel.PixelName.UOA_VISITED_AFTER_SHORTCUT)
    }

    @Test
    fun whenViewReadyIfDomainSameAsUseOurAppAfterDeleteCtaShownThenPixelSent() = coroutineRule.runBlocking {
        givenUseOurAppSiteSelected()
        whenever(mockDismissedCtaDao.exists(CtaId.USE_OUR_APP_DELETION)).thenReturn(true)

        testee.onViewReady()

        verify(mockPixel).fire(Pixel.PixelName.UOA_VISITED_AFTER_DELETE_CTA)
    }

    @Test
    fun whenViewReadyIfDomainSameAsUseOurAppThenPixelSent() = coroutineRule.runBlocking {
        givenUseOurAppSiteSelected()

        testee.onViewReady()

        verify(mockPixel).fire(Pixel.PixelName.UOA_VISITED)
    }

    @Test
    fun whenViewReadyIfDomainIsNotTheSameAsUseOurAppAfterNotificationSeenThenPixelNotSent() = coroutineRule.runBlocking {
        givenUseOurAppSiteIsNotSelected()
        whenever(mockNotificationDao.exists(UseOurAppNotification.ID)).thenReturn(true)

        testee.onViewReady()

        verify(mockPixel, never()).fire(Pixel.PixelName.UOA_VISITED_AFTER_NOTIFICATION)
    }

    @Test
    fun whenViewReadyIfDomainIsNotTheSameAsUseOurAppAfterShortcutAddedThenPixelNotSent() = coroutineRule.runBlocking {
        givenUseOurAppSiteIsNotSelected()
        whenever(mockUserEventsStore.getUserEvent(UserEventKey.USE_OUR_APP_SHORTCUT_ADDED)).thenReturn(UserEventEntity(UserEventKey.USE_OUR_APP_SHORTCUT_ADDED))

        testee.onViewReady()

        verify(mockPixel, never()).fire(Pixel.PixelName.UOA_VISITED_AFTER_SHORTCUT)
    }

    @Test
    fun whenViewReadyIfDomainIsNotTheSameAsUseOurAppAfterDeleteCtaShownThenPixelNotSent() = coroutineRule.runBlocking {
        givenUseOurAppSiteIsNotSelected()
        whenever(mockDismissedCtaDao.exists(CtaId.USE_OUR_APP_DELETION)).thenReturn(true)

        testee.onViewReady()

        verify(mockPixel, never()).fire(Pixel.PixelName.UOA_VISITED_AFTER_DELETE_CTA)
    }

    @Test
    fun whenViewReadyIfDomainIsNotTheSameAsUseOurAppAThenPixelNotSent() = coroutineRule.runBlocking {
        givenUseOurAppSiteIsNotSelected()

        testee.onViewReady()

        verify(mockPixel, never()).fire(Pixel.PixelName.UOA_VISITED)
    }

    @Test
    fun whenPageChangedIfPreviousOneWasNotUseOurAppSiteAfterNotificationSeenThenPixelSent() = coroutineRule.runBlocking {
        givenUseOurAppSiteIsNotSelected()
        whenever(mockNotificationDao.exists(UseOurAppNotification.ID)).thenReturn(true)

        loadUrl(USE_OUR_APP_DOMAIN, isBrowserShowing = true)

        verify(mockPixel).fire(Pixel.PixelName.UOA_VISITED_AFTER_NOTIFICATION)
    }

    @Test
    fun whenPageChangedIfPreviousOneWasNotUseOurAppSiteAfterShortcutAddedThenPixelSent() = coroutineRule.runBlocking {
        givenUseOurAppSiteIsNotSelected()
        whenever(mockUserEventsStore.getUserEvent(UserEventKey.USE_OUR_APP_SHORTCUT_ADDED)).thenReturn(UserEventEntity(UserEventKey.USE_OUR_APP_SHORTCUT_ADDED))

        loadUrl(USE_OUR_APP_DOMAIN, isBrowserShowing = true)

        verify(mockPixel).fire(Pixel.PixelName.UOA_VISITED_AFTER_SHORTCUT)
    }

    @Test
    fun whenPageChangedIfPreviousOneWasNotUseOurAppSiteAfterDeleteCtaShownThenPixelSent() = coroutineRule.runBlocking {
        givenUseOurAppSiteIsNotSelected()
        whenever(mockDismissedCtaDao.exists(CtaId.USE_OUR_APP_DELETION)).thenReturn(true)

        loadUrl(USE_OUR_APP_DOMAIN, isBrowserShowing = true)

        verify(mockPixel).fire(Pixel.PixelName.UOA_VISITED_AFTER_DELETE_CTA)
    }

    @Test
    fun whenPageChangedIfPreviousOneWasNotUseOurAppSiteThenPixelSent() = coroutineRule.runBlocking {
        givenUseOurAppSiteIsNotSelected()

        loadUrl(USE_OUR_APP_DOMAIN, isBrowserShowing = true)

        verify(mockPixel).fire(Pixel.PixelName.UOA_VISITED)
    }

    @Test
    fun whenPageChangedIfPreviousOneWasUseOurAppSiteAfterNotificationSeenThenPixelNotSent() = coroutineRule.runBlocking {
        givenUseOurAppSiteSelected()
        whenever(mockNotificationDao.exists(UseOurAppNotification.ID)).thenReturn(true)

        loadUrl(USE_OUR_APP_DOMAIN, isBrowserShowing = true)

        verify(mockPixel, never()).fire(Pixel.PixelName.UOA_VISITED_AFTER_NOTIFICATION)
    }

    @Test
    fun whenPageChangedIfPreviousOneWasUseOurAppSiteAfterShortcutAddedThenPixelNotSent() = coroutineRule.runBlocking {
        givenUseOurAppSiteSelected()
        val timestampEntity = UserEventEntity(UserEventKey.USE_OUR_APP_SHORTCUT_ADDED)
        whenever(mockUserEventsStore.getUserEvent(UserEventKey.USE_OUR_APP_SHORTCUT_ADDED)).thenReturn(timestampEntity)

        loadUrl(USE_OUR_APP_DOMAIN, isBrowserShowing = true)

        verify(mockPixel, never()).fire(Pixel.PixelName.UOA_VISITED_AFTER_SHORTCUT)
    }

    @Test
    fun whenPageChangedIfPreviousOneWasUseOurAppSiteThenAfterDeleteCtaShownPixelNotSent() = coroutineRule.runBlocking {
        givenUseOurAppSiteSelected()
        whenever(mockDismissedCtaDao.exists(CtaId.USE_OUR_APP_DELETION)).thenReturn(true)

        loadUrl(USE_OUR_APP_DOMAIN, isBrowserShowing = true)

        verify(mockPixel, never()).fire(Pixel.PixelName.UOA_VISITED_AFTER_DELETE_CTA)
    }

    @Test
    fun whenPageChangedIfPreviousOneWasUseOurAppSiteThenNotSent() = coroutineRule.runBlocking {
        givenUseOurAppSiteSelected()

        loadUrl(USE_OUR_APP_DOMAIN, isBrowserShowing = true)

        verify(mockPixel, never()).fire(Pixel.PixelName.UOA_VISITED)
    }

    private inline fun <reified T : Command> assertCommandIssued(instanceAssertions: T.() -> Unit = {}) {
        verify(mockCommandObserver, atLeastOnce()).onChanged(commandCaptor.capture())
        val issuedCommand = commandCaptor.allValues.find { it is T }
        assertNotNull(issuedCommand)
        (issuedCommand as T).apply { instanceAssertions() }
    }

    private inline fun <reified T : Command> assertCommandNotIssued() {
        val issuedCommand = commandCaptor.allValues.find { it is T }
        assertNull(issuedCommand)
    }

    private fun pixelParams(showedBookmarks: Boolean, bookmarkCapable: Boolean) = mapOf(
        Pixel.PixelParameter.SHOWED_BOOKMARKS to showedBookmarks.toString(),
        Pixel.PixelParameter.BOOKMARK_CAPABLE to bookmarkCapable.toString()
    )

    private fun givenExpectedCtaAddWidgetInstructions() {
        setBrowserShowing(false)
        whenever(mockWidgetCapabilities.supportsStandardWidgetAdd).thenReturn(true)
        whenever(mockWidgetCapabilities.supportsAutomaticWidgetAdd).thenReturn(false)
        whenever(mockWidgetCapabilities.hasInstalledWidgets).thenReturn(false)
    }

    private fun givenShownCtas(vararg shownCtas: CtaId) {
        shownCtas.forEach {
            whenever(mockDismissedCtaDao.exists(it)).thenReturn(true)
        }
    }

    private fun givenInvalidatedGlobalLayout() {
        testee.globalLayoutState.value = BrowserTabViewModel.GlobalLayoutViewState.Invalidated
    }

    private fun givenOneActiveTabSelected() {
        selectedTabLiveData.value = TabEntity("TAB_ID", "https://example.com", "", skipHome = false, viewed = true, position = 0)
        testee.loadData("TAB_ID", "https://example.com", false)
    }

    private fun givenUseOurAppSiteSelected() {
        whenever(mockOmnibarConverter.convertQueryToUrl(USE_OUR_APP_DOMAIN, null)).thenReturn(USE_OUR_APP_DOMAIN)
        val site: Site = mock()
        whenever(site.url).thenReturn(USE_OUR_APP_DOMAIN)
        val siteLiveData = MutableLiveData<Site>()
        siteLiveData.value = site
        whenever(mockTabsRepository.retrieveSiteData("TAB_ID")).thenReturn(siteLiveData)
        testee.loadData("TAB_ID", USE_OUR_APP_DOMAIN, false)
    }

    private fun givenUseOurAppSiteIsNotSelected() {
        whenever(mockOmnibarConverter.convertQueryToUrl("example.com", null)).thenReturn("example.com")
        val site: Site = mock()
        whenever(site.url).thenReturn("example.com")
        val siteLiveData = MutableLiveData<Site>()
        siteLiveData.value = site
        whenever(mockTabsRepository.retrieveSiteData("TAB_ID")).thenReturn(siteLiveData)
        testee.loadData("TAB_ID", "example.com", false)
    }

    private fun givenFireproofWebsiteDomain(vararg fireproofWebsitesDomain: String) {
        fireproofWebsitesDomain.forEach {
            fireproofWebsiteDao.insert(FireproofWebsiteEntity(domain = it))
        }
    }

    private fun givenLoginDetected(domain: String) = LoginDetected(authLoginDomain = "", forwardedToDomain = domain)

    private fun setBrowserShowing(isBrowsing: Boolean) {
        testee.browserViewState.value = browserViewState().copy(browserShowing = isBrowsing)
    }

    private fun setCta(cta: Cta) {
        testee.ctaViewState.value = ctaViewState().copy(cta = cta)
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

    private fun privacyGradeState() = testee.privacyGradeViewState.value!!
    private fun ctaViewState() = testee.ctaViewState.value!!
    private fun browserViewState() = testee.browserViewState.value!!
    private fun omnibarViewState() = testee.omnibarViewState.value!!
    private fun loadingViewState() = testee.loadingViewState.value!!
    private fun autoCompleteViewState() = testee.autoCompleteViewState.value!!
    private fun findInPageViewState() = testee.findInPageViewState.value!!
    private fun globalLayoutViewState() = testee.globalLayoutState.value!!
    private fun browserGlobalLayoutViewState() = testee.globalLayoutState.value!! as BrowserTabViewModel.GlobalLayoutViewState.Browser
}
