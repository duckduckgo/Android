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

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.net.http.SslCertificate
import android.net.http.SslError
import android.os.Build
import android.print.PrintAttributes
import android.view.MenuItem
import android.view.View
import android.webkit.HttpAuthHandler
import android.webkit.PermissionRequest
import android.webkit.SslErrorHandler
import android.webkit.ValueCallback
import android.webkit.WebBackForwardList
import android.webkit.WebChromeClient.FileChooserParams
import android.webkit.WebHistoryItem
import android.webkit.WebView
import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.core.net.toUri
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.Observer
import androidx.room.Room
import androidx.test.annotation.UiThreadTest
import androidx.test.filters.SdkSuppress
import androidx.test.platform.app.InstrumentationRegistry.getInstrumentation
import com.duckduckgo.adclick.api.AdClickManager
import com.duckduckgo.app.ValueCaptorObserver
import com.duckduckgo.app.accessibility.data.AccessibilitySettingsDataStore
import com.duckduckgo.app.accessibility.data.AccessibilitySettingsSharedPreferences
import com.duckduckgo.app.autocomplete.AutocompleteTabsFeature
import com.duckduckgo.app.autocomplete.api.AutoCompleteApi
import com.duckduckgo.app.autocomplete.api.AutoCompleteScorer
import com.duckduckgo.app.autocomplete.api.AutoCompleteService
import com.duckduckgo.app.autocomplete.impl.AutoCompleteRepository
import com.duckduckgo.app.browser.LongPressHandler.RequiredAction
import com.duckduckgo.app.browser.LongPressHandler.RequiredAction.DownloadFile
import com.duckduckgo.app.browser.LongPressHandler.RequiredAction.OpenInNewTab
import com.duckduckgo.app.browser.SSLErrorType.EXPIRED
import com.duckduckgo.app.browser.SSLErrorType.GENERIC
import com.duckduckgo.app.browser.SSLErrorType.NONE
import com.duckduckgo.app.browser.SSLErrorType.UNTRUSTED_HOST
import com.duckduckgo.app.browser.SSLErrorType.WRONG_HOST
import com.duckduckgo.app.browser.WebViewErrorResponse.BAD_URL
import com.duckduckgo.app.browser.WebViewErrorResponse.LOADING
import com.duckduckgo.app.browser.WebViewErrorResponse.OMITTED
import com.duckduckgo.app.browser.addtohome.AddToHomeCapabilityDetector
import com.duckduckgo.app.browser.animations.AddressBarTrackersAnimationFeatureToggle
import com.duckduckgo.app.browser.applinks.AppLinksHandler
import com.duckduckgo.app.browser.camera.CameraHardwareChecker
import com.duckduckgo.app.browser.certificates.BypassedSSLCertificatesRepository
import com.duckduckgo.app.browser.certificates.remoteconfig.SSLCertificatesFeature
import com.duckduckgo.app.browser.commands.Command
import com.duckduckgo.app.browser.commands.Command.CloseCustomTab
import com.duckduckgo.app.browser.commands.Command.EnqueueCookiesAnimation
import com.duckduckgo.app.browser.commands.Command.EscapeMaliciousSite
import com.duckduckgo.app.browser.commands.Command.HideBrokenSitePromptCta
import com.duckduckgo.app.browser.commands.Command.HideOnboardingDaxBubbleCta
import com.duckduckgo.app.browser.commands.Command.HideOnboardingDaxDialog
import com.duckduckgo.app.browser.commands.Command.HideWarningMaliciousSite
import com.duckduckgo.app.browser.commands.Command.LaunchNewTab
import com.duckduckgo.app.browser.commands.Command.LaunchPrivacyPro
import com.duckduckgo.app.browser.commands.Command.LoadExtractedUrl
import com.duckduckgo.app.browser.commands.Command.OpenBrokenSiteLearnMore
import com.duckduckgo.app.browser.commands.Command.RefreshAndShowPrivacyProtectionDisabledConfirmation
import com.duckduckgo.app.browser.commands.Command.RefreshAndShowPrivacyProtectionEnabledConfirmation
import com.duckduckgo.app.browser.commands.Command.ReportBrokenSiteError
import com.duckduckgo.app.browser.commands.Command.ShareLink
import com.duckduckgo.app.browser.commands.Command.ShowAutoconsentAnimation
import com.duckduckgo.app.browser.commands.Command.ShowBackNavigationHistory
import com.duckduckgo.app.browser.commands.Command.ShowKeyboard
import com.duckduckgo.app.browser.commands.NavigationCommand
import com.duckduckgo.app.browser.commands.NavigationCommand.Navigate
import com.duckduckgo.app.browser.customtabs.CustomTabPixelNames
import com.duckduckgo.app.browser.defaultbrowsing.prompts.AdditionalDefaultBrowserPrompts
import com.duckduckgo.app.browser.duckplayer.DUCK_PLAYER_FEATURE_NAME
import com.duckduckgo.app.browser.duckplayer.DUCK_PLAYER_PAGE_FEATURE_NAME
import com.duckduckgo.app.browser.duckplayer.DuckPlayerJSHelper
import com.duckduckgo.app.browser.favicon.FaviconManager
import com.duckduckgo.app.browser.favicon.FaviconSource
import com.duckduckgo.app.browser.history.NavigationHistoryEntry
import com.duckduckgo.app.browser.httperrors.HttpCodeSiteErrorHandler
import com.duckduckgo.app.browser.httperrors.HttpErrorPixelName
import com.duckduckgo.app.browser.httperrors.HttpErrorPixels
import com.duckduckgo.app.browser.httperrors.SiteErrorHandlerKillSwitch
import com.duckduckgo.app.browser.httperrors.StringSiteErrorHandler
import com.duckduckgo.app.browser.logindetection.FireproofDialogsEventHandler
import com.duckduckgo.app.browser.logindetection.LoginDetected
import com.duckduckgo.app.browser.logindetection.NavigationAwareLoginDetector
import com.duckduckgo.app.browser.logindetection.NavigationEvent
import com.duckduckgo.app.browser.logindetection.NavigationEvent.LoginAttempt
import com.duckduckgo.app.browser.menu.VpnMenuStateProvider
import com.duckduckgo.app.browser.model.BasicAuthenticationCredentials
import com.duckduckgo.app.browser.model.BasicAuthenticationRequest
import com.duckduckgo.app.browser.model.LongPressTarget
import com.duckduckgo.app.browser.newtab.FavoritesQuickAccessAdapter.QuickAccessFavorite
import com.duckduckgo.app.browser.omnibar.OmnibarEntryConverter
import com.duckduckgo.app.browser.omnibar.QueryOrigin.*
import com.duckduckgo.app.browser.refreshpixels.RefreshPixelSender
import com.duckduckgo.app.browser.remotemessage.RemoteMessagingModel
import com.duckduckgo.app.browser.santize.NonHttpAppLinkChecker
import com.duckduckgo.app.browser.session.WebViewSessionStorage
import com.duckduckgo.app.browser.tabs.TabManager
import com.duckduckgo.app.browser.trafficquality.AndroidFeaturesHeaderPlugin.Companion.X_DUCKDUCKGO_ANDROID_HEADER
import com.duckduckgo.app.browser.ui.dialogs.widgetprompt.OnboardingHomeScreenWidgetToggles
import com.duckduckgo.app.browser.viewstate.BrowserViewState
import com.duckduckgo.app.browser.viewstate.CtaViewState
import com.duckduckgo.app.browser.viewstate.FindInPageViewState
import com.duckduckgo.app.browser.viewstate.GlobalLayoutViewState
import com.duckduckgo.app.browser.viewstate.HighlightableButton
import com.duckduckgo.app.browser.viewstate.LoadingViewState
import com.duckduckgo.app.browser.viewstate.VpnMenuState
import com.duckduckgo.app.browser.webview.MaliciousSiteBlockedWarningLayout.Action.LearnMore
import com.duckduckgo.app.browser.webview.MaliciousSiteBlockedWarningLayout.Action.LeaveSite
import com.duckduckgo.app.browser.webview.MaliciousSiteBlockedWarningLayout.Action.ReportError
import com.duckduckgo.app.browser.webview.MaliciousSiteBlockedWarningLayout.Action.VisitSite
import com.duckduckgo.app.browser.webview.SslWarningLayout.Action
import com.duckduckgo.app.cta.db.DismissedCtaDao
import com.duckduckgo.app.cta.model.CtaId
import com.duckduckgo.app.cta.model.CtaId.DAX_DIALOG_NETWORK
import com.duckduckgo.app.cta.model.CtaId.DAX_DIALOG_TRACKERS_FOUND
import com.duckduckgo.app.cta.model.CtaId.DAX_END
import com.duckduckgo.app.cta.model.DismissedCta
import com.duckduckgo.app.cta.ui.BrokenSitePromptDialogCta
import com.duckduckgo.app.cta.ui.Cta
import com.duckduckgo.app.cta.ui.CtaViewModel
import com.duckduckgo.app.cta.ui.DaxBubbleCta
import com.duckduckgo.app.cta.ui.DaxBubbleCta.DaxIntroSearchOptionsCta
import com.duckduckgo.app.cta.ui.HomePanelCta
import com.duckduckgo.app.cta.ui.OnboardingDaxDialogCta
import com.duckduckgo.app.cta.ui.OnboardingDaxDialogCta.DaxMainNetworkCta
import com.duckduckgo.app.cta.ui.OnboardingDaxDialogCta.DaxSerpCta
import com.duckduckgo.app.cta.ui.OnboardingDaxDialogCta.DaxTrackersBlockedCta
import com.duckduckgo.app.dispatchers.ExternalIntentProcessingState
import com.duckduckgo.app.fire.fireproofwebsite.data.FireproofWebsiteDao
import com.duckduckgo.app.fire.fireproofwebsite.data.FireproofWebsiteEntity
import com.duckduckgo.app.fire.fireproofwebsite.data.FireproofWebsiteRepositoryImpl
import com.duckduckgo.app.fire.fireproofwebsite.ui.AutomaticFireproofSetting
import com.duckduckgo.app.generalsettings.showonapplaunch.ShowOnAppLaunchOptionHandler
import com.duckduckgo.app.global.db.AppDatabase
import com.duckduckgo.app.global.events.db.UserEventsStore
import com.duckduckgo.app.global.install.AppInstallStore
import com.duckduckgo.app.global.model.MaliciousSiteStatus.PHISHING
import com.duckduckgo.app.global.model.PrivacyShield.PROTECTED
import com.duckduckgo.app.global.model.Site
import com.duckduckgo.app.global.model.SiteFactoryImpl
import com.duckduckgo.app.location.data.LocationPermissionsDao
import com.duckduckgo.app.onboarding.store.AppStage
import com.duckduckgo.app.onboarding.store.AppStage.ESTABLISHED
import com.duckduckgo.app.onboarding.store.OnboardingStore
import com.duckduckgo.app.onboarding.store.UserStageStore
import com.duckduckgo.app.onboarding.ui.page.extendedonboarding.ExtendedOnboardingFeatureToggles
import com.duckduckgo.app.onboardingdesignexperiment.OnboardingDesignExperimentManager
import com.duckduckgo.app.pixels.AppPixelName
import com.duckduckgo.app.pixels.AppPixelName.AUTOCOMPLETE_BANNER_SHOWN
import com.duckduckgo.app.pixels.AppPixelName.DUCK_PLAYER_SETTING_ALWAYS_DUCK_PLAYER
import com.duckduckgo.app.pixels.AppPixelName.DUCK_PLAYER_SETTING_ALWAYS_OVERLAY_YOUTUBE
import com.duckduckgo.app.pixels.AppPixelName.DUCK_PLAYER_SETTING_NEVER_OVERLAY_YOUTUBE
import com.duckduckgo.app.pixels.AppPixelName.ONBOARDING_DAX_CTA_DISMISS_BUTTON
import com.duckduckgo.app.pixels.AppPixelName.ONBOARDING_SEARCH_CUSTOM
import com.duckduckgo.app.pixels.AppPixelName.ONBOARDING_VISIT_SITE_CUSTOM
import com.duckduckgo.app.pixels.remoteconfig.AndroidBrowserConfigFeature
import com.duckduckgo.app.privacy.db.NetworkLeaderboardDao
import com.duckduckgo.app.privacy.db.UserAllowListRepository
import com.duckduckgo.app.privacy.model.TestEntity
import com.duckduckgo.app.settings.db.SettingsDataStore
import com.duckduckgo.app.statistics.api.StatisticsUpdater
import com.duckduckgo.app.statistics.pixels.Pixel
import com.duckduckgo.app.statistics.pixels.Pixel.PixelParameter
import com.duckduckgo.app.statistics.pixels.Pixel.PixelType.Count
import com.duckduckgo.app.statistics.pixels.Pixel.PixelType.Daily
import com.duckduckgo.app.statistics.pixels.Pixel.PixelType.Unique
import com.duckduckgo.app.statistics.pixels.Pixel.PixelValues.DAX_INITIAL_CTA
import com.duckduckgo.app.statistics.pixels.Pixel.PixelValues.DAX_SERP_CTA
import com.duckduckgo.app.surrogates.SurrogateResponse
import com.duckduckgo.app.tabs.model.TabEntity
import com.duckduckgo.app.tabs.model.TabRepository
import com.duckduckgo.app.tabs.store.TabStatsBucketing
import com.duckduckgo.app.trackerdetection.EntityLookup
import com.duckduckgo.app.trackerdetection.model.TrackerStatus
import com.duckduckgo.app.trackerdetection.model.TrackerType
import com.duckduckgo.app.trackerdetection.model.TrackingEvent
import com.duckduckgo.app.usage.search.SearchCountDao
import com.duckduckgo.app.widget.ui.WidgetCapabilities
import com.duckduckgo.appbuildconfig.api.AppBuildConfig
import com.duckduckgo.autofill.api.AutofillCapabilityChecker
import com.duckduckgo.autofill.api.domain.app.LoginCredentials
import com.duckduckgo.autofill.api.email.EmailManager
import com.duckduckgo.autofill.api.passwordgeneration.AutomaticSavedLoginsMonitor
import com.duckduckgo.autofill.impl.AutofillFireproofDialogSuppressor
import com.duckduckgo.brokensite.api.BrokenSitePrompt
import com.duckduckgo.brokensite.api.RefreshPattern
import com.duckduckgo.browser.api.UserBrowserProperties
import com.duckduckgo.browser.api.autocomplete.AutoComplete
import com.duckduckgo.browser.api.autocomplete.AutoComplete.AutoCompleteSuggestion.AutoCompleteDefaultSuggestion
import com.duckduckgo.browser.api.autocomplete.AutoComplete.AutoCompleteSuggestion.AutoCompleteHistoryRelatedSuggestion.AutoCompleteHistorySearchSuggestion
import com.duckduckgo.browser.api.autocomplete.AutoComplete.AutoCompleteSuggestion.AutoCompleteHistoryRelatedSuggestion.AutoCompleteHistorySuggestion
import com.duckduckgo.browser.api.autocomplete.AutoComplete.AutoCompleteSuggestion.AutoCompleteUrlSuggestion.AutoCompleteSwitchToTabSuggestion
import com.duckduckgo.browser.api.autocomplete.AutoCompleteSettings
import com.duckduckgo.browser.api.brokensite.BrokenSiteContext
import com.duckduckgo.browser.api.webviewcompat.WebViewCompatWrapper
import com.duckduckgo.browser.ui.omnibar.OmnibarPosition.BOTTOM
import com.duckduckgo.browser.ui.omnibar.OmnibarPosition.TOP
import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.common.test.InstantSchedulersRule
import com.duckduckgo.common.ui.tabs.SwipingTabsFeature
import com.duckduckgo.common.ui.tabs.SwipingTabsFeatureProvider
import com.duckduckgo.common.utils.DefaultDispatcherProvider
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.common.utils.baseHost
import com.duckduckgo.common.utils.device.DeviceInfo
import com.duckduckgo.common.utils.plugins.PluginPoint
import com.duckduckgo.common.utils.plugins.headers.CustomHeadersProvider
import com.duckduckgo.downloads.api.DownloadStateListener
import com.duckduckgo.downloads.api.FileDownloader
import com.duckduckgo.downloads.api.FileDownloader.PendingFileDownload
import com.duckduckgo.duckchat.api.DuckAiFeatureState
import com.duckduckgo.duckchat.api.DuckChat
import com.duckduckgo.duckchat.impl.helper.DuckChatJSHelper
import com.duckduckgo.duckchat.impl.helper.RealDuckChatJSHelper.Companion.DUCK_CHAT_FEATURE_NAME
import com.duckduckgo.duckchat.impl.pixel.DuckChatPixelName
import com.duckduckgo.duckplayer.api.DuckPlayer
import com.duckduckgo.duckplayer.api.DuckPlayer.DuckPlayerOrigin.AUTO
import com.duckduckgo.duckplayer.api.DuckPlayer.DuckPlayerOrigin.OVERLAY
import com.duckduckgo.duckplayer.api.DuckPlayer.DuckPlayerState.DISABLED
import com.duckduckgo.duckplayer.api.DuckPlayer.DuckPlayerState.ENABLED
import com.duckduckgo.duckplayer.api.DuckPlayer.OpenDuckPlayerInNewTab.Off
import com.duckduckgo.duckplayer.api.DuckPlayer.OpenDuckPlayerInNewTab.On
import com.duckduckgo.duckplayer.api.DuckPlayer.OpenDuckPlayerInNewTab.Unavailable
import com.duckduckgo.duckplayer.api.DuckPlayer.UserPreferences
import com.duckduckgo.duckplayer.api.DuckPlayerPageSettingsPlugin
import com.duckduckgo.duckplayer.api.PrivatePlayerMode.AlwaysAsk
import com.duckduckgo.duckplayer.api.PrivatePlayerMode.Disabled
import com.duckduckgo.duckplayer.api.PrivatePlayerMode.Enabled
import com.duckduckgo.feature.toggles.api.FakeFeatureToggleFactory
import com.duckduckgo.feature.toggles.api.Toggle
import com.duckduckgo.feature.toggles.api.Toggle.State
import com.duckduckgo.history.api.HistoryEntry.VisitedPage
import com.duckduckgo.history.api.NavigationHistory
import com.duckduckgo.js.messaging.api.AddDocumentStartJavaScriptPlugin
import com.duckduckgo.js.messaging.api.JsCallbackData
import com.duckduckgo.js.messaging.api.PostMessageWrapperPlugin
import com.duckduckgo.js.messaging.api.SubscriptionEventData
import com.duckduckgo.js.messaging.api.WebMessagingPlugin
import com.duckduckgo.js.messaging.api.WebViewCompatMessageCallback
import com.duckduckgo.malicioussiteprotection.api.MaliciousSiteProtection.Feed
import com.duckduckgo.malicioussiteprotection.api.MaliciousSiteProtection.Feed.MALWARE
import com.duckduckgo.newtabpage.impl.pixels.NewTabPixels
import com.duckduckgo.privacy.config.api.AmpLinkInfo
import com.duckduckgo.privacy.config.api.AmpLinks
import com.duckduckgo.privacy.config.api.ContentBlocking
import com.duckduckgo.privacy.config.api.TrackingParameters
import com.duckduckgo.privacy.config.impl.features.gpc.RealGpc.Companion.GPC_HEADER
import com.duckduckgo.privacy.config.impl.features.gpc.RealGpc.Companion.GPC_HEADER_VALUE
import com.duckduckgo.privacy.dashboard.api.PrivacyProtectionTogglePlugin
import com.duckduckgo.privacy.dashboard.api.PrivacyToggleOrigin
import com.duckduckgo.privacy.dashboard.api.ui.ToggleReports
import com.duckduckgo.privacyprotectionspopup.api.PrivacyProtectionsPopupExperimentExternalPixels
import com.duckduckgo.privacyprotectionspopup.api.PrivacyProtectionsPopupManager
import com.duckduckgo.privacyprotectionspopup.api.PrivacyProtectionsPopupUiEvent
import com.duckduckgo.privacyprotectionspopup.api.PrivacyProtectionsPopupViewState
import com.duckduckgo.privacyprotectionspopup.api.PrivacyProtectionsToggleUsageListener
import com.duckduckgo.remote.messaging.api.RemoteMessage
import com.duckduckgo.remote.messaging.api.RemoteMessagingRepository
import com.duckduckgo.savedsites.api.SavedSitesRepository
import com.duckduckgo.savedsites.api.models.SavedSite.Bookmark
import com.duckduckgo.savedsites.api.models.SavedSite.Favorite
import com.duckduckgo.savedsites.impl.SavedSitesPixelName
import com.duckduckgo.serp.logos.api.SerpEasterEggLogosToggles
import com.duckduckgo.serp.logos.api.SerpLogo
import com.duckduckgo.site.permissions.api.SitePermissionsManager
import com.duckduckgo.site.permissions.api.SitePermissionsManager.LocationPermissionRequest
import com.duckduckgo.site.permissions.api.SitePermissionsManager.SitePermissionQueryResponse
import com.duckduckgo.site.permissions.api.SitePermissionsManager.SitePermissions
import com.duckduckgo.subscriptions.api.SUBSCRIPTIONS_FEATURE_NAME
import com.duckduckgo.subscriptions.api.SubscriptionRebrandingFeatureToggle
import com.duckduckgo.subscriptions.api.Subscriptions
import com.duckduckgo.subscriptions.api.SubscriptionsJSHelper
import com.duckduckgo.sync.api.favicons.FaviconsFetchingPrompt
import com.duckduckgo.voice.api.VoiceSearchAvailabilityPixelLogger
import dagger.Lazy
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.consumeAsFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.ArgumentMatchers.anyString
import org.mockito.Mockito.never
import org.mockito.Mockito.times
import org.mockito.Mockito.verify
import org.mockito.MockitoAnnotations
import org.mockito.internal.util.DefaultMockingDetails
import org.mockito.kotlin.KArgumentCaptor
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.atLeastOnce
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.io.File
import java.math.BigInteger
import java.security.cert.X509Certificate
import java.security.interfaces.RSAPublicKey
import java.time.LocalDateTime
import java.util.UUID
import java.util.concurrent.TimeUnit
import com.duckduckgo.mobile.android.R as CommonR

@SuppressLint("DenyListedApi")
@FlowPreview
class BrowserTabViewModelTest {
    @get:Rule
    var instantTaskExecutorRule = InstantTaskExecutorRule()

    @get:Rule
    val schedulers = InstantSchedulersRule()

    @get:Rule
    var coroutineRule = CoroutineTestRule()

    private val mockEntityLookup: EntityLookup = mock()

    private val mockNetworkLeaderboardDao: NetworkLeaderboardDao = mock()

    private val mockStatisticsUpdater: StatisticsUpdater = mock()

    private val mockCommandObserver: Observer<Command> = mock()

    private val ctaViewModelMockSettingsStore: SettingsDataStore = mock()

    private val mockSavedSitesRepository: SavedSitesRepository = mock()

    private val mockNavigationHistory: NavigationHistory = mock()

    private val mockLongPressHandler: LongPressHandler = mock()

    private val mockOmnibarConverter: OmnibarEntryConverter = mock()

    private val mockTabRepository: TabRepository = mock()

    private val webViewSessionStorage: WebViewSessionStorage = mock()

    private val mockFaviconManager: FaviconManager = mock()

    private val mockAddToHomeCapabilityDetector: AddToHomeCapabilityDetector = mock()

    private val mockDismissedCtaDao: DismissedCtaDao = mock()

    private val mockSearchCountDao: SearchCountDao = mock()

    private val mockAppInstallStore: AppInstallStore = mock()

    private val mockPixel: Pixel = mock()

    private val mockNewTabPixels: NewTabPixels = mock()

    private val mockHttpErrorPixels: HttpErrorPixels = mock()

    private val mockOnboardingStore: OnboardingStore = mock()

    private val mockAutoCompleteService: AutoCompleteService = mock()

    private val mockAutoCompleteScorer: AutoCompleteScorer = mock()

    private val mockWidgetCapabilities: WidgetCapabilities = mock()

    private val mockUserStageStore: UserStageStore = mock()

    private val mockContentBlocking: ContentBlocking = mock()

    private val mockNavigationAwareLoginDetector: NavigationAwareLoginDetector = mock()

    private val mockUserEventsStore: UserEventsStore = mock()

    private val mockFileDownloader: FileDownloader = mock()

    private val fireproofDialogsEventHandler: FireproofDialogsEventHandler = mock()

    private val mockEmailManager: EmailManager = mock()

    private val mockSpecialUrlDetector: SpecialUrlDetector = mock()

    private val mockAppLinksHandler: AppLinksHandler = mock()

    private val mockAmpLinks: AmpLinks = mock()

    private val mockTrackingParameters: TrackingParameters = mock()

    private val mockDownloadCallback: DownloadStateListener = mock()

    private val mockRemoteMessagingRepository: RemoteMessagingRepository = mock()

    private val voiceSearchPixelLogger: VoiceSearchAvailabilityPixelLogger = mock()

    private val mockSettingsDataStore: SettingsDataStore = mock()

    private val mockAutoCompleteSettings: AutoCompleteSettings = mock()

    private val mockAdClickManager: AdClickManager = mock()

    private val mockUserAllowListRepository: UserAllowListRepository = mock()

    private val mockBrokenSiteContext: BrokenSiteContext = mock()

    private val mockFileChooserCallback: ValueCallback<Array<Uri>> = mock()

    private val mockDuckPlayer: DuckPlayer = mock()

    private val mockDuckAiFeatureState: DuckAiFeatureState = mock()

    private val mockDuckAiFeatureStateInputScreenFlow = MutableStateFlow(false)

    private val mockDuckAiFeatureStateInputScreenOpenAutomaticallyFlow = MutableStateFlow(false)

    private val mockExternalIntentProcessingState: ExternalIntentProcessingState = mock()

    private val mockVpnMenuStateProvider: VpnMenuStateProvider = mock()

    private val mockHasPendingTabLaunchFlow = MutableStateFlow(false)

    private val mockHasPendingDuckAiOpenFlow = MutableStateFlow(false)

    private val mockAppBuildConfig: AppBuildConfig = mock()

    private val mockDuckDuckGoUrlDetector: DuckDuckGoUrlDetector = mock()

    private val mockPagesSettingPlugin: PluginPoint<DuckPlayerPageSettingsPlugin> = mock()

    private val mockShowOnAppLaunchHandler: ShowOnAppLaunchOptionHandler = mock()

    private lateinit var remoteMessagingModel: RemoteMessagingModel

    private val lazyFaviconManager = Lazy { mockFaviconManager }

    private lateinit var mockAutoCompleteApi: AutoComplete

    private lateinit var ctaViewModel: CtaViewModel

    private val commandCaptor = argumentCaptor<Command>()

    private val appLinkCaptor = argumentCaptor<() -> Unit>()

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

    private val autofillCapabilityChecker: FakeCapabilityChecker = FakeCapabilityChecker(enabled = false)

    private val autofillFireproofDialogSuppressor: AutofillFireproofDialogSuppressor = mock()

    private val automaticSavedLoginsMonitor: AutomaticSavedLoginsMonitor = mock()

    private val mockDeviceInfo: DeviceInfo = mock()

    private val mockSitePermissionsManager: SitePermissionsManager = mock()

    private val cameraHardwareChecker: CameraHardwareChecker = mock()

    private val mockEnabledToggle: Toggle = mock { on { it.isEnabled() } doReturn true }

    private val mockDisabledToggle: Toggle =
        mock {
            on { it.isEnabled() } doReturn false
        }

    private val mockPrivacyProtectionsPopupManager: PrivacyProtectionsPopupManager = mock()

    private val mockPrivacyProtectionsToggleUsageListener: PrivacyProtectionsToggleUsageListener = mock()

    private val subscriptions: Subscriptions = mock()

    private val refreshPixelSender: RefreshPixelSender = mock()

    private val privacyProtectionsPopupExperimentExternalPixels: PrivacyProtectionsPopupExperimentExternalPixels =
        mock {
            runBlocking { whenever(mock.getPixelParams()).thenReturn(emptyMap()) }
        }

    private val mockFaviconFetchingPrompt: FaviconsFetchingPrompt = mock()
    private val mockSSLCertificatesFeature: SSLCertificatesFeature = mock()
    private val mockBypassedSSLCertificatesRepository: BypassedSSLCertificatesRepository = mock()
    private val mockExtendedOnboardingFeatureToggles: ExtendedOnboardingFeatureToggles = mock()
    private val mockUserBrowserProperties: UserBrowserProperties = mock()
    private val mockAutoCompleteRepository: AutoCompleteRepository = mock()
    private val protectionTogglePlugin = FakePrivacyProtectionTogglePlugin()
    private val protectionTogglePluginPoint = FakePluginPoint(protectionTogglePlugin)
    private var fakeAndroidConfigBrowserFeature = FakeFeatureToggleFactory.create(AndroidBrowserConfigFeature::class.java)
    private val mockAutocompleteTabsFeature: AutocompleteTabsFeature = mock()
    private val fakeCustomHeadersPlugin = FakeCustomHeadersProvider(emptyMap())
    private val mockToggleReports: ToggleReports = mock()
    private val mockBrokenSitePrompt: BrokenSitePrompt = mock()
    private val mockTabStatsBucketing: TabStatsBucketing = mock()
    private val mockDuckChatJSHelper: DuckChatJSHelper = mock()
    private val swipingTabsFeature = FakeFeatureToggleFactory.create(SwipingTabsFeature::class.java)
    private val swipingTabsFeatureProvider = SwipingTabsFeatureProvider(swipingTabsFeature)
    private val mockDuckChat: DuckChat = mock()
    private val mockHistory: NavigationHistory = mock()

    private val defaultBrowserPromptsExperimentShowPopupMenuItemFlow = MutableStateFlow(false)
    private val mockAdditionalDefaultBrowserPrompts: AdditionalDefaultBrowserPrompts = mock()
    val mockStack: WebBackForwardList = mock()

    private val mockSiteErrorHandlerKillSwitch: SiteErrorHandlerKillSwitch = mock()
    private val mockSiteErrorHandlerKillSwitchToggle: Toggle = mock { on { it.isEnabled() } doReturn true }
    private val mockSiteErrorHandler: StringSiteErrorHandler = mock()
    private val mockSiteHttpErrorHandler: HttpCodeSiteErrorHandler = mock()
    private val mockSubscriptionsJSHelper: SubscriptionsJSHelper = mock()
    private val mockOnboardingHomeScreenWidgetToggles: OnboardingHomeScreenWidgetToggles = mock()
    private val mockRebrandingFeatureToggle: SubscriptionRebrandingFeatureToggle = mock()
    private val tabManager: TabManager = mock()

    private val mockAddressDisplayFormatter: AddressDisplayFormatter by lazy {
        mock {
            on { getShortUrl(any()) } doAnswer { invocation ->
                val url = invocation.getArgument<String>(0)
                if (url.startsWith("duck://player")) {
                    "Duck Player"
                } else {
                    url.toUri().baseHost ?: url
                }
            }
        }
    }

    private val mockOnboardingDesignExperimentManager: OnboardingDesignExperimentManager = mock()
    private val mockSerpEasterEggLogoToggles: SerpEasterEggLogosToggles = mock()
    private val mockAddressBarTrackersAnimationFeatureToggle: AddressBarTrackersAnimationFeatureToggle = mock()

    private val nonHttpAppLinkChecker: NonHttpAppLinkChecker = mock()

    private val exampleUrl = "http://example.com"
    private val shortExampleUrl = "example.com"

    private val selectedTab = TabEntity("TAB_ID", exampleUrl, position = 0, sourceTabId = "TAB_ID_SOURCE")
    private val flowSelectedTab = MutableStateFlow(selectedTab)

    private var isFullSiteAddressEnabled = true

    private val mockWebViewCompatWrapper: WebViewCompatWrapper = mock()

    private val mockWebView: WebView = mock()

    private val fakeAddDocumentStartJavaScriptPlugins = FakeAddDocumentStartJavaScriptPluginPoint()
    private val fakeMessagingPlugins = FakeWebMessagingPluginPoint()
    private val fakePostMessageWrapperPlugins = FakePostMessageWrapperPluginPoint()

