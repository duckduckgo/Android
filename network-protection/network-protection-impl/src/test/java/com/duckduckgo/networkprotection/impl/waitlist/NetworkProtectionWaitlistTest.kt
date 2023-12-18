package com.duckduckgo.networkprotection.impl.waitlist

import com.duckduckgo.appbuildconfig.api.AppBuildConfig
import com.duckduckgo.appbuildconfig.api.BuildFlavor.INTERNAL
import com.duckduckgo.appbuildconfig.api.BuildFlavor.PLAY
import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.feature.toggles.api.Toggle
import com.duckduckgo.networkprotection.api.NetworkProtectionScreens.NetPWaitlistInvitedScreenNoParams
import com.duckduckgo.networkprotection.api.NetworkProtectionScreens.NetworkProtectionManagementScreenNoParams
import com.duckduckgo.networkprotection.api.NetworkProtectionState
import com.duckduckgo.networkprotection.api.NetworkProtectionWaitlist
import com.duckduckgo.networkprotection.api.NetworkProtectionWaitlist.NetPWaitlistState.InBeta
import com.duckduckgo.networkprotection.api.NetworkProtectionWaitlist.NetPWaitlistState.JoinedWaitlist
import com.duckduckgo.networkprotection.api.NetworkProtectionWaitlist.NetPWaitlistState.NotUnlocked
import com.duckduckgo.networkprotection.api.NetworkProtectionWaitlist.NetPWaitlistState.PendingInviteCode
import com.duckduckgo.networkprotection.impl.pixels.NetworkProtectionPixels
import com.duckduckgo.networkprotection.impl.state.NetPFeatureRemover
import com.duckduckgo.networkprotection.impl.waitlist.store.FakeNetPWaitlistRepository
import com.duckduckgo.networkprotection.impl.waitlist.store.NetPWaitlistRepository
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class NetworkProtectionWaitlistTest {

    @get:Rule
    var coroutinesTestRule = CoroutineTestRule()

    private val appBuildConfig: AppBuildConfig = mock()

    private lateinit var netPWaitlistRepository: NetPWaitlistRepository
    private lateinit var netPRemoteFeature: NetPRemoteFeature
    private lateinit var networkProtectionWaitlist: NetworkProtectionWaitlist
    private val networkProtectionState: NetworkProtectionState = mock()
    private val networkProtectionPixels: NetworkProtectionPixels = mock()
    private var isUsTimeZone: Boolean = false

    @Before
    fun setup() {
        isUsTimeZone = false
        netPWaitlistRepository = FakeNetPWaitlistRepository()
        netPRemoteFeature = FakeNetPRemoteFeatureFactory.create()
        val netPRemoteFeatureWrapper = NetPRemoteFeatureWrapper(
            netPRemoteFeature,
            mock<NetPFeatureRemover>(),
            appBuildConfig,
            coroutinesTestRule.testScope,
            coroutinesTestRule.testDispatcherProvider,
        )
        whenever(appBuildConfig.flavor).thenReturn(PLAY)

        networkProtectionWaitlist = NetworkProtectionWaitlistImpl(
            netPRemoteFeatureWrapper,
            appBuildConfig,
            netPWaitlistRepository,
            networkProtectionState,
            networkProtectionPixels,
            coroutinesTestRule.testDispatcherProvider,
            { isUsTimeZone },
        )
    }

    @Test
    fun whenStartingInternalStateIsPendingInviteCode() = runTest {
        whenever(appBuildConfig.flavor).thenReturn(INTERNAL)
        assertEquals(PendingInviteCode, networkProtectionWaitlist.getState())
    }

    @Test
    fun whenStartingNonInternalStateIsLocked() = runTest {
        assertEquals(NotUnlocked, networkProtectionWaitlist.getState())
    }

    @Test
    fun whenInBetaAndTermsAcceptedThenInBetaTermsAccepted() = runTest {
        netPWaitlistRepository.setAuthenticationToken("fakeToken")
        netPWaitlistRepository.acceptWaitlistTerms()
        netPRemoteFeature.waitlist().setEnabled(Toggle.State(enable = true))
        netPRemoteFeature.self().setEnabled(Toggle.State(enable = true))
        assertEquals(InBeta(true), networkProtectionWaitlist.getState())
    }

    @Test
    fun whenAuthTokenSetStateIsInBeta() = runTest {
        netPWaitlistRepository.setAuthenticationToken("fakeToken")
        netPRemoteFeature.waitlist().setEnabled(Toggle.State(enable = true))
        netPRemoteFeature.self().setEnabled(Toggle.State(enable = true))
        assertEquals(InBeta(false), networkProtectionWaitlist.getState())
    }

    @Test
    fun whenSubFeatureNotTreatedAndAuthTokenSetThenInBeta() = runTest {
        netPRemoteFeature.waitlist().setEnabled(Toggle.State(enable = false))
        netPRemoteFeature.self().setEnabled(Toggle.State(enable = true))

        netPWaitlistRepository.setAuthenticationToken("fakeToken")
        assertEquals(InBeta(false), networkProtectionWaitlist.getState())
    }

    @Test
    fun whenSubFeatureNotTreatedAndWaitlistTokenThenJoinedWaitlist() = runTest {
        netPRemoteFeature.waitlist().setEnabled(Toggle.State(enable = false))
        netPRemoteFeature.self().setEnabled(Toggle.State(enable = true))

        netPWaitlistRepository.setWaitlistToken("fakeToken")
        assertEquals(JoinedWaitlist, networkProtectionWaitlist.getState())
    }

    @Test
    fun whenFeatureNotTreatedAndAuthTokenSetThenInBeta() = runTest {
        netPRemoteFeature.waitlist().setEnabled(Toggle.State(enable = true))
        netPRemoteFeature.self().setEnabled(Toggle.State(enable = false))

        netPWaitlistRepository.setAuthenticationToken("fakeToken")
        assertEquals(InBeta(false), networkProtectionWaitlist.getState())
    }

    @Test
    fun whenFeatureNotTreatedAndWaitlistTokenSetThenJoinedWaitlist() = runTest {
        netPRemoteFeature.waitlist().setEnabled(Toggle.State(enable = true))
        netPRemoteFeature.self().setEnabled(Toggle.State(enable = false))

        netPWaitlistRepository.setWaitlistToken("fakeToken")
        assertEquals(JoinedWaitlist, networkProtectionWaitlist.getState())
    }

    @Test
    fun whenFeatureAndSubFeatureNotTreatedAndAuthTokenSetStateInBeta() = runTest {
        netPWaitlistRepository.setAuthenticationToken("fakeToken")
        assertEquals(InBeta(false), networkProtectionWaitlist.getState())
    }

    @Test
    fun whenFeatureAndSubFeatureNotTreatedAndInternalBuildAndAuthTokenSetStateInBeta() = runTest {
        whenever(appBuildConfig.flavor).thenReturn(INTERNAL)

        netPWaitlistRepository.setAuthenticationToken("fakeToken")
        assertEquals(InBeta(false), networkProtectionWaitlist.getState())
    }

    @Test
    fun whenFeatureAndSubFeatureNotTreatedAndBothAuthAndWaitlistTokensSetThenStateIsInBeta() = runTest {
        netPRemoteFeature.waitlist().setEnabled(Toggle.State(enable = true))
        netPRemoteFeature.self().setEnabled(Toggle.State(enable = true))

        netPWaitlistRepository.setAuthenticationToken("fakeToken")
        assertEquals(InBeta(false), networkProtectionWaitlist.getState())

        netPWaitlistRepository.setWaitlistToken("fakeToken")
        assertEquals(InBeta(false), networkProtectionWaitlist.getState())
    }

    @Test
    fun whenFeatureAndSubFeatureNotTreatedAndInternalBuildAndWaitlistTokenSetStateDidJoinWaitlist() = runTest {
        whenever(appBuildConfig.flavor).thenReturn(INTERNAL)
        netPWaitlistRepository.setWaitlistToken("fakeToken")
        assertEquals(JoinedWaitlist, networkProtectionWaitlist.getState())
    }

    @Test
    fun whenWaitlistEnabledAndWaitlistTokenNonUSThenJoinedWaitlist() = runTest {
        whenever(appBuildConfig.flavor).thenReturn(PLAY)
        netPRemoteFeature.waitlist().setEnabled(Toggle.State(enable = true))
        netPRemoteFeature.self().setEnabled(Toggle.State(enable = true))
        netPWaitlistRepository.setWaitlistToken("fakeToken")
        assertEquals(JoinedWaitlist, networkProtectionWaitlist.getState())
    }

    @Test
    fun whenWaitlistEnabledAndWaitlistTokenAndUSThenJoinedWaitlist() = runTest {
        whenever(appBuildConfig.flavor).thenReturn(PLAY)
        netPRemoteFeature.waitlist().setEnabled(Toggle.State(enable = true))
        netPRemoteFeature.self().setEnabled(Toggle.State(enable = true))
        netPWaitlistRepository.setWaitlistToken("fakeToken")
        isUsTimeZone = true
        assertEquals(JoinedWaitlist, networkProtectionWaitlist.getState())
    }

    @Test
    fun whenWaitlistEnabledAndAuthTokenNonUSThenInBeta() = runTest {
        whenever(appBuildConfig.flavor).thenReturn(PLAY)
        netPRemoteFeature.waitlist().setEnabled(Toggle.State(enable = true))
        netPRemoteFeature.self().setEnabled(Toggle.State(enable = true))
        netPWaitlistRepository.setAuthenticationToken("fakeToken")
        isUsTimeZone = false
        assertEquals(InBeta(termsAccepted = false), networkProtectionWaitlist.getState())
    }

    @Test
    fun whenWaitlistEnabledAndAuthTokenUSThenInBeta() = runTest {
        whenever(appBuildConfig.flavor).thenReturn(PLAY)
        netPRemoteFeature.waitlist().setEnabled(Toggle.State(enable = true))
        netPRemoteFeature.self().setEnabled(Toggle.State(enable = true))
        netPWaitlistRepository.setAuthenticationToken("fakeToken")
        isUsTimeZone = true
        assertEquals(InBeta(termsAccepted = false), networkProtectionWaitlist.getState())
    }

    @Test
    fun whenWaitlistEnabledNonUSThenNotUnlocked() = runTest {
        whenever(appBuildConfig.flavor).thenReturn(PLAY)
        netPRemoteFeature.waitlist().setEnabled(Toggle.State(enable = true))
        netPRemoteFeature.self().setEnabled(Toggle.State(enable = true))
        isUsTimeZone = false
        assertEquals(NotUnlocked, networkProtectionWaitlist.getState())
    }

    @Test
    fun whenWaitlistEnabledUSThenPendingInviteCode() = runTest {
        whenever(appBuildConfig.flavor).thenReturn(PLAY)
        netPRemoteFeature.waitlist().setEnabled(Toggle.State(enable = true))
        netPRemoteFeature.self().setEnabled(Toggle.State(enable = true))
        isUsTimeZone = true
        assertEquals(PendingInviteCode, networkProtectionWaitlist.getState())
    }

    @Test
    fun whenNotTreatedAndAuthTokenExistsThenReturnWaitlistState() = runTest {
        netPRemoteFeature.waitlist().setEnabled(Toggle.State(enable = false))
        netPRemoteFeature.self().setEnabled(Toggle.State(enable = false))
        netPWaitlistRepository.setAuthenticationToken("token")

        assertEquals(InBeta(false), networkProtectionWaitlist.getState())
    }

    @Test
    fun whenNotTreatedAndWaitlistBetaFinishedAndAuthTokenExistsThenReturnNotUnlocked() = runTest {
        netPRemoteFeature.waitlist().setEnabled(Toggle.State(enable = false))
        netPRemoteFeature.self().setEnabled(Toggle.State(enable = false))
        netPRemoteFeature.waitlistBetaActive().setEnabled(Toggle.State(enable = false))
        netPWaitlistRepository.setAuthenticationToken("token")

        assertEquals(NotUnlocked, networkProtectionWaitlist.getState())
    }

    @Test
    fun whenGetScreenForStateAndNotTreatedThenReturnWaitlistScreen() = runTest {
        netPRemoteFeature.waitlist().setEnabled(Toggle.State(enable = false))
        netPRemoteFeature.self().setEnabled(Toggle.State(enable = false))

        assertEquals(NetPWaitlistScreenNoParams, networkProtectionWaitlist.getScreenForCurrentState())
    }

    @Test
    fun whenGetScreenForStateAndWaitlistBetaFinishedAndNotTreatedThenReturnWaitlistScreen() = runTest {
        netPRemoteFeature.waitlist().setEnabled(Toggle.State(enable = false))
        netPRemoteFeature.self().setEnabled(Toggle.State(enable = false))
        netPRemoteFeature.waitlistBetaActive().setEnabled(Toggle.State(enable = false))

        assertEquals(NetPWaitlistScreenNoParams, networkProtectionWaitlist.getScreenForCurrentState())
    }

    @Test
    fun whenGetScreenForStateNotTreatedAndInternalThenReturnWaitlistScreen() = runTest {
        netPRemoteFeature.waitlist().setEnabled(Toggle.State(enable = false))
        netPRemoteFeature.self().setEnabled(Toggle.State(enable = false))
        whenever(appBuildConfig.flavor).thenReturn(INTERNAL)

        assertEquals(NetPWaitlistScreenNoParams, networkProtectionWaitlist.getScreenForCurrentState())
    }

    @Test
    fun whenGetScreenForStateNotTreatedAndWaitlistBetaFinishedAndInternalThenReturnWaitlistScreen() = runTest {
        netPRemoteFeature.waitlist().setEnabled(Toggle.State(enable = false))
        netPRemoteFeature.self().setEnabled(Toggle.State(enable = false))
        netPRemoteFeature.waitlistBetaActive().setEnabled(Toggle.State(enable = false))
        whenever(appBuildConfig.flavor).thenReturn(INTERNAL)

        assertEquals(NetPWaitlistScreenNoParams, networkProtectionWaitlist.getScreenForCurrentState())
    }

    @Test
    fun whenGetScreenForStateSubFeatureDisabledAndInternalThenReturnWaitlistScreen() = runTest {
        netPRemoteFeature.waitlist().setEnabled(Toggle.State(enable = false))
        netPRemoteFeature.self().setEnabled(Toggle.State(enable = true))
        whenever(appBuildConfig.flavor).thenReturn(INTERNAL)

        assertEquals(NetPWaitlistScreenNoParams, networkProtectionWaitlist.getScreenForCurrentState())
    }

    @Test
    fun whenGetScreenForStateSubFeatureDisabledAndWaitlistBetaFinishedAndInternalThenReturnWaitlistScreen() = runTest {
        netPRemoteFeature.waitlist().setEnabled(Toggle.State(enable = false))
        netPRemoteFeature.self().setEnabled(Toggle.State(enable = true))
        netPRemoteFeature.waitlistBetaActive().setEnabled(Toggle.State(enable = false))
        whenever(appBuildConfig.flavor).thenReturn(INTERNAL)

        assertEquals(NetPWaitlistScreenNoParams, networkProtectionWaitlist.getScreenForCurrentState())
    }

    @Test
    fun whenGetScreenForStateFeatureDisabledAndInternalThenReturnWaitlistScreen() = runTest {
        netPRemoteFeature.waitlist().setEnabled(Toggle.State(enable = true))
        netPRemoteFeature.self().setEnabled(Toggle.State(enable = false))
        whenever(appBuildConfig.flavor).thenReturn(INTERNAL)

        assertEquals(NetPWaitlistScreenNoParams, networkProtectionWaitlist.getScreenForCurrentState())
    }

    @Test
    fun whenGetScreenForStateFeatureDisabledandWaitlistBetaFinishedAndInternalThenReturnWaitlistScreen() = runTest {
        netPRemoteFeature.waitlist().setEnabled(Toggle.State(enable = true))
        netPRemoteFeature.self().setEnabled(Toggle.State(enable = false))
        netPRemoteFeature.waitlistBetaActive().setEnabled(Toggle.State(enable = false))
        whenever(appBuildConfig.flavor).thenReturn(INTERNAL)

        assertEquals(NetPWaitlistScreenNoParams, networkProtectionWaitlist.getScreenForCurrentState())
    }

    @Test
    fun whenGetScreenForStateAndTreatedThenReturnWaitlistScreen() = runTest {
        netPRemoteFeature.waitlist().setEnabled(Toggle.State(enable = true))
        netPRemoteFeature.self().setEnabled(Toggle.State(enable = true))

        assertEquals(NetPWaitlistScreenNoParams, networkProtectionWaitlist.getScreenForCurrentState())
    }

    @Test
    fun whenGetScreenForStateAndWaitlistBetaFinishedAndTreatedThenReturnWaitlistScreen() = runTest {
        netPRemoteFeature.waitlist().setEnabled(Toggle.State(enable = true))
        netPRemoteFeature.self().setEnabled(Toggle.State(enable = true))
        netPRemoteFeature.waitlistBetaActive().setEnabled(Toggle.State(enable = false))

        assertEquals(NetPWaitlistScreenNoParams, networkProtectionWaitlist.getScreenForCurrentState())
    }

    @Test
    fun whenGetScreenForStateWithAuthTokenAndTreatedAndTermsNotAcceptedAndOnboardedThenReturnInviteCodeScreen() = runTest {
        whenever(networkProtectionState.isOnboarded()).thenReturn(true)

        netPRemoteFeature.waitlist().setEnabled(Toggle.State(enable = true))
        netPRemoteFeature.self().setEnabled(Toggle.State(enable = true))
        netPWaitlistRepository.setAuthenticationToken("token")

        assertEquals(NetworkProtectionManagementScreenNoParams, networkProtectionWaitlist.getScreenForCurrentState())
    }

    @Test
    fun whenGetScreenForStateWithAuthTokenAndWaitlistBetaFinishedAndTreatedAndTermsNotAcceptedAndOnboardedThenReturnInviteCodeScreen() = runTest {
        whenever(networkProtectionState.isOnboarded()).thenReturn(true)

        netPRemoteFeature.waitlist().setEnabled(Toggle.State(enable = true))
        netPRemoteFeature.self().setEnabled(Toggle.State(enable = true))
        netPRemoteFeature.waitlistBetaActive().setEnabled(Toggle.State(enable = false))
        netPWaitlistRepository.setAuthenticationToken("token")

        assertEquals(NetPWaitlistScreenNoParams, networkProtectionWaitlist.getScreenForCurrentState())
    }

    @Test
    fun whenGetScreenForStateWithAuthTokenAndTreatedAndTermsNotAcceptedAndNotOnboardedThenReturnInviteCodeScreen() = runTest {
        whenever(networkProtectionState.isOnboarded()).thenReturn(false)

        netPRemoteFeature.waitlist().setEnabled(Toggle.State(enable = true))
        netPRemoteFeature.self().setEnabled(Toggle.State(enable = true))
        netPWaitlistRepository.setAuthenticationToken("token")

        assertEquals(NetPWaitlistInvitedScreenNoParams, networkProtectionWaitlist.getScreenForCurrentState())
    }

    @Test
    fun whenGetScreenForStateWithAuthTokenAndWaitlistBetaFinishedAndTreatedAndTermsNotAcceptedAndNotOnboardedThenReturnInviteCodeScreen() = runTest {
        whenever(networkProtectionState.isOnboarded()).thenReturn(false)

        netPRemoteFeature.waitlist().setEnabled(Toggle.State(enable = true))
        netPRemoteFeature.self().setEnabled(Toggle.State(enable = true))
        netPRemoteFeature.waitlistBetaActive().setEnabled(Toggle.State(enable = false))
        netPWaitlistRepository.setAuthenticationToken("token")

        assertEquals(NetPWaitlistScreenNoParams, networkProtectionWaitlist.getScreenForCurrentState())
    }

    @Test
    fun whenGetScreenForStateWithAuthTokenAndTreatedAndTermsAcceptedAndOnboardedThenReturnNetPManagementScreen() = runTest {
        whenever(networkProtectionState.isOnboarded()).thenReturn(true)

        netPRemoteFeature.waitlist().setEnabled(Toggle.State(enable = true))
        netPRemoteFeature.self().setEnabled(Toggle.State(enable = true))
        netPWaitlistRepository.setAuthenticationToken("token")
        netPWaitlistRepository.acceptWaitlistTerms()

        assertEquals(NetworkProtectionManagementScreenNoParams, networkProtectionWaitlist.getScreenForCurrentState())
    }

    @Test
    fun whenGetScreenForStateWithAuthTokenAndWaitlistBetaFinishedAndTreatedAndTermsAcceptedAndOnboardedThenReturnNetPManagementScreen() = runTest {
        whenever(networkProtectionState.isOnboarded()).thenReturn(true)

        netPRemoteFeature.waitlist().setEnabled(Toggle.State(enable = true))
        netPRemoteFeature.self().setEnabled(Toggle.State(enable = true))
        netPRemoteFeature.waitlistBetaActive().setEnabled(Toggle.State(enable = false))
        netPWaitlistRepository.setAuthenticationToken("token")
        netPWaitlistRepository.acceptWaitlistTerms()

        assertEquals(NetPWaitlistScreenNoParams, networkProtectionWaitlist.getScreenForCurrentState())
    }

    @Test
    fun whenGetScreenForStateWithAuthTokenAndTreatedAndTermsAcceptedAndNotOnboardedThenReturnNetPManagementScreen() = runTest {
        whenever(networkProtectionState.isOnboarded()).thenReturn(false)

        netPRemoteFeature.waitlist().setEnabled(Toggle.State(enable = true))
        netPRemoteFeature.self().setEnabled(Toggle.State(enable = true))
        netPWaitlistRepository.setAuthenticationToken("token")
        netPWaitlistRepository.acceptWaitlistTerms()

        assertEquals(NetworkProtectionManagementScreenNoParams, networkProtectionWaitlist.getScreenForCurrentState())
    }

    @Test
    fun whenGetScreenForStateWithAuthTokenAndWaitlistBetaFinishedAndTreatedAndTermsAcceptedAndNotOnboardedThenReturnNetPManagementScreen() = runTest {
        whenever(networkProtectionState.isOnboarded()).thenReturn(false)

        netPRemoteFeature.waitlist().setEnabled(Toggle.State(enable = true))
        netPRemoteFeature.self().setEnabled(Toggle.State(enable = true))
        netPRemoteFeature.waitlistBetaActive().setEnabled(Toggle.State(enable = false))
        netPWaitlistRepository.setAuthenticationToken("token")
        netPWaitlistRepository.acceptWaitlistTerms()

        assertEquals(NetPWaitlistScreenNoParams, networkProtectionWaitlist.getScreenForCurrentState())
    }

    @Test
    fun whenGetScreenForStateWithWaitlistTokenThenReturnInviteCodeScreen() = runTest {
        netPRemoteFeature.waitlist().setEnabled(Toggle.State(enable = true))
        netPRemoteFeature.self().setEnabled(Toggle.State(enable = true))
        netPWaitlistRepository.setWaitlistToken("token")

        assertEquals(NetPWaitlistScreenNoParams, networkProtectionWaitlist.getScreenForCurrentState())
    }

    @Test
    fun whenGetScreenForStateWithWaitlistTokenAndWaitlistBetaFinishedThenReturnInviteCodeScreen() = runTest {
        netPRemoteFeature.waitlist().setEnabled(Toggle.State(enable = true))
        netPRemoteFeature.self().setEnabled(Toggle.State(enable = true))
        netPRemoteFeature.waitlistBetaActive().setEnabled(Toggle.State(enable = false))
        netPWaitlistRepository.setWaitlistToken("token")

        assertEquals(NetPWaitlistScreenNoParams, networkProtectionWaitlist.getScreenForCurrentState())
    }
}
