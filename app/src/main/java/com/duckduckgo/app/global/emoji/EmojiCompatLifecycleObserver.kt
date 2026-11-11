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

package com.duckduckgo.app.global.emoji

import android.content.Context
import androidx.emoji2.text.EmojiCompat
import androidx.lifecycle.LifecycleOwner
import com.duckduckgo.app.di.AppCoroutineScope
import com.duckduckgo.app.lifecycle.MainProcessLifecycleObserver
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.di.scopes.AppScope
import dev.zacsweers.metro.ContributesIntoSet
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import logcat.LogPriority.WARN
import logcat.logcat
import javax.inject.Inject

/**
 * Replaces emoji2's EmojiCompatInitializer (removed in the manifest) with the same timing: configure at process start,
 * load in the background shortly after the first resume, but with [UnstableProviderEmojiLoader].
 *
 * Failed loads are retried with backoff: the provider reports the font as unavailable while it is still downloading it
 * (common on a fresh install), and a Play Services restart fails an in-flight load instead of killing the app.
 */
@ContributesIntoSet(
    scope = AppScope::class,
    binding = binding<MainProcessLifecycleObserver>(),
)
@SingleIn(AppScope::class)
class EmojiCompatLifecycleObserver @Inject constructor(
    private val context: Context,
    @AppCoroutineScope private val appCoroutineScope: CoroutineScope,
    private val dispatchers: DispatcherProvider,
) : MainProcessLifecycleObserver {

    private val loader by lazy { UnstableProviderEmojiLoader(context) }
    private var loadStarted = false

    override fun onCreate(owner: LifecycleOwner) {
        EmojiCompat.init(UnstableProviderEmojiConfig(loader))
    }

    override fun onResume(owner: LifecycleOwner) {
        if (loadStarted) return
        loadStarted = true
        appCoroutineScope.launch(dispatchers.io()) {
            delay(LOAD_DELAY_MS)
            if (!loader.hasProvider()) return@launch
            loadWithRetry()
        }
    }

    // The loader runs synchronously on this thread, so the load state is final when load() returns
    private suspend fun loadWithRetry() {
        val emojiCompat = EmojiCompat.get()
        var retryDelayMs = INITIAL_RETRY_DELAY_MS
        repeat(MAX_ATTEMPTS) { attempt ->
            emojiCompat.load()
            if (emojiCompat.loadState == EmojiCompat.LOAD_STATE_SUCCEEDED) return
            logcat(WARN) { "EmojiCompat font load attempt ${attempt + 1} failed" }
            if (attempt < MAX_ATTEMPTS - 1) {
                delay(retryDelayMs)
                retryDelayMs *= 2
            }
        }
    }

    private class UnstableProviderEmojiConfig(loader: EmojiCompat.MetadataRepoLoader) : EmojiCompat.Config(loader) {
        init {
            setMetadataLoadStrategy(EmojiCompat.LOAD_STRATEGY_MANUAL)
        }
    }

    private companion object {
        // Same delay as EmojiCompatInitializer, to keep the font load off the first frames
        const val LOAD_DELAY_MS = 500L
        const val MAX_ATTEMPTS = 7
        const val INITIAL_RETRY_DELAY_MS = 1_000L
    }
}
