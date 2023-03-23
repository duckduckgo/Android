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
import com.duckduckgo.app.autofill.JavascriptInjector
import com.duckduckgo.app.browser.DuckDuckGoUrlDetector
import com.duckduckgo.app.email.EmailInjector
import com.duckduckgo.app.email.EmailInjectorJs
import com.duckduckgo.app.email.EmailManager
import com.duckduckgo.app.email.db.EmailDataStore
import com.duckduckgo.app.email.db.EmailEncryptedSharedPreferences
import com.duckduckgo.app.global.DispatcherProvider
import com.duckduckgo.app.statistics.pixels.Pixel
import com.duckduckgo.autofill.api.Autofill
import com.duckduckgo.autofill.api.AutofillFeature
import dagger.Module
import dagger.Provides

@Module
class EmailModule {

    @Provides
    fun providesEmailInjector(
        emailManager: EmailManager,
        duckDuckGoUrlDetector: DuckDuckGoUrlDetector,
        dispatcherProvider: DispatcherProvider,
        autofillFeature: AutofillFeature,
        javascriptInjector: JavascriptInjector,
        autofill: Autofill,
    ): EmailInjector {
        return EmailInjectorJs(emailManager, duckDuckGoUrlDetector, dispatcherProvider, autofillFeature, javascriptInjector, autofill)
    }

    @Provides
    fun providesEmailDataStore(
        context: Context,
        pixel: Pixel,
    ): EmailDataStore {
        return EmailEncryptedSharedPreferences(context, pixel)
    }
}
