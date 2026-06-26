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

package com.duckduckgo.app.browser

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.duckduckgo.app.browser.mode.ExternalUrl
import com.duckduckgo.app.browser.mode.InAppNavigation
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BrowserActivityIntentFactoryTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun whenExternalLaunchSourceThenIntentStampsRequiresRegularMode() {
        val intent = BrowserActivity.intent(context, launchSource = ExternalUrl)

        assertTrue(intent.getBooleanExtra(BrowserActivity.LAUNCH_REQUIRES_REGULAR_MODE, false))
    }

    @Test
    fun whenInternalLaunchSourceThenIntentDoesNotRequireRegularMode() {
        val intent = BrowserActivity.intent(context, launchSource = InAppNavigation)

        assertFalse(intent.getBooleanExtra(BrowserActivity.LAUNCH_REQUIRES_REGULAR_MODE, true))
    }

    @Test
    fun whenFireOnEntryIntentBuiltByFactoryThenIsTrustedFireOnEntryIntent() {
        val intent = BrowserActivity.intent(context, launchSource = InAppNavigation, performFireOnEntry = true)

        assertTrue(BrowserActivity.isTrustedFireOnEntryIntent(context, intent))
    }

    @Test
    fun whenIntentHasFireOnEntryExtraButNoVerificationSenderThenNotTrusted() {
        // Replicates a third-party app forging the public extra without a DDG-owned verification sender.
        val forgedIntent = BrowserActivity.intent(context, launchSource = InAppNavigation)
            .putExtra(BrowserActivity.PERFORM_FIRE_ON_ENTRY_EXTRA, true)

        assertFalse(BrowserActivity.isTrustedFireOnEntryIntent(context, forgedIntent))
    }

    @Test
    fun whenIntentDoesNotRequestFireOnEntryThenNotTrusted() {
        val intent = BrowserActivity.intent(context, launchSource = InAppNavigation)

        assertFalse(BrowserActivity.isTrustedFireOnEntryIntent(context, intent))
    }
}
