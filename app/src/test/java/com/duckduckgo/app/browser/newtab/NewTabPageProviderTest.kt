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
import com.duckduckgo.appbuildconfig.api.AppBuildConfig
import com.duckduckgo.appbuildconfig.api.BuildFlavor
import com.duckduckgo.common.utils.plugins.ActivePluginPoint
import com.duckduckgo.newtabpage.api.NewTabPagePlugin
import com.duckduckgo.newtabpage.api.NewTabPageVersion
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.Mockito.mock
import org.mockito.kotlin.whenever

class NewTabPageProviderTest {

    private lateinit var testee: NewTabPageProvider
    private val appBuildConfig: AppBuildConfig = mock()

    @Before
    fun setup() {
        whenever(appBuildConfig.flavor).thenReturn(BuildFlavor.PLAY)
    }

    @Test
    fun whenInternalBuildAndNoPluginsEnabledThenNewViewProvided() = runTest {
        testee = RealNewTabPageProvider(noPluginsEnabled, appBuildConfig)
        whenever(appBuildConfig.flavor).thenReturn(BuildFlavor.INTERNAL)

        testee.provideNewTabPageVersion().test {
            expectMostRecentItem().also {
                assertTrue(it.name == NewTabPageVersion.NEW.name)
            }
        }
    }

    @Test
    fun whenInternalBuildAndAllPluginsEnabledThenNewViewProvided() = runTest {
        testee = RealNewTabPageProvider(allPluginsEnabled, appBuildConfig)
        whenever(appBuildConfig.flavor).thenReturn(BuildFlavor.INTERNAL)

        testee.provideNewTabPageVersion().test {
            expectMostRecentItem().also {
                assertTrue(it.name == NewTabPageVersion.NEW.name)
            }
        }
    }

    @Test
    fun whenLegacyPluginEnabledThenLegacyViewProvided() = runTest {
        testee = RealNewTabPageProvider(legacyPluginEnabled, appBuildConfig)

        testee.provideNewTabPageVersion().test {
            expectMostRecentItem().also {
                assertTrue(it.name == NewTabPageVersion.LEGACY.name)
            }
        }
    }

    @Test
    fun whenNewPluginEnabledThenNewViewProvided() = runTest {
        testee = RealNewTabPageProvider(newPluginEnabled, appBuildConfig)

        testee.provideNewTabPageVersion().test {
            expectMostRecentItem().also {
                assertTrue(it.name == NewTabPageVersion.NEW.name)
            }
        }
    }

    @Test
    fun whenAllPluginsEnabledThenLegacyViewProvided() = runTest {
        testee = RealNewTabPageProvider(allPluginsEnabled, appBuildConfig)

        testee.provideNewTabPageVersion().test {
            expectMostRecentItem().also {
                assertTrue(it.name == NewTabPageVersion.LEGACY.name)
            }
        }
    }

    @Test
    fun whenNoPluginsEnabledThenLegacyViewProvided() = runTest {
        testee = RealNewTabPageProvider(noPluginsEnabled, appBuildConfig)

        testee.provideNewTabPageVersion().test {
            expectMostRecentItem().also {
                assertTrue(it.name == NewTabPageVersion.LEGACY.name)
            }
        }
    }

    private val legacyPluginEnabled = object : ActivePluginPoint<NewTabPagePlugin> {
        override suspend fun getPlugins(): Collection<NewTabPagePlugin> {
            return listOf(
                LegacyNewTabPlugin(),
            )
        }
    }

    private val newPluginEnabled = object : ActivePluginPoint<NewTabPagePlugin> {
        override suspend fun getPlugins(): Collection<NewTabPagePlugin> {
            return listOf(
                NewNewTabPlugin(),
            )
        }
    }

    private val allPluginsEnabled = object : ActivePluginPoint<NewTabPagePlugin> {
        override suspend fun getPlugins(): Collection<NewTabPagePlugin> {
            return listOf(
                LegacyNewTabPlugin(),
                NewNewTabPlugin(),
            )
        }
    }

    private val noPluginsEnabled = object : ActivePluginPoint<NewTabPagePlugin> {
        override suspend fun getPlugins(): Collection<NewTabPagePlugin> {
            return emptyList()
        }
    }

    class LegacyNewTabPlugin : NewTabPagePlugin {
        override val name: String
            get() = NewTabPageVersion.LEGACY.name

        override fun getView(context: Context): View {
            return View(context)
        }
    }

    class NewNewTabPlugin() : NewTabPagePlugin {
        override val name: String
            get() = NewTabPageVersion.NEW.name

        override fun getView(context: Context): View {
            return View(context)
        }
    }
}
