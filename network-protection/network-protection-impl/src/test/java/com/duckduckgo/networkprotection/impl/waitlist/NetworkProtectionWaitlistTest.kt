package com.duckduckgo.networkprotection.impl.waitlist

import com.duckduckgo.appbuildconfig.api.AppBuildConfig
import com.duckduckgo.appbuildconfig.api.BuildFlavor.INTERNAL
import com.duckduckgo.feature.toggles.api.Toggle
import com.duckduckgo.networkprotection.api.NetPInviteCodeScreenNoParams
import com.duckduckgo.networkprotection.api.NetworkProtectionManagementScreenNoParams
import com.duckduckgo.networkprotection.api.NetworkProtectionWaitlist
import com.duckduckgo.networkprotection.api.NetworkProtectionWaitlist.NetPWaitlistState.InBeta
import com.duckduckgo.networkprotection.api.NetworkProtectionWaitlist.NetPWaitlistState.JoinedWaitlist
import com.duckduckgo.networkprotection.api.NetworkProtectionWaitlist.NetPWaitlistState.NotUnlocked
import com.duckduckgo.networkprotection.api.NetworkProtectionWaitlist.NetPWaitlistState.PendingInviteCode
import com.duckduckgo.networkprotection.impl.waitlist.store.FakeNetPWaitlistRepository
import com.duckduckgo.networkprotection.impl.waitlist.store.NetPWaitlistRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

@OptIn(ExperimentalCoroutinesApi::class)
class NetworkProtectionWaitlistTest {

    private val appBuildConfig: AppBuildConfig = mock()

    private lateinit var netPWaitlistRepository: NetPWaitlistRepository
    private lateinit var netPRemoteFeature: NetPRemoteFeature
    private lateinit var networkProtectionWaitlist: NetworkProtectionWaitlist

    @Before
    fun setup() {
        netPWaitlistRepository = FakeNetPWaitlistRepository()
        netPRemoteFeature = FakeNetPRemoteFeatureFactory.create()

        networkProtectionWaitlist = NetworkProtectionWaitlistImpl(netPRemoteFeature, appBuildConfig, netPWaitlistRepository)
    }

    @Test
    fun whenStartingInternalStateIsPendingInviteCode() {
        whenever(appBuildConfig.flavor).thenReturn(INTERNAL)
        assertEquals(PendingInviteCode, networkProtectionWaitlist.getState())
    }

    @Test
    fun whenStartingNonInternalStateIsLocked() {
        assertEquals(NotUnlocked, networkProtectionWaitlist.getState())
    }

    @Test
    fun whenAuthTokenSetStateIsInBeta() {
        netPWaitlistRepository.setAuthenticationToken("fakeToken")
        netPRemoteFeature.waitlist().setEnabled(Toggle.State(enable = true))
        netPRemoteFeature.self().setEnabled(Toggle.State(enable = true))
        assertEquals(InBeta, networkProtectionWaitlist.getState())
    }

    @Test
    fun whenSubFeatureNotTreatedThenStateIsAlwaysNotUnlocked() = runTest {
        netPRemoteFeature.waitlist().setEnabled(Toggle.State(enable = false))
        netPRemoteFeature.self().setEnabled(Toggle.State(enable = true))

        netPWaitlistRepository.setAuthenticationToken("fakeToken")
        assertEquals(NotUnlocked, networkProtectionWaitlist.getState())

        netPWaitlistRepository.setWaitlistToken("fakeToken")
        assertEquals(NotUnlocked, networkProtectionWaitlist.getState())
    }

    @Test
    fun whenFeatureNotTreatedThenStateIsAlwaysNotUnlocked() = runTest {
        netPRemoteFeature.waitlist().setEnabled(Toggle.State(enable = true))
        netPRemoteFeature.self().setEnabled(Toggle.State(enable = false))

        netPWaitlistRepository.setAuthenticationToken("fakeToken")
        assertEquals(NotUnlocked, networkProtectionWaitlist.getState())

        netPWaitlistRepository.setWaitlistToken("fakeToken")
        assertEquals(NotUnlocked, networkProtectionWaitlist.getState())
    }

    @Test
    fun whenFeatureAndSubFeatureNotTreatedAndAuthTokenSetStateNotUnlocked() {
        netPWaitlistRepository.setAuthenticationToken("fakeToken")
        assertEquals(NotUnlocked, networkProtectionWaitlist.getState())
    }

    @Test
    fun whenFeatureAndSubFeatureNotTreatedAndInternalBuildAndAuthTokenSetStateInBeta() = runTest {
        whenever(appBuildConfig.flavor).thenReturn(INTERNAL)

        netPWaitlistRepository.setAuthenticationToken("fakeToken")
        assertEquals(InBeta, networkProtectionWaitlist.getState())
    }

