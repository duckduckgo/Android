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

package com.duckduckgo.app.browser.newtab

import android.annotation.SuppressLint
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.duckduckgo.app.browser.remotemessage.CommandActionMapper
import com.duckduckgo.app.cta.db.DismissedCtaDao
import com.duckduckgo.app.cta.model.CtaId
import com.duckduckgo.app.onboarding.ui.page.extendedonboarding.ExtendedOnboardingFeatureToggles
import com.duckduckgo.app.settings.db.SettingsDataStore
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.common.utils.playstore.PlayStoreUtils
import com.duckduckgo.mobile.android.app.tracking.AppTrackingProtection
import com.duckduckgo.remote.messaging.api.RemoteMessage
import com.duckduckgo.remote.messaging.api.RemoteMessageModel
import com.duckduckgo.savedsites.api.SavedSitesRepository
import com.duckduckgo.savedsites.api.models.SavedSite.Favorite
import com.duckduckgo.sync.api.engine.SyncEngine
import com.duckduckgo.sync.api.engine.SyncEngine.SyncTrigger.FEATURE_READ
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@SuppressLint("NoLifecycleObserver") // we don't observe app lifecycle
class NewTabLegacyPageViewModel @AssistedInject constructor(
    @Assisted private val showDaxLogo: Boolean,
    private val dispatchers: DispatcherProvider,
    private val remoteMessagingModel: RemoteMessageModel,
    private val playStoreUtils: PlayStoreUtils,
    private val savedSitesRepository: SavedSitesRepository,
    private val syncEngine: SyncEngine,
    private val commandActionMapper: CommandActionMapper,
    private val dismissedCtaDao: DismissedCtaDao,
    private val extendedOnboardingFeatureToggles: ExtendedOnboardingFeatureToggles,
    private val settingsDataStore: SettingsDataStore,
    private val lowPriorityMessagingModel: LowPriorityMessagingModel,
    private val appTrackingProtection: AppTrackingProtection,
) : ViewModel(), DefaultLifecycleObserver {

    data class ViewState(
        private val showDaxLogo: Boolean,
        private val appTpEnabled: Boolean = false,
        val message: RemoteMessage? = null,
        val newMessage: Boolean = false,
        val onboardingComplete: Boolean = false,
        val favourites: List<Favorite> = emptyList(),
        val lowPriorityMessage: LowPriorityMessage? = null,
    ) {

        private val hasContentThatDisplacesHomoLogo = onboardingComplete &&
            message != null ||
            favourites.isNotEmpty()
        private val hasLowPriorityMessage = lowPriorityMessage != null

        val shouldShowLogo = !hasContentThatDisplacesHomoLogo && showDaxLogo
        val hasContent = shouldShowLogo || hasContentThatDisplacesHomoLogo || appTpEnabled || hasLowPriorityMessage
    }

    private data class ViewStateSnapshot(
        val favourites: List<Favorite>,
        val remoteMessage: RemoteMessage?,
    )

    sealed class Command {
        data object DismissMessage : Command()
        data class LaunchPlayStore(val appPackage: String) : Command()
        data class SubmitUrl(val url: String) : Command()
        data object LaunchDefaultBrowser : Command()
        data object LaunchAppTPOnboarding : Command()
        data class SharePromoLinkRMF(
            val url: String,
            val shareTitle: String,
        ) : Command()

        data class LaunchScreen(
            val screen: String,
            val payload: String,
        ) : Command()
    }

    private var lastRemoteMessageSeen: RemoteMessage? = null
    private val _viewState = MutableStateFlow(ViewState(showDaxLogo = showDaxLogo))
    val viewState = _viewState.asStateFlow()

    private val command = Channel<Command>(1, BufferOverflow.DROP_OLDEST)
    internal fun commands(): Flow<Command> = command.receiveAsFlow()

    override fun onStart(owner: LifecycleOwner) {
        super.onStart(owner)

        viewModelScope.launch(dispatchers.io()) {
            savedSitesRepository.getFavorites()
                .combine(remoteMessagingModel.getActiveMessages()) { favorites, activeMessage ->
                    if (favorites.isNotEmpty()) {
                        syncEngine.triggerSync(FEATURE_READ)
                    }
                    ViewStateSnapshot(favorites, activeMessage)
                }
                .flowOn(dispatchers.io())
                .onEach { snapshot ->
                    val newMessage = snapshot.remoteMessage?.id != lastRemoteMessageSeen?.id
                    if (newMessage) {
                        lastRemoteMessageSeen = snapshot.remoteMessage
                    }

                    withContext(dispatchers.io()) {
                        _viewState.emit(
                            viewState.value.copy(
                                message = snapshot.remoteMessage,
                                newMessage = newMessage,
                                favourites = snapshot.favourites,
                                onboardingComplete = isHomeOnboardingComplete(),
                                lowPriorityMessage = if (!newMessage) lowPriorityMessagingModel.getMessage() else null,
                            ),
                        )
                    }
                }
                .flowOn(dispatchers.main())
                .launchIn(viewModelScope)
        }

        viewModelScope.launch {
            _viewState.update {
                it.copy(appTpEnabled = appTrackingProtection.isEnabled())
            }
        }
    }

    // We only want to show New Tab when the Home CTAs from Onboarding has finished
    // https://app.asana.com/0/1157893581871903/1207769731595075/f
    private fun isHomeOnboardingComplete(): Boolean {
        val noBrowserCtaExperiment = extendedOnboardingFeatureToggles.noBrowserCtas().isEnabled()
        return dismissedCtaDao.exists(CtaId.DAX_END) ||
            noBrowserCtaExperiment ||
            settingsDataStore.hideTips ||
            dismissedCtaDao.exists(CtaId.ADD_WIDGET)
    }

    fun onMessageShown() {
        val message = lastRemoteMessageSeen ?: return
        viewModelScope.launch {
            remoteMessagingModel.onMessageShown(message)
        }
    }

    fun onMessageCloseButtonClicked() {
        val message = lastRemoteMessageSeen ?: return
        viewModelScope.launch {
            remoteMessagingModel.onMessageDismissed(message)
        }
    }

    fun onMessagePrimaryButtonClicked() {
        val message = lastRemoteMessageSeen ?: return
        viewModelScope.launch {
            val action = remoteMessagingModel.onPrimaryActionClicked(message) ?: return@launch
            val tabCommand = commandActionMapper.asNewTabCommand(action)
            command.send(tabCommand)
        }
    }

    fun onMessageSecondaryButtonClicked() {
        val message = lastRemoteMessageSeen ?: return
        viewModelScope.launch {
            val action = remoteMessagingModel.onSecondaryActionClicked(message) ?: return@launch
            val tabCommand = commandActionMapper.asNewTabCommand(action)
            command.send(tabCommand)
        }
    }

    fun onMessageActionButtonClicked() {
        val message = lastRemoteMessageSeen ?: return
        viewModelScope.launch {
            val action = remoteMessagingModel.onActionClicked(message) ?: return@launch
            val tabCommand = commandActionMapper.asNewTabCommand(action)
            command.send(tabCommand)
        }
    }

    fun openPlayStore(appPackage: String) {
        playStoreUtils.launchPlayStore(appPackage)
    }

    fun onLowPriorityMessagePrimaryButtonClicked() {
        viewModelScope.launch {
            lowPriorityMessagingModel.getPrimaryButtonCommand()?.let { command.send(it) }
        }
    }

    class NewTabLegacyPageViewModelProviderFactory(
        private val assistedFactory: NewTabLegacyPageViewModelFactory,
        private val showDaxLogo: Boolean,
    ) : ViewModelProvider.Factory {

        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return assistedFactory.create(showDaxLogo) as T
        }
    }

    @AssistedFactory
    interface NewTabLegacyPageViewModelFactory {
        fun create(
            showDaxLogo: Boolean,
        ): NewTabLegacyPageViewModel
    }
}
