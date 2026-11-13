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

package com.duckduckgo.duckchat.impl.widget

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import androidx.lifecycle.LifecycleOwner
import com.duckduckgo.app.di.AppCoroutineScope
import com.duckduckgo.app.lifecycle.MainProcessLifecycleObserver
import com.duckduckgo.app.statistics.pixels.Pixel
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.duckchat.impl.feature.DuckChatFeature
import com.duckduckgo.duckchat.impl.pixel.DuckChatPixelName
import com.duckduckgo.duckchat.impl.repository.DuckChatFeatureRepository
import com.duckduckgo.privacy.config.api.PrivacyConfigCallbackPlugin
import dev.zacsweers.metro.ContributesIntoSet
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject

/**
 * Makes the widget receiver, declared disabled in the manifest, visible in the launcher's widget picker only while
 * [DuckChatFeature.duckAiWidget], [DuckChatFeature.self] and the user's Duck.ai setting are all on. Disabling the
 * receiver removes the widget from the picker and any placed instance with it.
 */
@SingleIn(AppScope::class)
@ContributesIntoSet(
    scope = AppScope::class,
    binding = binding<MainProcessLifecycleObserver>(),
)
@ContributesIntoSet(
    scope = AppScope::class,
    binding = binding<PrivacyConfigCallbackPlugin>(),
)
class DuckAiWidgetAvailability @Inject constructor(
    private val context: Context,
    private val duckChatFeature: DuckChatFeature,
    private val duckChatFeatureRepository: DuckChatFeatureRepository,
    private val dispatcherProvider: DispatcherProvider,
    private val pixel: Pixel,
    @AppCoroutineScope private val appCoroutineScope: CoroutineScope,
) : MainProcessLifecycleObserver, PrivacyConfigCallbackPlugin {

    private val mutex = Mutex()

    override fun onCreate(owner: LifecycleOwner) {
        launchSync()
        observeUserSetting()
    }

    override fun onPrivacyConfigDownloaded() {
        launchSync()
    }

    private fun launchSync() {
        appCoroutineScope.launch(dispatcherProvider.io()) { sync(duckChatFeatureRepository.isDuckChatUserEnabled()) }
    }

    // The first emission is the pre-load default, the sync launched alongside already covers the initial state.
    private fun observeUserSetting() {
        appCoroutineScope.launch(dispatcherProvider.io()) {
            duckChatFeatureRepository.observeDuckChatUserEnabled()
                .distinctUntilChanged()
                .drop(1)
                .collect { sync(userEnabled = it) }
        }
    }

    private suspend fun sync(userEnabled: Boolean) = mutex.withLock {
        val packageManager = context.packageManager
        val receiver = ComponentName(context, DuckAiWidgetProvider::class.java)
        val enable = duckChatFeature.duckAiWidget().isEnabled() &&
            duckChatFeature.self().isEnabled() &&
            userEnabled
        // DEFAULT resolves to the manifest value, which is disabled.
        val enabled = packageManager.getComponentEnabledSetting(receiver) == PackageManager.COMPONENT_ENABLED_STATE_ENABLED
        if (enable != enabled) {
            val removesPlacedWidgets = !enable && hasPlacedWidgets(receiver)
            val state = if (enable) PackageManager.COMPONENT_ENABLED_STATE_ENABLED else PackageManager.COMPONENT_ENABLED_STATE_DISABLED
            packageManager.setComponentEnabledSetting(receiver, state, PackageManager.DONT_KILL_APP)
            if (removesPlacedWidgets) {
                // A disabled receiver no longer gets the removal broadcast, so the removal is reported here.
                pixel.fire(DuckChatPixelName.DUCK_AI_WIDGET_DELETED)
            }
        }
    }

    private fun hasPlacedWidgets(receiver: ComponentName): Boolean =
        context.getSystemService(AppWidgetManager::class.java)?.getAppWidgetIds(receiver)?.isNotEmpty() == true
}
