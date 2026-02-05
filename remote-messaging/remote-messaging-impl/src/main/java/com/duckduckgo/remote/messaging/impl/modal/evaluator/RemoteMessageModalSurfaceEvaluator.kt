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

package com.duckduckgo.remote.messaging.impl.modal.evaluator

import android.content.Context
import android.content.Intent
import android.os.SystemClock
import com.duckduckgo.app.di.AppCoroutineScope
import com.duckduckgo.app.onboarding.OnboardingFlowChecker
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.modalcoordinator.api.ModalEvaluator
import com.duckduckgo.navigation.api.GlobalActivityStarter
import com.duckduckgo.remote.messaging.api.RemoteMessagingRepository
import com.duckduckgo.remote.messaging.api.Surface
import com.duckduckgo.remote.messaging.impl.RemoteMessagingFeatureToggles
import com.duckduckgo.remote.messaging.impl.modal.ModalSurfaceActivityFromMessageId
import com.duckduckgo.remote.messaging.impl.store.ModalSurfaceStore
import com.squareup.anvil.annotations.ContributesBinding
import com.squareup.anvil.annotations.ContributesMultibinding
import dagger.SingleInstanceIn
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

interface RemoteMessageModalSurfaceEvaluator

@ContributesMultibinding(
    scope = AppScope::class,
    boundType = ModalEvaluator::class,
)
@ContributesBinding(
    scope = AppScope::class,
    boundType = RemoteMessageModalSurfaceEvaluator::class,
)
@SingleInstanceIn(scope = AppScope::class)
class RemoteMessageModalSurfaceEvaluatorImpl @Inject constructor(
    @AppCoroutineScope private val appCoroutineScope: CoroutineScope,
    private val remoteMessagingRepository: RemoteMessagingRepository,
    private val modalSurfaceStore: ModalSurfaceStore,
    private val globalActivityStarter: GlobalActivityStarter,
    private val dispatchers: DispatcherProvider,
    private val applicationContext: Context,
    private val remoteMessagingFeatureToggles: RemoteMessagingFeatureToggles,
    private val onboardingFlowChecker: OnboardingFlowChecker,
) : RemoteMessageModalSurfaceEvaluator, ModalEvaluator {

    override val priority: Int = 1
    override val evaluatorId: String = "remote_message_modal"

    override suspend fun evaluate(): ModalEvaluator.EvaluationResult {
        return withContext(dispatchers.io()) {
            if (!remoteMessagingFeatureToggles.remoteMessageModalSurface().isEnabled()) {
                return@withContext ModalEvaluator.EvaluationResult.Skipped
            }

            if (!onboardingFlowChecker.isOnboardingComplete()) {
                return@withContext ModalEvaluator.EvaluationResult.Skipped
            }

            if (!hasMetBackgroundTimeThreshold()) {
                return@withContext ModalEvaluator.EvaluationResult.Skipped
            }

            val message = remoteMessagingRepository.message()
                ?: return@withContext ModalEvaluator.EvaluationResult.Skipped

            if (message.surfaces.contains(Surface.MODAL)) {
                // Skip if this message was already shown
                val lastShownMessageId = modalSurfaceStore.getLastShownRemoteMessageId()
                if (lastShownMessageId == message.id) {
                    remoteMessagingRepository.dismissMessage(lastShownMessageId)
                    return@withContext ModalEvaluator.EvaluationResult.Skipped
                }

                val intent = globalActivityStarter.startIntent(
                    applicationContext,
                    ModalSurfaceActivityFromMessageId(message.id, message.content.messageType),
                ) ?: return@withContext ModalEvaluator.EvaluationResult.Skipped

                // Launch activity in app scope to decouple from evaluation completion

                delay(MODAL_DISPLAY_DELAY)
                appCoroutineScope.launch(dispatchers.main()) {
                    intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
                    applicationContext.startActivity(intent)
                }

                // Record this message as shown, and clear background timestamp
                modalSurfaceStore.recordLastShownRemoteMessage(message)
                modalSurfaceStore.clearBackgroundTimestamp()

                return@withContext ModalEvaluator.EvaluationResult.ModalShown
            }

            return@withContext ModalEvaluator.EvaluationResult.Skipped
        }
    }

    private suspend fun hasMetBackgroundTimeThreshold(): Boolean {
        val backgroundTimestamp = modalSurfaceStore.getBackgroundedTimestamp() ?: return false

        // Using elapsed real time as this is how it's saved in the data store.
        val currentTimestamp = SystemClock.elapsedRealtime()
        return (currentTimestamp - backgroundTimestamp) >= BACKGROUND_THRESHOLD_MILLIS
    }

    companion object {
        private const val BACKGROUND_THRESHOLD_MILLIS = 4 * 60 * 60 * 1000L // 4 hours
        private const val MODAL_DISPLAY_DELAY = 1500L
    }
}
