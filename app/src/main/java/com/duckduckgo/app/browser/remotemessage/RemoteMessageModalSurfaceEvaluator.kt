/*
 * Copyright (c) 2025 DuckDuckGo
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

package com.duckduckgo.app.browser.remotemessage

import android.content.Context
import android.content.Intent
import androidx.lifecycle.LifecycleOwner
import com.duckduckgo.app.cta.db.DismissedCtaDao
import com.duckduckgo.app.cta.model.CtaId
import com.duckduckgo.app.di.AppCoroutineScope
import com.duckduckgo.app.lifecycle.MainProcessLifecycleObserver
import com.duckduckgo.app.onboarding.ui.page.extendedonboarding.ExtendedOnboardingFeatureToggles
import com.duckduckgo.app.settings.db.SettingsDataStore
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.navigation.api.GlobalActivityStarter
import com.duckduckgo.remote.messaging.api.RemoteMessagingRepository
import com.duckduckgo.remote.messaging.api.Surface
import com.duckduckgo.remote.messaging.impl.RemoteMessagingFeatureToggles
import com.duckduckgo.remote.messaging.impl.ui.ModalSurfaceActivityFromMessageId
import com.squareup.anvil.annotations.ContributesBinding
import com.squareup.anvil.annotations.ContributesMultibinding
import dagger.SingleInstanceIn
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

interface RemoteMessageModalSurfaceEvaluator

@ContributesMultibinding(
    scope = AppScope::class,
    boundType = MainProcessLifecycleObserver::class,
)
@ContributesBinding(
    scope = AppScope::class,
    boundType = RemoteMessageModalSurfaceEvaluator::class,
)
@SingleInstanceIn(scope = AppScope::class)
class RemoteMessageModalSurfaceEvaluatorImpl @Inject constructor(
    @AppCoroutineScope private val appCoroutineScope: CoroutineScope,
    private val remoteMessagingRepository: RemoteMessagingRepository,
    private val dismissedCtaDao: DismissedCtaDao,
    private val extendedOnboardingFeatureToggles: ExtendedOnboardingFeatureToggles,
    private val settingsDataStore: SettingsDataStore,
    private val globalActivityStarter: GlobalActivityStarter,
    private val dispatchers: DispatcherProvider,
    private val applicationContext: Context,
    private val remoteMessagingFeatureToggles: RemoteMessagingFeatureToggles,
) : RemoteMessageModalSurfaceEvaluator, MainProcessLifecycleObserver {

    override fun onResume(owner: LifecycleOwner) {
        super.onResume(owner)
        appCoroutineScope.launch {
            evaluate()
        }
    }

    private suspend fun evaluate() {
        withContext(dispatchers.io()) {
            if (!remoteMessagingFeatureToggles.self().isEnabled() || !remoteMessagingFeatureToggles.remoteMessageModalSurface().isEnabled()) {
                return@withContext
            }

            if (!isHomeOnboardingComplete()) {
                return@withContext
            }

            // TODO ANA: This is now called all the time. Update!
            val message = remoteMessagingRepository.message() ?: return@withContext
            if (message.surfaces.contains(Surface.MODAL)) {
                val intent = globalActivityStarter.startIntent(
                    applicationContext,
                    ModalSurfaceActivityFromMessageId(message.id, message.content.messageType),
                ) ?: return@withContext

                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
                applicationContext.startActivity(intent)
            }
        }
    }

    private fun isHomeOnboardingComplete(): Boolean {
        val noBrowserCtaExperiment = extendedOnboardingFeatureToggles.noBrowserCtas().isEnabled()
        return dismissedCtaDao.exists(CtaId.DAX_END) ||
            noBrowserCtaExperiment ||
            settingsDataStore.hideTips ||
            dismissedCtaDao.exists(CtaId.ADD_WIDGET)
    }
}
