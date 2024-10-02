package com.duckduckgo.networkprotection.impl.autoexclude

import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import app.cash.turbine.test
import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.networkprotection.impl.autoexclude.VpnAutoExcludePromptViewModel.ItemInfo
import com.duckduckgo.networkprotection.impl.autoexclude.VpnAutoExcludePromptViewModel.ViewState
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.ArgumentMatchers.eq
import org.mockito.Mock
import org.mockito.MockitoAnnotations
import org.mockito.kotlin.any
import org.mockito.kotlin.whenever

class VpnAutoExcludePromptViewModelTest {
    @get:Rule var coroutineRule = CoroutineTestRule()

    @Mock
    private lateinit var packageManager: PackageManager

    private lateinit var viewModel: VpnAutoExcludePromptViewModel

    @Before
    fun setUp() {
        MockitoAnnotations.openMocks(this)
        viewModel = VpnAutoExcludePromptViewModel(
            coroutineRule.testDispatcherProvider,
            packageManager,
        )
    }

    @Test
    fun whenOnPromptShownThenEmitViewState() = runTest {
        whenever(packageManager.getApplicationInfo(any(), eq(0))).thenReturn(ApplicationInfo())
        whenever(packageManager.getApplicationLabel(any())).thenReturn("Test")

        viewModel.onPromptShown(listOf("test1", "test2"))

        viewModel.viewState().test {
            assertEquals(
                ViewState(
                    incompatibleApps = listOf(
                        ItemInfo("test1", "Test"),
                        ItemInfo("test2", "Test"),
                    ),
                ),
                awaitItem(),
            )
        }
    }
}
