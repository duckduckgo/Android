/*
 * Copyright (c) 2025 DuckDuckGo
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

package com.duckduckgo.settings.impl

import android.annotation.SuppressLint
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.cash.turbine.test
import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.common.utils.plugins.PluginPoint
import com.duckduckgo.contentscopescripts.api.ContentScopeScriptsSubscriptionEventPlugin
import com.duckduckgo.feature.toggles.api.FakeFeatureToggleFactory
import com.duckduckgo.feature.toggles.api.Toggle.State
import com.duckduckgo.js.messaging.api.SubscriptionEventData
import com.duckduckgo.settings.api.SettingsPageFeature
import com.duckduckgo.settings.impl.SettingsWebViewViewModel.Command
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@SuppressLint("DenyListedApi")
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
class SettingsWebViewViewModelTest {

    @get:Rule
    val coroutineTestRule: CoroutineTestRule = CoroutineTestRule()

    private lateinit var viewModel: SettingsWebViewViewModel
    private lateinit var fakeContentScopeScriptsSubscriptionEventPluginPoint: FakeContentScopeScriptsSubscriptionEventPluginPoint
    private var fakeSettingsPageFeature = FakeFeatureToggleFactory.create(SettingsPageFeature::class.java)

    @Before
    fun setup() {
        fakeContentScopeScriptsSubscriptionEventPluginPoint = FakeContentScopeScriptsSubscriptionEventPluginPoint()

        viewModel = SettingsWebViewViewModel(
            contentScopeScriptsSubscriptionEventPluginPoint = fakeContentScopeScriptsSubscriptionEventPluginPoint,
            settingsPageFeature = fakeSettingsPageFeature,
        )
    }

    @Test
    fun whenOnStartWithUrlThenLoadUrlCommandEmitted() = runTest {
        val testUrl = "https://example.com/settings"
        viewModel.commands.test {
            viewModel.onStart(testUrl)

            val command = awaitItem()
            assertTrue(command is Command.LoadUrl)
            assertEquals(testUrl, (command as Command.LoadUrl).url)
        }
    }

    @Test
    fun whenOnStartWithNullUrlThenExitCommandEmitted() = runTest {
        viewModel.commands.test {
            viewModel.onStart(null)

            val command = awaitItem()
            assertTrue(command is Command.Exit)
        }
    }

    @Test
    fun whenOnViewResumedWithNoPluginsThenNoSubscriptionEventsSent() = runTest {
        fakeSettingsPageFeature.serpSettingsSync().setRawStoredState(State(enable = true))

        viewModel.onResume()

        viewModel.subscriptionEventDataFlow.test {
            expectNoEvents()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun whenOnViewResumedWithPluginsThenSubscriptionEventsSent() = runTest {
        fakeSettingsPageFeature.serpSettingsSync().setRawStoredState(State(enable = true))
        val events = mutableListOf<SubscriptionEventData>().apply {
            add(
                SubscriptionEventData(
                    featureName = "event1",
                    subscriptionName = "subscription1",
                    params = JSONObject().put("param1", "value1"),
                ),
            )
            add(
                SubscriptionEventData(
                    featureName = "event2",
                    subscriptionName = "subscription2",
                    params = JSONObject().put("param2", "value2"),
                ),
            )
        }

        fakeContentScopeScriptsSubscriptionEventPluginPoint.addPlugins(
            events.map { FakeContentScopeScriptsSubscriptionEventPlugin(it) },
        )

        viewModel.onResume()

        viewModel.subscriptionEventDataFlow.test {
            for (expectedEvent in events) {
                val emittedEvent = awaitItem()
                assertEquals(expectedEvent.featureName, emittedEvent.featureName)
                assertEquals(expectedEvent.subscriptionName, emittedEvent.subscriptionName)
                assertEquals(expectedEvent.params.toString(), emittedEvent.params.toString())
            }
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun whenOnViewResumedWithPluginsAndSerpSettingsFeatureFlagOffThenNoEventsSent() = runTest {
        fakeSettingsPageFeature.serpSettingsSync().setRawStoredState(State(enable = false))
        val events = mutableListOf<SubscriptionEventData>().apply {
            add(
                SubscriptionEventData(
                    featureName = "event1",
                    subscriptionName = "subscription1",
                    params = JSONObject().put("param1", "value1"),
                ),
            )
            add(
                SubscriptionEventData(
                    featureName = "event2",
                    subscriptionName = "subscription2",
                    params = JSONObject().put("param2", "value2"),
                ),
            )
        }

        fakeContentScopeScriptsSubscriptionEventPluginPoint.addPlugins(
            events.map { FakeContentScopeScriptsSubscriptionEventPlugin(it) },
        )

        viewModel.onResume()

        viewModel.subscriptionEventDataFlow.test {
            expectNoEvents()
            cancelAndIgnoreRemainingEvents()
        }
    }
}

class FakeContentScopeScriptsSubscriptionEventPlugin(
    private val eventData: SubscriptionEventData,
) : ContentScopeScriptsSubscriptionEventPlugin {
    override fun getSubscriptionEventData(): SubscriptionEventData = eventData
}

class FakeContentScopeScriptsSubscriptionEventPluginPoint : PluginPoint<ContentScopeScriptsSubscriptionEventPlugin> {

    private val plugins: MutableList<ContentScopeScriptsSubscriptionEventPlugin> = mutableListOf()

    fun addPlugins(plugins: List<ContentScopeScriptsSubscriptionEventPlugin>) {
        this.plugins.addAll(plugins)
    }

    override fun getPlugins(): Collection<ContentScopeScriptsSubscriptionEventPlugin> = plugins
}
