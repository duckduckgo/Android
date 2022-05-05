/*
 * Copyright (c) 2022 DuckDuckGo
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

package com.duckduckgo.app.onboarding.ui.page.vpn

import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.cash.turbine.test
import kotlinx.coroutines.test.runTest
import com.duckduckgo.app.CoroutineTestRule
import com.duckduckgo.app.onboarding.ui.page.vpn.VpnPagesViewModel.Action.ContinueToVpnExplanation
import com.duckduckgo.app.onboarding.ui.page.vpn.VpnPagesViewModel.Action.ContinueVpnConflictDialog
import com.duckduckgo.app.onboarding.ui.page.vpn.VpnPagesViewModel.Action.DismissVpnConflictDialog
import com.duckduckgo.app.onboarding.ui.page.vpn.VpnPagesViewModel.Action.EnableVPN
import com.duckduckgo.app.onboarding.ui.page.vpn.VpnPagesViewModel.Action.IntroPageBecameVisible
import com.duckduckgo.app.onboarding.ui.page.vpn.VpnPagesViewModel.Action.LearnMore
import com.duckduckgo.app.onboarding.ui.page.vpn.VpnPagesViewModel.Action.LeaveVpnIntro
import com.duckduckgo.app.onboarding.ui.page.vpn.VpnPagesViewModel.Action.LeaveVpnPermission
import com.duckduckgo.app.onboarding.ui.page.vpn.VpnPagesViewModel.Action.OpenSettingVpnConflictDialog
import com.duckduckgo.app.onboarding.ui.page.vpn.VpnPagesViewModel.Action.PermissionPageBecameVisible
import com.duckduckgo.app.onboarding.ui.page.vpn.VpnPagesViewModel.Action.VpnPermissionDenied
import com.duckduckgo.app.onboarding.ui.page.vpn.VpnPagesViewModel.Action.VpnPermissionGranted
import com.duckduckgo.app.onboarding.ui.page.vpn.VpnPagesViewModel.Action.VpnPermissionResult
import com.duckduckgo.app.onboarding.ui.page.vpn.VpnPagesViewModel.Command
import com.duckduckgo.app.pixels.AppPixelName
import com.duckduckgo.app.statistics.pixels.Pixel
import com.duckduckgo.mobile.android.vpn.network.VpnDetector
import com.duckduckgo.mobile.android.vpn.pixels.DeviceShieldPixels
import com.duckduckgo.mobile.android.vpn.ui.onboarding.VpnStore
import org.junit.Assert
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify

@RunWith(AndroidJUnit4::class)
class VpnPagesViewModelTest {

    @get:Rule
    @Suppress("unused")
    val coroutineRule = CoroutineTestRule()

    private val pixel = mock<Pixel>()
    private val vpnPixels = mock<DeviceShieldPixels>()
    private val vpnDetector = mock<VpnDetector>()
    private val vpnStore = mock<VpnStore>()

    private lateinit var testee: VpnPagesViewModel

    @Before
    fun setup() {
        testee = VpnPagesViewModel(
            pixel,
            vpnPixels,
            vpnDetector,
            vpnStore
        )
    }

    @Test
    fun whenIntroPageBecameVisibleThenPixelIsFired() = runTest {
        testee.onAction(IntroPageBecameVisible)

        verify(pixel).fire(AppPixelName.ONBOARDING_VPN_INTRO_SHOWN)
    }

    @Test
    fun whenPermissionPageBecameVisibleThenPixelIsFired() = runTest {
        testee.onAction(PermissionPageBecameVisible)

        verify(pixel).fire(AppPixelName.ONBOARDING_VPN_PERMISSION_SHOWN)
    }

    @Test
    fun whenContinueToVpnExplanationThenPixelIsFiredAndCommandIsSent() = runTest {
        testee.onAction(ContinueToVpnExplanation)

        verify(pixel).fire(AppPixelName.ONBOARDING_VPN_INTRO_CONTINUED)

        testee.commands().test {
            Assert.assertEquals(Command.ContinueToVpnExplanation, awaitItem())
        }
    }

    @Test
    fun whenCheckVpnPermissionThenCommandIsSent() = runTest {
        testee.onAction(EnableVPN)

        testee.commands().test {
            Assert.assertEquals(Command.CheckVPNPermission, awaitItem())
        }
    }
    @Test
    fun whenLeaveVpnIntroThenPixelIsFiredAndCommandIsSent() = runTest {
        testee.onAction(LeaveVpnIntro)

        verify(pixel).fire(AppPixelName.ONBOARDING_VPN_INTRO_SKIPPED)

        testee.commands().test {
            Assert.assertEquals(Command.LeaveVpnIntro, awaitItem())
        }
    }

    @Test
    fun whenLeaveVpnPermissionThenPixelIsFiredAndCommandIsSent() = runTest {
        testee.onAction(LeaveVpnPermission)

        verify(pixel).fire(AppPixelName.ONBOARDING_VPN_PERMISSION_SKIPPED)

        testee.commands().test {
            Assert.assertEquals(Command.LeaveVpnPermission, awaitItem())
        }
    }

    @Test
    fun whenLearnMoreThenCommandIsSent() = runTest {
        testee.onAction(LearnMore)

        testee.commands().test {
            Assert.assertEquals(Command.OpenVpnFAQ, awaitItem())
        }
    }

    @Test
    fun whenDismissVpnConflictDialogThenPixelIsFired() = runTest {
        testee.onAction(DismissVpnConflictDialog)

        verify(vpnPixels).didChooseToDismissVpnConflictDialog()
    }

    @Test
    fun whenOpenSettingVpnConflictDialogThenPixelIsFiredAndCommandIsSent() = runTest {
        testee.onAction(OpenSettingVpnConflictDialog)

        verify(vpnPixels).didChooseToOpenSettingsFromVpnConflictDialog()

        testee.commands().test {
            Assert.assertEquals(Command.OpenVpnSettings, awaitItem())
        }
    }

    @Test
    fun whenContinueVpnConflictDialogThenPixelIsFiredAndCommandIsSent() = runTest {
        testee.onAction(ContinueVpnConflictDialog)

        verify(vpnPixels).didChooseToContinueFromVpnConflictDialog()
        verify(pixel).fire(AppPixelName.ONBOARDING_VPN_PERMISSION_CONTINUED)
        verify(vpnStore).onboardingDidShow()
        verify(vpnPixels).enableFromDaxOnboarding()

        testee.commands().test {
            Assert.assertEquals(Command.StartVpn, awaitItem())
        }
    }

    @Test
    fun whenVpnPermissionGrantedThenPixelIsFiredAndCommandIsSent() = runTest {
        testee.onAction(VpnPermissionGranted)

        verify(pixel).fire(AppPixelName.ONBOARDING_VPN_PERMISSION_CONTINUED)
        verify(vpnStore).onboardingDidShow()
        verify(vpnPixels).enableFromDaxOnboarding()

        testee.commands().test {
            Assert.assertEquals(Command.StartVpn, awaitItem())
        }
    }

    @Test
    fun whenVpnPermissionDeniedThenPixelIsFiredAndCommandIsSent() = runTest {
        val permissionIntent = Intent()
        testee.onAction(VpnPermissionDenied(permissionIntent))

        verify(pixel).fire(AppPixelName.ONBOARDING_VPN_PERMISSION_LAUNCHED)

        testee.commands().test {
            Assert.assertEquals(Command.RequestVpnPermission(permissionIntent), awaitItem())
        }
    }

    @Test
    fun whenVpnPermissionResultGrantedThenCommandIsSent() = runTest {
        testee.onAction(VpnPermissionResult(AppCompatActivity.RESULT_OK))

        testee.commands().test {
            Assert.assertEquals(Command.StartVpn, awaitItem())
        }
    }

    @Test
    fun whenVpnPermissionResultDeniedBecauseAlwaysOnConflictThenAlawysOnCommandIsSent() = runTest {
        val permissionIntent = Intent()
        testee.onAction(VpnPermissionDenied(permissionIntent))
        testee.onAction(VpnPermissionResult(AppCompatActivity.RESULT_CANCELED))

        testee.commands().test {
            Assert.assertEquals(Command.ShowVpnAlwaysOnConflictDialog, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

}
