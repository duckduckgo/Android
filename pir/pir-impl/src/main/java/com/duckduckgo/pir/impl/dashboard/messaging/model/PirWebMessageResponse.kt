/*
 * Copyright (c) 2025 DuckDuckGo
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

package com.duckduckgo.pir.impl.dashboard.messaging.model

import com.duckduckgo.pir.impl.dashboard.messaging.PirDashboardWebConstants
import com.duckduckgo.pir.impl.dashboard.messaging.model.PirWebMessageResponse.GetDataBrokersResponse.DataBroker

/**
 * Represents the response body sent by the client back to the JS layer in Pir Web UI.
 */
sealed interface PirWebMessageResponse {

    data class DefaultResponse(
        val success: Boolean,
        val version: Int = PirDashboardWebConstants.SCRIPT_API_VERSION,
    ) : PirWebMessageResponse {
        companion object {
            val SUCCESS = DefaultResponse(success = true)
            val ERROR = DefaultResponse(success = false)
        }
    }

    data class HandshakeResponse(
        val success: Boolean,
        val userData: UserData,
        val version: Int = PirDashboardWebConstants.SCRIPT_API_VERSION,
    ) : PirWebMessageResponse {

        data class UserData(
            val isAuthenticatedUser: Boolean,
            val isUserEligibleForFreeTrial: Boolean,
        )
    }

    data class GetCurrentUserProfileResponse(
        val names: List<Name>,
        val birthYear: Int? = null,
        val addresses: List<Address>,
    ) : PirWebMessageResponse {

        data class Address(
            val city: String,
            val state: String,
        )

        data class Name(
            val first: String,
            val middle: String? = null,
            val last: String,
        )
    }

    data class GetDataBrokersResponse(
        val dataBrokers: List<DataBroker>,
    ) : PirWebMessageResponse {

        data class DataBroker(
            val name: String,
            val url: String,
            val optOutUrl: String? = null,
            val parentURL: String? = null,
        )
    }

    data class InitialScanResponse(
        val resultsFound: List<ScanResult>,
        val scanProgress: ScanProgress,
    ) : PirWebMessageResponse {

        data class ScanResult(
            val id: Long? = 0L,
            val dataBroker: DataBroker,
            val name: String,
            val addresses: List<ScanResultAddress>,
            val alternativeNames: List<String>,
            val relatives: List<String>,
            val foundDate: Long,
            val optOutSubmittedDate: Long?,
            val estimatedRemovalDate: Long?,
            val removedDate: Long?,
            val hasMatchingRecordOnParentBroker: Boolean,
        ) {
            data class ScanResultAddress(
                val street: String? = null,
                val city: String?,
                val state: String?,
            )
        }

        data class ScanProgress(
            val currentScan: Int,
            val totalScans: Int,
            val scannedBrokers: List<ScannedBroker>,
        ) {
            data class ScannedBroker(
                val name: String,
                val url: String,
                val optOutUrl: String? = null,
                val parentURL: String? = null,
                val status: String,
            )
        }
    }

    data class GetFeatureConfigResponse(
        val useUnifiedFeedback: Boolean,
    ) : PirWebMessageResponse
}
