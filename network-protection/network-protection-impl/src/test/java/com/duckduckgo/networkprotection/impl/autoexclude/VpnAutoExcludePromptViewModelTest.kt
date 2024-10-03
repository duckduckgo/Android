package com.duckduckgo.networkprotection.impl.autoexclude

import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import app.cash.turbine.test
import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.networkprotection.api.NetworkProtectionState
import com.duckduckgo.networkprotection.impl.autoexclude.VpnAutoExcludePromptViewModel.ItemInfo
import com.duckduckgo.networkprotection.impl.autoexclude.VpnAutoExcludePromptViewModel.ViewState
import com.duckduckgo.networkprotection.impl.settings.FakeNetPSettingsLocalConfigFactory
import com.duckduckgo.networkprotection.store.NetPManualExclusionListRepository
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.ArgumentMatchers.eq
import org.mockito.Mock
import org.mockito.MockitoAnnotations
import org.mockito.kotlin.any
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.whenever

class VpnAutoExcludePromptViewModelTest {
    @get:Rule var coroutineRule = CoroutineTestRule()

    @Mock
    private lateinit var packageManager: PackageManager

    @Mock
    private lateinit var networkProtectionState: NetworkProtectionState

    @Mock
    private lateinit var netPManualExclusionListRepository: NetPManualExclusionListRepository

    private val localConfig = FakeNetPSettingsLocalConfigFactory.create()
    private lateinit var viewModel: VpnAutoExcludePromptViewModel

    @Before
    fun setUp() {
        MockitoAnnotations.openMocks(this)
        whenever(packageManager.getApplicationInfo(any(), eq(0))).thenReturn(ApplicationInfo())
        whenever(packageManager.getApplicationLabel(any())).thenReturn("Test")
        viewModel = VpnAutoExcludePromptViewModel(
            coroutineRule.testDispatcherProvider,
            packageManager,
            localConfig,
            networkProtectionState,
            netPManualExclusionListRepository,
        )
    }

    @Test
    fun whenOnPromptShownThenEmitViewState() = runTest {
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

    @Test
    fun whenAllAppsOnlyCheckedThenManuallyExcludeAllApps() {
        viewModel.onPromptShown(listOf("test1", "test2"))

        viewModel.onAddExclusionsSelected(false)

        verify(netPManualExclusionListRepository).manuallyExcludeApps(listOf("test1", "test2"))
        verify(networkProtectionState).restart()
    }

    @Test
    fun whenOneAppCheckedThenManuallyExcludeOneApp() {
        viewModel.onPromptShown(listOf("test1", "test2"))

        viewModel.updateAppExcludeState("test1", false)
        viewModel.onAddExclusionsSelected(false)

        verify(netPManualExclusionListRepository).manuallyExcludeApps(listOf("test2"))
        verify(networkProtectionState).restart()
    }

    @Test
    fun whenAutoExcludeCheckedThenEnableAutoExclude() {
        viewModel.onPromptShown(listOf("test1", "test2"))

        viewModel.onAddExclusionsSelected(true)

        verifyNoInteractions(netPManualExclusionListRepository)
        verify(networkProtectionState).restart()
        assertTrue(localConfig.autoExcludeBrokenApps().isEnabled())
    }

    @Test
    fun whenNothingIsCheckedAndAddExclusionSelectedThenDoNothing() {
        viewModel.onPromptShown(listOf("test1"))

        viewModel.updateAppExcludeState("test1", false)
        viewModel.onAddExclusionsSelected(false)

        verifyNoInteractions(netPManualExclusionListRepository)
        verifyNoInteractions(networkProtectionState)
        assertFalse(localConfig.autoExcludeBrokenApps().isEnabled())
    }
}
