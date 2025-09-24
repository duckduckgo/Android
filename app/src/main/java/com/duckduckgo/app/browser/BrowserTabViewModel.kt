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
import android.graphics.Bitmap
import android.net.Uri
import android.net.http.SslCertificate
import android.os.Message
import android.print.PrintAttributes
import android.provider.MediaStore
import android.util.Patterns
import android.view.ContextMenu
import android.view.MenuItem
import android.view.View
import android.webkit.MimeTypeMap
import android.webkit.PermissionRequest
import android.webkit.SslErrorHandler
import android.webkit.URLUtil
import android.webkit.ValueCallback
import android.webkit.WebChromeClient.FileChooserParams
import android.webkit.WebView
import androidx.annotation.AnyThread
import androidx.annotation.VisibleForTesting
import androidx.core.net.toUri
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModel
import androidx.lifecycle.asLiveData
import androidx.lifecycle.viewModelScope
import androidx.webkit.JavaScriptReplyProxy
import com.duckduckgo.adclick.api.AdClickManager
import com.duckduckgo.anvil.annotations.ContributesViewModel
import com.duckduckgo.app.accessibility.data.AccessibilitySettingsDataStore
import com.duckduckgo.app.browser.LongPressHandler.RequiredAction
import com.duckduckgo.app.browser.SSLErrorType.EXPIRED
import com.duckduckgo.app.browser.SSLErrorType.GENERIC
import com.duckduckgo.app.browser.SSLErrorType.NONE
import com.duckduckgo.app.browser.SSLErrorType.UNTRUSTED_HOST
import com.duckduckgo.app.browser.SSLErrorType.WRONG_HOST
import com.duckduckgo.app.browser.SpecialUrlDetector.UrlType.AppLink
import com.duckduckgo.app.browser.SpecialUrlDetector.UrlType.NonHttpAppLink
import com.duckduckgo.app.browser.SpecialUrlDetector.UrlType.ShouldLaunchDuckChatLink
import com.duckduckgo.app.browser.SpecialUrlDetector.UrlType.ShouldLaunchPrivacyProLink
import com.duckduckgo.app.browser.WebViewErrorResponse.LOADING
import com.duckduckgo.app.browser.WebViewErrorResponse.OMITTED
import com.duckduckgo.app.browser.addtohome.AddToHomeCapabilityDetector
import com.duckduckgo.app.browser.applinks.AppLinksHandler
import com.duckduckgo.app.browser.camera.CameraHardwareChecker
import com.duckduckgo.app.browser.certificates.BypassedSSLCertificatesRepository
import com.duckduckgo.app.browser.certificates.remoteconfig.SSLCertificatesFeature
import com.duckduckgo.app.browser.commands.Command
import com.duckduckgo.app.browser.commands.Command.AddHomeShortcut
import com.duckduckgo.app.browser.commands.Command.AskToAutomateFireproofWebsite
import com.duckduckgo.app.browser.commands.Command.AskToDisableLoginDetection
import com.duckduckgo.app.browser.commands.Command.AskToFireproofWebsite
import com.duckduckgo.app.browser.commands.Command.AutocompleteItemRemoved
import com.duckduckgo.app.browser.commands.Command.BrokenSiteFeedback
import com.duckduckgo.app.browser.commands.Command.BypassMaliciousSiteWarning
import com.duckduckgo.app.browser.commands.Command.CancelIncomingAutofillRequest
import com.duckduckgo.app.browser.commands.Command.ChildTabClosed
import com.duckduckgo.app.browser.commands.Command.CloseCustomTab
import com.duckduckgo.app.browser.commands.Command.ConvertBlobToDataUri
import com.duckduckgo.app.browser.commands.Command.CopyAliasToClipboard
import com.duckduckgo.app.browser.commands.Command.CopyLink
import com.duckduckgo.app.browser.commands.Command.DeleteFavoriteConfirmation
import com.duckduckgo.app.browser.commands.Command.DeleteFireproofConfirmation
import com.duckduckgo.app.browser.commands.Command.DeleteSavedSiteConfirmation
import com.duckduckgo.app.browser.commands.Command.DialNumber
import com.duckduckgo.app.browser.commands.Command.DismissFindInPage
import com.duckduckgo.app.browser.commands.Command.DownloadImage
import com.duckduckgo.app.browser.commands.Command.EditWithSelectedQuery
import com.duckduckgo.app.browser.commands.Command.EmailSignEvent
import com.duckduckgo.app.browser.commands.Command.EscapeMaliciousSite
import com.duckduckgo.app.browser.commands.Command.ExtractSerpLogo
import com.duckduckgo.app.browser.commands.Command.ExtractUrlFromCloakedAmpLink
import com.duckduckgo.app.browser.commands.Command.FindInPageCommand
import com.duckduckgo.app.browser.commands.Command.GenerateWebViewPreviewImage
import com.duckduckgo.app.browser.commands.Command.HandleNonHttpAppLink
import com.duckduckgo.app.browser.commands.Command.HideBrokenSitePromptCta
import com.duckduckgo.app.browser.commands.Command.HideKeyboard
import com.duckduckgo.app.browser.commands.Command.HideKeyboardForChat
import com.duckduckgo.app.browser.commands.Command.HideOnboardingDaxBubbleCta
import com.duckduckgo.app.browser.commands.Command.HideOnboardingDaxDialog
import com.duckduckgo.app.browser.commands.Command.HideSSLError
import com.duckduckgo.app.browser.commands.Command.HideWarningMaliciousSite
import com.duckduckgo.app.browser.commands.Command.HideWebContent
import com.duckduckgo.app.browser.commands.Command.InjectEmailAddress
import com.duckduckgo.app.browser.commands.Command.LaunchAddWidget
import com.duckduckgo.app.browser.commands.Command.LaunchAutofillSettings
import com.duckduckgo.app.browser.commands.Command.LaunchBookmarksActivity
import com.duckduckgo.app.browser.commands.Command.LaunchFireDialogFromOnboardingDialog
import com.duckduckgo.app.browser.commands.Command.LaunchInputScreen
import com.duckduckgo.app.browser.commands.Command.LaunchNewTab
import com.duckduckgo.app.browser.commands.Command.LaunchPopupMenu
import com.duckduckgo.app.browser.commands.Command.LaunchPrivacyPro
import com.duckduckgo.app.browser.commands.Command.LaunchTabSwitcher
import com.duckduckgo.app.browser.commands.Command.LaunchVpnManagement
import com.duckduckgo.app.browser.commands.Command.LoadExtractedUrl
import com.duckduckgo.app.browser.commands.Command.OpenAppLink
import com.duckduckgo.app.browser.commands.Command.OpenBrokenSiteLearnMore
import com.duckduckgo.app.browser.commands.Command.OpenInNewBackgroundTab
import com.duckduckgo.app.browser.commands.Command.OpenInNewTab
import com.duckduckgo.app.browser.commands.Command.OpenMessageInNewTab
import com.duckduckgo.app.browser.commands.Command.PrintLink
import com.duckduckgo.app.browser.commands.Command.RefreshAndShowPrivacyProtectionDisabledConfirmation
import com.duckduckgo.app.browser.commands.Command.RefreshAndShowPrivacyProtectionEnabledConfirmation
import com.duckduckgo.app.browser.commands.Command.RefreshUserAgent
import com.duckduckgo.app.browser.commands.Command.ReportBrokenSiteError
import com.duckduckgo.app.browser.commands.Command.RequestFileDownload
import com.duckduckgo.app.browser.commands.Command.RequiresAuthentication
import com.duckduckgo.app.browser.commands.Command.ResetHistory
import com.duckduckgo.app.browser.commands.Command.SaveCredentials
import com.duckduckgo.app.browser.commands.Command.ScreenLock
import com.duckduckgo.app.browser.commands.Command.ScreenUnlock
import com.duckduckgo.app.browser.commands.Command.SendEmail
import com.duckduckgo.app.browser.commands.Command.SendResponseToJs
import com.duckduckgo.app.browser.commands.Command.SendSms
import com.duckduckgo.app.browser.commands.Command.SetBrowserBackground
import com.duckduckgo.app.browser.commands.Command.SetBrowserBackgroundColor
import com.duckduckgo.app.browser.commands.Command.SetOnboardingDialogBackground
import com.duckduckgo.app.browser.commands.Command.SetOnboardingDialogBackgroundColor
import com.duckduckgo.app.browser.commands.Command.ShareLink
import com.duckduckgo.app.browser.commands.Command.ShowAppLinkPrompt
import com.duckduckgo.app.browser.commands.Command.ShowAutoconsentAnimation
import com.duckduckgo.app.browser.commands.Command.ShowBackNavigationHistory
import com.duckduckgo.app.browser.commands.Command.ShowDomainHasPermissionMessage
import com.duckduckgo.app.browser.commands.Command.ShowEditSavedSiteDialog
import com.duckduckgo.app.browser.commands.Command.ShowEmailProtectionChooseEmailPrompt
import com.duckduckgo.app.browser.commands.Command.ShowErrorWithAction
import com.duckduckgo.app.browser.commands.Command.ShowExistingImageOrCameraChooser
import com.duckduckgo.app.browser.commands.Command.ShowFaviconsPrompt
import com.duckduckgo.app.browser.commands.Command.ShowFileChooser
import com.duckduckgo.app.browser.commands.Command.ShowFireproofWebSiteConfirmation
import com.duckduckgo.app.browser.commands.Command.ShowFullScreen
import com.duckduckgo.app.browser.commands.Command.ShowImageCamera
import com.duckduckgo.app.browser.commands.Command.ShowKeyboard
import com.duckduckgo.app.browser.commands.Command.ShowRemoveSearchSuggestionDialog
import com.duckduckgo.app.browser.commands.Command.ShowSSLError
import com.duckduckgo.app.browser.commands.Command.ShowSavedSiteAddedConfirmation
import com.duckduckgo.app.browser.commands.Command.ShowSitePermissionsDialog
import com.duckduckgo.app.browser.commands.Command.ShowSoundRecorder
import com.duckduckgo.app.browser.commands.Command.ShowUserCredentialSavedOrUpdatedConfirmation
import com.duckduckgo.app.browser.commands.Command.ShowVideoCamera
import com.duckduckgo.app.browser.commands.Command.ShowWarningMaliciousSite
import com.duckduckgo.app.browser.commands.Command.ShowWebContent
import com.duckduckgo.app.browser.commands.Command.ShowWebPageTitle
import com.duckduckgo.app.browser.commands.Command.ToggleReportFeedback
import com.duckduckgo.app.browser.commands.Command.WebShareRequest
import com.duckduckgo.app.browser.commands.Command.WebViewCompatScreenLock
import com.duckduckgo.app.browser.commands.Command.WebViewCompatWebShareRequest
import com.duckduckgo.app.browser.commands.Command.WebViewError
import com.duckduckgo.app.browser.commands.NavigationCommand
import com.duckduckgo.app.browser.customtabs.CustomTabPixelNames
import com.duckduckgo.app.browser.defaultbrowsing.prompts.AdditionalDefaultBrowserPrompts
import com.duckduckgo.app.browser.duckplayer.DUCK_PLAYER_FEATURE_NAME
import com.duckduckgo.app.browser.duckplayer.DUCK_PLAYER_PAGE_FEATURE_NAME
import com.duckduckgo.app.browser.duckplayer.DuckPlayerJSHelper
import com.duckduckgo.app.browser.favicon.FaviconManager
import com.duckduckgo.app.browser.favicon.FaviconSource.ImageFavicon
import com.duckduckgo.app.browser.favicon.FaviconSource.UrlFavicon
import com.duckduckgo.app.browser.history.NavigationHistoryAdapter.NavigationHistoryListener
import com.duckduckgo.app.browser.httperrors.HttpCodeSiteErrorHandler
import com.duckduckgo.app.browser.httperrors.HttpErrorPixelName
import com.duckduckgo.app.browser.httperrors.HttpErrorPixels
import com.duckduckgo.app.browser.httperrors.SiteErrorHandlerKillSwitch
import com.duckduckgo.app.browser.httperrors.StringSiteErrorHandler
import com.duckduckgo.app.browser.logindetection.FireproofDialogsEventHandler
import com.duckduckgo.app.browser.logindetection.FireproofDialogsEventHandler.Event
import com.duckduckgo.app.browser.logindetection.LoginDetected
import com.duckduckgo.app.browser.logindetection.NavigationAwareLoginDetector
import com.duckduckgo.app.browser.logindetection.NavigationEvent
import com.duckduckgo.app.browser.menu.VpnMenuStateProvider
import com.duckduckgo.app.browser.model.BasicAuthenticationCredentials
import com.duckduckgo.app.browser.model.BasicAuthenticationRequest
import com.duckduckgo.app.browser.model.LongPressTarget
import com.duckduckgo.app.browser.newtab.FavoritesQuickAccessAdapter
import com.duckduckgo.app.browser.omnibar.OmnibarEntryConverter
import com.duckduckgo.app.browser.omnibar.QueryOrigin
import com.duckduckgo.app.browser.omnibar.QueryOrigin.FromAutocomplete
import com.duckduckgo.app.browser.refreshpixels.RefreshPixelSender
import com.duckduckgo.app.browser.santize.NonHttpAppLinkChecker
import com.duckduckgo.app.browser.session.WebViewSessionStorage
import com.duckduckgo.app.browser.tabs.TabManager
import com.duckduckgo.app.browser.urlextraction.UrlExtractionListener
import com.duckduckgo.app.browser.viewstate.AccessibilityViewState
import com.duckduckgo.app.browser.viewstate.AutoCompleteViewState
import com.duckduckgo.app.browser.viewstate.BrowserViewState
import com.duckduckgo.app.browser.viewstate.CtaViewState
import com.duckduckgo.app.browser.viewstate.FindInPageViewState
import com.duckduckgo.app.browser.viewstate.GlobalLayoutViewState
import com.duckduckgo.app.browser.viewstate.GlobalLayoutViewState.Browser
import com.duckduckgo.app.browser.viewstate.GlobalLayoutViewState.Invalidated
import com.duckduckgo.app.browser.viewstate.HighlightableButton
import com.duckduckgo.app.browser.viewstate.LoadingViewState
import com.duckduckgo.app.browser.viewstate.OmnibarViewState
import com.duckduckgo.app.browser.viewstate.PrivacyShieldViewState
import com.duckduckgo.app.browser.viewstate.SavedSiteChangedViewState
import com.duckduckgo.app.browser.viewstate.VpnMenuState
import com.duckduckgo.app.browser.webview.MaliciousSiteBlockedWarningLayout
import com.duckduckgo.app.browser.webview.MaliciousSiteBlockedWarningLayout.Action.LearnMore
import com.duckduckgo.app.browser.webview.MaliciousSiteBlockedWarningLayout.Action.LeaveSite
import com.duckduckgo.app.browser.webview.MaliciousSiteBlockedWarningLayout.Action.ReportError
import com.duckduckgo.app.browser.webview.MaliciousSiteBlockedWarningLayout.Action.VisitSite
import com.duckduckgo.app.browser.webview.SCAM_PROTECTION_LEARN_MORE_URL
import com.duckduckgo.app.browser.webview.SslWarningLayout.Action
import com.duckduckgo.app.cta.ui.BrokenSitePromptDialogCta
import com.duckduckgo.app.cta.ui.Cta
import com.duckduckgo.app.cta.ui.CtaViewModel
import com.duckduckgo.app.cta.ui.DaxBubbleCta
import com.duckduckgo.app.cta.ui.HomePanelCta
import com.duckduckgo.app.cta.ui.OnboardingDaxDialogCta
import com.duckduckgo.app.di.AppCoroutineScope
import com.duckduckgo.app.dispatchers.ExternalIntentProcessingState
import com.duckduckgo.app.fire.fireproofwebsite.data.FireproofWebsiteEntity
import com.duckduckgo.app.fire.fireproofwebsite.data.FireproofWebsiteRepository
import com.duckduckgo.app.fire.fireproofwebsite.ui.AutomaticFireproofSetting.ALWAYS
import com.duckduckgo.app.fire.fireproofwebsite.ui.AutomaticFireproofSetting.ASK_EVERY_TIME
import com.duckduckgo.app.generalsettings.showonapplaunch.ShowOnAppLaunchOptionHandler
import com.duckduckgo.app.global.events.db.UserEventKey
import com.duckduckgo.app.global.events.db.UserEventsStore
import com.duckduckgo.app.global.model.MaliciousSiteStatus
import com.duckduckgo.app.global.model.PrivacyShield
import com.duckduckgo.app.global.model.Site
import com.duckduckgo.app.global.model.SiteFactory
import com.duckduckgo.app.global.model.domain
import com.duckduckgo.app.global.model.domainMatchesUrl
import com.duckduckgo.app.location.data.LocationPermissionType
import com.duckduckgo.app.onboardingdesignexperiment.OnboardingDesignExperimentManager
import com.duckduckgo.app.pixels.AppPixelName
import com.duckduckgo.app.pixels.AppPixelName.AUTOCOMPLETE_BANNER_DISMISSED
import com.duckduckgo.app.pixels.AppPixelName.AUTOCOMPLETE_BANNER_SHOWN
import com.duckduckgo.app.pixels.AppPixelName.AUTOCOMPLETE_RESULT_DELETED
import com.duckduckgo.app.pixels.AppPixelName.AUTOCOMPLETE_RESULT_DELETED_DAILY
import com.duckduckgo.app.pixels.AppPixelName.ONBOARDING_SEARCH_CUSTOM
import com.duckduckgo.app.pixels.AppPixelName.ONBOARDING_VISIT_SITE_CUSTOM
import com.duckduckgo.app.pixels.AppPixelName.TAB_MANAGER_CLICKED_DAILY
import com.duckduckgo.app.pixels.duckchat.createWasUsedBeforePixelParams
import com.duckduckgo.app.pixels.remoteconfig.AndroidBrowserConfigFeature
import com.duckduckgo.app.privacy.db.NetworkLeaderboardDao
import com.duckduckgo.app.privacy.db.UserAllowListRepository
import com.duckduckgo.app.settings.db.SettingsDataStore
import com.duckduckgo.app.statistics.api.StatisticsUpdater
import com.duckduckgo.app.statistics.pixels.Pixel
import com.duckduckgo.app.statistics.pixels.Pixel.PixelParameter
import com.duckduckgo.app.statistics.pixels.Pixel.PixelType.Count
import com.duckduckgo.app.statistics.pixels.Pixel.PixelType.Daily
import com.duckduckgo.app.statistics.pixels.Pixel.PixelType.Unique
import com.duckduckgo.app.surrogates.SurrogateResponse
import com.duckduckgo.app.tabs.model.TabEntity
import com.duckduckgo.app.tabs.model.TabRepository
import com.duckduckgo.app.tabs.store.TabStatsBucketing
import com.duckduckgo.app.trackerdetection.model.TrackingEvent
import com.duckduckgo.app.usage.search.SearchCountDao
import com.duckduckgo.autofill.api.AutofillCapabilityChecker
import com.duckduckgo.autofill.api.domain.app.LoginCredentials
import com.duckduckgo.autofill.api.email.EmailManager
import com.duckduckgo.autofill.api.passwordgeneration.AutomaticSavedLoginsMonitor
import com.duckduckgo.autofill.impl.AutofillFireproofDialogSuppressor
import com.duckduckgo.brokensite.api.BrokenSitePrompt
import com.duckduckgo.brokensite.api.RefreshPattern
import com.duckduckgo.browser.api.UserBrowserProperties
import com.duckduckgo.browser.api.autocomplete.AutoComplete
import com.duckduckgo.browser.api.autocomplete.AutoComplete.AutoCompleteResult
import com.duckduckgo.browser.api.autocomplete.AutoComplete.AutoCompleteSuggestion
import com.duckduckgo.browser.api.autocomplete.AutoComplete.AutoCompleteSuggestion.AutoCompleteDefaultSuggestion
import com.duckduckgo.browser.api.autocomplete.AutoComplete.AutoCompleteSuggestion.AutoCompleteHistoryRelatedSuggestion.AutoCompleteHistorySearchSuggestion
import com.duckduckgo.browser.api.autocomplete.AutoComplete.AutoCompleteSuggestion.AutoCompleteHistoryRelatedSuggestion.AutoCompleteHistorySuggestion
import com.duckduckgo.browser.api.autocomplete.AutoComplete.AutoCompleteSuggestion.AutoCompleteHistoryRelatedSuggestion.AutoCompleteInAppMessageSuggestion
import com.duckduckgo.browser.api.autocomplete.AutoComplete.AutoCompleteSuggestion.AutoCompleteSearchSuggestion
import com.duckduckgo.browser.api.autocomplete.AutoComplete.AutoCompleteSuggestion.AutoCompleteUrlSuggestion.AutoCompleteBookmarkSuggestion
import com.duckduckgo.browser.api.autocomplete.AutoComplete.AutoCompleteSuggestion.AutoCompleteUrlSuggestion.AutoCompleteSwitchToTabSuggestion
import com.duckduckgo.browser.api.autocomplete.AutoCompleteSettings
import com.duckduckgo.browser.api.brokensite.BrokenSiteData
import com.duckduckgo.browser.api.brokensite.BrokenSiteData.ReportFlow.MENU
import com.duckduckgo.browser.api.brokensite.BrokenSiteData.ReportFlow.RELOAD_THREE_TIMES_WITHIN_20_SECONDS
import com.duckduckgo.browser.ui.omnibar.OmnibarPosition
import com.duckduckgo.browser.ui.omnibar.OmnibarPosition.TOP
import com.duckduckgo.common.ui.tabs.SwipingTabsFeatureProvider
import com.duckduckgo.common.utils.AppUrl
import com.duckduckgo.common.utils.AppUrl.ParamKey.QUERY
import com.duckduckgo.common.utils.ConflatedJob
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.common.utils.SingleLiveEvent
import com.duckduckgo.common.utils.baseHost
import com.duckduckgo.common.utils.device.DeviceInfo
import com.duckduckgo.common.utils.extensions.asLocationPermissionOrigin
import com.duckduckgo.common.utils.isMobileSite
import com.duckduckgo.common.utils.plugins.PluginPoint
import com.duckduckgo.common.utils.plugins.headers.CustomHeadersProvider
import com.duckduckgo.common.utils.toDesktopUri
import com.duckduckgo.di.scopes.FragmentScope
import com.duckduckgo.downloads.api.DownloadCommand
import com.duckduckgo.downloads.api.DownloadStateListener
import com.duckduckgo.downloads.api.FileDownloader
import com.duckduckgo.downloads.api.FileDownloader.PendingFileDownload
import com.duckduckgo.duckchat.api.DuckAiFeatureState
import com.duckduckgo.duckchat.api.DuckChat
import com.duckduckgo.duckchat.impl.helper.DuckChatJSHelper
import com.duckduckgo.duckchat.impl.helper.RealDuckChatJSHelper.Companion.DUCK_CHAT_FEATURE_NAME
import com.duckduckgo.duckchat.impl.pixel.DuckChatPixelName
import com.duckduckgo.duckplayer.api.DuckPlayer
import com.duckduckgo.duckplayer.api.DuckPlayer.DuckPlayerState.ENABLED
import com.duckduckgo.feature.toggles.api.Toggle
import com.duckduckgo.history.api.NavigationHistory
import com.duckduckgo.js.messaging.api.JsCallbackData
import com.duckduckgo.malicioussiteprotection.api.MaliciousSiteProtection.Feed
import com.duckduckgo.malicioussiteprotection.api.MaliciousSiteProtection.Feed.MALWARE
import com.duckduckgo.malicioussiteprotection.api.MaliciousSiteProtection.Feed.PHISHING
import com.duckduckgo.malicioussiteprotection.api.MaliciousSiteProtection.Feed.SCAM
import com.duckduckgo.mobile.android.R as CommonR
import com.duckduckgo.newtabpage.impl.pixels.NewTabPixels
import com.duckduckgo.privacy.config.api.AmpLinkInfo
import com.duckduckgo.privacy.config.api.AmpLinks
import com.duckduckgo.privacy.config.api.ContentBlocking
import com.duckduckgo.privacy.config.api.TrackingParameters
import com.duckduckgo.privacy.dashboard.api.PrivacyProtectionTogglePlugin
import com.duckduckgo.privacy.dashboard.api.PrivacyToggleOrigin
import com.duckduckgo.privacy.dashboard.api.ui.DashboardOpener
import com.duckduckgo.privacy.dashboard.api.ui.ToggleReports
import com.duckduckgo.privacyprotectionspopup.api.PrivacyProtectionsPopupExperimentExternalPixels
import com.duckduckgo.privacyprotectionspopup.api.PrivacyProtectionsPopupManager
import com.duckduckgo.privacyprotectionspopup.api.PrivacyProtectionsPopupUiEvent
import com.duckduckgo.privacyprotectionspopup.api.PrivacyProtectionsToggleUsageListener
import com.duckduckgo.savedsites.api.SavedSitesRepository
import com.duckduckgo.savedsites.api.models.BookmarkFolder
import com.duckduckgo.savedsites.api.models.SavedSite
import com.duckduckgo.savedsites.api.models.SavedSite.Bookmark
import com.duckduckgo.savedsites.api.models.SavedSite.Favorite
import com.duckduckgo.savedsites.impl.SavedSitesPixelName
import com.duckduckgo.savedsites.impl.dialogs.EditSavedSiteDialogFragment.DeleteBookmarkListener
import com.duckduckgo.savedsites.impl.dialogs.EditSavedSiteDialogFragment.EditSavedSiteListener
import com.duckduckgo.serp.logos.api.SerpEasterEggLogosToggles
import com.duckduckgo.serp.logos.api.SerpLogo
import com.duckduckgo.site.permissions.api.SitePermissionsManager
import com.duckduckgo.site.permissions.api.SitePermissionsManager.LocationPermissionRequest
import com.duckduckgo.site.permissions.api.SitePermissionsManager.SitePermissionQueryResponse
import com.duckduckgo.site.permissions.api.SitePermissionsManager.SitePermissions
import com.duckduckgo.subscriptions.api.SUBSCRIPTIONS_FEATURE_NAME
import com.duckduckgo.subscriptions.api.Subscriptions
import com.duckduckgo.subscriptions.api.SubscriptionsJSHelper
import com.duckduckgo.sync.api.favicons.FaviconsFetchingPrompt
import dagger.Lazy
import io.reactivex.schedulers.Schedulers
import java.net.URI
import java.net.URISyntaxException
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import logcat.LogPriority.ERROR
import logcat.LogPriority.INFO
import logcat.LogPriority.VERBOSE
import logcat.LogPriority.WARN
import logcat.asLog
import logcat.logcat
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import org.json.JSONArray
import org.json.JSONObject

