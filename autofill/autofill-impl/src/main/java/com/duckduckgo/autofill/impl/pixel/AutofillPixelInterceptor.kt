/*
 * Copyright (c) 2023 DuckDuckGo
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

package com.duckduckgo.autofill.impl.pixel

import com.duckduckgo.app.global.plugins.pixel.PixelInterceptorPlugin
import com.duckduckgo.autofill.impl.pixel.AutofillPixelNames.AUTOFILL_DECLINE_PROMPT_TO_DISABLE_AUTOFILL_DISABLE
import com.duckduckgo.autofill.impl.pixel.AutofillPixelNames.AUTOFILL_DECLINE_PROMPT_TO_DISABLE_AUTOFILL_KEEP_USING
import com.duckduckgo.autofill.impl.pixel.AutofillPixelNames.AUTOFILL_DECLINE_PROMPT_TO_DISABLE_AUTOFILL_SHOWN
import com.duckduckgo.autofill.impl.pixel.AutofillPixelNames.AUTOFILL_ENABLE_AUTOFILL_TOGGLE_MANUALLY_DISABLED
import com.duckduckgo.autofill.impl.pixel.AutofillPixelNames.AUTOFILL_ENABLE_AUTOFILL_TOGGLE_MANUALLY_ENABLED
import com.duckduckgo.autofill.impl.pixel.AutofillPixelNames.AUTOFILL_PASSWORD_GENERATION_ACCEPTED
import com.duckduckgo.autofill.impl.pixel.AutofillPixelNames.AUTOFILL_PASSWORD_GENERATION_PROMPT_DISMISSED
import com.duckduckgo.autofill.impl.pixel.AutofillPixelNames.AUTOFILL_PASSWORD_GENERATION_PROMPT_SHOWN
import com.duckduckgo.autofill.impl.pixel.AutofillPixelNames.AUTOFILL_SAVE_LOGIN_PROMPT_DISMISSED
import com.duckduckgo.autofill.impl.pixel.AutofillPixelNames.AUTOFILL_SAVE_LOGIN_PROMPT_SAVED
import com.duckduckgo.autofill.impl.pixel.AutofillPixelNames.AUTOFILL_SAVE_LOGIN_PROMPT_SHOWN
import com.duckduckgo.autofill.impl.pixel.AutofillPixelNames.AUTOFILL_SAVE_PASSWORD_PROMPT_DISMISSED
import com.duckduckgo.autofill.impl.pixel.AutofillPixelNames.AUTOFILL_SAVE_PASSWORD_PROMPT_SAVED
import com.duckduckgo.autofill.impl.pixel.AutofillPixelNames.AUTOFILL_SAVE_PASSWORD_PROMPT_SHOWN
import com.duckduckgo.autofill.impl.pixel.AutofillPixelNames.AUTOFILL_SELECT_LOGIN_AUTOPROMPT_DISMISSED
import com.duckduckgo.autofill.impl.pixel.AutofillPixelNames.AUTOFILL_SELECT_LOGIN_AUTOPROMPT_SELECTED
import com.duckduckgo.autofill.impl.pixel.AutofillPixelNames.AUTOFILL_SELECT_LOGIN_AUTOPROMPT_SHOWN
import com.duckduckgo.autofill.impl.pixel.AutofillPixelNames.AUTOFILL_SELECT_LOGIN_PROMPT_DISMISSED
import com.duckduckgo.autofill.impl.pixel.AutofillPixelNames.AUTOFILL_SELECT_LOGIN_PROMPT_SELECTED
import com.duckduckgo.autofill.impl.pixel.AutofillPixelNames.AUTOFILL_SELECT_LOGIN_PROMPT_SHOWN
import com.duckduckgo.autofill.impl.pixel.AutofillPixelNames.MENU_ACTION_AUTOFILL_PRESSED
import com.duckduckgo.autofill.impl.pixel.AutofillPixelNames.SETTINGS_AUTOFILL_MANAGEMENT_OPENED
import com.duckduckgo.autofill.impl.pixel.AutofillPixelParameters.AUTOFILL_DEFAULT_STATE
import com.duckduckgo.autofill.store.AutofillPrefsStore
import com.duckduckgo.di.scopes.AppScope
import com.squareup.anvil.annotations.ContributesMultibinding
import javax.inject.Inject
import okhttp3.Interceptor
import okhttp3.Interceptor.Chain
import okhttp3.Response

@ContributesMultibinding(
    scope = AppScope::class,
    boundType = PixelInterceptorPlugin::class,
)
class AutofillPixelInterceptor @Inject constructor(
    private val autofillStore: AutofillPrefsStore,
) : PixelInterceptorPlugin, Interceptor {

    private fun isInPixelsList(pixel: String): Boolean {
        return pixels.firstOrNull { pixel.startsWith(it.pixelName) } != null
    }

    override fun intercept(chain: Chain): Response {
        val request = chain.request().newBuilder()
        val pixel = chain.request().url.pathSegments.last().removeSuffixes()

        val url = if (isInPixelsList(pixel)) {
            val defaultState = autofillStore.wasDefaultStateEnabled()
            chain.request().url.newBuilder().addQueryParameter(AUTOFILL_DEFAULT_STATE, defaultState.asDefaultStateParam()).build()
        } else {
            chain.request().url
        }

        return chain.proceed(request.url(url).build())
    }

    override fun getInterceptor(): Interceptor = this

    private fun Boolean.asDefaultStateParam(): String {
        return if (this) "on" else "off"
    }

    private fun String.removeSuffixes(): String {
        return this
            .removeSuffix("_android_phone")
            .removeSuffix("_android_tablet")
    }

    companion object {

        val pixels = listOf(
            AUTOFILL_SAVE_LOGIN_PROMPT_SHOWN,
            AUTOFILL_SAVE_LOGIN_PROMPT_SAVED,
            AUTOFILL_SAVE_LOGIN_PROMPT_DISMISSED,

            AUTOFILL_SAVE_PASSWORD_PROMPT_SHOWN,
            AUTOFILL_SAVE_PASSWORD_PROMPT_SAVED,
            AUTOFILL_SAVE_PASSWORD_PROMPT_DISMISSED,

            AUTOFILL_SELECT_LOGIN_PROMPT_SHOWN,
            AUTOFILL_SELECT_LOGIN_PROMPT_SELECTED,
            AUTOFILL_SELECT_LOGIN_PROMPT_DISMISSED,

            AUTOFILL_SELECT_LOGIN_AUTOPROMPT_SHOWN,
            AUTOFILL_SELECT_LOGIN_AUTOPROMPT_SELECTED,
            AUTOFILL_SELECT_LOGIN_AUTOPROMPT_DISMISSED,

            AUTOFILL_DECLINE_PROMPT_TO_DISABLE_AUTOFILL_SHOWN,
            AUTOFILL_DECLINE_PROMPT_TO_DISABLE_AUTOFILL_KEEP_USING,
            AUTOFILL_DECLINE_PROMPT_TO_DISABLE_AUTOFILL_DISABLE,

            AUTOFILL_ENABLE_AUTOFILL_TOGGLE_MANUALLY_ENABLED,
            AUTOFILL_ENABLE_AUTOFILL_TOGGLE_MANUALLY_DISABLED,

            AUTOFILL_PASSWORD_GENERATION_PROMPT_SHOWN,
            AUTOFILL_PASSWORD_GENERATION_ACCEPTED,
            AUTOFILL_PASSWORD_GENERATION_PROMPT_DISMISSED,

            MENU_ACTION_AUTOFILL_PRESSED,
            SETTINGS_AUTOFILL_MANAGEMENT_OPENED,
        )
    }
}
