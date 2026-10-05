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

package com.duckduckgo.pir.impl.freemium

/**
 * Where the user stands with Freemium PIR, independent of any one surface that presents it.
 */
enum class PirFreemiumState {
    /** Freemium PIR must not be offered: a gate is closed, or the user is signed in. */
    NOT_ELIGIBLE,

    /** Eligible, and no free scan has completed yet. */
    ELIGIBLE,

    /**
     * Eligible, and a free scan has completed — with or without matches. Activation alone (a saved
     * profile whose scan is still running) is still [ELIGIBLE].
     */
    USED,
}

interface PirFreemium {

    /**
     * Runs on the IO thread by default. Never throws: any failure other than cancellation resolves to
     * [PirFreemiumState.NOT_ELIGIBLE].
     */
    suspend fun getPirFreemiumState(): PirFreemiumState
}
