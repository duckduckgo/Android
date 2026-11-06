/*
 * Copyright (c) 2026 DuckDuckGo
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

package com.duckduckgo.duckchat.impl.promo

import androidx.annotation.UiThread
import androidx.lifecycle.LifecycleOwner
import com.duckduckgo.app.di.AppCoroutineScope
import com.duckduckgo.app.lifecycle.MainProcessLifecycleObserver
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.duckchat.impl.feature.DuckChatFeature
import com.duckduckgo.duckchat.impl.promo.data.PromoCampaignRepository
import com.duckduckgo.duckchat.impl.promo.data.PromoRecordCorruptedException
import com.duckduckgo.duckchat.impl.promo.data.PromoRecordStorageException
import dev.zacsweers.metro.ContributesIntoSet
import dev.zacsweers.metro.binding
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import logcat.LogPriority.WARN
import logcat.logcat
import javax.inject.Inject

/**
 * Ends an expired promo each time the app comes to the foreground, so its promo ID is deleted even if Duck.ai isn't opened again.
 */
@ContributesIntoSet(
    scope = AppScope::class,
    binding = binding<MainProcessLifecycleObserver>(),
)
class ExpiredPromoCleaner @Inject constructor(
    private val duckChatFeature: DuckChatFeature,
    private val promoCampaignRepository: PromoCampaignRepository,
    private val dispatcherProvider: DispatcherProvider,
    @param:AppCoroutineScope private val appCoroutineScope: CoroutineScope,
) : MainProcessLifecycleObserver {
    @UiThread
    override fun onStart(owner: LifecycleOwner) {
        appCoroutineScope.launch(dispatcherProvider.io()) {
            if (!duckChatFeature.duckAiPromoCampaigns().isEnabled()) {
                return@launch
            }
            try {
                promoCampaignRepository.endPromo(checkExpiry = true)
            } catch (_: PromoRecordStorageException) {
                logcat(WARN) { "Duck.ai promo: couldn't end an expired promo, the promo record can't be read or written" }
            } catch (_: PromoRecordCorruptedException) {
                logcat(WARN) { "Duck.ai promo: couldn't end an expired promo, the promo record can't be decoded" }
            }
        }
    }
}
