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

package com.duckduckgo.pagetranslation.impl.messaging

import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.di.scopes.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.withContext
import javax.inject.Inject

/**
 * The C-S-S pageTranslation bundle, set up to talk to the app over its own JavaScript interface. The names are the same for every
 * user, so pages that see them can't tell users apart.
 */
interface PageTranslationScript {
    val interfaceName: String

    val callbackName: String

    /**
     * The bundle, run only in the document whose `performance.timeOrigin` is [timeOrigin] and only once per document. It only
     * accepts messages that carry [secret], and sends it with every message.
     */
    suspend fun get(timeOrigin: Double, secret: String): String
}

@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class)
class RealPageTranslationScript @Inject constructor(
    private val dispatchers: DispatcherProvider,
) : PageTranslationScript {
    override val interfaceName: String = INTERFACE_NAME

    override val callbackName: String = CALLBACK_NAME

    private val bundle: String by lazy {
        val source = javaClass.classLoader?.getResource(BUNDLE_FILE_NAME)?.readText().orEmpty()
        source
            .replace(CONTENT_SCOPE_PLACEHOLDER, CONTENT_SCOPE)
            .replace(USER_UNPROTECTED_DOMAINS_PLACEHOLDER, "[]")
    }

    override suspend fun get(timeOrigin: Double, secret: String): String = withContext(dispatchers.io()) {
        // the bundle locks its callback, and the page's interface is gone once a first copy ran
        "(function() {\n" +
            "'use strict';\n" +
            "if (performance.timeOrigin !== $timeOrigin || Object.prototype.hasOwnProperty.call(window, '$callbackName')) return;\n" +
            bundle.replace(USER_PREFERENCES_PLACEHOLDER, getUserPreferences(secret)) + "\n" +
            "})();"
    }

    private fun getUserPreferences(secret: String): String =
        """{"platform":{"name":"android"},"messageSecret":"$secret","messageCallback":"$callbackName","javascriptInterface":"$interfaceName"}"""

    private companion object {
        const val INTERFACE_NAME = "DDGPageTranslation"
        const val CALLBACK_NAME = "DDGPageTranslationCallback"
        const val BUNDLE_FILE_NAME = "pageTranslation.js"
        const val CONTENT_SCOPE_PLACEHOLDER = "\$CONTENT_SCOPE$"
        const val USER_UNPROTECTED_DOMAINS_PLACEHOLDER = "\$USER_UNPROTECTED_DOMAINS$"
        const val USER_PREFERENCES_PLACEHOLDER = "\$USER_PREFERENCES$"

        // pageTranslation runs without a config entry, because the app starts it
        const val CONTENT_SCOPE = """{"features":{},"unprotectedTemporary":[]}"""
    }
}
