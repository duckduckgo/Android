package com.duckduckgo.newtabpage.impl.settings

import android.content.Context
import android.view.View
import app.cash.turbine.test
import com.duckduckgo.common.utils.plugins.ActivePluginPoint
import com.duckduckgo.common.utils.plugins.PluginPoint
import com.duckduckgo.newtabpage.api.NewTabPageSection.APP_TRACKING_PROTECTION
import com.duckduckgo.newtabpage.api.NewTabPageSection.FAVOURITES
import com.duckduckgo.newtabpage.api.NewTabPageSection.REMOTE_MESSAGING_FRAMEWORK
import com.duckduckgo.newtabpage.api.NewTabPageSection.SHORTCUTS
import com.duckduckgo.newtabpage.api.NewTabPageSectionPlugin
import com.duckduckgo.newtabpage.api.NewTabPageSectionSettingsPlugin
import com.duckduckgo.newtabpage.impl.FakeEnabledSectionPlugin
import com.duckduckgo.newtabpage.impl.FakeSettingStore
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class RealNewTabPageSectionSettingsProviderTest {

    private lateinit var testee: RealNewTabPageSectionSettingsProvider
    private val settingsStore = FakeSettingStore()

    @Before
    fun setup() {
        testee = RealNewTabPageSectionSettingsProvider(enabledSectionSettingsPlugins, enabledSectionPlugins, settingsStore)
    }

    @Test
    fun whenAllSectionsEnabledThenSectionsProvided() = runTest {
        testee.provideSections().test {
            expectMostRecentItem().also {
                assertTrue(it.size == 4)
                assertTrue(it[0].name == REMOTE_MESSAGING_FRAMEWORK.name)
                assertTrue(it[1].name == APP_TRACKING_PROTECTION.name)
                assertTrue(it[2].name == FAVOURITES.name)
                assertTrue(it[3].name == SHORTCUTS.name)
            }
        }
    }

    @Test
    fun whenUserDisabledAllPossibleSectionsThenSectionsProvided() = runTest {
        val settingsStore = FakeSettingStore(sections = listOf(REMOTE_MESSAGING_FRAMEWORK.name), emptyList())
        testee = RealNewTabPageSectionSettingsProvider(enabledSectionSettingsPlugins, enabledSectionPlugins, settingsStore)

        testee.provideSections().test {
            expectMostRecentItem().also {
                assertTrue(it.size == 1)
                assertTrue(it[0].name == REMOTE_MESSAGING_FRAMEWORK.name)
            }
        }
    }

    @Test
    fun whenRemoteSectionsAreDisabledThenSectionsProvided() = runTest {
        val settingsStore = FakeSettingStore(
            sections = listOf(
                REMOTE_MESSAGING_FRAMEWORK.name,
                APP_TRACKING_PROTECTION.name,
                FAVOURITES.name,
                SHORTCUTS.name,
            ),
            emptyList(),
        )

        testee = RealNewTabPageSectionSettingsProvider(enabledSectionSettingsPlugins, disabledSectionPlugins, settingsStore)

        testee.provideSections().test {
            expectMostRecentItem().also {
                assertTrue(it.isEmpty())
            }
        }
    }

    private val enabledSectionSettingsPlugins = object : PluginPoint<NewTabPageSectionSettingsPlugin> {
        override fun getPlugins(): Collection<NewTabPageSectionSettingsPlugin> {
            return listOf(
                FakeActiveSectionSettingPlugin(REMOTE_MESSAGING_FRAMEWORK.name, true),
                FakeActiveSectionSettingPlugin(APP_TRACKING_PROTECTION.name, true),
                FakeActiveSectionSettingPlugin(FAVOURITES.name, true),
                FakeActiveSectionSettingPlugin(SHORTCUTS.name, true),
            )
        }
    }

    private val enabledSectionPlugins = object : ActivePluginPoint<NewTabPageSectionPlugin> {
        override suspend fun getPlugins(): Collection<NewTabPageSectionPlugin> {
            return listOf(
                FakeEnabledSectionPlugin(REMOTE_MESSAGING_FRAMEWORK.name, true),
                FakeEnabledSectionPlugin(APP_TRACKING_PROTECTION.name, true),
                FakeEnabledSectionPlugin(FAVOURITES.name, true),
                FakeEnabledSectionPlugin(SHORTCUTS.name, true),
            )
        }
    }

    private val userDisabledSectionPlugins = object : ActivePluginPoint<NewTabPageSectionPlugin> {
        override suspend fun getPlugins(): Collection<NewTabPageSectionPlugin> {
            return listOf(
                FakeEnabledSectionPlugin(REMOTE_MESSAGING_FRAMEWORK.name, false),
                FakeEnabledSectionPlugin(APP_TRACKING_PROTECTION.name, false),
                FakeEnabledSectionPlugin(FAVOURITES.name, false),
                FakeEnabledSectionPlugin(SHORTCUTS.name, false),
            )
        }
    }

    private val disabledSectionPlugins = object : ActivePluginPoint<NewTabPageSectionPlugin> {
        override suspend fun getPlugins(): Collection<NewTabPageSectionPlugin> {
            return emptyList()
        }
    }

    private class FakeActiveSectionSettingPlugin(
        val section: String,
        val isEnabled: Boolean,
    ) : NewTabPageSectionSettingsPlugin {
        override val name: String
            get() = section

        override fun getView(context: Context): View? {
            return null
        }

        override suspend fun isActive(): Boolean {
            return isEnabled
        }
    }
}
