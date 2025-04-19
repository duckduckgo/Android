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

package com.duckduckgo.autofill.impl.importing.gpm.webflow

import android.os.Parcelable
import com.duckduckgo.autofill.impl.importing.gpm.webflow.ImportGooglePasswordsWebFlowViewModel.UserCannotImportReason
import kotlinx.parcelize.Parcelize

sealed interface ImportGooglePasswordResult : Parcelable {

    @Parcelize
    data object Success : ImportGooglePasswordResult

    @Parcelize
    data class UserCancelled(val stage: String) : ImportGooglePasswordResult

    @Parcelize
    data class Error(val reason: UserCannotImportReason) : ImportGooglePasswordResult

    companion object {
        const val RESULT_KEY = "importResult"
        const val RESULT_KEY_DETAILS = "importResultDetails"
    }
}
