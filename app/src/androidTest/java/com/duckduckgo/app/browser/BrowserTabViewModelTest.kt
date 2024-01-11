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
import android.print.PrintAttributes
import android.view.MenuItem
import android.view.View
import android.webkit.GeolocationPermissions
import android.webkit.HttpAuthHandler
import android.webkit.PermissionRequest
import android.webkit.ValueCallback
import android.webkit.WebChromeClient.FileChooserParams
import android.webkit.WebView
import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.core.net.toUri
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.Observer
import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry.getInstrumentation
import com.duckduckgo.adclick.api.AdClickManager
import com.duckduckgo.app.ValueCaptorObserver
import com.duckduckgo.app.accessibility.data.AccessibilitySettingsDataStore
import com.duckduckgo.app.accessibility.data.AccessibilitySettingsSharedPreferences
import com.duckduckgo.app.autocomplete.api.AutoComplete.AutoCompleteResult
import com.duckduckgo.app.autocomplete.api.AutoComplete.AutoCompleteSuggestion.AutoCompleteBookmarkSuggestion
import com.duckduckgo.app.autocomplete.api.AutoComplete.AutoCompleteSuggestion.AutoCompleteSearchSuggestion
import com.duckduckgo.app.autocomplete.api.AutoCompleteApi
import com.duckduckgo.app.autocomplete.api.AutoCompleteService
import com.duckduckgo.app.browser.BrowserTabViewModel.Command
import com.duckduckgo.app.browser.BrowserTabViewModel.Command.LoadExtractedUrl
import com.duckduckgo.app.browser.BrowserTabViewModel.Command.ShowBackNavigationHistory
import com.duckduckgo.app.browser.BrowserTabViewModel.Command.ShowPrivacyProtectionDisabledConfirmation
import com.duckduckgo.app.browser.BrowserTabViewModel.Command.ShowPrivacyProtectionEnabledConfirmation
import com.duckduckgo.app.browser.BrowserTabViewModel.HighlightableButton
import com.duckduckgo.app.browser.BrowserTabViewModel.NavigationCommand
import com.duckduckgo.app.browser.BrowserTabViewModel.NavigationCommand.Navigate
import com.duckduckgo.app.browser.LongPressHandler.RequiredAction.DownloadFile
import com.duckduckgo.app.browser.LongPressHandler.RequiredAction.OpenInNewTab
import com.duckduckgo.app.browser.WebViewErrorResponse.BAD_URL
import com.duckduckgo.app.browser.WebViewErrorResponse.LOADING
import com.duckduckgo.app.browser.WebViewErrorResponse.OMITTED
import com.duckduckgo.app.browser.addtohome.AddToHomeCapabilityDetector
import com.duckduckgo.app.browser.applinks.AppLinksHandler
import com.duckduckgo.app.browser.camera.CameraHardwareChecker
import com.duckduckgo.app.browser.favicon.FaviconManager
import com.duckduckgo.app.browser.favicon.FaviconSource
import com.duckduckgo.app.browser.favorites.FavoritesQuickAccessAdapter
import com.duckduckgo.app.browser.favorites.FavoritesQuickAccessAdapter.QuickAccessFavorite
import com.duckduckgo.app.browser.history.NavigationHistoryEntry
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
import com.duckduckgo.app.fire.fireproofwebsite.data.FireproofWebsiteDao
import com.duckduckgo.app.fire.fireproofwebsite.data.FireproofWebsiteEntity
import com.duckduckgo.app.fire.fireproofwebsite.data.FireproofWebsiteRepositoryImpl
import com.duckduckgo.app.fire.fireproofwebsite.ui.AutomaticFireproofSetting
import com.duckduckgo.app.global.db.AppDatabase
import com.duckduckgo.app.global.events.db.UserEventsStore
import com.duckduckgo.app.global.install.AppInstallStore
import com.duckduckgo.app.global.model.PrivacyShield.PROTECTED
import com.duckduckgo.app.global.model.Site
import com.duckduckgo.app.global.model.SiteFactoryImpl
import com.duckduckgo.app.location.GeoLocationPermissions
import com.duckduckgo.app.location.data.LocationPermissionEntity
import com.duckduckgo.app.location.data.LocationPermissionType
import com.duckduckgo.app.location.data.LocationPermissionsDao
import com.duckduckgo.app.location.data.LocationPermissionsRepositoryImpl
import com.duckduckgo.app.onboarding.store.AppStage
import com.duckduckgo.app.onboarding.store.OnboardingStore
import com.duckduckgo.app.onboarding.store.UserStageStore
import com.duckduckgo.app.pixels.AppPixelName
import com.duckduckgo.app.pixels.remoteconfig.AndroidBrowserConfigFeature
import com.duckduckgo.app.privacy.db.NetworkLeaderboardDao
import com.duckduckgo.app.privacy.db.UserAllowListRepository
import com.duckduckgo.app.privacy.model.TestEntity
import com.duckduckgo.app.settings.db.SettingsDataStore
import com.duckduckgo.app.statistics.api.StatisticsUpdater
import com.duckduckgo.app.statistics.pixels.Pixel
import com.duckduckgo.app.surrogates.SurrogateResponse
import com.duckduckgo.app.survey.api.SurveyRepository
import com.duckduckgo.app.survey.model.Survey
import com.duckduckgo.app.survey.notification.SurveyNotificationScheduler
import com.duckduckgo.app.tabs.model.TabEntity
import com.duckduckgo.app.tabs.model.TabRepository
import com.duckduckgo.app.trackerdetection.EntityLookup
import com.duckduckgo.app.trackerdetection.model.TrackerStatus
import com.duckduckgo.app.trackerdetection.model.TrackerType
import com.duckduckgo.app.trackerdetection.model.TrackingEvent
import com.duckduckgo.app.usage.search.SearchCountDao
import com.duckduckgo.app.widget.ui.WidgetCapabilities
import com.duckduckgo.autofill.api.AutofillCapabilityChecker
import com.duckduckgo.autofill.api.domain.app.LoginCredentials
import com.duckduckgo.autofill.api.email.EmailManager
import com.duckduckgo.autofill.api.passwordgeneration.AutomaticSavedLoginsMonitor
import com.duckduckgo.autofill.impl.AutofillFireproofDialogSuppressor
import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.common.test.InstantSchedulersRule
import com.duckduckgo.common.ui.store.AppTheme
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.common.utils.device.DeviceInfo
import com.duckduckgo.downloads.api.DownloadStateListener
import com.duckduckgo.downloads.api.FileDownloader
import com.duckduckgo.downloads.api.FileDownloader.PendingFileDownload
import com.duckduckgo.feature.toggles.api.FeatureToggle
import com.duckduckgo.feature.toggles.api.Toggle
import com.duckduckgo.privacy.config.api.*
import com.duckduckgo.privacy.config.impl.features.gpc.RealGpc
import com.duckduckgo.privacy.config.impl.features.gpc.RealGpc.Companion.GPC_HEADER
import com.duckduckgo.privacy.config.impl.features.gpc.RealGpc.Companion.GPC_HEADER_VALUE
import com.duckduckgo.privacy.config.store.features.gpc.GpcRepository
import com.duckduckgo.remote.messaging.api.Content
import com.duckduckgo.remote.messaging.api.RemoteMessage
import com.duckduckgo.remote.messaging.api.RemoteMessagingRepository
import com.duckduckgo.savedsites.api.SavedSitesRepository
import com.duckduckgo.savedsites.api.models.SavedSite.Bookmark
import com.duckduckgo.savedsites.api.models.SavedSite.Favorite
import com.duckduckgo.site.permissions.api.SitePermissionsManager
import com.duckduckgo.site.permissions.api.SitePermissionsManager.SitePermissionQueryResponse
import com.duckduckgo.site.permissions.api.SitePermissionsManager.SitePermissions
import com.duckduckgo.sync.api.engine.SyncEngine
import com.duckduckgo.sync.api.engine.SyncEngine.SyncTrigger.FEATURE_READ
import com.duckduckgo.voice.api.VoiceSearchAvailability
import com.duckduckgo.voice.api.VoiceSearchAvailabilityPixelLogger
import dagger.Lazy
import io.reactivex.Observable
import java.io.File
import java.util.Locale
import java.util.UUID
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.TimeUnit
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
import kotlinx.coroutines.test.runTest
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.ArgumentCaptor
import org.mockito.Captor
import org.mockito.Mock
import org.mockito.Mockito.*
import org.mockito.Mockito.never
import org.mockito.Mockito.times
import org.mockito.Mockito.verify
import org.mockito.MockitoAnnotations
import org.mockito.internal.util.DefaultMockingDetails
import org.mockito.kotlin.*
import org.mockito.kotlin.any
import org.mockito.kotlin.atLeastOnce
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.eq
import org.mockito.kotlin.whenever

@FlowPreview
class BrowserTabViewModelTest {

    @get:Rule
    var instantTaskExecutorRule = InstantTaskExecutorRule()

    @get:Rule
    val schedulers = InstantSchedulersRule()

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
    private lateinit var mockSettingsStore: SettingsDataStore

    @Mock
    private lateinit var mockSavedSitesRepository: SavedSitesRepository

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
    private lateinit var mockSpecialUrlDetector: SpecialUrlDetector

    @Mock
    private lateinit var mockAppLinksHandler: AppLinksHandler

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
    private lateinit var mockDownloadCallback: DownloadStateListener

    @Mock
    private lateinit var mockRemoteMessagingRepository: RemoteMessagingRepository

    @Mock
    private lateinit var voiceSearchAvailability: VoiceSearchAvailability

    @Mock
    private lateinit var voiceSearchPixelLogger: VoiceSearchAvailabilityPixelLogger

    @Mock
    private lateinit var mockSettingsDataStore: SettingsDataStore

    @Mock
    private lateinit var mockAdClickManager: AdClickManager

    @Mock
    private lateinit var mockUserAllowListRepository: UserAllowListRepository

    @Mock
    private lateinit var mockSurveyNotificationScheduler: SurveyNotificationScheduler

    @Mock
    private lateinit var mockSurveyRepository: SurveyRepository

    @Mock
    private lateinit var mockFileChooserCallback: ValueCallback<Array<Uri>>

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

    private val tabsLiveData = MutableLiveData<List<TabEntity>>()

    private val loginEventLiveData = MutableLiveData<LoginDetected>()

    private val fireproofDialogsEventHandlerLiveData = MutableLiveData<FireproofDialogsEventHandler.Event>()

    private val dismissedCtaDaoChannel = Channel<List<DismissedCta>>()

    private val childClosedTabsSharedFlow = MutableSharedFlow<String>()

    private val childClosedTabsFlow = childClosedTabsSharedFlow.asSharedFlow()

    private val emailStateFlow = MutableStateFlow(false)

    private val bookmarksListFlow = Channel<List<Bookmark>>()

    private val remoteMessageFlow = Channel<RemoteMessage>()

    private val favoriteListFlow = Channel<List<Favorite>>()

    private val mockAppTheme: AppTheme = mock()

    private val autofillCapabilityChecker: FakeCapabilityChecker = FakeCapabilityChecker(enabled = false)

    private val autofillFireproofDialogSuppressor: AutofillFireproofDialogSuppressor = mock()

    private val automaticSavedLoginsMonitor: AutomaticSavedLoginsMonitor = mock()

    private val mockDeviceInfo: DeviceInfo = mock()

    private val mockSitePermissionsManager: SitePermissionsManager = mock()

    private val mockSyncEngine: SyncEngine = mock()

    private val cameraHardwareChecker: CameraHardwareChecker = mock()

    private val androidBrowserConfig: AndroidBrowserConfigFeature = mock()

