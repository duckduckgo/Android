/*
 * Copyright (c) 2024 DuckDuckGo
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

package com.duckduckgo.verifiedinstallation.installsource

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

@RunWith(AndroidJUnit4::class)
class VerificationCheckPlayStoreInstallImplTest {

    private val installSourceExtractor: InstallSourceExtractor = mock()
    private val testee = VerificationCheckPlayStoreInstallImpl(installSourceExtractor)

    @Test
    fun whenInstallSourceMatchesPlayStorePackageThenIdentifiedAsInstalledFromPlayStore() {
        whenever(installSourceExtractor.extract()).thenReturn("com.android.vending")
        assertTrue(testee.installedFromPlayStore())
    }

    @Test
    fun whenInstallSourceDoesNotMatchPlayStorePackageThenNotIdentifiedAsInstalledFromPlayStore() {
        whenever(installSourceExtractor.extract()).thenReturn("com.random.app")
        assertFalse(testee.installedFromPlayStore())
    }
}
