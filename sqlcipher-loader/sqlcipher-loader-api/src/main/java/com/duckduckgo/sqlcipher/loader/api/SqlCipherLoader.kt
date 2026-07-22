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

package com.duckduckgo.sqlcipher.loader.api

interface SqlCipherLoader {
    /**
     * Suspends until the SQLCipher native library is ready to use.
     * Safe to call from any thread. Returns immediately if already loaded.
     *
     * @param timeoutMillis Maximum time to wait for library loading in milliseconds (default: 10 seconds)
     * @return Result.success if loaded, Result.failure on error or timeout.
     */
    suspend fun waitForLibraryLoad(timeoutMillis: Long = 10_000): Result<Unit>
}
