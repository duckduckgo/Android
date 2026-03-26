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

package com.duckduckgo.widget

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.Intent.ACTION_LOCALE_CHANGED
import com.duckduckgo.anvil.annotations.InjectWith
import com.duckduckgo.di.scopes.ReceiverScope
import dagger.android.AndroidInjection
import org.jetbrains.annotations.VisibleForTesting
import javax.inject.Inject

@InjectWith(ReceiverScope::class)
class VoiceSearchWidgetUpdaterReceiver : BroadcastReceiver() {

    @Inject
    lateinit var widgetUpdater: WidgetUpdater
    override fun onReceive(
        context: Context,
        intent: Intent,
    ) {
        AndroidInjection.inject(this, context)
        processIntent(context, intent)
    }

    @VisibleForTesting
    fun processIntent(context: Context, intent: Intent) {
        if (intent.action == ACTION_LOCALE_CHANGED) {
            widgetUpdater.updateWidgets(context)
        }
    }
}
