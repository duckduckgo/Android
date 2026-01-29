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

package com.duckduckgo.data.store.impl

import com.duckduckgo.app.statistics.pixels.Pixel

enum class DataStorePixelNames(override val pixelName: String) : Pixel.PixelName {
    DATA_STORE_MIGRATE_ENCRYPTED_GET_PREFERENCES_ORIGIN_FAILED("data-store_migrate_encrypted_get-preferences_origin_failed"),
    DATA_STORE_MIGRATE_UNENCRYPTED_GET_PREFERENCES_ORIGIN_FAILED("data-store_migrate_unencrypted_get-preferences_origin_failed"),
    DATA_STORE_MIGRATE_ENCRYPTED_GET_PREFERENCES_DESTINATION_FAILED("data-store_migrate_encrypted_get-preferences_destination_failed"),
    DATA_STORE_MIGRATE_UNENCRYPTED_GET_PREFERENCES_DESTINATION_FAILED("data-store_migrate_unencrypted_get-preferences_destination_failed"),
    DATA_STORE_MIGRATE_ENCRYPTED_QUERY_PREFERENCES_DESTINATION_FAILED("data-store_migrate_encrypted_query-preferences_destination_failed"),
    DATA_STORE_MIGRATE_UNENCRYPTED_QUERY_PREFERENCES_DESTINATION_FAILED("data-store_migrate_unencrypted_query-preferences_destination_failed"),
    DATA_STORE_MIGRATE_ENCRYPTED_QUERY_ALL_PREFERENCES_ORIGIN_FAILED("data-store_migrate_encrypted_query-all-preferences_origin_failed"),
    DATA_STORE_MIGRATE_UNENCRYPTED_QUERY_ALL_PREFERENCES_ORIGIN_FAILED("data-store_migrate_unencrypted_query-all-preferences_origin_failed"),
    DATA_STORE_MIGRATE_ENCRYPTED_UPDATE_PREFERENCES_DESTINATION_FAILED("data-store_migrate_encrypted_update-preferences_destination_failed"),
    DATA_STORE_MIGRATE_UNENCRYPTED_UPDATE_PREFERENCES_DESTINATION_FAILED("data-store_migrate_unencrypted_update-preferences_destination_failed"),
}
