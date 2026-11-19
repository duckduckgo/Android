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

package com.duckduckgo.pagetranslation.impl

import android.content.Context
import com.duckduckgo.app.di.AppCoroutineScope
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.translatekit.TranslateKit
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import logcat.LogPriority.ERROR
import logcat.asLog
import logcat.logcat
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject

interface TranslateKitLibrary {
    fun init()
    fun isInitialized(): Boolean
}

@ContributesBinding(AppScope::class)
class RealTranslateKitLibrary @Inject constructor(
    private val context: Context,
) : TranslateKitLibrary {
    override fun init() = TranslateKit.init(context)
    override fun isInitialized(): Boolean = TranslateKit.isInitialized()
}

@SingleIn(AppScope::class)
class TranslateKitLoader @Inject constructor(
    private val library: TranslateKitLibrary,
    private val feature: PageTranslationFeature,
    @AppCoroutineScope private val appScope: CoroutineScope,
    private val dispatchers: DispatcherProvider,
) {
    private val loadStarted = AtomicBoolean(false)

    fun loadIfNeeded() {
        if (loadStarted.get()) return
        appScope.launch(dispatchers.io()) {
            if (!feature.translation().isEnabled() || !loadStarted.compareAndSet(false, true)) return@launch
            try {
                library.init()
            } catch (e: Exception) {
                logcat(ERROR) { "TranslateKit failed to initialize: ${e.asLog()}" }
            }
        }
    }

    fun isAvailable(): Boolean = feature.translation().isEnabled() && library.isInitialized()
}