    private val mockToggle: Toggle = mock()

    @Before
    fun before() {
        MockitoAnnotations.openMocks(this)
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        fireproofWebsiteDao = db.fireproofWebsiteDao()
        locationPermissionsDao = db.locationPermissionsDao()

        mockAutoCompleteApi = AutoCompleteApi(mockAutoCompleteService, mockSavedSitesRepository)
        val fireproofWebsiteRepositoryImpl = FireproofWebsiteRepositoryImpl(
            fireproofWebsiteDao,
            coroutineRule.testDispatcherProvider,
            lazyFaviconManager,
        )

        whenever(mockDismissedCtaDao.dismissedCtas()).thenReturn(dismissedCtaDaoChannel.consumeAsFlow())
        whenever(mockTabRepository.flowTabs).thenReturn(flowOf(emptyList()))
        whenever(mockTabRepository.liveTabs).thenReturn(tabsLiveData)
        whenever(mockEmailManager.signedInFlow()).thenReturn(emailStateFlow.asStateFlow())
        whenever(mockSavedSitesRepository.getFavorites()).thenReturn(favoriteListFlow.consumeAsFlow())
        whenever(mockSavedSitesRepository.getBookmarks()).thenReturn(bookmarksListFlow.consumeAsFlow())
        whenever(mockRemoteMessagingRepository.messageFlow()).thenReturn(remoteMessageFlow.consumeAsFlow())
        whenever(mockSettingsDataStore.automaticFireproofSetting).thenReturn(AutomaticFireproofSetting.ASK_EVERY_TIME)
        whenever(androidBrowserConfig.screenLock()).thenReturn(mockToggle)

        remoteMessagingModel = givenRemoteMessagingModel(mockRemoteMessagingRepository, mockPixel, coroutineRule.testDispatcherProvider)

        ctaViewModel = CtaViewModel(
            appInstallStore = mockAppInstallStore,
            pixel = mockPixel,
            widgetCapabilities = mockWidgetCapabilities,
            dismissedCtaDao = mockDismissedCtaDao,
            userAllowListRepository = mockUserAllowListRepository,
            settingsDataStore = mockSettingsStore,
            onboardingStore = mockOnboardingStore,
            userStageStore = mockUserStageStore,
            tabRepository = mockTabRepository,
            dispatchers = coroutineRule.testDispatcherProvider,
            duckDuckGoUrlDetector = DuckDuckGoUrlDetectorImpl(),
            appTheme = mockAppTheme,
            surveyRepository = mockSurveyRepository,
        )

        val siteFactory = SiteFactoryImpl(
            mockEntityLookup,
            mockContentBlocking,
            mockUserAllowListRepository,
            coroutineRule.testScope,
            coroutineRule.testDispatcherProvider,
        )

        accessibilitySettingsDataStore = AccessibilitySettingsSharedPreferences(
            context,
            coroutineRule.testDispatcherProvider,
            coroutineRule.testScope,
        )

        whenever(mockOmnibarConverter.convertQueryToUrl(any(), any(), any())).thenReturn("duckduckgo.com")
        whenever(mockTabRepository.liveSelectedTab).thenReturn(selectedTabLiveData)
        whenever(mockNavigationAwareLoginDetector.loginEventLiveData).thenReturn(loginEventLiveData)
        whenever(mockTabRepository.retrieveSiteData(any())).thenReturn(MutableLiveData())
        whenever(mockTabRepository.childClosedTabs).thenReturn(childClosedTabsFlow)
        whenever(mockAppInstallStore.installTimestamp).thenReturn(System.currentTimeMillis() - TimeUnit.DAYS.toMillis(1))
        whenever(mockUserAllowListRepository.isDomainInUserAllowList(anyString())).thenReturn(false)
        whenever(mockUserAllowListRepository.domainsInUserAllowListFlow()).thenReturn(flowOf(emptyList()))
        whenever(mockContentBlocking.isAnException(anyString())).thenReturn(false)
        whenever(fireproofDialogsEventHandler.event).thenReturn(fireproofDialogsEventHandlerLiveData)
        whenever(cameraHardwareChecker.hasCameraHardware()).thenReturn(true)

        testee = BrowserTabViewModel(
            statisticsUpdater = mockStatisticsUpdater,
            queryUrlConverter = mockOmnibarConverter,
            duckDuckGoUrlDetector = DuckDuckGoUrlDetectorImpl(),
            siteFactory = siteFactory,
            tabRepository = mockTabRepository,
            userAllowListRepository = mockUserAllowListRepository,
            networkLeaderboardDao = mockNetworkLeaderboardDao,
            autoComplete = mockAutoCompleteApi,
            appSettingsPreferencesStore = mockSettingsStore,
            longPressHandler = mockLongPressHandler,
            webViewSessionStorage = webViewSessionStorage,
            specialUrlDetector = mockSpecialUrlDetector,
            faviconManager = mockFaviconManager,
            addToHomeCapabilityDetector = mockAddToHomeCapabilityDetector,
            ctaViewModel = ctaViewModel,
            searchCountDao = mockSearchCountDao,
            pixel = mockPixel,
            dispatchers = coroutineRule.testDispatcherProvider,
            fireproofWebsiteRepository = fireproofWebsiteRepositoryImpl,
            savedSitesRepository = mockSavedSitesRepository,
            locationPermissionsRepository = LocationPermissionsRepositoryImpl(
                locationPermissionsDao,
                lazyFaviconManager,
                coroutineRule.testDispatcherProvider,
            ),
            geoLocationPermissions = geoLocationPermissions,
            navigationAwareLoginDetector = mockNavigationAwareLoginDetector,
            userEventsStore = mockUserEventsStore,
            fileDownloader = mockFileDownloader,
            gpc = RealGpc(mockFeatureToggle, mockGpcRepository, mockUnprotectedTemporary, mockUserAllowListRepository),
            fireproofDialogsEventHandler = fireproofDialogsEventHandler,
            emailManager = mockEmailManager,
            appCoroutineScope = TestScope(),
            appLinksHandler = mockAppLinksHandler,
            contentBlocking = mockContentBlocking,
            accessibilitySettingsDataStore = accessibilitySettingsDataStore,
            ampLinks = mockAmpLinks,
            remoteMessagingModel = { remoteMessagingModel },
            downloadCallback = mockDownloadCallback,
            trackingParameters = mockTrackingParameters,
            voiceSearchAvailability = voiceSearchAvailability,
            voiceSearchPixelLogger = voiceSearchPixelLogger,
            settingsDataStore = mockSettingsDataStore,
            adClickManager = mockAdClickManager,
            autofillCapabilityChecker = autofillCapabilityChecker,
            autofillFireproofDialogSuppressor = autofillFireproofDialogSuppressor,
            automaticSavedLoginsMonitor = automaticSavedLoginsMonitor,
            surveyNotificationScheduler = mockSurveyNotificationScheduler,
            syncEngine = mockSyncEngine,
            device = mockDeviceInfo,
            sitePermissionsManager = mockSitePermissionsManager,
            cameraHardwareChecker = cameraHardwareChecker,
            androidBrowserConfig = androidBrowserConfig,
        )

        testee.loadData("abc", null, false, false)
        testee.command.observeForever(mockCommandObserver)
    }

    @After
    fun after() {
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
        whenever(mockWidgetCapabilities.hasInstalledWidgets).thenReturn(true)

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
        loadUrl("https://www.example.com", isBrowserShowing = true)
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
        loadUrl("https://www.example.com", isBrowserShowing = false)
        assertFalse(browserViewState().canSaveSite)
        assertFalse(browserViewState().addFavorite.isEnabled())
    }

    @Test
    fun whenBookmarkEditedThenRepositoryIsUpdated() = runTest {
        val folderId = "folder1"
        val bookmark =
            Bookmark(id = UUID.randomUUID().toString(), title = "A title", url = "www.example.com", parentId = folderId, lastModified = "timestamp")
        testee.onBookmarkEdited(bookmark, folderId, false)
        verify(mockSavedSitesRepository).updateBookmark(bookmark, folderId)
    }

    @Test
    fun whenFavoriteEditedThenRepositoryUpdated() = runTest {
        val favorite = Favorite(UUID.randomUUID().toString(), "A title", "www.example.com", lastModified = "timestamp", 1)
        testee.onFavouriteEdited(favorite)
        verify(mockSavedSitesRepository).updateFavourite(favorite)
    }

    @Test
    fun whenBookmarkDeleteRequestedThenViewStateUpdated() = runTest {
        val bookmark =
            Bookmark(id = UUID.randomUUID().toString(), title = "A title", url = "www.example.com", lastModified = "timestamp")

        testee.onSavedSiteDeleted(bookmark)

        assertTrue(browserViewState().bookmark == null)
        assertTrue(browserViewState().favorite == null)
    }

    @Test
    fun whenBookmarkDeletionConfirmedThenFaviconDeletedAndRepositoryIsUpdated() = runTest {
        val bookmark =
            Bookmark(id = UUID.randomUUID().toString(), title = "A title", url = "www.example.com", lastModified = "timestamp")

        testee.onDeleteFavoriteSnackbarDismissed(bookmark)

        verify(mockFaviconManager).deletePersistedFavicon(bookmark.url)
        verify(mockSavedSitesRepository).delete(bookmark)
    }

    @Test
    fun whenBookmarkAddedThenRepositoryIsUpdatedAndUserNotified() = runTest {
        val url = "http://www.example.com"
        val title = "A title"
        val bookmark = Bookmark(
            id = UUID.randomUUID().toString(),
            title = title,
            url = url,
            parentId = UUID.randomUUID().toString(),
            lastModified = "timestamp",
        )
        whenever(mockSavedSitesRepository.insertBookmark(title = anyString(), url = anyString())).thenReturn(bookmark)
        loadUrl(url = url, title = title)

        testee.onBookmarkMenuClicked()
        verify(mockSavedSitesRepository).insertBookmark(title = title, url = url)
        verify(mockCommandObserver, atLeastOnce()).onChanged(commandCaptor.capture())
        assertTrue(commandCaptor.lastValue is Command.ShowSavedSiteAddedConfirmation)
    }

    @Test
    fun whenFavoriteAddedThenRepositoryUpdatedAndUserNotified() = runTest {
        val url = "http://www.example.com"
        val title = "A title"

        val savedSite = Favorite(UUID.randomUUID().toString(), title, url, lastModified = "timestamp", 0)
        whenever(mockSavedSitesRepository.insertFavorite(url = url, title = title)).thenReturn(savedSite)
        loadUrl(url = url, title = title)

        testee.onFavoriteMenuClicked()
        verify(mockSavedSitesRepository).insertFavorite(title = title, url = url)
        verify(mockCommandObserver, atLeastOnce()).onChanged(commandCaptor.capture())
    }

    @Test
    fun whenNoSiteAndUserSelectsToAddFavoriteThenSiteIsNotAdded() = runTest {
        testee.onFavoriteMenuClicked()

        verify(mockSavedSitesRepository, times(0)).insertFavorite(any(), any(), any(), any())
    }

    @Test
    fun whenDeleteQuickAccessItemCalledWithFavoriteThenRepositoryUpdated() = runTest {
        val savedSite = Favorite(UUID.randomUUID().toString(), "title", "http://example.com", lastModified = "timestamp", 0)

        testee.onDeleteFavoriteSnackbarDismissed(savedSite)

        verify(mockSavedSitesRepository).delete(savedSite)
    }

