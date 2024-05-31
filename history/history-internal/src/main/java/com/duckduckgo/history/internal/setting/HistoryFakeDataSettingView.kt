/*
 * Copyright (c) 2024 DuckDuckGo
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

package com.duckduckgo.history.internal.setting

import android.content.Context
import android.util.AttributeSet
import android.view.View
import android.widget.CompoundButton
import android.widget.FrameLayout
import com.duckduckgo.anvil.annotations.InjectWith
import com.duckduckgo.app.di.AppCoroutineScope
import com.duckduckgo.common.ui.viewbinding.viewBinding
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.di.scopes.ActivityScope
import com.duckduckgo.di.scopes.ViewScope
import com.duckduckgo.feature.toggles.api.Toggle.State
import com.duckduckgo.history.api.NavigationHistory
import com.duckduckgo.history.impl.remoteconfig.HistoryRemoteFeature
import com.duckduckgo.history.internal.databinding.HistoryFakeDataViewBinding
import com.duckduckgo.history.internal.feature.HistorySettingPlugin
import com.duckduckgo.navigation.api.GlobalActivityStarter
import com.squareup.anvil.annotations.ContributesMultibinding
import dagger.android.support.AndroidSupportInjection
import java.util.UUID
import javax.inject.Inject
import kotlin.random.Random
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

@InjectWith(ViewScope::class)
class HistoryFakeDataSettingView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = 0,
) : FrameLayout(context, attrs, defStyle) {

    @Inject
    lateinit var globalActivityStarter: GlobalActivityStarter

    @Inject
    lateinit var historyInternalSettings: HistoryInternalSettings

    @Inject
    lateinit var remoteFeature: HistoryRemoteFeature

    @Inject
    lateinit var publicApiNavigationHistory: NavigationHistory

    @Inject
    lateinit var dispatcherProvider: DispatcherProvider

    @Inject
    @AppCoroutineScope
    lateinit var appCoroutineScope: CoroutineScope

    private val binding: HistoryFakeDataViewBinding by viewBinding()

    private val rootRCToggleListener = CompoundButton.OnCheckedChangeListener { _, value ->
        appCoroutineScope.launch(dispatcherProvider.io()) {
            remoteFeature.self().setEnabled(State(enable = value))
        }
    }

    private val storeHistoryRCToggleListener = CompoundButton.OnCheckedChangeListener { _, value ->
        appCoroutineScope.launch(dispatcherProvider.io()) {
            remoteFeature.storeHistory().setEnabled(State(enable = value))
        }
    }

    override fun onAttachedToWindow() {
        AndroidSupportInjection.inject(this)
        super.onAttachedToWindow()

        binding.addSitesToHistory.setOnClickListener {
            appCoroutineScope.launch(dispatcherProvider.io()) {
                publicApiNavigationHistory.saveToHistory("https://fill.dev/", "Fill.dev")
                publicApiNavigationHistory.saveToHistory("https://www.imdb.com/", "IMDb")
                publicApiNavigationHistory.saveToHistory("https://www.github.com/", "GitHub")
                publicApiNavigationHistory.saveToHistory("https://www.stackoverflow.com/", "Stack Overflow")
                publicApiNavigationHistory.saveToHistory("https://www.duckduckgo.com/", "DuckDuckGo")
                publicApiNavigationHistory.saveToHistory("https://www.google.com/", "Google")
                publicApiNavigationHistory.saveToHistory("https://www.bing.com/", "Bing")
                publicApiNavigationHistory.saveToHistory("https://www.yahoo.com/", "Yahoo")
                publicApiNavigationHistory.saveToHistory("https://www.facebook.com/", "Facebook")
                publicApiNavigationHistory.saveToHistory("https://www.twitter.com/", "Twitter")
                publicApiNavigationHistory.saveToHistory("https://www.instagram.com/", "Instagram")
                publicApiNavigationHistory.saveToHistory("https://www.linkedin.com/", "LinkedIn")
                publicApiNavigationHistory.saveToHistory("https://www.reddit.com/", "Reddit")
                publicApiNavigationHistory.saveToHistory("https://www.youtube.com/", "YouTube")
                publicApiNavigationHistory.saveToHistory("https://www.netflix.com/", "Netflix")
                publicApiNavigationHistory.saveToHistory("https://www.amazon.com/", "Amazon")
                publicApiNavigationHistory.saveToHistory("https://www.ebay.com/", "eBay")
                publicApiNavigationHistory.saveToHistory("https://www.wikipedia.org/", "Wikipedia")
                publicApiNavigationHistory.saveToHistory("https://edition.cnn.com/", "CNN")
            }
        }

        binding.add10kSitesToHistory.setOnClickListener {
            repeat(10_000) {
                appCoroutineScope.launch(dispatcherProvider.io()) {
                    val site = sitesToGenerateRandomUrls.random()
                    publicApiNavigationHistory.saveToHistory("${site.first}${UUID.randomUUID()}", site.second)
                }
            }
        }

        binding.add100SearchesToHistory.setOnClickListener {
            repeat(100) {
                appCoroutineScope.launch(dispatcherProvider.io()) {
                    publicApiNavigationHistory.saveToHistory("https://duckduckgo.com/?q=$it&atb=v345-1&ia=web", "$it at DuckDuckGo")
                }
            }
        }

        binding.addSearchesRandomATBToHistory.setOnClickListener {
            searches.forEach { keyword ->
                appCoroutineScope.launch(dispatcherProvider.io()) {
                    val nextInt = Random.nextInt(0, 1000)
                    publicApiNavigationHistory.saveToHistory("https://duckduckgo.com/?q=$keyword&atb=v$nextInt-1&ia=web", "$keyword at DuckDuckGo")
                }
            }
        }

        appCoroutineScope.launch(dispatcherProvider.io()) {
            binding.historyRC.showSwitch()
            binding.historyRC.quietlySetIsChecked(remoteFeature.self().isEnabled(), rootRCToggleListener)
        }
        appCoroutineScope.launch(dispatcherProvider.io()) {
            binding.storeHistoryRC.showSwitch()
            binding.storeHistoryRC.quietlySetIsChecked(remoteFeature.storeHistory().isEnabled(), storeHistoryRCToggleListener)
        }
    }

    companion object {
        private val sitesToGenerateRandomUrls = listOf(
            Pair("https://twitter.com/", "Twitter"),
            Pair("https://es.wikipedia.org/wiki/", "Wikipedia"),
            Pair("https://stackoverflow.com/questions/", "Stackoverflow"),
            Pair("https://www.amazon.com/", "Amazon"),
        )

        private val searches = listOf(
            "duck",
            "cats",
            "weather in my city",
            "restaurants near to me",
        )
    }
}

@ContributesMultibinding(ActivityScope::class)
class RecoverSubscriptionViewPlugin @Inject constructor() : HistorySettingPlugin {
    override fun getView(context: Context): View {
        return HistoryFakeDataSettingView(context)
    }
}
