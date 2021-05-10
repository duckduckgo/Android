/*
 * Copyright (c) 2020 DuckDuckGo
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

package com.duckduckgo.app.email.di

import android.content.Context
import com.duckduckgo.app.browser.DuckDuckGoUrlDetector
import com.duckduckgo.app.di.AppCoroutineScope
import com.duckduckgo.app.email.AppEmailManager
import com.duckduckgo.app.email.EmailInjector
import com.duckduckgo.app.email.EmailInjectorJs
import com.duckduckgo.app.email.EmailManager
import com.duckduckgo.app.email.api.EmailService
import com.duckduckgo.app.email.db.EmailDataStore
import com.duckduckgo.app.email.db.EmailEncryptedSharedPreferences
import com.duckduckgo.app.global.DispatcherProvider
import com.duckduckgo.app.statistics.pixels.Pixel
import dagger.Module
import dagger.Provides
import kotlinx.coroutines.CoroutineScope
import javax.inject.Singleton

@Module
class EmailModule {

    @Singleton
    @Provides
    fun providesEmailManager(emailService: EmailService, emailDataStore: EmailDataStore, dispatcherProvider: DispatcherProvider, @AppCoroutineScope appCoroutineScope: CoroutineScope): EmailManager {
        return AppEmailManager(emailService, emailDataStore, dispatcherProvider, appCoroutineScope)
    }

    @Provides
    fun providesEmailInjector(emailManager: EmailManager, duckDuckGoUrlDetector: DuckDuckGoUrlDetector): EmailInjector {
        return EmailInjectorJs(emailManager, duckDuckGoUrlDetector)
    }

    @Provides
    fun providesEmailDataStore(context: Context, pixel: Pixel): EmailDataStore {
        return EmailEncryptedSharedPreferences(context, pixel)
    }
}