    @Test
    fun whenDeleteQuickAccessItemCalledWithBookmarkThenRepositoryUpdated() = runTest {
        val savedSite = Bookmark(UUID.randomUUID().toString(), "title", "http://example.com", lastModified = "timestamp")

        testee.onDeleteFavoriteSnackbarDismissed(savedSite)

        verify(mockSavedSitesRepository).delete(savedSite)
    }

    @Test
    fun whenQuickAccessListChangedThenRepositoryUpdated() {
        val savedSite = Favorite(UUID.randomUUID().toString(), "title", "http://example.com", lastModified = "timestamp", 0)
        val savedSites = listOf(QuickAccessFavorite(savedSite))

        testee.onQuickAccessListChanged(savedSites)

        verify(mockSavedSitesRepository).updateWithPosition(listOf(savedSite))
    }

    @Test
    fun whenTrackerDetectedThenNetworkLeaderboardUpdated() {
        val networkEntity = TestEntity("Network1", "Network1", 10.0)
        val event = TrackingEvent(
            documentUrl = "http://www.example.com",
            trackerUrl = "http://www.tracker.com/tracker.js",
            categories = emptyList(),
            entity = networkEntity,
            surrogateId = null,
            status = TrackerStatus.ALLOWED,
            type = TrackerType.OTHER,
        )
        testee.trackerDetected(event)
        verify(mockNetworkLeaderboardDao).incrementNetworkCount("Network1")
    }

    @Test
    fun whenEmptyInputQueryThenQueryNavigateCommandNotSubmittedToActivity() {
        testee.onUserSubmittedQuery("")
        assertCommandNotIssued<Navigate>()
    }

