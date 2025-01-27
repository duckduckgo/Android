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

package com.duckduckgo.malicioussiteprotection.impl

import android.net.Uri
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.malicioussiteprotection.api.MaliciousSiteProtection
import com.duckduckgo.malicioussiteprotection.api.MaliciousSiteProtection.IsMaliciousResult
import com.squareup.anvil.annotations.ContributesBinding
import javax.inject.Inject
import timber.log.Timber

@ContributesBinding(AppScope::class, MaliciousSiteProtection::class)
class RealMaliciousSiteProtection @Inject constructor(
    maliciousSiteProtectionRCFeature: MaliciousSiteProtectionRCFeature,
) : MaliciousSiteProtection {

    override suspend fun isMalicious(url: Uri, confirmationCallback: (isMalicious: Boolean) -> Unit): IsMaliciousResult {
        Timber.tag("MaliciousSiteProtection").d("isMalicious $url")
        // TODO (cbarreiro): Implement the logic to check if the URL is malicious
        return IsMaliciousResult.SAFE
    }
}
