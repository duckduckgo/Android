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

package com.duckduckgo.autofill.impl.engagement

import com.duckduckgo.app.di.AppCoroutineScope
import com.duckduckgo.app.statistics.pixels.Pixel
import com.duckduckgo.app.statistics.pixels.Pixel.PixelType.Unique
import com.duckduckgo.autofill.impl.PasswordStoreEventListener
import com.duckduckgo.autofill.impl.pixel.AutofillPixelNames
import com.duckduckgo.browser.api.UserBrowserProperties
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.di.scopes.AppScope
import com.squareup.anvil.annotations.ContributesMultibinding
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import logcat.LogPriority.VERBOSE
import logcat.logcat

@ContributesMultibinding(AppScope::class)
class EngagementPasswordAddedListener @Inject constructor(
    private val userBrowserProperties: UserBrowserProperties,
    @AppCoroutineScope private val appCoroutineScope: CoroutineScope,
    private val dispatchers: DispatcherProvider,
    private val pixel: Pixel,
) : PasswordStoreEventListener {

    // keep in-memory state to avoid sending the pixel multiple times if called repeatedly in a short time
    private var credentialAdded = false

    @Synchronized
    override fun onCredentialAdded(newCredentialIds: List<Long>) {
        if (credentialAdded) return

        credentialAdded = true

        appCoroutineScope.launch(dispatchers.io()) {
            val daysInstalled = userBrowserProperties.daysSinceInstalled()
            logcat(VERBOSE) { "onCredentialAdded. daysInstalled: $daysInstalled" }
            if (daysInstalled < 7) {
                pixel.fire(AutofillPixelNames.AUTOFILL_ENGAGEMENT_ONBOARDED_USER, type = Unique())
            }
        }
    }
}
