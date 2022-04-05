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

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.view.MenuItem
import android.view.View
import android.webkit.GeolocationPermissions
import android.webkit.HttpAuthHandler
import android.webkit.WebView
import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.Observer
import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry.getInstrumentation
import com.duckduckgo.app.CoroutineTestRule
import kotlinx.coroutines.test.runTest
import com.duckduckgo.app.InstantSchedulersRule
import com.duckduckgo.app.ValueCaptorObserver
import com.duckduckgo.app.accessibility.data.AccessibilitySettingsDataStore
import com.duckduckgo.app.accessibility.data.AccessibilitySettingsSharedPreferences
import com.duckduckgo.app.autocomplete.api.AutoComplete.AutoCompleteResult
import com.duckduckgo.app.autocomplete.api.AutoComplete.AutoCompleteSuggestion.AutoCompleteBookmarkSuggestion
import com.duckduckgo.app.autocomplete.api.AutoComplete.AutoCompleteSuggestion.AutoCompleteSearchSuggestion
import com.duckduckgo.app.autocomplete.api.AutoCompleteApi
import com.duckduckgo.app.autocomplete.api.AutoCompleteService
import com.duckduckgo.app.bookmarks.db.BookmarksDao
import com.duckduckgo.app.bookmarks.model.BookmarksRepository
import com.duckduckgo.app.bookmarks.model.FavoritesRepository
import com.duckduckgo.app.bookmarks.model.SavedSite.Bookmark
import com.duckduckgo.app.bookmarks.model.SavedSite.Favorite
import com.duckduckgo.app.browser.BrowserTabViewModel.Command
import com.duckduckgo.app.browser.BrowserTabViewModel.Command.LoadExtractedUrl
import com.duckduckgo.app.browser.BrowserTabViewModel.Command.Navigate
import com.duckduckgo.app.browser.BrowserTabViewModel.HighlightableButton
import com.duckduckgo.app.browser.LongPressHandler.RequiredAction.DownloadFile
import com.duckduckgo.app.browser.LongPressHandler.RequiredAction.OpenInNewTab
import com.duckduckgo.app.browser.addtohome.AddToHomeCapabilityDetector
import com.duckduckgo.app.browser.applinks.AppLinksHandler
import com.duckduckgo.app.browser.downloader.DownloadFailReason
import com.duckduckgo.app.browser.downloader.FileDownloader
import com.duckduckgo.app.browser.favicon.FaviconManager
import com.duckduckgo.app.browser.favicon.FaviconSource
import com.duckduckgo.app.browser.favorites.FavoritesQuickAccessAdapter.QuickAccessFavorite
import com.duckduckgo.app.browser.logindetection.FireproofDialogsEventHandler
import com.duckduckgo.app.browser.logindetection.LoginDetected
import com.duckduckgo.app.browser.logindetection.NavigationAwareLoginDetector
import com.duckduckgo.app.browser.logindetection.NavigationEvent
import com.duckduckgo.app.browser.logindetection.NavigationEvent.LoginAttempt
import com.duckduckgo.app.browser.model.BasicAuthenticationCredentials
import com.duckduckgo.app.browser.model.BasicAuthenticationRequest
import com.duckduckgo.app.browser.model.LongPressTarget
import com.duckduckgo.app.browser.omnibar.OmnibarEntryConverter
import com.duckduckgo.app.browser.remotemessage.RemoteMessagingModel
import com.duckduckgo.app.browser.session.WebViewSessionStorage
import com.duckduckgo.app.cta.db.DismissedCtaDao
import com.duckduckgo.app.cta.model.CtaId
import com.duckduckgo.app.cta.model.DismissedCta
import com.duckduckgo.app.cta.ui.Cta
import com.duckduckgo.app.cta.ui.CtaViewModel
import com.duckduckgo.app.cta.ui.DaxBubbleCta
import com.duckduckgo.app.cta.ui.DaxDialogCta
import com.duckduckgo.app.cta.ui.HomePanelCta
import com.duckduckgo.app.email.EmailManager
import com.duckduckgo.app.fire.fireproofwebsite.data.FireproofWebsiteDao
import com.duckduckgo.app.fire.fireproofwebsite.data.FireproofWebsiteEntity
import com.duckduckgo.app.fire.fireproofwebsite.data.FireproofWebsiteRepository
import com.duckduckgo.app.global.DispatcherProvider
import com.duckduckgo.app.global.db.AppDatabase
import com.duckduckgo.app.global.events.db.UserEventsStore
import com.duckduckgo.app.global.install.AppInstallStore
import com.duckduckgo.app.global.model.Site
import com.duckduckgo.app.global.model.SiteFactory
import com.duckduckgo.app.location.GeoLocationPermissions
import com.duckduckgo.app.location.data.LocationPermissionEntity
import com.duckduckgo.app.location.data.LocationPermissionType
import com.duckduckgo.app.location.data.LocationPermissionsDao
import com.duckduckgo.app.location.data.LocationPermissionsRepository
import com.duckduckgo.app.onboarding.store.AppStage
import com.duckduckgo.app.onboarding.store.OnboardingStore
import com.duckduckgo.app.onboarding.store.UserStageStore
import com.duckduckgo.app.pixels.AppPixelName
import com.duckduckgo.app.privacy.db.NetworkLeaderboardDao
import com.duckduckgo.app.privacy.db.UserWhitelistDao
import com.duckduckgo.app.privacy.model.PrivacyGrade
import com.duckduckgo.app.privacy.model.PrivacyPractices
import com.duckduckgo.app.privacy.model.TestEntity
import com.duckduckgo.app.privacy.model.UserWhitelistedDomain
import com.duckduckgo.app.settings.db.SettingsDataStore
import com.duckduckgo.app.statistics.VariantManager
import com.duckduckgo.app.statistics.VariantManager.Companion.ACTIVE_VARIANTS
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
import com.duckduckgo.feature.toggles.api.FeatureToggle
import com.duckduckgo.privacy.config.api.*
import com.duckduckgo.privacy.config.impl.features.gpc.RealGpc
import com.duckduckgo.privacy.config.impl.features.gpc.RealGpc.Companion.GPC_HEADER
import com.duckduckgo.privacy.config.impl.features.gpc.RealGpc.Companion.GPC_HEADER_VALUE
import com.duckduckgo.privacy.config.impl.features.unprotectedtemporary.UnprotectedTemporary
import com.duckduckgo.privacy.config.store.features.gpc.GpcRepository
import com.duckduckgo.remote.messaging.api.Content
import com.duckduckgo.remote.messaging.api.RemoteMessage
import com.duckduckgo.remote.messaging.api.RemoteMessagingRepository
import org.mockito.kotlin.*
import org.mockito.kotlin.any
import org.mockito.kotlin.atLeastOnce
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.eq
import org.mockito.kotlin.whenever
import dagger.Lazy
import io.reactivex.Observable
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.consumeAsFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.ArgumentCaptor
import org.mockito.Captor
import org.mockito.Mock
import org.mockito.Mockito
import org.mockito.Mockito.*
import org.mockito.Mockito.never
import org.mockito.Mockito.times
import org.mockito.Mockito.verify
import org.mockito.MockitoAnnotations
import org.mockito.internal.util.DefaultMockingDetails
import java.io.File
import java.util.Locale
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.TimeUnit

