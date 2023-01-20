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

package com.duckduckgo.networkprotection.impl.configuration

import android.content.Context
import android.telephony.TelephonyManager
import com.duckduckgo.app.global.extensions.capitalizeFirstLetter
import com.duckduckgo.di.scopes.VpnScope
import com.duckduckgo.networkprotection.impl.configuration.WgServerDataProvider.WgServerData
import com.squareup.anvil.annotations.ContributesBinding
import java.util.*
import javax.inject.Inject

interface WgServerDataProvider {
    data class WgServerData(
        val publicKey: String,
        val publicEndpoint: String,
        val address: String,
        val location: String?,
        val allowedIPs: String = "0.0.0.0/0,::0/0",
    )

    suspend fun get(publicKey: String): WgServerData
}

@ContributesBinding(VpnScope::class)
class RealWgServerDataProvider @Inject constructor(
    private val wgVpnControllerService: WgVpnControllerService,
    private val countryIsoProvider: CountryIsoProvider,
) : WgServerDataProvider {

    override suspend fun get(publicKey: String): WgServerData = wgVpnControllerService.registerKey(
        RegisterKeyBody(
            publicKey = publicKey,
        ),
    ).getRelevantServer().toWgServerData()

    private fun EligibleServerInfo.toWgServerData(): WgServerData = WgServerData(
        publicKey = server.publicKey,
        publicEndpoint = server.hostnames[0] + ":" + server.port,
        address = allowedIPs.joinToString(","),
        location = server.attributes.extractLocation(),
    )

    private fun Map<String, String>.extractLocation(): String? {
        val country = this[SERVER_ATTR_COUNTRY]
        val city = this[SERVER_ATTR_CITY]?.lowercase()?.capitalizeFirstLetter()

        return if (country != null && city != null) {
            "$city, ${country.getDisplayableCountry()}"
        } else {
            null
        }
    }

    private fun String.getDisplayableCountry(): String = Locale("", this).displayCountry.lowercase().capitalizeFirstLetter()

    private fun List<EligibleServerInfo>.getRelevantServer(): EligibleServerInfo {
        val countryCode = countryIsoProvider.getCountryIso()
        val resultingList = if (US_COUNTRY_CODES.contains(countryCode)) {
            this.filter {
                it.server.name == SERVER_NAME_US
            }
        } else {
            this.filter {
                it.server.name != SERVER_NAME_US
            }
        }

        return if (resultingList.isEmpty()) {
            this[0] // we just take the first if for some reason the usc server changes in name or disappears
        } else {
            resultingList[0] // if there's a lot of matches, we just take the first
        }
    }

    companion object {
        private const val US_COUNTRY_CODES = "us,ca"
        private const val SERVER_NAME_US = "egress.usc"
        private const val SERVER_ATTR_CITY = "city"
        private const val SERVER_ATTR_COUNTRY = "country"
    }
}

interface CountryIsoProvider {
    fun getCountryIso(): String
}

@ContributesBinding(VpnScope::class)
class SystemCountryIsoProvider @Inject constructor(
    context: Context,
) : CountryIsoProvider {
    private val telephonyManager = context.applicationContext.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager

    override fun getCountryIso(): String = telephonyManager.networkCountryIso.lowercase()
}
