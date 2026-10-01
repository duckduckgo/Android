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

package com.duckduckgo.pir.api.freemium

/**
 * What the Freemium PIR entry point should show, if anything.
 */
enum class PirFreemiumEntryPoint {
    /** The user is not eligible, or is signed in, and must not see the free surface. */
    HIDDEN,

    /** Eligible, and no scan has completed yet. */
    START_FREE_SCAN,

    /** Eligible, and a scan has completed — with or without matches. */
    VIEW_SCAN_RESULTS,
}

interface PirFreemium {

    /**
     * Resolves whether the Freemium PIR entry point may be shown, and which call to action it
     * should carry.
     *
     * Runs on the IO thread by default. Never throws: any failure other than cancellation resolves to
     * [PirFreemiumEntryPoint.HIDDEN].
     */
    suspend fun getSettingsEntryPoint(): PirFreemiumEntryPoint
}
