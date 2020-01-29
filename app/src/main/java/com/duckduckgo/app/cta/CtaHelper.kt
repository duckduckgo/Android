/*
 * Copyright (c) 2020 DuckDuckGo
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

package com.duckduckgo.app.cta

import android.content.Context
import com.duckduckgo.app.browser.R
import com.duckduckgo.app.cta.ui.Cta
import com.duckduckgo.app.cta.ui.DaxBubbleCta
import com.duckduckgo.app.cta.ui.DaxDialogCta
import com.duckduckgo.app.cta.ui.HomePanelCta
import com.duckduckgo.app.global.install.AppInstallStore
import com.duckduckgo.app.global.install.daysInstalled
import com.duckduckgo.app.onboarding.store.OnboardingStore
import com.duckduckgo.app.trackerdetection.model.TrackingEvent
import javax.inject.Inject

interface CtaHelper {
    fun addCtaToHistory(newCta: String): String

    fun getTrackersBlockedCtaText(context: Context, trackers: List<TrackingEvent>): String

    fun getNetworkPercentage(network: String): String?

    fun isFromSameNetworkDomain(host: String): Boolean

    fun canSendPixel(cta: Cta): Boolean
}

class CtaDaxHelper @Inject constructor(val onboardingStore: OnboardingStore, val appInstallStore: AppInstallStore) : CtaHelper {

    override fun canSendPixel(cta: Cta): Boolean {
        val param = onboardingStore.onboardingDialogJourney?.split("-").orEmpty().toMutableList()
        return when (cta) {
            is HomePanelCta -> true
            is DaxDialogCta -> !(param.isNotEmpty() && param.last().contains(cta.ctaPixelParam))
            is DaxBubbleCta -> !(param.isNotEmpty() && param.last().contains(cta.ctaPixelParam))
            else -> false
        }
    }

    override fun addCtaToHistory(newCta: String): String {
        val param = onboardingStore.onboardingDialogJourney?.split("-").orEmpty().toMutableList()
        val daysInstalled = minOf(appInstallStore.daysInstalled().toInt(), MAX_DAYS_ALLOWED)
        param.add("$newCta:${daysInstalled}")
        val finalParam = param.joinToString("-")
        onboardingStore.onboardingDialogJourney = finalParam
        return finalParam
    }

    override fun getTrackersBlockedCtaText(context: Context, trackers: List<TrackingEvent>): String {
        val trackersFiltered = trackers.asSequence()
            .filter { it.entity?.isMajor == true }
            .map { it.entity?.displayName }
            .filterNotNull()
            .distinct()
            .take(MAX_TRACKERS_SHOWS)
            .toList()

        val trackersText = trackersFiltered.joinToString(", ")
        val size = trackers.size - trackersFiltered.size
        val quantityString =
            if (size == 0) {
                context.resources.getString(R.string.daxTrackersBlockedCtaZeroText)
            } else {
                context.resources.getQuantityString(R.plurals.daxTrackersBlockedCtaText, size, size)
            }
        return "<b>$trackersText</b>$quantityString"
    }

    override fun getNetworkPercentage(network: String): String? = NETWORK_PROPERTY_PERCENTAGES[network]

    override fun isFromSameNetworkDomain(host: String): Boolean = MAIN_TRACKER_DOMAINS.any { host.contains(it) }


    companion object {
        private const val MAX_DAYS_ALLOWED = 3
        private const val MAX_TRACKERS_SHOWS = 2
        private val MAIN_TRACKER_DOMAINS = listOf("facebook", "google")
        val NETWORK_PROPERTY_PERCENTAGES = mapOf(Pair("Google", "90%"), Pair("Facebook", "40%"))
    }
}