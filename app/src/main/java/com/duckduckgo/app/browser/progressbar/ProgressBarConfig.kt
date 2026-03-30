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

package com.duckduckgo.app.browser.progressbar

data class ProgressBarConfig(
    val fastStartTarget: Float = 20f,
    val fastStartDuration: Long = 600L,
    val springStiffness: Float = 2.0f,
    val dampingRatio: Float = 8.5f,
    val creepVelocity: Float = 0.004f,
    val endDuration: Long = 300L,
    val fadeInDuration: Long = 100L,
    val fadeOutDuration: Long = 100L,
    val shimmerSpeed: Long = 4000L,
    val shimmerBandDelay: Long = 1600L,
    val shimmerBandStartWidthDp: Float = 100f,
    val shimmerBandEndWidthDp: Float = 20f,
    val shimmerBandStartOpacity: Float = 0.4f,
    val shimmerBandEndOpacity: Float = 0.55f,
)
