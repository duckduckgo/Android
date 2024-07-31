package com.duckduckgo.app.browser.newtab

import android.content.Context
import android.view.View
import app.cash.turbine.test
import com.duckduckgo.appbuildconfig.api.AppBuildConfig
import com.duckduckgo.appbuildconfig.api.BuildFlavor
import com.duckduckgo.common.utils.plugins.ActivePluginPoint
import com.duckduckgo.newtabpage.api.FocusedViewPlugin
import com.duckduckgo.newtabpage.api.FocusedViewVersion
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.Mockito.mock
import org.mockito.kotlin.whenever

class RealFocusedViewProviderTest {

    private lateinit var testee: FocusedViewProvider
    private val appBuildConfig: AppBuildConfig = mock()

    @Before
    fun setup() {
        whenever(appBuildConfig.flavor).thenReturn(BuildFlavor.PLAY)
    }

    @Test
    fun whenInternalBuildAndNoPluginsEnabledThenNewViewProvided() = runTest {
        testee = RealFocusedViewProvider(noPluginsEnabled, appBuildConfig)
        whenever(appBuildConfig.flavor).thenReturn(BuildFlavor.INTERNAL)

        testee.provideFocusedViewVersion().test {
            expectMostRecentItem().also {
                assertTrue(it.name == FocusedViewVersion.NEW.name)
            }
        }
    }

    @Test
    fun whenInternalBuildAndAllPluginsEnabledThenNewViewProvided() = runTest {
        testee = RealFocusedViewProvider(allPluginsEnabled, appBuildConfig)
        whenever(appBuildConfig.flavor).thenReturn(BuildFlavor.INTERNAL)

        testee.provideFocusedViewVersion().test {
            expectMostRecentItem().also {
                assertTrue(it.name == FocusedViewVersion.NEW.name)
            }
        }
    }

    @Test
    fun whenLegacyPluginEnabledThenLegacyViewProvided() = runTest {
        testee = RealFocusedViewProvider(legacyPluginEnabled, appBuildConfig)

        testee.provideFocusedViewVersion().test {
            expectMostRecentItem().also {
                assertTrue(it.name == FocusedViewVersion.LEGACY.name)
            }
        }
    }

    @Test
    fun whenNewPluginEnabledThenNewViewProvided() = runTest {
        testee = RealFocusedViewProvider(newPluginEnabled, appBuildConfig)

        testee.provideFocusedViewVersion().test {
            expectMostRecentItem().also {
                assertTrue(it.name == FocusedViewVersion.NEW.name)
            }
        }
    }

    @Test
    fun whenAllPluginsEnabledThenLegacyViewProvided() = runTest {
        testee = RealFocusedViewProvider(allPluginsEnabled, appBuildConfig)

        testee.provideFocusedViewVersion().test {
            expectMostRecentItem().also {
                assertTrue(it.name == FocusedViewVersion.LEGACY.name)
            }
        }
    }

    @Test
    fun whenNoPluginsEnabledThenLegacyViewProvided() = runTest {
        testee = RealFocusedViewProvider(noPluginsEnabled, appBuildConfig)

        testee.provideFocusedViewVersion().test {
            expectMostRecentItem().also {
                assertTrue(it.name == FocusedViewVersion.LEGACY.name)
            }
        }
    }

    private val noPluginsEnabled = object : ActivePluginPoint<FocusedViewPlugin> {
        override suspend fun getPlugins(): Collection<FocusedViewPlugin> {
            return emptyList()
        }
    }

    private val allPluginsEnabled = object : ActivePluginPoint<FocusedViewPlugin> {
        override suspend fun getPlugins(): Collection<FocusedViewPlugin> {
            return listOf(
                LegacyNewTabPlugin(),
                NewNewTabPlugin(),
            )
        }
    }

    private val legacyPluginEnabled = object : ActivePluginPoint<FocusedViewPlugin> {
        override suspend fun getPlugins(): Collection<FocusedViewPlugin> {
            return listOf(
                LegacyNewTabPlugin(),
            )
        }
    }

    private val newPluginEnabled = object : ActivePluginPoint<FocusedViewPlugin> {
        override suspend fun getPlugins(): Collection<FocusedViewPlugin> {
            return listOf(
                NewNewTabPlugin(),
            )
        }
    }

    class LegacyNewTabPlugin : FocusedViewPlugin {
        override val name: String
            get() = FocusedViewVersion.LEGACY.name

        override fun getView(context: Context): View {
            return View(context)
        }
    }

    class NewNewTabPlugin() : FocusedViewPlugin {
        override val name: String
            get() = FocusedViewVersion.NEW.name

        override fun getView(context: Context): View {
            return View(context)
        }
    }
}
