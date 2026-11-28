/*
 * Copyright (c) 2026 DuckDuckGo
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

package com.duckduckgo.autofill.impl.ui.credential.management.viewing

import android.os.Bundle
import android.provider.Settings.ACTION_BIOMETRIC_ENROLL
import android.widget.FrameLayout
import androidx.appcompat.app.AppCompatActivity
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.duckduckgo.appbuildconfig.api.AppBuildConfig
import com.duckduckgo.autofill.impl.R
import com.duckduckgo.common.utils.edgetoedge.EdgeToEdgeHandler
import com.duckduckgo.common.utils.edgetoedge.EdgeToEdgeProvider
import dagger.android.AndroidInjector
import dagger.android.HasAndroidInjector
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.robolectric.Robolectric
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import com.duckduckgo.mobile.android.R as CommonR

@RunWith(AndroidJUnit4::class)
@Config(sdk = [30])
class AutofillManagementDisabledModeTest {

    private val appBuildConfig: AppBuildConfig = mock()
    private val edgeToEdgeProvider: EdgeToEdgeProvider = mock()

    @Test
    fun whenSetUpInSettingsClickedThenBiometricEnrollmentIsLaunchedAndActivityRemainsOpen() {
        whenever(appBuildConfig.manufacturer).thenReturn("Google")
        whenever(appBuildConfig.sdkInt).thenReturn(30)
        whenever(edgeToEdgeProvider.isEnabled(any())).thenReturn(false)
        val activity = Robolectric.buildActivity(TestActivity::class.java).setup().get()
        val fragment = AutofillManagementDisabledMode().apply {
            appBuildConfig = this@AutofillManagementDisabledModeTest.appBuildConfig
            edgeToEdgeProvider = this@AutofillManagementDisabledModeTest.edgeToEdgeProvider
            edgeToEdgeHandler = EdgeToEdgeHandler()
        }
        activity.supportFragmentManager.beginTransaction()
            .add(TestActivity.CONTAINER_ID, fragment)
            .commitNow()

        fragment.requireView().findViewById<android.view.View>(R.id.disabled_cta).performClick()

        assertEquals(ACTION_BIOMETRIC_ENROLL, shadowOf(activity).nextStartedActivity.action)
        assertFalse(activity.isFinishing)
    }
}

class TestActivity : AppCompatActivity(), HasAndroidInjector {

    override fun onCreate(savedInstanceState: Bundle?) {
        setTheme(CommonR.style.Theme_DuckDuckGo_Light)
        super.onCreate(savedInstanceState)
        setContentView(FrameLayout(this).apply { id = CONTAINER_ID })
    }

    override fun androidInjector(): AndroidInjector<Any> = AndroidInjector { }

    companion object {
        const val CONTAINER_ID = 1
    }
}
