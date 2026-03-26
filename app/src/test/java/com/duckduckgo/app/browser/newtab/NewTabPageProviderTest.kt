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

package com.duckduckgo.app.browser.newtab

import android.content.Context
import android.view.View
import app.cash.turbine.test
import com.duckduckgo.common.utils.plugins.ActivePluginPoint
import com.duckduckgo.newtabpage.api.NewTabPagePlugin
import com.duckduckgo.newtabpage.api.NewTabPageProvider
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertTrue
import org.junit.Test

class NewTabPageProviderTest {

    private lateinit var testee: NewTabPageProvider

    @Test
    fun whenNewPluginEnabledThenNewViewProvided() = runTest {
        testee = RealNewTabPageProvider(ntpPluginEnabled)

        testee.provideNewTabPageVersion().test {
            expectMostRecentItem().also {
                assertTrue(it is NewTabPlugin)
            }
        }
    }

    @Test
    fun whenConfigurablePluginEnabledThenConfigurablePluginViewProvided() = runTest {
        testee = RealNewTabPageProvider(configurablePluginEnabled)

        testee.provideNewTabPageVersion().test {
            expectMostRecentItem().also {
                assertTrue(it is ConfigurableNewTabPlugin)
            }
        }
    }

    @Test
    fun whenNTPFirstPluginFirstEnabledThenNTPViewProvided() = runTest {
        testee = RealNewTabPageProvider(ntpFirstPluginsEnabled)

        testee.provideNewTabPageVersion().test {
            expectMostRecentItem().also {
                assertTrue(it is ConfigurableNewTabPlugin)
            }
        }
    }

    @Test
    fun whenAllPluginsEnabledThenFirstViewProvided() = runTest {
        testee = RealNewTabPageProvider(allPluginsEnabled)

        testee.provideNewTabPageVersion().test {
            expectMostRecentItem().also {
                assertTrue(it is NewTabPlugin)
            }
        }
    }

    @Test
    fun whenNoPluginsEnabledThenNewViewProvided() = runTest {
        testee = RealNewTabPageProvider(noPluginsEnabled)

        testee.provideNewTabPageVersion().test {
            expectMostRecentItem().also {
                assertTrue(it is NewTabPage)
            }
        }
    }

    private val ntpPluginEnabled = object : ActivePluginPoint<NewTabPagePlugin> {
        override suspend fun getPlugins(): Collection<NewTabPagePlugin> {
            return listOf(
                NewTabPlugin(),
            )
        }
    }

    private val configurablePluginEnabled = object : ActivePluginPoint<NewTabPagePlugin> {
        override suspend fun getPlugins(): Collection<NewTabPagePlugin> {
            return listOf(
                ConfigurableNewTabPlugin(),
            )
        }
    }

    private val allPluginsEnabled = object : ActivePluginPoint<NewTabPagePlugin> {
        override suspend fun getPlugins(): Collection<NewTabPagePlugin> {
            return listOf(
                NewTabPlugin(),
                ConfigurableNewTabPlugin(),
            )
        }
    }

    private val ntpFirstPluginsEnabled = object : ActivePluginPoint<NewTabPagePlugin> {
        override suspend fun getPlugins(): Collection<NewTabPagePlugin> {
            return listOf(
                ConfigurableNewTabPlugin(),
                NewTabPlugin(),
            )
        }
    }

    private val noPluginsEnabled = object : ActivePluginPoint<NewTabPagePlugin> {
        override suspend fun getPlugins(): Collection<NewTabPagePlugin> {
            return emptyList()
        }
    }

    class NewTabPlugin : NewTabPagePlugin {
        override fun getView(
            context: Context,
            showLogo: Boolean,
            onHasContent: ((Boolean) -> Unit)?,
        ): View {
            return View(context)
        }
    }

    class ConfigurableNewTabPlugin : NewTabPagePlugin {
        override fun getView(
            context: Context,
            showLogo: Boolean,
            onHasContent: ((Boolean) -> Unit)?,
        ): View {
            return View(context)
        }
    }
}
