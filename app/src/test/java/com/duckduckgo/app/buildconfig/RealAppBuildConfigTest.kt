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

package com.duckduckgo.app.buildconfig

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.duckduckgo.app.browser.BuildConfig
import com.duckduckgo.app.install.ReinstallDetector
import com.duckduckgo.experiments.api.VariantManager
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

@RunWith(AndroidJUnit4::class)
class RealAppBuildConfigTest {

    private val reinstallDetector: ReinstallDetector = mock()

    private val testee = RealAppBuildConfig(
        variantManager = lazy { mock<VariantManager>() },
        reinstallDetector = lazy { reinstallDetector },
        context = mock(),
    )

    @Test
    fun whenVersionNameThenReturnsBuildConfigVersionNameWithoutSuffix() {
        assertEquals(BuildConfig.VERSION_NAME, testee.versionName)
    }

    @Test
    fun whenIsAppReinstallThenAnswerComesFromReinstallDetector() = runTest {
        whenever(reinstallDetector.isAppReinstall()).thenReturn(true)

        assertTrue(testee.isAppReinstall())
    }
}
