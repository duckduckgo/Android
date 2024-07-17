package com.duckduckgo.newtabpage.impl.shortcuts

import androidx.lifecycle.LifecycleOwner
import app.cash.turbine.test
import com.duckduckgo.common.test.CoroutineTestRule
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.Mockito.mock
import org.mockito.kotlin.whenever

class ShortcutsNewTabSettingsViewModelTest {

    @get:Rule
    var coroutinesTestRule = CoroutineTestRule()

    private lateinit var testee: ShortcutsNewTabSettingsViewModel
    private val setting: NewTabShortcutDataStore = mock()
    private val lifecycleOwner: LifecycleOwner = mock()

    @Before
    fun setup() {
        testee = ShortcutsNewTabSettingsViewModel(
            coroutinesTestRule.testDispatcherProvider,
            setting,
        )
    }

    @Test
    fun whenViewCreatedAndSettingEnabledThenViewStateUpdated() = runTest {
        whenever(setting.isEnabled()).thenReturn(true)
        testee.onCreate(lifecycleOwner)
        testee.viewState.test {
            expectMostRecentItem().also {
                assertTrue(it.enabled)
            }
        }
    }

    @Test
    fun whenViewCreatedAndSettingDisabledThenViewStateUpdated() = runTest {
        whenever(setting.isEnabled()).thenReturn(false)
        testee.onCreate(lifecycleOwner)
        testee.viewState.test {
            expectMostRecentItem().also {
                assertFalse(it.enabled)
            }
        }
    }
}
