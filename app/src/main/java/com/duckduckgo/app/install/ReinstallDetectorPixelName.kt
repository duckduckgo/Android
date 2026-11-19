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

package com.duckduckgo.app.install

import com.duckduckgo.app.statistics.pixels.Pixel.PixelName

enum class ReinstallDetectorPixelName(override val pixelName: String) : PixelName {
    BLOCK_STORE_CHECK_RESULT("atb_reinstall_check_block_store"),
}

enum class BlockStoreCheckResult(val value: String) {
    FOUND("found"),
    NOT_FOUND("not_found"),
    UNAVAILABLE("unavailable"),
    TIMEOUT("timeout"),
    ERROR("error"),
}
