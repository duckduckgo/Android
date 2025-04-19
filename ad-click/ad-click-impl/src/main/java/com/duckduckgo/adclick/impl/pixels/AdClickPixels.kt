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

package com.duckduckgo.adclick.impl.pixels

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import com.duckduckgo.adclick.impl.Exemption
import com.duckduckgo.adclick.impl.pixels.AdClickPixelParameters.AD_CLICK_PAGELOADS_WITH_AD_ATTRIBUTION_COUNT
import com.duckduckgo.app.statistics.pixels.Pixel
import com.duckduckgo.di.scopes.AppScope
import com.squareup.anvil.annotations.ContributesBinding
import java.time.Instant
import java.util.concurrent.TimeUnit
import javax.inject.Inject

interface AdClickPixels {
    fun fireAdClickActivePixel(exemption: Exemption?): Boolean
    fun fireAdClickDetectedPixel(savedAdDomain: String?, urlAdDomain: String, heuristicEnabled: Boolean, domainEnabled: Boolean)
    fun updateCountPixel(adClickPixelName: AdClickPixelName)
    fun fireCountPixel(adClickPixelName: AdClickPixelName)
}

@ContributesBinding(AppScope::class)
class RealAdClickPixels @Inject constructor(
    private val pixel: Pixel,
    private val context: Context,
) : AdClickPixels {

    private val preferences: SharedPreferences by lazy { context.getSharedPreferences(FILENAME, Context.MODE_PRIVATE) }

    override fun fireAdClickActivePixel(exemption: Exemption?): Boolean {
        if (exemption == null || exemption.adClickActivePixelFired) return false

        pixel.fire(AdClickPixelName.AD_CLICK_ACTIVE)

        return true
    }

    override fun fireAdClickDetectedPixel(savedAdDomain: String?, urlAdDomain: String, heuristicEnabled: Boolean, domainEnabled: Boolean) {
        val params = mutableMapOf<String, String>()
        when {
            !savedAdDomain.isNullOrEmpty() && savedAdDomain == urlAdDomain ->
                params[AdClickPixelParameters.AD_CLICK_DOMAIN_DETECTION] = AdClickPixelValues.AD_CLICK_DETECTED_MATCHED
            !savedAdDomain.isNullOrEmpty() && urlAdDomain.isNotEmpty() && savedAdDomain != urlAdDomain ->
                params[AdClickPixelParameters.AD_CLICK_DOMAIN_DETECTION] = AdClickPixelValues.AD_CLICK_DETECTED_MISMATCH
            !savedAdDomain.isNullOrEmpty() ->
                params[AdClickPixelParameters.AD_CLICK_DOMAIN_DETECTION] = AdClickPixelValues.AD_CLICK_DETECTED_SERP_ONLY
            savedAdDomain.isNullOrEmpty() && urlAdDomain.isNotEmpty() ->
                params[AdClickPixelParameters.AD_CLICK_DOMAIN_DETECTION] = AdClickPixelValues.AD_CLICK_DETECTED_HEURISTIC_ONLY
            else ->
                params[AdClickPixelParameters.AD_CLICK_DOMAIN_DETECTION] = AdClickPixelValues.AD_CLICK_DETECTED_NONE
        }
        params[AdClickPixelParameters.AD_CLICK_HEURISTIC_DETECTION] = heuristicEnabled.toString()
        params[AdClickPixelParameters.AD_CLICK_DOMAIN_DETECTION_ENABLED] = domainEnabled.toString()
        pixel.fire(AdClickPixelName.AD_CLICK_DETECTED, params)
    }

    override fun updateCountPixel(adClickPixelName: AdClickPixelName) {
        val count = preferences.getInt(adClickPixelName.appendCountSuffix(), 0)
        preferences.edit { putInt(adClickPixelName.appendCountSuffix(), count + 1) }
    }

    override fun fireCountPixel(adClickPixelName: AdClickPixelName) {
        val now = Instant.now().toEpochMilli()

        val count = preferences.getInt(adClickPixelName.appendCountSuffix(), 0)
        if (count == 0) {
            return
        }

        val timestamp = preferences.getLong(adClickPixelName.appendTimestampSuffix(), 0L)
        if (timestamp == 0L || now >= timestamp) {
            pixel.fire(adClickPixelName, mapOf(AD_CLICK_PAGELOADS_WITH_AD_ATTRIBUTION_COUNT to count.toString()))
                .also {
                    preferences.edit {
                        putLong(adClickPixelName.appendTimestampSuffix(), now.plus(TimeUnit.HOURS.toMillis(WINDOW_INTERVAL_HOURS)))
                        putInt(adClickPixelName.appendCountSuffix(), 0)
                    }
                }
        }
    }

    private fun AdClickPixelName.appendTimestampSuffix(): String {
        return "${this.pixelName}_timestamp"
    }

    private fun AdClickPixelName.appendCountSuffix(): String {
        return "${this.pixelName}_count"
    }

    companion object {
        private const val FILENAME = "com.duckduckgo.adclick.impl.pixels"
        private const val WINDOW_INTERVAL_HOURS = 24L
    }
}