private const val SCAM_PROTECTION_REPORT_ERROR_URL = "https://duckduckgo.com/malicious-site-protection/report-error?url="

@OptIn(ExperimentalCoroutinesApi::class)
@ContributesViewModel(FragmentScope::class)
class BrowserTabViewModel @Inject constructor(
    private val statisticsUpdater: StatisticsUpdater,
    private val queryUrlConverter: OmnibarEntryConverter,
    private val duckDuckGoUrlDetector: DuckDuckGoUrlDetector,
    private val siteFactory: SiteFactory,
    private val tabRepository: TabRepository,
    private val userAllowListRepository: UserAllowListRepository,
    private val contentBlocking: ContentBlocking,
    private val networkLeaderboardDao: NetworkLeaderboardDao,
    private val savedSitesRepository: SavedSitesRepository,
    private val fireproofWebsiteRepository: FireproofWebsiteRepository,
    private val navigationAwareLoginDetector: NavigationAwareLoginDetector,
    private val autoComplete: AutoComplete,
    private val appSettingsPreferencesStore: SettingsDataStore,
    private val autoCompleteSettings: AutoCompleteSettings,
    private val longPressHandler: LongPressHandler,
    private val webViewSessionStorage: WebViewSessionStorage,
    private val specialUrlDetector: SpecialUrlDetector,
    private val faviconManager: FaviconManager,
    private val addToHomeCapabilityDetector: AddToHomeCapabilityDetector,
    private val ctaViewModel: CtaViewModel,
    private val searchCountDao: SearchCountDao,
    private val pixel: Pixel,
    private val dispatchers: DispatcherProvider,
    private val userEventsStore: UserEventsStore,
    private val fileDownloader: FileDownloader,
    private val fireproofDialogsEventHandler: FireproofDialogsEventHandler,
    private val emailManager: EmailManager,
    private val accessibilitySettingsDataStore: AccessibilitySettingsDataStore,
    @AppCoroutineScope private val appCoroutineScope: CoroutineScope,
    private val appLinksHandler: AppLinksHandler,
    private val ampLinks: AmpLinks,
    private val trackingParameters: TrackingParameters,
    private val downloadCallback: DownloadStateListener,
    private val settingsDataStore: SettingsDataStore,
    private val autofillCapabilityChecker: AutofillCapabilityChecker,
    private val adClickManager: AdClickManager,
    private val autofillFireproofDialogSuppressor: AutofillFireproofDialogSuppressor,
    private val automaticSavedLoginsMonitor: AutomaticSavedLoginsMonitor,
    private val device: DeviceInfo,
    private val sitePermissionsManager: SitePermissionsManager,
    private val cameraHardwareChecker: CameraHardwareChecker,
    private val androidBrowserConfig: AndroidBrowserConfigFeature,
    private val privacyProtectionsPopupManager: PrivacyProtectionsPopupManager,
    private val privacyProtectionsToggleUsageListener: PrivacyProtectionsToggleUsageListener,
    private val privacyProtectionsPopupExperimentExternalPixels: PrivacyProtectionsPopupExperimentExternalPixels,
    private val faviconsFetchingPrompt: FaviconsFetchingPrompt,
    private val subscriptions: Subscriptions,
    private val sslCertificatesFeature: SSLCertificatesFeature,
    private val bypassedSSLCertificatesRepository: BypassedSSLCertificatesRepository,
    private val userBrowserProperties: UserBrowserProperties,
    private val history: NavigationHistory,
    private val newTabPixels: Lazy<NewTabPixels>, // Lazy to construct the instance and deps only when actually sending the pixel
    private val httpErrorPixels: Lazy<HttpErrorPixels>,
    private val duckPlayer: DuckPlayer,
    private val duckChat: DuckChat,
    private val duckAiFeatureState: DuckAiFeatureState,
    private val duckPlayerJSHelper: DuckPlayerJSHelper,
    private val duckChatJSHelper: DuckChatJSHelper,
    private val refreshPixelSender: RefreshPixelSender,
    private val privacyProtectionTogglePlugin: PluginPoint<PrivacyProtectionTogglePlugin>,
    private val showOnAppLaunchOptionHandler: ShowOnAppLaunchOptionHandler,
    private val customHeadersProvider: CustomHeadersProvider,
    private val toggleReports: ToggleReports,
    private val brokenSitePrompt: BrokenSitePrompt,
    private val tabStatsBucketing: TabStatsBucketing,
    private val additionalDefaultBrowserPrompts: AdditionalDefaultBrowserPrompts,
    private val swipingTabsFeature: SwipingTabsFeatureProvider,
    private val siteErrorHandlerKillSwitch: SiteErrorHandlerKillSwitch,
    private val siteErrorHandler: StringSiteErrorHandler,
    private val siteHttpErrorHandler: HttpCodeSiteErrorHandler,
    private val subscriptionsJSHelper: SubscriptionsJSHelper,
    private val tabManager: TabManager,
    private val addressDisplayFormatter: AddressDisplayFormatter,
    private val onboardingDesignExperimentManager: OnboardingDesignExperimentManager,
    private val serpEasterEggLogosToggles: SerpEasterEggLogosToggles,
    private val nonHttpAppLinkChecker: NonHttpAppLinkChecker,
    private val externalIntentProcessingState: ExternalIntentProcessingState,
    private val vpnMenuStateProvider: VpnMenuStateProvider,
) : WebViewClientListener,
    EditSavedSiteListener,
    DeleteBookmarkListener,
    UrlExtractionListener,
    ViewModel(),
    NavigationHistoryListener {

    private var buildingSiteFactoryJob: Job? = null
    private var hasUserSeenHistoryIAM = false
    private var lastAutoCompleteState: AutoCompleteViewState? = null
    private var lastFullSiteUrlEnabled: Boolean = settingsDataStore.isFullUrlEnabled

    // Map<String, Map<String, JavaScriptReplyProxy>>() = Map<Origin, Map<location.href, JavaScriptReplyProxy>>()
    private val fixedReplyProxyMap = mutableMapOf<String, Map<String, JavaScriptReplyProxy>>()

    data class FileChooserRequestedParams(
        val filePickingMode: Int,
        val acceptMimeTypes: List<String>,
    )

    val autoCompleteViewState: MutableLiveData<AutoCompleteViewState> = MutableLiveData()
    val browserViewState: MutableLiveData<BrowserViewState> = MutableLiveData()
    val globalLayoutState: MutableLiveData<GlobalLayoutViewState> = MutableLiveData()
    val loadingViewState: MutableLiveData<LoadingViewState> = MutableLiveData()
    val omnibarViewState: MutableLiveData<OmnibarViewState> = MutableLiveData()
    val findInPageViewState: MutableLiveData<FindInPageViewState> = MutableLiveData()
    val accessibilityViewState: MutableLiveData<AccessibilityViewState> = MutableLiveData()
    val ctaViewState: MutableLiveData<CtaViewState> = MutableLiveData()
    var siteLiveData: MutableLiveData<Site> = MutableLiveData()
    val privacyShieldViewState: MutableLiveData<PrivacyShieldViewState> = MutableLiveData()
    val buckTryASearchAnimationEnabled: MutableStateFlow<Boolean> = MutableStateFlow(false)
    val areFavoritesDisplayed = savedSitesRepository.getFavorites()
        .map { it.isNotEmpty() }
        .stateIn(viewModelScope, SharingStarted.Lazily, false)

    // if navigating from home, want to know if a site was loaded previously to decide whether to reset WebView
    private var returnedHomeAfterSiteLoaded = false
    var skipHome = false
    var hasCtaBeenShownForCurrentPage: AtomicBoolean = AtomicBoolean(false)
    val tabs: LiveData<List<TabEntity>> = tabRepository.liveTabs
    val liveSelectedTab: LiveData<TabEntity> = tabRepository.liveSelectedTab
    val command: SingleLiveEvent<Command> = SingleLiveEvent()
    private var refreshOnViewVisible = MutableStateFlow(true)
    private var ctaChangedTicker = MutableStateFlow("")
    val hiddenIds = MutableStateFlow(HiddenBookmarksIds())

    private var activeExperiments: List<Toggle>? = null

    data class HiddenBookmarksIds(
        val favorites: List<String> = emptyList(),
        val bookmarks: List<String> = emptyList(),
    )

    /*
      Used to prevent autofill credential picker from automatically showing
      Useful if user has done something that would result in a strange UX to then show the picker
      This only prevents against automatically showing; if the user taps on an autofill field directly, the dialog can still show
     */
    private var canAutofillSelectCredentialsDialogCanAutomaticallyShow = true

    val url: String?
        get() = site?.url

    val title: String?
        get() = site?.title

    private var locationPermissionRequest: LocationPermissionRequest? = null
    private val locationPermissionMessages: MutableMap<String, Boolean> = mutableMapOf()
    private val locationPermissionSession: MutableMap<String, LocationPermissionType> = mutableMapOf()

    @VisibleForTesting
    internal val autoCompleteStateFlow = MutableStateFlow("")
    private val fireproofWebsiteState: LiveData<List<FireproofWebsiteEntity>> = fireproofWebsiteRepository.getFireproofWebsites()

    @ExperimentalCoroutinesApi
    @FlowPreview
    private val showPulseAnimation: LiveData<Boolean> = ctaViewModel.showFireButtonPulseAnimation.asLiveData(
        context = viewModelScope.coroutineContext,
    )

    private var autoCompleteJob = ConflatedJob()

    private var site: Site? = null
        set(value) {
            field = value
            if (siteErrorHandlerKillSwitch.self().isEnabled()) {
                siteErrorHandler.assignErrorsAndClearCache(value)
                siteHttpErrorHandler.assignErrorsAndClearCache(value)
            }
        }
    private lateinit var tabId: String
    private var webNavigationState: WebNavigationState? = null
    private var httpsUpgraded = false
    private val browserStateModifier = BrowserStateModifier()
    private var faviconPrefetchJob: Job? = null
    private var deferredBlankSite: Job? = null
    private var accessibilityObserver: Job? = null
    private var isProcessingTrackingLink = false
    private var isLinkOpenedInNewTab = false
    private var allowlistRefreshTriggerJob: Job? = null

    private val fireproofWebsitesObserver = Observer<List<FireproofWebsiteEntity>> {
        browserViewState.value = currentBrowserViewState().copy(isFireproofWebsite = isFireproofWebsite())
    }

    private val fireproofDialogEventObserver = Observer<Event> { event ->
        command.value = when (event) {
            is Event.AskToDisableLoginDetection -> AskToDisableLoginDetection
            is Event.FireproofWebSiteSuccess -> ShowFireproofWebSiteConfirmation(event.fireproofWebsiteEntity)
        }
    }

    @ExperimentalCoroutinesApi
    private val fireButtonAnimation = Observer<Boolean> { shouldShowAnimation ->
        logcat(INFO) { "shouldShowAnimation $shouldShowAnimation" }
        if (currentBrowserViewState().fireButton is HighlightableButton.Visible) {
            browserViewState.value = currentBrowserViewState().copy(fireButton = HighlightableButton.Visible(highlighted = shouldShowAnimation))
        }

        if (shouldShowAnimation) {
            registerAndScheduleDismissAction()
        }
    }

    private fun registerAndScheduleDismissAction() {
        viewModelScope.launch(dispatchers.io()) {
            val fireButtonHighlightedEvent = userEventsStore.getUserEvent(UserEventKey.FIRE_BUTTON_HIGHLIGHTED)
            if (fireButtonHighlightedEvent == null) {
                userEventsStore.registerUserEvent(UserEventKey.FIRE_BUTTON_HIGHLIGHTED)
            }
            val pulseElapsedTime = System.currentTimeMillis() - (fireButtonHighlightedEvent?.timestamp ?: System.currentTimeMillis())
            val pulsePendingTime = ONE_HOUR_IN_MS - pulseElapsedTime
            delay(pulsePendingTime)
            ctaViewModel.dismissPulseAnimation()
        }
    }

    private val loginDetectionObserver = Observer<LoginDetected> { loginEvent ->
        logcat(INFO) { "LoginDetection for $loginEvent" }
        viewModelScope.launch(dispatchers.io()) {
            val canPromptAboutFireproofing = !autofillFireproofDialogSuppressor.isAutofillPreventingFireproofPrompts()

            if (!isFireproofWebsite(loginEvent.forwardedToDomain)) {
                withContext(dispatchers.main()) {
                    val showAutomaticFireproofDialog =
                        settingsDataStore.automaticFireproofSetting == ASK_EVERY_TIME && settingsDataStore.showAutomaticFireproofDialog
                    when {
                        showAutomaticFireproofDialog -> {
                            if (canPromptAboutFireproofing) {
                                command.value = AskToAutomateFireproofWebsite(FireproofWebsiteEntity(loginEvent.forwardedToDomain))
                            }
                        }

                        settingsDataStore.automaticFireproofSetting == ALWAYS ->
                            fireproofDialogsEventHandler.onUserConfirmedFireproofDialog(loginEvent.forwardedToDomain)

                        else -> {
                            if (canPromptAboutFireproofing) {
                                command.value = AskToFireproofWebsite(FireproofWebsiteEntity(loginEvent.forwardedToDomain))
                            }
                        }
                    }
                }
            }
        }
    }

    init {
        initializeViewStates()

        fireproofWebsiteState.observeForever(fireproofWebsitesObserver)
        fireproofDialogsEventHandler.event.observeForever(fireproofDialogEventObserver)
        navigationAwareLoginDetector.loginEventLiveData.observeForever(loginDetectionObserver)
        showPulseAnimation.observeForever(fireButtonAnimation)

        tabRepository.childClosedTabs.onEach { closedTab ->
            if (this@BrowserTabViewModel::tabId.isInitialized && tabId == closedTab) {
                command.value = ChildTabClosed
            }
        }.launchIn(viewModelScope)

        emailManager.signedInFlow().onEach { isSignedIn ->
            browserViewState.value = currentBrowserViewState().copy(isEmailSignedIn = isSignedIn)
            command.value = EmailSignEvent
        }.launchIn(viewModelScope)

        observeAccessibilitySettings()

        savedSitesRepository.getFavorites()
            .combine(hiddenIds) { favorites, hiddenIds ->
                favorites.filter { it.id !in hiddenIds.favorites }
            }
            .flowOn(dispatchers.io())
            .onEach { filteredFavourites ->
                withContext(dispatchers.main()) {
                    val favorites = filteredFavourites.map { FavoritesQuickAccessAdapter.QuickAccessFavorite(it) }
                    autoCompleteViewState.value = currentAutoCompleteViewState().copy(favorites = favorites)
                    val favorite = filteredFavourites.firstOrNull { it.url == url }
                    browserViewState.value = currentBrowserViewState().copy(favorite = favorite)
                }
            }
            .launchIn(viewModelScope)

        savedSitesRepository.getBookmarks()
            .flowOn(dispatchers.io())
            .combine(hiddenIds) { bookmarks, hiddenIds ->
                bookmarks.filter { it.id !in hiddenIds.bookmarks }
            }
            .map { bookmarks ->
                val bookmark = bookmarks.firstOrNull { it.url == url }
                val isFavorite = currentBrowserViewState().favorite != null
                browserViewState.value = currentBrowserViewState().copy(bookmark = bookmark?.copy(isFavorite = isFavorite))
            }
            .flowOn(dispatchers.main())
            .launchIn(viewModelScope)

        viewModelScope.launch(dispatchers.io()) {
            ctaChangedTicker.asStateFlow()
                .onEach { ticker ->
                    logcat(VERBOSE) { "RMF: $ticker" }

                    if (ticker.isEmpty()) return@onEach
                    if (currentBrowserViewState().browserShowing) return@onEach

                    val cta = currentCtaViewState().cta?.takeUnless { it ->
                        it is HomePanelCta
                    }

                    withContext(dispatchers.main()) {
                        ctaViewState.value = currentCtaViewState().copy(
                            cta = cta,
                        )
                    }
                }
                .flowOn(dispatchers.io())
                .launchIn(viewModelScope)
        }

        privacyProtectionsPopupManager.viewState
            .onEach { popupViewState ->
                browserViewState.value = currentBrowserViewState().copy(privacyProtectionsPopupViewState = popupViewState)
            }
            .launchIn(viewModelScope)

        duckPlayer.observeUserPreferences()
            .onEach { preferences ->
                command.value = duckPlayerJSHelper.userPreferencesUpdated(preferences)
            }
            .flowOn(dispatchers.main())
            .launchIn(viewModelScope)

        vpnMenuStateProvider.getVpnMenuState()
            .onEach { vpnMenuState ->
                browserViewState.value = currentBrowserViewState().copy(vpnMenuState = vpnMenuState)
            }
            .flowOn(dispatchers.main())
            .launchIn(viewModelScope)

        additionalDefaultBrowserPrompts.showSetAsDefaultPopupMenuItem
            .onEach {
                browserViewState.value = currentBrowserViewState().copy(showSelectDefaultBrowserMenuItem = it)
            }
            .launchIn(viewModelScope)

        // auto-launch input screen for new, empty tabs (New Tab Page)
        combine(
            externalIntentProcessingState.hasPendingTabLaunch,
            externalIntentProcessingState.hasPendingDuckAiOpen,
        ) { hasPendingTabLaunch, hasPendingDuckAiOpen ->
            hasPendingTabLaunch || hasPendingDuckAiOpen
        }.flatMapLatest {
            if (it) {
                // suppress auto-launch while processing external intents (for example, opening links from other apps)
                // this prevents the New Tab Page from incorrectly triggering the input screen when the app
                // is started via external intent while previously left on NTP
                emptyFlow()
            } else {
                tabRepository.flowSelectedTab
                    .distinctUntilChangedBy { selectedTab -> selectedTab?.tabId } // only observe when the tab changes and ignore further updates
                    .filter { selectedTab ->
                        // fire event when activating a new, empty tab
                        // (has no URL and wasn't opened from another tab)
                        val showInputScreenAutomatically = duckAiFeatureState.showInputScreenAutomaticallyOnNewTab.value
                        val isActiveTab = ::tabId.isInitialized && selectedTab?.tabId == tabId
                        val isOpenedFromAnotherTab = selectedTab?.sourceTabId != null
                        showInputScreenAutomatically && isActiveTab && selectedTab?.url.isNullOrBlank() && !isOpenedFromAnotherTab
                    }
                    .flowOn(dispatchers.main()) // don't use the immediate dispatcher so that the tabId field has a chance to initialize
            }
        }.onEach {
            // whenever an event fires, so the user switched to a new tab page, launch the input screen
            command.value = LaunchInputScreen
        }.launchIn(viewModelScope)
    }

    fun loadData(
        tabId: String,
        initialUrl: String?,
        skipHome: Boolean,
        isExternal: Boolean,
    ) {
        this.tabId = tabId
        this.skipHome = skipHome
        siteLiveData = tabRepository.retrieveSiteData(tabId)
        site = siteLiveData.value

        initialUrl?.let { buildSiteFactory(it, stillExternal = isExternal) }
    }

    fun onViewReady() {
        url?.let {
            onUserSubmittedQuery(it)
        }
    }

    fun onViewRecreated() {
        observeAccessibilitySettings()
    }

    fun observeAccessibilitySettings() {
        accessibilityObserver?.cancel()
        accessibilityObserver = accessibilitySettingsDataStore.settingsFlow()
            .combine(refreshOnViewVisible.asStateFlow(), ::Pair)
            .onEach { (settings, viewVisible) ->
                logcat(VERBOSE) { "Accessibility: newSettings $settings, $viewVisible" }
                val shouldRefreshWebview =
                    (currentAccessibilityViewState().forceZoom != settings.forceZoom) || currentAccessibilityViewState().refreshWebView
                accessibilityViewState.value =
                    currentAccessibilityViewState().copy(
                        fontSize = settings.fontSize,
                        forceZoom = settings.forceZoom,
                        refreshWebView = shouldRefreshWebview,
                    )
            }.launchIn(viewModelScope)
    }

    override fun getCurrentTabId(): String = tabId

    fun onMessageProcessed() {
        showBrowser()
    }

    fun downloadCommands(): Flow<DownloadCommand> {
        return downloadCallback.commands()
    }

    // updateMaliciousSiteStatus should be false, unless you know the site is safe or malicious
    private fun buildSiteFactory(
        url: String,
        title: String? = null,
        stillExternal: Boolean? = false,
        updateMaliciousSiteStatus: Boolean = false,
        maliciousSiteStatus: MaliciousSiteStatus? = null,
    ) {
        logcat(VERBOSE) {
            """
            buildSiteFactory for url=$url, 
            updateMaliciousSiteStatus=$updateMaliciousSiteStatus, 
            maliciousSiteStatus=$maliciousSiteStatus
            """.trimIndent()
        }
        if (buildingSiteFactoryJob?.isCompleted == false) {
            logcat(INFO) { "Cancelling existing work to build SiteMonitor for $url" }
            buildingSiteFactoryJob?.cancel()
        }
        val externalLaunch = stillExternal ?: false
        site = siteFactory.buildSite(url, tabId, title, httpsUpgraded, externalLaunch)
        if (updateMaliciousSiteStatus) {
            site?.maliciousSiteStatus = maliciousSiteStatus
        }
        site?.activeContentScopeExperiments = activeExperiments
        onSiteChanged()
        buildingSiteFactoryJob = viewModelScope.launch {
            site?.let {
                withContext(dispatchers.io()) {
                    siteFactory.loadFullSiteDetails(it)
                    onSiteChanged()
                }
            }
        }
    }

    @OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
    @SuppressLint("CheckResult")
    private fun configureAutoComplete() {
        autoCompleteJob += autoCompleteStateFlow
            .debounce(300)
            .distinctUntilChanged()
            .flatMapLatest { autoComplete.autoComplete(it) }
            .flowOn(dispatchers.io())
            .onEach { result ->
                if (result.suggestions.contains(AutoCompleteInAppMessageSuggestion)) {
                    hasUserSeenHistoryIAM = true
                }
                onAutoCompleteResultReceived(result)
            }
            .flowOn(dispatchers.main())
            .catch { t: Throwable? -> logcat(WARN) { "Failed to get search results: ${t?.asLog()}" } }
            .launchIn(viewModelScope)
    }

    private fun onAutoCompleteResultReceived(result: AutoCompleteResult) {
        val currentViewState = currentAutoCompleteViewState()
        currentViewState.copy(searchResults = AutoCompleteResult(result.query, result.suggestions)).also {
            lastAutoCompleteState = it
            autoCompleteViewState.value = it
        }
    }

    @VisibleForTesting
    public override fun onCleared() {
        buildingSiteFactoryJob?.cancel()
        autoCompleteJob.cancel()
        fireproofWebsiteState.removeObserver(fireproofWebsitesObserver)
        navigationAwareLoginDetector.loginEventLiveData.removeObserver(loginDetectionObserver)
        fireproofDialogsEventHandler.event.removeObserver(fireproofDialogEventObserver)
        showPulseAnimation.removeObserver(fireButtonAnimation)
        super.onCleared()
    }

    fun registerWebViewListener(
        browserWebViewClient: BrowserWebViewClient,
        browserChromeClient: BrowserChromeClient,
    ) {
        browserWebViewClient.webViewClientListener = this
        browserChromeClient.webViewClientListener = this
    }

    fun onViewResumed() {
        if (currentGlobalLayoutState() is Invalidated && currentBrowserViewState().browserShowing) {
            showErrorWithAction()
        }

        if (lastFullSiteUrlEnabled != settingsDataStore.isFullUrlEnabled) {
            lastFullSiteUrlEnabled = settingsDataStore.isFullUrlEnabled
            command.value = Command.RefreshOmnibar
        }
    }

    fun onViewVisible() {
        setAdClickActiveTabData(url)

        // we expect refreshCta to be called when a site is fully loaded if browsingShowing -trackers data available-.
        if (!currentBrowserViewState().browserShowing && !currentBrowserViewState().maliciousSiteBlocked) {
            viewModelScope.launch {
                val cta = refreshCta()
                showOrHideKeyboard(cta)
                if (onboardingDesignExperimentManager.isBuckEnrolledAndEnabled()) {
                    when (cta) {
                        is DaxBubbleCta.DaxIntroSearchOptionsCta -> {
                            // Let the keyboard show before showing the animation, using insets were problematic
                            delay(750.milliseconds)
                            buckTryASearchAnimationEnabled.value = true
                        }
                    }
                }
            }
        } else {
            command.value = HideKeyboard
        }

        browserViewState.value = currentBrowserViewState().copy(
            showDuckChatOption = duckAiFeatureState.showPopupMenuShortcut.value,
        )

        viewModelScope.launch {
            refreshOnViewVisible.emit(true)
        }
    }

    fun onViewHidden() {
        ctaViewState.value?.cta.let {
            if (it is BrokenSitePromptDialogCta) {
                command.value = HideBrokenSitePromptCta(it)
            }
        }
        skipHome = false
        viewModelScope.launch {
            downloadCallback
            refreshOnViewVisible.emit(false)
        }
    }

    fun userSelectedAutocomplete(suggestion: AutoCompleteSuggestion) {
        // send pixel before submitting the query and changing the autocomplete state to empty; otherwise will send the wrong params
        appCoroutineScope.launch(dispatchers.io()) {
            val autoCompleteViewState = currentAutoCompleteViewState()
            withContext(dispatchers.main()) {
                when (suggestion) {
                    is AutoCompleteDefaultSuggestion -> onUserSubmittedQuery(suggestion.phrase, FromAutocomplete(isNav = false))
                    is AutoCompleteBookmarkSuggestion -> onUserSubmittedQuery(suggestion.url, FromAutocomplete(isNav = true))
                    is AutoCompleteSearchSuggestion -> onUserSubmittedQuery(suggestion.phrase, FromAutocomplete(isNav = suggestion.isUrl))
                    is AutoCompleteHistorySuggestion -> onUserSubmittedQuery(suggestion.url, FromAutocomplete(isNav = true))
                    is AutoCompleteHistorySearchSuggestion -> onUserSubmittedQuery(suggestion.phrase, FromAutocomplete(isNav = false))
                    is AutoCompleteSwitchToTabSuggestion -> onUserSwitchedToTab(suggestion.tabId)
                    is AutoCompleteInAppMessageSuggestion -> return@withContext
                    is AutoCompleteSuggestion.AutoCompleteDuckAIPrompt -> onUserTappedDuckAiPromptAutocomplete(suggestion.phrase)
                }
            }
            autoComplete.fireAutocompletePixel(autoCompleteViewState.searchResults.suggestions, suggestion)
        }
    }

    fun userLongPressedAutocomplete(suggestion: AutoCompleteSuggestion) {
        when (suggestion) {
            is AutoCompleteHistorySuggestion, is AutoCompleteHistorySearchSuggestion -> showRemoveSearchSuggestionDialog(suggestion)
            else -> return
        }
    }

    private fun showRemoveSearchSuggestionDialog(suggestion: AutoCompleteSuggestion) {
        appCoroutineScope.launch(dispatchers.main()) {
            command.value = ShowRemoveSearchSuggestionDialog(suggestion)
        }
    }

    fun onRemoveSearchSuggestionConfirmed(
        suggestion: AutoCompleteSuggestion,
        omnibarText: String,
    ) {
        configureAutoComplete()

        appCoroutineScope.launch(dispatchers.io()) {
            pixel.fire(AUTOCOMPLETE_RESULT_DELETED)
            pixel.fire(AUTOCOMPLETE_RESULT_DELETED_DAILY, type = Daily())

            when (suggestion) {
                is AutoCompleteHistorySuggestion -> {
                    history.removeHistoryEntryByUrl(suggestion.url)
                }

                is AutoCompleteHistorySearchSuggestion -> {
                    history.removeHistoryEntryByQuery(suggestion.phrase)
                }

                else -> {}
            }
            withContext(dispatchers.main()) {
                autoCompleteStateFlow.value = omnibarText
                command.value = AutocompleteItemRemoved
            }
        }
    }

    fun onUserLongPressedBack() {
        val navigationHistory = webNavigationState?.navigationHistory ?: return

        // we don't want the current page, so drop the first entry. Also don't want too many, so take only most recent ones.
        val stack = navigationHistory
            .drop(1)
            .take(10)

        if (stack.isNotEmpty()) {
            command.value = ShowBackNavigationHistory(stack)
        }
    }

    fun onUserSubmittedQuery(
        query: String,
        queryOrigin: QueryOrigin = QueryOrigin.FromUser,
    ) {
        navigationAwareLoginDetector.onEvent(NavigationEvent.UserAction.NewQuerySubmitted)

        if (query.isBlank()) {
            return
        }

        if (currentGlobalLayoutState() is Invalidated) {
            recoverTabWithQuery(query)
            return
        }

        val cta = currentCtaViewState().cta

        if (cta is OnboardingDaxDialogCta) {
            onDismissOnboardingDaxDialog(cta)
        }

        when (cta) {
            is DaxBubbleCta.DaxIntroSearchOptionsCta,
            -> {
                if (!ctaViewModel.isSuggestedSearchOption(query)) {
                    pixel.fire(ONBOARDING_SEARCH_CUSTOM, type = Unique())
                    viewModelScope.launch {
                        onboardingDesignExperimentManager.fireSearchOrNavCustomPixel()
                    }
                }
            }

            is DaxBubbleCta.DaxIntroVisitSiteOptionsCta,
            is OnboardingDaxDialogCta.DaxSiteSuggestionsCta,
            -> {
                if (!ctaViewModel.isSuggestedSiteOption(query)) {
                    pixel.fire(ONBOARDING_VISIT_SITE_CUSTOM, type = Unique())
                }
            }

            is BrokenSitePromptDialogCta -> {
                viewModelScope.launch(dispatchers.main()) {
                    command.value = HideBrokenSitePromptCta(cta)
                }
            }
        }

        command.value = HideKeyboard
        val trimmedInput = query.trim()

        viewModelScope.launch(dispatchers.io()) {
            searchCountDao.incrementSearchCount()
        }

        val verticalParameter = extractVerticalParameter(url)
        var urlToNavigate = queryUrlConverter.convertQueryToUrl(trimmedInput, verticalParameter, queryOrigin)

        when (val type = specialUrlDetector.determineType(trimmedInput)) {
            is ShouldLaunchDuckChatLink -> {
                runCatching {
                    val queryParameter = urlToNavigate.toUri().getQueryParameter(QUERY)
                    if (queryParameter != null) {
                        duckChat.openDuckChatWithPrefill(queryParameter)
                    } else {
                        duckChat.openDuckChat()
                    }
                    return
                }
            }

            is ShouldLaunchPrivacyProLink -> {
                if (webNavigationState == null || webNavigationState?.hasNavigationHistory == false) {
                    closeCurrentTab()
                }
                command.value = LaunchPrivacyPro(urlToNavigate.toUri())
                return
            }

            is NonHttpAppLink -> {
                nonHttpAppLinkClicked(type)
            }

            is SpecialUrlDetector.UrlType.CloakedAmpLink -> {
                handleCloakedAmpLink(type.ampUrl)
            }

            else -> {
                if (type is SpecialUrlDetector.UrlType.ExtractedAmpLink) {
                    logcat { "AMP link detection: Using extracted URL: ${type.extractedUrl}" }
                    urlToNavigate = type.extractedUrl
                } else if (type is SpecialUrlDetector.UrlType.TrackingParameterLink) {
                    logcat { "Loading parameter cleaned URL: ${type.cleanedUrl}" }
                    urlToNavigate = type.cleanedUrl
                }

                if (shouldClearHistoryOnNewQuery()) {
                    returnedHomeAfterSiteLoaded = false
                    command.value = ResetHistory
                }

                fireQueryChangedPixel(trimmedInput)

                if (!appSettingsPreferencesStore.showAppLinksPrompt) {
                    appLinksHandler.updatePreviousUrl(urlToNavigate)
                    appLinksHandler.setUserQueryState(true)
                } else {
                    clearPreviousUrl()
                }

                site?.nextUrl = urlToNavigate
                command.value = NavigationCommand.Navigate(urlToNavigate, getUrlHeaders(urlToNavigate))
            }
        }

        globalLayoutState.value = Browser(isNewTabState = false)
        findInPageViewState.value = FindInPageViewState(visible = false)
        omnibarViewState.value = currentOmnibarViewState().copy(
            omnibarText = if (settingsDataStore.isFullUrlEnabled) trimmedInput else addressDisplayFormatter.getShortUrl(trimmedInput),
            queryOrFullUrl = trimmedInput,
            forceExpand = true,
        )
        browserViewState.value = currentBrowserViewState().copy(
            browserShowing = true,
            browserError = OMITTED,
            sslError = NONE,
            maliciousSiteBlocked = false,
            maliciousSiteStatus = null,
            lastQueryOrigin = queryOrigin,
        )
        autoCompleteViewState.value =
            currentAutoCompleteViewState().copy(showSuggestions = false, showFavorites = false, searchResults = AutoCompleteResult("", emptyList()))
    }

    private fun getUrlHeaders(url: String?): Map<String, String> {
        return url?.let { customHeadersProvider.getCustomHeaders(it) } ?: emptyMap()
    }

    private fun extractVerticalParameter(currentUrl: String?): String? {
        val url = currentUrl ?: return null

        return if (duckDuckGoUrlDetector.isDuckDuckGoVerticalUrl(url)) {
            duckDuckGoUrlDetector.extractVertical(url)
        } else {
            null
        }
    }

    private fun fireQueryChangedPixel(omnibarText: String) {
        val oldQuery = currentOmnibarViewState().queryOrFullUrl.toUri()
        val newQuery = omnibarText.toUri()

        if (Patterns.WEB_URL.matcher(oldQuery.toString()).matches()) return

        if (oldQuery == newQuery) {
            pixel.fire(String.format(Locale.US, AppPixelName.SERP_REQUERY.pixelName, PixelParameter.SERP_QUERY_NOT_CHANGED))
        } else if (oldQuery.toString().isNotBlank()) { // blank means no previous search, don't send pixel
            pixel.fire(String.format(Locale.US, AppPixelName.SERP_REQUERY.pixelName, PixelParameter.SERP_QUERY_CHANGED))
        }
    }

    private fun shouldClearHistoryOnNewQuery(): Boolean {
        val navigation = webNavigationState ?: return returnedHomeAfterSiteLoaded
        return !currentBrowserViewState().browserShowing && navigation.hasNavigationHistory
    }

    private suspend fun removeCurrentTabFromRepository() {
        val currentTab = tabRepository.liveSelectedTab.value
        currentTab?.let {
            adClickManager.clearTabId(it.tabId)
            tabRepository.deleteTabAndSelectSource(it.tabId)
        }
    }

    override fun willOverrideUrl(newUrl: String) {
        site?.nextUrl = newUrl
        logcat { "SSLError: willOverride is $newUrl" }
        navigationAwareLoginDetector.onEvent(NavigationEvent.Redirect(newUrl))
        val previousSiteStillLoading = currentLoadingViewState().isLoading
        if (previousSiteStillLoading) {
            showBlankContentfNewContentDelayed()
        }
    }

    override fun prefetchFavicon(url: String) {
        faviconPrefetchJob?.cancel()
        faviconPrefetchJob = viewModelScope.launch {
            val faviconFile = faviconManager.tryFetchFaviconForUrl(tabId = tabId, url = url)
            if (faviconFile != null) {
                tabRepository.updateTabFavicon(tabId, faviconFile.name)
            }
        }
    }

    override fun iconReceived(
        url: String,
        icon: Bitmap,
    ) {
        val currentTab = tabRepository.liveSelectedTab.value ?: return
        val currentUrl = currentTab.url ?: return
        if (currentUrl != url) {
            logcat { "Favicon received for a url $url, different than the current one $currentUrl" }
            return
        }
        viewModelScope.launch(dispatchers.io()) {
            val faviconFile = faviconManager.storeFavicon(currentTab.tabId, ImageFavicon(icon, url))
            faviconFile?.let {
                tabRepository.updateTabFavicon(tabId, faviconFile.name)
            }
        }
    }

    override fun iconReceived(
        visitedUrl: String,
        iconUrl: String,
    ) {
        val currentTab = tabRepository.liveSelectedTab.value ?: return
        val currentUrl = currentTab.url ?: return
        if (currentUrl.toUri().host != visitedUrl.toUri().host) {
            logcat { "Favicon received for a url $visitedUrl, different than the current one $currentUrl" }
            return
        }
        viewModelScope.launch {
            val faviconFile = faviconManager.storeFavicon(currentTab.tabId, UrlFavicon(iconUrl, visitedUrl))
            faviconFile?.let {
                tabRepository.updateTabFavicon(tabId, faviconFile.name)
            }
        }
    }

    override fun isDesktopSiteEnabled(): Boolean = currentBrowserViewState().isDesktopBrowsingMode

    override fun isTabInForeground(): Boolean {
        return if (swipingTabsFeature.isEnabled) {
            tabId == tabManager.getSelectedTabId()
        } else {
            true
        }
    }

    override fun closeCurrentTab() {
        viewModelScope.launch {
            removeCurrentTabFromRepository()
        }
    }

    private fun openNewTab() {
        command.value = GenerateWebViewPreviewImage

        if (swipingTabsFeature.isEnabled) {
            viewModelScope.launch {
                val emptyTab = tabRepository.getTabs().firstOrNull { it.url.isNullOrBlank() }?.tabId
                if (emptyTab != null) {
                    tabRepository.select(tabId = emptyTab)
                } else {
                    command.value = LaunchNewTab
                }
            }
        } else {
            command.value = LaunchNewTab
        }

        onUserDismissedCta(ctaViewState.value?.cta)
    }

    fun closeAndReturnToSourceIfBlankTab() {
        if (url == null) {
            closeAndSelectSourceTab()
        }
    }

    override fun closeAndSelectSourceTab() {
        viewModelScope.launch {
            removeAndSelectTabFromRepository()
        }
    }

    private suspend fun removeAndSelectTabFromRepository() {
        removeCurrentTabFromRepository()
    }

    fun onUserPressedForward() {
        navigationAwareLoginDetector.onEvent(NavigationEvent.UserAction.NavigateForward)
        if (!currentBrowserViewState().browserShowing) {
            browserViewState.value = browserStateModifier.copyForBrowserShowing(currentBrowserViewState())
            command.value = NavigationCommand.Refresh
        } else {
            command.value = NavigationCommand.NavigateForward
        }
    }

    fun onRefreshRequested(triggeredByUser: Boolean) {
        val omnibarContent = currentOmnibarViewState().queryOrFullUrl
        if (!Patterns.WEB_URL.matcher(omnibarContent).matches()) {
            fireQueryChangedPixel(omnibarContent)
        }
        navigationAwareLoginDetector.onEvent(NavigationEvent.UserAction.Refresh)
        if (currentGlobalLayoutState() is Invalidated) {
            recoverTabWithQuery(url.orEmpty())
        } else {
            command.value = NavigationCommand.Refresh
        }

        if (triggeredByUser) {
            site?.realBrokenSiteContext?.onUserTriggeredRefresh()
            site?.uri?.let {
                brokenSitePrompt.pageRefreshed(it)
            }
            privacyProtectionsPopupManager.onPageRefreshTriggeredByUser(isOmnibarAtTheTop = settingsDataStore.omnibarPosition == TOP)
        }
    }

    fun handleExternalLaunch(isExternal: Boolean) {
        if (isExternal) {
            site?.isExternalLaunch = isExternal
        }
    }

    fun urlUnchangedForExternalLaunchPurposes(
        oldUrl: String?,
        newUrl: String,
    ): Boolean {
        if (oldUrl == null) return false
        fun normalizeUrl(url: String): String {
            val regex = Regex("^(https?://)?(www\\.)?")
            var normalizedUrl = url.replace(regex, "")

            if (normalizedUrl.endsWith("/")) {
                normalizedUrl = normalizedUrl.dropLast(1)
            }

            return normalizedUrl
        }

        val normalizedOldUrl = normalizeUrl(oldUrl)
        val normalizedNewUrl = normalizeUrl(newUrl)
        return normalizedOldUrl == normalizedNewUrl
    }

    /**
     * Handles back navigation. Returns false if navigation could not be
     * handled at this level, giving system an opportunity to handle it
     *
     * @return true if navigation handled, otherwise false
     */
    fun onUserPressedBack(isCustomTab: Boolean = false): Boolean {
        navigationAwareLoginDetector.onEvent(NavigationEvent.UserAction.NavigateBack)
        val navigation = webNavigationState ?: return false
        val hasSourceTab = tabRepository.liveSelectedTab.value?.sourceTabId != null

        if (currentFindInPageViewState().visible) {
            dismissFindInView()
            return true
        }

        if (currentBrowserViewState().sslError != NONE) {
            command.postValue(HideSSLError)
            return true
        }

        if (currentBrowserViewState().maliciousSiteBlocked) {
            command.postValue(HideWarningMaliciousSite(navigation.canGoBack))
            return true
        }

        if (!currentBrowserViewState().browserShowing) {
            return false
        }

        if (navigation.canGoBack) {
            command.value = NavigationCommand.NavigateBack(navigation.stepsToPreviousPage)
            return true
        } else if (hasSourceTab && !isCustomTab) {
            viewModelScope.launch {
                removeCurrentTabFromRepository()
            }
            return true
        } else if (!skipHome && !isCustomTab) {
            command.value = ShowKeyboard
            navigateHome()
            return true
        }

        if (!isCustomTab) {
            logcat { "User pressed back and tab is set to skip home; need to generate WebView preview now" }
            command.value = GenerateWebViewPreviewImage
        }
        return false
    }

    private fun navigateHome() {
        site = null
        onSiteChanged()
        webNavigationState = null
        returnedHomeAfterSiteLoaded = true

        val browserState = browserStateModifier.copyForHomeShowing(currentBrowserViewState()).copy(
            canGoForward = currentGlobalLayoutState() !is Invalidated,
        )
        browserViewState.value = browserState

        findInPageViewState.value = FindInPageViewState()
        omnibarViewState.value = currentOmnibarViewState().copy(
            omnibarText = "",
            queryOrFullUrl = "",
            forceExpand = true,
        )
        loadingViewState.value = currentLoadingViewState().copy(isLoading = false)

        deleteTabPreview(tabId)
    }

    override fun goFullScreen(view: View) {
        command.value = ShowFullScreen(view)

        val currentState = currentBrowserViewState()
        browserViewState.value = currentState.copy(isFullScreen = true)
    }

    override fun exitFullScreen() {
        val currentState = currentBrowserViewState()
        browserViewState.value = currentState.copy(isFullScreen = false)
    }

    /*
     * This method shouldn't be called from outside the ViewModel.
     * Calling this method when an error page is shown might cause unexpected issues, if said error page is a consequence
     * of an error that prevents the page load from starting altogether. This currently only happens with malicious site protection
     * when the WebView is stopped before the page load starts (local blocks)
     * In such cases, by stopping the WebView, we're effectively stopping the load for the previously loaded
     * page, which then triggers onProgressChanged/onPageFinished/etc. If we allow those to trigger an unfiltered navigationStateChanged,
     * we will end up in a situation where we receive NewPage events for the previous page while the error is shown.
     */
    fun navigationStateChanged(newWebNavigationState: WebNavigationState) {
        val stateChange = newWebNavigationState.compare(webNavigationState)

        viewModelScope.launch {
            showOnAppLaunchOptionHandler.handleResolvedUrlStorage(
                currentUrl = newWebNavigationState.currentUrl,
                isRootOfTab = !newWebNavigationState.canGoBack,
                tabId = tabId,
            )
        }

        webNavigationState = newWebNavigationState

        if (!currentBrowserViewState().browserShowing) return

        canAutofillSelectCredentialsDialogCanAutomaticallyShow = true

        browserViewState.value = currentBrowserViewState().copy(
            canGoBack = newWebNavigationState.canGoBack || !skipHome,
            canGoForward = newWebNavigationState.canGoForward,
        )

        when (stateChange) {
            is WebNavigationStateChange.NewPage -> {
                val uri = stateChange.url.toUri()
                viewModelScope.launch(dispatchers.io()) {
                    if (duckPlayer.getDuckPlayerState() == ENABLED && duckPlayer.isSimulatedYoutubeNoCookie(uri)) {
                        duckPlayer.createDuckPlayerUriFromYoutubeNoCookie(uri)?.let {
                            withContext(dispatchers.main()) {
                                pageChanged(it, stateChange.title)
                            }
                        }
                    } else {
                        withContext(dispatchers.main()) {
                            pageChanged(stateChange.url, stateChange.title)
                        }
                    }
                    ctaViewState.value?.cta?.let { cta ->
                        if (cta is BrokenSitePromptDialogCta) {
                            withContext(dispatchers.main()) {
                                command.value = HideBrokenSitePromptCta(cta)
                            }
                        }
                    }
                }
            }

            is WebNavigationStateChange.PageCleared -> pageCleared()
            is WebNavigationStateChange.UrlUpdated -> {
                val uri = stateChange.url.toUri()
                viewModelScope.launch(dispatchers.io()) {
                    if (duckPlayer.getDuckPlayerState() == ENABLED && duckPlayer.isSimulatedYoutubeNoCookie(uri)) {
                        duckPlayer.createDuckPlayerUriFromYoutubeNoCookie(uri)?.let {
                            withContext(dispatchers.main()) {
                                urlUpdated(it)
                            }
                        }
                    } else {
                        withContext(dispatchers.main()) {
                            urlUpdated(stateChange.url)
                        }
                    }
                }
                ctaViewState.value?.cta?.let { cta ->
                    if (cta is BrokenSitePromptDialogCta) {
                        command.value = HideBrokenSitePromptCta(cta)
                    }
                }
            }

            is WebNavigationStateChange.PageNavigationCleared -> disableUserNavigation()
            else -> {}
        }

        if ((newWebNavigationState.progress ?: 0) >= SHOW_CONTENT_MIN_PROGRESS) {
            showWebContent()
        }
        navigationAwareLoginDetector.onEvent(NavigationEvent.WebNavigationEvent(stateChange))
    }

    override fun onPageContentStart(url: String) {
        showWebContent()
    }

    private fun showBlankContentfNewContentDelayed() {
        logcat(INFO) { "Blank: cancel job $deferredBlankSite" }
        deferredBlankSite?.cancel()
        deferredBlankSite = viewModelScope.launch(dispatchers.io()) {
            withContext(dispatchers.main()) {
                command.value = HideWebContent
            }
        }
        logcat(INFO) { "Blank: schedule new blank $deferredBlankSite" }
    }

    private fun showWebContent() {
        logcat(INFO) { "Blank: onsite changed cancel $deferredBlankSite" }
        deferredBlankSite?.cancel()
        command.value = ShowWebContent
    }

    private fun pageChanged(
        url: String,
        title: String?,
    ) {
        logcat(VERBOSE) { "Page changed: $url" }
        cleanupBlobDownloadReplyProxyMaps()

        hasCtaBeenShownForCurrentPage.set(false)
        buildSiteFactory(url, title, urlUnchangedForExternalLaunchPurposes(site?.url, url))
        setAdClickActiveTabData(url)

        val currentOmnibarViewState = currentOmnibarViewState()
        omnibarViewState.value = currentOmnibarViewState.copy(
            queryOrFullUrl = omnibarTextForUrl(url, true),
            omnibarText = omnibarTextForUrl(url, settingsDataStore.isFullUrlEnabled),
            forceExpand = true,
            serpLogo = null,
        )
        val currentBrowserViewState = currentBrowserViewState()
        val domain = site?.domain

        findInPageViewState.value = FindInPageViewState(visible = false)

        browserViewState.value = currentBrowserViewState.copy(
            browserShowing = true,
            canSaveSite = domain != null,
            addToHomeEnabled = domain != null,
            canSharePage = domain != null,
            showPrivacyShield = HighlightableButton.Visible(enabled = true),
            canReportSite = domain != null && !duckPlayer.isDuckPlayerUri(url),
            canChangePrivacyProtection = domain != null && !duckPlayer.isDuckPlayerUri(url),
            isPrivacyProtectionDisabled = false,
            canFindInPage = true,
            canChangeBrowsingMode = true,
            canFireproofSite = domain != null,
            isFireproofWebsite = isFireproofWebsite(),
            canPrintPage = domain != null,
            maliciousSiteBlocked = false,
            maliciousSiteStatus = null,
        )

        if (duckDuckGoUrlDetector.isDuckDuckGoQueryUrl(url)) {
            statisticsUpdater.refreshSearchRetentionAtb()
        }

        domain?.let { viewModelScope.launch { updateLoadingStatePrivacy(domain) } }
        domain?.let { viewModelScope.launch { updatePrivacyProtectionState(domain) } }

        allowlistRefreshTriggerJob?.cancel()
        if (domain != null) {
            allowlistRefreshTriggerJob = isDomainInUserAllowlist(domain)
                .drop(count = 1) // skip current state - we're only interested in change events
                .onEach { isInAllowList ->
                    if (isInAllowList) {
                        command.postValue(RefreshAndShowPrivacyProtectionDisabledConfirmation(domain))
                    } else {
                        command.postValue(RefreshAndShowPrivacyProtectionEnabledConfirmation(domain))
                    }
                }
                .launchIn(viewModelScope)
        }

        viewModelScope.launch { updateBookmarkAndFavoriteState(url) }

        val permissionOrigin = site?.uri?.host?.asLocationPermissionOrigin()
        permissionOrigin?.let { notifyPermanentLocationPermission(permissionOrigin) }

        registerSiteVisit()

        cacheAppLink(url)

        appLinksHandler.setUserQueryState(false)
        appLinksHandler.updatePreviousUrl(url)

        ampLinks.lastAmpLinkInfo?.let { lastAmpLinkInfo ->
            if (lastAmpLinkInfo.destinationUrl == null) {
                lastAmpLinkInfo.destinationUrl = url
            }
        }

        trackingParameters.lastCleanedUrl?.let {
            trackingParameters.lastCleanedUrl = null
            enableUrlParametersRemovedFlag()
        }

        isProcessingTrackingLink = false
        isLinkOpenedInNewTab = false

        automaticSavedLoginsMonitor.clearAutoSavedLoginId(tabId)

        site?.run {
            val hasBrowserError = currentBrowserViewState().browserError != OMITTED
            privacyProtectionsPopupManager.onPageLoaded(url, httpErrorCodeEvents, hasBrowserError)
        }
    }

    private fun cleanupBlobDownloadReplyProxyMaps() {
        fixedReplyProxyMap.clear()
    }

    private fun setAdClickActiveTabData(url: String?) {
        val sourceTabId = tabRepository.liveSelectedTab.value?.sourceTabId
        val sourceTabUrl = tabRepository.liveTabs.value?.firstOrNull { it.tabId == sourceTabId }?.url
        adClickManager.setActiveTabId(tabId, url, sourceTabId, sourceTabUrl)
    }

    private fun cacheAppLink(url: String?) {
        val urlType = specialUrlDetector.determineType(url)
        if (urlType is AppLink) {
            updatePreviousAppLink(urlType)
        } else {
            clearPreviousAppLink()
        }
    }

    private suspend fun updateLoadingStatePrivacy(domain: String) {
        val privacyProtectionDisabled = isPrivacyProtectionDisabled(domain)
        withContext(dispatchers.main()) {
            loadingViewState.value =
                currentLoadingViewState().copy(
                    trackersAnimationEnabled = !(privacyProtectionDisabled || currentBrowserViewState().maliciousSiteBlocked),
                    url = site?.url ?: "",
                )
        }
    }

    private suspend fun updatePrivacyProtectionState(domain: String) {
        val privacyProtectionDisabled = isPrivacyProtectionDisabled(domain)
        withContext(dispatchers.main()) {
            browserViewState.value = currentBrowserViewState().copy(isPrivacyProtectionDisabled = privacyProtectionDisabled)
        }
    }

    private suspend fun isPrivacyProtectionDisabled(domain: String): Boolean {
        return withContext(dispatchers.io()) {
            userAllowListRepository.isDomainInUserAllowList(domain) || contentBlocking.isAnException(domain)
        }
    }

    private fun isDomainInUserAllowlist(domain: String): Flow<Boolean> =
        userAllowListRepository
            .domainsInUserAllowListFlow()
            .map { allowlistedDomains -> domain in allowlistedDomains }
            .distinctUntilChanged()

    private suspend fun updateBookmarkAndFavoriteState(url: String) {
        val bookmark = getBookmark(url)
        val favorite = getFavorite(url)
        withContext(dispatchers.main()) {
            browserViewState.value = currentBrowserViewState().copy(
                bookmark = bookmark?.copy(isFavorite = favorite != null),
                favorite = favorite,
            )
        }
    }

    private suspend fun getBookmark(url: String): Bookmark? {
        return withContext(dispatchers.io()) {
            savedSitesRepository.getBookmark(url)
        }
    }

    private suspend fun getBookmarkFolder(bookmark: Bookmark?): BookmarkFolder? {
        if (bookmark == null) return null
        return withContext(dispatchers.io()) {
            savedSitesRepository.getFolder(bookmark.parentId)
        }
    }

    private suspend fun getFavorite(url: String): Favorite? {
        return withContext(dispatchers.io()) {
            savedSitesRepository.getFavorite(url)
        }
    }

    private fun notifyPermanentLocationPermission(domain: String) {
        viewModelScope.launch(dispatchers.io()) {
            if (sitePermissionsManager.hasSitePermanentPermission(domain, LocationPermissionRequest.RESOURCE_LOCATION_PERMISSION)) {
                logcat { "Location Permission: domain $domain site url ${site?.url} has location permission" }
                if (!locationPermissionMessages.containsKey(domain)) {
                    logcat { "Location Permission: We haven't shown message for $domain this session" }
                    setDomainHasLocationPermissionShown(domain)
                    if (shouldShowLocationPermissionMessage()) {
                        logcat { "Location Permission: Show location permission for $domain" }
                        withContext(dispatchers.main()) {
                            command.postValue(ShowDomainHasPermissionMessage(domain))
                        }
                    }
                }
            }
        }
    }

    private fun setDomainHasLocationPermissionShown(domain: String) {
        locationPermissionMessages[domain] = true
    }

    private fun shouldShowLocationPermissionMessage(): Boolean {
        val url = site?.url ?: return true
        return !duckDuckGoUrlDetector.isDuckDuckGoChatUrl(url)
    }

    private fun urlUpdated(url: String) {
        logcat(VERBOSE) { "Page url updated: $url" }
        site?.url = url
        onSiteChanged()
        val currentOmnibarViewState = currentOmnibarViewState()
        omnibarViewState.postValue(
            currentOmnibarViewState.copy(
                queryOrFullUrl = omnibarTextForUrl(url, true),
                omnibarText = omnibarTextForUrl(url, settingsDataStore.isFullUrlEnabled),
                forceExpand = false,
            ),
        )
        viewModelScope.launch { updateBookmarkAndFavoriteState(url) }
    }

    @VisibleForTesting
    fun stripBasicAuthFromUrl(url: String): String {
        try {
            val uri = URI(url)
            val userInfo = uri.userInfo

            if (userInfo != null) {
                val queryStr = uri.rawQuery?.let { "?$it" } ?: ""
                val uriFragment = uri.fragment?.let { "#$it" } ?: ""
                val portStr = if (uri.port != -1) ":${uri.port}" else ""
                return "${uri.scheme}://${uri.host}$portStr${uri.path}$queryStr$uriFragment"
            }
        } catch (e: URISyntaxException) {
            logcat(ERROR) { "Failed to parse url for auth stripping: ${e.asLog()}" }
            return url
        }
        return url
    }

    private fun omnibarTextForUrl(
        url: String?,
        showFullUrl: Boolean,
    ): String {
        if (url == null) return ""

        return if (duckDuckGoUrlDetector.isDuckDuckGoQueryUrl(url)) {
            duckDuckGoUrlDetector.extractQuery(url) ?: url
        } else {
            val strippedAuthUrl = stripBasicAuthFromUrl(url)
            if (showFullUrl) {
                strippedAuthUrl
            } else {
                addressDisplayFormatter.getShortUrl(strippedAuthUrl)
            }
        }
    }

    private fun pageCleared() {
        logcat(VERBOSE) { "Page cleared: $url" }
        site = null
        onSiteChanged()

        val currentBrowserViewState = currentBrowserViewState()
        browserViewState.value = currentBrowserViewState.copy(
            canSaveSite = false,
            addToHomeEnabled = false,
            canSharePage = false,
            showPrivacyShield = HighlightableButton.Visible(enabled = false),
            canReportSite = false,
            canFireproofSite = false,
            canPrintPage = false,
        )
        logcat { "showPrivacyShield=false, showSearchIcon=true, showClearButton=true" }
    }

    override fun pageRefreshed(refreshedUrl: String) {
        if (url == null || refreshedUrl == url) {
            logcat(VERBOSE) { "Page refreshed: $refreshedUrl" }
            pageChanged(refreshedUrl, title)
        }
    }

    override fun progressChanged(
        newProgress: Int,
        webViewNavigationState: WebViewNavigationState,
    ) {
        logcat(VERBOSE) { "Loading in progress $newProgress, url: ${webViewNavigationState.currentUrl}" }

        if (!currentBrowserViewState().maliciousSiteBlocked) {
            navigationStateChanged(webViewNavigationState)
        }

        if (!currentBrowserViewState().browserShowing) return

        val isLoading = newProgress < 100 || isProcessingTrackingLink
        val progress = currentLoadingViewState()
        if (progress.progress == newProgress) return
        val visualProgress = if (newProgress < FIXED_PROGRESS || isProcessingTrackingLink) {
            FIXED_PROGRESS
        } else {
            newProgress
        }

        loadingViewState.value = progress.copy(isLoading = isLoading, progress = visualProgress, url = site?.url ?: "")

        if (newProgress == 100) {
            command.value = RefreshUserAgent(url, currentBrowserViewState().isDesktopBrowsingMode)
            navigationAwareLoginDetector.onEvent(NavigationEvent.PageFinished)
        }
    }

    override fun pageFinished(
        webViewNavigationState: WebViewNavigationState,
        url: String?,
    ) {
        if (!currentBrowserViewState().maliciousSiteBlocked) {
            navigationStateChanged(webViewNavigationState)
            url?.let { prefetchFavicon(url) }

            viewModelScope.launch {
                onboardingDesignExperimentManager.onWebPageFinishedLoading(url)
            }

            evaluateSerpLogoState(url)
        }
    }

    private fun evaluateSerpLogoState(url: String?) {
        if (serpEasterEggLogosToggles.feature().isEnabled()) {
            if (url != null && duckDuckGoUrlDetector.isDuckDuckGoQueryUrl(url)) {
                command.value = ExtractSerpLogo(url)
            } else {
                omnibarViewState.value = currentOmnibarViewState().copy(serpLogo = null)
            }
        }
    }

    override fun onPageCommitVisible(
        webViewNavigationState: WebViewNavigationState,
        url: String,
    ) {
        if (!currentBrowserViewState().maliciousSiteBlocked) {
            navigationStateChanged(webViewNavigationState)
            onPageContentStart(url)
        }
    }

    override fun pageStarted(
        webViewNavigationState: WebViewNavigationState,
        activeExperiments: List<Toggle>,
    ) {
        this.activeExperiments = activeExperiments

        browserViewState.value =
            currentBrowserViewState().copy(
                browserShowing = true,
                showPrivacyShield = HighlightableButton.Visible(enabled = false),
                fireButton = HighlightableButton.Visible(enabled = false),
                maliciousSiteBlocked = false,
            )
        navigationStateChanged(webViewNavigationState)
    }

    override fun onSitePermissionRequested(
        request: PermissionRequest,
        sitePermissionsAllowedToAsk: SitePermissions,
    ) {
        if (request is LocationPermissionRequest) {
            if (!sameEffectiveTldPlusOne(site, request.origin)) {
                logcat { "Permissions: sameEffectiveTldPlusOne false" }
                request.deny()
                return
            }
        }

        viewModelScope.launch(dispatchers.io()) {
            command.postValue(ShowSitePermissionsDialog(sitePermissionsAllowedToAsk, request))
        }
    }

    private fun sameEffectiveTldPlusOne(
        site: Site?,
        origin: String,
    ): Boolean {
        val siteDomain = site?.url?.toHttpUrlOrNull() ?: return false
        val originDomain = origin.toUri().toString().toHttpUrlOrNull() ?: return false

        val siteETldPlusOne = siteDomain.topPrivateDomain()
        val originETldPlusOne = originDomain.topPrivateDomain()

        return siteETldPlusOne == originETldPlusOne
    }

    private fun registerSiteVisit() {
        Schedulers.io().scheduleDirect {
            networkLeaderboardDao.incrementSitesVisited()
        }
    }

    override fun dosAttackDetected() {
        invalidateBrowsingActions()
        showErrorWithAction(R.string.dosErrorMessage)
    }

    override fun titleReceived(newTitle: String) {
        site?.title = newTitle
        val url = site?.url
        viewModelScope.launch(dispatchers.main()) {
            val isDuckPlayerUrl = withContext(dispatchers.io()) {
                url != null && duckPlayer.getDuckPlayerState() == ENABLED && duckPlayer.isDuckPlayerUri(url)
            }
            command.postValue(ShowWebPageTitle(newTitle, url, isDuckPlayerUrl))
            onSiteChanged()
        }
    }

    @AnyThread
    override fun sendEmailRequested(emailAddress: String) {
        command.postValue(SendEmail(emailAddress))
    }

    @AnyThread
    override fun dialTelephoneNumberRequested(telephoneNumber: String) {
        command.postValue(DialNumber(telephoneNumber))
    }

    @AnyThread
    override fun sendSmsRequested(telephoneNumber: String) {
        command.postValue(SendSms(telephoneNumber))
    }

    override fun surrogateDetected(surrogate: SurrogateResponse) {
        site?.surrogateDetected(surrogate)
    }

    // This is called when interceptor upgrades to https
    override fun upgradedToHttps() {
        httpsUpgraded = true
    }

    override fun trackerDetected(event: TrackingEvent) {
        logcat { "Tracker detected while on $url and the document was ${event.documentUrl}" }
        if (site?.domainMatchesUrl(event.documentUrl) == true) {
            site?.trackerDetected(event)
            onSiteChanged()
        }
        updateNetworkLeaderboard(event)
    }

    private fun updateNetworkLeaderboard(event: TrackingEvent) {
        val networkName = event.entity?.name ?: return
        networkLeaderboardDao.incrementNetworkCount(networkName)
    }

    override fun pageHasHttpResources(page: String) {
        if (site?.domainMatchesUrl(page) == true) {
            site?.hasHttpResources = true
            onSiteChanged()
        }
    }

    override fun pageHasHttpResources(page: Uri) {
        if (site?.domainMatchesUrl(page) == true) {
            site?.hasHttpResources = true
            onSiteChanged()
        }
    }

    override fun onCertificateReceived(certificate: SslCertificate?) {
        site?.certificate = certificate
    }

    private fun enableUrlParametersRemovedFlag() {
        site?.urlParametersRemoved = true
        onSiteChanged()
    }

    fun onAutoconsentResultReceived(
        consentManaged: Boolean,
        optOutFailed: Boolean,
        selfTestFailed: Boolean,
        isCosmetic: Boolean?,
    ) {
        site?.consentManaged = consentManaged
        site?.consentOptOutFailed = optOutFailed
        site?.consentSelfTestFailed = selfTestFailed
        site?.consentCosmeticHide = isCosmetic
    }

    fun onMaliciousSiteUserAction(
        action: MaliciousSiteBlockedWarningLayout.Action,
        siteUrl: Uri,
        feed: Feed,
        activeCustomTab: Boolean,
    ) {
        when (action) {
            LeaveSite -> {
                if (activeCustomTab) {
                    command.postValue(CloseCustomTab)
                } else {
                    command.postValue(EscapeMaliciousSite)
                    openNewTab()
                    closeCurrentTab()
                }
            }

            VisitSite -> {
                val params = mapOf(CATEGORY_KEY to feed.name.lowercase())
                pixel.fire(AppPixelName.MALICIOUS_SITE_PROTECTION_VISIT_SITE, params)
                command.postValue(BypassMaliciousSiteWarning(siteUrl, feed))
                val documentUrlString = siteUrl.toString()
                loadingViewState.value = currentLoadingViewState().copy(
                    isLoading = true,
                    trackersAnimationEnabled = true,
                    /*We set the progress to 20 so the omnibar starts animating and the user knows we are loading the page.
                    * We don't show the browser until the page actually starts loading, to prevent previous sites from briefly
                    * showing in case the URL was blocked locally and therefore never started to show*/
                    progress = 20,
                    url = documentUrlString,
                )
                omnibarViewState.value = currentOmnibarViewState().copy(
                    omnibarText = if (settingsDataStore.isFullUrlEnabled) {
                        documentUrlString
                    } else {
                        addressDisplayFormatter.getShortUrl(
                            documentUrlString,
                        )
                    },
                    queryOrFullUrl = documentUrlString,
                    isEditing = false,
                    navigationChange = true,
                )
            }

            LearnMore -> command.postValue(OpenBrokenSiteLearnMore(SCAM_PROTECTION_LEARN_MORE_URL))
            ReportError -> command.postValue(ReportBrokenSiteError("$SCAM_PROTECTION_REPORT_ERROR_URL$siteUrl"))
        }
    }

    private fun onSiteChanged() {
        httpsUpgraded = false
        site?.isDesktopMode = currentBrowserViewState().isDesktopBrowsingMode
        viewModelScope.launch {
            val privacyProtection: PrivacyShield = withContext(dispatchers.io()) {
                site?.privacyProtection() ?: PrivacyShield.UNKNOWN
            }
            withContext(dispatchers.main()) {
                siteLiveData.value = site
                val previousPrivacyShieldState = currentPrivacyShieldState()
                privacyShieldViewState.value = previousPrivacyShieldState.copy(
                    privacyShield = privacyProtection,
                    trackersBlocked = site?.trackerCount ?: 0,
                )
            }
            withContext(dispatchers.io()) {
                tabRepository.update(tabId, site)
            }
        }
    }

    private fun resetAutoConsent() {
        site?.consentCosmeticHide = false
        site?.consentManaged = false
        site?.consentOptOutFailed = false
        site?.consentSelfTestFailed = false
    }

    override fun getSite(): Site? = site

    override fun onReceivedSslError(
        handler: SslErrorHandler,
        errorResponse: SslErrorResponse,
    ) {
        if (sslCertificatesFeature.allowBypass().isEnabled()) {
            logcat { "SSLError: error received for ${errorResponse.url} and nextUrl is ${site?.nextUrl} and currentUrl is ${site?.url}" }
            if (site?.nextUrl != null && errorResponse.url != site?.nextUrl) {
                logcat { "SSLError: received ssl error for a page we are not loading, cancelling request" }
                handler.cancel()
            } else {
                browserViewState.value =
                    currentBrowserViewState().copy(
                        browserShowing = false,
                        showPrivacyShield = HighlightableButton.Visible(enabled = false),
                        sslError = errorResponse.errorType,
                    )
                command.postValue(ShowSSLError(handler, errorResponse))
            }
        } else {
            logcat { "SSLError: allow bypass certificates feature disabled, cancelling request" }
            handler.cancel()
        }
    }

    override fun showFileChooser(
        filePathCallback: ValueCallback<Array<Uri>>,
        fileChooserParams: FileChooserParams,
    ) {
        val mimeTypes = convertAcceptTypesToMimeTypes(fileChooserParams.acceptTypes)
        val fileChooserRequestedParams = FileChooserRequestedParams(fileChooserParams.mode, mimeTypes)
        val cameraHardwareAvailable = cameraHardwareChecker.hasCameraHardware()

        command.value = when {
            fileChooserParams.isCaptureEnabled -> {
                when {
                    acceptsOnly("image/", fileChooserParams.acceptTypes) && cameraHardwareAvailable ->
                        ShowImageCamera(filePathCallback, fileChooserRequestedParams)

                    acceptsOnly("video/", fileChooserParams.acceptTypes) && cameraHardwareAvailable ->
                        ShowVideoCamera(filePathCallback, fileChooserRequestedParams)

                    acceptsOnly("audio/", fileChooserParams.acceptTypes) ->
                        ShowSoundRecorder(filePathCallback, fileChooserRequestedParams)

                    else ->
                        ShowFileChooser(filePathCallback, fileChooserRequestedParams)
                }
            }

            fileChooserParams.acceptTypes.any { it.startsWith("image/") && cameraHardwareAvailable } ->
                ShowExistingImageOrCameraChooser(filePathCallback, fileChooserRequestedParams, MediaStore.ACTION_IMAGE_CAPTURE)

            fileChooserParams.acceptTypes.any { it.startsWith("video/") && cameraHardwareAvailable } ->
                ShowExistingImageOrCameraChooser(filePathCallback, fileChooserRequestedParams, MediaStore.ACTION_VIDEO_CAPTURE)

            else ->
                ShowFileChooser(filePathCallback, fileChooserRequestedParams)
        }
    }

    private fun acceptsOnly(
        type: String,
        acceptTypes: Array<String>,
    ): Boolean {
        return acceptTypes.filter { it.startsWith(type) }.size == acceptTypes.size
    }

    private fun convertAcceptTypesToMimeTypes(acceptTypes: Array<String>): List<String> {
        val mimeTypeMap = MimeTypeMap.getSingleton()
        val mimeTypes = mutableSetOf<String>()
        acceptTypes.forEach { type ->
            // Attempt to convert any identified file extensions into corresponding MIME types.
            val fileExtension = MimeTypeMap.getFileExtensionFromUrl(type)
            if (fileExtension.isNotEmpty()) {
                mimeTypeMap.getMimeTypeFromExtension(type.substring(1))?.let {
                    mimeTypes.add(it)
                }
            } else {
                mimeTypes.add(type)
            }
        }
        return mimeTypes.toList()
    }

    private fun currentGlobalLayoutState(): GlobalLayoutViewState = globalLayoutState.value!!
    private fun currentAutoCompleteViewState(): AutoCompleteViewState = autoCompleteViewState.value!!
    private fun currentBrowserViewState(): BrowserViewState = browserViewState.value!!
    private fun currentFindInPageViewState(): FindInPageViewState = findInPageViewState.value!!
    private fun currentAccessibilityViewState(): AccessibilityViewState = accessibilityViewState.value!!
    private fun currentOmnibarViewState(): OmnibarViewState = omnibarViewState.value!!
    private fun currentLoadingViewState(): LoadingViewState = loadingViewState.value!!
    private fun currentCtaViewState(): CtaViewState = ctaViewState.value!!
    private fun currentPrivacyShieldState(): PrivacyShieldViewState = privacyShieldViewState.value!!

    fun triggerAutocomplete(
        query: String,
        hasFocus: Boolean,
        hasQueryChanged: Boolean,
    ) {
        configureAutoComplete()

        // determine if empty list to be shown, or existing search results
        val autoCompleteSearchResults = if (query.isBlank() || !hasFocus) {
            AutoCompleteResult(query, emptyList())
        } else {
            currentAutoCompleteViewState().searchResults
        }

        val autoCompleteSuggestionsEnabled = autoCompleteSettings.autoCompleteSuggestionsEnabled
        val showAutoCompleteSuggestions = hasFocus && query.isNotBlank() && hasQueryChanged && autoCompleteSuggestionsEnabled
        val showFavoritesAsSuggestions = if (!showAutoCompleteSuggestions) {
            val urlFocused =
                hasFocus && query.isNotBlank() && !hasQueryChanged && (UriString.isWebUrl(query) || duckPlayer.isDuckPlayerUri(query))
            val emptyQueryBrowsing = query.isBlank() && currentBrowserViewState().browserShowing
            val favoritesAvailable = currentAutoCompleteViewState().favorites.isNotEmpty()
            hasFocus && (urlFocused || emptyQueryBrowsing) && favoritesAvailable
        } else {
            false
        }

        autoCompleteViewState.value = currentAutoCompleteViewState()
            .copy(
                showSuggestions = showAutoCompleteSuggestions,
                showFavorites = showFavoritesAsSuggestions,
                searchResults = autoCompleteSearchResults,
            )

        if (hasFocus && autoCompleteSuggestionsEnabled) {
            autoCompleteStateFlow.value = query.trim()
        }
    }

    fun onBookmarkMenuClicked() {
        val url = url ?: return
        viewModelScope.launch {
            val bookmark = currentBrowserViewState().bookmark
            if (bookmark != null) {
                pixel.fire(AppPixelName.MENU_ACTION_EDIT_BOOKMARK_PRESSED.pixelName)
                onEditSavedSiteRequested(bookmark)
            } else {
                pixel.fire(AppPixelName.MENU_ACTION_ADD_BOOKMARK_PRESSED.pixelName)
                saveSiteBookmark(url, title ?: "")
            }
        }
    }

    fun onFavoriteMenuClicked() {
        val url = url ?: return
        viewModelScope.launch {
            val favorite = currentBrowserViewState().favorite
            if (favorite != null) {
                pixel.fire(AppPixelName.MENU_ACTION_REMOVE_FAVORITE_PRESSED.pixelName)
                onDeleteFavoriteRequested(favorite)
            } else {
                pixel.fire(AppPixelName.MENU_ACTION_ADD_FAVORITE_PRESSED.pixelName)
                pixel.fire(SavedSitesPixelName.MENU_ACTION_ADD_FAVORITE_PRESSED_DAILY.pixelName, type = Daily())
                saveFavoriteSite(url, title ?: "")
            }
        }
    }

    private suspend fun saveSiteBookmark(
        url: String,
        title: String,
    ) {
        val savedBookmark = withContext(dispatchers.io()) {
            if (url.isNotBlank()) {
                faviconManager.persistCachedFavicon(tabId, url)
            }
            savedSitesRepository.insertBookmark(url, title)
        }
        val bookmarkFolder = getBookmarkFolder(savedBookmark)
        withContext(dispatchers.main()) {
            command.value = ShowSavedSiteAddedConfirmation(SavedSiteChangedViewState(savedBookmark, bookmarkFolder))
        }
    }

    private fun saveFavoriteSite(
        url: String,
        title: String,
    ) {
        viewModelScope.launch {
            val favorite = withContext(dispatchers.io()) {
                if (url.isNotBlank()) {
                    faviconManager.persistCachedFavicon(tabId, url)
                    savedSitesRepository.insertFavorite(title = title, url = url)
                } else {
                    null
                }
            }
            favorite?.let {
                withContext(dispatchers.io()) {
                    hiddenIds.emit(
                        hiddenIds.value.copy(
                            favorites = hiddenIds.value.favorites - favorite.id,
                        ),
                    )
                }
            }
        }
    }

    fun onFireproofWebsiteMenuClicked() {
        val domain = site?.domain ?: return
        viewModelScope.launch {
            if (currentBrowserViewState().isFireproofWebsite) {
                val fireproofWebsiteEntity = FireproofWebsiteEntity(domain)
                fireproofWebsiteRepository.removeFireproofWebsite(fireproofWebsiteEntity)
                command.value = DeleteFireproofConfirmation(fireproofWebsiteEntity = fireproofWebsiteEntity)
                pixel.fire(AppPixelName.FIREPROOF_WEBSITE_REMOVE)
            } else {
                fireproofWebsiteRepository.fireproofWebsite(domain)?.let {
                    pixel.fire(AppPixelName.FIREPROOF_WEBSITE_ADDED)
                    command.value = ShowFireproofWebSiteConfirmation(fireproofWebsiteEntity = it)
                    faviconManager.persistCachedFavicon(tabId, url = domain)
                }
            }
        }
    }

    fun onFireproofLoginDialogShown() {
        viewModelScope.launch {
            fireproofDialogsEventHandler.onFireproofLoginDialogShown()
        }
    }

    fun onUserConfirmedFireproofDialog(domain: String) {
        viewModelScope.launch {
            fireproofDialogsEventHandler.onUserConfirmedFireproofDialog(domain)
        }
    }

    fun onUserDismissedFireproofLoginDialog() {
        viewModelScope.launch {
            fireproofDialogsEventHandler.onUserDismissedFireproofLoginDialog()
        }
    }

    fun onDisableLoginDetectionDialogShown() {
        viewModelScope.launch(dispatchers.io()) {
            fireproofDialogsEventHandler.onDisableLoginDetectionDialogShown()
        }
    }

    fun onUserConfirmedDisableLoginDetectionDialog() {
        viewModelScope.launch(dispatchers.io()) {
            fireproofDialogsEventHandler.onUserConfirmedDisableLoginDetectionDialog()
        }
    }

    fun onUserDismissedDisableLoginDetectionDialog() {
        viewModelScope.launch(dispatchers.io()) {
            fireproofDialogsEventHandler.onUserDismissedDisableLoginDetectionDialog()
        }
    }

    fun onFireproofWebsiteSnackbarUndoClicked(fireproofWebsiteEntity: FireproofWebsiteEntity) {
        viewModelScope.launch(dispatchers.io()) {
            fireproofWebsiteRepository.removeFireproofWebsite(fireproofWebsiteEntity)
            pixel.fire(AppPixelName.FIREPROOF_WEBSITE_UNDO)
        }
    }

    fun onUserDismissedAutomaticFireproofLoginDialog() {
        viewModelScope.launch {
            fireproofDialogsEventHandler.onUserDismissedAutomaticFireproofLoginDialog()
        }
    }

    fun onUserFireproofSiteInAutomaticFireproofLoginDialog(domain: String) {
        viewModelScope.launch(dispatchers.io()) {
            fireproofDialogsEventHandler.onUserRequestedAskEveryTime(domain)
        }
    }

    fun onUserEnabledAutomaticFireproofLoginDialog(domain: String) {
        viewModelScope.launch(dispatchers.io()) {
            fireproofDialogsEventHandler.onUserEnabledAutomaticFireproofing(domain)
        }
    }

    fun onRemoveFireproofWebsiteSnackbarUndoClicked(fireproofWebsiteEntity: FireproofWebsiteEntity) {
        viewModelScope.launch(dispatchers.io()) {
            fireproofWebsiteRepository.fireproofWebsite(fireproofWebsiteEntity.domain)
            pixel.fire(AppPixelName.FIREPROOF_REMOVE_WEBSITE_UNDO)
        }
    }

    override fun onFavouriteEdited(favorite: Favorite) {
        viewModelScope.launch(dispatchers.io()) {
            savedSitesRepository.updateFavourite(favorite)
        }
    }

    override fun onBookmarkEdited(
        bookmark: Bookmark,
        oldFolderId: String,
        updateFavorite: Boolean,
    ) {
        viewModelScope.launch(dispatchers.io()) {
            savedSitesRepository.updateBookmark(bookmark, oldFolderId, updateFavorite)
        }
    }

    override fun onFavoriteAdded() {
        pixel.fire(SavedSitesPixelName.EDIT_BOOKMARK_ADD_FAVORITE_TOGGLED)
        pixel.fire(SavedSitesPixelName.EDIT_BOOKMARK_ADD_FAVORITE_TOGGLED_DAILY)
    }

    override fun onFavoriteRemoved() {
        pixel.fire(SavedSitesPixelName.EDIT_BOOKMARK_REMOVE_FAVORITE_TOGGLED)
    }

    override fun onSavedSiteDeleted(savedSite: SavedSite) {
        onDeleteSavedSiteRequested(savedSite)
    }

    override fun onSavedSiteDeleteCancelled() {
        pixel.fire(SavedSitesPixelName.EDIT_BOOKMARK_DELETE_BOOKMARK_CANCELLED)
    }

    override fun onSavedSiteDeleteRequested() {
        pixel.fire(SavedSitesPixelName.EDIT_BOOKMARK_DELETE_BOOKMARK_CLICKED)
    }

    fun onEditSavedSiteRequested(savedSite: SavedSite) {
        viewModelScope.launch(dispatchers.io()) {
            val bookmarkFolder =
                if (savedSite is Bookmark) {
                    getBookmarkFolder(savedSite)
                } else {
                    null
                }

            withContext(dispatchers.main()) {
                command.value = ShowEditSavedSiteDialog(
                    SavedSiteChangedViewState(
                        savedSite,
                        bookmarkFolder,
                    ),
                )
            }
        }
    }

    fun onBrokenSiteSelected() {
        command.value = BrokenSiteFeedback(BrokenSiteData.fromSite(site, reportFlow = MENU))
    }

    fun onPrivacyProtectionMenuClicked(clickedFromCustomTab: Boolean = false) {
        val domain = site?.domain ?: return
        appCoroutineScope.launch(dispatchers.io()) {
            if (isPrivacyProtectionDisabled(domain)) {
                removeFromAllowList(domain, clickedFromCustomTab)
            } else {
                performToggleReportCheck()
                addToAllowList(domain, clickedFromCustomTab)
            }

            privacyProtectionsToggleUsageListener.onPrivacyProtectionsToggleUsed()
        }
    }

    private suspend fun performToggleReportCheck() {
        if (toggleReports.shouldPrompt()) {
            withContext(dispatchers.main()) {
                command.value = ToggleReportFeedback(opener = DashboardOpener.MENU)
            }
        }
    }

    private suspend fun addToAllowList(
        domain: String,
        clickedFromCustomTab: Boolean,
    ) {
        val pixelParams = privacyProtectionsPopupExperimentExternalPixels.getPixelParams()
        if (clickedFromCustomTab) {
            pixel.fire(CustomTabPixelNames.CUSTOM_TABS_MENU_DISABLE_PROTECTIONS_ALLOW_LIST_ADD)
        } else {
            pixel.fire(AppPixelName.BROWSER_MENU_ALLOWLIST_ADD, pixelParams, type = Count)
        }
        privacyProtectionsPopupExperimentExternalPixels.tryReportProtectionsToggledFromBrowserMenu(protectionsEnabled = false)
        userAllowListRepository.addDomainToUserAllowList(domain)
        privacyProtectionTogglePlugin.getPlugins().forEach {
            it.onToggleOff(PrivacyToggleOrigin.MENU)
        }
        withContext(dispatchers.main()) {
            browserViewState.value = currentBrowserViewState().copy(isPrivacyProtectionDisabled = true)
        }
    }

    private suspend fun removeFromAllowList(
        domain: String,
        clickedFromCustomTab: Boolean,
    ) {
        val pixelParams = privacyProtectionsPopupExperimentExternalPixels.getPixelParams()
        if (clickedFromCustomTab) {
            pixel.fire(CustomTabPixelNames.CUSTOM_TABS_MENU_DISABLE_PROTECTIONS_ALLOW_LIST_REMOVE)
        } else {
            pixel.fire(AppPixelName.BROWSER_MENU_ALLOWLIST_REMOVE, pixelParams, type = Count)
        }
        privacyProtectionsPopupExperimentExternalPixels.tryReportProtectionsToggledFromBrowserMenu(protectionsEnabled = true)
        userAllowListRepository.removeDomainFromUserAllowList(domain)
        privacyProtectionTogglePlugin.getPlugins().forEach {
            it.onToggleOn(PrivacyToggleOrigin.MENU)
        }
        withContext(dispatchers.main()) {
            browserViewState.value = currentBrowserViewState().copy(isPrivacyProtectionDisabled = false)
        }
    }

    fun onUserSelectedToEditQuery(query: String) {
        command.value = EditWithSelectedQuery(query)
    }

    fun userLongPressedInWebView(
        target: LongPressTarget,
        menu: ContextMenu,
    ) {
        logcat(INFO) { "Long pressed on ${target.type}, (url=${target.url}), (image url = ${target.imageUrl})" }
        longPressHandler.handleLongPress(target.type, target.url, menu)
    }

    fun userSelectedItemFromLongPressMenu(
        longPressTarget: LongPressTarget,
        item: MenuItem,
    ): Boolean {
        val requiredAction = longPressHandler.userSelectedMenuItem(longPressTarget, item)
        logcat { "Required action from long press is $requiredAction" }

        return when (requiredAction) {
            is RequiredAction.OpenInNewTab -> {
                if (subscriptions.shouldLaunchPrivacyProForUrl(requiredAction.url)) {
                    command.value = LaunchPrivacyPro(requiredAction.url.toUri())
                    return true
                }
                command.value = GenerateWebViewPreviewImage
                command.value = OpenInNewTab(query = requiredAction.url, sourceTabId = tabId)
                true
            }

            is RequiredAction.OpenInNewBackgroundTab -> {
                if (subscriptions.shouldLaunchPrivacyProForUrl(requiredAction.url)) {
                    command.value = LaunchPrivacyPro(requiredAction.url.toUri())
                    return true
                }
                command.value = GenerateWebViewPreviewImage
                viewModelScope.launch { openInNewBackgroundTab(requiredAction.url) }
                true
            }

            is RequiredAction.DownloadFile -> {
                command.value = DownloadImage(requiredAction.url, false)
                true
            }

            is RequiredAction.ShareLink -> {
                command.value = ShareLink(requiredAction.url)
                true
            }

            is RequiredAction.CopyLink -> {
                command.value = CopyLink(requiredAction.url)
                true
            }

            RequiredAction.None -> {
                false
            }
        }
    }

    suspend fun openInNewBackgroundTab(url: String) {
        tabRepository.addNewTabAfterExistingTab(url, tabId)
        command.value = OpenInNewBackgroundTab(url)
    }

    fun onFindInPageSelected() {
        findInPageViewState.value = FindInPageViewState(visible = true)
    }

    fun userFindingInPage(searchTerm: String) {
        val currentViewState = currentFindInPageViewState()
        if (!currentViewState.visible && searchTerm.isEmpty()) {
            return
        }

        var findInPage = currentViewState.copy(visible = true, searchTerm = searchTerm)
        if (searchTerm.isEmpty()) {
            findInPage = findInPage.copy(showNumberMatches = false)
        }
        findInPageViewState.value = findInPage
        command.value = FindInPageCommand(searchTerm)
    }

    fun dismissFindInView() {
        findInPageViewState.value = currentFindInPageViewState().copy(visible = false, searchTerm = "")
        command.value = DismissFindInPage
    }

    fun onFindResultsReceived(
        activeMatchOrdinal: Int,
        numberOfMatches: Int,
    ) {
        val activeIndex = if (numberOfMatches == 0) 0 else activeMatchOrdinal + 1
        val currentViewState = currentFindInPageViewState()
        findInPageViewState.value = currentViewState.copy(
            showNumberMatches = true,
            activeMatchIndex = activeIndex,
            numberMatches = numberOfMatches,
        )
    }

    fun onWebSessionRestored() {
        globalLayoutState.value = Browser(isNewTabState = false)
    }

    fun onChangeBrowserModeClicked() {
        val currentBrowserViewState = currentBrowserViewState()
        val desktopSiteRequested = !currentBrowserViewState().isDesktopBrowsingMode
        browserViewState.value = currentBrowserViewState.copy(isDesktopBrowsingMode = desktopSiteRequested)
        command.value = RefreshUserAgent(site?.uri?.toString(), desktopSiteRequested)
        site?.isDesktopMode = desktopSiteRequested

        val uri = site?.uri ?: return

        pixel.fire(
            if (desktopSiteRequested) {
                AppPixelName.MENU_ACTION_DESKTOP_SITE_ENABLE_PRESSED
            } else {
                AppPixelName.MENU_ACTION_DESKTOP_SITE_DISABLE_PRESSED
            },
        )

        if (desktopSiteRequested && uri.isMobileSite) {
            val desktopUrl = uri.toDesktopUri().toString()
            logcat(INFO) { "Original URL $url - attempting $desktopUrl with desktop site UA string" }
            command.value = NavigationCommand.Navigate(desktopUrl, getUrlHeaders(desktopUrl))
        } else {
            command.value = NavigationCommand.Refresh
        }
    }

    fun onPrivacyProtectionsPopupUiEvent(event: PrivacyProtectionsPopupUiEvent) {
        privacyProtectionsPopupManager.onUiEvent(event)
    }

    private fun initializeViewStates() {
        initializeDefaultViewStates()
        viewModelScope.launch {
            initializeViewStatesFromPersistedData()
        }
    }

    private fun initializeDefaultViewStates() {
        globalLayoutState.value = Browser()
        browserViewState.value = BrowserViewState()
        loadingViewState.value = LoadingViewState()
        autoCompleteViewState.value = AutoCompleteViewState()
        omnibarViewState.value = OmnibarViewState()
        findInPageViewState.value = FindInPageViewState()
        ctaViewState.value = CtaViewState()
        privacyShieldViewState.value = PrivacyShieldViewState()
        accessibilityViewState.value = AccessibilityViewState()
    }

    private suspend fun initializeViewStatesFromPersistedData() {
        withContext(dispatchers.io()) {
            val addToHomeSupported = addToHomeCapabilityDetector.isAddToHomeSupported()
            val showAutofill = autofillCapabilityChecker.canAccessCredentialManagementScreen()
            val showDuckChat = duckAiFeatureState.showPopupMenuShortcut.value

            withContext(dispatchers.main()) {
                browserViewState.value = currentBrowserViewState().copy(
                    addToHomeVisible = addToHomeSupported,
                    showAutofill = showAutofill,
                    showDuckChatOption = showDuckChat,
                )
            }
        }
    }

    fun onSetDefaultBrowserSelected() {
        additionalDefaultBrowserPrompts.onSetAsDefaultPopupMenuItemSelected()
    }

    fun onShareSelected() {
        url?.let {
            viewModelScope.launch(dispatchers.io()) {
                transformUrlToShare(it).let {
                    withContext(dispatchers.main()) {
                        command.value = ShareLink(it, title.orEmpty())
                    }
                }
            }
        }
    }

    fun determineShowBrowser() {
        val showBrowser = currentBrowserViewState().browserShowing || !url.isNullOrBlank()
        browserViewState.value = currentBrowserViewState().copy(browserShowing = showBrowser)
    }

    private fun showBrowser() {
        browserViewState.value = currentBrowserViewState().copy(browserShowing = true)
        globalLayoutState.value = Browser(isNewTabState = false)
    }

    override fun historicalPageSelected(stackIndex: Int) {
        command.value = NavigationCommand.NavigateToHistory(stackIndex)
    }

    private suspend fun transformUrlToShare(url: String): String {
        return if (duckDuckGoUrlDetector.isDuckDuckGoQueryUrl(url)) {
            removeAtbAndSourceParamsFromSearch(url)
        } else if (duckPlayer.isDuckPlayerUri(url)) {
            transformDuckPlayerUrl(url)
        } else {
            url
        }
    }

    private fun removeAtbAndSourceParamsFromSearch(url: String): String {
        if (!duckDuckGoUrlDetector.isDuckDuckGoQueryUrl(url)) {
            return url
        }

        val uri = Uri.parse(url)
        val paramsToRemove = arrayOf(AppUrl.ParamKey.ATB, AppUrl.ParamKey.SOURCE)
        val parameterNames = uri.queryParameterNames.filterNot { paramsToRemove.contains(it) }
        val builder = uri.buildUpon()
        builder.clearQuery()

        for (paramName in parameterNames) {
            builder.appendQueryParameter(paramName, uri.getQueryParameter(paramName))
        }

        return builder.build().toString()
    }

    private suspend fun transformDuckPlayerUrl(url: String): String {
        return if (duckPlayer.isDuckPlayerUri(url)) {
            duckPlayer.createYoutubeWatchUrlFromDuckPlayer(url.toUri()) ?: url
        } else {
            url
        }
    }

    fun saveWebViewState(
        webView: WebView?,
        tabId: String,
    ) {
        webViewSessionStorage.saveSession(webView, tabId)
    }

    fun restoreWebViewState(
        webView: WebView?,
        lastUrl: String,
    ) {
        val sessionRestored = webViewSessionStorage.restoreSession(webView, tabId)
        if (sessionRestored) {
            logcat(VERBOSE) { "Successfully restored session" }
            onWebSessionRestored()
        } else {
            if (lastUrl.isNotBlank()) {
                logcat(WARN) { "Restoring last url but page history has been lost - url=[$lastUrl]" }
                onUserSubmittedQuery(lastUrl)
            }
        }
    }

    @SuppressLint("CheckResult")
    fun onPinPageToHomeSelected() {
        val currentPage = url ?: return
        val title = if (duckDuckGoUrlDetector.isDuckDuckGoQueryUrl(currentPage)) {
            duckDuckGoUrlDetector.extractQuery(currentPage) ?: currentPage
        } else {
            currentPage.toUri().baseHost ?: currentPage
        }

        viewModelScope.launch {
            val favicon: Bitmap? = faviconManager.loadFromDisk(tabId = tabId, url = currentPage)
            command.value = AddHomeShortcut(title, currentPage, favicon)
        }
    }

    fun onBrowserMenuClicked(isCustomTab: Boolean) {
        val menuHighlighted = currentBrowserViewState().showMenuButton.isHighlighted()
        if (menuHighlighted) {
            browserViewState.value = currentBrowserViewState().copy(
                showMenuButton = HighlightableButton.Visible(highlighted = false),
            )
        }
        command.value = LaunchPopupMenu
    }

    fun onPopupMenuLaunched() {
        additionalDefaultBrowserPrompts.onPopupMenuLaunched()
    }

    fun onNewTabMenuItemClicked(
        longPress: Boolean = false,
    ) {
        openNewTab()

        if (longPress) {
            pixel.fire(AppPixelName.TAB_MANAGER_NEW_TAB_LONG_PRESSED)
        } else {
            val url = site?.url
            if (url != null) {
                if (duckDuckGoUrlDetector.isDuckDuckGoUrl(url)) {
                    pixel.fire(AppPixelName.MENU_ACTION_NEW_TAB_PRESSED_FROM_SERP)
                } else {
                    pixel.fire(AppPixelName.MENU_ACTION_NEW_TAB_PRESSED_FROM_SITE)
                }
            }
        }
    }

    fun onNavigationBarNewTabButtonClicked() {
        openNewTab()
    }

    fun onCtaShown() {
        val cta = ctaViewState.value?.cta ?: return
        viewModelScope.launch(dispatchers.io()) {
            ctaViewModel.onCtaShown(cta)
            onboardingDesignExperimentManager.fireInContextDialogShownPixel(cta)
        }
    }

    suspend fun refreshCta(): Cta? {
        if (currentGlobalLayoutState() is Browser) {
            val isBrowserShowing = currentBrowserViewState().browserShowing
            val isErrorShowing = currentBrowserViewState().maliciousSiteBlocked
            if (hasCtaBeenShownForCurrentPage.get() && isBrowserShowing) return null
            val detectedRefreshPatterns = brokenSitePrompt.getUserRefreshPatterns()
            handleBreakageRefreshPatterns(detectedRefreshPatterns)
            val cta = withContext(dispatchers.io()) {
                ctaViewModel.refreshCta(
                    dispatchers.io(),
                    isBrowserShowing && !isErrorShowing,
                    siteLiveData.value,
                    detectedRefreshPatterns,
                )
            }
            val contextDaxDialogsShown = withContext(dispatchers.io()) {
                ctaViewModel.areBubbleDaxDialogsCompleted()
            }
            if (isBrowserShowing && cta != null) hasCtaBeenShownForCurrentPage.set(true)
            ctaViewState.value = currentCtaViewState().copy(
                cta = cta,
                isOnboardingCompleteInNewTabPage = contextDaxDialogsShown,
                isBrowserShowing = isBrowserShowing,
                isErrorShowing = isErrorShowing,
            )
            ctaChangedTicker.emit(System.currentTimeMillis().toString())
            return cta
        }
        return null
    }

    private fun showOrHideKeyboard(cta: Cta?) {
        // we hide the keyboard when showing a DialogCta and HomeCta type in the home screen otherwise we show it
        val shouldHideKeyboard = cta is HomePanelCta || cta is DaxBubbleCta.DaxPrivacyProCta ||
            duckAiFeatureState.showInputScreen.value || currentBrowserViewState().lastQueryOrigin == QueryOrigin.FromBookmark
        command.value = if (shouldHideKeyboard) HideKeyboard else ShowKeyboard
    }

    fun onUserClickCtaOkButton(cta: Cta) {
        viewModelScope.launch {
            ctaViewModel.onUserClickCtaOkButton(cta)
        }
        val onboardingCommand = when (cta) {
            is HomePanelCta.AddWidgetAuto, is HomePanelCta.AddWidgetAutoOnboardingExperiment, is HomePanelCta.AddWidgetInstructions -> {
                LaunchAddWidget
            }
            is OnboardingDaxDialogCta -> onOnboardingCtaOkButtonClicked(cta)
            is DaxBubbleCta -> onDaxBubbleCtaOkButtonClicked(cta)
            is BrokenSitePromptDialogCta -> onBrokenSiteCtaOkButtonClicked(cta)
            else -> null
        }
        onboardingCommand?.let {
            command.value = it
        }
    }

    fun onUserClickCtaSecondaryButton(cta: Cta) {
        viewModelScope.launch {
            ctaViewModel.onUserDismissedCta(cta)
            if (cta is BrokenSitePromptDialogCta) {
                onBrokenSiteCtaDismissButtonClicked(cta)
            }
        }
    }

    fun onUserClickCtaDismissButton(cta: Cta) {
        viewModelScope.launch {
            ctaViewModel.onUserDismissedCta(cta, viaCloseBtn = true)
            if (cta is DaxBubbleCta) {
                command.value = HideOnboardingDaxBubbleCta(cta)
            } else if (cta is OnboardingDaxDialogCta) {
                command.value = HideOnboardingDaxDialog(cta)
                if (cta is OnboardingDaxDialogCta.DaxTrackersBlockedCta) {
                    if (currentBrowserViewState().showPrivacyShield.isHighlighted()) {
                        browserViewState.value =
                            currentBrowserViewState().copy(showPrivacyShield = HighlightableButton.Visible(highlighted = false))
                        ctaViewModel.dismissPulseAnimation()
                    }
                }
            }
        }
    }

    fun onUserDismissedCta(cta: Cta?) {
        cta?.let {
            viewModelScope.launch {
                ctaViewModel.onUserDismissedCta(it)
            }
            if (cta is OnboardingDaxDialogCta.DaxTrackersBlockedCta && currentBrowserViewState().showPrivacyShield.isHighlighted()) {
                browserViewState.value = currentBrowserViewState().copy(showPrivacyShield = HighlightableButton.Visible(highlighted = false))
            }
        }
    }

    fun updateTabPreview(
        tabId: String,
        fileName: String,
    ) {
        tabRepository.updateTabPreviewImage(tabId, fileName)
    }

    fun deleteTabPreview(tabId: String) {
        tabRepository.updateTabPreviewImage(tabId, null)
        tabRepository.updateTabFavicon(tabId, null)
    }

    override fun handleAppLink(
        appLink: AppLink,
        isForMainFrame: Boolean,
    ): Boolean {
        return appLinksHandler.handleAppLink(
            isForMainFrame,
            appLink.uriString,
            appSettingsPreferencesStore.appLinksEnabled,
            !appSettingsPreferencesStore.showAppLinksPrompt,
        ) { appLinkClicked(appLink) }
    }

    fun openAppLink() {
        browserViewState.value?.previousAppLink?.let { appLink ->
            command.value = OpenAppLink(appLink)
        }
    }

    fun clearPreviousUrl() {
        appLinksHandler.updatePreviousUrl(null)
    }

    private fun clearPreviousAppLink() {
        browserViewState.value = currentBrowserViewState().copy(
            previousAppLink = null,
        )
    }

    private fun updatePreviousAppLink(appLink: AppLink) {
        browserViewState.value = currentBrowserViewState().copy(
            previousAppLink = appLink,
        )
    }

    private fun appLinkClicked(appLink: AppLink) {
        if (appSettingsPreferencesStore.showAppLinksPrompt || appLinksHandler.isUserQuery()) {
            command.value = ShowAppLinkPrompt(appLink)
            appLinksHandler.setUserQueryState(false)
        } else {
            command.value = OpenAppLink(appLink)
        }
    }

    override fun handleNonHttpAppLink(nonHttpAppLink: NonHttpAppLink): Boolean {
        nonHttpAppLinkClicked(nonHttpAppLink)
        return true
    }

    fun nonHttpAppLinkClicked(appLink: NonHttpAppLink) {
        if (nonHttpAppLinkChecker.isPermitted(appLink.intent)) {
            command.value = HandleNonHttpAppLink(appLink, getUrlHeaders(appLink.fallbackUrl))
        }
    }

    fun onPrintSelected() {
        url?.let {
            pixel.fire(AppPixelName.MENU_ACTION_PRINT_PRESSED)
            command.value = PrintLink(removeAtbAndSourceParamsFromSearch(it), defaultMediaSize())
        }
    }

    fun printFromWebView() {
        viewModelScope.launch {
            onPrintSelected()
        }
    }

    override fun openMessageInNewTab(message: Message) {
        command.value = GenerateWebViewPreviewImage
        command.value = OpenMessageInNewTab(message, tabId)
    }

    override fun openLinkInNewTab(uri: Uri) {
        command.value = OpenInNewTab(uri.toString(), tabId)
    }

    override fun recoverFromRenderProcessGone() {
        webNavigationState?.let {
            navigationStateChanged(EmptyNavigationState(it))
        }
        invalidateBrowsingActions()
        showErrorWithAction()
    }

    override fun requiresAuthentication(request: BasicAuthenticationRequest) {
        if (request.host != site?.uri?.host) {
            omnibarViewState.value = currentOmnibarViewState().copy(
                omnibarText = if (settingsDataStore.isFullUrlEnabled) request.site else addressDisplayFormatter.getShortUrl(request.site),
                queryOrFullUrl = request.site,
                forceExpand = true,
            )
            command.value = HideWebContent
        }
        command.value = RequiresAuthentication(request)
    }

    fun handleAuthentication(
        request: BasicAuthenticationRequest,
        credentials: BasicAuthenticationCredentials,
    ) {
        request.handler.proceed(credentials.username, credentials.password)
        command.value = ShowWebContent
        command.value = SaveCredentials(request, credentials)
    }

    fun cancelAuthentication(request: BasicAuthenticationRequest) {
        request.handler.cancel()
        command.value = ShowWebContent
    }

    fun userLaunchingTabSwitcher(launchedFromFocusedNtp: Boolean) {
        command.value = LaunchTabSwitcher
        pixel.fire(AppPixelName.TAB_MANAGER_CLICKED)
        fireDailyLaunchPixel()

        if (!currentBrowserViewState().browserShowing) {
            val params = mapOf(PixelParameter.FROM_FOCUSED_NTP to launchedFromFocusedNtp.toString())
            pixel.fire(AppPixelName.TAB_MANAGER_OPENED_FROM_NEW_TAB, parameters = params)
        } else {
            val url = site?.url
            if (url != null) {
                if (duckDuckGoUrlDetector.isDuckDuckGoUrl(url)) {
                    pixel.fire(AppPixelName.TAB_MANAGER_OPENED_FROM_SERP)
                } else {
                    pixel.fire(AppPixelName.TAB_MANAGER_OPENED_FROM_SITE)
                }
            }
        }

        onUserDismissedCta(ctaViewState.value?.cta)
    }

    fun onLaunchTabSwitcherAfterTabsUndeletedRequest() {
        command.value = LaunchTabSwitcher
    }

    private fun fireDailyLaunchPixel() {
        val tabCount = viewModelScope.async(dispatchers.io()) { tabStatsBucketing.getNumberOfOpenTabs() }
        val activeTabCount = viewModelScope.async(dispatchers.io()) { tabStatsBucketing.getTabsActiveLastWeek() }
        val inactive1w = viewModelScope.async(dispatchers.io()) { tabStatsBucketing.getTabsActiveOneWeekAgo() }
        val inactive2w = viewModelScope.async(dispatchers.io()) { tabStatsBucketing.getTabsActiveTwoWeeksAgo() }
        val inactive3w = viewModelScope.async(dispatchers.io()) { tabStatsBucketing.getTabsActiveMoreThanThreeWeeksAgo() }

        viewModelScope.launch(dispatchers.io()) {
            val params = mapOf(
                PixelParameter.TAB_COUNT to tabCount.await(),
                PixelParameter.TAB_ACTIVE_7D to activeTabCount.await(),
                PixelParameter.TAB_INACTIVE_1W to inactive1w.await(),
                PixelParameter.TAB_INACTIVE_2W to inactive2w.await(),
                PixelParameter.TAB_INACTIVE_3W to inactive3w.await(),
            )
            pixel.fire(TAB_MANAGER_CLICKED_DAILY, params, emptyMap(), Daily())
        }
    }

    private fun isFireproofWebsite(domain: String? = site?.domain): Boolean {
        if (domain == null) return false
        val fireproofWebsites = fireproofWebsiteState.value
        return fireproofWebsites?.any { it.domain == domain } ?: false
    }

    private fun invalidateBrowsingActions() {
        globalLayoutState.value = Invalidated
        loadingViewState.value = LoadingViewState()
        findInPageViewState.value = FindInPageViewState()
    }

    private fun disableUserNavigation() {
        browserViewState.value = currentBrowserViewState().copy(
            canGoBack = false,
            canGoForward = false,
            canReportSite = false,
            canChangeBrowsingMode = false,
            canFireproofSite = false,
        )
    }

    private fun showErrorWithAction(errorMessage: Int = R.string.crashedWebViewErrorMessage) {
        command.value = ShowErrorWithAction(errorMessage) { this.onUserSubmittedQuery(url.orEmpty()) }
    }

    private fun recoverTabWithQuery(query: String) {
        closeCurrentTab()
        command.value = OpenInNewTab(query)
    }

    override fun redirectTriggeredByGpc() {
        navigationAwareLoginDetector.onEvent(NavigationEvent.GpcRedirect)
    }

    override fun loginDetected() {
        val currentUrl = site?.url ?: return
        navigationAwareLoginDetector.onEvent(NavigationEvent.LoginAttempt(currentUrl))
    }

    fun requestFileDownload(
        url: String,
        contentDisposition: String?,
        mimeType: String,
        requestUserConfirmation: Boolean,
        isBlobDownloadWebViewFeatureEnabled: Boolean,
    ) {
        if (url.startsWith("blob:")) {
            if (isBlobDownloadWebViewFeatureEnabled) {
                postMessageToConvertBlobToDataUri(url)
            } else {
                command.value = ConvertBlobToDataUri(url, mimeType)
            }
        } else {
            sendRequestFileDownloadCommand(url, contentDisposition, mimeType, requestUserConfirmation)
        }
    }

    private fun sendRequestFileDownloadCommand(
        url: String,
        contentDisposition: String?,
        mimeType: String,
        requestUserConfirmation: Boolean,
    ) {
        command.postValue(RequestFileDownload(url, contentDisposition, mimeType, requestUserConfirmation))
    }

    @SuppressLint("RequiresFeature") // it's already checked in isBlobDownloadWebViewFeatureEnabled
    private fun postMessageToConvertBlobToDataUri(url: String) {
        appCoroutineScope.launch(dispatchers.main()) { // main because postMessage is not always safe in another thread
            for ((key, proxies) in fixedReplyProxyMap) {
                if (sameOrigin(url.removePrefix("blob:"), key)) {
                    for (replyProxy in proxies.values) {
                        replyProxy.postMessage(url)
                    }
                    return@launch
                }
            }
        }
    }

    private fun sameOrigin(
        firstUrl: String,
        secondUrl: String,
    ): Boolean {
        return kotlin.runCatching {
            val firstUri = Uri.parse(firstUrl)
            val secondUri = Uri.parse(secondUrl)

            firstUri.host == secondUri.host && firstUri.scheme == secondUri.scheme && firstUri.port == secondUri.port
        }.getOrNull() ?: return false
    }

    fun showEmailProtectionChooseEmailPrompt() {
        emailManager.getEmailAddress()?.let {
            command.postValue(ShowEmailProtectionChooseEmailPrompt(it))
        }
    }

    fun consumeAliasAndCopyToClipboard() {
        emailManager.getAlias()?.let {
            command.value = CopyAliasToClipboard(it)
            pixel.enqueueFire(
                AppPixelName.EMAIL_COPIED_TO_CLIPBOARD,
                mapOf(
                    PixelParameter.COHORT to emailManager.getCohort(),
                    PixelParameter.LAST_USED_DAY to emailManager.getLastUsedDate(),
                ),
            )
            emailManager.setNewLastUsedDate()
        }
    }

    /**
     * API called after user selected to autofill a private alias into a form
     */
    fun usePrivateDuckAddress(
        originalUrl: String,
        duckAddress: String,
    ) {
        command.postValue(InjectEmailAddress(duckAddress = duckAddress, originalUrl = originalUrl, autoSaveLogin = true))
    }

    fun usePersonalDuckAddress(
        originalUrl: String,
        duckAddress: String,
    ) {
        command.postValue(InjectEmailAddress(duckAddress = duckAddress, originalUrl = originalUrl, autoSaveLogin = false))
    }

    fun download(pendingFileDownload: PendingFileDownload) {
        fileDownloader.enqueueDownload(pendingFileDownload)
    }

    fun onDeleteFavoriteSnackbarDismissed(savedSite: SavedSite) {
        delete(savedSite)
    }

    fun onDeleteSavedSiteSnackbarDismissed(savedSite: SavedSite) {
        delete(savedSite, true)
    }

    private fun delete(
        savedSite: SavedSite,
        deleteBookmark: Boolean = false,
    ) {
        appCoroutineScope.launch(dispatchers.io()) {
            if (savedSite is Bookmark || deleteBookmark) {
                faviconManager.deletePersistedFavicon(savedSite.url)
            }
            savedSitesRepository.delete(savedSite, deleteBookmark)
        }
    }

    fun onDeleteFavoriteRequested(savedSite: SavedSite) {
        hide(savedSite, DeleteFavoriteConfirmation(savedSite))
    }

    fun onDeleteSavedSiteRequested(savedSite: SavedSite) {
        hide(savedSite, DeleteSavedSiteConfirmation(savedSite))
    }

    private fun hide(
        savedSite: SavedSite,
        deleteCommand: Command,
    ) {
        viewModelScope.launch(dispatchers.io()) {
            when (savedSite) {
                is Bookmark -> {
                    hiddenIds.emit(
                        hiddenIds.value.copy(
                            bookmarks = hiddenIds.value.bookmarks + savedSite.id,
                            favorites = hiddenIds.value.favorites + savedSite.id,
                        ),
                    )
                }

                is Favorite -> {
                    hiddenIds.emit(hiddenIds.value.copy(favorites = hiddenIds.value.favorites + savedSite.id))
                }
            }
            withContext(dispatchers.main()) {
                command.value = deleteCommand
            }
        }
    }

    fun undoDelete(savedSite: SavedSite) {
        viewModelScope.launch(dispatchers.io()) {
            hiddenIds.emit(
                hiddenIds.value.copy(
                    favorites = hiddenIds.value.favorites - savedSite.id,
                    bookmarks = hiddenIds.value.bookmarks - savedSite.id,
                ),
            )
        }
    }

    fun onQuickAccessListChanged(newList: List<FavoritesQuickAccessAdapter.QuickAccessFavorite>) {
        viewModelScope.launch(dispatchers.io()) {
            savedSitesRepository.updateWithPosition(newList.map { it.favorite })
        }
    }

    fun resetErrors() {
        site?.resetErrors()
    }

    fun resetBrowserError() {
        browserViewState.value = currentBrowserViewState().copy(browserError = OMITTED)
    }

    fun refreshBrowserError() {
        if (currentBrowserViewState().browserError != OMITTED && currentBrowserViewState().browserError != LOADING) {
            browserViewState.value = currentBrowserViewState().copy(browserError = LOADING)
        }
        if (currentBrowserViewState().sslError != NONE) {
            browserViewState.value = currentBrowserViewState().copy(browserShowing = true, sslError = NONE)
        }
        if (currentBrowserViewState().maliciousSiteBlocked) {
            browserViewState.value = currentBrowserViewState().copy(browserShowing = true, maliciousSiteBlocked = false, maliciousSiteStatus = null)
        }
    }

    fun onWebViewRefreshed() {
        resetTrackersCount()
        refreshBrowserError()
        resetAutoConsent()
        accessibilityViewState.value = currentAccessibilityViewState().copy(refreshWebView = false)
        canAutofillSelectCredentialsDialogCanAutomaticallyShow = true
    }

    override fun handleCloakedAmpLink(initialUrl: String) {
        isProcessingTrackingLink = true
        command.value = ExtractUrlFromCloakedAmpLink(initialUrl)
    }

    override fun startProcessingTrackingLink() {
        isProcessingTrackingLink = true
    }

    fun updateLastAmpLink(url: String) {
        ampLinks.lastAmpLinkInfo = AmpLinkInfo(ampLink = url)
    }

    override fun onUrlExtractionError(initialUrl: String) {
        command.postValue(LoadExtractedUrl(extractedUrl = initialUrl))
    }

    override fun onUrlExtracted(
        initialUrl: String,
        extractedUrl: String?,
    ) {
        val destinationUrl = ampLinks.processDestinationUrl(initialUrl, extractedUrl)
        command.postValue(LoadExtractedUrl(extractedUrl = destinationUrl))
    }

    fun returnNoCredentialsWithPage(originalUrl: String) {
        command.postValue(CancelIncomingAutofillRequest(originalUrl))
    }

    fun onConfigurationChanged() {
        browserViewState.value = currentBrowserViewState().copy(
            forceRenderingTicker = System.currentTimeMillis(),
        )
    }

    fun onMessageReceived() {
        isLinkOpenedInNewTab = true
    }

    override fun linkOpenedInNewTab(): Boolean {
        return isLinkOpenedInNewTab
    }

    override fun isActiveTab(): Boolean {
        liveSelectedTab.value?.let {
            return it.tabId == tabId
        }

        return false
    }

    override fun onReceivedError(
        errorType: WebViewErrorResponse,
        url: String,
    ) {
        browserViewState.value =
            currentBrowserViewState().copy(
                browserError = errorType,
                showPrivacyShield = HighlightableButton.Visible(enabled = false),
            )
        if (androidBrowserConfig.errorPagePixel().isEnabled()) {
            pixel.enqueueFire(AppPixelName.ERROR_PAGE_SHOWN)
        }
        command.postValue(WebViewError(errorType, url))
    }

    override fun onReceivedMaliciousSiteWarning(
        siteUrl: Uri,
        feed: Feed,
        exempted: Boolean,
        clientSideHit: Boolean,
        isMainframe: Boolean,
    ) {
        val previousSite = site

        val siteUrlString = siteUrl.toString()

        val maliciousSiteStatus = when (feed) {
            MALWARE -> MaliciousSiteStatus.MALWARE
            PHISHING -> MaliciousSiteStatus.PHISHING
            SCAM -> MaliciousSiteStatus.SCAM
        }

        buildSiteFactory(
            url = siteUrlString,
            updateMaliciousSiteStatus = true,
            maliciousSiteStatus = maliciousSiteStatus,
        )

        if (!exempted) {
            if (currentBrowserViewState().maliciousSiteBlocked && previousSite?.url == url.toString()) {
                logcat { "maliciousSiteBlocked already shown for $url, previousSite: ${previousSite.url}" }
            } else {
                val params = mapOf(CATEGORY_KEY to feed.name.lowercase(), CLIENT_SIDE_HIT_KEY to clientSideHit.toString())
                pixel.fire(AppPixelName.MALICIOUS_SITE_PROTECTION_ERROR_SHOWN, params)
            }
            logcat { "Received MaliciousSiteWarning for $url, feed: $feed, exempted: false, clientSideHit: $clientSideHit" }

            viewModelScope.launch(dispatchers.main()) {
                loadingViewState.value =
                    currentLoadingViewState().copy(
                        isLoading = false,
                        progress = 100,
                        url = siteUrlString,
                        trackersAnimationEnabled = false,
                    )

                browserViewState.value =
                    browserStateModifier.copyForMaliciousSiteWarningShowing(currentBrowserViewState(), maliciousSiteStatus)

                omnibarViewState.value =
                    currentOmnibarViewState().copy(
                        omnibarText = if (settingsDataStore.isFullUrlEnabled) siteUrlString else addressDisplayFormatter.getShortUrl(siteUrlString),
                        queryOrFullUrl = siteUrlString,
                        isEditing = false,
                    )

                command.value =
                    ShowWarningMaliciousSite(siteUrl, feed, clientSideHit, isMainframe) { navigationStateChanged(it) }
            }
        } else {
            viewModelScope.launch(dispatchers.main()) {
                browserViewState.value = currentBrowserViewState().copy(maliciousSiteStatus = maliciousSiteStatus)
                omnibarViewState.value =
                    currentOmnibarViewState().copy(
                        omnibarText = if (settingsDataStore.isFullUrlEnabled) siteUrlString else addressDisplayFormatter.getShortUrl(siteUrlString),
                        queryOrFullUrl = siteUrlString,
                        isEditing = false,
                    )
                loadingViewState.value =
                    currentLoadingViewState().copy(
                        url = siteUrlString,
                    )
            }
        }
    }

    override fun onReceivedMaliciousSiteSafe(
        url: Uri,
        isForMainFrame: Boolean,
    ) {
        if (isForMainFrame && currentBrowserViewState().maliciousSiteBlocked) {
            viewModelScope.launch(dispatchers.main()) {
                browserViewState.value =
                    browserStateModifier.copyForBrowserShowing(currentBrowserViewState())
            }

            buildSiteFactory(url = url.toString(), updateMaliciousSiteStatus = true, maliciousSiteStatus = null)
        }
    }

    suspend fun updateTabTitle(
        tabId: String,
        newTitle: String,
    ) {
        getSite()?.let { site ->
            site.title = newTitle
            tabRepository.update(tabId, site)
        }
    }

    override fun recordErrorCode(
        error: String,
        url: String,
    ) {
        if (siteErrorHandlerKillSwitch.self().isEnabled()) {
            siteErrorHandler.handleError(currentSite = site, urlWithError = url, error = error)
        } else {
            // when navigating from one page to another it can happen that errors are recorded before pageChanged etc. are
            // called triggering a buildSite.
            if (url != site?.url) {
                site = siteFactory.buildSite(url = url, tabId = tabId)
            }
            site?.onErrorDetected(error)
        }
        logcat { "recordErrorCode $error in ${site?.url}" }
    }

    override fun recordHttpErrorCode(
        statusCode: Int,
        url: String,
    ) {
        if (siteErrorHandlerKillSwitch.self().isEnabled()) {
            siteHttpErrorHandler.handleError(currentSite = site, urlWithError = url, error = statusCode)
        } else {
            // when navigating from one page to another it can happen that errors are recorded before pageChanged etc. are
            // called triggering a buildSite.
            if (url != site?.url) {
                site = siteFactory.buildSite(url = url, tabId = tabId)
            }
            site?.onHttpErrorDetected(statusCode)
        }
        logcat { "recordHttpErrorCode $statusCode in ${site?.url}" }
        updateHttpErrorCount(statusCode)
    }

    fun onAutofillMenuSelected() {
        launchAutofillSettings()
    }

    fun onNavigationBarAutofillButtonClicked() {
        launchAutofillSettings()
    }

    private fun launchAutofillSettings() {
        command.value = LaunchAutofillSettings(privacyProtectionEnabled = !currentBrowserViewState().isPrivacyProtectionDisabled)
    }

    fun cancelPendingAutofillRequestToChooseCredentials() {
        canAutofillSelectCredentialsDialogCanAutomaticallyShow = false
    }

    fun canAutofillSelectCredentialsDialogCanAutomaticallyShow(): Boolean {
        return canAutofillSelectCredentialsDialogCanAutomaticallyShow && !currentOmnibarViewState().isEditing
    }

    fun onShowUserCredentialsSaved(it: LoginCredentials) {
        viewModelScope.launch(dispatchers.main()) {
            command.value = ShowUserCredentialSavedOrUpdatedConfirmation(
                credentials = it,
                includeShortcutToViewCredential = autofillCapabilityChecker.canAccessCredentialManagementScreen(),
                messageResourceId = R.string.autofillLoginSavedSnackbarMessage,
            )
        }
    }

    fun onShowUserCredentialsUpdated(it: LoginCredentials) {
        viewModelScope.launch(dispatchers.main()) {
            command.value = ShowUserCredentialSavedOrUpdatedConfirmation(
                credentials = it,
                includeShortcutToViewCredential = autofillCapabilityChecker.canAccessCredentialManagementScreen(),
                messageResourceId = R.string.autofillLoginUpdatedSnackbarMessage,
            )
        }
    }

    private fun updateHttpErrorCount(statusCode: Int) {
        viewModelScope.launch(dispatchers.io()) {
            when {
                // 400 errors
                statusCode == HTTP_STATUS_CODE_BAD_REQUEST_ERROR -> httpErrorPixels.get().updateCountPixel(
                    HttpErrorPixelName.WEBVIEW_RECEIVED_HTTP_ERROR_400_DAILY,
                )
                // all 4xx errors apart from 400
                statusCode / 100 == HTTP_STATUS_CODE_CLIENT_ERROR_PREFIX -> httpErrorPixels.get().updateCountPixel(
                    HttpErrorPixelName.WEBVIEW_RECEIVED_HTTP_ERROR_4XX_DAILY,
                )
                // all 5xx errors
                statusCode / 100 == HTTP_STATUS_CODE_SERVER_ERROR_PREFIX -> httpErrorPixels.get().update5xxCountPixel(
                    HttpErrorPixelName.WEBVIEW_RECEIVED_HTTP_ERROR_5XX_DAILY,
                    statusCode,
                )
            }
        }
    }

    private fun defaultMediaSize(): PrintAttributes.MediaSize {
        val country = device.country.uppercase(Locale.getDefault())
        return if (PRINT_LETTER_FORMAT_COUNTRIES_ISO3166_2.contains(country)) {
            PrintAttributes.MediaSize.NA_LETTER
        } else {
            PrintAttributes.MediaSize.ISO_A4
        }
    }

    private fun getDataForPermissionState(
        featureName: String,
        method: String,
        id: String,
        permissionState: SitePermissionQueryResponse,
    ): JsCallbackData {
        val strPermissionState = when (permissionState) {
            SitePermissionQueryResponse.Granted -> "granted"
            SitePermissionQueryResponse.Prompt -> "prompt"
            SitePermissionQueryResponse.Denied -> "denied"
        }

        return JsCallbackData(
            JSONObject("""{ "state":"$strPermissionState"}"""),
            featureName,
            method,
            id,
        )
    }

    fun webViewCompatProcessJsCallbackMessage(
        context: String,
        featureName: String,
        method: String,
        id: String?,
        data: JSONObject?,
        onResponse: (JSONObject) -> Unit,
    ) {
        processGlobalMessages(
            featureName = featureName,
            method = method,
            id = id,
            data = data,
            onResponse = onResponse,
        )

        when (context) {
            "contentScopeScripts" -> processContentScopeMessages(
                featureName = featureName,
                method = method,
                id = id,
                data = data,
                onResponse = onResponse,
            )
        }
    }

    private fun processGlobalMessages(
        featureName: String,
        method: String,
        id: String?,
        data: JSONObject?,
        onResponse: (JSONObject) -> Unit,
    ) {
        when (method) {
            "addDebugFlag" -> {
                site?.debugFlags =
                    (site?.debugFlags ?: listOf())
                        .toMutableList()
                        .plus(featureName)
                        .distinct()
            }
        }
    }

    private fun processContentScopeMessages(
        featureName: String,
        method: String,
        id: String?,
        data: JSONObject?,
        onResponse: (JSONObject) -> Unit,
    ) {
        when (featureName) {
            "webCompat" -> {
                when (method) {
                    "webShare" -> if (id != null && data != null) {
                        webViewCompatWebShare(featureName, method, id, data, onResponse)
                    }
                    "permissionsQuery" -> if (id != null && data != null) {
                        webViewCompatPermissionsQuery(featureName, method, id, data, onResponse)
                    }

                    "screenLock" -> if (id != null && data != null) {
                        webViewCompatScreenLock(featureName, method, id, data, onResponse)
                    }

                    "screenUnlock" -> screenUnlock()
                }
            }
            "breakageReporting" -> if (data != null) {
                when (method) {
                    "breakageReportResult" -> {
                        breakageReportResult(data)
                    }
                }
            }
            "messaging" -> when (method) {
                "initialPing" -> {
                    // TODO: Eventually, we might want plugins here
                    val response = JSONObject(
                        mapOf(
                            "desktopModeEnabled" to (getSite()?.isDesktopMode ?: false),
                            "forcedZoomEnabled" to (accessibilityViewState.value?.forceZoom ?: false),
                        ),
                    )
                    onResponse(response)
                }
            }
        }
    }

    fun processJsCallbackMessage(
        featureName: String,
        method: String,
        id: String?,
        data: JSONObject?,
        isActiveCustomTab: Boolean = false,
        getWebViewUrl: () -> String?,
    ) {
        when (method) {
            "webShare" -> if (id != null && data != null) {
                webShare(featureName, method, id, data)
            }

            "permissionsQuery" -> if (id != null && data != null) {
                permissionsQuery(featureName, method, id, data)
            }

            "screenLock" -> if (id != null && data != null) {
                screenLock(featureName, method, id, data)
            }

            "screenUnlock" -> screenUnlock()

            "breakageReportResult" -> if (data != null) {
                breakageReportResult(data)
            }

            "addDebugFlag" -> {
                site?.debugFlags = (site?.debugFlags ?: listOf()).toMutableList().plus(featureName)?.toList()
            }

            else -> {
                // NOOP
            }
        }

        when (featureName) {
            DUCK_PLAYER_FEATURE_NAME, DUCK_PLAYER_PAGE_FEATURE_NAME -> {
                viewModelScope.launch(dispatchers.io()) {
                    val webViewUrl = withContext(dispatchers.main()) { getWebViewUrl() }
                    val response = duckPlayerJSHelper.processJsCallbackMessage(featureName, method, id, data, webViewUrl, tabId, isActiveCustomTab)
                    withContext(dispatchers.main()) {
                        response?.let {
                            command.value = it
                        }
                    }
                }
            }

            DUCK_CHAT_FEATURE_NAME -> {
                viewModelScope.launch(dispatchers.io()) {
                    val response = duckChatJSHelper.processJsCallbackMessage(featureName, method, id, data)
                    withContext(dispatchers.main()) {
                        response?.let {
                            command.value = SendResponseToJs(it)
                        }
                    }
                }
            }

            SUBSCRIPTIONS_FEATURE_NAME -> {
                viewModelScope.launch(dispatchers.io()) {
                    val response = subscriptionsJSHelper.processJsCallbackMessage(featureName, method, id, data)
                    withContext(dispatchers.main()) {
                        response?.let {
                            command.value = SendResponseToJs(it)
                        }
                    }
                }
            }

            else -> {}
        }
    }

    private fun webShare(
        featureName: String,
        method: String,
        id: String,
        data: JSONObject,
    ) {
        viewModelScope.launch(dispatchers.main()) {
            command.value = WebShareRequest(JsCallbackData(data, featureName, method, id))
        }
    }

    private fun webViewCompatWebShare(
        featureName: String,
        method: String,
        id: String,
        data: JSONObject,
        onResponse: (JSONObject) -> Unit,
    ) {
        viewModelScope.launch(dispatchers.main()) {
            command.value = WebViewCompatWebShareRequest(JsCallbackData(data, featureName, method, id), onResponse)
        }
    }

    private fun permissionsQuery(
        featureName: String,
        method: String,
        id: String,
        data: JSONObject,
    ) {
        viewModelScope.launch(dispatchers.io()) {
            val response = if (url == null) {
                getDataForPermissionState(featureName, method, id, SitePermissionQueryResponse.Denied)
            } else {
                val permissionState = sitePermissionsManager.getPermissionsQueryResponse(url!!, tabId, data.optString("name"))
                getDataForPermissionState(featureName, method, id, permissionState)
            }

            withContext(dispatchers.main()) {
                command.value = SendResponseToJs(response)
            }
        }
    }

    private fun webViewCompatPermissionsQuery(
        featureName: String,
        method: String,
        id: String,
        data: JSONObject,
        onResponse: (JSONObject) -> Unit,
    ) {
        viewModelScope.launch(dispatchers.io()) {
            val response = if (url == null) {
                getDataForPermissionState(featureName, method, id, SitePermissionQueryResponse.Denied)
            } else {
                val permissionState = sitePermissionsManager.getPermissionsQueryResponse(url!!, tabId, data.optString("name"))
                getDataForPermissionState(featureName, method, id, permissionState)
            }

            withContext(dispatchers.main()) {
                onResponse(response.params)
            }
        }
    }

    private fun screenLock(
        featureName: String,
        method: String,
        id: String,
        data: JSONObject,
    ) {
        viewModelScope.launch(dispatchers.main()) {
            if (androidBrowserConfig.screenLock().isEnabled()) {
                withContext(dispatchers.main()) {
                    command.value = ScreenLock(JsCallbackData(data, featureName, method, id))
                }
            }
        }
    }

    private fun webViewCompatScreenLock(
        featureName: String,
        method: String,
        id: String,
        data: JSONObject,
        onResponse: (JSONObject) -> Unit,
    ) {
        viewModelScope.launch(dispatchers.main()) {
            if (androidBrowserConfig.screenLock().isEnabled()) {
                withContext(dispatchers.main()) {
                    command.value = WebViewCompatScreenLock(JsCallbackData(data, featureName, method, id), onResponse)
                }
            }
        }
    }

    private fun screenUnlock() {
        viewModelScope.launch(dispatchers.main()) {
            if (androidBrowserConfig.screenLock().isEnabled()) {
                withContext(dispatchers.main()) {
                    command.value = ScreenUnlock
                }
            }
        }
    }

    fun breakageReportResult(
        data: JSONObject,
    ) {
        val jsPerformanceData = data.get("jsPerformance") as JSONArray
        val referrer = data.get("referrer") as? String
        val sanitizedReferrer = referrer?.removeSurrounding("\"")
        val isExternalLaunch = site?.isExternalLaunch ?: false

        site?.realBrokenSiteContext?.recordJsPerformance(jsPerformanceData)
        site?.realBrokenSiteContext?.inferOpenerContext(sanitizedReferrer, isExternalLaunch)
    }

    fun onHomeShown() {
        clearPreviousAppLink()
        viewModelScope.launch(dispatchers.io()) {
            if (faviconsFetchingPrompt.shouldShow() && savedSitesRepository.hasFavorites()) {
                withContext(dispatchers.main()) {
                    command.value = ShowFaviconsPrompt
                }
            }
        }
    }

    fun onFaviconsFetchingEnabled(
        fetchingEnabled: Boolean,
    ) {
        viewModelScope.launch(dispatchers.io()) {
            faviconsFetchingPrompt.onPromptAnswered(fetchingEnabled)
        }
    }

    fun onSSLCertificateWarningAction(
        action: Action,
        url: String,
        activeCustomTab: Boolean = false,
    ) {
        when (action) {
            is Action.Shown -> {
                when (action.errorType) {
                    EXPIRED -> pixel.fire(AppPixelName.SSL_CERTIFICATE_WARNING_EXPIRED_SHOWN)
                    WRONG_HOST -> pixel.fire(AppPixelName.SSL_CERTIFICATE_WARNING_WRONG_HOST_SHOWN)
                    UNTRUSTED_HOST -> pixel.fire(AppPixelName.SSL_CERTIFICATE_WARNING_UNTRUSTED_SHOWN)
                    GENERIC -> pixel.fire(AppPixelName.SSL_CERTIFICATE_WARNING_GENERIC_SHOWN)
                    else -> {} // nothing to report
                }
            }

            Action.Advance -> {
                pixel.fire(AppPixelName.SSL_CERTIFICATE_WARNING_ADVANCED_PRESSED)
            }

            Action.LeaveSite -> {
                pixel.fire(AppPixelName.SSL_CERTIFICATE_WARNING_CLOSE_PRESSED)
                if (activeCustomTab) {
                    command.postValue(CloseCustomTab)
                } else {
                    command.postValue(HideSSLError)
                }
            }

            Action.Proceed -> {
                refreshBrowserError()
                bypassedSSLCertificatesRepository.add(url)
                site?.sslError = true
                pixel.fire(AppPixelName.SSL_CERTIFICATE_WARNING_PROCEED_PRESSED)
            }
        }
    }

    fun recoverFromWarningPage(showBrowser: Boolean) {
        site?.maliciousSiteStatus = null
        if (showBrowser) {
            browserViewState.value = currentBrowserViewState().copy(
                browserShowing = true,
                sslError = NONE,
                maliciousSiteBlocked = false,
                maliciousSiteStatus = null,
            )
        } else {
            omnibarViewState.value = currentOmnibarViewState().copy(
                omnibarText = "",
                queryOrFullUrl = "",
                forceExpand = true,
            )
            loadingViewState.value = currentLoadingViewState().copy(isLoading = false)
            browserViewState.value = currentBrowserViewState().copy(
                showPrivacyShield = HighlightableButton.Visible(enabled = false),
                browserShowing = showBrowser,
                sslError = NONE,
                maliciousSiteBlocked = false,
                maliciousSiteStatus = null,
            )
        }
    }

    private fun onBrokenSiteCtaDismissButtonClicked(cta: BrokenSitePromptDialogCta): Command? {
        viewModelScope.launch {
            command.value = HideBrokenSitePromptCta(cta)
        }
        return null
    }

    private fun onBrokenSiteCtaOkButtonClicked(cta: BrokenSitePromptDialogCta): Command? {
        viewModelScope.launch {
            command.value = BrokenSiteFeedback(BrokenSiteData.fromSite(site, reportFlow = RELOAD_THREE_TIMES_WITHIN_20_SECONDS))
            command.value = HideBrokenSitePromptCta(cta)
        }
        return null
    }

    private fun onOnboardingCtaOkButtonClicked(onboardingCta: OnboardingDaxDialogCta): Command? {
        onUserDismissedCta(onboardingCta)
        return when (onboardingCta) {
            is OnboardingDaxDialogCta.DaxSerpCta -> {
                viewModelScope.launch {
                    val cta = withContext(dispatchers.io()) {
                        ctaViewModel.getSiteSuggestionsDialogCta(
                            onSiteSuggestionOptionClicked = { index ->
                                onUserSelectedOnboardingSiteSuggestionOption(index)
                            },
                        )
                    }
                    ctaViewState.value = currentCtaViewState().copy(cta = cta)
                    if (cta == null) {
                        command.value = HideOnboardingDaxDialog(onboardingCta)
                    }
                }
                null
            }

            is OnboardingDaxDialogCta.DaxTrackersBlockedCta,
            is OnboardingDaxDialogCta.DaxNoTrackersCta,
            is OnboardingDaxDialogCta.DaxMainNetworkCta,
            -> {
                viewModelScope.launch {
                    val cta = withContext(dispatchers.io()) {
                        if (currentBrowserViewState().maliciousSiteBlocked) null else ctaViewModel.getFireDialogCta()
                    }
                    ctaViewState.value = currentCtaViewState().copy(cta = cta)
                    if (cta == null) {
                        command.value = HideOnboardingDaxDialog(onboardingCta)
                    }
                }
                null
            }

            is OnboardingDaxDialogCta.DaxFireButtonCta -> LaunchFireDialogFromOnboardingDialog(onboardingCta)

            else -> HideOnboardingDaxDialog(onboardingCta)
        }
    }

    private fun onDaxBubbleCtaOkButtonClicked(cta: DaxBubbleCta): Command? {
        onUserDismissedCta(cta)
        return when (cta) {
            is DaxBubbleCta.DaxPrivacyProCta -> {
                LaunchPrivacyPro("https://duckduckgo.com/pro?origin=funnel_onboarding_android".toUri())
            }

            is DaxBubbleCta.DaxEndCta -> {
                viewModelScope.launch {
                    val updatedCta = refreshCta()
                    ctaViewState.value = currentCtaViewState().copy(cta = updatedCta)
                    showOrHideKeyboard(updatedCta)
                }
                null
            }

            else -> null
        }
    }

    private fun onDismissOnboardingDaxDialog(cta: OnboardingDaxDialogCta) {
        onUserDismissedCta(cta)
        command.value = HideOnboardingDaxDialog(cta)
    }

    fun onFireMenuSelected() {
        val cta = currentCtaViewState().cta
        if (cta is OnboardingDaxDialogCta.DaxFireButtonCta) {
            viewModelScope.launch {
                onboardingDesignExperimentManager.fireFireButtonClickedFromOnboardingPixel()
            }
            onUserDismissedCta(cta)
            command.value = HideOnboardingDaxDialog(cta as OnboardingDaxDialogCta)
        }
    }

    fun onOnboardingDaxTypingAnimationFinished() {
        if (currentBrowserViewState().browserShowing && currentBrowserViewState().maliciousSiteBlocked.not()) {
            browserViewState.value = currentBrowserViewState().copy(showPrivacyShield = HighlightableButton.Visible(highlighted = true))
        }
    }

    override fun onShouldOverride() {
        val cta = currentCtaViewState().cta
        if (cta is OnboardingDaxDialogCta) {
            onDismissOnboardingDaxDialog(cta)
        }
    }

    fun onUserDismissedAutoCompleteInAppMessage() {
        viewModelScope.launch(dispatchers.io()) {
            autoComplete.userDismissedHistoryInAutoCompleteIAM()
            pixel.fire(AUTOCOMPLETE_BANNER_DISMISSED)
        }
    }

    fun autoCompleteSuggestionsGone() {
        viewModelScope.launch(dispatchers.io()) {
            if (hasUserSeenHistoryIAM) {
                autoComplete.submitUserSeenHistoryIAM()
                pixel.fire(AUTOCOMPLETE_BANNER_SHOWN)
            }
            hasUserSeenHistoryIAM = false
            lastAutoCompleteState?.searchResults?.suggestions?.let { suggestions ->
                if (suggestions.any { it is AutoCompleteBookmarkSuggestion && it.isFavorite }) {
                    pixel.fire(AppPixelName.AUTOCOMPLETE_DISPLAYED_LOCAL_FAVORITE)
                }
                if (suggestions.any { it is AutoCompleteBookmarkSuggestion && !it.isFavorite }) {
                    pixel.fire(AppPixelName.AUTOCOMPLETE_DISPLAYED_LOCAL_BOOKMARK)
                }
                if (suggestions.any { it is AutoCompleteHistorySuggestion }) {
                    pixel.fire(AppPixelName.AUTOCOMPLETE_DISPLAYED_LOCAL_HISTORY)
                }
                if (suggestions.any { it is AutoCompleteHistorySearchSuggestion }) {
                    pixel.fire(AppPixelName.AUTOCOMPLETE_DISPLAYED_LOCAL_HISTORY_SEARCH)
                }
                if (suggestions.any { it is AutoCompleteSearchSuggestion && it.isUrl }) {
                    pixel.fire(AppPixelName.AUTOCOMPLETE_DISPLAYED_LOCAL_WEBSITE)
                }
                if (suggestions.any { it is AutoCompleteSwitchToTabSuggestion }) {
                    pixel.fire(AppPixelName.AUTOCOMPLETE_DISPLAYED_LOCAL_SWITCH_TO_TAB)
                }
            }
            lastAutoCompleteState = null
            autoCompleteJob.cancel()
        }
    }

    fun saveReplyProxyForBlobDownload(
        originUrl: String,
        replyProxy: JavaScriptReplyProxy,
        locationHref: String? = null,
    ) {
        appCoroutineScope.launch(dispatchers.io()) { // FF check has disk IO
            val frameProxies = fixedReplyProxyMap[originUrl]?.toMutableMap() ?: mutableMapOf()
            // if location.href is not passed, we fall back to origin
            val safeLocationHref = locationHref ?: originUrl
            frameProxies[safeLocationHref] = replyProxy
            fixedReplyProxyMap[originUrl] = frameProxies
        }
    }

    fun onStartPrint() {
        logcat { "Print started" }
        browserViewState.value = currentBrowserViewState().copy(isPrinting = true)
    }

    fun onFinishPrint() {
        logcat { "Print finished" }
        browserViewState.value = currentBrowserViewState().copy(isPrinting = false)
    }

    fun isPrinting(): Boolean {
        return currentBrowserViewState().isPrinting
    }

    fun hasOmnibarPositionChanged(currentPosition: OmnibarPosition): Boolean = settingsDataStore.omnibarPosition != currentPosition

    private fun firePixelBasedOnCurrentUrl(
        emptyUrlPixel: AppPixelName,
        duckDuckGoQueryUrlPixel: AppPixelName,
        websiteUrlPixel: AppPixelName,
    ) {
        val text = url.orEmpty()
        if (text.isEmpty()) {
            pixel.fire(emptyUrlPixel)
        } else if (duckDuckGoUrlDetector.isDuckDuckGoQueryUrl(text)) {
            pixel.fire(duckDuckGoQueryUrlPixel)
        } else if (isUrl(text)) {
            pixel.fire(websiteUrlPixel)
        }
    }

    private fun isUrl(text: String): Boolean {
        return URLUtil.isNetworkUrl(text) || URLUtil.isAssetUrl(text) || URLUtil.isFileUrl(text) || URLUtil.isContentUrl(text)
    }

    fun onNewTabShown() {
        newTabPixels.get().fireNewTabDisplayed()
    }

    fun handleMenuRefreshAction() {
        refreshPixelSender.sendMenuRefreshPixels()
    }

    fun handlePullToRefreshAction() {
        refreshPixelSender.sendPullToRefreshPixels()
    }

    fun fireCustomTabRefreshPixel() {
        refreshPixelSender.sendCustomTabRefreshPixel()
    }

    private fun handleBreakageRefreshPatterns(refreshPatterns: Set<RefreshPattern>) {
        refreshPixelSender.onRefreshPatternDetected(refreshPatterns)
    }

    fun setBrowserBackground(lightModeEnabled: Boolean) {
        when {
            onboardingDesignExperimentManager.isBuckEnrolledAndEnabled() -> {
                command.value = SetBrowserBackgroundColor(getBuckOnboardingExperimentBackgroundColor(lightModeEnabled))
            }
            onboardingDesignExperimentManager.isBbEnrolledAndEnabled() -> {
                // TODO if BB wins the we should rename the function to SetBubbleDialogBackground
                command.value = Command.SetBubbleDialogBackground(getBBBackgroundResource(lightModeEnabled))
            }
            else -> {
                command.value = SetBrowserBackground(getBackgroundResource(lightModeEnabled))
            }
        }
    }

    private fun getBuckOnboardingExperimentBackgroundColor(lightModeEnabled: Boolean): Int =
        if (lightModeEnabled) {
            CommonR.color.buckYellow
        } else {
            CommonR.color.buckLightBlue
        }

    fun setOnboardingDialogBackground(lightModeEnabled: Boolean) {
        when {
            onboardingDesignExperimentManager.isBuckEnrolledAndEnabled() -> {
                command.value = SetOnboardingDialogBackgroundColor(getBuckOnboardingExperimentBackgroundColor(lightModeEnabled))
            }
            onboardingDesignExperimentManager.isBbEnrolledAndEnabled() -> {
                command.value = SetOnboardingDialogBackground(getBBBackgroundResource(lightModeEnabled))
            }
            else -> {
                command.value = SetOnboardingDialogBackground(getBackgroundResource(lightModeEnabled))
            }
        }
    }

    private fun getBackgroundResource(lightModeEnabled: Boolean): Int =
        if (lightModeEnabled) {
            R.drawable.onboarding_background_bitmap_light
        } else {
            R.drawable.onboarding_background_bitmap_dark
        }

    private fun getBBBackgroundResource(lightModeEnabled: Boolean): Int =
        if (lightModeEnabled) {
            R.drawable.onboarding_background_bb_bitmap_light
        } else {
            R.drawable.onboarding_background_bb_bitmap_dark
        }

    private fun onUserSwitchedToTab(tabId: String) {
        command.value = Command.SwitchToTab(tabId)
    }

    private fun onUserTappedDuckAiPromptAutocomplete(prompt: String) {
        command.value = Command.SubmitChat(prompt)

        viewModelScope.launch {
            val params = duckChat.createWasUsedBeforePixelParams()
            pixel.fire(DuckChatPixelName.DUCK_CHAT_OPEN_AUTOCOMPLETE_LEGACY, parameters = params)
        }
    }

    fun onDuckChatMenuClicked() {
        viewModelScope.launch {
            command.value = HideKeyboardForChat
            val params = duckChat.createWasUsedBeforePixelParams()
            pixel.fire(DuckChatPixelName.DUCK_CHAT_OPEN_BROWSER_MENU, parameters = params)
        }
        duckChat.openDuckChat()
    }

    fun onDuckChatOmnibarButtonClicked(query: String?, hasFocus: Boolean, isNtp: Boolean) {
        viewModelScope.launch {
            command.value = HideKeyboardForChat
        }
        when {
            hasFocus && isNtp && query.isNullOrBlank() -> duckChat.openDuckChat()
            hasFocus -> duckChat.openDuckChatWithAutoPrompt(query ?: "")
            else -> duckChat.openDuckChat()
        }
    }

    fun onVpnMenuClicked() {
        val vpnMenuState = currentBrowserViewState().vpnMenuState
        when (vpnMenuState) {
            VpnMenuState.NotSubscribed -> {
                command.value = LaunchPrivacyPro("https://duckduckgo.com/pro?origin=funnel_appmenu_android".toUri())
            }
            is VpnMenuState.Subscribed -> {
                command.value = LaunchVpnManagement
            }
            VpnMenuState.Hidden -> {} // Should not happen as menu item should not be visible
        }
    }

    fun onAutoConsentPopUpHandled(isCosmetic: Boolean) {
        if (!currentBrowserViewState().maliciousSiteBlocked) {
            command.postValue(ShowAutoconsentAnimation(isCosmetic))
        }
    }

    fun onTabSwipedAway() {
        command.value = GenerateWebViewPreviewImage
    }

    fun onBookmarksMenuItemClicked() {
        pixel.fire(AppPixelName.MENU_ACTION_BOOKMARKS_PRESSED.pixelName)
        launchBookmarksActivity()
    }

    fun onNavigationBarBookmarksButtonClicked() {
        launchBookmarksActivity()
    }

    private fun launchBookmarksActivity() {
        command.value = LaunchBookmarksActivity
    }

    fun trackersCount(): String = site?.trackerCount?.takeIf { it > 0 }?.toString() ?: ""

    fun resetTrackersCount() {
        site?.resetTrackingEvents()
    }

    fun onOmnibarPrivacyShieldButtonPressed() {
        if (currentCtaViewState().cta is OnboardingDaxDialogCta.DaxTrackersBlockedCta) {
            viewModelScope.launch {
                onboardingDesignExperimentManager.firePrivacyDashClickedFromOnboardingPixel()
            }
        }
    }

    fun onUserSelectedOnboardingDialogOption(
        cta: Cta,
        index: Int?,
    ) {
        if (index == null) return
        viewModelScope.launch {
            onboardingDesignExperimentManager.fireOptionSelectedPixel(cta, index)
        }
    }

    fun onUserSelectedOnboardingSiteSuggestionOption(index: Int) {
        viewModelScope.launch {
            onboardingDesignExperimentManager.fireSiteSuggestionOptionSelectedPixel(index)
        }
    }

    fun onLogoReceived(serpLogo: SerpLogo) {
        omnibarViewState.value = currentOmnibarViewState().copy(serpLogo = serpLogo)
    }

    fun onDynamicLogoClicked(url: String) {
        command.value = Command.ShowSerpEasterEggLogo(url)
    }

    companion object {
        private const val FIXED_PROGRESS = 50

        // Minimum progress to show web content again after decided to hide web content (possible spoofing attack).
        // We think that progress is enough to assume next site has already loaded new content.
        private const val SHOW_CONTENT_MIN_PROGRESS = 50
        private const val NEW_CONTENT_MAX_DELAY_MS = 1000L
        private const val ONE_HOUR_IN_MS = 3_600_000

        private const val HTTP_STATUS_CODE_BAD_REQUEST_ERROR = 400
        private const val HTTP_STATUS_CODE_CLIENT_ERROR_PREFIX = 4 // 4xx, client error status code prefix
        private const val HTTP_STATUS_CODE_SERVER_ERROR_PREFIX = 5 // 5xx, server error status code prefix

        private const val CATEGORY_KEY = "category"
        private const val CLIENT_SIDE_HIT_KEY = "clientSideHit"

        // https://www.iso.org/iso-3166-country-codes.html
        private val PRINT_LETTER_FORMAT_COUNTRIES_ISO3166_2 = setOf(
            Locale.US.country,
            Locale.CANADA.country,
            "MX", // Mexico
        )
    }
}
