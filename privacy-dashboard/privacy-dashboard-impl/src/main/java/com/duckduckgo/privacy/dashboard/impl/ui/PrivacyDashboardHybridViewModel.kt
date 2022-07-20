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
import com.duckduckgo.app.di.AppCoroutineScope
import com.duckduckgo.app.global.DispatcherProvider
import com.duckduckgo.app.global.model.Site
import com.duckduckgo.app.privacy.db.UserWhitelistDao
import com.duckduckgo.app.statistics.pixels.Pixel
import com.duckduckgo.browser.api.brokensite.BrokenSiteData
import com.duckduckgo.di.scopes.ActivityScope
import com.duckduckgo.privacy.config.api.ContentBlocking
import com.duckduckgo.privacy.dashboard.impl.pixels.PrivacyDashboardPixels.PRIVACY_DASHBOARD_ALLOWLIST_ADD
import com.duckduckgo.privacy.dashboard.impl.pixels.PrivacyDashboardPixels.PRIVACY_DASHBOARD_ALLOWLIST_REMOVE
import com.duckduckgo.privacy.dashboard.impl.pixels.PrivacyDashboardPixels.PRIVACY_DASHBOARD_OPENED
import com.duckduckgo.privacy.dashboard.impl.ui.PrivacyDashboardHybridViewModel.Command.LaunchReportBrokenSite
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.BufferOverflow.DROP_OLDEST
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.util.*
import javax.inject.Inject

@ContributesViewModel(ActivityScope::class)
class PrivacyDashboardHybridViewModel @Inject constructor(
    private val userWhitelistDao: UserWhitelistDao,
    private val contentBlocking: ContentBlocking,
    private val pixel: Pixel,
    private val dispatcher: DispatcherProvider,
    @AppCoroutineScope private val appCoroutineScope: CoroutineScope,
    private val siteProtectionsViewStateMapper: SiteProtectionsViewStateMapper
) : ViewModel() {

    private val command = Channel<Command>(1, DROP_OLDEST)

    sealed class Command {
        class LaunchReportBrokenSite(val data: BrokenSiteData) : Command()
    }

    data class ViewState(
        val userSettingsViewState: UserSettingsViewState,
        val siteProtectionsViewState: SiteProtectionsViewState,
        val userChangedValues: Boolean = false
    )

    data class UserSettingsViewState(
        val privacyProtectionEnabled: Boolean
    )

    data class SiteProtectionsViewState(
        val url: String,
        val status: String = "complete",
        val upgradedHttps: Boolean,
        val parentEntity: EntityViewState?,
        val site: SiteViewState,
        val trackers: Map<String, TrackerViewState>,
        val trackerBlocked: Map<String, TrackerViewState>,
        val secCertificateViewModels: List<CertificateViewState?> = emptyList(),
        val locale: String = Locale.getDefault().language
    )

    data class CertificateViewState(
        val commonName: String,
        val publicKey: PublicKeyViewState? = null,
        val emails: List<String> = emptyList(),
        val summary: String? = null
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
        val keyId: String?
    )

    data class EntityViewState(
        val displayName: String,
        val prevalence: Double
    )

    data class SiteViewState(
        val url: String,
        val domain: String,
        val trackersUrls: Set<String>,
        val whitelisted: Boolean,
    )

    data class TrackerViewState(
        val displayName: String,
        val prevalence: Double,
        val urls: Map<String, TrackerEventViewState>,
        val count: Int,
        val type: String = ""
    )

    data class TrackerEventViewState(
        val isBlocked: Boolean,
        val reason: String,
        val categories: Set<String> = emptySet()
    )

    val viewState = MutableStateFlow<ViewState?>(null)

    private var site: Site? = null

    init {
        pixel.fire(PRIVACY_DASHBOARD_OPENED)
        resetViewState()
    }

    fun viewState(): StateFlow<ViewState?> {
        return viewState
    }

    fun commands(): Flow<Command> {
        return command.receiveAsFlow()
    }

    fun onReportBrokenSiteSelected() {
        viewModelScope.launch(dispatcher.io()) {
            command.send(LaunchReportBrokenSite(BrokenSiteData.fromSite(site)))
        }
    }

    fun onSiteChanged(site: Site?) {
        Timber.i("PDHy: onSiteChanged $site")
        this.site = site
        if (site == null) {
            resetViewState()
        } else {
            viewModelScope.launch { updateSite(site) }
        }
    }

    private fun resetViewState() {
    }

    private suspend fun updateSite(site: Site) {
        Timber.i("PDHy: will generate viewstate for site:$site entity:${site.entity}")
        withContext(dispatcher.main()) {
            Timber.i("PDHy: site had ${site.trackingEvents.size} events / ${site.trackerCount}")
            viewState.emit(
                ViewState(
                    siteProtectionsViewState = siteProtectionsViewStateMapper.mapFromSite(site),
                    userSettingsViewState = UserSettingsViewState(
                        privacyProtectionEnabled = !site.userAllowList
                    )
                )
            )
        }
    }

    fun onPrivacyProtectionsClicked(enabled: Boolean) {
        Timber.i("PDHy: onPrivacyProtectionsClicked newValue $enabled")
        viewModelScope.launch(dispatcher.io()) {
            if (enabled) {
                userWhitelistDao.delete(currentViewState().siteProtectionsViewState.site.domain)
                pixel.fire(PRIVACY_DASHBOARD_ALLOWLIST_REMOVE)
            } else {
                userWhitelistDao.insert(currentViewState().siteProtectionsViewState.site.domain)
                pixel.fire(PRIVACY_DASHBOARD_ALLOWLIST_ADD)
            }
            delay(CLOSE_DASHBOARD_ON_INTERACTION_DELAY)
            withContext(dispatcher.main()) {
                viewState.value = currentViewState().copy(
                    userSettingsViewState = currentViewState().userSettingsViewState.copy(privacyProtectionEnabled = enabled),
                    userChangedValues = true
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
}
