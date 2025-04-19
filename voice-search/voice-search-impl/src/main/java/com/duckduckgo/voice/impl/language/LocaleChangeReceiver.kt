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

package com.duckduckgo.voice.impl.language

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.duckduckgo.anvil.annotations.InjectWith
import com.duckduckgo.di.scopes.ReceiverScope
import com.duckduckgo.voice.impl.VoiceSearchAvailabilityConfigProvider
import dagger.android.AndroidInjection
import javax.inject.Inject

@InjectWith(ReceiverScope::class)
class LocaleChangeReceiver : BroadcastReceiver() {

    @Inject lateinit var languageSupportChecker: LanguageSupportChecker

    @Inject lateinit var configProvider: VoiceSearchAvailabilityConfigProvider

    override fun onReceive(context: Context, intent: Intent) {
        AndroidInjection.inject(this, context)

        if (intent.action == Intent.ACTION_LOCALE_CHANGED) {
            languageSupportChecker.checkLanguageSupport(configProvider.get().languageTag)
        }
    }
}