@FlowPreview
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
    private lateinit var mockBookmarksRepository: BookmarksRepository

    @Mock
    private lateinit var mockLongPressHandler: LongPressHandler

    @Mock
    private lateinit var mockOmnibarConverter: OmnibarEntryConverter

    @Mock
    private lateinit var mockTabRepository: TabRepository

    @Mock
    private lateinit var webViewSessionStorage: WebViewSessionStorage

    @Mock
    private lateinit var mockFaviconManager: FaviconManager

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
    private lateinit var mockContentBlocking: ContentBlocking

    @Mock
    private lateinit var mockNavigationAwareLoginDetector: NavigationAwareLoginDetector

    @Mock
    private lateinit var mockUserEventsStore: UserEventsStore

    @Mock
    private lateinit var mockFileDownloader: FileDownloader

    @Mock
    private lateinit var geoLocationPermissions: GeoLocationPermissions

    @Mock
    private lateinit var fireproofDialogsEventHandler: FireproofDialogsEventHandler

    @Mock
    private lateinit var mockEmailManager: EmailManager

    @Mock
    private lateinit var mockFavoritesRepository: FavoritesRepository

    @Mock
    private lateinit var mockSpecialUrlDetector: SpecialUrlDetector

    @Mock
    private lateinit var mockAppLinksHandler: AppLinksHandler

    @Mock
    private lateinit var mockVariantManager: VariantManager

    @Mock
    private lateinit var mockFeatureToggle: FeatureToggle

    @Mock
    private lateinit var mockGpcRepository: GpcRepository

    @Mock
    private lateinit var mockUnprotectedTemporary: UnprotectedTemporary

    @Mock
    private lateinit var mockAmpLinks: AmpLinks

    @Mock
    private lateinit var mockTrackingParameters: TrackingParameters

    @Mock
    private lateinit var mockRemoteMessagingRepository: RemoteMessagingRepository

    private lateinit var remoteMessagingModel: RemoteMessagingModel

    private val lazyFaviconManager = Lazy { mockFaviconManager }

    private lateinit var mockAutoCompleteApi: AutoCompleteApi

    private lateinit var ctaViewModel: CtaViewModel

    @Captor
    private lateinit var commandCaptor: ArgumentCaptor<Command>

    @Captor
    private lateinit var appLinkCaptor: ArgumentCaptor<() -> Unit>

    private lateinit var db: AppDatabase

    private lateinit var testee: BrowserTabViewModel

    private lateinit var fireproofWebsiteDao: FireproofWebsiteDao

    private lateinit var locationPermissionsDao: LocationPermissionsDao

    private lateinit var accessibilitySettingsDataStore: AccessibilitySettingsDataStore

    private val context = getInstrumentation().targetContext

    private val selectedTabLiveData = MutableLiveData<TabEntity>()

    private val loginEventLiveData = MutableLiveData<LoginDetected>()

    private val fireproofDialogsEventHandlerLiveData = MutableLiveData<FireproofDialogsEventHandler.Event>()

    private val dismissedCtaDaoChannel = Channel<List<DismissedCta>>()

    private val childClosedTabsSharedFlow = MutableSharedFlow<String>()

    private val childClosedTabsFlow = childClosedTabsSharedFlow.asSharedFlow()

    private val emailStateFlow = MutableStateFlow(false)

    private val bookmarksListFlow = Channel<List<Bookmark>>()

    private val remoteMessageFlow = Channel<RemoteMessage>()

    private val favoriteListFlow = Channel<List<Favorite>>()

    @Before
    fun before() {
        MockitoAnnotations.openMocks(this)
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        fireproofWebsiteDao = db.fireproofWebsiteDao()
        locationPermissionsDao = db.locationPermissionsDao()

        mockAutoCompleteApi = AutoCompleteApi(mockAutoCompleteService, mockBookmarksDao, mockFavoritesRepository)
        val fireproofWebsiteRepository = FireproofWebsiteRepository(fireproofWebsiteDao, coroutineRule.testDispatcherProvider, lazyFaviconManager)

        whenever(mockDismissedCtaDao.dismissedCtas()).thenReturn(dismissedCtaDaoChannel.consumeAsFlow())
        whenever(mockTabRepository.flowTabs).thenReturn(flowOf(emptyList()))
        whenever(mockEmailManager.signedInFlow()).thenReturn(emailStateFlow.asStateFlow())
        whenever(mockFavoritesRepository.favorites()).thenReturn(favoriteListFlow.consumeAsFlow())
        whenever(mockBookmarksRepository.bookmarks()).thenReturn(bookmarksListFlow.consumeAsFlow())
        whenever(mockRemoteMessagingRepository.messageFlow()).thenReturn(remoteMessageFlow.consumeAsFlow())

        remoteMessagingModel = givenRemoteMessagingModel(mockRemoteMessagingRepository, mockPixel, coroutineRule.testDispatcherProvider)

        ctaViewModel = CtaViewModel(
            appInstallStore = mockAppInstallStore,
            pixel = mockPixel,
            surveyDao = mockSurveyDao,
            widgetCapabilities = mockWidgetCapabilities,
            dismissedCtaDao = mockDismissedCtaDao,
            userWhitelistDao = mockUserWhitelistDao,
            settingsDataStore = mockSettingsStore,
            onboardingStore = mockOnboardingStore,
            userStageStore = mockUserStageStore,
            tabRepository = mockTabRepository,
            dispatchers = coroutineRule.testDispatcherProvider,
            variantManager = mockVariantManager,
            userEventsStore = mockUserEventsStore,
            duckDuckGoUrlDetector = DuckDuckGoUrlDetector()
        )

        val siteFactory = SiteFactory(mockPrivacyPractices, mockEntityLookup)

        accessibilitySettingsDataStore = AccessibilitySettingsSharedPreferences(context, coroutineRule.testDispatcherProvider, TestScope())

        whenever(mockOmnibarConverter.convertQueryToUrl(any(), any(), any())).thenReturn("duckduckgo.com")
        whenever(mockTabRepository.liveSelectedTab).thenReturn(selectedTabLiveData)
        whenever(mockNavigationAwareLoginDetector.loginEventLiveData).thenReturn(loginEventLiveData)
        whenever(mockTabRepository.retrieveSiteData(any())).thenReturn(MutableLiveData())
        whenever(mockTabRepository.childClosedTabs).thenReturn(childClosedTabsFlow)
        whenever(mockPrivacyPractices.privacyPracticesFor(any())).thenReturn(PrivacyPractices.UNKNOWN)
        whenever(mockAppInstallStore.installTimestamp).thenReturn(System.currentTimeMillis() - TimeUnit.DAYS.toMillis(1))
        whenever(mockUserWhitelistDao.contains(anyString())).thenReturn(false)
        whenever(mockContentBlocking.isAnException(anyString())).thenReturn(false)
        whenever(fireproofDialogsEventHandler.event).thenReturn(fireproofDialogsEventHandlerLiveData)

        testee = BrowserTabViewModel(
            statisticsUpdater = mockStatisticsUpdater,
            queryUrlConverter = mockOmnibarConverter,
            duckDuckGoUrlDetector = DuckDuckGoUrlDetector(),
            siteFactory = siteFactory,
            tabRepository = mockTabRepository,
            userWhitelistDao = mockUserWhitelistDao,
            networkLeaderboardDao = mockNetworkLeaderboardDao,
            autoComplete = mockAutoCompleteApi,
            appSettingsPreferencesStore = mockSettingsStore,
            bookmarksRepository = mockBookmarksRepository,
            longPressHandler = mockLongPressHandler,
            webViewSessionStorage = webViewSessionStorage,
            specialUrlDetector = mockSpecialUrlDetector,
            faviconManager = mockFaviconManager,
            addToHomeCapabilityDetector = mockAddToHomeCapabilityDetector,
            ctaViewModel = ctaViewModel,
            searchCountDao = mockSearchCountDao,
            pixel = mockPixel,
            dispatchers = coroutineRule.testDispatcherProvider,
            fireproofWebsiteRepository = fireproofWebsiteRepository,
            locationPermissionsRepository = LocationPermissionsRepository(
                locationPermissionsDao,
                lazyFaviconManager,
                coroutineRule.testDispatcherProvider
            ),
            geoLocationPermissions = geoLocationPermissions,
            navigationAwareLoginDetector = mockNavigationAwareLoginDetector,
            userEventsStore = mockUserEventsStore,
            fileDownloader = mockFileDownloader,
            gpc = RealGpc(context, mockFeatureToggle, mockGpcRepository, mockUnprotectedTemporary),
            fireproofDialogsEventHandler = fireproofDialogsEventHandler,
            emailManager = mockEmailManager,
            favoritesRepository = mockFavoritesRepository,
            appCoroutineScope = TestScope(),
            appLinksHandler = mockAppLinksHandler,
            contentBlocking = mockContentBlocking,
            accessibilitySettingsDataStore = accessibilitySettingsDataStore,
            variantManager = mockVariantManager,
            ampLinks = mockAmpLinks,
            remoteMessagingModel = remoteMessagingModel,
            trackingParameters = mockTrackingParameters
        )

        testee.loadData("abc", null, false, false)
        testee.command.observeForever(mockCommandObserver)

        whenever(mockVariantManager.getVariant()).thenReturn(ACTIVE_VARIANTS.first { it.key == "mi" })
    }

    @ExperimentalCoroutinesApi
    @After
    fun after() {
        ctaViewModel.isFireButtonPulseAnimationFlowEnabled.close()
        dismissedCtaDaoChannel.close()
        bookmarksListFlow.close()
        favoriteListFlow.close()
        remoteMessageFlow.close()
        testee.onCleared()
        db.close()
        testee.command.removeObserver(mockCommandObserver)
        clearAccessibilitySettings()
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
    fun whenOpenInNewBackgroundRequestedThenTabRepositoryUpdatedAndCommandIssued() = runTest {
        val url = "http://www.example.com"
        testee.openInNewBackgroundTab(url)

        verify(mockCommandObserver, atLeastOnce()).onChanged(commandCaptor.capture())
        assertTrue(commandCaptor.lastValue is Command.OpenInNewBackgroundTab)

        verify(mockTabRepository).addNewTabAfterExistingTab(url, "abc")
    }

    @Test
    fun whenViewBecomesVisibleAndHomeShowingThenKeyboardShown() = runTest {
        setBrowserShowing(false)

        testee.onViewVisible()
        verify(mockCommandObserver, atLeastOnce()).onChanged(commandCaptor.capture())
        assertTrue(commandCaptor.allValues.contains(Command.ShowKeyboard))
    }

    @Test
    fun whenViewBecomesVisibleAndHomeCtaPresentThenKeyboardHidden() = runTest {
        givenExpectedCtaAddWidgetInstructions()

        testee.onViewVisible()

        assertCommandIssued<Command.HideKeyboard>()
        assertEquals(HomePanelCta.AddWidgetInstructions, testee.ctaViewState.value!!.cta)
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
        runTest {
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
        runTest {
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
    fun whenBrowsingAndUrlPresentThenAddBookmarkFavoriteButtonsEnabled() {
        loadUrl("www.example.com", isBrowserShowing = true)
        assertTrue(browserViewState().canSaveSite)
        assertTrue(browserViewState().addFavorite.isEnabled())
    }

    @Test
    fun whenBrowsingAndNoUrlThenAddBookmarkFavoriteButtonsDisabled() {
        loadUrl(null, isBrowserShowing = true)
        assertFalse(browserViewState().canSaveSite)
        assertFalse(browserViewState().addFavorite.isEnabled())
    }

    @Test
    fun whenNotBrowsingAndUrlPresentThenAddBookmarkFavoriteButtonsDisabled() {
        loadUrl("www.example.com", isBrowserShowing = false)
        assertFalse(browserViewState().canSaveSite)
        assertFalse(browserViewState().addFavorite.isEnabled())
    }

    @Test
    fun whenBookmarkEditedThenRepositoryIsUpdated() = runTest {
        val bookmark = Bookmark(id = 0, title = "A title", url = "www.example.com", parentId = 0)
        testee.onSavedSiteEdited(bookmark)
        verify(mockBookmarksRepository).update(bookmark)
    }

    @Test
    fun whenFavoriteEditedThenRepositoryUpdated() = runTest {
        val favorite = Favorite(0, "A title", "www.example.com", 1)
        testee.onSavedSiteEdited(favorite)
        verify(mockFavoritesRepository).update(favorite)
    }

    @Test
    fun whenBookmarkAddedThenRepositoryIsUpdatedAndUserNotified() = runTest {
        val url = "http://www.example.com"
        val title = "A title"
        val bookmark = Bookmark(id = 0, title = title, url = url, parentId = 0)
        whenever(mockBookmarksRepository.insert(title = anyString(), url = anyString(), parentId = anyLong())).thenReturn(bookmark)
        loadUrl(url = url, title = title)

        testee.onBookmarkMenuClicked()
        verify(mockBookmarksRepository).insert(title = title, url = url)
        verify(mockCommandObserver, atLeastOnce()).onChanged(commandCaptor.capture())
        assertTrue(commandCaptor.lastValue is Command.ShowSavedSiteAddedConfirmation)
    }

    @Test
    fun whenFavoriteAddedThenRepositoryUpdatedAndUserNotified() = runTest {
        val savedSite = Favorite(1, "title", "http://example.com", 0)
        loadUrl("www.example.com", "A title")
        whenever(mockFavoritesRepository.insert(any(), any())).thenReturn(savedSite)

        testee.onFavoriteMenuClicked()

        verify(mockFavoritesRepository).insert(title = "A title", url = "www.example.com")
        assertCommandIssued<Command.ShowSavedSiteAddedConfirmation>()
    }

    @Test
    fun whenNoSiteAndUserSelectsToAddFavoriteThenSiteIsNotAdded() = runTest {

        testee.onFavoriteMenuClicked()

        verify(mockFavoritesRepository, times(0)).insert(any(), any())
    }

    @Test
    fun whenQuickAccessDeletedThenRepositoryUpdated() = runTest {
        val savedSite = Favorite(1, "title", "http://example.com", 0)

        testee.deleteQuickAccessItem(savedSite)

        verify(mockFavoritesRepository).delete(savedSite)
    }

    @Test
    fun whenQuickAccessInsertedThenRepositoryUpdated() {
        val savedSite = Favorite(1, "title", "http://example.com", 0)

        testee.insertQuickAccessItem(savedSite)

        verify(mockFavoritesRepository).insert(savedSite)
    }

    @Test
    fun whenQuickAccessListChangedThenRepositoryUpdated() {
        val savedSite = Favorite(1, "title", "http://example.com", 0)
        val savedSites = listOf(QuickAccessFavorite(savedSite))

        testee.onQuickAccessListChanged(savedSites)

        verify(mockFavoritesRepository).updateWithPosition(listOf(savedSite))
    }

    @Test
    fun whenTrackerDetectedThenNetworkLeaderboardUpdated() {
        val networkEntity = TestEntity("Network1", "Network1", 10.0)
        val event = TrackingEvent("http://www.example.com", "http://www.tracker.com/tracker.js", emptyList(), networkEntity, false, null)
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
        assertCommandIssued<Command.OpenInNewTab> {
            assertNull(sourceTabId)
        }
    }

    @Test
    fun whenInvalidatedGlobalLayoutAndNonEmptyInputThenCloseCurrentTab() {
        givenOneActiveTabSelected()
        givenInvalidatedGlobalLayout()

        testee.onUserSubmittedQuery("foo")

        runTest {
            verify(mockTabRepository).deleteTabAndSelectSource(selectedTabLiveData.value!!.tabId)
        }
    }

    @Test
    fun whenBrowsingAndUrlLoadedThenSiteVisitedEntryAddedToLeaderboardDao() = runTest {
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
    fun whenUserRedirectedBeforePreviousSiteLoadedAndNewContentDelayedThenWebContentIsBlankedOut() = runTest {
        loadUrl("http://duckduckgo.com")
        testee.progressChanged(50)

        overrideUrl("http://example.com")
        advanceTimeBy(2000)

        assertCommandIssued<Command.HideWebContent>()
    }

    @Test
    fun whenUserRedirectedAfterSiteLoadedAndNewContentDelayedThenWebContentNotBlankedOut() = runTest {
        loadUrl("http://duckduckgo.com")
        testee.progressChanged(100)

        overrideUrl("http://example.com")
        advanceTimeBy(2000)

        assertCommandNotIssued<Command.HideWebContent>()
    }

    @Test
    fun whenUserRedirectedThenNotifyLoginDetector() = runTest {
        loadUrl("http://duckduckgo.com")
        testee.progressChanged(100)

        overrideUrl("http://example.com")

        verify(mockNavigationAwareLoginDetector).onEvent(NavigationEvent.Redirect("http://example.com"))
    }

    @Test
    fun whenLoadingProgressReaches50ThenShowWebContent() = runTest {
        loadUrl("http://duckduckgo.com")
        testee.progressChanged(50)
        overrideUrl("http://example.com")
        advanceTimeBy(2000)

        onProgressChanged(url = "http://example.com", newProgress = 50)

        assertCommandIssued<Command.ShowWebContent>()
    }

    @Test
    fun whenViewModelNotifiedThatUrlGotFocusThenViewStateIsUpdated() = runTest {
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
    fun whenUrlClearedThenPrivacyGradeIsCleared() = runTest {
        loadUrl("https://duckduckgo.com")
        assertNotNull(privacyGradeState().privacyGrade)
        loadUrl(null)
        assertNull(privacyGradeState().privacyGrade)
    }

    @Test
    fun whenUrlLoadedThenPrivacyGradeIsReset() = runTest {
        loadUrl("https://duckduckgo.com")
        assertNotNull(privacyGradeState().privacyGrade)
    }

    @Test
    fun whenEnoughTrackersDetectedThenPrivacyGradeIsUpdated() {
        val grade = privacyGradeState().privacyGrade
        loadUrl("https://example.com")
        val entity = TestEntity("Network1", "Network1", 10.0)
        for (i in 1..10) {
            testee.trackerDetected(TrackingEvent("https://example.com", "", null, entity, false, null))
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
    fun whenProgressChangesAndIsProcessingTrackingLinkThenVisualProgressEqualsFixedProgress() {
        setBrowserShowing(true)
        testee.startProcessingTrackingLink()
        testee.progressChanged(100)
        assertEquals(50, loadingViewState().progress)
    }

    @Test
    fun whenProgressChangesAndIsProcessingTrackingLinkThenPrivacyGradeShouldAnimateIsTrue() {
        setBrowserShowing(true)
        testee.startProcessingTrackingLink()
        testee.progressChanged(100)
        assertTrue(privacyGradeState().shouldAnimate)
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
    fun whenProgressChangesAndIsProcessingTrackingLinkThenShowLoadingGradeIsTrue() {
        setBrowserShowing(true)
        testee.loadingViewState.value = loadingViewState().copy(privacyOn = false)
        testee.startProcessingTrackingLink()
        testee.progressChanged(100)
        assertTrue(privacyGradeState().showEmptyGrade)
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
    fun whenShowEmptyGradeIsTrueThenIsEnableIsTrue() {
        val testee = BrowserTabViewModel.PrivacyGradeViewState(PrivacyGrade.A, shouldAnimate = false, showEmptyGrade = true)
        assertTrue(testee.isEnabled)
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
        assertTrue(browserViewState().fireButton is HighlightableButton.Visible)
    }

    @Test
    fun whenOmnibarInputDoesNotHaveFocusAndHasQueryThenFireButtonIsShown() {
        testee.onOmnibarInputStateChanged("query", false, hasQueryChanged = false)
        assertTrue(browserViewState().fireButton is HighlightableButton.Visible)
    }

    @Test
    fun whenOmnibarInputDoesNotHaveFocusOrQueryThenFireButtonIsShown() {
        testee.onOmnibarInputStateChanged("", false, hasQueryChanged = false)
        assertTrue(browserViewState().fireButton is HighlightableButton.Visible)
    }

    @Test
    fun whenOmnibarInputHasFocusAndNoQueryThenFireButtonIsShown() {
        testee.onOmnibarInputStateChanged("", true, hasQueryChanged = false)
        assertTrue(browserViewState().fireButton is HighlightableButton.Visible)
    }

    @Test
    fun whenOmnibarInputHasFocusAndQueryThenFireButtonIsHidden() {
        testee.onOmnibarInputStateChanged("query", true, hasQueryChanged = false)
        assertTrue(browserViewState().fireButton is HighlightableButton.Gone)
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
        assertTrue(browserViewState().showMenuButton.isEnabled())
    }

    @Test
    fun whenOmnibarInputDoesNotHaveFocusOrQueryThenMenuButtonIsShown() {
        testee.onOmnibarInputStateChanged("", false, hasQueryChanged = false)
        assertTrue(browserViewState().showMenuButton.isEnabled())
    }

    @Test
    fun whenOmnibarInputDoesNotHaveFocusAndHasQueryThenMenuButtonIsShown() {
        testee.onOmnibarInputStateChanged("query", false, hasQueryChanged = false)
        assertTrue(browserViewState().showMenuButton.isEnabled())
    }

    @Test
    fun whenOmnibarInputHasFocusAndNoQueryThenMenuButtonIsShown() {
        testee.onOmnibarInputStateChanged("", true, hasQueryChanged = false)
        assertTrue(browserViewState().showMenuButton.isEnabled())
    }

    @Test
    fun whenOmnibarInputHasFocusAndQueryThenMenuButtonIsHidden() {
        testee.onOmnibarInputStateChanged("query", true, hasQueryChanged = false)
        assertFalse(browserViewState().showMenuButton.isEnabled())
    }

    @Test
    fun whenEnteringQueryWithAutoCompleteEnabledThenAutoCompleteSuggestionsShown() {
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
    fun whenOmnibarFocusedWithUrlAndUserHasFavoritesThenAutoCompleteShowsFavorites() {
        testee.autoCompleteViewState.value =
            autoCompleteViewState().copy(favorites = listOf(QuickAccessFavorite(Favorite(1, "title", "http://example.com", 1))))
        doReturn(true).whenever(mockSettingsStore).autoCompleteSuggestionsEnabled
        testee.onOmnibarInputStateChanged("https://example.com", true, hasQueryChanged = false)
        assertFalse(autoCompleteViewState().showSuggestions)
        assertTrue(autoCompleteViewState().showFavorites)
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
    fun whenEnteringAppLinkQueryAndShouldShowAppLinksPromptThenNavigateInBrowserAndSetPreviousUrlToNull() {
        whenever(mockSettingsStore.showAppLinksPrompt).thenReturn(true)
        whenever(mockOmnibarConverter.convertQueryToUrl("foo", null)).thenReturn("foo.com")
        testee.onUserSubmittedQuery("foo")
        verify(mockCommandObserver, atLeastOnce()).onChanged(commandCaptor.capture())
        assertTrue(commandCaptor.allValues.any { it == Command.HideKeyboard })
        verify(mockAppLinksHandler).updatePreviousUrl(null)
    }

    @Test
    fun whenEnteringAppLinkQueryAndShouldNotShowAppLinksPromptThenNavigateInBrowserAndSetUserQueryState() {
        whenever(mockSettingsStore.showAppLinksPrompt).thenReturn(false)
        whenever(mockOmnibarConverter.convertQueryToUrl("foo", null)).thenReturn("foo.com")
        testee.onUserSubmittedQuery("foo")
        verify(mockCommandObserver, atLeastOnce()).onChanged(commandCaptor.capture())
        assertTrue(commandCaptor.allValues.any { it == Command.HideKeyboard })
        verify(mockAppLinksHandler).updatePreviousUrl("foo.com")
        verify(mockAppLinksHandler).setUserQueryState(true)
    }

    @Test
    fun whenNotifiedEnteringFullScreenThenViewStateUpdatedWithFullScreenFlag() {
        val stubView = View(context)
        testee.goFullScreen(stubView)
        assertTrue(browserViewState().isFullScreen)
    }

    @Test
    fun whenNotifiedEnteringFullScreenThenEnterFullScreenCommandIssued() {
        val stubView = View(context)
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
        setDesktopBrowsingMode(false)
        testee.onChangeBrowserModeClicked()
        verify(mockCommandObserver, atLeastOnce()).onChanged(commandCaptor.capture())
        verify(mockPixel).fire(AppPixelName.MENU_ACTION_DESKTOP_SITE_ENABLE_PRESSED)
        assertTrue(browserViewState().isDesktopBrowsingMode)
    }

    @Test
    fun whenUserSelectsMobileSiteThenMobileModeStateUpdated() {
        loadUrl("http://example.com")
        setDesktopBrowsingMode(true)
        testee.onChangeBrowserModeClicked()
        verify(mockPixel).fire(AppPixelName.MENU_ACTION_DESKTOP_SITE_DISABLE_PRESSED)
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

        assertCommandIssued<Command.OpenInNewTab>() {
            assertNull(sourceTabId)
        }
    }

    @Test
    fun whenRefreshRequestedWithInvalidatedGlobalLayoutThenCloseCurrentTab() {
        givenOneActiveTabSelected()
        givenInvalidatedGlobalLayout()

        testee.onRefreshRequested()

        runTest {
            verify(mockTabRepository).deleteTabAndSelectSource(selectedTabLiveData.value!!.tabId)
        }
    }

    @Test
    fun whenRefreshRequestedWithBrowserGlobalLayoutThenRefresh() {
        testee.onRefreshRequested()
        assertCommandIssued<Command.Refresh>()
    }

    @Test
    fun whenRefreshRequestedWithQuerySearchThenFireQueryChangePixelZero() {
        loadUrl("query")

        testee.onRefreshRequested()

        verify(mockPixel).fire("rq_0")
    }

    @Test
    fun whenRefreshRequestedWithUrlThenDoNotFireQueryChangePixel() {
        loadUrl("https://example.com")

        testee.onRefreshRequested()

        verify(mockPixel, never()).fire("rq_0")
    }

    @Test
    fun whenUserSubmittedQueryWithPreviousBlankQueryThenDoNotSendQueryChangePixel() {
        whenever(mockOmnibarConverter.convertQueryToUrl("another query", null)).thenReturn("another query")
        loadUrl("")

        testee.onUserSubmittedQuery("another query")

        verify(mockPixel, never()).fire("rq_0")
        verify(mockPixel, never()).fire("rq_1")
    }

    @Test
    fun whenUserSubmittedQueryWithDifferentPreviousQueryThenSendQueryChangePixel() {
        whenever(mockOmnibarConverter.convertQueryToUrl("another query", null)).thenReturn("another query")
        loadUrl("query")

        testee.onUserSubmittedQuery("another query")

        verify(mockPixel, never()).fire("rq_0")
        verify(mockPixel).fire("rq_1")
    }

    @Test
    fun whenUserSubmittedDifferentQueryAndOldQueryIsUrlThenDoNotSendQueryChangePixel() {
        whenever(mockOmnibarConverter.convertQueryToUrl("another query", null)).thenReturn("another query")
        loadUrl("www.foo.com")

        testee.onUserSubmittedQuery("another query")

        verify(mockPixel, never()).fire("rq_0")
        verify(mockPixel, never()).fire("rq_1")
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
        setDesktopBrowsingMode(false)
        testee.onChangeBrowserModeClicked()
        verify(mockCommandObserver, atLeastOnce()).onChanged(commandCaptor.capture())
        val ultimateCommand = commandCaptor.lastValue as Navigate
        assertEquals("http://example.com", ultimateCommand.url)
    }

    @Test
    fun whenUserSelectsDesktopSiteWhenNotOnMobileSpecificSiteThenUrlNotModified() {
        loadUrl("http://example.com")
        setDesktopBrowsingMode(false)
        testee.onChangeBrowserModeClicked()
        verify(mockCommandObserver, atLeastOnce()).onChanged(commandCaptor.capture())
        val ultimateCommand = commandCaptor.lastValue
        assertTrue(ultimateCommand == Command.Refresh)
    }

    @Test
    fun whenUserSelectsMobileSiteWhenOnMobileSpecificSiteThenUrlNotModified() {
        loadUrl("http://m.example.com")
        setDesktopBrowsingMode(true)
        testee.onChangeBrowserModeClicked()
        verify(mockCommandObserver, atLeastOnce()).onChanged(commandCaptor.capture())
        val ultimateCommand = commandCaptor.lastValue
        assertTrue(ultimateCommand == Command.Refresh)
    }

    @Test
    fun whenUserSelectsMobileSiteWhenNotOnMobileSpecificSiteThenUrlNotModified() {
        loadUrl("http://example.com")
        setDesktopBrowsingMode(true)
        testee.onChangeBrowserModeClicked()
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

        assertCommandIssued<Command.OpenInNewTab> {
            assertNotNull(sourceTabId)
        }
    }

    @Test
    fun whenSiteLoadedAndUserSelectsToAddBookmarkThenAddBookmarkCommandSentWithUrlAndTitle() = runTest {
        val url = "http://foo.com"
        val title = "Foo Title"
        val bookmark = Bookmark(id = 0, title = title, url = url, parentId = 0)
        whenever(mockBookmarksRepository.insert(title = anyString(), url = anyString(), parentId = anyLong())).thenReturn(bookmark)
        loadUrl(url = url)
        testee.titleReceived(newTitle = title)
        testee.onBookmarkMenuClicked()
        val command = captureCommands().value as Command.ShowSavedSiteAddedConfirmation
        assertEquals(url, command.savedSiteChangedViewState.savedSite.url)
        assertEquals(title, command.savedSiteChangedViewState.savedSite.title)
    }

    @Test
    fun whenNoSiteAndUserSelectsToAddBookmarkThenBookmarkIsNotAdded() = runTest {
        val bookmark = Bookmark(id = 0, title = "A title", url = "www.example.com", parentId = 0)
        whenever(mockBookmarksRepository.insert(anyString(), anyString(), anyLong())).thenReturn(bookmark)

        testee.onBookmarkMenuClicked()

        verify(mockBookmarksRepository, times(0)).insert(bookmark)
    }

    @Test
    fun whenPrivacyProtectionMenuClickedAndSiteNotInWhiteListThenSiteAddedToWhitelistAndPixelSentAndPageRefreshed() = runTest {
        whenever(mockUserWhitelistDao.contains("www.example.com")).thenReturn(false)
        loadUrl("http://www.example.com/home.html")
        testee.onPrivacyProtectionMenuClicked()
        verify(mockUserWhitelistDao).insert(UserWhitelistedDomain("www.example.com"))
        verify(mockPixel).fire(AppPixelName.BROWSER_MENU_WHITELIST_ADD)
        verify(mockCommandObserver).onChanged(Command.Refresh)
    }

    @Test
    fun whenPrivacyProtectionMenuClickedForWhiteListedSiteThenSiteRemovedFromWhitelistAndPixelSentAndPageRefreshed() = runTest {
        whenever(mockUserWhitelistDao.contains("www.example.com")).thenReturn(true)
        loadUrl("http://www.example.com/home.html")
        testee.onPrivacyProtectionMenuClicked()
        verify(mockUserWhitelistDao).delete(UserWhitelistedDomain("www.example.com"))
        verify(mockPixel).fire(AppPixelName.BROWSER_MENU_WHITELIST_REMOVE)
        verify(mockCommandObserver).onChanged(Command.Refresh)
    }

    @Test
    fun whenOnSiteAndBrokenSiteSelectedThenBrokenSiteFeedbackCommandSentWithUrl() = runTest {
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
    fun whenUrlNullThenSetBrowserNotShowing() = runTest {
        testee.loadData("id", null, false, false)
        testee.determineShowBrowser()
        assertEquals(false, testee.browserViewState.value?.browserShowing)
    }

    @Test
    fun whenUrlBlankThenSetBrowserNotShowing() = runTest {
        testee.loadData("id", "  ", false, false)
        testee.determineShowBrowser()
        assertEquals(false, testee.browserViewState.value?.browserShowing)
    }

    @Test
    fun whenUrlPresentThenSetBrowserShowing() = runTest {
        testee.loadData("id", "https://example.com", false, false)
        testee.determineShowBrowser()
        assertEquals(true, testee.browserViewState.value?.browserShowing)
    }

    @Test
    fun whenFavoritesOnboardingAndSiteLoadedThenHighglightMenuButton() = runTest {
        testee.loadData("id", "https://example.com", false, true)
        testee.determineShowBrowser()
        assertEquals(true, testee.browserViewState.value?.showMenuButton?.isHighlighted())
    }

    @Test
    fun whenFavoritesOnboardingAndUserOpensOptionsMenuThenHighglightAddFavoriteOption() = runTest {
        testee.loadData("id", "https://example.com", false, true)
        testee.determineShowBrowser()

        testee.onBrowserMenuClicked()

        assertEquals(true, testee.browserViewState.value?.addFavorite?.isHighlighted())
    }

    @Test
    fun whenFavoritesOnboardingAndUserClosesOptionsMenuThenMenuButtonNotHighlighted() = runTest {
        testee.loadData("id", "https://example.com", false, true)
        testee.determineShowBrowser()

        testee.onBrowserMenuClosed()

        assertEquals(false, testee.browserViewState.value?.addFavorite?.isHighlighted())
    }

    @Test
    fun whenFavoritesOnboardingAndUserClosesOptionsMenuThenLoadingNewSiteDoesNotHighlightMenuOption() = runTest {
        testee.loadData("id", "https://example.com", false, true)
        testee.determineShowBrowser()
        testee.onBrowserMenuClicked()
        testee.onBrowserMenuClosed()

        testee.determineShowBrowser()

        assertEquals(false, testee.browserViewState.value?.addFavorite?.isHighlighted())
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
            assertNull(sourceTabId)
        }
    }

    @Test
    fun whenUserClicksOnErrorActionThenOpenCurrentTabIsClosed() {
        givenOneActiveTabSelected()
        testee.recoverFromRenderProcessGone()
        verify(mockCommandObserver, atLeastOnce()).onChanged(commandCaptor.capture())
        val showErrorWithAction = commandCaptor.value as Command.ShowErrorWithAction

        showErrorWithAction.action()

        runTest {
            verify(mockTabRepository).deleteTabAndSelectSource(selectedTabLiveData.value!!.tabId)
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
    fun whenBookmarkSuggestionSubmittedThenAutoCompleteBookmarkSelectionPixelSent() = runTest {
        whenever(mockBookmarksRepository.hasBookmarks()).thenReturn(true)
        val suggestion = AutoCompleteBookmarkSuggestion("example", "Example", "https://example.com")
        testee.autoCompleteViewState.value = autoCompleteViewState().copy(searchResults = AutoCompleteResult("", listOf(suggestion)))
        testee.fireAutocompletePixel(suggestion)
        verify(mockPixel).fire(AppPixelName.AUTOCOMPLETE_BOOKMARK_SELECTION, pixelParams(showedBookmarks = true, bookmarkCapable = true))
    }

    @Test
    fun whenSearchSuggestionSubmittedWithBookmarksThenAutoCompleteSearchSelectionPixelSent() = runTest {
        whenever(mockBookmarksRepository.hasBookmarks()).thenReturn(true)
        val suggestions = listOf(AutoCompleteSearchSuggestion("", false), AutoCompleteBookmarkSuggestion("", "", ""))
        testee.autoCompleteViewState.value = autoCompleteViewState().copy(searchResults = AutoCompleteResult("", suggestions))
        testee.fireAutocompletePixel(AutoCompleteSearchSuggestion("example", false))

        verify(mockPixel).fire(AppPixelName.AUTOCOMPLETE_SEARCH_SELECTION, pixelParams(showedBookmarks = true, bookmarkCapable = true))
    }

    @Test
    fun whenSearchSuggestionSubmittedWithoutBookmarksThenAutoCompleteSearchSelectionPixelSent() = runTest {
        whenever(mockBookmarksRepository.hasBookmarks()).thenReturn(false)
        testee.autoCompleteViewState.value = autoCompleteViewState().copy(searchResults = AutoCompleteResult("", emptyList()))
        testee.fireAutocompletePixel(AutoCompleteSearchSuggestion("example", false))

        verify(mockPixel).fire(AppPixelName.AUTOCOMPLETE_SEARCH_SELECTION, pixelParams(showedBookmarks = false, bookmarkCapable = false))
    }

    @Test
    fun whenUserSelectToEditQueryThenMoveCaretToTheEnd() = runTest {
        testee.onUserSelectedToEditQuery("foo")

        assertCommandIssued<Command.EditWithSelectedQuery>()
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
    fun whenCloseCurrentTabSelectedThenTabDeletedFromRepository() = runTest {
        givenOneActiveTabSelected()
        testee.closeCurrentTab()
        verify(mockTabRepository).deleteTabAndSelectSource(selectedTabLiveData.value!!.tabId)
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
    fun whenUserPressesBackOnATabWithASourceTabThenDeleteCurrentAndSelectSource() = runTest {
        selectedTabLiveData.value = TabEntity("TAB_ID", "https://example.com", position = 0, sourceTabId = "TAB_ID_SOURCE")
        setupNavigation(isBrowsing = true)

        testee.onUserPressedBack()

        verify(mockTabRepository).deleteTabAndSelectSource("TAB_ID")
    }

    @Test
    fun whenScheduledSurveyChangesAndInstalledDaysMatchThenCtaIsSurvey() {
        testee.onSurveyChanged(Survey("abc", "http://example.com", daysInstalled = 1, status = Survey.Status.SCHEDULED), Locale.US)
        assertTrue(testee.ctaViewState.value!!.cta is HomePanelCta.Survey)
    }

    @Test
    fun whenScheduledSurveyChangesAndInstalledDaysDontMatchThenCtaIsNull() {
        testee.onSurveyChanged(Survey("abc", "http://example.com", daysInstalled = 2, status = Survey.Status.SCHEDULED), Locale.US)
        assertNull(testee.ctaViewState.value!!.cta)
    }

    @Test
    fun whenScheduledSurveyIsNullThenCtaIsNotSurvey() {
        testee.onSurveyChanged(null)
        assertFalse(testee.ctaViewState.value!!.cta is HomePanelCta.Survey)
    }

    @Test
    fun whenCtaRefreshedAndAutoAddSupportedAndWidgetNotInstalledThenCtaIsAutoWidget() = runTest {
        whenever(mockWidgetCapabilities.supportsStandardWidgetAdd).thenReturn(true)
        whenever(mockWidgetCapabilities.supportsAutomaticWidgetAdd).thenReturn(true)
        whenever(mockWidgetCapabilities.hasInstalledWidgets).thenReturn(false)
        testee.refreshCta()
        assertEquals(HomePanelCta.AddWidgetAuto, testee.ctaViewState.value!!.cta)
    }

    @Test
    fun whenCtaRefreshedAndAutoAddSupportedAndWidgetAlreadyInstalledThenCtaIsNull() = runTest {
        whenever(mockWidgetCapabilities.supportsStandardWidgetAdd).thenReturn(true)
        whenever(mockWidgetCapabilities.supportsAutomaticWidgetAdd).thenReturn(true)
        whenever(mockWidgetCapabilities.hasInstalledWidgets).thenReturn(true)
        testee.refreshCta()
        assertNull(testee.ctaViewState.value!!.cta)
    }

    @Test
    fun whenCtaRefreshedAndOnlyStandardAddSupportedAndWidgetNotInstalledThenCtaIsInstructionsWidget() = runTest {
        givenExpectedCtaAddWidgetInstructions()
        testee.refreshCta()
        assertEquals(HomePanelCta.AddWidgetInstructions, testee.ctaViewState.value!!.cta)
    }

    @Test
    fun whenCtaRefreshedAndOnlyStandardAddSupportedAndWidgetAlreadyInstalledThenCtaIsNull() = runTest {
        whenever(mockWidgetCapabilities.supportsStandardWidgetAdd).thenReturn(true)
        whenever(mockWidgetCapabilities.supportsAutomaticWidgetAdd).thenReturn(false)
        whenever(mockWidgetCapabilities.hasInstalledWidgets).thenReturn(true)
        testee.refreshCta()
        assertNull(testee.ctaViewState.value!!.cta)
    }

    @Test
    fun whenCtaRefreshedAndStandardAddNotSupportedAndWidgetNotInstalledThenCtaIsNull() = runTest {
        whenever(mockWidgetCapabilities.supportsStandardWidgetAdd).thenReturn(false)
        whenever(mockWidgetCapabilities.supportsAutomaticWidgetAdd).thenReturn(false)
        whenever(mockWidgetCapabilities.hasInstalledWidgets).thenReturn(false)
        testee.refreshCta()
        assertNull(testee.ctaViewState.value!!.cta)
    }

    @Test
    fun whenCtaRefreshedAndStandardAddNotSupportedAndWidgetAlreadyInstalledThenCtaIsNull() = runTest {
        whenever(mockWidgetCapabilities.supportsStandardWidgetAdd).thenReturn(false)
        whenever(mockWidgetCapabilities.supportsAutomaticWidgetAdd).thenReturn(false)
        whenever(mockWidgetCapabilities.hasInstalledWidgets).thenReturn(true)
        testee.refreshCta()
        assertNull(testee.ctaViewState.value!!.cta)
    }

    @Test
    fun whenCtaRefreshedAndIsNewTabIsFalseThenReturnNull() = runTest {
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
    fun whenRegisterDaxBubbleCtaDismissedThenRegisterInDatabase() = runTest {
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
    fun whenUserClickedLegacyAddWidgetCtaButtonThenLaunchAddWidgetCommand() {
        val cta = HomePanelCta.AddWidgetInstructions
        setCta(cta)
        testee.onUserClickCtaOkButton()
        assertCommandIssued<Command.LaunchAddWidget>()
    }

    @Test
    fun whenSurveyCtaDismissedAndNoOtherCtaPossibleCtaIsNull() = runTest {
        givenShownCtas(CtaId.DAX_INTRO, CtaId.DAX_END)
        testee.onSurveyChanged(Survey("abc", "http://example.com", daysInstalled = 1, status = Survey.Status.SCHEDULED))
        testee.onUserDismissedCta()
        assertNull(testee.ctaViewState.value!!.cta)
    }

    @Test
    fun whenSurveyCtaDismissedAndWidgetCtaIsPossibleThenNextCtaIsWidget() = runTest {
        whenever(mockWidgetCapabilities.supportsStandardWidgetAdd).thenReturn(true)
        whenever(mockWidgetCapabilities.supportsAutomaticWidgetAdd).thenReturn(true)
        whenever(mockWidgetCapabilities.hasInstalledWidgets).thenReturn(false)

        testee.onSurveyChanged(Survey("abc", "http://example.com", daysInstalled = 1, status = Survey.Status.SCHEDULED))
        testee.onUserDismissedCta()
        assertEquals(HomePanelCta.AddWidgetAuto, testee.ctaViewState.value!!.cta)
    }

    @Test
    fun whenUserDismissedCtaThenFirePixel() = runTest {
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
    fun whenUserDismissedCtaThenRegisterInDatabase() = runTest {
        val cta = HomePanelCta.AddWidgetAuto
        setCta(cta)
        testee.onUserDismissedCta()
        verify(mockDismissedCtaDao).insert(DismissedCta(cta.ctaId))
    }

    @Test
    fun whenUserDismissedSurveyCtaThenDoNotRegisterInDatabase() = runTest {
        val cta = HomePanelCta.Survey(Survey("abc", "http://example.com", daysInstalled = 1, status = Survey.Status.SCHEDULED))
        setCta(cta)
        testee.onUserDismissedCta()
        verify(mockDismissedCtaDao, never()).insert(DismissedCta(cta.ctaId))
    }

    @Test
    fun whenUserDismissedSurveyCtaThenCancelScheduledSurveys() = runTest {
        val cta = HomePanelCta.Survey(Survey("abc", "http://example.com", daysInstalled = 1, status = Survey.Status.SCHEDULED))
        setCta(cta)
        testee.onUserDismissedCta()
        verify(mockSurveyDao).cancelScheduledSurveys()
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
    fun whenOnBrokenSiteSelectedOpenBokenSiteFeedback() = runTest {
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
        verify(mockPixel).fire(AppPixelName.FIREPROOF_WEBSITE_ADDED)
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
        verify(mockPixel).fire(AppPixelName.FIREPROOF_WEBSITE_REMOVE)
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
        verify(mockPixel).fire(AppPixelName.FIREPROOF_WEBSITE_UNDO)
    }

    @Test
    fun whenUserFireproofsWebsiteFromLoginDialogThenShowConfirmationIsIssuedWithExpectedDomain() = runTest {
        whenever(fireproofDialogsEventHandler.onUserConfirmedFireproofDialog(anyString())).doAnswer {
            val domain = it.arguments.first() as String
            fireproofDialogsEventHandlerLiveData.postValue(FireproofDialogsEventHandler.Event.FireproofWebSiteSuccess(FireproofWebsiteEntity(domain)))
        }

        testee.onUserConfirmedFireproofDialog("login.example.com")

        assertCommandIssued<Command.ShowFireproofWebSiteConfirmation> {
            assertEquals("login.example.com", this.fireproofWebsiteEntity.domain)
        }
    }

    @Test
    fun whenAskToDisableLoginDetectionEventReceivedThenAskUserToDisableLoginDetection() = runTest {
        whenever(fireproofDialogsEventHandler.onUserDismissedFireproofLoginDialog()).doAnswer {
            fireproofDialogsEventHandlerLiveData.postValue(FireproofDialogsEventHandler.Event.AskToDisableLoginDetection)
        }

        testee.onUserDismissedFireproofLoginDialog()

        assertCommandIssued<Command.AskToDisableLoginDetection>()
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
        whenever(mockVariantManager.getVariant()).thenReturn(ACTIVE_VARIANTS.first { it.key == "mi" })

        loginEventLiveData.value = givenLoginDetected("example.com")
        assertCommandIssued<Command.AskToFireproofWebsite> {
            assertEquals(FireproofWebsiteEntity("example.com"), this.fireproofWebsite)
        }
    }

    @Test
    fun whenLoginDetectedAndIsFireproofExperimentAndAppLoginDetectionEnabledThenFireproofWebsite() = runTest {
        whenever(mockVariantManager.getVariant()).thenReturn(ACTIVE_VARIANTS.first { it.key == "mj" })
        whenever(mockSettingsStore.appLoginDetection).thenReturn(true)

        loginEventLiveData.value = givenLoginDetected("example.com")

        assertCommandNotIssued<Command.AskToFireproofWebsite>()
        verify(fireproofDialogsEventHandler).onUserConfirmedFireproofDialog("example.com")
    }

    @Test
    fun whenLoginDetectedAndIsFireproofExperimentAndAppLoginDetectionDisabledThenDoNothing() = runTest {
        whenever(mockVariantManager.getVariant()).thenReturn(ACTIVE_VARIANTS.first { it.key == "mj" })
        whenever(mockSettingsStore.appLoginDetection).thenReturn(false)

        loginEventLiveData.value = givenLoginDetected("example.com")

        assertCommandNotIssued<Command.AskToFireproofWebsite>()
        verify(fireproofDialogsEventHandler, never()).onUserConfirmedFireproofDialog("example.com")
    }

    @Test
    fun whenUserBrowsingPressesBackThenCannotAddBookmarkOrFavorite() {
        setupNavigation(skipHome = false, isBrowsing = true, canGoBack = false)
        assertTrue(testee.onUserPressedBack())
        assertFalse(browserViewState().canSaveSite)
        assertFalse(browserViewState().addFavorite.isEnabled())
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
        assertFalse(browserViewState().canChangePrivacyProtection)
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
    fun whenUserBrowsingPressesBackAndForwardThenCanAddBookmarkOrFavorite() {
        setupNavigation(skipHome = false, isBrowsing = true, canGoBack = false)
        testee.onUserPressedBack()
        testee.onUserPressedForward()
        assertTrue(browserViewState().canSaveSite)
        assertTrue(browserViewState().addFavorite.isEnabled())
    }

    @Test
    fun whenUserBrowsingPressesBackAndForwardThenCanWhitelist() {
        setupNavigation(skipHome = false, isBrowsing = true, canGoBack = false)
        testee.onUserPressedBack()
        testee.onUserPressedForward()
        assertTrue(browserViewState().canChangePrivacyProtection)
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
    fun whenBrowsingDDGSiteAndPrivacyGradeIsVisibleThenDaxIconIsVisible() {
        val url = "https://duckduckgo.com?q=test%20search"
        loadUrl(url, isBrowserShowing = true)
        assertTrue(browserViewState().showDaxIcon)
        assertFalse(browserViewState().showSearchIcon)
    }

    @Test
    fun whenBrowsingNonDDGSiteAndPrivacyGradeIsVisibleThenDaxIconIsNotVisible() {
        val url = "https://example.com"
        loadUrl(url, isBrowserShowing = true)
        assertFalse(browserViewState().showDaxIcon)
        assertTrue(browserViewState().showPrivacyGrade)
    }

    @Test
    fun whenNotBrowsingAndDDGUrlPresentThenDaxIconIsNotVisible() {
        loadUrl("https://duckduckgo.com?q=test%20search", isBrowserShowing = false)
        assertFalse(browserViewState().showDaxIcon)
    }

    @Test
    fun whenQueryIsNotHierarchicalThenUnsupportedOperationExceptionIsHandled() {
        whenever(mockOmnibarConverter.convertQueryToUrl("about:blank", null)).thenReturn("about:blank")
        testee.onUserSubmittedQuery("about:blank")
    }

    @Test
    fun whenDosAttackDetectedThenErrorIsShown() {
        testee.dosAttackDetected()
        assertCommandIssued<Command.ShowErrorWithAction>()
    }

    @Test
    fun whenDeviceLocationSharingIsDisabledThenSitePermissionIsDenied() = runTest {
        val domain = "https://www.example.com/"

        givenDeviceLocationSharingIsEnabled(false)
        givenCurrentSite(domain)
        givenNewPermissionRequestFromDomain(domain)

        verify(geoLocationPermissions).clear(domain)
    }

    @Test
    fun whenCurrentDomainAndPermissionRequestingDomainAreDifferentThenSitePermissionIsDenied() = runTest {
        givenDeviceLocationSharingIsEnabled(true)
        givenCurrentSite("https://wwww.example.com/")
        givenNewPermissionRequestFromDomain("https://wwww.anotherexample.com/")

        verify(geoLocationPermissions).clear("https://wwww.anotherexample.com/")
    }

    @Test
    fun whenDomainRequestsSitePermissionThenAppChecksSystemLocationPermission() = runTest {
        val domain = "https://www.example.com/"

        givenDeviceLocationSharingIsEnabled(true)
        givenLocationPermissionIsEnabled(true)
        givenCurrentSite(domain)
        givenNewPermissionRequestFromDomain(domain)

        assertCommandIssued<Command.CheckSystemLocationPermission>()
    }

    @Test
    fun whenDomainRequestsSitePermissionAndAlreadyRepliedThenAppChecksSystemLocationPermission() = runTest {
        val domain = "https://www.example.com/"

        givenDeviceLocationSharingIsEnabled(true)
        givenLocationPermissionIsEnabled(true)
        givenCurrentSite(domain)
        givenUserAlreadySelectedPermissionForDomain(domain, LocationPermissionType.DENY_ALWAYS)

        givenNewPermissionRequestFromDomain(domain)

        verify(geoLocationPermissions).clear(domain)
    }

    @Test
    fun whenDomainRequestsSitePermissionAndAllowedThenAppChecksSystemLocationPermission() = runTest {
        val domain = "https://www.example.com/"

        givenDeviceLocationSharingIsEnabled(true)
        givenLocationPermissionIsEnabled(true)
        givenCurrentSite(domain)
        givenUserAlreadySelectedPermissionForDomain(domain, LocationPermissionType.ALLOW_ALWAYS)

        givenNewPermissionRequestFromDomain(domain)

        assertCommandIssued<Command.CheckSystemLocationPermission>()
    }

    @Test
    fun whenDomainRequestsSitePermissionAndUserAllowedSessionPermissionThenPermissionIsAllowed() = runTest {
        val domain = "https://www.example.com/"

        givenDeviceLocationSharingIsEnabled(true)
        givenLocationPermissionIsEnabled(true)
        givenCurrentSite(domain)
        givenNewPermissionRequestFromDomain(domain)
        testee.onSiteLocationPermissionSelected(domain, LocationPermissionType.ALLOW_ONCE)

        givenNewPermissionRequestFromDomain(domain)

        assertCommandIssuedTimes<Command.CheckSystemLocationPermission>(times = 1)
    }

    @Test
    fun whenAppLocationPermissionIsDeniedThenSitePermissionIsDenied() = runTest {
        val domain = "https://www.example.com/"
        givenDeviceLocationSharingIsEnabled(true)
        givenLocationPermissionIsEnabled(false)
        givenCurrentSite(domain)
        givenNewPermissionRequestFromDomain(domain)

        verify(geoLocationPermissions).clear(domain)
    }

    @Test
    fun whenSystemPermissionIsDeniedThenSitePermissionIsCleared() = runTest {
        val domain = "https://www.example.com/"
        givenDeviceLocationSharingIsEnabled(true)
        givenLocationPermissionIsEnabled(true)
        givenCurrentSite(domain)
        givenNewPermissionRequestFromDomain(domain)

        testee.onSystemLocationPermissionDeniedOneTime()

        verify(mockPixel).fire(AppPixelName.PRECISE_LOCATION_SETTINGS_LOCATION_PERMISSION_DISABLE)
        verify(geoLocationPermissions).clear(domain)
    }

    @Test
    fun whenUserGrantsSystemLocationPermissionThenSettingsLocationPermissionShoulbBeEnabled() = runTest {
        val domain = "https://www.example.com/"
        givenDeviceLocationSharingIsEnabled(true)
        givenLocationPermissionIsEnabled(true)
        givenCurrentSite(domain)
        givenNewPermissionRequestFromDomain(domain)

        testee.onSystemLocationPermissionGranted()

        verify(mockSettingsStore).appLocationPermission = true
    }

    @Test
    fun whenUserGrantsSystemLocationPermissionThenPixelIsFired() = runTest {
        val domain = "https://www.example.com/"
        givenDeviceLocationSharingIsEnabled(true)
        givenLocationPermissionIsEnabled(true)
        givenCurrentSite(domain)
        givenNewPermissionRequestFromDomain(domain)

        testee.onSystemLocationPermissionGranted()

        verify(mockPixel).fire(AppPixelName.PRECISE_LOCATION_SETTINGS_LOCATION_PERMISSION_ENABLE)
    }

    @Test
    fun whenUserChoosesToAlwaysAllowSitePermissionThenGeoPermissionIsAllowed() = runTest {
        val domain = "https://www.example.com/"
        givenDeviceLocationSharingIsEnabled(true)
        givenLocationPermissionIsEnabled(true)
        givenCurrentSite(domain)
        givenUserAlreadySelectedPermissionForDomain(domain, LocationPermissionType.ALLOW_ALWAYS)
        givenNewPermissionRequestFromDomain(domain)

        testee.onSystemLocationPermissionGranted()

        verify(geoLocationPermissions, atLeastOnce()).allow(domain)
    }

    @Test
    fun whenUserChoosesToAlwaysDenySitePermissionThenGeoPermissionIsAllowed() = runTest {
        val domain = "https://www.example.com/"
        givenDeviceLocationSharingIsEnabled(true)
        givenLocationPermissionIsEnabled(true)
        givenCurrentSite(domain)
        givenUserAlreadySelectedPermissionForDomain(domain, LocationPermissionType.DENY_ALWAYS)
        givenNewPermissionRequestFromDomain(domain)

        testee.onSystemLocationPermissionGranted()

        verify(geoLocationPermissions, atLeastOnce()).clear(domain)
    }

    @Test
    fun whenUserChoosesToAllowSitePermissionThenGeoPermissionIsAllowed() = runTest {
        val domain = "https://www.example.com/"
        givenDeviceLocationSharingIsEnabled(true)
        givenLocationPermissionIsEnabled(true)
        givenCurrentSite(domain)
        givenUserAlreadySelectedPermissionForDomain(domain, LocationPermissionType.ALLOW_ONCE)
        givenNewPermissionRequestFromDomain(domain)

        testee.onSystemLocationPermissionGranted()

        assertCommandIssued<Command.AskDomainPermission>()
    }

    @Test
    fun whenUserChoosesToDenySitePermissionThenGeoPermissionIsAllowed() = runTest {
        val domain = "https://www.example.com/"
        givenDeviceLocationSharingIsEnabled(true)
        givenLocationPermissionIsEnabled(true)
        givenCurrentSite(domain)
        givenUserAlreadySelectedPermissionForDomain(domain, LocationPermissionType.DENY_ONCE)
        givenNewPermissionRequestFromDomain(domain)

        testee.onSystemLocationPermissionGranted()

        assertCommandIssued<Command.AskDomainPermission>()
    }

    @Test
    fun whenNewDomainRequestsForPermissionThenUserShouldBeAskedToGivePermission() = runTest {
        val domain = "https://www.example.com/"
        givenDeviceLocationSharingIsEnabled(true)
        givenLocationPermissionIsEnabled(true)
        givenCurrentSite(domain)
        givenNewPermissionRequestFromDomain(domain)

        testee.onSystemLocationPermissionGranted()

        assertCommandIssued<Command.AskDomainPermission>()
    }

    @Test
    fun whenSystemLocationPermissionIsDeniedThenSitePermissionIsDenied() = runTest {
        val domain = "https://www.example.com/"
        givenDeviceLocationSharingIsEnabled(true)
        givenLocationPermissionIsEnabled(true)
        givenCurrentSite(domain)
        givenNewPermissionRequestFromDomain(domain)

        testee.onSystemLocationPermissionNotAllowed()

        verify(mockPixel).fire(AppPixelName.PRECISE_LOCATION_SYSTEM_DIALOG_LATER)
        verify(geoLocationPermissions).clear(domain)
    }

    @Test
    fun whenSystemLocationPermissionIsNeverAllowedThenSitePermissionIsDenied() = runTest {
        val domain = "https://www.example.com/"
        givenDeviceLocationSharingIsEnabled(true)
        givenLocationPermissionIsEnabled(true)
        givenCurrentSite(domain)
        givenNewPermissionRequestFromDomain(domain)

        testee.onSystemLocationPermissionNeverAllowed()

        verify(mockPixel).fire(AppPixelName.PRECISE_LOCATION_SYSTEM_DIALOG_NEVER)
        verify(geoLocationPermissions).clear(domain)
        assertEquals(locationPermissionsDao.getPermission(domain)!!.permission, LocationPermissionType.DENY_ALWAYS)
    }

    @Test
    fun whenSystemLocationPermissionIsAllowedThenAppAsksForSystemPermission() = runTest {
        val domain = "https://www.example.com/"
        givenDeviceLocationSharingIsEnabled(true)
        givenLocationPermissionIsEnabled(true)
        givenCurrentSite(domain)
        givenNewPermissionRequestFromDomain(domain)

        testee.onSystemLocationPermissionAllowed()

        assertCommandIssued<Command.RequestSystemLocationPermission>()
    }

    @Test
    fun whenUserDeniesSitePermissionThenSitePermissionIsDenied() = runTest {
        val domain = "https://www.example.com/"
        givenDeviceLocationSharingIsEnabled(true)
        givenLocationPermissionIsEnabled(true)
        givenCurrentSite(domain)
        givenNewPermissionRequestFromDomain(domain)

        testee.onSiteLocationPermissionAlwaysDenied()

        verify(geoLocationPermissions).clear(domain)
    }

    @Test
    fun whenUserVisitsDomainWithPermanentLocationPermissionThenMessageIsShown() = runTest {
        val domain = "https://www.example.com/"

        givenUserAlreadySelectedPermissionForDomain(domain, LocationPermissionType.ALLOW_ALWAYS)
        givenCurrentSite(domain)
        givenDeviceLocationSharingIsEnabled(true)
        givenLocationPermissionIsEnabled(true)

        loadUrl("https://www.example.com", isBrowserShowing = true)

        assertCommandIssued<Command.ShowDomainHasPermissionMessage>()
    }

    @Test
    fun whenUserVisitsDomainWithoutPermanentLocationPermissionThenMessageIsNotShown() = runTest {
        val domain = "https://www.example.com/"

        givenUserAlreadySelectedPermissionForDomain(domain, LocationPermissionType.DENY_ALWAYS)
        givenCurrentSite(domain)
        givenDeviceLocationSharingIsEnabled(true)
        givenLocationPermissionIsEnabled(true)

        loadUrl("https://www.example.com", isBrowserShowing = true)

        assertCommandNotIssued<Command.ShowDomainHasPermissionMessage>()
    }

    @Test
    fun whenUserVisitsDomainWithoutLocationPermissionThenMessageIsNotShown() = runTest {
        val domain = "https://www.example.com"
        givenDeviceLocationSharingIsEnabled(true)
        givenLocationPermissionIsEnabled(true)
        givenCurrentSite(domain)
        loadUrl("https://www.example.com", isBrowserShowing = true)

        assertCommandNotIssued<Command.ShowDomainHasPermissionMessage>()
    }

    @Test
    fun whenUserVisitsDomainAndLocationIsNotEnabledThenMessageIsNotShown() = runTest {
        val domain = "https://www.example.com"
        givenDeviceLocationSharingIsEnabled(true)
        givenLocationPermissionIsEnabled(false)
        givenCurrentSite(domain)

        loadUrl("https://www.example.com", isBrowserShowing = true)

        assertCommandNotIssued<Command.ShowDomainHasPermissionMessage>()
    }

    @Test
    fun whenUserRefreshesASiteLocationMessageIsNotShownAgain() = runTest {
        val domain = "https://www.example.com/"

        givenUserAlreadySelectedPermissionForDomain(domain, LocationPermissionType.ALLOW_ALWAYS)
        givenCurrentSite(domain)
        givenDeviceLocationSharingIsEnabled(true)
        givenLocationPermissionIsEnabled(true)

        loadUrl("https://www.example.com", isBrowserShowing = true)
        loadUrl("https://www.example.com", isBrowserShowing = true)
        assertCommandIssuedTimes<Command.ShowDomainHasPermissionMessage>(1)
    }

    @Test
    fun whenUserSelectsPermissionAndRefreshesPageThenLocationMessageIsNotShown() = runTest {
        val domain = "http://example.com"

        givenCurrentSite(domain)
        givenDeviceLocationSharingIsEnabled(true)
        givenLocationPermissionIsEnabled(true)

        testee.onSiteLocationPermissionSelected(domain, LocationPermissionType.ALLOW_ALWAYS)

        loadUrl(domain, isBrowserShowing = true)

        assertCommandNotIssued<Command.ShowDomainHasPermissionMessage>()
    }

    @Test
    fun whenSystemLocationPermissionIsDeniedThenSiteLocationPermissionIsAlwaysDenied() = runTest {
        val domain = "https://www.example.com/"
        givenDeviceLocationSharingIsEnabled(true)
        givenLocationPermissionIsEnabled(true)
        givenCurrentSite(domain)
        givenNewPermissionRequestFromDomain(domain)

        testee.onSystemLocationPermissionDeniedOneTime()

        verify(geoLocationPermissions).clear(domain)
    }

    @Test
    fun whenSystemLocationPermissionIsDeniedForeverThenSiteLocationPermissionIsAlwaysDenied() = runTest {
        val domain = "https://www.example.com/"
        givenDeviceLocationSharingIsEnabled(true)
        givenLocationPermissionIsEnabled(true)
        givenCurrentSite(domain)
        givenNewPermissionRequestFromDomain(domain)

        testee.onSystemLocationPermissionDeniedForever()

        verify(geoLocationPermissions).clear(domain)
    }

    @Test
    fun whenSystemLocationPermissionIsDeniedForeverThenSettingsFlagIsUpdated() = runTest {
        val domain = "https://www.example.com/"
        givenDeviceLocationSharingIsEnabled(true)
        givenLocationPermissionIsEnabled(true)
        givenCurrentSite(domain)
        givenNewPermissionRequestFromDomain(domain)

        testee.onSystemLocationPermissionDeniedForever()

        verify(mockSettingsStore).appLocationPermissionDeniedForever = true
    }

    @Test
    fun whenSystemLocationIsGrantedThenSettingsFlagIsUpdated() = runTest {
        val domain = "https://www.example.com/"
        givenDeviceLocationSharingIsEnabled(true)
        givenLocationPermissionIsEnabled(true)
        givenCurrentSite(domain)
        givenNewPermissionRequestFromDomain(domain)

        testee.onSystemLocationPermissionGranted()

        verify(mockSettingsStore).appLocationPermissionDeniedForever = false
    }

    @Test
    fun whenPrefetchFaviconThenFetchFaviconForCurrentTab() = runTest {
        val url = "https://www.example.com/"
        givenCurrentSite(url)
        testee.prefetchFavicon(url)

        verify(mockFaviconManager).tryFetchFaviconForUrl("TAB_ID", url)
    }

    @Test
    fun whenPrefetchFaviconAndFaviconExistsThenUpdateTabFavicon() = runTest {
        val url = "https://www.example.com/"
        val file = File("test")
        givenCurrentSite(url)
        whenever(mockFaviconManager.tryFetchFaviconForUrl(any(), any())).thenReturn(file)

        testee.prefetchFavicon(url)

        verify(mockTabRepository).updateTabFavicon("TAB_ID", file.name)
    }

    @Test
    fun whenPrefetchFaviconAndFaviconDoesNotExistThenDoNotCallUpdateTabFavicon() = runTest {
        whenever(mockFaviconManager.tryFetchFaviconForUrl(any(), any())).thenReturn(null)

        testee.prefetchFavicon("url")

        verify(mockTabRepository, never()).updateTabFavicon(any(), any())
    }

    @Test
    fun whenIconReceivedThenStoreFavicon() = runTest {
        givenOneActiveTabSelected()
        val bitmap: Bitmap = Bitmap.createBitmap(1, 1, Bitmap.Config.RGB_565)

        testee.iconReceived("https://example.com", bitmap)

        verify(mockFaviconManager).storeFavicon("TAB_ID", FaviconSource.ImageFavicon(bitmap, "https://example.com"))
    }

    @Test
    fun whenIconReceivedIfCorrectlySavedThenUpdateTabFavicon() = runTest {
        givenOneActiveTabSelected()
        val bitmap: Bitmap = Bitmap.createBitmap(1, 1, Bitmap.Config.RGB_565)
        val file = File("test")
        whenever(mockFaviconManager.storeFavicon(any(), any())).thenReturn(file)

        testee.iconReceived("https://example.com", bitmap)

        verify(mockTabRepository).updateTabFavicon("TAB_ID", file.name)
    }

    @Test
    fun whenIconReceivedIfNotCorrectlySavedThenDoNotUpdateTabFavicon() = runTest {
        givenOneActiveTabSelected()
        val bitmap: Bitmap = Bitmap.createBitmap(1, 1, Bitmap.Config.RGB_565)
        whenever(mockFaviconManager.storeFavicon(any(), any())).thenReturn(null)

        testee.iconReceived("https://example.com", bitmap)

        verify(mockTabRepository, never()).updateTabFavicon(any(), any())
    }

    @Test
    fun whenIconReceivedFromPreviousUrlThenDontUpdateTabFavicon() = runTest {
        givenOneActiveTabSelected()
        val bitmap: Bitmap = Bitmap.createBitmap(1, 1, Bitmap.Config.RGB_565)
        val file = File("test")
        whenever(mockFaviconManager.storeFavicon(any(), any())).thenReturn(file)

        testee.iconReceived("https://notexample.com", bitmap)

        verify(mockTabRepository, never()).updateTabFavicon("TAB_ID", file.name)
    }

    @Test
    fun whenUrlIconReceivedThenStoreFavicon() = runTest {
        givenOneActiveTabSelected()

        testee.iconReceived("https://example.com", "https://example.com/favicon.png")

        verify(mockFaviconManager).storeFavicon("TAB_ID", FaviconSource.UrlFavicon("https://example.com/favicon.png", "https://example.com"))
    }

    @Test
    fun whenUrlIconReceivedIfCorrectlySavedThenUpdateTabFavicon() = runTest {
        givenOneActiveTabSelected()
        val file = File("test")
        whenever(mockFaviconManager.storeFavicon(any(), any())).thenReturn(file)

        testee.iconReceived("https://example.com", "https://example.com/favicon.png")

        verify(mockTabRepository).updateTabFavicon("TAB_ID", file.name)
    }

    @Test
    fun whenUrlIconReceivedIfNotCorrectlySavedThenDoNotUpdateTabFavicon() = runTest {
        givenOneActiveTabSelected()
        whenever(mockFaviconManager.storeFavicon(any(), any())).thenReturn(null)

        testee.iconReceived("https://example.com", "https://example.com/favicon.png")

        verify(mockTabRepository, never()).updateTabFavicon(any(), any())
    }

    @Test
    fun whenUrlIconReceivedFromPreviousUrlThenDontUpdateTabFavicon() = runTest {
        givenOneActiveTabSelected()
        val file = File("test")
        whenever(mockFaviconManager.storeFavicon(any(), any())).thenReturn(file)

        testee.iconReceived("https://notexample.com", "https://example.com/favicon.png")

        verify(mockFaviconManager, never()).storeFavicon(any(), any())
    }

    @Test
    fun whenOnSiteLocationPermissionSelectedAndPermissionIsAllowAlwaysThenPersistFavicon() = runTest {
        val url = "http://example.com"
        val permission = LocationPermissionType.ALLOW_ALWAYS
        givenNewPermissionRequestFromDomain(url)

        testee.onSiteLocationPermissionSelected(url, permission)

        verify(mockFaviconManager).persistCachedFavicon(any(), eq(url))
    }

    @Test
    fun whenOnSiteLocationPermissionSelectedAndPermissionIsDenyAlwaysThenPersistFavicon() = runTest {
        val url = "http://example.com"
        val permission = LocationPermissionType.DENY_ALWAYS
        givenNewPermissionRequestFromDomain(url)

        testee.onSiteLocationPermissionSelected(url, permission)

        verify(mockFaviconManager).persistCachedFavicon(any(), eq(url))
    }

    @Test
    fun whenOnSystemLocationPermissionNeverAllowedThenPersistFavicon() = runTest {
        val url = "http://example.com"
        givenNewPermissionRequestFromDomain(url)

        testee.onSystemLocationPermissionNeverAllowed()

        verify(mockFaviconManager).persistCachedFavicon(any(), eq(url))
    }

    @Test
    fun whenBookmarkAddedThenPersistFavicon() = runTest {
        val url = "http://example.com"
        val title = "A title"
        val bookmark = Bookmark(id = 0, title = title, url = url, parentId = 0)
        whenever(mockBookmarksRepository.insert(title = anyString(), url = anyString(), parentId = anyLong())).thenReturn(bookmark)
        loadUrl(url = url, title = title)

        testee.onBookmarkMenuClicked()

        verify(mockFaviconManager).persistCachedFavicon(any(), eq(url))
    }

    @Test
    fun whenBookmarkAddedButUrlIsNullThenDoNotPersistFavicon() = runTest {
        loadUrl(null, "A title")

        testee.onBookmarkMenuClicked()

        verify(mockFaviconManager, never()).persistCachedFavicon(any(), any())
    }

    @Test
    fun whenFireproofWebsiteAddedThenPersistFavicon() = runTest {
        val url = "http://example.com"
        loadUrl(url, isBrowserShowing = true)

        testee.onFireproofWebsiteMenuClicked()

        assertCommandIssued<Command.ShowFireproofWebSiteConfirmation> {
            verify(mockFaviconManager).persistCachedFavicon(any(), eq(this.fireproofWebsiteEntity.domain))
        }
    }

    @Test
    fun whenOnPinPageToHomeSelectedThenAddHomeShortcutCommandIssuedWithFavicon() = runTest {
        val url = "http://example.com"
        val bitmap: Bitmap = Bitmap.createBitmap(1, 1, Bitmap.Config.RGB_565)
        whenever(mockFaviconManager.loadFromDisk(any(), any())).thenReturn(bitmap)
        loadUrl(url, "A title")

        testee.onPinPageToHomeSelected()

        assertCommandIssued<Command.AddHomeShortcut> {
            assertEquals(bitmap, this.icon)
            assertEquals(url, this.url)
            assertEquals("example.com", this.title)
        }
    }

    @Test
    fun whenOnPinPageToHomeSelectedAndFaviconDoesNotExistThenAddHomeShortcutCommandIssuedWithoutFavicon() = runTest {
        val url = "http://example.com"
        whenever(mockFaviconManager.loadFromDisk(any(), any())).thenReturn(null)
        loadUrl(url, "A title")

        testee.onPinPageToHomeSelected()

        assertCommandIssued<Command.AddHomeShortcut> {
            assertNull(this.icon)
            assertEquals(url, this.url)
            assertEquals("example.com", this.title)
        }
    }

    @Test
    fun whenUserSubmittedQueryIfGpcIsEnabledAndUrlIsValidThenAddHeaderToUrl() {
        givenUrlCanUseGpc()
        whenever(mockOmnibarConverter.convertQueryToUrl("foo", null)).thenReturn("foo.com")

        testee.onUserSubmittedQuery("foo")
        verify(mockCommandObserver, atLeastOnce()).onChanged(commandCaptor.capture())

        val command = commandCaptor.lastValue as Navigate
        assertEquals(GPC_HEADER_VALUE, command.headers[GPC_HEADER])
    }

    @Test
    fun whenUserSubmittedQueryIfGpcIsEnabledAndUrlIsNotValidThenDoNotAddHeaderToUrl() {
        val url = "foo.com"
        givenUrlCannotUseGpc(url)
        whenever(mockOmnibarConverter.convertQueryToUrl("foo", null)).thenReturn(url)

        testee.onUserSubmittedQuery("foo")
        verify(mockCommandObserver, atLeastOnce()).onChanged(commandCaptor.capture())

        val command = commandCaptor.lastValue as Navigate
        assertTrue(command.headers.isEmpty())
    }

    @Test
    fun whenUserSubmittedQueryIfGpcIsDisabledThenDoNotAddHeaderToUrl() {
        givenGpcIsDisabled()
        whenever(mockOmnibarConverter.convertQueryToUrl("foo", null)).thenReturn("foo.com")

        testee.onUserSubmittedQuery("foo")
        verify(mockCommandObserver, atLeastOnce()).onChanged(commandCaptor.capture())

        val command = commandCaptor.lastValue as Navigate
        assertTrue(command.headers.isEmpty())
    }

    @Test
    fun whenOnDesktopSiteModeToggledIfGpcIsEnabledAndUrlIsValidThenAddHeaderToUrl() {
        givenUrlCanUseGpc()
        loadUrl("http://m.example.com")
        setDesktopBrowsingMode(false)
        testee.onChangeBrowserModeClicked()
        verify(mockCommandObserver, atLeastOnce()).onChanged(commandCaptor.capture())

        val command = commandCaptor.lastValue as Navigate
        assertEquals(GPC_HEADER_VALUE, command.headers[GPC_HEADER])
    }

    @Test
    fun whenOnDesktopSiteModeToggledIfGpcIsEnabledAndUrlIsNotValidThenDoNotAddHeaderToUrl() {
        givenUrlCannotUseGpc("example.com")
        loadUrl("http://m.example.com")
        setDesktopBrowsingMode(false)
        testee.onChangeBrowserModeClicked()
        verify(mockCommandObserver, atLeastOnce()).onChanged(commandCaptor.capture())

        val command = commandCaptor.lastValue as Navigate
        assertTrue(command.headers.isEmpty())
    }

    @Test
    fun whenOnDesktopSiteModeToggledIfGpcIsDisabledThenDoNotAddHeaderToUrl() {
        givenGpcIsDisabled()
        loadUrl("http://m.example.com")
        setDesktopBrowsingMode(false)
        testee.onChangeBrowserModeClicked()
        verify(mockCommandObserver, atLeastOnce()).onChanged(commandCaptor.capture())

        val command = commandCaptor.lastValue as Navigate
        assertTrue(command.headers.isEmpty())
    }

    @Test
    fun whenExternalAppLinkClickedIfGpcIsEnabledAndUrlIsValidThenAddHeaderToUrl() {
        givenUrlCanUseGpc()
        val intentType = SpecialUrlDetector.UrlType.NonHttpAppLink("query", mock(), "fallback")

        testee.nonHttpAppLinkClicked(intentType)
        verify(mockCommandObserver, atLeastOnce()).onChanged(commandCaptor.capture())

        val command = commandCaptor.lastValue as Command.HandleNonHttpAppLink
        assertEquals(GPC_HEADER_VALUE, command.headers[GPC_HEADER])
    }

    @Test
    fun whenExternalAppLinkClickedIfGpcIsEnabledAndFallbackUrlIsNullThenDoNotAddHeaderToUrl() {
        givenUrlCanUseGpc()
        val intentType = SpecialUrlDetector.UrlType.NonHttpAppLink("query", mock(), null)

        testee.nonHttpAppLinkClicked(intentType)
        verify(mockCommandObserver, atLeastOnce()).onChanged(commandCaptor.capture())

        val command = commandCaptor.lastValue as Command.HandleNonHttpAppLink
        assertTrue(command.headers.isEmpty())
    }

    @Test
    fun whenExternalAppLinkClickedIfGpcIsEnabledAndUrlIsNotValidThenDoNotAddHeaderToUrl() {
        val url = "fallback"
        givenUrlCannotUseGpc(url)
        val intentType = SpecialUrlDetector.UrlType.NonHttpAppLink("query", mock(), url)

        testee.nonHttpAppLinkClicked(intentType)
        verify(mockCommandObserver, atLeastOnce()).onChanged(commandCaptor.capture())

        val command = commandCaptor.lastValue as Command.HandleNonHttpAppLink
        assertTrue(command.headers.isEmpty())
    }

    @Test
    fun whenExternalAppLinkClickedIfGpcIsDisabledThenDoNotAddHeaderToUrl() {
        givenGpcIsDisabled()
        val intentType = SpecialUrlDetector.UrlType.NonHttpAppLink("query", mock(), "fallback")

        testee.nonHttpAppLinkClicked(intentType)
        verify(mockCommandObserver, atLeastOnce()).onChanged(commandCaptor.capture())

        val command = commandCaptor.lastValue as Command.HandleNonHttpAppLink
        assertTrue(command.headers.isEmpty())
    }

    @Test
    fun whenFirePulsingAnimationStartsThenItStopsAfterMoreThanOneHour() = runTest {
        givenFireButtonPulsing()
        val observer = ValueCaptorObserver<BrowserTabViewModel.BrowserViewState>(false)
        testee.browserViewState.observeForever(observer)

        testee.onViewVisible()

        advanceTimeBy(4_600_000)
        verify(mockDismissedCtaDao).insert(DismissedCta(CtaId.DAX_FIRE_BUTTON))
        verify(mockDismissedCtaDao).insert(DismissedCta(CtaId.DAX_FIRE_BUTTON_PULSE))
    }

    @Test
    fun whenRedirectTriggeredByGpcThenGpcRedirectEventSent() {
        testee.redirectTriggeredByGpc()
        verify(mockNavigationAwareLoginDetector).onEvent(NavigationEvent.GpcRedirect)
    }

    @Test
    fun whenProgressIs100ThenRefreshUserAgentCommandSent() {
        loadUrl("http://duckduckgo.com")
        testee.progressChanged(100)

        assertCommandIssued<Command.RefreshUserAgent>()
    }

    @Test
    fun whenRequestFileDownloadAndUrlIsBlobThenConvertBlobToDataUriCommandSent() {
        val blobUrl = "blob:https://example.com/283nasdho23jkasdAjd"
        val mime = "application/plain"

        testee.requestFileDownload(blobUrl, null, mime, true)

        assertCommandIssued<Command.ConvertBlobToDataUri> {
            assertEquals(blobUrl, url)
            assertEquals(mime, mimeType)
        }
    }

    @Test
    fun whenRequestFileDownloadAndUrlIsNotBlobThenRquestFileDownloadCommandSent() {
        val normalUrl = "https://example.com/283nasdho23jkasdAjd"
        val mime = "application/plain"

        testee.requestFileDownload(normalUrl, null, mime, true)

        assertCommandIssued<Command.RequestFileDownload> {
            assertEquals(normalUrl, url)
            assertEquals(mime, mimeType)
            assertNull(contentDisposition)
            assertTrue(requestUserConfirmation)
        }
    }

    @Test
    fun whenChildrenTabClosedIfViewModelIsParentThenChildTabClosedCommandSent() = runTest {
        givenOneActiveTabSelected()

        childClosedTabsSharedFlow.emit("TAB_ID")

        assertCommandIssued<Command.ChildTabClosed>()
    }

    @Test
    fun whenChildrenTabClosedIfViewModelIsNotParentThenChildTabClosedCommandNotSent() = runTest {
        givenOneActiveTabSelected()

        childClosedTabsSharedFlow.emit("other_tab")

        assertCommandNotIssued<Command.ChildTabClosed>()
    }

    @Test
    fun whenConsumeAliasAndCopyToClipboardThenCopyAliasToClipboardCommandSent() {
        whenever(mockEmailManager.getAlias()).thenReturn("alias")

        testee.consumeAliasAndCopyToClipboard()

        assertCommandIssued<Command.CopyAliasToClipboard>()
    }

    @Test
    fun whenConsumeAliasAndCopyToClipboardThenSetNewLastUsedDateCalled() {
        whenever(mockEmailManager.getAlias()).thenReturn("alias")

        testee.consumeAliasAndCopyToClipboard()

        verify(mockEmailManager).setNewLastUsedDate()
    }

    @Test
    fun whenConsumeAliasAndCopyToClipboardThenPixelSent() {
        whenever(mockEmailManager.getAlias()).thenReturn("alias")
        whenever(mockEmailManager.getCohort()).thenReturn("cohort")
        whenever(mockEmailManager.getLastUsedDate()).thenReturn("2021-01-01")

        testee.consumeAlias()

        verify(mockPixel).enqueueFire(
            AppPixelName.EMAIL_USE_ALIAS,
            mapOf(Pixel.PixelParameter.COHORT to "cohort", Pixel.PixelParameter.LAST_USED_DAY to "2021-01-01")
        )
    }

    @Test
    fun whenEmailIsSignedOutThenIsEmailSignedInReturnsFalse() = runTest {
        emailStateFlow.emit(false)

        assertFalse(browserViewState().isEmailSignedIn)
    }

    @Test
    fun whenEmailIsSignedInThenIsEmailSignedInReturnsTrue() = runTest {
        emailStateFlow.emit(true)

        assertTrue(browserViewState().isEmailSignedIn)
    }

    @Test
    fun whenConsumeAliasThenInjectAddressCommandSent() {
        whenever(mockEmailManager.getAlias()).thenReturn("alias")

        testee.consumeAlias()

        assertCommandIssued<Command.InjectEmailAddress> {
            assertEquals("alias", this.address)
        }
    }

    @Test
    fun whenConsumeAliasThenPixelSent() {
        whenever(mockEmailManager.getAlias()).thenReturn("alias")
        whenever(mockEmailManager.getCohort()).thenReturn("cohort")
        whenever(mockEmailManager.getLastUsedDate()).thenReturn("2021-01-01")

        testee.consumeAlias()

        verify(mockPixel).enqueueFire(
            AppPixelName.EMAIL_USE_ALIAS,
            mapOf(Pixel.PixelParameter.COHORT to "cohort", Pixel.PixelParameter.LAST_USED_DAY to "2021-01-01")
        )
    }

    @Test
    fun whenConsumeAliasThenSetNewLastUsedDateCalled() {
        whenever(mockEmailManager.getAlias()).thenReturn("alias")

        testee.consumeAlias()

        verify(mockEmailManager).setNewLastUsedDate()
    }

    @Test
    fun whenCancelAutofillTooltipThenPixelSent() {
        whenever(mockEmailManager.getAlias()).thenReturn("alias")
        whenever(mockEmailManager.getCohort()).thenReturn("cohort")

        testee.cancelAutofillTooltip()

        verify(mockPixel).enqueueFire(AppPixelName.EMAIL_TOOLTIP_DISMISSED, mapOf(Pixel.PixelParameter.COHORT to "cohort"))
    }

    @Test
    fun whenUseAddressThenInjectAddressCommandSent() {
        whenever(mockEmailManager.getEmailAddress()).thenReturn("address")

        testee.useAddress()

        assertCommandIssued<Command.InjectEmailAddress> {
            assertEquals("address", this.address)
        }
    }

    @Test
    fun whenUseAddressThenPixelSent() {
        whenever(mockEmailManager.getEmailAddress()).thenReturn("address")
        whenever(mockEmailManager.getCohort()).thenReturn("cohort")
        whenever(mockEmailManager.getLastUsedDate()).thenReturn("2021-01-01")

        testee.useAddress()

        verify(mockPixel).enqueueFire(
            AppPixelName.EMAIL_USE_ADDRESS,
            mapOf(Pixel.PixelParameter.COHORT to "cohort", Pixel.PixelParameter.LAST_USED_DAY to "2021-01-01")
        )
    }

    @Test
    fun whenUseAddressThenSetNewLastUsedDateCalled() {
        whenever(mockEmailManager.getEmailAddress()).thenReturn("address")

        testee.useAddress()

        verify(mockEmailManager).setNewLastUsedDate()
    }

    @Test
    fun whenShowEmailTooltipIfAddressExistsThenShowEmailTooltipCommandSent() {
        whenever(mockEmailManager.getEmailAddress()).thenReturn("address")

        testee.showEmailTooltip()

        assertCommandIssued<Command.ShowEmailTooltip> {
            assertEquals("address", this.address)
        }
    }

    @Test
    fun whenShowEmailTooltipIfAddressDoesNotExistThenCommandNotSent() {
        whenever(mockEmailManager.getEmailAddress()).thenReturn(null)

        testee.showEmailTooltip()

        assertCommandNotIssued<Command.ShowEmailTooltip>()
    }

    @Test
    fun whenHandleAppLinkCalledAndShowAppLinksPromptIsTrueThenShowAppLinkPromptAndUserQueryStateSetToFalse() {
        val urlType = SpecialUrlDetector.UrlType.AppLink(uriString = "http://example.com")
        testee.handleAppLink(urlType, isForMainFrame = true)
        whenever(mockAppLinksHandler.isUserQuery()).thenReturn(false)
        whenever(mockSettingsStore.showAppLinksPrompt).thenReturn(true)
        verify(mockAppLinksHandler).handleAppLink(eq(true), eq("http://example.com"), eq(false), eq(true), capture(appLinkCaptor))
        appLinkCaptor.value.invoke()
        assertCommandIssued<Command.ShowAppLinkPrompt>()
        verify(mockAppLinksHandler).setUserQueryState(false)
    }

    @Test
    fun whenHandleAppLinkCalledAndIsUserQueryThenShowAppLinkPromptAndUserQueryStateSetToFalse() {
        val urlType = SpecialUrlDetector.UrlType.AppLink(uriString = "http://example.com")
        testee.handleAppLink(urlType, isForMainFrame = true)
        whenever(mockAppLinksHandler.isUserQuery()).thenReturn(true)
        whenever(mockSettingsStore.showAppLinksPrompt).thenReturn(false)
        verify(mockAppLinksHandler).handleAppLink(eq(true), eq("http://example.com"), eq(false), eq(true), capture(appLinkCaptor))
        appLinkCaptor.value.invoke()
        assertCommandIssued<Command.ShowAppLinkPrompt>()
        verify(mockAppLinksHandler).setUserQueryState(false)
    }

    @Test
    fun whenHandleAppLinkCalledAndIsNotUserQueryAndShowAppLinksPromptIsFalseThenOpenAppLink() {
        val urlType = SpecialUrlDetector.UrlType.AppLink(uriString = "http://example.com")
        testee.handleAppLink(urlType, isForMainFrame = true)
        whenever(mockAppLinksHandler.isUserQuery()).thenReturn(false)
        whenever(mockSettingsStore.showAppLinksPrompt).thenReturn(false)
        verify(mockAppLinksHandler).handleAppLink(eq(true), eq("http://example.com"), eq(false), eq(true), capture(appLinkCaptor))
        appLinkCaptor.value.invoke()
        assertCommandIssued<Command.OpenAppLink>()
    }

    @Test
    fun whenHandleNonHttpAppLinkCalledThenHandleNonHttpAppLink() {
        val urlType = SpecialUrlDetector.UrlType.NonHttpAppLink("market://details?id=com.example", Intent(), "http://example.com")
        assertTrue(testee.handleNonHttpAppLink(urlType))
        assertCommandIssued<Command.HandleNonHttpAppLink>()
    }

    @Test
    fun whenUserSubmittedQueryIsAppLinkAndShouldShowPromptThenOpenAppLinkInBrowserAndSetPreviousUrlToNull() {
        whenever(mockOmnibarConverter.convertQueryToUrl("foo", null)).thenReturn("foo.com")
        whenever(mockSpecialUrlDetector.determineType(anyString())).thenReturn(SpecialUrlDetector.UrlType.AppLink(uriString = "http://foo.com"))
        whenever(mockSettingsStore.showAppLinksPrompt).thenReturn(true)
        testee.onUserSubmittedQuery("foo")
        verify(mockAppLinksHandler).updatePreviousUrl(null)
        assertCommandIssued<Navigate>()
    }

    @Test
    fun whenUserSubmittedQueryIsAppLinkAndShouldNotShowPromptThenOpenAppLinkInBrowserAndSetPreviousUrl() {
        whenever(mockOmnibarConverter.convertQueryToUrl("foo", null)).thenReturn("foo.com")
        whenever(mockSpecialUrlDetector.determineType(anyString())).thenReturn(SpecialUrlDetector.UrlType.AppLink(uriString = "http://foo.com"))
        whenever(mockSettingsStore.showAppLinksPrompt).thenReturn(false)
        testee.onUserSubmittedQuery("foo")
        verify(mockAppLinksHandler).updatePreviousUrl("foo.com")
        assertCommandIssued<Navigate>()
    }

    @Test
    fun whenSubmittedQueryAndNavigationStateIsNullThenResetHistoryCommandSent() {
        whenever(mockOmnibarConverter.convertQueryToUrl("nytimes.com", null)).thenReturn("nytimes.com")
        testee.onUserSubmittedQuery("nytimes.com")
        assertCommandIssued<Command.ResetHistory>()
    }

    @Test
    fun whenSubmittedQueryAndNavigationStateIsNotNullThenResetHistoryCommandNotSent() {
        setupNavigation(isBrowsing = true)
        whenever(mockOmnibarConverter.convertQueryToUrl("nytimes.com", null)).thenReturn("nytimes.com")
        testee.onUserSubmittedQuery("nytimes.com")
        assertCommandNotIssued<Command.ResetHistory>()
    }

    @Test
    fun whenLoadUrlAndUrlIsInContentBlockingExceptionsListThenIsWhitelistedIsTrue() {
        whenever(mockContentBlocking.isAnException("example.com")).thenReturn(true)
        loadUrl("https://example.com")
        assertTrue(browserViewState().isPrivacyProtectionEnabled)
    }

    @Test
    fun whenLoadUrlAndUrlIsInContentBlockingExceptionsListThenPrivacyOnIsFalse() {
        whenever(mockContentBlocking.isAnException("example.com")).thenReturn(true)
        loadUrl("https://example.com")
        assertFalse(loadingViewState().privacyOn)
    }

    @Test
    fun whenLoadUrlAndSiteIsInContentBlockingExceptionsListThenDoNotChangePrivacyGrade() {
        whenever(mockContentBlocking.isAnException(any())).thenReturn(true)
        loadUrl("https://example.com")
        assertNull(privacyGradeState().privacyGrade)
    }

    @Test
    fun whenEditBookmarkRequestedThenRepositoryIsNotUpdated() = runTest {
        val url = "http://www.example.com"
        val bookmark = Bookmark(id = 1L, title = "", url = url, parentId = 0L)
        whenever(mockBookmarksRepository.getBookmark(url = url)).thenReturn(bookmark)
        bookmarksListFlow.send(listOf(bookmark))
        loadUrl(url = url, isBrowserShowing = true)
        testee.onBookmarkMenuClicked()
        verify(mockBookmarksRepository, never()).insert(title = anyString(), url = anyString(), parentId = anyLong())
    }

    @Test
    fun whenEditBookmarkRequestedThenEditBookmarkPressedPixelIsFired() = runTest {
        val bookmark = Bookmark(id = 1L, title = "title", url = "www.example.com", parentId = 0L)
        whenever(mockBookmarksRepository.getBookmark("www.example.com")).thenReturn(bookmark)
        bookmarksListFlow.send(listOf(bookmark))
        loadUrl("www.example.com", isBrowserShowing = true)
        testee.onBookmarkMenuClicked()
        verify(mockPixel).fire(AppPixelName.MENU_ACTION_EDIT_BOOKMARK_PRESSED.pixelName)
    }

    @Test
    fun whenEditBookmarkRequestedThenEditDialogIsShownWithCorrectUrlAndTitle() = runTest {
        val bookmark = Bookmark(id = 1L, title = "title", url = "www.example.com", parentId = 0L)
        whenever(mockBookmarksRepository.getBookmark("www.example.com")).thenReturn(bookmark)
        bookmarksListFlow.send(listOf(bookmark))
        loadUrl("www.example.com", isBrowserShowing = true)
        testee.onBookmarkMenuClicked()
        verify(mockCommandObserver, atLeastOnce()).onChanged(commandCaptor.capture())
        assertTrue(commandCaptor.lastValue is Command.ShowEditSavedSiteDialog)
        val command = commandCaptor.lastValue as Command.ShowEditSavedSiteDialog
        assertEquals("www.example.com", command.savedSiteChangedViewState.savedSite.url)
        assertEquals("title", command.savedSiteChangedViewState.savedSite.title)
    }

    @Test
    fun whenRemoveFavoriteRequestedThenDaoInsertIsNotCalled() = runTest {
        val favoriteSite = Favorite(id = 1L, title = "", url = "www.example.com", position = 0)
        favoriteListFlow.send(listOf(favoriteSite))
        loadUrl("www.example.com", isBrowserShowing = true)
        testee.onFavoriteMenuClicked()
        verify(mockFavoritesRepository, never()).insert(any())
    }

    @Test
    fun whenRemoveFavoriteRequestedThenRemoveFavoritePressedPixelIsFired() = runTest {
        val favoriteSite = Favorite(id = 1L, title = "", url = "www.example.com", position = 0)
        whenever(mockFavoritesRepository.favorite("www.example.com")).thenReturn(favoriteSite)
        favoriteListFlow.send(listOf(favoriteSite))
        loadUrl("www.example.com", isBrowserShowing = true)
        testee.onFavoriteMenuClicked()
        verify(mockPixel).fire(
            AppPixelName.MENU_ACTION_REMOVE_FAVORITE_PRESSED.pixelName
        )
    }

    @Test
    fun whenRemoveFavoriteRequestedThenRepositoryDeleteIsCalledForThatSite() = runTest {
        val favoriteSite = Favorite(id = 1L, title = "", url = "www.example.com", position = 0)
        whenever(mockFavoritesRepository.favorite("www.example.com")).thenReturn(favoriteSite)
        favoriteListFlow.send(listOf(favoriteSite))
        loadUrl("www.example.com", isBrowserShowing = true)
        testee.onFavoriteMenuClicked()
        verify(mockFavoritesRepository, atLeastOnce()).delete(favoriteSite)
    }

    @Test
    fun whenRemoveFavoriteRequestedThenDeleteConfirmationDialogIsShownWithCorrectUrlAndTitle() = runTest {
        val favoriteSite = Favorite(id = 1L, title = "title", url = "www.example.com", position = 0)
        whenever(mockFavoritesRepository.favorite("www.example.com")).thenReturn(favoriteSite)
        favoriteListFlow.send(listOf(favoriteSite))
        loadUrl("www.example.com", isBrowserShowing = true)
        testee.onFavoriteMenuClicked()
        verify(mockCommandObserver, atLeastOnce()).onChanged(commandCaptor.capture())
        assertTrue(commandCaptor.lastValue is Command.DeleteSavedSiteConfirmation)
        val command = commandCaptor.lastValue as Command.DeleteSavedSiteConfirmation
        assertEquals("www.example.com", command.savedSite.url)
        assertEquals("title", command.savedSite.title)
    }

    @Test
    fun whenPageChangedThenUpdatePreviousUrlAndUserQueryStateSetToFalse() {
        loadUrl(url = "www.example.com", isBrowserShowing = true)
        verify(mockAppLinksHandler).updatePreviousUrl("www.example.com")
        verify(mockAppLinksHandler).setUserQueryState(false)
    }

    @Test
    fun whenPageChangedAndIsAppLinkThenUpdatePreviousAppLink() {
        val appLink = SpecialUrlDetector.UrlType.AppLink(uriString = "www.example.com")
        whenever(mockSpecialUrlDetector.determineType(anyString())).thenReturn(appLink)
        loadUrl(url = "www.example.com", isBrowserShowing = true)
        assertEquals(appLink, browserViewState().previousAppLink)
    }

    @Test
    fun whenPageChangedAndIsNotAppLinkThenSetPreviousAppLinkToNull() {
        whenever(mockSpecialUrlDetector.determineType(anyString())).thenReturn(SpecialUrlDetector.UrlType.Web("www.example.com"))
        loadUrl(url = "www.example.com", isBrowserShowing = true)
        assertNull(browserViewState().previousAppLink)
    }

    @Test
    fun whenOpenAppLinkThenOpenPreviousAppLink() {
        testee.browserViewState.value = browserViewState().copy(previousAppLink = SpecialUrlDetector.UrlType.AppLink(uriString = "example.com"))
        testee.openAppLink()
        assertCommandIssued<Command.OpenAppLink>()
    }

    @Test
    fun whenOpenAppLinkAndPreviousAppLinkIsNullThenDoNotOpenAppLink() {
        testee.openAppLink()
        assertCommandNotIssued<Command.OpenAppLink>()
    }

    @Test
    fun whenForceZoomEnabledThenEmitNewState() {
        accessibilitySettingsDataStore.forceZoom = true
        assertTrue(accessibilityViewState().forceZoom)
        assertTrue(accessibilityViewState().refreshWebView)
    }

    @Test
    fun whenForceZoomEnabledAndWebViewRefreshedThenEmitNewState() {
        accessibilitySettingsDataStore.forceZoom = true
        assertTrue(accessibilityViewState().forceZoom)
        assertTrue(accessibilityViewState().refreshWebView)

        testee.onWebViewRefreshed()

        assertFalse(accessibilityViewState().refreshWebView)
    }

    @Test
    fun whenFontSizeChangedThenEmitNewState() {
        accessibilitySettingsDataStore.appFontSize = 150f
        accessibilitySettingsDataStore.overrideSystemFontSize = false

        assertFalse(accessibilityViewState().refreshWebView)
        assertEquals(accessibilitySettingsDataStore.fontSize, accessibilityViewState().fontSize)
    }

    @Test
    fun whenDownloadIsCalledThenDownloadRequestStartedPixelFired() {
        val pendingFileDownload = buildPendingDownload(url = "http://www.example.com/download.pdf", contentDisposition = null, mimeType = null)

        testee.download(pendingFileDownload)

        verify(mockPixel).fire(AppPixelName.DOWNLOAD_REQUEST_STARTED)
    }

    @Test
    fun whenDownloadIsCalledForDataAndFinishedThenDownloadRequestStartedAndDownloadRequestSucceededPixelsFired() {
        val pendingFileDownload = buildPendingDownload(url = "data://image.jpg", contentDisposition = null, mimeType = null)
        whenever(mockFileDownloader.download(eq(pendingFileDownload), any())).then {
            (it.getArgument(1) as FileDownloader.FileDownloadListener).downloadFinishedDataUri(
                file = File("image.jpg"),
                mimeType = null
            )
        }

        testee.download(pendingFileDownload)

        verify(mockPixel).fire(AppPixelName.DOWNLOAD_REQUEST_STARTED)
        verify(mockPixel).fire(AppPixelName.DOWNLOAD_REQUEST_SUCCEEDED)
    }

    @Test
    fun whenDownloadIsCalledForDataAndFinishedThenDownloadRequestStartedAndDownloadRequestFailedPixelsFired() {
        val pendingFileDownload = buildPendingDownload(url = "data://image.jpg", contentDisposition = null, mimeType = null)
        whenever(mockFileDownloader.download(eq(pendingFileDownload), any())).then {
            (it.getArgument(1) as FileDownloader.FileDownloadListener).downloadFailed(
                message = "message",
                downloadFailReason = DownloadFailReason.ConnectionRefused
            )
        }

        testee.download(pendingFileDownload)

        verify(mockPixel).fire(AppPixelName.DOWNLOAD_REQUEST_STARTED)
        verify(mockPixel).fire(AppPixelName.DOWNLOAD_REQUEST_FAILED)
    }

    private fun buildPendingDownload(
        url: String,
        contentDisposition: String?,
        mimeType: String?
    ): FileDownloader.PendingFileDownload {
        return FileDownloader.PendingFileDownload(
            url = url,
            contentDisposition = contentDisposition,
            mimeType = mimeType,
            subfolder = "folder",
            userAgent = "user_agent"
        )
    }

    @Test
    fun whenHandleCloakedAmpLinkThenIssueExtractUrlFromCloakedAmpLinkCommand() {
        testee.handleCloakedAmpLink(initialUrl = "example.com")
        assertCommandIssued<Command.ExtractUrlFromCloakedAmpLink>()
    }

    @Test
    fun whenPageChangedThenUpdateAmpLinkInfo() {
        val ampLinkInfo = AmpLinkInfo("https://foo.com")
        whenever(mockAmpLinks.lastAmpLinkInfo).thenReturn(ampLinkInfo)
        updateUrl("http://www.example.com/", "http://twitter.com/explore", true)
        assertEquals("https://foo.com", ampLinkInfo.ampLink)
        assertEquals("http://twitter.com/explore", ampLinkInfo.destinationUrl)
    }

    @Test
    fun whenPageChangedAndAmpLinkInfoHasDestinationUrlThenDontUpdateAmpLinkInfo() {
        val ampLinkInfo = AmpLinkInfo("https://foo.com", "https://bar.com")
        whenever(mockAmpLinks.lastAmpLinkInfo).thenReturn(ampLinkInfo)
        updateUrl("http://www.example.com/", "http://twitter.com/explore", true)
        assertEquals("https://foo.com", ampLinkInfo.ampLink)
        assertEquals("https://bar.com", ampLinkInfo.destinationUrl)
    }

    @Test
    fun whenPageChangedAndAmpLinkInfoIsNullThenDontUpdateAmpLinkInfo() {
        val ampLinkInfo = null
        whenever(mockAmpLinks.lastAmpLinkInfo).thenReturn(ampLinkInfo)
        updateUrl("http://www.example.com/", "http://twitter.com/explore", true)
        assertNull(ampLinkInfo)
    }

    @Test
    fun whenUpdateLastAmpLinkThenUpdateAmpLinkInfo() {
        testee.updateLastAmpLink("https://foo.com")
        verify(mockAmpLinks).lastAmpLinkInfo = AmpLinkInfo("https://foo.com")
    }

    @Test
    fun whenUserSubmittedQueryIsCloakedAmpLinkThenHandleCloakedAmpLink() {
        whenever(mockOmnibarConverter.convertQueryToUrl("foo", null)).thenReturn("foo.com")
        whenever(mockSpecialUrlDetector.determineType(anyString()))
            .thenReturn(SpecialUrlDetector.UrlType.CloakedAmpLink(ampUrl = "http://foo.com"))
        testee.onUserSubmittedQuery("foo")
        verify(mockCommandObserver, atLeastOnce()).onChanged(commandCaptor.capture())
        val issuedCommand = commandCaptor.allValues.find { it is Command.ExtractUrlFromCloakedAmpLink }
        assertEquals("http://foo.com", (issuedCommand as Command.ExtractUrlFromCloakedAmpLink).initialUrl)
    }

    @Test
    fun whenUserSubmittedQueryIsExtractedAmpLinkThenNavigateToExtractedAmpLink() {
        whenever(mockOmnibarConverter.convertQueryToUrl("foo", null)).thenReturn("foo.com")
        whenever(mockSpecialUrlDetector.determineType(anyString()))
            .thenReturn(SpecialUrlDetector.UrlType.ExtractedAmpLink(extractedUrl = "http://foo.com"))
        testee.onUserSubmittedQuery("foo")
        verify(mockCommandObserver, atLeastOnce()).onChanged(commandCaptor.capture())
        val issuedCommand = commandCaptor.allValues.find { it is Navigate }
        assertEquals("http://foo.com", (issuedCommand as Navigate).url)
    }

    @Test
    fun whenUserSubmittedQueryIsTrackingParameterLinkThenNavigateToCleanedUrl() {
        whenever(mockOmnibarConverter.convertQueryToUrl("foo", null)).thenReturn("foo.com")
        whenever(mockSpecialUrlDetector.determineType(anyString()))
            .thenReturn(SpecialUrlDetector.UrlType.TrackingParameterLink(cleanedUrl = "http://foo.com"))
        testee.onUserSubmittedQuery("foo")
        verify(mockCommandObserver, atLeastOnce()).onChanged(commandCaptor.capture())
        val issuedCommand = commandCaptor.allValues.find { it is Navigate }
        assertEquals("http://foo.com", (issuedCommand as Navigate).url)
    }

    @Test
    fun whenUrlExtractionErrorThenIssueLoadExtractedUrlCommandWithInitialUrl() {
        testee.onUrlExtractionError("http://foo.com")
        verify(mockCommandObserver, atLeastOnce()).onChanged(commandCaptor.capture())
        val issuedCommand = commandCaptor.allValues.find { it is LoadExtractedUrl }
        assertEquals("http://foo.com", (issuedCommand as LoadExtractedUrl).extractedUrl)
    }

    @Test
    fun whenUrlExtractedAndIsNotNullThenIssueLoadExtractedUrlCommandWithExtractedUrl() {
        testee.onUrlExtracted("http://foo.com", "http://example.com")
        verify(mockAmpLinks).lastAmpLinkInfo = AmpLinkInfo(ampLink = "http://foo.com")
        verify(mockCommandObserver, atLeastOnce()).onChanged(commandCaptor.capture())
        val issuedCommand = commandCaptor.allValues.find { it is LoadExtractedUrl }
        assertEquals("http://example.com", (issuedCommand as LoadExtractedUrl).extractedUrl)
    }

    @Test
    fun whenUrlExtractedAndIsNullThenIssueLoadExtractedUrlCommandWithInitialUrl() {
        testee.onUrlExtracted("http://foo.com", null)
        verify(mockCommandObserver, atLeastOnce()).onChanged(commandCaptor.capture())
        val issuedCommand = commandCaptor.allValues.find { it is LoadExtractedUrl }
        assertEquals("http://foo.com", (issuedCommand as LoadExtractedUrl).extractedUrl)
    }

    @Test
    fun whenPageChangedThenClearLastCleanedUrlAndUpdateSite() {
        whenever(mockTrackingParameters.lastCleanedUrl).thenReturn("https://foo.com")
        updateUrl("http://www.example.com/", "http://twitter.com/explore", true)
        verify(mockTrackingParameters).lastCleanedUrl = null
        assertTrue(testee.siteLiveData.value?.urlParametersRemoved!!)
    }

    @Test
    fun whenPageChangedAndLastCleanedUrlIsNullThenDoNothing() {
        whenever(mockTrackingParameters.lastCleanedUrl).thenReturn(null)
        updateUrl("http://www.example.com/", "http://twitter.com/explore", true)
        verify(mockTrackingParameters, times(0)).lastCleanedUrl = null
        assertFalse(testee.siteLiveData.value?.urlParametersRemoved!!)
    }

    @Test
    fun whenRemoteMessageShownThenFirePixelAndMarkAsShown() = runTest {
        val remoteMessage = RemoteMessage("id1", Content.Small("", ""), emptyList(), emptyList())
        givenRemoteMessage(remoteMessage)
        testee.onViewVisible()

        testee.onMessageShown()

        verify(mockRemoteMessagingRepository).markAsShown(remoteMessage)
        verify(mockPixel).fire(AppPixelName.REMOTE_MESSAGE_SHOWN_UNIQUE, mapOf("cta" to "id1"))
        verify(mockPixel).fire(AppPixelName.REMOTE_MESSAGE_SHOWN, mapOf("cta" to "id1"))
    }

    @Test
    fun whenRemoteMessageCloseButtonClickedThenFirePixelAndDismiss() = runTest {
        val remoteMessage = RemoteMessage("id1", Content.Small("", ""), emptyList(), emptyList())
        givenRemoteMessage(remoteMessage)
        testee.onViewVisible()

        testee.onMessageCloseButtonClicked()

        verify(mockRemoteMessagingRepository).dismissMessage("id1")
        verify(mockPixel).fire(AppPixelName.REMOTE_MESSAGE_DISMISSED, mapOf("cta" to "id1"))
    }

    @Test
    fun whenRemoteMessagePrimaryButtonClickedThenFirePixelAndDismiss() = runTest {
        val remoteMessage = RemoteMessage("id1", Content.Small("", ""), emptyList(), emptyList())
        givenRemoteMessage(remoteMessage)
        testee.onViewVisible()

        testee.onMessagePrimaryButtonClicked()

        verify(mockRemoteMessagingRepository).dismissMessage("id1")
        verify(mockPixel).fire(AppPixelName.REMOTE_MESSAGE_PRIMARY_ACTION_CLICKED, mapOf("cta" to "id1"))
    }

    @Test
    fun whenRemoteMessageSecondaryButtonClickedThenFirePixelAndDismiss() = runTest {
        val remoteMessage = RemoteMessage("id1", Content.Small("", ""), emptyList(), emptyList())
        givenRemoteMessage(remoteMessage)
        testee.onViewVisible()

        testee.onMessageSecondaryButtonClicked()

        verify(mockRemoteMessagingRepository).dismissMessage("id1")
        verify(mockPixel).fire(AppPixelName.REMOTE_MESSAGE_SECONDARY_ACTION_CLICKED, mapOf("cta" to "id1"))
    }

    private fun givenUrlCanUseGpc() {
        whenever(mockFeatureToggle.isFeatureEnabled(any(), any())).thenReturn(true)
        whenever(mockGpcRepository.isGpcEnabled()).thenReturn(true)
        whenever(mockGpcRepository.exceptions).thenReturn(CopyOnWriteArrayList())
    }

    private fun givenUrlCannotUseGpc(url: String) {
        val exceptions = CopyOnWriteArrayList<GpcException>().apply { add(GpcException(url)) }
        whenever(mockFeatureToggle.isFeatureEnabled(eq(PrivacyFeatureName.GpcFeatureName), any())).thenReturn(true)
        whenever(mockGpcRepository.isGpcEnabled()).thenReturn(true)
        whenever(mockGpcRepository.exceptions).thenReturn(exceptions)
    }

    private fun givenGpcIsDisabled() {
        whenever(mockFeatureToggle.isFeatureEnabled(any(), any())).thenReturn(true)
        whenever(mockGpcRepository.isGpcEnabled()).thenReturn(false)
    }

    private suspend fun givenFireButtonPulsing() {
        whenever(mockUserStageStore.getUserAppStage()).thenReturn(AppStage.DAX_ONBOARDING)
        dismissedCtaDaoChannel.send(listOf(DismissedCta(CtaId.DAX_DIALOG_TRACKERS_FOUND)))
    }

    private fun givenNewPermissionRequestFromDomain(domain: String) {
        testee.onSiteLocationPermissionRequested(domain, StubPermissionCallback())
    }

    private fun givenDeviceLocationSharingIsEnabled(state: Boolean) {
        whenever(geoLocationPermissions.isDeviceLocationEnabled()).thenReturn(state)
    }

    private fun givenLocationPermissionIsEnabled(state: Boolean) {
        whenever(mockSettingsStore.appLocationPermission).thenReturn(state)
    }

    private fun givenUserAlreadySelectedPermissionForDomain(
        domain: String,
        permission: LocationPermissionType
    ) {
        locationPermissionsDao.insert(LocationPermissionEntity(domain, permission))
    }

    class StubPermissionCallback : GeolocationPermissions.Callback {
        override fun invoke(
            p0: String?,
            p1: Boolean,
            p2: Boolean
        ) {
            // nothing to see
        }
    }

    private inline fun <reified T : Command> assertCommandIssued(instanceAssertions: T.() -> Unit = {}) {
        verify(mockCommandObserver, atLeastOnce()).onChanged(commandCaptor.capture())
        val issuedCommand = commandCaptor.allValues.find { it is T }
        assertNotNull(issuedCommand)
        (issuedCommand as T).apply { instanceAssertions() }
    }

    private inline fun <reified T : Command> assertCommandNotIssued() {
        val defaultMockingDetails = DefaultMockingDetails(mockCommandObserver)
        if (defaultMockingDetails.invocations.isNotEmpty()) {
            verify(mockCommandObserver, atLeastOnce()).onChanged(commandCaptor.capture())
            val issuedCommand = commandCaptor.allValues.find { it is T }
            assertNull(issuedCommand)
        }
    }

    private inline fun <reified T : Command> assertCommandIssuedTimes(times: Int) {
        if (times == 0) {
            assertCommandNotIssued<T>()
        } else {
            verify(mockCommandObserver, atLeastOnce()).onChanged(commandCaptor.capture())
            val timesIssued = commandCaptor.allValues.count { it is T }
            assertEquals(times, timesIssued)
        }
    }

    private fun pixelParams(
        showedBookmarks: Boolean,
        bookmarkCapable: Boolean
    ) = mapOf(
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
        testee.loadData("TAB_ID", "https://example.com", false, false)
    }

    private fun givenFireproofWebsiteDomain(vararg fireproofWebsitesDomain: String) {
        fireproofWebsitesDomain.forEach {
            fireproofWebsiteDao.insert(FireproofWebsiteEntity(domain = it))
        }
    }

    private fun givenLoginDetected(domain: String) = LoginDetected(authLoginDomain = "", forwardedToDomain = domain)

    private fun givenCurrentSite(domain: String) {
        val site: Site = mock()
        whenever(site.url).thenReturn(domain)
        whenever(site.uri).thenReturn(Uri.parse(domain))
        val siteLiveData = MutableLiveData<Site>()
        siteLiveData.value = site
        whenever(mockTabRepository.retrieveSiteData("TAB_ID")).thenReturn(siteLiveData)
        testee.loadData("TAB_ID", domain, false, false)
    }

    private fun givenRemoteMessagingModel(
        remoteMessagingRepository: RemoteMessagingRepository,
        pixel: Pixel,
        dispatchers: DispatcherProvider
    ) = RemoteMessagingModel(remoteMessagingRepository, pixel, dispatchers)

    private fun setBrowserShowing(isBrowsing: Boolean) {
        testee.browserViewState.value = browserViewState().copy(browserShowing = isBrowsing)
    }

    private fun setDesktopBrowsingMode(desktopBrowsingMode: Boolean) {
        testee.browserViewState.value = browserViewState().copy(isDesktopBrowsingMode = desktopBrowsingMode)
    }

    private fun setCta(cta: Cta) {
        testee.ctaViewState.value = ctaViewState().copy(cta = cta)
    }

    private suspend fun givenRemoteMessage(remoteMessage: RemoteMessage) {
        remoteMessageFlow.send(remoteMessage)
    }

    private fun loadUrl(
        url: String?,
        title: String? = null,
        isBrowserShowing: Boolean = true
    ) {
        setBrowserShowing(isBrowserShowing)
        testee.navigationStateChanged(buildWebNavigation(originalUrl = url, currentUrl = url, title = title))
    }

    @Suppress("SameParameterValue")
    private fun updateUrl(
        originalUrl: String?,
        currentUrl: String?,
        isBrowserShowing: Boolean
    ) {
        setBrowserShowing(isBrowserShowing)
        testee.navigationStateChanged(buildWebNavigation(originalUrl = originalUrl, currentUrl = currentUrl))
    }

    @Suppress("SameParameterValue")
    private fun onProgressChanged(
        url: String?,
        newProgress: Int
    ) {
        testee.navigationStateChanged(buildWebNavigation(originalUrl = url, currentUrl = url, progress = newProgress))
    }

    private fun overrideUrl(
        url: String,
        isBrowserShowing: Boolean = true
    ) {
        setBrowserShowing(isBrowserShowing)
        testee.willOverrideUrl(newUrl = url)
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

    private fun clearAccessibilitySettings() {
        context.getSharedPreferences(AccessibilitySettingsSharedPreferences.FILENAME, Context.MODE_PRIVATE).edit().clear().commit()
    }

    private fun buildWebNavigation(
        currentUrl: String? = null,
        originalUrl: String? = null,
        title: String? = null,
        canGoForward: Boolean = false,
        canGoBack: Boolean = false,
        stepsToPreviousPage: Int = 0,
        progress: Int? = null
    ): WebNavigationState {
        val nav: WebNavigationState = mock()
        whenever(nav.originalUrl).thenReturn(originalUrl)
        whenever(nav.currentUrl).thenReturn(currentUrl)
        whenever(nav.title).thenReturn(title)
        whenever(nav.canGoForward).thenReturn(canGoForward)
        whenever(nav.canGoBack).thenReturn(canGoBack)
        whenever(nav.stepsToPreviousPage).thenReturn(stepsToPreviousPage)
        whenever(nav.progress).thenReturn(progress)
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
    private fun accessibilityViewState() = testee.accessibilityViewState.value!!
}
