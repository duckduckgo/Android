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
import javax.inject.Inject

/**
 * Replaces emoji2's EmojiCompatInitializer (removed in the manifest) with the same timing: configure at process start,
 * load in the background shortly after the first resume, but with [UnstableProviderEmojiLoader].
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

    private var loadStarted = false

    override fun onCreate(owner: LifecycleOwner) {
        EmojiCompat.init(UnstableProviderEmojiConfig(UnstableProviderEmojiLoader(context)))
    }

    override fun onResume(owner: LifecycleOwner) {
        if (loadStarted) return
        loadStarted = true
        appCoroutineScope.launch(dispatchers.io()) {
            delay(LOAD_DELAY_MS)
            EmojiCompat.get().load()
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
    }
}
