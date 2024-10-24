package com.duckduckgo.networkprotection.impl.autoexclude

import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import app.cash.turbine.test
import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.networkprotection.api.NetworkProtectionState
import com.duckduckgo.networkprotection.impl.autoexclude.VpnAutoExcludePromptFragment.Companion.Source.EXCLUSION_LIST_SCREEN
import com.duckduckgo.networkprotection.impl.autoexclude.VpnAutoExcludePromptFragment.Companion.Source.VPN_SCREEN
import com.duckduckgo.networkprotection.impl.autoexclude.VpnAutoExcludePromptViewModel.ItemInfo
import com.duckduckgo.networkprotection.impl.autoexclude.VpnAutoExcludePromptViewModel.PromptState.ALL_INCOMPATIBLE_APPS
import com.duckduckgo.networkprotection.impl.autoexclude.VpnAutoExcludePromptViewModel.PromptState.NEW_INCOMPATIBLE_APP
import com.duckduckgo.networkprotection.impl.autoexclude.VpnAutoExcludePromptViewModel.ViewState
import com.duckduckgo.networkprotection.impl.pixels.NetworkProtectionPixels
import com.duckduckgo.networkprotection.impl.settings.FakeNetPSettingsLocalConfigFactory
import com.duckduckgo.networkprotection.store.NetPManualExclusionListRepository
import com.duckduckgo.networkprotection.store.db.NetPManuallyExcludedApp
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
import org.mockito.kotlin.verifyNoMoreInteractions
import org.mockito.kotlin.whenever

class VpnAutoExcludePromptViewModelTest {
    @get:Rule var coroutineRule = CoroutineTestRule()

    @Mock
    private lateinit var packageManager: PackageManager

    @Mock
    private lateinit var networkProtectionState: NetworkProtectionState

    @Mock
    private lateinit var netPManualExclusionListRepository: NetPManualExclusionListRepository

    @Mock
    private lateinit var networkProtectionPixels: NetworkProtectionPixels

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
            networkProtectionPixels,
        )
    }

    @Test
    fun whenOnPromptShownFromVPNThenEmitViewState() = runTest {
        viewModel.onPromptShown(listOf("test1", "test2"), VPN_SCREEN)

        viewModel.viewState().test {
            assertEquals(
                ViewState(
                    incompatibleApps = listOf(
                        ItemInfo("test1", "Test"),
                        ItemInfo("test2", "Test"),
                    ),
                    promptState = NEW_INCOMPATIBLE_APP,
                ),
                awaitItem(),
            )
        }
        verify(networkProtectionPixels).reportAutoExcludePromptShownInVPNScreen()
    }

    @Test
    fun whenOnPromptShownFromExclusionListThenEmitViewState() = runTest {
        viewModel.onPromptShown(listOf("test1", "test2"), EXCLUSION_LIST_SCREEN)

        viewModel.viewState().test {
            assertEquals(
                ViewState(
                    incompatibleApps = listOf(
                        ItemInfo("test1", "Test"),
                        ItemInfo("test2", "Test"),
                    ),
                    promptState = ALL_INCOMPATIBLE_APPS,
                ),
                awaitItem(),
            )
        }
        verify(networkProtectionPixels).reportAutoExcludePromptShownInExclusionList()
    }

    @Test
    fun whenAllAppsOnlyCheckedThenManuallyExcludeAllApps() {
        viewModel.onPromptShown(listOf("test1", "test2"), VPN_SCREEN)

        viewModel.onAddExclusionsSelected(false)

        verify(netPManualExclusionListRepository).manuallyExcludeApps(listOf("test1", "test2"))
        verify(networkProtectionState).restart()
    }

    @Test
    fun whenOneAppCheckedThenManuallyExcludeOneApp() {
        viewModel.onPromptShown(listOf("test1", "test2"), VPN_SCREEN)

        viewModel.updateAppExcludeState("test1", false)
        viewModel.onAddExclusionsSelected(false)

        verify(netPManualExclusionListRepository).manuallyExcludeApps(listOf("test2"))
        verify(networkProtectionState).restart()
        verify(networkProtectionPixels).reportAutoExcludePromptShownInVPNScreen()
        verify(networkProtectionPixels).reportAutoExcludePromptExcludeApps()
        verifyNoMoreInteractions(networkProtectionPixels)
    }

    @Test
    fun whenAutoExcludeCheckedThenEnableAutoExclude() {
        whenever(netPManualExclusionListRepository.getManualAppExclusionList()).thenReturn(emptyList())
        viewModel.onPromptShown(listOf("test1", "test2"), VPN_SCREEN)

        viewModel.onAddExclusionsSelected(true)

        verify(netPManualExclusionListRepository).getManualAppExclusionList()
        verifyNoMoreInteractions(netPManualExclusionListRepository)
        verify(networkProtectionState).restart()
        assertTrue(localConfig.autoExcludeBrokenApps().isEnabled())
    }

    @Test
    fun whenAutoExcludeCheckedWithOneAppUncheckedThenEnableAutoExcludeAndEnableOneUncheckedApp() {
        whenever(netPManualExclusionListRepository.getManualAppExclusionList()).thenReturn(emptyList())
        viewModel.onPromptShown(listOf("test1", "test2"), VPN_SCREEN)
        viewModel.updateAppExcludeState("test2", false)

        viewModel.onAddExclusionsSelected(true)

        verify(netPManualExclusionListRepository).getManualAppExclusionList()
        verify(netPManualExclusionListRepository).manuallyEnableApps(listOf("test2"))
        verifyNoMoreInteractions(netPManualExclusionListRepository)
        verify(networkProtectionState).restart()
        assertTrue(localConfig.autoExcludeBrokenApps().isEnabled())
    }

    @Test
    fun whenAddExclusionsWithAnIncompatibleAppManuallyEnabledThenEnableAutoExcludeAndManuallyExcludeApp() {
        whenever(netPManualExclusionListRepository.getManualAppExclusionList()).thenReturn(
            listOf(
                NetPManuallyExcludedApp("test2", true),
                NetPManuallyExcludedApp("test3", false),
            ),
        )
        viewModel.onPromptShown(listOf("test1", "test2"), VPN_SCREEN)

        viewModel.onAddExclusionsSelected(true)

        verify(netPManualExclusionListRepository).manuallyExcludeApps(listOf("test2"))
        verify(networkProtectionState).restart()
        assertTrue(localConfig.autoExcludeBrokenApps().isEnabled())
        verify(networkProtectionPixels).reportAutoExcludePromptShownInVPNScreen()
        verify(networkProtectionPixels).reportAutoExcludePromptEnable()
        verify(networkProtectionPixels).reportAutoExcludePromptExcludeApps()
        verifyNoMoreInteractions(networkProtectionPixels)
    }

    @Test
    fun whenPromptCancelledThenDoNothing() {
        viewModel.onPromptShown(listOf("test1", "test2"), VPN_SCREEN)

        viewModel.onCancelPrompt()

        verifyNoInteractions(netPManualExclusionListRepository)
        verifyNoInteractions(networkProtectionState)
        assertFalse(localConfig.autoExcludeBrokenApps().isEnabled())
        verify(networkProtectionPixels).reportAutoExcludePromptShownInVPNScreen()
        verify(networkProtectionPixels).reportAutoExcludePromptNoAction()
        verifyNoMoreInteractions(networkProtectionPixels)
    }
}
