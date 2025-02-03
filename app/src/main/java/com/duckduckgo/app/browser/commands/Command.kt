/*
 * Copyright (c) 2024 DuckDuckGo
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

package com.duckduckgo.app.browser.commands

import android.graphics.Bitmap
import android.net.Uri
import android.os.Message
import android.print.PrintAttributes.MediaSize
import android.view.View
import android.webkit.PermissionRequest
import android.webkit.SslErrorHandler
import android.webkit.ValueCallback
import androidx.annotation.DrawableRes
import com.duckduckgo.app.autocomplete.api.AutoComplete.AutoCompleteSuggestion
import com.duckduckgo.app.browser.BrowserTabViewModel.FileChooserRequestedParams
import com.duckduckgo.app.browser.SpecialUrlDetector.UrlType.AppLink
import com.duckduckgo.app.browser.SpecialUrlDetector.UrlType.NonHttpAppLink
import com.duckduckgo.app.browser.SslErrorResponse
import com.duckduckgo.app.browser.WebViewErrorResponse
import com.duckduckgo.app.browser.history.NavigationHistoryEntry
import com.duckduckgo.app.browser.model.BasicAuthenticationCredentials
import com.duckduckgo.app.browser.model.BasicAuthenticationRequest
import com.duckduckgo.app.browser.omnibar.animations.TrackerLogo
import com.duckduckgo.app.browser.viewstate.SavedSiteChangedViewState
import com.duckduckgo.app.cta.ui.BrokenSitePromptDialogCta
import com.duckduckgo.app.cta.ui.OnboardingDaxDialogCta
import com.duckduckgo.app.fire.fireproofwebsite.data.FireproofWebsiteEntity
import com.duckduckgo.autofill.api.domain.app.LoginCredentials
import com.duckduckgo.browser.api.brokensite.BrokenSiteData
import com.duckduckgo.js.messaging.api.JsCallbackData
import com.duckduckgo.js.messaging.api.SubscriptionEventData
import com.duckduckgo.privacy.dashboard.api.ui.DashboardOpener
import com.duckduckgo.savedsites.api.models.SavedSite
import com.duckduckgo.site.permissions.api.SitePermissionsManager.SitePermissions

sealed class Command {
    class OpenInNewTab(
        val query: String,
        val sourceTabId: String? = null,
    ) : Command()

    class OpenMessageInNewTab(
        val message: Message,
        val sourceTabId: String? = null,
    ) : Command()

    class OpenInNewBackgroundTab(val query: String) : Command()
    object LaunchNewTab : Command()
    object ResetHistory : Command()
    class LaunchPrivacyPro(val uri: Uri) : Command()
    class DialNumber(val telephoneNumber: String) : Command()
    class SendSms(val telephoneNumber: String) : Command()
    class SendEmail(val emailAddress: String) : Command()
    object ShowKeyboard : Command()
    object HideKeyboard : Command()
    class ShowFullScreen(val view: View) : Command()
    class DownloadImage(
        val url: String,
        val requestUserConfirmation: Boolean,
    ) : Command()

    class ShowSavedSiteAddedConfirmation(val savedSiteChangedViewState: SavedSiteChangedViewState) : Command()
    class ShowEditSavedSiteDialog(val savedSiteChangedViewState: SavedSiteChangedViewState) : Command()
    class DeleteSavedSiteConfirmation(val savedSite: SavedSite) : Command()
    class DeleteFavoriteConfirmation(val savedSite: SavedSite) : Command()

    class ShowFireproofWebSiteConfirmation(val fireproofWebsiteEntity: FireproofWebsiteEntity) : Command()
    class DeleteFireproofConfirmation(val fireproofWebsiteEntity: FireproofWebsiteEntity) : Command()
    class RefreshAndShowPrivacyProtectionEnabledConfirmation(val domain: String) : Command()
    class RefreshAndShowPrivacyProtectionDisabledConfirmation(val domain: String) : Command()
    object AskToDisableLoginDetection : Command()
    class AskToFireproofWebsite(val fireproofWebsite: FireproofWebsiteEntity) : Command()
    class AskToAutomateFireproofWebsite(val fireproofWebsite: FireproofWebsiteEntity) : Command()
    class ShareLink(
        val url: String,
        val title: String = "",
    ) : Command()

    class SharePromoLinkRMF(
        val url: String,
        val shareTitle: String,
    ) : Command()

    class PrintLink(
        val url: String,
        val mediaSize: MediaSize,
    ) : Command()

    class CopyLink(val url: String) : Command()
    class FindInPageCommand(val searchTerm: String) : Command()
    class BrokenSiteFeedback(val data: BrokenSiteData) : Command()
    class ToggleReportFeedback(val opener: DashboardOpener) : Command()
    object DismissFindInPage : Command()
    class ShowFileChooser(
        val filePathCallback: ValueCallback<Array<Uri>>,
        val fileChooserParams: FileChooserRequestedParams,
    ) : Command()

    class ShowExistingImageOrCameraChooser(
        val filePathCallback: ValueCallback<Array<Uri>>,
        val fileChooserParams: FileChooserRequestedParams,
        val inputAction: String,
    ) : Command()
    class ShowImageCamera(
        val filePathCallback: ValueCallback<Array<Uri>>,
        val fileChooserParams: FileChooserRequestedParams,
    ) : Command()
    class ShowVideoCamera(
        val filePathCallback: ValueCallback<Array<Uri>>,
        val fileChooserParams: FileChooserRequestedParams,
    ) : Command()
    class ShowSoundRecorder(
        val filePathCallback: ValueCallback<Array<Uri>>,
        val fileChooserParams: FileChooserRequestedParams,
    ) : Command()

    class HandleNonHttpAppLink(
        val nonHttpAppLink: NonHttpAppLink,
        val headers: Map<String, String>,
    ) : Command()

    class ShowAppLinkPrompt(val appLink: AppLink) : Command()
    class OpenAppLink(val appLink: AppLink) : Command()
    class ExtractUrlFromCloakedAmpLink(val initialUrl: String) : Command()
    class LoadExtractedUrl(val extractedUrl: String) : Command()
    class AddHomeShortcut(
        val title: String,
        val url: String,
        val icon: Bitmap? = null,
    ) : Command()

    class SubmitUrl(val url: String) : Command()
    class LaunchPlayStore(val appPackage: String) : Command()
    object LaunchDefaultBrowser : Command()
    object LaunchAppTPOnboarding : Command()
    object LaunchAddWidget : Command()
    class RequiresAuthentication(val request: BasicAuthenticationRequest) : Command()
    class SaveCredentials(
        val request: BasicAuthenticationRequest,
        val credentials: BasicAuthenticationCredentials,
    ) : Command()

    object GenerateWebViewPreviewImage : Command()
    object LaunchTabSwitcher : Command()
    object HideWebContent : Command()
    object ShowWebContent : Command()
    class ShowWebPageTitle(
        val title: String,
        val url: String?,
        val showDuckPlayerIcon: Boolean = false,
    ) : Command()
    class RefreshUserAgent(
        val url: String?,
        val isDesktop: Boolean,
    ) : Command()

    class ShowErrorWithAction(
        val textResId: Int,
        val action: () -> Unit,
    ) : Command()

    class ShowDomainHasPermissionMessage(val domain: String) : Command()
    class ConvertBlobToDataUri(
        val url: String,
        val mimeType: String,
    ) : Command()

    class RequestFileDownload(
        val url: String,
        val contentDisposition: String?,
        val mimeType: String,
        val requestUserConfirmation: Boolean,
    ) : Command()

    object ChildTabClosed : Command()

    class CopyAliasToClipboard(val alias: String) : Command()
    class InjectEmailAddress(
        val duckAddress: String,
        val originalUrl: String,
        val autoSaveLogin: Boolean,
    ) : Command()

    class ShowEmailProtectionChooseEmailPrompt(val address: String) : Command()
    object ShowEmailProtectionInContextSignUpPrompt : Command()
    class CancelIncomingAutofillRequest(val url: String) : Command()
    data class LaunchAutofillSettings(val privacyProtectionEnabled: Boolean) : Command()
    class EditWithSelectedQuery(val query: String) : Command()
    class ShowBackNavigationHistory(val history: List<NavigationHistoryEntry>) : Command()
    object EmailSignEvent : Command()
    class ShowSitePermissionsDialog(
        val permissionsToRequest: SitePermissions,
        val request: PermissionRequest,
    ) : Command()

    class ShowUserCredentialSavedOrUpdatedConfirmation(
        val credentials: LoginCredentials,
        val includeShortcutToViewCredential: Boolean,
        val messageResourceId: Int,
    ) : Command()

    data class WebViewError(
        val errorType: WebViewErrorResponse,
        val url: String,
    ) : Command()

    data class ShowWarningMaliciousSite(
        val url: Uri,
    ) : Command()

    data object HideWarningMaliciousSite : Command()

    data object EscapeMaliciousSite : Command()

    data class BypassMaliciousSiteWarning(
        val url: Uri,
    ) : Command()

    data class OpenBrokenSiteLearnMore(val url: String) : Command()
    data class ReportBrokenSiteError(val url: String) : Command()

    // TODO (cbarreiro) Rename to SendResponseToCSS
    data class SendResponseToJs(val data: JsCallbackData) : Command()
    data class SendResponseToDuckPlayer(val data: JsCallbackData) : Command()
    data class SendSubscriptions(val cssData: SubscriptionEventData, val duckPlayerData: SubscriptionEventData) : Command()
    data class WebShareRequest(val data: JsCallbackData) : Command()
    data class ScreenLock(val data: JsCallbackData) : Command()
    object ScreenUnlock : Command()
    data object ShowFaviconsPrompt : Command()
    data class ShowSSLError(val handler: SslErrorHandler, val error: SslErrorResponse) : Command()
    data object HideSSLError : Command()
    class LaunchScreen(
        val screen: String,
        val payload: String,
    ) : Command()
    data class HideOnboardingDaxDialog(val onboardingCta: OnboardingDaxDialogCta) : Command()
    data class HideBrokenSitePromptCta(val brokenSitePromptDialogCta: BrokenSitePromptDialogCta) : Command()
    data class ShowRemoveSearchSuggestionDialog(val suggestion: AutoCompleteSuggestion) : Command()
    data object AutocompleteItemRemoved : Command()
    object OpenDuckPlayerSettings : Command()
    object OpenDuckPlayerOverlayInfo : Command()
    object OpenDuckPlayerPageInfo : Command()
    class SetBrowserBackground(@DrawableRes val backgroundRes: Int) : Command()
    class SetOnboardingDialogBackground(@DrawableRes val backgroundRes: Int) : Command()
    data class LaunchFireDialogFromOnboardingDialog(val onboardingCta: OnboardingDaxDialogCta) : Command()
    data class SwitchToTab(val tabId: String) : Command()
    data class StartExperimentTrackersBurstAnimation(val logos: List<TrackerLogo>) : Command()
    data object StartExperimentShieldPopAnimation : Command()
}