    @Test
    fun whenBlankInputQueryThenQueryNavigateCommandNotSubmittedToActivity() {
        testee.onUserSubmittedQuery("     ")
        assertCommandNotIssued<Navigate>()
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
    fun whentrackersDetectedThenPrivacyGradeIsUpdated() {
        val grade = privacyShieldState().privacyShield
        loadUrl("https://example.com")
        val entity = TestEntity("Network1", "Network1", 10.0)
        for (i in 1..10) {
            testee.trackerDetected(
                TrackingEvent(
                    documentUrl = "https://example.com",
                    trackerUrl = "",
                    categories = null,
                    entity = entity,
                    surrogateId = null,
                    status = TrackerStatus.ALLOWED,
                    type = TrackerType.OTHER,
                ),
            )
        }
        assertNotEquals(grade, privacyShieldState().privacyShield)
    }

    @Test
    fun whenOnSiteChangedThenPrivacyShieldIsUpdated() {
        givenCurrentSite("https://www.example.com/").also {
            whenever(it.privacyProtection()).thenReturn(PROTECTED)
        }
        loadUrl("https://example.com")
        val entity = TestEntity("Network1", "Network1", 10.0)
        for (i in 1..10) {
            testee.trackerDetected(
                TrackingEvent(
                    documentUrl = "https://example.com",
                    trackerUrl = "",
                    categories = null,
                    entity = entity,
                    surrogateId = null,
                    status = TrackerStatus.ALLOWED,
                    type = TrackerType.OTHER,
                ),
            )
        }
        assertEquals(PROTECTED, privacyShieldState().privacyShield)
    }

    @Test
    fun whenProgressChangesAndIsProcessingTrackingLinkThenVisualProgressEqualsFixedProgress() {
        setBrowserShowing(true)
        testee.startProcessingTrackingLink()
        testee.progressChanged(100)
        assertEquals(50, loadingViewState().progress)
    }

    @Test
    fun whenInitialisedThenPrivacyGradeIsNotShown() {
        assertFalse(browserViewState().showPrivacyShield)
    }

    @Test
    fun whenUrlUpdatedThenPrivacyGradeIsShown() {
        loadUrl("")
        assertTrue(browserViewState().showPrivacyShield)
    }

    @Test
    fun whenOmnibarDoesNotHaveFocusThenPrivacyGradeIsShownAndSearchIconIsHidden() {
        testee.onOmnibarInputStateChanged(query = "", hasFocus = false, hasQueryChanged = false)
        assertTrue(browserViewState().showPrivacyShield)
        assertFalse(browserViewState().showSearchIcon)
    }

    @Test
    fun whenBrowserShownAndOmnibarInputDoesNotHaveFocusThenPrivacyGradeIsShownAndSearchIconIsHidden() {
        whenever(mockOmnibarConverter.convertQueryToUrl("foo", null)).thenReturn("foo.com")
        testee.onUserSubmittedQuery("foo")
        testee.onOmnibarInputStateChanged(query = "", hasFocus = false, hasQueryChanged = false)
        assertTrue(browserViewState().showPrivacyShield)
        assertFalse(browserViewState().showSearchIcon)
    }

    @Test
    fun whenBrowserNotShownAndOmnibarInputHasFocusThenPrivacyGradeIsNotShown() {
        testee.onOmnibarInputStateChanged("", true, hasQueryChanged = false)
        assertFalse(browserViewState().showPrivacyShield)
    }

    @Test
    fun whenBrowserShownAndOmnibarInputHasFocusThenSearchIconIsShownAndPrivacyGradeIsHidden() {
        whenever(mockOmnibarConverter.convertQueryToUrl("foo", null)).thenReturn("foo.com")
        testee.onUserSubmittedQuery("foo")
        testee.onOmnibarInputStateChanged("", true, hasQueryChanged = false)
        assertFalse(browserViewState().showPrivacyShield)
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
    fun whenTriggeringAutocompleteThenAutoCompleteSuggestionsShown() {
        whenever(mockAutoCompleteService.autoComplete("foo")).thenReturn(Observable.just(emptyList()))
        doReturn(true).whenever(mockSettingsStore).autoCompleteSuggestionsEnabled
        testee.triggerAutocomplete("foo", true, hasQueryChanged = true)
        assertTrue(autoCompleteViewState().showSuggestions)
    }

    @Test
    fun whenTriggeringAutoCompleteButNoQueryChangeThenAutoCompleteSuggestionsNotShown() {
        doReturn(true).whenever(mockSettingsStore).autoCompleteSuggestionsEnabled
        testee.triggerAutocomplete("foo", true, hasQueryChanged = false)
        assertFalse(autoCompleteViewState().showSuggestions)
    }

    @Test
    fun whenTriggeringAutocompleteWithUrlAndUserHasFavoritesThenAutoCompleteShowsFavorites() {
        testee.autoCompleteViewState.value =
            autoCompleteViewState().copy(
                favorites = listOf(
                    QuickAccessFavorite(
                        Favorite(
                            UUID.randomUUID().toString(),
                            "title",
                            "http://example.com",
                            lastModified = "timestamp",
                            1,
                        ),
                    ),
                ),
            )
        doReturn(true).whenever(mockSettingsStore).autoCompleteSuggestionsEnabled
        testee.triggerAutocomplete("https://example.com", true, hasQueryChanged = false)
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
        verify(mockCommandObserver, never()).onChanged(any(Command.HideKeyboard.javaClass))
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
        val site = testee.siteLiveData.value
        assertTrue(site?.isDesktopMode == true)
    }

    @Test
    fun whenUserSelectsMobileSiteThenMobileModeStateUpdated() {
        loadUrl("http://example.com")
        setDesktopBrowsingMode(true)
        testee.onChangeBrowserModeClicked()
        verify(mockPixel).fire(AppPixelName.MENU_ACTION_DESKTOP_SITE_DISABLE_PRESSED)
        assertFalse(browserViewState().isDesktopBrowsingMode)
        val site = testee.siteLiveData.value
        assertFalse(site?.isDesktopMode == true)
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

        val issuedCommand = commandCaptor.allValues.find { it is NavigationCommand.NavigateBack }
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
        assertTrue(captureCommands().lastValue == NavigationCommand.NavigateForward)
    }

    @Test
    fun whenUserOnHomePressesForwardThenBrowserShownAndPageRefreshed() {
        setBrowserShowing(false)
        testee.onUserPressedForward()
        assertTrue(browserViewState().browserShowing)
        assertTrue(captureCommands().lastValue == NavigationCommand.Refresh)
    }

    @Test
    fun whenRefreshRequestedWithInvalidatedGlobalLayoutThenOpenCurrentUrlInNewTab() {
        givenOneActiveTabSelected()
        givenInvalidatedGlobalLayout()

        testee.onRefreshRequested()

        assertCommandIssued<Command.OpenInNewTab> {
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
        assertCommandIssued<NavigationCommand.Refresh>()
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

        val backCommand = captureCommands().lastValue as NavigationCommand.NavigateBack
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
        assertTrue(ultimateCommand == NavigationCommand.Refresh)
    }

    @Test
    fun whenUserSelectsMobileSiteWhenOnMobileSpecificSiteThenUrlNotModified() {
        loadUrl("http://m.example.com")
        setDesktopBrowsingMode(true)
        testee.onChangeBrowserModeClicked()
        verify(mockCommandObserver, atLeastOnce()).onChanged(commandCaptor.capture())
        val ultimateCommand = commandCaptor.lastValue
        assertTrue(ultimateCommand == NavigationCommand.Refresh)
    }

    @Test
    fun whenUserSelectsMobileSiteWhenNotOnMobileSpecificSiteThenUrlNotModified() {
        loadUrl("http://example.com")
        setDesktopBrowsingMode(true)
        testee.onChangeBrowserModeClicked()
        verify(mockCommandObserver, atLeastOnce()).onChanged(commandCaptor.capture())
        val ultimateCommand = commandCaptor.lastValue
        assertTrue(ultimateCommand == NavigationCommand.Refresh)
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
        val bookmark = Bookmark(
            id = UUID.randomUUID().toString(),
            title = title,
            url = url,
            parentId = UUID.randomUUID().toString(),
            lastModified = "timestamp",
        )
        whenever(mockSavedSitesRepository.insertBookmark(title = anyString(), url = anyString())).thenReturn(bookmark)
        loadUrl(url = url)
        testee.titleReceived(newTitle = title)
        testee.onBookmarkMenuClicked()
        val command = captureCommands().value as Command.ShowSavedSiteAddedConfirmation
        assertEquals(url, command.savedSiteChangedViewState.savedSite.url)
        assertEquals(title, command.savedSiteChangedViewState.savedSite.title)
    }

    @Test
    fun whenNoSiteAndUserSelectsToAddBookmarkThenBookmarkIsNotAdded() = runTest {
        val bookmark = Bookmark(
            id = UUID.randomUUID().toString(),
            title = "A title",
            url = "www.example.com",
            parentId = UUID.randomUUID().toString(),
            lastModified = "timestamp",
        )
        whenever(mockSavedSitesRepository.insertBookmark(anyString(), anyString())).thenReturn(bookmark)

        testee.onBookmarkMenuClicked()

        verify(mockSavedSitesRepository, times(0)).insert(bookmark)
    }

    @Test
    fun whenPrivacyProtectionMenuClickedAndSiteNotInAllowListThenSiteAddedToAllowListAndPixelSentAndPageRefreshed() = runTest {
        whenever(mockUserAllowListRepository.isDomainInUserAllowList("www.example.com")).thenReturn(false)
        loadUrl("http://www.example.com/home.html")
        testee.onPrivacyProtectionMenuClicked()
        verify(mockUserAllowListRepository).addDomainToUserAllowList("www.example.com")
        verify(mockPixel).fire(AppPixelName.BROWSER_MENU_ALLOWLIST_ADD)
    }

    @Test
    fun whenPrivacyProtectionMenuClickedAndSiteNotInAllowListThenShowDisabledConfirmationMessage() = runTest {
        whenever(mockUserAllowListRepository.isDomainInUserAllowList("www.example.com")).thenReturn(false)
        loadUrl("http://www.example.com/home.html")
        testee.onPrivacyProtectionMenuClicked()
        assertCommandIssued<ShowPrivacyProtectionDisabledConfirmation> {
            assertEquals("www.example.com", this.domain)
        }
    }

    @Test
    fun whenPrivacyProtectionMenuClickedForAllowListedSiteThenSiteRemovedFromAllowListAndPixelSentAndPageRefreshed() = runTest {
        whenever(mockUserAllowListRepository.isDomainInUserAllowList("www.example.com")).thenReturn(true)
        loadUrl("http://www.example.com/home.html")
        testee.onPrivacyProtectionMenuClicked()
        verify(mockUserAllowListRepository).removeDomainFromUserAllowList("www.example.com")
        verify(mockPixel).fire(AppPixelName.BROWSER_MENU_ALLOWLIST_REMOVE)
    }

    @Test
    fun whenPrivacyProtectionMenuClickedForAllowListedSiteThenShowDisabledConfirmationMessage() = runTest {
        whenever(mockUserAllowListRepository.isDomainInUserAllowList("www.example.com")).thenReturn(true)
        loadUrl("http://www.example.com/home.html")
        testee.onPrivacyProtectionMenuClicked()
        assertCommandIssued<ShowPrivacyProtectionEnabledConfirmation> {
            assertEquals("www.example.com", this.domain)
        }
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
        assertCommandNotIssued<Command.ShareLink>()
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
        assertFalse(browserViewState().canFindInPage)
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
        whenever(mockSavedSitesRepository.hasBookmarks()).thenReturn(true)
        val suggestion = AutoCompleteBookmarkSuggestion("example", "Example", "https://example.com")
        testee.autoCompleteViewState.value = autoCompleteViewState().copy(searchResults = AutoCompleteResult("", listOf(suggestion)))
        testee.fireAutocompletePixel(suggestion)
        verify(mockPixel).fire(AppPixelName.AUTOCOMPLETE_BOOKMARK_SELECTION, pixelParams(showedBookmarks = true, bookmarkCapable = true))
    }

    @Test
    fun whenSearchSuggestionSubmittedWithBookmarksThenAutoCompleteSearchSelectionPixelSent() = runTest {
        whenever(mockSavedSitesRepository.hasBookmarks()).thenReturn(true)
        val suggestions = listOf(AutoCompleteSearchSuggestion("", false), AutoCompleteBookmarkSuggestion("", "", ""))
        testee.autoCompleteViewState.value = autoCompleteViewState().copy(searchResults = AutoCompleteResult("", suggestions))
        testee.fireAutocompletePixel(AutoCompleteSearchSuggestion("example", false))

        verify(mockPixel).fire(AppPixelName.AUTOCOMPLETE_SEARCH_SELECTION, pixelParams(showedBookmarks = true, bookmarkCapable = true))
    }

    @Test
    fun whenSearchSuggestionSubmittedWithoutBookmarksThenAutoCompleteSearchSelectionPixelSent() = runTest {
        whenever(mockSavedSitesRepository.hasBookmarks()).thenReturn(false)
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
        assertCommandIssued<Command.GenerateWebViewPreviewImage>()
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
        val testSurvey = Survey("abc", "http://example.com", daysInstalled = 1, status = Survey.Status.SCHEDULED)
        whenever(mockSurveyRepository.shouldShowSurvey(testSurvey)).thenReturn(true)
        testee.onSurveyChanged(testSurvey, Locale.US)
        assertTrue(testee.ctaViewState.value!!.cta is HomePanelCta.Survey)
    }

    @Test
    fun whenScheduledSurveyIsNullThenCtaIsNotSurvey() {
        testee.onSurveyChanged(null)
        assertFalse(testee.ctaViewState.value!!.cta is HomePanelCta.Survey)
    }

    @Test
    fun whenCtaRefreshedAndAutoAddSupportedAndWidgetNotInstalledThenCtaIsAutoWidget() = runTest {
        whenever(mockWidgetCapabilities.supportsAutomaticWidgetAdd).thenReturn(true)
        whenever(mockWidgetCapabilities.hasInstalledWidgets).thenReturn(false)
        testee.refreshCta()
        assertEquals(HomePanelCta.AddWidgetAuto, testee.ctaViewState.value!!.cta)
    }

    @Test
    fun whenCtaRefreshedAndAutoAddSupportedAndWidgetAlreadyInstalledThenCtaIsNull() = runTest {
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
        whenever(mockWidgetCapabilities.supportsAutomaticWidgetAdd).thenReturn(false)
        whenever(mockWidgetCapabilities.hasInstalledWidgets).thenReturn(true)
        testee.refreshCta()
        assertNull(testee.ctaViewState.value!!.cta)
    }

    @Test
    fun whenCtaRefreshedAndIsNewTabIsFalseThenReturnNull() = runTest {
        setBrowserShowing(true)
        whenever(mockWidgetCapabilities.supportsAutomaticWidgetAdd).thenReturn(true)
        whenever(mockWidgetCapabilities.hasInstalledWidgets).thenReturn(false)
        testee.refreshCta()
        assertNull(testee.ctaViewState.value!!.cta)
    }

    @Test
    fun whenCtaShownThenFirePixel() = runTest {
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
    fun whenRegisterDaxBubbleCtaDismissedThenCtaChangedToNull() = runTest {
        val cta = DaxBubbleCta.DaxIntroCta(mockOnboardingStore, mockAppInstallStore)
        testee.ctaViewState.value = BrowserTabViewModel.CtaViewState(cta = cta)

        testee.registerDaxBubbleCtaDismissed()
        assertNull(testee.ctaViewState.value!!.cta)
    }

    @Test
    fun whenRefreshCtaIfCtaAlreadyShownForCurrentPageThenReturnNull() = runTest {
        setBrowserShowing(isBrowsing = true)
        testee.hasCtaBeenShownForCurrentPage.set(true)

        assertNull(testee.refreshCta())
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
        setBrowserShowing(isBrowsing = false)
        whenever(mockWidgetCapabilities.hasInstalledWidgets).thenReturn(true)

        givenShownCtas(CtaId.DAX_INTRO, CtaId.DAX_END)
        testee.onSurveyChanged(Survey("abc", "http://example.com", daysInstalled = 1, status = Survey.Status.SCHEDULED))
        testee.onUserDismissedCta()
        assertNull(testee.ctaViewState.value!!.cta)
    }

    @Test
    fun whenSurveyCtaDismissedAndWidgetCtaIsPossibleThenNextCtaIsWidget() = runTest {
        setBrowserShowing(isBrowsing = false)
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
        assertTrue(command is Command.DaxCommand.FinishPartialTrackerAnimation)
    }

    @Test
    fun whenUserDismissDifferentThanDaxTrackersBlockedDialogThenFinishTrackerAnimationCommandNotSent() {
        val cta = DaxDialogCta.DaxSerpCta(mockOnboardingStore, mockAppInstallStore)
        setCta(cta)
        testee.onDaxDialogDismissed()
        assertCommandNotIssued<Command.DaxCommand.FinishPartialTrackerAnimation>()
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
        verify(mockSurveyRepository).cancelScheduledSurveys()
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
    fun whenUserRemovesFireproofWebsiteFromOptionMenuThenShowConfirmationIsIssued() {
        givenFireproofWebsiteDomain("mobile.example.com")
        loadUrl("http://mobile.example.com/", isBrowserShowing = true)
        testee.onFireproofWebsiteMenuClicked()
        assertCommandIssued<Command.DeleteFireproofConfirmation> {
            assertEquals("mobile.example.com", this.fireproofWebsiteEntity.domain)
        }
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
    fun whenUserClicksOnRemoveFireproofingSnackbarUndoActionThenFireproofWebsiteIsAddedBack() {
        givenFireproofWebsiteDomain("example.com")
        loadUrl("http://example.com/", isBrowserShowing = true)
        testee.onFireproofWebsiteMenuClicked()
        assertCommandIssued<Command.DeleteFireproofConfirmation> {
            testee.onRemoveFireproofWebsiteSnackbarUndoClicked(this.fireproofWebsiteEntity)
        }
        assertTrue(browserViewState().canFireproofSite)
        assertTrue(browserViewState().isFireproofWebsite)
    }

    @Test
    fun whenUserClicksOnRemoveFireproofingSnackbarUndoActionThenPixelSent() {
        givenFireproofWebsiteDomain("example.com")
        loadUrl("http://example.com/", isBrowserShowing = true)
        testee.onFireproofWebsiteMenuClicked()
        assertCommandIssued<Command.DeleteFireproofConfirmation> {
            testee.onRemoveFireproofWebsiteSnackbarUndoClicked(this.fireproofWebsiteEntity)
        }
        verify(mockPixel).fire(AppPixelName.FIREPROOF_REMOVE_WEBSITE_UNDO)
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
    fun whenLoginDetectedAndAutomaticFireproofSettingIsAskEveryTimeThenAskToFireproofWebsite() {
        loginEventLiveData.value = givenLoginDetected("example.com")
        assertCommandIssued<Command.AskToFireproofWebsite> {
            assertEquals(FireproofWebsiteEntity("example.com"), this.fireproofWebsite)
        }
    }

    @Test
    fun whenLoginDetectedAndAutomaticFireproofSettingIsAlwaysThenDoNotAskToFireproofWebsite() {
        whenever(mockSettingsDataStore.automaticFireproofSetting).thenReturn(AutomaticFireproofSetting.ALWAYS)
        loginEventLiveData.value = givenLoginDetected("example.com")
        assertCommandNotIssued<Command.AskToFireproofWebsite>()
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
    fun whenUserBrowsingPressesBackThenCannotAllowList() {
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
        assertFalse(browserViewState().canFindInPage)
    }

    @Test
    fun whenUserBrowsingPressesBackThenCannotPrintPage() {
        setupNavigation(skipHome = false, isBrowsing = true, canGoBack = false)
        assertTrue(testee.onUserPressedBack())
        assertFalse(browserViewState().canPrintPage)
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
    fun whenUserBrowsingPressesBackAndForwardThenCanAllowList() {
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
        assertTrue(browserViewState().canFindInPage)
    }

    @Test
    fun whenUserBrowsingPressesBackAndForwardThenCanPrint() {
        setupNavigation(skipHome = false, isBrowsing = true, canGoBack = false)
        testee.onUserPressedBack()
        testee.onUserPressedForward()
        assertTrue(browserViewState().canPrintPage)
    }

    @Test
    fun whenBrowsingDDGSiteThenDaxIconIsVisible() {
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
        assertTrue(browserViewState().showPrivacyShield)
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
    fun whenUserGrantsSystemLocationPermissionThenSettingsLocationPermissionShouldBeEnabled() = runTest {
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
        val bookmark = Bookmark(
            id = UUID.randomUUID().toString(),
            title = title,
            url = url,
            parentId = UUID.randomUUID().toString(),
            lastModified = "timestamp",
        )
        whenever(mockSavedSitesRepository.insertBookmark(title = anyString(), url = anyString())).thenReturn(bookmark)
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

        testee.consumeAliasAndCopyToClipboard()

        verify(mockPixel).enqueueFire(
            AppPixelName.EMAIL_COPIED_TO_CLIPBOARD,
            mapOf(Pixel.PixelParameter.COHORT to "cohort", Pixel.PixelParameter.LAST_USED_DAY to "2021-01-01"),
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
    fun whenEmailSignOutEventThenEmailSignEventCommandSent() = runTest {
        emailStateFlow.emit(false)

        assertCommandIssued<Command.EmailSignEvent>()
    }

    @Test
    fun whenEmailIsSignedInThenEmailSignEventCommandSent() = runTest {
        emailStateFlow.emit(true)

        assertCommandIssued<Command.EmailSignEvent>()
    }

    @Test
    fun whenConsumeAliasThenInjectAddressCommandSent() {
        whenever(mockEmailManager.getAlias()).thenReturn("alias")

        testee.usePrivateDuckAddress("", "alias")

        assertCommandIssued<Command.InjectEmailAddress> {
            assertEquals("alias", this.duckAddress)
        }
    }

    @Test
    fun whenUseAddressThenInjectAddressCommandSent() {
        whenever(mockEmailManager.getEmailAddress()).thenReturn("address")

        testee.usePersonalDuckAddress("", "address")

        assertCommandIssued<Command.InjectEmailAddress> {
            assertEquals("address", this.duckAddress)
        }
    }

    @Test
    fun whenShowEmailTooltipIfAddressExistsThenShowEmailTooltipCommandSent() {
        whenever(mockEmailManager.getEmailAddress()).thenReturn("address")

        testee.showEmailProtectionChooseEmailPrompt()

        assertCommandIssued<Command.ShowEmailProtectionChooseEmailPrompt> {
            assertEquals("address", this.address)
        }
    }

    @Test
    fun whenShowEmailTooltipIfAddressDoesNotExistThenCommandNotSent() {
        whenever(mockEmailManager.getEmailAddress()).thenReturn(null)

        testee.showEmailProtectionChooseEmailPrompt()

        assertCommandNotIssued<Command.ShowEmailProtectionChooseEmailPrompt>()
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
    fun whenUserSelectsToPrintPageAndCountryFromLetterFormatDefinedSetThenPrintLinkCommandSentWithLetter() {
        whenever(mockDeviceInfo.country).thenReturn("US")
        loadUrl("foo.com")
        testee.onPrintSelected()
        val command = captureCommands().value as Command.PrintLink
        assertEquals("foo.com", command.url)
        assertEquals(PrintAttributes.MediaSize.NA_LETTER, command.mediaSize)
    }

    @Test
    fun whenUserSelectsToPrintPageAndCountryNotFromLetterFormatDefinedSetThenPrintLinkCommandSentWithA4() {
        whenever(mockDeviceInfo.country).thenReturn("FR")
        loadUrl("foo.com")
        testee.onPrintSelected()
        val command = captureCommands().value as Command.PrintLink
        assertEquals("foo.com", command.url)
        assertEquals(PrintAttributes.MediaSize.ISO_A4, command.mediaSize)
    }

    @Test
    fun whenUserSelectsToPrintPageAndCountryIsEmptyThenPrintLinkCommandSentWithA4() {
        whenever(mockDeviceInfo.country).thenReturn("")
        loadUrl("foo.com")
        testee.onPrintSelected()
        val command = captureCommands().value as Command.PrintLink
        assertEquals("foo.com", command.url)
        assertEquals(PrintAttributes.MediaSize.ISO_A4, command.mediaSize)
    }

    @Test
    fun whenUserSelectsToPrintPageThenPixelIsSent() {
        whenever(mockDeviceInfo.country).thenReturn("US")
        loadUrl("foo.com")
        testee.onPrintSelected()
        verify(mockPixel).fire(AppPixelName.MENU_ACTION_PRINT_PRESSED)
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
    fun whenLoadUrlAndUrlIsInContentBlockingExceptionsListThenIsPrivacyProtectionDisabledIsTrue() {
        whenever(mockContentBlocking.isAnException("example.com")).thenReturn(true)
        loadUrl("https://example.com")
        assertTrue(browserViewState().isPrivacyProtectionDisabled)
    }

    @Test
    fun whenLoadUrlAndUrlIsInContentBlockingExceptionsListThenPrivacyOnIsFalse() {
        whenever(mockContentBlocking.isAnException("example.com")).thenReturn(true)
        loadUrl("https://example.com")
        assertFalse(loadingViewState().privacyOn)
    }

    @Test
    fun whenEditBookmarkRequestedThenRepositoryIsNotUpdated() = runTest {
        val url = "http://www.example.com"
        val bookmark = Bookmark(
            id = UUID.randomUUID().toString(),
            title = "",
            url = url,
            parentId = UUID.randomUUID().toString(),
            lastModified = "timestamp",
        )
        whenever(mockSavedSitesRepository.getBookmark(url = url)).thenReturn(bookmark)
        bookmarksListFlow.send(listOf(bookmark))
        loadUrl(url = url, isBrowserShowing = true)
        testee.onBookmarkMenuClicked()
        verify(mockSavedSitesRepository, never()).insertBookmark(title = anyString(), url = anyString())
    }

    @Test
    fun whenEditBookmarkRequestedThenEditBookmarkPressedPixelIsFired() = runTest {
        val bookmark = Bookmark(
            id = UUID.randomUUID().toString(),
            title = "title",
            url = "www.example.com",
            parentId = UUID.randomUUID().toString(),
            lastModified = "timestamp",
        )
        whenever(mockSavedSitesRepository.getBookmark("www.example.com")).thenReturn(bookmark)
        bookmarksListFlow.send(listOf(bookmark))
        loadUrl("www.example.com", isBrowserShowing = true)
        testee.onBookmarkMenuClicked()
        verify(mockPixel).fire(AppPixelName.MENU_ACTION_EDIT_BOOKMARK_PRESSED.pixelName)
    }

    @Test
    fun whenEditBookmarkRequestedThenEditDialogIsShownWithCorrectUrlAndTitle() = runTest {
        val bookmark = Bookmark(
            id = UUID.randomUUID().toString(),
            title = "title",
            url = "www.example.com",
            parentId = UUID.randomUUID().toString(),
            lastModified = "timestamp",
        )
        whenever(mockSavedSitesRepository.getBookmark("www.example.com")).thenReturn(bookmark)
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
        val favoriteSite = Favorite(id = UUID.randomUUID().toString(), title = "", url = "www.example.com", position = 0, lastModified = "timestamp")
        favoriteListFlow.send(listOf(favoriteSite))
        loadUrl("www.example.com", isBrowserShowing = true)
        testee.onFavoriteMenuClicked()
        verify(mockSavedSitesRepository, never()).insert(any<Favorite>())
    }

    @Test
    fun whenRemoveFavoriteRequestedThenRemoveFavoritePressedPixelIsFired() = runTest {
        val favoriteSite = Favorite(id = UUID.randomUUID().toString(), title = "", url = "www.example.com", position = 0, lastModified = "timestamp")
        whenever(mockSavedSitesRepository.getFavorite("www.example.com")).thenReturn(favoriteSite)
        favoriteListFlow.send(listOf(favoriteSite))
        loadUrl("www.example.com", isBrowserShowing = true)
        testee.onFavoriteMenuClicked()
        verify(mockPixel).fire(
            AppPixelName.MENU_ACTION_REMOVE_FAVORITE_PRESSED.pixelName,
        )
    }

    @Test
    fun whenRemoveFavoriteRequestedThenViewStateUpdated() = runTest {
        val favoriteSite = Favorite(id = UUID.randomUUID().toString(), title = "", url = "www.example.com", position = 0, lastModified = "timestamp")
        whenever(mockSavedSitesRepository.getFavorite("www.example.com")).thenReturn(favoriteSite)
        favoriteListFlow.send(listOf(favoriteSite))
        loadUrl("www.example.com", isBrowserShowing = true)
        testee.onFavoriteMenuClicked()

        assertTrue(browserViewState().favorite == null)
    }

    @Test
    fun whenRemoveFavoriteRequestedThenDeleteConfirmationDialogIsShownWithCorrectUrlAndTitle() = runTest {
        val favoriteSite = Favorite(
            id = UUID.randomUUID().toString(),
            title = "title",
            url = "www.example.com",
            position = 0,
            lastModified = "timestamp",
        )
        whenever(mockSavedSitesRepository.getFavorite("www.example.com")).thenReturn(favoriteSite)
        favoriteListFlow.send(listOf(favoriteSite))
        loadUrl("www.example.com", isBrowserShowing = true)
        testee.onFavoriteMenuClicked()
        verify(mockCommandObserver, atLeastOnce()).onChanged(commandCaptor.capture())
        assertTrue(commandCaptor.lastValue is Command.DeleteFavoriteConfirmation)
        val command = commandCaptor.lastValue as Command.DeleteFavoriteConfirmation
        assertEquals("www.example.com", command.savedSite.url)
        assertEquals("title", command.savedSite.title)
    }

    @Test
    fun whenRemoveFavoriteUndoThenViewStateUpdated() = runTest {
        val favoriteSite = Favorite(id = UUID.randomUUID().toString(), title = "", url = "www.example.com", position = 0, lastModified = "timestamp")
        val quickAccessFavorites = listOf(FavoritesQuickAccessAdapter.QuickAccessFavorite(favoriteSite))

        whenever(mockSavedSitesRepository.getFavorite("www.example.com")).thenReturn(favoriteSite)
        favoriteListFlow.send(listOf(favoriteSite))
        loadUrl("www.example.com", isBrowserShowing = true)
        testee.onFavoriteMenuClicked()

        assertTrue(browserViewState().favorite == null)
        assertTrue(autoCompleteViewState().favorites.isEmpty())
        assertTrue(ctaViewState().favorites.isEmpty())

        testee.undoDelete(favoriteSite)

        assertTrue(browserViewState().favorite == favoriteSite)
        assertTrue(autoCompleteViewState().favorites == quickAccessFavorites)
        assertTrue(ctaViewState().favorites == quickAccessFavorites)
    }

    @Test
    fun whenDeleteBookmarkUndoThenViewStateUpdated() = runTest {
        val bookmark =
            Bookmark(id = UUID.randomUUID().toString(), title = "A title", url = "www.example.com", lastModified = "timestamp")

        bookmarksListFlow.send(listOf(bookmark))

        loadUrl(bookmark.url, isBrowserShowing = true)

        testee.onSavedSiteDeleted(bookmark)

        assertTrue(browserViewState().bookmark == null)
        assertTrue(browserViewState().favorite == null)

        testee.undoDelete(bookmark)

        assertTrue(browserViewState().bookmark == bookmark)
    }

    @Test
    fun whenDeleteFavouriteUndoThenViewStateUpdated() = runTest {
        val favourite =
            Favorite(id = UUID.randomUUID().toString(), title = "A title", url = "www.example.com", lastModified = "timestamp", 0)

        favoriteListFlow.send(listOf(favourite))

        loadUrl(favourite.url, isBrowserShowing = true)

        testee.onSavedSiteDeleted(favourite)

        assertTrue(browserViewState().bookmark == null)
        assertTrue(browserViewState().favorite == null)

        testee.undoDelete(favourite)

        assertTrue(browserViewState().favorite == favourite)
    }

    @Test
    fun whenPageChangedThenUpdatePreviousUrlAndUserQueryStateSetToFalse() {
        loadUrl(url = "www.example.com", isBrowserShowing = true)
        verify(mockAppLinksHandler).updatePreviousUrl("www.example.com")
        verify(mockAppLinksHandler).setUserQueryState(false)
    }

    @Test
    fun whenPageChangedThenSetCtaBeenShownForCurrentPageToFalse() {
        testee.hasCtaBeenShownForCurrentPage.set(true)
        loadUrl(url = "www.example.com", isBrowserShowing = true)
        assertFalse(testee.hasCtaBeenShownForCurrentPage.get())
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
    fun whenDownloadIsCalledThenDownloadRequestedForUrl() = runTest {
        val pendingFileDownload = buildPendingDownload(url = "http://www.example.com/download.pdf", contentDisposition = null, mimeType = null)

        testee.download(pendingFileDownload)

        verify(mockFileDownloader).enqueueDownload(pendingFileDownload)
    }

    private fun buildPendingDownload(
        url: String,
        contentDisposition: String?,
        mimeType: String?,
    ): PendingFileDownload {
        return PendingFileDownload(
            url = url,
            contentDisposition = contentDisposition,
            mimeType = mimeType,
            subfolder = "folder",
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
    fun whenUrlExtractedThenIssueLoadExtractedUrlCommand() {
        whenever(mockAmpLinks.processDestinationUrl(anyString(), anyOrNull())).thenReturn("http://example.com")
        testee.onUrlExtracted("http://foo.com", "http://example.com")
        verify(mockCommandObserver, atLeastOnce()).onChanged(commandCaptor.capture())
        val issuedCommand = commandCaptor.allValues.find { it is LoadExtractedUrl }
        assertEquals("http://example.com", (issuedCommand as LoadExtractedUrl).extractedUrl)
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
        verify(mockPixel).fire(AppPixelName.REMOTE_MESSAGE_SHOWN_UNIQUE, mapOf("message" to "id1"))
        verify(mockPixel).fire(AppPixelName.REMOTE_MESSAGE_SHOWN, mapOf("message" to "id1"))
    }

    @Test
    fun whenRemoteMessageCloseButtonClickedThenFirePixelAndDismiss() = runTest {
        val remoteMessage = RemoteMessage("id1", Content.Small("", ""), emptyList(), emptyList())
        givenRemoteMessage(remoteMessage)
        testee.onViewVisible()

        testee.onMessageCloseButtonClicked()

        verify(mockRemoteMessagingRepository).dismissMessage("id1")
        verify(mockPixel).fire(AppPixelName.REMOTE_MESSAGE_DISMISSED, mapOf("message" to "id1"))
    }

    @Test
    fun whenRemoteMessagePrimaryButtonClickedThenFirePixelAndDismiss() = runTest {
        val remoteMessage = RemoteMessage("id1", Content.Small("", ""), emptyList(), emptyList())
        givenRemoteMessage(remoteMessage)
        testee.onViewVisible()

        testee.onMessagePrimaryButtonClicked()

        verify(mockRemoteMessagingRepository).dismissMessage("id1")
        verify(mockPixel).fire(AppPixelName.REMOTE_MESSAGE_PRIMARY_ACTION_CLICKED, mapOf("message" to "id1"))
    }

    @Test
    fun whenRemoteMessageSecondaryButtonClickedThenFirePixelAndDismiss() = runTest {
        val remoteMessage = RemoteMessage("id1", Content.Small("", ""), emptyList(), emptyList())
        givenRemoteMessage(remoteMessage)
        testee.onViewVisible()

        testee.onMessageSecondaryButtonClicked()

        verify(mockRemoteMessagingRepository).dismissMessage("id1")
        verify(mockPixel).fire(AppPixelName.REMOTE_MESSAGE_SECONDARY_ACTION_CLICKED, mapOf("message" to "id1"))
    }

    @Test
    fun whenRemoteMessageActionButtonClickedThenFirePixelAndDontDismiss() = runTest {
        val remoteMessage = RemoteMessage("id1", Content.Small("", ""), emptyList(), emptyList())
        givenRemoteMessage(remoteMessage)
        testee.onViewVisible()

        testee.onMessageActionButtonClicked()

        verify(mockRemoteMessagingRepository, never()).dismissMessage("id1")
        verify(mockPixel).fire(AppPixelName.REMOTE_MESSAGE_ACTION_CLICKED, mapOf("message" to "id1"))
    }

    @Test
    fun whenConfigurationChangesThenForceRenderingMenu() {
        val oldForceRenderingTicker = browserViewState().forceRenderingTicker

        testee.onConfigurationChanged()

        assertTrue(oldForceRenderingTicker != browserViewState().forceRenderingTicker)
    }

    @Test
    fun whenShouldShowVoiceSearchAndUserSubmittedQueryThenUpdateOmnibarViewStateToShowVoiceSearchTrue() {
        whenever(mockOmnibarConverter.convertQueryToUrl("foo", null)).thenReturn("foo.com")
        whenever(voiceSearchAvailability.shouldShowVoiceSearch(anyBoolean(), anyString(), anyBoolean(), anyString())).thenReturn(true)

        testee.onUserSubmittedQuery("foo")

        assertTrue(browserViewState().showVoiceSearch)
    }

    @Test
    fun whenShouldShowVoiceSearchAndUserNavigatesHomeThenUpdateOmnibarViewStateToShowVoiceSearchTrue() {
        whenever(voiceSearchAvailability.shouldShowVoiceSearch(anyBoolean(), anyString(), anyBoolean(), anyString())).thenReturn(true)
        setupNavigation(skipHome = false, isBrowsing = true, canGoBack = false)

        testee.onUserPressedBack()

        assertTrue(browserViewState().showVoiceSearch)
    }

    @Test
    fun whenShouldShowVoiceSearchAndUserLoadedUrlThenUpdateOmnibarViewStateToShowVoiceSearchTrue() {
        whenever(voiceSearchAvailability.shouldShowVoiceSearch(anyBoolean(), anyString(), anyBoolean(), anyString())).thenReturn(true)

        loadUrl("https://test.com")

        assertTrue(browserViewState().showVoiceSearch)
    }

    @Test
    fun whenShouldShowVoiceSearchAndOmnibarInputStateChangedThenUpdateOmnibarViewStateToShowVoiceSearchTrue() {
        whenever(voiceSearchAvailability.shouldShowVoiceSearch(anyBoolean(), anyString(), anyBoolean(), anyString())).thenReturn(true)

        testee.onOmnibarInputStateChanged("www.fb.com", true, hasQueryChanged = false)

        assertTrue(browserViewState().showVoiceSearch)
    }

    @Test
    fun whenInitializedAndVoiceSearchNotSupportedThenDontLogVoiceSearch() {
        verify(voiceSearchPixelLogger, never()).log()
    }

    @Test
    fun whenMessageReceivedThenSetLinkOpenedInNewTabToTrue() {
        assertFalse(testee.linkOpenedInNewTab())
        testee.onMessageReceived()
        assertTrue(testee.linkOpenedInNewTab())
    }

    @Test
    fun whenPageChangedThenSetLinkOpenedInNewTabToFalse() {
        testee.onMessageReceived()
        loadUrl(url = "www.example.com", isBrowserShowing = true)
        assertFalse(testee.linkOpenedInNewTab())
    }

    @Test
    fun whenUserLongPressedBackOnEmptyStackBrowserNotShowingThenShowHistoryCommandNotSent() {
        setBrowserShowing(false)
        testee.onUserLongPressedBack()
        assertCommandNotIssued<ShowBackNavigationHistory>()
    }

    @Test
    fun whenUserLongPressedBackOnEmptyStackBrowserShowingThenShowHistoryCommandNotSent() {
        buildNavigationHistoryStack(stackSize = 0)
        testee.onUserLongPressedBack()
        assertCommandNotIssued<ShowBackNavigationHistory>()
    }

    @Test
    fun whenUserLongPressedBackOnSingleStackEntryThenShowHistoryCommandNotSent() {
        buildNavigationHistoryStack(stackSize = 1)
        testee.onUserLongPressedBack()
        assertCommandNotIssued<ShowBackNavigationHistory>()
    }

    @Test
    fun whenUserLongPressedBackOnStackWithMultipleEntriesThenShowHistoryCommandSent() {
        buildNavigationHistoryStack(stackSize = 10)
        testee.onUserLongPressedBack()
        assertShowHistoryCommandSent(expectedStackSize = 9)
    }

    @Test
    fun whenUserLongPressedBackOnStackWithMoreThanTenEntriesThenTruncatedToMostRecentOnly() {
        buildNavigationHistoryStack(stackSize = 20)
        testee.onUserLongPressedBack()
        assertShowHistoryCommandSent(expectedStackSize = 10)
    }

    @Test
    fun whenReturnNoCredentialsWithPageThenEmitCancelIncomingAutofillRequestCommand() = runTest {
        val url = "originalurl.com"
        testee.returnNoCredentialsWithPage(url)

        assertCommandIssued<Command.CancelIncomingAutofillRequest> {
            assertEquals(url, this.url)
        }
    }

    @Test
    fun whenOnAutoconsentResultReceivedThenSiteUpdated() {
        updateUrl("http://www.example.com/", "http://twitter.com/explore", true)
        testee.onAutoconsentResultReceived(consentManaged = true, optOutFailed = true, selfTestFailed = true, isCosmetic = true)
        assertTrue(testee.siteLiveData.value?.consentManaged!!)
        assertTrue(testee.siteLiveData.value?.consentOptOutFailed!!)
        assertTrue(testee.siteLiveData.value?.consentSelfTestFailed!!)
        assertTrue(testee.siteLiveData.value?.consentCosmeticHide!!)
    }

    @Test
    fun whenNotEditingUrlBarAndNotCancelledThenCanAutomaticallyShowAutofillPrompt() {
        configureOmnibarNotEditing()
        assertTrue(testee.canAutofillSelectCredentialsDialogCanAutomaticallyShow())
    }

    @Test
    fun whenEditingUrlBarAndNotCancelledThenCannotAutomaticallyShowAutofillPrompt() {
        configureOmnibarEditing()
        assertFalse(testee.canAutofillSelectCredentialsDialogCanAutomaticallyShow())
    }

    @Test
    fun whenNotEditingUrlBarAndCancelledThenCannotAutomaticallyShowAutofillPrompt() {
        configureOmnibarNotEditing()
        testee.cancelPendingAutofillRequestToChooseCredentials()
        assertFalse(testee.canAutofillSelectCredentialsDialogCanAutomaticallyShow())
    }

    @Test
    fun whenEditingUrlBarAndCancelledThenCannotAutomaticallyShowAutofillPrompt() {
        configureOmnibarEditing()
        testee.cancelPendingAutofillRequestToChooseCredentials()
        assertFalse(testee.canAutofillSelectCredentialsDialogCanAutomaticallyShow())
    }

    @Test
    fun whenNavigationStateChangesSameSiteThenShowAutofillPromptFlagIsReset() {
        testee.cancelPendingAutofillRequestToChooseCredentials()
        updateUrl("example.com", "example.com", true)
        assertTrue(testee.canAutofillSelectCredentialsDialogCanAutomaticallyShow())
    }

    @Test
    fun whenNavigationStateChangesDifferentSiteThenShowAutofillPromptFlagIsReset() {
        testee.cancelPendingAutofillRequestToChooseCredentials()
        updateUrl("example.com", "foo.com", true)
        assertTrue(testee.canAutofillSelectCredentialsDialogCanAutomaticallyShow())
    }

    @Test
    fun whenPageRefreshesThenShowAutofillPromptFlagIsReset() {
        testee.cancelPendingAutofillRequestToChooseCredentials()
        testee.onWebViewRefreshed()
        assertTrue(testee.canAutofillSelectCredentialsDialogCanAutomaticallyShow())
    }

    @Test
    fun whenShowingUserCredentialsSavedConfirmationAndCanAccessCredentialManagementScreenThenShouldShowLinkToViewCredential() = runTest {
        autofillCapabilityChecker.enabled = true
        testee.onShowUserCredentialsSaved(aCredential())
        assertCommandIssued<Command.ShowUserCredentialSavedOrUpdatedConfirmation> {
            assertTrue(this.includeShortcutToViewCredential)
        }
    }

    @Test
    fun whenShowingUserCredentialsSavedConfirmationAndCannotAccessCredentialManagementScreenThenShouldNotShowLinkToViewCredential() = runTest {
        autofillCapabilityChecker.enabled = false
        testee.onShowUserCredentialsSaved(aCredential())
        assertCommandIssued<Command.ShowUserCredentialSavedOrUpdatedConfirmation> {
            assertFalse(this.includeShortcutToViewCredential)
        }
    }

    @Test
    fun whenShowingUserCredentialsUpdatedConfirmationAndCanAccessCredentialManagementScreenThenShouldShowLinkToViewCredential() = runTest {
        autofillCapabilityChecker.enabled = true
        testee.onShowUserCredentialsUpdated(aCredential())
        assertCommandIssued<Command.ShowUserCredentialSavedOrUpdatedConfirmation> {
            assertTrue(this.includeShortcutToViewCredential)
        }
    }

    @Test
    fun whenShowingUserCredentialsUpdatedConfirmationAndCannotAccessCredentialManagementScreenThenShouldNotShowLinkToViewCredential() = runTest {
        autofillCapabilityChecker.enabled = false
        testee.onShowUserCredentialsUpdated(aCredential())
        assertCommandIssued<Command.ShowUserCredentialSavedOrUpdatedConfirmation> {
            assertFalse(this.includeShortcutToViewCredential)
        }
    }

    @Test
    fun whenMultipleTabsAndViewModelIsForActiveTabThenActiveTabReturnsTrue() {
        val tabId = "abc123"
        selectedTabLiveData.value = aTabEntity(id = tabId)
        loadTabWithId("foo")
        loadTabWithId("bar")
        loadTabWithId(tabId)
        assertTrue(testee.isActiveTab())
    }

    @Test
    fun whenMultipleTabsAndViewModelIsForInactiveTabThenActiveTabReturnsFalse() {
        val tabId = "abc123"
        selectedTabLiveData.value = aTabEntity(id = tabId)
        loadTabWithId(tabId)
        loadTabWithId("foo")
        loadTabWithId("bar")
        assertFalse(testee.isActiveTab())
    }

    @Test
    fun whenSingleTabThenActiveTabReturnsTrue() {
        val tabId = "abc123"
        selectedTabLiveData.value = aTabEntity(id = tabId)
        loadTabWithId(tabId)
        assertTrue(testee.isActiveTab())
    }

    @Test
    fun whenNoTabsThenActiveTabReturnsFalse() {
        assertFalse(testee.isActiveTab())
    }

    @Test
    fun whenUrlIsUpdatedWithDifferentHostThenForceUpdateShouldBeTrue() {
        val originalUrl = "http://www.example.com/"
        loadUrl(originalUrl, isBrowserShowing = true)
        updateUrl(originalUrl, "http://twitter.com/explore", true)

        assertTrue(omnibarViewState().forceExpand)
    }

    @Test
    fun whenUrlIsUpdateButSameHostThenForceUpdateShouldBeFalse() = runTest {
        val originalUrl = "https://www.example.com/search/sss#search=1~grid~0~25"
        loadUrl(originalUrl, isBrowserShowing = true)
        updateUrl(
            originalUrl,
            "https://www.example.com/search/sss#search=1~grid~0~28",
            true,
        )

        assertFalse(omnibarViewState().forceExpand)
    }

    @Test
    fun whenVoiceSearchIsDisabledThenShowVoiceSearchShouldBeFalse() {
        whenever(voiceSearchAvailability.shouldShowVoiceSearch(any(), any(), any(), any())).thenReturn(false)

        testee.voiceSearchDisabled()

        assertFalse(browserViewState().showVoiceSearch)
    }

    @Test
    fun whenOnSitePermissionRequestedThenSendCommand() = runTest {
        val request: PermissionRequest = mock()
        val sitePermissions = SitePermissions(
            autoAccept = listOf(PermissionRequest.RESOURCE_AUDIO_CAPTURE),
            userHandled = listOf(PermissionRequest.RESOURCE_VIDEO_CAPTURE),
        )
        whenever(request.origin).thenReturn("https://example.com".toUri())
        testee.onSitePermissionRequested(request, sitePermissions)
        assertCommandIssued<Command.ShowSitePermissionsDialog> {
            assertEquals(request, this.request)
            assertEquals(sitePermissions, this.permissionsToRequest)
        }
    }

    @Test
    fun whenBasicAuthCredentialsInUrlThenStrippedSafely() {
        val testUrls = listOf(
            // Valid basic auth URLs
            "https://user:pass@example.com",
            "http://user:pass@example.com",
            "ftp://user:pass@example.com",
            "https://user@example.com",
            "https://user:pass@sub.example.com",
            "https://user:pass@sub.sub.example.com",
            "https://user:pass@sub.example.com/path",
            "https://user:pass@sub.example.com/path?param=value",
            "https://user:pass@sub.example.com/path#fragment",
            "https://user:pass@sub.example.com/path?param=value#fragment",
            "https://user:pass@sub.example.com:8080",
            "https://user:pass@sub.example.com:8080/path",
            "https://user:pass@sub.example.com:8080/path?param=value",
            "https://user:pass@sub.example.com:8080/path#fragment",
            "https://user:pass@sub.example.com:8080/path?param=value#fragment",
            "https://user:pass@192.0.2.0",
            "https://user:pass@[2001:db8::1]",
            "https://user:pass@[2001:db8::1]/path",
            "https://user:pass@[2001:db8::1]/path?param=value",
            "https://user:pass@[2001:db8::1]/path#fragment",
            "https://user:pass@[2001:db8::1]/path?param=value#fragment",
            "https://user:pass@[2001:db8::1]:8080",
            "https://user:pass@[2001:db8::1]:8080/path",
            "https://user:pass@[2001:db8::1]:8080/path?param=value",
            "https://user:pass@[2001:db8::1]:8080/path#fragment",
            "https://user:pass@[2001:db8::1]:8080/path?param=value#fragment",
        )

        val expectedUrls = listOf(
            "https://example.com",
            "http://example.com",
            "ftp://example.com",
            "https://example.com",
            "https://sub.example.com",
            "https://sub.sub.example.com",
            "https://sub.example.com/path",
            "https://sub.example.com/path?param=value",
            "https://sub.example.com/path#fragment",
            "https://sub.example.com/path?param=value#fragment",
            "https://sub.example.com:8080",
            "https://sub.example.com:8080/path",
            "https://sub.example.com:8080/path?param=value",
            "https://sub.example.com:8080/path#fragment",
            "https://sub.example.com:8080/path?param=value#fragment",
            "https://192.0.2.0",
            "https://[2001:db8::1]",
            "https://[2001:db8::1]/path",
            "https://[2001:db8::1]/path?param=value",
            "https://[2001:db8::1]/path#fragment",
            "https://[2001:db8::1]/path?param=value#fragment",
            "https://[2001:db8::1]:8080",
            "https://[2001:db8::1]:8080/path",
            "https://[2001:db8::1]:8080/path?param=value",
            "https://[2001:db8::1]:8080/path#fragment",
            "https://[2001:db8::1]:8080/path?param=value#fragment",
        )

        for (i in testUrls.indices) {
            val actual = testee.stripBasicAuthFromUrl(testUrls[i])
            assertEquals(expectedUrls[i], actual)
        }
    }

    @Test
    fun whenNoBasicAuthProvidedThenDoNotAffectAddressBar() {
        val testUrls = listOf(
            // No basic auth, should not be affected
            "https://example.com/@?param=value",
            "https://example.com/@path/to/resource?param=value",
            "https://example.com#@fragment",
            "https://example.com/path/to/@resource#fragment",
            "https://example.com?param=%E2%82%AC",
            "https://example.com/@notbasicAuth?q=none#f",
            "https://example.com:8080/foobar/",
            "https://sub.domain.example.com/foobar/",
            "https://sub.domain.example.com:8080/?q=none#f",
            // IP address/port combinations
            "https://192.0.2.0",
            "https://192.0.2.0:1337",
            "https://[2001:db8::1]",
            "https://[2001:db8::1]/path?param=value#fragment",
            "https://[2001:db8::1]:8080",
            "https://[2001:db8::1]:8080/path",
            "https://[2001:db8::1]:8080/path?param=value",
            // invalid URLs, should do nothing
            "https://user:pass%40example.com/%40urlencoded@symbol",
            "user:pass@https://example.com",
            "not a valid URI",
            "982.000.564.11:65666",
            "http://example.com/index[/].html",
            "http://example.com/</a/path>",
        )

        for (i in testUrls.indices) {
            val actual = testee.stripBasicAuthFromUrl(testUrls[i])
            assertEquals(testUrls[i], actual)
        }
    }

    @Test
    fun whenNewTabOpenedAndFavouritesPresentThenSyncTriggered() = runTest {
        val favoriteSite = Favorite(id = UUID.randomUUID().toString(), title = "", url = "www.example.com", position = 0, lastModified = "timestamp")
        favoriteListFlow.send(listOf(favoriteSite))
        loadUrl("www.example.com", isBrowserShowing = true)

        testee.onNewTabFavouritesShown()

        verify(mockSyncEngine).triggerSync(FEATURE_READ)
    }

    @Test
    fun whenOnShowFileChooserWithImageWildcardedTypeThenImageOrCameraChooserCommandSent() {
        val params = buildFileChooserParams(arrayOf("image/*"))
        testee.showFileChooser(mockFileChooserCallback, params)
        assertCommandIssued<Command.ShowExistingImageOrCameraChooser>()
    }

    @Test
    fun whenOnShowFileChooserWithImageWildcardedTypeButCameraHardwareUnavailableThenFileChooserCommandSent() {
        whenever(cameraHardwareChecker.hasCameraHardware()).thenReturn(false)
        val params = buildFileChooserParams(arrayOf("image/*"))
        testee.showFileChooser(mockFileChooserCallback, params)
        assertCommandIssued<Command.ShowFileChooser>()
    }

    @Test
    fun whenOnShowFileChooserContainsImageWildcardedTypeThenImageOrCameraChooserCommandSent() {
        val params = buildFileChooserParams(arrayOf("image/*", "application/pdf"))
        testee.showFileChooser(mockFileChooserCallback, params)
        assertCommandIssued<Command.ShowExistingImageOrCameraChooser>()
    }

    @Test
    fun whenOnShowFileChooserWithImageSpecificTypeThenImageOrCameraChooserCommandSent() {
        val params = buildFileChooserParams(arrayOf("image/png"))
        testee.showFileChooser(mockFileChooserCallback, params)
        assertCommandIssued<Command.ShowExistingImageOrCameraChooser>()
    }

    @Test
    fun whenOnShowFileChooserWithNonImageTypeThenExistingFileChooserCommandSent() {
        val params = buildFileChooserParams(arrayOf("application/pdf"))
        testee.showFileChooser(mockFileChooserCallback, params)
        assertCommandIssued<Command.ShowFileChooser>()
    }

    @Test
    fun whenOnShowFileChooserWithFileExtensionTypesThenFileChooserCommandSentWithUpdatedValues() {
        val fileExtensionTypes = arrayOf(".doc", ".docx", ".pdf")
        val expectedMimeTypes =
            arrayOf("application/msword", "application/vnd.openxmlformats-officedocument.wordprocessingml.document", "application/pdf")
        val params = buildFileChooserParams(fileExtensionTypes)

        testee.showFileChooser(mockFileChooserCallback, params)

        verify(mockCommandObserver, atLeastOnce()).onChanged(commandCaptor.capture())
        val issuedCommand = commandCaptor.allValues.find { it is Command.ShowFileChooser }
        assertNotNull(issuedCommand)
        assertArrayEquals(
            expectedMimeTypes,
            (issuedCommand as Command.ShowFileChooser).fileChooserParams.acceptMimeTypes.toTypedArray(),
        )
    }

    @Test
    fun whenOnShowFileChooserWithFileExtensionTypesAndImageMimeTypeThenImageOrCameraChooserCommandSentWithUpdatedValues() {
        val fileExtensionTypes = arrayOf("image/jpeg", "image/pjpeg", ".jpeg", ".jpg")
        val expectedMimeTypes = arrayOf("image/jpeg", "image/pjpeg")
        val params = buildFileChooserParams(fileExtensionTypes)

        testee.showFileChooser(mockFileChooserCallback, params)

        verify(mockCommandObserver, atLeastOnce()).onChanged(commandCaptor.capture())
        val issuedCommand = commandCaptor.allValues.find { it is Command.ShowExistingImageOrCameraChooser }
        assertNotNull(issuedCommand)
        assertArrayEquals(
            expectedMimeTypes,
            (issuedCommand as Command.ShowExistingImageOrCameraChooser).fileChooserParams.acceptMimeTypes.toTypedArray(),
        )
    }

    @Test
    fun whenWebViewRefreshedThenBrowserErrorStateIsOmitted() {
        testee.onWebViewRefreshed()

        assertEquals(OMITTED, browserViewState().browserError)
    }

    @Test
    fun whenWebViewRefreshedWithErrorThenBrowserErrorStateIsLoading() {
        testee.onReceivedError(BAD_URL, "http://example.com")
        testee.onWebViewRefreshed()

        assertEquals(LOADING, browserViewState().browserError)
    }

    @Test
    fun whenResetBrowserErrorThenBrowserErrorStateIsLoading() {
        testee.onReceivedError(BAD_URL, "http://example.com")
        assertEquals(BAD_URL, browserViewState().browserError)
        testee.resetBrowserError()
        assertEquals(OMITTED, browserViewState().browserError)
    }

    @Test
    fun whenProcessJsCallbackMessageWebShareSendCommand() = runTest {
        val url = "someUrl"
        loadUrl(url)
        testee.processJsCallbackMessage("myFeature", "webShare", "myId", JSONObject("""{ "my":"object"}"""))
        assertCommandIssued<Command.WebShareRequest> {
            assertEquals("object", this.data.params.getString("my"))
            assertEquals("myFeature", this.data.featureName)
            assertEquals("webShare", this.data.method)
            assertEquals("myId", this.data.id)
        }
    }

    @Test
    fun whenProcessJsCallbackMessagePermissionsQuerySendCommand() = runTest {
        val url = "someUrl"
        loadUrl(url)
        whenever(mockSitePermissionsManager.getPermissionsQueryResponse(eq(url), any(), any())).thenReturn(SitePermissionQueryResponse.Granted)
        testee.processJsCallbackMessage("myFeature", "permissionsQuery", "myId", JSONObject("""{ "name":"somePermission"}"""))
        assertCommandIssued<Command.SendResponseToJs> {
            assertEquals("granted", this.data.params.getString("state"))
            assertEquals("myFeature", this.data.featureName)
            assertEquals("permissionsQuery", this.data.method)
            assertEquals("myId", this.data.id)
        }
    }

    @Test
    fun whenProcessJsCallbackMessageScreenLockNotEnabledDoNotSendCommand() = runTest {
        whenever(mockToggle.isEnabled()).thenReturn(false)
        testee.processJsCallbackMessage("myFeature", "screenLock", "myId", JSONObject("""{ "my":"object"}"""))
        assertCommandNotIssued<Command.ScreenLock>()
    }

    @Test
    fun whenProcessJsCallbackMessageScreenLockEnabledSendCommand() = runTest {
        whenever(mockToggle.isEnabled()).thenReturn(true)
        testee.processJsCallbackMessage("myFeature", "screenLock", "myId", JSONObject("""{ "my":"object"}"""))
        assertCommandIssued<Command.ScreenLock> {
            assertEquals("object", this.data.params.getString("my"))
            assertEquals("myFeature", this.data.featureName)
            assertEquals("screenLock", this.data.method)
            assertEquals("myId", this.data.id)
        }
    }

    @Test
    fun whenProcessJsCallbackMessageScreenUnlockNotEnabledDoNotSendCommand() = runTest {
        whenever(mockToggle.isEnabled()).thenReturn(false)
        testee.processJsCallbackMessage("myFeature", "screenUnlock", "myId", JSONObject("""{ "my":"object"}"""))
        assertCommandNotIssued<Command.ScreenUnlock>()
    }

    @Test
    fun whenProcessJsCallbackMessageScreenUnlockEnabledSendCommand() = runTest {
        whenever(mockToggle.isEnabled()).thenReturn(true)
        testee.processJsCallbackMessage("myFeature", "screenUnlock", "myId", JSONObject("""{ "my":"object"}"""))
        assertCommandIssued<Command.ScreenUnlock>()
    }

    private fun aCredential(): LoginCredentials {
        return LoginCredentials(domain = null, username = null, password = null)
    }

    private fun assertShowHistoryCommandSent(expectedStackSize: Int) {
        assertCommandIssued<ShowBackNavigationHistory> {
            assertEquals(expectedStackSize, history.size)
        }
    }

    private fun configureOmnibarEditing() {
        testee.onOmnibarInputStateChanged(hasFocus = true, query = "", hasQueryChanged = false)
    }

    private fun configureOmnibarNotEditing() {
        testee.onOmnibarInputStateChanged(hasFocus = false, query = "", hasQueryChanged = false)
    }

    private fun buildNavigationHistoryStack(stackSize: Int) {
        val history = mutableListOf<NavigationHistoryEntry>()
        for (i in 0 until stackSize) {
            history.add(NavigationHistoryEntry(url = "$i.example.com"))
        }

        testee.navigationStateChanged(buildWebNavigation(navigationHistory = history))
    }

    private fun givenUrlCanUseGpc() {
        whenever(mockFeatureToggle.isFeatureEnabled(any(), any())).thenReturn(true)
        whenever(mockGpcRepository.isGpcEnabled()).thenReturn(true)
        whenever(mockGpcRepository.exceptions).thenReturn(CopyOnWriteArrayList())
    }

    private fun givenUrlCannotUseGpc(url: String) {
        val exceptions = CopyOnWriteArrayList<GpcException>().apply { add(GpcException(url)) }
        whenever(mockFeatureToggle.isFeatureEnabled(eq(PrivacyFeatureName.GpcFeatureName.value), any())).thenReturn(true)
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
        permission: LocationPermissionType,
    ) {
        locationPermissionsDao.insert(LocationPermissionEntity(domain, permission))
    }

    class StubPermissionCallback : GeolocationPermissions.Callback {
        override fun invoke(
            p0: String?,
            p1: Boolean,
            p2: Boolean,
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
        bookmarkCapable: Boolean,
    ) = mapOf(
        Pixel.PixelParameter.SHOWED_BOOKMARKS to showedBookmarks.toString(),
        Pixel.PixelParameter.BOOKMARK_CAPABLE to bookmarkCapable.toString(),
    )

    private fun givenExpectedCtaAddWidgetInstructions() {
        setBrowserShowing(false)
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

    private fun givenCurrentSite(domain: String): Site {
        val site: Site = mock()
        whenever(site.url).thenReturn(domain)
        whenever(site.uri).thenReturn(Uri.parse(domain))
        val siteLiveData = MutableLiveData<Site>()
        siteLiveData.value = site
        whenever(mockTabRepository.retrieveSiteData("TAB_ID")).thenReturn(siteLiveData)
        testee.loadData("TAB_ID", domain, false, false)

        return site
    }

    private fun givenRemoteMessagingModel(
        remoteMessagingRepository: RemoteMessagingRepository,
        pixel: Pixel,
        dispatchers: DispatcherProvider,
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

    private fun aTabEntity(id: String): TabEntity {
        return TabEntity(tabId = id, position = 0)
    }

    private fun loadTabWithId(tabId: String) {
        testee.loadData(tabId, initialUrl = null, skipHome = false, favoritesOnboarding = false)
    }

    private fun loadUrl(
        url: String?,
        title: String? = null,
        isBrowserShowing: Boolean = true,
    ) {
        setBrowserShowing(isBrowserShowing)
        testee.navigationStateChanged(buildWebNavigation(originalUrl = url, currentUrl = url, title = title))
    }

    @Suppress("SameParameterValue")
    private fun updateUrl(
        originalUrl: String?,
        currentUrl: String?,
        isBrowserShowing: Boolean,
    ) {
        setBrowserShowing(isBrowserShowing)
        testee.navigationStateChanged(buildWebNavigation(originalUrl = originalUrl, currentUrl = currentUrl))
    }

    @Suppress("SameParameterValue")
    private fun onProgressChanged(
        url: String?,
        newProgress: Int,
    ) {
        testee.navigationStateChanged(buildWebNavigation(originalUrl = url, currentUrl = url, progress = newProgress))
    }

    private fun overrideUrl(
        url: String,
        isBrowserShowing: Boolean = true,
    ) {
        setBrowserShowing(isBrowserShowing)
        testee.willOverrideUrl(newUrl = url)
    }

    private fun setupNavigation(
        skipHome: Boolean = false,
        isBrowsing: Boolean,
        canGoForward: Boolean = false,
        canGoBack: Boolean = false,
        stepsToPreviousPage: Int = 0,
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
        progress: Int? = null,
        navigationHistory: List<NavigationHistoryEntry> = emptyList(),
    ): WebNavigationState {
        val nav: WebNavigationState = mock()
        whenever(nav.originalUrl).thenReturn(originalUrl)
        whenever(nav.currentUrl).thenReturn(currentUrl)
        whenever(nav.title).thenReturn(title)
        whenever(nav.canGoForward).thenReturn(canGoForward)
        whenever(nav.canGoBack).thenReturn(canGoBack)
        whenever(nav.stepsToPreviousPage).thenReturn(stepsToPreviousPage)
        whenever(nav.progress).thenReturn(progress)
        whenever(nav.navigationHistory).thenReturn(navigationHistory)
        return nav
    }

    private fun buildFileChooserParams(acceptTypes: Array<String>): FileChooserParams {
        return object : FileChooserParams() {
            override fun getAcceptTypes(): Array<String> = acceptTypes
            override fun getMode(): Int = 0
            override fun isCaptureEnabled(): Boolean = false
            override fun getTitle(): CharSequence? = null
            override fun getFilenameHint(): String? = null
            override fun createIntent(): Intent = Intent()
        }
    }

    private fun privacyShieldState() = testee.privacyShieldViewState.value!!
    private fun ctaViewState() = testee.ctaViewState.value!!
    private fun browserViewState() = testee.browserViewState.value!!
    private fun omnibarViewState() = testee.omnibarViewState.value!!
    private fun loadingViewState() = testee.loadingViewState.value!!
    private fun autoCompleteViewState() = testee.autoCompleteViewState.value!!
    private fun findInPageViewState() = testee.findInPageViewState.value!!
    private fun globalLayoutViewState() = testee.globalLayoutState.value!!
    private fun browserGlobalLayoutViewState() = testee.globalLayoutState.value!! as BrowserTabViewModel.GlobalLayoutViewState.Browser
    private fun accessibilityViewState() = testee.accessibilityViewState.value!!

    class FakeCapabilityChecker(var enabled: Boolean) : AutofillCapabilityChecker {
        override suspend fun isAutofillEnabledByConfiguration(url: String) = enabled
        override suspend fun canInjectCredentialsToWebView(url: String) = enabled
        override suspend fun canSaveCredentialsFromWebView(url: String) = enabled
        override suspend fun canGeneratePasswordFromWebView(url: String) = enabled
        override suspend fun canAccessCredentialManagementScreen() = enabled
    }
}