    @Test
    fun whenFeatureAndSubFeatureNotTreatedAndBothAuthAndWaitlistTokensSetThenStateIsInBeta() = runTest {
        netPRemoteFeature.waitlist().setEnabled(Toggle.State(enable = true))
        netPRemoteFeature.self().setEnabled(Toggle.State(enable = true))

        netPWaitlistRepository.setAuthenticationToken("fakeToken")
        assertEquals(InBeta, networkProtectionWaitlist.getState())

        netPWaitlistRepository.setWaitlistToken("fakeToken")
        assertEquals(InBeta, networkProtectionWaitlist.getState())
    }

    @Test
    fun whenFeatureAndSubFeatureNotTreatedAndInternalBuildAndWaitlistTokenSetStateDidJoinWaitlist() = runTest {
        whenever(appBuildConfig.flavor).thenReturn(INTERNAL)
        netPWaitlistRepository.setWaitlistToken("fakeToken")
        assertEquals(JoinedWaitlist, networkProtectionWaitlist.getState())
    }

    @Test
    fun whenNotTreatedAndAuthTokenExistsThenReturnWaitlistState() {
        netPRemoteFeature.waitlist().setEnabled(Toggle.State(enable = false))
        netPRemoteFeature.self().setEnabled(Toggle.State(enable = false))
        netPWaitlistRepository.setAuthenticationToken("token")

        assertEquals(InBeta, networkProtectionWaitlist.getState())
    }

    @Test
    fun whenGetScreenForStateAndNotTreatedThenReturnWaitlistScreen() {
        netPRemoteFeature.waitlist().setEnabled(Toggle.State(enable = false))
        netPRemoteFeature.self().setEnabled(Toggle.State(enable = false))

        assertEquals(NetPWaitlistScreenNoParams, networkProtectionWaitlist.getScreenForCurrentState())
    }

    @Test
    fun whenGetScreenForStateNotTreatedAndInternalThenReturnWaitlistScreen() {
        netPRemoteFeature.waitlist().setEnabled(Toggle.State(enable = false))
        netPRemoteFeature.self().setEnabled(Toggle.State(enable = false))
        whenever(appBuildConfig.flavor).thenReturn(INTERNAL)

        assertEquals(NetPWaitlistScreenNoParams, networkProtectionWaitlist.getScreenForCurrentState())
    }

    @Test
    fun whenGetScreenForStateSubFeatureDisabledAndInternalThenReturnWaitlistScreen() {
        netPRemoteFeature.waitlist().setEnabled(Toggle.State(enable = false))
        netPRemoteFeature.self().setEnabled(Toggle.State(enable = true))
        whenever(appBuildConfig.flavor).thenReturn(INTERNAL)

        assertEquals(NetPWaitlistScreenNoParams, networkProtectionWaitlist.getScreenForCurrentState())
    }

    @Test
    fun whenGetScreenForStateFeatureDisabledAndInternalThenReturnWaitlistScreen() {
        netPRemoteFeature.waitlist().setEnabled(Toggle.State(enable = true))
        netPRemoteFeature.self().setEnabled(Toggle.State(enable = false))
        whenever(appBuildConfig.flavor).thenReturn(INTERNAL)

        assertEquals(NetPWaitlistScreenNoParams, networkProtectionWaitlist.getScreenForCurrentState())
    }

    @Test
    fun whenGetScreenForStateAndTreatedThenReturnWaitlistScreen() {
        netPRemoteFeature.waitlist().setEnabled(Toggle.State(enable = true))
        netPRemoteFeature.self().setEnabled(Toggle.State(enable = true))

        assertEquals(NetPWaitlistScreenNoParams, networkProtectionWaitlist.getScreenForCurrentState())
    }

    @Test
    fun whenGetScreenForStateWithAuthTokenAndTreatedAndTermsNotAcceptedThenReturnInviteCodeScreen() {
        netPRemoteFeature.waitlist().setEnabled(Toggle.State(enable = true))
        netPRemoteFeature.self().setEnabled(Toggle.State(enable = true))
        netPWaitlistRepository.setAuthenticationToken("token")

        assertEquals(NetPInviteCodeScreenNoParams, networkProtectionWaitlist.getScreenForCurrentState())
    }

    @Test
    fun whenGetScreenForStateWithAuthTokenAndTreatedAndTermsAcceptedThenReturnNetPManagementScreen() {
        netPRemoteFeature.waitlist().setEnabled(Toggle.State(enable = true))
        netPRemoteFeature.self().setEnabled(Toggle.State(enable = true))
        netPWaitlistRepository.setAuthenticationToken("token")
        netPWaitlistRepository.acceptWaitlistTerms()

        assertEquals(NetworkProtectionManagementScreenNoParams, networkProtectionWaitlist.getScreenForCurrentState())
    }

    @Test
    fun whenGetScreenForStateWithWaitlistTokenThenReturnInviteCodeScreen() = runTest {
        netPRemoteFeature.waitlist().setEnabled(Toggle.State(enable = true))
        netPRemoteFeature.self().setEnabled(Toggle.State(enable = true))
        netPWaitlistRepository.setWaitlistToken("token")

        assertEquals(NetPWaitlistScreenNoParams, networkProtectionWaitlist.getScreenForCurrentState())
    }
}