    @Before
    fun before() =
        runTest {
            MockitoAnnotations.openMocks(this)

            swipingTabsFeature.self().setRawStoredState(State(enable = true))
            swipingTabsFeature.enabledForUsers().setRawStoredState(State(enable = true))

            db =
                Room
                    .inMemoryDatabaseBuilder(context, AppDatabase::class.java)
                    .allowMainThreadQueries()
                    .build()
            fireproofWebsiteDao = db.fireproofWebsiteDao()
            locationPermissionsDao = db.locationPermissionsDao()

            mockAutoCompleteApi =
                AutoCompleteApi(
                    mockAutoCompleteService,
                    mockSavedSitesRepository,
                    mockNavigationHistory,
                    mockAutoCompleteScorer,
                    mockAutoCompleteRepository,
                    mockTabRepository,
                    mockUserStageStore,
                    mockAutocompleteTabsFeature,
                    mockDuckChat,
                    mockHistory,
                    DefaultDispatcherProvider(),
                    mockPixel,
                )
            val fireproofWebsiteRepositoryImpl =
                FireproofWebsiteRepositoryImpl(
                    fireproofWebsiteDao,
                    coroutineRule.testDispatcherProvider,
                    lazyFaviconManager,
                )

            whenever(mockDuckPlayer.observeUserPreferences()).thenReturn(flowOf(UserPreferences(false, Disabled)))
            whenever(mockDismissedCtaDao.dismissedCtas()).thenReturn(dismissedCtaDaoChannel.consumeAsFlow())
            whenever(mockTabRepository.flowTabs).thenReturn(flowOf(emptyList()))
            whenever(mockTabRepository.getTabs()).thenReturn(emptyList())
            whenever(mockTabRepository.flowSelectedTab).thenReturn(flowSelectedTab)
            whenever(mockTabRepository.liveTabs).thenReturn(tabsLiveData)
            whenever(mockEmailManager.signedInFlow()).thenReturn(emailStateFlow.asStateFlow())
            whenever(mockSavedSitesRepository.getFavorites()).thenReturn(favoriteListFlow.consumeAsFlow())
            whenever(mockSavedSitesRepository.getBookmarks()).thenReturn(bookmarksListFlow.consumeAsFlow())
            whenever(mockRemoteMessagingRepository.messageFlow()).thenReturn(remoteMessageFlow.consumeAsFlow())
            whenever(mockSettingsDataStore.automaticFireproofSetting).thenReturn(AutomaticFireproofSetting.ASK_EVERY_TIME)
            whenever(mockSettingsDataStore.omnibarPosition).thenReturn(TOP)
            whenever(mockSettingsDataStore.isFullUrlEnabled).then { isFullSiteAddressEnabled }
            whenever(mockSSLCertificatesFeature.allowBypass()).thenReturn(mockEnabledToggle)
            whenever(subscriptions.shouldLaunchPrivacyProForUrl(any())).thenReturn(false)
            whenever(mockDuckDuckGoUrlDetector.isDuckDuckGoUrl(any())).thenReturn(false)
            whenever(mockDuckPlayer.isSimulatedYoutubeNoCookie(any())).thenReturn(false)
            whenever(mockDuckPlayer.isDuckPlayerUri(anyString())).thenReturn(false)
            whenever(mockDuckPlayer.getDuckPlayerState()).thenReturn(ENABLED)
            whenever(mockAutocompleteTabsFeature.self()).thenReturn(mockEnabledToggle)
            whenever(mockAutocompleteTabsFeature.self().isEnabled()).thenReturn(true)
            whenever(mockSitePermissionsManager.hasSitePermanentPermission(any(), any())).thenReturn(false)
            whenever(mockToggleReports.shouldPrompt()).thenReturn(false)
            whenever(subscriptions.isEligible()).thenReturn(false)
            whenever(mockDuckAiFeatureState.showPopupMenuShortcut).thenReturn(MutableStateFlow(false))
            whenever(mockDuckAiFeatureState.showInputScreen).thenReturn(mockDuckAiFeatureStateInputScreenFlow)
            whenever(mockDuckAiFeatureState.showInputScreenAutomaticallyOnNewTab).thenReturn(mockDuckAiFeatureStateInputScreenOpenAutomaticallyFlow)
            whenever(mockExternalIntentProcessingState.hasPendingTabLaunch).thenReturn(mockHasPendingTabLaunchFlow)
            whenever(mockExternalIntentProcessingState.hasPendingDuckAiOpen).thenReturn(mockHasPendingDuckAiOpenFlow)
            whenever(mockVpnMenuStateProvider.getVpnMenuState()).thenReturn(flowOf(VpnMenuState.Hidden))
            whenever(mockOnboardingDesignExperimentManager.isModifiedControlEnrolledAndEnabled()).thenReturn(false)
            whenever(mockOnboardingDesignExperimentManager.isBuckEnrolledAndEnabled()).thenReturn(false)
            whenever(mockOnboardingDesignExperimentManager.isBbEnrolledAndEnabled()).thenReturn(false)
            whenever(mockSerpEasterEggLogoToggles.feature()).thenReturn(mockDisabledToggle)
            whenever(nonHttpAppLinkChecker.isPermitted(anyOrNull())).thenReturn(true)
            remoteMessagingModel = givenRemoteMessagingModel(mockRemoteMessagingRepository, mockPixel, coroutineRule.testDispatcherProvider)
            whenever(mockAddressBarTrackersAnimationFeatureToggle.feature()).thenReturn(mockDisabledToggle)

            ctaViewModel =
                CtaViewModel(
                    appInstallStore = mockAppInstallStore,
                    pixel = mockPixel,
                    widgetCapabilities = mockWidgetCapabilities,
                    dismissedCtaDao = mockDismissedCtaDao,
                    userAllowListRepository = mockUserAllowListRepository,
                    settingsDataStore = ctaViewModelMockSettingsStore,
                    onboardingStore = mockOnboardingStore,
                    userStageStore = mockUserStageStore,
                    tabRepository = mockTabRepository,
                    dispatchers = coroutineRule.testDispatcherProvider,
                    duckDuckGoUrlDetector = DuckDuckGoUrlDetectorImpl(),
                    extendedOnboardingFeatureToggles = mockExtendedOnboardingFeatureToggles,
                    subscriptions = subscriptions,
                    duckPlayer = mockDuckPlayer,
                    brokenSitePrompt = mockBrokenSitePrompt,
                    onboardingHomeScreenWidgetToggles = mockOnboardingHomeScreenWidgetToggles,
                    onboardingDesignExperimentManager = mockOnboardingDesignExperimentManager,
                    rebrandingFeatureToggle = mockRebrandingFeatureToggle,
                )

            val siteFactory =
                SiteFactoryImpl(
                    mockEntityLookup,
                    mockContentBlocking,
                    mockUserAllowListRepository,
                    mockBypassedSSLCertificatesRepository,
                    coroutineRule.testScope,
                    coroutineRule.testDispatcherProvider,
                    DuckDuckGoUrlDetectorImpl(),
                    mockDuckPlayer,
                )

            accessibilitySettingsDataStore =
                AccessibilitySettingsSharedPreferences(
                    context,
                    coroutineRule.testDispatcherProvider,
                    coroutineRule.testScope,
                )

            whenever(mockOmnibarConverter.convertQueryToUrl(any(), any(), any(), any())).thenReturn("duckduckgo.com")
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
            whenever(mockPrivacyProtectionsPopupManager.viewState).thenReturn(flowOf(PrivacyProtectionsPopupViewState.Gone))
            whenever(mockAppBuildConfig.buildType).thenReturn("debug")
            whenever(mockDuckPlayer.observeUserPreferences()).thenReturn(flowOf(UserPreferences(false, AlwaysAsk)))
            whenever(mockAdditionalDefaultBrowserPrompts.showSetAsDefaultPopupMenuItem).thenReturn(
                defaultBrowserPromptsExperimentShowPopupMenuItemFlow,
            )

            whenever(mockSiteErrorHandlerKillSwitch.self()).thenReturn(mockSiteErrorHandlerKillSwitchToggle)

            testee =
                BrowserTabViewModel(
                    statisticsUpdater = mockStatisticsUpdater,
                    queryUrlConverter = mockOmnibarConverter,
                    duckDuckGoUrlDetector = DuckDuckGoUrlDetectorImpl(),
                    siteFactory = siteFactory,
                    tabRepository = mockTabRepository,
                    userAllowListRepository = mockUserAllowListRepository,
                    networkLeaderboardDao = mockNetworkLeaderboardDao,
                    autoComplete = mockAutoCompleteApi,
                    appSettingsPreferencesStore = ctaViewModelMockSettingsStore,
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
                    navigationAwareLoginDetector = mockNavigationAwareLoginDetector,
                    userEventsStore = mockUserEventsStore,
                    fileDownloader = mockFileDownloader,
                    fireproofDialogsEventHandler = fireproofDialogsEventHandler,
                    emailManager = mockEmailManager,
                    appCoroutineScope = TestScope(),
                    appLinksHandler = mockAppLinksHandler,
                    contentBlocking = mockContentBlocking,
                    accessibilitySettingsDataStore = accessibilitySettingsDataStore,
                    ampLinks = mockAmpLinks,
                    downloadCallback = mockDownloadCallback,
                    trackingParameters = mockTrackingParameters,
                    settingsDataStore = mockSettingsDataStore,
                    adClickManager = mockAdClickManager,
                    autofillCapabilityChecker = autofillCapabilityChecker,
                    autofillFireproofDialogSuppressor = autofillFireproofDialogSuppressor,
                    automaticSavedLoginsMonitor = automaticSavedLoginsMonitor,
                    device = mockDeviceInfo,
                    sitePermissionsManager = mockSitePermissionsManager,
                    cameraHardwareChecker = cameraHardwareChecker,
                    androidBrowserConfig = fakeAndroidConfigBrowserFeature,
                    privacyProtectionsPopupManager = mockPrivacyProtectionsPopupManager,
                    privacyProtectionsToggleUsageListener = mockPrivacyProtectionsToggleUsageListener,
                    privacyProtectionsPopupExperimentExternalPixels = privacyProtectionsPopupExperimentExternalPixels,
                    faviconsFetchingPrompt = mockFaviconFetchingPrompt,
                    subscriptions = subscriptions,
                    sslCertificatesFeature = mockSSLCertificatesFeature,
                    bypassedSSLCertificatesRepository = mockBypassedSSLCertificatesRepository,
                    userBrowserProperties = mockUserBrowserProperties,
                    history = mockNavigationHistory,
                    newTabPixels = { mockNewTabPixels },
                    httpErrorPixels = { mockHttpErrorPixels },
                    duckPlayer = mockDuckPlayer,
                    duckChat = mockDuckChat,
                    duckAiFeatureState = mockDuckAiFeatureState,
                    duckPlayerJSHelper =
                    DuckPlayerJSHelper(
                        mockDuckPlayer,
                        mockAppBuildConfig,
                        mockPixel,
                        mockDuckDuckGoUrlDetector,
                        mockPagesSettingPlugin,
                    ),
                    duckChatJSHelper = mockDuckChatJSHelper,
                    refreshPixelSender = refreshPixelSender,
                    privacyProtectionTogglePlugin = protectionTogglePluginPoint,
                    showOnAppLaunchOptionHandler = mockShowOnAppLaunchHandler,
                    customHeadersProvider = fakeCustomHeadersPlugin,
                    toggleReports = mockToggleReports,
                    brokenSitePrompt = mockBrokenSitePrompt,
                    tabStatsBucketing = mockTabStatsBucketing,
                    additionalDefaultBrowserPrompts = mockAdditionalDefaultBrowserPrompts,
                    swipingTabsFeature = swipingTabsFeatureProvider,
                    siteErrorHandlerKillSwitch = mockSiteErrorHandlerKillSwitch,
                    siteErrorHandler = mockSiteErrorHandler,
                    siteHttpErrorHandler = mockSiteHttpErrorHandler,
                    subscriptionsJSHelper = mockSubscriptionsJSHelper,
                    onboardingDesignExperimentManager = mockOnboardingDesignExperimentManager,
                    tabManager = tabManager,
                    addressDisplayFormatter = mockAddressDisplayFormatter,
                    autoCompleteSettings = mockAutoCompleteSettings,
                    serpEasterEggLogosToggles = mockSerpEasterEggLogoToggles,
                    nonHttpAppLinkChecker = nonHttpAppLinkChecker,
                    externalIntentProcessingState = mockExternalIntentProcessingState,
                    vpnMenuStateProvider = mockVpnMenuStateProvider,
                    webViewCompatWrapper = mockWebViewCompatWrapper,
                    addDocumentStartJavascriptPlugins = fakeAddDocumentStartJavaScriptPlugins,
                    webMessagingPlugins = fakeMessagingPlugins,
                    postMessageWrapperPlugins = fakePostMessageWrapperPlugins,
                    addressBarTrackersAnimationFeatureToggle = mockAddressBarTrackersAnimationFeatureToggle,
                )

            testee.loadData("abc", null, false, false)
            testee.command.observeForever(mockCommandObserver)
            val mockWebHistoryItem: WebHistoryItem = mock()
            whenever(mockWebHistoryItem.title).thenReturn("title")
            whenever(mockWebHistoryItem.url).thenReturn(exampleUrl)
            whenever(mockStack.getItemAtIndex(any())).thenReturn(mockWebHistoryItem)
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
        assertTrue(commandCaptor.lastValue is ShareLink)

        val shareLink = commandCaptor.lastValue as ShareLink
        assertEquals("https://duckduckgo.com/?q=test", shareLink.url)
    }

    @Test
    fun whenNonSearchUrlSharedThenUrlIsUnchanged() {
        val url = "https://duckduckgo.com/about?atb=v117-1&t=ddg_test"
        loadUrl(url)
        testee.onShareSelected()
        verify(mockCommandObserver, atLeastOnce()).onChanged(commandCaptor.capture())
        assertTrue(commandCaptor.lastValue is ShareLink)

        val shareLink = commandCaptor.lastValue as ShareLink
        assertEquals(url, shareLink.url)
    }

    @Test
    fun whenOpenInNewBackgroundRequestedThenTabRepositoryUpdatedAndCommandIssued() =
        runTest {
            val url = "http://www.example.com"
            testee.openInNewBackgroundTab(url)

            verify(mockCommandObserver, atLeastOnce()).onChanged(commandCaptor.capture())
            assertTrue(commandCaptor.lastValue is Command.OpenInNewBackgroundTab)

            verify(mockTabRepository).addNewTabAfterExistingTab(url, "abc")
        }

    @Test
    fun whenViewBecomesVisibleAndHomeShowingThenKeyboardShown() =
        runTest {
            whenever(mockExtendedOnboardingFeatureToggles.noBrowserCtas()).thenReturn(mockDisabledToggle)
            whenever(mockWidgetCapabilities.hasInstalledWidgets).thenReturn(true)

            setBrowserShowing(false)

            testee.onViewVisible()
            verify(mockCommandObserver, atLeastOnce()).onChanged(commandCaptor.capture())
            assertTrue(commandCaptor.allValues.contains(Command.ShowKeyboard))
        }

    @Test
    fun whenViewBecomesVisibleAndMaliciousSiteBlockedThenKeyboardNotShown() =
        runTest {
            whenever(mockExtendedOnboardingFeatureToggles.noBrowserCtas()).thenReturn(mockDisabledToggle)
            whenever(mockWidgetCapabilities.hasInstalledWidgets).thenReturn(true)
            testee.browserViewState.value = browserViewState().copy(maliciousSiteBlocked = true)

            testee.onViewVisible()

            assertCommandNotIssued<ShowKeyboard>()
        }

