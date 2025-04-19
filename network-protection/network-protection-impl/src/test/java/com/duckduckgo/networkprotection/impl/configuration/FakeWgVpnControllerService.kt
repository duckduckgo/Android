/*
 * Copyright (c) 2023 DuckDuckGo
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

import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types

class FakeWgVpnControllerService : WgVpnControllerService {
    private val moshi: Moshi = Moshi.Builder().build()
    private val serverConfigAdapter: JsonAdapter<List<RegisteredServerInfo>> = moshi.adapter(
        Types.newParameterizedType(List::class.java, RegisteredServerInfo::class.java),
    )
    private val locationConfigAdapter: JsonAdapter<List<EligibleLocation>> = moshi.adapter(
        Types.newParameterizedType(List::class.java, EligibleLocation::class.java),
    )
    private val servers = serverConfigAdapter.fromJson(SERVERS_JSON) ?: emptyList()
    private val serverLocations = locationConfigAdapter.fromJson(SERVER_LOCATIONS_JSON) ?: emptyList()

    override suspend fun getServers(): List<RegisteredServerInfo> {
        return servers
    }

    override suspend fun getServerStatus(serverName: String): ServerStatus {
        return ServerStatus(shouldMigrate = false)
    }

    override suspend fun getEligibleLocations(): List<EligibleLocation> {
        return serverLocations
    }

    override suspend fun registerKey(registerKeyBody: RegisterKeyBody): List<EligibleServerInfo> {
        return servers.filter {
            return@filter if (registerKeyBody.server != "*") {
                it.server.name == registerKeyBody.server
            } else if (registerKeyBody.country != null) {
                if (registerKeyBody.city != null) {
                    it.server.attributes["country"] == registerKeyBody.country && it.server.attributes["city"] == registerKeyBody.city
                } else {
                    it.server.attributes["country"] == registerKeyBody.country
                }
            } else {
                true
            }
        }.map { it.toEligibleServerInfo() }
    }

    private fun RegisteredServerInfo.toEligibleServerInfo(): EligibleServerInfo {
        return EligibleServerInfo(
            publicKey = "key",
            allowedIPs = listOf("10.64.169.158/32"),
            server = this.server,
        )
    }
}

private val SERVER_LOCATIONS_JSON = """
    [
      {
        "country": "nl",
        "cities": [
          {
            "name": "Rotterdam"
          }
        ]
      },
      {
        "country": "us",
        "cities": [
          {
            "name": "Newark"
          },
          {
            "name": "El Segundo"
          },
          {
            "name": "Des Moines"
          }
        ]
      },
      {
        "country": "se",
        "cities": [
          {
            "name": "Gothenburg"
          },
          {
            "name": "Malmo"
          },
          {
            "name": "Stockholm"
          }
        ]
      }
    ]
""".trimIndent()

private val SERVERS_JSON = """
            [
              {
                "expiresAt": "2023-01-30T14:02:51.056245702-05:00",
                "allowedIPs": [
                "10.64.169.158/32"
                ],
                "server": {
                  "name": "egress.usw.1",
                  "attributes": {
                    "city": "Newark",
                    "country": "us",
                    "latitude": 33.9192,
                    "longitude": -118.4165,
                    "region": "North America",
                    "tzOffset": -28800,
                    "state": "ca"
                  },
                  "publicKey": "R/BMR6Rr5rzvp7vSIWdAtgAmOLK9m7CqTcDynblM3Us=",
                  "hostnames": [],
                  "internalIp": "1.2.3.4",
                  "ips": [
                    "162.245.204.100"
                  ],
                  "port": 443
                }
              },
              {
                "expiresAt": "2023-01-30T14:02:51.109695295-05:00",
                "allowedIPs": [
                "10.64.169.158/32"
                ],
                "server": {
                  "name": "egress.euw",
                  "attributes": {
                    "city": "Amsterdam",
                    "latitude": 52.377956,
                    "longitude": 4.89707,
                    "region": "Europe",
                    "tzOffset": 3600,
                    "state": "na"
                  },
                  "publicKey": "CLQMP4SFzpyvAzMj3rXwShm+3n6Yt68hGHBF67At+x0=",
                  "hostnames": [
                    "euw.egress.np.duck.com"
                  ],
                  "internalIp": "1.2.3.4",
                  "ips": [],
                  "port": 443
                }
              },
              {
                "expiresAt": "2023-01-30T14:02:52.602419176-05:00",
                "allowedIPs": [
                "10.64.169.158/32"
                ],
                "server": {
                  "name": "egress.euw.2",
                  "attributes": {
                    "city": "Rotterdam",
                    "country": "nl",
                    "latitude": 51.9225,
                    "longitude": 4.4792,
                    "region": "Europe",
                    "tzOffset": 3600,
                    "state": "na"
                  },
                  "publicKey": "4PnM/V0CodegK44rd9fKTxxS9QDVTw13j8fxKsVud3s=",
                  "hostnames": [],
                  "internalIp": "1.2.3.4",
                  "ips": [
                    "31.204.129.39"
                  ],
                  "port": 443
                }
              },
              {
                "expiresAt": "2023-01-30T14:02:52.130321638-05:00",
                "allowedIPs": [
                "10.64.169.158/32"
                ],
                "server": {
                  "name": "egress.euw.1",
                  "attributes": {
                    "city": "Rotterdam",
                    "country": "nl",
                    "latitude": 51.9225,
                    "longitude": 4.4792,
                    "region": "Europe",
                    "tzOffset": 3600,
                    "state": "na"
                  },
                  "publicKey": "ocUfgaqaN/s/D3gTwJstipGh03T2v6wLL+aVtg3Viz4=",
                  "hostnames": [],
                  "internalIp": "1.2.3.4",
                  "ips": [
                    "31.204.129.36"
                  ],
                  "port": 443
                }
              },
              {
                "expiresAt": "2023-01-30T14:02:51.301505435-05:00",
                "allowedIPs": [
                "10.64.169.158/32"
                ],
                "server": {
                  "name": "egress.use.2",
                  "attributes": {
                    "city": "Newark",
                    "country": "us",
                    "latitude": 40.7357,
                    "longitude": -74.1724,
                    "region": "North America",
                    "tzOffset": -18000,
                    "state": "nj"
                  },
                  "publicKey": "q3YJJUwMNP31J8qSvMdVsxASKNcjrm8ep8cLcI0qViY=",
                  "hostnames": [],
                  "internalIp": "1.2.3.4",
                  "ips": [
                    "109.200.208.198"
                  ],
                  "port": 443
                }
              },
              {
                "expiresAt": "2023-01-30T14:02:50.945553924-05:00",
                "allowedIPs": [
                "10.64.169.158/32"
                ],
                "server": {
                  "name": "egress.use.1",
                  "attributes": {
                    "city": "Newark",
                    "country": "us",
                    "latitude": 40.7357,
                    "longitude": -74.1724,
                    "region": "North America",
                    "tzOffset": -18000,
                    "state": "nj"
                  },
                  "publicKey": "L4gDTg3KqbhjjiN99n/Zmwxwmbv+P+n8ZZVL0v34cAs=",
                  "hostnames": [],
                  "internalIp": "1.2.3.4",
                  "ips": [
                    "109.200.208.196"
                  ],
                  "port": 443
                }
              },
              {
                "expiresAt": "2023-01-30T14:02:52.648854078-05:00",
                "allowedIPs": [
                "10.64.169.158/32"
                ],
                "server": {
                  "name": "egress.usc",
                  "attributes": {
                    "city": "Des Moines",
                    "country": "us",
                    "latitude": 41.619549,
                    "longitude": -93.598022,
                    "region": "North America",
                    "tzOffset": -21600,
                    "state": "IA"
                  },
                  "publicKey": "ovn9RpzUuvQ4XLQt6B3RKuEXGIxa5QpTnehjduZlcSE=",
                  "hostnames": [
                    "usc.egress.np.duck.com"
                  ],
                  "internalIp": "1.2.3.4",
                  "ips": [
                    "109.200.208.196"
                  ],
                  "port": 443
                }
              },
              {
                "expiresAt": "2023-01-30T14:02:52.745221926-05:00",
                "allowedIPs": [
                "10.64.169.158/32"
                ],
                "server": {
                  "name": "egress.usw.2",
                  "attributes": {
                    "city": "El Segundo",
                    "country": "us",
                    "latitude": 33.9192,
                    "longitude": -118.4165,
                    "region": "North America",
                    "tzOffset": -28800,
                    "state": "ca"
                  },
                  "publicKey": "8JjNmnFYZA+CnWAkbiucDrUJ70wl+Tl3O3ETkRgw028=",
                  "hostnames": [],
                  "internalIp": "1.2.3.4",
                  "ips": [
                    "162.245.204.102"
                  ],
                  "port": 443
                }
              }
            ]
""".trimIndent()
