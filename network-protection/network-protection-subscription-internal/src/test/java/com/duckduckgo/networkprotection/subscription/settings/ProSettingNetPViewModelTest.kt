package com.duckduckgo.networkprotection.subscription.settings

import app.cash.turbine.test
import com.duckduckgo.app.statistics.pixels.Pixel
import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.common.ui.view.listitem.CheckListItem.CheckItemStatus.ENABLED
import com.duckduckgo.common.ui.view.listitem.CheckListItem.CheckItemStatus.WARNING
import com.duckduckgo.navigation.api.GlobalActivityStarter.ActivityParams
import com.duckduckgo.networkprotection.api.NetworkProtectionState
import com.duckduckgo.networkprotection.api.NetworkProtectionState.ConnectionState.CONNECTING
import com.duckduckgo.networkprotection.api.NetworkProtectionState.ConnectionState.DISCONNECTED
import com.duckduckgo.networkprotection.api.NetworkProtectionWaitlist
import com.duckduckgo.networkprotection.api.NetworkProtectionWaitlist.NetPWaitlistState.InBeta
import com.duckduckgo.networkprotection.api.NetworkProtectionWaitlist.NetPWaitlistState.JoinedWaitlist
import com.duckduckgo.networkprotection.api.NetworkProtectionWaitlist.NetPWaitlistState.NotUnlocked
import com.duckduckgo.networkprotection.api.NetworkProtectionWaitlist.NetPWaitlistState.PendingInviteCode
import com.duckduckgo.networkprotection.impl.pixels.NetworkProtectionPixelNames.NETP_SETTINGS_PRESSED
import com.duckduckgo.networkprotection.subscription.R
import com.duckduckgo.networkprotection.subscription.settings.ProSettingNetPViewModel.Command
import com.duckduckgo.networkprotection.subscription.settings.ProSettingNetPViewModel.NetPEntryState.Hidden
import com.duckduckgo.networkprotection.subscription.settings.ProSettingNetPViewModel.NetPEntryState.Pending
import com.duckduckgo.networkprotection.subscription.settings.ProSettingNetPViewModel.NetPEntryState.ShowState
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

@OptIn(ExperimentalCoroutinesApi::class)
class ProSettingNetPViewModelTest {

    @get:Rule
    val coroutineTestRule: CoroutineTestRule = CoroutineTestRule()

    private val pixel: Pixel = mock()
    private val networkProtectionState: NetworkProtectionState = mock()
    private val networkProtectionWaitlist: NetworkProtectionWaitlist = mock()
    private lateinit var proSettingNetPViewModel: ProSettingNetPViewModel

    @Before
    fun before() {
        runBlocking {
            whenever(networkProtectionWaitlist.getState()).thenReturn(NotUnlocked)
            whenever(networkProtectionState.isRunning()).thenReturn(false)
            whenever(networkProtectionState.isEnabled()).thenReturn(false)
            whenever(networkProtectionState.getConnectionStateFlow()).thenReturn(emptyFlow())
        }
        proSettingNetPViewModel = ProSettingNetPViewModel(
            networkProtectionWaitlist,
            networkProtectionState,
            coroutineTestRule.testDispatcherProvider,
            pixel,
        )
    }

    @Test
    fun whenNetPSettingClickedThenReturnScreenForCurrentState() = runTest {
        val testScreen = object : ActivityParams {}
        whenever(networkProtectionWaitlist.getScreenForCurrentState()).thenReturn(testScreen)

        proSettingNetPViewModel.commands().test {
            proSettingNetPViewModel.onNetPSettingClicked()

            assertEquals(Command.OpenNetPScreen(testScreen), awaitItem())
            verify(pixel).fire(NETP_SETTINGS_PRESSED)

            cancelAndConsumeRemainingEvents()
        }
    }

    @Test
    fun whenNetPIsNotUnlockedThenNetPEntryStateShouldShowHidden() = runTest {
        whenever(networkProtectionState.isRunning()).thenReturn(false)
        whenever(networkProtectionWaitlist.getState()).thenReturn(NotUnlocked)

        proSettingNetPViewModel.onResume(mock())

        proSettingNetPViewModel.viewState.test {
            assertEquals(
                Hidden,
                expectMostRecentItem().networkProtectionEntryState,
            )
        }
    }

