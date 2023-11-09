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

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.duckduckgo.anvil.annotations.ContributesViewModel
import com.duckduckgo.app.global.DispatcherProvider
import com.duckduckgo.app.global.model.Site
import com.duckduckgo.app.privacy.db.UserAllowListDao
import com.duckduckgo.app.statistics.pixels.Pixel
import com.duckduckgo.browser.api.brokensite.BrokenSiteData
import com.duckduckgo.di.scopes.ActivityScope
import com.duckduckgo.privacy.dashboard.impl.pixels.PrivacyDashboardPixels.DASHBOARD_TOGGLE_HIGHLIGHT
import com.duckduckgo.privacy.dashboard.impl.pixels.PrivacyDashboardPixels.PRIVACY_DASHBOARD_ALLOWLIST_ADD
import com.duckduckgo.privacy.dashboard.impl.pixels.PrivacyDashboardPixels.PRIVACY_DASHBOARD_ALLOWLIST_REMOVE
import com.duckduckgo.privacy.dashboard.impl.pixels.PrivacyDashboardPixels.PRIVACY_DASHBOARD_OPENED
import com.duckduckgo.privacy.dashboard.impl.pixels.PrivacyDashboardRemoteFeature
import com.duckduckgo.privacy.dashboard.impl.ui.PrivacyDashboardHybridViewModel.Command.LaunchReportBrokenSite
import com.duckduckgo.privacy.dashboard.impl.ui.PrivacyDashboardHybridViewModel.Command.OpenSettings
import com.duckduckgo.privacy.dashboard.impl.ui.PrivacyDashboardHybridViewModel.Command.OpenURL
import java.util.*
import javax.inject.Inject
import kotlinx.coroutines.channels.BufferOverflow.DROP_OLDEST
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber

@ContributesViewModel(ActivityScope::class)
class PrivacyDashboardHybridViewModel @Inject constructor(
    private val userAllowListDao: UserAllowListDao,
    private val pixel: Pixel,
    private val dispatcher: DispatcherProvider,
    private val siteViewStateMapper: SiteViewStateMapper,
    private val requestDataViewStateMapper: RequestDataViewStateMapper,
    private val protectionStatusViewStateMapper: ProtectionStatusViewStateMapper,
    private val privacyDashboardPayloadAdapter: PrivacyDashboardPayloadAdapter,
    private val autoconsentStatusViewStateMapper: AutoconsentStatusViewStateMapper,
    private val privacyDashboardRemoteFeature: PrivacyDashboardRemoteFeature,
) : ViewModel() {

    private val command = Channel<Command>(1, DROP_OLDEST)

    sealed class Command {
        class LaunchReportBrokenSite(val data: BrokenSiteData) : Command()
        class OpenURL(val url: String) : Command()
        class OpenSettings(val target: String) : Command()
    }

    data class ViewState(
        val siteViewState: SiteViewState,
        val userChangedValues: Boolean = false,
        val requestData: RequestDataViewState,
        val protectionStatus: ProtectionStatusViewState,
        val cookiePromptManagementStatus: CookiePromptManagementState,
        val remoteFeatureSettings: RemoteFeatureSettingsViewState = RemoteFeatureSettingsViewState(),
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
        val primaryScreen: PrimaryScreenSettings = PrimaryScreenSettings(),
    )

    enum class LayoutType(val value: String) {
        DEFAULT("default"),
        HIGHLIGHTED_PROTECTIONS_TOGGLE("highlighted-protections-toggle"),
    }

    data class PrimaryScreenSettings(
        val layout: String = LayoutType.DEFAULT.value,
    )

    val viewState = MutableStateFlow<ViewState?>(null)

    private val site = MutableStateFlow<Site?>(null)

    init {
        pixel.fire(PRIVACY_DASHBOARD_OPENED)

        site.filterNotNull()
            .onEach(::updateSite)
            .launchIn(viewModelScope)
    }

    fun viewState(): StateFlow<ViewState?> {
        return viewState
    }

    fun commands(): Flow<Command> {
        return command.receiveAsFlow()
    }

    fun onReportBrokenSiteSelected() {
        viewModelScope.launch(dispatcher.io()) {
            // when the broken site form is opened from the dashboard, send
            // along a list of params to be sent with the `m_bsr` pixel
            val siteData = BrokenSiteData.fromSite(site.value, pixelParamList())
            command.send(LaunchReportBrokenSite(siteData))
        }
    }

    fun onSiteChanged(site: Site?) {
        this.site.value = site
    }

    private fun pixelParamList(): List<String> {
        val viewState = viewState.value ?: return emptyList()
        if (viewState.remoteFeatureSettings.primaryScreen.layout == LayoutType.DEFAULT.value) return emptyList()
        // otherwise, send the pixel param
        return listOf(DASHBOARD_TOGGLE_HIGHLIGHT.pixelName)
    }

    private fun pixelParamMap(): Map<String, String> {
        return pixelParamList().associateWith { true.toString() }
    }

    private suspend fun createRemoteFeatureSettingsViewState() = withContext(dispatcher.io()) {
        val altLayoutEnabled = privacyDashboardRemoteFeature.highlightedProtectionsToggle().isEnabled()
        val isEnglish = Locale.getDefault().language == Locale.ENGLISH.language
        val primaryLayout = if (altLayoutEnabled && isEnglish) {
            LayoutType.HIGHLIGHTED_PROTECTIONS_TOGGLE.value
        } else {
            LayoutType.DEFAULT.value
        }
        return@withContext RemoteFeatureSettingsViewState(
            primaryScreen = PrimaryScreenSettings(
                layout = primaryLayout,
            ),
        )
    }

    private suspend fun updateSite(site: Site) {
        val remoteFeatureSettings = createRemoteFeatureSettingsViewState()
        withContext(dispatcher.main()) {
            viewState.emit(
                ViewState(
                    siteViewState = siteViewStateMapper.mapFromSite(site),
                    requestData = requestDataViewStateMapper.mapFromSite(site),
                    protectionStatus = protectionStatusViewStateMapper.mapFromSite(site),
                    cookiePromptManagementStatus = autoconsentStatusViewStateMapper.mapFromSite(site),
                    remoteFeatureSettings = remoteFeatureSettings,
                ),
            )
        }
    }

    fun onPrivacyProtectionsClicked(enabled: Boolean) {
        Timber.i("PrivacyDashboard: onPrivacyProtectionsClicked $enabled")

        viewModelScope.launch(dispatcher.io()) {
            currentViewState().siteViewState.domain?.let { domain ->
                val pixelParams = pixelParamMap()
                if (enabled) {
                    userAllowListDao.delete(domain)
                    pixel.fire(PRIVACY_DASHBOARD_ALLOWLIST_REMOVE, pixelParams)
                } else {
                    userAllowListDao.insert(domain)
                    pixel.fire(PRIVACY_DASHBOARD_ALLOWLIST_ADD, pixelParams)
                }
            }
            delay(CLOSE_DASHBOARD_ON_INTERACTION_DELAY)
            withContext(dispatcher.main()) {
                viewState.value = currentViewState().copy(
                    protectionStatus = currentViewState().protectionStatus.copy(allowlisted = enabled),
                    userChangedValues = true,
                )
            }
        }
    }

    private companion object {
        const val CLOSE_DASHBOARD_ON_INTERACTION_DELAY = 300L
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
}
