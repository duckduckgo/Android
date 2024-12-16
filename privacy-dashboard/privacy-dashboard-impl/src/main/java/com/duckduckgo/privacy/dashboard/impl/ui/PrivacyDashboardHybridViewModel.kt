/*
 * Copyright (c) 2022 DuckDuckGo
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

package com.duckduckgo.privacy.dashboard.impl.ui

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.duckduckgo.anvil.annotations.ContributesViewModel
import com.duckduckgo.app.global.model.Site
import com.duckduckgo.app.global.model.domain
import com.duckduckgo.app.privacy.db.UserAllowListRepository
import com.duckduckgo.app.statistics.pixels.Pixel
import com.duckduckgo.app.statistics.pixels.Pixel.PixelType.Count
import com.duckduckgo.app.statistics.pixels.Pixel.PixelType.Unique
import com.duckduckgo.app.trackerdetection.model.TrackerStatus.BLOCKED
import com.duckduckgo.brokensite.api.BrokenSite
import com.duckduckgo.brokensite.api.BrokenSiteSender
import com.duckduckgo.brokensite.api.ReportFlow
import com.duckduckgo.browser.api.UserBrowserProperties
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.common.utils.baseHost
import com.duckduckgo.common.utils.plugins.PluginPoint
import com.duckduckgo.di.scopes.ActivityScope
import com.duckduckgo.privacy.dashboard.api.PrivacyProtectionTogglePlugin
import com.duckduckgo.privacy.dashboard.api.PrivacyToggleOrigin
import com.duckduckgo.privacy.dashboard.api.ui.DashboardOpener
import com.duckduckgo.privacy.dashboard.api.ui.ToggleReports
import com.duckduckgo.privacy.dashboard.impl.pixels.PrivacyDashboardCustomTabPixelNames.CUSTOM_TABS_PRIVACY_DASHBOARD_ALLOW_LIST_ADD
import com.duckduckgo.privacy.dashboard.impl.pixels.PrivacyDashboardCustomTabPixelNames.CUSTOM_TABS_PRIVACY_DASHBOARD_ALLOW_LIST_REMOVE
import com.duckduckgo.privacy.dashboard.impl.pixels.PrivacyDashboardPixels.*
import com.duckduckgo.privacy.dashboard.impl.ui.AppPrivacyDashboardPayloadAdapter.ToggleReportOptions
import com.duckduckgo.privacy.dashboard.impl.ui.PrivacyDashboardHybridViewModel.Command.FetchToggleData
import com.duckduckgo.privacy.dashboard.impl.ui.PrivacyDashboardHybridViewModel.Command.GoBack
import com.duckduckgo.privacy.dashboard.impl.ui.PrivacyDashboardHybridViewModel.Command.LaunchAppFeedback
import com.duckduckgo.privacy.dashboard.impl.ui.PrivacyDashboardHybridViewModel.Command.LaunchToggleReport
import com.duckduckgo.privacy.dashboard.impl.ui.PrivacyDashboardHybridViewModel.Command.OpenSettings
import com.duckduckgo.privacy.dashboard.impl.ui.PrivacyDashboardHybridViewModel.Command.OpenURL
import com.duckduckgo.privacy.dashboard.impl.ui.ScreenKind.BREAKAGE_FORM
import com.duckduckgo.privacy.dashboard.impl.ui.ScreenKind.PRIMARY_SCREEN
import com.duckduckgo.privacyprotectionspopup.api.PrivacyProtectionsPopupExperimentExternalPixels
import com.duckduckgo.privacyprotectionspopup.api.PrivacyProtectionsToggleUsageListener
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import java.util.Locale
import javax.inject.Inject
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.BufferOverflow.DROP_OLDEST
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber

@OptIn(ExperimentalCoroutinesApi::class)
@ContributesViewModel(ActivityScope::class)
class PrivacyDashboardHybridViewModel @Inject constructor(
    private val userAllowListRepository: UserAllowListRepository,
    private val pixel: Pixel,
    private val dispatcher: DispatcherProvider,
    private val siteViewStateMapper: SiteViewStateMapper,
    private val requestDataViewStateMapper: RequestDataViewStateMapper,
    private val protectionStatusViewStateMapper: ProtectionStatusViewStateMapper,
    private val privacyDashboardPayloadAdapter: PrivacyDashboardPayloadAdapter,
    private val autoconsentStatusViewStateMapper: AutoconsentStatusViewStateMapper,
    private val protectionsToggleUsageListener: PrivacyProtectionsToggleUsageListener,
    private val privacyProtectionsPopupExperimentExternalPixels: PrivacyProtectionsPopupExperimentExternalPixels,
    private val userBrowserProperties: UserBrowserProperties,
    private val toggleReports: ToggleReports,
    private val brokenSiteSender: BrokenSiteSender,
    private val moshi: Moshi,
    private val privacyProtectionTogglePlugin: PluginPoint<PrivacyProtectionTogglePlugin>,
) : ViewModel() {

    private val command = Channel<Command>(1, DROP_OLDEST)

    sealed class Command {
        class LaunchToggleReport(val opener: DashboardOpener) : Command()
        class OpenURL(val url: String) : Command()
        class OpenSettings(val target: String) : Command()
        class FetchToggleData(val toggleData: String) : Command()
        data object LaunchAppFeedback : Command()
        data object GoBack : Command()
    }

    data class ViewState(
        val siteViewState: SiteViewState,
        val userChangedValues: Boolean = false,
        val requestData: RequestDataViewState,
        val protectionStatus: ProtectionStatusViewState,
        val cookiePromptManagementStatus: CookiePromptManagementState,
        val remoteFeatureSettings: RemoteFeatureSettingsViewState,
    )

    data class ProtectionStatusViewState(
        val allowlisted: Boolean,
        val denylisted: Boolean,
        val enabledFeatures: List<String>,
        val unprotectedTemporary: Boolean,
    )

    data class RequestDataViewState(
        val installedSurrogates: List<String>? = null,
        val requests: List<DetectedRequest>,
    )

    data class DetectedRequest(
        val category: String?,
        val eTLDplus1: String?,
        val entityName: String?,
        val ownerName: String?,
        val pageUrl: String,
        val prevalence: Double?,
        val state: RequestState,
        val url: String,
    )

    sealed class RequestState {
        // Using Any as placeholde value. We shouldn't pass any value to blocked. This is just to honour expected json.
        // refer to docs in: https://duckduckgo.github.io/privacy-dashboard/example/docs/interfaces/Generated_Schema_Definitions.StateBlocked.html#blocked
        data class Blocked(val blocked: Any = Any()) : RequestState()
        data class Allowed(val allowed: Reason) : RequestState()
    }

    data class Reason(val reason: String)

    enum class AllowedReasons(val value: String) {
        PROTECTIONS_DISABLED("protectionDisabled"),
        OWNED_BY_FIRST_PARTY("ownedByFirstParty"),
        RULE_EXCEPTION("ruleException"),
        AD_CLICK_ATTRIBUTION("adClickAttribution"),
        OTHER_THIRD_PARTY_REQUEST("otherThirdPartyRequest"),
    }

    data class SiteViewState(
        val url: String,
        val domain: String?,
        val upgradedHttps: Boolean,
        val parentEntity: EntityViewState?,
        val secCertificateViewModels: List<CertificateViewState?> = emptyList(),
        val locale: String = Locale.getDefault().language,
    )

    data class CertificateViewState(
        val commonName: String,
        val publicKey: PublicKeyViewState? = null,
        val emails: List<String> = emptyList(),
        val summary: String? = null,
    )

    data class PublicKeyViewState(
        val blockSize: Int?,
        val canEncrypt: Boolean?,
        val bitSize: Int?,
        val canSign: Boolean?,
        val canDerive: Boolean?,
        val canUnwrap: Boolean?,
        val canWrap: Boolean?,
        val canDecrypt: Boolean?,
        val effectiveSize: Int?,
        val isPermanent: Boolean?,
        val type: String?,
        val externalRepresentation: String?,
        val canVerify: Boolean?,
        val keyId: String?,
    )

    data class EntityViewState(
        val displayName: String,
        val prevalence: Double,
    )

    data class CookiePromptManagementState(
        val consentManaged: Boolean = false,
        val optoutFailed: Boolean? = false,
        val configurable: Boolean? = true,
        val cosmetic: Boolean? = false,
    )

    data class RemoteFeatureSettingsViewState(
        val primaryScreen: PrimaryScreenSettings,
        val webBreakageForm: WebBrokenSiteFormSettings,
    )

    enum class LayoutType(val value: String) {
        DEFAULT("default"),
    }

    data class PrimaryScreenSettings(
        val layout: String,
    )

    data class WebBrokenSiteFormSettings(
        val state: String,
    )

    enum class WebBrokenSiteFormState(val value: String) {
        ENABLED("enabled"),
        DISABLED("disabled"),
    }

    val viewState = MutableStateFlow<ViewState?>(null)

    private val site = MutableStateFlow<Site?>(null)

    init {
        viewModelScope.launch {
            val pixelParams = privacyProtectionsPopupExperimentExternalPixels.getPixelParams()
            pixel.fire(PRIVACY_DASHBOARD_OPENED, pixelParams, type = Count)
            pixel.fire(
                pixel = PRIVACY_DASHBOARD_FIRST_TIME_OPENED,
                parameters = mapOf("daysSinceInstall" to userBrowserProperties.daysSinceInstalled().toString(), "from_onboarding" to "false"),
                type = Unique(),
            )
        }
        privacyProtectionsPopupExperimentExternalPixels.tryReportPrivacyDashboardOpened()

        site.filterNotNull()
            .onEach(::updateSite)
            .launchIn(viewModelScope)

        site.filterNotNull()
            .mapNotNull { it.domain }
            .distinctUntilChanged()
            .flatMapLatest { domain ->
                userAllowListRepository.domainsInUserAllowListFlow()
                    .map { allowlistedDomains -> domain in allowlistedDomains }
                    .distinctUntilChanged()
                    .drop(1) // Emit only when domain was added to or removed from the allowlist
            }
            .onEach {
                // Setting userChangedValues to true will trigger closing the screen
                viewState.update { it?.copy(userChangedValues = true) }
            }
            .launchIn(viewModelScope)
    }

    fun viewState(): StateFlow<ViewState?> {
        return viewState
    }

    fun commands(): Flow<Command> {
        return command.receiveAsFlow()
    }

    fun launchAppFeedbackFlow() {
        viewModelScope.launch(dispatcher.io()) {
            command.send(LaunchAppFeedback)
        }
    }

    fun onSiteChanged(site: Site?) {
        this.site.value = site
    }

    private suspend fun updateSite(site: Site) {
        withContext(dispatcher.main()) {
            viewState.emit(
                ViewState(
                    siteViewState = siteViewStateMapper.mapFromSite(site),
                    requestData = requestDataViewStateMapper.mapFromSite(site),
                    protectionStatus = protectionStatusViewStateMapper.mapFromSite(site),
                    cookiePromptManagementStatus = autoconsentStatusViewStateMapper.mapFromSite(site),
                    remoteFeatureSettings = createRemoteFeatureSettings(),
                ),
            )
        }
    }

    private suspend fun createRemoteFeatureSettings(): RemoteFeatureSettingsViewState {
        return RemoteFeatureSettingsViewState(
            primaryScreen = PrimaryScreenSettings(layout = LayoutType.DEFAULT.value),
            webBreakageForm = WebBrokenSiteFormSettings(state = WebBrokenSiteFormState.ENABLED.value),
        )
    }

    fun onPrivacyProtectionsClicked(
        payload: String,
        dashboardOpenedFromCustomTab: Boolean = false,
    ) {
        Timber.i("PrivacyDashboard: onPrivacyProtectionsClicked $payload")

        viewModelScope.launch(dispatcher.io()) {
            val event = privacyDashboardPayloadAdapter.onPrivacyProtectionsClicked(payload) ?: return@launch

            protectionsToggleUsageListener.onPrivacyProtectionsToggleUsed()

            delay(CLOSE_ON_PROTECTIONS_TOGGLE_DELAY)

            currentViewState().siteViewState.domain?.let { domain ->
                val pixelParams = privacyProtectionsPopupExperimentExternalPixels.getPixelParams()
                if (event.isProtected) {
                    userAllowListRepository.removeDomainFromUserAllowList(domain)
                    if (dashboardOpenedFromCustomTab) {
                        if (event.eventOrigin.screen == PRIMARY_SCREEN) {
                            pixel.fire(CUSTOM_TABS_PRIVACY_DASHBOARD_ALLOW_LIST_REMOVE)
                        }
                    } else {
                        val pixelName = when (event.eventOrigin.screen) {
                            PRIMARY_SCREEN -> PRIVACY_DASHBOARD_ALLOWLIST_REMOVE
                            BREAKAGE_FORM -> BROKEN_SITE_ALLOWLIST_REMOVE
                            else -> null
                        }
                        pixelName?.let {
                            pixel.fire(it, pixelParams, type = Count)
                            val origin = if (it == PRIVACY_DASHBOARD_ALLOWLIST_REMOVE) {
                                PrivacyToggleOrigin.DASHBOARD
                            } else {
                                PrivacyToggleOrigin.BREAKAGE_FORM
                            }
                            privacyProtectionTogglePlugin.getPlugins().forEach { plugin ->
                                plugin.onToggleOn(origin)
                            }
                        }
                    }
                } else {
                    userAllowListRepository.addDomainToUserAllowList(domain)
                    if (event.eventOrigin.screen == PRIMARY_SCREEN) {
                        if (toggleReports.shouldPrompt()) {
                            viewModelScope.launch(dispatcher.main()) {
                                command.send(LaunchToggleReport(opener = DashboardOpener.DASHBOARD))
                            }
                        }
                    }
                    if (dashboardOpenedFromCustomTab) {
                        pixel.fire(CUSTOM_TABS_PRIVACY_DASHBOARD_ALLOW_LIST_ADD)
                    } else {
                        val pixelName = when (event.eventOrigin.screen) {
                            PRIMARY_SCREEN -> PRIVACY_DASHBOARD_ALLOWLIST_ADD
                            BREAKAGE_FORM -> BROKEN_SITE_ALLOWLIST_ADD
                            else -> null
                        }
                        pixelName?.let { it ->
                            pixel.fire(it, pixelParams, type = Count)
                            val origin = if (it == PRIVACY_DASHBOARD_ALLOWLIST_ADD) {
                                PrivacyToggleOrigin.DASHBOARD
                            } else {
                                PrivacyToggleOrigin.BREAKAGE_FORM
                            }
                            privacyProtectionTogglePlugin.getPlugins().forEach { plugin ->
                                plugin.onToggleOff(origin)
                            }
                        }
                    }
                }
                privacyProtectionsPopupExperimentExternalPixels.tryReportProtectionsToggledFromPrivacyDashboard(event.isProtected)
            }
        }
    }

    private companion object {
        val CLOSE_ON_PROTECTIONS_TOGGLE_DELAY = 300.milliseconds
        val CLOSE_AFTER_TOGGLE_REPORT_PROMPT_DELAY = 200.milliseconds
    }

    private fun currentViewState(): ViewState {
        return viewState.value!!
    }

    fun onUrlClicked(payload: String) {
        viewModelScope.launch(dispatcher.io()) {
            privacyDashboardPayloadAdapter.onUrlClicked(payload).takeIf { it.isNotEmpty() }?.let {
                command.send(OpenURL(it))
            }
        }
    }

    fun onOpenSettings(payload: String) {
        viewModelScope.launch(dispatcher.io()) {
            privacyDashboardPayloadAdapter.onOpenSettings(payload).takeIf { it.isNotEmpty() }?.let {
                command.send(OpenSettings(it))
            }
        }
    }

    fun onSubmitBrokenSiteReport(
        payload: String,
        reportFlow: ReportFlow,
    ) {
        viewModelScope.launch(dispatcher.io()) {
            val request = privacyDashboardPayloadAdapter.onSubmitBrokenSiteReport(payload) ?: return@launch
            val site = site.value ?: return@launch
            val siteUrl = site.url
            if (siteUrl.isEmpty()) return@launch

            val brokenSite = BrokenSite(
                category = request.category,
                description = request.description,
                siteUrl = siteUrl,
                upgradeHttps = site.upgradedHttps,
                blockedTrackers = site.trackingEvents
                    .filter { it.status == BLOCKED }
                    .map { Uri.parse(it.trackerUrl).baseHost.orEmpty() }
                    .distinct().joinToString(","),
                surrogates = site.surrogates
                    .map { Uri.parse(it.name).baseHost }
                    .distinct()
                    .joinToString(","),
                siteType = if (site.isDesktopMode) BrokenSite.SITE_TYPE_DESKTOP else BrokenSite.SITE_TYPE_MOBILE,
                urlParametersRemoved = site.urlParametersRemoved,
                consentManaged = site.consentManaged,
                consentOptOutFailed = site.consentOptOutFailed,
                consentSelfTestFailed = site.consentSelfTestFailed,
                errorCodes = moshi.adapter<List<String>>(
                    Types.newParameterizedType(List::class.java, String::class.java),
                ).toJson(site.errorCodeEvents.toList()).toString(),
                httpErrorCodes = site.httpErrorCodeEvents.distinct().joinToString(","),
                loginSite = null,
                reportFlow = reportFlow,
                userRefreshCount = site.realBrokenSiteContext.userRefreshCount,
                openerContext = site.realBrokenSiteContext.openerContext?.context,
                jsPerformance = site.realBrokenSiteContext.jsPerformance?.toList(),
            )

            brokenSiteSender.submitBrokenSiteFeedback(brokenSite, toggle = false)
        }
    }

    fun onGetToggleReportOptions() {
        viewModelScope.launch(dispatcher.io()) {
            val site = site.value ?: return@launch
            val siteUrl = site.url

            val options = ToggleReportOptions(
                data = listOf(
                    ToggleReportOptions.ToggleReportOption(
                        id = "siteUrl",
                        additional = ToggleReportOptions.Additional(url = siteUrl),
                    ),
                    ToggleReportOptions.ToggleReportOption(id = "wvVersion"),
                    ToggleReportOptions.ToggleReportOption(id = "requests"),
                    ToggleReportOptions.ToggleReportOption(id = "features"),
                    ToggleReportOptions.ToggleReportOption(id = "appVersion"),
                    ToggleReportOptions.ToggleReportOption(id = "atb"),
                    ToggleReportOptions.ToggleReportOption(id = "errorDescriptions"),
                    ToggleReportOptions.ToggleReportOption(id = "extensionVersion"),
                    ToggleReportOptions.ToggleReportOption(id = "httpErrorCodes"),
                    ToggleReportOptions.ToggleReportOption(id = "lastSentDay"),
                    ToggleReportOptions.ToggleReportOption(id = "device"),
                    ToggleReportOptions.ToggleReportOption(id = "os"),
                    ToggleReportOptions.ToggleReportOption(id = "reportFlow"),
                    ToggleReportOptions.ToggleReportOption(id = "listVersions"),
                    ToggleReportOptions.ToggleReportOption(id = "didOpenReportInfo"),
                    ToggleReportOptions.ToggleReportOption(id = "toggleReportCounter"),
                    ToggleReportOptions.ToggleReportOption(id = "openerContext"),
                    ToggleReportOptions.ToggleReportOption(id = "userRefreshCount"),
                    ToggleReportOptions.ToggleReportOption(id = "jsPerformance"),
                    ToggleReportOptions.ToggleReportOption(id = "locale"),
                ),
            )
            privacyDashboardPayloadAdapter.onGetToggleReportOptions(options).takeIf { it.isNotEmpty() }?.let {
                command.send(FetchToggleData(it))
            }
        }
    }

    fun onToggleReportPromptDismissed() {
        viewModelScope.launch(dispatcher.io()) {
            toggleReports.onPromptDismissed()
            delay(CLOSE_AFTER_TOGGLE_REPORT_PROMPT_DELAY)
        }
    }

    fun onSubmitToggleReport(opener: DashboardOpener) {
        viewModelScope.launch(dispatcher.io()) {
            val site = site.value ?: return@launch
            val siteUrl = site.url
            if (siteUrl.isEmpty()) return@launch

            val brokenSite = BrokenSite(
                category = null,
                description = null,
                siteUrl = siteUrl,
                upgradeHttps = site.upgradedHttps,
                blockedTrackers = site.trackingEvents
                    .filter { it.status == BLOCKED }
                    .map { Uri.parse(it.trackerUrl).baseHost.orEmpty() }
                    .distinct().joinToString(","),
                surrogates = site.surrogates
                    .map { Uri.parse(it.name).baseHost }
                    .distinct()
                    .joinToString(","),
                siteType = if (site.isDesktopMode) BrokenSite.SITE_TYPE_DESKTOP else BrokenSite.SITE_TYPE_MOBILE,
                urlParametersRemoved = site.urlParametersRemoved,
                consentManaged = site.consentManaged,
                consentOptOutFailed = site.consentOptOutFailed,
                consentSelfTestFailed = site.consentSelfTestFailed,
                errorCodes = moshi.adapter<List<String>>(
                    Types.newParameterizedType(List::class.java, String::class.java),
                ).toJson(site.errorCodeEvents.toList()).toString(),
                httpErrorCodes = site.httpErrorCodeEvents.distinct().joinToString(","),
                loginSite = null,
                reportFlow = when (opener) {
                    DashboardOpener.MENU -> ReportFlow.TOGGLE_MENU
                    else -> ReportFlow.TOGGLE_DASHBOARD
                },
                userRefreshCount = site.realBrokenSiteContext.userRefreshCount,
                openerContext = site.realBrokenSiteContext.openerContext?.context,
                jsPerformance = site.realBrokenSiteContext.jsPerformance?.toList(),
            )

            brokenSiteSender.submitBrokenSiteFeedback(brokenSite, toggle = true)
            toggleReports.onReportSent()
            delay(CLOSE_AFTER_TOGGLE_REPORT_PROMPT_DELAY)
            command.send(GoBack)
        }
    }
}
