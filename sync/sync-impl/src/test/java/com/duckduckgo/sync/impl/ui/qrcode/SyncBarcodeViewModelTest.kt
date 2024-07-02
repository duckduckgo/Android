/*
 * Copyright (c) 2023 DuckDuckGo
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

package com.duckduckgo.sync.impl.ui.qrcode

import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.cash.turbine.test
import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.sync.impl.ui.qrcode.SquareDecoratedBarcodeViewModel.Command.CheckCameraAvailable
import com.duckduckgo.sync.impl.ui.qrcode.SquareDecoratedBarcodeViewModel.Command.CheckPermissions
import com.duckduckgo.sync.impl.ui.qrcode.SquareDecoratedBarcodeViewModel.Command.OpenSettings
import com.duckduckgo.sync.impl.ui.qrcode.SquareDecoratedBarcodeViewModel.Command.RequestPermissions
import com.duckduckgo.sync.impl.ui.qrcode.SquareDecoratedBarcodeViewModel.ViewState.CameraUnavailable
import com.duckduckgo.sync.impl.ui.qrcode.SquareDecoratedBarcodeViewModel.ViewState.PermissionsGranted
import com.duckduckgo.sync.impl.ui.qrcode.SquareDecoratedBarcodeViewModel.ViewState.PermissionsNotGranted
import com.duckduckgo.sync.impl.ui.qrcode.SquareDecoratedBarcodeViewModel.ViewState.Unknown
import kotlinx.coroutines.test.runTest
import org.junit.Assert
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SyncBarcodeViewModelTest {

    @get:Rule
    var coroutineRule = CoroutineTestRule()

    private val permissionDeniedWrapper = PermissionDeniedWrapper()
    private val fakeLifecycleOwner = object : LifecycleOwner {
        override val lifecycle: Lifecycle
            get() = TODO("Not yet implemented")
    }

    private val testee: SquareDecoratedBarcodeViewModel by lazy {
        SquareDecoratedBarcodeViewModel(permissionDeniedWrapper)
    }

    @Test
    fun whenHandleCameraAvailabilityWithCameraUnavailableThenViewStateCameraUnavailable() = runTest {
        testee.handleCameraAvailability(false)

        testee.viewState.test {
            val state = awaitItem()
            Assert.assertEquals(CameraUnavailable, state)
        }
    }

    @Test
    fun whenHandlePermissionsWithPermissionGrantedThenViewStatePermissionsGranted() = runTest {
        testee.handlePermissions(true)

        testee.viewState.test {
            val state = awaitItem()
            Assert.assertEquals(PermissionsGranted, state)
        }
    }

    @Test
    fun whenHandlePermissionsWithPermissionNotGrantedAndPermissionNotDeniedYetThenCommandRequestPermissions() = runTest {
        permissionDeniedWrapper.permissionAlreadyDenied = false
        testee.handlePermissions(false)

        testee.commands().test {
            val state = awaitItem()
            Assert.assertEquals(RequestPermissions, state)
        }
    }

    @Test
    fun whenHandlePermissionsWithPermissionNotGrantedThenViewStateUnknown() = runTest {
        testee.handlePermissions(false)

        testee.viewState.test {
            val state = awaitItem()
            Assert.assertEquals(Unknown, state)
        }
    }

    @Test
    fun whenHandlePermissionsWithPermissionNotGrantedAndPermissionAlreadyDeniedThenViewStatePermissionsNotGranted() = runTest {
        permissionDeniedWrapper.permissionAlreadyDenied = true
        testee.handlePermissions(false)

        testee.viewState.test {
            val state = awaitItem()
            Assert.assertEquals(PermissionsNotGranted, state)
        }
    }

    @Test
    fun whenGoToSettingsThenCommandOpenSettings() = runTest {
        testee.goToSettings()

        testee.commands().test {
            val state = awaitItem()
            Assert.assertEquals(OpenSettings, state)
        }
    }

    @Test
    fun whenGoToSettingsThenViewStateUnknown() = runTest {
        testee.goToSettings()

        testee.viewState.test {
            val state = awaitItem()
            Assert.assertEquals(Unknown, state)
        }
    }

    @Test
    fun whenOnResumeThenCommandCheckCameraAvailable() = runTest {
        testee.onResume(fakeLifecycleOwner)

        testee.commands().test {
            val state = awaitItem()
            Assert.assertEquals(CheckCameraAvailable, state)
        }
    }

    @Test
    fun whenOnResumeThenViewStateUnknown() = runTest {
        testee.onResume(fakeLifecycleOwner)

        testee.viewState.test {
            val state = awaitItem()
            Assert.assertEquals(Unknown, state)
        }
    }

    @Test
    fun whenHandleCameraAvailabilityWithCameraAvailableThenCommandCheckPermissions() = runTest {
        testee.handleCameraAvailability(true)

        testee.commands().test {
            val state = awaitItem()
            Assert.assertEquals(CheckPermissions, state)
        }
    }

    @Test
    fun whenHandleCameraAvailabilityWithCameraAvailableThenViewStateUnknown() = runTest {
        testee.handleCameraAvailability(true)

        testee.viewState.test {
            val state = awaitItem()
            Assert.assertEquals(Unknown, state)
        }
    }

    @Test
    fun whenHandlePermissionsWithPermissionNotGrantedAndPermissionNotDeniedYetThenPermissionAlreadyDeniedTrue() = runTest {
        permissionDeniedWrapper.permissionAlreadyDenied = false

        testee.handlePermissions(false)

        Assert.assertTrue(permissionDeniedWrapper.permissionAlreadyDenied)
    }
}
