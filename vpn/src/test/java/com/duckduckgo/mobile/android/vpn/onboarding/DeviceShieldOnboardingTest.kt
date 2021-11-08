/*
 * Copyright (c) 2021 DuckDuckGo
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

package com.duckduckgo.mobile.android.vpn.onboarding

import android.content.Context
import android.content.SharedPreferences
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.duckduckgo.mobile.android.vpn.ui.onboarding.DeviceShieldOnboardingImpl
import com.duckduckgo.mobile.android.vpn.ui.onboarding.DeviceShieldOnboardingStore
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.whenever
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.ArgumentMatchers.*
import org.mockito.Mock
import org.mockito.Mockito.mock
import org.mockito.MockitoAnnotations

@RunWith(AndroidJUnit4::class)
class DeviceShieldOnboardingTest {
    @Mock
    lateinit var sharedPreferences: SharedPreferences

    @Mock
    lateinit var mockContext: Context

    lateinit var deviceShieldOnboardingStore: DeviceShieldOnboardingStore

    @Before
    fun setup() {
        MockitoAnnotations.initMocks(this)

        whenever(mockContext.getSharedPreferences(anyString(), eq(Context.MODE_PRIVATE))).thenReturn(sharedPreferences)

        deviceShieldOnboardingStore = DeviceShieldOnboardingImpl(mockContext)
    }

    @Test
    fun whenOnboardingDidShowThenStoreItDidShow() {
        val editor = mock(SharedPreferences.Editor::class.java)

        whenever(sharedPreferences.edit()).thenReturn(editor)
        deviceShieldOnboardingStore.onboardingDidShow()

        verify(editor).putBoolean("KEY_DEVICE_SHIELD_ONBOARDING_LAUNCHED", true)
    }

    @Test
    fun whenOnboardingDidNotShowThenStoreItDidNotShow() {
        val editor = mock(SharedPreferences.Editor::class.java)

        whenever(sharedPreferences.edit()).thenReturn(editor)
        deviceShieldOnboardingStore.onboardingDidNotShow()

        verify(editor).putBoolean("KEY_DEVICE_SHIELD_ONBOARDING_LAUNCHED", false)
    }
}
