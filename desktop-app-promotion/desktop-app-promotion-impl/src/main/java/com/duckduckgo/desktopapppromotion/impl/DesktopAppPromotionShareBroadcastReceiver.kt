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

package com.duckduckgo.desktopapppromotion.impl

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.duckduckgo.anvil.annotations.InjectWith
import com.duckduckgo.app.di.AppCoroutineScope
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.desktopapppromotion.api.DesktopAppPromotionInteractionHandler.Interaction
import com.duckduckgo.di.scopes.ReceiverScope
import dagger.android.AndroidInjection
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Reports a *completed* share — the user picked a target in the chooser, rather than merely opening
 * it. The chooser calls back after the promo screen may already be gone, which is why this arrives
 * as a broadcast and not as an activity result.
 */
@InjectWith(ReceiverScope::class)
class DesktopAppPromotionShareBroadcastReceiver : BroadcastReceiver() {

    @Inject
    lateinit var interactionDispatcher: DesktopAppPromotionInteractionDispatcher

    @Inject
    @AppCoroutineScope
    lateinit var appCoroutineScope: CoroutineScope

    @Inject
    lateinit var dispatchers: DispatcherProvider

    override fun onReceive(
        context: Context,
        intent: Intent,
    ) {
        AndroidInjection.inject(this, context)

        val handlerId = intent.getStringExtra(EXTRA_HANDLER_ID) ?: return
        val pendingResult = goAsync()

        appCoroutineScope.launch(dispatchers.io()) {
            interactionDispatcher.dispatch(handlerId, Interaction.SHARE_COMPLETED)
            pendingResult.finish()
        }
    }

    companion object {
        const val EXTRA_HANDLER_ID = "desktopAppPromotion.handlerId"
    }
}
