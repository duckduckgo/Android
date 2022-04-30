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

package com.duckduckgo.privacy.config.impl.features.drm

import android.net.Uri
import android.webkit.PermissionRequest
import androidx.core.net.toUri
import com.duckduckgo.app.global.baseHost
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.feature.toggles.api.FeatureToggle
import com.duckduckgo.privacy.config.api.Drm
import com.duckduckgo.privacy.config.api.PrivacyFeatureName
import com.duckduckgo.privacy.config.store.features.drm.DrmRepository
import com.squareup.anvil.annotations.ContributesBinding
import dagger.SingleInstanceIn
import javax.inject.Inject

@ContributesBinding(AppScope::class)
@SingleInstanceIn(AppScope::class)
class RealDrm @Inject constructor(
    private val featureToggle: FeatureToggle,
    private val drmRepository: DrmRepository
) : Drm {

    override fun getDrmPermissionsForRequest(
        url: String,
        resources: Array<String>
    ): Array<String> {
        val perms = mutableSetOf<String>()
        if (shouldEnableDrmForUri(url.toUri())) {
            resources.find { (it == PermissionRequest.RESOURCE_PROTECTED_MEDIA_ID) }?.let {
                perms.add(PermissionRequest.RESOURCE_PROTECTED_MEDIA_ID)
            }
        }
        return perms.toTypedArray()
    }

    private fun shouldEnableDrmForUri(uri: Uri): Boolean {
        val isFeatureEnabled = featureToggle.isFeatureEnabled(PrivacyFeatureName.DrmFeatureName, defaultValue = true)
        return isFeatureEnabled && domainsThatAllowDrm(uri.baseHost)
    }

    private fun domainsThatAllowDrm(host: String?): Boolean {
        return drmRepository.exceptions.firstOrNull { it.domain == host } != null
    }
}