    @Test
    fun whenNetPStateIsPendingInviteCodeThenNetPEntryStateShouldShowPending() = runTest {
        whenever(networkProtectionState.isRunning()).thenReturn(false)
        whenever(networkProtectionWaitlist.getState()).thenReturn(PendingInviteCode)

        proSettingNetPViewModel.onResume(mock())

        proSettingNetPViewModel.viewState.test {
            assertEquals(
                Pending,
                expectMostRecentItem().networkProtectionEntryState,
            )
        }
    }

    @Test
    fun whenNetPStateIsJoinedWaitlistThenNetPEntryStateShouldShowPending() = runTest {
        whenever(networkProtectionState.isRunning()).thenReturn(false)
        whenever(networkProtectionWaitlist.getState()).thenReturn(JoinedWaitlist)

        proSettingNetPViewModel.onResume(mock())

        proSettingNetPViewModel.viewState.test {
            assertEquals(
                Pending,
                expectMostRecentItem().networkProtectionEntryState,
            )
        }
    }

    @Test
    fun whenNetPStateIsInBetaButNotAcceptedTermsThenNetPEntryStateShouldShowPending() = runTest {
        whenever(networkProtectionState.isRunning()).thenReturn(false)
        whenever(networkProtectionState.isOnboarded()).thenReturn(false)
        whenever(networkProtectionWaitlist.getState()).thenReturn(InBeta(false))

        proSettingNetPViewModel.onResume(mock())

        proSettingNetPViewModel.viewState.test {
            assertEquals(
                Pending,
                expectMostRecentItem().networkProtectionEntryState,
            )
        }
    }

    @Test
    fun whenNetPStateIsInBetaWithTermsAcceptedAndEnabledThenNetPEntryStateShouldCorrectShowState() = runTest {
        whenever(networkProtectionState.isRunning()).thenReturn(true)
        whenever(networkProtectionState.isOnboarded()).thenReturn(false)
        whenever(networkProtectionWaitlist.getState()).thenReturn(InBeta(true))

        proSettingNetPViewModel.onResume(mock())

        proSettingNetPViewModel.viewState.test {
            assertEquals(
                ShowState(
                    icon = ENABLED,
                    subtitle = R.string.netpSubscriptionSettingsConnected,
                ),
                expectMostRecentItem().networkProtectionEntryState,
            )
        }
    }

    @Test
    fun whenNetPStateIsInBetaOnboardedAndEnabledThenNetPEntryStateShouldCorrectShowState() = runTest {
        whenever(networkProtectionState.isRunning()).thenReturn(true)
        whenever(networkProtectionState.isOnboarded()).thenReturn(true)
        whenever(networkProtectionWaitlist.getState()).thenReturn(InBeta(false))

        proSettingNetPViewModel.onResume(mock())

        proSettingNetPViewModel.viewState.test {
            assertEquals(
                ShowState(
                    icon = ENABLED,
                    subtitle = R.string.netpSubscriptionSettingsConnected,
                ),
                expectMostRecentItem().networkProtectionEntryState,
            )
        }
    }

    @Test
    fun whenNetPStateIsInBetaAndConnectingThenNetPEntryStateShouldCorrectShowState() = runTest {
        whenever(networkProtectionState.isRunning()).thenReturn(false)
        whenever(networkProtectionState.getConnectionStateFlow()).thenReturn(flowOf(CONNECTING))
        whenever(networkProtectionState.isOnboarded()).thenReturn(false)
        whenever(networkProtectionWaitlist.getState()).thenReturn(InBeta(true))

        proSettingNetPViewModel.onResume(mock())

        proSettingNetPViewModel.viewState.test {
            assertEquals(
                ShowState(
                    icon = ENABLED,
                    subtitle = R.string.netpSubscriptionSettingsConnecting,
                ),
                expectMostRecentItem().networkProtectionEntryState,
            )
        }
    }

    @Test
    fun whenNetPStateIsInBetaAndDisabledThenNetPEntryStateShouldCorrectShowState() = runTest {
        whenever(networkProtectionState.isRunning()).thenReturn(false)
        whenever(networkProtectionState.getConnectionStateFlow()).thenReturn(flowOf(DISCONNECTED))
        whenever(networkProtectionState.isOnboarded()).thenReturn(true)
        whenever(networkProtectionWaitlist.getState()).thenReturn(InBeta(false))

        proSettingNetPViewModel.onResume(mock())

        proSettingNetPViewModel.viewState.test {
            assertEquals(
                ShowState(
                    icon = WARNING,
                    subtitle = R.string.netpSubscriptionSettingsDisconnected,
                ),
                expectMostRecentItem().networkProtectionEntryState,
            )
        }
    }
}
