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

package com.duckduckgo.referral.impl.messaging

import android.content.Context
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.referral.api.AppReferrer
import com.squareup.anvil.annotations.ContributesBinding
import dagger.SingleInstanceIn
import java.util.concurrent.TimeUnit
import javax.inject.Inject

/**
 * Resolves the install-origin variant for a given experiment campaign, for the SERP install-origin bridge.
 */
interface InstallOriginVariantResolver {
    fun getVariant(campaign: String): String?
}

@ContributesBinding(AppScope::class)
@SingleInstanceIn(AppScope::class)
class RealInstallOriginVariantResolver @Inject constructor(
    private val context: Context,
    private val appReferrer: AppReferrer,
) : InstallOriginVariantResolver {

    override fun getVariant(campaign: String): String? {
        val origin = appReferrer.getOriginAttributeCampaign() ?: return null
        val segments = origin.split(SEGMENT_DELIMITER, limit = EXPECTED_SEGMENT_COUNT)
        if (segments.size != EXPECTED_SEGMENT_COUNT) return null

        val (_, entry, _, originCampaign, content) = segments
        if (entry != HOME_ENTRY) return null
        if (originCampaign != campaign) return null
        if (daysSinceInstall() > MAX_INSTALL_AGE_DAYS) return null

        return content
    }

    private fun daysSinceInstall(): Long {
        val firstInstallTime = context.packageManager.getPackageInfo(context.packageName, 0).firstInstallTime
        return TimeUnit.MILLISECONDS.toDays(System.currentTimeMillis() - firstInstallTime)
    }

    companion object {
        private const val SEGMENT_DELIMITER = "_"
        private const val EXPECTED_SEGMENT_COUNT = 5
        private const val HOME_ENTRY = "home"
        private const val MAX_INSTALL_AGE_DAYS = 28
    }
}
