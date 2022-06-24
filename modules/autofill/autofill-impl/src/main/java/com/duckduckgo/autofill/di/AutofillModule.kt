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

package com.duckduckgo.autofill.di

import android.content.Context
import com.duckduckgo.app.autofill.FileBasedJavaScriptInjector
import com.duckduckgo.app.autofill.JavascriptInjector
import com.duckduckgo.app.di.AppCoroutineScope
import com.duckduckgo.autofill.AutofillJavascriptInterface
import com.duckduckgo.autofill.jsbridge.AutofillMessagePoster
import com.duckduckgo.autofill.BrowserAutofill
import com.duckduckgo.autofill.CredentialAutofillDialogFactory
import com.duckduckgo.autofill.InlineBrowserAutofill
import com.duckduckgo.autofill.jsbridge.request.AutofillRequestParser
import com.duckduckgo.autofill.jsbridge.response.AutofillResponseWriter
import com.duckduckgo.autofill.store.AutofillStore
import com.duckduckgo.autofill.ui.CredentialAutofillDialogAndroidFactory
import com.duckduckgo.di.scopes.AppScope
import com.squareup.anvil.annotations.ContributesTo
import com.squareup.moshi.Moshi
import dagger.Module
import dagger.Provides
import kotlinx.coroutines.CoroutineScope

@Module
@ContributesTo(AppScope::class)
class AutofillModule {

    @Provides
    fun browserAutofill(
        javascriptInterface: AutofillJavascriptInterface,
        javascriptInjector: JavascriptInjector,
        @AppCoroutineScope coroutineScope: CoroutineScope
    ): BrowserAutofill {
        return InlineBrowserAutofill(javascriptInterface, javascriptInjector, coroutineScope)
    }

    @Provides
    fun providesAutofillMessagePoster(): AutofillMessagePoster {
        return AutofillMessagePoster()
    }

    @Provides
    fun providesAutofillResponseWriter(moshi: Moshi): AutofillResponseWriter {
        return AutofillResponseWriter(moshi)
    }

    @Provides
    fun providesAutofillInterface(
        requestParser: AutofillRequestParser,
        autofillStore: AutofillStore,
        @AppCoroutineScope coroutineScope: CoroutineScope,
        autofillMessagePoster: AutofillMessagePoster,
        autofillResponseWriter: AutofillResponseWriter,
        context: Context
    ): AutofillJavascriptInterface {
        return AutofillJavascriptInterface(
            requestParser = requestParser,
            autofillStore = autofillStore,
            autofillMessagePoster = autofillMessagePoster,
            autofillResponseWriter = autofillResponseWriter,
            coroutineScope = coroutineScope
        )
    }

    @Provides
    fun providesFactory(): CredentialAutofillDialogFactory {
        return CredentialAutofillDialogAndroidFactory()
    }

    @Provides
    fun providesAutofillInjector(): JavascriptInjector {
        return FileBasedJavaScriptInjector()
    }
}
