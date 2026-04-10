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

package com.duckduckgo.duckchat.impl.clearing

import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.duckchat.impl.pixel.DuckChatPixels
import com.duckduckgo.duckchat.store.impl.DuckAiChatStore
import com.squareup.anvil.annotations.ContributesBinding
import dagger.Lazy
import dagger.SingleInstanceIn
import logcat.logcat
import javax.inject.Inject

@SingleInstanceIn(AppScope::class)
@ContributesBinding(AppScope::class, replaces = [RealDuckChatDeleter::class])
class DelegatingDuckChatDeleter @Inject constructor(
    private val nativeDeleter: NativeDuckChatDeleter,
    // Typed as RealDuckChatDeleter (not DuckChatDeleter) to prevent Anvil
    // from treating this field as a competing binding for the DuckChatDeleter interface.
    private val webViewDeleter: RealDuckChatDeleter,
    private val store: DuckAiChatStore,
    // Lazy to break the DI cycle: RealDuckChat → DuckChatDeleter → DuckChatPixels
    //   → DuckChatTermsOfServiceHandler → DuckChatInternal (RealDuckChat)
    private val pixels: Lazy<DuckChatPixels>,
) : DuckChatDeleter {

    override suspend fun deleteChat(chatId: String): Boolean {
        val deleter = if (store.hasMigrated()) nativeDeleter else webViewDeleter
        logcat { "DuckAI chat deletion: using ${if (deleter === nativeDeleter) "native store" else "WebView"} deleter" }
        pixels.get().reportNativeStorageDeletionUsed(native = deleter === nativeDeleter)
        return deleter.deleteChat(chatId)
    }
}
