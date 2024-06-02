/*
 * Copyright (c) 2018 DuckDuckGo
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

package com.duckduckgo.app.onboarding.store

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import com.duckduckgo.app.browser.R
import com.duckduckgo.app.cta.ui.DaxBubbleCta.DaxDialogIntroOption
import com.duckduckgo.app.onboarding.ui.page.extendedonboarding.OnboardingExperimentPixel.PixelName.ONBOARDING_SEARCH_MIGHTY_DUCK
import com.duckduckgo.app.onboarding.ui.page.extendedonboarding.OnboardingExperimentPixel.PixelName.ONBOARDING_SEARCH_SAY_DUCK
import com.duckduckgo.app.onboarding.ui.page.extendedonboarding.OnboardingExperimentPixel.PixelName.ONBOARDING_SEARCH_SURPRISE_ME
import com.duckduckgo.app.onboarding.ui.page.extendedonboarding.OnboardingExperimentPixel.PixelName.ONBOARDING_SEARCH_WEATHER
import com.duckduckgo.app.onboarding.ui.page.extendedonboarding.OnboardingExperimentPixel.PixelName.ONBOARDING_VISIT_SITE_EBAY
import com.duckduckgo.app.onboarding.ui.page.extendedonboarding.OnboardingExperimentPixel.PixelName.ONBOARDING_VISIT_SITE_ESPN
import com.duckduckgo.app.onboarding.ui.page.extendedonboarding.OnboardingExperimentPixel.PixelName.ONBOARDING_VISIT_SITE_SURPRISE_ME
import com.duckduckgo.app.onboarding.ui.page.extendedonboarding.OnboardingExperimentPixel.PixelName.ONBOARDING_VISIT_SITE_YAHOO
import com.duckduckgo.mobile.android.R.drawable
import java.util.Locale
import javax.inject.Inject

class OnboardingStoreImpl @Inject constructor(private val context: Context) : OnboardingStore {

    private val preferences: SharedPreferences by lazy { context.getSharedPreferences(FILENAME, Context.MODE_PRIVATE) }

    override var onboardingDialogJourney: String?
        get() = preferences.getString(ONBOARDING_JOURNEY, null)
        set(dialogJourney) = preferences.edit { putString(ONBOARDING_JOURNEY, dialogJourney) }

    override fun getSearchOptions(): List<DaxDialogIntroOption> {
        val country = Locale.getDefault().country
        val language = Locale.getDefault().language

        return listOf(
            DaxDialogIntroOption(
                optionText = if (language == "en") {
                    context.getString(R.string.onboardingSearchDaxDialogOption1English)
                } else {
                    context.getString(R.string.onboardingSearchDaxDialogOption1)
                },
                iconRes = drawable.ic_find_search_16,
                link = if (language == "en") "how to say duck in spanish" else context.getString(R.string.onboardingSearchQueryOption1),
                pixel = ONBOARDING_SEARCH_SAY_DUCK,
            ),
            DaxDialogIntroOption(
                optionText = if (country == "US") "mighty ducks cast" else context.getString(R.string.onboardingSearchDaxDialogOption2),
                iconRes = drawable.ic_find_search_16,
                link = if (country == "US") "mighty ducks cast" else context.getString(R.string.onboardingSearchDaxDialogOption2),
                pixel = ONBOARDING_SEARCH_MIGHTY_DUCK,
            ),
            DaxDialogIntroOption(
                optionText = context.getString(R.string.onboardingSearchDaxDialogOption3),
                iconRes = drawable.ic_find_search_16,
                link = context.getString(R.string.onboardingSearchDaxDialogOption3),
                pixel = ONBOARDING_SEARCH_WEATHER,
            ),
            DaxDialogIntroOption(
                optionText = context.getString(R.string.onboardingSearchDaxDialogOption4),
                iconRes = drawable.ic_wand_16,
                link = if (country == "US") "chocolate chip cookie recipes" else context.getString(R.string.onboardingSearchQueryOption4),
                pixel = ONBOARDING_SEARCH_SURPRISE_ME,
            ),
        )
    }

    override fun getSitesOptions(): List<DaxDialogIntroOption> {
        val site1: String
        val site2: String
        val site3: String
        val site4Query: String
        when (Locale.getDefault().country) {
            "ID" -> {
                site1 = "bolasport.com "
                site2 = "kompas.com"
                site3 = "tokopedia.com"
                site4Query = "britannica.com/animal/duck"
            }

            "GB" -> {
                site1 = "skysports.com "
                site2 = "bbc.co.uk"
                site3 = "ebay.com"
                site4Query = "britannica.com/animal/duck"
            }

            "DE" -> {
                site1 = "kicker.de"
                site2 = "tagesschau.de"
                site3 = "ebay.com"
                site4Query = "duden.de/rechtschreibung/Ente"
            }

            "CA" -> {
                site1 = "tsn.ca"
                site2 = "cbc.ca"
                site3 = "canadiantire.ca"
                site4Query = "britannica.com/animal/duck"
            }

            "NL" -> {
                site1 = "voetbalprimeur.nl"
                site2 = "nu.nl"
                site3 = "bol.com"
                site4Query = "woorden.org/woord/eend"
            }

            "AU" -> {
                site1 = "afl.com.au"
                site2 = "abc.net.au"
                site3 = "ebay.com"
                site4Query = "britannica.com/animal/duck"
            }

            "SE" -> {
                site1 = "svenskafans.com"
                site2 = "dn.se"
                site3 = "tradera.com"
                site4Query = "synonymer.se/sv-syn/anka"
            }

            "IE" -> {
                site1 = "skysports.com"
                site2 = "bbc.co.uk "
                site3 = "ebay.com"
                site4Query = "britannica.com/animal/duck"
            }

            else -> {
                site1 = "espn.com"
                site2 = "yahoo.com"
                site3 = "ebay.com"
                site4Query = "britannica.com/animal/duck"
            }
        }
        return listOf(
            DaxDialogIntroOption(
                optionText = site1,
                iconRes = drawable.ic_globe_gray_16dp,
                link = site1,
                pixel = ONBOARDING_VISIT_SITE_ESPN,
            ),
            DaxDialogIntroOption(
                optionText = site2,
                iconRes = drawable.ic_globe_gray_16dp,
                link = site2,
                pixel = ONBOARDING_VISIT_SITE_YAHOO,
            ),
            DaxDialogIntroOption(
                optionText = site3,
                iconRes = drawable.ic_globe_gray_16dp,
                link = site3,
                pixel = ONBOARDING_VISIT_SITE_EBAY,
            ),
            DaxDialogIntroOption(
                optionText = context.getString(R.string.onboardingSitesDaxDialogOption4),
                iconRes = drawable.ic_wand_16,
                link = site4Query,
                pixel = ONBOARDING_VISIT_SITE_SURPRISE_ME,
            ),
        )
    }

    companion object {
        const val FILENAME = "com.duckduckgo.app.onboarding.settings"
        const val ONBOARDING_JOURNEY = "onboardingJourney"
    }
}