    @Test
    fun whenViewBecomesVisibleAndDuckAIPoCIsEnabledThenKeyboardNotShown() =
        runTest {
            whenever(mockExtendedOnboardingFeatureToggles.noBrowserCtas()).thenReturn(mockDisabledToggle)
            whenever(mockWidgetCapabilities.hasInstalledWidgets).thenReturn(true)
            whenever(mockDuckAiFeatureState.showInputScreen).thenReturn(MutableStateFlow(true))

            testee.onViewVisible()

            assertCommandNotIssued<ShowKeyboard>()
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
            whenever(mockExtendedOnboardingFeatureToggles.noBrowserCtas()).thenReturn(mockDisabledToggle)
            val observer = ValueCaptorObserver<CtaViewState>()
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
            val observer = ValueCaptorObserver<CtaViewState>()
            testee.ctaViewState.observeForever(observer)

            testee.onViewVisible()

            testee.ctaViewState.removeObserver(observer)
            assertFalse(observer.hasReceivedValue)
        }
    }

    @Test
    fun whenViewBecomesVisibleAndDuckChatDisabledThenDuckChatNotVisible() {
        whenever(mockDuckAiFeatureState.showPopupMenuShortcut).thenReturn(MutableStateFlow(false))
        setBrowserShowing(true)
        testee.onViewVisible()
        assertFalse(browserViewState().showDuckChatOption)
    }

    @Test
    fun whenViewBecomesVisibleAndDuckChatEnabledThenDuckChatIsVisible() {
        whenever(mockDuckAiFeatureState.showPopupMenuShortcut).thenReturn(MutableStateFlow(true))
        setBrowserShowing(true)
        testee.onViewVisible()
        assertTrue(browserViewState().showDuckChatOption)
    }

    @Test
    fun whenInvalidatedGlobalLayoutRestoredThenErrorIsShown() {
        givenInvalidatedGlobalLayout()
        setBrowserShowing(true)
        testee.onViewResumed()
        assertCommandIssued<Command.ShowErrorWithAction>()
    }

    @Test
    fun whenSubmittedQueryIsPrivacyProThenSendLaunchPrivacyProComment() {
        whenever(mockSpecialUrlDetector.determineType(anyString())).thenReturn(SpecialUrlDetector.UrlType.ShouldLaunchPrivacyProLink)
        whenever(mockOmnibarConverter.convertQueryToUrl("https://duckduckgo.com/pro", null)).thenReturn("https://duckduckgo.com/pro")
        whenever(subscriptions.shouldLaunchPrivacyProForUrl(any())).thenReturn(true)
        testee.onUserSubmittedQuery("https://duckduckgo.com/pro")
        assertCommandIssued<LaunchPrivacyPro> {
            assertEquals("https://duckduckgo.com/pro", uri.toString())
        }
    }

    @Test
    fun whenSubmittedQueryIsPrivacyProAndNavigationStateNullThenCloseTab() =
        runTest {
            givenOneActiveTabSelected()
            whenever(mockSpecialUrlDetector.determineType(anyString())).thenReturn(SpecialUrlDetector.UrlType.ShouldLaunchPrivacyProLink)
            whenever(mockOmnibarConverter.convertQueryToUrl("https://duckduckgo.com/pro", null)).thenReturn("https://duckduckgo.com/pro")
            whenever(subscriptions.shouldLaunchPrivacyProForUrl(any())).thenReturn(true)

            testee.onUserSubmittedQuery("https://duckduckgo.com/pro")
            assertCommandIssued<LaunchPrivacyPro> {
                assertEquals("https://duckduckgo.com/pro", uri.toString())
            }
            verify(mockAdClickManager).clearTabId(any())
            verify(mockTabRepository).deleteTabAndSelectSource(any())
        }

    @Test
    fun whenSubmittedQueryIsPrivacyProAndNoNavigationHistoryThenCloseTab() =
        runTest {
            givenOneActiveTabSelected()
            whenever(mockSpecialUrlDetector.determineType(anyString())).thenReturn(SpecialUrlDetector.UrlType.ShouldLaunchPrivacyProLink)
            whenever(mockOmnibarConverter.convertQueryToUrl("https://duckduckgo.com/pro", null)).thenReturn("https://duckduckgo.com/pro")
            whenever(subscriptions.shouldLaunchPrivacyProForUrl(any())).thenReturn(true)
            val nav: WebNavigationState = mock()
            whenever(nav.hasNavigationHistory).thenReturn(false)

            testee.navigationStateChanged(nav)
            testee.onUserSubmittedQuery("https://duckduckgo.com/pro")
            assertCommandIssued<LaunchPrivacyPro> {
                assertEquals("https://duckduckgo.com/pro", uri.toString())
            }
            verify(mockAdClickManager).clearTabId(any())
            verify(mockTabRepository).deleteTabAndSelectSource(any())
        }

    @Test
    fun whenSubmittedQueryIsPrivacyProAndNavigationHistoryThenDoNotCloseTab() =
        runTest {
            givenOneActiveTabSelected()
            whenever(mockSpecialUrlDetector.determineType(anyString())).thenReturn(SpecialUrlDetector.UrlType.ShouldLaunchPrivacyProLink)
            whenever(mockOmnibarConverter.convertQueryToUrl("https://duckduckgo.com/pro", null)).thenReturn("https://duckduckgo.com/pro")
            whenever(subscriptions.shouldLaunchPrivacyProForUrl(any())).thenReturn(true)
            val nav: WebNavigationState = mock()
            whenever(nav.hasNavigationHistory).thenReturn(true)

            testee.navigationStateChanged(nav)
            testee.onUserSubmittedQuery("https://duckduckgo.com/pro")
            assertCommandIssued<LaunchPrivacyPro> {
                assertEquals("https://duckduckgo.com/pro", uri.toString())
            }
            verify(mockAdClickManager, never()).clearTabId(any())
            verify(mockTabRepository, never()).deleteTabAndSelectSource(any())
        }

    @Test
    fun whenSubmittedQueryHasWhitespaceItIsTrimmed() {
        whenever(mockOmnibarConverter.convertQueryToUrl("nytimes.com", null)).thenReturn("nytimes.com")
        testee.onUserSubmittedQuery(" nytimes.com ")
        assertEquals("nytimes.com", omnibarViewState().omnibarText)
    }

    @Test
    fun whenBrowsingAndUrlPresentThenAddBookmarkButtonEnabled() {
        loadUrl("https://www.example.com", isBrowserShowing = true)
        assertTrue(browserViewState().canSaveSite)
    }

    @Test
    fun whenBrowsingAndNoUrlThenAddBookmarkButtonDisabled() {
        loadUrl(null, isBrowserShowing = true)
        assertFalse(browserViewState().canSaveSite)
    }

    @Test
    fun whenNotBrowsingAndUrlPresentThenAddBookmarkButtonDisabled() {
        loadUrl("https://www.example.com", isBrowserShowing = false)
        assertFalse(browserViewState().canSaveSite)
    }

    @Test
    fun whenLoadUrlDoNotUpdateSiteMaliciousSiteStatus() {
        val maliciousUri = "https://www.malicious.com".toUri()
        testee.onReceivedMaliciousSiteWarning(
            maliciousUri,
            Feed.PHISHING,
            exempted = false,
            clientSideHit = false,
            isMainframe = true,
        )

        testee.navigationStateChanged(
            buildWebNavigation(originalUrl = "https://www.example.com", currentUrl = "https://www.example.com", title = "title"),
        )

        assertEquals(PHISHING, testee.getSite()?.maliciousSiteStatus)
    }

    @Test
    fun whenOnReceivedSafeSiteUpdateSiteMaliciousSiteStatus() {
        val maliciousUri = "https://www.malicious.com".toUri()
        testee.onReceivedMaliciousSiteWarning(
            maliciousUri,
            Feed.PHISHING,
            exempted = false,
            clientSideHit = false,
            isMainframe = true,
        )
        testee.navigationStateChanged(
            buildWebNavigation(originalUrl = "https://www.example.com", currentUrl = "https://www.example.com", title = "title"),
        )

        testee.onReceivedMaliciousSiteSafe("https://www.example.com".toUri(), true)

        assertNull(testee.getSite()?.maliciousSiteStatus)
    }

    @Test
    fun whenBookmarkEditedThenRepositoryIsUpdated() =
        runTest {
            val folderId = "folder1"
            val bookmark =
                Bookmark(
                    id = UUID.randomUUID().toString(),
                    title = "A title",
                    url = "www.example.com",
                    parentId = folderId,
                    lastModified = "timestamp",
                )
            testee.onBookmarkEdited(bookmark, folderId, false)
            verify(mockSavedSitesRepository).updateBookmark(bookmark, folderId)
        }

    @Test
    fun whenFavoriteEditedThenRepositoryUpdated() =
        runTest {
            val favorite =
                Favorite(
                    id = UUID.randomUUID().toString(),
                    title = "A title",
                    url = "www.example.com",
                    lastModified = "timestamp",
                    position = 1,
                )
            testee.onFavouriteEdited(favorite)
            verify(mockSavedSitesRepository).updateFavourite(favorite)
        }

    @Test
    fun whenBookmarkDeleteRequestedThenViewStateUpdated() =
        runTest {
            val bookmark =
                Bookmark(
                    id = UUID.randomUUID().toString(),
                    title = "A title",
                    url = "www.example.com",
                    lastModified = "timestamp",
                )

            testee.onSavedSiteDeleted(bookmark)

            assertTrue(browserViewState().bookmark == null)
            assertTrue(browserViewState().favorite == null)
        }

    @Test
    fun whenBookmarkDeletionConfirmedThenFaviconDeletedAndRepositoryIsUpdated() =
        runTest {
            val bookmark =
                Bookmark(
                    id = UUID.randomUUID().toString(),
                    title = "A title",
                    url = "www.example.com",
                    lastModified = "timestamp",
                )

            testee.onDeleteFavoriteSnackbarDismissed(bookmark)

            verify(mockFaviconManager).deletePersistedFavicon(bookmark.url)
            verify(mockSavedSitesRepository).delete(bookmark)
        }

    @Test
    fun whenBookmarkAddedThenRepositoryIsUpdatedAndUserNotified() =
        runTest {
            val url = "http://www.example.com"
            val title = "A title"
            val bookmark =
                Bookmark(
                    id = UUID.randomUUID().toString(),
                    title = title,
                    url = url,
                    parentId = UUID.randomUUID().toString(),
                    lastModified = "timestamp",
                )
            whenever(mockSavedSitesRepository.insertBookmark(title = anyString(), url = anyString())).thenReturn(bookmark)
            loadUrl(url = url, title = title)

            testee.onBookmarkMenuClicked()

            verify(mockSavedSitesRepository)
                .insertBookmark(title = title, url = url)
            verify(mockCommandObserver, atLeastOnce()).onChanged(commandCaptor.capture())
            assertTrue(commandCaptor.lastValue is Command.ShowSavedSiteAddedConfirmation)
        }

    @Test
    fun whenFavoriteAddedThenRepositoryUpdatedAndUserNotified() =
        runTest {
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
    fun whenNoSiteAndUserSelectsToAddFavoriteThenSiteIsNotAdded() =
        runTest {
            testee.onFavoriteMenuClicked()

            verify(mockSavedSitesRepository, times(0)).insertFavorite(any(), any(), any(), any())
        }

    @Test
    fun whenDeleteQuickAccessItemCalledWithFavoriteThenRepositoryUpdated() =
        runTest {
            val savedSite = Favorite(UUID.randomUUID().toString(), "title", exampleUrl, lastModified = "timestamp", 0)

            testee.onDeleteFavoriteSnackbarDismissed(savedSite)

            verify(mockSavedSitesRepository).delete(savedSite)
        }

    @Test
    fun whenDeleteQuickAccessItemCalledWithBookmarkThenRepositoryUpdated() =
        runTest {
            val savedSite = Bookmark(UUID.randomUUID().toString(), "title", exampleUrl, lastModified = "timestamp")

            testee.onDeleteFavoriteSnackbarDismissed(savedSite)

            verify(mockSavedSitesRepository).delete(savedSite)
        }

    @Test
    fun whenDeleteSavedSiteCalledThenRepositoryUpdated() =
        runTest {
            val savedSite = Favorite(UUID.randomUUID().toString(), "title", exampleUrl, lastModified = "timestamp", 0)

            testee.onDeleteSavedSiteSnackbarDismissed(savedSite)

            verify(mockSavedSitesRepository).delete(savedSite, true)
        }

    @Test
    fun whenQuickAccessListChangedThenRepositoryUpdated() {
        val savedSite = Favorite(UUID.randomUUID().toString(), "title", exampleUrl, lastModified = "timestamp", 0)
        val savedSites = listOf(QuickAccessFavorite(savedSite))

        testee.onQuickAccessListChanged(savedSites)

        verify(mockSavedSitesRepository).updateWithPosition(listOf(savedSite))
    }

    @Test
    fun whenTrackerDetectedThenNetworkLeaderboardUpdated() {
        val networkEntity = TestEntity("Network1", "Network1", 10.0)
        val event =
            TrackingEvent(
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
    fun whenBrowsingAndUrlLoadedThenSiteVisitedEntryAddedToLeaderboardDao() =
        runTest {
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
    fun whenBrowsingAndUrlLoadedAndFullUrlDisabledThenUrlTitleAndOmnibarTextUpdatedToMatch() {
        isFullSiteAddressEnabled = false
        val exampleUrl = "http://example.com/abc"
        val exampleTitle = "Title"
        loadUrl(exampleUrl, title = exampleTitle, isBrowserShowing = true)
        assertEquals(exampleUrl, testee.url)
        assertEquals(shortExampleUrl, omnibarViewState().omnibarText)
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
    fun whenBrowsingAndUrlIsUpdatedAndFullUrlDisabledThenUrlAndOmnibarTextUpdatedToMatch() {
        isFullSiteAddressEnabled = false
        val currentUrl = "http://example.com/current"
        loadUrl(exampleUrl, isBrowserShowing = true)
        updateUrl(exampleUrl, currentUrl, true)
        assertEquals(currentUrl, testee.url)
        assertEquals(shortExampleUrl, omnibarViewState().omnibarText)
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
    fun whenNotBrowsingAndUrlIsUpdatedAndFullUrlDisabledThenUrlAndOmnibarTextRemainUnchanged() {
        isFullSiteAddressEnabled = false
        val currentUrl = "http://example.com/current"
        loadUrl(exampleUrl, isBrowserShowing = true)
        updateUrl(exampleUrl, currentUrl, false)
        assertEquals(exampleUrl, testee.url)
        assertEquals(shortExampleUrl, omnibarViewState().omnibarText)
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
    fun whenUserRedirectedBeforePreviousSiteLoadedAndNewContentDelayedThenWebContentIsBlankedOut() =
        runTest {
            loadUrl("http://duckduckgo.com")
            testee.progressChanged(50, WebViewNavigationState(mockStack, 50))

            overrideUrl(exampleUrl)
            advanceTimeBy(2000)

            assertCommandIssued<Command.HideWebContent>()
        }

    @Test
    fun whenUserRedirectedAfterSiteLoadedAndNewContentDelayedThenWebContentNotBlankedOut() =
        runTest {
            loadUrl("http://duckduckgo.com")
            testee.progressChanged(100, WebViewNavigationState(mockStack, 100))

            overrideUrl(exampleUrl)
            advanceTimeBy(2000)

            assertCommandNotIssued<Command.HideWebContent>()
        }

    @Test
    fun whenUserRedirectedThenNotifyLoginDetector() =
        runTest {
            loadUrl("http://duckduckgo.com")
            testee.progressChanged(100, WebViewNavigationState(mockStack, 100))

            overrideUrl(exampleUrl)

            verify(mockNavigationAwareLoginDetector).onEvent(NavigationEvent.Redirect(exampleUrl))
        }

    @Test
    fun whenLoadingProgressReaches50ThenShowWebContent() =
        runTest {
            loadUrl("http://duckduckgo.com")
            testee.progressChanged(50, WebViewNavigationState(mockStack, 50))
            overrideUrl(exampleUrl)
            advanceTimeBy(2000)

            onProgressChanged(url = exampleUrl, newProgress = 50)

            assertCommandIssued<Command.ShowWebContent>()
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
    fun whenDuckDuckGoUrlNotContainingQueryLoadedAndFullUrlDisabledThenShortUrlShown() {
        isFullSiteAddressEnabled = false
        loadUrl("http://duckduckgo.com")
        assertEquals("duckduckgo.com", omnibarViewState().omnibarText)
    }

    @Test
    fun whenNonDuckDuckGoUrlLoadedThenFullUrlShown() {
        loadUrl(exampleUrl)
        assertEquals(exampleUrl, omnibarViewState().omnibarText)
    }

    @Test
    fun whenNonDuckDuckGoUrlLoadedAndFullUrlDisabledThenShortUrlShown() {
        isFullSiteAddressEnabled = false
        loadUrl(exampleUrl)
        assertEquals(shortExampleUrl, omnibarViewState().omnibarText)
    }

    @Test
    fun whenBrowsingAndViewModelGetsProgressUpdateThenViewStateIsUpdated() {
        setBrowserShowing(true)

        testee.progressChanged(50, WebViewNavigationState(mockStack, 50))
        assertEquals(50, loadingViewState().progress)
        assertEquals(true, loadingViewState().isLoading)

        testee.progressChanged(100, WebViewNavigationState(mockStack, 100))
        assertEquals(100, loadingViewState().progress)
        assertEquals(false, loadingViewState().isLoading)
    }

    @Test
    fun whenBrowsingAndViewModelGetsProgressUpdateLowerThan50ThenViewStateIsUpdatedTo50() {
        setBrowserShowing(true)

        testee.progressChanged(15, WebViewNavigationState(mockStack, 15))
        assertEquals(50, loadingViewState().progress)
        assertEquals(true, loadingViewState().isLoading)
    }

    @Test
    fun whenNotBrowserAndViewModelGetsProgressUpdateThenViewStateIsNotUpdated() {
        setBrowserShowing(false)
        testee.progressChanged(10, WebViewNavigationState(mockStack, 10))
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
        testee.progressChanged(100, WebViewNavigationState(mockStack, 100))
        assertEquals(50, loadingViewState().progress)
    }

    @Test
    fun whenInitialisedThenPrivacyGradeIsNotShown() {
        assertFalse(browserViewState().showPrivacyShield.isEnabled())
    }

    @Test
    fun whenUrlUpdatedThenPrivacyGradeIsShown() {
        loadUrl("")
        assertTrue(browserViewState().showPrivacyShield.isEnabled())
    }

    @Test
    fun whenInitialisedThenFireButtonIsShown() {
        assertTrue(browserViewState().fireButton is HighlightableButton.Visible)
    }

    @Test
    fun whenInitialisedThenMenuButtonIsShown() {
        assertTrue(browserViewState().showMenuButton.isEnabled())
    }

    @Test
    fun whenTriggeringAutocompleteThenAutoCompleteSuggestionsShown() =
        runTest {
            whenever(mockAutoCompleteService.autoComplete("foo")).thenReturn(emptyList())
            doReturn(true).whenever(mockAutoCompleteSettings).autoCompleteSuggestionsEnabled
            testee.triggerAutocomplete("foo", true, hasQueryChanged = true)
            assertTrue(autoCompleteViewState().showSuggestions)
        }

    @Test
    fun whenTriggeringAutoCompleteButNoQueryChangeThenAutoCompleteSuggestionsNotShown() {
        doReturn(true).whenever(mockAutoCompleteSettings).autoCompleteSuggestionsEnabled
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
                            exampleUrl,
                            lastModified = "timestamp",
                            1,
                        ),
                    ),
                ),
            )
        doReturn(true).whenever(mockAutoCompleteSettings).autoCompleteSuggestionsEnabled
        testee.triggerAutocomplete("https://example.com", true, hasQueryChanged = false)
        assertFalse(autoCompleteViewState().showSuggestions)
        assertTrue(autoCompleteViewState().showFavorites)
    }

    @Test
    fun wheneverAutoCompleteIsGoneAndHistoryIAMHasBeenShownThenNotifyUserSeenIAM() {
        runTest {
            whenever(mockAutoCompleteService.autoComplete("title")).thenReturn(emptyList())
            whenever(mockSavedSitesRepository.getBookmarks()).thenReturn(
                flowOf(listOf(Bookmark("abc", "title", "https://example.com", lastModified = null))),
            )
            whenever(mockSavedSitesRepository.getFavorites()).thenReturn(
                flowOf(listOf(Favorite("abc", "title", "https://example.com", position = 1, lastModified = null))),
            )
            whenever(mockNavigationHistory.getHistory()).thenReturn(
                flowOf(listOf(VisitedPage("https://foo.com".toUri(), "title", listOf(LocalDateTime.now())))),
            )
            whenever(mockTabRepository.flowTabs).thenReturn(
                flowOf(listOf(TabEntity(tabId = "1", position = 1, url = "https://example.com", title = "title"))),
            )
            doReturn(true).whenever(mockAutoCompleteSettings).autoCompleteSuggestionsEnabled

            whenever(mockAutoCompleteRepository.wasHistoryInAutoCompleteIAMDismissed()).thenReturn(false)
            whenever(mockAutoCompleteRepository.countHistoryInAutoCompleteIAMShown()).thenReturn(0)
            whenever(mockAutoCompleteScorer.score("title", "https://foo.com".toUri(), 1, "title")).thenReturn(1)
            whenever(mockUserStageStore.getUserAppStage()).thenReturn(ESTABLISHED)

            testee.triggerAutocomplete("title", hasFocus = true, hasQueryChanged = true)
            delay(500)
            testee.autoCompleteSuggestionsGone()
            verify(mockAutoCompleteRepository).submitUserSeenHistoryIAM()
            verify(mockPixel).fire(AUTOCOMPLETE_BANNER_SHOWN)
        }
    }

    @Test
    fun wheneverAutoCompleteIsGoneAndHistoryIAMHasNotBeenShownThenDoNotNotifyUserSeenIAM() {
        runTest {
            whenever(mockAutoCompleteService.autoComplete("query")).thenReturn(emptyList())
            whenever(mockSavedSitesRepository.getBookmarks()).thenReturn(
                flowOf(listOf(Bookmark("abc", "title", "https://example.com", lastModified = null))),
            )
            whenever(mockSavedSitesRepository.getFavorites()).thenReturn(
                flowOf(listOf(Favorite("abc", "title", "https://example.com", position = 1, lastModified = null))),
            )
            whenever(mockNavigationHistory.getHistory()).thenReturn(flowOf(emptyList()))
            doReturn(true).whenever(mockAutoCompleteSettings).autoCompleteSuggestionsEnabled
            testee.autoCompleteStateFlow.value = "query"
            testee.autoCompleteSuggestionsGone()
            verify(mockAutoCompleteRepository, never()).submitUserSeenHistoryIAM()
            verify(mockPixel, never()).fire(AUTOCOMPLETE_BANNER_SHOWN)
        }
    }

    @Test
    fun whenEnteringEmptyQueryThenHideKeyboardCommandNotIssued() {
        testee.onUserSubmittedQuery("")
        verify(mockCommandObserver, never()).onChanged(any<Command.HideKeyboard>())
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
        whenever(ctaViewModelMockSettingsStore.showAppLinksPrompt).thenReturn(true)
        whenever(mockOmnibarConverter.convertQueryToUrl("foo", null)).thenReturn("foo.com")
        testee.onUserSubmittedQuery("foo")
        verify(mockCommandObserver, atLeastOnce()).onChanged(commandCaptor.capture())
        assertTrue(commandCaptor.allValues.any { it == Command.HideKeyboard })
        verify(mockAppLinksHandler).updatePreviousUrl(null)
    }

    @Test
    fun whenEnteringAppLinkQueryAndShouldNotShowAppLinksPromptThenNavigateInBrowserAndSetUserQueryState() {
        whenever(ctaViewModelMockSettingsStore.showAppLinksPrompt).thenReturn(false)
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
        loadUrl(exampleUrl)
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
        loadUrl(exampleUrl)
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
    fun whenIsCustomTabAndCannotGoBackThenReturnFalse() {
        setupNavigation(isBrowsing = true, canGoBack = false)
        assertFalse(testee.onUserPressedBack(isCustomTab = true))
    }

    @Test
    fun whenIsCustomTabAndCannotGoBackThenNavigateBackAndReturnTrue() {
        setupNavigation(isBrowsing = true, canGoBack = true)
        assertTrue(testee.onUserPressedBack(isCustomTab = true))

        val backCommand = captureCommands().lastValue as NavigationCommand.NavigateBack
        assertNotNull(backCommand)
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
    fun whenMaliciousSiteWarningShowingByPressingBackThenHideMaliciousSiteBlocked() {
        setupNavigation(isBrowsing = false, isMaliciousSiteBlocked = true, canGoBack = true, stepsToPreviousPage = 1)

        val result = testee.onUserPressedBack()

        assertFalse(browserViewState().browserShowing)
        assertCommandIssued<HideWarningMaliciousSite>()
        assertTrue(result)
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

        testee.onRefreshRequested(triggeredByUser = true)

        assertCommandIssued<Command.OpenInNewTab> {
            assertNull(sourceTabId)
        }
    }

    @Test
    fun whenRefreshRequestedWithInvalidatedGlobalLayoutThenCloseCurrentTab() {
        givenOneActiveTabSelected()
        givenInvalidatedGlobalLayout()

        testee.onRefreshRequested(triggeredByUser = true)

        runTest {
            verify(mockTabRepository).deleteTabAndSelectSource(selectedTabLiveData.value!!.tabId)
        }
    }

    @Test
    fun whenRefreshRequestedWithBrowserGlobalLayoutThenRefresh() {
        testee.onRefreshRequested(triggeredByUser = true)
        assertCommandIssued<NavigationCommand.Refresh>()
    }

    @Test
    fun whenRefreshRequestedWithQuerySearchThenFireQueryChangePixelZero() {
        loadUrl("query")

        testee.onRefreshRequested(triggeredByUser = true)

        verify(mockPixel).fire("rq_0")
    }

    @Test
    fun whenRefreshRequestedWithUrlThenDoNotFireQueryChangePixel() {
        loadUrl("https://example.com")

        testee.onRefreshRequested(triggeredByUser = true)

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
        assertEquals(exampleUrl, ultimateCommand.url)
    }

    @Test
    fun whenUserSelectsDesktopSiteWhenNotOnMobileSpecificSiteThenUrlNotModified() {
        loadUrl(exampleUrl)
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
        loadUrl(exampleUrl)
        setDesktopBrowsingMode(true)
        testee.onChangeBrowserModeClicked()
        verify(mockCommandObserver, atLeastOnce()).onChanged(commandCaptor.capture())
        val ultimateCommand = commandCaptor.lastValue
        assertTrue(ultimateCommand == NavigationCommand.Refresh)
    }

    @Test
    fun whenUserSelectsOpenTabAndItIsPrivacyProThenLaunchPrivacyProCommandSent() {
        whenever(mockLongPressHandler.userSelectedMenuItem(any(), any())).thenReturn(OpenInNewTab(exampleUrl))
        whenever(subscriptions.shouldLaunchPrivacyProForUrl(any())).thenReturn(true)

        val mockMenItem: MenuItem = mock()
        val longPressTarget = LongPressTarget(url = exampleUrl, type = WebView.HitTestResult.SRC_ANCHOR_TYPE)
        testee.userSelectedItemFromLongPressMenu(longPressTarget, mockMenItem)

        assertCommandIssued<LaunchPrivacyPro>()
        assertCommandNotIssued<Command.OpenInNewTab>()
    }

    @Test
    fun whenUserSelectsOpenInBackgroundTabAndItIsPrivacyProThenLaunchPrivacyProCommandSent() {
        whenever(mockLongPressHandler.userSelectedMenuItem(any(), any())).thenReturn(RequiredAction.OpenInNewBackgroundTab(exampleUrl))
        whenever(subscriptions.shouldLaunchPrivacyProForUrl(any())).thenReturn(true)

        val mockMenItem: MenuItem = mock()
        val longPressTarget = LongPressTarget(url = exampleUrl, type = WebView.HitTestResult.SRC_ANCHOR_TYPE)
        testee.userSelectedItemFromLongPressMenu(longPressTarget, mockMenItem)

        assertCommandIssued<LaunchPrivacyPro>()
        assertCommandNotIssued<Command.OpenInNewBackgroundTab>()
    }

    @Test
    fun whenUserSelectsOpenTabThenTabCommandSent() {
        whenever(mockLongPressHandler.userSelectedMenuItem(any(), any())).thenReturn(OpenInNewTab(exampleUrl))
        val mockMenItem: MenuItem = mock()
        val longPressTarget = LongPressTarget(url = exampleUrl, type = WebView.HitTestResult.SRC_ANCHOR_TYPE)
        testee.userSelectedItemFromLongPressMenu(longPressTarget, mockMenItem)
        val command = captureCommands().lastValue as Command.OpenInNewTab
        assertEquals(exampleUrl, command.query)

        assertCommandIssued<Command.OpenInNewTab> {
            assertNotNull(sourceTabId)
        }
    }

    @Test
    fun whenSiteLoadedAndUserSelectsToAddBookmarkThenAddBookmarkCommandSentWithUrlAndTitle() =
        runTest {
            val url = "http://foo.com"
            val title = "Foo Title"
            val bookmark =
                Bookmark(
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
            val command = captureCommands().lastValue as Command.ShowSavedSiteAddedConfirmation
            assertEquals(url, command.savedSiteChangedViewState.savedSite.url)
            assertEquals(title, command.savedSiteChangedViewState.savedSite.title)
        }

    @Test
    fun whenSiteLoadedWithSimulatedYouTubeNoCookieAndDuckPlayerEnabledThenShowWebPageTitleWithDuckPlayerIcon() =
        runTest {
            val url = "http://youtube-nocookie.com/videoID=1234"
            val title = "Duck Player"
            whenever(mockDuckPlayer.isDuckPlayerUri(anyString())).thenReturn(true)
            whenever(mockDuckPlayer.isSimulatedYoutubeNoCookie(any())).thenReturn(true)
            whenever(mockDuckPlayer.createDuckPlayerUriFromYoutubeNoCookie(any())).thenReturn("duck://player/1234")
            whenever(mockDuckPlayer.getDuckPlayerState()).thenReturn(ENABLED)

            loadUrl(url = url)
            testee.titleReceived(newTitle = title)
            val command = captureCommands().lastValue as Command.ShowWebPageTitle
            assertTrue(command.showDuckPlayerIcon)
            assertEquals("duck://player/1234", command.url)
        }

    @Test
    fun whenSiteLoadedWithDuckPlayerDisabledThenShowWebPageTitleWithoutDuckPlayerIcon() =
        runTest {
            val url = "http://youtube-nocookie.com/videoID=1234"
            val title = "Duck Player"
            whenever(mockDuckPlayer.getDuckPlayerState()).thenReturn(DISABLED)

            loadUrl(url = url)
            testee.titleReceived(newTitle = title)
            val command = captureCommands().lastValue as Command.ShowWebPageTitle
            assertFalse(command.showDuckPlayerIcon)
            assertEquals("http://youtube-nocookie.com/videoID=1234", command.url)
        }

    @Test
    fun whenNoSiteAndUserSelectsToAddBookmarkThenBookmarkIsNotAdded() =
        runTest {
            val bookmark =
                Bookmark(
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
    fun whenPrivacyProtectionMenuClickedAndSiteNotInAllowListThenSiteAddedToAllowListAndPixelSentAndPageRefreshed() =
        runTest {
            whenever(mockUserAllowListRepository.isDomainInUserAllowList("www.example.com")).thenReturn(false)
            loadUrl("http://www.example.com/home.html")
            testee.onPrivacyProtectionMenuClicked()
            verify(mockUserAllowListRepository).addDomainToUserAllowList("www.example.com")
            verify(mockPixel).fire(AppPixelName.BROWSER_MENU_ALLOWLIST_ADD)
            assertEquals(1, protectionTogglePlugin.toggleOff)
        }

    @Test
    fun whenPrivacyProtectionMenuClickedToTurnOffProtectionsThenTogglePromptIsShownWhenCheckIsTrue() =
        runTest {
            whenever(mockUserAllowListRepository.isDomainInUserAllowList("www.example.com")).thenReturn(false)
            loadUrl("http://www.example.com/home.html")
            testee.onPrivacyProtectionMenuClicked()
            verify(mockToggleReports).shouldPrompt()
        }

    @Test
    fun whenPrivacyProtectionMenuClickedForNonAllowListedSiteThenRefreshAndShowDisabledConfirmationMessage() =
        runTest {
            val domain = "www.example.com"
            val url = "http://www.example.com/home.html"

            val allowlistFlow = MutableStateFlow(listOf<String>())
            whenever(mockUserAllowListRepository.domainsInUserAllowListFlow()).thenReturn(allowlistFlow)

            loadUrl(url)
            testee.onPrivacyProtectionMenuClicked()
            allowlistFlow.value = listOf(domain)

            assertCommandIssued<RefreshAndShowPrivacyProtectionDisabledConfirmation> {
                assertEquals(domain, this.domain)
            }
        }

    @Test
    fun whenPrivacyProtectionMenuClickedForAllowListedSiteThenRefreshAndShowEnabledConfirmationMessage() =
        runTest {
            val domain = "www.example.com"
            val url = "http://www.example.com/home.html"

            val allowlistFlow = MutableStateFlow(listOf(domain))
            whenever(mockUserAllowListRepository.domainsInUserAllowListFlow()).thenReturn(allowlistFlow)

            loadUrl(url)
            testee.onPrivacyProtectionMenuClicked()
            allowlistFlow.value = emptyList()

            assertCommandIssued<RefreshAndShowPrivacyProtectionEnabledConfirmation> {
                assertEquals(domain, this.domain)
            }
        }

    @Test
    fun whenPrivacyProtectionMenuClickedForAllowListedSiteThenSiteRemovedFromAllowListAndPixelSentAndPageRefreshed() =
        runTest {
            whenever(mockUserAllowListRepository.isDomainInUserAllowList("www.example.com")).thenReturn(true)
            loadUrl("http://www.example.com/home.html")
            testee.onPrivacyProtectionMenuClicked()
            verify(mockUserAllowListRepository).removeDomainFromUserAllowList("www.example.com")
            verify(mockPixel).fire(AppPixelName.BROWSER_MENU_ALLOWLIST_REMOVE)
            assertEquals(1, protectionTogglePlugin.toggleOn)
        }

    @Test
    fun whenInCustomTabAndPrivacyProtectionMenuClickedAndSiteNotInAllowListThenSiteAddedToAllowListAndPixelSentAndPageRefreshed() =
        runTest {
            whenever(mockUserAllowListRepository.isDomainInUserAllowList("www.example.com")).thenReturn(false)
            loadUrl("http://www.example.com/home.html")
            testee.onPrivacyProtectionMenuClicked(clickedFromCustomTab = true)
            verify(mockUserAllowListRepository).addDomainToUserAllowList("www.example.com")
            verify(mockPixel).fire(CustomTabPixelNames.CUSTOM_TABS_MENU_DISABLE_PROTECTIONS_ALLOW_LIST_ADD)
        }

    @Test
    fun whenInCustomTabAndPrivacyProtectionMenuClickedForAllowListedSiteThenSiteRemovedFromAllowListAndPixelSentAndPageRefreshed() =
        runTest {
            whenever(mockUserAllowListRepository.isDomainInUserAllowList("www.example.com")).thenReturn(true)
            loadUrl("http://www.example.com/home.html")
            testee.onPrivacyProtectionMenuClicked(clickedFromCustomTab = true)
            verify(mockUserAllowListRepository).removeDomainFromUserAllowList("www.example.com")
            verify(mockPixel).fire(CustomTabPixelNames.CUSTOM_TABS_MENU_DISABLE_PROTECTIONS_ALLOW_LIST_REMOVE)
        }

    @Test
    fun whenOnSiteAndBrokenSiteSelectedThenBrokenSiteFeedbackCommandSentWithUrl() =
        runTest {
            loadUrl("foo.com", isBrowserShowing = true)
            testee.onBrokenSiteSelected()
            val command = captureCommands().lastValue as Command.BrokenSiteFeedback
            assertEquals("foo.com", command.data.url)
        }

    @Test
    fun whenNoSiteAndBrokenSiteSelectedThenBrokenSiteFeedbackCommandSentWithoutUrl() {
        testee.onBrokenSiteSelected()
        val command = captureCommands().lastValue as Command.BrokenSiteFeedback
        assertEquals("", command.data.url)
    }

    @Test
    fun whenUserSelectsToShareLinkThenShareLinkCommandSent() {
        loadUrl("foo.com")
        testee.onShareSelected()
        val command = captureCommands().lastValue as ShareLink
        assertEquals("foo.com", command.url)
    }

    @Test
    fun whenUserSelectsToShareLinkWithNullUrlThenShareLinkCommandNotSent() {
        loadUrl(null)
        testee.onShareSelected()
        assertCommandNotIssued<ShareLink>()
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
    fun whenUrlNullThenSetBrowserNotShowing() =
        runTest {
            testee.loadData("id", null, false, false)
            testee.determineShowBrowser()
            assertEquals(false, testee.browserViewState.value?.browserShowing)
        }

    @Test
    fun whenUrlBlankThenSetBrowserNotShowing() =
        runTest {
            testee.loadData("id", "  ", false, false)
            testee.determineShowBrowser()
            assertEquals(false, testee.browserViewState.value?.browserShowing)
        }

    @Test
    fun whenUrlPresentThenSetBrowserShowing() =
        runTest {
            testee.loadData("id", "https://example.com", false, false)
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
        val showErrorWithAction = commandCaptor.lastValue as Command.ShowErrorWithAction

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
        val showErrorWithAction = commandCaptor.lastValue as Command.ShowErrorWithAction

        showErrorWithAction.action()

        runTest {
            verify(mockTabRepository).deleteTabAndSelectSource(selectedTabLiveData.value!!.tabId)
        }
    }

    @Test
    fun whenRecoveringFromProcessGoneThenGlobalLayoutIsInvalidated() {
        testee.recoverFromRenderProcessGone()

        assertTrue(globalLayoutViewState() is GlobalLayoutViewState.Invalidated)
    }

    @Test
    fun whenRecoveringFromProcessGoneThenLoadingIsReset() {
        testee.recoverFromRenderProcessGone()

        assertEquals(loadingViewState(), LoadingViewState())
    }

    @Test
    fun whenRecoveringFromProcessGoneThenFindInPageIsReset() {
        testee.recoverFromRenderProcessGone()

        assertEquals(findInPageViewState(), FindInPageViewState())
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

        loadUrl(url = exampleUrl, isBrowserShowing = true)
        testee.requiresAuthentication(authenticationRequest)

        assertCommandNotIssued<Command.HideWebContent>()
        assertEquals(exampleUrl, omnibarViewState().omnibarText)
    }

    @Test
    fun whenAuthenticationIsRequiredForSameHostAndFullUrlDisabledThenNoChangesOnBrowser() {
        isFullSiteAddressEnabled = false

        val mockHandler = mock<HttpAuthHandler>()
        val siteURL = "http://example.com/requires-auth"
        val authenticationRequest = BasicAuthenticationRequest(mockHandler, "example.com", "test realm", siteURL)

        loadUrl(url = exampleUrl, isBrowserShowing = true)
        testee.requiresAuthentication(authenticationRequest)

        assertCommandNotIssued<Command.HideWebContent>()
        assertEquals(shortExampleUrl, omnibarViewState().omnibarText)
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
    fun whenAuthenticationIsRequiredForDifferentHostAndFullUrlDisabledThenUpdateUrlAndHideWebContent() {
        isFullSiteAddressEnabled = false
        val mockHandler = mock<HttpAuthHandler>()
        val siteURL = "http://example.com/requires-auth"
        val authenticationRequest = BasicAuthenticationRequest(mockHandler, "example.com", "test realm", siteURL)

        loadUrl(url = "http://another.website.com", isBrowserShowing = true)
        testee.requiresAuthentication(authenticationRequest)

        assertCommandIssued<Command.HideWebContent>()
        assertEquals(shortExampleUrl, omnibarViewState().omnibarText)
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
    fun whenUserSelectToEditQueryThenMoveCaretToTheEnd() =
        runTest {
            testee.onUserSelectedToEditQuery("foo")

            assertCommandIssued<Command.EditWithSelectedQuery>()
        }

    @Test
    fun whenUserRequestedToOpenNewTabThenGenerateWebViewPreviewImage() {
        testee.onNewTabMenuItemClicked()
        assertCommandIssued<Command.GenerateWebViewPreviewImage>()
        verify(mockPixel, never()).fire(AppPixelName.TAB_MANAGER_NEW_TAB_LONG_PRESSED)
    }

    @Test
    fun whenUserRequestedToOpenNewTabAndNoEmptyTabExistsThenNewTabCommandIssued() {
        tabsLiveData.value = listOf(TabEntity("1", "https://example.com", position = 0))
        testee.onNewTabMenuItemClicked()
        verify(mockCommandObserver, atLeastOnce()).onChanged(commandCaptor.capture())
        val command = commandCaptor.lastValue
        assertTrue(command is Command.LaunchNewTab)
        verify(mockPixel, never()).fire(AppPixelName.TAB_MANAGER_NEW_TAB_LONG_PRESSED)
    }

    @Test
    fun whenUserRequestedToOpenNewTabAndEmptyTabExistsThenSelectTheEmptyTab() =
        runTest {
            val emptyTabId = "EMPTY_TAB"
            whenever(mockTabRepository.getTabs()).thenReturn(listOf(TabEntity(emptyTabId)))
            testee.onNewTabMenuItemClicked()

            verify(mockCommandObserver, atLeastOnce()).onChanged(commandCaptor.capture())
            val command = commandCaptor.lastValue
            assertFalse(command is Command.LaunchNewTab)

            verify(mockTabRepository).select(emptyTabId)
            verify(mockPixel, never()).fire(AppPixelName.TAB_MANAGER_NEW_TAB_LONG_PRESSED)
        }

    @Test
    fun whenUserRequestedToOpenNewTabByLongPressThenPixelFired() {
        testee.onNewTabMenuItemClicked(longPress = true)

        verify(mockPixel).fire(AppPixelName.TAB_MANAGER_NEW_TAB_LONG_PRESSED)
    }

    @Test
    fun whenCloseCurrentTabSelectedThenTabDeletedFromRepository() =
        runTest {
            givenOneActiveTabSelected()
            testee.closeCurrentTab()
            verify(mockTabRepository).deleteTabAndSelectSource(selectedTabLiveData.value!!.tabId)
        }

    @Test
    fun whenCloseAndSelectSourceTabSelectedThenTabDeletedFromRepository() =
        runTest {
            givenOneActiveTabSelected()
            testee.closeAndSelectSourceTab()
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
    fun whenUserPressesBackAndGoesToHomeWithInputScreenEnabledThenKeyboardNotShown() {
        whenever(mockDuckAiFeatureState.showInputScreen).thenReturn(MutableStateFlow(true))

        setupNavigation(isBrowsing = true, canGoBack = false, skipHome = false)
        testee.onUserPressedBack()

        assertCommandNotIssued<ShowKeyboard>()
    }

    @Test
    fun whenUserPressesBackOnATabWithASourceTabThenDeleteCurrentAndSelectSource() =
        runTest {
            selectedTabLiveData.value = TabEntity("TAB_ID", "https://example.com", position = 0, sourceTabId = "TAB_ID_SOURCE")
            setupNavigation(isBrowsing = true)

            testee.onUserPressedBack()

            verify(mockTabRepository).deleteTabAndSelectSource("TAB_ID")
        }

    @Test
    fun whenCtaRefreshedAndOnlyStandardAddSupportedAndWidgetAlreadyInstalledThenCtaIsNull() =
        runTest {
            whenever(mockExtendedOnboardingFeatureToggles.noBrowserCtas()).thenReturn(mockDisabledToggle)
            whenever(mockWidgetCapabilities.supportsAutomaticWidgetAdd).thenReturn(false)
            whenever(mockWidgetCapabilities.hasInstalledWidgets).thenReturn(true)
            testee.refreshCta()
            assertNull(testee.ctaViewState.value!!.cta)
        }

    @Test
    fun whenCtaRefreshedAndIsNewTabIsFalseThenReturnNull() =
        runTest {
            setBrowserShowing(true)
            whenever(mockExtendedOnboardingFeatureToggles.noBrowserCtas()).thenReturn(mockDisabledToggle)
            whenever(mockWidgetCapabilities.supportsAutomaticWidgetAdd).thenReturn(true)
            whenever(mockWidgetCapabilities.hasInstalledWidgets).thenReturn(false)
            testee.refreshCta()
            assertNull(testee.ctaViewState.value!!.cta)
        }

    @Test
    fun whenCtaRefreshedAndOnboardingCompleteThenViewStateUpdated() =
        runTest {
            whenever(mockExtendedOnboardingFeatureToggles.noBrowserCtas()).thenReturn(mockDisabledToggle)
            whenever(mockWidgetCapabilities.supportsAutomaticWidgetAdd).thenReturn(false)
            whenever(mockWidgetCapabilities.hasInstalledWidgets).thenReturn(true)
            whenever(mockDismissedCtaDao.exists(DAX_END)).thenReturn(true)
            whenever(mockDismissedCtaDao.exists(DAX_DIALOG_TRACKERS_FOUND)).thenReturn(true)
            testee.refreshCta()
            assertNull(testee.ctaViewState.value!!.cta)
            assertTrue(testee.ctaViewState.value!!.isOnboardingCompleteInNewTabPage)
            assertFalse(testee.ctaViewState.value!!.isBrowserShowing)
        }

    @Test
    fun whenCtaRefreshedAndBrowserShowingThenViewStateUpdated() =
        runTest {
            setBrowserShowing(true)
            whenever(mockExtendedOnboardingFeatureToggles.noBrowserCtas()).thenReturn(mockDisabledToggle)
            whenever(mockWidgetCapabilities.supportsAutomaticWidgetAdd).thenReturn(false)
            whenever(mockWidgetCapabilities.hasInstalledWidgets).thenReturn(true)
            whenever(mockDismissedCtaDao.exists(DAX_END)).thenReturn(true)
            whenever(mockDismissedCtaDao.exists(DAX_DIALOG_NETWORK)).thenReturn(true)
            testee.refreshCta()
            assertNull(testee.ctaViewState.value!!.cta)
            assertTrue(testee.ctaViewState.value!!.isOnboardingCompleteInNewTabPage)
            assertTrue(testee.ctaViewState.value!!.isBrowserShowing)
        }

    @Test
    fun whenCtaRefreshedAndMaliciousSiteBlockedThenViewStateUpdated() =
        runTest {
            whenever(mockExtendedOnboardingFeatureToggles.noBrowserCtas()).thenReturn(mockDisabledToggle)
            testee.browserViewState.value =
                browserViewState().copy(
                    browserShowing = false,
                    maliciousSiteBlocked = true,
                )

            testee.refreshCta()

            assertTrue(testee.ctaViewState.value!!.isErrorShowing)
        }

    @Test
    fun whenCtaRefreshedGetUserRefreshesCalled() =
        runTest {
            setBrowserShowing(true)
            whenever(mockExtendedOnboardingFeatureToggles.noBrowserCtas()).thenReturn(mockDisabledToggle)
            whenever(mockWidgetCapabilities.supportsAutomaticWidgetAdd).thenReturn(false)
            whenever(mockWidgetCapabilities.hasInstalledWidgets).thenReturn(true)
            val expectedRefreshPatterns = setOf(RefreshPattern.THRICE_IN_20_SECONDS)
            whenever(mockBrokenSitePrompt.getUserRefreshPatterns()).thenReturn(expectedRefreshPatterns)
            testee.refreshCta()
            verify(mockBrokenSitePrompt).getUserRefreshPatterns()
        }

    @Test
    fun whenCtaShownThenFirePixel() =
        runTest {
            val cta = HomePanelCta.AddWidgetAuto
            testee.ctaViewState.value = CtaViewState(cta = cta)

            testee.onCtaShown()
            verify(mockPixel).fire(cta.shownPixel!!, cta.pixelShownParameters())
        }

    @Test
    fun whenRefreshCtaIfCtaAlreadyShownForCurrentPageThenReturnNull() =
        runTest {
            setBrowserShowing(isBrowsing = true)
            testee.hasCtaBeenShownForCurrentPage.set(true)

            assertNull(testee.refreshCta())
        }

    @Test
    fun whenUserClickedCtaButtonThenFirePixel() {
        val cta = DaxBubbleCta.DaxIntroSearchOptionsCta(mockOnboardingStore, mockAppInstallStore)
        setCta(cta)
        testee.onUserClickCtaOkButton(cta)
        verify(mockPixel).fire(cta.okPixel!!, cta.pixelOkParameters())
    }

    @Test
    fun whenUserClickedDaxMainNetworkCtaOKButtonAndMaliciousSiteBlockedThenCtaIsNull() {
        val cta = DaxMainNetworkCta(mockOnboardingStore, mockAppInstallStore, "", "", mockOnboardingDesignExperimentManager)
        setCta(cta)

        testee.onUserClickCtaOkButton(cta)

        assertNull(testee.ctaViewState.value?.cta)
    }

    @Test
    fun whenUserClickedAddWidgetCtaButtonThenLaunchAddWidgetCommand() {
        val cta = HomePanelCta.AddWidgetAuto
        setCta(cta)
        testee.onUserClickCtaOkButton(cta)
        assertCommandIssued<Command.LaunchAddWidget>()
    }

    @Test
    fun whenUserClickedLegacyAddWidgetCtaButtonThenLaunchAddWidgetCommand() {
        val cta = HomePanelCta.AddWidgetInstructions
        setCta(cta)
        testee.onUserClickCtaOkButton(cta)
        assertCommandIssued<Command.LaunchAddWidget>()
    }

    @Test
    fun whenUserClickedLearnMoreExperimentBubbleCtaButtonThenLaunchPrivacyPro() {
        val cta =
            DaxBubbleCta.DaxPrivacyProCta(
                mockOnboardingStore,
                mockAppInstallStore,
                R.string.onboardingPrivacyProDaxDialogTitle,
                R.string.onboardingPrivacyProDaxDialogDescription,
                R.string.onboardingPrivacyProDaxDialogOkButton,
            )
        setCta(cta)
        testee.onUserClickCtaOkButton(cta)
        assertCommandIssued<LaunchPrivacyPro>()
    }

    @Test
    fun whenUserDismissedCtaThenFirePixel() =
        runTest {
            val cta = HomePanelCta.AddWidgetAuto
            setCta(cta)
            testee.onUserDismissedCta(cta)
            verify(mockPixel).fire(cta.cancelPixel!!, cta.pixelCancelParameters())
        }

    @Test
    fun whenUserDismissedCtaThenRegisterInDatabase() =
        runTest {
            val cta = HomePanelCta.AddWidgetAuto
            setCta(cta)
            testee.onUserDismissedCta(cta)
            verify(mockDismissedCtaDao).insert(DismissedCta(cta.ctaId))
        }

    @Test
    fun whenSurrogateDetectedThenSiteUpdated() {
        givenOneActiveTabSelected()
        val surrogate = SurrogateResponse()
        testee.surrogateDetected(surrogate)
        assertTrue(
            testee.siteLiveData.value
                ?.surrogates
                ?.size == 1,
        )
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
    fun whenOnBrokenSiteSelectedOpenBokenSiteFeedback() =
        runTest {
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
    fun whenUserClicksOnRemoveFireproofingSnackbarUndoActionThenPixelSent() =
        runTest {
            givenFireproofWebsiteDomain("example.com")
            loadUrl("http://example.com/", isBrowserShowing = true)
            testee.onFireproofWebsiteMenuClicked()
            assertCommandIssued<Command.DeleteFireproofConfirmation> {
                testee.onRemoveFireproofWebsiteSnackbarUndoClicked(this.fireproofWebsiteEntity)
            }
            verify(mockPixel).fire(AppPixelName.FIREPROOF_REMOVE_WEBSITE_UNDO)
        }

    @Test
    fun whenUserFireproofsWebsiteFromLoginDialogThenShowConfirmationIsIssuedWithExpectedDomain() =
        runTest {
            whenever(fireproofDialogsEventHandler.onUserConfirmedFireproofDialog(anyString())).doAnswer {
                val domain = it.arguments.first() as String
                fireproofDialogsEventHandlerLiveData.postValue(
                    FireproofDialogsEventHandler.Event.FireproofWebSiteSuccess(FireproofWebsiteEntity(domain)),
                )
            }

            testee.onUserConfirmedFireproofDialog("login.example.com")

            assertCommandIssued<Command.ShowFireproofWebSiteConfirmation> {
                assertEquals("login.example.com", this.fireproofWebsiteEntity.domain)
            }
        }

    @Test
    fun whenAskToDisableLoginDetectionEventReceivedThenAskUserToDisableLoginDetection() =
        runTest {
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
    fun whenUserBrowsingPressesBackThenCannotAddBookmark() {
        setupNavigation(skipHome = false, isBrowsing = true, canGoBack = false)
        assertTrue(testee.onUserPressedBack())
        assertFalse(browserViewState().canSaveSite)
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
    fun whenUserInDuckPlayerThenCannotReportSite() {
        setupNavigation(skipHome = false, isBrowsing = true)
        whenever(mockDuckPlayer.isDuckPlayerUri(anyString())).thenReturn(true)
        assertFalse(browserViewState().canReportSite)
    }

    @Test
    fun whenUserInDuckPlayerThenCannotAllowList() {
        setupNavigation(skipHome = false, isBrowsing = true)
        whenever(mockDuckPlayer.isDuckPlayerUri(anyString())).thenReturn(true)
        assertFalse(browserViewState().canChangePrivacyProtection)
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
    fun whenUserBrowsingPressesBackAndForwardThenCanAddBookmark() {
        setupNavigation(skipHome = false, isBrowsing = true, canGoBack = false)
        testee.onUserPressedBack()
        testee.onUserPressedForward()
        assertTrue(browserViewState().canSaveSite)
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
    fun whenUserVisitsDomainWithPermanentLocationPermissionThenMessageIsShown() =
        runTest {
            val domain = "https://www.example.com/"

            whenever(mockSitePermissionsManager.hasSitePermanentPermission(domain, LocationPermissionRequest.RESOURCE_LOCATION_PERMISSION))
                .thenReturn(true)

            givenCurrentSite(domain)

            loadUrl("https://www.example.com", isBrowserShowing = true)

            assertCommandIssued<Command.ShowDomainHasPermissionMessage>()
        }

    @Test
    fun whenUserVisitsDomainWithoutPermanentLocationPermissionThenMessageIsNotShown() =
        runTest {
            val domain = "https://www.example.com/"

            whenever(mockSitePermissionsManager.hasSitePermanentPermission(domain, LocationPermissionRequest.RESOURCE_LOCATION_PERMISSION))
                .thenReturn(false)

            givenCurrentSite(domain)

            loadUrl("https://www.example.com", isBrowserShowing = true)

            assertCommandNotIssued<Command.ShowDomainHasPermissionMessage>()
        }

    @Test
    fun whenUserVisitsDomainWithoutLocationPermissionThenMessageIsNotShown() =
        runTest {
            val domain = "https://www.example.com"
            givenCurrentSite(domain)
            loadUrl("https://www.example.com", isBrowserShowing = true)

            assertCommandNotIssued<Command.ShowDomainHasPermissionMessage>()
        }

    @Test
    fun whenUserVisitsDomainAndLocationIsNotEnabledThenMessageIsNotShown() =
        runTest {
            val domain = "https://www.example.com"
            givenCurrentSite(domain)

            loadUrl("https://www.example.com", isBrowserShowing = true)

            assertCommandNotIssued<Command.ShowDomainHasPermissionMessage>()
        }

    @Test
    fun whenUserRefreshesASiteLocationMessageIsNotShownAgain() =
        runTest {
            val domain = "https://www.example.com/"

            whenever(mockSitePermissionsManager.hasSitePermanentPermission(domain, LocationPermissionRequest.RESOURCE_LOCATION_PERMISSION))
                .thenReturn(true)

            givenCurrentSite(domain)

            loadUrl("https://www.example.com", isBrowserShowing = true)
            loadUrl("https://www.example.com", isBrowserShowing = true)
            assertCommandIssuedTimes<Command.ShowDomainHasPermissionMessage>(1)
        }

    @Test
    fun whenPrefetchFaviconThenFetchFaviconForCurrentTab() =
        runTest {
            val url = "https://www.example.com/"
            givenCurrentSite(url)
            testee.prefetchFavicon(url)

            verify(mockFaviconManager).tryFetchFaviconForUrl("TAB_ID", url)
        }

    @Test
    fun whenPrefetchFaviconAndFaviconExistsThenUpdateTabFavicon() =
        runTest {
            val url = "https://www.example.com/"
            val file = File("test")
            givenCurrentSite(url)
            whenever(mockFaviconManager.tryFetchFaviconForUrl(any(), any())).thenReturn(file)

            testee.prefetchFavicon(url)

            verify(mockTabRepository).updateTabFavicon("TAB_ID", file.name)
        }

    @Test
    fun whenPrefetchFaviconAndFaviconDoesNotExistThenDoNotCallUpdateTabFavicon() =
        runTest {
            whenever(mockFaviconManager.tryFetchFaviconForUrl(any(), any())).thenReturn(null)

            testee.prefetchFavicon("url")

            verify(mockTabRepository, never()).updateTabFavicon(any(), any())
        }

    @Test
    fun whenIconReceivedThenStoreFavicon() =
        runTest {
            givenOneActiveTabSelected()
            val bitmap: Bitmap = Bitmap.createBitmap(1, 1, Bitmap.Config.RGB_565)

            testee.iconReceived("https://example.com", bitmap)

            verify(mockFaviconManager).storeFavicon("TAB_ID", FaviconSource.ImageFavicon(bitmap, "https://example.com"))
        }

    @Test
    fun whenIconReceivedIfCorrectlySavedThenUpdateTabFavicon() =
        runTest {
            givenOneActiveTabSelected()
            val bitmap: Bitmap = Bitmap.createBitmap(1, 1, Bitmap.Config.RGB_565)
            val file = File("test")
            whenever(mockFaviconManager.storeFavicon(any(), any())).thenReturn(file)

            testee.iconReceived("https://example.com", bitmap)

            verify(mockTabRepository).updateTabFavicon("TAB_ID", file.name)
        }

    @Test
    fun whenIconReceivedIfNotCorrectlySavedThenDoNotUpdateTabFavicon() =
        runTest {
            givenOneActiveTabSelected()
            val bitmap: Bitmap = Bitmap.createBitmap(1, 1, Bitmap.Config.RGB_565)
            whenever(mockFaviconManager.storeFavicon(any(), any())).thenReturn(null)

            testee.iconReceived("https://example.com", bitmap)

            verify(mockTabRepository, never()).updateTabFavicon(any(), any())
        }

    @Test
    fun whenIconReceivedFromPreviousUrlThenDontUpdateTabFavicon() =
        runTest {
            givenOneActiveTabSelected()
            val bitmap: Bitmap = Bitmap.createBitmap(1, 1, Bitmap.Config.RGB_565)
            val file = File("test")
            whenever(mockFaviconManager.storeFavicon(any(), any())).thenReturn(file)

            testee.iconReceived("https://notexample.com", bitmap)

            verify(mockTabRepository, never()).updateTabFavicon("TAB_ID", file.name)
        }

    @Test
    fun whenUrlIconReceivedThenStoreFavicon() =
        runTest {
            givenOneActiveTabSelected()

            testee.iconReceived("https://example.com", "https://example.com/favicon.png")

            verify(mockFaviconManager).storeFavicon("TAB_ID", FaviconSource.UrlFavicon("https://example.com/favicon.png", "https://example.com"))
        }

    @Test
    fun whenUrlIconReceivedIfCorrectlySavedThenUpdateTabFavicon() =
        runTest {
            givenOneActiveTabSelected()
            val file = File("test")
            whenever(mockFaviconManager.storeFavicon(any(), any())).thenReturn(file)

            testee.iconReceived("https://example.com", "https://example.com/favicon.png")

            verify(mockTabRepository).updateTabFavicon("TAB_ID", file.name)
        }

    @Test
    fun whenUrlIconReceivedIfNotCorrectlySavedThenDoNotUpdateTabFavicon() =
        runTest {
            givenOneActiveTabSelected()
            whenever(mockFaviconManager.storeFavicon(any(), any())).thenReturn(null)

            testee.iconReceived("https://example.com", "https://example.com/favicon.png")

            verify(mockTabRepository, never()).updateTabFavicon(any(), any())
        }

    @Test
    fun whenUrlIconReceivedFromPreviousUrlThenDontUpdateTabFavicon() =
        runTest {
            givenOneActiveTabSelected()
            val file = File("test")
            whenever(mockFaviconManager.storeFavicon(any(), any())).thenReturn(file)

            testee.iconReceived("https://notexample.com", "https://example.com/favicon.png")

            verify(mockFaviconManager, never()).storeFavicon(any(), any())
        }

    @Test
    fun whenBookmarkAddedThenPersistFavicon() =
        runTest {
            val url = exampleUrl
            val title = "A title"
            val bookmark =
                Bookmark(
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
    fun whenBookmarkAddedButUrlIsNullThenDoNotPersistFavicon() =
        runTest {
            loadUrl(null, "A title")

            testee.onBookmarkMenuClicked()

            verify(mockFaviconManager, never()).persistCachedFavicon(any(), any())
        }

    @Test
    fun whenFireproofWebsiteAddedThenPersistFavicon() =
        runTest {
            val url = exampleUrl
            loadUrl(url, isBrowserShowing = true)

            testee.onFireproofWebsiteMenuClicked()

            assertCommandIssued<Command.ShowFireproofWebSiteConfirmation> {
                verify(mockFaviconManager).persistCachedFavicon(any(), eq(this.fireproofWebsiteEntity.domain))
            }
        }

    @Test
    fun whenOnPinPageToHomeSelectedThenAddHomeShortcutCommandIssuedWithFavicon() =
        runTest {
            val url = exampleUrl
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
    fun whenOnPinPageToHomeSelectedAndFaviconDoesNotExistThenAddHomeShortcutCommandIssuedWithoutFavicon() =
        runTest {
            val url = exampleUrl
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
        givenCustomHeadersProviderReturnsGpcHeader()
        whenever(mockOmnibarConverter.convertQueryToUrl("foo", null)).thenReturn("foo.com")

        testee.onUserSubmittedQuery("foo")
        verify(mockCommandObserver, atLeastOnce()).onChanged(commandCaptor.capture())

        val command = commandCaptor.lastValue as Navigate
        assertEquals(GPC_HEADER_VALUE, command.headers[GPC_HEADER])
    }

    @Test
    fun whenUserSubmittedQueryIfGpcReturnsNoHeaderThenDoNotAddHeaderToUrl() {
        val url = "foo.com"
        givenCustomHeadersProviderReturnsNoHeaders()
        whenever(mockOmnibarConverter.convertQueryToUrl("foo", null)).thenReturn(url)

        testee.onUserSubmittedQuery("foo")
        verify(mockCommandObserver, atLeastOnce()).onChanged(commandCaptor.capture())

        val command = commandCaptor.lastValue as Navigate
        assertTrue(command.headers.isEmpty())
    }

    @Test
    fun whenOnDesktopSiteModeToggledIfGpcReturnsHeaderThenAddHeaderToUrl() {
        givenCustomHeadersProviderReturnsGpcHeader()
        loadUrl("http://m.example.com")
        setDesktopBrowsingMode(false)
        testee.onChangeBrowserModeClicked()
        verify(mockCommandObserver, atLeastOnce()).onChanged(commandCaptor.capture())

        val command = commandCaptor.lastValue as Navigate
        assertEquals(GPC_HEADER_VALUE, command.headers[GPC_HEADER])
    }

    @Test
    fun whenExternalAppLinkClickedIfGpcReturnsHeaderThenAddHeaderToUrl() {
        givenCustomHeadersProviderReturnsGpcHeader()
        val intentType = SpecialUrlDetector.UrlType.NonHttpAppLink("query", mock(), "fallback")

        testee.nonHttpAppLinkClicked(intentType)
        verify(mockCommandObserver, atLeastOnce()).onChanged(commandCaptor.capture())

        val command = commandCaptor.lastValue as Command.HandleNonHttpAppLink
        assertEquals(GPC_HEADER_VALUE, command.headers[GPC_HEADER])
    }

    @Test
    fun whenExternalAppLinkClickedIfGpcReturnsNoHeaderThenDoNotAddHeaderToUrl() {
        givenCustomHeadersProviderReturnsNoHeaders()
        val intentType = SpecialUrlDetector.UrlType.NonHttpAppLink("query", mock(), null)

        testee.nonHttpAppLinkClicked(intentType)
        verify(mockCommandObserver, atLeastOnce()).onChanged(commandCaptor.capture())

        val command = commandCaptor.lastValue as Command.HandleNonHttpAppLink
        assertTrue(command.headers.isEmpty())
    }

    @Test
    fun whenUserSubmittedQueryIfAndroidFeaturesReturnsHeaderThenAddHeaderToUrl() {
        givenCustomHeadersProviderReturnsAndroidFeaturesHeader()
        whenever(mockOmnibarConverter.convertQueryToUrl("foo", null)).thenReturn("foo.com")

        testee.onUserSubmittedQuery("foo")
        verify(mockCommandObserver, atLeastOnce()).onChanged(commandCaptor.capture())

        val command = commandCaptor.lastValue as Navigate
        assertEquals("TEST_VALUE", command.headers[X_DUCKDUCKGO_ANDROID_HEADER])
    }

    @Test
    fun whenUserSubmittedQueryIfAndroidFeaturesReturnsNoHeaderThenDoNotAddHeaderToUrl() {
        givenCustomHeadersProviderReturnsNoHeaders()
        whenever(mockOmnibarConverter.convertQueryToUrl("foo", null)).thenReturn("foo.com")

        testee.onUserSubmittedQuery("foo")
        verify(mockCommandObserver, atLeastOnce()).onChanged(commandCaptor.capture())

        val command = commandCaptor.lastValue as Navigate
        assertTrue(command.headers.isEmpty())
    }

    @Test
    fun whenFirePulsingAnimationStartsThenItStopsAfterMoreThanOneHour() =
        runTest {
            givenFireButtonPulsing()
            val observer = ValueCaptorObserver<BrowserViewState>(false)
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
        testee.progressChanged(100, WebViewNavigationState(mockStack, 100))

        assertCommandIssued<Command.RefreshUserAgent>()
    }

    @Test
    fun whenRequestFileDownloadAndUrlIsBlobAndBlobDownloadWebViewFeatureIsNotEnabledThenConvertBlobToDataUriCommandSent() {
        val blobUrl = "blob:https://example.com/283nasdho23jkasdAjd"
        val mime = "application/plain"
        val enabled = false

        testee.requestFileDownload(
            webView = mockWebView,
            url = blobUrl,
            contentDisposition = null,
            mimeType = mime,
            requestUserConfirmation = true,
            isBlobDownloadWebViewFeatureEnabled = enabled,
        )

        assertCommandIssued<Command.ConvertBlobToDataUri> {
            assertEquals(blobUrl, url)
            assertEquals(mime, mimeType)
        }
    }

    @Test
    fun whenRequestFileDownloadAndUrlIsBlobAndBlobDownloadWebViewFeatureIsEnabledThenConvertBlobToDataUriCommandNotSent() {
        val blobUrl = "blob:https://example.com/283nasdho23jkasdAjd"
        val mime = "application/plain"
        val enabled = true

        testee.requestFileDownload(
            webView = mockWebView,
            url = blobUrl,
            contentDisposition = null,
            mimeType = mime,
            requestUserConfirmation = true,
            isBlobDownloadWebViewFeatureEnabled = enabled,
        )

        assertCommandNotIssued<Command.ConvertBlobToDataUri>()
    }

    @Test
    fun whenRequestFileDownloadAndUrlIsNotBlobThenRequestFileDownloadCommandSent() {
        val normalUrl = "https://example.com/283nasdho23jkasdAjd"
        val mime = "application/plain"

        testee.requestFileDownload(
            webView = mockWebView,
            url = normalUrl,
            contentDisposition = null,
            mimeType = mime,
            requestUserConfirmation = true,
            isBlobDownloadWebViewFeatureEnabled = false,
        )

        assertCommandIssued<Command.RequestFileDownload> {
            assertEquals(normalUrl, url)
            assertEquals(mime, mimeType)
            assertNull(contentDisposition)
            assertTrue(requestUserConfirmation)
        }
    }

    @Test
    fun whenChildrenTabClosedIfViewModelIsParentThenChildTabClosedCommandSent() =
        runTest {
            givenOneActiveTabSelected()

            childClosedTabsSharedFlow.emit("TAB_ID")

            assertCommandIssued<Command.ChildTabClosed>()
        }

    @Test
    fun whenChildrenTabClosedIfViewModelIsNotParentThenChildTabClosedCommandNotSent() =
        runTest {
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
            mapOf(PixelParameter.COHORT to "cohort", PixelParameter.LAST_USED_DAY to "2021-01-01"),
        )
    }

    @Test
    fun whenEmailIsSignedOutThenIsEmailSignedInReturnsFalse() =
        runTest {
            emailStateFlow.emit(false)

            assertFalse(browserViewState().isEmailSignedIn)
        }

    @Test
    fun whenEmailIsSignedInThenIsEmailSignedInReturnsTrue() =
        runTest {
            emailStateFlow.emit(true)

            assertTrue(browserViewState().isEmailSignedIn)
        }

    @Test
    fun whenEmailSignOutEventThenEmailSignEventCommandSent() =
        runTest {
            emailStateFlow.emit(true)
            emailStateFlow.emit(false)

            assertCommandIssuedTimes<Command.EmailSignEvent>(2)
        }

    @Test
    fun whenEmailIsSignedInThenEmailSignEventCommandSent() =
        runTest {
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
        val urlType = SpecialUrlDetector.UrlType.AppLink(uriString = exampleUrl)
        testee.handleAppLink(urlType, isForMainFrame = true)
        whenever(mockAppLinksHandler.isUserQuery()).thenReturn(false)
        whenever(ctaViewModelMockSettingsStore.showAppLinksPrompt).thenReturn(true)
        verify(mockAppLinksHandler).handleAppLink(eq(true), eq(exampleUrl), eq(false), eq(true), appLinkCaptor.capture())
        appLinkCaptor.lastValue.invoke()
        assertCommandIssued<Command.ShowAppLinkPrompt>()
        verify(mockAppLinksHandler).setUserQueryState(false)
    }

    @Test
    fun whenHandleAppLinkCalledAndIsUserQueryThenShowAppLinkPromptAndUserQueryStateSetToFalse() {
        val urlType = SpecialUrlDetector.UrlType.AppLink(uriString = exampleUrl)
        testee.handleAppLink(urlType, isForMainFrame = true)
        whenever(mockAppLinksHandler.isUserQuery()).thenReturn(true)
        whenever(ctaViewModelMockSettingsStore.showAppLinksPrompt).thenReturn(false)
        verify(mockAppLinksHandler).handleAppLink(eq(true), eq(exampleUrl), eq(false), eq(true), appLinkCaptor.capture())
        appLinkCaptor.lastValue.invoke()
        assertCommandIssued<Command.ShowAppLinkPrompt>()
        verify(mockAppLinksHandler).setUserQueryState(false)
    }

    @Test
    fun whenHandleAppLinkCalledAndIsNotUserQueryAndShowAppLinksPromptIsFalseThenOpenAppLink() {
        val urlType = SpecialUrlDetector.UrlType.AppLink(uriString = exampleUrl)
        testee.handleAppLink(urlType, isForMainFrame = true)
        whenever(mockAppLinksHandler.isUserQuery()).thenReturn(false)
        whenever(ctaViewModelMockSettingsStore.showAppLinksPrompt).thenReturn(false)
        verify(mockAppLinksHandler).handleAppLink(eq(true), eq(exampleUrl), eq(false), eq(true), appLinkCaptor.capture())
        appLinkCaptor.lastValue.invoke()
        assertCommandIssued<Command.OpenAppLink>()
    }

    @Test
    fun whenHandleAppLinkCalledAndCustomTabIsTrueThenOpenAppLink() {
        val urlType = SpecialUrlDetector.UrlType.AppLink(uriString = exampleUrl)
        testee.setIsCustomTab(true)
        testee.handleAppLink(urlType, isForMainFrame = true)
        whenever(mockAppLinksHandler.isUserQuery()).thenReturn(false)
        whenever(ctaViewModelMockSettingsStore.showAppLinksPrompt).thenReturn(false)
        verify(mockAppLinksHandler).handleAppLink(eq(true), eq(exampleUrl), eq(false), eq(true), appLinkCaptor.capture())
        appLinkCaptor.lastValue.invoke()
        assertCommandIssued<Command.OpenAppLink>()
    }

    @Test
    fun whenHandleNonHttpAppLinkCalledThenHandleNonHttpAppLink() {
        val urlType = SpecialUrlDetector.UrlType.NonHttpAppLink("market://details?id=com.example", Intent(), exampleUrl)
        assertTrue(testee.handleNonHttpAppLink(urlType))
        assertCommandIssued<Command.HandleNonHttpAppLink>()
    }

    @Test
    fun whenHandleNonHttpAppLinkCalledWithUnpermittedIntentThenDoNotHandleNonHttpAppLink() {
        whenever(nonHttpAppLinkChecker.isPermitted(any())).thenReturn(false)
        val urlType = SpecialUrlDetector.UrlType.NonHttpAppLink("market://details?id=com.example", Intent(), exampleUrl)
        assertTrue(testee.handleNonHttpAppLink(urlType))
        assertCommandNotIssued<Command.HandleNonHttpAppLink>()
    }

    @Test
    fun whenUserSubmittedQueryIsAppLinkAndShouldShowPromptThenOpenAppLinkInBrowserAndSetPreviousUrlToNull() {
        whenever(mockOmnibarConverter.convertQueryToUrl("foo", null)).thenReturn("foo.com")
        whenever(mockSpecialUrlDetector.determineType(anyString())).thenReturn(SpecialUrlDetector.UrlType.AppLink(uriString = "http://foo.com"))
        whenever(ctaViewModelMockSettingsStore.showAppLinksPrompt).thenReturn(true)
        testee.onUserSubmittedQuery("foo")
        verify(mockAppLinksHandler).updatePreviousUrl(null)
        assertCommandIssued<Navigate>()
    }

    @Test
    fun whenUserSubmittedQueryIsAppLinkAndShouldNotShowPromptThenOpenAppLinkInBrowserAndSetPreviousUrl() {
        whenever(mockOmnibarConverter.convertQueryToUrl("foo", null)).thenReturn("foo.com")
        whenever(mockSpecialUrlDetector.determineType(anyString())).thenReturn(SpecialUrlDetector.UrlType.AppLink(uriString = "http://foo.com"))
        whenever(ctaViewModelMockSettingsStore.showAppLinksPrompt).thenReturn(false)
        testee.onUserSubmittedQuery("foo")
        verify(mockAppLinksHandler).updatePreviousUrl("foo.com")
        assertCommandIssued<Navigate>()
    }

    @Test
    fun whenUserSelectsToPrintPageAndCountryFromLetterFormatDefinedSetThenPrintLinkCommandSentWithLetter() {
        whenever(mockDeviceInfo.country).thenReturn("US")
        loadUrl("foo.com")
        testee.onPrintSelected()
        val command = captureCommands().lastValue as Command.PrintLink
        assertEquals("foo.com", command.url)
        assertEquals(PrintAttributes.MediaSize.NA_LETTER, command.mediaSize)
    }

    @Test
    fun whenUserSelectsToPrintPageAndCountryNotFromLetterFormatDefinedSetThenPrintLinkCommandSentWithA4() {
        whenever(mockDeviceInfo.country).thenReturn("FR")
        loadUrl("foo.com")
        testee.onPrintSelected()
        val command = captureCommands().lastValue as Command.PrintLink
        assertEquals("foo.com", command.url)
        assertEquals(PrintAttributes.MediaSize.ISO_A4, command.mediaSize)
    }

    @Test
    fun whenUserSelectsToPrintPageAndCountryIsEmptyThenPrintLinkCommandSentWithA4() {
        whenever(mockDeviceInfo.country).thenReturn("")
        loadUrl("foo.com")
        testee.onPrintSelected()
        val command = captureCommands().lastValue as Command.PrintLink
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
    fun whenSubmittedQueryAndNavigationStateIsNullAndNeverPreviouslyLoadedSiteThenResetHistoryCommandNotSent() {
        whenever(mockOmnibarConverter.convertQueryToUrl("nytimes.com", null)).thenReturn("nytimes.com")
        testee.onUserSubmittedQuery("nytimes.com")
        assertCommandNotIssued<Command.ResetHistory>()
    }

    @Test
    fun whenSubmittedQueryAndNavigationStateIsNullAndPreviouslyLoadedSiteThenResetHistoryCommandSent() {
        whenever(mockOmnibarConverter.convertQueryToUrl("nytimes.com", null)).thenReturn("nytimes.com")
        setupNavigation(isBrowsing = true)
        testee.onUserPressedBack()
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
        assertFalse(loadingViewState().trackersAnimationEnabled)
    }

    @Test
    fun whenEditBookmarkRequestedThenRepositoryIsNotUpdated() =
        runTest {
            val url = "http://www.example.com"
            val bookmark =
                Bookmark(
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
    fun whenEditBookmarkRequestedThenEditBookmarkPressedPixelIsFired() =
        runTest {
            val bookmark =
                Bookmark(
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
    fun whenEditBookmarkRequestedThenEditDialogIsShownWithCorrectUrlAndTitle() =
        runTest {
            val bookmark =
                Bookmark(
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
    fun whenRemoveFavoriteRequestedThenDaoInsertIsNotCalled() =
        runTest {
            val favoriteSite =
                Favorite(id = UUID.randomUUID().toString(), title = "", url = "www.example.com", position = 0, lastModified = "timestamp")
            favoriteListFlow.send(listOf(favoriteSite))
            loadUrl("www.example.com", isBrowserShowing = true)
            testee.onFavoriteMenuClicked()
            verify(mockSavedSitesRepository, never()).insert(any<Favorite>())
        }

    @Test
    fun whenRemoveFavoriteRequestedThenRemoveFavoritePressedPixelIsFired() =
        runTest {
            val favoriteSite =
                Favorite(id = UUID.randomUUID().toString(), title = "", url = "www.example.com", position = 0, lastModified = "timestamp")
            whenever(mockSavedSitesRepository.getFavorite("www.example.com")).thenReturn(favoriteSite)
            favoriteListFlow.send(listOf(favoriteSite))
            loadUrl("www.example.com", isBrowserShowing = true)
            testee.onFavoriteMenuClicked()
            verify(mockPixel).fire(
                AppPixelName.MENU_ACTION_REMOVE_FAVORITE_PRESSED.pixelName,
            )
        }

    @Test
    fun whenRemoveFavoriteRequestedThenViewStateUpdated() =
        runTest {
            val favoriteSite =
                Favorite(id = UUID.randomUUID().toString(), title = "", url = "www.example.com", position = 0, lastModified = "timestamp")
            whenever(mockSavedSitesRepository.getFavorite("www.example.com")).thenReturn(favoriteSite)
            favoriteListFlow.send(listOf(favoriteSite))
            loadUrl("www.example.com", isBrowserShowing = true)
            testee.onFavoriteMenuClicked()

            assertTrue(browserViewState().favorite == null)
        }

    @Test
    fun whenRemoveFavoriteRequestedThenDeleteConfirmationDialogIsShownWithCorrectUrlAndTitle() =
        runTest {
            val favoriteSite =
                Favorite(
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
    fun whenRemoveFavoriteUndoThenViewStateUpdated() =
        runTest {
            val favoriteSite =
                Favorite(id = UUID.randomUUID().toString(), title = "", url = "www.example.com", position = 0, lastModified = "timestamp")
            val quickAccessFavorites = listOf(QuickAccessFavorite(favoriteSite))

            whenever(mockSavedSitesRepository.getFavorite("www.example.com")).thenReturn(favoriteSite)
            favoriteListFlow.send(listOf(favoriteSite))
            loadUrl("www.example.com", isBrowserShowing = true)
            testee.onFavoriteMenuClicked()

            assertTrue(browserViewState().favorite == null)
            assertTrue(autoCompleteViewState().favorites.isEmpty())

            testee.undoDelete(favoriteSite)

            assertTrue(browserViewState().favorite == favoriteSite)
            assertTrue(autoCompleteViewState().favorites == quickAccessFavorites)
        }

    @Test
    fun whenDeleteBookmarkUndoThenViewStateUpdated() =
        runTest {
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
    fun whenDeleteFavouriteUndoThenViewStateUpdated() =
        runTest {
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
    fun whenDownloadIsCalledThenDownloadRequestedForUrl() =
        runTest {
            val pendingFileDownload = buildPendingDownload(url = "http://www.example.com/download.pdf", contentDisposition = null, mimeType = null)

            testee.download(pendingFileDownload)

            verify(mockFileDownloader).enqueueDownload(pendingFileDownload)
        }

    private fun buildPendingDownload(
        url: String,
        contentDisposition: String?,
        mimeType: String?,
    ): PendingFileDownload =
        PendingFileDownload(
            url = url,
            contentDisposition = contentDisposition,
            mimeType = mimeType,
            subfolder = "folder",
        )

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
        whenever(mockAmpLinks.processDestinationUrl(anyString(), anyOrNull())).thenReturn(exampleUrl)
        testee.onUrlExtracted("http://foo.com", exampleUrl)
        verify(mockCommandObserver, atLeastOnce()).onChanged(commandCaptor.capture())
        val issuedCommand = commandCaptor.allValues.find { it is LoadExtractedUrl }
        assertEquals(exampleUrl, (issuedCommand as LoadExtractedUrl).extractedUrl)
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
    fun whenConfigurationChangesThenForceRenderingMenu() {
        val oldForceRenderingTicker = browserViewState().forceRenderingTicker

        testee.onConfigurationChanged()

        assertTrue(oldForceRenderingTicker != browserViewState().forceRenderingTicker)
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
    fun whenReturnNoCredentialsWithPageThenEmitCancelIncomingAutofillRequestCommand() =
        runTest {
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
    fun whenOnPageChangeThenAutoconsentReset() {
        updateUrl("http://www.example.com/", "http://twitter.com/explore", true)
        testee.onAutoconsentResultReceived(consentManaged = true, optOutFailed = true, selfTestFailed = true, isCosmetic = true)
        assertTrue(testee.siteLiveData.value?.consentManaged!!)
        assertTrue(testee.siteLiveData.value?.consentOptOutFailed!!)
        assertTrue(testee.siteLiveData.value?.consentSelfTestFailed!!)
        assertTrue(testee.siteLiveData.value?.consentCosmeticHide!!)
        testee.onWebViewRefreshed()
        assertFalse(testee.siteLiveData.value?.consentManaged!!)
        assertFalse(testee.siteLiveData.value?.consentOptOutFailed!!)
        assertFalse(testee.siteLiveData.value?.consentSelfTestFailed!!)
        assertFalse(testee.siteLiveData.value?.consentCosmeticHide!!)
    }

    @Test
    fun whenNotEditingUrlBarAndNotCancelledThenCanAutomaticallyShowAutofillPrompt() {
        assertTrue(testee.canAutofillSelectCredentialsDialogCanAutomaticallyShow())
    }

    @Test
    fun whenNotEditingUrlBarAndCancelledThenCannotAutomaticallyShowAutofillPrompt() {
        testee.cancelPendingAutofillRequestToChooseCredentials()
        assertFalse(testee.canAutofillSelectCredentialsDialogCanAutomaticallyShow())
    }

    @Test
    fun whenEditingUrlBarAndCancelledThenCannotAutomaticallyShowAutofillPrompt() {
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
    fun whenShowingUserCredentialsSavedConfirmationAndCanAccessCredentialManagementScreenThenShouldShowLinkToViewCredential() =
        runTest {
            autofillCapabilityChecker.enabled = true
            testee.onShowUserCredentialsSaved(aCredential())
            assertCommandIssued<Command.ShowUserCredentialSavedOrUpdatedConfirmation> {
                assertTrue(this.includeShortcutToViewCredential)
            }
        }

    @Test
    fun whenShowingUserCredentialsSavedConfirmationAndCannotAccessCredentialManagementScreenThenShouldNotShowLinkToViewCredential() =
        runTest {
            autofillCapabilityChecker.enabled = false
            testee.onShowUserCredentialsSaved(aCredential())
            assertCommandIssued<Command.ShowUserCredentialSavedOrUpdatedConfirmation> {
                assertFalse(this.includeShortcutToViewCredential)
            }
        }

    @Test
    fun whenShowingUserCredentialsUpdatedConfirmationAndCanAccessCredentialManagementScreenThenShouldShowLinkToViewCredential() =
        runTest {
            autofillCapabilityChecker.enabled = true
            testee.onShowUserCredentialsUpdated(aCredential())
            assertCommandIssued<Command.ShowUserCredentialSavedOrUpdatedConfirmation> {
                assertTrue(this.includeShortcutToViewCredential)
            }
        }

    @Test
    fun whenShowingUserCredentialsUpdatedConfirmationAndCannotAccessCredentialManagementScreenThenShouldNotShowLinkToViewCredential() =
        runTest {
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
    fun whenUrlIsUpdateButSameHostThenForceUpdateShouldBeFalse() =
        runTest {
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
    fun whenOnSitePermissionRequestedThenSendCommand() =
        runTest {
            val request: PermissionRequest = mock()
            val sitePermissions =
                SitePermissions(
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
        val testUrls =
            listOf(
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

        val expectedUrls =
            listOf(
                "https://example.com",
                exampleUrl,
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
        val testUrls =
            listOf(
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
    fun whenOnShowFileChooserWithImageWildcardedTypeOnlyAndCaptureEnabledThenShowImageCameraCommandSent() {
        val params = buildFileChooserParams(acceptTypes = arrayOf("image/*"), captureEnabled = true)

        testee.showFileChooser(mockFileChooserCallback, params)

        assertCommandIssued<Command.ShowImageCamera>()
    }

    @Test
    fun whenOnShowFileChooserWithVideoWildcardedTypeOnlyAndCaptureEnabledThenShowVideoCameraCommandSent() {
        val params = buildFileChooserParams(acceptTypes = arrayOf("video/*"), captureEnabled = true)

        testee.showFileChooser(mockFileChooserCallback, params)

        assertCommandIssued<Command.ShowVideoCamera>()
    }

    @Test
    fun whenOnShowFileChooserWithImageWildcardedTypeOnlyAndCaptureEnabledButCameraHardwareUnavailableThenShowFileChooserCommandSent() {
        whenever(cameraHardwareChecker.hasCameraHardware()).thenReturn(false)
        val params = buildFileChooserParams(acceptTypes = arrayOf("image/*"), captureEnabled = true)

        testee.showFileChooser(mockFileChooserCallback, params)

        assertCommandIssued<Command.ShowFileChooser>()
    }

    @Test
    fun whenOnShowFileChooserWithAudioWildcardedTypeOnlyAndCaptureEnabledThenShowSoundRecorderCommandSent() {
        val params = buildFileChooserParams(acceptTypes = arrayOf("audio/*"), captureEnabled = true)

        testee.showFileChooser(mockFileChooserCallback, params)

        assertCommandIssued<Command.ShowSoundRecorder>()
    }

    @Test
    fun whenWebViewRefreshedThenBrowserErrorStateIsOmitted() {
        testee.onWebViewRefreshed()

        assertEquals(OMITTED, browserViewState().browserError)
    }

    @Test
    fun whenWebViewRefreshedWithErrorThenBrowserErrorStateIsLoading() {
        testee.onReceivedError(BAD_URL, exampleUrl)
        testee.onWebViewRefreshed()

        assertEquals(LOADING, browserViewState().browserError)
    }

    @Test
    fun whenResetBrowserErrorThenBrowserErrorStateIsLoading() {
        testee.onReceivedError(BAD_URL, exampleUrl)
        assertEquals(BAD_URL, browserViewState().browserError)
        testee.resetBrowserError()
        assertEquals(OMITTED, browserViewState().browserError)
    }

    @Test
    fun whenProcessJsCallbackMessageWebShareSendCommand() =
        runTest {
            val url = "someUrl"
            loadUrl(url)
            testee.processJsCallbackMessage(
                "myFeature",
                "webShare",
                "myId",
                JSONObject("""{ "my":"object"}"""),
                false,
                { "someUrl" },
            )
            assertCommandIssued<Command.WebShareRequest> {
                assertEquals("object", this.data.params.getString("my"))
                assertEquals("myFeature", this.data.featureName)
                assertEquals("webShare", this.data.method)
                assertEquals("myId", this.data.id)
            }
        }

    @Test
    fun whenProcessJsCallbackMessagePermissionsQuerySendCommand() =
        runTest {
            val url = "someUrl"
            loadUrl(url)
            whenever(mockSitePermissionsManager.getPermissionsQueryResponse(eq(url), any(), any())).thenReturn(SitePermissionQueryResponse.Granted)
            testee.processJsCallbackMessage(
                "myFeature",
                "permissionsQuery",
                "myId",
                JSONObject("""{ "name":"somePermission"}"""),
                false,
            ) { "someUrl" }
            assertCommandIssued<Command.SendResponseToJs> {
                assertEquals("granted", this.data.params.getString("state"))
                assertEquals("myFeature", this.data.featureName)
                assertEquals("permissionsQuery", this.data.method)
                assertEquals("myId", this.data.id)
            }
        }

    @Test
    fun whenProcessJsCallbackMessageScreenLockNotEnabledDoNotSendCommand() =
        runTest {
            whenever(mockEnabledToggle.isEnabled()).thenReturn(false)
            testee.processJsCallbackMessage(
                "myFeature",
                "screenLock",
                "myId",
                JSONObject("""{ "my":"object"}"""),
                false,
            ) { "someUrl" }
            assertCommandNotIssued<Command.ScreenLock>()
        }

    @Test
    fun whenProcessJsCallbackMessageScreenLockEnabledSendCommand() =
        runTest {
            fakeAndroidConfigBrowserFeature.screenLock().setRawStoredState(State(enable = true))
            testee.processJsCallbackMessage(
                "myFeature",
                "screenLock",
                "myId",
                JSONObject("""{ "my":"object"}"""),
                false,
                { "someUrl" },
            )
            assertCommandIssued<Command.ScreenLock> {
                assertEquals("object", this.data.params.getString("my"))
                assertEquals("myFeature", this.data.featureName)
                assertEquals("screenLock", this.data.method)
                assertEquals("myId", this.data.id)
            }
        }

    @Test
    fun whenProcessJsCallbackMessageScreenUnlockNotEnabledDoNotSendCommand() =
        runTest {
            whenever(mockEnabledToggle.isEnabled()).thenReturn(false)
            testee.processJsCallbackMessage(
                "myFeature",
                "screenUnlock",
                "myId",
                JSONObject("""{ "my":"object"}"""),
                false,
                { "someUrl" },
            )
            assertCommandNotIssued<Command.ScreenUnlock>()
        }

    @Test
    fun whenProcessJsCallbackMessageScreenUnlockEnabledSendCommand() =
        runTest {
            fakeAndroidConfigBrowserFeature.screenLock().setRawStoredState(State(enable = true))
            testee.processJsCallbackMessage(
                "myFeature",
                "screenUnlock",
                "myId",
                JSONObject("""{ "my":"object"}"""),
                false,
                { "someUrl" },
            )
            assertCommandIssued<Command.ScreenUnlock>()
        }

    @Test
    fun whenProcessJsCallbackMessageGetUserPreferencesFromOverlayThenSendCommand() =
        runTest {
            whenever(mockEnabledToggle.isEnabled()).thenReturn(true)
            whenever(mockDuckPlayer.getUserPreferences()).thenReturn(UserPreferences(overlayInteracted = true, privatePlayerMode = AlwaysAsk))
            testee.processJsCallbackMessage(
                DUCK_PLAYER_FEATURE_NAME,
                "getUserValues",
                "id",
                data = null,
                false,
                { "someUrl" },
            )
            assertCommandIssued<Command.SendResponseToJs>()
        }

    @Test
    fun whenProcessJsCallbackMessageSetUserPreferencesDisabledFromDuckPlayerOverlayThenSendCommand() =
        runTest {
            whenever(mockEnabledToggle.isEnabled()).thenReturn(true)
            whenever(mockDuckPlayer.getUserPreferences()).thenReturn(UserPreferences(overlayInteracted = true, privatePlayerMode = AlwaysAsk))
            testee.processJsCallbackMessage(
                DUCK_PLAYER_FEATURE_NAME,
                "setUserValues",
                "id",
                JSONObject("""{ overlayInteracted: "true", privatePlayerMode: {disabled: {} }}"""),
                false,
            ) { "someUrl" }
            assertCommandIssued<Command.SendResponseToJs>()
            verify(mockDuckPlayer).setUserPreferences(any(), any())
            verify(mockPixel).fire(DUCK_PLAYER_SETTING_NEVER_OVERLAY_YOUTUBE)
        }

    @Test
    fun whenProcessJsCallbackMessageSetUserPreferencesEnabledFromDuckPlayerOverlayThenSendCommand() =
        runTest {
            whenever(mockEnabledToggle.isEnabled()).thenReturn(true)
            whenever(mockDuckPlayer.getUserPreferences()).thenReturn(UserPreferences(overlayInteracted = true, privatePlayerMode = AlwaysAsk))
            testee.processJsCallbackMessage(
                DUCK_PLAYER_FEATURE_NAME,
                "setUserValues",
                "id",
                JSONObject("""{ overlayInteracted: "true", privatePlayerMode: {enabled: {} }}"""),
                false,
                { "someUrl" },
            )
            assertCommandIssued<Command.SendResponseToJs>()
            verify(mockDuckPlayer).setUserPreferences(any(), any())
            verify(mockPixel).fire(DUCK_PLAYER_SETTING_ALWAYS_OVERLAY_YOUTUBE)
        }

    @Test
    fun whenProcessJsCallbackMessageSetUserPreferencesFromDuckPlayerPageThenSendCommand() =
        runTest {
            whenever(mockEnabledToggle.isEnabled()).thenReturn(true)
            whenever(mockDuckPlayer.getUserPreferences()).thenReturn(UserPreferences(overlayInteracted = true, privatePlayerMode = AlwaysAsk))
            testee.processJsCallbackMessage(
                DUCK_PLAYER_PAGE_FEATURE_NAME,
                "setUserValues",
                "id",
                JSONObject("""{ overlayInteracted: "true", privatePlayerMode: {enabled: {} }}"""),
                false,
            ) { "someUrl" }
            assertCommandIssued<Command.SendResponseToDuckPlayer>()
            verify(mockDuckPlayer).setUserPreferences(true, "enabled")
            verify(mockPixel).fire(DUCK_PLAYER_SETTING_ALWAYS_DUCK_PLAYER)
        }

    @Test
    fun whenProcessJsCallbackMessageSendDuckPlayerPixelThenSendPixel() =
        runTest {
            whenever(mockEnabledToggle.isEnabled()).thenReturn(true)
            whenever(mockDuckPlayer.getUserPreferences()).thenReturn(UserPreferences(overlayInteracted = true, privatePlayerMode = AlwaysAsk))
            testee.processJsCallbackMessage(
                DUCK_PLAYER_FEATURE_NAME,
                "sendDuckPlayerPixel",
                "id",
                JSONObject("""{ pixelName: "pixel", params: {}}"""),
                false,
            ) { "someUrl" }
            verify(mockDuckPlayer).sendDuckPlayerPixel("pixel", mapOf())
        }

    @Test
    fun whenProcessJsCallbackMessageOpenDuckPlayerWithUrlAndOpenInNewTabOffThenNavigate() =
        runTest {
            whenever(mockEnabledToggle.isEnabled()).thenReturn(true)
            whenever(mockDuckPlayer.getUserPreferences()).thenReturn(UserPreferences(overlayInteracted = true, privatePlayerMode = AlwaysAsk))
            whenever(mockDuckPlayer.shouldOpenDuckPlayerInNewTab()).thenReturn(Off)
            testee.processJsCallbackMessage(
                DUCK_PLAYER_FEATURE_NAME,
                "openDuckPlayer",
                "id",
                JSONObject("""{ href: "duck://player/1234" }"""),
                false,
                { "someUrl" },
            )
            assertCommandIssued<Navigate>()
        }

    @Test
    fun whenProcessJsCallbackMessageOpenDuckPlayerWithUrlAndOpenInNewTabThenSetOrigin() =
        runTest {
            whenever(mockEnabledToggle.isEnabled()).thenReturn(true)
            whenever(mockDuckPlayer.getUserPreferences()).thenReturn(UserPreferences(overlayInteracted = true, privatePlayerMode = AlwaysAsk))
            whenever(mockDuckPlayer.shouldOpenDuckPlayerInNewTab()).thenReturn(Off)
            testee.processJsCallbackMessage(
                DUCK_PLAYER_FEATURE_NAME,
                "openDuckPlayer",
                "id",
                JSONObject("""{ href: "duck://player/1234" }"""),
                false,
                { "someUrl" },
            )
            verify(mockDuckPlayer).setDuckPlayerOrigin(OVERLAY)
        }

    @Test
    fun whenProcessJsCallbackMessageOpenDuckPlayerWithDuckPlayerAlwaysEnabledUrlAndOpenInNewTabThenSetOrigin() =
        runTest {
            whenever(mockEnabledToggle.isEnabled()).thenReturn(true)
            whenever(mockDuckPlayer.getUserPreferences()).thenReturn(UserPreferences(overlayInteracted = true, privatePlayerMode = Enabled))
            whenever(mockDuckPlayer.shouldOpenDuckPlayerInNewTab()).thenReturn(Off)
            testee.processJsCallbackMessage(
                DUCK_PLAYER_FEATURE_NAME,
                "openDuckPlayer",
                "id",
                JSONObject("""{ href: "duck://player/1234" }"""),
                false,
                { "someUrl" },
            )
            verify(mockDuckPlayer).setDuckPlayerOrigin(AUTO)
        }

    @Test
    fun whenProcessJsCallbackMessageOpenDuckPlayerWithUrlAndOpenInNewTabUnavailableThenNavigate() =
        runTest {
            whenever(mockEnabledToggle.isEnabled()).thenReturn(true)
            whenever(mockDuckPlayer.getUserPreferences()).thenReturn(UserPreferences(overlayInteracted = true, privatePlayerMode = AlwaysAsk))
            whenever(mockDuckPlayer.shouldOpenDuckPlayerInNewTab()).thenReturn(Unavailable)
            testee.processJsCallbackMessage(
                DUCK_PLAYER_FEATURE_NAME,
                "openDuckPlayer",
                "id",
                JSONObject("""{ href: "duck://player/1234" }"""),
                false,
                { "someUrl" },
            )
            assertCommandIssued<Navigate>()
        }

    @Test
    fun whenProcessJsCallbackMessageOpenDuckPlayerWithUrlAndOpenInNewTabOnThenOpenInNewTab() =
        runTest {
            whenever(mockEnabledToggle.isEnabled()).thenReturn(true)
            whenever(mockDuckPlayer.getUserPreferences()).thenReturn(UserPreferences(overlayInteracted = true, privatePlayerMode = AlwaysAsk))
            whenever(mockDuckPlayer.shouldOpenDuckPlayerInNewTab()).thenReturn(On)
            testee.processJsCallbackMessage(
                DUCK_PLAYER_FEATURE_NAME,
                "openDuckPlayer",
                "id",
                JSONObject("""{ href: "duck://player/1234" }"""),
                false,
                { "someUrl" },
            )
            assertCommandIssued<Command.OpenInNewTab>()
        }

    @Test
    fun whenProcessJsCallbackMessageOpenDuckPlayerWithUrlAndOpenInNewTabOnWithCustomTabThenNavigate() =
        runTest {
            whenever(mockEnabledToggle.isEnabled()).thenReturn(true)
            whenever(mockDuckPlayer.getUserPreferences()).thenReturn(UserPreferences(overlayInteracted = true, privatePlayerMode = AlwaysAsk))
            whenever(mockDuckPlayer.shouldOpenDuckPlayerInNewTab()).thenReturn(On)
            testee.processJsCallbackMessage(
                DUCK_PLAYER_FEATURE_NAME,
                "openDuckPlayer",
                "id",
                JSONObject("""{ href: "duck://player/1234" }"""),
                true,
            ) { "someUrl" }
            assertCommandIssued<Navigate>()
        }

    @Test
    fun whenProcessJsCallbackMessageOpenDuckPlayerWithoutUrlThenDoNotNavigate() =
        runTest {
            whenever(mockEnabledToggle.isEnabled()).thenReturn(true)
            whenever(mockDuckPlayer.shouldOpenDuckPlayerInNewTab()).thenReturn(On)
            whenever(mockDuckPlayer.getUserPreferences()).thenReturn(UserPreferences(overlayInteracted = true, privatePlayerMode = AlwaysAsk))
            testee.processJsCallbackMessage(
                DUCK_PLAYER_FEATURE_NAME,
                "openDuckPlayer",
                "id",
                null,
                false,
            ) { "someUrl" }
            assertCommandNotIssued<Navigate>()
        }

    @Test
    fun whenJsCallbackMessageInitialSetupFromOverlayThenSendResponseToJs() =
        runTest {
            whenever(mockEnabledToggle.isEnabled()).thenReturn(true)
            whenever(mockDuckPlayer.getUserPreferences()).thenReturn(UserPreferences(overlayInteracted = true, privatePlayerMode = AlwaysAsk))
            testee.processJsCallbackMessage(
                DUCK_PLAYER_FEATURE_NAME,
                "initialSetup",
                "id",
                null,
                false,
                { "someUrl" },
            )
            assertCommandIssued<Command.SendResponseToJs>()
        }

    @Test
    fun whenJsCallbackMessageInitialSetupFromDuckPlayerPageThenSendResponseToDuckPlayer() =
        runTest {
            whenever(mockEnabledToggle.isEnabled()).thenReturn(true)
            whenever(mockDuckPlayer.getUserPreferences()).thenReturn(UserPreferences(overlayInteracted = true, privatePlayerMode = AlwaysAsk))
            testee.processJsCallbackMessage(
                DUCK_PLAYER_PAGE_FEATURE_NAME,
                "initialSetup",
                "id",
                null,
                false,
                { "someUrl" },
            )
            assertCommandIssued<Command.SendResponseToDuckPlayer>()
        }

    @Test
    fun whenJsCallbackMessageOpenSettingsThenOpenSettings() =
        runTest {
            whenever(mockEnabledToggle.isEnabled()).thenReturn(true)
            testee.processJsCallbackMessage(
                DUCK_PLAYER_PAGE_FEATURE_NAME,
                "openSettings",
                "id",
                null,
                false,
                { "someUrl" },
            )
            assertCommandIssued<Command.OpenDuckPlayerSettings>()
        }

    @Test
    fun whenJsCallbackMessageOpenInfoThenOpenInfo() =
        runTest {
            whenever(mockEnabledToggle.isEnabled()).thenReturn(true)
            whenever(mockDuckPlayer.getDuckPlayerState()).thenReturn(ENABLED)

            testee.processJsCallbackMessage(
                DUCK_PLAYER_PAGE_FEATURE_NAME,
                "openInfo",
                "id",
                null,
                false,
                { "someUrl" },
            )
            assertCommandIssued<Command.OpenDuckPlayerPageInfo>()
        }

    @Test
    fun whenPrivacyProtectionMenuClickedThenListenerIsInvoked() =
        runTest {
            loadUrl("http://www.example.com/home.html")
            testee.onPrivacyProtectionMenuClicked()
            verify(mockPrivacyProtectionsToggleUsageListener).onPrivacyProtectionsToggleUsed()
        }

    @Test
    fun whenPageIsChangedThenPrivacyProtectionsPopupManagerIsNotified() =
        runTest {
            updateUrl(
                originalUrl = "example.com",
                currentUrl = "example2.com",
                isBrowserShowing = true,
            )

            verify(mockPrivacyProtectionsPopupManager).onPageLoaded(
                url = "example2.com",
                httpErrorCodes = emptyList(),
                hasBrowserError = false,
            )
        }

    @Test
    fun whenPageIsChangedWithWebViewErrorResponseThenPrivacyProtectionsPopupManagerIsNotified() =
        runTest {
            testee.onReceivedError(BAD_URL, "example2.com")

            updateUrl(
                originalUrl = "example.com",
                currentUrl = "example2.com",
                isBrowserShowing = true,
            )

            verify(mockPrivacyProtectionsPopupManager).onPageLoaded(
                url = "example2.com",
                httpErrorCodes = emptyList(),
                hasBrowserError = true,
            )
        }

    @Test
    fun whenErrorHandlerKilledAndPageIsChangedWithHttpErrorThenPrivacyProtectionsPopupManagerIsNotified() =
        runTest {
            whenever(mockSiteErrorHandlerKillSwitchToggle.isEnabled()).thenReturn(false)
            testee.recordHttpErrorCode(statusCode = 404, url = "example2.com")

            updateUrl(
                originalUrl = "example.com",
                currentUrl = "example2.com",
                isBrowserShowing = true,
            )

            verify(mockPrivacyProtectionsPopupManager).onPageLoaded(
                url = "example2.com",
                httpErrorCodes = listOf(404),
                hasBrowserError = false,
            )
        }

    @Test
    fun whenPageIsChangedWithHttpErrorThenPrivacyProtectionsPopupManagerIsNotified() =
        runTest {
            val siteCaptor = argumentCaptor<Site>()
            whenever(mockSiteHttpErrorHandler.assignErrorsAndClearCache(siteCaptor.capture())).then {
                siteCaptor.lastValue.onHttpErrorDetected(404)
            }

            updateUrl(
                originalUrl = "example.com",
                currentUrl = "example2.com",
                isBrowserShowing = true,
            )

            verify(mockPrivacyProtectionsPopupManager).onPageLoaded(
                url = "example2.com",
                httpErrorCodes = listOf(404),
                hasBrowserError = false,
            )
        }

    @Test
    fun whenPageIsChangedWithHttpError400ThenUpdateCountPixelCalledForWebViewReceivedHttpError400Daily() =
        runTest {
            testee.recordHttpErrorCode(statusCode = 400, url = "example2.com")

            verify(mockHttpErrorPixels).updateCountPixel(HttpErrorPixelName.WEBVIEW_RECEIVED_HTTP_ERROR_400_DAILY)
        }

    @Test
    fun whenPageIsChangedWithHttpError4XXThenUpdateCountPixelCalledForWebViewReceivedHttpError4XXDaily() =
        runTest {
            testee.recordHttpErrorCode(statusCode = 403, url = "example2.com")

            verify(mockHttpErrorPixels).updateCountPixel(HttpErrorPixelName.WEBVIEW_RECEIVED_HTTP_ERROR_4XX_DAILY)
            verify(mockHttpErrorPixels, never()).updateCountPixel(HttpErrorPixelName.WEBVIEW_RECEIVED_HTTP_ERROR_400_DAILY)
        }

    @Test
    fun whenPageIsChangedWithHttpError5XXThenUpdate5xxCountPixelCalledForWebViewReceivedHttpError5XXDaily() =
        runTest {
            testee.recordHttpErrorCode(statusCode = 504, url = "example2.com")

            verify(mockHttpErrorPixels).update5xxCountPixel(HttpErrorPixelName.WEBVIEW_RECEIVED_HTTP_ERROR_5XX_DAILY, 504)
        }

    @Test
    fun whenPrivacyProtectionsPopupUiEventIsReceivedThenItIsPassedToPrivacyProtectionsPopupManager() =
        runTest {
            PrivacyProtectionsPopupUiEvent.entries.forEach { event ->
                testee.onPrivacyProtectionsPopupUiEvent(event)
                verify(mockPrivacyProtectionsPopupManager).onUiEvent(event)
            }
        }

    @Test
    fun whenRefreshIsTriggeredByUserThenIncrementRefreshCount() =
        runTest {
            val url = exampleUrl
            givenCurrentSite(url)

            testee.onRefreshRequested(triggeredByUser = false)
            verify(mockBrokenSitePrompt, never()).pageRefreshed(any())

            testee.onRefreshRequested(triggeredByUser = true)
            verify(mockBrokenSitePrompt).pageRefreshed(url.toUri())
        }

    @Test
    fun whenRefreshIsTriggeredByUserThenPrivacyProtectionsPopupManagerIsNotifiedWithTopPosition() =
        runTest {
            testee.onRefreshRequested(triggeredByUser = false)
            verify(mockPrivacyProtectionsPopupManager, never()).onPageRefreshTriggeredByUser(isOmnibarAtTheTop = true)
            testee.onRefreshRequested(triggeredByUser = true)
            verify(mockPrivacyProtectionsPopupManager).onPageRefreshTriggeredByUser(isOmnibarAtTheTop = true)
        }

    @Test
    fun whenRefreshIsTriggeredByUserThenPrivacyProtectionsPopupManagerIsNotifiedWithBottomPosition() =
        runTest {
            whenever(mockSettingsDataStore.omnibarPosition).thenReturn(BOTTOM)
            testee.onRefreshRequested(triggeredByUser = false)
            verify(mockPrivacyProtectionsPopupManager, never()).onPageRefreshTriggeredByUser(isOmnibarAtTheTop = false)
            testee.onRefreshRequested(triggeredByUser = true)
            verify(mockPrivacyProtectionsPopupManager).onPageRefreshTriggeredByUser(isOmnibarAtTheTop = false)
        }

    @Test
    fun whenOnlyChangeInUrlIsHttpsUpgradeNakedDomainRedirectOrTrailingSlashThenConsiderSameForExternalLaunch() =
        runTest {
            val urlA = "https://example.com"
            val urlB = "http://www.example.com"
            val urlC = "https://www.example.com/"
            val urlD = "http://www.example.com/path/"

            assertTrue(testee.urlUnchangedForExternalLaunchPurposes(urlA, urlB))
            assertTrue(testee.urlUnchangedForExternalLaunchPurposes(urlB, urlC))
            assertTrue(testee.urlUnchangedForExternalLaunchPurposes(urlA, urlC))
            assertFalse(testee.urlUnchangedForExternalLaunchPurposes(urlC, urlD))
        }

    @Test
    fun whenPrivacyProtectionsAreToggledThenCorrectPixelsAreSent() =
        runTest {
            val params = mapOf("test_key" to "test_value")
            whenever(privacyProtectionsPopupExperimentExternalPixels.getPixelParams()).thenReturn(params)
            whenever(mockUserAllowListRepository.isDomainInUserAllowList("www.example.com")).thenReturn(false)
            loadUrl("http://www.example.com/home.html")
            testee.onPrivacyProtectionMenuClicked()
            whenever(mockUserAllowListRepository.isDomainInUserAllowList("www.example.com")).thenReturn(true)
            testee.onPrivacyProtectionMenuClicked()

            verify(mockPixel).fire(AppPixelName.BROWSER_MENU_ALLOWLIST_ADD, params, type = Count)
            verify(mockPixel).fire(AppPixelName.BROWSER_MENU_ALLOWLIST_REMOVE, params, type = Count)
            assertEquals(1, protectionTogglePlugin.toggleOff)
            assertEquals(1, protectionTogglePlugin.toggleOn)
            verify(privacyProtectionsPopupExperimentExternalPixels).tryReportProtectionsToggledFromBrowserMenu(protectionsEnabled = false)
            verify(privacyProtectionsPopupExperimentExternalPixels).tryReportProtectionsToggledFromBrowserMenu(protectionsEnabled = true)
        }

    @Test
    fun whenHomeShownAndFaviconPromptShouldShowAndFavouritesIsNotEmptyThenShowFaviconsPromptCommandSent() =
        runTest {
            whenever(mockFaviconFetchingPrompt.shouldShow()).thenReturn(true)
            whenever(mockSavedSitesRepository.hasFavorites()).thenReturn(true)

            testee.onHomeShown()

            assertCommandIssued<Command.ShowFaviconsPrompt>()
        }

    @Test
    fun whenHomeShownAndFaviconPromptShouldShowAndFavouritesIsEmptyThenShowFaviconsPromptCommandNotSent() =
        runTest {
            whenever(mockFaviconFetchingPrompt.shouldShow()).thenReturn(true)
            whenever(mockSavedSitesRepository.hasFavorites()).thenReturn(false)

            testee.onHomeShown()

            assertCommandNotIssued<Command.ShowFaviconsPrompt>()
        }

    @Test
    fun whenHomeShownAndFaviconPromptShouldNotShowAndFavouritesIsNotEmptyThenShowFaviconsPromptCommandNotSent() =
        runTest {
            whenever(mockFaviconFetchingPrompt.shouldShow()).thenReturn(false)
            whenever(mockSavedSitesRepository.hasFavorites()).thenReturn(true)

            testee.onHomeShown()

            assertCommandNotIssued<Command.ShowFaviconsPrompt>()
        }

    @Test
    fun whenHomeShownAndFaviconPromptShouldNotShowAndFavouritesEmptyThenShowFaviconsPromptCommandNotSent() =
        runTest {
            whenever(mockFaviconFetchingPrompt.shouldShow()).thenReturn(false)
            whenever(mockSavedSitesRepository.hasFavorites()).thenReturn(false)

            testee.onHomeShown()

            assertCommandNotIssued<Command.ShowFaviconsPrompt>()
        }

    @Test
    @SdkSuppress(minSdkVersion = Build.VERSION_CODES.Q)
    fun whenAllowBypassSSLCertificatesFeatureDisabledThenSSLCertificateErrorsAreIgnored() {
        whenever(mockEnabledToggle.isEnabled()).thenReturn(false)

        val url = exampleUrl
        givenCurrentSite(url)

        val certificate = aRSASslCertificate()
        val sslErrorResponse = SslErrorResponse(SslError(SslError.SSL_EXPIRED, certificate, url), EXPIRED, url)
        testee.onReceivedSslError(aHandler(), sslErrorResponse)

        assertCommandNotIssued<Command.ShowSSLError>()
    }

    @Test
    @SdkSuppress(minSdkVersion = Build.VERSION_CODES.Q)
    fun whenSslCertificateIssueReceivedForLoadingSiteThenShowSslWarningCommandSentAndViewStatesUpdated() {
        whenever(mockEnabledToggle.isEnabled()).thenReturn(true)

        val url = exampleUrl
        givenCurrentSite(url)

        val certificate = aRSASslCertificate()
        val sslErrorResponse = SslErrorResponse(SslError(SslError.SSL_EXPIRED, certificate, url), EXPIRED, url)
        testee.onReceivedSslError(aHandler(), sslErrorResponse)

        assertCommandIssued<Command.ShowSSLError>()

        assertEquals(EXPIRED, browserViewState().sslError)
        assertEquals(false, browserViewState().showPrivacyShield.isEnabled())
        assertEquals(false, loadingViewState().isLoading)
    }

    @Test
    @SdkSuppress(minSdkVersion = Build.VERSION_CODES.Q)
    fun whenSslCertificateIssueReceivedForAnotherSiteThenShowSslWarningCommandNotSentAndViewStatesNotUpdated() {
        whenever(mockEnabledToggle.isEnabled()).thenReturn(true)
        val url = exampleUrl
        givenCurrentSite(url)

        val certificate = aRSASslCertificate()
        val sslErrorResponse = SslErrorResponse(SslError(SslError.SSL_EXPIRED, certificate, url), EXPIRED, "another.com")
        testee.onReceivedSslError(aHandler(), sslErrorResponse)

        assertCommandNotIssued<Command.ShowSSLError>()

        assertEquals(NONE, browserViewState().sslError)
    }

    @Test
    @SdkSuppress(minSdkVersion = Build.VERSION_CODES.Q)
    fun whenInFreshStartAndSslCertificateIssueReceivedThenShowSslWarningCommandSentAndViewStatesUpdated() =
        runTest {
            whenever(mockEnabledToggle.isEnabled()).thenReturn(true)
            val url = exampleUrl
            val site: Site = mock()
            whenever(site.url).thenReturn(url)
            whenever(site.nextUrl).thenReturn(null)
            val siteLiveData = MutableLiveData<Site>()
            siteLiveData.value = site

            val certificate = aRSASslCertificate()
            val sslErrorResponse = SslErrorResponse(SslError(SslError.SSL_EXPIRED, certificate, url), EXPIRED, url)
            testee.onReceivedSslError(aHandler(), sslErrorResponse)

            assertCommandIssued<Command.ShowSSLError>()

            assertEquals(EXPIRED, browserViewState().sslError)
            assertEquals(false, browserViewState().showPrivacyShield.isEnabled())
            assertEquals(false, loadingViewState().isLoading)
        }

    @Test
    fun whenSslCertificateBypassedThenUrlAddedToRepository() {
        val url = exampleUrl

        testee.onSSLCertificateWarningAction(Action.Proceed, url)

        verify(mockBypassedSSLCertificatesRepository).add(url)

        verify(mockPixel).fire(AppPixelName.SSL_CERTIFICATE_WARNING_PROCEED_PRESSED)
    }

    @Test
    fun whenSslCertificateActionShownThenPixelsFired() {
        val url = exampleUrl

        testee.onSSLCertificateWarningAction(Action.Shown(EXPIRED), url)
        verify(mockPixel).fire(AppPixelName.SSL_CERTIFICATE_WARNING_EXPIRED_SHOWN)

        testee.onSSLCertificateWarningAction(Action.Shown(WRONG_HOST), url)
        verify(mockPixel).fire(AppPixelName.SSL_CERTIFICATE_WARNING_WRONG_HOST_SHOWN)

        testee.onSSLCertificateWarningAction(Action.Shown(UNTRUSTED_HOST), url)
        verify(mockPixel).fire(AppPixelName.SSL_CERTIFICATE_WARNING_UNTRUSTED_SHOWN)

        testee.onSSLCertificateWarningAction(Action.Shown(GENERIC), url)
        verify(mockPixel).fire(AppPixelName.SSL_CERTIFICATE_WARNING_GENERIC_SHOWN)
    }

    @Test
    fun whenSslCertificateActionAdvanceThenPixelsFired() {
        val url = exampleUrl
        testee.onSSLCertificateWarningAction(Action.Advance, url)

        verify(mockPixel).fire(AppPixelName.SSL_CERTIFICATE_WARNING_ADVANCED_PRESSED)
    }

    @Test
    fun whenSslCertificateActionLeaveSiteThenPixelsFiredAndViewStatesUpdated() {
        val url = exampleUrl
        testee.onSSLCertificateWarningAction(Action.LeaveSite, url)

        verify(mockPixel).fire(AppPixelName.SSL_CERTIFICATE_WARNING_CLOSE_PRESSED)
        assertEquals(NONE, browserViewState().sslError)
    }

    @Test
    fun whenSslCertificateActionLeaveSiteAndCustomTabThenClose() {
        val url = exampleUrl
        testee.onSSLCertificateWarningAction(Action.LeaveSite, url, true)

        verify(mockPixel).fire(AppPixelName.SSL_CERTIFICATE_WARNING_CLOSE_PRESSED)
        verify(mockCommandObserver, atLeastOnce()).onChanged(commandCaptor.capture())
        assertTrue(commandCaptor.allValues.any { it is Command.CloseCustomTab })
    }

    @Test
    fun whenSslCertificateActionLeaveSiteAndCustomTabFalseThenHideSSLError() {
        val url = exampleUrl
        testee.onSSLCertificateWarningAction(Action.LeaveSite, url, false)

        verify(mockPixel).fire(AppPixelName.SSL_CERTIFICATE_WARNING_CLOSE_PRESSED)
        verify(mockCommandObserver, atLeastOnce()).onChanged(commandCaptor.capture())
        assertTrue(commandCaptor.allValues.any { it is Command.HideSSLError })
    }

    @Test
    fun whenMaliciousSiteActionLeaveSiteAndCustomTabThenClose() {
        val url = exampleUrl.toUri()
        testee.onMaliciousSiteUserAction(LeaveSite, url, MALWARE, true)
        verify(mockCommandObserver, atLeastOnce()).onChanged(commandCaptor.capture())
        assertTrue(commandCaptor.allValues.any { it is Command.CloseCustomTab })
    }

    @Test
    fun whenMaliciousSiteActionLeaveSiteAndCustomTabFalseThenHideSSLError() {
        val url = exampleUrl.toUri()
        testee.onMaliciousSiteUserAction(LeaveSite, url, MALWARE, false)
        verify(mockCommandObserver, atLeastOnce()).onChanged(commandCaptor.capture())
        assertTrue(commandCaptor.allValues.any { it is Command.EscapeMaliciousSite })
    }

    @Test
    @SdkSuppress(minSdkVersion = Build.VERSION_CODES.Q)
    fun whenSslCertificateActionProceedThenPixelsFiredAndViewStatesUpdated() {
        whenever(mockEnabledToggle.isEnabled()).thenReturn(true)
        val url = exampleUrl
        val certificate = aRSASslCertificate()
        val sslErrorResponse = SslErrorResponse(SslError(SslError.SSL_EXPIRED, certificate, url), EXPIRED, url)

        testee.onReceivedSslError(aHandler(), sslErrorResponse)

        testee.onSSLCertificateWarningAction(Action.Proceed, url)

        assertCommandNotIssued<Command.HideSSLError>()
        verify(mockPixel).fire(AppPixelName.SSL_CERTIFICATE_WARNING_PROCEED_PRESSED)
        verify(mockBypassedSSLCertificatesRepository).add(url)
        assertEquals(NONE, browserViewState().sslError)
        assertEquals(true, browserViewState().browserShowing)
    }

    @Test
    @SdkSuppress(minSdkVersion = Build.VERSION_CODES.Q)
    fun whenWebViewRefreshedThenSSLErrorStateIsNone() {
        whenever(mockEnabledToggle.isEnabled()).thenReturn(true)
        val url = exampleUrl
        val certificate = aRSASslCertificate()
        val sslErrorResponse = SslErrorResponse(SslError(SslError.SSL_EXPIRED, certificate, url), EXPIRED, url)

        testee.onReceivedSslError(aHandler(), sslErrorResponse)

        testee.onWebViewRefreshed()

        assertEquals(NONE, browserViewState().sslError)
    }

    @Test
    @SdkSuppress(minSdkVersion = Build.VERSION_CODES.Q)
    fun whenResetSSLErrorThenBrowserErrorStateIsLoading() {
        whenever(mockEnabledToggle.isEnabled()).thenReturn(true)
        val url = exampleUrl
        val certificate = aRSASslCertificate()
        val sslErrorResponse = SslErrorResponse(SslError(SslError.SSL_EXPIRED, certificate, url), EXPIRED, url)

        testee.onReceivedSslError(aHandler(), sslErrorResponse)
        assertEquals(EXPIRED, browserViewState().sslError)

        testee.refreshBrowserError()
        assertEquals(NONE, browserViewState().sslError)
    }

    @Test
    fun whenRecoveringFromWarningPageAndBrowserShouldShowThenViewStatesUpdated() {
        testee.recoverFromWarningPage(true)

        assertEquals(NONE, browserViewState().sslError)
        assertEquals(true, browserViewState().browserShowing)
    }

    @Test
    fun whenRecoveringFromWarningPageAndBrowserShouldNotShowThenViewStatesUpdated() =
        runTest {
            testee.recoverFromWarningPage(false)

            assertEquals(NONE, browserViewState().sslError)
            assertEquals(false, browserViewState().browserShowing)
            assertEquals(false, browserViewState().showPrivacyShield.isEnabled())

            assertEquals(false, loadingViewState().isLoading)

            assertEquals("", omnibarViewState().omnibarText)
            assertEquals(true, omnibarViewState().forceExpand)
        }

    fun aHandler(): SslErrorHandler {
        val handler =
            mock<SslErrorHandler>().apply {
            }
        return handler
    }

    private fun aRSASslCertificate(): SslCertificate {
        val certificate =
            mock<X509Certificate>().apply {
                val key =
                    mock<RSAPublicKey>().apply {
                        whenever(this.algorithm).thenReturn("rsa")
                        whenever(this.modulus).thenReturn(BigInteger("1"))
                    }
                whenever(this.publicKey).thenReturn(key)
            }
        return mock<SslCertificate>().apply {
            whenever(x509Certificate).thenReturn(certificate)
        }
    }

    @Test
    fun whenTrackersBlockedCtaShownWithBrowserShowingThenPrivacyShieldIsHighlighted() =
        runTest {
            val cta =
                DaxTrackersBlockedCta(
                    onboardingStore = mockOnboardingStore,
                    appInstallStore = mockAppInstallStore,
                    trackers = emptyList(),
                    settingsDataStore = mockSettingsDataStore,
                    onboardingDesignExperimentManager = mockOnboardingDesignExperimentManager,
                )
            testee.ctaViewState.value = ctaViewState().copy(cta = cta)
            testee.browserViewState.value = browserViewState().copy(browserShowing = true, maliciousSiteBlocked = false)

            testee.onOnboardingDaxTypingAnimationFinished()

            assertTrue(browserViewState().showPrivacyShield.isHighlighted())
        }

    @Test
    fun whenTrackersBlockedCtaShownWithMaliciousSiteBlockedThenPrivacyShieldIsNotHighlighted() =
        runTest {
            val cta =
                DaxTrackersBlockedCta(
                    onboardingStore = mockOnboardingStore,
                    appInstallStore = mockAppInstallStore,
                    trackers = emptyList(),
                    settingsDataStore = mockSettingsDataStore,
                    onboardingDesignExperimentManager = mockOnboardingDesignExperimentManager,
                )
            testee.ctaViewState.value = ctaViewState().copy(cta = cta)
            testee.browserViewState.value = browserViewState().copy(browserShowing = false, maliciousSiteBlocked = true)

            testee.onOnboardingDaxTypingAnimationFinished()

            assertFalse(browserViewState().showPrivacyShield.isHighlighted())
        }

    @Test
    fun givenTrackersBlockedCtaShownWhenLaunchingTabSwitcherThenCtaIsDismissed() =
        runTest {
            val cta =
                DaxTrackersBlockedCta(
                    onboardingStore = mockOnboardingStore,
                    appInstallStore = mockAppInstallStore,
                    trackers = emptyList(),
                    settingsDataStore = mockSettingsDataStore,
                    onboardingDesignExperimentManager = mockOnboardingDesignExperimentManager,
                )
            testee.ctaViewState.value = ctaViewState().copy(cta = cta)

            testee.userLaunchingTabSwitcher(false)

            verify(mockDismissedCtaDao).insert(DismissedCta(cta.ctaId))
        }

    @Test
    fun givenTrackersBlockedCtaShownWhenUserRequestOpeningNewTabThenCtaIsDismissed() =
        runTest {
            val cta =
                DaxTrackersBlockedCta(
                    onboardingStore = mockOnboardingStore,
                    appInstallStore = mockAppInstallStore,
                    trackers = emptyList(),
                    settingsDataStore = mockSettingsDataStore,
                    onboardingDesignExperimentManager = mockOnboardingDesignExperimentManager,
                )
            testee.ctaViewState.value = ctaViewState().copy(cta = cta)

            testee.onNewTabMenuItemClicked()

            verify(mockDismissedCtaDao).insert(DismissedCta(cta.ctaId))
        }

    @Test
    fun whenUserDismissDaxTrackersBlockedDialogThenFinishPrivacyShieldPulse() {
        val cta =
            DaxTrackersBlockedCta(
                onboardingStore = mockOnboardingStore,
                appInstallStore = mockAppInstallStore,
                trackers = emptyList(),
                settingsDataStore = mockSettingsDataStore,
                onboardingDesignExperimentManager = mockOnboardingDesignExperimentManager,
            )
        setCta(cta)

        testee.onUserDismissedCta(cta)
        assertFalse(browserViewState().showPrivacyShield.isHighlighted())
    }

    @Test
    fun givenOnboardingCtaShownWhenUserSubmittedQueryThenDismissCta() {
        whenever(mockOmnibarConverter.convertQueryToUrl("foo", null)).thenReturn("foo.com")
        val cta = DaxSerpCta(mockOnboardingStore, mockAppInstallStore, mockOnboardingDesignExperimentManager)
        testee.ctaViewState.value = CtaViewState(cta = cta)

        testee.onUserSubmittedQuery("foo")

        assertCommandIssued<HideOnboardingDaxDialog> {
            assertEquals(cta, this.onboardingCta)
        }
    }

    @Test
    fun givenRequestSubmittedFromBookmarkThenCorrectQueryOriginSetInBrowserViewState() {
        whenever(mockOmnibarConverter.convertQueryToUrl("foo", null, FromBookmark)).thenReturn("foo.com")

        testee.onUserSubmittedQuery("foo", queryOrigin = FromBookmark)

        assertEquals(testee.browserViewState.value?.lastQueryOrigin, FromBookmark)
    }

    @Test
    fun givenRequestSubmittedFromUserThenCorrectQueryOriginSetInBrowserViewState() {
        whenever(mockOmnibarConverter.convertQueryToUrl("foo", null)).thenReturn("foo.com")

        testee.onUserSubmittedQuery("foo")

        assertEquals(testee.browserViewState.value?.lastQueryOrigin, FromUser)
    }

    @Test
    fun givenSuggestedSearchesDialogShownWhenUserSubmittedQueryThenCustomSearchPixelIsSent() {
        whenever(mockOmnibarConverter.convertQueryToUrl("foo", null)).thenReturn("foo.com")
        val cta = DaxBubbleCta.DaxIntroSearchOptionsCta(mockOnboardingStore, mockAppInstallStore)
        testee.ctaViewState.value = CtaViewState(cta = cta)

        testee.onUserSubmittedQuery("foo")

        verify(mockPixel).fire(ONBOARDING_SEARCH_CUSTOM, type = Unique())
    }

    @Test
    fun givenSuggestedSitesDialogShownWhenUserSubmittedQueryThenCustomSitePixelIsSent() {
        whenever(mockOmnibarConverter.convertQueryToUrl("foo", null)).thenReturn("foo.com")
        val cta = DaxBubbleCta.DaxIntroVisitSiteOptionsCta(mockOnboardingStore, mockAppInstallStore)
        testee.ctaViewState.value = CtaViewState(cta = cta)

        testee.onUserSubmittedQuery("foo")

        verify(mockPixel).fire(ONBOARDING_VISIT_SITE_CUSTOM, type = Unique())
    }

    @Test
    fun whenOnStartPrintThenIsPrintingTrue() {
        testee.onStartPrint()
        assertTrue(browserViewState().isPrinting)
        assertTrue(testee.isPrinting())
    }

    @Test
    fun whenOnFinishPrintThenIsPrintingFalse() {
        testee.onFinishPrint()
        assertFalse(browserViewState().isPrinting)
        assertFalse(testee.isPrinting())
    }

    @Test
    fun whenOnFavoriteAddedThePixelFired() {
        testee.onFavoriteAdded()

        verify(mockPixel).fire(SavedSitesPixelName.EDIT_BOOKMARK_ADD_FAVORITE_TOGGLED)
    }

    @Test
    fun whenOnFavoriteRemovedThePixelFired() {
        testee.onFavoriteRemoved()

        verify(mockPixel).fire(SavedSitesPixelName.EDIT_BOOKMARK_REMOVE_FAVORITE_TOGGLED)
    }

    @Test
    fun whenOnSavedSiteDeleteCancelledThenPixelFired() {
        testee.onSavedSiteDeleteCancelled()

        verify(mockPixel).fire(SavedSitesPixelName.EDIT_BOOKMARK_DELETE_BOOKMARK_CANCELLED)
    }

    @Test
    fun whenOnSavedSiteDeleteRequestedThenPixelFired() {
        testee.onSavedSiteDeleteRequested()

        verify(mockPixel).fire(SavedSitesPixelName.EDIT_BOOKMARK_DELETE_BOOKMARK_CLICKED)
    }

    @Test
    fun whenUserLaunchingTabSwitcherThenLaunchTabSwitcherCommandSentAndPixelFired() =
        runTest {
            val tabCount = "61-80"
            val active7d = "21+"
            val inactive1w = "11-20"
            val inactive2w = "6-10"
            val inactive3w = "0"

            whenever(mockTabStatsBucketing.getNumberOfOpenTabs()).thenReturn(tabCount)
            whenever(mockTabStatsBucketing.getTabsActiveLastWeek()).thenReturn(active7d)
            whenever(mockTabStatsBucketing.getTabsActiveOneWeekAgo()).thenReturn(inactive1w)
            whenever(mockTabStatsBucketing.getTabsActiveTwoWeeksAgo()).thenReturn(inactive2w)
            whenever(mockTabStatsBucketing.getTabsActiveMoreThanThreeWeeksAgo()).thenReturn(inactive3w)

            val params =
                mapOf(
                    PixelParameter.TAB_COUNT to tabCount,
                    PixelParameter.TAB_ACTIVE_7D to active7d,
                    PixelParameter.TAB_INACTIVE_1W to inactive1w,
                    PixelParameter.TAB_INACTIVE_2W to inactive2w,
                    PixelParameter.TAB_INACTIVE_3W to inactive3w,
                )

            testee.userLaunchingTabSwitcher(false)

            assertCommandIssued<Command.LaunchTabSwitcher>()
            verify(mockPixel).fire(AppPixelName.TAB_MANAGER_CLICKED)
            verify(mockPixel).fire(AppPixelName.TAB_MANAGER_CLICKED_DAILY, params, emptyMap(), Daily())
        }

    @Test
    fun whenNewTabShownThenPixelIsFired() {
        testee.onNewTabShown()

        verify(mockNewTabPixels).fireNewTabDisplayed()
    }

    @Test
    fun whenUserLongPressedOnHistorySuggestionThenShowRemoveSearchSuggestionDialogCommandIssued() {
        val suggestion = AutoCompleteHistorySuggestion(phrase = "phrase", title = "title", url = "url", isAllowedInTopHits = false)

        testee.userLongPressedAutocomplete(suggestion)

        verify(mockCommandObserver, atLeastOnce()).onChanged(commandCaptor.capture())
        val issuedCommand = commandCaptor.allValues.find { it is Command.ShowRemoveSearchSuggestionDialog }
        assertEquals(suggestion, (issuedCommand as Command.ShowRemoveSearchSuggestionDialog).suggestion)
    }

    @Test
    fun whenUserLongPressedOnHistorySearchSuggestionThenShowRemoveSearchSuggestionDialogCommandIssued() {
        val suggestion = AutoCompleteHistorySearchSuggestion(phrase = "phrase", isAllowedInTopHits = false)

        testee.userLongPressedAutocomplete(suggestion)

        verify(mockCommandObserver, atLeastOnce()).onChanged(commandCaptor.capture())
        val issuedCommand = commandCaptor.allValues.find { it is Command.ShowRemoveSearchSuggestionDialog }
        assertEquals(suggestion, (issuedCommand as Command.ShowRemoveSearchSuggestionDialog).suggestion)
    }

    @Test
    fun whenUserLongPressedOnOtherSuggestionThenDoNothing() {
        val suggestion = AutoCompleteDefaultSuggestion(phrase = "phrase")

        testee.userLongPressedAutocomplete(suggestion)

        assertCommandNotIssued<Command.ShowRemoveSearchSuggestionDialog>()
    }

    @Test
    fun whenOnRemoveSearchSuggestionConfirmedForHistorySuggestionThenPixelsFiredAndHistoryEntryRemoved() =
        runBlocking {
            val suggestion = AutoCompleteHistorySuggestion(phrase = "phrase", title = "title", url = "url", isAllowedInTopHits = false)
            val omnibarText = "foo"

            testee.onRemoveSearchSuggestionConfirmed(suggestion, omnibarText)

            verify(mockPixel).fire(AppPixelName.AUTOCOMPLETE_RESULT_DELETED)
            verify(mockPixel).fire(AppPixelName.AUTOCOMPLETE_RESULT_DELETED_DAILY, type = Daily())
            verify(mockNavigationHistory).removeHistoryEntryByUrl(suggestion.url)
            assertCommandIssued<Command.AutocompleteItemRemoved>()
        }

    @Test
    fun whenOnRemoveSearchSuggestionConfirmedForHistorySearchSuggestionThenPixelsFiredAndHistoryEntryRemoved() =
        runBlocking {
            val suggestion = AutoCompleteHistorySearchSuggestion(phrase = "phrase", isAllowedInTopHits = false)
            val omnibarText = "foo"

            testee.onRemoveSearchSuggestionConfirmed(suggestion, omnibarText)

            verify(mockPixel).fire(AppPixelName.AUTOCOMPLETE_RESULT_DELETED)
            verify(mockPixel).fire(AppPixelName.AUTOCOMPLETE_RESULT_DELETED_DAILY, type = Daily())
            verify(mockNavigationHistory).removeHistoryEntryByQuery(suggestion.phrase)
            assertCommandIssued<Command.AutocompleteItemRemoved>()
        }

    @Test
    fun whenNewPageWithUrlYouTubeNoCookieThenReplaceUrlWithDuckPlayer() =
        runTest {
            whenever(mockDuckPlayer.isSimulatedYoutubeNoCookie("https://youtube-nocookie.com/?videoID=1234".toUri())).thenReturn(true)
            whenever(mockDuckPlayer.isSimulatedYoutubeNoCookie("duck://player/1234".toUri())).thenReturn(false)
            whenever(mockDuckPlayer.createDuckPlayerUriFromYoutubeNoCookie("https://youtube-nocookie.com/?videoID=1234".toUri())).thenReturn(
                "duck://player/1234",
            )
            whenever(mockDuckPlayer.getDuckPlayerState()).thenReturn(ENABLED)
            testee.browserViewState.value = browserViewState().copy(browserShowing = true)

            testee.navigationStateChanged(buildWebNavigation("https://youtube-nocookie.com/?videoID=1234"))

            assertEquals("duck://player/1234", omnibarViewState().omnibarText)
        }

    @Test
    fun whenNewPageWithUrlYouTubeNoCookieAndFullUrlDisabledThenReplaceUrlWithDuckPlayer() =
        runTest {
            isFullSiteAddressEnabled = false
            whenever(mockDuckPlayer.isSimulatedYoutubeNoCookie("https://youtube-nocookie.com/?videoID=1234".toUri())).thenReturn(true)
            whenever(mockDuckPlayer.isSimulatedYoutubeNoCookie("duck://player/1234".toUri())).thenReturn(false)
            whenever(mockDuckPlayer.createDuckPlayerUriFromYoutubeNoCookie("https://youtube-nocookie.com/?videoID=1234".toUri())).thenReturn(
                "duck://player/1234",
            )
            whenever(mockDuckPlayer.getDuckPlayerState()).thenReturn(ENABLED)
            testee.browserViewState.value = browserViewState().copy(browserShowing = true)

            testee.navigationStateChanged(buildWebNavigation("https://youtube-nocookie.com/?videoID=1234"))

            assertEquals("Duck Player", omnibarViewState().omnibarText)
        }

    @Test
    fun whenNewPageAndBrokenSitePromptVisibleThenHideCta() =
        runTest {
            setCta(BrokenSitePromptDialogCta())

            testee.browserViewState.value = browserViewState().copy(browserShowing = true)
            testee.navigationStateChanged(buildWebNavigation("https://example.com"))

            assertCommandIssued<HideBrokenSitePromptCta>()
        }

    @Test
    fun whenUrlUpdatedWithUrlYouTubeNoCookieThenReplaceUrlWithDuckPlayer() =
        runTest {
            whenever(mockDuckPlayer.isSimulatedYoutubeNoCookie("https://youtube-nocookie.com/?videoID=1234".toUri())).thenReturn(true)
            whenever(mockDuckPlayer.isSimulatedYoutubeNoCookie("duck://player/1234".toUri())).thenReturn(false)
            whenever(mockDuckPlayer.isSimulatedYoutubeNoCookie(exampleUrl.toUri())).thenReturn(false)
            whenever(mockDuckPlayer.createDuckPlayerUriFromYoutubeNoCookie("https://youtube-nocookie.com/?videoID=1234".toUri())).thenReturn(
                "duck://player/1234",
            )
            whenever(mockDuckPlayer.getDuckPlayerState()).thenReturn(ENABLED)

            testee.browserViewState.value = browserViewState().copy(browserShowing = true)

            testee.navigationStateChanged(buildWebNavigation(exampleUrl))
            testee.navigationStateChanged(buildWebNavigation("https://youtube-nocookie.com/?videoID=1234"))

            assertEquals("duck://player/1234", omnibarViewState().omnibarText)
        }

    @Test
    fun whenUrlUpdatedWithUrlYouTubeNoCookieAndFullUrlDisabledThenReplaceUrlWithDuckPlayerString() =
        runTest {
            isFullSiteAddressEnabled = false
            whenever(mockDuckPlayer.isSimulatedYoutubeNoCookie("https://youtube-nocookie.com/?videoID=1234".toUri())).thenReturn(true)
            whenever(mockDuckPlayer.isSimulatedYoutubeNoCookie("duck://player/1234".toUri())).thenReturn(false)
            whenever(mockDuckPlayer.isSimulatedYoutubeNoCookie(exampleUrl.toUri())).thenReturn(false)
            whenever(mockDuckPlayer.createDuckPlayerUriFromYoutubeNoCookie("https://youtube-nocookie.com/?videoID=1234".toUri())).thenReturn(
                "duck://player/1234",
            )
            whenever(mockDuckPlayer.getDuckPlayerState()).thenReturn(ENABLED)

            testee.browserViewState.value = browserViewState().copy(browserShowing = true)

            testee.navigationStateChanged(buildWebNavigation(exampleUrl))
            testee.navigationStateChanged(buildWebNavigation("https://youtube-nocookie.com/?videoID=1234"))

            assertEquals("Duck Player", omnibarViewState().omnibarText)
        }

    @Test
    fun whenSharingDuckPlayerUrlThenReplaceWithYouTubeUrl() =
        runTest {
            givenCurrentSite("duck://player/1234")
            whenever(mockDuckPlayer.isDuckPlayerUri("duck://player/1234")).thenReturn(true)
            whenever(mockDuckPlayer.createYoutubeWatchUrlFromDuckPlayer(any())).thenReturn("https://youtube.com/watch?v=1234")

            testee.onShareSelected()

            assertCommandIssued<ShareLink> {
                assertEquals("https://youtube.com/watch?v=1234", this.url)
            }
        }

    @Test
    fun whenHandleMenuRefreshActionThenSendMenuRefreshPixels() {
        testee.handleMenuRefreshAction()

        verify(refreshPixelSender).sendMenuRefreshPixels()
    }

    @Test
    fun whenHandlePullToRefreshActionThenSendPullToRefreshPixels() {
        testee.handlePullToRefreshAction()

        verify(refreshPixelSender).sendPullToRefreshPixels()
    }

    @Test
    fun whenFireCustomTabRefreshPixelThenSendCustomTabRefreshPixel() {
        testee.fireCustomTabRefreshPixel()

        verify(refreshPixelSender).sendCustomTabRefreshPixel()
    }

    @Test
    fun whenRefreshCtaAndPatternsDetectedThenSendBreakageRefreshPixels() =
        runTest {
            setBrowserShowing(true)
            whenever(mockExtendedOnboardingFeatureToggles.noBrowserCtas()).thenReturn(mockDisabledToggle)
            val refreshPatterns = setOf(RefreshPattern.TWICE_IN_12_SECONDS, RefreshPattern.THRICE_IN_20_SECONDS)
            whenever(mockBrokenSitePrompt.getUserRefreshPatterns()).thenReturn(refreshPatterns)
            testee.refreshCta()

            verify(refreshPixelSender).onRefreshPatternDetected(refreshPatterns)
        }

    @Test
    fun whenPageIsChangedWithWebViewErrorResponseThenPixelIsFired() =
        runTest {
            testee.onReceivedError(BAD_URL, "example2.com")

            updateUrl(
                originalUrl = "example.com",
                currentUrl = "example2.com",
                isBrowserShowing = true,
            )

            verify(mockPixel).enqueueFire(AppPixelName.ERROR_PAGE_SHOWN)
        }

    @Test
    fun givenErrorPageFeatureDisabledWhenPageIsChangedWithWebViewErrorResponseThenPixelIsNotFired() =
        runTest {
            fakeAndroidConfigBrowserFeature.errorPagePixel().setRawStoredState(State(enable = false))
            testee.onReceivedError(BAD_URL, "example2.com")

            updateUrl(
                originalUrl = "example.com",
                currentUrl = "example2.com",
                isBrowserShowing = true,
            )

            verify(mockPixel, never()).enqueueFire(AppPixelName.ERROR_PAGE_SHOWN)
        }

    @Test
    fun whenUserSelectedAutocompleteWithAutoCompleteSwitchToTabSuggestionThenSwitchToTabCommandSentWithTabId() =
        runTest {
            val tabId = "tabId"
            val suggestion = AutoCompleteSwitchToTabSuggestion(phrase = "phrase", title = "title", url = "https://www.example.com", tabId = tabId)

            whenever(mockDuckChat.wasOpenedBefore()).thenReturn(true)
            whenever(mockSavedSitesRepository.hasBookmarks()).thenReturn(false)
            whenever(mockSavedSitesRepository.hasFavorites()).thenReturn(false)
            whenever(mockNavigationHistory.hasHistory()).thenReturn(false)

            testee.userSelectedAutocomplete(suggestion)

            assertCommandIssued<Command.SwitchToTab> {
                assertEquals(tabId, this.tabId)
            }
        }

    @Test
    fun whenUserSelectedAutocompleteDuckAiPromptThenCommandSent() =
        runTest {
            whenever(mockDuckChat.wasOpenedBefore()).thenReturn(true)
            whenever(mockSavedSitesRepository.hasBookmarks()).thenReturn(false)
            whenever(mockSavedSitesRepository.hasFavorites()).thenReturn(false)
            whenever(mockNavigationHistory.hasHistory()).thenReturn(false)

            val duckPrompt = AutoComplete.AutoCompleteSuggestion.AutoCompleteDuckAIPrompt("title")

            testee.userSelectedAutocomplete(duckPrompt)
            assertCommandIssued<Command.SubmitChat> {
                assertEquals(query, "title")
            }
        }

    @Test
    fun whenNavigationStateChangedCalledThenHandleResolvedUrlIsChecked() =
        runTest {
            testee.navigationStateChanged(buildWebNavigation("https://example.com"))

            verify(mockShowOnAppLaunchHandler).handleResolvedUrlStorage(eq("https://example.com"), any(), any())
        }

    @Test
    fun whenTabSwitcherPressedAndUserOnSiteThenPixelIsSent() =
        runTest {
            givenTabManagerData()
            setBrowserShowing(true)
            val domain = "https://www.example.com"
            givenCurrentSite(domain)

            testee.userLaunchingTabSwitcher(false)

            verify(mockPixel).fire(AppPixelName.TAB_MANAGER_OPENED_FROM_SITE)
        }

    @Test
    fun whenTabSwitcherPressedAndUserOnNtpThenPixelIsSent() =
        runTest {
            givenTabManagerData()
            setBrowserShowing(false)

            testee.userLaunchingTabSwitcher(false)

            verify(mockPixel).fire(AppPixelName.TAB_MANAGER_OPENED_FROM_NEW_TAB, mapOf(PixelParameter.FROM_FOCUSED_NTP to "false"))
        }

    @Test
    fun whenTabSwitcherPressedInFocusModeAndUserOnNtpThenPixelIsSent() =
        runTest {
            givenTabManagerData()
            setBrowserShowing(false)

            testee.userLaunchingTabSwitcher(true)

            verify(mockPixel).fire(AppPixelName.TAB_MANAGER_OPENED_FROM_NEW_TAB, mapOf(PixelParameter.FROM_FOCUSED_NTP to "true"))
        }

    @Test
    fun whenTabSwitcherPressedAndUserOnSerpThenPixelIsSent() =
        runTest {
            givenTabManagerData()
            setBrowserShowing(true)
            whenever(mockDuckDuckGoUrlDetector.isDuckDuckGoUrl(any())).thenReturn(true)
            val domain = "https://duckduckgo.com/?q=test&atb=v395-1-wb&ia=web"
            givenCurrentSite(domain)

            testee.userLaunchingTabSwitcher(false)

            verify(mockPixel).fire(AppPixelName.TAB_MANAGER_OPENED_FROM_SERP)
        }

    @Test
    fun whenInitialisedThenDefaultBrowserMenuButtonIsNotShown() {
        assertFalse(browserViewState().showSelectDefaultBrowserMenuItem)
    }

    @Test
    fun whenDefaultBrowserMenuButtonVisibilityChangesThenShowIt() =
        runTest {
            defaultBrowserPromptsExperimentShowPopupMenuItemFlow.value = true
            assertTrue(browserViewState().showSelectDefaultBrowserMenuItem)
        }

    @Test
    fun whenDefaultBrowserMenuButtonClickedThenNotifyExperiment() =
        runTest {
            testee.onSetDefaultBrowserSelected()
            verify(mockAdditionalDefaultBrowserPrompts).onSetAsDefaultPopupMenuItemSelected()
        }

    @Test
    fun whenPopupMenuLaunchedThenNotifyDefaultBrowserPromptsExperiment() =
        runTest {
            testee.onPopupMenuLaunched()
            verify(mockAdditionalDefaultBrowserPrompts).onPopupMenuLaunched()
        }

    private fun givenTabManagerData() =
        runTest {
            val tabCount = "61-80"
            val active7d = "21+"
            val inactive1w = "11-20"
            val inactive2w = "6-10"
            val inactive3w = "0"

            whenever(mockTabStatsBucketing.getNumberOfOpenTabs()).thenReturn(tabCount)
            whenever(mockTabStatsBucketing.getTabsActiveLastWeek()).thenReturn(active7d)
            whenever(mockTabStatsBucketing.getTabsActiveOneWeekAgo()).thenReturn(inactive1w)
            whenever(mockTabStatsBucketing.getTabsActiveTwoWeeksAgo()).thenReturn(inactive2w)
            whenever(mockTabStatsBucketing.getTabsActiveMoreThanThreeWeeksAgo()).thenReturn(inactive3w)
        }

    @Test
    fun whenSubmittedQueryIsDuckChatLinkThenOpenDuckChat() {
        whenever(mockSpecialUrlDetector.determineType(anyString())).thenReturn(SpecialUrlDetector.UrlType.ShouldLaunchDuckChatLink)
        whenever(mockOmnibarConverter.convertQueryToUrl("https://duckduckgo.com/?q=example&ia=chat&duckai=5", null)).thenReturn(
            "https://duckduckgo.com/?q=example&ia=chat&duckai=5",
        )
        testee.onUserSubmittedQuery("https://duckduckgo.com/?q=example&ia=chat&duckai=5")
        mockDuckChat.openDuckChatWithPrefill("example")
    }

    @Test
    fun whenSubmittedQueryIsDuckChatLinkWithoutQueryThenOpenDuckChatWithoutQuery() {
        whenever(mockSpecialUrlDetector.determineType(anyString())).thenReturn(SpecialUrlDetector.UrlType.ShouldLaunchDuckChatLink)
        whenever(mockOmnibarConverter.convertQueryToUrl("https://duckduckgo.com/?ia=chat", null)).thenReturn("https://duckduckgo.com/?ia=chat")
        testee.onUserSubmittedQuery("https://duckduckgo.com/?ia=chat")
        mockDuckChat.openDuckChat()
    }

    @Test
    fun whenProcessJsCallbackMessageForDuckChatThenSendCommand() =
        runTest {
            whenever(mockEnabledToggle.isEnabled()).thenReturn(true)
            val jsCallbackData = JsCallbackData(JSONObject(), "", "", "")
            whenever(mockDuckChatJSHelper.processJsCallbackMessage(anyString(), anyString(), anyOrNull(), anyOrNull())).thenReturn(jsCallbackData)
            testee.processJsCallbackMessage(
                DUCK_CHAT_FEATURE_NAME,
                "method",
                "id",
                data = null,
                false,
            ) { "someUrl" }
            verify(mockDuckChatJSHelper).processJsCallbackMessage(DUCK_CHAT_FEATURE_NAME, "method", "id", null)
            assertCommandIssued<Command.SendResponseToJs>()
        }

    @Test
    fun whenProcessJsCallbackMessageForDuckChatAndResponseIsNullThenDoNotSendCommand() =
        runTest {
            whenever(mockEnabledToggle.isEnabled()).thenReturn(true)
            whenever(mockDuckChatJSHelper.processJsCallbackMessage(anyString(), anyString(), anyOrNull(), anyOrNull())).thenReturn(null)
            testee.processJsCallbackMessage(
                DUCK_CHAT_FEATURE_NAME,
                "method",
                "id",
                data = null,
                false,
            ) { "someUrl" }
            verify(mockDuckChatJSHelper).processJsCallbackMessage(DUCK_CHAT_FEATURE_NAME, "method", "id", null)
            assertCommandNotIssued<Command.SendResponseToJs>()
        }

    @Test
    fun whenDuckChatMenuItemClickedAndItWasntUsedBeforeThenOpenDuckChatAndSendPixel() =
        runTest {
            whenever(mockDuckChat.wasOpenedBefore()).thenReturn(false)

            testee.onDuckChatMenuClicked()

            verify(mockPixel).fire(DuckChatPixelName.DUCK_CHAT_OPEN_BROWSER_MENU, mapOf("was_used_before" to "0"))
            verify(mockDuckChat).openDuckChat()
        }

    @Test
    fun whenDuckChatMenuItemClickedAndItWasUsedBeforeThenOpenDuckChatAndSendPixel() =
        runTest {
            whenever(mockDuckChat.wasOpenedBefore()).thenReturn(true)

            testee.onDuckChatMenuClicked()

            verify(mockPixel).fire(DuckChatPixelName.DUCK_CHAT_OPEN_BROWSER_MENU, mapOf("was_used_before" to "1"))
            verify(mockDuckChat).openDuckChat()
        }

    @Test
    fun whenOnDuckChatOmnibarButtonClickedWithFocusThenOpenDuckChatWithAutoPrompt() {
        testee.onDuckChatOmnibarButtonClicked(query = "example", hasFocus = true, isNtp = false)
        verify(mockDuckChat).openDuckChatWithAutoPrompt(query = "example")
    }

    @Test
    fun whenOnDuckChatOmnibarButtonClickedWithoutFocusThenOpenDuckChat() {
        testee.onDuckChatOmnibarButtonClicked(query = "example", hasFocus = false, isNtp = false)
        verify(mockDuckChat).openDuckChat()
    }

    @Test
    fun whenOnDuckChatOmnibarButtonClickedWithNullQueryAndFocusThenOpenDuckChatWithAutoPrompt() {
        testee.onDuckChatOmnibarButtonClicked(query = null, hasFocus = true, isNtp = false)
        verify(mockDuckChat).openDuckChatWithAutoPrompt(query = "")
    }

    @Test
    fun whenOnDuckChatOmnibarButtonClickedWithNullQueryWithoutFocusThenOpenDuckChat() {
        testee.onDuckChatOmnibarButtonClicked(query = null, hasFocus = false, isNtp = false)
        verify(mockDuckChat).openDuckChat()
    }

    @Test
    fun whenOnDuckChatOmnibarButtonClickedWhenOnNtpWithNullQueryAndWithFocusThenOpenDuckChat() {
        testee.onDuckChatOmnibarButtonClicked(query = null, hasFocus = true, isNtp = true)
        verify(mockDuckChat).openDuckChat()
    }

    @Test
    fun whenOnDuckChatOmnibarButtonClickedWhenOnNtpWithBlankQueryAndWithFocusThenOpenDuckChat() {
        testee.onDuckChatOmnibarButtonClicked(query = " ", hasFocus = true, isNtp = true)
        verify(mockDuckChat).openDuckChat()
    }

    @Test
    fun whenOnDuckChatOmnibarButtonClickedWhenOnNtpWithQueryAndWithFocusThenOpenDuckChatWithAutoPrompt() {
        testee.onDuckChatOmnibarButtonClicked(query = "example", hasFocus = true, isNtp = true)
        verify(mockDuckChat).openDuckChatWithAutoPrompt(query = "example")
    }

    @Test
    fun whenOnDuckChatOmnibarButtonClickedWhenOnNtpWithQueryAndWithoutFocusThenOpenDuckChat() {
        testee.onDuckChatOmnibarButtonClicked(query = "example", hasFocus = false, isNtp = true)
        verify(mockDuckChat).openDuckChat()
    }

    @Test
    fun whenPageFinishedWithMaliciousSiteBlockedThenDoNotUpdateSite() {
        testee.browserViewState.value =
            testee.browserViewState.value?.copy(
                browserShowing = false,
                maliciousSiteBlocked = true,
                maliciousSiteStatus = PHISHING,
            )
        val site: Site = mock()
        whenever(site.url).thenReturn("http://malicious.com")
        whenever(site.maliciousSiteStatus).thenReturn(PHISHING)
        testee.siteLiveData.value = site

        testee.pageFinished(mockWebView, WebViewNavigationState(mockStack, 100), exampleUrl)

        assertEquals("http://malicious.com", testee.siteLiveData.value?.url)
        assertEquals(PHISHING, testee.siteLiveData.value?.maliciousSiteStatus)
    }

    @Test
    fun whenPageCommitVisibleWithMaliciousSiteBlockedThenDoNotUpdateSite() {
        testee.browserViewState.value =
            testee.browserViewState.value?.copy(
                browserShowing = false,
                maliciousSiteBlocked = true,
                maliciousSiteStatus = PHISHING,
            )
        val site: Site = mock()
        whenever(site.url).thenReturn("http://malicious.com")
        whenever(site.maliciousSiteStatus).thenReturn(PHISHING)
        testee.siteLiveData.value = site

        testee.onPageCommitVisible(WebViewNavigationState(mockStack, 100), exampleUrl)

        assertEquals("http://malicious.com", testee.siteLiveData.value?.url)
        assertEquals(PHISHING, testee.siteLiveData.value?.maliciousSiteStatus)
    }

    @Test
    fun whenProgressChangedWithMaliciousSiteBlockedThenDoNotUpdateSite() {
        testee.browserViewState.value =
            testee.browserViewState.value?.copy(
                browserShowing = false,
                maliciousSiteBlocked = true,
                maliciousSiteStatus = PHISHING,
            )
        val site: Site = mock()
        whenever(site.url).thenReturn("http://malicious.com")
        whenever(site.maliciousSiteStatus).thenReturn(PHISHING)
        testee.siteLiveData.value = site

        testee.progressChanged(100, WebViewNavigationState(mockStack, 100))

        assertEquals("http://malicious.com", testee.siteLiveData.value?.url)
        assertEquals(PHISHING, testee.siteLiveData.value?.maliciousSiteStatus)
    }

    @Test
    fun whenAutoConsentPopupHandledWithMaliciousSiteBlockedThenDoNotPostCommand() {
        testee.browserViewState.value =
            testee.browserViewState.value?.copy(
                browserShowing = false,
                maliciousSiteBlocked = true,
                maliciousSiteStatus = PHISHING,
            )

        testee.onAutoConsentPopUpHandled(true)

        assertCommandNotIssued<ShowAutoconsentAnimation>()
    }

    @Test
    fun whenAutoConsentPopupHandledWithNotMaliciousSiteBlockedThenPostCommand() {
        testee.browserViewState.value =
            testee.browserViewState.value?.copy(
                browserShowing = true,
                maliciousSiteBlocked = false,
                maliciousSiteStatus = null,
            )

        testee.onAutoConsentPopUpHandled(true)

        assertCommandIssued<ShowAutoconsentAnimation>()
    }

    @Test
    fun whenAutoConsentPopupHandledWithFeatureToggleEnabledAndTrackersBlockedThenEnqueueCookiesAnimation() {
        whenever(mockAddressBarTrackersAnimationFeatureToggle.feature()).thenReturn(mockEnabledToggle)

        testee.browserViewState.value =
            testee.browserViewState.value?.copy(
                browserShowing = true,
                maliciousSiteBlocked = false,
                maliciousSiteStatus = null,
            )

        val site = givenCurrentSite("https://example.com")
        whenever(site.trackerCount).thenReturn(5)
        testee.siteLiveData.value = site

        testee.onAutoConsentPopUpHandled(true)

        assertCommandIssued<EnqueueCookiesAnimation> {
            assertTrue(isCosmetic)
        }
    }

    @Test
    fun whenAutoConsentPopupHandledWithFeatureToggleDisabledThenShowAutoconsentAnimation() {
        whenever(mockAddressBarTrackersAnimationFeatureToggle.feature()).thenReturn(mockDisabledToggle)

        testee.browserViewState.value =
            testee.browserViewState.value?.copy(
                browserShowing = true,
                maliciousSiteBlocked = false,
                maliciousSiteStatus = null,
            )

        testee.onAutoConsentPopUpHandled(true)

        assertCommandIssued<ShowAutoconsentAnimation>()
    }

    @Test
    fun whenAutoConsentPopupHandledWithFeatureToggleEnabledButNoTrackersThenShowAutoconsentAnimation() {
        whenever(mockAddressBarTrackersAnimationFeatureToggle.feature()).thenReturn(mockEnabledToggle)

        testee.browserViewState.value =
            testee.browserViewState.value?.copy(
                browserShowing = true,
                maliciousSiteBlocked = false,
                maliciousSiteStatus = null,
            )

        val site = givenCurrentSite("https://example.com")
        whenever(site.trackerCount).thenReturn(0)
        testee.siteLiveData.value = site

        testee.onAutoConsentPopUpHandled(true)

        assertCommandIssued<ShowAutoconsentAnimation>()
    }

    @Test
    fun whenVisitSiteThenUpdateLoadingViewStateAndOmnibarViewState() {
        testee.browserViewState.value =
            browserViewState().copy(
                browserShowing = false,
                maliciousSiteBlocked = true,
                showPrivacyShield = HighlightableButton.Gone,
                fireButton = HighlightableButton.Gone,
            )
        testee.loadingViewState.value = loadingViewState().copy(isLoading = false)
        testee.omnibarViewState.value = omnibarViewState().copy(isEditing = true)

        testee.onMaliciousSiteUserAction(VisitSite, exampleUrl.toUri(), Feed.PHISHING, false)

        assertEquals(
            loadingViewState().copy(
                isLoading = true,
                trackersAnimationEnabled = true,
                progress = 20,
                url = exampleUrl,
            ),
            loadingViewState(),
        )
        assertEquals(
            omnibarViewState().copy(
                omnibarText = exampleUrl,
                isEditing = false,
                navigationChange = true,
            ),
            omnibarViewState(),
        )
    }

    @Test
    fun whenVisitSiteThenUpdateLoadingViewStateAndOmnibarViewStateAndFullUrlDisabled() {
        isFullSiteAddressEnabled = false

        testee.browserViewState.value =
            browserViewState().copy(
                browserShowing = false,
                maliciousSiteBlocked = true,
                showPrivacyShield = HighlightableButton.Gone,
                fireButton = HighlightableButton.Gone,
            )
        testee.loadingViewState.value = loadingViewState().copy(isLoading = false)
        testee.omnibarViewState.value = omnibarViewState().copy(isEditing = true)

        testee.onMaliciousSiteUserAction(VisitSite, exampleUrl.toUri(), Feed.PHISHING, false)

        assertEquals(
            loadingViewState().copy(
                isLoading = true,
                trackersAnimationEnabled = true,
                progress = 20,
                url = exampleUrl,
            ),
            loadingViewState(),
        )
        assertEquals(
            omnibarViewState().copy(
                omnibarText = shortExampleUrl,
                isEditing = false,
                navigationChange = true,
            ),
            omnibarViewState(),
        )
    }

    @Test
    fun whenLeaveSiteAndCustomTabThenEmitCloseCustomTab() {
        testee.onMaliciousSiteUserAction(LeaveSite, exampleUrl.toUri(), Feed.PHISHING, true)
        assertCommandIssued<CloseCustomTab>()
    }

    @Test
    fun whenLeaveSiteAndNotCustomTabThenEmitEscapeMaliciousSite() =
        runTest {
            val mockLiveSelectedTab = mock<LiveData<TabEntity>>()
            whenever(mockLiveSelectedTab.value).thenReturn(TabEntity("ID"))
            whenever(mockTabRepository.liveSelectedTab).thenReturn(mockLiveSelectedTab)

            testee.onMaliciousSiteUserAction(LeaveSite, exampleUrl.toUri(), Feed.PHISHING, false)

            assertCommandIssued<EscapeMaliciousSite>()
            assertCommandIssued<LaunchNewTab>()
            verify(mockTabRepository).deleteTabAndSelectSource("ID")
        }

    @Test
    fun whenLearnMoreThenEmitOpenBrokenSiteLearnMore() {
        testee.onMaliciousSiteUserAction(LearnMore, exampleUrl.toUri(), Feed.PHISHING, false)
        assertCommandIssued<OpenBrokenSiteLearnMore>()
    }

    @Test
    fun whenReportErrorThenEmitOpenBrokenSiteLearnMore() {
        testee.onMaliciousSiteUserAction(ReportError, exampleUrl.toUri(), Feed.PHISHING, false)
        assertCommandIssued<ReportBrokenSiteError>()
    }

    @Test
    fun whenUserClicksDaxIntroSearchOptionsCtaDismissButtonThenHideOnboardingDaxBubbleCtaCommandIssuedAndPixelFired() =
        runTest {
            val cta = DaxIntroSearchOptionsCta(mockOnboardingStore, mockAppInstallStore)

            testee.onUserClickCtaDismissButton(cta)

            assertCommandIssued<HideOnboardingDaxBubbleCta> {
                assertEquals(cta, this.daxBubbleCta)
            }
            verify(mockPixel).fire(ONBOARDING_DAX_CTA_DISMISS_BUTTON, mapOf(PixelParameter.CTA_SHOWN to DAX_INITIAL_CTA))
        }

    @Test
    fun whenUserClicksDaxSerpCtaDismissButtonThenHideOnboardingDaxDialogCommandIssuedAndPixelFired() =
        runTest {
            val cta = DaxSerpCta(mockOnboardingStore, mockAppInstallStore, mockOnboardingDesignExperimentManager)

            testee.onUserClickCtaDismissButton(cta)

            assertCommandIssued<HideOnboardingDaxDialog> {
                assertEquals(cta, this.onboardingCta)
            }
            verify(mockPixel).fire(ONBOARDING_DAX_CTA_DISMISS_BUTTON, mapOf(PixelParameter.CTA_SHOWN to DAX_SERP_CTA))
        }

    @Test
    fun whenRecordErrorCodeThenProvideValueToErrorHandler() {
        val site = givenCurrentSite("some.domain")
        val siteCaptor = argumentCaptor<Site>()
        val errorUrl = "some.domain"
        val errorValue = "error value"

        testee.recordErrorCode(url = errorUrl, error = errorValue)

        verify(mockSiteErrorHandler).handleError(currentSite = siteCaptor.capture(), urlWithError = eq(errorUrl), error = eq(errorValue))
        assertEquals(site.url, siteCaptor.lastValue.url)
    }

    @Test
    fun whenRecordHttpErrorCodeThenProvideValueToErrorHandler() {
        val site = givenCurrentSite("some.domain")
        val siteCaptor = argumentCaptor<Site>()
        val errorUrl = "some.domain"
        val errorValue = -1

        testee.recordHttpErrorCode(url = errorUrl, statusCode = errorValue)

        verify(mockSiteHttpErrorHandler).handleError(currentSite = siteCaptor.capture(), urlWithError = eq(errorUrl), error = eq(errorValue))
        assertEquals(site.url, siteCaptor.lastValue.url)
    }

    @Test
    fun whenRecordErrorCodeNotMatchingCurrentSiteThenProvideValueToErrorHandler() {
        val site = givenCurrentSite("some.domain")
        val siteCaptor = argumentCaptor<Site>()
        val errorUrl = "error.com"
        val errorValue = "error value"

        testee.recordErrorCode(url = errorUrl, error = errorValue)

        verify(mockSiteErrorHandler).handleError(currentSite = siteCaptor.capture(), urlWithError = eq(errorUrl), error = eq(errorValue))
        assertEquals(site.url, siteCaptor.lastValue.url)
    }

    @Test
    fun whenRecordHttpErrorCodeNotMatchingCurrentSiteThenProvideValueToErrorHandler() {
        val site = givenCurrentSite("some.domain")
        val siteCaptor = argumentCaptor<Site>()
        val errorUrl = "error.com"
        val errorValue = -1

        testee.recordHttpErrorCode(url = errorUrl, statusCode = errorValue)

        verify(mockSiteHttpErrorHandler).handleError(currentSite = siteCaptor.capture(), urlWithError = eq(errorUrl), error = eq(errorValue))
        assertEquals(site.url, siteCaptor.lastValue.url)
    }

    @Test
    fun whenSiteLoadedThenAssignCachedErrors() {
        val site = givenCurrentSite("some.domain")

        verify(mockSiteErrorHandler).assignErrorsAndClearCache(site)
        verify(mockSiteHttpErrorHandler).assignErrorsAndClearCache(site)
    }

    @Test
    fun whenProcessJsCallbackMessageForSubscriptionsThenSendCommand() =
        runTest {
            val jsCallbackData = JsCallbackData(JSONObject(), "", "", "")
            whenever(mockSubscriptionsJSHelper.processJsCallbackMessage(anyString(), anyString(), anyOrNull(), anyOrNull()))
                .thenReturn(jsCallbackData)
            testee.processJsCallbackMessage(
                featureName = SUBSCRIPTIONS_FEATURE_NAME,
                method = "method",
                id = "id",
                data = null,
            ) { "someUrl" }
            verify(mockSubscriptionsJSHelper).processJsCallbackMessage(SUBSCRIPTIONS_FEATURE_NAME, "method", "id", null)
            assertCommandIssued<Command.SendResponseToJs>()
        }

    @Test
    fun whenProcessJsCallbackMessageForSubscriptionsAndResponseIsNullThenDoNotSendCommand() =
        runTest {
            whenever(mockDuckChatJSHelper.processJsCallbackMessage(anyString(), anyString(), anyOrNull(), anyOrNull())).thenReturn(null)
            testee.processJsCallbackMessage(
                featureName = SUBSCRIPTIONS_FEATURE_NAME,
                method = "method",
                id = "id",
                data = null,
            ) { "someUrl" }
            verify(mockSubscriptionsJSHelper).processJsCallbackMessage(SUBSCRIPTIONS_FEATURE_NAME, "method", "id", null)
            assertCommandNotIssued<Command.SendResponseToJs>()
        }

    @Test
    fun whenShowFullSiteAddressSettingDisabledThenOmnibarIsRefreshed() =
        runTest {
            testee.onViewResumed()

            assertCommandNotIssued<Command.RefreshOmnibar>()

            isFullSiteAddressEnabled = false

            testee.onViewResumed()

            assertCommandIssued<Command.RefreshOmnibar>()
        }

    @Test
    fun whenShowFullSiteAddressSettingEnabledThenOmnibarIsRefreshed() =
        runTest {
            isFullSiteAddressEnabled = false

            testee.onViewResumed()

            isFullSiteAddressEnabled = true

            testee.onViewResumed()

            assertCommandIssued<Command.RefreshOmnibar>()
        }

    @Test
    fun whenSetBrowserBackgroundWithBuckOnboardingEnabledAndLightModeEnabledThenSetBrowserBackgroundColorCommandIssuedWithCorrectColor() {
        whenever(mockOnboardingDesignExperimentManager.isBuckEnrolledAndEnabled()).thenReturn(true)

        testee.setBrowserBackground(lightModeEnabled = true)

        assertCommandIssued<Command.SetBrowserBackgroundColor> {
            assertEquals(CommonR.color.buckYellow, this.colorRes)
        }
    }

    @Test
    fun whenSetBrowserBackgroundWithBuckOnboardingEnabledAndLightModeDisabledThenSetBrowserBackgroundColorCommandIssuedWithCorrectColor() {
        whenever(mockOnboardingDesignExperimentManager.isBuckEnrolledAndEnabled()).thenReturn(true)

        testee.setBrowserBackground(lightModeEnabled = false)

        assertCommandIssued<Command.SetBrowserBackgroundColor> {
            assertEquals(CommonR.color.buckLightBlue, this.colorRes)
        }
    }

    @Test
    fun whenSetBrowserBackgroundWithBuckOnboardingDisabledAndLightModeEnabledThenSetBrowserBackgroundCommandIssuedWithCorrectColor() {
        whenever(mockOnboardingDesignExperimentManager.isBuckEnrolledAndEnabled()).thenReturn(false)

        testee.setBrowserBackground(lightModeEnabled = true)

        assertCommandIssued<Command.SetBrowserBackground> {
            assertEquals(R.drawable.onboarding_background_bitmap_light, this.backgroundRes)
        }
    }

    @Test
    fun whenSetBrowserBackgroundWithBuckOnboardingDisabledAndDarkModeEnabledThenSetBrowserBackgroundCommandIssuedWithCorrectColor() {
        whenever(mockOnboardingDesignExperimentManager.isBuckEnrolledAndEnabled()).thenReturn(false)

        testee.setBrowserBackground(lightModeEnabled = false)

        assertCommandIssued<Command.SetBrowserBackground> {
            assertEquals(R.drawable.onboarding_background_bitmap_dark, this.backgroundRes)
        }
    }

    @Test
    fun whenSetOnboardingDialogBackgroundWithBuckOnboardingAndLightModeEnabledThenSetOnboardingDialogBackgroundColorCommandIssuedWithCorrectColor() {
        whenever(mockOnboardingDesignExperimentManager.isBuckEnrolledAndEnabled()).thenReturn(true)

        testee.setOnboardingDialogBackground(lightModeEnabled = true)

        assertCommandIssued<Command.SetOnboardingDialogBackgroundColor> {
            assertEquals(CommonR.color.buckYellow, this.colorRes)
        }
    }

    @Test
    fun whenSetOnboardingDialogBackgroundWithBuckOnboardinAndDarkModeEnabledThenSetOnboardingDialogBackgroundColorCommandIssuedWithCorrectColor() {
        whenever(mockOnboardingDesignExperimentManager.isBuckEnrolledAndEnabled()).thenReturn(true)

        testee.setOnboardingDialogBackground(lightModeEnabled = false)

        assertCommandIssued<Command.SetOnboardingDialogBackgroundColor> {
            assertEquals(CommonR.color.buckLightBlue, this.colorRes)
        }
    }

    @Test
    fun whenSetOnboardingDialogBackgroundAndLightModeEnabledThenSetBrowserBackgroundCommandIssuedWithCorrectColor() {
        whenever(mockOnboardingDesignExperimentManager.isBuckEnrolledAndEnabled()).thenReturn(false)

        testee.setOnboardingDialogBackground(lightModeEnabled = true)

        assertCommandIssued<Command.SetOnboardingDialogBackground> {
            assertEquals(R.drawable.onboarding_background_bitmap_light, this.backgroundRes)
        }
    }

    @Test
    fun whenSetOnboardingDialogBackgroundAndDarkModeEnabledThenSetBrowserBackgroundCommandIssuedWithCorrectColor() {
        whenever(mockOnboardingDesignExperimentManager.isBuckEnrolledAndEnabled()).thenReturn(false)

        testee.setOnboardingDialogBackground(lightModeEnabled = false)

        assertCommandIssued<Command.SetOnboardingDialogBackground> {
            assertEquals(R.drawable.onboarding_background_bitmap_dark, this.backgroundRes)
        }
    }

    @Test
    fun whenInitializedThenQueryOrFullUrlIsEmptyString() {
        assertEquals("", omnibarViewState().queryOrFullUrl)
    }

    @Test
    fun whenLoadingUrlThenQueryOrFullUrlIsUpdated() =
        runTest {
            testee.omnibarViewState.value = omnibarViewState().copy(omnibarText = "foo", queryOrFullUrl = "foo")
            loadUrl("https://example.com")

            assertEquals("https://example.com", omnibarViewState().queryOrFullUrl)
        }

    @Test
    fun whenPageChangesToNewUrlThenViewStatesAreUpdatedCorrectly() =
        runTest {
            // Setup initial state
            val initialUrl = "https://example.com"
            val newUrl = "https://example.org"
            val title = "Example Page"

            // First load initial URL
            loadUrl(initialUrl)

            // Then navigate to a new URL with title
            testee.navigationStateChanged(buildWebNavigation(originalUrl = initialUrl, currentUrl = newUrl, title = title))

            // Verify browser view state is updated
            assertTrue(browserViewState().browserShowing)

            // Verify omnibar state is updated with the new URL
            assertEquals(newUrl, omnibarViewState().omnibarText)
            assertEquals(newUrl, omnibarViewState().queryOrFullUrl)

            // Verify loading state reflects the completed navigation
            assertFalse(loadingViewState().isLoading)
        }

    @Test
    fun whenPageChangesToNewUrlAndFullUrlDisabledThenViewStatesAreUpdatedCorrectly() =
        runTest {
            // Setup initial state
            val initialUrl = "https://example.org"
            val title = "Example Page"
            isFullSiteAddressEnabled = false

            // First load initial URL
            loadUrl(initialUrl)

            // Then navigate to a new URL with title
            testee.navigationStateChanged(buildWebNavigation(originalUrl = initialUrl, currentUrl = exampleUrl, title = title))

            // Verify browser view state is updated
            assertTrue(browserViewState().browserShowing)

            // Verify omnibar state is updated with the new URL
            assertEquals(shortExampleUrl, omnibarViewState().omnibarText)
            assertEquals(exampleUrl, omnibarViewState().queryOrFullUrl)

            // Verify loading state reflects the completed navigation
            assertFalse(loadingViewState().isLoading)
        }

    @Test
    fun whenNavigatingThenQueryOrFullUrlIsPreserved() =
        runTest {
            loadUrl(exampleUrl)

            testee.navigationStateChanged(buildWebNavigation(exampleUrl))

            assertEquals(exampleUrl, omnibarViewState().queryOrFullUrl)
        }

    @Test
    fun whenNavigatingAndFullUrlDisabledThenQueryOrFullUrlIsPreserved() =
        runTest {
            isFullSiteAddressEnabled = false

            loadUrl(exampleUrl)

            testee.navigationStateChanged(buildWebNavigation(exampleUrl))

            assertEquals(exampleUrl, omnibarViewState().queryOrFullUrl)
        }

    @Test
    fun whenRecoveringFromWarningPageThenQueryOrFullUrlIsEmptied() =
        runTest {
            loadUrl("https://example.com")

            testee.recoverFromWarningPage(false)

            assertEquals("", omnibarViewState().omnibarText)
            assertEquals("", omnibarViewState().queryOrFullUrl)
        }

    @Test
    fun whenMaliciousSiteVisitedThenQueryOrFullUrlUpdates() {
        testee.browserViewState.value =
            browserViewState().copy(
                browserShowing = false,
                maliciousSiteBlocked = true,
            )
        testee.loadingViewState.value = loadingViewState().copy(isLoading = false)
        testee.omnibarViewState.value = omnibarViewState().copy(isEditing = true)

        testee.onMaliciousSiteUserAction(VisitSite, exampleUrl.toUri(), Feed.PHISHING, false)

        assertEquals(exampleUrl, omnibarViewState().queryOrFullUrl)
    }

    @Test
    fun whenMaliciousSiteVisitedAndFullUrlDisabledThenQueryOrFullUrlUpdates() {
        isFullSiteAddressEnabled = false

        testee.browserViewState.value =
            browserViewState().copy(
                browserShowing = false,
                maliciousSiteBlocked = true,
            )
        testee.loadingViewState.value = loadingViewState().copy(isLoading = false)
        testee.omnibarViewState.value = omnibarViewState().copy(isEditing = true)

        testee.onMaliciousSiteUserAction(VisitSite, exampleUrl.toUri(), Feed.PHISHING, false)

        assertEquals(exampleUrl, omnibarViewState().queryOrFullUrl)
    }

    @Test
    fun whenOnOmnibarPrivacyShieldButtonPressedWithTrackersBlockedCtaThenFirePrivacyDashClickedFromOnboardingPixel() =
        runTest {
            val cta =
                DaxTrackersBlockedCta(
                    onboardingStore = mockOnboardingStore,
                    appInstallStore = mockAppInstallStore,
                    trackers = emptyList(),
                    settingsDataStore = mockSettingsDataStore,
                    onboardingDesignExperimentManager = mockOnboardingDesignExperimentManager,
                )
            setCta(cta)

            testee.onOmnibarPrivacyShieldButtonPressed()

            verify(mockOnboardingDesignExperimentManager).firePrivacyDashClickedFromOnboardingPixel()
        }

    @Test
    fun whenOnOmnibarPrivacyShieldButtonPressedWithoutTrackersBlockedCtaThenDoNotFirePrivacyDashClickedFromOnboardingPixel() =
        runTest {
            val cta = DaxSerpCta(mockOnboardingStore, mockAppInstallStore, mockOnboardingDesignExperimentManager)
            setCta(cta)

            testee.onOmnibarPrivacyShieldButtonPressed()

            verify(mockOnboardingDesignExperimentManager, never()).firePrivacyDashClickedFromOnboardingPixel()
        }

    @Test
    fun whenOnUserSelectedOnboardingDialogOptionWithValidIndexThenFireOptionSelectedPixel() =
        runTest {
            val cta = DaxSerpCta(mockOnboardingStore, mockAppInstallStore, mockOnboardingDesignExperimentManager)
            val index = 2

            testee.onUserSelectedOnboardingDialogOption(cta, index)

            verify(mockOnboardingDesignExperimentManager).fireOptionSelectedPixel(cta, index)
        }

    @Test
    fun whenOnUserSelectedOnboardingDialogOptionWithNullIndexThenDoNotFireOptionSelectedPixel() =
        runTest {
            val cta = DaxSerpCta(mockOnboardingStore, mockAppInstallStore, mockOnboardingDesignExperimentManager)

            testee.onUserSelectedOnboardingDialogOption(cta, null)

            verify(mockOnboardingDesignExperimentManager, never()).fireOptionSelectedPixel(any(), any())
        }

    @Test
    fun whenOnUserSelectedOnboardingSiteSuggestionOptionWithValidIndexThenFireOptionSelectedPixel() =
        runTest {
            testee.onUserSelectedOnboardingSiteSuggestionOption(1)

            verify(mockOnboardingDesignExperimentManager).fireSiteSuggestionOptionSelectedPixel(1)
        }

    @Test
    fun whenUserSubmittedQueryNotSuggestedSearchOptionThenFireSearchOrNavCustomPixel() =
        runTest {
            whenever(mockOmnibarConverter.convertQueryToUrl("custom query", null)).thenReturn("custom query")
            whenever(mockOnboardingStore.getSearchOptions()).thenReturn(emptyList())
            val cta = DaxIntroSearchOptionsCta(mockOnboardingStore, mockAppInstallStore)
            setCta(cta)

            testee.onUserSubmittedQuery("custom query")

            verify(mockOnboardingDesignExperimentManager).fireSearchOrNavCustomPixel()
        }

    @Test
    fun whenPageFinishedThenOnWebPageFinishedLoadingCalled() =
        runTest {
            val url = "https://example.com"
            val webViewNavState = WebViewNavigationState(mockStack, 100)

            testee.pageFinished(mockWebView, webViewNavState, url)

            verify(mockOnboardingDesignExperimentManager).onWebPageFinishedLoading(url)
        }

    @Test
    fun whenPageFinishedAndUpdateScriptOnPageFinishedEnabledThenAddDocumentStartJavaScript() =
        runTest {
            val url = "https://example.com"
            val webViewNavState = WebViewNavigationState(mockStack, 100)
            fakeAndroidConfigBrowserFeature.updateScriptOnPageFinished().setRawStoredState(State(true))

            testee.pageFinished(mockWebView, webViewNavState, url)

            assertEquals(1, fakeAddDocumentStartJavaScriptPlugins.cssPlugin.countInitted)
            assertEquals(1, fakeAddDocumentStartJavaScriptPlugins.otherPlugin.countInitted)
        }

    @Test
    fun whenPageFinishedAndUpdateScriptOnPageFinishedDisabledThenDoNotCallAddDocumentStartJavaScript() =
        runTest {
            val url = "https://example.com"
            val webViewNavState = WebViewNavigationState(mockStack, 100)
            fakeAndroidConfigBrowserFeature.updateScriptOnPageFinished().setRawStoredState(State(false))

            testee.pageFinished(mockWebView, webViewNavState, url)

            assertEquals(0, fakeAddDocumentStartJavaScriptPlugins.cssPlugin.countInitted)
            assertEquals(0, fakeAddDocumentStartJavaScriptPlugins.otherPlugin.countInitted)
        }

    @Test
    fun whenPrivacyProtectionsUpdatedAndUpdateScriptOnPageFinishedEnabledAndUpdateScriptOnProtectionsChangedEnabledThenAddDocumentStartJavaScript() =
        runTest {
            fakeAndroidConfigBrowserFeature.updateScriptOnPageFinished().setRawStoredState(State(true))
            fakeAndroidConfigBrowserFeature.updateScriptOnProtectionsChanged().setRawStoredState(State(true))

            testee.privacyProtectionsUpdated(mockWebView)

            assertEquals(1, fakeAddDocumentStartJavaScriptPlugins.cssPlugin.countInitted)
            assertEquals(1, fakeAddDocumentStartJavaScriptPlugins.otherPlugin.countInitted)
        }

    @Test
    fun whenPrivacyProtectionsUpdatedAndUpdateScriptOnPageFinishedTrueAndUpdateScriptOnProtectionsChangedFalseThenNotAddDocumentStartJavaScript() =
        runTest {
            fakeAndroidConfigBrowserFeature.updateScriptOnPageFinished().setRawStoredState(State(true))
            fakeAndroidConfigBrowserFeature.updateScriptOnProtectionsChanged().setRawStoredState(State(false))

            testee.privacyProtectionsUpdated(mockWebView)

            assertEquals(0, fakeAddDocumentStartJavaScriptPlugins.cssPlugin.countInitted)
            assertEquals(0, fakeAddDocumentStartJavaScriptPlugins.otherPlugin.countInitted)
        }

    @Test
    fun whenPrivacyProtectionsUpdatedAndUpdateScriptOnPageFinishedDisabledThenAddDocumentStartJavaScriptOnlyOnCSS() =
        runTest {
            fakeAndroidConfigBrowserFeature.updateScriptOnPageFinished().setRawStoredState(State(false))
            fakeAndroidConfigBrowserFeature.updateScriptOnProtectionsChanged().setRawStoredState(State(true))

            testee.privacyProtectionsUpdated(mockWebView)

            assertEquals(1, fakeAddDocumentStartJavaScriptPlugins.cssPlugin.countInitted)
            assertEquals(0, fakeAddDocumentStartJavaScriptPlugins.otherPlugin.countInitted)
        }

    @Test
    fun whenPrivacyProtectionsUpdatedAndUpdateScriptOnPageFinishedFalseAndUpdateScriptOnProtectionsChangedFalseThenNotAddDocumentStartJavaScript() =
        runTest {
            fakeAndroidConfigBrowserFeature.updateScriptOnPageFinished().setRawStoredState(State(false))
            fakeAndroidConfigBrowserFeature.updateScriptOnProtectionsChanged().setRawStoredState(State(false))

            testee.privacyProtectionsUpdated(mockWebView)

            assertEquals(0, fakeAddDocumentStartJavaScriptPlugins.cssPlugin.countInitted)
            assertEquals(0, fakeAddDocumentStartJavaScriptPlugins.otherPlugin.countInitted)
        }

    @Test
    fun whenCtaShownThenFireInContextDialogShownPixel() =
        runTest {
            val cta = DaxSerpCta(mockOnboardingStore, mockAppInstallStore, mockOnboardingDesignExperimentManager)
            setCta(cta)

            testee.onCtaShown()

            verify(mockOnboardingDesignExperimentManager).fireInContextDialogShownPixel(cta)
        }

    @Test
    fun whenFireMenuSelectedAndDaxFireButtonCtaThenFireFireButtonClickedFromOnboardingPixel() =
        runTest {
            val cta = OnboardingDaxDialogCta.DaxFireButtonCta(mockOnboardingStore, mockAppInstallStore, mockOnboardingDesignExperimentManager)
            setCta(cta)

            testee.onFireMenuSelected()

            verify(mockOnboardingDesignExperimentManager).fireFireButtonClickedFromOnboardingPixel()
        }

    @Test
    fun whenInputScreenEnabledAndSwitchToNewTabThenLaunchInputScreenCommandTriggered() =
        runTest {
            val initialTabId = "initial-tab"
            val initialTab =
                TabEntity(
                    tabId = initialTabId,
                    url = "https://example.com",
                    title = "EX",
                    skipHome = false,
                    viewed = true,
                    position = 0,
                )
            val ntpTabId = "ntp-tab"
            val ntpTab = TabEntity(tabId = ntpTabId, url = null, title = "", skipHome = false, viewed = true, position = 0)
            whenever(mockTabRepository.getTab(initialTabId)).thenReturn(initialTab)
            whenever(mockTabRepository.getTab(ntpTabId)).thenReturn(ntpTab)
            flowSelectedTab.emit(initialTab)

            testee.loadData(tabId = ntpTabId, initialUrl = null, skipHome = false, isExternal = false)
            mockDuckAiFeatureStateInputScreenOpenAutomaticallyFlow.emit(true)

            flowSelectedTab.emit(ntpTab)

            verify(mockCommandObserver, atLeastOnce()).onChanged(commandCaptor.capture())
            val commands = commandCaptor.allValues
            assertTrue(
                "LaunchInputScreen command should be triggered for null URL tab",
                commands.any { it is Command.LaunchInputScreen },
            )
        }

    @Test
    fun whenInputScreenDisabledAndSwitchToNewTabThenLaunchInputScreenCommandNotTriggered() =
        runTest {
            val initialTabId = "initial-tab"
            val initialTab =
                TabEntity(
                    tabId = initialTabId,
                    url = "https://example.com",
                    title = "EX",
                    skipHome = false,
                    viewed = true,
                    position = 0,
                )
            val ntpTabId = "ntp-tab"
            val ntpTab = TabEntity(tabId = ntpTabId, url = null, title = "", skipHome = false, viewed = true, position = 0)
            whenever(mockTabRepository.getTab(initialTabId)).thenReturn(initialTab)
            whenever(mockTabRepository.getTab(ntpTabId)).thenReturn(ntpTab)
            flowSelectedTab.emit(initialTab)

            testee.loadData(tabId = ntpTabId, initialUrl = null, skipHome = false, isExternal = false)
            mockDuckAiFeatureStateInputScreenOpenAutomaticallyFlow.emit(false)

            flowSelectedTab.emit(ntpTab)

            verify(mockCommandObserver, atLeastOnce()).onChanged(commandCaptor.capture())
            val commands = commandCaptor.allValues
            assertFalse(
                "LaunchInputScreen command should NOT be triggered when input screen is disabled",
                commands.any { it is Command.LaunchInputScreen },
            )
        }

    @Test
    fun whenInputScreenEnabledAndSwitchToTabWithUrlThenLaunchInputScreenCommandNotTriggered() =
        runTest {
            val initialTabId = "initial-tab"
            val initialTab =
                TabEntity(
                    tabId = initialTabId,
                    url = "https://example.com",
                    title = "EX",
                    skipHome = false,
                    viewed = true,
                    position = 0,
                )
            val targetTabId = "target-tab"
            val targetTab =
                TabEntity(
                    tabId = targetTabId,
                    url = "https://duckduckgo.com",
                    title = "DDG",
                    skipHome = false,
                    viewed = true,
                    position = 0,
                )
            whenever(mockTabRepository.getTab(initialTabId)).thenReturn(initialTab)
            whenever(mockTabRepository.getTab(targetTabId)).thenReturn(targetTab)
            flowSelectedTab.emit(initialTab)

            testee.loadData(tabId = targetTabId, initialUrl = null, skipHome = false, isExternal = false)
            mockDuckAiFeatureStateInputScreenOpenAutomaticallyFlow.emit(true)

            flowSelectedTab.emit(targetTab)

            verify(mockCommandObserver, atLeastOnce()).onChanged(commandCaptor.capture())
            val commands = commandCaptor.allValues
            assertFalse(
                "LaunchInputScreen command should NOT be triggered when switching to tab with existing URL",
                commands.any { it is Command.LaunchInputScreen },
            )
        }

    @Test
    fun whenInputScreenEnabledAndSwitchToNewTabThatManagedByAnotherViewModelThenLaunchInputScreenCommandNotTriggered() =
        runTest {
            val initialTabId = "initial-tab"
            val initialTab =
                TabEntity(
                    tabId = initialTabId,
                    url = "https://example.com",
                    title = "EX",
                    skipHome = false,
                    viewed = true,
                    position = 0,
                )
            val ntpTabId = "ntp-tab"
            val ntpTab = TabEntity(tabId = ntpTabId, url = null, title = "", skipHome = false, viewed = true, position = 0)
            whenever(mockTabRepository.getTab(initialTabId)).thenReturn(initialTab)
            whenever(mockTabRepository.getTab(ntpTabId)).thenReturn(ntpTab)
            flowSelectedTab.emit(initialTab)

            testee.loadData(tabId = initialTabId, initialUrl = null, skipHome = false, isExternal = false)
            mockDuckAiFeatureStateInputScreenOpenAutomaticallyFlow.emit(true)

            flowSelectedTab.emit(ntpTab)

            verify(mockCommandObserver, atLeastOnce()).onChanged(commandCaptor.capture())
            val commands = commandCaptor.allValues
            assertFalse(
                "LaunchInputScreen command should NOT be triggered when switching to new tab that's managed by another view model",
                commands.any { it is Command.LaunchInputScreen },
            )
        }

    @Test
    fun whenInputScreenPrefChangesToEnabledThenLaunchInputScreenCommandNotTriggered() =
        runTest {
            val initialTabId = "initial-tab"
            val initialTab =
                TabEntity(
                    tabId = initialTabId,
                    url = "https://example.com",
                    title = "EX",
                    skipHome = false,
                    viewed = true,
                    position = 0,
                )
            val ntpTabId = "ntp-tab"
            val ntpTab = TabEntity(tabId = ntpTabId, url = null, title = "", skipHome = false, viewed = true, position = 0)
            whenever(mockTabRepository.getTab(initialTabId)).thenReturn(initialTab)
            whenever(mockTabRepository.getTab(ntpTabId)).thenReturn(ntpTab)
            flowSelectedTab.emit(initialTab)

            testee.loadData(tabId = ntpTabId, initialUrl = null, skipHome = false, isExternal = false)
            mockDuckAiFeatureStateInputScreenOpenAutomaticallyFlow.emit(false)

            flowSelectedTab.emit(ntpTab)
            mockDuckAiFeatureStateInputScreenOpenAutomaticallyFlow.emit(true)

            verify(mockCommandObserver, atLeastOnce()).onChanged(commandCaptor.capture())
            val commands = commandCaptor.allValues
            assertFalse(
                "LaunchInputScreen command should NOT be triggered when preference is toggled",
                commands.any { it is Command.LaunchInputScreen },
            )
        }

    @Test
    fun whenInputScreenEnabledAndSwitchToNewTabOpenedFromAnotherTabThenLaunchInputScreenCommandNotTriggered() =
        runTest {
            val initialTabId = "initial-tab"
            val initialTab =
                TabEntity(
                    tabId = initialTabId,
                    url = "https://example.com",
                    title = "EX",
                    skipHome = false,
                    viewed = true,
                    position = 0,
                )
            val sourceTabId = "source-tab"
            val ntpTabId = "ntp-tab"
            val ntpTab =
                TabEntity(
                    tabId = ntpTabId,
                    url = null,
                    title = "",
                    skipHome = false,
                    viewed = true,
                    position = 0,
                    sourceTabId = sourceTabId,
                )
            whenever(mockTabRepository.getTab(initialTabId)).thenReturn(initialTab)
            whenever(mockTabRepository.getTab(ntpTabId)).thenReturn(ntpTab)
            flowSelectedTab.emit(initialTab)

            testee.loadData(tabId = ntpTabId, initialUrl = null, skipHome = false, isExternal = false)
            mockDuckAiFeatureStateInputScreenOpenAutomaticallyFlow.emit(true)

            flowSelectedTab.emit(ntpTab)

            verify(mockCommandObserver, atLeastOnce()).onChanged(commandCaptor.capture())
            val commands = commandCaptor.allValues
            assertFalse(
                "LaunchInputScreen command should NOT be triggered when switching to new tab that was opened from another tab",
                commands.any { it is Command.LaunchInputScreen },
            )
        }

    @Test
    fun whenInputScreenEnabledAndSwitchToNewTabNotOpenedFromAnotherTabThenLaunchInputScreenCommandTriggered() =
        runTest {
            val initialTabId = "initial-tab"
            val initialTab =
                TabEntity(
                    tabId = initialTabId,
                    url = "https://example.com",
                    title = "EX",
                    skipHome = false,
                    viewed = true,
                    position = 0,
                )
            val ntpTabId = "ntp-tab"
            val ntpTab =
                TabEntity(
                    tabId = ntpTabId,
                    url = null,
                    title = "",
                    skipHome = false,
                    viewed = true,
                    position = 0,
                    sourceTabId = null,
                )
            whenever(mockTabRepository.getTab(initialTabId)).thenReturn(initialTab)
            whenever(mockTabRepository.getTab(ntpTabId)).thenReturn(ntpTab)
            flowSelectedTab.emit(initialTab)

            testee.loadData(tabId = ntpTabId, initialUrl = null, skipHome = false, isExternal = false)
            mockDuckAiFeatureStateInputScreenOpenAutomaticallyFlow.emit(true)

            flowSelectedTab.emit(ntpTab)

            verify(mockCommandObserver, atLeastOnce()).onChanged(commandCaptor.capture())
            val commands = commandCaptor.allValues
            assertTrue(
                "LaunchInputScreen command should be triggered when switching to new tab that was NOT opened from another tab",
                commands.any { it is Command.LaunchInputScreen },
            )
        }

    @Test
    fun whenInputScreenEnabledAndExternalIntentProcessingThenLaunchInputScreenCommandSuppressed() =
        runTest {
            val initialTabId = "initial-tab"
            val initialTab =
                TabEntity(
                    tabId = initialTabId,
                    url = "https://example.com",
                    title = "EX",
                    skipHome = false,
                    viewed = true,
                    position = 0,
                )
            val ntpTabId = "ntp-tab"
            val ntpTab = TabEntity(tabId = ntpTabId, url = null, title = "", skipHome = false, viewed = true, position = 0)
            whenever(mockTabRepository.getTab(initialTabId)).thenReturn(initialTab)
            whenever(mockTabRepository.getTab(ntpTabId)).thenReturn(ntpTab)
            flowSelectedTab.emit(initialTab)

            testee.loadData(tabId = ntpTabId, initialUrl = null, skipHome = false, isExternal = false)
            mockDuckAiFeatureStateInputScreenOpenAutomaticallyFlow.emit(true)
            mockHasPendingTabLaunchFlow.emit(true)

            flowSelectedTab.emit(ntpTab)

            verify(mockCommandObserver, atLeastOnce()).onChanged(commandCaptor.capture())
            val commands = commandCaptor.allValues
            assertFalse(
                "LaunchInputScreen command should be suppressed when external intent processing is active",
                commands.any { it is Command.LaunchInputScreen },
            )
        }

    @Test
    fun whenInputScreenEnabledAndExternalIntentProcessingCompletedThenLaunchInputScreenCommandTriggered() =
        runTest {
            val initialTabId = "initial-tab"
            val initialTab =
                TabEntity(
                    tabId = initialTabId,
                    url = "https://example.com",
                    title = "EX",
                    skipHome = false,
                    viewed = true,
                    position = 0,
                )
            val ntpTabId = "ntp-tab"
            val ntpTab = TabEntity(tabId = ntpTabId, url = null, title = "", skipHome = false, viewed = true, position = 0)
            whenever(mockTabRepository.getTab(initialTabId)).thenReturn(initialTab)
            whenever(mockTabRepository.getTab(ntpTabId)).thenReturn(ntpTab)
            flowSelectedTab.emit(initialTab)

            testee.loadData(tabId = ntpTabId, initialUrl = null, skipHome = false, isExternal = false)
            mockDuckAiFeatureStateInputScreenOpenAutomaticallyFlow.emit(true)
            mockHasPendingTabLaunchFlow.emit(true)

            // Switch to a new tab with no URL
            flowSelectedTab.emit(ntpTab)

            // Complete external intent processing
            mockHasPendingTabLaunchFlow.emit(false)

            verify(mockCommandObserver, atLeastOnce()).onChanged(commandCaptor.capture())
            val commands = commandCaptor.allValues
            assertTrue(
                "LaunchInputScreen command should be triggered when external intent processing completes",
                commands.any { it is Command.LaunchInputScreen },
            )
        }

    @Test
    fun whenInputScreenEnabledAndDuckAiOpenThenLaunchInputScreenCommandSuppressed() =
        runTest {
            val initialTabId = "initial-tab"
            val initialTab =
                TabEntity(
                    tabId = initialTabId,
                    url = "https://example.com",
                    title = "EX",
                    skipHome = false,
                    viewed = true,
                    position = 0,
                )
            val ntpTabId = "ntp-tab"
            val ntpTab = TabEntity(tabId = ntpTabId, url = null, title = "", skipHome = false, viewed = true, position = 0)
            whenever(mockTabRepository.getTab(initialTabId)).thenReturn(initialTab)
            whenever(mockTabRepository.getTab(ntpTabId)).thenReturn(ntpTab)
            flowSelectedTab.emit(initialTab)

            testee.loadData(tabId = ntpTabId, initialUrl = null, skipHome = false, isExternal = false)
            mockDuckAiFeatureStateInputScreenOpenAutomaticallyFlow.emit(true)
            mockHasPendingDuckAiOpenFlow.emit(true)

            flowSelectedTab.emit(ntpTab)

            verify(mockCommandObserver, atLeastOnce()).onChanged(commandCaptor.capture())
            val commands = commandCaptor.allValues
            assertFalse(
                "LaunchInputScreen command should be suppressed when Duck.ai is opened",
                commands.any { it is Command.LaunchInputScreen },
            )
        }

    @Test
    fun whenInputScreenEnabledAndDuckAiClosedThenLaunchInputScreenCommandTriggered() =
        runTest {
            val initialTabId = "initial-tab"
            val initialTab =
                TabEntity(
                    tabId = initialTabId,
                    url = "https://example.com",
                    title = "EX",
                    skipHome = false,
                    viewed = true,
                    position = 0,
                )
            val ntpTabId = "ntp-tab"
            val ntpTab =
                TabEntity(
                    tabId = ntpTabId,
                    url = null,
                    title = "",
                    skipHome = false,
                    viewed = true,
                    position = 0,
                )
            whenever(mockTabRepository.getTab(initialTabId)).thenReturn(initialTab)
            whenever(mockTabRepository.getTab(ntpTabId)).thenReturn(ntpTab)
            flowSelectedTab.emit(initialTab)

            testee.loadData(tabId = ntpTabId, initialUrl = null, skipHome = false, isExternal = false)
            mockDuckAiFeatureStateInputScreenOpenAutomaticallyFlow.emit(true)
            mockHasPendingDuckAiOpenFlow.emit(true)

            // Switch to a new tab with no URL
            flowSelectedTab.emit(ntpTab)

            // Close Duck.ai
            mockHasPendingDuckAiOpenFlow.emit(false)

            verify(mockCommandObserver, atLeastOnce()).onChanged(commandCaptor.capture())
            val commands = commandCaptor.allValues
            assertTrue(
                "LaunchInputScreen command should be triggered when Duck.ai is closed",
                commands.any { it is Command.LaunchInputScreen },
            )
        }

    @Test
    fun whenEvaluateSerpLogoStateCalledWithDuckDuckGoUrlAndFeatureEnabledThenExtractSerpLogoCommandIssued() {
        whenever(mockSerpEasterEggLogoToggles.feature()).thenReturn(mockEnabledToggle)
        val ddgUrl = "https://duckduckgo.com/?q=test"
        val webViewNavState = WebViewNavigationState(mockStack, 100)

        testee.pageFinished(mockWebView, webViewNavState, ddgUrl)

        verify(mockCommandObserver, atLeastOnce()).onChanged(commandCaptor.capture())
        val commands = commandCaptor.allValues
        assertTrue(
            "ExtractSerpLogo command should be issued when SERP logos feature is enabled and URL is DuckDuckGo query",
            commands.any { it is Command.ExtractSerpLogo && it.currentUrl == ddgUrl },
        )
    }

    @Test
    fun whenEvaluateSerpLogoStateCalledWithDuckDuckGoUrlAndFeatureDisabledThenExtractSerpLogoCommandNotIssued() {
        whenever(mockSerpEasterEggLogoToggles.feature()).thenReturn(mockDisabledToggle)
        val ddgUrl = "https://duckduckgo.com/?q=test"
        val webViewNavState = WebViewNavigationState(mockStack, 100)

        testee.pageFinished(mockWebView, webViewNavState, ddgUrl)

        val commands = commandCaptor.allValues
        assertFalse(
            "ExtractSerpLogo command should NOT be issued when SERP logos feature is disabled",
            commands.any { it is Command.ExtractSerpLogo },
        )
    }

    @Test
    fun whenEvaluateSerpLogoStateCalledWithNonDuckDuckGoUrlAndFeatureEnabledThenExtractSerpLogoCommandNotIssued() {
        whenever(mockSerpEasterEggLogoToggles.feature()).thenReturn(mockEnabledToggle)
        val nonDdgUrl = "https://example.com/search?q=test"
        val webViewNavState = WebViewNavigationState(mockStack, 100)

        testee.pageFinished(mockWebView, webViewNavState, nonDdgUrl)

        val commands = commandCaptor.allValues
        assertFalse(
            "ExtractSerpLogo command should NOT be issued for non-DuckDuckGo URLs even when feature is enabled",
            commands.any { it is Command.ExtractSerpLogo },
        )
    }

    @Test
    fun whenEvaluateSerpLogoStateCalledWithNonDuckDuckGoUrlAndFeatureEnabledThenSerpLogoIsCleared() {
        whenever(mockSerpEasterEggLogoToggles.feature()).thenReturn(mockEnabledToggle)
        val nonDdgUrl = "https://example.com/search?q=test"
        val webViewNavState = WebViewNavigationState(mockStack, 100)

        testee.omnibarViewState.value = omnibarViewState().copy(serpLogo = SerpLogo.EasterEgg("some-logo-url"))
        testee.pageFinished(mockWebView, webViewNavState, nonDdgUrl)

        assertNull("SERP logo should be cleared when navigating to non-DuckDuckGo URL", omnibarViewState().serpLogo)
    }

    @UiThreadTest
    @Test
    fun whenConfigureWebViewThenCallAddDocumentStartJavaScript() =
        runTest {
            val mockCallback = mock<WebViewCompatMessageCallback>()
            val webView = DuckDuckGoWebView(context)
            assertEquals(0, fakeAddDocumentStartJavaScriptPlugins.cssPlugin.countInitted)

            testee.configureWebView(webView, mockCallback)

            assertEquals(1, fakeAddDocumentStartJavaScriptPlugins.cssPlugin.countInitted)
        }

    @UiThreadTest
    @Test
    fun whenConfigureWebViewThenCallAddMessageListener() =
        runTest {
            val mockCallback = mock<WebViewCompatMessageCallback>()
            val webView = DuckDuckGoWebView(context)
            assertFalse(fakeMessagingPlugins.plugin.registered)

            testee.configureWebView(webView, mockCallback)

            assertTrue(fakeMessagingPlugins.plugin.registered)
        }

    @UiThreadTest
    @Test
    fun whenPostMessageThenCallPostContentScopeMessage() =
        runTest {
            val data = SubscriptionEventData("feature", "method", JSONObject())
            val webView = DuckDuckGoWebView(context)

            assertFalse(fakePostMessageWrapperPlugins.plugin.postMessageCalled)

            testee.postContentScopeMessage(data, webView)

            assertTrue(fakePostMessageWrapperPlugins.plugin.postMessageCalled)
        }

    private fun aCredential(): LoginCredentials = LoginCredentials(domain = null, username = null, password = null)

    private fun assertShowHistoryCommandSent(expectedStackSize: Int) {
        assertCommandIssued<ShowBackNavigationHistory> {
            assertEquals(expectedStackSize, history.size)
        }
    }

    private fun buildNavigationHistoryStack(stackSize: Int) {
        val history = mutableListOf<NavigationHistoryEntry>()
        for (i in 0 until stackSize) {
            history.add(NavigationHistoryEntry(url = "$i.example.com"))
        }

        testee.navigationStateChanged(buildWebNavigation(navigationHistory = history))
    }

    private fun givenCustomHeadersProviderReturnsGpcHeader() {
        fakeCustomHeadersPlugin.headers = mapOf(GPC_HEADER to GPC_HEADER_VALUE)
    }

    private fun givenCustomHeadersProviderReturnsNoHeaders() {
        fakeCustomHeadersPlugin.headers = emptyMap()
    }

    private fun givenCustomHeadersProviderReturnsAndroidFeaturesHeader() {
        fakeCustomHeadersPlugin.headers = mapOf(X_DUCKDUCKGO_ANDROID_HEADER to "TEST_VALUE")
    }

    private suspend fun givenFireButtonPulsing() {
        whenever(mockExtendedOnboardingFeatureToggles.noBrowserCtas()).thenReturn(mockEnabledToggle)
        whenever(mockUserStageStore.getUserAppStage()).thenReturn(AppStage.DAX_ONBOARDING)
        dismissedCtaDaoChannel.send(listOf(DismissedCta(DAX_DIALOG_TRACKERS_FOUND)))
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

    private fun givenInvalidatedGlobalLayout() {
        testee.globalLayoutState.value = GlobalLayoutViewState.Invalidated
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
        whenever(site.nextUrl).thenReturn(domain)
        whenever(site.uri).thenReturn(Uri.parse(domain))
        whenever(site.realBrokenSiteContext).thenReturn(mockBrokenSiteContext)
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

    private fun aTabEntity(id: String): TabEntity = TabEntity(tabId = id, position = 0)

    private fun loadTabWithId(tabId: String) {
        testee.loadData(tabId, initialUrl = null, skipHome = false, isExternal = false)
    }

    private fun loadUrl(
        url: String?,
        title: String? = null,
        isBrowserShowing: Boolean = true,
    ) = runTest {
        whenever(mockDuckPlayer.observeUserPreferences()).thenReturn(flowOf(UserPreferences(false, Disabled)))

        setBrowserShowing(isBrowserShowing)
        testee.navigationStateChanged(buildWebNavigation(originalUrl = url, currentUrl = url, title = title))
    }

    @Suppress("SameParameterValue")
    private fun updateUrl(
        originalUrl: String?,
        currentUrl: String?,
        isBrowserShowing: Boolean,
    ) = runTest {
        whenever(mockDuckPlayer.isSimulatedYoutubeNoCookie(anyUri())).thenReturn(false)
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
        isMaliciousSiteBlocked: Boolean = false,
        canGoForward: Boolean = false,
        canGoBack: Boolean = false,
        stepsToPreviousPage: Int = 0,
    ) {
        testee.skipHome = skipHome
        setBrowserShowing(isBrowsing)
        setMaliciousSiteBlocked(isMaliciousSiteBlocked)
        val nav = buildWebNavigation(canGoForward = canGoForward, canGoBack = canGoBack, stepsToPreviousPage = stepsToPreviousPage)
        testee.navigationStateChanged(nav)
    }

    private fun setMaliciousSiteBlocked(isMaliciousSiteBlocked: Boolean) {
        testee.browserViewState.value = browserViewState().copy(maliciousSiteBlocked = isMaliciousSiteBlocked)
    }

    private fun captureCommands(): KArgumentCaptor<Command> {
        verify(mockCommandObserver, atLeastOnce()).onChanged(commandCaptor.capture())
        return commandCaptor
    }

    private fun clearAccessibilitySettings() {
        context
            .getSharedPreferences(AccessibilitySettingsSharedPreferences.FILENAME, Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
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

    private fun buildFileChooserParams(
        acceptTypes: Array<String>,
        captureEnabled: Boolean = false,
    ): FileChooserParams =
        object : FileChooserParams() {
            override fun getAcceptTypes(): Array<String> = acceptTypes

            override fun getMode(): Int = 0

            override fun isCaptureEnabled(): Boolean = captureEnabled

            override fun getTitle(): CharSequence? = null

            override fun getFilenameHint(): String? = null

            override fun createIntent(): Intent = Intent()
        }

    private fun privacyShieldState() = testee.privacyShieldViewState.value!!

    private fun ctaViewState() = testee.ctaViewState.value!!

    private fun browserViewState() = testee.browserViewState.value!!

    private fun omnibarViewState() = testee.omnibarViewState.value!!

    private fun loadingViewState() = testee.loadingViewState.value!!

    private fun autoCompleteViewState() = testee.autoCompleteViewState.value!!

    private fun findInPageViewState() = testee.findInPageViewState.value!!

    private fun globalLayoutViewState() = testee.globalLayoutState.value!!

    private fun browserGlobalLayoutViewState() = testee.globalLayoutState.value!! as GlobalLayoutViewState.Browser

    private fun accessibilityViewState() = testee.accessibilityViewState.value!!

    fun anyUri(): Uri = any()

    class FakeCapabilityChecker(
        var enabled: Boolean,
    ) : AutofillCapabilityChecker {
        override suspend fun isAutofillEnabledByConfiguration(url: String) = enabled

        override suspend fun canInjectCredentialsToWebView(url: String) = enabled

        override suspend fun canSaveCredentialsFromWebView(url: String) = enabled

        override suspend fun canGeneratePasswordFromWebView(url: String) = enabled

        override suspend fun canAccessCredentialManagementScreen() = enabled
    }

    class FakePluginPoint(
        val plugin: FakePrivacyProtectionTogglePlugin,
    ) : PluginPoint<PrivacyProtectionTogglePlugin> {
        override fun getPlugins(): Collection<PrivacyProtectionTogglePlugin> = listOf(plugin)
    }

    class FakePrivacyProtectionTogglePlugin : PrivacyProtectionTogglePlugin {
        var toggleOff = 0
        var toggleOn = 0

        override suspend fun onToggleOff(origin: PrivacyToggleOrigin) {
            toggleOff++
        }

        override suspend fun onToggleOn(origin: PrivacyToggleOrigin) {
            toggleOn++
        }
    }

    class FakeCustomHeadersProvider(
        var headers: Map<String, String>,
    ) : CustomHeadersProvider {
        override fun getCustomHeaders(url: String): Map<String, String> = headers
    }

    class FakeAddDocumentStartJavaScriptPlugin(
        override val context: String,
    ) : AddDocumentStartJavaScriptPlugin {
        var countInitted = 0
            private set

        override suspend fun addDocumentStartJavaScript(webView: WebView) {
            countInitted++
        }
    }

    class FakeAddDocumentStartJavaScriptPluginPoint : PluginPoint<AddDocumentStartJavaScriptPlugin> {
        val cssPlugin = FakeAddDocumentStartJavaScriptPlugin("contentScopeScripts")
        val otherPlugin = FakeAddDocumentStartJavaScriptPlugin("test")

        override fun getPlugins() = listOf(cssPlugin, otherPlugin)
    }

    class FakeWebMessagingPlugin : WebMessagingPlugin {
        var registered = false
            private set

        override suspend fun unregister(webView: WebView) {
            registered = false
        }

        override suspend fun register(
            jsMessageCallback: WebViewCompatMessageCallback,
            webView: WebView,
        ) {
            registered = true
        }

        override suspend fun postMessage(
            webView: WebView,
            subscriptionEventData: SubscriptionEventData,
        ) {
        }

        override val context: String
            get() = "test"
    }

    class FakeWebMessagingPluginPoint : PluginPoint<WebMessagingPlugin> {
        val plugin = FakeWebMessagingPlugin()

        override fun getPlugins(): Collection<WebMessagingPlugin> = listOf(plugin)
    }

    class FakePostMessageWrapperPlugin : PostMessageWrapperPlugin {
        var postMessageCalled = false
            private set

        override suspend fun postMessage(
            message: SubscriptionEventData,
            webView: WebView,
        ) {
            postMessageCalled = true
        }

        override val context: String
            get() = "contentScopeScripts"
    }

    class FakePostMessageWrapperPluginPoint : PluginPoint<PostMessageWrapperPlugin> {
        val plugin = FakePostMessageWrapperPlugin()

        override fun getPlugins(): Collection<PostMessageWrapperPlugin> = listOf(plugin)
    }

    @Test
    fun whenVpnMenuClickedWithNotSubscribedStateThenPixelFiredWithPillStatus() {
        testee.browserViewState.value = browserViewState().copy(
            vpnMenuState = VpnMenuState.NotSubscribed,
        )

        testee.onVpnMenuClicked()

        verify(mockPixel).fire(
            AppPixelName.MENU_ACTION_VPN_PRESSED,
            mapOf(PixelParameter.STATUS to "pill"),
        )
    }

    @Test
    fun whenVpnMenuClickedWithNotSubscribedNoPillStateThenPixelFiredWithNoPillStatus() {
        testee.browserViewState.value = browserViewState().copy(
            vpnMenuState = VpnMenuState.NotSubscribedNoPill,
        )

        testee.onVpnMenuClicked()

        verify(mockPixel).fire(
            AppPixelName.MENU_ACTION_VPN_PRESSED,
            mapOf(PixelParameter.STATUS to "no_pill"),
        )
    }

    @Test
    fun whenVpnMenuClickedWithSubscribedStateThenPixelFiredWithSubscribedStatus() {
        testee.browserViewState.value = browserViewState().copy(
            vpnMenuState = VpnMenuState.Subscribed(isVpnEnabled = true),
        )

        testee.onVpnMenuClicked()

        verify(mockPixel).fire(
            AppPixelName.MENU_ACTION_VPN_PRESSED,
            mapOf(PixelParameter.STATUS to "subscribed"),
        )
    }
}
