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

package com.duckduckgo.app.dispatchers

import androidx.core.net.toUri
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LaunchingAppPackageTest {

    @Test
    fun whenReferrerIsAnotherAppThenItsPackageReturned() {
        assertEquals("com.example.origin", launchingAppPackage("android-app://com.example.origin".toUri(), OWN_PACKAGE))
    }

    @Test
    fun whenReferrerIsOurOwnPackageThenIgnored() {
        assertNull(launchingAppPackage("android-app://$OWN_PACKAGE".toUri(), OWN_PACKAGE))
    }

    @Test
    fun whenReferrerIsAWebUrlThenIgnored() {
        assertNull(launchingAppPackage("https://example.com".toUri(), OWN_PACKAGE))
    }

    @Test
    fun whenNoReferrerThenNull() {
        assertNull(launchingAppPackage(null, OWN_PACKAGE))
    }

    private companion object {
        const val OWN_PACKAGE = "com.duckduckgo.mobile.android"
    }
}
