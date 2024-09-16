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

package com.duckduckgo.autofill.api.credential.saving

/**
 * Allows for the creation of a login when a private duck address alias is used
 */
interface DuckAddressLoginCreator {

    /**
     * Creates a login for the given duck address, where username matches duck address and password is empty
     * @param duckAddress the duck address to create a login for
     * @param tabId the tab id of the tab that the duck address was used in
     * @param originalUrl the original url that the duck address was used in
     */
    fun createLoginForPrivateDuckAddress(
        duckAddress: String,
        tabId: String,
        originalUrl: String,
    )
}
