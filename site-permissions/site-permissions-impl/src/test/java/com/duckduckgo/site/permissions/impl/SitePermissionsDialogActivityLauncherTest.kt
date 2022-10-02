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

package com.duckduckgo.site.permissions.impl

import com.duckduckgo.app.statistics.pixels.Pixel
import com.nhaarman.mockitokotlin2.mock
import org.junit.Before

class SitePermissionsDialogActivityLauncherTest {

    private lateinit var testee: SitePermissionsDialogActivityLauncher

    private val mockSystemPermissionsHelper: SystemPermissionsHelper = mock()
    private val mockSitePermissionsRepository: SitePermissionsRepository = mock()
    private val mockPixel: Pixel = mock()

    @Before
    fun before() {
        testee = SitePermissionsDialogActivityLauncher(mockSystemPermissionsHelper, mockSitePermissionsRepository, mockPixel)
    }
}
